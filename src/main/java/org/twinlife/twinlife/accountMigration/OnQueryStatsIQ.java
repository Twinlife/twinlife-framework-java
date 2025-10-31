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
 * Query stats response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"0906f883-6adf-4d90-9252-9ab401fbe531",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnQueryStatsIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"directoryCount", "type":"long"},
 *     {"name":"fileCount", "type":"long"},
 *     {"name":"maxFileSize", "type":"long"},
 *     {"name":"totalFileSize", "type":"long"},
 *     {"name":"databaseFileSize", "type":"long"},
 *     {"name":"localDatabaseSpace", "type":"long"},
 *     {"name":"localFilesystemSpace", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */
class OnQueryStatsIQ extends BinaryPacketIQ {

    static class OnQueryStatsIQSerializer extends BinaryPacketIQSerializer {

        OnQueryStatsIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnQueryStatsIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnQueryStatsIQ onQueryStatsIQ = (OnQueryStatsIQ)object;

            encoder.writeLong(onQueryStatsIQ.queryInfo.getDirectoryCount());
            encoder.writeLong(onQueryStatsIQ.queryInfo.getFileCount());
            encoder.writeLong(onQueryStatsIQ.queryInfo.getMaxFileSize());
            encoder.writeLong(onQueryStatsIQ.queryInfo.getTotalFileSize());
            encoder.writeLong(onQueryStatsIQ.queryInfo.getDatabaseFileSize());
            encoder.writeLong(onQueryStatsIQ.queryInfo.getFilesystemAvailableSpace());
            encoder.writeLong(onQueryStatsIQ.queryInfo.getDatabaseAvailableSpace());
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            long directoryCount = decoder.readLong();
            long fileCount = decoder.readLong();
            long maxFileSize = decoder.readLong();
            long totalFileSize = decoder.readLong();
            long databaseFileSize = decoder.readLong();
            long localFileAvailableSpace = decoder.readLong();
            long localDatabaseAvailableSpace = decoder.readLong();
            QueryInfoImpl queryInfo = new QueryInfoImpl(directoryCount, fileCount, maxFileSize, totalFileSize,
                    databaseFileSize, localFileAvailableSpace, localDatabaseAvailableSpace);
            return new OnQueryStatsIQ(this, serviceRequestIQ, queryInfo);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnQueryStatsIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final QueryInfoImpl queryInfo;

    OnQueryStatsIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                   @NonNull QueryInfoImpl queryInfo) {

        super(serializer, serviceRequestIQ);

        this.queryInfo = queryInfo;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" queryInfo=");
            stringBuilder.append(queryInfo);
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("OnQueryStatsIQ\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
