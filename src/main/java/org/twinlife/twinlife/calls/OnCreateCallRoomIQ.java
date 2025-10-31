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
 * Create Call Room response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"9e53e24a-acf3-4819-8539-2af37272254f",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnCreateCallRoomIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"callRoomId", "type":"uuid"},
 *     {"name":"memberId", "type":"string"},
 *     {"name":"mode", "type":"int"},
 *     {"name":"maxMemberCount", "type":"int"}
 *  ]
 * }
 *
 * </pre>
 */
class OnCreateCallRoomIQ extends BinaryPacketIQ {

    private static class OnCreateCallRoomIQSerializer extends BinaryPacketIQSerializer {

        OnCreateCallRoomIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnCreateCallRoomIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnCreateCallRoomIQ onCreateCallRoomIQ = (OnCreateCallRoomIQ) object;
            encoder.writeUUID(onCreateCallRoomIQ.callRoomId);
            encoder.writeString(onCreateCallRoomIQ.memberId);
            encoder.writeInt(onCreateCallRoomIQ.mode);
            encoder.writeInt(onCreateCallRoomIQ.maxMemberCount);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID callRoomId = decoder.readUUID();
            String memberId = decoder.readString();
            int mode = decoder.readInt();
            int maxMemberCount = decoder.readInt();

            return new OnCreateCallRoomIQ(this, serviceRequestIQ, callRoomId, memberId, mode, maxMemberCount);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnCreateCallRoomIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID callRoomId;
    @NonNull
    final String memberId;
    final int mode;
    final int maxMemberCount;

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
            stringBuilder.append(" mode=");
            stringBuilder.append(mode);
            stringBuilder.append(" maxMemberCount=");
            stringBuilder.append(maxMemberCount);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnCreateCallRoomIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    OnCreateCallRoomIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                       @NonNull UUID callRoomId, @NonNull String memberId, int mode, int maxMemberCount) {

        super(serializer, serviceRequestIQ);

        this.callRoomId = callRoomId;
        this.memberId = memberId;
        this.mode = mode;
        this.maxMemberCount = maxMemberCount;
    }
}
