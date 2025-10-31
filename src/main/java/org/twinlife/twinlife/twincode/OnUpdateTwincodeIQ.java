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

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Update twincode response IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"2b0ff6f7-75bb-44a6-9fac-0a9b28fc84dd",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnUpdateTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"modificationDate", "type":"long"},
 * }
 *
 * </pre>
 */
public class OnUpdateTwincodeIQ extends BinaryPacketIQ {

    static class OnUpdateTwincodeIQSerializer extends BinaryPacketIQSerializer {

        OnUpdateTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnUpdateTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnUpdateTwincodeIQ onGetTwincodeIQ = (OnUpdateTwincodeIQ) object;

            encoder.writeLong(onGetTwincodeIQ.modificationDate);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            long modificationDate = decoder.readLong();

            return new OnUpdateTwincodeIQ(this, serviceRequestIQ, modificationDate);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnUpdateTwincodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final Serializer serializer;
    final long modificationDate;

    OnUpdateTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, long modificationDate) {

        super(serializer, iq);

        this.serializer = serializer;
        this.modificationDate = modificationDate;
    }

    public long getModificationDate() {

        return modificationDate;
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnUpdateTwincodeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
