/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * InviteGroup IQ.
 * <p>
 * Schema version 2
 *  Date: 2024/08/28
 *
 * <pre>
 * {
 *  "schemaId":"55e698ff-b429-425f-bcaa-0b21d4620621",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"InviteGroupIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"groupTwincodeOutboundId", "type":"uuid"},
 *     {"name":"publicKey", "type":[null, "String"]},
 *     {"name":"name", "type":"String"},
 *     {"name":"createdTimestamp", "type":"long"},
 *     {"name":"sentTimestamp", "type":"long"},
 *     {"name":"expireTimeout", "type":"long"}
 *  ]
 * }
 * </pre>
 */
class InviteGroupIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("55e698ff-b429-425f-bcaa-0b21d4620621");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQSerializer IQ_INVITE_GROUP_SERIALIZER_2 = new InviteGroupIQSerializer_2(SCHEMA_ID, SCHEMA_VERSION_2);

    @NonNull
    final InvitationDescriptorImpl invitationDescriptorImpl;

    InviteGroupIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull InvitationDescriptorImpl invitationDescriptor) {

        super(serializer, requestId);

        this.invitationDescriptorImpl = invitationDescriptor;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" invitationDescriptor=");
            stringBuilder.append(invitationDescriptorImpl);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("InviteGroupIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class InviteGroupIQSerializer_2 extends BinaryPacketIQSerializer {

        InviteGroupIQSerializer_2(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, InviteGroupIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            InviteGroupIQ inviteGroupIQ = (InviteGroupIQ) object;
            InvitationDescriptorImpl invitationDescriptor = inviteGroupIQ.invitationDescriptorImpl;

            encoder.writeUUID(invitationDescriptor.getDescriptorId().twincodeOutboundId);
            encoder.writeLong(invitationDescriptor.getDescriptorId().sequenceId);
            encoder.writeUUID(invitationDescriptor.getGroupTwincodeId());
            encoder.writeOptionalString(invitationDescriptor.getPublicKey());
            encoder.writeString(invitationDescriptor.getName());
            encoder.writeLong(invitationDescriptor.getCreatedTimestamp());
            encoder.writeLong(invitationDescriptor.getSentTimestamp());
            encoder.writeLong(invitationDescriptor.getExpireTimeout());
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final UUID groupTwincodeOutboundId = decoder.readUUID();
            final String publicKey = decoder.readOptionalString();
            final String name = decoder.readString();
            final long createdTimestamp = decoder.readLong();
            final long sentTimestamp = decoder.readLong();
            final long expireTimeout = decoder.readLong();
            InvitationDescriptorImpl invitationDescriptor = new InvitationDescriptorImpl(new DescriptorId(0, twincodeOutboundId, sequenceId),
                    groupTwincodeOutboundId, twincodeOutboundId, name, publicKey, createdTimestamp, sentTimestamp, expireTimeout);
            return new InviteGroupIQ(this, requestId, invitationDescriptor);
        }
    }
}
