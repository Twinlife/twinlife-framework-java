/*
 *  Copyright (c) 2024-2026 twinlife SA.
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
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.TwincodeInfo;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.account.DerivedServerKeyInfo;
import org.twinlife.twinlife.backup.handlers.AccountSecuredConfigurationHandler;
import org.twinlife.twinlife.backup.handlers.BackupHeaderHandler;
import org.twinlife.twinlife.backup.handlers.ImageHandler;
import org.twinlife.twinlife.backup.handlers.RepositoryObjectHandler;
import org.twinlife.twinlife.backup.handlers.TwincodeInboundHandler;
import org.twinlife.twinlife.backup.handlers.TwincodeOutboundHandler;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundImpl;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundServiceImpl;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

class RestoreExecutor {
    private static final String LOG_TAG = "RestoreExecutor";
    private static final boolean DEBUG = false;

    private static final UUID NIL_UUID = new UUID(0, 0);

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "restoreThread"));
    private final Map<UUID, BackupHandler<?>> mHandlers = new HashMap<>();

    @NonNull
    private final BackupServiceImpl mBackupService;
    @NonNull
    private final TwinlifeImpl mTwinlifeImpl;
    @NonNull
    private final TwinlifeContext mTwinlifeContext;
    @NonNull
    private final byte[] mUserPassword;
    @NonNull
    private final String mBackupPath;
    @NonNull
    private final List<UUID> mSupportedSchemaIds;
    @Nullable
    private final Boolean mInPlaceRequested;

    @Nullable
    private BackupHeaderInfo mBackupHeaderInfo;

    @Nullable
    private InputStream mInputStream = null;
    @Nullable
    private BinaryDecoder mDecoder = null;

    @Nullable
    private ConfigurationService.SecuredConfiguration mAccountConfiguration = null;

    private boolean mInPlaceRestore;

    @NonNull
    private RestoreState mRestoreState;
    @Nullable
    private BackupService.TerminateReason mTerminateReason = null;
    @NonNull
    private final Map<UUID, Integer> mStats = new HashMap<>();
    @Nullable
    private RestoreContent mRestoreContent = null;

    @NonNull
    private final List<TwincodeInfo> mAddedTwincodes = new ArrayList<>();
    @NonNull
    private final List<RepositoryObject> mDeletedObjects = new ArrayList<>();
    @NonNull
    private final List<RepositoryObject> mActiveObjects = new ArrayList<>();

    @NonNull
    private final List<UUID> mServerTwincodeSyncErrors = new ArrayList<>();
    @NonNull
    private final List<RepositoryObject> mLocalObjectSyncErrors = new ArrayList<>();
    @NonNull
    private final List<RepositoryObject> mLocalObjectDeleteErrors = new ArrayList<>();


    private int mRetryReconnect = 3;

    RestoreExecutor(@NonNull BackupServiceImpl backupService, @NonNull byte[] password, @NonNull String backupPath, @NonNull List<UUID> supportedSchemaIds, @Nullable Boolean inPlace, @NonNull TwinlifeContext twinlifeContext) {
        mBackupService = backupService;
        mTwinlifeImpl = backupService.getTwinlifeImpl();
        mTwinlifeContext = twinlifeContext;
        mUserPassword = password;
        mBackupPath = backupPath;
        mSupportedSchemaIds = supportedSchemaIds;
        mInPlaceRequested = inPlace;
        initHandlers();

        setRestoreState(RestoreState.STARTING);
    }

    void startRestore() {
        if (DEBUG) {
            Log.d(LOG_TAG, "startRestore");
        }

        executeIfNotCancelled(this::internalStartRestore);
    }

    private void executeIfNotCancelled(@NonNull Runnable runnable) {
        RestoreState restoreState = getRestoreState();

        if (restoreState == RestoreState.TERMINATED) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Restore terminated: exit");
            }
            return;
        }

        if (restoreState == RestoreState.CANCEL) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Restore cancelled: rollback and exit");
            }
            if (!mExecutor.isShutdown()) {
                try {
                    mExecutor.execute(() -> cancel(BackupService.TerminateReason.CANCEL));
                } catch (RejectedExecutionException e) {
                    Log.e(LOG_TAG, "Executor shut down, could not execute cancel", e);
                }
            }
            return;
        }

        if (!mExecutor.isShutdown()) {
            try {
                mExecutor.execute(runnable);
            } catch (RejectedExecutionException e) {
                Log.e(LOG_TAG, "Executor shut down, could not execute task", e);
            }
        }
    }

    private void internalStartRestore() {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalStartRestore");
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
            mBackupHeaderInfo = new BackupHeaderHandler(BackupConfig.FILE_SIGNATURE).restore(mDecoder, false);
        } catch (SerializerException e) {
            Log.e(LOG_TAG, "Couldn't decode BackupHeaderInfo", e);
            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.FILE_NOT_SUPPORTED);
            return;
        }

        if (mBackupHeaderInfo == null) {
            Log.e(LOG_TAG, "Couldn't restore BackupHeaderInfo");
            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.FILE_NOT_SUPPORTED);
            try {
                mInputStream.close();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Couldn't close mInputStream", e);
            }
            return;
        }

        mTwinlifeImpl.getAccountServiceImpl().generateBackupKey(mBackupHeaderInfo.backupId, mUserPassword, mBackupHeaderInfo.salt, true, (errorCode, derivedServerKeyInfo) ->
                executeIfNotCancelled(() -> onGenerateBackupKey(errorCode, derivedServerKeyInfo)));
    }

    private void onGenerateBackupKey(@NonNull BaseService.ErrorCode errorCode, @Nullable DerivedServerKeyInfo derivedServerKeyInfo) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGenerateBackupPassword: errorCode=" + errorCode + " derivedServerKeyInfo=" + derivedServerKeyInfo);
        }

        if (errorCode != BaseService.ErrorCode.SUCCESS || derivedServerKeyInfo == null) {
            mBackupService.onRestoreError(BackupService.ErrorCode.KEY_GEN_FAILED, errorCode);
            cancel(BackupService.TerminateReason.ERROR);
            return;
        }

        if (mBackupHeaderInfo == null) {
            mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            cancel(BackupService.TerminateReason.ERROR);
            return;
        }

        mBackupService.onBackupHeader(mBackupHeaderInfo, derivedServerKeyInfo.lastBackupId, derivedServerKeyInfo.lastBackupTimestamp);

        setRestoreState(RestoreState.RESTORE_ACCOUNT);

        if (mInputStream == null) {
            mBackupService.onRestoreError(BackupService.ErrorCode.IO_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            cancel(BackupService.TerminateReason.ERROR);
            return;
        }

        // After the plaintext header, the payload is encrypted
        mInputStream = mTwinlifeImpl.getCryptoService().wrapCryptoInputStream(mInputStream, derivedServerKeyInfo.derivedServerKey);

        if (mInputStream == null) {
            mBackupService.onRestoreError(BackupService.ErrorCode.KEY_GEN_FAILED, BaseService.ErrorCode.DECRYPT_ERROR);
            cancel(BackupService.TerminateReason.ERROR);
            return;
        }

        mDecoder = new BinaryDecoder(mInputStream);

        BackupHeaderInfo backupHeaderInfo = mBackupHeaderInfo;

        if (backupHeaderInfo == null) {
            Log.e(LOG_TAG, "No BackupHeaderInfo");
            mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            cancel(BackupService.TerminateReason.ERROR);
            return;
        }

        ConfigurationService.SecuredConfiguration accountConfiguration;
        try {
            // Read Account secured configuration, needed to log in with the backed-up account.
            UUID accountConfId = mDecoder.readUUID();
            if (!accountConfId.equals(AccountSecuredConfigurationHandler.SCHEMA_ID)) {
                Log.e(LOG_TAG, "Expected AccountSecuredConfiguration schema ID but got: " + accountConfId);
                mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_KEY, BaseService.ErrorCode.DECRYPT_ERROR);
                cancel(BackupService.TerminateReason.ERROR);

                return;
            }
        } catch (SerializerException e) {
            Log.e(LOG_TAG, "Error occurred while restoring account configuration", e);
            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_KEY, BaseService.ErrorCode.DECRYPT_ERROR);
            cancel(BackupService.TerminateReason.ERROR);
            return;
        }

        try {
            accountConfiguration = new AccountSecuredConfigurationHandler(mTwinlifeImpl.getConfigurationService()).restore(mDecoder, false);
            if (accountConfiguration == null) {
                Log.e(LOG_TAG, "Couldn't restore AccountSecuredConfiguration");
                mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.DECRYPT_ERROR);
                cancel(BackupService.TerminateReason.ERROR);
                return;
            }
            mAccountConfiguration = accountConfiguration;
        } catch (SerializerException e) {
            Log.e(LOG_TAG, "Error occurred while restoring account configuration", e);
            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.DECRYPT_ERROR);
            cancel(BackupService.TerminateReason.ERROR);
            return;
        }

        mInPlaceRestore = mInPlaceRequested != null ?
                mInPlaceRequested :
                mTwinlifeImpl.getAccountServiceImpl().isCurrentAccount(mAccountConfiguration);

        mTwinlifeImpl.getAccountServiceImpl().restoreChallenge(accountConfiguration, backupHeaderInfo.backupId, (authErrorCode, nothing) ->
                executeIfNotCancelled(() -> {
                    if (authErrorCode != BaseService.ErrorCode.SUCCESS) {
                        Log.e(LOG_TAG, "Restore auth failed: " + authErrorCode);
                        mBackupService.onRestoreError(BackupService.ErrorCode.REVOKED, authErrorCode);
                        cancel(BackupService.TerminateReason.ERROR);
                        return;
                    }

                    onRestoreAuthSuccess();
                }));
    }

    private void onRestoreAuthSuccess() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRestoreAuthSuccess");
        }

        setRestoreState(RestoreState.PREPARE_DATABASE);

        if (mAccountConfiguration == null) {
            Log.e(LOG_TAG, "No mAccountConfiguration");
            mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            cancel(BackupService.TerminateReason.ERROR);
            return;
        }

        BaseService.ErrorCode errorCode = mTwinlifeImpl.prepareDatabaseForRestore(mInPlaceRestore);

        if (errorCode != BaseService.ErrorCode.SUCCESS) {
            Log.e(LOG_TAG, "Error while preparing database for restore: " + errorCode);
            mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, errorCode);
            cancel(BackupService.TerminateReason.ERROR);
            return;
        }

        restoreData();
    }

    private void restoreData() {
        if (DEBUG) {
            Log.d(LOG_TAG, "restoreData");
        }

        executeIfNotCancelled(() -> {
            setRestoreState(RestoreState.RESTORE_DATA);

            if (mDecoder == null) {
                mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
                cancel(BackupService.TerminateReason.ERROR);
                return;
            }

            while (!mDecoder.isEof()) {
                if (getRestoreState() != RestoreState.CANCEL) {
                    try {
                        UUID schemaId = mDecoder.readUUID();
                        BackupHandler<?> handler = mHandlers.get(schemaId);

                        if (handler == null) {
                            Log.e(LOG_TAG, "No handler found for schemaId " + schemaId);
                            mBackupService.onRestoreError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.DECRYPT_ERROR);
                            cancel(BackupService.TerminateReason.ERROR);
                            return;
                        } else {
                            handler.restore(mDecoder, mInPlaceRestore);
                        }
                    } catch (SerializerException e) {
                        Log.e(LOG_TAG, "Error occurred while restoring data", e);

                        BackupService.ErrorCode errorCode = (e.getCause() instanceof IOException) ? BackupService.ErrorCode.IO_ERROR : BackupService.ErrorCode.INVALID_FILE;
                        mBackupService.onRestoreError(errorCode, BaseService.ErrorCode.DECRYPT_ERROR);

                        cancel(BackupService.TerminateReason.ERROR);
                        return;
                    }
                } else {
                    cancel(BackupService.TerminateReason.CANCEL);
                    return;
                }
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

            for (BackupHandler<?> handler : mHandlers.values()) {
                Map<UUID, Integer> stats = handler.getStats();
                if (stats != null) {
                    // TODO BKP: necessary? At the moment we pass the results of checkTwincodeConsistency()
                    // to the UI, ignoring the contents of mStats.
                    mStats.putAll(stats);
                }
            }

            checkTwincodeConsistency((status, restoreContent) -> {
                if (getRestoreState() == RestoreState.CANCEL) {
                    cancel(BackupService.TerminateReason.CANCEL);
                    return;
                }

                if (status != BaseService.ErrorCode.SUCCESS) {
                    Log.e(LOG_TAG, "Error occurred while checking twincodes");
                    mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, status);
                    cancel(BackupService.TerminateReason.ERROR);
                } else {
                    setRestoreState(RestoreState.WAIT_CONFIRM);
                }
            });
        });
    }

    private void checkTwincodeConsistency(@NonNull Consumer<RestoreContent> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkTwincodeConsistency");
        }

        final TwincodeOutboundServiceImpl twincodeService = mTwinlifeImpl.getTwincodeOutboundServiceImpl();

        twincodeService.getAllTwincodes((status, serverTwincodes) ->
                executeIfNotCancelled(() -> {
                    if (status != BaseService.ErrorCode.SUCCESS || serverTwincodes == null) {
                        if (DEBUG) {
                            Log.d(LOG_TAG, "Error occurred while getting twincodes: status=" + status);
                        }
                        complete.onGet(status, null);
                        return;
                    }

                    if (DEBUG) {
                        Log.d(LOG_TAG, "Got " + serverTwincodes.size() + " server twincodes");
                    }

                    final List<TwincodeOutbound> localTwincodes = twincodeService.getLocalTwincodes();

                    if (DEBUG) {
                        Log.d(LOG_TAG, "Got " + localTwincodes.size() + " local twincodes");
                    }

                    final Map<UUID, List<UUID>> activeTwincodeIds = new HashMap<>();

                    // Find active twincodes (exist both in the backup and on the server)
                    for (Map.Entry<UUID, List<TwincodeInfo>> serverTwincodeInfos : serverTwincodes.entrySet()) {
                        for (Iterator<TwincodeInfo> serverTwincodeIt = serverTwincodeInfos.getValue().iterator(); serverTwincodeIt.hasNext(); ) {
                            TwincodeInfo twincodeInfo = serverTwincodeIt.next();

                            for (Iterator<TwincodeOutbound> localTwincodesIt = localTwincodes.iterator(); localTwincodesIt.hasNext(); ) {
                                TwincodeOutbound localTwincode = localTwincodesIt.next();

                                if (twincodeInfo.twincodeOutboundId.equals(localTwincode.getId())) {
                                    serverTwincodeIt.remove();
                                    localTwincodesIt.remove();

                                    List<UUID> activeTwincodes = activeTwincodeIds.get(serverTwincodeInfos.getKey());
                                    if (activeTwincodes == null) {
                                        activeTwincodes = new ArrayList<>();
                                        activeTwincodeIds.put(serverTwincodeInfos.getKey(), activeTwincodes);
                                    }
                                    activeTwincodes.add(twincodeInfo.twincodeOutboundId);
                                    break;
                                }
                            }
                        }
                    }

                    List<RepositoryObject> localObjects = mTwinlifeImpl.getRepositoryServiceImpl().getLocalObjects(mSupportedSchemaIds);

                    // Remove local twincodes that don't belong to us. We can't use isOwner() as twincodes created before 2025 don't have the OWNER flag.
                    for (Iterator<TwincodeOutbound> localTwincodesIt = localTwincodes.iterator(); localTwincodesIt.hasNext(); ) {
                        boolean found = false;
                        TwincodeOutbound localTwincode = localTwincodesIt.next();
                        for (RepositoryObject object : localObjects) {
                            TwincodeOutbound objectTwincode = object.getTwincodeOutbound();
                            if (objectTwincode != null && objectTwincode.getId().equals(localTwincode.getId())) {
                                found = true;
                                break;
                            }
                        }

                        if (found) {
                            localTwincodesIt.remove();
                        }
                    }

                    List<TwincodeInfo> oldServerTwincodes = serverTwincodes.get(NIL_UUID);

                    if (oldServerTwincodes != null && !oldServerTwincodes.isEmpty()) {
                        Log.w(LOG_TAG, "Old twincodes without schema ID found on server but not in the backup: " + oldServerTwincodes);
                    }

                    // Find only-local twincodes schema ID by searching for their corresponding repository object's schema ID.
                    Map<UUID, List<UUID>> onlyLocal = new HashMap<>();
                    for (TwincodeOutbound localTwincode : localTwincodes) {
                        for (RepositoryObject object : localObjects) {
                            TwincodeOutbound objectTwincode = object.getTwincodeOutbound();
                            if (objectTwincode != null && objectTwincode.getId().equals(localTwincode.getId())) {
                                UUID schemaId = object.getDatabaseId().getSchemaId();
                                List<UUID> twincodeIds = onlyLocal.get(schemaId);
                                if (twincodeIds == null) {
                                    twincodeIds = new ArrayList<>();
                                    onlyLocal.put(schemaId, twincodeIds);
                                }
                                twincodeIds.add(localTwincode.getId());
                                break;
                            }
                        }
                    }

                    // Do the same for old active twincodes: the server doesn't know their schema ID.
                    List<UUID> oldActiveTwincodes = activeTwincodeIds.get(NIL_UUID);
                    if (oldActiveTwincodes != null) {
                        Iterator<UUID> oldTwincodesIt = oldActiveTwincodes.iterator();
                        while (oldTwincodesIt.hasNext()) {
                            UUID oldTwincodeId = oldTwincodesIt.next();
                            for (RepositoryObject object : localObjects) {
                                TwincodeOutbound objectTwincode = object.getTwincodeOutbound();
                                if (objectTwincode != null && objectTwincode.getId().equals(oldTwincodeId)) {
                                    UUID schemaId = object.getDatabaseId().getSchemaId();
                                    List<UUID> twincodeIds = activeTwincodeIds.get(schemaId);
                                    if (twincodeIds == null) {
                                        twincodeIds = new ArrayList<>();
                                        activeTwincodeIds.put(schemaId, twincodeIds);
                                    }
                                    twincodeIds.add(oldTwincodeId);
                                    break;
                                }
                            }
                            oldTwincodesIt.remove();
                        }

                        if (!oldActiveTwincodes.isEmpty()) {
                            Log.w(LOG_TAG, "Old active twincodes have no corresponding repository object: " + oldActiveTwincodes);
                        }
                    }

                    //TODO BKP: compute modified? Twincodes should be doable, not objects if not in-place
                    mRestoreContent = new RestoreContent(serverTwincodes, onlyLocal, Collections.emptyMap(), activeTwincodeIds);

                    if (DEBUG) {
                        Log.d(LOG_TAG, "Twincode consistency check results:"+mRestoreContent);
                    }

                    complete.onGet(status, mRestoreContent);
                }));
    }

    void commit() {
        if (DEBUG) {
            Log.d(LOG_TAG, "commit");
        }

        executeIfNotCancelled(() -> {
            setRestoreState(RestoreState.COMMIT);

            mTwinlifeImpl.getAccountServiceImpl().commitRestore((errorCode, restoreCount) -> {

                if (errorCode != BaseService.ErrorCode.SUCCESS || restoreCount == null) {
                    Log.e(LOG_TAG, "Error while committing restore: " + errorCode);

                    if (errorCode == BaseService.ErrorCode.TWINLIFE_OFFLINE) {
                        if (mAccountConfiguration == null || mBackupHeaderInfo == null) {
                            Log.e(LOG_TAG, "Missing data to retry auth: mAccountConfiguration=" + mAccountConfiguration + ", mBackupHeaderInfo=" + mBackupHeaderInfo);
                        } else if (mRetryReconnect-- > 0) {
                            mTwinlifeImpl.getAccountServiceImpl().onDisconnect();
                            mTwinlifeImpl.getAccountServiceImpl().restoreChallenge(mAccountConfiguration, mBackupHeaderInfo.backupId, (authErrorCode, nothing) -> {
                                // Ignore the result, we'll try to reconnect again a couple of times if AccountServiceImpl.commitRestore() fails again.
                                executeIfNotCancelled(this::commit);
                            });
                            return;
                        }
                    }

                    mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, errorCode);
                    cancel(BackupService.TerminateReason.ERROR);
                    return;
                }

                ConfigurationService.SecuredConfiguration accountConfiguration = mAccountConfiguration;
                if (accountConfiguration == null) {
                    Log.e(LOG_TAG, "Error while committing restore: no SecuredAccountConfiguration");
                    mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
                    cancel(BackupService.TerminateReason.ERROR);
                    return;
                }

                BaseService.ErrorCode imagesErrorCode = mTwinlifeImpl.getImageServiceImpl().commitRestoredImages();
                if (imagesErrorCode != BaseService.ErrorCode.SUCCESS) {
                    Log.e(LOG_TAG, "Error while committing restored images");
                    mBackupService.onRestoreError(BackupService.ErrorCode.IO_ERROR, BaseService.ErrorCode.NO_STORAGE_SPACE);
                    cancel(BackupService.TerminateReason.ERROR);
                    return;
                }

                boolean dbMoveSuccess = mTwinlifeImpl.commitRestoredDatabase();
                if (!dbMoveSuccess) {
                    Log.e(LOG_TAG, "Could not move restored DB to main DB");
                    mBackupService.onRestoreError(BackupService.ErrorCode.IO_ERROR, BaseService.ErrorCode.DATABASE_ERROR);
                    cancel(BackupService.TerminateReason.ERROR);
                    return;
                }

                mTwinlifeImpl.getAccountServiceImpl().restoreAccountSecuredConfiguration(accountConfiguration, restoreCount);

                prepareObjectsForUpdate();
                updateObjectsAfterRestore();
            });
        });
    }

    private void prepareObjectsForUpdate() {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareObjectsForUpdate");
        }

        setRestoreState(RestoreState.SYNCING_OBJECTS);

        if (mRestoreContent == null) {
            Log.e(LOG_TAG, "mRestoreContent is null");
            mBackupService.onRestoreError(BackupService.ErrorCode.INTERNAL_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            return;
        }

        mAddedTwincodes.clear();
        mDeletedObjects.clear();
        mActiveObjects.clear();

        mAddedTwincodes.addAll(mRestoreContent.getAddedTwincodeInfos());

        List<UUID> deletedTwincodeIds = mRestoreContent.getDeletedTwincodeIds();

        List<RepositoryObject> objects = mTwinlifeImpl.getRepositoryServiceImpl().getLocalObjects(mSupportedSchemaIds);
        for (RepositoryObject object : objects) {
            // Ignore objects without twincodeOutbounds (space, space settings)
            if (object.getTwincodeOutbound() != null) {
                if (deletedTwincodeIds.contains(object.getTwincodeOutbound().getId())) {
                    mDeletedObjects.add(object);
                } else {
                    mActiveObjects.add(object);
                }
            }
        }
    }

    private void updateObjectsAfterRestore() {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateObjectsAfterRestore");
        }

        List<TwincodeInfo> serverTwincodes = new ArrayList<>(mAddedTwincodes);
        for (TwincodeInfo twincodeInfo : serverTwincodes) {
            mTwinlifeImpl.getTwincodeFactoryService().deleteTwincode(twincodeInfo.twincodeFactoryId,
                    (errorCode, deletedTwincodeId) -> executeIfNotCancelled(() -> {
                        if (errorCode != BaseService.ErrorCode.SUCCESS) {
                            Log.e(LOG_TAG, "Error deleting twincodeFactory " + twincodeInfo.twincodeFactoryId + ": " + errorCode);
                        } else {
                            if (DEBUG) {
                                Log.d(LOG_TAG, "Deleted twincodeFactory: " + twincodeInfo.twincodeFactoryId);
                            }
                        }

                        if (errorCode == BaseService.ErrorCode.TWINLIFE_OFFLINE) {
                            //wait for reconnection
                            return;
                        }

                        if (errorCode != BaseService.ErrorCode.SUCCESS) {
                            mServerTwincodeSyncErrors.add(twincodeInfo.twincodeOutboundId);
                        }

                        mAddedTwincodes.remove(twincodeInfo);
                        checkIfSyncingDone();
                    }));
        }

        List<RepositoryObject> deviceObjects = new ArrayList<>(mDeletedObjects);
        for (RepositoryObject object : deviceObjects) {
            mTwinlifeImpl.getRepositoryServiceImpl().deleteObjectAfterRestore(mTwinlifeContext, object,
                    (errorCode, deletedObjectId) -> executeIfNotCancelled(() -> {
                        if (errorCode != BaseService.ErrorCode.SUCCESS) {
                            Log.e(LOG_TAG, "Error deleting object " + object + ": " + errorCode);
                        } else {
                            if (DEBUG) {
                                Log.d(LOG_TAG, "Deleted object: " + deletedObjectId);
                            }
                        }

                        if (errorCode == BaseService.ErrorCode.TWINLIFE_OFFLINE) {
                            //wait for reconnection
                            return;
                        }

                        if (errorCode != BaseService.ErrorCode.SUCCESS) {
                            mLocalObjectDeleteErrors.add(object);
                        }

                        mDeletedObjects.remove(object);
                        checkIfSyncingDone();
                    }));
        }

        List<RepositoryObject> activeObjects = new ArrayList<>(mActiveObjects);
        for (RepositoryObject object : activeObjects) {
            mTwinlifeImpl.getRepositoryServiceImpl().syncObjectAfterRestore(mTwinlifeContext, object,
                    (errorCode, updatedObject) -> executeIfNotCancelled(() -> {
                        if (errorCode != BaseService.ErrorCode.SUCCESS) {
                            Log.e(LOG_TAG, "Error syncing object " + object + ": " + errorCode);
                        } else {
                            if (DEBUG) {
                                Log.d(LOG_TAG, "Synced object: " + errorCode);
                            }
                        }

                        if (errorCode == BaseService.ErrorCode.TWINLIFE_OFFLINE) {
                            //wait for reconnection
                            return;
                        }

                        if (errorCode != BaseService.ErrorCode.SUCCESS) {
                            mLocalObjectSyncErrors.add(object);
                        }

                        mActiveObjects.remove(object);
                        checkIfSyncingDone();
                    }));
        }
    }

    private void checkIfSyncingDone() {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkIfSyncingDone");
        }

        if (mActiveObjects.isEmpty() && mDeletedObjects.isEmpty() && mAddedTwincodes.isEmpty()) {
            if (!mLocalObjectSyncErrors.isEmpty() || !mLocalObjectDeleteErrors.isEmpty() || !mServerTwincodeSyncErrors.isEmpty()) {
                mBackupService.onSyncError(mServerTwincodeSyncErrors, mLocalObjectSyncErrors, mLocalObjectDeleteErrors);
            }

            setRestoreState(RestoreState.TERMINATED);
            mTerminateReason = BackupService.TerminateReason.SUCCESS;
            mBackupService.onTerminateRestore(BackupService.TerminateReason.SUCCESS);
            mExecutor.shutdown();
        }
    }

    void requestCancel() {
        setRestoreState(RestoreState.CANCEL);
        if (!mExecutor.isShutdown()) {
            try {
                mExecutor.execute(() -> cancel(BackupService.TerminateReason.CANCEL));
            } catch (RejectedExecutionException e) {
                Log.e(LOG_TAG, "Executor shut down, could not execute cancel", e);
            }
        }
    }

    private void cancel(@NonNull BackupService.TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "cancel");
        }

        if (getRestoreState() == RestoreState.TERMINATED) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Already terminated, abort cancel");
            }
            return;
        }

        mTerminateReason = terminateReason;

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

        mTwinlifeImpl.getAccountServiceImpl().rollbackRestore((errorCode, restoreCount) -> {
            // NOOP, the restore is canceled so we don't update the incarnation count.
        });


        boolean dbDeleted = mTwinlifeImpl.deleteRestoredDatabase();

        if (!dbDeleted) {
            Log.e(LOG_TAG, "Could not delete restored DB");

            mBackupService.onRestoreError(BackupService.ErrorCode.IO_ERROR, BaseService.ErrorCode.DATABASE_ERROR);
        }

        File restoredImages = new File(mTwinlifeImpl.getFilesDir(), ImageHandler.RESTORE_DIR);

        if (restoredImages.exists()) {
            boolean imagesDeleted = Utils.deleteDirectory(restoredImages);

            if (!imagesDeleted) {
                Log.e(LOG_TAG, "Could not delete restored image directory (" + restoredImages + ")");

                mBackupService.onRestoreError(BackupService.ErrorCode.IO_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            }
        }

        setRestoreState(RestoreState.TERMINATED);
        mBackupService.onTerminateRestore(terminateReason);

        mExecutor.shutdown();

        // Disconnect to reset the server-side session.
        mTwinlifeImpl.disconnect();
    }

    void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        executeIfNotCancelled(() -> {
            if (getRestoreState() == RestoreState.SYNCING_OBJECTS) {
                updateObjectsAfterRestore();
            }
        });
    }

    synchronized RestoreState getRestoreState() {
        return mRestoreState;
    }

    private void setRestoreState(@NonNull RestoreState restoreState) {
        synchronized (this) {
            mRestoreState = restoreState;
        }
        mBackupService.onRestoreStateChange(restoreState, mRestoreContent);
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
}
