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
 * Authenticate Challenge Response IQ.
 *
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"A5F47729-2FEE-4B38-AC91-3A67F3F9E1B6",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnAuthChallengeIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"salt", "type":"bytes"},
 *     {"name":"iteration", "type":"int"},
 *     {"name":"server-nonce", "type":"bytes"},
 *     {"name":"serverTimestamp", "type":"long"}
 *  ]
 * }
 * </pre>
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"A5F47729-2FEE-4B38-AC91-3A67F3F9E1B6",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnAuthChallengeIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"salt", "type":"bytes"},
 *     {"name":"iteration", "type":"int"},
 *     {"name":"server-nonce", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class OnAuthChallengeIQ extends BinaryPacketIQ {

    private static class OnAuthChallengeIQSerializer extends BinaryPacketIQSerializer {

        OnAuthChallengeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnAuthChallengeIQ.class);
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

            byte[] salt = decoder.readBytes(null).array();
            int iteration = decoder.readInt();
            byte[] nonce = decoder.readBytes(null).array();
            long serverTimestamp = decoder.readLong();

            return new OnAuthChallengeIQ(this, serviceRequestIQ, salt, iteration, nonce, serverTimestamp);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnAuthChallengeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final byte[] salt;
    final int iteration;
    @NonNull
    final byte[] serverNonce;
    final long serverTimestamp;

    OnAuthChallengeIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq,
                      @NonNull byte[] salt, int iteration, @NonNull byte[] nonce, long serverTimestamp) {

        super(serializer, iq);

        this.salt = salt;
        this.iteration = iteration;
        this.serverNonce = nonce;
        this.serverTimestamp = serverTimestamp;
    }

    @NonNull
    String getServerFirstMessage() {

        StringBuilder authMessage = new StringBuilder();
        authMessage.append(Utils.encodeBase64(salt));
        authMessage.append(iteration);
        authMessage.append(Utils.encodeBase64(serverNonce));

        return authMessage.toString();
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" salt=");
            stringBuilder.append(salt.length);
            stringBuilder.append(" iteration=");
            stringBuilder.append(iteration);
            stringBuilder.append(" serverTimestamp=");
            stringBuilder.append(serverTimestamp);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnAuthChallengeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
