/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import android.content.ContentValues;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.Database;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.crypto.CryptoServiceImpl;
import org.twinlife.twinlife.twincode.inbound.TwincodeInboundImpl;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundImpl;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Database transaction with operations to update the database.
 * Pattern of use:
 * <pre>
 * try (Transaction transaction = newTransaction()) {
 *     transaction.insert(...);
 *     transaction.update(...);
 *     transaction.commit();
 * } catch (Exception exception) {
 *     ...
 * }
 * </pre>
 * - The transaction is rollback when an exception is raised,
 * - SQL queries to insert/update/delete are only provided on the Transaction class to make sure we are within a transaction,
 */
public class Transaction implements Closeable {
    private static final String LOG_TAG = "Transaction";
    private static final boolean DEBUG = false;

    private final DatabaseServiceImpl mDatabaseService;
    private final Database mDatabase;
    private boolean mActive;
    @Nullable
    private List<DatabaseServiceImpl.Allocator> mUsedAllocators;

    Transaction(@NonNull DatabaseServiceImpl databaseService) {

        // When the transaction is started, we must lock the database to make sure
        // we are alone and there are no issues with the SQLDatabase internal lock
        // that will be acquired later (but we don't know when).  The lock is automatically
        // release by close().
        databaseService.lock();
        mDatabaseService = databaseService;
        mDatabase = mDatabaseService.getDatabase();
        mActive = false;
        mUsedAllocators = null;
    }

    public long insert(@NonNull String tablename, @NonNull ContentValues values) throws DatabaseException {

        start();
        return mDatabase.insert(tablename, null, values);
    }

    public long insertOrThrow(@NonNull String tablename, @Nullable String sql,
                              @NonNull ContentValues values) throws DatabaseException {

        start();
        return mDatabase.insertOrThrow(tablename, sql, values);
    }

    public long insertOrIgnore(@NonNull String tablename, @Nullable String sql,
                               @NonNull ContentValues values) throws DatabaseException {

        start();
        return mDatabase.insertOrIgnore(tablename, sql, values);
    }

    public long insertOrReplace(@NonNull String tablename,
                                @NonNull ContentValues values) throws DatabaseException {

        start();
        return mDatabase.insertOrReplace(tablename, values);
    }

    public int update(@NonNull String tablename, @NonNull ContentValues values,
                      @NonNull String sql, String[] args) throws DatabaseException {

        start();
        return mDatabase.update(tablename, values, sql, args);
    }

    public int updateWithId(@NonNull String tablename, @NonNull ContentValues values, long id) throws DatabaseException {

        start();
        return mDatabase.update(tablename, values, "id=?", new String[] { Long.toString(id) });
    }

    public void execSQLWithArgs(@NonNull String sql, String[] args) throws DatabaseException {

        start();
        mDatabase.execSQLWithArgs(sql, args);
    }

    public int delete(@NonNull String tablename, @NonNull String sql, Object[] args) throws DatabaseException {

        start();
        return mDatabase.delete(tablename, sql, args);
    }

    public int deleteWithId(@NonNull String tablename, long id) throws DatabaseException {

        start();
        return mDatabase.delete(tablename, "id=?", new Object[] { id });
    }

    /**
     * Delete a list of records from the database table.
     *
     * @param tablename the database table to delete.
     * @param list the list of row ids to remove.
     * @return
     * @throws DatabaseException
     */
    public int deleteWithList(@NonNull String tablename, @NonNull List<Long> list) throws DatabaseException {

        start();
        for (Long id : list) {
            deleteWithId(tablename, id);
        }
        return 0;
    }

    /**
     * Create a database table or index.
     *
     * @param sql the SQL to create the table or index.
     * @throws DatabaseException raised if some database error occurred.
     */
    public void createSchema(@NonNull String sql) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSchema: sql=" + sql);
        }

        start();
        mDatabase.execSQL(sql);
    }

    public boolean hasTable(@NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasTable: name=" + name);
        }

        return mDatabase.hasTable(name);
    }

    public void dropTable(@NonNull String name) throws DatabaseException {

        start();
        mDatabase.execSQL("DROP TABLE IF EXISTS " + name);
    }

    public void commit() throws DatabaseException {

        if (mActive) {
            mDatabase.setTransactionSuccessful();
            mDatabase.endTransaction();
            mActive = false;
            mUsedAllocators = null;
        }
    }

    public void rollback() throws DatabaseException {

        if (mActive) {
            if (mUsedAllocators != null) {
                synchronized (mDatabaseService) {
                    // Force a reload of the allocators because the transaction was aborted
                    // and we have allocated some ids, which means the sequence table was
                    // not updated either.
                    for (DatabaseServiceImpl.Allocator allocator : mUsedAllocators) {
                        allocator.next = 0;
                        allocator.last = 0;
                    }
                }
                mUsedAllocators = null;
            }
            mDatabase.endTransaction();
            mActive = false;
        }
    }

    @Override
    public void close() throws IOException {

        // Rollback the transaction if it is active and release the database
        // lock acquired when the Transaction instance was created.
        rollback();
        mDatabaseService.unlock();
    }

    /**
     * Allocate a unique id for the given database table.
     *
     * @param kind the type of table
     * @return the new unique identifier.
     */
    public long allocateId(@NonNull DatabaseTable kind) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "allocateId: kind=" + kind);
        }

        final DatabaseServiceImpl.Allocator allocator = mDatabaseService.getAllocator(kind);
        //  Keep a list of sequence allocators used in case we have to rollback.
        if (mUsedAllocators == null) {
            mUsedAllocators = new ArrayList<>();
        }
        if (!mUsedAllocators.contains(allocator)) {
            mUsedAllocators.add(allocator);
        }
        start();
        synchronized (mDatabaseService) {
            if (allocator.next < allocator.last) {
                return allocator.next++;
            }

            final String name = Tables.getTable(kind);
            final long increment = 10;
            final String maxQuery = kind != DatabaseTable.SEQUENCE ? "SELECT MAX(id) FROM " + name : null;
            while (true) {
                if (allocator.next == 0) {
                    // Get the max ID used by looking at the target table: it happened that there was
                    // some inconsistency between some target table and the sequence table.  This should
                    // not occur but we check and recover from that the first time we allocate an ID.
                    // There is no `sequence` table and a query on the descriptor table would need to take
                    // into account the conversation and would be expensive, use 0 for that.
                    // Add 1 to avoid re-using the MAX(id).
                    final Long maxId = maxQuery != null ? mDatabase.longQuery(maxQuery, null) : null;
                    try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id FROM sequence WHERE name=?", new String[]{name})) {
                        if (cursor.moveToFirst()) {
                            allocator.last = cursor.getLong(0);
                            // Use the max between the MAX(id) and the value of the sequence allocator.
                            if (maxId == null || allocator.last > maxId + 1) {
                                allocator.next = allocator.last;
                            } else {
                                allocator.next = maxId + 1;
                            }
                        } else {
                            // Start a new sequence at maxId excluding 0 to prevent using default long values.
                            final ContentValues values = new ContentValues();
                            final long sequenceId = (maxId == null ? increment : increment + maxId + 1);
                            values.put(Columns.NAME, name);
                            values.put(Columns.ID, sequenceId);
                            try {
                                mDatabase.insertOrThrow(Tables.SEQUENCE, null, values);
                                allocator.next = (maxId == null ? 1 : maxId + 1);
                                allocator.last = sequenceId;
                                return allocator.next++;

                            } catch (DatabaseException exception) {
                                Log.e(LOG_TAG, "Insert failed", exception);
                            }
                        }
                    }
                }

                if (allocator.last > 0) {
                    final long sequenceId = allocator.last;
                    final long lastId = allocator.next + increment;
                    ContentValues values = new ContentValues();
                    values.put(Columns.ID, lastId);
                    int update = mDatabase.update(Tables.SEQUENCE, values, "name=? AND id=?", new String[]{
                            name,
                            Long.toString(sequenceId)
                    });
                    if (update == 1) {
                        allocator.last = lastId;
                        return allocator.next++;
                    }
                }
                allocator.next = 0;
                allocator.last = 0;
            }
        }
    }

    /**
     * Delete the object from its associated database table and remove it from the cache.
     *
     * @param object the object to delete.
     */
    public void deleteObject(@NonNull DatabaseObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObject: object=" + object);
        }

        try {
            final DatabaseIdentifier id = object.getDatabaseId();
            String tablename = Tables.getTable(id.getKind());
            if (tablename != null) {
                synchronized (mDatabaseService) {
                    mDatabase.delete(tablename, "id=?", new Object[]{id.getId()});
                    mDatabaseService.evictCache(id);
                }
            }
        } catch (DatabaseException exception) {
            // mService.onDatabaseException(exception);
        }
    }

    /**
     * Delete from the database table the object with the given uuid.
     * The object is also removed from the cache if it was present.
     *
     * @param uuid the object uuid
     * @param kind the database table.
     */
    public void deleteObjectTable(@NonNull UUID uuid, @NonNull DatabaseTable kind) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObjectTable: uuid=" + kind);
        }

        try {
            String tablename = Tables.getTable(kind);
            if (tablename != null) {
                synchronized (mDatabaseService) {
                    mDatabase.delete(tablename, "uuid=?", new Object[]{uuid.toString()});
                    DatabaseObject object = mDatabaseService.getCache(uuid);
                    if (object != null) {
                        mDatabaseService.evictCache(object.getDatabaseId());
                    }
                }
            }
        } catch (DatabaseException exception) {
            // mService.onDatabaseException(exception);
        }
    }

    public void deleteConversations(@Nullable Long subjectId, @Nullable Long twincodeId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteConversations: subjectId=" + subjectId + " twincodeId=" + twincodeId);
        }

        final ConversationsCleaner conversationsCleaner = mDatabaseService.getConversationsCleaner();
        if (conversationsCleaner != null) {
            conversationsCleaner.deleteConversations(this, subjectId, twincodeId);
        }
    }

    public void deleteNotifications(@NonNull Long subjectId, @Nullable Long twincodeId,
                                    @Nullable Long descriptorId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteNotifications: subjectId=" + subjectId + " twincodeId=" + twincodeId + " descriptorId=" + descriptorId);
        }

        final NotificationsCleaner notificationsCleaner = mDatabaseService.getNotificationCleaner();
        if (notificationsCleaner != null) {
            notificationsCleaner.deleteNotifications(this, subjectId, twincodeId, descriptorId);
        }
    }

    public void deleteImage(@NonNull ImageId imageId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteImage: imageId=" + imageId);
        }

        final ImagesCleaner imagesCleaner = mDatabaseService.getImagesCleaner();
        if (imagesCleaner != null) {
            imagesCleaner.deleteImage(this, imageId);
        }
    }

    public void deleteTwincode(@NonNull TwincodeOutbound twincodeOutbound) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteTwincode: twincodeOutbound=" + twincodeOutbound);
        }

        final TwincodesCleaner twincodesCleaner = mDatabaseService.getTwincodesCleaner();
        if (twincodesCleaner != null) {
            twincodesCleaner.deleteTwincode(this, twincodeOutbound);
        }
    }

    @NonNull
    public TwincodeInbound storeTwincodeInbound(@NonNull UUID twincodeId,
                                                @NonNull TwincodeOutbound twincodeOutbound,
                                                @Nullable UUID twincodeFactoryId,
                                                @Nullable List<AttributeNameValue> attributes,
                                                int flags,
                                                long modificationDate) throws DatabaseException {

        final TwincodeObjectFactory<TwincodeInbound> factory = mDatabaseService.getTwincodeInboundFactory();
        long id = allocateId(DatabaseTable.TABLE_TWINCODE_INBOUND);
        DatabaseIdentifier identifier = new DatabaseIdentifier(factory, id);
        return factory.storeObject(this, identifier, twincodeId, attributes, flags, modificationDate,
                0, 0, 0, (TwincodeInbound twincodeInbound) -> {
                    TwincodeInboundImpl twincodeInboundImpl = (TwincodeInboundImpl) twincodeInbound;
                    twincodeInboundImpl.setTwincodeOutbound(twincodeOutbound);
                    twincodeInboundImpl.setTwincodeFactoryId(twincodeFactoryId);
                });
    }

    public boolean storeAvatar(@NonNull TwincodeOutboundImpl twincodeOutboundImpl,
                               @Nullable List<AttributeNameValue> attributes) throws DatabaseException {

        if (attributes == null) {
            return false;
        }

        final AttributeNameValue image = AttributeNameValue.getAttribute(attributes, Twincode.AVATAR_ID);
        if (image == null) {
            return false;
        }

        if (image.value instanceof ExportedImageId) {
            twincodeOutboundImpl.setAvatarId((ImageId) image.value);
        } else if (image.value instanceof UUID) {
            UUID avatarId = (UUID) image.value;
            Long id = mDatabase.longQuery("SELECT id FROM image WHERE uuid=?", new String[]{avatarId.toString()});
            if (id == null) {
                id = allocateId(DatabaseTable.TABLE_IMAGE);
                ContentValues values = new ContentValues();
                values.put(Columns.ID, id);
                values.put(Columns.UUID, avatarId.toString());
                values.put(Columns.CREATION_DATE, System.currentTimeMillis());
                values.put(Columns.FLAGS, 5);
                insertOrThrow(Tables.IMAGE, null, values);
            } else {
                ImageId currentAvatarId = twincodeOutboundImpl.getAvatarId();
                if (currentAvatarId != null && currentAvatarId.getId() == id) {
                    return false;
                }
            }
            twincodeOutboundImpl.setAvatarId(new ImageId(id));
        }
        return true;
    }

    @NonNull
    public TwincodeOutbound storeTwincodeOutbound(@NonNull UUID twincodeId,
                                                  @Nullable List<AttributeNameValue> attributes,
                                                  int flags,
                                                  long modificationDate, long refreshPeriod,
                                                  long refreshDate, long refreshTimestamp) throws DatabaseException {

        final TwincodeObjectFactory<TwincodeOutbound> factory = mDatabaseService.getTwincodeOutboundFactory();
        final long id = allocateId(DatabaseTable.TABLE_TWINCODE_OUTBOUND);
        final DatabaseIdentifier identifier = new DatabaseIdentifier(factory, id);
        return factory.storeObject(this, identifier, twincodeId, attributes, flags, modificationDate,
                refreshPeriod, refreshDate, refreshTimestamp, (TwincodeOutbound twincodeOutbound) -> {
                    final TwincodeOutboundImpl twincodeOutboundImpl = (TwincodeOutboundImpl) twincodeOutbound;
                    storeAvatar(twincodeOutboundImpl, attributes);
                });
    }

    public TwincodeOutbound loadOrStoreTwincodeOutboundId(@NonNull UUID twincodeId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadOrStoreTwincodeOutboundId: twincodeId=" + twincodeId);
        }

        final TwincodeOutbound result = mDatabaseService.loadTwincodeOutbound(twincodeId);
        if (result != null) {
            return result;
        }

        final TwincodeObjectFactory<TwincodeOutbound> factory = mDatabaseService.getTwincodeOutboundFactory();
        final long id = allocateId(DatabaseTable.TABLE_TWINCODE_OUTBOUND);
        final DatabaseIdentifier identifier = new DatabaseIdentifier(factory, id);
        return factory.storeObject(this, identifier, twincodeId, new ArrayList<>(), 0, 0,
                TwincodeOutboundService.REFRESH_PERIOD, 0, 0, (TwincodeOutbound twincodeOutbound) -> {
                    TwincodeOutboundImpl twincodeOutboundImpl = (TwincodeOutboundImpl) twincodeOutbound;

                    // We store this twincode without information: we need to fetch them later from the server.
                    twincodeOutboundImpl.needFetch();
                });
    }

    /**
     * Store a peer public signing and encryption key with an optional secret key.
     *
     * @param twincodeOutbound the peer twincode.
     * @param pubSigningKey the public signing key.
     * @param pubEncryptionKey the public encryption key.
     * @param keyIndex the key index associated with the optional secret.
     * @param secretKey the optional secret.
     * @throws DatabaseException when some database error occurred.
     */
    public void storePublicKey(@NonNull TwincodeOutbound twincodeOutbound, @NonNull byte[] pubSigningKey,
                               @Nullable byte[] pubEncryptionKey, int keyIndex, @Nullable byte[] secretKey) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "storePublicKey: twincodeOutbound=" + twincodeOutbound + " keyIndex=" + keyIndex);
        }

        final long id = twincodeOutbound.getDatabaseId().getId();
        final long now = System.currentTimeMillis();
        final ContentValues values = new ContentValues();
        values.put(Columns.ID, id);
        values.put(Columns.SIGNING_KEY, pubSigningKey);
        if (pubEncryptionKey != null) {
            values.put(Columns.ENCRYPTION_KEY, pubEncryptionKey);
        }
        values.put(Columns.FLAGS, CryptoServiceImpl.getKeyType(pubSigningKey));
        values.put(Columns.CREATION_DATE, now);
        values.put(Columns.MODIFICATION_DATE, now);
        values.put(Columns.NONCE_SEQUENCE, 0);
        insertOrReplace(Tables.TWINCODE_KEYS, values);

        // Either insert or update the secret key.
        // Flags are always 0 and peerTwincodeId is always NULL because this is the peer secret.
        // Note: we cannot use the insert() to detect if the row existed because the secretKeys table
        // is using a primary key on two columns and SQLite is using a rowid as primary column, hence
        // it will always successfully insert any row.
        if (secretKey != null) {
            values.clear();
            String[] params = new String[] { Long.toString(id) };
            values.put(Columns.SECRET_UPDATE_DATE, now);
            if (keyIndex == 1) {
                values.put(Columns.SECRET1, secretKey);
            } else if (keyIndex == 2) {
                values.put(Columns.SECRET2, secretKey);
            }

            Long hasSecrets = mDatabase.longQuery("SELECT flags FROM secretKeys WHERE id=? AND peerTwincodeId IS NULL", params);
            if (hasSecrets == null) {
                values.put(Columns.ID, id);
                values.putNull(Columns.PEER_TWINCODE_ID);
                values.put(Columns.CREATION_DATE, now);
                values.put(Columns.MODIFICATION_DATE, now);
                insert(Tables.SECRET_KEYS, values);
            } else {
                update(Tables.SECRET_KEYS, values, "id=? AND peerTwincodeId IS NULL", params);
            }
        }
    }

    public void updateTwincodeEncryptFlags(@NonNull TwincodeOutboundImpl twincodeOutbound,
                                           @NonNull TwincodeOutboundImpl peerTwincodeOutbound,
                                           long now) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateTwincodeEncryptFlags twincodeOutbound=" + twincodeOutbound
                    + " peerTwincodeOutbound=" + peerTwincodeOutbound + " now=" + now);
        }

        final long twincodeId = twincodeOutbound.getDatabaseId().getId();
        final long peerId = peerTwincodeOutbound.getDatabaseId().getId();

        // Check if the FLAG_ENCRYPT flags are set on the twincodes:
        // - we must have the { <twincode>, <peer-twincode> } key association,
        // - we must know the peer secret { <peer-twincode>, null } association.
        final Long secretFlags = mDatabase.longQuery("SELECT s.flags"
                                + " FROM secretKeys AS s, secretKeys AS peer"
                                + " WHERE s.id=? AND s.peerTwincodeId=? AND peer.id=s.peerTwincodeId AND peer.secret1 IS NOT NULL",
                        new String[]{Long.toString(twincodeId), Long.toString(peerId)});
        if (secretFlags != null && (secretFlags & (CryptoService.USE_SECRET1 | CryptoService.USE_SECRET2)) != 0) {
            final ContentValues values = new ContentValues();

            // Now, make sure our twincode has FLAG_ENCRYPT set.
            int twincodeFlags = twincodeOutbound.getFlags();
            if ((twincodeFlags & TwincodeOutboundImpl.FLAG_ENCRYPT) == 0) {
                twincodeFlags |= TwincodeOutboundImpl.FLAG_ENCRYPT;
                values.put(Columns.FLAGS, twincodeFlags);
                values.put(Columns.MODIFICATION_DATE, now);
                updateWithId(Tables.TWINCODE_OUTBOUND, values, twincodeId);
                twincodeOutbound.setFlags(twincodeFlags);
            }

            // Likewise for the peer twincode.
            int peerTwincodeFlags = peerTwincodeOutbound.getFlags();
            if ((peerTwincodeFlags & TwincodeOutboundImpl.FLAG_ENCRYPT) == 0) {
                peerTwincodeFlags |= TwincodeOutboundImpl.FLAG_ENCRYPT;
                values.put(Columns.FLAGS, peerTwincodeFlags);
                values.put(Columns.MODIFICATION_DATE, now);
                updateWithId(Tables.TWINCODE_OUTBOUND, values, peerId);
                peerTwincodeOutbound.setFlags(peerTwincodeFlags);
            }
        }
    }

    private void start() {

        if (!mActive) {
            mActive = true;
            mDatabase.beginTransaction();
        }
    }
}