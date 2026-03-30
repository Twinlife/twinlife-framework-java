/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.Arrays;
import java.util.UUID;

/**
 * Generate backup key Response IQ.
 *
 * Schema version 1
 *  Date: 2025/09/02
 *
 *  <pre>
 * {
 *  "schemaId":"e5ac97e6-bf8b-4054-9115-17edaee8de83",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnGenerateBackupKeyIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"derivedServerKey", "type":"bytes"},
 *     {"name":"lastBackupId", "type":[null, "uuid"]},
 *     {"name":"lastBackupTimestamp", "type":"long"}
 *  ]
 * }
 * </pre>
 */
class OnGenerateBackupKeyIQ extends BinaryPacketIQ {

    private static class OnGenerateBackupKeyIQSerializer extends BinaryPacketIQSerializer {

        OnGenerateBackupKeyIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnGenerateBackupKeyIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {
            throw new SerializerException();
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            byte[] serverKey = decoder.readBytes(null).array();
            UUID lastBackupId = decoder.readOptionalUUID();
            long lastBackupTimestamp = decoder.readLong();

            return new OnGenerateBackupKeyIQ(this, serviceRequestIQ, serverKey, lastBackupId, lastBackupTimestamp);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnGenerateBackupKeyIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final byte[] derivedServerKey;
    @Nullable
    final UUID lastBackupId;
    final long lastBackupTimestamp;


    OnGenerateBackupKeyIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq,
                          @NonNull byte[] derivedServerKey, @Nullable UUID lastBackupId, long lastBackupTimestamp) {

        super(serializer, iq);

        this.derivedServerKey = derivedServerKey;
        this.lastBackupId = lastBackupId;
        this.lastBackupTimestamp = lastBackupTimestamp;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" derivedServerKey=");
            stringBuilder.append(Arrays.toString(derivedServerKey));
            stringBuilder.append(", lastBackupId=");
            stringBuilder.append(lastBackupId);
            stringBuilder.append(", lastBackupTimestamp=");
            stringBuilder.append(lastBackupTimestamp);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnGenerateBackupKeyIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
