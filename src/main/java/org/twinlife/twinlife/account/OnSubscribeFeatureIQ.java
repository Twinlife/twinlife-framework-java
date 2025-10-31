/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;

import java.util.UUID;

/**
 * Subscribe Feature Response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"50FEC907-1D63-4617-A099-D495971930EF",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnSubscribeFeatureIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryErrorPacketIQ"
 *  "fields": [
 *     {"name":"featureList", [null, "type":"String"]}
 *  ]
 * }
 *
 * </pre>
 */
class OnSubscribeFeatureIQ extends BinaryErrorPacketIQ {

    private static class OnSubscribeFeatureIQSerializer extends BinaryErrorPacketIQSerializer {

        OnSubscribeFeatureIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnSubscribeFeatureIQ.class);
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

            BinaryErrorPacketIQ serviceRequestIQ = (BinaryErrorPacketIQ) super.deserialize(serializerFactory, decoder);

            String featureList = decoder.readOptionalString();

            return new OnSubscribeFeatureIQ(this, serviceRequestIQ, featureList);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnSubscribeFeatureIQSerializer(schemaId, schemaVersion);
    }

    @Nullable
    final String featureList;

    OnSubscribeFeatureIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryErrorPacketIQ iq,
                         @Nullable String featureList) {

        super(serializer, iq, iq.getErrorCode());

        this.featureList = featureList;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" featureList=");
            stringBuilder.append(featureList);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnSubscribeFeatureIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
