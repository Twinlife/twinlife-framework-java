/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.backup.BackupHeaderInfo;
import org.twinlife.twinlife.backup.RestoreContent;
import org.twinlife.twinlife.backup.VerifyReport;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface BackupService extends BaseService<BackupService.ServiceObserver> {
    String VERSION = "1.0.0";

    enum BackupState {
        STARTING,
        GENERATE_KEY,
        CREATE_FILE,
        TERMINATED,
        CANCELED
    }

    enum RestoreState {
        STARTING,
        RESTORE_ACCOUNT,
        PREPARE_DATABASE,
        RESTORE_DATA,
        GET_ALL_TWINCODES,
        CHECK_CONSISTENCY,
        WAIT_CONFIRM,
        SYNCING_OBJECTS,
        COMMIT,
        CANCEL,
        TERMINATED
    }

    enum TerminateReason {
        SUCCESS,
        ERROR,
        CANCEL
    }

    enum ErrorCode {
        INTERNAL_ERROR,

        // Not enough space on the device.
        NO_SPACE_LEFT,

        // Read or write error while saving the file.
        IO_ERROR,

        // Backup key was revoked
        REVOKED,

        // The current version is older than the version that performed the backup.
        WRONG_VERSION,

        // The backup file was created on another variant.
        WRONG_APP,

        // Error occurred while generating the key
        KEY_GEN_FAILED,

        // Can't decrypt the AccountSecuredConfiguration's schema ID: the key is invalid or has been revoked
        // (or the file is invalid, but we successfully read the header so it's most likely a key problem).
        INVALID_KEY,

        // The backup file's content is corrupted, or otherwise invalid
        INVALID_FILE,

        SYNC_FAILED,

        DIFFERENT_ACCOUNT,

        SUCCESS
    }

    class WrongAppException extends SerializerException {

        public WrongAppException(@NonNull String message) {
            super(message);
        }
    }

    class WrongVersionException extends SerializerException {

        public WrongVersionException(@NonNull String message) {
            super(message);
        }
    }

    class BackupServiceConfiguration extends BaseService.BaseServiceConfiguration {

        public BackupServiceConfiguration() {

            super(BaseService.BaseServiceId.BACKUP_SERVICE_ID, VERSION, false);
        }
    }

    interface ServiceObserver extends BaseService.ServiceObserver {
        default void onHeaderInfo(@NonNull BackupHeaderInfo backupHeaderInfo, @Nullable UUID lastBackupId, long lastBackupTimestamp) {

        }
        default void onBackupStateChange(@NonNull UUID backupId, @NonNull BackupState state) {
        }

        default void onRestoreStateChange(@NonNull RestoreState state, @Nullable RestoreContent restoreContent) {
        }

        default void onTerminateBackup(@NonNull UUID backupId, @Nullable String backupFilePath, @NonNull Map<UUID, Integer> stats, boolean done) {
        }

        default void onTerminateRestore(@NonNull TerminateReason terminateReason) {
        }

        default void onBackupError(@NonNull ErrorCode backupErrorCode, @NonNull org.twinlife.twinlife.ErrorCode baseErrorCode) {

        }
        default void onRestoreError(@NonNull ErrorCode backupErrorCode, @NonNull org.twinlife.twinlife.ErrorCode baseErrorCode) {
        }

        default void onVerifyReport(@NonNull VerifyReport report) {
        }

        default void onSyncError(@NonNull List<UUID> serverTwincodeDeleteErrors, @NonNull List<RepositoryObject> localObjectSyncErrors, @NonNull List<RepositoryObject> localObjectDeleteErrors) {
        }
    }

    ErrorCode checkFileCompatibility(@NonNull String backupPath);

    void backup(@NonNull byte[] password, @NonNull List<UUID> supportedSchemaIds);

    void restore(@NonNull byte[] password, @NonNull String backupPath, @NonNull List<UUID> supportedSchemaIds, @Nullable Boolean inPlace, @NonNull TwinlifeContext twinlifeContext);

    void commitRestore();

    void cancelRestore();

    void verifyBackup(@NonNull byte[] password, @NonNull String backupPath, @NonNull List<UUID> supportedSchemaIds);

    void getAllBackups(@NonNull Consumer<List<BackupInfo>> consumer);

    void deleteBackups(@NonNull Consumer<Void> consumer);

    boolean isRestoreInProgress();
}
