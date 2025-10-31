/*
 *  Copyright (c) 2014-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;
import android.util.Pair;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.crypto.CryptoServiceImpl;
import org.twinlife.twinlife.datatype.ArrayData;
import org.twinlife.twinlife.datatype.Data;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundImpl;
import org.twinlife.twinlife.util.Logger;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the RepositoryService which creates and manages RepositoryObject only stored locally
 * in the database.
 */
public class RepositoryServiceImpl extends BaseServiceImpl<RepositoryService.ServiceObserver> implements RepositoryService {
    private static final String LOG_TAG = "LocalRepositoryServ...";
    private static final boolean DEBUG = false;

    protected final RepositoryServiceProvider mServiceProvider;
    private final HashMap<UUID, Weight[]> mWeights = new HashMap<>();
    private final Map<DatabaseIdentifier, ObjectStatImpl> mObjectStats = new HashMap<>();
    private final XmlPullParser mParser;
    @Nullable
    private List<StatQueued> mStatQueue;

    private static final class StatQueued {
        final RepositoryObject object;
        final StatType kind;
        final long value;

        StatQueued(RepositoryObject object, StatType kind) {
            this.object = object;
            this.kind = kind;
            this.value = -1;
        }

        StatQueued(RepositoryObject object, StatType kind, long value) {
            this.object = object;
            this.kind = kind;
            this.value = value;
        }
    }

    public RepositoryServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection,
                                 @NonNull RepositoryObjectFactory<?>[] factories) {

        super(twinlifeImpl, connection);

        setServiceConfiguration(new RepositoryServiceConfiguration());
        mServiceProvider = new RepositoryServiceProvider(this, twinlifeImpl.getDatabaseService(), factories);

        XmlPullParser parser = null;
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            parser = factory.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "start", exception);
            }
        }
        mParser = parser;
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof RepositoryServiceConfiguration)) {
            setConfigured(false);

            return;
        }
        RepositoryServiceConfiguration repositoryServiceConfiguration = new RepositoryServiceConfiguration();

        setServiceConfiguration(repositoryServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    //
    // Implement RepositoryService interface
    //
    @Override
    public void getObject(@NonNull UUID objectId, @NonNull RepositoryObjectFactory<?> factory,
                          @NonNull Consumer<RepositoryObject> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getObject: objectId=" + objectId + " factory=" + factory);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final UUID schemaId = factory.getSchemaId();
        final RepositoryObjectFactoryImpl<RepositoryObject> dbFactory = mServiceProvider.getFactory(schemaId);
        if (dbFactory == null) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        final RepositoryObject object = mServiceProvider.loadObject(0, objectId, dbFactory);
        if (object != null) {
            complete.onGet(ErrorCode.SUCCESS, object);
            return;
        }

        if (factory.isLocal()) {
            complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }
        complete.onGet(ErrorCode.FEATURE_NOT_IMPLEMENTED, null);
    }

    @Override
    public void listObjects(@NonNull RepositoryObjectFactory<?> factory,
                            @Nullable Filter<RepositoryObject> filter,
                            @NonNull Consumer<List<RepositoryObject>> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listObjects: factory=" + factory + " filter=" + filter);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final UUID schemaId = factory.getSchemaId();
        final RepositoryObjectFactoryImpl<RepositoryObject> dbFactory = mServiceProvider.getFactory(schemaId);
        if (dbFactory == null) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        List<RepositoryObject> objects = mServiceProvider.listObjects(dbFactory, filter);
        complete.onGet(ErrorCode.SUCCESS, objects);
    }

    @Override
    @NonNull
    public RepositoryService.FindResult findObject(boolean withTwincodeInbound, @NonNull UUID key,
                                                   @NonNull RepositoryObjectFactory<?>[] factories) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findObject: withTwincodeInbound=" + withTwincodeInbound + " key=" + key);
        }

        if (!isServiceOn()) {
            return FindResult.error(ErrorCode.SERVICE_UNAVAILABLE);
        }

        final RepositoryObjectFactoryImpl<?>[] dbFactories = new RepositoryObjectFactoryImpl[factories.length];
        for (int i = 0; i < factories.length; i++) {
            dbFactories[i] = mServiceProvider.getFactory(factories[i].getSchemaId());
            if (dbFactories[i] == null) {
                return FindResult.error(ErrorCode.BAD_REQUEST);
            }
        }

        final RepositoryObject object = mServiceProvider.findObject(withTwincodeInbound, key, dbFactories);
        if (object == null) {
            return FindResult.error(ErrorCode.ITEM_NOT_FOUND);
        } else {
            return FindResult.ok(object);
        }
    }

    @Override
    @NonNull
    public RepositoryService.FindResult findWithSignature(@NonNull String signature, @NonNull RepositoryObjectFactory<?>[] factories) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findObject: findWithSignature=" + signature);
        }

        if (!isServiceOn()) {
            return FindResult.error(ErrorCode.SERVICE_UNAVAILABLE);
        }

        final RepositoryObjectFactoryImpl<?>[] dbFactories = new RepositoryObjectFactoryImpl[factories.length];
        for (int i = 0; i < factories.length; i++) {
            dbFactories[i] = mServiceProvider.getFactory(factories[i].getSchemaId());
            if (dbFactories[i] == null) {
                return FindResult.error(ErrorCode.BAD_REQUEST);
            }
        }

        final CryptoServiceImpl cryptoService = mTwinlifeImpl.getCryptoService();
        final Pair<ErrorCode, UUID> verifyResult = cryptoService.verifyAuthenticate(signature);
        if (verifyResult.first != ErrorCode.SUCCESS) {
            return FindResult.error(verifyResult.first);
        }

        final RepositoryObject object = mServiceProvider.findObject(false, verifyResult.second, dbFactories);
        if (object == null) {
            return FindResult.error(ErrorCode.ITEM_NOT_FOUND);
        } else {
            return FindResult.ok(object);
        }
    }

    @Nullable
    public RepositoryObject findObject(@NonNull UUID key) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findObject key=" + key);
        }

        if (!isServiceOn()) {
            return null;
        }

        return mServiceProvider.findObject(true, key, mServiceProvider.getFactories());
    }

    @Override
    public void updateObject(@NonNull RepositoryObject object,
                             @NonNull Consumer<RepositoryObject> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateObject: object=" + object);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        // When the object is local only, update its instance now.
        final DatabaseIdentifier id = object.getDatabaseId();
        if (id.isLocal()) {

            onUpdateObject(object, System.currentTimeMillis(), complete);
            return;
        }

        complete.onGet(ErrorCode.FEATURE_NOT_IMPLEMENTED, null);
    }

    @Override
    public boolean hasObjects(@NonNull UUID schemaId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasObjects schemaId=" + schemaId);
        }

        if (!isServiceOn()) {

            return false;
        }

        return mServiceProvider.hasObjects(schemaId);
    }

    @Override
    public void createObject(@NonNull RepositoryObjectFactory<?> factory, AccessRights accessRights,
                             @NonNull Initializer initializer, @NonNull Consumer<RepositoryObject> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObject: accessRights=" + accessRights + " factory=" + factory);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final UUID schemaId = factory.getSchemaId();
        final RepositoryObjectFactoryImpl<?> dbFactory = mServiceProvider.getFactory(schemaId);
        if (dbFactory == null) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        if (factory.isLocal()) {
            final RepositoryObject object = mServiceProvider.createObject(UUID.randomUUID(), dbFactory, initializer);
            complete.onGet(object == null ? ErrorCode.NO_STORAGE_SPACE : ErrorCode.SUCCESS, object);
            return;
        }
        complete.onGet(ErrorCode.FEATURE_NOT_IMPLEMENTED, null);
    }

    @Override
    public void deleteObject(@NonNull RepositoryObject object, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObject: object=" + object);
        }

        if (!isServiceOn()) {

            return;
        }

        final DatabaseIdentifier id = object.getDatabaseId();

        // When the object is local only, delete its instance now.
        if (id.isLocal()) {

            onDeleteObject(object, complete);
            return;
        }
        complete.onGet(ErrorCode.FEATURE_NOT_IMPLEMENTED, null);
    }

    @Override
    public void setOwner(@NonNull RepositoryObjectFactory<?> factory, @NonNull RepositoryObject newOwner) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setOwner: factory=" + factory);
        }

        if (!isServiceOn()) {

            return;
        }

        mServiceProvider.setOwner(factory, newOwner);
    }

    @Override
    public void incrementStat(@NonNull RepositoryObject object, StatType kind) {
        if (DEBUG) {
            Log.d(LOG_TAG, "incrementStat: object=" + object + " kind=" + kind);
        }

        if (!isServiceOn()) {

            return;
        }

        ObjectStatImpl stats;
        synchronized (this) {
            // Stats are frozen, remember the new item in the queue.
            if (mStatQueue != null) {
                mStatQueue.add(new StatQueued(object, kind));
                return;
            }
            stats = mObjectStats.get(object.getDatabaseId());
        }

        if (stats == null) {
            stats = mServiceProvider.loadStat(object);
            if (stats != null) {
                synchronized (this) {
                    ObjectStatImpl lObjectImpl = mObjectStats.put(object.getDatabaseId(), stats);
                    if (lObjectImpl != null) {
                        stats = lObjectImpl;
                    }
                }
            }
        }

        if (stats != null) {
            final Weight[] weights = mWeights.get(stats.getObjectSchemaId());
            synchronized (this) {
                stats.increment(kind, weights);
            }
            mServiceProvider.updateObjectStats(stats);
        }
    }

    @Override
    public void updateStat(@NonNull RepositoryObject object, @NonNull StatType kind, long value) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateStat: objectId=" + object + " kind=" + kind + " value=" + value);
        }

        if (!isServiceOn()) {

            return;
        }

        ObjectStatImpl stats;
        synchronized (this) {
            // Stats are frozen, remember the new item in the queue.
            if (mStatQueue != null) {
                mStatQueue.add(new StatQueued(object, kind, value));
                return;
            }
            stats = mObjectStats.get(object.getDatabaseId());
        }

        if (stats == null) {
            stats = mServiceProvider.loadStat(object);
            if (stats != null) {
                synchronized (this) {
                    ObjectStatImpl lObjectImpl = mObjectStats.put(object.getDatabaseId(), stats);
                    if (lObjectImpl != null) {
                        stats = lObjectImpl;
                    }
                }
            }
        }

        if (stats != null) {
            final Weight[] weights = mWeights.get(stats.getObjectSchemaId());
            synchronized (this) {
                stats.update(kind, weights, value);
            }
            mServiceProvider.updateObjectStats(stats);
        }
    }

    @Override
    public void updateStats(@NonNull RepositoryObjectFactory<?> factory, boolean updateScore,
                            @NonNull Consumer<List<RepositoryObject>> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateStats factory=" + factory + " updateScore=" + updateScore);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        // Step 1: freeze the stats and collect the objects of the given schema with their corresponding computed scale factor.
        synchronized (this) {
            // Freeze the stats and queue them.
            if (mStatQueue != null) {
                mStatQueue = new ArrayList<>();
            }
        }

        // Step 2: get the object stats from the database.
        final UUID schemaId = factory.getSchemaId();
        final Map<DatabaseIdentifier, Pair<ObjectStatImpl, Integer>> stats = mServiceProvider.loadStats(schemaId);

        // Step 3: update the scores.
        List<RepositoryObject> result = new ArrayList<>();
        /* Map<ObjectImpl, Double> objectScales = new HashMap<>();
        Set<ObjectImpl> objects = objectScales.keySet();
        for (Map.Entry<ObjectImpl, Double> objectScale : objectScales.entrySet()) {
            ObjectImpl object1 = objectScale.getKey();

            if (updateScore && mWeights.get(schemaId) != null) {
                // Step 2: compute the scale to apply to this object (product of all scales excluding the current object).
                double newScale = objectScale.getValue();
                for (ObjectImpl object2 : objects) {
                    if (object1 != object2) {
                        Double scale2 = objectScales.get(object2);
                        if (scale2 != null) {
                            newScale = newScale * scale2;
                        }
                    }
                }

                // Step 3: update the score with the scale for the object and save the results.
                ObjectStatImpl stat = object1.getStats();
                if (stat.updateScore(newScale)) {
                    mServiceProvider.updateObjectStats(object1.getId(), stat);
                }
            }

            // result.add(new ObjectImpl.ToObject(object1));
        }*/

        // Now take into account stats we have queued.
        flushQueuedStats();

        complete.onGet(ErrorCode.SUCCESS, result);
    }

    @Override
    @Nullable
    public StatReport reportStats(@NonNull UUID schemaId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "reportStats schemaId=" + schemaId);
        }

        if (!isServiceOn()) {

            return null;
        }

        // Step 1: get the object stats from the database.
        final Map<DatabaseIdentifier, Pair<ObjectStatImpl, Integer>> stats = mServiceProvider.loadStats(schemaId);

        // Step 2: freeze the stats and collect the objects that have stats since the last report.
        final List<ObjectStatReport> reports = new ArrayList<>();

        long certifiedCount = 0;
        long invitationCodeCount = 0;
        synchronized (this) {
            // Freeze the stats and queue them.
            if (mStatQueue != null) {
                mStatQueue = new ArrayList<>();
            }
            for (Map.Entry<DatabaseIdentifier, Pair<ObjectStatImpl, Integer>> item : stats.entrySet()) {
                final DatabaseIdentifier objectId = item.getKey();
                final ObjectStatImpl cachedStat = mObjectStats.get(objectId);
                final ObjectStatImpl stat = cachedStat == null ? item.getValue().first : cachedStat;
                final int peerTwincodeFlags = item.getValue().second;
                if (stat.needReport()) {
                    reports.add(new ObjectStatReport(objectId, stat.getDiff()));

                    // The stat is reported but it was not part of the cache,
                    // add it so that the checkpointStats() will clear the counters correctly.
                    if (cachedStat == null) {
                        mObjectStats.put(objectId, stat);
                    }
                }
                if ((peerTwincodeFlags & TwincodeOutboundImpl.FLAG_CERTIFIED) != 0) {
                    certifiedCount++;
                } else if (TwincodeOutboundImpl.toTrustMethod(peerTwincodeFlags) == TrustMethod.INVITATION_CODE) {
                    invitationCodeCount++;
                }
            }
        }

        return new StatReport(reports, stats.size() - certifiedCount - invitationCodeCount, certifiedCount, invitationCodeCount);
    }

    @Override
    public void checkpointStats() {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkpointStats");
        }

        if (!isServiceOn()) {

            return;
        }

        // Step 1: make a new reference for every object and collect the objects that have new references.
        final List<ObjectStatImpl> updateStats = new ArrayList<>();
        synchronized (this) {
            for (Map.Entry<DatabaseIdentifier, ObjectStatImpl> item : mObjectStats.entrySet()) {
                final ObjectStatImpl stat = item.getValue();
                if (stat.checkpoint()) {
                    updateStats.add(stat);
                }
            }
        }

        // Step 2: update the objects that have new stat references.
        mServiceProvider.updateStats(updateStats);

        // Now take into account stats we have queued.
        flushQueuedStats();
    }

    @Override
    public void setWeightTable(@NonNull UUID schemaId, @NonNull Weight[] weights) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setWeightTable: schemaId=" + schemaId);
        }

        if (BuildConfig.ENABLE_CHECKS && StatType.values().length != weights.length) {
            throw new IllegalArgumentException("Invalid arguments to setWeightTable");
        }
        synchronized (this) {
            mWeights.put(schemaId, weights);
        }
    }

    @Nullable
    List<AttributeNameValue> deserializeContent(@NonNull String content) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deserializeContent: content=" + content);
        }

        ArrayData arrayData = new ArrayData();
        synchronized (mParser) {
            try {
                mParser.setInput(new StringReader(content));
                arrayData.parse(mParser);
            } catch (XmlPullParserException | SerializerException ignored) {
                return null;
            }
        }

        List<Data> values = arrayData.getValues();
        if (values.size() == 1) {
            Data value = values.get(0);

            return value.isArrayData() ? ((ArrayData) value).getData() : new ArrayList<>();
        }

        return new ArrayList<>();
    }

    //
    // Private Methods
    //

    void notifyInvalid(@NonNull RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "notifyInvalid: object=" + object);
        }

        for (RepositoryService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onInvalidObject(object));
        }
    }

    private void flushQueuedStats() {
        if (DEBUG) {
            Log.d(LOG_TAG, "flushQueuedStats");
        }

        List<StatQueued> queue;
        synchronized (this) {
            queue = mStatQueue;
            mStatQueue = null;
        }
        if (queue != null) {
            for (StatQueued item : queue) {
                if (item.value >= 0) {
                    updateStat(item.object, item.kind, item.value);
                } else {
                    incrementStat(item.object, item.kind);
                }
            }
        }
    }

    protected void onDeleteObject(@NonNull RepositoryObject object, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteObject object=" + object);
        }

        final UUID id = object.getId();

        mServiceProvider.deleteObject(object);
        complete.onGet(ErrorCode.SUCCESS, id);

        for (RepositoryService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onDeleteObject(id));
        }
    }

    protected void onUpdateObject(@NonNull RepositoryObject object, long modificationDate,
                                @NonNull Consumer<RepositoryObject> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject object=" + object + " modificationDate=" + modificationDate);
        }

        mServiceProvider.updateObject(object, modificationDate);

        complete.onGet(ErrorCode.SUCCESS, object);
        for (RepositoryService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateObject(object));
        }
    }
}
