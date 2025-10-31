/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
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

import java.util.UUID;

/**
 * Create account Request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"84449ECB-F09F-4C12-A936-038948C2D980",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"CreateAccountIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"applicationId", "type":"uuid"},
 *     {"name":"serviceId", "type":"uuid"},
 *     {"name":"apiKey", "type":"string"},
 *     {"name":"accessToken", "type":"string"},
 *     {"name":"applicationName", "type":"string"},
 *     {"name":"applicationVersion", "type":"string"},
 *     {"name":"twinlifeVersion", "type":"string"},
 *     {"name":"accountIdentifier", "type":"string"},
 *     {"name":"accountPassword", "type":"string"},
 *     {"name":"authToken", [null, "type":"string"}]
 *  ]
 * }
 *
 * </pre>
 */
class CreateAccountIQ extends BinaryPacketIQ {

    private static class CreateAccountIQSerializer extends BinaryPacketIQSerializer {

        CreateAccountIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, CreateAccountIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            CreateAccountIQ createAccountIQ = (CreateAccountIQ) object;

            encoder.writeUUID(createAccountIQ.applicationId);
            encoder.writeUUID(createAccountIQ.serviceId);
            encoder.writeString(createAccountIQ.apiKey);
            encoder.writeString(createAccountIQ.accessToken);
            encoder.writeString(createAccountIQ.applicationName);
            encoder.writeString(createAccountIQ.applicationVersion);
            encoder.writeString(createAccountIQ.twinlifeVersion);
            encoder.writeString(createAccountIQ.accountIdentifier);
            encoder.writeString(createAccountIQ.accountPassword);
            encoder.writeOptionalString(createAccountIQ.authToken);
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

        return new CreateAccountIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID applicationId;
    @NonNull
    final UUID serviceId;
    @NonNull
    final String apiKey;
    @NonNull
    final String accessToken;
    @NonNull
    final String applicationName;
    @NonNull
    final String applicationVersion;
    @NonNull
    final String twinlifeVersion;
    @NonNull
    final String accountIdentifier;
    @NonNull
    final String accountPassword;
    @Nullable
    final String authToken;

    CreateAccountIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull UUID applicationId, @NonNull UUID serviceId, @NonNull String apiKey, @NonNull String accessToken,
                    @NonNull String applicationName, @NonNull String applicationVersion, @NonNull String twinlifeVersion,
                    @NonNull String accountIdentifier,
                    @NonNull String accountPassword, @Nullable String authToken) {

        super(serializer, requestId);

        this.applicationId = applicationId;
        this.serviceId = serviceId;
        this.apiKey = apiKey;
        this.accessToken = accessToken;
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
        this.twinlifeVersion = twinlifeVersion;
        this.accountIdentifier = accountIdentifier;
        this.accountPassword = accountPassword;
        this.authToken = authToken;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" applicationId=");
            stringBuilder.append(applicationId);
            stringBuilder.append(" serviceId=");
            stringBuilder.append(serviceId);
            stringBuilder.append(" apiKey=");
            stringBuilder.append(apiKey);
            stringBuilder.append(" accessToken=");
            stringBuilder.append(accessToken);
            stringBuilder.append(" applicationName=");
            stringBuilder.append(applicationName);
            stringBuilder.append(" applicationVersion=");
            stringBuilder.append(applicationVersion);
            stringBuilder.append(" twinlifeVersion=");
            stringBuilder.append(twinlifeVersion);
            stringBuilder.append(" accountIdentifier=");
            stringBuilder.append(accountIdentifier);
            stringBuilder.append(" accountPassword=");
            stringBuilder.append(accountPassword);
            stringBuilder.append(" authToken=");
            stringBuilder.append(authToken);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("CreateAccountIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
