/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.calls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Join the call room response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"fd30c970-a16c-4346-936d-d541aa239cb8",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnJoinCallRoom",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"memberId", "type":"string"},
 *     {"name":"memberCount", "type":"int"},
 *     [{"name":"peerMemberId", "type":"string"},
 *      {"name":"p2pSessionId", [null, "type":"uuid"]}
 *     ]
 *  ]
 * }
 *
 * </pre>
 */
class OnJoinCallRoomIQ extends BinaryPacketIQ {

    private static class OnJoinCallRoomIQSerializer extends BinaryPacketIQSerializer {

        OnJoinCallRoomIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnJoinCallRoomIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnJoinCallRoomIQ onJoinCallRoomIQ = (OnJoinCallRoomIQ) object;
            encoder.writeString(onJoinCallRoomIQ.memberId);
            MemberSessionInfo.serialize(encoder, onJoinCallRoomIQ.members);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            String memberId = decoder.readString();
            MemberSessionInfo[] members = MemberSessionInfo.deserialize(decoder);

            return new OnJoinCallRoomIQ(this, serviceRequestIQ, memberId, members);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnJoinCallRoomIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final String memberId;
    @Nullable
    final MemberSessionInfo[] members;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" memberId=");
            stringBuilder.append(memberId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnJoinCallRoomIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    OnJoinCallRoomIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                     @NonNull String memberId, @Nullable MemberSessionInfo[] members) {

        super(serializer, serviceRequestIQ);

        this.memberId = memberId;
        this.members = members;
    }
}
