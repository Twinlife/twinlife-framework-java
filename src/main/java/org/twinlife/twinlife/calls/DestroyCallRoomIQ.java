/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.calls;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Destroy the call room request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"f4e195c7-3f84-4e05-a268-b4e3a956a787",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"LeaveCallRoomIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"callRoomId", "type":"uuid"},
 *     {"name":"memberId", "type":"string"}
 *  ]
 * }
 *
 * </pre>
 */
class DestroyCallRoomIQ extends BinaryPacketIQ {

    private static class DestroyCallRoomIQSerializer extends BinaryPacketIQSerializer {

        DestroyCallRoomIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, DestroyCallRoomIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final DestroyCallRoomIQ destroyCallRoomIQ = (DestroyCallRoomIQ) object;
            encoder.writeUUID(destroyCallRoomIQ.callRoomId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID callRoomId = decoder.readUUID();

            return new DestroyCallRoomIQ(this, serviceRequestIQ.getRequestId(), callRoomId);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new DestroyCallRoomIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID callRoomId;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" callRoomId=");
            stringBuilder.append(callRoomId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("DestroyCallRoomIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    DestroyCallRoomIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                      @NonNull UUID callRoomId) {

        super(serializer, requestId);

        this.callRoomId = callRoomId;
    }
}
