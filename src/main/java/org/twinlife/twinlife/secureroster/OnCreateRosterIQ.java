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
 * On create a secure roster response IQ.
 *
 * Schema version 1
 *  Date: 2026/03/25
 * <pre>
 * {
 *  "schemaId":"796995be-2ec5-44c4-b016-7a277e3e9bd3",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnCreateSecureRosterIQ",
 *  "namespace":"org.twinlife.schemas.secureroster",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"rosterId", "type":"uuid"},
 *     {"name":"maxMemberCount", "type":"int"},
 *  ]
 * }
 * </pre>
 */
class OnCreateRosterIQ extends BinaryPacketIQ {

    private static class OnCreateRosterIQSerializer extends BinaryPacketIQSerializer {

        OnCreateRosterIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnCreateRosterIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnCreateRosterIQ onCreateRosterIQ = (OnCreateRosterIQ) object;
            encoder.writeUUID(onCreateRosterIQ.rosterId);
            encoder.writeInt(onCreateRosterIQ.maxMemberCount);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            final UUID rosterId = decoder.readUUID();
            final int maxMemberCount = decoder.readInt();

            return new OnCreateRosterIQ(this, serviceRequestIQ.getRequestId(), rosterId, maxMemberCount);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnCreateRosterIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final UUID rosterId;
    private final int maxMemberCount;

    @NonNull
    public UUID getRosterId() {
        return rosterId;
    }

    public int getMaxMemberCount() {
        return maxMemberCount;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" rosterId=");
            stringBuilder.append(rosterId);
            stringBuilder.append(" maxMemberCount=");
            stringBuilder.append(maxMemberCount);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnCreateRosterIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    OnCreateRosterIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID rosterId, int maxMemberCount) {

        super(serializer, requestId);

        this.rosterId = rosterId;
        this.maxMemberCount = maxMemberCount;
    }
}
