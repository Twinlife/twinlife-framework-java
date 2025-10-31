/*
 *  Copyright (c) 2014-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * onTwinlifeReady
 * onConnectionStatusChange
 * onSignIn
 * onTwinlifeOnline
 * onTwinlifeOffline
 * onDisconnect
 * <p>
 * onSignOut
 **/

package org.twinlife.twinlife;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BaseService.ServiceStats;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

@SuppressWarnings("unused")
public interface TwinlifeContext {

    interface Observer {

        void onTwinlifeReady();

        void onTwinlifeOnline();

        void onTwinlifeOffline();

        //
        // Connectivity Management
        //

        default void onConnectionStatusChange(@NonNull ConnectionStatus connectionStatus) {}

        //
        // Account Management
        //

        void onSignIn();

        void onSignInError(@NonNull ErrorCode errorCode);

        void onSignOut();

        void onShutdown();

        void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter);

        @SuppressWarnings("EmptyMethod")
        void onFatalError(ErrorCode errorCode);
    }

    @SuppressWarnings({"EmptyMethod"})
    class DefaultObserver implements Observer {

        @Override
        public void onTwinlifeReady() {
        }

        @Override
        public void onTwinlifeOnline() {
        }

        @Override
        public void onTwinlifeOffline() {
        }

        @Override
        public void onSignIn() {
        }

        @Override
        public void onSignInError(@NonNull ErrorCode errorCode) {
        }

        @Override
        public void onSignOut() {
        }

        @Override
        public void onShutdown() {
        }

        @Override
        public void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {
        }

        @Override
        public void onFatalError(ErrorCode errorCode) {
        }
    }

    void start(@NonNull Context context);

    void shutdown();

    void stop();

    boolean hasTwinlife();

    boolean isConnected();

    @NonNull
    ConnectionStatus getConnectionStatus();

    void connect();

    void disconnect();

    long newRequestId();

    void setObserver(@NonNull Observer observer);

    void removeObserver(@NonNull Observer observer);

    void execute(@NonNull Runnable command);

    void executeImage(@NonNull Runnable command);

    @NonNull
    Iterator<Observer> observersIterator();

    @NonNull
    AccountService getAccountService();

    @NonNull
    ConnectivityService getConnectivityService();

    @NonNull
    ConversationService getConversationService();

    @NonNull
    ManagementService getManagementService();

    @NonNull
    NotificationService getNotificationService();

    @NonNull
    PeerConnectionService getPeerConnectionService();

    @NonNull
    JobService getJobService();

    @NonNull
    RepositoryService getRepositoryService();

    @NonNull
    TwincodeFactoryService getTwincodeFactoryService();

    @NonNull
    TwincodeInboundService getTwincodeInboundService();

    @NonNull
    TwincodeOutboundService getTwincodeOutboundService();

    @NonNull
    ConfigurationService getConfigurationService();

    @NonNull
    ImageService getImageService();

    @NonNull
    AccountMigrationService getAccountMigrationService();

    @NonNull
    PeerCallService getPeerCallService();

    @NonNull
    Map<String, ServiceStats> getServiceStats();

    @Nullable
    File getFilesDir();

    @Nullable
    File getCacheDir();

    @NonNull
    SerializerFactory getSerializerFactory();

    /**
     * Assertion point to verify that the given object is not null.  If not, the assertion is reported
     * with the marker.  The marker is a free number whose goal is to help find the assertion in the code.
     *
     * @param assertPoint the assertion to report when the object is null.
     * @param object the object to check.
     * @param marker the free marker to report.
     */
    void assertNotNull(@NonNull AssertPoint assertPoint, @Nullable Object object, int marker);

    void assertNotNull(@NonNull AssertPoint assertPoint, Object object);

    void assertEqual(@NonNull AssertPoint assertPoint, Object object1, Object object2);

    void exception(@NonNull AssertPoint assertPoint, @Nullable Exception exception,
                   @Nullable AssertPoint.Values values);

    void assertion(@NonNull AssertPoint assertPoint, @Nullable AssertPoint.Values values);

    void fireOnError(long requestId, ErrorCode errorCode, @Nullable String errorParameter);
}
