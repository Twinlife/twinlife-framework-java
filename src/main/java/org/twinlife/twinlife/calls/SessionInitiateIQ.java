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
 * Session Initiate request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"0ac5f97d-0fa1-4e18-bd99-c13297086752",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SessionInitiateIQ",
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
class SessionInitiateIQ extends BinaryPacketIQ {

    // Set of flags representing the offer and offerToReceive
    static final int OFFER_DATA  = 0x01;
    static final int OFFER_AUDIO = 0x02;
    static final int OFFER_VIDEO = 0x04;
    static final int OFFER_VIDEO_BELL = 0x08;
    static final int OFFER_GROUP_CALL = 0x10;
    static final int OFFER_ANSWER = 0x20;          // The SDP is an answer
    static final int OFFER_COMPRESSED = 0x40;      // Indicates the SDP is compressed.
    static final int OFFER_TRANSFER = 0x80;        // The SDP is a session transfer (added in 1.3.0)
    static final int OFFER_ENCRYPT_MASK = 0x0ff00; // The encryption key index.
    static final int OFFER_ENCRYPT_SHIFT = 8;
    static final int OFFER_VOIP  = OFFER_AUDIO | OFFER_VIDEO;

    private static class SessionInitiateIQSerializer extends BinaryPacketIQSerializer {

        SessionInitiateIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SessionInitiateIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            SessionInitiateIQ sessionInitiateIQ = (SessionInitiateIQ) object;
            encoder.writeString(sessionInitiateIQ.from);
            encoder.writeString(sessionInitiateIQ.to);
            encoder.writeUUID(sessionInitiateIQ.sessionId);
            encoder.writeInt(sessionInitiateIQ.majorVersion);
            encoder.writeInt(sessionInitiateIQ.minorVersion);
            encoder.writeInt(sessionInitiateIQ.offer);
            encoder.writeInt(sessionInitiateIQ.offerToReceive);
            encoder.writeInt(sessionInitiateIQ.priority);
            encoder.writeLong(sessionInitiateIQ.expirationDeadline);
            encoder.writeInt(sessionInitiateIQ.frameSize);
            encoder.writeInt(sessionInitiateIQ.frameRate);
            encoder.writeInt(sessionInitiateIQ.estimatedDataSize);
            encoder.writeInt(sessionInitiateIQ.operationCount);
            encoder.writeBytes(sessionInitiateIQ.sdp, 0, sessionInitiateIQ.sdpLength);
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

            return new SessionInitiateIQ(this, serviceRequestIQ.getRequestId(),
                    from, to, sessionId,
                    offer, offerToReceive, priority, expirationDeadline, majorVersion, minorVersion,
                    frameSize, frameRate, estimatedDataSize, operationCount, sdp, sdp.length);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new SessionInitiateIQSerializer(schemaId, schemaVersion);
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
        final boolean compressed = (offer & OFFER_COMPRESSED) != 0;
        final int keyIndex = (offer & OFFER_ENCRYPT_MASK) >> OFFER_ENCRYPT_SHIFT;

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
            stringBuilder.append("SessionInitiateIQ\n");
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

    SessionInitiateIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
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
