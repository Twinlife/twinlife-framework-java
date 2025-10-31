/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * OnPush IQ.
 * <p>
 * Schema version N
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"<XXXXX>",
 *  "schemaVersion":"N",
 *
 *  "type":"record",
 *  "name":"OnPushIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"deviceState", "type":"byte"},
 *     {"name":"receivedTimestamp", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */
class OnPushIQ extends BinaryPacketIQ {

    final int deviceState;
    final long receivedTimestamp;

    OnPushIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, int deviceState, long receivedTimestamp) {

        super(serializer, requestId);

        this.deviceState = deviceState;
        this.receivedTimestamp = receivedTimestamp;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnPushIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" deviceState=");
            stringBuilder.append(deviceState);
            stringBuilder.append(" receivedTimestamp=");
            stringBuilder.append(receivedTimestamp);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnPushIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class OnPushIQSerializer extends BinaryPacketIQSerializer {

        OnPushIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnPushIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnPushIQ onPushIQ = (OnPushIQ) object;

            encoder.writeInt(onPushIQ.deviceState);
            encoder.writeLong(onPushIQ.receivedTimestamp);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final int deviceState = decoder.readInt();
            final long receivedTimestamp = decoder.readLong();

            return new OnPushIQ(this, requestId, deviceState, receivedTimestamp);
        }
    }
}
