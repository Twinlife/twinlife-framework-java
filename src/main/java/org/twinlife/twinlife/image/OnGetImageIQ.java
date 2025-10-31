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
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Image get response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"9ec1280e-a298-4c8b-b0fd-35383f7b5424",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnGetImageIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"totalSize", "type":"long"},
 *     {"name":"offset", "type":"long"},
 *     {"name":"data", "type":"bytes"}
 *     {"name": "imageSha": [null, "type":"bytes"]}
 *  ]
 * }
 *
 * </pre>
 */
class OnGetImageIQ extends BinaryPacketIQ {

    static class OnGetImageIQSerializer extends BinaryPacketIQSerializer {

        OnGetImageIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnGetImageIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnGetImageIQ onGetImageIQ = (OnGetImageIQ) object;
            encoder.writeLong(onGetImageIQ.totalSize);
            encoder.writeLong(onGetImageIQ.offset);

            // The imageData can be larger.
            encoder.writeBytes(onGetImageIQ.imageData, (int) onGetImageIQ.offset, (int) onGetImageIQ.size);
            encoder.writeOptionalBytes(onGetImageIQ.imageSha);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceResultIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            long total = decoder.readLong();
            long offset = decoder.readLong();
            byte[] imageData = decoder.readBytes(null).array();
            byte[] sha256 = decoder.readOptionalBytes(null);

            return new OnGetImageIQ(this, serviceResultIQ, imageData, offset, imageData.length, total, sha256);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnGetImageIQSerializer(schemaId, schemaVersion);
    }

    final long offset;
    final long totalSize;
    @NonNull
    final byte[] imageData;
    @Nullable
    final byte[] imageSha;
    final long size;

    OnGetImageIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, @NonNull byte[] imageData,
                 long offset, long size, long totalSize, @Nullable byte[] imageSha) {

        super(serializer, iq);

        this.imageData = imageData;
        this.offset = offset;
        this.size = size;
        this.totalSize = totalSize;
        this.imageSha = imageSha;
    }

    protected int getBufferSize() {

        return SERIALIZER_BUFFER_DEFAULT_SIZE
                + (imageSha != null ? imageSha.length : 0)
                + (int) size;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" offset=");
            stringBuilder.append(offset);
            stringBuilder.append(" totalSize=");
            stringBuilder.append(totalSize);
            stringBuilder.append(" offset=");
            stringBuilder.append(offset);
            stringBuilder.append(" data.length=");
            stringBuilder.append(size);
            stringBuilder.append(" sha.length=");
            stringBuilder.append(imageSha == null ? 0 : imageSha.length);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnGetImageIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
