/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup.handlers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BackupService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.backup.BackupHandler;
import org.twinlife.twinlife.backup.BackupHeaderInfo;
import org.twinlife.twinlife.backup.VerifyResult;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.util.Arrays;
import java.util.UUID;

public class BackupHeaderHandler extends BackupHandler<BackupHeaderInfo> {
    private static final String LOG_TAG = "BackupHeaderHandler";
    private static final boolean DEBUG = false;

    public static final UUID SCHEMA_ID = UUID.fromString("7fe7023c-f2f3-4148-bf40-43ef04e7a86a");
    private static final int SCHEMA_VERSION = 2;

    private final long mDate;
    @Nullable
    private final UUID mBackupId;
    @Nullable
    private final byte[] mSalt;
    @Nullable
    private final String mApplicationName;
    @Nullable
    private final String mVersion;
    @NonNull
    private final byte[] mFileSignature;

    public BackupHeaderHandler(@NonNull byte[] fileSignature) {
        super();
        mDate = -1;
        mBackupId = null;
        mSalt = null;
        mApplicationName = null;
        mVersion = null;
        mFileSignature = fileSignature;
    }

    public BackupHeaderHandler(@NonNull UUID backupId, long date, @NonNull byte[] salt, @NonNull String applicationName, @NonNull String version, @NonNull byte[] fileSignature) {
        super();
        mDate = date;
        mBackupId = backupId;
        mSalt = salt;
        mApplicationName = applicationName;
        mVersion = version;
        mFileSignature = fileSignature;
    }

    @Override
    protected void initDeserializers() {
        mRestorers.put(BackupHeaderRestorerV1.VERSION, new BackupHeaderRestorerV1());
        mRestorers.put(BackupHeaderRestorerV2.VERSION, new BackupHeaderRestorerV2());
    }

    public void backup(@NonNull BinaryEncoder encoder) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "backup: encoder=" + encoder);
        }

        if (mDate == -1 || mBackupId == null || mSalt == null || mApplicationName == null || mVersion == null) {
            Log.e(LOG_TAG, "handler not initialized : mDate=" + mDate + ", mBackupId=" + mBackupId + ", mSalt=" + Arrays.toString(mSalt) + ", applicationName=" + mApplicationName + ", version=" + mVersion);
            throw new SerializerException("date, ID and salt must be initialized to perform backup.");
        }

        encoder.writeData(mFileSignature);
        encoder.writeInt(SCHEMA_VERSION);
        encoder.writeLong(mDate);
        encoder.writeUUID(mBackupId);
        encoder.writeBytes(mSalt, 0, mSalt.length);
        encoder.writeString(mApplicationName);
        encoder.writeString(mVersion);

        if (DEBUG) {
            Log.d(LOG_TAG, "Backup header: date=" + mDate + ", backupId=" + mBackupId);
        }
    }

    @Nullable
    @Override
    public BackupHeaderInfo restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "restore: decoder=" + decoder + " inPlace=" + inPlace);
        }

        if (!checkSignature(decoder)) {
            Log.e(LOG_TAG, "Invalid file signature");
            return null;
        }

        return super.restore(decoder, inPlace);
    }

    @NonNull
    @Override
    public VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "verify: decoder=" + decoder);
        }

        if (!checkSignature(decoder)) {
            Log.e(LOG_TAG, "Invalid file signature");
            throw new BackupService.WrongAppException("Invalid file signature");
        }

        int version = decoder.readInt();
        Restorer<BackupHeaderInfo> restorer = mRestorers.get(version);
        if (restorer == null) {
            throw new BackupService.WrongVersionException("No restorer found for version " + version);
        }

        return restorer.verify(decoder);
    }

    public boolean checkSignature(@NonNull BinaryDecoder decoder) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkSignature: decoder=" + decoder);
        }

        byte[] signature = decoder.readBytes(null).array();

        return Arrays.equals(signature, mFileSignature);
    }

    private static class BackupHeaderRestorerV2 extends BackupHeaderRestorerV1 {
        public static final int VERSION = 2;
    }

    private static class BackupHeaderRestorerV1 implements Restorer<BackupHeaderInfo> {
        public static final int VERSION = 1;

        @NonNull
        @Override
        public BackupHeaderInfo restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException {
            if (DEBUG) {
                Log.d(LOG_TAG, "deserialize: decoder=" + decoder);
            }

            long date = decoder.readLong();
            UUID backupId = decoder.readUUID();
            byte[] salt = decoder.readBytes(null).array();
            String applicationName = decoder.readString();
            String version = decoder.readString();

            BackupHeaderInfo backupHeaderInfo = new BackupHeaderInfo(date, backupId, salt, applicationName, version);
            if (DEBUG) {
                Log.d(LOG_TAG, "decoded " + backupHeaderInfo);
            }

            return backupHeaderInfo;
        }

        @NonNull
        public VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException {
            return new VerifyResult.Present<>(restore(decoder, true), false);
        }
    }
}
