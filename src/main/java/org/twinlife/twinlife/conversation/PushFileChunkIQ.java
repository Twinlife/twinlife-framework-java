/*
 *  Copyright (c) 2021-2025 twinlife SA.
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
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * PushFileChunk IQ.
 * <pre>
 *
 * Schema version 2
 *  Date: 2021/04/07
 *
 * {
 *  "schemaId":"ae5192f5-f505-4211-84c5-76cb5bf9b147",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"PushFileChunkIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields":
 *  [
 *   {"name":"twincodeOutboundId", "type":"uuid"}
 *   {"name":"sequenceId", "type":"long"}
 *   {"name":"timestamp", "type":"long"}
 *   {"name":"chunkStart", "type":"long"}
 *   {"name":"chunk", "type":[null, "bytes"]}
 *  ]
 * }
 *
 * </pre>
 */
class PushFileChunkIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("ae5192f5-f505-4211-84c5-76cb5bf9b147");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQSerializer IQ_PUSH_FILE_CHUNK_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

    @NonNull
    final DescriptorId descriptorId;
    final long timestamp;
    final long chunkStart;
    final int size;
    @Nullable
    final byte[] chunk;
    final int startPos;

    PushFileChunkIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull DescriptorId descriptorId, long timestamp,
                    long chunkStart, int startPos, @Nullable byte[] chunk, int size) {

        super(serializer, requestId);

        this.descriptorId = descriptorId;
        this.timestamp = timestamp;
        this.chunkStart = chunkStart;
        this.startPos = startPos;
        this.chunk = chunk;
        this.size = size;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new PushFileChunkIQSerializer(schemaId, schemaVersion);
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
            stringBuilder.append(" timestamp=");
            stringBuilder.append(timestamp);
            stringBuilder.append(" chunkStart=");
            stringBuilder.append(chunkStart);
            stringBuilder.append(" size=");
            stringBuilder.append(size);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushFileChunkIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class PushFileChunkIQSerializer extends BinaryPacketIQSerializer {

        PushFileChunkIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PushFileChunkIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PushFileChunkIQ pushFileChunkIQ = (PushFileChunkIQ) object;

            encoder.writeUUID(pushFileChunkIQ.descriptorId.twincodeOutboundId);
            encoder.writeLong(pushFileChunkIQ.descriptorId.sequenceId);
            encoder.writeLong(pushFileChunkIQ.timestamp);
            encoder.writeLong(pushFileChunkIQ.chunkStart);
            if (pushFileChunkIQ.chunk != null) {
                encoder.writeEnum(1);
                encoder.writeBytes(pushFileChunkIQ.chunk, pushFileChunkIQ.startPos, pushFileChunkIQ.size);
            } else {
                encoder.writeEnum(0);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final long timestamp = decoder.readLong();
            final long chunkStart = decoder.readLong();
            byte[] chunk;
            int size;
            if (decoder.readEnum() == 1) {
                chunk = decoder.readBytes(null).array();
                size = chunk.length;
            } else {
                chunk = null;
                size = 0;
            }

            return new PushFileChunkIQ(this, requestId, new DescriptorId(0, twincodeOutboundId, sequenceId),
                    timestamp, chunkStart, 0, chunk, size);
        }
    }
}
