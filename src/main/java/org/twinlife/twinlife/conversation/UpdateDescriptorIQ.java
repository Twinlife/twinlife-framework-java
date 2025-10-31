/*
 *  Copyright (c) 2025 twinlife SA.
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
 * UpdateDescriptor IQ.
 * <p>
 * Schema version 1
 *  Date: 2025/05/21
 *
 * <pre>
 * {
 *  "schemaId":"346eea33-61e9-460d-bf2c-2d6d487a7bc6",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"UpdateDescriptorIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"updatedTimestamp", "type":"long"}
 *     {"name":"expireTimeout", "type":["null", "long"]}
 *     {"name":"copyAllowed", "type":["null", "boolean"]}
 *     {"name":"message", "type":"String"}
 *  ]
 * }
 *
 * </pre>
 */
class UpdateDescriptorIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("346eea33-61e9-460d-bf2c-2d6d487a7bc6");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQSerializer IQ_UPDATE_DESCRIPTOR_SERIALIZER = UpdateDescriptorIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    @NonNull
    final DescriptorId descriptorId;
    final long updatedTimestamp;
    @Nullable
    final String message;
    @Nullable
    final Long expiredTimeout;
    @Nullable
    final Boolean copyAllowed;

    UpdateDescriptorIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                       @NonNull DescriptorId descriptorId, long updateTimestamp,
                       @Nullable String message, @Nullable Boolean copyAllowed,
                       @Nullable Long expiredTimeout) {

        super(serializer, requestId);

        this.descriptorId = descriptorId;
        this.updatedTimestamp = updateTimestamp;
        this.message = message;
        this.expiredTimeout = expiredTimeout;
        this.copyAllowed = copyAllowed;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new UpdateDescriptorIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" descriptorId=");
            stringBuilder.append(descriptorId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("UpdateObjectIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class UpdateDescriptorIQSerializer extends BinaryPacketIQSerializer {

        UpdateDescriptorIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, UpdateDescriptorIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final UpdateDescriptorIQ updateDescriptorIQ = (UpdateDescriptorIQ) object;

            encoder.writeUUID(updateDescriptorIQ.descriptorId.twincodeOutboundId);
            encoder.writeLong(updateDescriptorIQ.descriptorId.sequenceId);
            encoder.writeLong(updateDescriptorIQ.updatedTimestamp);
            if (updateDescriptorIQ.expiredTimeout == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeLong(updateDescriptorIQ.expiredTimeout);
            }
            if (updateDescriptorIQ.copyAllowed == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeBoolean(updateDescriptorIQ.copyAllowed);
            }
            encoder.writeOptionalString(updateDescriptorIQ.message);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final long updatedTimestamp = decoder.readLong();
            final Long expireTimeout;
            if (decoder.readEnum() == 0) {
                expireTimeout = null;
            } else {
                expireTimeout = decoder.readLong();
            }
            final Boolean copyAllowed;
            if (decoder.readEnum() == 0) {
                copyAllowed = null;
            } else {
                copyAllowed = decoder.readBoolean();
            }
            final String message = decoder.readOptionalString();

            return new UpdateDescriptorIQ(this, requestId, new DescriptorId(0, twincodeOutboundId, sequenceId),
                    updatedTimestamp, message, copyAllowed, expireTimeout);
        }
    }
}
