/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.Arrays;
import java.util.UUID;

/**
 * Generate backup key Request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"3d6cef13-f703-415c-bb5c-38459d8e32e1",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"GenerateBackupKeyIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"backupId", "type":"uuid"},
 *     {"name":"derivedUserKey", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class GenerateBackupKeyIQ extends BinaryPacketIQ {

    private static class GenerateBackupKeyIQSerializer extends BinaryPacketIQSerializer {

        GenerateBackupKeyIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, GenerateBackupKeyIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            GenerateBackupKeyIQ generateBackupKeyIQ = (GenerateBackupKeyIQ) object;

            encoder.writeUUID(generateBackupKeyIQ.backupId);
            encoder.writeData(generateBackupKeyIQ.derivedUserKey);
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
    final byte[] derivedUserKey;

    GenerateBackupKeyIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                        @NonNull UUID backupId, @NonNull byte[] derivedUserKey) {

        super(serializer, requestId);

        this.backupId = backupId;
        this.derivedUserKey = derivedUserKey;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" backupId=");
            stringBuilder.append(backupId);
            stringBuilder.append(" derivedUserKey=");
            stringBuilder.append(Arrays.toString(derivedUserKey));
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("GenerateBackupKeyIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
