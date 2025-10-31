/*
 *  Copyright (c) 2016-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributor: Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "type":"enum",
 *  "name":"ErrorIQType",
 *  "namespace":"org.twinlife.schemas",
 *  "symbols" : ["CANCEL", "CONTINUE", "MODIFY", "AUTH", "WAIT"]
 * }
 *
 * {
 *  "type":"record",
 *  "name":"ErrorIQ",
 *  "namespace":"org.twinlife.schemas",
 *  "super":"org.twinlife.schemas.IQ"
 *  "fields":
 *  [
 *   {"name":"errorType", "type":"org.twinlife.schemas.ErrorIQType"}
 *   {"name":"condition", "type":"string"}
 *   {"name":"requestSchemaId", "type":"uuid"},
 *   {"name":"requestSchemaVersion", "type":"int"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class ErrorIQ extends IQ {

    public static final UUID SCHEMA_ID = UUID.fromString("982ca04e-5b94-4382-acda-b710973b9a04");
    public static final int SCHEMA_VERSION = 1;

    public enum Type {
        CANCEL, CONTINUE, MODIFY, AUTH, WAIT
    }

    public static final String BAD_REQUEST = "bad-request";
    public static final String FEATURE_NOT_IMPLEMENTED = "feature-not-implemented";

    public static class ErrorIQSerializer extends IQSerializer {

        @SuppressWarnings("SameParameterValue")
        ErrorIQSerializer(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ErrorIQ errorIQ = (ErrorIQ) object;
            switch (errorIQ.mErrorType) {
                case CANCEL:
                    encoder.writeEnum(0);
                    break;
                case CONTINUE:
                    encoder.writeEnum(1);
                    break;
                case MODIFY:
                    encoder.writeEnum(2);
                    break;
                case AUTH:
                    encoder.writeEnum(3);
                    break;
                case WAIT:
                    encoder.writeEnum(4);
                    break;
            }
            encoder.writeString(errorIQ.mCondition);
            encoder.writeUUID(errorIQ.mRequestSchemaId);
            encoder.writeInt(errorIQ.mRequestSchemaVersion);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            IQ iq = (IQ) super.deserialize(serializerFactory, decoder);

            int value = decoder.readEnum();
            Type errorType;
            switch (value) {
                case 0:
                    errorType = Type.CANCEL;
                    break;
                case 1:
                    errorType = Type.CONTINUE;
                    break;
                case 2:
                    errorType = Type.MODIFY;
                    break;
                case 3:
                    errorType = Type.AUTH;
                    break;
                case 4:
                    errorType = Type.WAIT;
                    break;
                default:
                    throw new SerializerException();
            }
            String condition = decoder.readString();
            UUID requestSchemaId = decoder.readUUID();
            int requestSchemaVersion = decoder.readInt();

            return new ErrorIQ(iq, errorType, condition, requestSchemaId, requestSchemaVersion);
        }

        private ErrorIQSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, ErrorIQ.class);
        }
    }

    public static final ErrorIQSerializer SERIALIZER = new ErrorIQSerializer();

    private final Type mErrorType;
    private final String mCondition;
    private final UUID mRequestSchemaId;
    private final int mRequestSchemaVersion;

    public ErrorIQ(String id, String from, String to, ErrorIQ.Type errorType, String condition, UUID requestSchemaId, int requestSchemaVersion) {

        super(id, from, to, IQ.Type.ERROR);

        mErrorType = errorType;
        mCondition = condition;
        mRequestSchemaId = requestSchemaId;
        mRequestSchemaVersion = requestSchemaVersion;
    }

    ErrorIQ(ErrorIQ errorIQ) {

        super(errorIQ);

        mErrorType = errorIQ.mErrorType;
        mCondition = errorIQ.mCondition;
        mRequestSchemaId = errorIQ.mRequestSchemaId;
        mRequestSchemaVersion = errorIQ.mRequestSchemaVersion;
    }

    void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" errorType=");
            stringBuilder.append(mErrorType);
            stringBuilder.append("\n");
            stringBuilder.append(" condition=");
            stringBuilder.append(mCondition);
            stringBuilder.append("\n");
            stringBuilder.append(" requestSchemaId=");
            stringBuilder.append(mRequestSchemaId);
            stringBuilder.append("\n");
            stringBuilder.append(" requestSchemaVersion=");
            stringBuilder.append(mRequestSchemaVersion);
            stringBuilder.append("\n");
        }
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("ErrorIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    //
    // Private Methods
    //

    private ErrorIQ(IQ iq, Type errorType, String condition, UUID requestSchemaId, int requestSchemaVersion) {

        super(iq);

        mErrorType = errorType;
        mCondition = condition;
        mRequestSchemaId = requestSchemaId;
        mRequestSchemaVersion = requestSchemaVersion;
    }
}
