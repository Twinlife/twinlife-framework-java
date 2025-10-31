/*
 *  Copyright (c) 2020-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Service to migrate an account from one device to another.
 */
public interface AccountMigrationService extends BaseService<AccountMigrationService.ServiceObserver> {

    String VERSION = "2.1.1";

    enum State {
        STARTING,

        // Negotiate size limits before transfer.
        NEGOTIATE,

        // List files with their size and date.
        LIST_FILES,

        // Send files in chunks of N kb.
        SEND_FILES,

        // Send application settings.
        SEND_SETTINGS,

        // Send database content.
        SEND_DATABASE,

        // Wait for files and database to be received.
        WAIT_FILES,

        // Send twinlife secure configuration, account, environmentId.
        SEND_ACCOUNT,

        // Wait for peer account.
        WAIT_ACCOUNT,

        // Wait for the terminate phase.
        TERMINATE,

        // All steps above are processed.
        TERMINATED,

        // Operation was canceled.
        CANCELED,

        // Operation aborted due to an error.
        ERROR,

        // Stopping or stopped: the executor must not be used.
        STOPPED
    }

    enum ErrorCode {
        INTERNAL_ERROR,

        // Not enough space on the target.
        NO_SPACE_LEFT,

        // Read or write error while saving a file.
        IO_ERROR,

        // Twincode was revoked (canceled by the peer device ?)
        REVOKED,

        // The peer device is not compatible with this device
        BAD_PEER_VERSION,

        // Error when checking SQLCipher database with its key.
        BAD_DATABASE,

        // Error when storing secure configuration
        SECURE_STORE_ERROR
    }

    class AccountMigrationServiceConfiguration extends BaseServiceConfiguration {

        public AccountMigrationServiceConfiguration() {

            super(BaseServiceId.ACCOUNT_MIGRATION_SERVICE_ID, VERSION, false);
        }
    }

    interface QueryInfo extends Serializable {
        long getDirectoryCount();

        long getFileCount();

        long getMaxFileSize();

        long getDatabaseFileSize();

        long getTotalFileSize();

        long getDatabaseAvailableSpace();

        long getFilesystemAvailableSpace();
    }

    interface Status extends Serializable {
        @NonNull
        State getState();

        boolean isConnected();

        long getBytesSent();

        long getEstimatedBytesRemainSend();

        long getBytesReceived();

        long getEstimatedBytesRemainReceive();

        double getSendProgress();

        double getReceiveProgress();

        double getProgress();

        int getSendErrorCount();

        int getReceiveErrorCount();

        @Nullable
        ErrorCode getErrorCode();
    }

    interface ServiceObserver extends BaseService.ServiceObserver {

        void onQueryStats(long requestId, @NonNull QueryInfo peerInfo, @Nullable QueryInfo localInfo);

        void onStatusChange(@NonNull UUID deviceMigrationId, @NonNull Status status);

        void onTerminateMigration(long requestId, @NonNull UUID deviceMigrationId, boolean commit, boolean done);
    }

    @SuppressWarnings("EmptyMethod")
    class DefaultServiceObserver extends BaseService.DefaultServiceObserver implements ServiceObserver {

        @Override
        public void onQueryStats(long requestId, @NonNull QueryInfo peerInfo, @Nullable QueryInfo localInfo) {

        }

        @Override
        public void onStatusChange(@NonNull UUID deviceMigrationId, @NonNull Status status) {

        }

        @Override
        public void onTerminateMigration(long requestId, @NonNull UUID deviceMigrationId, boolean commit, boolean done) {

        }
    }

    /**
     * Check and get the active device migration id.
     *
     * @return the active device migration id or null.
     */
    @Nullable
    UUID getActiveDeviceMigrationId();

    /**
     * Start the device migration process by setting up and opening the P2P connection to the peer twincode outboundid.
     *
     * @param requestId the request identifier.
     * @param accountMigrationId the account migration identifier.
     * @param peerTwincodeOutboundId the peer twincode outbound id.
     * @param twincodeOutboundId the current device twincode outbound id.
     */
    void outgoingStartMigration(long requestId, @NonNull UUID accountMigrationId,
                                @NonNull UUID peerTwincodeOutboundId, @NonNull UUID twincodeOutboundId);

    /**
     * Start the device migration process by accepting the incoming P2P connection from the peer.
     *
     * @param peerConnectionId the P2P incoming peer connection.
     * @param accountMigrationId the account migration identifier
     * @param peerTwincodeOutboundId the peer twincode outbound id.
     * @param twincodeOutboundId our local twincode id.
     */
    void incomingStartMigration(@NonNull UUID peerConnectionId, @NonNull UUID accountMigrationId,
                                @Nullable UUID peerTwincodeOutboundId, @NonNull UUID twincodeOutboundId);

    /**
     * Query the peer device to obtain statistics about the files it provides.
     *
     * @param requestId the request identifier.
     * @param maxFileSize the maximum file size we accept.
     */
    void queryStats(long requestId, long maxFileSize);

    /**
     * Start the migration by asking the peer device to send its files.
     *
     * @param requestId the request identifier.
     * @param maxFileSize the maximum file size we accept.
     */
    void startMigration(long requestId, long maxFileSize);

    /**
     * Terminate the migration by sending the termination IQ and closing the P2P connection.
     *
     * @param requestId the request identifier.
     * @param commit true if we commit or and false if we abort the migration.
     * @param done true if the DeviceMigration object and its twincode was deleted.
     */
    void terminateMigration(long requestId, boolean commit, boolean done);

    /**
     * Shutdown the P2P connection gracefully after the terminate phase1 and phase2.
     *
     * @param requestId the request identifier.
     */
    void shutdownMigration(long requestId);

    /**
     * Cancel a possible device migration:
     * - if there is a P2P connection close it,
     * - if there is an active (ie, opened) device migration engine stop it,
     * - if there are some migration files, remove them.
     * Last, notify a possible migration service that the migration was canceled.
     *
     * @param deviceMigrationId the device migration identifier.
     * @return true if the migration was canceled and false if there is nothing to cancel.
     */
    boolean cancelMigration(@NonNull UUID deviceMigrationId);
}
