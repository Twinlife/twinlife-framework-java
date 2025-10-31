/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.crypto;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.Arrays;
import java.util.UUID;

/**
 * SignatureInfoIQ IQ.
 * <p>
 * Schema version 1
 * Date: 2024/07/26
 *
 * <pre>
 * {
 *  "schemaId":"e08a0f39-fb5c-4e54-9f4c-eb0fd60b5a37",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SignatureInfoIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"UUID"},
 *     {"name":"publicKey", "type":"String"},
 *     {"name":"keyIndex", "type":"int"}
 *     {"name":"secret", "type":"bytes"}
 *  ]
 * }
 *
 * </pre>
 */
public class SignatureInfoIQ extends BinaryPacketIQ {

    public static final int SCHEMA_VERSION_1 = 1;
    public static final UUID SCHEMA_ID = UUID.fromString("e08a0f39-fb5c-4e54-9f4c-eb0fd60b5a37");
    public static final BinaryPacketIQSerializer IQ_SIGNATURE_INFO_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    @NonNull
    public final UUID twincodeOutboundId;
    @NonNull
    public final String publicKey;
    @NonNull
    public final byte[] secret;
    public final int keyIndex;

    SignatureInfoIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull UUID twincodeOutboundId, @NonNull String publicKey, @NonNull byte[] secret, int keyIndex) {

        super(serializer, requestId);

        this.twincodeOutboundId = twincodeOutboundId;
        this.publicKey = publicKey;
        this.secret = secret;
        this.keyIndex = keyIndex;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new SignatureInfoIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" twincodeOutboundId=");
            stringBuilder.append(twincodeOutboundId);
            stringBuilder.append(" publicKey=");
            stringBuilder.append(publicKey);
            stringBuilder.append(" secret=");
            stringBuilder.append(Arrays.toString(secret));
        }
    }

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SignatureInfoIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class SignatureInfoIQSerializer extends BinaryPacketIQSerializer {

        SignatureInfoIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SignatureInfoIQ.class);
        }


        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {
            super.serialize(serializerFactory, encoder, object);

            SignatureInfoIQ signatureInfoIQ = (SignatureInfoIQ) object;
            encoder.writeUUID(signatureInfoIQ.twincodeOutboundId);
            encoder.writeString(signatureInfoIQ.publicKey);
            encoder.writeInt(signatureInfoIQ.keyIndex);
            encoder.writeBytes(signatureInfoIQ.secret, 0, signatureInfoIQ.secret.length);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final String publicKey = decoder.readString();
            final int keyIndex = decoder.readInt();
            final byte[] secret = decoder.readBytes(null).array();

            return new SignatureInfoIQ(this, requestId, twincodeOutboundId, publicKey, secret, keyIndex);
        }
    }
}
