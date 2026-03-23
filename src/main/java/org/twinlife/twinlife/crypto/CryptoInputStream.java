/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.crypto;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CryptoInputStream extends FilterInputStream {
    private static final String LOG_TAG = "CryptoInputStream";

    @NonNull
    private final CryptoBox mCryptoBox;

    @NonNull
    private byte[] mBuffer;

    private int mCount;
    private int mPos;

    public CryptoInputStream(@NonNull InputStream in, @NonNull CryptoBox cryptoBox) {
        super(in);
        mCryptoBox = cryptoBox;
        mBuffer = new byte[0];
        mCount = 0;
        mPos = 0;
    }

    @Override
    public int available() throws IOException {
        return in.available() + mCount - mPos;
    }

    @Override
    public int read() throws IOException {
        if (mPos >= mCount) {
            decryptBlock();
            if (mPos >= mCount) {
                return -1;
            }
        }
        return mBuffer[mPos++] & 0xff;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (mPos >= mCount) {
            decryptBlock();
            if (mPos >= mCount) {
                return -1;
            }
        }

        int read = 0;

        while (mPos < mCount && read < len) {
            int count = Math.min(len - read, mCount - mPos);
            System.arraycopy(mBuffer, mPos, b, off + read, count);
            mPos += count;
            read += count;

            if (mPos >= mCount) {
                decryptBlock();
            }
        }

        return read;
    }

    private void decryptBlock() throws IOException {
        byte[] count = new byte[4];
        byte[] nonceSequence = new byte[8];
        byte[] encryptedDataLength = new byte[4];

        int len = in.read(count);

        if (len == -1) {
            // End of encrypted stream
            mPos = 0;
            mCount = 0;
            return;
        }

        if (len != 4) {
            Log.e(LOG_TAG, "Error while reading count: expected 4 bytes, read " + len);
            throw new IOException("Decryption error");
        }

        len = in.read(nonceSequence);
        if (len != 8) {
            Log.e(LOG_TAG, "Error while reading nonceSequence: expected 8 bytes, read " + len);
            throw new IOException("Decryption error");
        }

        len = in.read(encryptedDataLength);
        if (len != 4) {
            Log.e(LOG_TAG, "Error while reading encrypted data length: expected 4 bytes, read " + len);
            throw new IOException("Decryption error");
        }

        if (bytesToInt(encryptedDataLength) < 0) {
            Log.e(LOG_TAG, "Invalid encrypted data length: " + bytesToInt(encryptedDataLength));
            throw new IOException("Decryption error");
        }

        byte[] encrypted = new byte[bytesToInt(encryptedDataLength)];
        len = in.read(encrypted);

        if (len != bytesToInt(encryptedDataLength)) {
            Log.e(LOG_TAG, "Error while reading encrypted data: expected " + bytesToInt(encryptedDataLength) + " bytes, read " + len);
            throw new IOException("Decryption error");
        }

        byte[] decrypted = new byte[bytesToInt(count)];

        len = mCryptoBox.decryptAEAD(bytesToLong(nonceSequence), encrypted, 4 + 8, decrypted);

        if (len <= 0 || len > decrypted.length) {
            Log.e(LOG_TAG, "Error while decrypting data: " + len);
            throw new IOException("Decryption error");
        }

        mBuffer = decrypted;
        mCount = len;
        mPos = 0;
    }

    private int bytesToInt(byte[] bytes) {
        int result = 0;
        for (int i = 0; i < 4; i++) {
            result = (result << 8) + (bytes[i] & 0xff);
        }
        return result;
    }

    private long bytesToLong(byte[] bytes) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result = (result << 8) + (bytes[i] & 0xff);
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        super.close();
        mCryptoBox.dispose();
    }
}
