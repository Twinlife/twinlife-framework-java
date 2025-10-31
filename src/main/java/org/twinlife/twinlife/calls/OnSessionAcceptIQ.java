/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.calls;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Session Accept response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"39b4838a-857c-4d03-9a63-c226fab2cd01",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnSessionAcceptIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"timestamp", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */
class OnSessionAcceptIQ extends BinaryPacketIQ {

    private static class OnSessionAcceptIQSerializer extends BinaryPacketIQSerializer {

        OnSessionAcceptIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnSessionAcceptIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnSessionAcceptIQ onSessionAcceptIQ = (OnSessionAcceptIQ) object;
            encoder.writeLong(onSessionAcceptIQ.timestamp);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            long timestamp = decoder.readLong();

            return new OnSessionAcceptIQ(this, serviceRequestIQ, timestamp);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnSessionAcceptIQSerializer(schemaId, schemaVersion);
    }

    final long timestamp;

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" timestamp=");
            stringBuilder.append(timestamp);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnSessionAcceptIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    OnSessionAcceptIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                      long timestamp) {

        super(serializer, serviceRequestIQ);

        this.timestamp = timestamp;
    }
}
