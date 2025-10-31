/*
 *  Copyright (c) 2022-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.SerializerException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

public class BinaryCompactDecoder extends BinaryDecoder {

    public BinaryCompactDecoder(@NonNull InputStream inputStream) {
        super(inputStream);
    }

    private long readBinaryLong() throws Exception {
        long value;

        // Note: use read() is faster than using doReadBytes() or a read() with a 16-byte buffer.
        value = mInputStream.read();
        value |= ((long) mInputStream.read() << 8);
        value |= ((long) mInputStream.read() << 16);
        value |= ((long) mInputStream.read()) << 24;
        value |= ((long) mInputStream.read()) << 32;
        value |= ((long) mInputStream.read()) << 40;
        value |= ((long) mInputStream.read()) << 48;

            long b = mInputStream.read();
        if (b < 0) {
                throw new SerializerException();
            }
        value |= b << 56;

        return value;
    }

    @Override
    @NonNull
    public UUID readUUID() throws SerializerException {

        try {
            long leastSignificantBits = readBinaryLong();
            long mostSignificantBits = readBinaryLong();

            return new UUID(mostSignificantBits, leastSignificantBits);
        } catch (Exception exception) {
            throw new SerializerException();
        }
    }

    @Nullable
    public static List<BaseService.AttributeNameValue> deserialize(@Nullable byte[] content) {

        if (content == null) {
            return null;
        }

        List<BaseService.AttributeNameValue> attributes = null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
            BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

            attributes = decoder.readAttributes();

        } catch (Exception exception) {

        }
        return attributes;
    }
}
