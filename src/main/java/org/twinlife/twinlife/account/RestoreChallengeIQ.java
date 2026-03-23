/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Utils;

import java.util.Arrays;
import java.util.UUID;

/**
 * Restore challenge IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"093b4e5c-3040-48d1-9981-cb1f20c16d89",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"StartRestoreChallengeIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"backupId", "type":"uuid"},
 *     {"name":"accountIdentifier", "type":"string"},
 *     {"name":"nonce", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class RestoreChallengeIQ extends BinaryPacketIQ {

    private static class GenerateBackupKeyIQSerializer extends BinaryPacketIQSerializer {

        GenerateBackupKeyIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, RestoreChallengeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            RestoreChallengeIQ restoreChallengeIQ = (RestoreChallengeIQ) object;

            encoder.writeUUID(restoreChallengeIQ.backupId);
            encoder.writeString(restoreChallengeIQ.accountIdentifier);
            encoder.writeData(restoreChallengeIQ.nonce);
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

        return new GenerateBackupKeyIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID backupId;
    @NonNull
    final String accountIdentifier;
    @NonNull
    final byte[] nonce;

    public RestoreChallengeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                               @NonNull UUID backupId, @NonNull String accountIdentifier, @NonNull byte[] nonce) {

        super(serializer, requestId);

        this.backupId = backupId;
        this.accountIdentifier = accountIdentifier;
        this.nonce = nonce;
    }

    @NonNull
    public String getClientFirstMessageBare() {
        return backupId + accountIdentifier + Utils.encodeBase64(nonce);
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);
        stringBuilder.append(" backupId=");
        stringBuilder.append(backupId);
        stringBuilder.append(" accountIdentifier=");
        stringBuilder.append(accountIdentifier);
        stringBuilder.append(" nonce=");
        stringBuilder.append(Arrays.toString(nonce));
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("StartRestoreChallengeIQ\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();

    }
}
