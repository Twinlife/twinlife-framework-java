/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * UpdateGeolocation IQ.
 * <p>
 * Schema version 1
 *  Date: 2024/06/17
 *
 * <pre>
 * {
 *  "schemaId":"92790026-71f3-4702-b8ca-e9d8ce5a3f4d",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushGeolocationIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"updatedTimestamp", "type":"long"}
 *     {"name":"longitude", "type":"double"}
 *     {"name":"latitude", "type":"double"}
 *     {"name":"altitude", "type":"double"}
 *     {"name":"mapLongitudeDelta", "type":"double"}
 *     {"name":"mapLatitudeDelta", "type":"double"}
 *  ]
 * }
 *
 * </pre>
 */
class UpdateGeolocationIQ extends BinaryPacketIQ {

    static final int SCHEMA_VERSION_1 = 1;
    static final UUID SCHEMA_ID = UUID.fromString("92790026-71f3-4702-b8ca-e9d8ce5a3f4d");
    static final BinaryPacketIQSerializer IQ_UPDATE_GEOLOCATION_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    final long updatedTimestamp;
    final double longitude;
    final double latitude;
    final double altitude;
    final double mapLongitudeDelta;
    final double mapLatitudeDelta;

    UpdateGeolocationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                        long updatedTimestamp, double longitude, double latitude, double altitude,
                        double mapLongitudeDelta, double mapLatitudeDelta) {

        super(serializer, requestId);

        this.updatedTimestamp = updatedTimestamp;
        this.longitude = longitude;
        this.latitude = latitude;
        this.altitude = altitude;
        this.mapLongitudeDelta = mapLongitudeDelta;
        this.mapLatitudeDelta = mapLatitudeDelta;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new UpdateGeolocationIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" longitude=");
            stringBuilder.append(longitude);
            stringBuilder.append(" latitude=");
            stringBuilder.append(latitude);
            stringBuilder.append(" altitude=");
            stringBuilder.append(altitude);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("UpdateGeolocationIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class UpdateGeolocationIQSerializer extends BinaryPacketIQSerializer {

        UpdateGeolocationIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, UpdateGeolocationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            UpdateGeolocationIQ updateGeolocationIQ = (UpdateGeolocationIQ) object;

            encoder.writeLong(updateGeolocationIQ.updatedTimestamp);
            encoder.writeDouble(updateGeolocationIQ.longitude);
            encoder.writeDouble(updateGeolocationIQ.latitude);
            encoder.writeDouble(updateGeolocationIQ.altitude);
            encoder.writeDouble(updateGeolocationIQ.mapLongitudeDelta);
            encoder.writeDouble(updateGeolocationIQ.mapLongitudeDelta);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final long updatedTimestamp = decoder.readLong();

            double longitude = decoder.readDouble();
            double latitude = decoder.readDouble();
            double altitude = decoder.readDouble();
            double mapLongitudeDelta = decoder.readDouble();
            double mapLatitudeDelta = decoder.readDouble();

            return new UpdateGeolocationIQ(this, requestId, updatedTimestamp, longitude,
                    latitude, altitude, mapLongitudeDelta, mapLatitudeDelta);
        }
    }
}
