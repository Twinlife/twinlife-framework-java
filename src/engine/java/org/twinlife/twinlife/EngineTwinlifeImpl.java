/*
 *  Copyright (c) 2019-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.content.ContentResolver;

import android.content.Context;
import android.database.Cursor;
import androidx.annotation.NonNull;
import android.util.Log;
import android.database.sqlite.SQLiteOpenHelper;

import org.twinlife.twinlife.connectivity.EngineConnectivityServiceImpl;
import org.twinlife.twinlife.peerconnection.PeerConnectionServiceImpl;
import org.twinlife.twinlife.util.Logger;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;

import java.io.File;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Engine twinlife implementation.
 */
public class EngineTwinlifeImpl extends TwinlifeImpl implements Runnable {
    private static final String LOG_TAG = "EngineTwinlifeImpl";
    private static final boolean INFO = false;
    private static final boolean DEBUG = false;
    private static final int SERVER_SECURED_PORT = 443;
    private static final String SERVER_NAME = Twinlife.DOMAIN;
    private static final String SERVER_URL = BuildConfig.SERVER_URL;

    private static final String RESOURCE = "engine";
    private static final int MIN_CONNECTED_TIMEOUT = 64; // s
    private static final int MAX_CONNECTED_TIMEOUT = 1024; // s
    private static final int NO_RECONNECTION_TIMEOUT = 0; // ms
    private static final int MIN_RECONNECTION_TIMEOUT = 1000; // ms
    private static final int MAX_RECONNECTION_TIMEOUT = 8000; // ms

    @NonNull
    private final File mFilesDir;
    @NonNull
    private final File mCacheDir;

    private final ConfigurationService mConfigurationService;
    private volatile static boolean sPeerConnectionFactoryInitialized = false;

    private final ReentrantLock mConnectedLock = new ReentrantLock();
    private final Condition mConnectedCondition = mConnectedLock.newCondition();
    private volatile int mConnectedTimeout = MIN_CONNECTED_TIMEOUT;
    private final ReentrantLock mReconnectionLock = new ReentrantLock();
    private final Condition mReconnectionCondition = mReconnectionLock.newCondition();
    private volatile int mReconnectionTimeout = NO_RECONNECTION_TIMEOUT;
    @NonNull
    private final ImageTools mImageTools;
    private final JobService mJobServiceImpl;
    //
    // Database fields
    //

    private final Object mTwinlifeSQLiteLock = new Object();
    private TwinlifeSQLiteOpenHelper mTwinlifeSQLiteOpenHelper;

    private class TwinlifeSQLiteOpenHelper extends SQLiteOpenHelper {

        @SuppressWarnings("SameParameterValue")
        TwinlifeSQLiteOpenHelper(Context context, String name,
                                 android.database.sqlite.SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version);

            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeSQLiteOpenHelper context=" + context + " name=" + name + " factory=" + factory + " version=" + version);
            }
        }

        @Override
        public void onCreate(android.database.sqlite.SQLiteDatabase database) {
            if (DEBUG) {
                Log.d(LOG_TAG, "onCreate database=" + database);
            }

            final SQLiteDatabase db = new SQLiteDatabase(database);
            for (BaseServiceImpl baseService : mBaseServiceImpls) {
                if (baseService.isServiceOn()) {
                    baseService.onCreateDatabase(db);
                }
            }
            if (mTwinlifeConfiguration.databaseObservers != null) {
                for (DatabaseObserver observer : mTwinlifeConfiguration.databaseObservers) {
                    observer.onCreate(db);
                }
            }
        }

        @Override
        public void onUpgrade(android.database.sqlite.SQLiteDatabase database, int oldVersion, int newVersion) {
            if (DEBUG) {
                Log.d(LOG_TAG, "onUpgrade database=" + database + " oldVersion=" + oldVersion + " newVersion=" + newVersion);
            }

            // When we switched to SQLcipher the database version was lost in a first implementation.
            // The statement 'database.execSQL("PRAGMA encrypted.user_version = " + database.getVersion());' was missing.
            // The SQLcipher database was setup with version 13 but it was missing some upgrade steps.
            if (oldVersion == 13) {
                int checkVersion = 7;
                try {
                    // Check if we have the schemaId column in the repositoryObject table (if exception => version 7, 2017/04/10).
                    try (Cursor cursor = database.rawQuery("SELECT schemaId FROM repositoryObject LIMIT 1", null)) {

                    }

                    // Check if we have the cid column (if exception => version 8, 2018/11/26).
                    checkVersion = 8;
                    try (Cursor cursor = database.rawQuery("SELECT cid FROM conversationConversation LIMIT 1", null)) {

                    }

                    // Check if we have the refreshPeriod column (if exception => version 9).
                    checkVersion = 9;
                    try (Cursor cursor = database.rawQuery("SELECT refreshPeriod FROM twincodeOutboundTwincodeOutbound LIMIT 1", null)) {

                    }

                } catch (Exception exception) {
                    oldVersion = checkVersion;
                }
            }

            mDatabaseUpgraded = true;
            final SQLiteDatabase db = new SQLiteDatabase(database);
            for (BaseServiceImpl baseService : mBaseServiceImpls) {
                if (baseService.isServiceOn()) {
                    baseService.onUpgradeDatabase(db, oldVersion, newVersion);
                }
            }
            if (mTwinlifeConfiguration.databaseObservers != null) {
                for (DatabaseObserver observer : mTwinlifeConfiguration.databaseObservers) {
                    observer.onUpgrade(db, oldVersion, newVersion);
                }
            }
        }

        @Override
        public void onOpen(android.database.sqlite.SQLiteDatabase database) {
            if (DEBUG) {
                Log.d(LOG_TAG, "onOpen database=" + database);
            }

            final SQLiteDatabase db = new SQLiteDatabase(database);
            for (BaseServiceImpl baseService : mBaseServiceImpls) {
                if (baseService.isServiceOn()) {
                    baseService.onOpenDatabase(db);
                }
            }
            if (mTwinlifeConfiguration.databaseObservers != null) {
                for (DatabaseObserver observer : mTwinlifeConfiguration.databaseObservers) {
                    observer.onOpen(db);
                }
            }
        }
    }

    public EngineTwinlifeImpl(Context context,
                              @NonNull TwinlifeContextImpl twinlifeContext,
                              @NonNull File filesDir, @NonNull File cacheDir,
                              @NonNull ImageTools imageTools) {
        super(context, twinlifeContext.mTwinlifeExecutor);

        if (DEBUG) {
            Log.d(LOG_TAG, "EngineTwinlifeImpl");
        }

        mWebRtcReady = true;
        mConfigurationService = twinlifeContext.getConfigurationService();
        mFilesDir = filesDir;
        mCacheDir = cacheDir;
        mImageTools = imageTools;
        mJobServiceImpl = twinlifeContext.getJobService();
    }

    @Override
    @NonNull
    public JobService getJobService() {

        return mJobServiceImpl;
    }

    @Override
    @NonNull
    public ConfigurationService getConfigurationService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfigurationService");
        }

        return mConfigurationService;
    }

    //
    // Override Service methods
    //

    @Override
    @NonNull
    public File getFilesDir() {

        return mFilesDir;
    }

    @Override
    @NonNull
    public File getCacheDir() {

        return mCacheDir;
    }

    @NonNull
    @Override
    public String getResource() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getResource");
        }

        return RESOURCE + getDeviceIdentifier();
    }

    @Override
    @NonNull
    public PackageInfo getPackageInfo() {
        PackageInfo packageInfo = new PackageInfo();

        packageInfo.installerName = "twinme-installer";
        packageInfo.packageName = "twinme-engine";
        packageInfo.versionCode = BuildConfig.VERSION_NAME;
        packageInfo.versionName = BuildConfig.VERSION_NAME;
        return packageInfo;
    }

    public void onCreate() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate");
        }

        super.onCreate();

        if (!sPeerConnectionFactoryInitialized) {
            sPeerConnectionFactoryInitialized = true;

            try {
                PeerConnectionFactory.InitializationOptions.Builder builder = PeerConnectionFactory.InitializationOptions.builder(mContext);
                builder.setEnableInternalTracer(false);
                builder.setInjectableLogger((String message, Logging.Severity severity, String tag) -> {
                    switch (severity) {
                        case LS_VERBOSE:
                            Log.d(tag, message);
                            break;
                        case LS_INFO:
                            Log.i(tag, message);
                            break;
                        case LS_WARNING:
                            Log.w(tag, message);
                            break;
                        case LS_ERROR:
                            Log.e(tag, message);
                            break;
                    }
                }, Logging.Severity.LS_INFO);

                Logging.enableLogToDebugOutput(Logging.Severity.LS_NONE);
                PeerConnectionFactory.initialize(builder.createInitializationOptions());
                mWebRtcReady = true;

            } catch (Throwable ex) {

                Log.e(LOG_TAG, "WebRTC initialisation failed: " + ex.getMessage());
            }
        }

        mRunning = true;
    }

    @Override
    public void connect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "connect");
        }

        if (isConfigured() && !isConnected()) {
            mReconnectionTimeout = NO_RECONNECTION_TIMEOUT;
            mConnectedTimeout = MIN_CONNECTED_TIMEOUT;

            mConnectedLock.lock();
            mConnectedCondition.signalAll();
            mConnectedLock.unlock();

            mReconnectionLock.lock();
            mReconnectionCondition.signalAll();
            mReconnectionLock.unlock();
        }
    }

    //
    // Protected Methods
    //

    protected Connection getConnection() {
        WebSocketConnectionConfiguration webSocketConfiguration
                = new WebSocketConnectionConfiguration(true, SERVER_URL, SERVER_SECURED_PORT, SERVER_NAME,
                     "/twinlife/server");
        return new WebSocketConnection(this, webSocketConfiguration, BuildConfig.sProxyDescriptors);
    }

    @Override
    protected final ConnectivityService createConnectivityService(Connection webSocketConnection, ConnectivityService.ConnectivityServiceConfiguration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createConnectivityService");
        }

        EngineConnectivityServiceImpl connectivityServiceImpl = new EngineConnectivityServiceImpl(this, webSocketConnection);
        mBaseServiceImpls.add(connectivityServiceImpl);
        connectivityServiceImpl.configure(configuration);
        mTwinlifeConfiguration.connectivityServiceConfiguration = (ConnectivityService.ConnectivityServiceConfiguration) connectivityServiceImpl.getServiceConfiguration();

        return connectivityServiceImpl;
    }

    @Override
    protected final PeerConnectionService createPeerConnectionService(Connection webSocketConnection,
                                                                      PeerConnectionService.PeerConnectionServiceConfiguration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createPeerConnectionService");
        }

        PeerConnectionServiceImpl peerConnectionServiceImpl = new PeerConnectionServiceImpl(this, webSocketConnection);
        mBaseServiceImpls.add(peerConnectionServiceImpl);
        peerConnectionServiceImpl.configure(configuration);
        mTwinlifeConfiguration.peerConnectionServiceConfiguration = (PeerConnectionService.PeerConnectionServiceConfiguration)peerConnectionServiceImpl.getServiceConfiguration();

        return peerConnectionServiceImpl;
    }

    @Override
    protected String getFingerprint(String serial) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFingerprint");
        }

        return "engine-fingerprint";
    }

    @Override
    public void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        super.onDisconnect();

        mConnectedLock.lock();
        mConnectedCondition.signalAll();
        mConnectedLock.unlock();

        mReconnectionLock.lock();
        mReconnectionCondition.signalAll();
        mReconnectionLock.unlock();
    }

    @Override
    protected void start() {
        if (INFO) {
            Log.i(LOG_TAG, "start");
        }

        Thread twinlifeThread = new Thread(this);
        twinlifeThread.setName("twinlife-connect");
        twinlifeThread.start();
    }

    public void stop() {
        if (INFO) {
            Log.i(LOG_TAG, "stop");
        }

        super.stop();

        // mJobServiceImpl.destroy();
    }

    @Override
    public void run() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        if (!isConfigured()) {
            if (DEBUG) {
                Log.d(LOG_TAG, "run not configured");
            }

            return;
        }

        onCreate();

        mRunning = true;
        final Random random = new Random();
        while (mRunning) {
            if (INFO) {
                Log.i(LOG_TAG, "wait for connected network...");
            }

            if (!isConnected()) {
                if (INFO) {
                    Log.i(LOG_TAG, "connect...");
                }

                final int timeout = connectInternal();
                if (timeout == 0) {
                    if (INFO) {
                        Log.i(LOG_TAG, "connected");
                    }

                    mConnectedTimeout = MIN_CONNECTED_TIMEOUT;
                    mReconnectionTimeout = random.nextInt(MIN_RECONNECTION_TIMEOUT);
                } else {
                    mReconnectionTimeout = random.nextInt(MAX_RECONNECTION_TIMEOUT) + timeout;
                }
            }
            if (isConnected()) {
                if (INFO) {
                    Log.i(LOG_TAG, "still connected");
                }

                //noinspection EmptyCatchBlock
                try {
                    mConnectedLock.lock();
                    if (!mConnectedCondition.await(mConnectedTimeout, TimeUnit.SECONDS)) {
                        mConnectedTimeout *= 2;
                        if (mConnectedTimeout > MAX_CONNECTED_TIMEOUT) {
                            mConnectedTimeout = MAX_CONNECTED_TIMEOUT;
                        }
                    }
                } catch (InterruptedException exception) {
                } finally {
                    mConnectedLock.unlock();
                }
            } else {
                if (INFO) {
                    Log.i(LOG_TAG, "wait before reconnecting " + mReconnectionTimeout);
                }

                if (mReconnectionTimeout == NO_RECONNECTION_TIMEOUT) {
                    mReconnectionTimeout = MIN_RECONNECTION_TIMEOUT + random.nextInt(MAX_RECONNECTION_TIMEOUT);
                } else {
                    //noinspection EmptyCatchBlock
                    try {
                        mReconnectionLock.lock();
                        if (!mReconnectionCondition.await(mReconnectionTimeout, TimeUnit.MILLISECONDS)) {
                            mReconnectionTimeout = MIN_RECONNECTION_TIMEOUT + random.nextInt(MAX_RECONNECTION_TIMEOUT);
                        }
                    } catch (InterruptedException exception) {
                    } finally {
                        mReconnectionLock.unlock();
                    }
                }
            }
        }
    }

    @Override
    protected void alive() {
        if (DEBUG) {
            Log.d(LOG_TAG, "alive");
        }
    }

    @NonNull
    @Override
    public ImageTools getImageTools() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getImageTools");
        }

        return mImageTools;
    }

    @Override
    @NonNull
    public ContentResolver getContentResolver() {

        return new ContentResolver();
    }

    @Override
    @NonNull
    public BaseService.ErrorCode getDatabaseStatus() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDatabaseStatus");
        }

        BaseService.ErrorCode errorCode;
        synchronized (mTwinlifeSQLiteLock) {
            if (mTwinlifeSQLiteOpenHelper == null) {
                errorCode = BaseService.ErrorCode.SERVICE_UNAVAILABLE;
            } else {
                errorCode = mDatabaseError;
                mDatabaseError = BaseService.ErrorCode.SUCCESS;
            }
        }
        return errorCode;
    }

    @Override
    protected void openDatabase() {
        if (DEBUG) {
            Log.d(LOG_TAG, "openDatabase");
        }

        synchronized (mTwinlifeSQLiteLock) {
            if (mTwinlifeSQLiteOpenHelper != null) {

                return;
            }

            final String name = DATABASE_NAME;

            mTwinlifeSQLiteOpenHelper = new TwinlifeSQLiteOpenHelper(mContext, name, null, DATABASE_VERSION);
            mTwinlifeSQLiteOpenHelper.getWritableDatabase();
        }
    }

    @Override
    protected void removeDatabase() {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeDatabase");
        }
        try {
            synchronized (mTwinlifeSQLiteLock) {
                if (mTwinlifeSQLiteOpenHelper != null) {
                    mTwinlifeSQLiteOpenHelper.close();
                    mTwinlifeSQLiteOpenHelper = null;
                }
                mContext.deleteDatabase(CIPHER_V3_DATABASE_NAME);
                mContext.deleteDatabase(CIPHER_V4_DATABASE_NAME);
                mContext.deleteDatabase(DATABASE_NAME);
            }
        } catch (Exception ex) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "error", ex);
            }
        }
    }

    @Override
    protected void closeDatabase() {
        if (DEBUG) {
            Log.d(LOG_TAG, "closeDatabase");
        }

        try {
            synchronized (mTwinlifeSQLiteLock) {
                if (mTwinlifeSQLiteOpenHelper != null) {
                    mTwinlifeSQLiteOpenHelper.close();
                    mTwinlifeSQLiteOpenHelper = null;
                }
            }
        } catch (Exception ex) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "error", ex);
            }
        }
    }
}
