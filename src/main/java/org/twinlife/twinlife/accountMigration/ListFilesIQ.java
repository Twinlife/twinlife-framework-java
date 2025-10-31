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
import org.twinlife.twinlife.util.FileInfoImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * List files IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"5964dbf0-5620-4c78-963b-c6e08665fc33",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ListFilesIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"count", "type":"int"},
 *     [{"name":"path", "type":"string"},
 *      {"name":"fileId", "type":"int"},
 *      {"name":"size", "type":"long"},
 *      {"name":"timestamp", "type":"long"}]
 *  ]
 * }
 *
 * </pre>
 */
class ListFilesIQ extends BinaryPacketIQ {

    // Path is relative, build from an UUID and in the form 'Conversations/<UUID>/<seq-id>.<ext>'
    // This value need not be exact and a file can be larger.
    private static final int MAX_SIZE_PER_FILE = 256;

    static class ListFilesIQSerializer extends BinaryPacketIQSerializer {

        ListFilesIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, ListFilesIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ListFilesIQ listFilesIQ = (ListFilesIQ) object;

            encoder.writeInt(listFilesIQ.files.size());
            for (FileInfoImpl file : listFilesIQ.files) {
                encoder.writeString(file.getPath());
                encoder.writeInt(file.getIndex());
                encoder.writeLong(file.getSize());
                encoder.writeLong(file.getModificationDate());
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            List<FileInfoImpl> files = new ArrayList<>();
            int count = decoder.readInt();
            while (count > 0) {
                String path = decoder.readString();
                int fileId = decoder.readInt();
                long size = decoder.readLong();
                long modificationDate = decoder.readLong();

                files.add(new FileInfoImpl(fileId, path, size, modificationDate));
                count--;
            }

            return new ListFilesIQ(this, serviceRequestIQ, files);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new ListFilesIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final List<FileInfoImpl> files;

    ListFilesIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull List<FileInfoImpl> files) {

        super(serializer, requestId);

        this.files = files;
    }

    protected int getBufferSize() {

        return SERIALIZER_BUFFER_DEFAULT_SIZE + files.size() * MAX_SIZE_PER_FILE;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" count=");
            stringBuilder.append(files.size());
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("ListFilesIQ\n");
            appendTo(stringBuilder);
        }
        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private ListFilesIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                        @NonNull List<FileInfoImpl> files) {

        super(serializer, serviceRequestIQ);

        this.files = files;
    }
}
