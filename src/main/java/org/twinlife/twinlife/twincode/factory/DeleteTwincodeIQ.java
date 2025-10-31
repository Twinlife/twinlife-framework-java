/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.factory;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Delete twincode IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"cf8f2889-4ee2-4e50-a26a-5cbd475bb07a",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"DeleteTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeId", "type":"uuid"}
 *     {"name":"options", "type":"int"}
 *  ]
 * }
 *
 * </pre>
 */
public class DeleteTwincodeIQ extends BinaryPacketIQ {

    static class DeleteTwincodeIQSerializer extends BinaryPacketIQSerializer {

        DeleteTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, DeleteTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            DeleteTwincodeIQ deleteTwincodeIQ = (DeleteTwincodeIQ) object;

            encoder.writeUUID(deleteTwincodeIQ.twincodeId);
            encoder.writeInt(deleteTwincodeIQ.options);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new DeleteTwincodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final UUID twincodeId;
    private final int options;

    @NonNull
    public UUID getTwincodeId() {

        return twincodeId;
    }

    public int getOptions() {

        return options;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" twincodeId=");
            stringBuilder.append(twincodeId);
            stringBuilder.append(" options=");
            stringBuilder.append(options);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("DeleteTwincodeIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public DeleteTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                            @NonNull UUID twincodeId, int options) {

        super(serializer, requestId);

        this.twincodeId = twincodeId;
        this.options = options;
    }
}
