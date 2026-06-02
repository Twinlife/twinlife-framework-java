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
 * List members of a secure roster for members created after a given timestamp.
 *
 * Schema version 1
 *  Date: 2026/03/25
 * <pre>
 * {
 *  "schemaId":"8da99a60-25e5-49f6-acc9-93c28c1d3314",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ListRosterIQ",
 *  "namespace":"org.twinlife.schemas.secureroster",
 *  "super":"org.twinlife.schemas.SecureRosterIQ"
 *  "fields": {
 *     {"name":"minimumCreationTime", "type":"long"},
 *  }
 * }
 * </pre>
 */
class ListRosterIQ extends SecureRosterIQ {

    protected static class ListRosterIQSerializer extends SecureRosterIQ.RosterIQSerializer {

        ListRosterIQSerializer(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final ListRosterIQ listRosterIQ = (ListRosterIQ) object;
            encoder.writeLong(listRosterIQ.minimumCreationTime);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final SecureRosterIQ secureRosterIQ = (SecureRosterIQ) super.deserialize(serializerFactory, decoder);

            final long minimumCreationTime = decoder.readLong();

            return new ListRosterIQ(this, secureRosterIQ.getRequestId(), secureRosterIQ.rosterId, minimumCreationTime);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new ListRosterIQSerializer(schemaId, schemaVersion, ListRosterIQ.class);
    }

    final long minimumCreationTime;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" minimumCreationTime=");
            stringBuilder.append(minimumCreationTime);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("ListRosterIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    ListRosterIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                   @NonNull UUID rosterId, long minimumCreationTime) {

        super(serializer, requestId, rosterId);

        this.minimumCreationTime = minimumCreationTime;
    }
}
