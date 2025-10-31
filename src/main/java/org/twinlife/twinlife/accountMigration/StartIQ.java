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
 * Start migration IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"8a26fefe-6bd5-45e2-9098-3d736d8a1c4e",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"StartIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"maxFileSize", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */
class StartIQ extends BinaryPacketIQ {

    static class StartIQSerializer extends BinaryPacketIQSerializer {

        StartIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, StartIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            StartIQ startIQ = (StartIQ) object;

            encoder.writeLong(startIQ.maxFileSize);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            long maxFileSize = decoder.readLong();

            return new StartIQ(this, serviceRequestIQ, maxFileSize);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new StartIQSerializer(schemaId, schemaVersion);
    }

    final long maxFileSize;

    StartIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, long maxFileSize) {

        super(serializer, requestId);

        this.maxFileSize = maxFileSize;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" maxFileSize=");
            stringBuilder.append(maxFileSize);
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("StartIQ\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private StartIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ, long maxFileSize) {

        super(serializer, serviceRequestIQ);

        this.maxFileSize = maxFileSize;
    }
}
