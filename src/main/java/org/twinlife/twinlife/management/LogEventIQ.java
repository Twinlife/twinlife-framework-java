/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.management;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Log event request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"a2065d6f-a7aa-43cd-9c0e-030ece70d234",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"LogEventIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"eventCount", "type":"int"},[
 *       {"name":"eventId", "type":"string"},
 *       {"name":"eventTimestamp", "type":"long"},
 *       {"name":"eventAttrCount", "type":"int"},[
 *         {"name":"eventAttrName", "type":"string"},
 *         {"name":"eventAttrValue", "type":"string"}
 *       ]
 *     ]
 *  ]
 * }
 *
 * </pre>
 */
class LogEventIQ extends BinaryPacketIQ {

    private static class LogEventIQSerializer extends BinaryPacketIQSerializer {

        LogEventIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, LogEventIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            LogEventIQ logEventIQ = (LogEventIQ) object;

            encoder.writeInt(logEventIQ.eventList.size());
            for (ManagementServiceImpl.Event event : logEventIQ.eventList) {
                encoder.writeString(event.eventId);
                encoder.writeLong(event.timestamp);
                if (event.key != null && event.value != null) {
                    encoder.writeInt(1);
                    encoder.writeString(event.key);
                    encoder.writeString(event.value);
                } else if (event.attributes != null) {
                    encoder.writeInt(event.attributes.size());
                    for (Map.Entry<String, String> item : event.attributes.entrySet()) {
                        encoder.writeString(item.getKey());
                        encoder.writeString(item.getValue());
                    }
                } else {
                    encoder.writeInt(0);
                }
            }
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

        return new LogEventIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final List<ManagementServiceImpl.Event> eventList;

    LogEventIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull List<ManagementServiceImpl.Event> eventList) {

        super(serializer, requestId);

        this.eventList = eventList;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" eventList=");
            stringBuilder.append(eventList);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("LogEventIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
