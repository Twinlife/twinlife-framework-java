/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup;

import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BackupService;
import org.twinlife.twinlife.BackupService.RestoreState;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.account.DerivedServerKeyInfo;
import org.twinlife.twinlife.backup.handlers.AccountSecuredConfigurationHandler;
import org.twinlife.twinlife.backup.handlers.BackupHeaderHandler;
import org.twinlife.twinlife.backup.handlers.ImageHandler;
import org.twinlife.twinlife.backup.handlers.RepositoryObjectHandler;
import org.twinlife.twinlife.backup.handlers.TwincodeInboundHandler;
import org.twinlife.twinlife.backup.handlers.TwincodeOutboundHandler;
import org.twinlife.twinlife.util.BinaryDecoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class VerifyExecutor {
    private static final String LOG_TAG = "VerifyExecutor";
    private static final boolean DEBUG = false;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "verifyThread"));
    private final Map<UUID, BackupHandler<?>> mHandlers = new HashMap<>();

    @NonNull
    private final BackupServiceImpl mBackupService;
    @NonNull
    private final TwinlifeImpl mTwinlifeImpl;
    @NonNull
    private final byte[] mUserPassword;
    @NonNull
    private final String mBackupPath;
    @NonNull
    private final List<UUID> mSupportedSchemaIds;

    @Nullable
    private BackupHeaderInfo mBackupHeaderInfo;

    @Nullable
    private InputStream mInputStream = null;
    @Nullable
    private BinaryDecoder mDecoder = null;

    @NonNull
    private RestoreState mRestoreState;

    @NonNull
    private final Map<UUID, List<UUID>> addedObjects = new HashMap<>();
    @NonNull
    private final Map<UUID, List<UUID>> deletedObjects = new HashMap<>();
    @NonNull
    private final Map<UUID, List<RepositoryObject>> upToDateObjects = new HashMap<>();
    @NonNull
    private final Map<UUID, List<UUID>> modifiedObjects = new HashMap<>();
    @NonNull
    private final List<VerifyResult.Present<TwincodeOutbound>> twincodeOutbounds = new ArrayList<>();

    VerifyExecutor(@NonNull BackupServiceImpl backupService, @NonNull byte[] password, @NonNull String backupPath, @NonNull List<UUID> supportedSchemaIds) {
        mBackupService = backupService;
        mTwinlifeImpl = backupService.getTwinlifeImpl();
        mUserPassword = password;
        mBackupPath = backupPath;
        mSupportedSchemaIds = supportedSchemaIds;
        initHandlers();

        setRestoreState(RestoreState.STARTING);
    }

    VerifyExecutor(@NonNull BackupServiceImpl backupService, @NonNull String backupPath) {
        mBackupService = backupService;
        mTwinlifeImpl = backupService.getTwinlifeImpl();
        mBackupPath = backupPath;
        mUserPassword = new byte[0];
        mSupportedSchemaIds = Collections.emptyList();
    }

    BackupService.ErrorCode verifyHeader() {
        if (DEBUG) {
            Log.d(LOG_TAG, "verifyHeader");
        }

        File backupFile = new File(URI.create(mBackupPath));

        if (!backupFile.exists()) {
            Log.e(LOG_TAG, "Backup file does not exist: " + mBackupPath);
            return BackupService.ErrorCode.IO_ERROR;
        }

        try (InputStream inputStream = new FileInputStream(backupFile)) {
            new BackupHeaderHandler(BackupConfig.FILE_SIGNATURE).verify(new BinaryDecoder(inputStream));
            // BackupHeaderHandler.verify() always returns Present if it didn't throw.
            return BackupService.ErrorCode.SUCCESS;
        } catch (BackupService.WrongAppException e) {
            Log.e(LOG_TAG, "Backup file " + mBackupPath + " created by wrong app", e);
            return BackupService.ErrorCode.WRONG_APP;
        } catch (BackupService.WrongVersionException e) {
            Log.e(LOG_TAG, "Backup file " + mBackupPath + " created by wrong version", e);
            return BackupService.ErrorCode.WRONG_VERSION;
        } catch (Exception e) {
            Log.e(LOG_TAG, "Error occurred while checking header in backup file " + mBackupPath, e);
            return BackupService.ErrorCode.INVALID_FILE;
        }
    }

    void startVerify() {
        if (DEBUG) {
            Log.d(LOG_TAG, "startVerify");
        }

        executeIfNotCancelled(this::internalStartVerify);
    }

    private void executeIfNotCancelled(@NonNull Runnable runnable) {
        if (getRestoreState() == RestoreState.CANCEL) {
            if (DEBUG) {
                Log.d(LOG_TAG, "verify cancelled: rollback and exit");
            }
            cancel();
            return;
        }

        mExecutor.execute(runnable);
    }

    private void internalStartVerify() {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalStartVerify");
        }

        // Let Uri sanitize the path
        Uri uri = Uri.parse(mBackupPath);
        if (uri.getPath() == null) {
            Log.e(LOG_TAG, "Backup file path " + mBackupPath + " is invalid");
            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.FILE_NOT_FOUND);
            return;
        }

        try {
            mInputStream = new FileInputStream(uri.getPath());
        } catch (FileNotFoundException e) {
            Log.e(LOG_TAG, "Backup file path " + mBackupPath + " is invalid");
            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.FILE_NOT_FOUND);
            return;
        }
        mDecoder = new BinaryDecoder(mInputStream);

        // Read backup header: UUID, date, salt
        try {
            VerifyResult header = new BackupHeaderHandler(BackupConfig.FILE_SIGNATURE).verify(mDecoder);
            if (!(header instanceof VerifyResult.Present)) {
                mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.FILE_NOT_SUPPORTED);
                return;
            }
            //noinspection unchecked
            mBackupHeaderInfo = ((VerifyResult.Present<BackupHeaderInfo>) header).object;
        } catch (SerializerException e) {
            Log.e(LOG_TAG, "Couldn't decode BackupHeaderInfo", e);
            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.FILE_NOT_SUPPORTED);
            return;
        }

        mTwinlifeImpl.getAccountServiceImpl().generateBackupKey(mBackupHeaderInfo.backupId, mUserPassword, mBackupHeaderInfo.salt, true, (errorCode, derivedServerKeyInfo) ->
                executeIfNotCancelled(() -> onGenerateBackupKey(errorCode, derivedServerKeyInfo)));
    }

    private void onGenerateBackupKey(@NonNull BaseService.ErrorCode errorCode, @Nullable DerivedServerKeyInfo derivedServerKeyInfo) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGenerateBackupKey: errorCode=" + errorCode + " derivedServerKeyInfo=" + derivedServerKeyInfo);
        }

        if (errorCode != BaseService.ErrorCode.SUCCESS || derivedServerKeyInfo == null) {
            mBackupService.onRestoreError(BackupService.ErrorCode.KEY_GEN_FAILED, errorCode);
            cancel();
            return;
        }

        if (mBackupHeaderInfo == null) {
            mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            cancel();
            return;
        }

        mBackupService.onBackupHeader(mBackupHeaderInfo, derivedServerKeyInfo.lastBackupId, derivedServerKeyInfo.lastBackupTimestamp);

        setRestoreState(RestoreState.RESTORE_ACCOUNT);

        if (mInputStream == null) {
            mBackupService.onRestoreError(BackupService.ErrorCode.IO_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            cancel();
            return;
        }

        // After the plaintext header, the payload is encrypted
        mInputStream = mTwinlifeImpl.getCryptoService().wrapCryptoInputStream(mInputStream, derivedServerKeyInfo.derivedServerKey);

        if (mInputStream == null) {
            mBackupService.onRestoreError(BackupService.ErrorCode.KEY_GEN_FAILED, BaseService.ErrorCode.DECRYPT_ERROR);
            cancel();
            return;
        }

        mDecoder = new BinaryDecoder(mInputStream);

        if (!checkHeader()) {
            return;
        }

        if (!checkAccount()) {
            return;
        }

        if (!checkData()) {
            return;
        }

        if (mInputStream == null) {
            Log.w(LOG_TAG, "mInputStream is null");
        } else {
            try {
                mInputStream.close();
                mInputStream = null;
            } catch (IOException e) {
                Log.w(LOG_TAG, "Could not close mInputStream", e);
            }
        }

        generateReport();
    }

    private boolean checkHeader() {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkHeader");
        }

        BackupHeaderInfo backupHeaderInfo = mBackupHeaderInfo;

        if (mDecoder == null) {
            throw new IllegalStateException("mDecoder is null");
        }

        if (backupHeaderInfo == null) {
            Log.e(LOG_TAG, "No BackupHeaderInfo");
            mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            cancel();
            return false;
        }

        try {
            UUID accountConfId = mDecoder.readUUID();
            if (!accountConfId.equals(AccountSecuredConfigurationHandler.SCHEMA_ID)) {
                Log.e(LOG_TAG, "Expected AccountSecuredConfiguration schema ID but got: " + accountConfId);
                mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_KEY, BaseService.ErrorCode.DECRYPT_ERROR);
                cancel();

                return false;
            }
        } catch (SerializerException e) {
            Log.e(LOG_TAG, "Error occurred while restoring account configuration", e);
            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_KEY, BaseService.ErrorCode.DECRYPT_ERROR);
            cancel();
            return false;
        }
        return true;
    }

    private boolean checkAccount() {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkAccount");
        }

        if (mDecoder == null) {
            throw new IllegalStateException("mDecoder is null");
        }

        try {
            VerifyResult accountVerify = new AccountSecuredConfigurationHandler(
                    mTwinlifeImpl.getConfigurationService(),
                    mTwinlifeImpl.getAccountServiceImpl())
                    .verify(mDecoder);

            if (!(accountVerify instanceof VerifyResult.Present)) {
                // Result is always Present if AccountSecuredConfigurationHandler's verify() didn't throw.
                throw new IllegalStateException("AccountSecuredConfiguration not found");
            }

            //noinspection unchecked
            VerifyResult.Present<ConfigurationService.SecuredConfiguration> account = (VerifyResult.Present<ConfigurationService.SecuredConfiguration>) accountVerify;

            if (account.modified) {
                Log.e(LOG_TAG, "Backup and DB account are different");
                mBackupService.onRestoreError(BackupService.ErrorCode.DIFFERENT_ACCOUNT, BaseService.ErrorCode.FILE_NOT_SUPPORTED);
                cancel();
                return false;
            }
        } catch (SerializerException e) {
            Log.e(LOG_TAG, "Error occurred while restoring account configuration", e);
            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.DECRYPT_ERROR);
            cancel();
            return false;
        }
        return true;
    }

    private boolean checkData() {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkData");
        }

        if (mDecoder == null) {
            throw new IllegalStateException("mDecoder is null");
        }

        while (!mDecoder.isEof()) {
            if (getRestoreState() != RestoreState.CANCEL) {
                try {
                    UUID schemaId = mDecoder.readUUID();
                    BackupHandler<?> handler = mHandlers.get(schemaId);

                    if (handler == null) {
                        Log.e(LOG_TAG, "No handler found for schemaId " + schemaId);
                        mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.DECRYPT_ERROR);
                        cancel();
                        return false;
                    } else {
                        VerifyResult verifyResult = handler.verify(mDecoder);
                        handleVerifyResult(verifyResult);
                    }
                } catch (Throwable t) {
                    Log.e(LOG_TAG, "Error occurred while restoring data", t);

                    BackupService.ErrorCode error = (t.getCause() instanceof IOException) ? BackupService.ErrorCode.IO_ERROR : BackupService.ErrorCode.INVALID_FILE;
                    mBackupService.onRestoreError(error, BaseService.ErrorCode.DECRYPT_ERROR);

                    cancel();
                    return false;
                }
            } else {
                cancel();
                return false;
            }
        }
        return true;
    }


    private void generateReport() {
        if (DEBUG) {
            Log.d(LOG_TAG, "generateReport");
        }

        findAddedObjects();
        findTwincodeUpdates();

        Map<UUID, List<UUID>> upToDateIds = new HashMap<>();

        for (Map.Entry<UUID, List<RepositoryObject>> entry : upToDateObjects.entrySet()) {
            UUID schemaId = entry.getKey();
            List<RepositoryObject> objects = entry.getValue();
            for (RepositoryObject object : objects) {
                addValueBySchemaId(upToDateIds, schemaId, object.getId());
            }
        }

        VerifyReport report = new VerifyReport(addedObjects, deletedObjects, modifiedObjects, upToDateIds);

        if (DEBUG) {
            Log.d(LOG_TAG, report.toString());
        }

        mBackupService.onTerminateVerify(report);
    }

    /**
     * Find objects that are in the database but not in the backup.
     */
    private void findAddedObjects() {
        if (DEBUG) {
            Log.d(LOG_TAG, "findAddedObjects");
        }

        List<UUID> activeObjects = new ArrayList<>();
        for (List<RepositoryObject> objects : upToDateObjects.values()) {
            for (RepositoryObject object : objects) {
                activeObjects.add(object.getId());
            }
        }
        for (List<UUID> objectIds : modifiedObjects.values()) {
            activeObjects.addAll(objectIds);
        }

        for (RepositoryObject localObject : mTwinlifeImpl.getRepositoryServiceImpl().getLocalObjects(mSupportedSchemaIds)) {
            boolean found = false;
            for (UUID objectId : activeObjects) {
                if (localObject.getId().equals(objectId)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                addValueBySchemaId(addedObjects, localObject.getDatabaseId().getSchemaId(), localObject.getId());
            }
        }
    }

    /**
     * Check for objects whose twincode has been updated after the backup.
     */
    private void findTwincodeUpdates() {
        if (DEBUG) {
            Log.d(LOG_TAG, "findTwincodeUpdates");
        }

        for (List<RepositoryObject> objects : upToDateObjects.values()) {
            for (Iterator<RepositoryObject> iterator = objects.iterator(); iterator.hasNext(); ) {
                RepositoryObject repositoryObject = iterator.next();

                if (repositoryObject.getTwincodeOutbound() == null) {
                    continue;
                }

                for (VerifyResult.Present<TwincodeOutbound> result : twincodeOutbounds) {

                    TwincodeOutbound twincodeOutbound = result.object;

                    if (twincodeOutbound.getId().equals(repositoryObject.getTwincodeOutbound().getId())) {
                        if (result.modified) {
                            // Move object from upToDateObjects to modifiedObjects.
                            iterator.remove();
                            addValueBySchemaId(modifiedObjects, repositoryObject.getDatabaseId().getSchemaId(), repositoryObject.getId());
                        }
                        break;
                    }
                }
            }
        }
    }

    private void handleVerifyResult(@NonNull VerifyResult verifyResult) {
        if (DEBUG) {
            Log.d(LOG_TAG, "handleVerifyResult: verifyResult=" + verifyResult);
        }

        if (verifyResult instanceof VerifyResult.Present) {
            VerifyResult.Present<?> present = (VerifyResult.Present<?>) verifyResult;

            if (present.object instanceof RepositoryObject) {
                RepositoryObject object = (RepositoryObject) present.object;

                if (present.modified) {
                    addValueBySchemaId(modifiedObjects, object.getDatabaseId().getSchemaId(), object.getId());
                } else {
                    addValueBySchemaId(upToDateObjects, object.getDatabaseId().getSchemaId(), object);
                }
            } else if (present.object instanceof TwincodeOutbound) {
                // Keep twincodes to check if repository objects were modified after reading the entire backup.
                //noinspection unchecked
                twincodeOutbounds.add((VerifyResult.Present<TwincodeOutbound>) present);
            }
        } else {
            VerifyResult.Absent<?> absent = (VerifyResult.Absent<?>) verifyResult;

            if (absent.type.equals(RepositoryObject.class)) {
                //noinspection DataFlowIssue: if the type is RepositoryObject, we know schemaId is not null.
                addValueBySchemaId(deletedObjects, absent.schemaId, absent.id);
            }
        }
    }

    private void cancel() {
        if (DEBUG) {
            Log.d(LOG_TAG, "cancel");
        }

        if (mInputStream == null) {
            Log.w(LOG_TAG, "mInputStream is null");
        } else {
            try {
                mInputStream.close();
                mInputStream = null;
            } catch (IOException e) {
                Log.w(LOG_TAG, "Could not close mInputStream", e);
            }
        }

        setRestoreState(RestoreState.TERMINATED);

        mExecutor.shutdown();
    }

    synchronized RestoreState getRestoreState() {
        return mRestoreState;
    }

    private void setRestoreState(@NonNull RestoreState restoreState) {
        synchronized (this) {
            mRestoreState = restoreState;
        }
        mBackupService.onRestoreStateChange(restoreState, null);
    }

    private void initHandlers() {
        mHandlers.put(TwincodeOutboundHandler.SCHEMA_ID, new TwincodeOutboundHandler(mTwinlifeImpl.getTwincodeOutboundServiceImpl(), mTwinlifeImpl.getCryptoService()));
        mHandlers.put(TwincodeInboundHandler.SCHEMA_ID, new TwincodeInboundHandler(mTwinlifeImpl.getTwincodeInboundServiceImpl(), mTwinlifeImpl.getTwincodeOutboundServiceImpl()));
        mHandlers.put(RepositoryObjectHandler.SCHEMA_ID, new RepositoryObjectHandler(mTwinlifeImpl.getRepositoryServiceImpl(), mSupportedSchemaIds));

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {
            throw new IllegalStateException("filesDir not initialized");
        }
        mHandlers.put(ImageHandler.SCHEMA_ID, new ImageHandler(mTwinlifeImpl.getImageServiceImpl(), filesDir));
    }

    private <V> void addValueBySchemaId(@NonNull Map<UUID, List<V>> map, @NonNull UUID schemaId, @NonNull V value) {
        List<V> values = map.get(schemaId);

        if (values == null) {
            values = new ArrayList<>();
            map.put(schemaId, values);
        }

        values.add(value);
    }
}
