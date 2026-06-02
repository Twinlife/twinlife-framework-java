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
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * PushPoll IQ.
 * <p>
 * Schema version 1
 * Date: 2026/03/20
 *
 * <pre>
 * {
 *  "schemaId":"963e3df1-dad7-43c1-8135-f858b94d69ab",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushPollIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"sendToTwincodeOutboundId", "type":["null", "UUID"]},
 *     {"name":"createdTimestamp", "type":"long"}
 *     {"name":"sentTimestamp", "type":"long"}
 *     {"name":"expireTimeout", "type":"long"}
 *     {"name":"multipleAnswersAllowed", "type":"boolean"}
 *     {"name":"copyAllowed", "type":"boolean"}
 *     {"name":"question", "type":"string"}
 *     [
 *        {"name":"position", "type":"int"},
 *        {"name":"label", "type":"string"}
 *     ]
 *  ]
 * }
 *
 * </pre>
 */
class PushPollIQ extends BinaryPacketIQ {

    static final int SCHEMA_VERSION_1 = 1;
    static final UUID SCHEMA_ID = UUID.fromString("963e3df1-dad7-43c1-8135-f858b94d69ab");
    static final BinaryPacketIQSerializer IQ_PUSH_POLL_SERIALIZER = new PushPollIQSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    @NonNull
    final PollDescriptorImpl pollDescriptorImpl;

    PushPollIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull PollDescriptorImpl pollDescriptorImpl) {

        super(serializer, requestId);

        this.pollDescriptorImpl = pollDescriptorImpl;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" pollDescriptor=");
            stringBuilder.append(pollDescriptorImpl);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushPollIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class PushPollIQSerializer extends BinaryPacketIQSerializer {

        PushPollIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PushGeolocationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PushPollIQ pushPollIQ = (PushPollIQ) object;

            PollDescriptorImpl pollDescriptor = pushPollIQ.pollDescriptorImpl;

            encoder.writeUUID(pollDescriptor.getTwincodeOutboundId());
            encoder.writeLong(pollDescriptor.getSequenceId());
            encoder.writeOptionalUUID(pollDescriptor.getSendTo());

            encoder.writeLong(pollDescriptor.getCreatedTimestamp());
            encoder.writeLong(pollDescriptor.getSentTimestamp());
            encoder.writeLong(pollDescriptor.getExpireTimeout());

            encoder.writeBoolean(pollDescriptor.isMultipleChoicesAllowed());
            encoder.writeBoolean(pollDescriptor.isCopyAllowed());
            encoder.writeString(pollDescriptor.getQuestion());

            encoder.writeInt(pollDescriptor.getChoices().size());
            for (ConversationService.PollDescriptor.Choice choice : pollDescriptor.getChoices()) {
                encoder.writeInt(choice.position);
                encoder.writeString(choice.label);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final UUID sendTo = decoder.readOptionalUUID();

            final long createdTimestamp = decoder.readLong();
            final long sentTimestamp = decoder.readLong();
            final long expireTimeout = decoder.readLong();

            final boolean multipleChoicesAllowed = decoder.readBoolean();
            final boolean copyAllowed = decoder.readBoolean();
            final String question = decoder.readString();

            List<ConversationService.PollDescriptor.Choice> choices = new ArrayList<>();

            final int nbChoices = decoder.readInt();
            for (int i = 0; i < nbChoices; i++) {
                final int position = decoder.readInt();
                final String label = decoder.readString();
                choices.add(new ConversationService.PollDescriptor.Choice(position, label));
            }

            PollDescriptorImpl pollDescriptor = new PollDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, createdTimestamp, sentTimestamp, multipleChoicesAllowed, question, choices, copyAllowed);

            return new PushPollIQ(this, requestId, pollDescriptor);
        }
    }
}
