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
 * Synchronize IQ.
 * <p>
 * Schema version 1
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"d2447a5f-7aed-439a-808b-2858c5f1ba39",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SynchronizeIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"peerTwincodeOutboundId", "type":"UUID"},
 *     {"name":"resourceId", "type":"UUID"},
 *     {"name":"timestamp", "type":"long"},
 *  ]
 * }
 *
 * </pre>
 */
class SynchronizeIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("d2447a5f-7aed-439a-808b-2858c5f1ba39");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQSerializer IQ_SYNCHRONIZE_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    @NonNull
    final UUID peerTwincodeOutboundId;
    @NonNull
    final UUID resourceId;
    final long timestamp;
    SynchronizeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                  @NonNull UUID peerTwincodeOutboundId, @NonNull UUID resourceId, long timestamp) {

        super(serializer, requestId);

        this.peerTwincodeOutboundId = peerTwincodeOutboundId;
        this.resourceId = resourceId;
        this.timestamp = timestamp;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new SynchronizeIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" peerTwincodeOutboundId=");
            stringBuilder.append(peerTwincodeOutboundId);
            stringBuilder.append(" resourceId=");
            stringBuilder.append(resourceId);
            stringBuilder.append(" timestamp=");
            stringBuilder.append(timestamp);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SynchronizeIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class SynchronizeIQSerializer extends BinaryPacketIQSerializer {

        SynchronizeIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SynchronizeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            SynchronizeIQ synchronizeIQ = (SynchronizeIQ) object;

            encoder.writeUUID(synchronizeIQ.peerTwincodeOutboundId);
            encoder.writeUUID(synchronizeIQ.resourceId);
            encoder.writeLong(synchronizeIQ.timestamp);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            long requestId = decoder.readLong();
            UUID peerTwincodeOutboundId = decoder.readUUID();
            UUID resourceId = decoder.readUUID();
            long timestamp = decoder.readLong();

            return new SynchronizeIQ(this, requestId, peerTwincodeOutboundId, resourceId, timestamp);
        }
    }
}
