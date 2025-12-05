/*
 *  Copyright (c) 2014-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Chedi Baccari (Chedi.Baccari@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BaseService.ServiceStats;
import org.twinlife.twinlife.management.ManagementServiceImpl;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

public class TwinlifeContextImpl implements TwinlifeContext {
    private static final String LOG_TAG = "TwinlifeContextImpl";
    private static final boolean DEBUG = false;
    private static final int GLOBAL_ERROR_DELAY_GUARD = 2 * 120 * 1000; // 2 minutes

    private class ConnectivityServiceObserver extends ConnectivityService.DefaultServiceObserver {

        @Override
        public void onNetworkConnect() {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConnectivityServiceObserver.onNetworkConnect");
            }

            TwinlifeContextImpl.this.onNetworkConnect();
        }

        @Override
        public void onNetworkDisconnect() {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConnectivityServiceObserver.onNetworkDisconnect");
            }

            TwinlifeContextImpl.this.onNetworkDisconnect();
        }

        @Override
        public void onConnect() {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConnectivityServiceObserver.onConnect");
            }

            TwinlifeContextImpl.this.onConnect();
        }

        @Override
        public void onDisconnect() {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConnectivityServiceObserver.onDisconnect");
            }

            TwinlifeContextImpl.this.onDisconnect();
        }
    }

    private class AccountServiceObserver extends AccountService.DefaultServiceObserver {

        @Override
        public void onSignIn() {
            if (DEBUG) {
                Log.d(LOG_TAG, "AccountServiceObserver.onSignIn");
            }

            TwinlifeContextImpl.this.onSignIn();
        }

        @Override
        public void onSignInError(@NonNull ErrorCode errorCode) {
            if (DEBUG) {
                Log.d(LOG_TAG, "AccountServiceObserver.onSignInError errorCode=" + errorCode);
            }

            TwinlifeContextImpl.this.onSignInError(errorCode);
        }

        @Override
        public void onSignOut() {
            if (DEBUG) {
                Log.d(LOG_TAG, "AccountServiceObserver.onSignOut");
            }

            TwinlifeContextImpl.this.onSignOut();
        }
    }

    private class ManagementServiceObserver extends ManagementService.DefaultServiceObserver {
        private volatile long mLastErrorTime = 0L;

        @Override
        public void onError(long requestId, ErrorCode errorCode, String errorParameter) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ManagementServiceObserver.onError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
            }

            if (requestId == DEFAULT_REQUEST_ID) {

                // Report a fatal database error.
                if (errorCode == ErrorCode.DATABASE_CORRUPTION) {
                    TwinlifeContextImpl.this.fireFatalError(errorCode);
                    return;
                }

                long now = System.currentTimeMillis();
                if (now < mLastErrorTime + GLOBAL_ERROR_DELAY_GUARD) {
                    return;
                }

                // Report a global error such as file system is full only once per 2 minutes.
                mLastErrorTime = now;
                TwinlifeContextImpl.this.fireOnError(requestId, errorCode, errorParameter);
            }
        }

        @Override
        public void onValidateConfiguration(long requestId) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ManagementServiceObserver.onValidateConfiguration: requestId=" + requestId);
            }

            TwinlifeContextImpl.this.onValidateConfiguration();
        }
    }

    protected final TwinlifeConfiguration mTwinlifeConfiguration;

    protected volatile TwinlifeImpl mTwinlifeImpl;
    private volatile boolean mIsOnline = false;

    private final CopyOnWriteArraySet<Observer> mObservers = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<Observer> mPendingObservers = new CopyOnWriteArraySet<>();

    private final ConnectivityServiceObserver mConnectivityServiceObserver;
    private final AccountServiceObserver mAccountServiceObserver;
    private final ManagementServiceObserver mManagementServiceObserver;
    @NonNull
    protected final JobService mJobService;
    @NonNull
    protected final ConfigurationService mConfigurationService;
    @NonNull
    protected final ExecutorService mTwinlifeExecutor;
    @NonNull
    protected final Executor mImageExecutor;

    private ErrorCode mConfigureStatus = ErrorCode.SUCCESS;

    private static final class ObserverThreadFactory implements ThreadFactory {

        public Thread newThread(@NonNull Runnable runnable) {

            return new Thread(runnable, "twinlife-observer");
        }
    }

    private static final class ImageThreadFactory implements ThreadFactory {

        public Thread newThread(@NonNull Runnable runnable) {

            return new Thread(runnable, "twinlife-image");
        }
    }

    public TwinlifeContextImpl(TwinlifeConfiguration twinlifeConfiguration,
                               @NonNull JobService jobService,
                               @NonNull ConfigurationService configurationService) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinlifeContextImpl: twinlifeConfiguration=" + twinlifeConfiguration);
        }

        mTwinlifeConfiguration = twinlifeConfiguration;
        mJobService = jobService;
        mConfigurationService = configurationService;
        mTwinlifeExecutor = Executors.newSingleThreadExecutor(new ObserverThreadFactory());
        mImageExecutor = Executors.newSingleThreadExecutor(new ImageThreadFactory());

        mConnectivityServiceObserver = new ConnectivityServiceObserver();
        mAccountServiceObserver = new AccountServiceObserver();
        mManagementServiceObserver = new ManagementServiceObserver();
    }

    //
    // Override TwinlifeContext methods
    //

    @Override
    public void start(@NonNull Context context) {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

    }

    @Override
    public void shutdown() {
        if (DEBUG) {
            Log.d(LOG_TAG, "shutdown");
        }

        mTwinlifeImpl.suspend();

        getJobService().setObserver(new JobService.Observer() {
            @Override
            public void onEnterForeground() {
            }

            @Override
            public void onEnterBackground() {
            }

            @Override
            public void onBackgroundNetworkStart() {
            }

            @Override
            public void onBackgroundNetworkStop() {
                mTwinlifeExecutor.execute(() -> onShutdown());
            }

            @Override
            public void onActivePeers(int count) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "Active peers " + count);
                }

                if (count == 0) {
                    mTwinlifeExecutor.execute(() -> onShutdown());
                }
            }
        });

        if (!getPeerConnectionService().hasPeerConnections()) {
            mTwinlifeExecutor.execute(this::onShutdown);
        }
    }

    @Override
    public void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        mConfigureStatus = ErrorCode.SERVICE_UNAVAILABLE;
        mTwinlifeImpl.stop();
    }

    @Override
    public final boolean hasTwinlife() {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasTwinlife");
        }

        return mTwinlifeImpl != null && mTwinlifeImpl.isConfigured();
    }

    @Override
    public final boolean isConnected() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isConnected");
        }

        return mTwinlifeImpl != null && mTwinlifeImpl.isConnected();
    }

    @Override
    @NonNull
    public ConnectionStatus getConnectionStatus() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConnectionStatus");
        }

        final TwinlifeImpl twinlifeImpl = mTwinlifeImpl;
        if (twinlifeImpl != null) {
            return twinlifeImpl.getConnectionStatus();
        } else {
            return ConnectionStatus.NO_SERVICE;
        }
    }

    @Override
    public final void connect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "connect");
        }

        if (mTwinlifeImpl != null) {
            mTwinlifeImpl.connect();
        }
    }

    @Override
    public final void disconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "disconnect");
        }

        if (mTwinlifeImpl != null) {
            mTwinlifeImpl.disconnect();
        }
    }

    public final void suspend() {
        if (DEBUG) {
            Log.d(LOG_TAG, "suspend");
        }

        if (mTwinlifeImpl != null) {
            mTwinlifeImpl.suspend();
        }
    }

    @Override
    public final long newRequestId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "newRequestId");
        }

        return mTwinlifeImpl.newRequestId();
    }

    @Override
    public final void setObserver(@NonNull Observer observer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setObserver observer=" + observer);
        }

        if (hasTwinlife() && mConfigureStatus == ErrorCode.SUCCESS) {
            if (mObservers.add(observer)) {
                // Get the current connected, sign-in, online states immediately:
                // - the isSignIn implies isConnected (checked atomically by WebSocketConnection),
                // - the mIsOnline does not imply isSignIn nor isConnected because it is cleared asynchronously.
                boolean isSignIn = mTwinlifeImpl.getAccountServiceImpl().isSignIn();
                boolean isConnected = isSignIn || mTwinlifeImpl.isConnected();
                boolean isOnline = isSignIn && mIsOnline;

                mTwinlifeExecutor.execute(observer::onTwinlifeReady);

                if (isConnected) {
                    mTwinlifeExecutor.execute(() -> observer.onConnectionStatusChange(ConnectionStatus.CONNECTED));

                    if (isSignIn) {
                        mTwinlifeExecutor.execute(observer::onSignIn);

                        if (isOnline) {
                            mTwinlifeExecutor.execute(observer::onTwinlifeOnline);
                        }
                    }
                }
            }
        } else if (mConfigureStatus == ErrorCode.SUCCESS) {
            mPendingObservers.add(observer);
        } else {
            // Twinlife failed to initialize, propagate the fatal error to the new observer.
            // This is necessary when the TwinmeApplication is created, fails to build the service
            // and the main activity is started after.
            mTwinlifeExecutor.execute(() -> observer.onFatalError(mConfigureStatus));
        }
    }

    @Override
    public final void removeObserver(@NonNull Observer observer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeObserver observer=" + observer);
        }

        mObservers.remove(observer);
    }

    @Override
    @NonNull
    public Iterator<Observer> observersIterator() {
        if (DEBUG) {
            Log.d(LOG_TAG, "observersIterator");
        }

        return mObservers.iterator();
    }

    @Override
    @NonNull
    public final AccountService getAccountService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getAccountService");
        }

        return mTwinlifeImpl.getAccountService();
    }

    @Override
    @NonNull
    public final ConnectivityService getConnectivityService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConnectivityService");
        }

        return mTwinlifeImpl.getConnectivityService();
    }

    @Override
    @NonNull
    public final ConversationService getConversationService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConversationService");
        }

        return mTwinlifeImpl.getConversationService();
    }

    @Override
    @NonNull
    public final ManagementService getManagementService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getManagementService");
        }

        return mTwinlifeImpl.getManagementService();
    }

    @Override
    @NonNull
    public final NotificationService getNotificationService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotificationService");
        }

        return mTwinlifeImpl.getNotificationService();
    }

    @Override
    @NonNull
    public final PeerConnectionService getPeerConnectionService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPeerConnectionService");
        }

        return mTwinlifeImpl.getPeerConnectionService();
    }

    @Override
    @NonNull
    public JobService getJobService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getJobService");
        }

        // The Job service is required before the TwinlifeImpl is available.
        return mJobService;
    }

    @Override
    @NonNull
    public final RepositoryService getRepositoryService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getRepositoryService");
        }

        return mTwinlifeImpl.getRepositoryService();
    }

    @Override
    @NonNull
    public final TwincodeFactoryService getTwincodeFactoryService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwincodeFactoryService");
        }

        return mTwinlifeImpl.getTwincodeFactoryService();
    }

    @Override
    @NonNull
    public final TwincodeInboundService getTwincodeInboundService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwincodeInboundService");
        }

        return mTwinlifeImpl.getTwincodeInboundService();
    }

    @Override
    @NonNull
    public final TwincodeOutboundService getTwincodeOutboundService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwincodeOutboundService");
        }

        return mTwinlifeImpl.getTwincodeOutboundService();
    }

    @Override
    @NonNull
    public final ConfigurationService getConfigurationService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfigurationService");
        }

        return mConfigurationService;
    }

    @Override
    @NonNull
    public final ImageService getImageService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getImageService");
        }

        return mTwinlifeImpl.getImageService();
    }

    @Override
    @NonNull
    public final AccountMigrationService getAccountMigrationService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDeviceMigrationService");
        }

        return mTwinlifeImpl.getAccountMigrationService();
    }

    @Override
    @NonNull
    public PeerCallService getPeerCallService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPeerCallService");
        }

        return mTwinlifeImpl.getPeerCallService();
    }

    @Override
    @NonNull
    public final Map<String, ServiceStats> getServiceStats() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getServiceStats");
        }

        return mTwinlifeImpl.getServiceStats();
    }

    @Override
    @NonNull
    public final File getFilesDir() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFilesDir");
        }

        return mTwinlifeImpl.getFilesDir();
    }

    @Nullable
    public final File getCacheDir() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCacheDir");
        }

        return mTwinlifeImpl.getCacheDir();
    }

    @NonNull
    public SerializerFactory getSerializerFactory() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSerializerFactory");
        }

        return mTwinlifeImpl.getSerializerFactory();
    }

    @Override
    public void execute(@NonNull Runnable command) {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute");
        }

        mTwinlifeExecutor.execute(command);
    }

    @Override
    public void executeImage(@NonNull Runnable command) {
        if (DEBUG) {
            Log.d(LOG_TAG, "executeImage");
        }

        mImageExecutor.execute(command);
    }

    @Override
    public void exception(@NonNull AssertPoint assertPoint, @Nullable Exception exception,
                          @Nullable AssertPoint.Values values) {
        if (DEBUG) {
            Log.d(LOG_TAG, "assertException: controlPoint=" + assertPoint);
        }

        if (mTwinlifeImpl != null) {
            mTwinlifeImpl.exception(assertPoint, exception, values);
        }
    }

    @Override
    public void assertion(@NonNull AssertPoint assertPoint, @Nullable AssertPoint.Values values) {
        if (DEBUG) {
            Log.d(LOG_TAG, "assertException: controlPoint=" + assertPoint);
        }

        if (mTwinlifeImpl != null) {
            mTwinlifeImpl.assertion(assertPoint, values);
        }
    }

    @Override
    public void assertNotNull(@NonNull AssertPoint assertPoint, Object object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "assertNotNull: controlPoint=" + assertPoint + " object=" + object);
        }

        if (mTwinlifeImpl != null && object == null) {
            mTwinlifeImpl.assertion(assertPoint, null);
        }
    }

    @Override
    public void assertEqual(@NonNull AssertPoint assertPoint, Object object1, Object object2) {
        if (DEBUG) {
            Log.d(LOG_TAG, "assertEqual: controlPoint=" + assertPoint + " object1=" + object1 + " object2=" + object2);
        }

        if (mTwinlifeImpl != null && !Utils.equals(object1, object2)) {
            mTwinlifeImpl.assertion(assertPoint, null);
        }
    }

    @Override
    public void assertNotNull(@NonNull AssertPoint assertPoint, @Nullable Object object, int marker) {
        if (DEBUG) {
            Log.d(LOG_TAG, "assertNotNull: assertPoint=" + assertPoint + " object=" + object + " marker=" + marker);
        }

        if (object == null && mTwinlifeImpl != null) {
            mTwinlifeImpl.assertion(assertPoint, AssertPoint.createMarker(marker));
        }
    }

    @Override
    public void fireOnError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fireOnError: requestId=" + requestId + " errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(() -> observer.onError(requestId, errorCode, errorParameter));
        }
    }

    public void fireFatalError(@NonNull ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fireFatalError: errorCode=" + errorCode);
        }

        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(() -> observer.onFatalError(errorCode));
        }
    }

    //
    // Implementation of ServiceConnection class
    //

    public void onServiceConnected(@NonNull TwinlifeImpl twinlifeImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onServiceConnected: twinlifeImpl=" + twinlifeImpl);
        }

        mTwinlifeImpl = twinlifeImpl;
        mTwinlifeExecutor.execute(this::configure);
    }

    private void configure() {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure");
        }

        if (!mTwinlifeImpl.isConfigured()) {
            final Connection connection = mTwinlifeImpl.getConnection(mTwinlifeConfiguration);
            mConfigureStatus = mTwinlifeImpl.configure(mTwinlifeConfiguration, connection);
            if (mConfigureStatus != ErrorCode.SUCCESS) {

                // Call the pending observers to give them a chance to catch the onFatalError.
                fireFatalError(mConfigureStatus);
                return;
            }

            if (mJobService instanceof Observer) {
                mPendingObservers.add((Observer)mJobService);
            }
            if (mConfigurationService instanceof Observer) {
                mPendingObservers.add((Observer)mConfigurationService);
            }
        }

        if (getConnectivityService() != null) {
            getConnectivityService().addServiceObserver(mConnectivityServiceObserver);
        }
        getAccountService().addServiceObserver(mAccountServiceObserver);
        getManagementService().addServiceObserver(mManagementServiceObserver);

        onTwinlifeReady();

        for (Observer observer : mPendingObservers) {
            setObserver(observer);
        }
        mPendingObservers.clear();
    }

    public void onServiceDisconnected() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onServiceDisconnected");
        }

        getConnectivityService().removeServiceObserver(mConnectivityServiceObserver);
        getAccountService().removeServiceObserver(mAccountServiceObserver);
        getManagementService().removeServiceObserver(mManagementServiceObserver);
    }

    //
    // Protected Methods
    //

    protected void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(observer::onTwinlifeReady);
        }
    }

    protected void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        mIsOnline = true;
        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(observer::onTwinlifeOnline);
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onNetworkConnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onNetworkConnect");
        }

        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(() -> observer.onConnectionStatusChange(ConnectionStatus.CONNECTING));
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onNetworkDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onNetworkDisconnect");
        }

        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(() -> observer.onConnectionStatusChange(ConnectionStatus.NO_INTERNET));
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onConnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConnect");
        }

        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(() -> observer.onConnectionStatusChange(ConnectionStatus.CONNECTED));
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        mIsOnline = false;
        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(observer::onTwinlifeOffline);
        }

        final ConnectionStatus connectionStatus = mTwinlifeImpl.getConnectionStatus();
        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(() -> observer.onConnectionStatusChange(connectionStatus));
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignIn");
        }

        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(observer::onSignIn);
        }
    }

    protected void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        mIsOnline = false;
        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(observer::onSignOut);
        }
    }

    protected void onShutdown() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onShutdown");
        }

        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(observer::onShutdown);
        }
    }

    //
    // Private Methods
    //

    private void onSignInError(@NonNull ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignInError errorCode=" + errorCode);
        }

        // This is a serious error and we cannot proceed, invalidate any operation from now.
        // It occurs if:
        // - the Twinme Configuration is incorrect (ex: bad application id, bad service id, ...)
        // - the user account has been deleted.
        mConfigureStatus = errorCode;

        for (Observer observer : mObservers) {
            mTwinlifeExecutor.execute(() -> observer.onSignInError(errorCode));
        }
    }

    private void onValidateConfiguration() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onValidateConfiguration");
        }

        onTwinlifeOnline();
    }

    @Nullable
    protected String getNotificationKey(Context context) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotificationKey: context=" + context);
        }

        ManagementService managementService = getManagementService();
        if (!(managementService instanceof ManagementServiceImpl)) {

            return null;
        }

        return ((ManagementServiceImpl) managementService).getNotificationKey();
    }
}
