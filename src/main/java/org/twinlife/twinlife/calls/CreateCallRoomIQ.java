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
 * Create call room request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"e53c8953-6345-4e77-bf4b-c1dc227d5d2f",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"CreateCallRoomIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"ownerId", "type":"uuid"},
 *     {"name":"memberId", "type":"uuid"},
 *     {"name":"mode", "type":"int"},
 *     {"name":"memberCount", "type":"int"},
 *     [{"name":"peerMemberId", "type":"string"},
 *      {"name":"p2pSessionId", [null, "type":"uuid"]},
 *     ],
 *     {"name":"sfuUri", [null, "type":"string"]}
 *  ]
 * }
 *
 * </pre>
 */
class CreateCallRoomIQ extends BinaryPacketIQ {

    static final int ALLOW_DATA  = 0x01;  // Call room is allowed to make Data, Audio, Video
    static final int ALLOW_AUDIO = 0x02;
    static final int ALLOW_VIDEO = 0x04;
    static final int ALLOW_INVITE = 0x08; // Call room member are allowed to invite other members.
    static final int AUTO_DESTROY = 0x10; // Call room destroyed automatically when last participant leaves.

    private static class CreateCallRoomIQSerializer extends BinaryPacketIQSerializer {

        CreateCallRoomIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, CreateCallRoomIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            CreateCallRoomIQ createCallRoomIQ = (CreateCallRoomIQ) object;
            encoder.writeUUID(createCallRoomIQ.ownerId);
            encoder.writeUUID(createCallRoomIQ.memberId);
            encoder.writeInt(createCallRoomIQ.mode);
            MemberSessionInfo.serialize(encoder, createCallRoomIQ.members);
            encoder.writeOptionalString(createCallRoomIQ.sfuURI);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID ownerId = decoder.readUUID();
            UUID memberId = decoder.readUUID();
            int mode = decoder.readInt();
            MemberSessionInfo[] members = MemberSessionInfo.deserialize(decoder);
            String sfuURI = decoder.readString();

            return new CreateCallRoomIQ(this, serviceRequestIQ.getRequestId(), ownerId, memberId, mode, members, sfuURI);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new CreateCallRoomIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID ownerId;
    @NonNull
    final UUID memberId;
    final int mode;
    @Nullable
    final MemberSessionInfo[] members;
    @Nullable
    final String sfuURI;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" ownerId=");
            stringBuilder.append(ownerId);
            stringBuilder.append(" memberId=");
            stringBuilder.append(memberId);
            stringBuilder.append(" mode=");
            stringBuilder.append(mode);
            stringBuilder.append(" sfuURI=");
            stringBuilder.append(sfuURI);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("CreateCallRoomIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    CreateCallRoomIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                     @NonNull UUID ownerId, @NonNull UUID memberId, int mode,
                     @Nullable MemberSessionInfo[] members, @Nullable String sfuURI) {

        super(serializer, requestId);

        this.ownerId = ownerId;
        this.memberId = memberId;
        this.mode = mode;
        this.members = members;
        this.sfuURI = sfuURI;
    }
}
