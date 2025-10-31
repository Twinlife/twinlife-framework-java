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
 * Session Accept request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"fd545960-d9ac-4e3e-bddf-76f381f163a5",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SessionAcceptIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"from", "type":"string"},
 *     {"name":"to", "type":"string"},
 *     {"name":"sessionId", "type":"uuid"},
 *     {"name":"majorVersion", "type":"int"},
 *     {"name":"minorVersion", "type":"int"},
 *     {"name":"offer", "type":"int"},
 *     {"name":"offerToReceive", "type":"int"},
 *     {"name":"priority", "type":"int"},
 *     {"name":"expirationDeadline", "type":"long"},
 *     {"name":"frameSize", "type":"int"},
 *     {"name":"frameRate", "type":"int"},
 *     {"name":"estimatedDataSize", "type":"int"},
 *     {"name":"operationCount", "type":"int"},
 *     {"name":"sdp", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class SessionAcceptIQ extends BinaryPacketIQ {

    private static class SessionAcceptIQSerializer extends BinaryPacketIQSerializer {

        SessionAcceptIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SessionAcceptIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            SessionAcceptIQ sessionAcceptIQ = (SessionAcceptIQ) object;
            encoder.writeString(sessionAcceptIQ.from);
            encoder.writeString(sessionAcceptIQ.to);
            encoder.writeUUID(sessionAcceptIQ.sessionId);
            encoder.writeInt(sessionAcceptIQ.majorVersion);
            encoder.writeInt(sessionAcceptIQ.minorVersion);
            encoder.writeInt(sessionAcceptIQ.offer);
            encoder.writeInt(sessionAcceptIQ.offerToReceive);
            encoder.writeInt(sessionAcceptIQ.priority);
            encoder.writeLong(sessionAcceptIQ.expirationDeadline);
            encoder.writeInt(sessionAcceptIQ.frameSize);
            encoder.writeInt(sessionAcceptIQ.frameRate);
            encoder.writeInt(sessionAcceptIQ.estimatedDataSize);
            encoder.writeInt(sessionAcceptIQ.operationCount);
            encoder.writeBytes(sessionAcceptIQ.sdp, 0, sessionAcceptIQ.sdpLength);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            String from = decoder.readString();
            String to = decoder.readString();
            UUID sessionId = decoder.readUUID();
            int majorVersion = decoder.readInt();
            int minorVersion = decoder.readInt();
            int offer = decoder.readInt();
            int offerToReceive = decoder.readInt();
            int priority = decoder.readInt();
            long expirationDeadline = decoder.readLong();
            int frameSize = decoder.readInt();
            int frameRate = decoder.readInt();
            int estimatedDataSize = decoder.readInt();
            int operationCount = decoder.readInt();
            byte[] sdp = decoder.readBytes(null).array();

            return new SessionAcceptIQ(this, serviceRequestIQ.getRequestId(), from, to, sessionId,
                    offer, offerToReceive, priority, expirationDeadline, majorVersion, minorVersion,
                    frameSize, frameRate, estimatedDataSize, operationCount, sdp, sdp.length);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new SessionAcceptIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    String from;
    @NonNull
    String to;
    @NonNull
    final UUID sessionId;
    final int offer;
    final int offerToReceive;
    final int priority;
    long expirationDeadline;
    final int majorVersion;
    final int minorVersion;
    final int frameSize;
    final int frameRate;
    final int estimatedDataSize;
    final int operationCount;
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
        final boolean compressed = (offer & SessionInitiateIQ.OFFER_COMPRESSED) != 0;
        final int keyIndex = (offer & SessionInitiateIQ.OFFER_ENCRYPT_MASK) >> SessionInitiateIQ.OFFER_ENCRYPT_SHIFT;

        return new Sdp(sdp, sdpLength, compressed, keyIndex);
    }

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
            stringBuilder.append("SessionAcceptIQ[");
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

    SessionAcceptIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull String from, @NonNull String to, @NonNull UUID sessionId,
                    int offer, int offerToReceive, int priority, long expirationDeadline,
                    int majorVersion, int minorVersion, int frameSize, int frameRate, int estimatedDataSize,
                    int operationCount, @NonNull byte[] sdp, int sdpLength) {

        super(serializer, requestId);

        this.from = from;
        this.to = to;
        this.sessionId = sessionId;
        this.offer = offer;
        this.offerToReceive = offerToReceive;
        this.priority = priority;
        this.expirationDeadline = expirationDeadline;
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
        this.frameSize = frameSize;
        this.frameRate = frameRate;
        this.estimatedDataSize = estimatedDataSize;
        this.operationCount = operationCount;
        this.sdp = sdp;
        this.sdpLength = sdpLength;
    }
}
