/*
 *  Copyright (c) 2016-2018 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 1
 *  Date: 2016/09/12
 *
 * {
 *  "type":"record",
 *  "name":"ServiceErrorIQ",
 *  "namespace":"org.twinlife.schemas",
 *  "super":"org.twinlife.schemas.ErrorIQ"
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
 * </pre>
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class ServiceErrorIQ extends ErrorIQ {

    public static final UUID SCHEMA_ID = UUID.fromString("6548c8a9-3a68-45da-a26e-e82b1630c321");
    public static final int SCHEMA_VERSION = 1;

    public static class ServiceErrorIQSerializer extends ErrorIQSerializer {

        ServiceErrorIQSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, ServiceErrorIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ServiceErrorIQ serviceErrorIQ = (ServiceErrorIQ) object;
            encoder.writeLong(serviceErrorIQ.mRequestId);
            encoder.writeString(serviceErrorIQ.mService);
            encoder.writeString(serviceErrorIQ.mAction);
            encoder.writeInt(serviceErrorIQ.mMajorVersion);
            encoder.writeInt(serviceErrorIQ.mMinorVersion);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            ErrorIQ iqRequest = (ErrorIQ) super.deserialize(serializerFactory, decoder);
            long requestId = decoder.readLong();
            String service = decoder.readString();
            String action = decoder.readString();
            int majorVersion = decoder.readInt();
            int minorVersion = decoder.readInt();

            return new ServiceErrorIQ(iqRequest, requestId, service, action, majorVersion, minorVersion);
        }
    }

    public static final ServiceErrorIQSerializer SERIALIZER = new ServiceErrorIQSerializer();

    private final long mRequestId;
    private final String mService;
    private final String mAction;
    private final int mMajorVersion;
    private final int mMinorVersion;

    public ServiceErrorIQ(String id, String from, String to, @SuppressWarnings("SameParameterValue") ErrorIQ.Type errorType, @SuppressWarnings("SameParameterValue") String condition,
                          UUID requestSchemaId, int requestSchemaVersion, long requestId, String service, String action, int majorVersion, int minorVersion) {

        super(id, from, to, errorType, condition, requestSchemaId, requestSchemaVersion);

        mRequestId = requestId;
        mService = service;
        mAction = action;
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
    }

    public long getRequestId() {

        return mRequestId;
    }

    void appendTo(@NonNull StringBuilder stringBuilder) {

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
        stringBuilder.append("ServiceErrorIQ:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private ServiceErrorIQ(ErrorIQ errorIQ, long requestId, String service, String action, int majorVersion, int minorVersion) {

        super(errorIQ);

        mRequestId = requestId;
        mService = service;
        mAction = action;
        mMajorVersion = majorVersion;
        mMinorVersion = minorVersion;
    }
}
