/*
 *  Copyright (c) 2020-2025 twinlife SA.
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
 * Image copy response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"9fe6e706-2442-455b-8c7e-384d371560c1",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnCopyImageIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"imageId", "type":"uuid"}
 *  ]
 * }
 *
 * </pre>
 */
class OnCopyImageIQ extends BinaryPacketIQ {

    static class OnCopyImageIQSerializer extends BinaryPacketIQSerializer {

        OnCopyImageIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnCopyImageIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnCopyImageIQ onCopyImageIQ = (OnCopyImageIQ) object;
            encoder.writeUUID(onCopyImageIQ.imageId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceResultIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID imageId = decoder.readUUID();

            return new OnCopyImageIQ(this, serviceResultIQ, imageId);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnCopyImageIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID imageId;

    OnCopyImageIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, @NonNull UUID imageId) {

        super(serializer, iq);

        this.imageId = imageId;
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnCopyImageIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
