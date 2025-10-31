/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Acknowledge invocation IQ.
 * <p>
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"eee63e5e-8af1-41e9-9a1b-79806a0056a2",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"AcknowledgeInvocationIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"invocationId", "type":"uuid"}
 *     {"name":"errorCode", "type":"enum"}
 *  ]
 * }
 * </pre>
 */
public class AcknowledgeInvocationIQ extends BinaryPacketIQ {

    static class AcknowledgeInvocationIQSerializer_2 extends BinaryPacketIQSerializer {

        AcknowledgeInvocationIQSerializer_2(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, AcknowledgeInvocationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            AcknowledgeInvocationIQ acknowledgeInvocationIQ = (AcknowledgeInvocationIQ) object;

            encoder.writeUUID(acknowledgeInvocationIQ.invocationId);
            encoder.writeEnum(ErrorCode.fromErrorCode(acknowledgeInvocationIQ.errorCode));
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID invocationId = decoder.readUUID();
            ErrorCode errorCode = ErrorCode.toErrorCode(decoder.readEnum());

            return new AcknowledgeInvocationIQ(this, serviceRequestIQ.getRequestId(), invocationId, errorCode);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer_2(@NonNull UUID schemaId, int schemaVersion) {

        return new AcknowledgeInvocationIQSerializer_2(schemaId, schemaVersion);
    }

    @NonNull
    private final UUID invocationId;
    @NonNull
    private final ErrorCode errorCode;

    @NonNull
    public UUID getInvocationId() {

        return invocationId;
    }

    @NonNull
    public ErrorCode getErrorCode() {

        return errorCode;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" invocationId=");
        stringBuilder.append(invocationId);
        stringBuilder.append(" errorCode=");
        stringBuilder.append(errorCode);
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("AcknowledgeInvocationIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    public AcknowledgeInvocationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                                   @NonNull UUID invocationId, @NonNull ErrorCode errorCode) {

        super(serializer, requestId);

        this.invocationId = invocationId;
        this.errorCode = errorCode;
    }
}
