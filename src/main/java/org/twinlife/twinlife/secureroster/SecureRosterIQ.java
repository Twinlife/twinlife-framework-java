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
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Base IQ for secure roster operation. This IQ is used as is to:
 * - List members of a secure roster (schemaId "8da99a60-25e5-49f6-acc9-93c28c1d3314"),
 * - Delete a secure roster (schemaId "1ddcdf5c-c810-4b04-bbdb-4b9f14d41483")
 *
 * Schema version 1
 *  Date: 2026/03/25
 * <pre>
 * {
 *  "schemaId":"8da99a60-25e5-49f6-acc9-93c28c1d3314",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SecureRosterIQ",
 *  "namespace":"org.twinlife.schemas.secureroster",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"rosterId", "type":"uuid"},
 *  ]
 * }
 * </pre>
 */
class SecureRosterIQ extends BinaryPacketIQ {

    protected static class RosterIQSerializer extends BinaryPacketIQSerializer {

        RosterIQSerializer(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final SecureRosterIQ listRosterIQ = (SecureRosterIQ) object;
            encoder.writeUUID(listRosterIQ.rosterId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            final UUID rosterId = decoder.readUUID();

            return new SecureRosterIQ(this, serviceRequestIQ.getRequestId(), rosterId);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new RosterIQSerializer(schemaId, schemaVersion, SecureRosterIQ.class);
    }

    @NonNull
    final UUID rosterId;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" rosterId=");
            stringBuilder.append(rosterId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("RosterIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    SecureRosterIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                   @NonNull UUID rosterId) {

        super(serializer, requestId);

        this.rosterId = rosterId;
    }
}
