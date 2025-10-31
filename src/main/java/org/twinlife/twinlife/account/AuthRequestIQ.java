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
 * Authenticate Request after the AuthChallenge request IQ.
 *
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"BF0A6327-FD04-4DFF-998E-72253CFD91E5",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"AuthRequestIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"accountIdentifier", "type":"string"},
 *     {"name":"resourceIdentifier", "type":"string"},
 *     {"name":"deviceNonce", "type":"bytes"},
 *     {"name":"deviceProof", "type":"bytes"},
 *     {"name":"deviceState", "type":"int"}
 *     {"name":"deviceLatency", "type":"int"},
 *     {"name":"deviceTimestamp", "type":"long"},
 *     {"name":"serverTimestamp", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"BF0A6327-FD04-4DFF-998E-72253CFD91E5",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AuthRequestIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"accountIdentifier", "type":"string"},
 *     {"name":"resourceIdentifier", "type":"string"},
 *     {"name":"deviceNonce", "type":"bytes"},
 *     {"name":"deviceProof", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class AuthRequestIQ extends BinaryPacketIQ {

    private static class AuthRequestIQSerializer extends BinaryPacketIQSerializer {

        AuthRequestIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, AuthRequestIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            AuthRequestIQ authRequestIQ = (AuthRequestIQ) object;

            encoder.writeString(authRequestIQ.accountIdentifier);
            encoder.writeString(authRequestIQ.resourceIdentifier);
            encoder.writeData(authRequestIQ.deviceNonce);
            encoder.writeData(authRequestIQ.deviceProof);
            encoder.writeInt(authRequestIQ.deviceState);
            encoder.writeInt(authRequestIQ.deviceLatency);
            encoder.writeLong(authRequestIQ.deviceTimestamp);
            encoder.writeLong(authRequestIQ.serverTimestamp);
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

        return new AuthRequestIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final String accountIdentifier;
    @NonNull
    final String resourceIdentifier;
    @NonNull
    final byte[] deviceNonce;
    @NonNull
    final byte[] deviceProof;
    final int deviceState;
    final int deviceLatency;
    final long deviceTimestamp;
    final long serverTimestamp;

    AuthRequestIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                  @NonNull String accountIdentifier, @NonNull String resourceIdentifier, @NonNull byte[] deviceNone,
                  @NonNull byte[] deviceProof, int deviceState, int deviceLatency,
                  long deviceTimestamp, long serverTimestamp) {

        super(serializer, requestId);

        this.accountIdentifier = accountIdentifier;
        this.resourceIdentifier = resourceIdentifier;
        this.deviceNonce = deviceNone;
        this.deviceProof = deviceProof;
        this.deviceState = deviceState;
        this.deviceLatency = deviceLatency;
        this.deviceTimestamp = deviceTimestamp;
        this.serverTimestamp = serverTimestamp;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" accountIdentifier=");
            stringBuilder.append(accountIdentifier);
            stringBuilder.append(" resourceIdentifier=");
            stringBuilder.append(resourceIdentifier);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("AuthRequestIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
