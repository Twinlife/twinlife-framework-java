/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * JoinGroupIQ IQ.
 * <p>
 * Schema version 2
 *  Date: 2024/08/28
 *
 * <pre>
 * {
 *  "schemaId":"c1315d7f-bf10-4cec-811b-84c44302e7bd",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"JoinGroupIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"},
 *     {"name":"sequenceId", "type":"long"}}
 *     {"name":"groupTwincodeId", "type":"uuid"},
 *     {"name":"mode", "type":"int"
 *      0 => {},
 *      1 => {{"name":"memberTwincodeId", "type":"uuid"},
 *            {"name":"publicKey", "type":[null, "String"]},
 *            {"name":"secret", "type":[null, "bytes"]}}
 *  ]
 * }
 * </pre>
 */
class JoinGroupIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("c1315d7f-bf10-4cec-811b-84c44302e7bd");
    static final int SCHEMA_VERSION_2 = 2;
    public static final BinaryPacketIQSerializer IQ_JOIN_GROUP_SERIALIZER_2 = new JoinGroupIQSerializer_2(SCHEMA_ID, SCHEMA_VERSION_2);

    @NonNull
    public final DescriptorId invitationDescriptorId;
    @NonNull
    public final UUID groupTwincodeId;
    @Nullable
    public final UUID memberTwincodeId;
    @Nullable
    public final String publicKey;
    @Nullable
    public final byte[] secretKey;

    public JoinGroupIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                       @NonNull DescriptorId invitationDescriptorId, @NonNull UUID groupTwincodeId,
                       @Nullable UUID memberTwincodeId, @Nullable String publicKey, @Nullable byte[] secretKey) {

        super(serializer, requestId);

        this.invitationDescriptorId = invitationDescriptorId;
        this.groupTwincodeId = groupTwincodeId;
        this.memberTwincodeId = memberTwincodeId;
        this.publicKey = publicKey;
        this.secretKey = secretKey;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" invitationDescriptorId=");
            stringBuilder.append(invitationDescriptorId);
            stringBuilder.append(" groupTwincodeId=");
            stringBuilder.append(groupTwincodeId);
            stringBuilder.append(" memberTwincodeId=");
            stringBuilder.append(memberTwincodeId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("JoinGroupIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class JoinGroupIQSerializer_2 extends BinaryPacketIQSerializer {

        JoinGroupIQSerializer_2(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, JoinGroupIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            JoinGroupIQ joinGroupIQ = (JoinGroupIQ) object;

            encoder.writeUUID(joinGroupIQ.invitationDescriptorId.twincodeOutboundId);
            encoder.writeLong(joinGroupIQ.invitationDescriptorId.sequenceId);
            encoder.writeUUID(joinGroupIQ.groupTwincodeId);
            if (joinGroupIQ.memberTwincodeId == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(joinGroupIQ.memberTwincodeId);
                encoder.writeOptionalString(joinGroupIQ.publicKey);
                encoder.writeOptionalBytes(joinGroupIQ.secretKey);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final UUID groupTwincodeId = decoder.readUUID();
            final DescriptorId descriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
            final int mode = decoder.readEnum();
            final UUID memberTwincodeId;
            final String publicKey;
            final byte[] secret;
            if (mode != 0) {
                memberTwincodeId = decoder.readUUID();
                publicKey = decoder.readOptionalString();
                secret = decoder.readOptionalBytes(null);
            } else {
                memberTwincodeId = null;
                publicKey = null;
                secret = null;
            }
            return new JoinGroupIQ(this, requestId, descriptorId, groupTwincodeId, memberTwincodeId, publicKey, secret);
        }
    }
}
