/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
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
 * Device ringing IQ, sent to the caller to indicate that the peer's device has started ringing.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"acd63138-bec7-402d-86d3-b82707d8b40c",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"DeviceRingingIQ",
 *  "namespace":"org.twinlife.schemas.calls",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *  ]
 * }
 *
 * </pre>
 */
class DeviceRingingIQ extends BinaryPacketIQ {

    private static class DeviceRingingIQSerializer extends BinaryPacketIQSerializer {

        DeviceRingingIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, DeviceRingingIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            DeviceRingingIQ deviceRingingIQ = (DeviceRingingIQ) object;
            encoder.writeString(deviceRingingIQ.to);
            encoder.writeUUID(deviceRingingIQ.sessionId);
         }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            String to = decoder.readString();
            UUID sessionId = decoder.readUUID();

            return new DeviceRingingIQ(this, serviceRequestIQ.getRequestId(), to, sessionId);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new DeviceRingingIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final String to;
    @NonNull
    final UUID sessionId;

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("DeviceRingingIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {
        super.appendTo(stringBuilder);
        stringBuilder.append(" to=");
        stringBuilder.append(to);
    }

    DeviceRingingIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull String to, @NonNull UUID sessionId) {

        super(serializer, requestId);

        this.to = to;
        this.sessionId = sessionId;
    }
}
