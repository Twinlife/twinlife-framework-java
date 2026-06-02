/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.secureroster;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

/**
 * Add a public key to a secure roster request IQ.
 *
 * Schema version 1
 *  Date: 2026/03/26
 * <pre>
 * {
 *  "schemaId":"a0c5183a-062e-412d-b31b-1a6c79b4c6f6",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AddSecureRosterPublicKeyIQ",
 *  "namespace":"org.twinlife.schemas.secureroster",
 *  "super":"SecureRosterIQ"
 *  "fields": [
 *     {"name":"signingKeyId", "type":"uuid"},
 *     {"name":"newKeyId", "type":"uuid"},
 *     {"name":"newPublicKey", "type":"bytes"}
 *     {"name":"signature", "type":"bytes"}
 *  ]
 * }
 * </pre>
 */
class AddRosterPublicKeyIQ extends SecureRosterIQ {

    private static class AddRosterPublicKeyIQSerializer extends RosterIQSerializer {

        AddRosterPublicKeyIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, AddRosterPublicKeyIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final AddRosterPublicKeyIQ addRosterPublicKeyIQ = (AddRosterPublicKeyIQ) object;
            encoder.writeUUID(addRosterPublicKeyIQ.signingKeyId);
            encoder.writeUUID(addRosterPublicKeyIQ.newKeyId);
            encoder.writeBytes(addRosterPublicKeyIQ.newPublicKey, 0, addRosterPublicKeyIQ.newPublicKey.length);
            encoder.writeBytes(addRosterPublicKeyIQ.signature, 0, addRosterPublicKeyIQ.signature.length);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final SecureRosterIQ secureRosterIQ = (SecureRosterIQ) super.deserialize(serializerFactory, decoder);

            final UUID signerKeyId = decoder.readUUID();
            final UUID newKeyId = decoder.readUUID();
            final byte[] newPublicKey = decoder.readBytes(null).array();
            final byte[] signature = decoder.readBytes(null).array();

            return new AddRosterPublicKeyIQ(this, secureRosterIQ.getRequestId(), secureRosterIQ.rosterId,
                    signerKeyId, newKeyId, newPublicKey, signature);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new AddRosterPublicKeyIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID signingKeyId;
    @NonNull
    final UUID newKeyId;
    @NonNull
    final byte[] newPublicKey;
    @NonNull
    final byte[] signature;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" rosterId=");
            stringBuilder.append(rosterId);
            stringBuilder.append(" newKeyId=");
            stringBuilder.append(newKeyId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("AddRosterPublicKeyIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    AddRosterPublicKeyIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                         @NonNull UUID rosterId, @NonNull UUID signingKeyId, @NonNull UUID newKeyId,
                         @NonNull byte[] newPublicKey, @NonNull byte[] signature) {

        super(serializer, requestId, rosterId);

        this.signingKeyId = signingKeyId;
        this.newKeyId = newKeyId;
        this.newPublicKey = newPublicKey;
        this.signature = signature;
    }
}
