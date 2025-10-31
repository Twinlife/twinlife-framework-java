/*
 *  Copyright (c) 2014-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.inbound;

import android.content.ContentValues;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.database.Columns;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.DatabaseServiceProvider;
import org.twinlife.twinlife.database.Tables;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.database.TwincodeObjectFactory;

import java.util.List;
import java.util.UUID;

class TwincodeInboundServiceProvider extends DatabaseServiceProvider implements TwincodeObjectFactory<TwincodeInbound> {
    private static final String LOG_TAG = "TwincodeInboundServi...";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("33c38ac6-e89d-4639-b116-90fc47a5f9f4");

    /**
     * twincodeInbound table:
     * id INTEGER: local database identifier (primary key)
     * twincodeId TEXT UNIQUE NOT NULL: twincode inbound id
     * factoryId TEXT: the factory that created this twincode.
     * twincodeOutbound INTEGER: the associated twincode outbound.
     * capabilities TEXT: capabilities attribute
     * modificationDate INTEGER: the last modification date.
     * attributes BLOB: the other attributes
     * Note:
     * - id, twincodeId, factoryId, twincodeOutbound are readonly (creationDate is on the twincodeOutbound).
     */
    private static final String TWINCODE_INBOUND_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS twincodeInbound (id INTEGER PRIMARY KEY,"
                    + " twincodeId TEXT UNIQUE NOT NULL, factoryId TEXT, twincodeOutbound INTEGER,"
                    + " capabilities TEXT,"
                    + " modificationDate INTEGER NOT NULL, attributes BLOB)";

    /**
     * Table from V7 to V19:
     * "CREATE TABLE IF NOT EXISTS twincodeInboundTwincodeInbound (uuid TEXT PRIMARY KEY, content BLOB);";
     */

    TwincodeInboundServiceProvider(@NonNull TwincodeInboundServiceImpl service,
                                   @NonNull DatabaseServiceImpl database) {
        super(service, database, TWINCODE_INBOUND_CREATE_TABLE, DatabaseTable.TABLE_TWINCODE_INBOUND);
        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeInboundServiceProvider: service=" + service);
        }

        database.setTwincodeInboundFactory(this);
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
         *   New database model with twincodeInbound table and change of primary key
         *   The old table to new table migration is made by the RepositoryServiceProvider.
         * </pre>
         */

        super.onUpgrade(transaction, oldVersion, newVersion);
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
    public TwincodeInbound createObject(@NonNull DatabaseIdentifier identifier,
                                        @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObject identifier=" + identifier + " offset=" + offset);
        }

        // ti.twincodeId, ti.factory, ti.twincodeOutbound, ti.modificationDate, ti.capabilities, ti.attributes
        final UUID twincodeId = cursor.getUUID(offset);
        final UUID factoryId = cursor.getUUID(offset + 1);
        final long twincodeOutboundId = cursor.getLong(offset + 2);
        final TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(twincodeOutboundId);
        final long modificationDate = cursor.getLong(offset + 3);
        final String capabilities = cursor.getString(offset + 4);
        final byte[] content = cursor.getBlob(offset + 5);
        return new TwincodeInboundImpl(identifier, twincodeId, factoryId, twincodeOutbound, capabilities, content, modificationDate);
    }

    @Override
    public boolean loadObject(@NonNull TwincodeInbound object, @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadObject object=" + object + " offset=" + offset);
        }

        final TwincodeInboundImpl twincodeInbound = (TwincodeInboundImpl) object;
        // final UUID twincodeId = cursor.getUUID(offset);
        // final UID factoryId = cursor.getUUID(offset + 1);
        // final long twincodeOutboundId = cursor.getLong(offset + 2);
        final long modificationDate = cursor.getLong(offset + 3);
        if (twincodeInbound.getModificationDate() == modificationDate) {
            return false;
        }
        final String capabilities = cursor.getString(offset + 4);
        final byte[] content = cursor.getBlob(offset + 5);
        twincodeInbound.update(modificationDate, capabilities, content);
        return true;
    }

    @Override
    public TwincodeInbound storeObject(@NonNull Transaction transaction, @NonNull DatabaseIdentifier identifier,
                                       @NonNull UUID twincodeId,
                                       @Nullable List<BaseService.AttributeNameValue> attributes,
                                       int flags,
                                       long modificationDate, long refreshPeriod,
                                       long refreshDate, long refreshTimestamp,
                                       @Nullable Initializer<TwincodeInbound> initializer) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "storeObject identifier=" + identifier + " twincodeId=" + twincodeId);
        }

        TwincodeInboundImpl twincodeInbound = new TwincodeInboundImpl(identifier, twincodeId, modificationDate, attributes);
        if (initializer != null) {
            initializer.initialize(twincodeInbound);
        }

        ContentValues values = new ContentValues();
        values.put(Columns.ID, identifier.getId());
        values.put(Columns.TWINCODE_ID, twincodeId.toString());
        values.put(Columns.CAPABILITIES, twincodeInbound.getCapabilities());
        values.put(Columns.MODIFICATION_DATE, modificationDate);
        if (twincodeInbound.getTwincodeFactoryId() != null) {
            values.put(Columns.FACTORY_ID, twincodeInbound.getTwincodeFactoryId().toString());
        }
        byte[] data = twincodeInbound.serialize();
        if (data != null) {
            values.put(Columns.ATTRIBUTES, data);
        }
        TwincodeOutbound twincodeOutbound = twincodeInbound.getTwincodeOutbound();
        if (twincodeOutbound != null) {
            values.put(Columns.TWINCODE_OUTBOUND, twincodeOutbound.getDatabaseId().getId());
        }
        transaction.insertOrThrow(Tables.TWINCODE_INBOUND, null, values);
        mDatabase.putCache(twincodeInbound);
        return twincodeInbound;
    }

    @Override
    public boolean isLocal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isLocal");
        }

        return false;
    }

    //
    // Package scoped methods
    //
    @Nullable
    TwincodeInbound loadTwincode(@NonNull UUID twincodeInboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadTwincode: twincodeInboundId=" + twincodeInboundId);
        }

        try {
            return mDatabase.loadTwincodeInbound(twincodeInboundId);

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    /**
     * Update an existing twincode in the database.  The twincode instance is updated with the
     * given attributes and saved in the database.
     *
     * @param twincodeInboundImpl the twincode object.
     * @param attributes the twincode attributes to update.
     * @param modificationTimestamp the new twincode modification date.
     */
    void updateTwincode(@NonNull TwincodeInboundImpl twincodeInboundImpl,
                        @NonNull List<BaseService.AttributeNameValue> attributes,
                        long modificationTimestamp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateTwincode: twincodeInboundImpl=" + twincodeInboundImpl);
        }

        twincodeInboundImpl.importAttributes(attributes, modificationTimestamp);
        try (Transaction transaction = newTransaction()) {
            final ContentValues values = new ContentValues();
            values.put(Columns.CAPABILITIES, twincodeInboundImpl.getCapabilities());
            values.put(Columns.ATTRIBUTES, twincodeInboundImpl.serialize());
            values.put(Columns.MODIFICATION_DATE, twincodeInboundImpl.getModificationDate());
            transaction.updateWithId(Tables.TWINCODE_INBOUND, values, twincodeInboundImpl.getDatabaseId().getId());
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Store a possibly new twincode in the database.  The twincode is associated with the refresh timestamp and period.
     * The refresh update is scheduled to be the current date + refresh period.
     *
     * @param twincodeId the twincode to insert/update.
     * @param twincodeOutbound the twincode outbound to associated with the twincode inbound.
     * @param attributes the list of attributes to update.
     * @param modificationDate the new modification date.
     */
    @Nullable
    TwincodeInbound importTwincode(@NonNull UUID twincodeId, @NonNull TwincodeOutbound twincodeOutbound,
                                   @NonNull List<BaseService.AttributeNameValue> attributes, long modificationDate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "importTwincode: twincodeId=" + twincodeId + " twincodeOutbound=" + twincodeOutbound);
        }

        try (Transaction transaction = newTransaction()) {
            try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT"
                    + " ti.id"
                    + " FROM twincodeInbound AS ti"
                    + " WHERE ti.twincodeId = ?", new String[]{ twincodeId.toString() })) {
                if (cursor.moveToNext()) {
                    final DatabaseObject object = mDatabase.getCache(new DatabaseIdentifier(this, cursor.getLong(0)));
                    if (object instanceof TwincodeInbound) {
                        return (TwincodeInbound) object;
                    }
                    return mDatabase.loadTwincodeInbound(twincodeId);
                } else {
                    final TwincodeInbound result = transaction.storeTwincodeInbound(twincodeId, twincodeOutbound, null,
                            attributes, 0, modificationDate);
                    transaction.commit();
                    return result;
                }
            }
        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }
}
