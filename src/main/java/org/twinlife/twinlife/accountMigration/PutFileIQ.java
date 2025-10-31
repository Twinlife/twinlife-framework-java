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
 * File upload IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"ccc791c2-3a5c-4d83-ab06-48137a4ad262",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PutFileIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"fileId", "type":"int"},
 *     {"name":"offset", "type":"long"},
 *     {"name":"data", [null, "type":"bytes"]},
 *     {"name":"sha256", [null, "type":"bytes"]}
 *  ]
 * }
 *
 * </pre>
 */
class PutFileIQ extends BinaryPacketIQ {

    static class PutFileIQSerializer extends BinaryPacketIQSerializer {

        PutFileIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PutFileIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PutFileIQ putFileIQ = (PutFileIQ) object;

            encoder.writeInt(putFileIQ.fileId);
            encoder.writeLong(putFileIQ.offset);
            if (putFileIQ.fileData != null) {
                encoder.writeEnum(1);
                encoder.writeBytes(putFileIQ.fileData, putFileIQ.dataOffset, putFileIQ.size);
            } else {
                encoder.writeEnum(0);
            }
            encoder.writeOptionalBytes(putFileIQ.sha256);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            int fileId = decoder.readInt();
            long offset = decoder.readLong();

            byte[] imageData;
            if (decoder.readEnum() == 1) {
                ByteBuffer data = decoder.readBytes(null);
                imageData = data.array();
            } else {
                imageData = null;
            }

            byte[] sha256;
            if (decoder.readEnum() == 1) {
                ByteBuffer data = decoder.readBytes(null);
                sha256 = data.array();
            } else {
                sha256 = null;
            }

            return new PutFileIQ(this, serviceRequestIQ, fileId, imageData, offset, sha256);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new PutFileIQSerializer(schemaId, schemaVersion);
    }

    final int fileId;
    final int dataOffset;
    final long offset;
    final int size;
    @Nullable
    final byte[] fileData;
    @Nullable
    final byte[] sha256;

    PutFileIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, int fileId,
              @Nullable byte[] data, int dataOffset, long offset, int size, @Nullable byte[] sha256) {

        super(serializer, requestId);

        this.fileId = fileId;
        this.offset = offset;
        this.dataOffset = dataOffset;
        this.size = size;
        this.fileData = data;
        this.sha256 = sha256;
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
            stringBuilder.append(" fileId=");
            stringBuilder.append(fileId);
            stringBuilder.append(" offset=");
            stringBuilder.append(offset);
            stringBuilder.append(" size=");
            stringBuilder.append(size);
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("PutFileIQ\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private PutFileIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                      int fileId, @Nullable byte[] data, long offset, @Nullable byte[] sha256) {

        super(serializer, serviceRequestIQ);

        this.fileId = fileId;
        if (data != null) {
            this.size = data.length;
        } else {
            this.size = 0;
        }
        this.dataOffset = 0;
        this.offset = offset;
        this.fileData = data;
        this.sha256 = sha256;
    }
}
