/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Get invitation code IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"95335487-91fa-4cdc-939b-e047a068e94d",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"GetInvitationCodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"code", "type":"string"}
 *  ]
 * }
 *
 * </pre>
 */
public class GetInvitationCodeIQ extends BinaryPacketIQ {

    static class GetInvitationCodeIQSerializer extends BinaryPacketIQSerializer {

        GetInvitationCodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, GetInvitationCodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            GetInvitationCodeIQ getInvitationCodeIQ = (GetInvitationCodeIQ) object;

            encoder.writeString(getInvitationCodeIQ.code);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            String code = decoder.readString();

            return new GetInvitationCodeIQ(this, serviceRequestIQ, code);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new GetInvitationCodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final String code;

    public GetInvitationCodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull String code) {

        super(serializer, requestId);
        this.code = code;
    }

    @NonNull
    public String getCode() {

        return code;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" code=");
        stringBuilder.append(code);
    }

    @Override
    @NonNull
    public String toString() {

        if (!BuildConfig.ENABLE_DUMP) {
            return "";
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("GetInvitationCodeIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private GetInvitationCodeIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ, @NonNull String code) {

        super(serializer, serviceRequestIQ);

        this.code = code;
    }
}
