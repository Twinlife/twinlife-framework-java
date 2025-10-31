/*
 *  Copyright (c) 2015-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 *  Based on:
 *   org.apache.avro.io.DirectBinaryDecoder.java
 *   org.apache.avro.io.BinaryDecoder.java
 *   org.apache.avro.io.BinaryData.java
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.SerializerException;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BinaryDecoder implements Decoder {
    private static final String LOG_TAG = "BinaryDecoder";
    private static final boolean DEBUG = false;

    @NonNull
    protected final InputStream mInputStream;
    private final byte[] mBuffer = new byte[8];

    public BinaryDecoder(@NonNull InputStream inputStream) {
        if (DEBUG) {
            Log.d(LOG_TAG, "BinaryDecoder: inputStream=" + inputStream);
        }

        mInputStream = inputStream;
    }
    @Override
    public boolean isEof() {

        try {
            return mInputStream.available() == 0;
        } catch (Exception ignored) {
            return true;
        }
    }

    @Override
    public boolean readBoolean() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readBoolean");
        }

        int value;
        try {
            value = mInputStream.read();
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
        if (value < 0) {
            throw new SerializerException();
        }

        return value != 0;
    }

    @Override
    public int readInt() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readInt");
        }

        int value = 0;
        int shift = 0;
        do {
            int b;
            try {
                b = mInputStream.read();
            } catch (Exception exception) {
                throw new SerializerException(exception);
            }
            if (b >= 0) {
                value |= (b & 0x7F) << shift;
                if ((b & 0x80) == 0) {

                    return (value >>> 1) ^ -(value & 1);
                }
            } else {
                throw new SerializerException();
            }
            shift += 7;
        } while (shift < 32);

        throw new SerializerException();
    }

    @Override
    public long readLong() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readLong");
        }

        long value = 0;
        int shift = 0;
        do {
            int b;
            try {
                b = mInputStream.read();
            } catch (Exception exception) {
                throw new SerializerException(exception);
            }
            if (b >= 0) {
                value |= (b & 0x7FL) << shift;
                if ((b & 0x80) == 0) {

                    return (value >>> 1) ^ -(value & 1);
                }
            } else {
                throw new SerializerException();
            }
            shift += 7;
        } while (shift < 64);

        throw new SerializerException();
    }

    @Override
    @NonNull
    public UUID readUUID() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readUUID");
        }

        long leastSignificantBits = readLong();
        long mostSignificantBits = readLong();

        return new UUID(mostSignificantBits, leastSignificantBits);
    }

    @Override
    @Nullable
    public UUID readOptionalUUID() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readOptionalUUID");
        }

        if (readBoolean()) {
            return readUUID();
        } else {
            return null;
        }
    }

    @Override
    public double readDouble() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readDouble");
        }

        doReadBytes(mBuffer, 0, 8);
        long value = (((long) mBuffer[0]) & 0xff) | ((((long) mBuffer[1]) & 0xff) << 8) | ((((long) mBuffer[2]) & 0xff) << 16) | ((((long) mBuffer[3]) & 0xff) << 24) |
                ((((long) mBuffer[4]) & 0xff) << 32) | ((((long) mBuffer[5]) & 0xff) << 40) | ((((long) mBuffer[6]) & 0xff) << 48) | ((((long) mBuffer[7]) & 0xff) << 56);

        return Double.longBitsToDouble(value);
    }

    @Override
    public int readEnum() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readEnum");
        }

        return readInt();
    }

    @Override
    @NonNull
    public String readString() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readString");
        }

        int length = readInt();
        if (length <= 0) {
            return "";
        }

        byte[] data = new byte[length];
        doReadBytes(data, 0, length);

        return Utf8.create(data, length);
    }

    @Override
    @Nullable
    public String readOptionalString() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readOptionalString");
        }

        if (readInt() == 1) {
            return readString();
        } else {
            return null;
        }
    }

    @Override
    @NonNull
    public ByteBuffer readBytes(@Nullable ByteBuffer buffer) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readBytes: buffer=" + buffer);
        }

        int length = readInt();
        ByteBuffer lBuffer;
        if (buffer != null && length <= buffer.capacity()) {
            lBuffer = buffer;
            lBuffer.clear();
        } else {
            lBuffer = ByteBuffer.allocate(length);
        }
        doReadBytes(lBuffer.array(), lBuffer.position(), length);
        lBuffer.limit(length);

        return lBuffer;
    }

    @Override
    @Nullable
    public byte[] readOptionalBytes(@Nullable ByteBuffer buffer) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readOptionalBytes: buffer=" + buffer);
        }

        if (readInt() == 1) {
            return readBytes(buffer).array();
        } else {
            return null;
        }
    }

    @Override
    public void readFixed(@NonNull byte[] bytes, int start, int length) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readFixed: bytes=" + Arrays.toString(bytes) + " start=" + start + " length=" + length);
        }

        doReadBytes(bytes, start, length);
    }

    @Override
    @Nullable
    public List<BaseService.AttributeNameValue> readAttributes() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "readAttributes");
        }

        int count = readInt();
        if (count == 0) {
            return null;
        }

        List<BaseService.AttributeNameValue> attributes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = readString();
            BaseService.AttributeNameValue attr;
            int value = readEnum();
            switch (value) {
                case 0:
                    attr = new BaseService.AttributeNameVoidValue(name);
                    break;

                case 1:
                    attr = new BaseService.AttributeNameBooleanValue(name, readBoolean());
                    break;

                case 2:
                    attr = new BaseService.AttributeNameLongValue(name, readLong());
                    break;

                case 3:
                    attr = new BaseService.AttributeNameStringValue(name, readString());
                    break;

                case 4:
                    attr = new BaseService.AttributeNameUUIDValue(name, readUUID());
                    break;

                case 5:
                {
                    List<BaseService.AttributeNameValue> list = readAttributes();
                    if (list == null) {
                        list = new ArrayList<>();
                    }
                    attr = new BaseService.AttributeNameListValue(name, list);
                }
                    break;

                default:
                    throw new SerializerException("Unsupported attribute");
            }
            attributes.add(attr);
        }

        return attributes;
    }

    //
    // Private Methods
    //

    private void doReadBytes(@NonNull byte[] bytes, int start, int length) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "doReadBytes: bytes=" + Arrays.toString(bytes) + " start=" + start + " length=" + length);
        }

        while (true) {
            int n;
            try {
                n = mInputStream.read(bytes, start, length);
            } catch (Exception exception) {
                throw new SerializerException(exception);
            }
            if (n == length || length == 0) {

                return;
            }
            if (n < 0) {
                throw new SerializerException();
            }
            start += n;
            length -= n;
        }
    }
}
