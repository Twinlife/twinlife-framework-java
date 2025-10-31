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

import java.util.UUID;

/**
 * Get image IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"3a9ca7c4-6153-426d-b716-d81fd625293c",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"GetImageIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"imageId", "type":"uuid"},
 *     {"name":"kind", ["normal", "thumbnail", "large"]}
 *  ]
 * }
 *
 * </pre>
 */
class GetImageIQ extends BinaryPacketIQ {

    static class GetImageIQSerializer extends BinaryPacketIQSerializer {

        GetImageIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, GetImageIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            GetImageIQ getImageIQ = (GetImageIQ) object;

            encoder.writeUUID(getImageIQ.imageId);
            switch (getImageIQ.kind) {
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

            return new GetImageIQ(this, serviceRequestIQ.getRequestId(), imageId, kind);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new GetImageIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID imageId;
    @NonNull
    final ImageService.Kind kind;

    GetImageIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID imageId, @NonNull ImageService.Kind kind) {

        super(serializer, requestId);

        this.imageId = imageId;
        this.kind = kind;
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("GetImageIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
