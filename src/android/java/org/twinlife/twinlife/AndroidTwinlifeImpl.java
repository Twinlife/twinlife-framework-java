/*
 *  Copyright (c) 2012-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Tengfei Wang (Tengfei.Wang@twinlife-systems.com)
 *   Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *   Xiaobo Xie (Xiaobo.Xie@twinlife-systems.com)
 *   Chedi Baccari (Chedi.Baccari@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.database.Cursor;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.libwebsockets.ErrorCategory;
import org.twinlife.twinlife.util.StringUtils;
import org.twinlife.twinlife.connectivity.ConnectivityServiceImpl;
import org.twinlife.twinlife.job.AndroidJobServiceImpl;
import org.twinlife.twinlife.peerconnection.PeerConnectionServiceImpl;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utils;
import org.webrtc.PeerConnectionFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.SecureRandom;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import net.sqlcipher.DatabaseErrorHandler;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteDatabase.CursorFactory;
import net.sqlcipher.database.SQLiteDatabaseHook;
import net.sqlcipher.database.SQLiteException;
import net.sqlcipher.database.SQLiteOpenHelper;

/**
 * Android specific Twinlife implementation.
 */
public class AndroidTwinlifeImpl extends TwinlifeImpl implements Runnable {
    private static final String LOG_TAG = "AndroidTwinlifeImpl";
    private static final boolean INFO = false;
    private static final boolean DEBUG = false;

    private static final String RESOURCE = "android";

    private static final int MIN_DISCONNECTED_TIMEOUT = 16000; // ms
    private static final int MAX_DISCONNECTED_TIMEOUT = 512000; // ms

    private static final int SERVER_SECURED_PORT = 443;

    private static final class Cipher5Hook implements SQLiteDatabaseHook {
        /**
         * Called immediately before opening the database.
         */
        @Override
        public void preKey(@NonNull SQLiteDatabase database) {
            database.execSQL("PRAGMA cipher_plaintext_header_size = 32");
        }

        /**
         * Called immediately after opening the database.
         */
        @Override
        public void postKey(@NonNull SQLiteDatabase database) {
        }
    }

    private static final class Cipher3Hook implements SQLiteDatabaseHook {
        /**
         * Called immediately before opening the database.
         */
        @Override
        public void preKey(SQLiteDatabase database) {
        }

        /**
         * Called immediately after opening the database.
         */
        @Override
        public void postKey(SQLiteDatabase database) {
            database.execSQL("PRAGMA cipher_compatibility = 3;");
        }
    }

    private class TwinlifeDatabaseErrorHandler implements DatabaseErrorHandler {

        @Override
        public void onCorruption(@NonNull SQLiteDatabase dbObj) {
            Log.e(LOG_TAG, "Corruption reported by sqlite on database: " + dbObj.getPath());

            // Don't erase the database file.  The application fatal error will be displayed.
            error(BaseService.ErrorCode.DATABASE_CORRUPTION, "Corruption reported by SQLCipher");
        }
    }

    private class TwinlifeSQLiteOpenHelper extends SQLiteOpenHelper {

        @SuppressWarnings("SameParameterValue")
        TwinlifeSQLiteOpenHelper(Context context, String name, CursorFactory factory, int version,
                                 SQLiteDatabaseHook databaseHook, DatabaseErrorHandler errorHandler) {
            super(context, name, factory, version, databaseHook, errorHandler);

            if (DEBUG) {
                Log.d(LOG_TAG, "TwinlifeSQLiteOpenHelper context=" + context + " name=" + name + " factory=" + factory + " version=" + version);
            }
        }

        @Override
        public void onCreate(SQLiteDatabase database) {
            if (DEBUG) {
                Log.d(LOG_TAG, "onCreate database=" + database);
            }

            final AndroidDatabase db = new AndroidDatabase(database);
            AndroidTwinlifeImpl.this.onCreate(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
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

            // Try to free up some disk space: cleanup some temporary files.
            File dir = AndroidTwinlifeImpl.this.getFilesDir();
            if (dir != null) {
                dir = new File(dir, "images");
                if (dir.exists()) {
                    Utils.cleanupTemporaryDirectory(dir);
                }
            }
            final File cacheDir = AndroidTwinlifeImpl.this.getCacheDir();
            if (cacheDir != null) {
                // If there is less than 100Mb, aggressively delete the cache directory
                // which contains contact images and some temporary files.
                long dbSpaceAvailable = Utils.getDiskSpace(new File(database.getPath()));
                if (dbSpaceAvailable < 100 * 1024 * 1024) {
                    Utils.deleteDirectory(cacheDir);
                } else {
                    Utils.cleanupTemporaryDirectory(cacheDir);
                }
            }

            mDatabaseUpgraded = true;
            final AndroidDatabase db = new AndroidDatabase(database);

            // Leave the current transaction to let the database service onUpgrade()
            // handle several commits at different steps of the upgrade so that we can
            // restart and still do some upgrade progression.
            boolean inTransaction = database.inTransaction();
            if (inTransaction) {
                try {
                    database.endTransaction();
                } catch (Exception exception) {
                    Log.e(LOG_TAG, "Exception", exception);
                }
            }
            try {
                AndroidTwinlifeImpl.this.onUpgrade(db, oldVersion, newVersion);
            } catch (DatabaseException dbException) {
                throw new RuntimeException(dbException);
            } finally {
                // Restore the transaction for the SQL opener to handle the final
                // version update and final transaction commit.
                if (inTransaction) {
                    database.beginTransaction();
                }
            }
        }

        @Override
        public void onOpen(SQLiteDatabase database) {
            if (DEBUG) {
                Log.d(LOG_TAG, "onOpen database=" + database);
            }

            final AndroidDatabase db = new AndroidDatabase(database);
            AndroidTwinlifeImpl.this.onOpen(db);
        }
    }

    //
    // Database fields
    //

    private final ConfigurationService mConfigurationService;
    private volatile static boolean sPeerConnectionFactoryInitialized = false;
    @SuppressLint("StaticFieldLeak")
    private volatile static TwinlifeImpl sInstance;
    private final ImageTools mImageTools = new AndroidImageTools();
    private final File mFilesDir;
    private final SecureRandom mSecureRandom = new SecureRandom();
    private long mReconnectionTime;
    private long mDisconnectedTimeout;

    //
    // Database fields
    //

    private final Object mTwinlifeSQLiteLock = new Object();
    private TwinlifeSQLiteOpenHelper mTwinlifeSQLiteOpenHelper;

    public AndroidTwinlifeImpl(@NonNull TwinlifeContextImpl twinlifeContext, @NonNull Context context) {
        super(context, twinlifeContext.mTwinlifeExecutor);

        if (DEBUG) {
            Log.d(LOG_TAG, "AndroidTwinlifeImpl");
        }

        sInstance = this;
        mConfigurationService = twinlifeContext.getConfigurationService();

        // Get the external files first and cache it (see Android StrictMode).
        if (Environment.isExternalStorageEmulated()) {
            mFilesDir = context.getFilesDir();
        } else {
            mFilesDir = context.getExternalFilesDir(null);
        }
    }

    public static boolean isStarted() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isStarted");
        }

        return sInstance != null;
    }

    public void onCreate() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate");
        }

        super.onCreate();

        if (!sPeerConnectionFactoryInitialized) {
            sPeerConnectionFactoryInitialized = true;

            // Load and setup the WebRTC library from the Twinlife thread to avoid blocking the main thread.
            mTwinlifeExecutor.execute(() -> {
                try {
                    PeerConnectionFactory.InitializationOptions.Builder builder = PeerConnectionFactory.InitializationOptions.builder(mContext);
                    builder.setEnableInternalTracer(false);
                    PeerConnectionFactory.initialize(builder.createInitializationOptions());

                    mWebRtcReady = true;

                } catch (Throwable ex) {

                    Log.e(LOG_TAG, "WebRTC initialisation failed: " + ex.getMessage());
                }
            });
        }

        mRunning = true;
    }

    public void onDestroy() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDestroy");
        }

        super.onDestroy();

        sInstance = null;
    }

    @Override
    public void connect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "connect");
        }

        if (isConfigured() && !isConnected()) {
            mReconnectionTime = 0;
            mDisconnectedTimeout = 0;
            getConnectivityService().signalAll();
            mWebSocketConnection.wakeupWorker();
        }
    }

    @Override
    @NonNull
    public ConfigurationService getConfigurationService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfigurationService");
        }

        return mConfigurationService;
    }

    @Override
    @NonNull
    public JobService getJobService() {

        return AndroidJobServiceImpl.getInstance();
    }

    @Override
    @Nullable
    public PackageInfo getPackageInfo() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPackageInfo");
        }

        PackageInfo packageInfo = new PackageInfo();

        try {
            android.content.pm.PackageInfo info;

            info = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0);
            packageInfo.installerName = mContext.getPackageManager().getInstallerPackageName(mContext.getPackageName());
            if (packageInfo.installerName == null) {
                packageInfo.installerName = "";
            }
            packageInfo.packageName = info.packageName;
            packageInfo.versionName = info.versionName;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.versionCode = String.valueOf(info.getLongVersionCode());
            } else {
                packageInfo.versionCode = String.valueOf(info.versionCode);
            }
        } catch (PackageManager.NameNotFoundException exception) {
            Log.e(LOG_TAG, "getPackageInfo exception=" + exception);

            return null;
        }

        return packageInfo;
    }

    @Override
    @NonNull
    public ContentResolver getContentResolver() {

        return mContext.getContentResolver();
    }

    @Override
    @NonNull
    public File getFilesDir() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFilesDir");
        }

        return mFilesDir;
    }

    @Override
    @Nullable
    public File getCacheDir() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCacheDir");
        }

        return mContext.getCacheDir();
    }

    @NonNull
    @Override
    public String getResource() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getResource");
        }

        return RESOURCE + getDeviceIdentifier();
    }

    //
    // Protected Methods
    //

    @Override
    protected final ConnectivityService createConnectivityService(Connection webSocketConnection, ConnectivityService.ConnectivityServiceConfiguration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createConnectivityService");
        }

        ConnectivityServiceImpl connectivityServiceImpl = new ConnectivityServiceImpl(this, mContext, webSocketConnection);
        mBaseServiceImpls.add(connectivityServiceImpl);
        connectivityServiceImpl.configure(configuration);
        mTwinlifeConfiguration.connectivityServiceConfiguration = (ConnectivityService.ConnectivityServiceConfiguration) connectivityServiceImpl.getServiceConfiguration();

        return connectivityServiceImpl;
    }

    @Override
    protected PeerConnectionService createPeerConnectionService(Connection webSocketConnection, PeerConnectionService.PeerConnectionServiceConfiguration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createPeerConnectionService");
        }

        PeerConnectionServiceImpl peerConnectionServiceImpl = new PeerConnectionServiceImpl(this, webSocketConnection);
        mBaseServiceImpls.add(peerConnectionServiceImpl);
        peerConnectionServiceImpl.configure(configuration);
        mTwinlifeConfiguration.peerConnectionServiceConfiguration = (PeerConnectionService.PeerConnectionServiceConfiguration) peerConnectionServiceImpl.getServiceConfiguration();

        return peerConnectionServiceImpl;
    }

    @SuppressLint("PackageManagerGetSignatures")
    @Nullable
    @Override
    protected String getFingerprint(@NonNull String certificateSerialNumber) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFingerprint");
        }

        final Signature[] signatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.content.pm.PackageInfo packageInfo;
            try {
                packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
            } catch (PackageManager.NameNotFoundException exception) {
                Log.e(LOG_TAG, "getFingerprint exception=" + exception);

                return null;
            }

            SigningInfo signingInfo = packageInfo.signingInfo;
            if (signingInfo == null) {

                return null;
            }
            signatures = signingInfo.getApkContentsSigners();

        } else {
            android.content.pm.PackageInfo packageInfo;

            try {
                packageInfo = mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), PackageManager.GET_SIGNATURES);
            } catch (PackageManager.NameNotFoundException exception) {
                Log.e(LOG_TAG, "getFingerprint exception=" + exception);

                return null;
            }
            signatures = packageInfo.signatures;
        }

        if (signatures != null) {
            final String[] expectSerial = certificateSerialNumber.split(",");
            for (Signature signature : signatures) {
                byte[] cert = signature.toByteArray();
                InputStream input = new ByteArrayInputStream(cert);
                try {
                    CertificateFactory certificateFactory = CertificateFactory.getInstance("X509");
                    X509Certificate certificate = (X509Certificate) certificateFactory.generateCertificate(input);
                    String serialNumber = certificate.getSerialNumber().toString(16);
                    for (String checkSerial : expectSerial) {
                        if (checkSerial.equals(serialNumber) || !BuildConfig.ENABLE_SIGNATURE_CHECK) {
                            try {
                                MessageDigest messageDigest = MessageDigest.getInstance("SHA1");
                                byte[] key = messageDigest.digest(certificate.getEncoded());

                                return StringUtils.encodeHex(key);
                            } catch (Exception exception) {
                                Log.e(LOG_TAG, "getFingerprint exception=" + exception);

                                return null;
                            }
                        }
                    }
                } catch (Exception exception) {
                    Log.e(LOG_TAG, "getFingerprint exception=" + exception);
                }
            }
        }
        return null;
    }

    @Override
    public void onDisconnect(@NonNull ErrorCategory error) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect error=" + error);
        }

        // Reconnect after a delay that depends on the error we got.
        // A random delay is added to make sure devices will not reconnect at the same time
        // in case the error is triggered by the server.
        long timeout;
        switch (error) {
            case ERR_NONE:
                // Connection was closed by us or by the server.
                timeout = 500 + mSecureRandom.nextInt(8000);
                break;

            case ERR_DNS:
            case ERR_IO:
            case ERR_TIMEOUT:
                // For transient error, we can retry more aggressively.
                timeout = 2000 + mSecureRandom.nextInt(2000);
                break;

            case ERR_INVALID_CA:
            case ERR_TLS_HOSTNAME:
                // Trying to connect to a wrong server, no need to retry very often.
                timeout = 60 * 1000 + mSecureRandom.nextInt(60 * 1000);
                break;

            default:
                timeout = 10 * 1000 + mSecureRandom.nextInt(10 * 1000);
                break;
        }

        if (error == ErrorCategory.ERR_NONE) {
            Log.i(LOG_TAG, "Connection closed retrying in " + timeout + " ms");
        } else {
            Log.e(LOG_TAG, "Connection failed " + error + " retry in " + timeout + " ms");
        }
        mReconnectionTime = System.currentTimeMillis() + timeout;

        mWebSocketConnection.wakeupWorker();
        super.onDisconnect(error);
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

    @Override
    public void run() {
        if (DEBUG) {
            Log.d(LOG_TAG, "run");
        }

        if (!isConfigured()) {
            if (DEBUG) {
                Log.d(LOG_TAG, "run not configured");
            }

            return;
        }

        final AndroidJobServiceImpl jobService = AndroidJobServiceImpl.getInstance();
        final Connection webSocketConnection = mWebSocketConnection;
        final ConnectivityService connectivityService = getConnectivityService();
        while (mRunning) {
            if (INFO) {
                Log.i(LOG_TAG, "wait for connected network " + mDisconnectedTimeout);
            }

            boolean hasNetwork = connectivityService.waitForConnectedNetwork(mDisconnectedTimeout);
            if (!hasNetwork) {
                if (INFO) {
                    Log.i(LOG_TAG, "network not connected");
                }

                if (!jobService.isIdle()) {
                    // The Android ConnectivityManager is not always reliable and we have seen cases where it does not inform
                    // us about the network connectivity.  If we are not idle (app in foreground or we received a Firebase push),
                    // be more pro-active in testing the network connectivity.  The delay will start at 0 and we increase it
                    // by 250ms min until we reach 10s and then we start again from 1s.
                    // 0, 250, 625, 1187, 2030, 3305, 5207, 8060
                    mDisconnectedTimeout += 250 + mDisconnectedTimeout / 2;
                    if (mDisconnectedTimeout > 10000) {
                        mDisconnectedTimeout = 1000;
                    }
                } else {
                    // We are in background and there is no work to do.
                    jobService.scheduleAlarm();

                    mDisconnectedTimeout = 2 * mDisconnectedTimeout + MIN_DISCONNECTED_TIMEOUT;
                    if (mDisconnectedTimeout > MAX_DISCONNECTED_TIMEOUT) {
                        mDisconnectedTimeout = MAX_DISCONNECTED_TIMEOUT;
                    }
                }
                // In any case, give 100ms to the libwebsocket to handle possible events.
                webSocketConnection.service(100);

            } else {
                ConnectionStatus connectionStatus = webSocketConnection.getConnectionStatus();
                do {
                    // Decide how much time we have to wait in the libwebsocket service() loop.
                    long timeout;
                    if (jobService.isIdle()) {
                        if (connectionStatus == ConnectionStatus.NO_SERVICE) {
                            jobService.scheduleAlarm();
                        }
                        timeout = 60 * 1000;
                    } else if (connectionStatus == ConnectionStatus.NO_SERVICE) {
                        final long now = System.currentTimeMillis();
                        timeout = mReconnectionTime - now;
                        if (timeout <= 0) {
                            mReconnectionTime = now + 20000;
                            webSocketConnection.connect();
                            timeout = 10000;
                        }
                    } else {
                        timeout = 10000;
                    }
                    webSocketConnection.service((int) timeout);

                    // If we are now connected, try to remain in this service() loop because we know
                    // the network is alive.  If we are not connected, leave to give a chance to test the
                    // network connectivity.
                    connectionStatus = webSocketConnection.getConnectionStatus();
                } while (connectionStatus == ConnectionStatus.CONNECTED);
            }
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
    protected Connection getConnection(@NonNull TwinlifeConfiguration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConnection configuration=" + configuration);
        }

        WebSocketConnectionConfiguration webSocketConfiguration
                = new WebSocketConnectionConfiguration(true, ServerConfig.URL, SERVER_SECURED_PORT, ServerConfig.DOMAIN,
                "/twinlife/server");
        return new WebSocketConnection(this, webSocketConfiguration, configuration);
    }

    @Override
    protected void openDatabase() throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "openDatabase");
        }

        synchronized (mTwinlifeSQLiteLock) {
            if (mTwinlifeSQLiteOpenHelper != null) {

                return;
            }

            File databaseFile = mContext.getDatabasePath(CIPHER_V4_DATABASE_NAME);

            final int cipherVersion;
            if (databaseFile.exists()) {
                cipherVersion = 4;
            } else {
                // Check if an old database exists and try to migrate into an SQLcipher database.
                // If the migration fails (file system is full), continue using the old database.
                File oldCipher = mContext.getDatabasePath(CIPHER_V3_DATABASE_NAME);
                if (oldCipher.exists()) {
                    if (tryMigrateCipher3Database(oldCipher)) {
                        cipherVersion = 4;
                    } else {
                        databaseFile = oldCipher;
                        cipherVersion = 3;
                    }
                } else {
                    File oldFile = mContext.getDatabasePath(DATABASE_NAME);

                    if (oldFile.exists() && !tryMigrateDatabase(oldFile)) {
                        databaseFile = oldFile;
                        cipherVersion = 0;
                    } else {
                        cipherVersion = 4;
                    }
                }
            }

            if (mTwinlifeSecuredConfiguration.createdKey && databaseFile.exists()) {
                Log.e(LOG_TAG, "openDatabase: a previous database exists but a new key was generated");
                Utils.deleteFile(LOG_TAG, databaseFile);
            }

            final String name;
            final String key;
            final SQLiteDatabaseHook hook;
            switch (cipherVersion) {
                case 4:
                    name = CIPHER_V4_DATABASE_NAME;
                    // If the key is 96 bytes, this it contains the encryption key followed by the cipher salt
                    // and we must setup cipher_plaintext_header_size to 32 bytes.  The database was created on iOS.
                    if (mTwinlifeSecuredConfiguration.databaseKey.length() == 96) {
                        hook = new Cipher5Hook();
                        key = "x'" + mTwinlifeSecuredConfiguration.databaseKey + "'";
                    } else {
                        key = mTwinlifeSecuredConfiguration.databaseKey;
                        hook = null;
                    }
                    break;

                case 3:
                    name = CIPHER_V3_DATABASE_NAME;
                    key = mTwinlifeSecuredConfiguration.databaseKey;
                    hook = new Cipher3Hook();
                    break;

                default:
                    name = DATABASE_NAME;
                    key = null;
                    hook = null;
                    break;
            }

            if (BuildConfig.ENABLE_DUMP) {
                Log.e(LOG_TAG, "Twinme database key=" + key);
            }
            try {
                mTwinlifeSQLiteOpenHelper = new TwinlifeSQLiteOpenHelper(mContext, name, null, DATABASE_VERSION, hook, new TwinlifeDatabaseErrorHandler());
                mTwinlifeSQLiteOpenHelper.getWritableDatabase(key);

            } catch (SQLiteException fullException) {
                AndroidDatabase.raiseException(fullException);

            } catch (RuntimeException exception) {
                if (exception.getCause() instanceof DatabaseException) {
                    throw (DatabaseException) exception.getCause();
                }

            } catch (Exception exception) {
                Log.e(LOG_TAG, "Database exception", exception);
            }
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

    private boolean tryMigrateDatabase(@NonNull File oldDatabaseFile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "tryMigrateDatabase oldDatabaseFile=" + oldDatabaseFile);
        }

        mDatabaseUpgraded = true;

        File newDatabaseFile = mContext.getDatabasePath(CIPHER_V4_DATABASE_NAME);
        SQLiteDatabase database = null;
        try {
            Log.w(LOG_TAG, "Migrating SQLite database to SQLcipher");

            database = SQLiteDatabase.openOrCreateDatabase(oldDatabaseFile, null, null, null, null);
            database.execSQL("ATTACH DATABASE ? AS 'encrypted' KEY ?;", new String[]{
                    newDatabaseFile.getAbsolutePath(),
                    mTwinlifeSecuredConfiguration.databaseKey
            });
            database.rawExecSQL("SELECT sqlcipher_export('encrypted');");

            // Keep the database version so that we handle a possible upgrade.
            database.execSQL("PRAGMA encrypted.user_version = " + database.getVersion());
            database.execSQL("DETACH DATABASE 'encrypted'");
            database.close();
            database = null;

            if (oldDatabaseFile.delete()) {
                Log.w(LOG_TAG, "Database migrated to SQLcipher");
            }
            return true;

        } catch (Exception exception) {
            if (database != null && database.isOpen()) {
                database.close();
            }
            Utils.deleteFile(LOG_TAG, newDatabaseFile);
            return false;
        }
    }

    private boolean tryMigrateCipher3Database(@NonNull File oldDatabaseFile) {
        if (DEBUG) {
            Log.d(LOG_TAG, "tryMigrateCipher3Database oldDatabaseFile=" + oldDatabaseFile);
        }

        mDatabaseUpgraded = true;

        File newDatabaseFile = mContext.getDatabasePath(CIPHER_V4_DATABASE_NAME);
        SQLiteDatabase database = null;
        try {
            Log.w(LOG_TAG, "Migrating SQLcipher v3 database to SQLcipher v4");

            database = SQLiteDatabase.openOrCreateDatabase(oldDatabaseFile, mTwinlifeSecuredConfiguration.databaseKey, null, new Cipher3Hook(), null);

            database.execSQL("ATTACH DATABASE ? AS 'encrypted' KEY ?;", new String[]{
                    newDatabaseFile.getAbsolutePath(),
                    mTwinlifeSecuredConfiguration.databaseKey
            });
            database.rawExecSQL("SELECT sqlcipher_export('encrypted');");

            // Keep the database version so that we handle a possible upgrade.
            database.execSQL("PRAGMA encrypted.user_version = " + database.getVersion());
            database.execSQL("DETACH DATABASE 'encrypted'");
            database.close();
            database = null;
            if (oldDatabaseFile.delete()) {
                Log.w(LOG_TAG, "Database migrated to SQLcipher");
            }
            return true;

        } catch (Exception exception) {
            if (database != null && database.isOpen()) {
                database.close();
            }
            Utils.deleteFile(LOG_TAG, newDatabaseFile);
            return false;
        }
    }
}
