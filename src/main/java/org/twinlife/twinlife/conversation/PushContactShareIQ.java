/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.InvitationDescriptor;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * PushPoll IQ.
 * <p>
 * Schema version 1
 * Date: 2026/07/04
 *
 * <pre>
 * {
 *  "schemaId":"9c338e8d-f7ec-40a9-b5e5-77e31ede8939",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushContactShareIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"name", "type":"string"},
 *     {"name":"status", "type":"enum"},
 *     {"name":"avatar", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class PushContactShareIQ extends BinaryPacketIQ {

    static final int SCHEMA_VERSION_1 = 1;
    static final UUID SCHEMA_ID = UUID.fromString("9c338e8d-f7ec-40a9-b5e5-77e31ede8939");
    static final BinaryPacketIQSerializer IQ_PUSH_CONTACT_SHARE_SERIALIZER = new PushContactShareIQSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    @NonNull
    final ContactShareDescriptorImpl contactShareDescriptor;
    @NonNull
    final byte[] avatar;

    PushContactShareIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull ContactShareDescriptorImpl contactShareDescriptor, @NonNull byte[] avatar) {

        super(serializer, requestId);

        this.contactShareDescriptor = contactShareDescriptor;
        this.avatar = avatar;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" contactShareDescriptor=");
            stringBuilder.append(contactShareDescriptor);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushContactShareIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class PushContactShareIQSerializer extends BinaryPacketIQSerializer {

        PushContactShareIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PushContactShareIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PushContactShareIQ pushContactShareIQ = (PushContactShareIQ) object;

            ContactShareDescriptorImpl contactShareDescriptor = pushContactShareIQ.contactShareDescriptor;

            encoder.writeUUID(contactShareDescriptor.getTwincodeOutboundId());
            encoder.writeLong(contactShareDescriptor.getSequenceId());
            encoder.writeOptionalUUID(contactShareDescriptor.getSendTo());

            ConversationService.DescriptorId replyTo = contactShareDescriptor.getReplyToDescriptorId();
            if (replyTo == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(1);
                encoder.writeUUID(replyTo.twincodeOutboundId);
                encoder.writeLong(replyTo.sequenceId);
            }

            encoder.writeLong(contactShareDescriptor.getCreatedTimestamp());
            encoder.writeLong(contactShareDescriptor.getSentTimestamp());
            encoder.writeLong(contactShareDescriptor.getExpireTimeout());

            encoder.writeString(contactShareDescriptor.getName());
            encoder.writeEnum(contactShareDescriptor.getStatus().toInt());
            encoder.writeData(pushContactShareIQ.avatar);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();

            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final UUID sendTo = decoder.readOptionalUUID();

            final ConversationService.DescriptorId replyTo = DescriptorImpl.DescriptorImplSerializer_4.readOptionalDescriptorId(decoder);

            final long createdTimestamp = decoder.readLong();
            final long sentTimestamp = decoder.readLong();
            final long expireTimeout = decoder.readLong();

            final String name = decoder.readString();
            final InvitationDescriptor.Status status = InvitationDescriptor.Status.toStatus(decoder.readEnum());
            final byte[] avatar = decoder.readBytes(null).array();

            ContactShareDescriptorImpl contactShareDescriptor = new ContactShareDescriptorImpl(twincodeOutboundId, sequenceId,
                    expireTimeout, sendTo, replyTo, createdTimestamp, sentTimestamp, name, status);

            return new PushContactShareIQ(this, requestId, contactShareDescriptor, avatar);
        }
    }
}
