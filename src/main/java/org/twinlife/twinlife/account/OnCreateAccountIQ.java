/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Create Account Response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"3D8A1111-61F8-4B27-8229-43DE24A9709B",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnCreateAccountIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"environmentId", "type":"uuid"}
 *  ]
 * }
 *
 * </pre>
 */
class OnCreateAccountIQ extends BinaryPacketIQ {

    private static class OnCreateAccountIQSerializer extends BinaryPacketIQSerializer {

        OnCreateAccountIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnCreateAccountIQ.class);
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

            UUID environmentId = decoder.readUUID();

            return new OnCreateAccountIQ(this, serviceRequestIQ, environmentId);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnCreateAccountIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID environmentId;

    OnCreateAccountIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, @NonNull UUID environmentId) {

        super(serializer, iq);

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
            stringBuilder.append("OnCreateAccountIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
