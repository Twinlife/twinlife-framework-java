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

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.List;
import java.util.UUID;

/**
 * Get twincode response IQ.
 * <p>
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"3a9ca7c4-6153-426d-b716-d81fd625293c",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnGetTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"modificationDate", "type":"long"},
 *     {"name":"attributeCount", "type":"int"},
 *     {"name":"attributes", [
 *      {"name":"name", "type": "string"}
 *      {"name":"type", ["long", "string", "uuid"]}
 *      {"name":"value", "type": ["long", "string", "uuid"]}
 *     ]}
 *     {"name": "signature": [null, "type":"bytes"]}
 *   ]
 * }
 * </pre>
 * Schema version 1 (REMOVED 2024-02-02 after 22.x)
 */
public class OnGetTwincodeIQ extends BinaryPacketIQ {

    static class OnGetTwincodeIQSerializer extends BinaryPacketIQSerializer {

        OnGetTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnGetTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            throw new SerializerException();
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            long modificationDate = decoder.readLong();
            List<AttributeNameValue> attributes = deserializeAttributeList(decoder);
            byte[] signature = decoder.readOptionalBytes(null);

            return new OnGetTwincodeIQ(this, serviceRequestIQ, modificationDate, attributes, signature);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnGetTwincodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final Serializer serializer;
    final List<AttributeNameValue> attributes;
    final long modificationDate;
    @Nullable
    final byte[] signature;

    OnGetTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, long modificationDate,
                    @NonNull List<AttributeNameValue> attributes, @Nullable byte[] signature) {

        super(serializer, iq);

        this.serializer = serializer;
        this.attributes = attributes;
        this.modificationDate = modificationDate;
        this.signature = signature;
    }

    public List<AttributeNameValue> getAttributes() {

        return attributes;
    }

    public long getModificationDate() {

        return modificationDate;
    }

    @Nullable
    public byte[] getSignature() {

        return signature;
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnGetTwincodeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
