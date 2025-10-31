/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.calls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Transport Info request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"fdf1bba1-0c16-4b12-a59c-0f70cf4da1d9",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"TransportInfoIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"to", "type":"string"},
 *     {"name":"sessionId", "type":"uuid"},
 *     {"name":"expirationDeadline", "type":"long"},
 *     {"name":"mode", "type":"int"},
 *     {"name":"sdp", "type":"bytes"}
 *     [
 *       {"name":"mode", "type":"int"},
 *       {"name":"sdp", "type":"bytes"}
 *       ...
 *     ]
 *  ]
 * }
 *
 * </pre>
 */
class TransportInfoIQ extends BinaryPacketIQ {

    private static final int HAS_NEXT_MARKER = 0x10000;

    private static class TransportInfoIQSerializer extends BinaryPacketIQSerializer {

        TransportInfoIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, TransportInfoIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            TransportInfoIQ transportInfoIQ = (TransportInfoIQ) object;
            encoder.writeString(transportInfoIQ.to);
            encoder.writeUUID(transportInfoIQ.sessionId);
            encoder.writeLong(transportInfoIQ.expirationDeadline);
            encoder.writeInt(transportInfoIQ.mode);
            encoder.writeBytes(transportInfoIQ.sdp, 0, transportInfoIQ.sdpLength);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            String to = decoder.readString();
            UUID sessionId = decoder.readUUID();
            long expirationDeadline = decoder.readLong();

            // The server can somehow concatenate several transport-info together.
            // When the transport-info is not encrypted, they are merged and re-compressed within the server.
            // Otherwise, the server cannot decrypt and we append them to the TransportInfoIQ packet with a marker.
            // This marker is cleared by the server in case a client sent it!
            TransportInfoIQ nextTransportInfoIQ = null;
            int mode;
            byte[] sdp;
            while (true) {
                mode = decoder.readInt();
                sdp = decoder.readBytes(null).array();
                if ((mode & HAS_NEXT_MARKER) == 0) {
                    break;
                }
                mode &= ~HAS_NEXT_MARKER;

                nextTransportInfoIQ = new TransportInfoIQ(this, serviceRequestIQ.getRequestId(), to,
                        sessionId, expirationDeadline, mode, sdp, sdp.length, nextTransportInfoIQ);
            }

            return new TransportInfoIQ(this, serviceRequestIQ.getRequestId(), to, sessionId, expirationDeadline,
                    mode, sdp, sdp.length, nextTransportInfoIQ);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new TransportInfoIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    String to;
    @NonNull
    final UUID sessionId;
    long expirationDeadline;
    final int mode;
    @NonNull
    final byte[] sdp;
    final int sdpLength;
    @Nullable
    final TransportInfoIQ nextTransportIQ;

    /**
     * Get the SDP as the Sdp instance.
     *
     * @return the sdp instance.
     */
    @NonNull
    public Sdp getSdp() {
        final boolean compressed = (mode & SessionInitiateIQ.OFFER_COMPRESSED) != 0;
        final int keyIndex = (mode & SessionInitiateIQ.OFFER_ENCRYPT_MASK) >> SessionInitiateIQ.OFFER_ENCRYPT_SHIFT;

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
            stringBuilder.append(" mode=");
            stringBuilder.append(mode);
            stringBuilder.append(" sdpLength=");
            stringBuilder.append(sdpLength);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("TransportInfoIQ[");
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

    TransportInfoIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull String to, @NonNull UUID sessionId, long expirationDeadline,
                    int mode, @NonNull byte[] sdp, int sdpLength, @Nullable TransportInfoIQ nextTransportIQ) {

        super(serializer, requestId);

        this.to = to;
        this.sessionId = sessionId;
        this.expirationDeadline = expirationDeadline;
        this.mode = mode;
        this.sdp = sdp;
        this.sdpLength = sdpLength;
        this.nextTransportIQ = nextTransportIQ;
    }
}
