/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.AccountService.MerchantIdentification;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Subscribe Feature Request IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"eb420020-e55a-44b0-9e9e-9922ec055407",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SubscribeFeatureIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"merchantId", "type":"enum"},
 *     {"name":"purchaseProductId", "type":"string"},
 *     {"name":"purchaseToken", "type":"string"},
 *     {"name":"purchaseOrderId", "type":"string"}
 *  ]
 * }
 *
 * </pre>
 */
class SubscribeFeatureIQ extends BinaryPacketIQ {

    private static class SubscribeFeatureIQSerializer extends BinaryPacketIQSerializer {

        SubscribeFeatureIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SubscribeFeatureIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            SubscribeFeatureIQ subscribeFeatureIQ = (SubscribeFeatureIQ) object;

            switch (subscribeFeatureIQ.merchantId) {
                case MERCHANT_GOOGLE:
                    encoder.writeEnum(1);
                    break;

                case MERCHANT_EXTERNAL:
                    encoder.writeEnum(2);
                    break;

                default:
                    throw new SerializerException();
            }
            encoder.writeString(subscribeFeatureIQ.purchaseProductId);
            encoder.writeString(subscribeFeatureIQ.purchaseToken);
            encoder.writeString(subscribeFeatureIQ.purchaseOrderId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new SubscribeFeatureIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final MerchantIdentification merchantId;
    @NonNull
    final String purchaseProductId;
    @NonNull
    final String purchaseToken;
    @NonNull
    final String purchaseOrderId;

    SubscribeFeatureIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                       @NonNull MerchantIdentification merchantId, @NonNull String purchaseProductId,
                       @NonNull String purchaseToken, @NonNull String purchaseOrderId) {

        super(serializer, requestId);

        this.merchantId = merchantId;
        this.purchaseProductId = purchaseProductId;
        this.purchaseToken = purchaseToken;
        this.purchaseOrderId = purchaseOrderId;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" merchantId=");
            stringBuilder.append(merchantId);
            stringBuilder.append(" purchaseProductId=");
            stringBuilder.append(purchaseProductId);
            stringBuilder.append(" purchaseToken=");
            stringBuilder.append(purchaseToken);
            stringBuilder.append(" purchaseOrderId=");
            stringBuilder.append(purchaseOrderId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SubscribeFeatureIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
