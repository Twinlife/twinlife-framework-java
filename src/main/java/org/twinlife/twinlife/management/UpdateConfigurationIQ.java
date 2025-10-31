/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.management;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Update configuration IQ.
 *
 * Schema version 1 AND schema version 2
 * <pre>
 * {
 *  "schemaId":"3b726b45-c3fc-4062-8ecd-0ddab2dd1537",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"UpdateConfigurationIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"environmentId", "type":"uuid"}
 *  ]
 * }
 *
 * </pre>
 */
class UpdateConfigurationIQ extends BinaryPacketIQ {

    private static class UpdateConfigurationIQSerializer extends BinaryPacketIQSerializer {

        UpdateConfigurationIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, UpdateConfigurationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            UpdateConfigurationIQ updateConfigurationIQ = (UpdateConfigurationIQ) object;

            encoder.writeOptionalUUID(updateConfigurationIQ.environmentId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID environmentId = decoder.readUUID();

            return new UpdateConfigurationIQ(this, serviceRequestIQ.getRequestId(), environmentId);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new UpdateConfigurationIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID environmentId;

    UpdateConfigurationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID environmentId) {

        super(serializer, requestId);

        this.environmentId = environmentId;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" environmentId=");
            stringBuilder.append(environmentId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("UpdateConfigurationIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
