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
 * Synchronize IQ response.
 * <p>
 * Schema version 1
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"380ebc30-1aa9-4e66-bcd8-d0436b5724e8",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SynchronizeIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"deviceState", "type":"byte"},
 *     {"name":"timestamp", "type":"long"},
 *     {"name":"senderTimestamp", "type":"long"},
 *  ]
 * }
 *
 * </pre>
 */
class OnSynchronizeIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("380ebc30-1aa9-4e66-bcd8-d0436b5724e8");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQSerializer IQ_ON_SYNCHRONIZE_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    final int deviceState;
    final long timestamp;
    final long senderTimestamp;

    OnSynchronizeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    int deviceState, long timestamp, long senderTimestamp) {

        super(serializer, requestId);

        this.deviceState = deviceState;
        this.timestamp = timestamp;
        this.senderTimestamp = senderTimestamp;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnSynchronizeIQSerializer(schemaId, schemaVersion);
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
            stringBuilder.append(" timestamp=");
            stringBuilder.append(timestamp);
            stringBuilder.append(" senderTimestamp=");
            stringBuilder.append(senderTimestamp);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnSynchronizeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class OnSynchronizeIQSerializer extends BinaryPacketIQSerializer {

        OnSynchronizeIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnSynchronizeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnSynchronizeIQ onSynchronizeIQ = (OnSynchronizeIQ) object;

            encoder.writeInt(onSynchronizeIQ.deviceState);
            encoder.writeLong(onSynchronizeIQ.timestamp);
            encoder.writeLong(onSynchronizeIQ.senderTimestamp);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            long requestId = decoder.readLong();
            int deviceState = decoder.readInt();
            long timestamp = decoder.readLong();
            long senderTimestamp = decoder.readLong();

            return new OnSynchronizeIQ(this, requestId, deviceState, timestamp, senderTimestamp);
        }
    }
}
