/*
 *  Copyright (c) 2014-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.repository;

import android.content.ContentValues;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;
import android.util.Pair;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeAssertPoint;
import org.twinlife.twinlife.database.Columns;
import org.twinlife.twinlife.database.DatabaseDump;
import org.twinlife.twinlife.database.DatabaseObjectFactory;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.DatabaseServiceProvider;
import org.twinlife.twinlife.database.QueryBuilder;
import org.twinlife.twinlife.database.RepositoryObjectLoader;
import org.twinlife.twinlife.database.Tables;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RepositoryServiceProvider extends DatabaseServiceProvider implements RepositoryObjectLoader  {
    private static final String LOG_TAG = "RepositoryServicePro...";
    private static final boolean DEBUG = false;

    private static final int DEFAULT_SIZE = 1024;

    /**
     * repository table:
     * id INTEGER: local database identifier (primary key)
     * uuid TEXT UNIQUE NOT NULL: object id
     * schemaId TEXT: the object schema id
     * schemaVersion INTEGER: the object schema version
     * creationDate INTEGER NOT NULL: object creation date
     * twincodeInbound INTEGER: the optional twincode inbound local database identifier
     * twincodeOutbound INTEGER: the optional twincode outbound
     * peerTwincodeOutbound INTEGER: the optional peer twincode outbound
     * owner INTEGER: the optional object owner.
     * name TEXT: name attribute
     * description TEXT: description attribute
     * modificationDate INTEGER NOT NULL: object modification date
     * flags INTEGER: various control flags on the object
     * attributes BLOB: other attributes (serialized)
     * stats BLOB: the object stats (serialized)
     * Note:
     * - id, uuid, schemaId, creationDate are readonly.
     */
    private static final String REPOSITORY_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS repository (id INTEGER PRIMARY KEY,"
                    + " uuid TEXT UNIQUE NOT NULL, schemaId TEXT, schemaVersion INTEGER DEFAULT 0, creationDate INTEGER NOT NULL,"
                    + " twincodeInbound INTEGER, twincodeOutbound INTEGER, peerTwincodeOutbound INTEGER, owner INTEGER,"
                    + " name TEXT, description TEXT, modificationDate INTEGER NOT NULL, attributes BLOB, flags INTEGER,"
                    + " stats BLOB"
                    + ")";

    /**
     * Index used for owner search with WHERE r.owner=?
     */
    private static final String REPOSITORY_CREATE_INDEX = "CREATE INDEX IF NOT EXISTS idx_repository_owner ON repository (owner)";

    /**
     * Table from V7 to V19:
     * "CREATE TABLE IF NOT EXISTS repositoryObject (uuid TEXT PRIMARY KEY NOT NULL, key TEXT,
     *  schemaId TEXT, content BLOB, stats BLOB);";
     */

    @NonNull
    private final RepositoryServiceImpl mService;
    @NonNull
    private final RepositoryObjectFactoryImpl<RepositoryObject>[] mFactories;
    private final Map<UUID, RepositoryObjectFactoryImpl<RepositoryObject>> mFactoryMap;
    private boolean mMigration;

    //
    // Implement BaseServiceProvider interface
    //

    /** @noinspection unchecked*/
    RepositoryServiceProvider(@NonNull RepositoryServiceImpl service,
                              @NonNull DatabaseServiceImpl database,
                              @NonNull RepositoryObjectFactory<?>[] factories) {
        super(service, database, REPOSITORY_CREATE_TABLE, DatabaseTable.TABLE_REPOSITORY_OBJECT);
        if (DEBUG) {
            Log.d(LOG_TAG, "RepositoryServiceProvider: service=" + service);
        }

        mService = service;
        mFactories = new RepositoryObjectFactoryImpl[factories.length];
        mFactoryMap = new HashMap<>();
        mMigration = false;
        for (int i = 0; i < factories.length; i++) {
            RepositoryObjectFactory<RepositoryObject> factory = (RepositoryObjectFactory<RepositoryObject>) factories[i];
            mFactories[i] = new RepositoryObjectFactoryImpl<>(database, this, factory);
            mFactoryMap.put(factories[i].getSchemaId(), mFactories[i]);
        }
    }

    @Override
    protected void onCreate(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate: SQL=" + mCreateTable);
        }

        super.onCreate(transaction);
        transaction.createSchema(REPOSITORY_CREATE_INDEX);
    }

    @Override
    protected void onUpgrade(@NonNull Transaction transaction, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade: oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        /*
         * <pre>
         * Database Version 17:
         *  Date: 2022/12/07:
         *   Repair the repositoryObject inconsistency in the key column that is sometimes null.
         *
         * Database Version 8
         *  Date: 2018/11/26
         *
         *  RepositoryService
         *   Update oldVersion [3,7]:
         *    Add column stats BLOB in  repositoryObject
         *    Add column schemaId TEXT in repositoryObject
         *   Update oldVersion [0,2]: reset
         * </pre>
         **/

        super.onUpgrade(transaction, oldVersion, newVersion);
        transaction.createSchema(REPOSITORY_CREATE_INDEX);
        if (oldVersion < 20 && transaction.hasTable("repositoryObject")) {
            upgrade20(transaction);
        }
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
    @Override
    public RepositoryObject loadRepositoryObject(long dbId, @NonNull UUID schemaId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadRepositoryObject: dbId=" + dbId + " schemaId=" + schemaId);
        }

        final RepositoryObjectFactoryImpl<RepositoryObject> factory = mFactoryMap.get(schemaId);
        if (factory == null) {
            return null;
        }

        final DatabaseIdentifier identifier = new DatabaseIdentifier(factory, dbId);
        final DatabaseObject object = mDatabase.getCache(identifier);
        if (object != null) {
            return (RepositoryObject) object;
        }

        return loadObject(dbId, null, factory);
    }

    //
    // Package scoped methods
    //

    /**
     * Get the repository object factory to create objects with the given schema.
     *
     * @param schemaId the object schema.
     * @return the factory instance or null if there is no factory for the given schema.
     */
    @Nullable
    RepositoryObjectFactoryImpl<RepositoryObject> getFactory(@NonNull UUID schemaId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFactory: schemaId=" + schemaId);
        }

        return mFactoryMap.get(schemaId);
    }

    /**
     * Internal method to get the array of supported object factory.
     *
     * @return the array of factories.
     */
    @NonNull
    RepositoryObjectFactoryImpl<?>[] getFactories() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFactories");
        }

        return mFactories;
    }

    /**
     * Load from the database the repository object with the given uuid or given database id and factory.
     * The factory is used to know the object schema and create instance of that object
     * to load it from the database.
     *
     * @param dbId the database identifier.
     * @param objectId the object id.
     * @param factory the factory for the creation of object instances.
     * @return the object or null.
     */
    @Nullable
    RepositoryObject loadObject(long dbId, @Nullable UUID objectId, @NonNull RepositoryObjectFactoryImpl<RepositoryObject> factory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadObject: objectId=" + objectId);
        }

        if (objectId != null) {
            DatabaseObject object = mDatabase.getCache(objectId);
            if (object != null) {
                // Verify that the cached object comes from the asked factory.
                // It could happen due to programming mistake that we ask a Contact by using a group UUID.
                if (!object.isFromFactory(factory)) {
                    return null;
                }
                return (RepositoryObject) object;
            }
        }

        final UUID schemaId = factory.getSchemaId();
        final int mode = factory.getTwincodeUsage();
        final QueryBuilder query = new QueryBuilder("r.id, r.uuid, r.creationDate, r.name, "
                + "r.description, r.attributes, r.modificationDate, r.owner");

        if ((mode & RepositoryObjectFactory.USE_OUTBOUND) != 0) {
            query.append(", twout.id, twout.twincodeId, twout.modificationDate, twout.name, twout.avatarId, twout.description, twout.capabilities, twout.attributes, twout.flags");
        }
        if ((mode & RepositoryObjectFactory.USE_PEER_OUTBOUND) != 0) {
            query.append(", po.id, po.twincodeId, po.modificationDate, po.name, po.avatarId, po.description, po.capabilities, po.attributes, po.flags");
        }
        if ((mode & RepositoryObjectFactory.USE_INBOUND) != 0) {
            query.append(", ti.id, ti.twincodeId, ti.factoryId, ti.twincodeOutbound, ti.modificationDate, ti.capabilities, ti.attributes");
        }
        query.append(" FROM repository AS r");
        if ((mode & RepositoryObjectFactory.USE_INBOUND) != 0) {
            query.append(" LEFT JOIN twincodeInbound AS ti on r.twincodeInbound = ti.id");
        }
        if ((mode & RepositoryObjectFactory.USE_OUTBOUND) != 0) {
            query.append(" LEFT JOIN twincodeOutbound AS twout on r.twincodeOutbound = twout.id");
        }
        if ((mode & RepositoryObjectFactory.USE_PEER_OUTBOUND) != 0) {
            query.append(" LEFT JOIN twincodeOutbound AS po on r.peerTwincodeOutbound = po.id");
        }

        if (objectId == null) {
            query.filterLong("r.id", dbId);
        } else {
            query.filterUUID("r.uuid", objectId);
        }
        query.filterUUID("r.schemaId", schemaId);
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {

            while (cursor.moveToNext()) {
                final RepositoryObject repoObject = loadRepositoryObject(factory, cursor, mode, 0);
                if (repoObject != null) {
                    return repoObject;
                }
            }
        } catch (DatabaseException exception) {
            Log.e(LOG_TAG, "Database exception", exception);
            mService.onDatabaseException(exception);
        }

        return null;
    }

    /**
     * List the objects with the given factory.  The factory is used to know the schema Id of the objects to load
     * and to create instances when they are loaded from the database.
     *
     * @param factory the factory
     * @return the list of objects.
     */
    @NonNull
    List<RepositoryObject> listObjects(@NonNull RepositoryObjectFactoryImpl<RepositoryObject> factory,
                                       @Nullable Filter<RepositoryObject> filter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listObjects factory=" + factory + " filter=" + filter);
        }

        final UUID schemaId = factory.getSchemaId();
        final int mode = factory.getTwincodeUsage();
        final QueryBuilder query = new QueryBuilder("r.id, r.uuid, r.creationDate, r.name, r.description, r.attributes, r.modificationDate, r.owner");

        if ((mode & RepositoryObjectFactory.USE_OUTBOUND) != 0) {
            query.append(", twout.id, twout.twincodeId, twout.modificationDate, twout.name, twout.avatarId, twout.description, twout.capabilities, twout.attributes, twout.flags");
        }
        if ((mode & RepositoryObjectFactory.USE_PEER_OUTBOUND) != 0) {
            query.append(", po.id, po.twincodeId, po.modificationDate, po.name, po.avatarId, po.description, po.capabilities, po.attributes, po.flags");
        }
        if ((mode & RepositoryObjectFactory.USE_INBOUND) != 0) {
            query.append(", ti.id, ti.twincodeId, ti.factoryId, ti.twincodeOutbound, ti.modificationDate, ti.capabilities, ti.attributes");
        }
        query.append(" FROM repository AS r");
        if ((mode & RepositoryObjectFactory.USE_INBOUND) != 0) {
            query.append(" LEFT JOIN twincodeInbound AS ti on r.twincodeInbound = ti.id");
        }
        if ((mode & RepositoryObjectFactory.USE_OUTBOUND) != 0) {
            query.append(" LEFT JOIN twincodeOutbound AS twout on r.twincodeOutbound = twout.id");
        }
        if ((mode & RepositoryObjectFactory.USE_PEER_OUTBOUND) != 0) {
            query.append(" LEFT JOIN twincodeOutbound AS po on r.peerTwincodeOutbound = po.id");
        }
        query.filterUUID("r.schemaId", schemaId);
        if (filter != null) {
            query.filterOwner("r.owner", filter.owner);
            query.filterTwincode("po.id", filter.twincodeOutbound);
        }
        final List<RepositoryObject> result = new ArrayList<>();
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {

            while (cursor.moveToNext()) {
                final RepositoryObject repoObject = loadRepositoryObject(factory, cursor, mode, 0);
                if (repoObject != null && (filter == null || filter.accept(repoObject))) {
                    result.add(repoObject);
                }
            }
        } catch (DatabaseException exception) {
            Log.e(LOG_TAG, "Database exception", exception);
            mService.onDatabaseException(exception);
        }
        if (DEBUG) {
            Log.d(LOG_TAG, "listObjects found " + result.size() + " objects for schema " + schemaId);
        }
        return result;
    }

    /**
     * Find the object with either the objectId or the twincode inbound id of the object.
     * Only one object must be found and its schema must be from one of the factories.
     *
     * @param withInboundId when true look at the twincode inbound id for the match.
     * @param id the uuid to search.
     * @param factories the list of factories (ie, schema ids) which are considered.
     * @return the object or nil.
     */
    @Nullable
    RepositoryObject findObject(boolean withInboundId, @NonNull UUID id, @NonNull RepositoryObjectFactoryImpl<?>[] factories) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findObject withInboundId=" + withInboundId + " id=" + id);
        }

        final QueryBuilder query = new QueryBuilder("r.schemaId,"
                + " r.id, r.uuid, r.creationDate, r.name, r.description, r.attributes, r.modificationDate, r.owner,"
                + " twout.id, twout.twincodeId, twout.modificationDate, twout.name, twout.avatarId, twout.description, twout.capabilities, twout.attributes, twout.flags,"
                + " po.id, po.twincodeId, po.modificationDate, po.name, po.avatarId, po.description, po.capabilities, po.attributes, po.flags,"
                + " ti.id, ti.twincodeId, ti.factoryId, ti.twincodeOutbound, ti.modificationDate, ti.capabilities, ti.attributes");

        if (withInboundId) {
            query.append(" FROM twincodeInbound AS ti"
                    + " INNER JOIN repository AS r ON r.twincodeInbound = ti.id"
                    + " LEFT JOIN twincodeOutbound AS twout ON r.twincodeOutbound = twout.id"
                    + " LEFT JOIN twincodeOutbound AS po ON r.peerTwincodeOutbound = po.id");
            query.filterUUID("ti.twincodeId", id);
        } else {
            query.append(" FROM repository AS r"
                    + " LEFT JOIN twincodeInbound AS ti ON r.twincodeInbound = ti.id"
                    + " LEFT JOIN twincodeOutbound AS twout ON r.twincodeOutbound = twout.id"
                    + " LEFT JOIN twincodeOutbound AS po ON r.peerTwincodeOutbound = po.id");
            query.filterUUID("r.uuid", id);
        }

        final List<UUID> schemas = new ArrayList<>(factories.length);
        for (RepositoryObjectFactoryImpl<?> factory : factories) {
            schemas.add(factory.getSchemaId());
        }
        query.filterIn("r.schemaId", schemas);

        final List<RepositoryObject> result = new ArrayList<>();
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {

            while (cursor.moveToNext()) {
                final UUID schemaId = cursor.getUUID(0);
                if (schemaId != null) {
                    final RepositoryObjectFactoryImpl<RepositoryObject> factory = mFactoryMap.get(schemaId);
                    if (factory != null) {
                        final RepositoryObject repoObject = loadRepositoryObject(factory, cursor, RepositoryObjectFactory.USE_ALL, 1);
                        if (repoObject != null) {
                            result.add(repoObject);
                        }
                    }
                }
            }
        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }
        return result.size() == 1 ? result.get(0) : null;
    }

    boolean hasObjects(@NonNull UUID schemaId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasObjects: schemaId=" + schemaId);
        }

        try {
            final Long result = mDatabase.longQuery("SELECT COUNT(*) FROM repository WHERE schemaId=?",
                    new String[]{schemaId.toString()});
            return result != null && result > 0;

        } catch (Exception exception) {
            // An exception can be raised when the account is deleted (database is closed and removed).
            mService.onDatabaseException(exception);
            return false;
        }
    }

    /**
     * Load the object stats for every object of the given schema.
     *
     * @param schemaId the schema id.
     * @return the map of objects with only their stats.
     */
    @NonNull
    Map<DatabaseIdentifier, Pair<ObjectStatImpl, Integer>> loadStats(@NonNull UUID schemaId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadStats: schemaId=" + schemaId);
        }

        final RepositoryObjectFactoryImpl<RepositoryObject> factory = getFactory(schemaId);
        final Map<DatabaseIdentifier, Pair<ObjectStatImpl, Integer>> result = new HashMap<>();
        if (factory != null) {
            try (final DatabaseCursor cursor = mDatabase.rawQuery("SELECT r.id, r.stats, po.flags FROM repository AS r"
                    + " LEFT JOIN twincodeOutbound AS po ON r.peerTwincodeOutbound = po.id"
                    + " WHERE r.schemaId=?", new String[]{schemaId.toString()})) {
                while (cursor.moveToNext()) {
                    final long databaseId = cursor.getLong(0);
                    final ObjectStatImpl stat = extractObjectStatImpl(databaseId, cursor.getBlob(1));

                    if (stat != null) {
                        final int peerTwincodeFlags = cursor.getInt(2);
                        final DatabaseIdentifier objectId = new DatabaseIdentifier(factory, databaseId);
                        result.put(objectId, new Pair<>(stat, peerTwincodeFlags));
                    }
                }
            } catch (DatabaseException exception) {
                mService.onDatabaseException(exception);
            }
        }
        return result;
    }

    /**
     * Load the object stat for the given object.
     *
     * @param object the repository object.
     * @return the object stat or null if the object is not found.
     */
    @Nullable
    ObjectStatImpl loadStat(@NonNull RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadStat: object=" + object);
        }

        ObjectStatImpl result = null;
        final DatabaseIdentifier id = object.getDatabaseId();
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT stats FROM repository"
                + " WHERE id=?", new String[]{ Long.toString(id.getId()) })) {
            while (cursor.moveToNext()) {
                ObjectStatImpl stat = extractObjectStatImpl(id.getId(), cursor.getBlob(0));

                if (stat == null) {
                    stat = new ObjectStatImpl(id.getId());
                }
                stat.setObjectSchemaId(id.getSchemaId());
                result = stat;
            }
        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }
        return result;
    }

    @Nullable
    RepositoryObject createObject(@NonNull UUID objectId, @NonNull RepositoryObjectFactoryImpl<?> factory,
                                  @NonNull RepositoryService.Initializer initializer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObject: objectId=" + objectId);
        }

        long now = System.currentTimeMillis();
        try (Transaction transaction = newTransaction()) {

            long id = transaction.allocateId(DatabaseTable.TABLE_REPOSITORY_OBJECT);
            DatabaseIdentifier identifier = new DatabaseIdentifier(factory, id);
            RepositoryObject object = factory.saveObject(identifier, objectId, null, now);
            initializer.initialize(object);
            internalInsertObject(transaction, object, now, now,null);
            transaction.commit();
            mDatabase.putCache(object);
            return object;

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    @Nullable
    RepositoryObject importObject(@NonNull UUID objectId, long creationDate, @NonNull RepositoryObjectFactoryImpl<?> factory,
                                  @NonNull List<BaseService.AttributeNameValue> attributes,
                                  @Nullable UUID objectKey) {
        if (DEBUG) {
            Log.d(LOG_TAG, "importObject: objectId=" + objectId);
        }

        long now = System.currentTimeMillis();
        try (Transaction transaction = newTransaction()) {

            long id = transaction.allocateId(DatabaseTable.TABLE_REPOSITORY_OBJECT);
            DatabaseIdentifier identifier = new DatabaseIdentifier(factory, id);
            RepositoryObject object = factory.importObject(transaction, identifier, objectId, objectKey, now, attributes);
            if (object != null) {
                internalInsertObject(transaction, object, creationDate, now, null);
                transaction.commit();
                mDatabase.putCache(object);
            }
            return object;

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    void setOwner(@NonNull RepositoryObjectFactory<?> factory, @NonNull RepositoryObject newOwner) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setOwner: factory=" + factory);
        }

        final UUID schemaId = factory.getSchemaId();
        try (Transaction transaction = newTransaction()) {

            ContentValues values = new ContentValues();
            values.put(Columns.OWNER, newOwner.getDatabaseId().getId());
            transaction.update(Tables.REPOSITORY, values, "schemaId=? AND owner IS NULL", new String[] { schemaId.toString() });
            transaction.commit();
            mDatabase.evictCacheWithSchemaId(schemaId);

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void updateObject(@NonNull RepositoryObject object, long modificationDate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateObject: object=" + object + " modificationDate=" + modificationDate);
        }

        try (Transaction transaction = newTransaction()) {

            ContentValues values = new ContentValues();
            values.put(Columns.NAME, object.getName());
            values.put(Columns.DESCRIPTION, object.getDescription());
            values.put(Columns.MODIFICATION_DATE, modificationDate);
            TwincodeInbound twincodeInbound = object.getTwincodeInbound();
            if (twincodeInbound != null) {
                values.put(Columns.TWINCODE_INBOUND, twincodeInbound.getDatabaseId().getId());
            }
            TwincodeOutbound twincodeOutbound = object.getTwincodeOutbound();
            if (twincodeOutbound != null) {
                values.put(Columns.TWINCODE_OUTBOUND, twincodeOutbound.getDatabaseId().getId());
            }
            TwincodeOutbound peerTwincodeOutbound = object.getPeerTwincodeOutbound();
            if (peerTwincodeOutbound != null) {
                values.put(Columns.PEER_TWINCODE_OUTBOUND, peerTwincodeOutbound.getDatabaseId().getId());
            }
            RepositoryObject owner = object.getOwner();
            if (owner != null) {
                values.put(Columns.OWNER, owner.getDatabaseId().getId());
            }
            List<BaseService.AttributeNameValue> otherAttributes = object.getAttributes(false);
            byte[] data = BinaryCompactEncoder.serialize(otherAttributes);
            values.put(Columns.ATTRIBUTES, data);

            transaction.updateWithId(Tables.REPOSITORY, values, object.getDatabaseId().getId());
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void updateObjectStats(@NonNull ObjectStatImpl stats) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateObjectStats: stats=" + stats);
        }

        try (Transaction transaction = newTransaction()) {

            internalUpdateObjectStat(transaction, stats);
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void updateStats(@NonNull List<ObjectStatImpl> stats) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateStats: stats=" + stats);
        }

        try (Transaction transaction = newTransaction()) {
            for (ObjectStatImpl stat : stats) {
                internalUpdateObjectStat(transaction, stat);
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Delete the repository object under a transaction.
     * We also trigger the deletion of its notifications.
     *
     * @param object the repository object to delete.
     */
    void deleteObject(@NonNull RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObject: object=" + object);
        }

        try (Transaction transaction = newTransaction()) {
            transaction.deleteConversations(object.getDatabaseId().getId(), null);
            transaction.deleteObject(object);
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    boolean isMigrationRunning() {

        return mMigration;
    }

    /**
     * Migrate the twincodeInboundTwincodeInbound table to the twincodeInbound new table format.
     *
     * @throws DatabaseException when a database error occurred.
     */
    @Nullable
    TwincodeInbound loadLegacyTwincodeInbound(@NonNull Transaction transaction,
                                              @NonNull UUID twincodeId, @Nullable TwincodeOutbound twincodeOutbound,
                                              @Nullable UUID twincodeFactoryId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadLegacyTwincodeInbound");
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT content FROM twincodeInboundTwincodeInbound WHERE uuid=?",
                new String[]{ twincodeId.toString() })) {
            while (cursor.moveToNext()) {
                byte[] content = cursor.getBlob(0);

                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(content);
                DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
                long modificationDate = 0;
                List<BaseService.AttributeNameValue> attributes;
                try {
                    dataInputStream.readLong();
                    dataInputStream.readLong();
                    modificationDate = dataInputStream.readLong();

                    attributes = new ArrayList<>();
                    int size = dataInputStream.readInt();
                    for (int i = 0; i < size; i++) {
                        BaseService.AttributeNameValue attribute = BaseServiceImpl.deserialize(dataInputStream);
                        if (attribute != null) {
                            attributes.add(attribute);
                        }
                    }

                } catch (Exception ignored) {
                    attributes = null;
                }
                if (attributes != null && twincodeOutbound != null) {
                    return transaction.storeTwincodeInbound(twincodeId, twincodeOutbound, twincodeFactoryId,
                            attributes, 0, modificationDate);
                }
            }
        }
        return null;
    }

    @Nullable
    private ObjectStatImpl extractObjectStatImpl(long databaseId, @Nullable byte[] stats) {
        if (stats == null) {
            return null;
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(stats);
            BinaryDecoder binaryDecoder = new BinaryDecoder(inputStream);
            UUID statSchemaId = binaryDecoder.readUUID();
            int schemaVersion = binaryDecoder.readInt();
            if (ObjectStatImpl.SCHEMA_ID.equals(statSchemaId)) {
                if (ObjectStatImpl.SCHEMA_VERSION_1 == schemaVersion) {
                    return (ObjectStatImpl) ObjectStatImpl.SERIALIZER_1.deserialize(databaseId, binaryDecoder);
                } else if (ObjectStatImpl.SCHEMA_VERSION_2 == schemaVersion) {
                    return (ObjectStatImpl) ObjectStatImpl.SERIALIZER_2.deserialize(databaseId, binaryDecoder);
                } else if (ObjectStatImpl.SCHEMA_VERSION == schemaVersion) {
                    return (ObjectStatImpl) ObjectStatImpl.SERIALIZER.deserialize(databaseId, binaryDecoder);
                }
            }
        } catch (SerializerException ex) {
            // mTwinlifeImpl.sendProblemReport(LOG_TAG, "Cannot deserialize repository stats", ex);
        }
        return null;
    }

    private void internalUpdateObjectStat(@NonNull Transaction transaction, @NonNull ObjectStatImpl stats) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalUpdateObjectStat: transaction=" + transaction + " stats=" + stats);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(DEFAULT_SIZE);
        BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
        try {
            ObjectStatImpl.SERIALIZER.serialize(mService.getSerializerFactoryImpl(), binaryEncoder, stats);

        } catch (SerializerException ex) {
            mService.getTwinlifeImpl().exception(TwinlifeAssertPoint.SERIALIZER_EXCEPTION, ex, null);
            return;
        }

        ContentValues values = new ContentValues();
        values.put(Columns.STATS, outputStream.toByteArray());
        transaction.updateWithId(Tables.REPOSITORY, values, stats.getDatabaseId());
    }

    @Nullable
    private RepositoryObject loadRepositoryObject(@NonNull DatabaseObjectFactory<RepositoryObject> factory,
                                                  @NonNull DatabaseCursor cursor, int mode, int offset) throws DatabaseException {

        if (cursor.isNull(offset)) {
            return null;
        }

        final DatabaseIdentifier identifier = new DatabaseIdentifier(factory, cursor.getLong(offset));
        final DatabaseObject object = mDatabase.getCache(identifier);
        RepositoryObject result;
        if (object == null) {
            result = factory.createObject(identifier, cursor, offset + 1);
            if (result == null) {
                return null;
            }
        } else {
            result = (RepositoryObject) object;
            if (!factory.loadObject(result, cursor, offset + 1)) {
                return result;
            }
        }

        offset += 8;
        if ((mode & RepositoryObjectFactory.USE_OUTBOUND) != 0) {
            TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(cursor, offset);
            result.setTwincodeOutbound(twincodeOutbound);
            offset += 9;
        }
        if ((mode & RepositoryObjectFactory.USE_PEER_OUTBOUND) != 0) {
            TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(cursor, offset);
            result.setPeerTwincodeOutbound(twincodeOutbound);
            offset += 9;
        }
        if ((mode & RepositoryObjectFactory.USE_INBOUND) != 0) {
            TwincodeInbound twincodeInbound = mDatabase.loadTwincodeInbound(cursor, offset);
            result.setTwincodeInbound(twincodeInbound);
            // offset += 7;
        }
        if (!result.isValid()) {
            mService.notifyInvalid(result);
            return null;
        }
        if (object == null) {
            mDatabase.putCache(result);
        }
        return result;
    }

    private void internalInsertObject(@NonNull Transaction transaction, @NonNull RepositoryObject object,
                                      long creationDate, long modificationDate,
                                      @Nullable byte[] stats) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalInsertObject transaction=" + transaction + " object=" + object);
        }

        final DatabaseIdentifier identifier = object.getDatabaseId();
        final ContentValues values = new ContentValues();
        values.put(Columns.ID, identifier.getId());
        values.put(Columns.UUID, object.getId().toString());
        values.put(Columns.SCHEMA_ID, identifier.getSchemaId().toString());
        values.put(Columns.SCHEMA_VERSION, identifier.getSchemaVersion());
        values.put(Columns.NAME, object.getName());
        values.put(Columns.DESCRIPTION, object.getDescription());
        values.put(Columns.CREATION_DATE, creationDate);
        values.put(Columns.MODIFICATION_DATE, modificationDate);
        TwincodeInbound twincodeInbound = object.getTwincodeInbound();
        if (twincodeInbound != null) {
            values.put(Columns.TWINCODE_INBOUND, twincodeInbound.getDatabaseId().getId());
        }
        TwincodeOutbound twincodeOutbound = object.getTwincodeOutbound();
        if (twincodeOutbound != null) {
            values.put(Columns.TWINCODE_OUTBOUND, twincodeOutbound.getDatabaseId().getId());
        }
        TwincodeOutbound peerTwincodeOutbound = object.getPeerTwincodeOutbound();
        if (peerTwincodeOutbound != null) {
            values.put(Columns.PEER_TWINCODE_OUTBOUND, peerTwincodeOutbound.getDatabaseId().getId());
        }
        RepositoryObject owner = object.getOwner();
        if (owner != null) {
            values.put(Columns.OWNER, owner.getDatabaseId().getId());
        }
        List<BaseService.AttributeNameValue> otherAttributes = object.getAttributes(false);
        byte[] data = BinaryCompactEncoder.serialize(otherAttributes);
        values.put(Columns.ATTRIBUTES, data);
        if (stats != null) {
            values.put(Columns.STATS, stats);
        }
        transaction.insertOrThrow(Tables.REPOSITORY, null, values);
    }

    /**
     * Migrate the repositoryObject table to the repository new table format.
     *
     * @throws DatabaseException when a database error occurred.
     */
    private void upgrade20(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgrade20");
        }

        mMigration = true;
        transaction.createSchema(REPOSITORY_CREATE_TABLE);
        for (RepositoryObjectFactoryImpl<?> factory : mFactories) {
            upgradeSchema20(transaction, factory);
        }
        mMigration = false;
        if (DEBUG) {
            DatabaseDump.printRepository(mDatabase);
        }

        // Now we can drop the old tables.
        transaction.dropTable("repositoryObject");
        transaction.dropTable("twincodeInboundTwincodeInbound");
    }

    /**
     * Migrate the repositoryObject table to the repository new table format.
     *
     * @throws DatabaseException when a database error occurred.
     */
    private void upgradeSchema20(@NonNull Transaction transaction,
                                 @NonNull RepositoryObjectFactoryImpl<?> factory) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgradeSchema20");
        }

        final long startTime = EventMonitor.start();
        int count = 0;

        final UUID schemaId = factory.getSchemaId();
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT uuid, key, content, stats "
                + " FROM repositoryObject WHERE schemaId=?", new String[] { schemaId.toString() })) {
            while (cursor.moveToNext()) {
                UUID id = cursor.getUUID(0);
                UUID key = cursor.getUUID(1);
                byte[] content = cursor.getBlob(2);
                byte[] stats = cursor.getBlob(3);
                if (id == null || content == null) {
                    continue;
                }

                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(content);
                DataInputStream dataInputStream = new DataInputStream(byteArrayInputStream);
                long modificationDate = 0;
                List<BaseService.AttributeNameValue> attributes = null;

                try {
                    // Ignore object ID
                    dataInputStream.readLong();
                    dataInputStream.readLong();
                    modificationDate = dataInputStream.readLong();
                    // Ignore schemaId
                    dataInputStream.readLong();
                    dataInputStream.readLong();
                    /* int schemaVersion = */ dataInputStream.readInt();
                    /* String serializer = */ dataInputStream.readUTF();
                    /* boolean immutable = */ dataInputStream.readBoolean();
                    UUID serializedKey = new UUID(dataInputStream.readLong(), dataInputStream.readLong());
                    if (serializedKey.getMostSignificantBits() == 0 && serializedKey.getLeastSignificantBits() == 0) {
                        serializedKey = null;
                    }

                    // Before 2019, the CreateContactPhase1Executor was creating a Contact without a key.
                    // The key was initialized/known after the creation of identity twincode and then the object updated.
                    // After that update, the database repositoryObject `key` column was not updated and hence we have it null now.
                    // The ObjectImpl that is serialize contains the key and we load these bad rows and update the key
                    // in the database table 3 years after!  The CreateContactPhase2Executor did not have that issue.
                    // After 2019, the twincode inbound that represents the key is always created before the Contact.
                    // A Contact is always inserted with a non null key (and it is never modified).
                    if (key == null) {
                        key = serializedKey;
                    }
                    int length = dataInputStream.readInt();
                    byte[] bytes = new byte[length];
                    if (dataInputStream.read(bytes) != length) {
                        continue;
                    }

                    String attrContent = new String(bytes);
                    try {
                        attributes = mService.deserializeContent(attrContent);
                    } catch (Exception exception) {
                        //
                        // Handle possible meta-character '&' in contact name (stored before escaping XML meta-characters in serialization)
                        //
                        try {
                            attributes = mService.deserializeContent(Utils.escapeAmpersand(attrContent));
                        } catch (Exception ignored) {
                        }
                    }
                    // Ignore the exclusiveContents (never used).

                } catch (Exception exception) {
                    if (Logger.ERROR) {
                        Logger.error(LOG_TAG, "deserialize", exception);
                    }

                }

                if (attributes != null) {
                    long newId = transaction.allocateId(DatabaseTable.TABLE_REPOSITORY_OBJECT);
                    DatabaseIdentifier identifier = new DatabaseIdentifier(factory, newId);
                    RepositoryObject object = factory.importObject(transaction, identifier, id, key, modificationDate, attributes);
                    if (object != null) {
                        internalInsertObject(transaction, object, modificationDate, modificationDate, stats);
                        mDatabase.putCache(object);
                        count++;
                    }
                }
            }
        }
        if (BuildConfig.ENABLE_EVENT_MONITOR) {
            EventMonitor.event("Migrated " + count + " objects with schema " + schemaId, startTime);
        }
    }
}
