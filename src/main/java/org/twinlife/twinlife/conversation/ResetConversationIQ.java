/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.ClearMode;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * ResetConversation IQ.
 * <p>
 * Schema version 4
 *  Date: 2022/02/09
 *  Clear, reset conversation and inform the user
 *
 * <pre>
 * {
 *  "schemaId":"412f43fa-bee9-4268-ac6f-98e99e457d03",
 *  "schemaVersion":"4",
 *
 *  "type":"record",
 *  "name":"ResetConversationIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields":
 *  [
 *   {"name":"clearDescriptor", "type":["null", {
 *     {"name":"twincodeOutboundId", "type":"uuid"},
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"createdTimestamp", "type":"long"},
 *     {"name":"sentTimestamp", "type":"long"},
 *   }},
 *   {"name":"clearTimestamp", "type":"long"},
 *   {"name":"clearMode", "type":"enum"}
 *  ]
 * }
 * </pre>
 */
class ResetConversationIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("412f43fa-bee9-4268-ac6f-98e99e457d03");
    static final int SCHEMA_VERSION_4 = 4;
    static final BinaryPacketIQSerializer IQ_RESET_CONVERSATION_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_4);

    @Nullable
    final ClearDescriptorImpl clearDescriptorImpl;
    final long clearTimestamp;
    @NonNull
    final ClearMode clearMode;

    ResetConversationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @Nullable ClearDescriptorImpl clearDescriptorImpl,
                        long clearTimestamp, @NonNull ClearMode clearMode) {

        super(serializer, requestId);

        this.clearDescriptorImpl = clearDescriptorImpl;
        this.clearTimestamp = clearTimestamp;
        this.clearMode = clearMode;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new ResetConversationIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" clearDescriptorImpl=");
            stringBuilder.append(clearDescriptorImpl);
            stringBuilder.append(" clearTimestamp=");
            stringBuilder.append(clearTimestamp);
            stringBuilder.append(" clearMode=");
            stringBuilder.append(clearMode);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("ResetConversationIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class ResetConversationIQSerializer extends BinaryPacketIQSerializer {

        ResetConversationIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, ResetConversationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ResetConversationIQ resetConversationIQ = (ResetConversationIQ) object;

            ClearDescriptorImpl clearDescriptorImpl = resetConversationIQ.clearDescriptorImpl;
            if (clearDescriptorImpl == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(clearDescriptorImpl.getTwincodeOutboundId());
                encoder.writeLong(clearDescriptorImpl.getSequenceId());
                encoder.writeLong(clearDescriptorImpl.getCreatedTimestamp());
                encoder.writeLong(clearDescriptorImpl.getSentTimestamp());
            }

            encoder.writeLong(resetConversationIQ.clearTimestamp);
            switch (resetConversationIQ.clearMode) {
                case CLEAR_LOCAL:
                    encoder.writeEnum(0);
                    break;

                case CLEAR_BOTH:
                    encoder.writeEnum(1);
                    break;

                // 2023-02-21: added this new clear mode but supported only starting with ConversationService 2.15.
                case CLEAR_MEDIA:
                    encoder.writeEnum(2);
                    break;
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final ClearDescriptorImpl clearDescriptorImpl;
            final long clearTimestamp;
            if (decoder.readEnum() == 1) {
                final UUID twincodeOutboundId = decoder.readUUID();
                final long sequenceId = decoder.readLong();
                final long createdTimestamp = decoder.readLong();
                final long sentTimestamp = decoder.readLong();

                clearTimestamp = decoder.readLong();
                clearDescriptorImpl = new ClearDescriptorImpl(new DescriptorId(0, twincodeOutboundId, sequenceId), createdTimestamp, sentTimestamp, clearTimestamp);
            } else {
                clearDescriptorImpl = null;
                clearTimestamp = decoder.readLong();
            }

            ClearMode clearMode;
            switch (decoder.readEnum()) {
                case 0:
                    clearMode = ClearMode.CLEAR_LOCAL;
                    break;

                case 1:
                    clearMode = ClearMode.CLEAR_BOTH;
                    break;

                // 2023-02-21: added this new clear mode but supported only starting with ConversationService 2.15.
                case 2:
                    clearMode = ClearMode.CLEAR_MEDIA;
                    break;

                default:
                    throw new SerializerException();
            }

            return new ResetConversationIQ(this, requestId, clearDescriptorImpl, clearTimestamp, clearMode);
        }
    }
}
