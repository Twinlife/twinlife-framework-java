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
 * Image creation response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"dfb67bd7-2e6a-4fd0-b05d-b34b916ea6cf",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnCreateImageIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"imageId", "type":"uuid"},
 *     {"name":"chunkSize", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */
class OnCreateImageIQ extends BinaryPacketIQ {

    static class OnCreateImageIQSerializer extends BinaryPacketIQSerializer {

        OnCreateImageIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnCreateImageIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnCreateImageIQ onCreateImageIQ = (OnCreateImageIQ) object;
            encoder.writeUUID(onCreateImageIQ.imageId);
            encoder.writeLong(onCreateImageIQ.chunkSize);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceResultIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID imageId = decoder.readUUID();
            long chunkSize = decoder.readLong();

            return new OnCreateImageIQ(this, serviceResultIQ, imageId, chunkSize);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnCreateImageIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID imageId;
    final long chunkSize;

    OnCreateImageIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, @NonNull UUID imageId, long chunkSize) {

        super(serializer, iq);

        this.imageId = imageId;
        this.chunkSize = chunkSize;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" imageId=");
            stringBuilder.append(imageId);
            stringBuilder.append(" chunkSize=");
            stringBuilder.append(chunkSize);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnCreateImageIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
