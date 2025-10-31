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
 * PushTwincode IQ.
 * <p>
 * Schema version 3
 *  Date: 2024/07/26
 *
 * <pre>
 * {
 *  "schemaId":"72863c61-c0a9-437b-8b88-3b78354e54b8",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"PushTwincodeIQ",
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
 *     {"name":"twincode", "type":"UUID"}
 *     {"name":"schemaId", "type":"UUID"}
 *     {"name":"copyAllowed", "type":"boolean"}
 *     {"name":"publicKey", "type":[null, "String"]}
 *  ]
 * }
 * </pre>
 * <p>
 * Schema version 2
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"72863c61-c0a9-437b-8b88-3b78354e54b8",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"PushTwincodeIQ",
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
 *     {"name":"twincode", "type":"UUID"}
 *     {"name":"schemaId", "type":"UUID"}
 *     {"name":"copyAllowed", "type":"boolean"}
 *  ]
 * }
 *
 * </pre>
 */
class PushTwincodeIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("72863c61-c0a9-437b-8b88-3b78354e54b8");
    static final int SCHEMA_VERSION_3 = 3;
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQSerializer IQ_PUSH_TWINCODE_SERIALIZER_3 = new PushTwincodeIQSerializer_3(SCHEMA_ID, SCHEMA_VERSION_3);
    static final BinaryPacketIQSerializer IQ_PUSH_TWINCODE_SERIALIZER_2 = new PushTwincodeIQSerializer_2(SCHEMA_ID, SCHEMA_VERSION_2);

    @NonNull
    final TwincodeDescriptorImpl twincodeDescriptorImpl;

    PushTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull TwincodeDescriptorImpl twincodeDescriptorImpl) {

        super(serializer, requestId);

        this.twincodeDescriptorImpl = twincodeDescriptorImpl;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" twincodeDescriptor=");
            stringBuilder.append(twincodeDescriptorImpl);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushTwincodeIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
    static class PushTwincodeIQSerializer_3 extends BinaryPacketIQSerializer {

        PushTwincodeIQSerializer_3(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PushTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PushTwincodeIQ pushTwincodeIQ = (PushTwincodeIQ) object;

            TwincodeDescriptorImpl twincodeDescriptor = pushTwincodeIQ.twincodeDescriptorImpl;
            encoder.writeUUID(twincodeDescriptor.getTwincodeOutboundId());
            encoder.writeLong(twincodeDescriptor.getSequenceId());
            encoder.writeOptionalUUID(twincodeDescriptor.getSendTo());
            DescriptorId replyTo = twincodeDescriptor.getReplyToDescriptorId();
            if (replyTo == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(replyTo.twincodeOutboundId);
                encoder.writeLong(replyTo.sequenceId);
            }
            encoder.writeLong(twincodeDescriptor.getCreatedTimestamp());
            encoder.writeLong(twincodeDescriptor.getSentTimestamp());
            encoder.writeLong(twincodeDescriptor.getExpireTimeout());
            encoder.writeUUID(twincodeDescriptor.getTwincodeId());
            encoder.writeUUID(twincodeDescriptor.getSchemaId());
            encoder.writeBoolean(twincodeDescriptor.isCopyAllowed());
            encoder.writeOptionalString(twincodeDescriptor.getPublicKey());
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

            final UUID twincodeId = decoder.readUUID();
            final UUID schemaId = decoder.readUUID();
            final boolean copyAllowed = decoder.readBoolean();
            final String publicKey = decoder.readOptionalString();

            TwincodeDescriptorImpl twincodeDescriptor = new TwincodeDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo,
                    twincodeId, schemaId, publicKey, copyAllowed, createdTimestamp, sentTimestamp);
            return new PushTwincodeIQ(this, requestId, twincodeDescriptor);
        }
    }

    static class PushTwincodeIQSerializer_2 extends BinaryPacketIQSerializer {

        PushTwincodeIQSerializer_2(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PushTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PushTwincodeIQ pushTwincodeIQ = (PushTwincodeIQ) object;

            TwincodeDescriptorImpl twincodeDescriptor = pushTwincodeIQ.twincodeDescriptorImpl;
            encoder.writeUUID(twincodeDescriptor.getTwincodeOutboundId());
            encoder.writeLong(twincodeDescriptor.getSequenceId());
            encoder.writeOptionalUUID(twincodeDescriptor.getSendTo());
            DescriptorId replyTo = twincodeDescriptor.getReplyToDescriptorId();
            if (replyTo == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(replyTo.twincodeOutboundId);
                encoder.writeLong(replyTo.sequenceId);
            }
            encoder.writeLong(twincodeDescriptor.getCreatedTimestamp());
            encoder.writeLong(twincodeDescriptor.getSentTimestamp());
            encoder.writeLong(twincodeDescriptor.getExpireTimeout());
            encoder.writeUUID(twincodeDescriptor.getTwincodeId());
            encoder.writeUUID(twincodeDescriptor.getSchemaId());
            encoder.writeBoolean(twincodeDescriptor.isCopyAllowed());
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

            final UUID twincodeId = decoder.readUUID();
            final UUID schemaId = decoder.readUUID();
            final boolean copyAllowed = decoder.readBoolean();

            TwincodeDescriptorImpl twincodeDescriptor = new TwincodeDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo,
                    twincodeId, schemaId, null, copyAllowed, createdTimestamp, sentTimestamp);
            return new PushTwincodeIQ(this, requestId, twincodeDescriptor);
        }
    }
}
