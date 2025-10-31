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
 * Set push token request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"3c1115d7-ed74-4445-b689-63e9c10eb50c",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SetPushTokenIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"environmentId", "type":"uuid"}
 *     {"name":"pushVariant", "type":"string"},
 *     {"name":"pushToken", "type":"string"}
 *     {"name":"pushRemoteToken", [null, "type":"string"]}
 *  ]
 * }
 *
 * </pre>
 */
class SetPushTokenIQ extends BinaryPacketIQ {

    private static class SetPushTokenIQSerializer extends BinaryPacketIQSerializer {

        SetPushTokenIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SetPushTokenIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            SetPushTokenIQ setPushTokenIQ = (SetPushTokenIQ) object;

            encoder.writeUUID(setPushTokenIQ.environmentId);
            encoder.writeString(setPushTokenIQ.pushVariant);
            encoder.writeString(setPushTokenIQ.pushToken);
            encoder.writeOptionalString(null);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new SetPushTokenIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID environmentId;
    @NonNull
    final String pushVariant;
    @NonNull
    final String pushToken;

    SetPushTokenIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID environmentId,
                   @NonNull String pushVariant, @NonNull String pushToken) {

        super(serializer, requestId);

        this.environmentId = environmentId;
        this.pushVariant = pushVariant;
        this.pushToken = pushToken;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" environmentId=");
            stringBuilder.append(environmentId);
            stringBuilder.append(" pushVariant=");
            stringBuilder.append(pushVariant);
            stringBuilder.append(" pushToken=");
            stringBuilder.append(pushToken);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SetPushTokenIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
