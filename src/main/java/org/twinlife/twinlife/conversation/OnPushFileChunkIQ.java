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
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * OnPushFileChunk IQ.
 * <p>
 * Schema version 2
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"af9e04d2-88c5-4054-8707-ad5f06ce9fc4",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnPushFileChunkIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"deviceState", "type":"byte"},
 *     {"name":"receivedTimestamp", "type":"long"}
 *     {"name":"senderTimestamp", "type":"long"}
 *     {"name":"nextChunkStart", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */
class OnPushFileChunkIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("af9e04d2-88c5-4054-8707-ad5f06ce9fc4");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_PUSH_FILE_CHUNK_SERIALIZER = OnPushFileChunkIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

    final int deviceState;
    final long receivedTimestamp;
    final long senderTimestamp;
    final long nextChunkStart;

    OnPushFileChunkIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, int deviceState,
                      long receivedTimestamp, long senderTimestamp, long nextChunkStart) {

        super(serializer, requestId);

        this.deviceState = deviceState;
        this.receivedTimestamp = receivedTimestamp;
        this.senderTimestamp = senderTimestamp;
        this.nextChunkStart = nextChunkStart;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnPushFileChunkIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" deviceState=");
            stringBuilder.append(deviceState);
            stringBuilder.append(" receivedTimestamp=");
            stringBuilder.append(receivedTimestamp);
            stringBuilder.append(" senderTimestamp=");
            stringBuilder.append(senderTimestamp);
            stringBuilder.append(" nextChunkStart=");
            stringBuilder.append(nextChunkStart);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnPushFileChunkIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class OnPushFileChunkIQSerializer extends BinaryPacketIQSerializer {

        OnPushFileChunkIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnPushFileChunkIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnPushFileChunkIQ onPushFileChunkIQ = (OnPushFileChunkIQ) object;

            encoder.writeInt(onPushFileChunkIQ.deviceState);
            encoder.writeLong(onPushFileChunkIQ.receivedTimestamp);
            encoder.writeLong(onPushFileChunkIQ.senderTimestamp);
            encoder.writeLong(onPushFileChunkIQ.nextChunkStart);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final int deviceState = decoder.readInt();
            final long receivedTimestamp = decoder.readLong();
            final long senderTimestamp = decoder.readLong();
            final long nextChunkStart = decoder.readLong();

            return new OnPushFileChunkIQ(this, requestId, deviceState, receivedTimestamp, senderTimestamp, nextChunkStart);
        }
    }
}
