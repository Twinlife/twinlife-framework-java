/*
 *  Copyright (c) 2025 twinlife SA.
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

import java.util.UUID;

/**
 * Terminate account restore Request IQ.
 *
 * Schema version 1
 *  Date: 2025/07/01
 *
 * <pre>
 * {
 *  "schemaId":"2810fd0c-3973-41f3-912b-57872d881b2d",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"TerminateAccountRestoreIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"commit", "type":"boolean"}
 *  ]
 * }
 *
 * </pre>
 */

public class TerminateAccountRestoreIQ extends BinaryPacketIQ {

    private static class TerminateAccountRestoreIQSerializer extends BinaryPacketIQSerializer {

        public TerminateAccountRestoreIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, TerminateAccountRestoreIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            TerminateAccountRestoreIQ terminateAccountRestoreIQ = (TerminateAccountRestoreIQ) object;

            encoder.writeBoolean(terminateAccountRestoreIQ.commit);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new TerminateAccountRestoreIQSerializer(schemaId, schemaVersion);
    }

    final boolean commit;

    public TerminateAccountRestoreIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, boolean commit) {

        super(serializer, requestId);

        this.commit = commit;
    }

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" commit=");
            stringBuilder.append(commit);
        }
    }

    @NonNull
    @Override
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("TerminateAccountRestoreIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
