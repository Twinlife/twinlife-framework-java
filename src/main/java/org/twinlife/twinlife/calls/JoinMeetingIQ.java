/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.calls;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import androidx.annotation.NonNull;
import java.util.UUID;

/**
 * Join the meeting request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"02166307-8400-4521-bec1-1be77d6233e7",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"JoinMeetingIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"meetingTwincodeId", "type":"uuid"},
 *     {"name":"memberTwincodeId", "type":"uuid"},
 *     {"name":"maxDelay", "type":"int"},
 * }
 * </pre>
 */
class JoinMeetingIQ extends BinaryPacketIQ {

    private static class JoinMeetingIQSerializer_1 extends BinaryPacketIQSerializer {

        JoinMeetingIQSerializer_1(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, JoinMeetingIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final JoinMeetingIQ joinMeetingIQ = (JoinMeetingIQ) object;
            encoder.writeUUID(joinMeetingIQ.meetingTwincodeId);
            encoder.writeUUID(joinMeetingIQ.memberTwincodeId);
            encoder.writeInt(joinMeetingIQ.maxWaitTime);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID meetingTwincodeId = decoder.readUUID();
            UUID memberTwincodeId = decoder.readUUID();
            int maxWaitTime = decoder.readInt();

            return new JoinMeetingIQ(this, serviceRequestIQ.getRequestId(), meetingTwincodeId, memberTwincodeId, maxWaitTime);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer_1(@NonNull UUID schemaId, int schemaVersion) {

        return new JoinMeetingIQSerializer_1(schemaId, schemaVersion);
    }

    @NonNull
    final UUID meetingTwincodeId;
    @NonNull
    final UUID memberTwincodeId;
    final int maxWaitTime;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" meetingTwincodeId=");
        stringBuilder.append(meetingTwincodeId);
        stringBuilder.append(" memberTwincodeId=");
        stringBuilder.append(memberTwincodeId);
        stringBuilder.append(" maxWaitTime=");
        stringBuilder.append(maxWaitTime);
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("JoinMeetingIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    JoinMeetingIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                  @NonNull UUID meetingTwincodeId, @NonNull UUID memberTwincodeId, int maxWaitTime) {
        super(serializer, requestId);

        this.meetingTwincodeId = meetingTwincodeId;
        this.memberTwincodeId = memberTwincodeId;
        this.maxWaitTime = maxWaitTime;
    }
}
