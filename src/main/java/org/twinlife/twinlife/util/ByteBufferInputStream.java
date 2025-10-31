/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import java.io.InputStream;
import java.nio.ByteBuffer;

public class ByteBufferInputStream extends InputStream {
    private final ByteBuffer mBuf;

    public ByteBufferInputStream(ByteBuffer buf) {

        mBuf = buf;
    }

    @Override
    public int read() {

        if (!mBuf.hasRemaining()) {
            return -1;
        }
        return mBuf.get() & 0xFF;
    }

    @Override
    public int read(byte[] bytes, int off, int len) {

        if (!mBuf.hasRemaining()) {
            return -1;
        }
        len = Math.min(len, mBuf.remaining());
        mBuf.get(bytes, off, len);
        return len;
    }
}