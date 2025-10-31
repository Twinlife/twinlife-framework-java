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
 * Image delete response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"9e2f9bb9-b614-4674-b3a6-0474aefa961f",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnDeleteImageIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *  ]
 * }
 *
 * </pre>
 */
class OnDeleteImageIQ extends BinaryPacketIQ {

    static class OnDeleteImageIQSerializer extends BinaryPacketIQSerializer {

        OnDeleteImageIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnDeleteImageIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceResultIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            return new OnDeleteImageIQ(this, serviceResultIQ);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnDeleteImageIQSerializer(schemaId, schemaVersion);
    }

    OnDeleteImageIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq) {

        super(serializer, iq);

    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnDeleteImageIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
