/*
 *  Copyright (c) 2020-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * A file that is being received.  The file output stream remains open while we are receiving it.
 * The SHA256 signature is computed while we write the output stream.
 * At the end, the close() method will verify the SHA256 signature.
 * If the signature is not correct, the file is removed and it must be transferred again.
 */
public class ReceivingFileInfo {
    private static final String LOG_TAG = "ReceivingFileInfo";
    private static final boolean DEBUG = false;

    @NonNull
    private final File mFile;
    @NonNull
    private final FileInfoImpl mFileInfo;
    @NonNull
    private final MessageDigest mDigest;
    @NonNull
    private final RandomAccessFile mOutputFile;
    private long mPosition;

    public ReceivingFileInfo(@NonNull File file, @NonNull FileInfoImpl fileInfo, long offset) throws IOException, NoSuchAlgorithmException {
        if (DEBUG) {
            Log.d(LOG_TAG, "ReceivingFileInfo: file=" + file + " fileInfo=" + fileInfo + " offset=" + offset);
        }

        mFile = file;
        mFileInfo = fileInfo;
        mDigest = MessageDigest.getInstance("SHA-256");
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs() && Logger.WARN) {
                Logger.warn(LOG_TAG, "Cannot create directory: ", parentDir);
            }
        }

        // We are receiving a first block and the file exists: remove it so that we start a fresh file.
        if (offset == 0 && mFile.exists() && mFile.length() > 0) {
            if (Logger.WARN) {
                Logger.warn(LOG_TAG, "Removing old file ", mFile);
            }
            Utils.deleteFile(LOG_TAG, mFile);
        }

        // Open the file in read/write mode so that we can proceed after interruption.
        mOutputFile = new RandomAccessFile(file.getPath(), "rw");
        mPosition = 0;

        // Setup to the correct position reading the file and computing its checksum.
        long fileSize = mFile.length();
        if (fileSize > 0) {
            byte[] data = new byte[64 * 1024];

            if (fileSize > offset) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Truncated file to ", offset, " to continue receiving data");
                }
                mOutputFile.setLength(offset);
            }

            while (mPosition < fileSize) {
                long remain = fileSize - mPosition;
                if (remain < data.length) {
                    data = new byte[(int)remain];
                }
                int size = mOutputFile.read(data);
                if (size <= 0) {
                    break;
                }
                mDigest.update(data, 0, size);
                mPosition += size;
                if (size != data.length) {
                    if (Logger.ERROR) {
                        Logger.error(LOG_TAG, "Read block too short missing ", (data.length - size), " bytes");
                    }
                    break;
                }
            }
        }
    }

    /**
     * Write on the receiving file stream and update the digest.
     *
     * @param data the data block
     * @param size the size in data block
     * @throws Exception if there is a problem writing or computing the digest.
     */
    public void write(byte[] data, int size) throws Exception {

        mOutputFile.write(data, 0, size);
        mDigest.update(data, 0, size);
        mPosition = mPosition + size;
    }

    /**
     * Get the current receiving file position.
     *
     * @return the current position.
     */
    public long getPosition() {

        return mPosition;
    }

    /**
     * Get the file index.
     *
     * @return the file index.
     */
    @NonNull
    public Integer getFileIndex() {

        return mFileInfo.getIndex();
    }

    /**
     * Close the receiving file stream and verify the SHA256 signature.
     * - If there is a write error, the file is removed.
     * - If the signature is invalid, the file is removed.
     * - If the signature is correct, the modification date is updated.
     * @param expectSha256 the expected SHA256 signature.
     * @return true if the file was received correctly.
     */
    public boolean close(byte[] expectSha256) {

        try {
            mOutputFile.close();
            byte[] sha256 = mDigest.digest();
            if (!Arrays.equals(sha256, expectSha256)) {
                if (Logger.WARN) {
                    Logger.warn(LOG_TAG, "Signature is invalid for ", mFile);
                }

            } else if (!mFile.setLastModified(mFileInfo.getModificationDate())) {
                if (Logger.WARN) {
                    Logger.warn(LOG_TAG, "Cannot set modification date for ", mFile);
                }

            } else {
                return true;
            }

        } catch (Exception exception) {
            if (Logger.WARN) {
                Logger.warn(LOG_TAG, "close", exception);
            }
        }

        Utils.deleteFile(LOG_TAG, mFile);
        mFileInfo.setRemoteOffset(0);
        return false;
    }

    /**
     * Close the receiving file stream without verification.
     * If there is a write error, the file is removed.
     *
     * @return true if the file was received correctly.
     */
    public boolean close() {

        try {
            mOutputFile.close();
            return true;

        } catch (Exception exception) {
            Logger.warn(LOG_TAG, "close", exception);

        }

        Utils.deleteFile(LOG_TAG, mFile);
        mFileInfo.setRemoteOffset(0);
        return false;
    }

    /**
     * Cancel receiving the file.
     * The file is not removed because we may want to restart the transfer.
     */
    public void cancel() {

        try {
            mOutputFile.close();
        } catch (Exception exception) {
            Logger.debug(LOG_TAG, "close", exception);
        }
    }
}
