/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.secureroster;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Create a secure roster request IQ.
 *
 * Schema version 1
 *  Date: 2026/03/25
 * <pre>
 * {
 *  "schemaId":"74a4430b-9910-4fad-bba3-85c92dc99c9e",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"CreateSecureRosterIQ",
 *  "namespace":"org.twinlife.schemas.secureroster",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"createOptions", "type":"int"},
 *     {"name":"rosterSchemaId", "type":"uuid"},
 *     {"name":"publicKeyId", "type":"uuid"},
 *     {"name":"publicKey", "type":"bytes"},
 *  ]
 * }
 * </pre>
 */
class CreateRosterIQ extends BinaryPacketIQ {

    private static class CreateRosterIQSerializer extends BinaryPacketIQSerializer {

        CreateRosterIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, CreateRosterIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final CreateRosterIQ createRosterIQ = (CreateRosterIQ) object;
            encoder.writeInt(createRosterIQ.createOptions);
            encoder.writeUUID(createRosterIQ.rosterSchemaId);
            encoder.writeUUID(createRosterIQ.publicKeyId);
            encoder.writeBytes(createRosterIQ.publicKey, 0, createRosterIQ.publicKey.length);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            final int createOptions = decoder.readInt();
            final UUID rosterSchemaId = decoder.readUUID();
            final UUID publicKeyId = decoder.readUUID();
            final byte[] publicKey = decoder.readBytes(null).array();

            return new CreateRosterIQ(this, serviceRequestIQ.getRequestId(), createOptions, rosterSchemaId, publicKeyId, publicKey);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new CreateRosterIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID rosterSchemaId;
    @NonNull
    final UUID publicKeyId;
    @NonNull
    final byte[] publicKey;
    final int createOptions;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" rosterSchemaId=");
            stringBuilder.append(rosterSchemaId);
            stringBuilder.append(" createOptions=");
            stringBuilder.append(createOptions);
            stringBuilder.append(" publicKeyId=");
            stringBuilder.append(publicKeyId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("CreateRosterIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    CreateRosterIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                     int createOptions, @NonNull UUID rosterSchemaId, @NonNull UUID publicKeyId, @NonNull byte[] publicKey) {

        super(serializer, requestId);

        this.createOptions = createOptions;
        this.rosterSchemaId = rosterSchemaId;
        this.publicKeyId = publicKeyId;
        this.publicKey = publicKey;
    }
}
