/*
 *  Copyright (c) 2019-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 2
 *  Date: 2021/04/07
 *
 * {
 *  "schemaId":"753da853-a54d-4cc5-b8b6-dec3855d8e08",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"GeolocationDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.4"
 *  "fields":
 *  [
 *   {"name":"longitude", "type":"double"}
 *   {"name":"latitude", "type":"double"}
 *   {"name":"altitude", "type":"double"}
 *   {"name":"mapLongitudeDelta", "type":"double"}
 *   {"name":"mapLatitudeDelta", "type":"double"}
 *   {"name":"updated", "type":"boolean"}
 *   {"name":"localMapPath", "type": ["null", "string"]}
 *  ]
 * }
 *
 * Schema version 1
 *  Date: 2019/02/14
 *
 * {
 *  "schemaId":"753da853-a54d-4cc5-b8b6-dec3855d8e08",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"GeolocationDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.3"
 *  "fields":
 *  [
 *   {"name":"longitude", "type":"double"}
 *   {"name":"latitude", "type":"double"}
 *   {"name":"altitude", "type":"double"}
 *   {"name":"mapLongitudeDelta", "type":"double"}
 *   {"name":"mapLatitudeDelta", "type":"double"}
 *   {"name":"updated", "type":"boolean"}
 *   {"name":"localMapPath", "type": ["null", "string"]}
 *  ]
 * }
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.util.UUID;

public class GeolocationDescriptorImpl extends DescriptorImpl implements ConversationService.GeolocationDescriptor {
    private static final String LOG_TAG = "GeolocationDescripto...";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("753da853-a54d-4cc5-b8b6-dec3855d8e08");
    static final int SCHEMA_VERSION_2 = 2;

    static class GeolocationDescriptorImplSerializer_2 extends DescriptorImplSerializer_4 {

        GeolocationDescriptorImplSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, ObjectDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            GeolocationDescriptorImpl geolocationDescriptorImpl = (GeolocationDescriptorImpl) object;
            encoder.writeDouble(geolocationDescriptorImpl.mLongitude);
            encoder.writeDouble(geolocationDescriptorImpl.mLatitude);
            encoder.writeDouble(geolocationDescriptorImpl.mAltitude);
            encoder.writeDouble(geolocationDescriptorImpl.mMapLongitudeDelta);
            encoder.writeDouble(geolocationDescriptorImpl.mMapLatitudeDelta);
            encoder.writeBoolean(geolocationDescriptorImpl.mUpdated);
            encoder.writeOptionalString(geolocationDescriptorImpl.mLocalMapPath);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            double longitude = decoder.readDouble();
            double latitude = decoder.readDouble();
            double altitude = decoder.readDouble();
            double mapLongitudeDelta = decoder.readDouble();
            double mapLatitudeDelta = decoder.readDouble();
            boolean updated = decoder.readBoolean();
            String localMapPath = decoder.readOptionalString();

            return new GeolocationDescriptorImpl(descriptorImpl, longitude, latitude, altitude, mapLongitudeDelta, mapLatitudeDelta, updated, localMapPath);
        }

        @NonNull
        public Object deserialize(@NonNull Decoder decoder, @NonNull UUID twincodeOutboundId, long sequenceId, long createdTimestamp) throws SerializerException {

            long expireTimeout = decoder.readLong();
            UUID sendTo = decoder.readOptionalUUID();
            DescriptorId replyTo = readOptionalDescriptorId(decoder);

            double longitude = decoder.readDouble();
            double latitude = decoder.readDouble();
            double altitude = decoder.readDouble();
            double mapLongitudeDelta = decoder.readDouble();
            double mapLatitudeDelta = decoder.readDouble();
            boolean updated = decoder.readBoolean();
            String localMapPath = decoder.readOptionalString();

            return new GeolocationDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, createdTimestamp, 0, longitude, latitude, altitude, mapLongitudeDelta, mapLatitudeDelta, updated, localMapPath);
        }
    }

    static final GeolocationDescriptorImplSerializer_2 SERIALIZER_2 = new GeolocationDescriptorImplSerializer_2();

    static final int SCHEMA_VERSION_1 = 1;

    static class GeolocationDescriptorImplSerializer_1 extends DescriptorImplSerializer_3 {

        GeolocationDescriptorImplSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, ObjectDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            GeolocationDescriptorImpl geolocationDescriptorImpl = (GeolocationDescriptorImpl) object;
            encoder.writeDouble(geolocationDescriptorImpl.mLongitude);
            encoder.writeDouble(geolocationDescriptorImpl.mLatitude);
            encoder.writeDouble(geolocationDescriptorImpl.mAltitude);
            encoder.writeDouble(geolocationDescriptorImpl.mMapLongitudeDelta);
            encoder.writeDouble(geolocationDescriptorImpl.mMapLatitudeDelta);
            encoder.writeBoolean(geolocationDescriptorImpl.mUpdated);
            encoder.writeOptionalString(geolocationDescriptorImpl.mLocalMapPath);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            double longitude = decoder.readDouble();
            double latitude = decoder.readDouble();
            double altitude = decoder.readDouble();
            double mapLongitudeDelta = decoder.readDouble();
            double mapLatitudeDelta = decoder.readDouble();
            boolean updated = decoder.readBoolean();
            String localMapPath = decoder.readOptionalString();

            return new GeolocationDescriptorImpl(descriptorImpl, longitude, latitude, altitude, mapLongitudeDelta, mapLatitudeDelta, updated, localMapPath);
        }
    }

    static final GeolocationDescriptorImplSerializer_1 SERIALIZER_1 = new GeolocationDescriptorImplSerializer_1();

    private double mLongitude;
    private double mLatitude;
    private double mAltitude;
    private double mMapLongitudeDelta;
    private double mMapLatitudeDelta;
    private boolean mUpdated;
    private String mLocalMapPath;

    GeolocationDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                              @Nullable ConversationService.DescriptorId replyTo, long createdTimestamp, long sentTimestamp,
                              double longitude, double latitude, double altitude,
                              double mapLongitudeDelta, double mapLatitudeDelta, boolean updated, @Nullable String localMapPath) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, createdTimestamp, sentTimestamp);

        if (DEBUG) {
            Log.d(LOG_TAG, "GeolocationDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId
                    + " longitude=" + longitude + " latitude=" + latitude + " altitude=" + altitude
                    + " mapLongitudeDelta=" + mapLatitudeDelta + " mapLatitudeDelta=" + mapLatitudeDelta);
        }

        mLongitude = longitude;
        mLatitude = latitude;
        mAltitude = altitude;
        mMapLongitudeDelta = mapLongitudeDelta;
        mMapLatitudeDelta = mapLatitudeDelta;
        mLocalMapPath = localMapPath;
        mUpdated = updated;
    }

    GeolocationDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, long expireTimeout, double longitude, double latitude, double altitude,
                              double mapLongitudeDelta, double mapLatitudeDelta) {

        super(descriptorId, cid, expireTimeout, null, null);

        if (DEBUG) {
            Log.d(LOG_TAG, "GeolocationDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid
                    + " expireTimeout=" + expireTimeout
                    + " longitude=" + longitude + " latitude=" + latitude + " altitude=" + altitude
                    + " mapLongitudeDelta=" + mapLatitudeDelta + " mapLatitudeDelta=" + mapLatitudeDelta);
        }

        mLongitude = longitude;
        mLatitude = latitude;
        mAltitude = altitude;
        mMapLongitudeDelta = mapLongitudeDelta;
        mMapLatitudeDelta = mapLatitudeDelta;
        mLocalMapPath = null;
        mUpdated = false;
    }

    GeolocationDescriptorImpl(@NonNull ConversationService.DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                              @Nullable ConversationService.DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                              long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                              int flags, String content) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout);

        if (DEBUG) {
            Log.d(LOG_TAG, "GeolocationDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " content=" + content);
        }

        final String[] args = extract(content);
        mLongitude = extractDouble(args, 0, 0);
        mLatitude = extractDouble(args, 1, 0);
        mAltitude = extractDouble(args, 2, 0);
        mMapLongitudeDelta = extractDouble(args, 3, 0);
        mMapLatitudeDelta = extractDouble(args, 4, 0);
        mLocalMapPath = extractString(args, 5, null);
        mUpdated = (flags & FLAG_UPDATED) != 0;
    }

    /*
     * Override Descriptor methods
     */

    @Override
    public Type getType() {

        return Type.GEOLOCATION_DESCRIPTOR;
    }

    @Override
    public double getLongitude() {

        return mLongitude;
    }

    @Override
    public double getLatitude() {

        return mLatitude;
    }

    @Override
    public double getAltitude() {

        return mAltitude;
    }

    @Override
    public double getMapLongitudeDelta() {

        return mMapLongitudeDelta;
    }

    @Override
    public double getMapLatitudeDelta() {

        return mMapLatitudeDelta;
    }

    @Override
    @Nullable
    public String getLocalMapPath() {

        return mLocalMapPath;
    }

    @Override
    public boolean isValidLocalMap() {

        return mLocalMapPath != null && !mUpdated;
    }

    void setLocalMapPath(@Nullable String localMapPath) {

        mLocalMapPath = localMapPath;
        if (localMapPath != null) {
            mUpdated = false;
        }
    }

    void update(double longitude, double latitude, double altitude,
                double mapLongitudeDelta, double mapLatitudeDelta) {

        boolean updated = false;

        if (longitude != mLongitude) {
            mLongitude = longitude;
            updated = true;
        }
        if (latitude != mLatitude) {
            mLatitude = latitude;
            updated = true;
        }
        if (altitude != mAltitude) {
            mAltitude = altitude;
            updated = true;
        }
        if (mapLongitudeDelta != mMapLongitudeDelta) {
            mMapLongitudeDelta = mapLongitudeDelta;
            updated = true;
        }
        if (mapLatitudeDelta != mMapLatitudeDelta) {
            mMapLatitudeDelta = mapLatitudeDelta;
            updated = true;
        }
        if (updated) {
            mUpdated = true;
        }
    }

    boolean update(@NonNull GeolocationDescriptorImpl geolocationDescriptor) {

        boolean updated = false;

        if (geolocationDescriptor.mLongitude != mLongitude) {
            mLongitude = geolocationDescriptor.mLongitude;
            updated = true;
        }
        if (geolocationDescriptor.mLatitude != mLatitude) {
            mAltitude = geolocationDescriptor.mLatitude;
            updated = true;
        }
        if (geolocationDescriptor.mAltitude != mAltitude) {
            mAltitude = geolocationDescriptor.mAltitude;
            updated = true;
        }
        if (geolocationDescriptor.mMapLongitudeDelta != mMapLongitudeDelta) {
            mMapLongitudeDelta = geolocationDescriptor.mMapLongitudeDelta;
            updated = true;
        }
        if (geolocationDescriptor.mMapLatitudeDelta != mMapLatitudeDelta) {
            mMapLatitudeDelta = geolocationDescriptor.mMapLatitudeDelta;
            updated = true;
        }
        if (updated) {
            mUpdated = true;
        }
        return updated;
    }

    @Override
    void delete(@Nullable File filesDir) {
        if (DEBUG) {
            Log.d(LOG_TAG, "delete");
        }

        final String path = getLocalMapPath();
        if (path != null) {
            File file = new File(path);
            Utils.deleteFile(LOG_TAG, file);
        }
    }

    @Override
    @Nullable
    String serialize() {

        return mLongitude + FIELD_SEPARATOR + mLatitude + FIELD_SEPARATOR + mAltitude
                + FIELD_SEPARATOR + mMapLongitudeDelta + FIELD_SEPARATOR + mMapLatitudeDelta
                + FIELD_SEPARATOR + mLocalMapPath;
    }

    @Override
    @Nullable
    DescriptorImpl createForward(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout,
                                 @Nullable UUID sendTo, boolean copyAllowed) {

        return new GeolocationDescriptorImpl(descriptorId, conversationId, expireTimeout,
                sendTo, null, this, copyAllowed);
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" longitude=");
            stringBuilder.append(mLongitude);
            stringBuilder.append(" latitude=");
            stringBuilder.append(mLatitude);
            stringBuilder.append(" altitude=");
            stringBuilder.append(mAltitude);
            stringBuilder.append(" mapLongitudeDelta=");
            stringBuilder.append(mMapLongitudeDelta);
            stringBuilder.append(" mMapLatitudeDelta=");
            stringBuilder.append(mMapLatitudeDelta);
            stringBuilder.append(" localMapPath=");
            stringBuilder.append(mLocalMapPath);
            stringBuilder.append("\n");
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {

            stringBuilder.append("GeolocationDescriptorImpl\n");
            appendTo(stringBuilder);
        }
        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private GeolocationDescriptorImpl(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout, @Nullable UUID sendTo,
                                      @Nullable DescriptorId replyTo, @NonNull GeolocationDescriptorImpl source, boolean copyAllowed) {
        super(descriptorId, conversationId, expireTimeout, sendTo, replyTo);

        if (DEBUG) {
            Log.d(LOG_TAG, "GeolocationDescriptorImpl: descriptorId=" + descriptorId + " conversationId=" + conversationId
                    + " source=" + source);
        }

        mLongitude = source.mLongitude;
        mLatitude = source.mLatitude;
        mAltitude = source.mAltitude;
        mMapLongitudeDelta = source.mMapLongitudeDelta;
        mMapLatitudeDelta = source.mMapLatitudeDelta;
        mLocalMapPath = null;
        mUpdated = false;
    }

    private GeolocationDescriptorImpl(@NonNull DescriptorImpl descriptorImpl, double longitude, double latitude,
                                      double altitude, double mapLongitudeDelta, double mapLatitudeDelta,
                                      boolean updated, @Nullable String localMapPath) {

        super(descriptorImpl);

        if (DEBUG) {
            Log.d(LOG_TAG, "GeolocationDescriptorImpl: descriptorImpl=" + descriptorImpl
                    + " longitude=" + longitude + " latitude=" + latitude + " altitude=" + altitude
                    + " mapLongitudeDelta= " + mapLongitudeDelta + " mapLatitudeDelta=" + mapLatitudeDelta
                    + " updated=" + updated + " localMapPath=" + localMapPath);
        }

        mLongitude = longitude;
        mLatitude = latitude;
        mAltitude = altitude;
        mMapLongitudeDelta = mapLongitudeDelta;
        mMapLatitudeDelta = mapLatitudeDelta;
        mLocalMapPath = localMapPath;
        mUpdated = updated;
    }
}
