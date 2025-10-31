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

import org.twinlife.twinlife.BaseServiceProvider;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Database;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.EventMonitor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The database service that gives access to the database to other services.  There is only one instance
 * which is shared by every service (RepositoryService, TwincodeXXXService, ...).
 * - it maintains a cache of database objects to make sure we have single instance of RepositoryObject,
 *   TwincodeInbound and TwincodeOutbound,
 * - it provides helper operations to load twincodes from the database (used by several services),
 * - it provides raw query (SELECT only) methods,
 * - updating the database must be made by the Transaction class by calling `newTransaction()`,
 * - it provides the `Allocator` used by the Transaction class to allocate database identifiers.
 */
public class DatabaseServiceImpl implements BaseServiceProvider {
    private static final String LOG_TAG = "DatabaseServiceImpl";
    private static final boolean DEBUG = false;

    /**
     * sequence table:
     * name TEXT NOT NULL: the sequence name (primary key)
     * id INTEGER: the next sequence id ready to be used.
     */
    private static final String SEQUENCE_TABLE_CREATE = "CREATE TABLE IF NOT EXISTS sequence"
            + " (name TEXT PRIMARY KEY NOT NULL, id INTEGER NOT NULL);";

    /**
     * Tables from V7 to V19:
     *  "CREATE TABLE IF NOT EXISTS conversationId (key TEXT PRIMARY KEY NOT NULL, id INTEGER);";
     */

    static final class Allocator {
        long next;
        long last;

        Allocator() {
            this.next = 0;
            this.last = 0;
        }
    }
    private final ConcurrentHashMap<DatabaseIdentifier, DatabaseObject> mCache;
    private final ConcurrentHashMap<UUID, DatabaseIdentifier> mIdCache;
    private final Allocator[] mAllocateIds;
    private final ArrayList<DatabaseServiceProvider> mServiceProviders;
    private final ReentrantLock mLock;
    private Database mDatabase;
    private TwincodeObjectFactory<TwincodeInbound> mTwincodeInboundFactory;
    private TwincodeObjectFactory<TwincodeOutbound> mTwincodeOutboundFactory;
    private RepositoryObjectLoader mRepositoryObjectLoader;
    private NotificationsCleaner mNotificationsCleaner;
    private ConversationsCleaner mConversationsCleaner;
    private ImagesCleaner mImagesCleaner;
    private TwincodesCleaner mTwincodesCleaner;

    public DatabaseServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "DatabaseServiceImpl");
        }

        mServiceProviders = new ArrayList<>();
        mCache = new ConcurrentHashMap<>();
        mIdCache = new ConcurrentHashMap<>();
        mLock = new ReentrantLock(true);
        DatabaseTable[] kinds = DatabaseTable.values();
        mAllocateIds = new Allocator[kinds.length];
        for (int i = 0; i < mAllocateIds.length; i++) {
            mAllocateIds[i] = new Allocator();
        }
    }

    void addServiceProvider(@NonNull DatabaseServiceProvider serviceProvider) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addServiceProvider serviceProvider=" + serviceProvider);
        }

        mServiceProviders.add(serviceProvider);
        if (serviceProvider instanceof RepositoryObjectLoader) {
            mRepositoryObjectLoader = (RepositoryObjectLoader) serviceProvider;
        } else if (serviceProvider instanceof NotificationsCleaner) {
            mNotificationsCleaner = (NotificationsCleaner) serviceProvider;
        } else if (serviceProvider instanceof ConversationsCleaner) {
            mConversationsCleaner = (ConversationsCleaner) serviceProvider;
        } else if (serviceProvider instanceof ImagesCleaner) {
            mImagesCleaner = (ImagesCleaner) serviceProvider;
        } else if (serviceProvider instanceof TwincodesCleaner) {
            mTwincodesCleaner = (TwincodesCleaner) serviceProvider;
        }
    }

    public String getDatabasePath() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDatabasePath");
        }

        return mDatabase.getPath();
    }

    public void setTwincodeOutboundFactory(@NonNull TwincodeObjectFactory<TwincodeOutbound> factory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setTwincodeOutboundFactory");
        }

        mTwincodeOutboundFactory = factory;
    }

    public void setTwincodeInboundFactory(@NonNull TwincodeObjectFactory<TwincodeInbound> factory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setTwincodeInboundFactory");
        }

        mTwincodeInboundFactory = factory;
    }

    @Override
    public void onCreate(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate database=" + database);
        }

        mDatabase = database;
        configureDatabase(database);
        try (Transaction transaction = new Transaction(this)) {
            transaction.createSchema(SEQUENCE_TABLE_CREATE);
            for (DatabaseServiceProvider serviceProvider : mServiceProviders) {
                serviceProvider.onCreate(transaction);
            }
            transaction.commit();

        }  catch (Exception exception) {
            Log.e(LOG_TAG, "Database exception", exception);
        }
    }

    @Override
    public void onUpgrade(@NonNull Database database, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade database=" + database + " oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        mDatabase = database;
        if (oldVersion < 20) {
            // Migrate old conversationId table to the new sequence table and change some names:
            // localId     -> conversation
            // operationId -> operation
            try (Transaction transaction = new Transaction(this)) {
                // Drop some old tables that seem to be present in some configuration.
                transaction.dropTable("directoryContext");
                transaction.dropTable("directoryNode");
                transaction.dropTable("twincodeFactoryTwincodeFactory");
                transaction.dropTable("twincodeSwitchTwincodeSwitch");

                transaction.createSchema(SEQUENCE_TABLE_CREATE);
                if (transaction.hasTable("conversationId")) {
                    final ContentValues values = new ContentValues();
                    try (DatabaseCursor cursor = rawQuery("SELECT key, id FROM conversationId", null)) {
                        while (cursor.moveToNext()) {
                            final String name = cursor.getString(0);
                            final long id = cursor.getLong(1);

                            if ("localId".equals(name)) {
                                values.put(Columns.NAME, Tables.CONVERSATION);
                            } else if ("operationId".equals(name)) {
                                values.put(Columns.NAME, Tables.OPERATION);
                            } else {
                                values.put(Columns.NAME, name);
                            }
                            values.put(Columns.ID, id);
                            transaction.insert(Tables.SEQUENCE, values);
                        }
                    }
                    transaction.dropTable("conversationId");
                }
                transaction.commit();

            } catch (Exception exception) {
                Log.e(LOG_TAG, "Database exception", exception);
            }
        }

        // Migrate each service in a specific order and commit transaction after each service migration.
        // If we are interrupted in the middle, the service must be prepared to re-do or do nothing at
        // a next application restart.
        for (DatabaseServiceProvider serviceProvider : mServiceProviders) {
            final long startTime = EventMonitor.start();
            try (Transaction transaction = new Transaction(this)) {
                serviceProvider.onUpgrade(transaction, oldVersion, newVersion);
                transaction.commit();
                if (BuildConfig.ENABLE_EVENT_MONITOR) {
                    EventMonitor.event("Database upgrade: " + serviceProvider.mService.getServiceName(), startTime);
                }

            } catch (DatabaseException dbException) {
                throw dbException;

            } catch (Exception exception) {
                throw new DatabaseException(exception);
            }
        }
        configureDatabase(database);
    }

    @Override
    public void onOpen(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOpen database=" + database);
        }

        mDatabase = database;
        for (DatabaseServiceProvider serviceProvider : mServiceProviders) {
            try {
                serviceProvider.onOpen();
            } catch (DatabaseException exception) {
                Log.e(LOG_TAG, "Database exception", exception);
            }
        }
        configureDatabase(database);
    }

    /**
     * Sync the database by running the WAL checkpoint and switch to DELETE journal mode.
     */
    public void syncDatabase() {
        if (DEBUG) {
            Log.d(LOG_TAG, "syncDatabase");
        }

        // Before a database backup/migration, we must checkpoint the WAL file
        // and also change the journal mode to DELETE to put the database in correct state.
        try {
            mDatabase.execSQL("PRAGMA wal_checkpoint(FULL)");
            mDatabase.execSQL("PRAGMA journal_mode = DELETE");

        } catch (Exception exception) {
            Log.e(LOG_TAG, "Database exception", exception);
        }
    }

    private void configureDatabase(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configureDatabase database=" + database);
        }

        try {
            // Switch to WAL which is recommended everywhere.
            // We must now be careful when doing the account migration.
            mDatabase.execSQL("PRAGMA journal_mode=WAL;");

            // But reduce to 500 pages (2Mb) the WAL autocheckpoint
            mDatabase.execSQL("PRAGMA wal_autocheckpoint=500;");

            // And activate the secure delete.
            mDatabase.execSQL("PRAGMA secure_delete=ON;");

        } catch (Exception exception) {
            Log.e(LOG_TAG, "Database exception", exception);
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery("PRAGMA journal_mode;", null)) {
            if (cursor.moveToNext()) {
                String mode = cursor.getString(0);
                Log.e(LOG_TAG, "Journal mode: " + mode);
            }

        } catch (Exception exception) {
            Log.e(LOG_TAG, "Database exception", exception);
        }
    }

    @NonNull
    public Transaction newTransaction() {

        return new Transaction(this);
    }

    /**
     * Lock the database.
     */
    void lock() {

        mLock.lock();
    }

    /**
     * Release the database lock.
     */
    void unlock() {

        mLock.unlock();
    }

    /**
     * Get from the cache the object with the given database identifier.
     *
     * @param identifier the database identifier.
     * @return the cached object instance or null.
     */
    @Nullable
    public DatabaseObject getCache(@NonNull DatabaseIdentifier identifier) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCache: identifier=" + identifier);
        }

        return mCache.get(identifier);
    }

    @Nullable
    public DatabaseObject getCache(@NonNull UUID identifier) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCache: identifier=" + identifier);
        }

        final DatabaseIdentifier id = mIdCache.get(identifier);
        if (id == null) {
            return null;
        }
        return mCache.get(id);
    }

    /**
     * Put in the cache the database object instance.
     *
     * @param object the database object to add in the cache.
     */
    public void putCache(@NonNull DatabaseObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "putCache: object=" + object);
        }

        final DatabaseIdentifier id = object.getDatabaseId();
        final UUID objectId = object.getId();
        mCache.put(id, object);
        mIdCache.put(objectId, id);
    }

    /**
     * Remove from the cache the database object with the given database identifier.
     *
     * @param identifier the database identifier.
     */
    public void evictCache(@NonNull DatabaseIdentifier identifier) {
        if (DEBUG) {
            Log.d(LOG_TAG, "evictCache: identifier=" + identifier);
        }

        final DatabaseObject object = mCache.remove(identifier);
        if (object != null) {
            mIdCache.remove(object.getId());
        }
    }

    /**
     * Remove from the cache the object with the given object UUID.
     *
     * @param objectId the object id.
     */
    public void evictCacheWithObjectId(@Nullable UUID objectId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "evictCacheWithObjectId: objectId=" + objectId);
        }

        if (objectId != null) {
            final DatabaseIdentifier identifier = mIdCache.remove(objectId);
            if (identifier != null) {
                mCache.remove(identifier);
            }
        }
    }

    public void evictCacheWithSchemaId(@NonNull UUID schemaId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "evictCacheWithSchemaId: schemaId=" + schemaId);
        }

        final Set<DatabaseIdentifier> keys = mCache.keySet();
        for (DatabaseIdentifier id : keys) {
            if (schemaId.equals(id.getSchemaId())) {
                mCache.remove(id);
            }
        }
    }

    public DatabaseCursor rawQuery(@NonNull String sql, String[] args) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "rawQuery: sql=" + sql);
        }

        return mDatabase.rawQuery(sql, args);
    }

    public DatabaseCursor execQuery(@NonNull QueryBuilder query) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execQuery: query=" + query);
        }

        final String sql = query.getQuery();
        return mDatabase.rawQuery(sql, query.getParams());
    }

    @Nullable
    public TwincodeInbound loadTwincodeInbound(@NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadTwincodeInbound: cursor=" + cursor  + " offset=" + offset);
        }

        if (cursor.isNull(offset)) {
            return null;
        }

        DatabaseIdentifier identifier = new DatabaseIdentifier(mTwincodeInboundFactory, cursor.getLong(offset));
        DatabaseObject object = getCache(identifier);
        TwincodeInbound result;
        if (object == null) {
            result = mTwincodeInboundFactory.createObject(identifier, cursor, offset + 1);
            if (result != null) {
                putCache(result);
            }
        } else {
            result = (TwincodeInbound) object;
            mTwincodeInboundFactory.loadObject(result, cursor, offset + 1);
        }
        return result;
    }

    @Nullable
    public TwincodeInbound loadTwincodeInbound(@NonNull UUID twincodeInboundId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadTwincodeInbound: twincodeInboundId=" + twincodeInboundId);
        }

        final DatabaseIdentifier id = mIdCache.get(twincodeInboundId);
        if (id != null) {
            final DatabaseObject obj = mCache.get(id);
            if (obj instanceof TwincodeInbound) {
                return (TwincodeInbound) obj;
            }
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT"
                + " ti.id, ti.twincodeId, ti.factoryId, ti.twincodeOutbound, ti.modificationDate, ti.capabilities, ti.attributes"
                + " FROM twincodeInbound AS ti"
                + " WHERE ti.twincodeId = ?", new String[]{ twincodeInboundId.toString() })) {
            if (cursor.moveToNext()) {
                return loadTwincodeInbound(cursor, 0);
            } else {
                return null;
            }
        }
    }

    @Nullable
    public TwincodeOutbound loadTwincodeOutbound(@NonNull UUID twincodeOutboundId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadTwincodeOutbound: twincodeOutboundId=" + twincodeOutboundId);
        }

        final DatabaseIdentifier id = mIdCache.get(twincodeOutboundId);
        if (id != null) {
            final DatabaseObject obj = mCache.get(id);
            if (obj instanceof TwincodeOutbound) {
                return (TwincodeOutbound) obj;
            }
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT"
                + " twout.id, twout.twincodeId, twout.modificationDate, twout.name,"
                + " twout.avatarId, twout.description, twout.capabilities, twout.attributes, twout.flags"
                + " FROM twincodeOutbound AS twout"
                + " WHERE twout.twincodeId = ?", new String[]{ twincodeOutboundId.toString() })) {
            if (cursor.moveToNext()) {
                return loadTwincodeOutbound(cursor, 0);
            } else {
                return null;
            }
        }
    }

    @Nullable
    public TwincodeOutbound loadTwincodeOutbound(long twincodeOutboundId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadTwincodeOutbound: twincodeOutboundId=" + twincodeOutboundId);
        }

        final DatabaseIdentifier identifier = new DatabaseIdentifier(mTwincodeOutboundFactory, twincodeOutboundId);
        final DatabaseObject obj = mCache.get(identifier);
        if (obj instanceof TwincodeOutbound) {
            return (TwincodeOutbound) obj;
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT"
                + " twout.id, twout.twincodeId, twout.modificationDate, twout.name,"
                + " twout.avatarId, twout.description, twout.capabilities, twout.attributes, twout.flags"
                + " FROM twincodeOutbound AS twout"
                + " WHERE twout.id = ?", new String[]{ Long.toString(twincodeOutboundId) })) {
            if (cursor.moveToNext()) {
                return loadTwincodeOutbound(cursor, 0);
            } else {
                return null;
            }
        }
    }

    @Nullable
    public TwincodeOutbound loadTwincodeOutbound(@NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadTwincodeOutbound: cursor=" + cursor + " offset=" + offset);
        }

        if (cursor.isNull(offset)) {
            return null;
        }

        DatabaseIdentifier identifier = new DatabaseIdentifier(mTwincodeOutboundFactory, cursor.getLong(offset));
        DatabaseObject object = getCache(identifier);
        TwincodeOutbound result;
        if (object == null) {
            result = mTwincodeOutboundFactory.createObject(identifier, cursor, offset + 1);
            if (result != null) {
                putCache(result);
            }
        } else {
            result = (TwincodeOutbound) object;
            mTwincodeOutboundFactory.loadObject(result, cursor, offset + 1);
        }
        return result;
    }

    /**
     * Load the repository object with the given database id and using the given schema Id.
     * The schemaId is used to find the good repository object factory if the repository object
     * is not found in the cache and must be loaded.
     *
     * @param dbId the repository object id
     * @param schemaId the repository object schema
     * @return the repository object or null if it was not found.
     */
    @Nullable
    public RepositoryObject loadRepositoryObject(long dbId, @NonNull UUID schemaId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadRepositoryObject: dbId=" + dbId + " schemaId=" + schemaId);
        }

        return mRepositoryObjectLoader.loadRepositoryObject(dbId, schemaId);
    }

    @Nullable
    public Long longQuery(@NonNull String sql, @Nullable Object[] args) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "longQuery: sql=" + sql);
        }

        return mDatabase.longQuery(sql, args);
    }

    public void loadIds(@NonNull String sql, @Nullable String[] args, @NonNull List<Long> ids) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadIds: sql=" + sql);
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery(sql, args)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                ids.add(id);
            }
        }
    }

    @Nullable
    public ExportedImageId getPublicImageId(@NonNull ImageId imageId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPublicImageId imageId=" + imageId);
        }

        final String[] params = { String.valueOf(imageId.getId()) };
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT uuid FROM image WHERE id=?", params)) {
            if (cursor.moveToNext()) {
                UUID uuid = cursor.getUUID(0);
                if (uuid != null) {
                    return new ExportedImageId(imageId, uuid);
                }
            }
            return null;
        }
    }

    @NonNull
    public String checkConsistency() {

        return DatabaseCheck.checkConsistency(mDatabase);
    }

    //
    // Package scoped methods
    //

    Database getDatabase() {

        return mDatabase;
    }

    TwincodeObjectFactory<TwincodeInbound> getTwincodeInboundFactory() {

        return mTwincodeInboundFactory;
    }

    TwincodeObjectFactory<TwincodeOutbound> getTwincodeOutboundFactory() {

        return mTwincodeOutboundFactory;
    }

    @Nullable
    NotificationsCleaner getNotificationCleaner() {

        return mNotificationsCleaner;
    }

    @Nullable
    ConversationsCleaner getConversationsCleaner() {

        return mConversationsCleaner;
    }

    @Nullable
    ImagesCleaner getImagesCleaner() {

        return mImagesCleaner;
    }

    @Nullable
    TwincodesCleaner getTwincodesCleaner() {

        return mTwincodesCleaner;
    }

    Allocator getAllocator(@NonNull DatabaseTable kind) {

        return mAllocateIds[kind.ordinal()];
    }
}
