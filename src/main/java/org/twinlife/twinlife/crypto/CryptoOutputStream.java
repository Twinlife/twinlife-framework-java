/*
 *  Copyright (c) 2025-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.crypto;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class CryptoOutputStream extends FilterOutputStream {
    private static final String LOG_TAG = "CryptoOutputStream";

    @NonNull
    private final CryptoBox mCryptoBox;

    /**
     * The internal buffer where (unencrypted) data is stored.
     */
    @NonNull
    private final byte[] mBuffer;

    /**
     * The number of valid bytes in the buffer. This value is always
     * in the range {@code 0} through {@code buf.length}; elements
     * {@code buf[0]} through {@code buf[count-1]} contain valid
     * byte data.
     */
    private int mCount;

    private long mNonceSequence;

    /**
     * Creates a new buffered output stream to write data to the
     * specified underlying output stream with the specified buffer
     * size.
     *
     * @param out       the underlying output stream.
     * @param size      the buffer size.
     * @param cryptoBox the crypto box to use for encryption.
     * @throws IllegalArgumentException if size &lt;= 0.
     */
    public CryptoOutputStream(@NonNull OutputStream out, int size, @NonNull CryptoBox cryptoBox) {
        super(out);

        if (size <= 0) {
            throw new IllegalArgumentException("Buffer size <= 0");
        }
        mBuffer = new byte[size];

        mCryptoBox = cryptoBox;
        mCount = 0;
        mNonceSequence = 0;
    }

    /**
     * Writes the specified byte to this buffered output stream.
     *
     * @param b the byte to be written.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void write(int b) throws IOException {
        if (mCount >= mBuffer.length) {
            flushBuffer();
        }

        mBuffer[mCount++] = (byte) b;
    }

    /**
     * Writes {@code len} bytes from the specified byte array
     * starting at offset {@code off} to this buffered output stream.
     *
     * <p> Ordinarily this method stores bytes from the given array into this
     * stream's buffer, flushing the buffer to the underlying output stream as
     * needed.  If the requested length is at least as large as this stream's
     * buffer, however, then this method will flush the buffer and write the
     * bytes directly to the underlying output stream.  Thus redundant
     * {@code BufferedOutputStream}s will not copy data unnecessarily.
     *
     * @param b   the data.
     * @param off the start offset in the data.
     * @param len the number of bytes to write.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public synchronized void write(byte[] b, int off, int len) throws IOException {
        while (len >= mBuffer.length) {
            flushBuffer();
            System.arraycopy(b, off, mBuffer, mCount, mBuffer.length);
            mCount = mBuffer.length;
            off += mBuffer.length;
            len -= mBuffer.length;
        }

        if (len > mBuffer.length - mCount) {
            flushBuffer();
        }

        System.arraycopy(b, off, mBuffer, mCount, len);
        mCount += len;
    }

    /**
     * Encrypt and flush the internal buffer
     */
    private void flushBuffer() throws IOException {
        if (mCount > 0) {
            byte[] auth = new byte[4 + 8];

            System.arraycopy(intToBytes(mCount), 0, auth, 0, 4);
            System.arraycopy(longToBytes(mNonceSequence), 0, auth, 4, 8);

            byte[] encrypted = new byte[auth.length + mBuffer.length + 64];
            int len = mCryptoBox.encryptAEAD(mNonceSequence, mBuffer, mCount, auth, encrypted);
            if (len <= 0) {
                Log.e(LOG_TAG, "encryption failed: " + len);
                throw new IOException("encryption failed: " + len);
            }
            out.write(auth);
            out.write(intToBytes(len));
            out.write(encrypted, 0, len);

            mNonceSequence++;
            mCount = 0;
        }
    }

    private byte[] longToBytes(long l) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (l & 0xFF);
            l >>= 8;
        }
        return result;
    }

    private byte[] intToBytes(int i) {
        byte[] result = new byte[4];
        for (int j = 3; j >= 0; j--) {
            result[j] = (byte) (i & 0xFF);
            i >>= 8;
        }
        return result;
    }

    /**
     * Flushes this buffered output stream. This forces any buffered
     * output bytes to be written out to the underlying output stream.
     *
     * @throws IOException if an I/O error occurs.
     * @see java.io.FilterOutputStream#out
     */
    @Override
    public void flush() throws IOException {
        flushBuffer();
        out.flush();
    }

    @Override
    public void close() throws IOException {
        super.close();
        mCryptoBox.dispose();
    }
}
