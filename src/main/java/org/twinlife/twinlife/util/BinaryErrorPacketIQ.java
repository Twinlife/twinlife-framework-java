/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class BinaryErrorPacketIQ extends BinaryPacketIQ {

    public static final UUID ON_ERROR_SCHEMA_ID = UUID.fromString("12f8b46b-89fa-4b15-b3a3-946bc3abbb65");
    public static final BinaryPacketIQSerializer IQ_ON_ERROR_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_ERROR_SCHEMA_ID, 1);

    public static class BinaryErrorPacketIQSerializer extends BinaryPacketIQSerializer {

        public BinaryErrorPacketIQSerializer(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            BinaryErrorPacketIQ errorIQ = (BinaryErrorPacketIQ) object;
            encoder.writeEnum(ErrorCode.fromErrorCode(errorIQ.errorCode));
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);
            ErrorCode errorCode = ErrorCode.toErrorCode(decoder.readEnum());

            return new BinaryErrorPacketIQ(this, serviceRequestIQ, errorCode);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new BinaryErrorPacketIQSerializer(schemaId, schemaVersion, BinaryErrorPacketIQ.class);
    }

    @NonNull
    protected final ErrorCode errorCode;

    public BinaryErrorPacketIQ(long requestId, @NonNull ErrorCode errorCode) {
        super(IQ_ON_ERROR_SERIALIZER, requestId);

        this.errorCode = errorCode;
    }

    public BinaryErrorPacketIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                                @NonNull ErrorCode errorCode) {
        super(serializer, serviceRequestIQ);

        this.errorCode = errorCode;
    }

    @NonNull
    public ErrorCode getErrorCode() {

        return errorCode;
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);
        stringBuilder.append(" errorCode=");
        stringBuilder.append(errorCode);
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("BinaryErrorPacketIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

}
