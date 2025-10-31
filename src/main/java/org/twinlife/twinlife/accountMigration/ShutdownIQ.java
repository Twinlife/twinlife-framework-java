/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Shutdown IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"05c90756-d56c-4e2f-92bf-36b2d3f31b76",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ShutdownIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"close", "type":"boolean"}
 *  ]
 * }
 *
 * </pre>
 */
class ShutdownIQ extends BinaryPacketIQ {

    static class ShutdownIQSerializer extends BinaryPacketIQSerializer {

        ShutdownIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, ShutdownIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ShutdownIQ shutdownIQ = (ShutdownIQ) object;

            encoder.writeBoolean(shutdownIQ.close);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            boolean close = decoder.readBoolean();

            return new ShutdownIQ(this, serviceRequestIQ.getRequestId(), close);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new ShutdownIQSerializer(schemaId, schemaVersion);
    }

    final boolean close;

    ShutdownIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, boolean close) {

        super(serializer, requestId);

        this.close = close;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" close=");
            stringBuilder.append(close);
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("ShutdownIQ\n");
            appendTo(stringBuilder);
        }
        return stringBuilder.toString();
    }
}
