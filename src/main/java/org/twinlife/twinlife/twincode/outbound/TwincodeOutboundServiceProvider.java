/*
 *  Copyright (c) 2014-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.outbound;

import android.content.ContentValues;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.AttributeNameImageIdValue;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.database.Columns;
import org.twinlife.twinlife.database.DatabaseDump;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.DatabaseServiceProvider;
import org.twinlife.twinlife.database.Tables;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.database.TwincodeObjectFactory;
import org.twinlife.twinlife.database.TwincodesCleaner;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class TwincodeOutboundServiceProvider extends DatabaseServiceProvider implements TwincodeObjectFactory<TwincodeOutbound>, TwincodesCleaner {
    private static final String LOG_TAG = "TwincodeOutboundServ...";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("20b764ab-7069-4c28-8cab-8c2926d7334a");
    private static final int MAX_REFRESH_TWINCODES = 20;
    private static final long REFRESH_PERIOD = 3600*1000L;

    /**
     * twincodeOutbound table:
     * id INTEGER: local database identifier (primary key)
     * twincodeId TEXT UNIQUE NOT NULL: twincode outbound id
     * creationDate INTEGER: twincode creation date
     * modificationDate INTEGER: twincode modification date
     * name TEXT: name attribute
     * avatarId INTEGER: avatar id attribute
     * capabilities TEXT: capabilities attribute
     * description TEXT: description attribute
     * attributes BLOB: other attributes (serialized)
     * refreshPeriod INTEGER: period in ms to refresh the twincode information
     * refreshDate INTEGER: deadline date for the next refresh for the twincode information
     * refreshTimestamp INTEGER: server timestamp from a previous/past refresh
     * flags INTEGER NOT NULL: various control flags
     * <p>
     * Note: id, twincodeId, creationDate are readonly.
     */
    private static final String TWINCODE_OUTBOUND_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS twincodeOutbound (id INTEGER PRIMARY KEY,"
                    + " twincodeId TEXT UNIQUE NOT NULL, creationDate INTEGER NOT NULL, modificationDate INTEGER NOT NULL,"
                    + " name TEXT, avatarId INTEGER, capabilities TEXT, description TEXT, attributes BLOB,"
                    + " refreshPeriod INTEGER DEFAULT 3600000, refreshDate INTEGER DEFAULT 0,"
                    + " refreshTimestamp INTEGER, flags INTEGER NOT NULL"
                    + ")";

    /**
     * twinlife-1.0 twincodeOutboundTwincodeOutbound table:
     * uuid TEXT: twincode outbound id
     * refreshPeriod INTEGER: period in ms to refresh the twincode information
     * refreshDate INTEGER: deadline date for the next refresh for the twincode information
     * refreshTimestamp INTEGER: server timestamp from a previous/past refresh
     * content BLOB: the twincode data.
     * <p>
     * CREATE TABLE IF NOT EXISTS twincodeOutboundTwincodeOutbound (uuid TEXT PRIMARY KEY, " +
     *                     "refreshPeriod INTEGER DEFAULT 3600000, refreshDate INTEGER DEFAULT 0, refreshTimestamp INTEGER, content BLOB);
     */

    private static final String ALTER_TWINCODE_OUTBOUND_ADD_REFRESH_PERIOD =
            "ALTER TABLE twincodeOutboundTwincodeOutbound ADD COLUMN refreshPeriod INTEGER DEFAULT 3600000";

    private static final String ALTER_TWINCODE_OUTBOUND_ADD_REFRESH_DATE =
            "ALTER TABLE twincodeOutboundTwincodeOutbound ADD COLUMN refreshDate INTEGER DEFAULT 0";

    private static final String ALTER_TWINCODE_OUTBOUND_ADD_REFRESH_TIMESTAMP =
            "ALTER TABLE twincodeOutboundTwincodeOutbound ADD COLUMN refreshTimestamp INTEGER";

    TwincodeOutboundServiceProvider(@NonNull TwincodeOutboundServiceImpl service,
                                    @NonNull DatabaseServiceImpl database) {
        super(service, database, TWINCODE_OUTBOUND_CREATE_TABLE, DatabaseTable.TABLE_TWINCODE_OUTBOUND);
        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeOutboundServiceProvider: service=" + service);
        }

        database.setTwincodeOutboundFactory(this);
    }

    @Override
    protected void onUpgrade(@NonNull Transaction transaction, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade: oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        /*
         * <pre>
         * Database Version 20
         *  Date: 2023/08/29
         *   New database model with twincodeOutbound table and change of primary key
         *
         * Database Version 11
         *  Date: 2020/05/25
         *
         *  TwincodeOutboundService
         *   Update oldVersion [3,9]:
         *    Add column refreshPeriod INTEGER in  twincodeOutboundTwincodeOutbound
         *    Add column refreshDate INTEGER in  twincodeOutboundTwincodeOutbound
         *    Add column refreshTimestamp INTEGER in  twincodeOutboundTwincodeOutbound
         *   Update oldVersion [0,2]: reset
         *
         * </pre>
         */

        if (oldVersion > 2 && oldVersion < 10) {
            // Note: it is safe to run this migration code several times on the same database.
            try {
                transaction.createSchema(ALTER_TWINCODE_OUTBOUND_ADD_REFRESH_PERIOD);
            } catch (DatabaseException exception) {
                mService.onDatabaseException(exception);
            }
            try {
                transaction.createSchema(ALTER_TWINCODE_OUTBOUND_ADD_REFRESH_DATE);
            } catch (DatabaseException exception) {
                mService.onDatabaseException(exception);
            }
            try {
                transaction.createSchema(ALTER_TWINCODE_OUTBOUND_ADD_REFRESH_TIMESTAMP);
            } catch (DatabaseException exception) {
                mService.onDatabaseException(exception);
            }
        }

        super.onUpgrade(transaction, oldVersion, newVersion);
        if (oldVersion < 20 && transaction.hasTable("twincodeOutboundTwincodeOutbound")) {
            upgrade20(transaction);
        }

        if (oldVersion > 20 && oldVersion <= 23) {
            upgradeRepair20_23(transaction);
        }

        // When debugging, dump the twincode table.
        if (DEBUG) {
            DatabaseDump.printTwincodeOutbound(mDatabase);
        }
    }

    //
    // Implement DatabaseObjectFactory interface
    //

    @Override
    @NonNull
    public UUID getSchemaId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSchemaId");
        }

        return SCHEMA_ID;
    }

    @Override
    public int getSchemaVersion() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSchemaVersion");
        }

        return 0;
    }

    @Override
    @NonNull
    public TwincodeOutbound createObject(@NonNull DatabaseIdentifier identifier,
                                         @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObject identifier=" + identifier + " offset=" + offset);
        }

        // to.twincodeId, to.modificationDate, to.name, to.avatarId, to.description, to.capabilities, to.attributes, to.flags
        final UUID twincodeId = cursor.getUUID(offset);
        final long modificationDate = cursor.getLong(offset + 1);
        final String name = cursor.getString(offset + 2);
        final long avatarId = cursor.getLong(offset + 3);
        final String description = cursor.getString(offset + 4);
        final String capabilities = cursor.getString(offset + 5);
        final byte[] content = cursor.getBlob(offset + 6);
        final int flags = cursor.getInt(offset + 7);
        return new TwincodeOutboundImpl(identifier, twincodeId, modificationDate, name, description,
                avatarId != 0 ? new ImageId(avatarId) : null, capabilities, content, flags);
    }

    @Override
    public boolean loadObject(@NonNull TwincodeOutbound object, @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadObject object=" + object + " offset=" + offset);
        }

        final TwincodeOutboundImpl twincodeOutbound = (TwincodeOutboundImpl) object;
        // final UUID twincodeId = cursor.getUUID(offset);
        final long modificationDate = cursor.getLong(offset + 1);
        if (twincodeOutbound.getModificationDate() == modificationDate) {
            return false;
        }
        final String name = cursor.getString(offset + 2);
        final long avatarId = cursor.getLong(offset + 3);
        final String description = cursor.getString(offset + 4);
        final String capabilities = cursor.getString(offset + 5);
        final byte[] content = cursor.getBlob(offset + 6);
        final int flags = cursor.getInt(offset + 7);
        twincodeOutbound.update(modificationDate, name, description,
                avatarId != 0 ? new ImageId(avatarId) : null, capabilities, content, flags);
        return true;
    }

    @Override
    public TwincodeOutbound storeObject(@NonNull Transaction transaction, @NonNull DatabaseIdentifier identifier,
                                        @NonNull UUID twincodeId,
                                        @Nullable List<AttributeNameValue> attributes,
                                        int flags,
                                        long modificationDate, long refreshPeriod,
                                        long refreshDate, long refreshTimestamp,
                                        @Nullable Initializer<TwincodeOutbound> initializer) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "storeObject identifier=" + identifier + " twincodeId=" + twincodeId);
        }

        TwincodeOutboundImpl twincodeOutbound = new TwincodeOutboundImpl(identifier, twincodeId, flags, modificationDate, attributes);
        if (initializer != null) {
            initializer.initialize(twincodeOutbound);
        }

        if (refreshPeriod > 0 && refreshDate == 0) {
            refreshDate = modificationDate + refreshPeriod;
        }
        ContentValues values = new ContentValues();
        values.put(Columns.ID, identifier.getId());
        values.put(Columns.TWINCODE_ID, twincodeId.toString());
        values.put(Columns.REFRESH_PERIOD, refreshPeriod);
        values.put(Columns.NAME, twincodeOutbound.getName());
        values.put(Columns.DESCRIPTION, twincodeOutbound.getDescription());
        if (twincodeOutbound.getAvatarId() != null) {
            values.put(Columns.AVATAR_ID, twincodeOutbound.getAvatarId().getId());
        }
        values.put(Columns.CAPABILITIES, twincodeOutbound.getCapabilities());
        values.put(Columns.CREATION_DATE, modificationDate);
        values.put(Columns.MODIFICATION_DATE, modificationDate);
        values.put(Columns.FLAGS, twincodeOutbound.getFlags());
        if (refreshDate > 0) {
            values.put(Columns.REFRESH_DATE, refreshDate);
        }
        if (refreshTimestamp > 0) {
            values.put(Columns.REFRESH_TIMESTAMP, refreshTimestamp);
        }
        byte[] data = twincodeOutbound.serialize();
        if (data != null) {
            values.put(Columns.ATTRIBUTES, data);
        }
        transaction.insertOrThrow(Tables.TWINCODE_OUTBOUND, null, values);
        mDatabase.putCache(twincodeOutbound);
        return twincodeOutbound;
    }

    @Override
    public boolean isLocal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isLocal");
        }

        return false;
    }

    @Override
    public void deleteTwincode(@NonNull Transaction transaction, @NonNull TwincodeOutbound twincodeOutbound) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteTwincode: transaction=" + transaction + " twincodeOutbound=" + twincodeOutbound);
        }

        final ImageId avatarId = twincodeOutbound.getAvatarId();
        final long twincodeId = twincodeOutbound.getDatabaseId().getId();

        //  Also delete the twincode keys and every secret we could have with that twincode.
        transaction.deleteWithId(Tables.TWINCODE_KEYS, twincodeId);
        transaction.delete(Tables.SECRET_KEYS, "id=? OR peerTwincodeId=?", new Object[] { Long.toString(twincodeId), Long.toString(twincodeId) });
        transaction.deleteObject(twincodeOutbound);
        if (avatarId != null) {
            transaction.deleteImage(avatarId);
        }
    }

    //
    // Package scoped methods
    //

    @Nullable
    TwincodeOutbound loadTwincode(@NonNull UUID twincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadTwincode: twincodeOutboundId=" + twincodeOutboundId);
        }

        try {
            return mDatabase.loadTwincodeOutbound(twincodeOutboundId);

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    /**
     * Update an existing twincode in the database.  The twincode instance is updated with the
     * given attributes and saved in the database.
     * Note: this is one of our twincode.
     *
     * @param twincodeOutboundImpl the twincode object.
     * @param attributes the twincode attributes to update.
     * @param modificationTimestamp the new twincode modification date.
     * @param signed must be true to mark the twincode outbound with FLAG_SIGNED.
     */
    void updateTwincode(@NonNull TwincodeOutboundImpl twincodeOutboundImpl,
                        @NonNull List<AttributeNameValue> attributes,
                        long modificationTimestamp,
                        boolean signed) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateTwincode: twincodeOutboundImpl=" + twincodeOutboundImpl);
        }

        try (Transaction transaction = newTransaction()) {
            // If our twincode is signed, mark it as TRUSTED so that the TwinmeFramework knows the public keys
            // are set correctly and the server has received the signature.
            int twincodeFlags = twincodeOutboundImpl.getFlags();
            if (signed) {
                twincodeFlags |= TwincodeOutboundImpl.FLAG_SIGNED | TwincodeOutboundImpl.toFlags(TrustMethod.OWNER);
            }
            internalUpdateTwincode(transaction, twincodeOutboundImpl, twincodeFlags, attributes, null, modificationTimestamp);
            transaction.commit();
            twincodeOutboundImpl.setFlags(twincodeFlags);

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Import a possibly new twincode from the server in the database.  The twincode is associated with the refresh
     * timestamp and period.  The refresh update is scheduled to be the current date + refresh period.
     *
     * @param twincodeId the twincode to insert/update.
     * @param attributes the list of attributes to update.
     * @param pubSigningKey the public key used to sign the twincode attributes.
     * @param pubEncryptionKey the public key used to encrypt twincode invocations.
     * @param keyIndex the key index associated with the optional secret.
     * @param secretKey the optional secret key provided by the peer to decrypt its messages.
     * @param trust whether the public key is trusted and how (ie, received or validated through an external channel).
     * @param modificationDate the new modification date.
     * @param refreshPeriod the refresh period (in ms).
     */
    @Nullable
    TwincodeOutbound importTwincode(@NonNull UUID twincodeId, @NonNull List<AttributeNameValue> attributes,
                                    @Nullable byte[] pubSigningKey, @Nullable byte[] pubEncryptionKey,
                                    int keyIndex, @Nullable byte[] secretKey, @NonNull TrustMethod trust,
                                    long modificationDate, long refreshPeriod) {
        if (DEBUG) {
            Log.d(LOG_TAG, "importTwincode: twincodeId=" + twincodeId);
        }

        try (Transaction transaction = newTransaction()) {
            Long id = mDatabase.longQuery("SELECT"
                    + " twout.id"
                    + " FROM twincodeOutbound AS twout"
                    + " WHERE twout.twincodeId = ?", new String[]{ twincodeId.toString() });
            int flags;
            if (pubSigningKey == null) {
                flags = 0;
            } else {
                flags = TwincodeOutboundImpl.FLAG_SIGNED | TwincodeOutboundImpl.FLAG_VERIFIED
                        | TwincodeOutboundImpl.toFlags(trust);
            }
            if (id != null) {
                DatabaseObject object = mDatabase.getCache(new DatabaseIdentifier(this, id));
                if (object == null) {
                    object = mDatabase.loadTwincodeOutbound(id);
                }
                if (!(object instanceof TwincodeOutboundImpl)) {
                    return null;
                }

                TwincodeOutboundImpl twincodeOutbound = (TwincodeOutboundImpl) object;
                if (!twincodeOutbound.isKnown() || flags != twincodeOutbound.getFlags() || secretKey != null) {
                    final boolean isOwner = twincodeOutbound.isOwner();
                    if (isOwner) {
                        // Keep the existing flags if we are owner of the twincode and don't store the public key!
                        flags = twincodeOutbound.getFlags();
                    }
                    flags = flags & ~(TwincodeOutboundImpl.FLAG_NEED_FETCH);
                    internalUpdateTwincode(transaction, twincodeOutbound, flags, attributes, null, modificationDate);
                    if (pubSigningKey != null && !isOwner) {
                        transaction.storePublicKey(twincodeOutbound, pubSigningKey, pubEncryptionKey, keyIndex, secretKey);
                    }
                    transaction.commit();
                    twincodeOutbound.setFlags(flags);
                }
                return twincodeOutbound;

            } else {
                TwincodeOutbound twincodeOutbound = transaction.storeTwincodeOutbound(twincodeId, attributes, flags,
                        modificationDate, refreshPeriod, 0, 0);
                if (pubSigningKey != null) {
                    transaction.storePublicKey(twincodeOutbound, pubSigningKey, pubEncryptionKey, keyIndex, secretKey);
                }
                transaction.commit();
                return twincodeOutbound;
            }
        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    /**
     * Remove the twincode from the database if there is no reference to it from a Conversation and Repository.
     * The image associated with the twincode is also evicted.
     *
     * @param twincodeOutbound the twincode to evict.
     * @param twincodeOutboundId the twincode Id to evict (when twincodeOutbound is NULL).
     */
    void evictTwincode(@Nullable TwincodeOutbound twincodeOutbound,
                       @Nullable UUID twincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "evictTwincode: twincodeOutbound=" + twincodeOutbound + " twincodeOutboundId=" + twincodeOutboundId);
        }

        try (Transaction transaction = newTransaction()) {
            if (twincodeOutboundId != null) {
                twincodeOutbound = mDatabase.loadTwincodeOutbound(twincodeOutboundId);
            }
            if (twincodeOutbound == null) {
                return;
            }

            final long twincodeId = twincodeOutbound.getDatabaseId().getId();
            final String id = String.valueOf(twincodeId);
            Long usedRepo = mDatabase.longQuery("SELECT COUNT(*) FROM repository WHERE peerTwincodeOutbound=?"
                    + " OR twincodeOutbound=?", new Object[] { id, id });
            Long usedConv = mDatabase.longQuery("SELECT COUNT(*) FROM conversation WHERE peerTwincodeOutbound=?",
                    new Object[] { id });
            if (usedRepo != null && usedConv != null && (usedRepo + usedConv == 0)) {
                deleteTwincode(transaction, twincodeOutbound);
                transaction.commit();
            }

        } catch (Exception ex) {
            mService.onDatabaseException(ex);
        }
    }

    void deleteTwincode(@NonNull Long twincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteTwincode: twincodeId=" + twincodeId);
        }

        try (Transaction transaction = newTransaction()) {
            final TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(twincodeId);
            if (twincodeOutbound != null) {
                transaction.deleteConversations(null, twincodeId);
                deleteTwincode(transaction, twincodeOutbound);
                transaction.commit();
            }

        } catch (Exception ex) {
            mService.onDatabaseException(ex);
        }
    }

    /**
     * Get the next deadline date to refresh the twincodes.
     *
     * @return the next refresh deadline date, 0 means there is nothing to refresh.
     */
    long getRefreshDeadline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getRefreshDeadline");
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT COUNT(*), MIN(refreshDate) FROM twincodeOutbound " +
                "WHERE refreshPeriod > 0", null)) {
            if (cursor.moveToFirst()) {
                long count = cursor.getLong(0);
                long refreshDate = cursor.getLong(1);
                // We could have a refreshDate == 0, in that case use a positive value to trigger an immediate refresh.
                return count == 0 ? 0 : (refreshDate <= 0 ? 1000L : refreshDate);
            }
        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        return 0;
    }

    static class RefreshInfo {
        final Map<UUID, Long> twincodes;
        final long timestamp;

        RefreshInfo(Map<UUID, Long> twincodes, long timestamp) {
            this.twincodes = twincodes;
            this.timestamp = timestamp;
        }
    }

    /**
     * Get a list of twincodes that must be refreshed.
     *
     * @return the list of twincodes with min refresh timestamp.
     */
    @NonNull
    RefreshInfo getRefreshList() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getRefreshList");
        }

        final Map<UUID, Long> result = new HashMap<>();
        long timestamp = Long.MAX_VALUE;
        try {
            long now = System.currentTimeMillis();
            try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id, twincodeId, refreshTimestamp"
                    + " FROM twincodeOutbound WHERE refreshPeriod > 0 AND refreshDate < ? LIMIT ?", new String[]{
                    Long.toString(now),
                    Integer.toString(MAX_REFRESH_TWINCODES)
            })) {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    UUID uuid = cursor.getUUID(1);
                    if (uuid != null) {
                        long twincodeTimestamp = cursor.getLong(2);
                        if (twincodeTimestamp < timestamp) {
                            timestamp = twincodeTimestamp;
                        }

                        result.put(uuid, id);
                    }
                }
            }
        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        return new RefreshInfo(result, timestamp);
    }

    /**
     * Refresh the twincode in the database after an explicit refresh.
     *
     * @param twincodeOutbound the twincode id to refresh.
     * @param attributes the updated twincode attributes.
     * @param modificationTimestamp the new twincode modification date.
     * @param previousAttributes populate the list with the attributes that are modified.
     */
    void refreshTwincodeOutbound(@NonNull TwincodeOutboundImpl twincodeOutbound,
                                 @NonNull List<AttributeNameValue> attributes,
                                 @NonNull List<AttributeNameValue> previousAttributes,
                                 long modificationTimestamp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshTwincodeOutbound: twincodeOutbound=" + twincodeOutbound);
        }

        try (Transaction transaction = newTransaction()) {
            internalUpdateTwincode(transaction, twincodeOutbound, twincodeOutbound.getFlags(), attributes, previousAttributes, modificationTimestamp);
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Refresh the twincode in the database when it was changed on the server.  The twincode is associated with the refresh
     * timestamp and period.  The refresh update is scheduled to be the current date + refresh period.
     *
     * @param twincodeId the twincode id to refresh.
     * @param attributes the updated twincode attributes.
     * @param serverTimestamp the new twincode modification date.
     * @return the twincode outbound instance or null
     */
    @Nullable
    TwincodeOutbound refreshTwincode(@NonNull Long twincodeId,
                                     @NonNull List<AttributeNameValue> attributes,
                                     @NonNull List<AttributeNameValue> previousAttributes,
                                     long serverTimestamp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshTwincode: twincodeId=" + twincodeId);
        }

        try (Transaction transaction = newTransaction()) {
            TwincodeOutboundImpl twincodeOutbound = (TwincodeOutboundImpl) mDatabase.loadTwincodeOutbound(twincodeId);
            if (twincodeOutbound == null) {
                // This twincode is removed from the database: no need to refresh it.
                return null;
            }

            internalUpdateTwincode(transaction, twincodeOutbound, twincodeOutbound.getFlags(), attributes, previousAttributes, serverTimestamp);
            transaction.commit();
            return twincodeOutbound;

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    /**
     * Update the twincode refresh information and setup a new refresh date based on the currentDate and the twincode refresh period.
     *
     * @param twincodeIds the list of twincode ids to update.
     * @param refreshTimestamp the twincode refresh date (in ms).
     * @param currentDate the current date to compute the next twincode refresh date.
     */
    void updateRefreshTimestamp(@NonNull Collection<Long> twincodeIds, long refreshTimestamp, long currentDate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateRefreshTimestamp: twincodeIds=" + twincodeIds + " refreshTimestamp=" + refreshTimestamp
                    + " currentDate=" + currentDate);
        }

        try (Transaction transaction = newTransaction()) {
            for (Long twincodeId : twincodeIds) {
                transaction.execSQLWithArgs("UPDATE twincodeOutbound"
                        + " SET refreshTimestamp = ?, refreshDate = ? + refreshPeriod WHERE id=?", new String[]{
                        Long.toString(refreshTimestamp),
                        Long.toString(currentDate),
                        String.valueOf(twincodeId)
                });
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void associateTwincodes(@NonNull TwincodeOutboundImpl twincodeOutbound,
                            @Nullable TwincodeOutbound previousPeerTwincodeOutbound,
                            @NonNull TwincodeOutboundImpl peerTwincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "associateTwincodes twincodeOutbound=" + twincodeOutbound
                    + " previousPeerTwincodeOutbound=" + previousPeerTwincodeOutbound + " newPeerTwincode=" + peerTwincodeOutbound);
        }

        try (Transaction transaction = newTransaction()) {
            final long twincodeId = twincodeOutbound.getDatabaseId().getId();
            final long now = System.currentTimeMillis();

            // If a previous peer twincode is defined, re-associate the secrets to the new { twincode, peerTwincode } pair.
            if (previousPeerTwincodeOutbound != null) {
                final long oldPeerId = previousPeerTwincodeOutbound.getDatabaseId().getId();
                final String[] previouskeys = {
                        Long.toString(twincodeId),
                        Long.toString(oldPeerId)
                };

                try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT flags, creationDate, secretUpdateDate, secret1, secret2"
                        + " FROM secretKeys WHERE id=? AND peerTwincodeId=?", previouskeys)) {
                    if (!cursor.moveToNext()) {
                        // Previous key association was not found, nothing to associate.
                        return;
                    }
                    int flags = cursor.getInt(0);
                    long creationDate = cursor.getLong(1);
                    long secretUpdateDate = cursor.getLong(2);
                    byte[] secret1 = cursor.getBlob(3);
                    byte[] secret2 = cursor.getBlob(4);

                    final ContentValues values = new ContentValues();
                    values.put(Columns.ID, twincodeId);
                    values.put(Columns.PEER_TWINCODE_ID, peerTwincodeOutbound.getDatabaseId().getId());
                    values.put(Columns.CREATION_DATE, creationDate);
                    values.put(Columns.MODIFICATION_DATE, now);
                    values.put(Columns.SECRET_UPDATE_DATE, secretUpdateDate);
                    values.put(Columns.FLAGS, flags);
                    values.put(Columns.SECRET1, secret1);
                    values.put(Columns.SECRET2, secret2);
                    transaction.insertOrReplace(Tables.SECRET_KEYS, values);
                }
                transaction.delete(Tables.SECRET_KEYS, "id=? AND peerTwincodeId=?", previouskeys);
            }

            // Check if the FLAG_ENCRYPT flags are set on the twincodes:
            // - we must have the { <twincode>, <peer-twincode> } key association,
            // - we must know the peer secret { <peer-twincode>, null } association.
            transaction.updateTwincodeEncryptFlags(twincodeOutbound, peerTwincodeOutbound, now);
            transaction.commit();

            if (Logger.INFO) {
                if (previousPeerTwincodeOutbound != null) {
                    Logger.info(LOG_TAG, "Associated secrets for twincode ", twincodeOutbound.getId(),
                            " with ", peerTwincodeOutbound.getId() + " previous twincode was ", previousPeerTwincodeOutbound.getId());
                } else {
                    Logger.info(LOG_TAG, "Associated secrets for twincode ", twincodeOutbound.getId(),
                            " with ", peerTwincodeOutbound.getId());
                }
            }
        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Mark the two twincode relation as certified by the given trust process.
     *
     * @param twincodeOutboundImpl our twincode.
     * @param peerTwincodeOutboundImpl the peer twincode.
     * @param trustMethod the certification method that was used.
     * @return the status of the update.
     */
    @NonNull
    ErrorCode setCertified(@NonNull TwincodeOutboundImpl twincodeOutboundImpl,
                           @NonNull TwincodeOutboundImpl peerTwincodeOutboundImpl,
                           @NonNull TrustMethod trustMethod) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setCertified twincodeOutboundImpl=" + twincodeOutboundImpl + " peerTwincodeOutboundImpl=" + peerTwincodeOutboundImpl
                    + " trustMethod=" + trustMethod);
        }

        if (trustMethod != TrustMethod.QR_CODE && trustMethod != TrustMethod.LINK && trustMethod != TrustMethod.VIDEO) {
            return ErrorCode.BAD_REQUEST;
        }
        try (Transaction transaction = newTransaction()) {
            final ContentValues values = new ContentValues();
            final long now = System.currentTimeMillis();

            // For our twincode, only update the FLAG_CERTIFIED.
            final int twincodeFlags = twincodeOutboundImpl.getFlags() | TwincodeOutboundImpl.FLAG_CERTIFIED;
            values.put(Columns.FLAGS, twincodeFlags);
            values.put(Columns.MODIFICATION_DATE, now);
            transaction.updateWithId(Tables.TWINCODE_OUTBOUND, values, twincodeOutboundImpl.getDatabaseId().getId());

            // For the peer twincode, set the FLAG_CERTIFIED but also the FLAG_TRUSTED and record the trust method used.
            // If an existing trust method is defined, it is added (ex: INVITATION_CODE + VIDEO).
            final int peerTwincodeFlags = peerTwincodeOutboundImpl.getFlags() | TwincodeOutboundImpl.FLAG_CERTIFIED
                    | TwincodeOutboundImpl.FLAG_TRUSTED | TwincodeOutboundImpl.toFlags(trustMethod);
            values.put(Columns.FLAGS, peerTwincodeFlags);
            values.put(Columns.MODIFICATION_DATE, now);
            transaction.updateWithId(Tables.TWINCODE_OUTBOUND, values, peerTwincodeOutboundImpl.getDatabaseId().getId());

            transaction.commit();
            twincodeOutboundImpl.setFlags(twincodeFlags);
            peerTwincodeOutboundImpl.setFlags(peerTwincodeFlags);
            return ErrorCode.SUCCESS;

        } catch (Exception exception) {
            return mService.onDatabaseException(exception);
        }
    }

    private void internalUpdateTwincode(@NonNull Transaction transaction,
                                        @NonNull TwincodeOutboundImpl twincodeOutboundImpl,
                                        int flags,
                                        @NonNull List<AttributeNameValue> attributes,
                                        @Nullable List<AttributeNameValue> previousAttributes,
                                        long serverTimestamp) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalUpdateTwincode: twincodeOutboundImpl=" + twincodeOutboundImpl);
        }

        twincodeOutboundImpl.importAttributes(attributes, serverTimestamp, previousAttributes);

        final ImageId previousImageId = twincodeOutboundImpl.getAvatarId();
        if (transaction.storeAvatar(twincodeOutboundImpl, attributes) && previousAttributes != null && previousImageId != null) {
            final ExportedImageId avatarId = mDatabase.getPublicImageId(previousImageId);
            if (avatarId != null) {
                previousAttributes.add(new AttributeNameImageIdValue(Twincode.AVATAR_ID, avatarId));
            }
        }

        final ContentValues values = new ContentValues();
        values.put(Columns.ATTRIBUTES, twincodeOutboundImpl.serialize());
        values.put(Columns.NAME, twincodeOutboundImpl.getName());
        values.put(Columns.DESCRIPTION, twincodeOutboundImpl.getDescription());
        values.put(Columns.FLAGS, flags);
        if (twincodeOutboundImpl.getAvatarId() != null) {
            values.put(Columns.AVATAR_ID, twincodeOutboundImpl.getAvatarId().getId());
        } else {
            values.putNull(Columns.AVATAR_ID);
        }
        values.put(Columns.CAPABILITIES, twincodeOutboundImpl.getCapabilities());
        values.put(Columns.MODIFICATION_DATE, twincodeOutboundImpl.getModificationDate());
        values.put(Columns.REFRESH_TIMESTAMP, serverTimestamp);
        values.put(Columns.REFRESH_DATE, System.currentTimeMillis() + REFRESH_PERIOD);
        transaction.updateWithId(Tables.TWINCODE_OUTBOUND, values, twincodeOutboundImpl.getDatabaseId().getId());
    }

    /**
     * Bug introduced with database version 20: when the TwincodeInboundService updates the twincode inbound
     * in the database, it updates the twincode outbound table.  The server has the correct information.
     * We must cleanup the twincode outbound table because we will create a signature with one more attribute
     * that is not known by the peer.  The inserted attribute was `pair::twincodeOutboundId`.
     *
     * @throws DatabaseException when a database error occurred.
     */
    private void upgradeRepair20_23(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgradeRepair20_23");
        }

        final Map<Long, byte[]> updateMap = new HashMap<>();
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id, attributes"
                + " FROM twincodeOutbound WHERE attributes IS NOT NULL", null)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                byte[] content = cursor.getBlob(1);
                if (content != null) {
                    List<AttributeNameValue> attributes = BinaryCompactDecoder.deserialize(content);
                    if (attributes != null && AttributeNameValue.removeAttribute(attributes, "pair::twincodeOutboundId") != null) {
                        updateMap.put(id, BinaryCompactEncoder.serialize(attributes));
                    }
                }
            }
        }

        final ContentValues values = new ContentValues();
        for (Map.Entry<Long, byte[]> update : updateMap.entrySet()) {
            if (update.getValue() == null) {
                values.putNull(Columns.ATTRIBUTES);
            } else {
                values.put(Columns.ATTRIBUTES, update.getValue());
            }
            transaction.updateWithId(Tables.TWINCODE_OUTBOUND, values, update.getKey());
        }
    }

    /**
     * Migrate the twincodeOutboundTwincodeOutbound table to the twincodeOutbound new table format.
     *
     * @throws DatabaseException when a database error occurred.
     */
    private void upgrade20(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgrade20");
        }

        final long startTime = EventMonitor.start();
        int count = 0;

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT uuid, refreshPeriod, refreshDate, "
                + "refreshTimestamp, content FROM twincodeOutboundTwincodeOutbound", null)) {
            while (cursor.moveToNext()) {
                UUID twincodeId = cursor.getUUID(0);
                long refreshPeriod = cursor.getLong(1);
                long refreshDate = cursor.getLong(2);
                long refreshTimestamp = cursor.getLong(3);
                byte[] content = cursor.getBlob(4);

                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(content);
                DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
                long modificationDate = 0;
                List<AttributeNameValue> attributes;
                try {
                    dataInputStream.readLong();
                    dataInputStream.readLong();
                    modificationDate = dataInputStream.readLong();

                    attributes = new ArrayList<>();
                    int size = dataInputStream.readInt();
                    for (int i = 0; i < size; i++) {
                        AttributeNameValue attribute = BaseServiceImpl.deserialize(dataInputStream);
                        if (attribute != null) {
                            attributes.add(attribute);
                        }
                    }

                } catch (Exception ignored) {
                    attributes = null;
                }
                if (attributes != null && twincodeId != null) {
                    transaction.storeTwincodeOutbound(twincodeId, attributes, 0, modificationDate, refreshPeriod,
                            refreshDate, refreshTimestamp);
                    count++;
                }
            }
        }

        transaction.dropTable("twincodeOutboundTwincodeOutbound");
        if (BuildConfig.ENABLE_EVENT_MONITOR) {
            EventMonitor.event("Migrated " + count + " twincodes", startTime);
        }
    }
}
