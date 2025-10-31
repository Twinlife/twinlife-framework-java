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
 * Query stats IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"4b201b06-7952-43a4-8157-96b9aeffa667",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"QueryStatsIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"maxFileSize", "type":"long"},
 *  ]
 * }
 *
 * </pre>
 */
class QueryStatsIQ extends BinaryPacketIQ {

    static class QueryStatsIQSerializer extends BinaryPacketIQSerializer {

        QueryStatsIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, QueryStatsIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            QueryStatsIQ queryStatsIQ = (QueryStatsIQ)object;
            encoder.writeLong(queryStatsIQ.maxFileSize);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            long maxFileSize = decoder.readLong();
            return new QueryStatsIQ(this, serviceRequestIQ, maxFileSize);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new QueryStatsIQSerializer(schemaId, schemaVersion);
    }

    final long maxFileSize;

    QueryStatsIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, long maxFileSize) {

        super(serializer, requestId);

        this.maxFileSize = maxFileSize;
    }

    //
    // Override Object methods
    //
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("QueryStatsIQ\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private QueryStatsIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ, long maxFileSize) {

        super(serializer, serviceRequestIQ);

        this.maxFileSize = maxFileSize;
    }
}
