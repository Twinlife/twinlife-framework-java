/*
 *  Copyright (c) 2023-2024 twinlife SA.
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
 * Change account password request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"f7295462-019e-4bd5-b830-20f98f8a9735",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ChangePasswordIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"accountIdentifier", "type":"string"},
 *     {"name":"oldAccountPassword", "type":"string"},
 *     {"name":"newAccountPassword", "type":"string"}
 *  ]
 * }
 *
 * </pre>
 */
class ChangePasswordIQ extends BinaryPacketIQ {

    private static class ChangePasswordIQSerializer extends BinaryPacketIQSerializer {

        ChangePasswordIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, ChangePasswordIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {
            super.serialize(serializerFactory, encoder, object);

            ChangePasswordIQ changePasswordIQ = (ChangePasswordIQ) object;

            encoder.writeString(changePasswordIQ.accountIdentifier);
            encoder.writeString(changePasswordIQ.oldAccountPassword);
            encoder.writeString(changePasswordIQ.newAccountPassword);
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

        return new ChangePasswordIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final String accountIdentifier;
    @NonNull
    final String oldAccountPassword;
    @NonNull
    final String newAccountPassword;

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" accountIdentifier=");
            stringBuilder.append(accountIdentifier);
            stringBuilder.append(" oldAccountPassword=");
            stringBuilder.append(oldAccountPassword);
            stringBuilder.append(" newAccountPassword=");
            stringBuilder.append(newAccountPassword);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("ChangePasswordIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");
            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    //
    // Private Methods
    //

    ChangePasswordIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                     @NonNull String accountIdentifier, @NonNull String oldAccountPassword,
                     @NonNull String newAccountPassword) {

        super(serializer, requestId);

        this.accountIdentifier = accountIdentifier;
        this.oldAccountPassword = oldAccountPassword;
        this.newAccountPassword = newAccountPassword;
    }
}
