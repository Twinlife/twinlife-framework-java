/*
 *  Copyright (c) 2025-2026 twinlife SA.
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

import java.util.UUID;

/**
 * Restore Request after the RestoreChallenge request IQ.
 *
 * Schema version 1
 *  Date: 2025/12/04
 *
 * <pre>
 * {
 *  "schemaId":"8576bcf4-5901-4e54-b5d5-7e3b70622a7f",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"RestoreRequestIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"accountIdentifier", "type":"string"},
 *     {"name":"resourceIdentifier", "type":"string"},
 *     {"name":"deviceNonce", "type":"bytes"},
 *     {"name":"deviceProof", "type":"bytes"},
 *     {"name":"backupId", "type":"uuid"}
 *  ]
 * }
 *
 * </pre>
 */
class RestoreRequestIQ extends BinaryPacketIQ {

    private static class RestoreRequestIQSerializer extends BinaryPacketIQSerializer {

        RestoreRequestIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, RestoreRequestIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            RestoreRequestIQ restoreRequestIQ = (RestoreRequestIQ) object;

            encoder.writeString(restoreRequestIQ.accountIdentifier);
            encoder.writeString(restoreRequestIQ.resourceIdentifier);
            encoder.writeData(restoreRequestIQ.deviceNonce);
            encoder.writeData(restoreRequestIQ.deviceProof);
            encoder.writeUUID(restoreRequestIQ.backupId);
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

        return new RestoreRequestIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final String accountIdentifier;
    @NonNull
    final String resourceIdentifier;
    @NonNull
    final byte[] deviceNonce;
    @NonNull
    final byte[] deviceProof;
    @NonNull
    final UUID backupId;

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" accountIdentifier=");
        stringBuilder.append(accountIdentifier);
        stringBuilder.append(" resourceIdentifier=");
        stringBuilder.append(resourceIdentifier);
        stringBuilder.append(" backupId=");
        stringBuilder.append(backupId);
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("RestoreRequestIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    RestoreRequestIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                     @NonNull String accountIdentifier, @NonNull String resourceIdentifier, @NonNull byte[] deviceNone,
                     @NonNull byte[] deviceProof, @NonNull UUID backupId) {

        super(serializer, requestId);

        this.accountIdentifier = accountIdentifier;
        this.resourceIdentifier = resourceIdentifier;
        this.deviceNonce = deviceNone;
        this.deviceProof = deviceProof;
        this.backupId = backupId;
    }
}
