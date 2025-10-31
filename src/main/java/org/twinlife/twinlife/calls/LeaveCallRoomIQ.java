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
 * Leave the call room request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"ffc5b5d4-a5e7-471e-aef3-97fadfdbda94",
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
class LeaveCallRoomIQ extends BinaryPacketIQ {

    private static class LeaveCallRoomIQSerializer extends BinaryPacketIQSerializer {

        LeaveCallRoomIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, LeaveCallRoomIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final LeaveCallRoomIQ joinCallRoomIQ = (LeaveCallRoomIQ) object;
            encoder.writeUUID(joinCallRoomIQ.callRoomId);
            encoder.writeString(joinCallRoomIQ.memberId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID callRoomId = decoder.readUUID();
            String memberId = decoder.readString();

            return new LeaveCallRoomIQ(this, serviceRequestIQ.getRequestId(), callRoomId, memberId);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new LeaveCallRoomIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID callRoomId;
    @NonNull
    final String memberId;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" callRoomId=");
            stringBuilder.append(callRoomId);
            stringBuilder.append(" memberId=");
            stringBuilder.append(memberId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("LeaveCallRoomIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    LeaveCallRoomIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull UUID callRoomId, @NonNull String memberId) {

        super(serializer, requestId);

        this.callRoomId = callRoomId;
        this.memberId = memberId;
    }
}
