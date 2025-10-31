/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Update twincode IQ.
 * <p>
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"8efcb2a1-6607-4b06-964c-ec65ed459ffc",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"UpdateTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeId", "type":"uuid"},
 *     {"name":"attributes", [
 *      {"name":"name", "type": "string"}
 *      {"name":"type", ["long", "string", "uuid"]}
 *      {"name":"value", "type": ["long", "string", "uuid"]}
 *     ]}
 *     {"name":"deleteAttributes", [
 *       {"name":"name", "type": "string"}
 *     ]},
 *     {"name": "signature": [null, "type":"bytes"]}
 *   ]
 * }
 * </pre>
 * Schema version 1 (REMOVED 2024-02-02 after 22.x)
 */
public class UpdateTwincodeIQ extends BinaryPacketIQ {

    static class UpdateTwincodeIQSerializer extends BinaryPacketIQSerializer {

        UpdateTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, UpdateTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            UpdateTwincodeIQ updateTwincodeIQ = (UpdateTwincodeIQ) object;

            encoder.writeUUID(updateTwincodeIQ.twincodeId);
            serialize(encoder, updateTwincodeIQ.attributes);
            if (updateTwincodeIQ.deleteAttributeNames == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(updateTwincodeIQ.deleteAttributeNames.size());
                for (String name : updateTwincodeIQ.deleteAttributeNames) {
                    encoder.writeString(name);
                }
            }
            encoder.writeOptionalBytes(updateTwincodeIQ.signature);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID twincodeId = decoder.readUUID();
            List<BaseService.AttributeNameValue> attributes = deserializeAttributeList(decoder);
            int deleteCount = decoder.readInt();
            List<String> deleteAttributeNames = null;
            if (deleteCount > 0) {
                deleteAttributeNames = new ArrayList<>(deleteCount);
                for (int i = 0; i < deleteCount; i++) {
                    deleteAttributeNames.add(decoder.readString());
                }
            }
            byte[] signature = decoder.readOptionalBytes(null);

            return new UpdateTwincodeIQ(this, serviceRequestIQ.getRequestId(), twincodeId,
                    attributes, deleteAttributeNames, signature);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new UpdateTwincodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final Serializer serializer;
    @NonNull
    final UUID twincodeId;
    @NonNull
    final List<BaseService.AttributeNameValue> attributes;
    @Nullable
    final List<String> deleteAttributeNames;
    @Nullable
    final byte[] signature;

    public UpdateTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID twincodeId,
                            @NonNull List<BaseService.AttributeNameValue> attributes,
                            @Nullable List<String> deleteAttributeNames, @Nullable byte[] signature) {

        super(serializer, requestId);

        this.serializer = serializer;
        this.twincodeId = twincodeId;
        this.attributes = attributes;
        this.deleteAttributeNames = deleteAttributeNames;
        this.signature = signature;
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("UpdateTwincodeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
