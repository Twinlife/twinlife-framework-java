/*
 *  Copyright (c) 2025-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.account;


import androidx.annotation.NonNull;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Terminate restore response IQ.
 *
 * Schema version 1
 *  Date: 2025/12/04
 *
 * <pre>
 * {
 *  "schemaId":"a9945fd0-7f68-42ea-8b41-f6bc4d22cfb4",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnTerminateAccountRestoreIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name:"errorCode", "type":"enum"},
 *     {"name":"restoreCount", "type":"int"}
 *  ]
 * }
 *
 * </pre>
 */

public class OnTerminateAccountRestoreIQ extends BinaryPacketIQ {

    private static class OnTerminateAccountRestoreIQSerializer extends BinaryPacketIQSerializer {

        public OnTerminateAccountRestoreIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnTerminateAccountRestoreIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            throw new SerializerException();
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {
            BinaryPacketIQ iq = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            ErrorCode errorCode = ErrorCode.toErrorCode(decoder.readEnum());
            int restoreCount = decoder.readInt();

            return new OnTerminateAccountRestoreIQ(this, iq, errorCode, restoreCount);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnTerminateAccountRestoreIQSerializer(schemaId, schemaVersion);
    }

    final ErrorCode errorCode;
    final int restoreCount;

    public OnTerminateAccountRestoreIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ, @NonNull ErrorCode errorCode, int restoreCount) {

        super(serializer, serviceRequestIQ);

        this.errorCode = errorCode;
        this.restoreCount = restoreCount;
    }

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);
        stringBuilder.append(" errorCode=");
        stringBuilder.append(errorCode);
        stringBuilder.append(" restoreCount=");
        stringBuilder.append(restoreCount);
    }

    @NonNull
    @Override
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("OnTerminateAccountRestoreIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }
}
