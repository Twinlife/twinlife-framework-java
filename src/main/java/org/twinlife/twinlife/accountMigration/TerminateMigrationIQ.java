/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Terminate migration IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"a35089f8-326f-4f25-b160-e0f9f2c9795c",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"TerminateMigrationIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"commit", "type":"boolean"},
 *     {"name":"done", "type":"boolean"},
 *  ]
 * }
 *
 * </pre>
 */
class TerminateMigrationIQ extends BinaryPacketIQ {

    static class TerminateMigrationIQSerializer extends BinaryPacketIQSerializer {

        TerminateMigrationIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, TerminateMigrationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            TerminateMigrationIQ terminateMigrationIQ = (TerminateMigrationIQ)object;
            encoder.writeBoolean(terminateMigrationIQ.commit);
            encoder.writeBoolean(terminateMigrationIQ.done);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            boolean commit = decoder.readBoolean();
            boolean done = decoder.readBoolean();
            return new TerminateMigrationIQ(this, serviceRequestIQ.getRequestId(), commit, done);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new TerminateMigrationIQSerializer(schemaId, schemaVersion);
    }

    final boolean commit;
    final boolean done;

    TerminateMigrationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, boolean commit, boolean done) {

        super(serializer, requestId);

        this.commit = commit;
        this.done = done;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {
        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" commit=");
            stringBuilder.append(commit);
            stringBuilder.append(" done=");
            stringBuilder.append(done);
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("TerminateMigrationIQ\n");
            appendTo(stringBuilder);
        }
        return stringBuilder.toString();
    }
}
