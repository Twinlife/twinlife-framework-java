/*
 *  Copyright (c) 2020-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;

/**
 * A file that is being sent.  The file input stream remains open while we are sending it.
 * The SHA256 signature is computed while we read the input stream.
 * The signature is returned by getDigest() when the complete file was transferred.
 */
public class SendingFileInfo {
    private static final String LOG_TAG = "SendingFileInfo";

    @NonNull
    private final FileInfoImpl mFileInfo;
    @NonNull
    private final MessageDigest mDigest;
    @NonNull
    private final FileInputStream mInputStream;
    private long mPosition;

    public SendingFileInfo(@NonNull File file, @NonNull FileInfoImpl fileInfo) throws Exception {

        mFileInfo = fileInfo;
        mDigest = MessageDigest.getInstance("SHA-256");
        mInputStream = new FileInputStream(file);
        mPosition = 0;

        // Position to the correct position reading the file and computing its checksum.
        if (fileInfo.mRemoteOffset > 0) {
            byte[] data = new byte[32 * 1024];

            while (mPosition < fileInfo.mRemoteOffset) {
                long remain = fileInfo.mRemoteOffset - mPosition;
                if (remain < data.length) {
                    data = new byte[(int)remain];
                }
                int size = read(data);
                if (size <= 0) {
                    break;
                }
                if (size != data.length) {
                    if (Logger.ERROR) {
                        Logger.error(LOG_TAG, "Read block too short missing ",(data.length - size), " bytes");
                    }
                    break;
                }
            }
        }
    }

    @NonNull
    public Integer getFileIndex() {

        return mFileInfo.getIndex();
    }

    /**
     * Get the current receiving file position.
     *
     * @return the current position.
     */
    public long getPosition() {

        return mPosition;
    }

    public long getLength() {

        return mFileInfo.getSize();
    }

    /**
     * Check if we have transferred the complete file.
     *
     * @return true if we have transferred the complete file.
     */
    public boolean isFinished() {

        return mPosition == mFileInfo.getSize();
    }

    /**
     * Check if the data chunk was accepted by the peer.
     *
     * @param fileInfo the file info.
     * @param offset the offset acknowledged by the peer
     * @param queueSize the IQ data queue size (max IQ count * data chunk size).
     * @return true if this is accepted and we can continue sending.
     */
    public boolean isAcceptedDataChunk(@NonNull FileInfoImpl fileInfo, long offset, long queueSize) {

        if (!mFileInfo.getIndex().equals(fileInfo.getIndex())) {
            return true;
        }

        return mPosition <= offset + queueSize;
    }

    /**
     * Read a block of data from the file stream and update the digest.
     *
     * @param data the data block to read.
     * @return the number of bytes read.
     * @throws Exception if there is a problem reading or computing the digest.
     */
    public int read(@NonNull byte[] data) throws Exception {

        int size = mInputStream.read(data, 0, data.length);
        if (size > 0) {
            mDigest.update(data, 0, size);
            mPosition = mPosition + size;
        } else if (Logger.ERROR) {
            Logger.error(LOG_TAG, "Read returned ", size, " for ", mFileInfo);
        }
        return size;
    }

    /**
     * Close the sending file stream and return the SHA256 signature.
     *
     * @return the SHA256 signature.
     * @throws Exception if there is a problem computing the digest.
     */
    public byte[] getDigest() throws Exception {

        mInputStream.close();
        return mDigest.digest();
    }

    /**
     * Cancel sending the file.
     */
    public void cancel() {

        try {
            mInputStream.close();
        } catch (Exception exception) {
            if (Logger.DEBUG) {
                Logger.debug(LOG_TAG, "close", exception);
            }
        }
    }
}
