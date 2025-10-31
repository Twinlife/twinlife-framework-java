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
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Image upload IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"6e0db5e2-318a-4a78-8162-ad88c6ae4b07",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PutImageIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"imageId", "type":"uuid"},
 *     {"name":"kind", ["normal", "thumbnail", "large"]}
 *     {"name":"totalSize", "type":"long"},
 *     {"name":"offset", "type":"long"},
 *     {"name":"data", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
class PutImageIQ extends BinaryPacketIQ {

    static class PutImageIQSerializer extends BinaryPacketIQSerializer {

        PutImageIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, CreateImageIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PutImageIQ putImageIQ = (PutImageIQ) object;

            encoder.writeUUID(putImageIQ.imageId);
            switch (putImageIQ.kind) {
                case NORMAL:
                    encoder.writeEnum(0);
                    break;

                case THUMBNAIL:
                    encoder.writeEnum(1);
                    break;

                case LARGE:
                    encoder.writeEnum(2);
                    break;
            }
            encoder.writeLong(putImageIQ.totalSize);
            encoder.writeLong(putImageIQ.offset);
            encoder.writeBytes(putImageIQ.imageData, putImageIQ.dataOffset, putImageIQ.size);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID imageId = decoder.readUUID();
            ImageService.Kind kind;
            switch (decoder.readEnum()) {
                case 0:
                    kind = ImageService.Kind.NORMAL;
                    break;

                case 1:
                    kind = ImageService.Kind.THUMBNAIL;
                    break;

                case 2:
                    kind = ImageService.Kind.LARGE;
                    break;

                default:
                    throw new SerializerException("Invalid image kind");
            }

            long total = decoder.readLong();
            long offset = decoder.readLong();

            ByteBuffer data = decoder.readBytes(null);
            byte[] imageData = data.array();

            return new PutImageIQ(this, serviceRequestIQ.getRequestId(), imageId, kind,
                    imageData, 0, offset, imageData.length, total);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new PutImageIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID imageId;
    @NonNull
    final ImageService.Kind kind;
    final int dataOffset;
    final long offset;
    final long totalSize;
    final int size;
    @NonNull
    final byte[] imageData;

    PutImageIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID imageId,
               @NonNull ImageService.Kind kind, @NonNull byte[] data, int dataOffset, long offset, int size, long total) {

        super(serializer, requestId);

        this.imageId = imageId;
        this.kind = kind;
        this.offset = offset;
        this.dataOffset = dataOffset;
        this.totalSize = total;
        this.size = size;
        this.imageData = data;
    }

    protected int getBufferSize() {

        return SERIALIZER_BUFFER_DEFAULT_SIZE + size;
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
            stringBuilder.append(" kind=");
            stringBuilder.append(kind);
            stringBuilder.append(" offset=");
            stringBuilder.append(offset);
            stringBuilder.append(" size=");
            stringBuilder.append(size);
            stringBuilder.append(" totalSize=");
            stringBuilder.append(totalSize);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PutImageIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
