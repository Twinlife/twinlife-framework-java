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

import java.util.UUID;

/**
 * Get twincode IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"3a9ca7c4-6153-426d-b716-d81fd625293c",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"GetTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeId", "type":"uuid"}
 *  ]
 * }
 *
 * </pre>
 */
public class GetTwincodeIQ extends BinaryPacketIQ {

    static class GetTwincodeIQSerializer extends BinaryPacketIQSerializer {

        GetTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, GetTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            GetTwincodeIQ getTwincodeIQ = (GetTwincodeIQ) object;

            encoder.writeUUID(getTwincodeIQ.twincodeId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID twincodeId = decoder.readUUID();

            return new GetTwincodeIQ(this, serviceRequestIQ.getRequestId(), twincodeId);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new GetTwincodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final Serializer serializer;
    @NonNull
    final UUID twincodeId;

    public GetTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID twincodeId) {

        super(serializer, requestId);

        this.serializer = serializer;
        this.twincodeId = twincodeId;
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("GetTwincodeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
