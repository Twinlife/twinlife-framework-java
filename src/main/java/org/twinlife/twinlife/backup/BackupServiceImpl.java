/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BackupInfo;
import org.twinlife.twinlife.BackupService;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinlife.TwinlifeImpl;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BackupServiceImpl extends BaseServiceImpl<BackupService.ServiceObserver> implements BackupService {
    private static final String LOG_TAG = "BackupServiceImpl";
    private static final boolean DEBUG = false;

    @Nullable
    private BackupExecutor mCurrentBackup = null;
    @Nullable
    private RestoreExecutor mCurrentRestore = null;
    @Nullable
    private VerifyExecutor mVerifyExecutor = null;

    public BackupServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {
        super(twinlifeImpl, connection);

        setServiceConfiguration(new BackupServiceConfiguration());
    }

    @Override
    protected void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        if (mCurrentRestore != null) {
            mCurrentRestore.onTwinlifeOnline();
        }
    }

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof BackupServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        setServiceConfiguration(new BackupServiceConfiguration());
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    public BackupService.ErrorCode checkFileCompatibility(@NonNull String backupPath) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkFileSignature: backupPath=" + backupPath);
        }

        return new VerifyExecutor(this, backupPath).verifyHeader();
    }

    @Override
    public void backup(@NonNull byte[] password, @NonNull List<UUID> supportedSchemaIds) {
        if (DEBUG) {
            Log.d(LOG_TAG, "backup: password=" + Arrays.toString(password) + " supportedSchemaIds=" + supportedSchemaIds);
        }

        if (!isServiceOn()) {
            Log.e(LOG_TAG, "service is not configured");
            onBackupError(BackupService.ErrorCode.INTERNAL_ERROR, org.twinlife.twinlife.ErrorCode.LIBRARY_ERROR);
            return;
        }

        try {
            mCurrentBackup = new BackupExecutor(this, password, supportedSchemaIds);
        } catch (IOException e) {
            Log.e(LOG_TAG, "Could not instantiate BackupExecutor", e);
            onBackupError(BackupService.ErrorCode.IO_ERROR, org.twinlife.twinlife.ErrorCode.FILE_NOT_FOUND);

            return;
        }
        mCurrentBackup.startBackup();
    }

    @Override
    public void restore(@NonNull byte[] password, @NonNull String backupPath, @NonNull List<UUID> supportedSchemaIds, @Nullable Boolean inPlace, @NonNull TwinlifeContext twinlifeContext) {
        if (DEBUG) {
            Log.d(LOG_TAG, "restore: password=" + Arrays.toString(password) + " backupPath=" + backupPath + " supportedSchemaIds=" + supportedSchemaIds + " inPlace=" + inPlace);
        }

        if (!isServiceOn()) {
            Log.e(LOG_TAG, "service is not configured");
            onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, org.twinlife.twinlife.ErrorCode.LIBRARY_ERROR);
            return;
        }

        mCurrentRestore = new RestoreExecutor(this, password, backupPath, supportedSchemaIds, inPlace, twinlifeContext);
        mCurrentRestore.startRestore();
    }

    @Override
    public void commitRestore() {
        if (DEBUG) {
            Log.d(LOG_TAG, "commitRestore");
        }

        if (mCurrentRestore == null) {
            Log.e(LOG_TAG, "No current restore, can't commit");
            onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, org.twinlife.twinlife.ErrorCode.LIBRARY_ERROR);
            return;
        }

        mCurrentRestore.commit();

    }

    @Override
    public void cancelRestore() {
        if (DEBUG) {
            Log.d(LOG_TAG, "cancelRestore");
        }

        if (mCurrentRestore == null) {
            Log.e(LOG_TAG, "No current restore, can't cancel");
            onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, org.twinlife.twinlife.ErrorCode.LIBRARY_ERROR);
            return;
        }

        mCurrentRestore.requestCancel();
    }

    public void verifyBackup(@NonNull byte[] password, @NonNull String backupPath, @NonNull List<UUID> supportedSchemaIds) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verifyBackup: password=" + Arrays.toString(password) + " backupPath=" + backupPath + " supportedSchemaIds=" + supportedSchemaIds);
        }

        mVerifyExecutor = new VerifyExecutor(this, password, backupPath, supportedSchemaIds);

        mVerifyExecutor.startVerify();
    }


    @Override
    public boolean isRestoreInProgress() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isRestoreInProgress");
        }

        return mCurrentRestore != null && mCurrentRestore.getRestoreState() != RestoreState.TERMINATED;
    }

    @Override
    public void getAllBackups(@NonNull Consumer<List<BackupInfo>> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getAllBackups: consumer=" + consumer);
        }

        mTwinlifeImpl.getAccountServiceImpl().getAllBackups(consumer);
    }

    @Override
    public void deleteBackups(@NonNull Consumer<Void> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteBackups: consumer=" + consumer);
        }

        mTwinlifeImpl.getAccountServiceImpl().deleteBackups(consumer);
    }

    void onBackupHeader(@NonNull BackupHeaderInfo backupHeaderInfo, @Nullable UUID lastBackupId, long lastBackupTimestamp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBackupHeader: backupHeaderInfo=" + backupHeaderInfo + " lastBackupId=" + lastBackupId + " lastBackupTimestamp=" + lastBackupTimestamp);
        }

        for (BackupService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onHeaderInfo(backupHeaderInfo, lastBackupId, lastBackupTimestamp));
        }
    }

    void onBackupStateChange(@NonNull UUID backupId, @NonNull BackupState backupState) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBackupStateChange: backupState=" + backupState);
        }

        for (BackupService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onBackupStateChange(backupId, backupState));
        }
    }

    void onRestoreStateChange(@NonNull RestoreState restoreState, @Nullable RestoreContent restoreContent) {
       if (DEBUG) {
           Log.d(LOG_TAG, "onRestoreStateChange: restoreState=" + restoreState + " restoreContent=" + restoreContent);
       }

        for (BackupService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onRestoreStateChange(restoreState, restoreContent));
        }
    }

    void onBackupError(@NonNull BackupService.ErrorCode backupErrorCode, @NonNull org.twinlife.twinlife.ErrorCode baseErrorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBackupError: backupErrorCode=" + backupErrorCode + " baseErrorCode=" + baseErrorCode);
        }

        for (BackupService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onBackupError(backupErrorCode, baseErrorCode));
        }
    }

    void onRestoreError(@NonNull BackupService.ErrorCode backupErrorCode, @NonNull org.twinlife.twinlife.ErrorCode baseErrorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRestoreError: backupErrorCode=" + backupErrorCode + " baseErrorCode=" + baseErrorCode);
        }

        for (BackupService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onRestoreError(backupErrorCode, baseErrorCode));
        }
    }

    void onSyncError(@NonNull List<UUID> serverTwincodeDeleteErrors, @NonNull List<RepositoryObject> localObjectSyncErrors, @NonNull List<RepositoryObject> localObjectDeleteErrors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSyncError: serverTwincodeDeleteErrors=[" + Arrays.asList(serverTwincodeDeleteErrors.toArray()) + " localObjectSyncErrors=" + Arrays.asList(localObjectSyncErrors.toArray()) + " localObjectDeleteErrors=" + Arrays.asList(localObjectDeleteErrors.toArray()));
        }

        for (BackupService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onSyncError(serverTwincodeDeleteErrors, localObjectSyncErrors, localObjectDeleteErrors));
        }
    }

    void onTerminateBackup(@NonNull UUID backupId, @NonNull String backupFilePath, @NonNull Map<UUID, Integer> stats) {
       if (DEBUG) {
           Log.d(LOG_TAG, "onTerminateBackup: backupId=" + backupId + " backupFilePath=" + backupFilePath);
       }

        mCurrentBackup = null;

        for (BackupService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onTerminateBackup(backupId, backupFilePath, stats, true));
        }
    }

    void onTerminateRestore(@NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTerminateRestore");
        }

        mCurrentRestore = null;

        for (BackupService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onTerminateRestore(terminateReason));
        }
    }

    void onTerminateVerify(@NonNull VerifyReport verifyReport) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onVerifyReport: verifyReport=" + verifyReport);
        }

        mVerifyExecutor = null;

        for (BackupService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onVerifyReport(verifyReport));
        }
    }
}
