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

import org.twinlife.twinlife.BackupService;
import org.twinlife.twinlife.BackupService.BackupState;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.account.DerivedServerKeyInfo;
import org.twinlife.twinlife.backup.handlers.AccountSecuredConfigurationHandler;
import org.twinlife.twinlife.backup.handlers.BackupHeaderHandler;
import org.twinlife.twinlife.backup.handlers.ImageHandler;
import org.twinlife.twinlife.backup.handlers.RepositoryObjectHandler;
import org.twinlife.twinlife.backup.handlers.TwincodeInboundHandler;
import org.twinlife.twinlife.backup.handlers.TwincodeOutboundHandler;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BackupExecutor {
    private static final String LOG_TAG = "BackupExecutor";
    private static final boolean DEBUG = false;

    private static final int SALT_LENGTH = 64;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "backupThread"));

    private final List<BackupHandler<?>> mUnencryptedHandlers = new ArrayList<>();
    private final List<BackupHandler<?>> mEncryptedHandlers = new ArrayList<>();

    @NonNull
    private final BackupServiceImpl mBackupService;
    @NonNull
    private final TwinlifeImpl mTwinlifeImpl;
    @NonNull
    private final byte[] mUserPassword;
    @NonNull
    private final List<UUID> mSupportedSchemaIds;
    @Nullable
    private File mBackupFile;
    private final long mDate;
    @NonNull
    private final UUID mBackupId;
    @NonNull
    private final byte[] mSalt;
    @NonNull
    private BackupState mBackupState = BackupState.STARTING;
    @NonNull
    private final Map<UUID, Integer> mStats = new HashMap<>();

    public BackupExecutor(@NonNull BackupServiceImpl backupService, @NonNull byte[] password, @NonNull List<UUID> supportedSchemaIds) throws IOException {
        mBackupService = backupService;
        mTwinlifeImpl = backupService.getTwinlifeImpl();
        mUserPassword = password;
        mSupportedSchemaIds = supportedSchemaIds;
        mDate = System.currentTimeMillis();
        mBackupId = UUID.randomUUID();
        mSalt = new byte[SALT_LENGTH];
        new SecureRandom().nextBytes(mSalt);

        if (!createBackupFile()) {
            Log.e(LOG_TAG, "backup file not created");

            mExecutor.shutdown();
            throw new IOException("Couldn't create backup file");
        }

        initHandlers();

        mBackupService.onBackupStateChange(mBackupId, mBackupState);
    }

    void startBackup() {
        if (DEBUG) {
            Log.d(LOG_TAG, "startBackup");
        }

        mExecutor.execute(this::internalStartBackup);
    }

    private void internalStartBackup() {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalStartBackup");
        }

        mBackupState = BackupState.GENERATE_KEY;
        mBackupService.onBackupStateChange(mBackupId, mBackupState);

        mTwinlifeImpl.getTwinlifeExecutor().execute(() ->
                mTwinlifeImpl.getAccountServiceImpl().generateBackupKey(mBackupId, mUserPassword, mSalt, false,
                        (errorCode, derivedServerKey) ->
                                mExecutor.execute(() -> BackupExecutor.this.onGenerateKey(errorCode, derivedServerKey))));
    }

    private void onGenerateKey(@NonNull BaseService.ErrorCode errorCode, @Nullable DerivedServerKeyInfo derivedServerKey) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGeneratePassword: errorCode=" + errorCode + " derivedServerKey=" + derivedServerKey);
        }

        if (errorCode != BaseService.ErrorCode.SUCCESS || derivedServerKey == null) {
            handleError(BackupService.ErrorCode.KEY_GEN_FAILED, errorCode);
            return;
        }

        mBackupState = BackupState.CREATE_FILE;
        mBackupService.onBackupStateChange(mBackupId, mBackupState);

        if (mBackupFile == null) {
            handleError(BackupService.ErrorCode.INVALID_FILE, BaseService.ErrorCode.FILE_NOT_FOUND);
            return;
        }

        //noinspection IOStreamConstructor (needs SDK 26)
        try (OutputStream outputStream = new FileOutputStream(mBackupFile)) {
            BinaryEncoder encoder = new BinaryEncoder(outputStream);

            for (BackupHandler<?> handler : mUnencryptedHandlers) {
                handler.backup(encoder);
            }

            try (OutputStream encryptedOutputStream = mTwinlifeImpl.getCryptoService().wrapCryptoOutputStream(outputStream, derivedServerKey.derivedServerKey)) {

                if (encryptedOutputStream == null) {
                    Log.e(LOG_TAG, "CryptoOutputStream creation failed");
                    handleError(BackupService.ErrorCode.KEY_GEN_FAILED, BaseService.ErrorCode.ENCRYPT_ERROR);
                    return;
                }

                encoder = new BinaryEncoder(encryptedOutputStream);
                for (BackupHandler<?> handler : mEncryptedHandlers) {
                    handler.backup(encoder);
                    Map<UUID, Integer> stats = handler.getStats();
                    if (stats != null) {
                        mStats.putAll(stats);
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(LOG_TAG, "Encrypted file creation failed", t);
            handleError(BackupService.ErrorCode.IO_ERROR, BaseService.ErrorCode.LIBRARY_ERROR);
            return;
        }

        if (DEBUG) {
            Log.d(LOG_TAG, "backup done, file: " + mBackupFile.getAbsolutePath());
        }

        mBackupService.onTerminateBackup(mBackupId, mBackupFile.getAbsolutePath(), mStats);
        mExecutor.shutdown();
    }

    private void initHandlers() {
        mUnencryptedHandlers.clear();
        mUnencryptedHandlers.add(new BackupHeaderHandler(mBackupId, mDate, mSalt, mTwinlifeImpl.getApplicationName(), mTwinlifeImpl.getApplicationVersion(), BackupConfig.FILE_SIGNATURE));

        File filesDir = mTwinlifeImpl.getFilesDir();

        mEncryptedHandlers.clear();
        mEncryptedHandlers.add(new AccountSecuredConfigurationHandler(mTwinlifeImpl.getConfigurationService()));
        mEncryptedHandlers.add(new ImageHandler(mTwinlifeImpl.getImageServiceImpl(), filesDir));
        mEncryptedHandlers.add(new TwincodeOutboundHandler(mTwinlifeImpl.getTwincodeOutboundServiceImpl(), mTwinlifeImpl.getCryptoService()));
        mEncryptedHandlers.add(new TwincodeInboundHandler(mTwinlifeImpl.getTwincodeInboundServiceImpl(), mTwinlifeImpl.getTwincodeOutboundServiceImpl()));
        mEncryptedHandlers.add(new RepositoryObjectHandler(mTwinlifeImpl.getRepositoryServiceImpl(), mSupportedSchemaIds));
    }

    private boolean createBackupFile() {
        if (DEBUG) {
            Log.d(LOG_TAG, "createBackupFile");
        }

        String shortUUID = mBackupId.toString().length() >= 8  ? mBackupId.toString().substring(0, 8) : mBackupId.toString();

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String fileName = "backup-" + shortUUID + "-" + simpleDateFormat.format(new Date()) + "." + BuildConfig.BACKUP_EXTENSION;
        mBackupFile = new File(mTwinlifeImpl.getCacheDir(), fileName);

        if (mBackupFile.exists()) {
            boolean deleted = mBackupFile.delete();
            if (!deleted) {
                Log.e(LOG_TAG, "Could not delete file " + mBackupFile);
                return false;
            }
        }

        boolean created;
        try {
            created = mBackupFile.createNewFile();
        } catch (IOException exception) {
            Log.e(LOG_TAG, "Could not create file " + mBackupFile, exception);
            return false;
        }

        return created;
    }

    private void handleError(@NonNull BackupService.ErrorCode backupError, @NonNull BaseService.ErrorCode baseError) {
        Log.e(LOG_TAG, "handleError: backupError=" + backupError + " baseError=" + baseError);

        if (mBackupFile != null) {
            Utils.deleteFile(LOG_TAG, mBackupFile);
        }

        mBackupService.onBackupError(backupError, baseError);
        mExecutor.shutdown();
    }
}
