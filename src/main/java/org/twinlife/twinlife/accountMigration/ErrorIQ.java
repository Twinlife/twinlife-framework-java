/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.AccountMigrationService.ErrorCode;

import java.util.UUID;

/**
 * Error message IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"42705574-8e05-47fd-9742-ffd86a923cea",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ErrorIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"errorCode", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */
class ErrorIQ extends BinaryPacketIQ {

    static class ErrorIQSerializer extends BinaryPacketIQSerializer {

        ErrorIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, ErrorIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ErrorIQ errorIQ = (ErrorIQ) object;

            switch (errorIQ.errorCode) {
                default:
                case INTERNAL_ERROR:
                    encoder.writeEnum(1);
                    break;

                case NO_SPACE_LEFT:
                    encoder.writeEnum(2);
                    break;

                case IO_ERROR:
                    encoder.writeEnum(3);
                    break;

                case REVOKED:
                    encoder.writeEnum(4); // Note: will never be sent in the IQ.
                    break;

                case BAD_PEER_VERSION:
                    encoder.writeEnum(5); // Note: will never be sent in the IQ.
                    break;

                case BAD_DATABASE:
                    encoder.writeEnum(6);
                    break;

                case SECURE_STORE_ERROR:
                    encoder.writeEnum(7);
                    break;
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            ErrorCode errorCode;
            switch (decoder.readEnum()) {
                default:
                case 1:
                    errorCode = ErrorCode.INTERNAL_ERROR;
                    break;

                case 2:
                    errorCode = ErrorCode.NO_SPACE_LEFT;
                    break;

                case 3:
                    errorCode = ErrorCode.IO_ERROR;
                    break;

                case 4:
                    errorCode = ErrorCode.REVOKED;
                    break;

                case 5:
                    errorCode = ErrorCode.BAD_PEER_VERSION;
                    break;

                case 6:
                    errorCode = ErrorCode.BAD_DATABASE;
                    break;

                case 7:
                    errorCode = ErrorCode.SECURE_STORE_ERROR;
                    break;
            }

            return new ErrorIQ(this, serviceRequestIQ, errorCode);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new ErrorIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final ErrorCode errorCode;

    ErrorIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull ErrorCode errorCode) {

        super(serializer, requestId);

        this.errorCode = errorCode;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" errorCode=");
            stringBuilder.append(errorCode);
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("ErrorIQ\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private ErrorIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ, @NonNull ErrorCode errorCode) {

        super(serializer, serviceRequestIQ);

        this.errorCode = errorCode;
    }
}
