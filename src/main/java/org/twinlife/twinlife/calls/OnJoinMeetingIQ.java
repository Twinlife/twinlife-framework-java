/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.calls;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import java.util.UUID;

/**
 * Join meeting response IQ.
 *
 * Schema version 1
 *  Date: 2026/01/15
 *
 * <pre>
 * {
 *  "schemaId":"64728bdd-d4b7-4042-b90d-d94c6a56fae6",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnJoinMeeting",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"callRoomId", "type":"uuid"},
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
class OnJoinMeetingIQ extends BinaryPacketIQ {

    private static class OnJoinMeetingIQSerializer extends BinaryPacketIQSerializer {

        OnJoinMeetingIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnJoinMeetingIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnJoinMeetingIQ onJoinMeetingIQ = (OnJoinMeetingIQ) object;
            encoder.writeUUID(onJoinMeetingIQ.callRoomId);
            encoder.writeString(onJoinMeetingIQ.memberId);
            MemberSessionInfo.serialize(encoder, onJoinMeetingIQ.members);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID callRoomId = decoder.readUUID();
            String memberId = decoder.readString();
            MemberSessionInfo[] members = MemberSessionInfo.deserialize(decoder);

            return new OnJoinMeetingIQ(this, serviceRequestIQ, callRoomId, memberId, members);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnJoinMeetingIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID callRoomId;
    @NonNull
    final String memberId;
    @Nullable
    final MemberSessionInfo[] members;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" callRoomId=");
        stringBuilder.append(callRoomId);
        stringBuilder.append(" memberId=");
        stringBuilder.append(memberId);
        if (members != null) {
            stringBuilder.append(" members=[");
            for (MemberSessionInfo member : members) {
                if (member != null) {
                    stringBuilder.append(member.memberId);
                    stringBuilder.append(":");
                    stringBuilder.append(member.p2pSessionId);
                    stringBuilder.append(" ");
                }
            }
            stringBuilder.append("]");
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("OnJoinMeetingIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    OnJoinMeetingIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                    @NonNull UUID callRoomId, @NonNull String memberId, @Nullable MemberSessionInfo[] members) {

        super(serializer, serviceRequestIQ);

        this.callRoomId = callRoomId;
        this.memberId = memberId;
        this.members = members;
    }
}
