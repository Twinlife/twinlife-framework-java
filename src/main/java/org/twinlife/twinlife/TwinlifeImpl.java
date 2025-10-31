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

import android.content.ContentResolver;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.libwebsockets.ErrorCategory;
import org.twinlife.twinlife.AccountMigrationService.AccountMigrationServiceConfiguration;
import org.twinlife.twinlife.AccountService.AccountServiceConfiguration;
import org.twinlife.twinlife.BaseService.BaseServiceId;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.ConversationService.ConversationServiceConfiguration;
import org.twinlife.twinlife.ImageService.ImageServiceConfiguration;
import org.twinlife.twinlife.ManagementService.ManagementServiceConfiguration;
import org.twinlife.twinlife.NotificationService.NotificationServiceConfiguration;
import org.twinlife.twinlife.RepositoryService.RepositoryServiceConfiguration;
import org.twinlife.twinlife.TwincodeFactoryService.TwincodeFactoryServiceConfiguration;
import org.twinlife.twinlife.TwincodeInboundService.TwincodeInboundServiceConfiguration;
import org.twinlife.twinlife.TwincodeOutboundService.TwincodeOutboundServiceConfiguration;
import org.twinlife.twinlife.account.AccountServiceImpl;
import org.twinlife.twinlife.accountMigration.AccountMigrationServiceImpl;
import org.twinlife.twinlife.calls.PeerCallServiceImpl;
import org.twinlife.twinlife.conversation.ConversationServiceImpl;
import org.twinlife.twinlife.crypto.CryptoServiceImpl;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.image.ImageServiceImpl;
import org.twinlife.twinlife.management.ManagementServiceImpl;
import org.twinlife.twinlife.notification.NotificationServiceImpl;
import org.twinlife.twinlife.repository.RepositoryServiceImpl;
import org.twinlife.twinlife.twincode.factory.TwincodeFactoryServiceImpl;
import org.twinlife.twinlife.twincode.inbound.TwincodeInboundServiceImpl;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundServiceImpl;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.SerializerFactoryImpl;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;

public abstract class TwinlifeImpl implements Twinlife, ConnectionListener, BaseServiceProvider {
    private static final String LOG_TAG = "TwinlifeImpl";
    private static final boolean INFO = BuildConfig.ENABLE_INFO_LOG;
    private static final boolean DEBUG = false;

    // private static final int MAX_ADJUST_TIME = 3600 * 1000; // Absolute maximum wallclock time adjustment in ms made.

    public static final String DATABASE_NAME = "twinlife.db";
    public static final String CIPHER_V3_DATABASE_NAME = "twinlife.cipher";
    public static final String CIPHER_V4_DATABASE_NAME = "twinlife-4.cipher";

    /*
     * <pre>
     * Database Version 25
     *  Date: 2024/10/14
     *   Fix twincodeOutbound flags after introduction of beta support for SDPs encryption keys (internal version).
     *   Fix conversation table that could contain incorrect peerTwincodeOutbound for contact.
     *
     * Database Version 24
     *  Date: 2024/10/10
     *   Fix twincodeOutbound table which contains invalid `pair::twincodeOutboundId` due to a bug in TwincodeInboundService.
     *
     * Database Version 23
     *  Date: 2024/09/27
     *   New database model with twincodeKeys and secretKeys table
     *
     * Database Version 22
     *  Date: 2024/07/19
     *   Fix bad mapping between Android and iOS for TLImageStatusTypeOwner and TLImageStatusTypeLocale
     *   Note: the SQL migration code is only executed on iOS but the version must also be incremented on Android.
     *
     * Database Version 21
     *  Date: 2024/05/07
     *    Add columns creationDate and notificationId in the annotation table to record who annotates for the notification.
     *
     * Database Version 20
     *  Date: 2023/08/28
     *    New database schema optimized to allow loading repository objects and twincodes in a single SQL query.
     *
     * Database Version 17
     *  Date: 2022/12/07
     *   Repair the inconsistency in repositoryObject table in the key column that is sometimes null
     *
     * Database Version 16
     *  Date: 2022/02/25
     *
     *  ConversationService
     *   Update oldVersion [12]:
     *    Add table conversationDescriptorAnnotation
     *
     * Database Version 15
     *  Date: 2020/12/07
     *   No change but trigger a join group in the ConversationService for each group and to each group member.
     *
     * Database Version 14
     *  Date: 2020/11/23
     *   No change but migration to SQLcipher and we lost the pragma version during the migration.
     *   Force a call to onUpgrade() to recover from failed SQLcipher upgrade between V7 and V13.
     *
     * Database Version 13
     *  Date: 2020/07/31
     *   No change but fix migration code in ConversationServiceProvider and force execution of onUpgrade().
     *
     * Database Version 12
     *  Date: 2020/07/08
     *   No change but fix migration code in ConversationServiceProvider and force execution of onUpgrade().
     *
     * Database Version 11
     *  Date: 2020/06/17
     *   No change but fix migration code in ConversationServiceProvider and force execution of onUpgrade().
     *
     * Database Version 10
     *  Date: 2020/05/04
     *
     *  ImageService
     *   Create table twincodeImage
     *  TwincodeOutboundService
     *    Add column refreshPeriod INTEGER in  twincodeOutboundTwincodeOutbound
     *    Add column refreshDate INTEGER in  twincodeOutboundTwincodeOutbound
     *    Add column refreshTimestamp INTEGER in  twincodeOutboundTwincodeOutbound
     *
     * Database Version 9
     *  Date: 2020/02/07
     *
     *   ConversationService:
     *    Add column 'createdTimestamp INTEGER' in table conversationDescriptor
     *    Add column 'cid INTEGER' in table conversationDescriptor
     *    Add column 'descriptorType INTEGER' in table conversationDescriptor
     *    Add column 'cid INTEGER' in table conversationConversation
     *
     * Database Version 8
     *  Date: 2018/11/26
     *
     *  RepositoryService
     *   Update oldVersion [3,7]:
     *    Add column stats BLOB in  repositoryObject
     *    Add column schemaId TEXT in repositoryObject
     *   Update oldVersion [0,2]: reset
     *
     * Database Version 7
     *  Date: 2017/04/10
     *
     *  ConversationService
     *   Update oldVersion [6]: -
     *   Update oldVersion [5]
     *   Update oldVersion [0,4]: reset
     *  DirectoryService
     *   Update oldVersion [3,6]: -
     *   Update oldVersion [0,2]: reset
     *  NotificationService
     *  RepositoryService
     *   Update oldVersion [3,6]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeFactoryService
     *   Update oldVersion [3,6]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeInboundService
     *   Update oldVersion [3,6]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeOutboundService
     *   Update oldVersion [3,6]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeSwitchService
     *   Update oldVersion [3,6]: -
     *   Update oldVersion [0,2]: reset
     *
     * Database Version 6
     *  Date: 2016/09/09
     *
     *  ConversationService
     *   Update oldVersion [5]
     *   Update oldVersion [0,4]: reset
     *  DirectoryService
     *   Update oldVersion [3,5]: -
     *   Update oldVersion [0,2]: reset
     *  RepositoryService
     *   Update oldVersion [3,5]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeFactoryService
     *   Update oldVersion [3,5]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeInboundService
     *   Update oldVersion [3,5]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeOutboundService
     *   Update oldVersion [3,5]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeSwitchService
     *   Update oldVersion [3,5]: -
     *   Update oldVersion [0,2]: reset
     *
     * Database Version 5
     *  Date: 2015/12/02
     *
     *  ConversationService
     *   Update oldVersion [0,4]: reset
     *  DirectoryService
     *   Update oldVersion [3,4]: -
     *   Update oldVersion [0,2]: reset
     *  RepositoryService
     *   Update oldVersion [3,4]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeFactoryService
     *   Update oldVersion [3,4]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeInboundService
     *   Update oldVersion [3,4]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeOutboundService
     *   Update oldVersion [3,4]: -
     *   Update oldVersion [0,2]: reset
     *  TwincodeSwitchService
     *   Update oldVersion [3,4]: -
     *   Update oldVersion [0,2]: reset
     *
     * Database Version 4
     *  Date: 2015/11/13
     *
     *  ConversationService
     *   Update oldVersion <= 3: reset
     *  DirectoryService
     *   Update oldVersion == 3: -
     *   Update oldVersion <= 2: reset
     *  RepositoryService
     *   Update oldVersion == 3: -
     *   Update oldVersion <= 2: reset
     *  TwincodeFactoryService
     *   Update oldVersion == 3: -
     *   Update oldVersion <= 2: reset
     *  TwincodeInboundService
     *   Update oldVersion == 3: -
     *   Update oldVersion <= 2: reset
     *  TwincodeOutboundService
     *   Update oldVersion == 3: -
     *   Update oldVersion <= 2: reset
     *  TwincodeSwitchService
     *   Update oldVersion == 3: -
     *   Update oldVersion <= 2: reset
     *
     * </pre>
     */

    protected static final int DATABASE_VERSION = 25;

    //
    // Singleton instance
    //

    protected volatile static boolean mWebRtcReady = false;
    protected static ServiceFactory sServiceFactory = null;
    protected final Context mContext;

    //
    // Global requestId generator
    //

    private final AtomicLong mRequestId = new AtomicLong(0);

    //
    // Generic fields
    //

    protected volatile boolean mRunning;

    //
    // Configuration fields
    //

    private final Object mConfigurationLock = new Object();
    private boolean mConfigured = false;
    protected final TwinlifeConfiguration mTwinlifeConfiguration = new TwinlifeConfiguration();
    protected boolean mDatabaseUpgraded = false;
    protected boolean mRemoveDatabaseOnDestroy = false;
    protected ErrorCode mDatabaseError = ErrorCode.SUCCESS;

    //
    // XMPP Connection fields
    //
    protected Connection mWebSocketConnection;
    protected long mLastConnect;

    //
    // Services
    //

    private volatile AccountServiceImpl mAccountServiceImpl;
    private volatile ConnectivityService mConnectivityServiceImpl;
    private volatile ConversationServiceImpl mConversationServiceImpl;
    private volatile ManagementServiceImpl mManagementServiceImpl;
    private volatile NotificationServiceImpl mNotificationServiceImpl;
    private volatile PeerConnectionService mPeerConnectionServiceImpl;
    private volatile RepositoryServiceImpl mRepositoryServiceImpl;
    private volatile TwincodeFactoryServiceImpl mTwincodeFactoryServiceImpl;
    private volatile TwincodeInboundServiceImpl mTwincodeInboundServiceImpl;
    private volatile TwincodeOutboundServiceImpl mTwincodeOutboundServiceImpl;
    private volatile ImageServiceImpl mImageServiceImpl;
    private volatile AccountMigrationServiceImpl mAccountMigrationServiceImpl;
    private volatile PeerCallServiceImpl mPeerCallServiceImpl;
    private volatile CryptoServiceImpl mCryptoServiceImpl;
    protected TwinlifeSecuredConfiguration mTwinlifeSecuredConfiguration;
    protected final List<BaseServiceImpl<?>> mBaseServiceImpls = new ArrayList<>();
    private final DatabaseServiceImpl mDatabaseService;

    //
    // SerializerFactory
    //

    private final SerializerFactoryImpl mSerializerFactoryImpl;

    private volatile String mFullJid;
    private long mServerTimeCorrection = 0L;
    private long mEstimatedRTT = 0L;
    protected boolean mConnectionFailed = false;

    @NonNull
    protected final ExecutorService mTwinlifeExecutor;

    public long newRequestId() {

        return mRequestId.incrementAndGet();
    }

    public TwinlifeImpl(@NonNull Context context, @NonNull ExecutorService executor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinlifeImpl");
        }

        mSerializerFactoryImpl = new SerializerFactoryImpl();
        mContext = context;
        mTwinlifeExecutor = executor;
        mDatabaseService = new DatabaseServiceImpl();
    }

    public Context getContext() {

        return mContext;
    }

    public void onCreate() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate");
        }
    }

    public void onDestroy() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDestroy");
        }

        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                baseService.onDestroy();
            }
        }

        if (mRemoveDatabaseOnDestroy) {
            removeDatabase();
        }
    }

    private void destroy() {
        Log.i(LOG_TAG, "destroy");

        if (mWebSocketConnection != null) {
            mWebSocketConnection.destroy();
        }

        mAccountServiceImpl = null;
        // mConnectivityServiceImpl = null;
        mConversationServiceImpl = null;
        mManagementServiceImpl = null;
        mPeerConnectionServiceImpl = null;
        mRepositoryServiceImpl = null;
        mTwincodeFactoryServiceImpl = null;
        mTwincodeInboundServiceImpl = null;
        mTwincodeOutboundServiceImpl = null;
        mNotificationServiceImpl = null;
        mImageServiceImpl = null;
        mAccountMigrationServiceImpl = null;
        mPeerCallServiceImpl = null;
        mCryptoServiceImpl = null;

        mTwinlifeSecuredConfiguration = null;
        mBaseServiceImpls.clear();

        mWebSocketConnection = null;

        if (mRemoveDatabaseOnDestroy) {
            removeDatabase();
        }
    }

    //
    // Implementation of Twinlife interface
    //

    @Override
    public boolean isConfigured() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isConfigured=" + mConfigured);
        }

        synchronized (mConfigurationLock) {

            return mConfigured;
        }
    }

    @Override
    public boolean isDatabaseUpgraded() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isUpgraded");
        }

        return mDatabaseUpgraded;
    }

    @Override
    @NonNull
    public ErrorCode configure(@NonNull TwinlifeConfiguration twinlifeConfiguration,
                               @NonNull Connection connection) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: twinlifeConfiguration=" + twinlifeConfiguration);
        }

        UUID serviceId = Utils.UUIDFromString(twinlifeConfiguration.serviceId);
        UUID applicationId = Utils.UUIDFromString(twinlifeConfiguration.applicationId);
        if (serviceId == null) {

            Logger.error(LOG_TAG, "invalid serviceId configuration");
            return ErrorCode.WRONG_LIBRARY_CONFIGURATION;
        }
        if (applicationId == null) {

            Logger.error(LOG_TAG, "invalid applicationId configuration");
            return ErrorCode.WRONG_LIBRARY_CONFIGURATION;
        }
        if (twinlifeConfiguration.applicationName == null) {

            Logger.error(LOG_TAG, "invalid applicationName configuration");
            return ErrorCode.WRONG_LIBRARY_CONFIGURATION;
        }
        if (twinlifeConfiguration.applicationVersion == null) {

            Logger.error(LOG_TAG, "invalid applicationVersion configuration");
            return ErrorCode.WRONG_LIBRARY_CONFIGURATION;
        }
        if (twinlifeConfiguration.apiKey == null) {

            Logger.error(LOG_TAG, "invalid apiKey configuration");
            return ErrorCode.WRONG_LIBRARY_CONFIGURATION;
        }
        if (twinlifeConfiguration.certificateSerialNumber == null) {

            Logger.error(LOG_TAG, "invalid certificateSerialNumber configuration");
            return ErrorCode.WRONG_LIBRARY_CONFIGURATION;
        }

        if (connection.getDomain().indexOf('.') < 0) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "invalid server URL");
            }

            return BaseService.ErrorCode.WRONG_LIBRARY_CONFIGURATION;
        }

        String accessToken = getFingerprint(twinlifeConfiguration.certificateSerialNumber);
        if (accessToken == null) {

            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "invalid application fingerprint");
            }
            return ErrorCode.LIBRARY_ERROR;
        }

        mSerializerFactoryImpl.addSerializers(twinlifeConfiguration.serializers);

        mWebSocketConnection = connection;
        mWebSocketConnection.addPacketListener(BinaryErrorPacketIQ.IQ_ON_ERROR_SERIALIZER, this::onErrorPacket);

        // Create all service instances.
        mAccountServiceImpl = new AccountServiceImpl(this, mWebSocketConnection, applicationId,
                serviceId, twinlifeConfiguration.apiKey, accessToken);
        mManagementServiceImpl = new ManagementServiceImpl(this, mWebSocketConnection, applicationId);
        mImageServiceImpl = new ImageServiceImpl(this, mWebSocketConnection, getImageTools());
        mCryptoServiceImpl = new CryptoServiceImpl(this, mWebSocketConnection);
        mTwincodeInboundServiceImpl = new TwincodeInboundServiceImpl(this, mWebSocketConnection);
        mTwincodeOutboundServiceImpl = new TwincodeOutboundServiceImpl(this, mWebSocketConnection);
        mTwincodeFactoryServiceImpl = new TwincodeFactoryServiceImpl(this, mWebSocketConnection);
        mRepositoryServiceImpl = new RepositoryServiceImpl(this, mWebSocketConnection, twinlifeConfiguration.factories);
        mNotificationServiceImpl = new NotificationServiceImpl(this, mWebSocketConnection);
        mConversationServiceImpl = new ConversationServiceImpl(this, mWebSocketConnection, getImageTools());
        mPeerCallServiceImpl = new PeerCallServiceImpl(this, mWebSocketConnection);
        mPeerConnectionServiceImpl = createPeerConnectionService(mWebSocketConnection, twinlifeConfiguration.peerConnectionServiceConfiguration);
        mAccountMigrationServiceImpl = new AccountMigrationServiceImpl(this, mWebSocketConnection);

        // Setup and configure the device migration service before getting the Twinlife configuration.
        mBaseServiceImpls.add(mAccountMigrationServiceImpl);
        mAccountMigrationServiceImpl.configure(twinlifeConfiguration.accountMigrationServiceConfiguration);
        mTwinlifeConfiguration.accountMigrationServiceConfiguration = (AccountMigrationServiceConfiguration) mAccountMigrationServiceImpl.getServiceConfiguration();

        // Finish a possible migration that was successfully finished but which was not installed yet.
        // By installing it here, the secure configuration, account configuration and database are moved
        // before starting to use them.  If there is no migration, this is no-op.
        mAccountMigrationServiceImpl.finishMigration(mContext);

        synchronized (mConfigurationLock) {
            mTwinlifeConfiguration.serviceId = twinlifeConfiguration.serviceId;
            mTwinlifeConfiguration.applicationId = twinlifeConfiguration.applicationId;
            mTwinlifeConfiguration.applicationName = twinlifeConfiguration.applicationName;
            mTwinlifeConfiguration.applicationVersion = twinlifeConfiguration.applicationVersion;
            mTwinlifeConfiguration.certificateSerialNumber = twinlifeConfiguration.certificateSerialNumber;

            mTwinlifeSecuredConfiguration = TwinlifeSecuredConfiguration.init(mSerializerFactoryImpl, getConfigurationService(), mTwinlifeConfiguration);
            if (mTwinlifeSecuredConfiguration == null) {

                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "invalid secure configuration");
                }
                return ErrorCode.LIBRARY_ERROR;
            }
        }

        mCryptoServiceImpl.configure(twinlifeConfiguration.cryptoServiceConfiguration);
        mAccountServiceImpl.configure(twinlifeConfiguration.accountServiceConfiguration);
        mTwinlifeConfiguration.accountServiceConfiguration = (AccountServiceConfiguration) mAccountServiceImpl.getServiceConfiguration();

        mConnectivityServiceImpl = createConnectivityService(mWebSocketConnection, twinlifeConfiguration.connectivityServiceConfiguration);

        mBaseServiceImpls.add(mManagementServiceImpl);
        mManagementServiceImpl.configure(twinlifeConfiguration.managementServiceConfiguration);
        mTwinlifeConfiguration.managementServiceConfiguration = (ManagementServiceConfiguration) mManagementServiceImpl.getServiceConfiguration();

        mBaseServiceImpls.add(mNotificationServiceImpl);
        mNotificationServiceImpl.configure(twinlifeConfiguration.notificationServiceConfiguration);
        mTwinlifeConfiguration.notificationServiceConfiguration = (NotificationServiceConfiguration) mNotificationServiceImpl.getServiceConfiguration();

        mBaseServiceImpls.add(mTwincodeFactoryServiceImpl);
        mTwincodeFactoryServiceImpl.configure(twinlifeConfiguration.twincodeFactoryServiceConfiguration);
        mTwinlifeConfiguration.twincodeFactoryServiceConfiguration = (TwincodeFactoryServiceConfiguration) mTwincodeFactoryServiceImpl.getServiceConfiguration();

        mBaseServiceImpls.add(mTwincodeInboundServiceImpl);
        mTwincodeInboundServiceImpl.configure(twinlifeConfiguration.twincodeInboundServiceConfiguration);
        mTwinlifeConfiguration.twincodeInboundServiceConfiguration = (TwincodeInboundServiceConfiguration) mTwincodeInboundServiceImpl.getServiceConfiguration();

        mBaseServiceImpls.add(mTwincodeOutboundServiceImpl);
        mTwincodeOutboundServiceImpl.configure(twinlifeConfiguration.twincodeOutboundServiceConfiguration);
        mTwinlifeConfiguration.twincodeOutboundServiceConfiguration = (TwincodeOutboundServiceConfiguration) mTwincodeOutboundServiceImpl.getServiceConfiguration();

        mBaseServiceImpls.add(mRepositoryServiceImpl);
        mRepositoryServiceImpl.configure(twinlifeConfiguration.repositoryServiceConfiguration);
        mTwinlifeConfiguration.repositoryServiceConfiguration = (RepositoryServiceConfiguration) mRepositoryServiceImpl.getServiceConfiguration();

        mBaseServiceImpls.add(mConversationServiceImpl);
        mConversationServiceImpl.configure(twinlifeConfiguration.conversationServiceConfiguration);
        mTwinlifeConfiguration.conversationServiceConfiguration = (ConversationServiceConfiguration) mConversationServiceImpl.getServiceConfiguration();

        mBaseServiceImpls.add(mImageServiceImpl);
        mImageServiceImpl.configure(twinlifeConfiguration.imageServiceConfiguration);
        mTwinlifeConfiguration.imageServiceConfiguration = (ImageServiceConfiguration) mImageServiceImpl.getServiceConfiguration();

        mBaseServiceImpls.add(mPeerCallServiceImpl);
        mPeerCallServiceImpl.configure(twinlifeConfiguration.peerCallServiceConfiguration);
        mTwinlifeConfiguration.peerCallServiceConfiguration = (PeerCallService.PeerCallServiceConfiguration) mPeerCallServiceImpl.getServiceConfiguration();

        // If a service factory is defined, create the additional service it provides (see "dev" flavor).
        if (sServiceFactory != null) {
            BaseServiceImpl<?> service = sServiceFactory.createServices(this, mWebSocketConnection);
            if (service != null) {
                service.configure(twinlifeConfiguration.connectivityServiceConfiguration);
                mBaseServiceImpls.add(service);
            }
        }

        //
        // AccountService should be the last service to call onConnect() before
        // onSignIn() in all services
        //
        mBaseServiceImpls.add(mAccountServiceImpl);

        boolean configured = true;
        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            configured = configured && baseService.isConfigured();
        }

        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                baseService.onCreate();
            }
        }

        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                baseService.onConfigure();
            }
        }

        //
        // Databases initialization
        //

        try {
            openDatabase();

        } catch (DatabaseException dbException) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot open the database: ", dbException);
            }
            if (dbException.isDatabaseFull()) {
                return ErrorCode.NO_STORAGE_SPACE;
            } else {
                return ErrorCode.DATABASE_ERROR;
            }
        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot open the database: ", exception);
            }
            return ErrorCode.DATABASE_ERROR;
        }

        // Set the configured only when the database setup is finished.
        if (configured) {
            setConfigured();
        }

        start();

        if (!mWebRtcReady) {

            return ErrorCode.WEBRTC_ERROR;
        }
        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                baseService.onTwinlifeReady();
            }
        }
        return isConfigured() ? ErrorCode.SUCCESS : ErrorCode.WRONG_LIBRARY_CONFIGURATION;
    }

    @NonNull
    public DatabaseServiceImpl getDatabaseService() {

        return mDatabaseService;
    }

    public void shutdown() {
        if (INFO) {
            Log.i(LOG_TAG, "shutdown");
        }

        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                baseService.onTwinlifeSuspend();
            }
        }
    }

    @Override
    public void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mRunning = false;
        mTwinlifeExecutor.shutdown();
        if (mWebSocketConnection != null) {
            mWebSocketConnection.disconnect();
        }

        onDestroy();

        destroy();

        closeDatabase();
    }

    @Override
    @NonNull
    public AccountService getAccountService() {

        return mAccountServiceImpl;
    }

    @Override
    @NonNull
    public ConnectivityService getConnectivityService() {

        return mConnectivityServiceImpl;
    }

    @Override
    @NonNull
    public ConversationService getConversationService() {

        return mConversationServiceImpl;
    }

    @Override
    @Nullable
    public ManagementService getManagementService() {

        return mManagementServiceImpl;
    }

    @Override
    @NonNull
    public NotificationService getNotificationService() {

        return mNotificationServiceImpl;
    }

    @Override
    @NonNull
    public PeerConnectionService getPeerConnectionService() {

        return mPeerConnectionServiceImpl;
    }

    @Override
    @NonNull
    public RepositoryService getRepositoryService() {

        return mRepositoryServiceImpl;
    }

    @Override
    @NonNull
    public TwincodeFactoryService getTwincodeFactoryService() {

        return mTwincodeFactoryServiceImpl;
    }

    @Override
    @NonNull
    public TwincodeInboundService getTwincodeInboundService() {

        return mTwincodeInboundServiceImpl;
    }

    @Override
    @NonNull
    public TwincodeOutboundService getTwincodeOutboundService() {

        return mTwincodeOutboundServiceImpl;
    }

    @Override
    @NonNull
    public ImageService getImageService() {

        return mImageServiceImpl;
    }

    @Override
    @NonNull
    public AccountMigrationService getAccountMigrationService() {

        return mAccountMigrationServiceImpl;
    }

    @Override
    @NonNull
    public PeerCallService getPeerCallService() {

        return mPeerCallServiceImpl;
    }

    //
    // Public Methods
    //

    public CryptoServiceImpl getCryptoService() {

        return mCryptoServiceImpl;
    }

    public String getApplicationName() {

        return mTwinlifeConfiguration.applicationName;
    }

    public String getApplicationVersion() {

        return mTwinlifeConfiguration.applicationVersion;
    }

    public String getFullJid() {

        return mFullJid;
    }

    public String toBareJid(String username) {
        if (DEBUG) {
            Log.d(LOG_TAG, "toBareJid username=" + username);
        }

        return Utils.escapeNode(username.toLowerCase(Locale.US)) + "@" + mWebSocketConnection.getDomain();
    }

    /**
     * Get the server domain string.
     *
     * @return the server domain.
     */
    @NonNull
    public String getDomain() {

        final Connection connection = mWebSocketConnection;
        return connection == null ? "" : connection.getDomain();
    }

    @NonNull
    public abstract String getResource();

    @NonNull
    public String getDeviceIdentifier() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDeviceIdentifier");
        }

        return mTwinlifeSecuredConfiguration.deviceIdentifier;
    }

    public boolean isConnected() {

        final boolean result = mWebSocketConnection != null && mWebSocketConnection.isConnected();
        if (DEBUG) {
            Log.d(LOG_TAG, "isConnected=" + result);
        }

        return result;
    }

    @NonNull
    public ConnectionStatus getConnectionStatus() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConnectionStatus");
        }

        final Connection connection = mWebSocketConnection;
        if (connection == null) {
            return ConnectionStatus.NO_SERVICE;
        }

        final ConnectionStatus connectionStatus = connection.getConnectionStatus();
        if (connectionStatus != ConnectionStatus.NO_SERVICE) {
            return connectionStatus;
        }

        // The ConnectivityService could be null during startup.
        final ConnectivityService connectivityService = mConnectivityServiceImpl;
        if (connectivityService != null && !connectivityService.isConnectedNetwork()) {
            return ConnectionStatus.NO_INTERNET;
        }

        final ErrorStats errorStats = connection.getErrorStats(false);
        if (errorStats == null || errorStats.connectCounter <= 4) {
            return ConnectionStatus.CONNECTING;
        }
        return ConnectionStatus.NO_SERVICE;
    }

    public abstract void connect();

    public void disconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "disconnect");
        }

        if (isConnected()) {
            try {
                mWebSocketConnection.disconnect();
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "disconnect", exception);
                }
            }
        }
    }

    @Override
    public void onCreate(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate database=" + database);
        }

        mDatabaseService.onCreate(database);
    }

    @Override
    public void onUpgrade(@NonNull Database database, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade database=" + database + " oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        mDatabaseUpgraded = true;
        mDatabaseService.onUpgrade(database, oldVersion, newVersion);
    }

    @Override
    public void onOpen(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOpen database=" + database);
        }

        mDatabaseService.onOpen(database);
    }

    public void onUpdateConfiguration(Configuration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateConfiguration: configuration");
        }

        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                baseService.onUpdateConfiguration(configuration);
            }
        }
    }

    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                baseService.onTwinlifeOnline();
            }
        }
    }

    public void onSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignIn");
        }
        if (INFO) {
            Log.i(LOG_TAG, "onSignIn");
        }

        try {
            openDatabase();
        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot open the database: ", exception);
            }

        }

        mFullJid = mAccountServiceImpl.getUser();

        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                baseService.onSignIn();
            }
        }
    }

    public void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                baseService.onSignOut();
            }
        }

        mRunning = false;
        mRemoveDatabaseOnDestroy = true;

        ConfigurationService configurationService = getConfigurationService();
        mTwinlifeSecuredConfiguration.erase(configurationService);

        shutdown();
    }

    /**
     * Compute the wall clock adjustment between the server and our local clock.
     * This time correction is applied to times that we received from the server.
     * We continue to send our times not-adjusted: the server will do its own correction.
     *
     * The algorithm is inspired from NTP but it is simplified:
     * - we compute the RTT between the device and the server,
     * - we compute the time difference between the device and server,
     * - the difference is corrected by RTT/2.
     *
     * @param serverTime the server time when it processed the IQ.
     * @param deviceTime the time when we received the IQ.
     * @param latency the time taken on the server to process the request on its side and respond.
     * @param requestTime the time between our initial request and the server response.
     */
    public void adjustServerTime(long serverTime, long deviceTime, int latency, long requestTime) {
        if (DEBUG) {
            Log.d(LOG_TAG, "adjustServerTime: serverTime=" + serverTime + " deviceTime=" + deviceTime);
        }

        if (latency < 0) {
            return;
        }

        // Compute the propagation time: RTT (ignore excessive values).
        long tp = (requestTime - latency);
        if (tp < 0 || tp > 10000) {
            return;
        }

        // Compute the time correction (note: deviceTime is the time when we received the
        // server response it is ahead of tp/2 compared to the server time).
        long tc = (serverTime - (deviceTime - (tp / 2)));

        mServerTimeCorrection = -tc;
        mEstimatedRTT = (int) tp;
    }

    /**
     * Get the estimated RTT time with the Openfire server.
     *
     * @return the RTT time in milliseconds.
     */
    public long getEstimatedRTT() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getEstimatedRTT " + mEstimatedRTT + " ms");
        }

        return mEstimatedRTT;
    }

    /**
     * Get the server time correction with the device clock.
     *
     * @return the server time correction.
     */
    public long getServerTimeCorrection() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getServerTimeCorrection " + mServerTimeCorrection + " ms");
        }

        return mServerTimeCorrection;
    }

    public HashMap<BaseServiceId, String> getTwinlifeConfiguration() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwinlifeConfiguration");
        }

        HashMap<BaseServiceId, String> twinlifeConfiguration = new HashMap<>();
        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn()) {
                twinlifeConfiguration.put(baseService.getId(), baseService.getVersion());
            }
        }

        return twinlifeConfiguration;
    }

    public AccountServiceImpl getAccountServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getAccountServiceImpl");
        }

        return mAccountServiceImpl;
    }

    @SuppressWarnings("unused")
    public ConnectivityService getConnectivityServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConnectivityServiceImpl");
        }

        return mConnectivityServiceImpl;
    }

    @SuppressWarnings("unused")
    public ConversationServiceImpl getConversationServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConversationServiceImpl");
        }

        return mConversationServiceImpl;
    }

    public ManagementServiceImpl getManagementServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getManagementServiceImpl");
        }

        return mManagementServiceImpl;
    }

    @SuppressWarnings("unused")
    public NotificationServiceImpl getNotificationServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotificationServiceImpl");
        }

        return mNotificationServiceImpl;
    }

    @SuppressWarnings("unused")
    public TwincodeFactoryServiceImpl getTwincodeFactoryServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwincodeFactoryServiceImpl");
        }

        return mTwincodeFactoryServiceImpl;
    }

    @SuppressWarnings("unused")
    public TwincodeInboundServiceImpl getTwincodeInboundServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwincodeInboundServiceImpl");
        }

        return mTwincodeInboundServiceImpl;
    }

    @SuppressWarnings("unused")
    public TwincodeOutboundServiceImpl getTwincodeOutboundServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwincodeOutboundServiceImpl");
        }

        return mTwincodeOutboundServiceImpl;
    }

    @NonNull
    public PeerCallServiceImpl getPeerCallServiceImpl() {

        return mPeerCallServiceImpl;
    }

    @Override
    @NonNull
    public SerializerFactory getSerializerFactory() {

        return mSerializerFactoryImpl;
    }

    public SerializerFactoryImpl getSerializerFactoryImpl() {

        return mSerializerFactoryImpl;
    }

    @NonNull
    public Executor getTwinlifeExecutor() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwinlifeExecutor");
        }

        return mTwinlifeExecutor;
    }

    @NonNull
    public final Map<String, BaseService.ServiceStats> getServiceStats() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getServiceStats");
        }

        Map<String, BaseService.ServiceStats> result = new HashMap<>();
        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceReady()) {
                result.put(baseService.getServiceName(), baseService.getServiceStats());
            }
        }
        return result;
    }

    @Override
    public void exception(@NonNull AssertPoint assertPoint, @Nullable Throwable exception,
                          @Nullable AssertPoint.Values values) {
        if (DEBUG) {
            Log.d(LOG_TAG, "assertException: controlPoint=" + assertPoint);
        }

        if (mManagementServiceImpl == null) {
            return;
        }

        mManagementServiceImpl.assertion(assertPoint, values, false, exception);
    }

    @Override
    public void assertion(@NonNull AssertPoint assertPoint, @Nullable AssertPoint.Values values) {
        if (DEBUG) {
            Log.d(LOG_TAG, "assertException: controlPoint=" + assertPoint);
        }

        if (mManagementServiceImpl == null) {
            return;
        }

        mManagementServiceImpl.assertion(assertPoint, values, assertPoint.stackTrace(), null);
    }

    public void error(@NonNull ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "error: errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        mDatabaseError = errorCode;
        if (mManagementServiceImpl != null) {
            mManagementServiceImpl.error(errorCode, errorParameter);
        }
    }

    //
    // Private Methods
    //

    private void onErrorPacket(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onErrorPacket: iq=" + iq);
        }

        if (!(iq instanceof BinaryErrorPacketIQ)) {

            return;
        }

        final BinaryErrorPacketIQ errorPacketIQ = (BinaryErrorPacketIQ) iq;
        final long requestId = errorPacketIQ.getRequestId();
        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceOn() && baseService.receivedIQ(requestId)) {
                baseService.onErrorPacket(errorPacketIQ);
            }
        }
    }

    private void setConfigured() {
        if (DEBUG) {
            Log.d(LOG_TAG, "setConfigured");
        }

        synchronized (mConfigurationLock) {
            mConfigured = true;
        }
    }

    @NonNull
    public abstract ImageTools getImageTools();

    protected int connectInternal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "connectInternal");
        }

        final Connection webSocketConnection = mWebSocketConnection;
        if (webSocketConnection == null) {
            return Integer.MAX_VALUE;
        }

        webSocketConnection.connect();
        return 20000;
    }

    @Override
    public void onConnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConnect");
        }

        mConnectionFailed = false;
        for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
            if (baseService.isServiceReady()) {
                baseService.onConnect();
            }
        }
    }

    @Override
    public void onDisconnect(@NonNull ErrorCategory errorCategory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect error=" + errorCategory);
        }

        if (mRunning) {
            for (BaseServiceImpl<?> baseService : mBaseServiceImpls) {
                if (baseService.isServiceReady()) {
                    baseService.onDisconnect();
                }
            }
        }
    }

    /**
     * Abstract methods that must be implemented for the concrete class.
     */

    protected abstract ConnectivityService createConnectivityService(Connection webSocketConnection, ConnectivityService.ConnectivityServiceConfiguration configuration);

    protected abstract PeerConnectionService createPeerConnectionService(Connection webSocketConnection, PeerConnectionService.PeerConnectionServiceConfiguration configuration);

    @NonNull
    public abstract ContentResolver getContentResolver();

    @Nullable
    protected abstract String getFingerprint(@NonNull String certificateSerialNumber);

    protected abstract void start();

    protected abstract void openDatabase() throws DatabaseException;

    protected abstract void removeDatabase();

    protected abstract void closeDatabase();

    protected abstract Connection getConnection(@NonNull TwinlifeConfiguration configuration);
}
