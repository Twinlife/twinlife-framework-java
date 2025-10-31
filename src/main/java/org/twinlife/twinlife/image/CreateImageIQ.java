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
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Image creation IQ.
 *
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"ea6b4372-3c7d-4ce8-92d8-87a589906a01",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"CreateImageIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"thumbnailSha", "type":"bytes"},
 *     {"name":"imageSha", [null, "type":"bytes"]},
 *     {"name":"imageLargeSha", [null, "type":"bytes"]},
 *     {"name":"thumbnail", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class CreateImageIQ extends BinaryPacketIQ {

    static class CreateImageIQSerializer extends BinaryPacketIQSerializer {

        CreateImageIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, CreateImageIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            CreateImageIQ createImageIQ = (CreateImageIQ) object;

            encoder.writeData(createImageIQ.thumbnailSha);
            if (createImageIQ.imageSha != null) {
                encoder.writeEnum(1);
                encoder.writeData(createImageIQ.imageSha);
            } else {
                encoder.writeEnum(0);
            }
            if (createImageIQ.imageLargeSha != null) {
                encoder.writeEnum(1);
                encoder.writeData(createImageIQ.imageLargeSha);
            } else {
                encoder.writeEnum(0);
            }
            encoder.writeData(createImageIQ.thumbnailData);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            byte[] thumbnailSha;
            ByteBuffer data = decoder.readBytes(null);
            thumbnailSha = data.array();

            byte[] imageSha;
            if (decoder.readEnum() == 1) {
                data = decoder.readBytes(null);
                imageSha = data.array();
            } else {
                imageSha = null;
            }

            byte[] imageLargeSha;
            if (decoder.readEnum() == 1) {
                data = decoder.readBytes(null);
                imageLargeSha = data.array();
            } else {
                imageLargeSha = null;
            }

            data = decoder.readBytes(null);
            byte[] thumbnailData = data.array();

            return new CreateImageIQ(this, serviceRequestIQ.getRequestId(), thumbnailSha,
                    imageSha, imageLargeSha, thumbnailData);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new CreateImageIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final byte[] thumbnailSha;
    @Nullable
    final byte[] imageSha;
    @Nullable
    final byte[] imageLargeSha;
    @NonNull
    final byte[] thumbnailData;

    CreateImageIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull byte[] thumbnailSha, @Nullable byte[] imageSha, @Nullable byte[] imageLargeSha,
                  @NonNull byte[] thumbnailData) {

        super(serializer, requestId);

        this.thumbnailSha = thumbnailSha;
        this.imageSha = imageSha;
        this.imageLargeSha = imageLargeSha;
        this.thumbnailData = thumbnailData;
    }

    protected int getBufferSize() {

        return SERIALIZER_BUFFER_DEFAULT_SIZE
                + (imageSha != null ? imageSha.length : 0)
                + (imageLargeSha != null ? imageLargeSha.length : 0)
                + thumbnailData.length;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" thumbnailData.length=");
            stringBuilder.append(thumbnailData.length);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("CreateImageIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
