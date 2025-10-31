/*
 *  Copyright (c) 2015-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "type":"enum",
 *  "name":"IQType",
 *  "namespace":"org.twinlife.schemas",
 *  "symbols" : ["SET", "GET", "RESULT", "ERROR"]
 * }
 *
 * {
 *  "type":"record",
 *  "name":"IQ",
 *  "namespace":"org.twinlife.schemas",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"id", "type":"string"}
 *   {"name":"from", "type":"string"}
 *   {"name":"to", "type":"string"}
 *   {"name":"type", "type":"org.twinlife.schemas.IQType"}
 *  ]
 * }
 *
 * </pre>
 */

public class IQ {

    private static final UUID IQ_SCHEMA_ID = UUID.fromString("7866f017-62b6-4c3f-8c55-711f48aae233");
    private static final int IQ_SCHEMA_VERSION = 1;

    public enum Type {
        SET, GET, RESULT, ERROR
    }

    public static class IQSerializer extends Serializer {

        IQSerializer(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            IQ iq = (IQ) object;
            encoder.writeString(iq.mId);
            encoder.writeString(iq.mFrom);
            encoder.writeString(iq.mTo);
            switch (iq.mType) {
                case SET:
                    encoder.writeEnum(0);
                    break;
                case GET:
                    encoder.writeEnum(1);
                    break;
                case RESULT:
                    encoder.writeEnum(2);
                    break;
                case ERROR:
                    encoder.writeEnum(3);
                    break;
            }
        }

        @NonNull
        @Override
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            String id = decoder.readString();
            String from = decoder.readString();
            String to = decoder.readString();
            int value = decoder.readEnum();
            Type type;
            switch (value) {
                case 0:
                    type = Type.SET;
                    break;
                case 1:
                    type = Type.GET;
                    break;
                case 2:
                    type = Type.RESULT;
                    break;
                case 3:
                    type = Type.ERROR;
                    break;
                default:
                    throw new SerializerException();
            }

            return new IQ(id, from, to, type);
        }
    }

    public static final IQSerializer IQ_SERIALIZER = new IQSerializer(IQ_SCHEMA_ID, IQ_SCHEMA_VERSION, IQ.class);

    private static final Random RANDOM;
    private static final AtomicInteger ID;

    private final String mId;
    private final String mFrom;
    private final String mTo;
    private final Type mType;

    static {
        RANDOM = new Random();
        ID = new AtomicInteger();
    }

    IQ(String id, String from, String to, Type type) {

        mId = id;
        mFrom = from;
        mTo = to;
        mType = type;
    }

    IQ(String from, String to, @SuppressWarnings("SameParameterValue") Type type) {

        mId = Integer.toHexString(RANDOM.nextInt()) + Integer.toHexString(ID.getAndIncrement());
        mFrom = from;
        mTo = to;
        mType = type;
    }

    IQ(IQ iq) {

        mId = iq.mId;
        mFrom = iq.mFrom;
        mTo = iq.mTo;
        mType = iq.mType;
    }

    public String getId() {

        return mId;
    }

    public String getFrom() {

        return mFrom;
    }

    @SuppressWarnings("unused")
    public String getTo() {

        return mTo;
    }

    public Type getType() {

        return mType;
    }

    void appendTo(@NonNull StringBuilder stringBuilder) {

        stringBuilder.append(" id=");
        stringBuilder.append(mId);
        stringBuilder.append("\n");
        stringBuilder.append(" from=");
        stringBuilder.append(mFrom);
        stringBuilder.append("\n");
        stringBuilder.append(" to=");
        stringBuilder.append(mTo);
        stringBuilder.append("\n");
        stringBuilder.append(" type=");
        stringBuilder.append(mType);
        stringBuilder.append("\n");
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("IQ\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }
}
