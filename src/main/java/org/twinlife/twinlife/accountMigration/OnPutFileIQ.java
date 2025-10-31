/*
 *  Copyright (c) 2020 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * File put response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"e74fea73-abc7-42ca-ad37-b636f6c4df2b",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnPutFileIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"fileId", "type":"int"}
 *     {"name":"offset", "type":"long"},
 *  ]
 * }
 *
 * </pre>
 */
class OnPutFileIQ extends BinaryPacketIQ {

    static class OnPutFileIQSerializer extends BinaryPacketIQSerializer {

        OnPutFileIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnPutFileIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnPutFileIQ onPutImageIQ = (OnPutFileIQ) object;

            encoder.writeInt(onPutImageIQ.fileId);
            encoder.writeLong(onPutImageIQ.offset);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceResultIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            int fileId = decoder.readInt();
            long offset = decoder.readLong();

            return new OnPutFileIQ(this, serviceResultIQ, fileId, offset);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnPutFileIQSerializer(schemaId, schemaVersion);
    }

    final int fileId;
    final long offset;

    OnPutFileIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, int fileId, long offset) {

        super(serializer, iq);

        this.fileId = fileId;
        this.offset = offset;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" fileId=");
            stringBuilder.append(fileId);
            stringBuilder.append(" offset=");
            stringBuilder.append(offset);
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("OnPutFileIQ\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
