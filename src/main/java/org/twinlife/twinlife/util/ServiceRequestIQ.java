/*
 *  Copyright (c) 2015-2018 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

/*
 * <pre>
 *
 * Schema version 2
 *  Date: 2016/09/08
 *
 * {
 *  "type":"record",
 *  "name":"ServiceRequestIQ",
 *  "namespace":"org.twinlife.schemas",
 *  "super":"org.twinlife.schemas.RequestIQ"
 *  "fields":
 *  [
 *   {"name":"requestId", "type":"long"}
 *   {"name":"service", "type":"string"},
 *   {"name":"action", "type":"string"}
 *   {"name":"majorVersion", "type":"int"}
 *   {"name":"minorVersion", "type":"int"}
 *  ]
 * }
 *
 *
 * Schema version 1
 *
 * {
 *  "type":"record",
 *  "name":"ServiceRequestIQ",
 *  "namespace":"org.twinlife.schemas",
 *  "super":"org.twinlife.schemas.RequestIQ"
 *  "fields":
 *  [
 *   {"name":"requestId", "type":"long"}
 *   {"name":"service", "type":"string"},
 *   {"name":"action", "type":"string"}
 *   {"name":"version", "type":"string"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class ServiceRequestIQ extends RequestIQ {
    private static final UUID SCHEMA_ID = UUID.fromString("8eea0b0a-e061-4cf4-b43c-f33cffea5eec");

    public static class ServiceRequestIQSerializer extends RequestIQSerializer {

        public ServiceRequestIQSerializer(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) object;
            encoder.writeLong(serviceRequestIQ.mRequestId);
            encoder.writeString(serviceRequestIQ.mService);
            encoder.writeString(serviceRequestIQ.mAction);
            encoder.writeInt(serviceRequestIQ.mMajorVersion);
            encoder.writeInt(serviceRequestIQ.mMinorVersion);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            RequestIQ requestIQ = (RequestIQ) super.deserialize(serializerFactory, decoder);

            long requestId = decoder.readLong();
            String service = decoder.readString();
            String action = decoder.readString();
            int majorVersion = decoder.readInt();
            int minorVersion = decoder.readInt();

            return new ServiceRequestIQ(requestIQ, requestId, service, action, majorVersion, minorVersion);
        }
    }
    public static final ServiceRequestIQSerializer SERIALIZER = new ServiceRequestIQSerializer(SCHEMA_ID, 1, ServiceRequestIQ.class);

    public static class ServiceRequestIQSerializer_1 extends RequestIQSerializer {

        public ServiceRequestIQSerializer_1(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) object;
            encoder.writeLong(serviceRequestIQ.mRequestId);
            encoder.writeString(serviceRequestIQ.mService);
            encoder.writeString(serviceRequestIQ.mAction);
            encoder.writeString("1.0.0");
        }

        @NonNull
        @Override
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            RequestIQ requestIQ = (RequestIQ) super.deserialize(serializerFactory, decoder);
            long requestId = decoder.readLong();
            String service = decoder.readString();
            String action = decoder.readString();
            @SuppressWarnings("unused") String version = decoder.readString();
            int majorVersion = 1;
            int minorVersion = 0;

            return new ServiceRequestIQ(requestIQ, requestId, service, action, majorVersion, minorVersion);
        }
    }

    private final long mRequestId;
    private final String mService;
    private final String mAction;
    private final int mMajorVersion;
    private final int mMinorVersion;

    protected ServiceRequestIQ(String from, String to, long requestId, @SuppressWarnings("SameParameterValue") String service, String action, int majorVersion, int minorVersion) {

        super(from, to);

        mRequestId = requestId;
        mService = service;
        mAction = action;
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
    }

    protected ServiceRequestIQ(ServiceRequestIQ serviceRequestIQ) {

        super(serviceRequestIQ);

        mRequestId = serviceRequestIQ.mRequestId;
        mService = serviceRequestIQ.mService;
        mAction = serviceRequestIQ.mAction;
        mMajorVersion = serviceRequestIQ.mMajorVersion;
        mMinorVersion = serviceRequestIQ.mMinorVersion;
    }

    public long getRequestId() {

        return mRequestId;
    }

    public String getService() {

        return mService;
    }

    public String getAction() {

        return mAction;
    }

    public int getMajorVersion() {

        return mMajorVersion;
    }

    public int getMinorVersion() {

        return mMinorVersion;
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" requestId=");
        stringBuilder.append(mRequestId);
        stringBuilder.append("\n");
        stringBuilder.append(" service=");
        stringBuilder.append(mService);
        stringBuilder.append("\n");
        stringBuilder.append(" action=");
        stringBuilder.append(mAction);
        stringBuilder.append("\n");
        stringBuilder.append(" majorVersion=");
        stringBuilder.append(mMajorVersion);
        stringBuilder.append("\n");
        stringBuilder.append(" minorVersion=");
        stringBuilder.append(mMinorVersion);
        stringBuilder.append("\n");
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ServiceRequestIQ:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private ServiceRequestIQ(RequestIQ RequestIQ, long requestId, String service, String action, int majorVersion, int minorVersion) {

        super(RequestIQ);

        mRequestId = requestId;
        mService = service;
        mAction = action;
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
    }
}
