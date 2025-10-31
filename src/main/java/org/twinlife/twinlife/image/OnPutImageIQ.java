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
 * Image put response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"f48fa894-a200-4aa8-a7d4-22ea21cfd008",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnPutImageIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name": "offset", "type":"long"}
 *     {"name":"status", ["incomplete", "complete", "error"]}
 *  ]
 * }
 *
 * </pre>
 */
class OnPutImageIQ extends BinaryPacketIQ {

    static class OnPutImageIQSerializer extends BinaryPacketIQSerializer {

        OnPutImageIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnGetImageIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnPutImageIQ onPutImageIQ = (OnPutImageIQ) object;

            encoder.writeLong(onPutImageIQ.offset);
            switch (onPutImageIQ.status) {
                case INCOMPLETE:
                    encoder.writeEnum(0);
                    break;

                case COMPLETE:
                    encoder.writeEnum(1);
                    break;

                case ERROR:
                    encoder.writeEnum(2);
                    break;
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceResultIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            long offset = decoder.readLong();
            Status status;
            switch (decoder.readEnum()) {
                case 0:
                    status = Status.INCOMPLETE;
                    break;
                case 1:
                    status = Status.COMPLETE;
                    break;
                case 2:
                    status = Status.ERROR;
                    break;
                default:
                    throw new SerializerException();
            }

            return new OnPutImageIQ(this, serviceResultIQ, offset, status);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnPutImageIQSerializer(schemaId, schemaVersion);
    }

    public enum Status {
        INCOMPLETE,
        COMPLETE,
        ERROR
    }

    final long offset;
    @NonNull
    final Status status;

    OnPutImageIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, long offset, @NonNull Status status) {

        super(serializer, iq);

        this.offset = offset;
        this.status = status;
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
            stringBuilder.append(" status=");
            stringBuilder.append(status);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnPutImageIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
