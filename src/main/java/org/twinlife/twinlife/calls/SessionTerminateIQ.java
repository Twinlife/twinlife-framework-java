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
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Session Terminate request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"342d4d82-d91f-437b-bcf2-a2051bd94ac1",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SessionTerminateIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"to", "type":"string"},
 *     {"name":"sessionId", "type":"uuid"},
 *     {"name":"reason", "type":"enum"}
 *  ]
 * }
 *
 * </pre>
 */
class SessionTerminateIQ extends BinaryPacketIQ {

    private static class SessionTerminateIQSerializer extends BinaryPacketIQSerializer {

        SessionTerminateIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SessionTerminateIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            SessionTerminateIQ sessionTerminateIQ = (SessionTerminateIQ) object;
            encoder.writeString(sessionTerminateIQ.to);
            encoder.writeUUID(sessionTerminateIQ.sessionId);
            switch (sessionTerminateIQ.reason) {
                case SUCCESS:
                    encoder.writeEnum(0);
                    break;

                case BUSY:
                    encoder.writeEnum(1);
                    break;

                case CANCEL:
                    encoder.writeEnum(2);
                    break;

                case CONNECTIVITY_ERROR:
                    encoder.writeEnum(3);
                    break;

                case DECLINE:
                    encoder.writeEnum(4);
                    break;

                case DISCONNECTED:
                    encoder.writeEnum(5);
                    break;

                case GENERAL_ERROR:
                    encoder.writeEnum(6);
                    break;

                case GONE:
                    encoder.writeEnum(7);
                    break;

                case NOT_AUTHORIZED:
                    encoder.writeEnum(8);
                    break;

                case REVOKED:
                    encoder.writeEnum(9);
                    break;

                case TIMEOUT:
                    encoder.writeEnum(10);
                    break;

                case TRANSFER_DONE:
                    encoder.writeEnum(12);
                    break;

                case SCHEDULE:
                    encoder.writeEnum(13);
                    break;

                case MERGE:
                    encoder.writeEnum(14);
                    break;

                case NO_PRIVATE_KEY:
                    encoder.writeEnum(15);
                    break;

                case NO_SECRET_KEY:
                    encoder.writeEnum(16);
                    break;

                case DECRYPT_ERROR:
                    encoder.writeEnum(17);
                    break;

                case ENCRYPT_ERROR:
                    encoder.writeEnum(18);
                    break;

                case NO_PUBLIC_KEY:
                    encoder.writeEnum(19);
                    break;

                case NOT_ENCRYPTED:
                    encoder.writeEnum(20);
                    break;

                case UNKNOWN:
                default:
                    encoder.writeEnum(11);
                    break;
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            String to = decoder.readString();
            UUID sessionId = decoder.readUUID();
            TerminateReason reason;
            switch (decoder.readEnum()) {
                case 0:
                    reason = TerminateReason.SUCCESS;
                    break;

                case 1:
                    reason = TerminateReason.BUSY;
                    break;

                case 2:
                    reason = TerminateReason.CANCEL;
                    break;

                case 3:
                    reason = TerminateReason.CONNECTIVITY_ERROR;
                    break;

                case 4:
                    reason = TerminateReason.DECLINE;
                    break;

                case 5:
                    reason = TerminateReason.DISCONNECTED;
                    break;

                case 6:
                    reason = TerminateReason.GENERAL_ERROR;
                    break;

                case 7:
                    reason = TerminateReason.GONE;
                    break;

                case 8:
                    reason = TerminateReason.NOT_AUTHORIZED;
                    break;

                case 9:
                    reason = TerminateReason.REVOKED;
                    break;

                case 10:
                    reason = TerminateReason.TIMEOUT;
                    break;

                case 12:
                    reason = TerminateReason.TRANSFER_DONE;
                    break;

                case 13:
                    reason = TerminateReason.SCHEDULE;
                    break;

                case 14:
                    reason = TerminateReason.MERGE;
                    break;

                case 15:
                    reason = TerminateReason.NO_PRIVATE_KEY;
                    break;

                case 16:
                    reason = TerminateReason.NO_SECRET_KEY;
                    break;

                case 17:
                    reason = TerminateReason.DECRYPT_ERROR;
                    break;

                case 18:
                    reason = TerminateReason.ENCRYPT_ERROR;
                    break;

                case 19:
                    reason = TerminateReason.NO_PUBLIC_KEY;
                    break;

                case 20:
                    reason = TerminateReason.NOT_ENCRYPTED;
                    break;

                case 11:
                default:
                    reason = TerminateReason.UNKNOWN;
                    break;
            }

            return new SessionTerminateIQ(this, serviceRequestIQ.getRequestId(), to, sessionId, reason);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new SessionTerminateIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    String to;
    @NonNull
    final UUID sessionId;
    @NonNull
    final TerminateReason reason;

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
            stringBuilder.append(" reason=");
            stringBuilder.append(reason);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SessionTerminateIQ[");
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

    SessionTerminateIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                       @NonNull String to, @NonNull UUID sessionId, @NonNull TerminateReason reason) {

        super(serializer, requestId);

        this.to = to;
        this.sessionId = sessionId;
        this.reason = reason;
    }
}
