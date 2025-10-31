/*
 *  Copyright (c) 2022-2024 twinlife SA.
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
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Session Update request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"44f0c7d0-8d03-453d-8587-714ef92087ae",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SessionUpdateIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"to", "type":"string"},
 *     {"name":"sessionId", "type":"uuid"},
 *     {"name":"expirationDeadline", "type":"long"},
 *     {"name":"update", "type":"int"},
 *     {"name":"sdp", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class SessionUpdateIQ extends BinaryPacketIQ {

    private static class SessionUpdateIQSerializer extends BinaryPacketIQSerializer {

        SessionUpdateIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SessionUpdateIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            SessionUpdateIQ sessionUpdateIQ = (SessionUpdateIQ) object;
            encoder.writeString(sessionUpdateIQ.to);
            encoder.writeUUID(sessionUpdateIQ.sessionId);
            encoder.writeLong(sessionUpdateIQ.expirationDeadline);
            encoder.writeEnum(sessionUpdateIQ.updateType);
            encoder.writeBytes(sessionUpdateIQ.sdp, 0, sessionUpdateIQ.sdpLength);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            String to = decoder.readString();
            UUID sessionId = decoder.readUUID();
            long expirationDeadline = decoder.readLong();
            int updateType = decoder.readInt();
            byte[] sdp = decoder.readBytes(null).array();

            return new SessionUpdateIQ(this, serviceRequestIQ.getRequestId(), to, sessionId,
                    expirationDeadline, updateType, sdp, sdp.length);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new SessionUpdateIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    String to;
    @NonNull
    final UUID sessionId;
    long expirationDeadline;
    final int updateType;
    @NonNull
    final byte[] sdp;
    final int sdpLength;

    /**
     * Get the SDP as the Sdp instance.
     *
     * @return the sdp instance.
     */
    @NonNull
    public Sdp getSdp() {
        final boolean compressed = (updateType & SessionInitiateIQ.OFFER_COMPRESSED) != 0;
        final int keyIndex = (updateType & SessionInitiateIQ.OFFER_ENCRYPT_MASK) >> SessionInitiateIQ.OFFER_ENCRYPT_SHIFT;

        return new Sdp(sdp, sdpLength, compressed, keyIndex);
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

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
            stringBuilder.append("SessionUpdateIQ[");
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

    SessionUpdateIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull String to, @NonNull UUID sessionId, long expirationDeadline,
                    int updateType, @NonNull byte[] sdp, int sdpLength) {

        super(serializer, requestId);

        this.to = to;
        this.sessionId = sessionId;
        this.expirationDeadline = expirationDeadline;
        this.updateType = updateType;
        this.sdp = sdp;
        this.sdpLength = sdpLength;
    }
}
