/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Authenticate Request Response IQ.
 *
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"9CEE4256-D2B7-4DE3-A724-1F61BB1454C8",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnAuthRequestIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"serviceSignature", "type":"bytes"},
 *     {"name":"serverTimestamp", "type":"long"},
 *     {"name":"serverLatency", "type":"int"},
 *     {"name":"deviceTimestamp", "type":"long"}
 *  ]
 * }
 * </pre>
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"9CEE4256-D2B7-4DE3-A724-1F61BB1454C8",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnAuthRequestIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"serviceSignature", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class OnAuthRequestIQ extends BinaryPacketIQ {

    private static class OnAuthRequestIQSerializer extends BinaryPacketIQSerializer {

        OnAuthRequestIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnAuthRequestIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {
            throw new SerializerException();
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            byte[] serverSignature = decoder.readBytes(null).array();
            long serverTimestamp = decoder.readLong();
            int serverLatency = decoder.readInt();
            long deviceTimestamp = decoder.readLong();

            return new OnAuthRequestIQ(this, serviceRequestIQ, serverSignature, serverTimestamp, serverLatency, deviceTimestamp);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnAuthRequestIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final byte[] serverSignature;
    final long serverTimestamp;
    final int serverLatency;
    final long deviceTimestamp;

    OnAuthRequestIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, @NonNull byte[] serverSignature,
                    long serverTimestamp, int serverLatency, long deviceTimestamp) {

        super(serializer, iq);

        this.serverSignature = serverSignature;
        this.serverTimestamp = serverTimestamp;
        this.serverLatency = serverLatency;
        this.deviceTimestamp = deviceTimestamp;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" serverSignature=");
            stringBuilder.append(serverSignature.length);
            stringBuilder.append(" serverTimestamp=");
            stringBuilder.append(serverTimestamp);
            stringBuilder.append(" serverLatency=");
            stringBuilder.append(serverLatency);
            stringBuilder.append(" deviceTimestamp=");
            stringBuilder.append(deviceTimestamp);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnAuthRequestIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
