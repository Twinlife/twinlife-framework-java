/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
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
 * PushObject IQ.
 * <p>
 * Schema version 5
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"26e3a3bd-7db0-4fc5-9857-bbdb2032960e",
 *  "schemaVersion":"5",
 *
 *  "type":"record",
 *  "name":"PushObjectIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"sendToTwincodeOutboundId", "type":["null", "UUID"]},
 *     {"name":"replyTo", "type":["null", {
 *         {"name":"twincodeOutboundId", "type":"uuid"},
 *         {"name":"sequenceId", "type":"long"}
 *     }},
 *     {"name":"createdTimestamp", "type":"long"}
 *     {"name":"sentTimestamp", "type":"long"}
 *     {"name":"expireTimeout", "type":"long"}
 *     {"name":"object", "type":"Object"}
 *     {"name":"copyAllowed", "type":"boolean"}
 *  ]
 * }
 *
 * </pre>
 */
class PushObjectIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("26e3a3bd-7db0-4fc5-9857-bbdb2032960e");
    static final int SCHEMA_VERSION_5 = 5;
    static final BinaryPacketIQSerializer IQ_PUSH_OBJECT_SERIALIZER = PushObjectIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_5);

    @NonNull
    final ObjectDescriptorImpl objectDescriptorImpl;

    PushObjectIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull ObjectDescriptorImpl objectDescriptorImpl) {

        super(serializer, requestId);

        this.objectDescriptorImpl = objectDescriptorImpl;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new PushObjectIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" objectDescriptor=");
            stringBuilder.append(objectDescriptorImpl);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushObjectIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class PushObjectIQSerializer extends BinaryPacketIQSerializer {

        PushObjectIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PushObjectIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PushObjectIQ pushObjectIQ = (PushObjectIQ) object;

            ObjectDescriptorImpl objectDescriptor = pushObjectIQ.objectDescriptorImpl;
            encoder.writeUUID(objectDescriptor.getTwincodeOutboundId());
            encoder.writeLong(objectDescriptor.getSequenceId());
            encoder.writeOptionalUUID(objectDescriptor.getSendTo());
            DescriptorId replyTo = objectDescriptor.getReplyToDescriptorId();
            if (replyTo == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(replyTo.twincodeOutboundId);
                encoder.writeLong(replyTo.sequenceId);
            }
            encoder.writeLong(objectDescriptor.getCreatedTimestamp());
            encoder.writeLong(objectDescriptor.getSentTimestamp());
            encoder.writeLong(objectDescriptor.getExpireTimeout());
            objectDescriptor.serialize(encoder);
            encoder.writeBoolean(objectDescriptor.isCopyAllowed());
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final UUID sendTo = decoder.readOptionalUUID();
            final DescriptorId replyTo = DescriptorImpl.DescriptorImplSerializer_4.readOptionalDescriptorId(decoder);
            final long createdTimestamp = decoder.readLong();
            final long sentTimestamp = decoder.readLong();
            final long expireTimeout = decoder.readLong();

            final UUID schemaId = decoder.readUUID();
            final int schemaVersion = decoder.readInt();
            // final Serializer serializer = serializerFactory.getSerializer(schemaId, schemaVersion);
            if (!Message.SCHEMA_ID.equals(schemaId) || schemaVersion != Message.SCHEMA_VERSION) {
                throw new SerializerException();
            }
            final String message = decoder.readString();
            final boolean copyAllowed = decoder.readBoolean();

            ObjectDescriptorImpl objectDescriptor = new ObjectDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout,
                    sendTo, replyTo, message, copyAllowed, createdTimestamp, sentTimestamp);
            return new PushObjectIQ(this, requestId, objectDescriptor);
        }
    }
}
