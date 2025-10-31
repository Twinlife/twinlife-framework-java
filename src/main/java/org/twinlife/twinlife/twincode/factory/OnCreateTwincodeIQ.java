/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.factory;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Create twincode response IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"6c0442f5-b0bf-4b7e-9ae5-40ad720b1f71",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"CreateTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"factoryTwincodeId", "type":"uuid"}
 *     {"name":"inbountTwincodeId", "type":"uuid"}
 *     {"name":"outboundTwincodeId", "type":"uuid"}
 *     {"name":"switchTwincodeId", "type":"uuid"}
 *  ]
 * }
 * </pre>
 */
public class OnCreateTwincodeIQ extends BinaryPacketIQ {

    static class OnCreateTwincodeIQSerializer extends BinaryPacketIQSerializer {

        OnCreateTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnCreateTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            throw new SerializerException();
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID factoryTwincodeId = decoder.readUUID();
            UUID inboundTwincodeId = decoder.readUUID();
            UUID outboundTwincodeId = decoder.readUUID();
            UUID switchTwincodeId = decoder.readUUID();

            return new OnCreateTwincodeIQ(this, serviceRequestIQ.getRequestId(), factoryTwincodeId,
                    inboundTwincodeId, outboundTwincodeId, switchTwincodeId);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnCreateTwincodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final UUID factoryTwincodeId;
    @NonNull
    private final UUID inboundTwincodeId;
    @NonNull
    private final UUID outboundTwincodeId;
    @NonNull
    private final UUID switchTwincodeId;

    @NonNull
    public UUID getFactoryTwincodeId() {

        return factoryTwincodeId;
    }

    @NonNull
    public UUID getInboundTwincodeId() {

        return inboundTwincodeId;
    }

    @NonNull
    public UUID getOutboundTwincodeId() {

        return outboundTwincodeId;
    }

    @NonNull
    public UUID getSwitchTwincodeId() {

        return switchTwincodeId;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" factoryTwincodeId=");
            stringBuilder.append(factoryTwincodeId);
            stringBuilder.append(" inboundTwincodeId=");
            stringBuilder.append(inboundTwincodeId);
            stringBuilder.append(" outboundTwincodeId=");
            stringBuilder.append(outboundTwincodeId);
            stringBuilder.append(" switchTwincodeId=");
            stringBuilder.append(switchTwincodeId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnCreateTwincodeIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public OnCreateTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                              @NonNull UUID factoryTwincodeId,
                              @NonNull UUID inboundTwincodeId,
                              @NonNull UUID outboundTwincodeId,
                              @NonNull UUID switchTwincodeId) {
        super(serializer, requestId);

        this.factoryTwincodeId = factoryTwincodeId;
        this.inboundTwincodeId = inboundTwincodeId;
        this.outboundTwincodeId = outboundTwincodeId;
        this.switchTwincodeId = switchTwincodeId;
    }
}
