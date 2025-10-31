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

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Create invitation code IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"8dcfcba5-b8c0-4375-a501-d24534ed4a3b",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"CreateInvitationCodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *      {"name":"twincodeId", "type":"uuid"},
 *      {"name":"validityPeriod", "type":"int"},
 *      {"name":"publicKey", [null, "type":"string"]}
 *  ]
 * }
 * </pre>
 */
public class CreateInvitationCodeIQ extends BinaryPacketIQ {

    static class CreateInvitationCodeIQSerializer extends BinaryPacketIQSerializer {

        CreateInvitationCodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, CreateInvitationCodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            CreateInvitationCodeIQ createInvitationCodeIQ = (CreateInvitationCodeIQ) object;

            encoder.writeUUID(createInvitationCodeIQ.twincodeId);
            encoder.writeInt(createInvitationCodeIQ.validityPeriod);
            encoder.writeOptionalString(createInvitationCodeIQ.publicKey);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID twincodeId = decoder.readUUID();
            int validityPeriod = decoder.readInt();
            String pubKey = decoder.readOptionalString();

            return new CreateInvitationCodeIQ(this, serviceRequestIQ.getRequestId(), twincodeId, validityPeriod, pubKey);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new CreateInvitationCodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final UUID twincodeId;
    private final int validityPeriod;
    @Nullable
    private final String publicKey;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" twincodeId=");
        stringBuilder.append(twincodeId);
        stringBuilder.append(" validityPeriod=");
        stringBuilder.append(validityPeriod);
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
        stringBuilder.append("CreateInvitationCodeIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    public CreateInvitationCodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                                  @NonNull UUID twincodeId, int validityPeriod, @Nullable String publicKey) {
        super(serializer, requestId);

        this.twincodeId = twincodeId;
        this.validityPeriod = validityPeriod;
        this.publicKey = publicKey;
    }
}
