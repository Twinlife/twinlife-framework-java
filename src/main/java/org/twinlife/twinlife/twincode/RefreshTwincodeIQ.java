/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Refresh twincode IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"e8028e21-e657-4240-b71a-21ea1367ebf2",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"RefreshTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"timestamp", "type":"long"},
 *     {"name":"twincodes", [
 *      {"name":"twincode", "type": "uuid"}
 *     ]}
 *   ]
 * }
 *
 * </pre>
 */
public class RefreshTwincodeIQ extends BinaryPacketIQ {

    static class RefreshTwincodeIQSerializer extends BinaryPacketIQSerializer {

        RefreshTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, RefreshTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            RefreshTwincodeIQ refreshTwincodeIQ = (RefreshTwincodeIQ) object;

            encoder.writeLong(refreshTwincodeIQ.timestamp);
            encoder.writeInt(refreshTwincodeIQ.twincodeList.size());
            for (UUID twincodeId : refreshTwincodeIQ.twincodeList) {
                encoder.writeUUID(twincodeId);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new RefreshTwincodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final Serializer serializer;
    @NonNull
    private final Set<UUID> twincodeList;
    final long timestamp;

    public long getTimestamp() {

        return timestamp;
    }

    public RefreshTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                             @NonNull Map<UUID, Long> twincodeList, long timestamp) {

        super(serializer, requestId);

        this.serializer = serializer;
        this.twincodeList = twincodeList.keySet();
        this.timestamp = timestamp;
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("RefreshTwincodeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
