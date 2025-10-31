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

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public class BinaryCompactEncoder extends BinaryEncoder {

    public BinaryCompactEncoder(@NonNull OutputStream outputStream) {
        super(outputStream);
    }

    /**
     * Write the UUID in binary form (this form is compact as it always uses 16 bytes).
     *
     * @param value the UUID value to write.
     * @throws SerializerException when there is a serialization issue.
     */
    @Override
    public void writeUUID(@NonNull UUID value) throws SerializerException {

        try {

            long val = value.getLeastSignificantBits();
            mBuffer[0] = (byte) ((val) & 0x0FF);
            mBuffer[1] = (byte) ((val >> 8) & 0x0FF);
            mBuffer[2] = (byte) ((val >> 16) & 0x0FF);
            mBuffer[3] = (byte) ((val >> 24) & 0x0FF);
            mBuffer[4] = (byte) ((val >> 32) & 0x0FF);
            mBuffer[5] = (byte) ((val >> 40) & 0x0FF);
            mBuffer[6] = (byte) ((val >> 48) & 0x0FF);
            mBuffer[7] = (byte) ((val >> 56) & 0x0FF);

            val = value.getMostSignificantBits();
            mBuffer[8] = (byte) ((val) & 0x0FF);
            mBuffer[9] = (byte) ((val >> 8) & 0x0FF);
            mBuffer[10] = (byte) ((val >> 16) & 0x0FF);
            mBuffer[11] = (byte) ((val >> 24) & 0x0FF);
            mBuffer[12] = (byte) ((val >> 32) & 0x0FF);
            mBuffer[13] = (byte) ((val >> 40) & 0x0FF);
            mBuffer[14] = (byte) ((val >> 48) & 0x0FF);
            mBuffer[15] = (byte) ((val >> 56) & 0x0FF);

            mOutputStream.write(mBuffer, 0, 16);

        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    @Nullable
    public static byte[] serialize(@Nullable List<BaseService.AttributeNameValue> attributes) {

        if (attributes == null || attributes.isEmpty()) {
            return null;
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);

            encoder.writeAttributes(attributes);
            return outputStream.toByteArray();

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error("serialize", "serialize", exception);
            }
            return null;
        }
    }
}
