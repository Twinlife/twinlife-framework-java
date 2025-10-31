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
import org.twinlife.twinlife.util.Utils;

import java.util.UUID;

/**
 * Authenticate Challenge Request IQ.
 *
 * Schema version 1, Schema version 2
 * <pre>
 * {
 *  "schemaId":"91780AB7-016A-463B-9901-434E52C200AE",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AuthChallengeIQ",
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
 *     {"name":"nonce", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class AuthChallengeIQ extends BinaryPacketIQ {

    private static class AuthChallengeIQSerializer extends BinaryPacketIQSerializer {

        AuthChallengeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, AuthChallengeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            AuthChallengeIQ authChallengeIQ = (AuthChallengeIQ) object;

            encoder.writeUUID(authChallengeIQ.applicationId);
            encoder.writeUUID(authChallengeIQ.serviceId);
            encoder.writeString(authChallengeIQ.apiKey);
            encoder.writeString(authChallengeIQ.accessToken);
            encoder.writeString(authChallengeIQ.applicationName);
            encoder.writeString(authChallengeIQ.applicationVersion);
            encoder.writeString(authChallengeIQ.twinlifeVersion);
            encoder.writeString(authChallengeIQ.accountIdentifier);
            encoder.writeData(authChallengeIQ.nonce);
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

        return new AuthChallengeIQSerializer(schemaId, schemaVersion);
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
    final byte[] nonce;

    AuthChallengeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull UUID applicationId, @NonNull UUID serviceId, @NonNull String apiKey, @NonNull String accessToken,
                    @NonNull String applicationName, @NonNull String applicationVersion, @NonNull String twinlifeVersion,
                    @NonNull String accountIdentifier, @NonNull byte[] nonce) {

        super(serializer, requestId);

        this.applicationId = applicationId;
        this.serviceId = serviceId;
        this.apiKey = apiKey;
        this.accessToken = accessToken;
        this.applicationName = applicationName;
        this.applicationVersion = applicationVersion;
        this.twinlifeVersion = twinlifeVersion;
        this.accountIdentifier = accountIdentifier;
        this.nonce = nonce;
    }

    @NonNull
    String getClientFirstMessageBare() {

        StringBuilder authMessage = new StringBuilder();
        authMessage.append(applicationId);
        authMessage.append(serviceId);
        authMessage.append(apiKey);
        authMessage.append(accessToken);
        authMessage.append(applicationName);
        authMessage.append(applicationVersion);
        authMessage.append(twinlifeVersion);
        authMessage.append(accountIdentifier);
        authMessage.append(Utils.encodeBase64(nonce));

        return authMessage.toString();
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
            stringBuilder.append(twinlifeVersion);
            stringBuilder.append(" twinlifeVersion=");
            stringBuilder.append(applicationVersion);
            stringBuilder.append(" accountIdentifier=");
            stringBuilder.append(accountIdentifier);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("AuthChallengeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
