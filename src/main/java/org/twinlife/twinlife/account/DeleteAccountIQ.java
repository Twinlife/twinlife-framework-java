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
 * Delete account Request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"60e72a89-c1ef-49fa-86a8-0793e5e662e4",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"DeleteAccountIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"accountIdentifier", "type":"string"},
 *     {"name":"accountPassword", "type":"string"}
 *  ]
 * }
 *
 * </pre>
 */
class DeleteAccountIQ extends BinaryPacketIQ {

    private static class DeleteAccountIQSerializer extends BinaryPacketIQSerializer {

        DeleteAccountIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, DeleteAccountIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            DeleteAccountIQ createAccountIQ = (DeleteAccountIQ) object;

            encoder.writeString(createAccountIQ.accountIdentifier);
            encoder.writeString(createAccountIQ.accountPassword);
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

        return new DeleteAccountIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final String accountIdentifier;
    @NonNull
    final String accountPassword;

    DeleteAccountIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull String accountIdentifier, @NonNull String accountPassword) {

        super(serializer, requestId);

        this.accountIdentifier = accountIdentifier;
        this.accountPassword = accountPassword;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" accountIdentifier=");
            stringBuilder.append(accountIdentifier);
            stringBuilder.append(" accountPassword=");
            stringBuilder.append(accountPassword);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("DeleteAccountIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
