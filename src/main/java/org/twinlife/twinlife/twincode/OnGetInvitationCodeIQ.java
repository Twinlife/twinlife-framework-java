/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.List;
import java.util.UUID;

/**
 * Get invitation code response IQ.
 *
 * <pre>
 * {
 *  "schemaId":"a16cf169-81dd-4a47-8787-5856f409e017",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnGetInvitationCodeIQ",
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
 *     {"name": "signature": [null, "type":"bytes"]},
 *     {"name": "twincodeId", "type":"uuid"},
 *     {"name": "publicKey", [null, "type": "string"]}}
 *   ]
 * }
 * </pre>
 */
public class OnGetInvitationCodeIQ extends OnGetTwincodeIQ {

    static class OnGetInvitationCodeIQSerializer extends BinaryPacketIQSerializer {

        OnGetInvitationCodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnGetInvitationCodeIQ.class);
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
            UUID twincodeId = decoder.readUUID();
            String publicKey = decoder.readOptionalString();

            return new OnGetInvitationCodeIQ(this, serviceRequestIQ, twincodeId, modificationDate, attributes, signature, publicKey);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnGetInvitationCodeIQSerializer(schemaId, schemaVersion);
    }


    @NonNull
    final UUID twincodeId;
    @Nullable
    final String publicKey;

    public OnGetInvitationCodeIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, @NonNull UUID twincodeId, long modificationDate,
                                 @NonNull List<AttributeNameValue> attributes, byte[] signature, @Nullable String publicKey) {

        super(serializer, iq, modificationDate, attributes, signature);

        this.twincodeId = twincodeId;
        this.publicKey = publicKey;
    }

    @NonNull
    public UUID getTwincodeId() {
        return twincodeId;
    }

    @Nullable
    public String getPublicKey() {
        return publicKey;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" twincodeId=");
        stringBuilder.append(twincodeId);
        stringBuilder.append(" publicKey=");
        stringBuilder.append(publicKey);
    }

    @Override
    @NonNull
    public String toString() {

        if (!BuildConfig.ENABLE_DUMP) {
            return "";
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("OnGetInvitationCodeIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }
}
