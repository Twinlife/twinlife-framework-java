/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.image;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Image copy IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"6c2a932e-3dc6-47f2-b253-6975818d3a3c",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"CopyImageIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"imageId", "type":"uuid"}
 *  ]
 * }
 *
 * </pre>
 */
class CopyImageIQ extends BinaryPacketIQ {

    private static class CopyImageIQSerializer extends BinaryPacketIQSerializer {

        CopyImageIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, CopyImageIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            CopyImageIQ copyImageIQ = (CopyImageIQ) object;

            encoder.writeUUID(copyImageIQ.imageId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID imageId = decoder.readUUID();

            return new CopyImageIQ(this, serviceRequestIQ.getRequestId(), imageId);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new CopyImageIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID imageId;

    CopyImageIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID imageId) {

        super(serializer, requestId);

        this.imageId = imageId;
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("CopyImageIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
