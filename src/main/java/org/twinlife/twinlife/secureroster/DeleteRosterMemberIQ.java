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
 * Delete a member from a secure roster request IQ.
 *
 * Schema version 1
 *  Date: 2026/03/26
 * <pre>
 * {
 *  "schemaId":"e10bbf8e-cab3-4817-9e22-2a8664135ab8",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"DeleteSecureRosterMemberIQ",
 *  "namespace":"org.twinlife.schemas.secureroster",
 *  "super":"SecureRosterIQ"
 *  "fields": [
 *     {"name":"memberId", "type":"uuid"},
 *  ]
 * }
 * </pre>
 */
class DeleteRosterMemberIQ extends SecureRosterIQ {

    private static class DeleteRosterMemberIQSerializer extends RosterIQSerializer {

        DeleteRosterMemberIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, DeleteRosterMemberIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final DeleteRosterMemberIQ deleteRosterMemberIQ = (DeleteRosterMemberIQ) object;
            encoder.writeUUID(deleteRosterMemberIQ.memberId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final SecureRosterIQ secureRosterIQ = (SecureRosterIQ) super.deserialize(serializerFactory, decoder);

            final UUID memberId = decoder.readUUID();

            return new DeleteRosterMemberIQ(this, secureRosterIQ.getRequestId(), secureRosterIQ.rosterId, memberId);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new DeleteRosterMemberIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID memberId;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" memberId=");
            stringBuilder.append(memberId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("DeleteRosterMemberIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    DeleteRosterMemberIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                         @NonNull UUID rosterId, @NonNull UUID memberId) {

        super(serializer, requestId, rosterId);

        this.memberId = memberId;
    }
}
