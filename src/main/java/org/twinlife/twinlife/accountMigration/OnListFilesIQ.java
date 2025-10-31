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

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * List files IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"e74fea73-abc7-42ca-ad37-b636f6c4df2b",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ListFilesIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"count", "type":"int"},
 *     [{"name":"fileId", "type":"int"},
 *      {"name":"offset", "type":"long"}]
 *  ]
 * }
 *
 * </pre>
 */
class OnListFilesIQ extends BinaryPacketIQ {

    private static final int MAX_SIZE_PER_FILE = 16;

    static class ListFilesIQSerializer extends BinaryPacketIQSerializer {

        ListFilesIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnListFilesIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnListFilesIQ listFilesIQ = (OnListFilesIQ) object;

            encoder.writeInt(listFilesIQ.files.size());
            for (FileState state : listFilesIQ.files) {
                encoder.writeInt(state.mFileId);
                encoder.writeLong(state.mOffset);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            List<FileState> files = new ArrayList<>();
            int count = decoder.readInt();
            while (count > 0) {
                int fileId = decoder.readInt();
                long offset = decoder.readLong();

                files.add(new FileState(fileId, offset));
                count--;
            }

            return new OnListFilesIQ(this, serviceRequestIQ, files);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new ListFilesIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final List<FileState> files;

    OnListFilesIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                  @NonNull List<FileState> files) {

        super(serializer, serviceRequestIQ);

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
            stringBuilder.append("OnListFilesIQ\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
