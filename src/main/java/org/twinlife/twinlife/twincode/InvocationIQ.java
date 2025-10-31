/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Invoke twincode response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"35d11e72-84d7-4a3b-badd-9367ef8c9e43",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"InvocationIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"invocationId", "type":"uuid"}
 *  ]
 * }
 * </pre>
 *
 * Acknowledge invocation IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"eee63e5e-8af1-41e9-9a1b-79806a0056a2",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"InvocationIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"invocationId", "type":"uuid"}
 *  ]
 * }
 *
 * </pre>
 */
public class InvocationIQ extends BinaryPacketIQ {

    static class AcknowledgeInvocationIQSerializer extends BinaryPacketIQSerializer {

        AcknowledgeInvocationIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, InvocationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            InvocationIQ invocationIQ = (InvocationIQ) object;

            encoder.writeUUID(invocationIQ.invocationId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID invocationId = decoder.readUUID();

            return new InvocationIQ(this, serviceRequestIQ.getRequestId(), invocationId);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new AcknowledgeInvocationIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final UUID invocationId;

    @NonNull
    public UUID getInvocationId() {

        return invocationId;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {
        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" invocationId=");
            stringBuilder.append(invocationId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("InvocationIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public InvocationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID invocationId) {

        super(serializer, requestId);

        this.invocationId = invocationId;
    }
}
