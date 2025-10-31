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
 *  "name":"ServiceResultIQ",
 *  "namespace":"org.twinlife.schemas",
 *  "super":"org.twinlife.schemas.ResultIQ"
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
 *  "name":"ServiceResultIQ",
 *  "namespace":"org.twinlife.schemas",
 *  "super":"org.twinlife.schemas.ResultIQ"
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

public class ServiceResultIQ extends ResultIQ {

    public static class ServiceResultIQSerializer extends ResultIQSerializer {

        public ServiceResultIQSerializer(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ServiceResultIQ serviceResultIQ = (ServiceResultIQ) object;
            encoder.writeLong(serviceResultIQ.mRequestId);
            encoder.writeString(serviceResultIQ.mService);
            encoder.writeString(serviceResultIQ.mAction);
            encoder.writeInt(serviceResultIQ.mMajorVersion);
            encoder.writeInt(serviceResultIQ.mMinorVersion);
        }

        @NonNull
        @Override
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            ResultIQ resultIQ = (ResultIQ) super.deserialize(serializerFactory, decoder);
            long requestId = decoder.readLong();
            String service = decoder.readString();
            String action = decoder.readString();
            int majorVersion = decoder.readInt();
            int minorVersion = decoder.readInt();

            return new ServiceResultIQ(resultIQ, requestId, service, action, majorVersion, minorVersion);
        }
    }

    public static class ServiceResultIQSerializer_1 extends ResultIQSerializer {

        public ServiceResultIQSerializer_1(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ServiceResultIQ serviceResultIQ = (ServiceResultIQ) object;
            encoder.writeLong(serviceResultIQ.mRequestId);
            encoder.writeString(serviceResultIQ.mService);
            encoder.writeString(serviceResultIQ.mAction);
            encoder.writeString("1.0.0");
        }

        @NonNull
        @Override
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            ResultIQ resultIQ = (ResultIQ) super.deserialize(serializerFactory, decoder);
            long requestId = decoder.readLong();
            String service = decoder.readString();
            String action = decoder.readString();
            @SuppressWarnings("unused") String version = decoder.readString();
            int majorVersion = 1;
            int minorVersion = 0;

            return new ServiceResultIQ(resultIQ, requestId, service, action, majorVersion, minorVersion);
        }
    }

    private final long mRequestId;
    private final String mService;
    private final String mAction;
    private final int mMajorVersion;
    private final int mMinorVersion;

    protected ServiceResultIQ(String id, String from, String to, long requestId, @SuppressWarnings("SameParameterValue") String service, String action,
                              int majorVersion, int minorVersion) {

        super(id, from, to);

        mRequestId = requestId;
        mService = service;
        mAction = action;
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
    }

    protected ServiceResultIQ(ServiceResultIQ serviceResultIQ) {

        super(serviceResultIQ);

        mRequestId = serviceResultIQ.mRequestId;
        mService = serviceResultIQ.mService;
        mAction = serviceResultIQ.mAction;
        mMajorVersion = serviceResultIQ.mMajorVersion;
        mMinorVersion = serviceResultIQ.mMinorVersion;
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
        stringBuilder.append("ServiceResultIQ:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private ServiceResultIQ(ResultIQ replyIQ, long requestId, String service, String action, int majorVersion, int minorVersion) {

        super(replyIQ);

        mRequestId = requestId;
        mService = service;
        mAction = action;
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
    }
}
