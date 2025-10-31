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
import org.twinlife.twinlife.PeerCallService.MemberStatus;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Member notification IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"f7460e42-387c-41fe-97c3-18a5f2a97052",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"MemberNotificationIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"callRoomId", "type":"uuid"},
 *     {"name":"memberId", "type":"string"},
 *     {"name":"p2pSessionId", [null, "type":"uuid"]},
 *     {"name":"status", "type":["NewMember", "NewMemberNeedSession", "DelMember"]},
 *     {"name":"maxFrameWidth", "type":"int"},
 *     {"name":"maxFrameHeight", "type":"int"},
 *     {"name":"frameRate", "type":"int"}
 *  ]
 * }
 *
 * </pre>
 */
class MemberNotificationIQ extends BinaryPacketIQ {

    private static class MemberNotificationIQSerializer extends BinaryPacketIQSerializer {

        MemberNotificationIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, MemberNotificationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            throw new SerializerException();
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID callRoomId = decoder.readUUID();
            String memberId = decoder.readString();
            UUID p2pSessionId = decoder.readOptionalUUID();
            MemberStatus status;
            switch (decoder.readEnum()) {
                case 0:
                    status = MemberStatus.NEW_MEMBER;
                    break;

                case 1:
                    status = MemberStatus.NEW_MEMBER_NEED_SESSION;
                    break;

                case 2:
                default:
                    status = MemberStatus.DEL_MEMBER;
                    break;
            }
            int maxFrameWidth = decoder.readInt();
            int maxFrameHeight = decoder.readInt();
            int maxFrameRate = decoder.readInt();

            return new MemberNotificationIQ(this, serviceRequestIQ.getRequestId(), callRoomId, memberId,
                    p2pSessionId, status, maxFrameWidth, maxFrameHeight, maxFrameRate);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new MemberNotificationIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID callRoomId;
    @NonNull
    final String memberId;
    @Nullable
    final UUID p2pSessionId;
    @NonNull
    final MemberStatus status;
    final int maxFrameWidth;
    final int maxFrameHeight;
    final int maxFrameRate;

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
            stringBuilder.append(" p2pSessionId=");
            stringBuilder.append(p2pSessionId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("MemberNotificationIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    MemberNotificationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                         @NonNull UUID callRoomId, @NonNull String memberId, @Nullable UUID p2pSessionId,
                         @NonNull MemberStatus status, int maxFrameWidth, int maxFrameHeight, int maxFrameRate) {

        super(serializer, requestId);

        this.callRoomId = callRoomId;
        this.memberId = memberId;
        this.p2pSessionId = p2pSessionId;
        this.status = status;
        this.maxFrameWidth = maxFrameWidth;
        this.maxFrameHeight = maxFrameHeight;
        this.maxFrameRate = maxFrameRate;
    }
}
