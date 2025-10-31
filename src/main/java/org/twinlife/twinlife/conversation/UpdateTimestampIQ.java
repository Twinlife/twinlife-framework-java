/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Houssem Temanni (Houssem.Temanni@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.conversation.UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * UpdateTimestampIQ IQ.
 * <p>
 * Schema version 2
 *  Date: 2024/06/17
 *
 * <pre>
 * {
 *  "schemaId":"b814c454-299b-48c0-aa40-19afa72ccef8",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"UpdateTimestampIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"type", ["READ", "DELETE", "PEER_DELETE"]}
 *     {"name":"timestamp", "type":"long"}
 * }
 *
 * </pre>
 */
class UpdateTimestampIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("b814c454-299b-48c0-aa40-19afa72ccef8");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQSerializer IQ_UPDATE_TIMESTAMPS_SERIALIZER = UpdateTimestampIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

    @NonNull
    final DescriptorId descriptorId;
    @NonNull
    final UpdateDescriptorTimestampType timestampType;
    final long timestamp;

    UpdateTimestampIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull DescriptorId descriptorId,
                      @NonNull UpdateDescriptorTimestampType timestampType, long timestamp) {

        super(serializer, requestId);

        this.descriptorId = descriptorId;
        this.timestampType = timestampType;
        this.timestamp = timestamp;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new UpdateDescriptorTimestampIQSerializer(schemaId, schemaVersion);
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
            stringBuilder.append("UpdateTimestampIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class UpdateDescriptorTimestampIQSerializer extends BinaryPacketIQSerializer {

        UpdateDescriptorTimestampIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, UpdateTimestampIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            UpdateTimestampIQ updateTimestampIQ = (UpdateTimestampIQ) object;
            encoder.writeUUID(updateTimestampIQ.descriptorId.twincodeOutboundId);
            encoder.writeLong(updateTimestampIQ.descriptorId.sequenceId);
            switch (updateTimestampIQ.timestampType) {
                case READ:
                    encoder.writeEnum(0);
                    break;

                case DELETE:
                    encoder.writeEnum(1);
                    break;

                case PEER_DELETE:
                    encoder.writeEnum(2);
                    break;
            }

            encoder.writeLong(updateTimestampIQ.timestamp);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final DescriptorId descriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
            final UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType mode;
            switch (decoder.readEnum()) {
                case 0:
                    mode = UpdateDescriptorTimestampType.READ;
                    break;

                case 1:
                    mode = UpdateDescriptorTimestampType.DELETE;
                    break;

                case 2:
                    mode = UpdateDescriptorTimestampType.PEER_DELETE;
                    break;

                default:
                    throw new SerializerException();
            }
            final long timestamp = decoder.readLong();

            return new UpdateTimestampIQ(this, requestId, descriptorId, mode, timestamp);
        }
    }
}
