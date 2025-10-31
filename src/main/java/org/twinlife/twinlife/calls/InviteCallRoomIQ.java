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
 * Invite a member in the call room request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"8974ff91-a6c6-42d7-b2a2-fc11041892bd",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"InviteCallRoomIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"callRoomId", "type":"uuid"},
 *     {"name":"twincodeId", "type":"uuid"},
 *     {"name":"p2pSessionId", [null, "type":"uuid"]}
 *     {"name":"mode", "type":"int"},
 *     {"name":"maxMemberCount", "type":"int"},
 *  ]
 * }
 *
 * </pre>
 */
class InviteCallRoomIQ extends BinaryPacketIQ {

    private static class InviteCallRoomIQSerializer extends BinaryPacketIQSerializer {

        InviteCallRoomIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, InviteCallRoomIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            InviteCallRoomIQ inviteCallRoomIQ = (InviteCallRoomIQ) object;
            encoder.writeUUID(inviteCallRoomIQ.callRoomId);
            encoder.writeUUID(inviteCallRoomIQ.twincodeId);
            encoder.writeOptionalUUID(inviteCallRoomIQ.p2pSessionId);
            encoder.writeInt(inviteCallRoomIQ.mode);
            encoder.writeInt(inviteCallRoomIQ.maxMemberCount);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID callRoomId = decoder.readUUID();
            UUID twincodeId = decoder.readUUID();
            UUID p2pSessionId = decoder.readOptionalUUID();
            int mode = decoder.readInt();
            int maxMemberCount = decoder.readInt();

            return new InviteCallRoomIQ(this, serviceRequestIQ.getRequestId(), callRoomId,
                    twincodeId, p2pSessionId, mode, maxMemberCount);
        } 
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new InviteCallRoomIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID callRoomId;
    @NonNull
    final UUID twincodeId;
    final int mode;
    @Nullable
    final UUID p2pSessionId;
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
            stringBuilder.append(" twincodeId=");
            stringBuilder.append(twincodeId);
            stringBuilder.append(" p2pSessionId=");
            stringBuilder.append(p2pSessionId);
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
            stringBuilder.append("InviteCallRoomIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    InviteCallRoomIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                     @NonNull UUID callRoomId, @NonNull UUID twincodeId,
                     @Nullable UUID p2pSessionId, int mode, int maxMemberCount) {

        super(serializer, requestId);

        this.callRoomId = callRoomId;
        this.twincodeId = twincodeId;
        this.p2pSessionId = p2pSessionId;
        this.mode = mode;
        this.maxMemberCount = maxMemberCount;
    }
}
