/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.InvitationDescriptor;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * AnswerContactShare IQ.
 * <p>
 * Schema version 1
 * Date: 2026/07/06
 *
 * <pre>
 * {
 *  "schemaId":"f2a9dbf3-4439-47a6-b2ab-20ac59b57d48",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AnswerContactShareIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"},
 *     {"name":"sequenceId", "type":"long"},
 *     {"name":"status", "type":"enum"},
 *     {"name":"autoAnswer", "type":"boolean"},
 *     {"name":"invitationTwincodeOutboundId", "type":["null", "UUID"]},
 *     {"name":"invitationTwincodeOutboundPubkey", "type":["null", "string"]}
 *  ]
 * }
 *
 * </pre>
 */
class AnswerContactShareIQ extends BinaryPacketIQ {

    static final int SCHEMA_VERSION_1 = 1;
    static final UUID SCHEMA_ID = UUID.fromString("f2a9dbf3-4439-47a6-b2ab-20ac59b57d48");
    static final BinaryPacketIQSerializer IQ_ANSWER_CONTACT_SHARE_SERIALIZER = new AnswerContactShareIQSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    @NonNull
    final DescriptorId contactShareDescriptorId;
    @NonNull
    final InvitationDescriptor.Status status;
    final boolean autoAnswer;
    @Nullable
    final UUID invitationTwincodeOutboundId;
    @Nullable
    final CryptoService.PublicKeyData invitationTwincodeOutboundPubkey;

    AnswerContactShareIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull DescriptorId contactShareDescriptorId, @NonNull InvitationDescriptor.Status status, boolean autoAnswer, @Nullable UUID invitationTwincodeOutboundId, @Nullable CryptoService.PublicKeyData invitationTwincodeOutboundPubkey) {

        super(serializer, requestId);

        this.contactShareDescriptorId = contactShareDescriptorId;
        this.status = status;
        this.autoAnswer = autoAnswer;
        this.invitationTwincodeOutboundId = invitationTwincodeOutboundId;
        this.invitationTwincodeOutboundPubkey = invitationTwincodeOutboundPubkey;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" contactShareDescriptorId=");
            stringBuilder.append(contactShareDescriptorId);
            stringBuilder.append(" status=");
            stringBuilder.append(status);
            stringBuilder.append(" autoAnswer=");
            stringBuilder.append(autoAnswer);
            stringBuilder.append(" invitationTwincodeOutboundId=");
            stringBuilder.append(invitationTwincodeOutboundId);
            stringBuilder.append(" invitationTwincodeOutboundPubkey=");
            stringBuilder.append(invitationTwincodeOutboundPubkey != null ? invitationTwincodeOutboundPubkey.asString() : "null");
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("AnswerContactShareIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class AnswerContactShareIQSerializer extends BinaryPacketIQSerializer {

        AnswerContactShareIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, AnswerContactShareIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            AnswerContactShareIQ answerContactShareIQ = (AnswerContactShareIQ) object;

            encoder.writeUUID(answerContactShareIQ.contactShareDescriptorId.twincodeOutboundId);
            encoder.writeLong(answerContactShareIQ.contactShareDescriptorId.sequenceId);

            encoder.writeEnum(answerContactShareIQ.status.toInt());
            encoder.writeBoolean(answerContactShareIQ.autoAnswer);
            encoder.writeOptionalUUID(answerContactShareIQ.invitationTwincodeOutboundId);
            encoder.writeOptionalString(answerContactShareIQ.invitationTwincodeOutboundPubkey != null ? answerContactShareIQ.invitationTwincodeOutboundPubkey.asString() : null);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();

            final DescriptorId contactShareDescriptorId = new DescriptorId(0, decoder.readUUID(), decoder.readLong());

            final InvitationDescriptor.Status status = InvitationDescriptor.Status.toStatus(decoder.readEnum());
            final boolean autoAnswer = decoder.readBoolean();
            final UUID invitationTwincodeOutboundId = decoder.readOptionalUUID();
            final String pubkeyString = decoder.readOptionalString();
            final CryptoService.PublicKeyData invitationTwincodeOutboundPubkey = CryptoService.PublicKeyData.create(pubkeyString);

            return new AnswerContactShareIQ(this, requestId, contactShareDescriptorId, status, autoAnswer, invitationTwincodeOutboundId, invitationTwincodeOutboundPubkey);
        }
    }
}
