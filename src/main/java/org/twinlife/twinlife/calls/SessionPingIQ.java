/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.calls;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Session Ping request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"f2cb4a52-7928-42cb-8439-248388b9a4c7",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SessionPingIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"from", "type":"string"},
 *     {"name":"to", "type":"string"},
 *     {"name":"sessionId", "type":"uuid"}
 *  ]
 * }
 *
 * </pre>
 */
class SessionPingIQ extends BinaryPacketIQ {

    private static class SessionPingIQSerializer extends BinaryPacketIQSerializer {

        SessionPingIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SessionPingIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            SessionPingIQ sessionPingIQ = (SessionPingIQ) object;
            encoder.writeString(sessionPingIQ.from);
            encoder.writeString(sessionPingIQ.to);
            encoder.writeUUID(sessionPingIQ.sessionId);
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

        return new SessionPingIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    String from;
    @NonNull
    String to;
    @NonNull
    final UUID sessionId;

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" from=");
            stringBuilder.append(from);
            stringBuilder.append(" to=");
            stringBuilder.append(to);
            stringBuilder.append(" sessionId=");
            stringBuilder.append(sessionId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SessionPingIQ[");
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

    SessionPingIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                  @NonNull String from, @NonNull String to, @NonNull UUID sessionId) {

        super(serializer, requestId);

        this.from = from;
        this.to = to;
        this.sessionId = sessionId;
    }
}
