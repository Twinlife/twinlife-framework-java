/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * PushGeolocation IQ.
 * <p>
 * Schema version 2
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"7a9772c3-5f99-468d-87af-d67fdb181295",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"PushGeolocationIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"sendToTwincodeOutboundId", "type":["null", "UUID"]},
 *     {"name":"replyTo", "type":["null", {
 *         {"name":"twincodeOutboundId", "type":"uuid"},
 *         {"name":"sequenceId", "type":"long"}
 *     }},
 *     {"name":"createdTimestamp", "type":"long"}
 *     {"name":"sentTimestamp", "type":"long"}
 *     {"name":"expireTimeout", "type":"long"}
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
class PushGeolocationIQ extends BinaryPacketIQ {

    static final int SCHEMA_VERSION_2 = 2;
    static final UUID SCHEMA_ID = UUID.fromString("7a9772c3-5f99-468d-87af-d67fdb181295");
    static final BinaryPacketIQSerializer IQ_PUSH_GEOLOCATION_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

    @NonNull
    final GeolocationDescriptorImpl geolocationDescriptorImpl;

    PushGeolocationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull GeolocationDescriptorImpl geolocationDescriptorImpl) {

        super(serializer, requestId);

        this.geolocationDescriptorImpl = geolocationDescriptorImpl;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new PushGeolocationIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" geolocationDescriptor=");
            stringBuilder.append(geolocationDescriptorImpl);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushGeolocationIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class PushGeolocationIQSerializer extends BinaryPacketIQSerializer {

        PushGeolocationIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PushGeolocationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PushGeolocationIQ pushGeolocationIQ = (PushGeolocationIQ) object;

            GeolocationDescriptorImpl geolocationDescriptor = pushGeolocationIQ.geolocationDescriptorImpl;
            encoder.writeUUID(geolocationDescriptor.getTwincodeOutboundId());
            encoder.writeLong(geolocationDescriptor.getSequenceId());
            encoder.writeOptionalUUID(geolocationDescriptor.getSendTo());
            DescriptorId replyTo = geolocationDescriptor.getReplyToDescriptorId();
            if (replyTo == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(replyTo.twincodeOutboundId);
                encoder.writeLong(replyTo.sequenceId);
            }
            encoder.writeLong(geolocationDescriptor.getCreatedTimestamp());
            encoder.writeLong(geolocationDescriptor.getSentTimestamp());
            encoder.writeLong(geolocationDescriptor.getExpireTimeout());
            encoder.writeDouble(geolocationDescriptor.getLongitude());
            encoder.writeDouble(geolocationDescriptor.getLatitude());
            encoder.writeDouble(geolocationDescriptor.getAltitude());
            encoder.writeDouble(geolocationDescriptor.getMapLongitudeDelta());
            encoder.writeDouble(geolocationDescriptor.getMapLatitudeDelta());
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final UUID sendTo = decoder.readOptionalUUID();
            final DescriptorId replyTo = DescriptorImpl.DescriptorImplSerializer_4.readOptionalDescriptorId(decoder);
            final long createdTimestamp = decoder.readLong();
            final long sentTimestamp = decoder.readLong();
            final long expireTimeout = decoder.readLong();

            double longitude = decoder.readDouble();
            double latitude = decoder.readDouble();
            double altitude = decoder.readDouble();
            double mapLongitudeDelta = decoder.readDouble();
            double mapLatitudeDelta = decoder.readDouble();

            GeolocationDescriptorImpl geolocationDescriptor = new GeolocationDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout,
                    sendTo, replyTo, createdTimestamp, sentTimestamp, longitude, latitude, altitude, mapLongitudeDelta, mapLatitudeDelta, false, null);
            return new PushGeolocationIQ(this, requestId, geolocationDescriptor);
        }
    }
}
