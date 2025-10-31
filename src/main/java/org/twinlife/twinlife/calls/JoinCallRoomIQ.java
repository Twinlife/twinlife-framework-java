/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.calls;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Join the call room request IQ.
 *
 * Schema version 3
 * <pre>
 * {
 *  "schemaId":"f34ce0b8-8b1c-4384-b7a3-19fddcfd2789",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"JoinCallRoomIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"callRoomId", "type":"uuid"},
 *     {"name":"twincodeId", "type":"uuid"},
 *     {"name":"p2pSessionCount", "type":"int"},
 *     {"name":"p2pSessionIds", [
 *         {"name":"sessionId", "type":"uuid"},
 *         {"name":"peerId", "type":["null", "string"]}
 *     ]
 * }
 * </pre>
 */
class JoinCallRoomIQ extends BinaryPacketIQ {

    private static class JoinCallRoomIQSerializer extends BinaryPacketIQSerializer {

        JoinCallRoomIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, JoinCallRoomIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final JoinCallRoomIQ joinCallRoomIQ = (JoinCallRoomIQ) object;
            encoder.writeUUID(joinCallRoomIQ.callRoomId);
            encoder.writeUUID(joinCallRoomIQ.twincodeId);

            encoder.writeInt(joinCallRoomIQ.p2pSessionIds.size());
            for (Pair<UUID, String> sessionId : joinCallRoomIQ.p2pSessionIds) {
                encoder.writeUUID(sessionId.first);
                encoder.writeOptionalString(sessionId.second);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            final UUID callRoomId = decoder.readUUID();
            final UUID twincodeId = decoder.readUUID();

            final List<Pair<UUID, String>> p2pSessionIds = new ArrayList<>();
            final int nbSessions = decoder.readInt();
            for (int i = 0; i < nbSessions; i++) {
                final UUID sessionId = decoder.readUUID();
                final String peerId = decoder.readOptionalString();
                p2pSessionIds.add(new Pair<>(sessionId, peerId));
            }

            return new JoinCallRoomIQ(this, serviceRequestIQ.getRequestId(), callRoomId, twincodeId, p2pSessionIds);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new JoinCallRoomIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID callRoomId;
    @NonNull
    final UUID twincodeId;
    @NonNull
    final List<Pair<UUID, String>> p2pSessionIds = new ArrayList<>();

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" callRoomId=");
            stringBuilder.append(callRoomId);
            stringBuilder.append(" twincodeId=");
            stringBuilder.append(twincodeId);
            stringBuilder.append(" p2pSessionId=");
            stringBuilder.append(p2pSessionIds);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("JoinCallRoomIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    JoinCallRoomIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                   @NonNull UUID callRoomId, @NonNull UUID twincodeId, @Nullable List<Pair<UUID, String>> p2pSessionIds) {

        super(serializer, requestId);

        this.callRoomId = callRoomId;
        this.twincodeId = twincodeId;
        if (p2pSessionIds != null) {
            this.p2pSessionIds.addAll(p2pSessionIds);
        }
    }
}
