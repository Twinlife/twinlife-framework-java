/*
 *  Copyright (c) 2015-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 *  Based on:
 *   org.apache.avro.io.DirectBinaryEncoder.java
 *   org.apache.avro.io.BinaryEncoder.java
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
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;

import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class BinaryEncoder implements Encoder {
    private static final String LOG_TAG = "BinaryEncoder";
    private static final boolean DEBUG = false;

    @NonNull
    protected final OutputStream mOutputStream;
    protected final byte[] mBuffer = new byte[16];

    public BinaryEncoder(@NonNull OutputStream outputStream) {
        if (DEBUG) {
            Log.d(LOG_TAG, "BinaryEncoder: outputStream=" + outputStream);
        }

        mOutputStream = outputStream;
    }

    @Override
    public void writeBoolean(boolean value) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeBoolean: value=" + value);
        }

        try {
            mOutputStream.write(value ? 1 : 0);
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    @Override
    public void writeZero() throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeZero");
        }

        try {
            mOutputStream.write(0);
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    protected void writeNonNull() throws SerializerException {

        // Similar to writeInt(1)
        try {
            mOutputStream.write(2);
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    @Override
    public void writeInt(int value) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeInt: value=" + value);
        }

        try {
            int lValue = (value << 1) ^ (value >> 31);
            if ((lValue & ~0x7F) == 0) {
                mOutputStream.write(lValue);

                return;
            }

            if ((lValue & ~0x3FFF) == 0) {
                mOutputStream.write(0x80 | lValue);
                mOutputStream.write(lValue >>> 7);

                return;
            }

            int length = encodeInt(value, mBuffer);
            mOutputStream.write(mBuffer, 0, length);
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    @Override
    public void writeLong(long value) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeLong: value=" + value);
        }

        try {
            long lValue = (value << 1) ^ (value >> 63);
            if ((lValue & ~0x7FFFFFFFL) == 0) {
                int i = (int) lValue;
                while ((i & ~0x7F) != 0) {
                    mOutputStream.write((byte) ((0x80 | i) & 0xFF));
                    i >>>= 7;
                }
                mOutputStream.write((byte) i);

                return;
            }

            int length = encodeLong(value, mBuffer);
            mOutputStream.write(mBuffer, 0, length);
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    /**
     * Write the UUID in legacy mode (this form uses 4 bytes more than the compact binary form).
     *
     * @param value the UUID value to write.
     * @throws SerializerException when there is a serialization issue.
     */
    @Override
    public void writeUUID(@NonNull UUID value) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeUUID: value=" + value);
        }

        writeLong(value.getLeastSignificantBits());
        writeLong(value.getMostSignificantBits());
    }

    @Override
    public void writeOptionalUUID(@Nullable UUID value) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeOptionalUUID: value=" + value);
        }

        if (value == null) {
            writeZero();
        } else {
            writeNonNull();
            writeUUID(value);
        }
    }

    @Override
    public void writeDouble(double value) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeDouble: value=" + value);
        }

        try {
            int length = encodeDouble(value, mBuffer);
            mOutputStream.write(mBuffer, 0, length);
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    @Override
    public void writeEnum(int value) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeEnum: value=" + value);
        }

        writeInt(value);
    }

    @Override
    public void writeString(@NonNull String value) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeString: value=" + value);
        }

        try {
            if (value.isEmpty()) {
                writeZero();

                return;
            }

            byte[] bytes = Utf8.getBytes(value);
            writeInt(bytes.length);
            mOutputStream.write(bytes, 0, bytes.length);
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    @Override
    public void writeOptionalString(@Nullable String value) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeOptionalString: value=" + value);
        }

        if (value == null) {
            writeZero();
        } else {
            writeNonNull();
            writeString(value);
        }
    }

    @Override
    public void writeData(@NonNull byte[] bytes) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeBytes: bytes=" + Arrays.toString(bytes));
        }

        writeBytes(bytes, 0, bytes.length);
    }

    @Override
    public void writeOptionalBytes(@Nullable byte[] bytes) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeOptionalBytes: bytes=" + Arrays.toString(bytes));
        }

        if (bytes == null) {
            writeZero();
        } else {
            writeNonNull();
            writeBytes(bytes, 0, bytes.length);
        }
    }

    @Override
    public void writeBytes(@NonNull byte[] bytes, int start, int length) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeBytes: bytes=" + Arrays.toString(bytes) + " start=" + start + " length=" + length);
        }

        if (length == 0) {
            writeZero();
            return;
        }

        writeInt(length);
        writeFixed(bytes, start, length);
    }

    @Override
    public void writeFixed(@NonNull byte[] bytes, int start, int length) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeFixed: bytes=" + Arrays.toString(bytes) + " start=" + start + " length=" + length);
        }

        try {
            mOutputStream.write(bytes, start, length);
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    @Override
    public void writeAttributes(@Nullable List<BaseService.AttributeNameValue> attributes) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "serializeAttributes");
        }

        if (attributes == null) {
            writeInt(0);
            return;
        }

        writeInt(attributes.size());
        for (BaseService.AttributeNameValue attr : attributes) {
            writeAttribute(attr);
        }
    }

    public void writeAttribute(@NonNull BaseService.AttributeNameValue attr) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "serializeAttributes");
        }

        writeString(attr.name);
        if (attr instanceof BaseService.AttributeNameVoidValue) {
            writeEnum(0);
        } else if (attr.value instanceof Boolean) {
            writeEnum(1);
            writeBoolean((Boolean) attr.value);
        } else if (attr.value instanceof Long) {
            writeEnum(2);
            writeLong((Long) attr.value);
        } else if (attr.value instanceof String) {
            writeEnum(3);
            writeString((String) attr.value);
        } else if (attr.value instanceof UUID) {
            writeEnum(4);
            writeUUID((UUID) attr.value);
        } else if (attr.value instanceof ExportedImageId) {
            writeEnum(4);
            writeUUID(((ExportedImageId) attr.value).getExportedId());
        } else if (attr.value instanceof List) {
            writeEnum(5);
            writeAttributes((List) attr.value);
        } else {
            throw new SerializerException("Unsupported Attribute: " + attr);
        }
    }

    //
    // Private Methods
    //

    private static int encodeInt(int value, @NonNull byte[] buffer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "encodeInt: value=" + value + " buffer=" + Arrays.toString(buffer));
        }

        value = (value << 1) ^ (value >> 31);
        int position = 0;
        if ((value & ~0x7F) != 0) {
            buffer[position++] = (byte) ((value | 0x80) & 0xFF);
            value >>>= 7;
            if (value > 0x7F) {
                buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                value >>>= 7;
                if (value > 0x7F) {
                    buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                    value >>>= 7;
                    if (value > 0x7F) {
                        buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                        value >>>= 7;
                    }
                }
            }
        }
        buffer[position++] = (byte) value;

        return position;
    }

    private static int encodeLong(long value, @NonNull byte[] buffer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "encodeLong: value=" + value + " buffer=" + Arrays.toString(buffer));
        }

        value = (value << 1) ^ (value >> 63);
        int position = 0;
        if ((value & ~0x7FL) != 0) {
            buffer[position++] = (byte) ((value | 0x80) & 0xFF);
            value >>>= 7;
            if (value > 0x7F) {
                buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                value >>>= 7;
                if (value > 0x7F) {
                    buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                    value >>>= 7;
                    if (value > 0x7F) {
                        buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                        value >>>= 7;
                        if (value > 0x7F) {
                            buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                            value >>>= 7;
                            if (value > 0x7F) {
                                buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                                value >>>= 7;
                                if (value > 0x7F) {
                                    buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                                    value >>>= 7;
                                    if (value > 0x7F) {
                                        buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                                        value >>>= 7;
                                        if (value > 0x7F) {
                                            buffer[position++] = (byte) ((value | 0x80) & 0xFF);
                                            value >>>= 7;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        buffer[position++] = (byte) value;

        return position;
    }

    @SuppressWarnings("SameReturnValue")
    private static int encodeFloat(float value, @NonNull byte[] buffer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "encodeFloat: value=" + value + " buffer=" + Arrays.toString(buffer));
        }

        int bits = Float.floatToRawIntBits(value);
        buffer[0] = (byte) ((bits) & 0xFF);
        buffer[1] = (byte) ((bits >>> 8) & 0xFF);
        buffer[2] = (byte) ((bits >>> 16) & 0xFF);
        buffer[3] = (byte) ((bits >>> 24) & 0xFF);

        return 4;
    }

    @SuppressWarnings("SameReturnValue")
    private static int encodeDouble(double value, @NonNull byte[] buffer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "encodeDouble: value=" + value + " buffer=" + Arrays.toString(buffer));
        }

        long bits = Double.doubleToRawLongBits(value);
        int first = (int) bits;
        int second = (int) (bits >>> 32);
        buffer[0] = (byte) ((first) & 0xFF);
        buffer[4] = (byte) ((second) & 0xFF);
        buffer[5] = (byte) ((second >>> 8) & 0xFF);
        buffer[1] = (byte) ((first >>> 8) & 0xFF);
        buffer[2] = (byte) ((first >>> 16) & 0xFF);
        buffer[6] = (byte) ((second >>> 16) & 0xFF);
        buffer[7] = (byte) ((second >>> 24) & 0xFF);
        buffer[3] = (byte) ((first >>> 24) & 0xFF);

        return 8;
    }
}
