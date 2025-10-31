/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.List;
import java.util.UUID;

/**
 * Invoke twincode IQ.
 * <p>
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"c74e79e6-5157-4fb4-bad8-2de545711fa0",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"InvokeTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"invocationOptions", "type":"int"},
 *     {"name":"invocationId", [null, "type":"uuid"]},
 *     {"name":"twincodeId", "type":"uuid"},
 *     {"name":"actionName", "type": "string"}
 *     {"name":"attributes", [
 *      {"name":"name", "type": "string"}
 *      {"name":"type", ["long", "string", "uuid"]}
 *      {"name":"value", "type": ["long", "string", "uuid"]}
 *     ]},
 *     {"name": "data": [null, "type":"bytes"]}
 *     {"name": "deadline", "type":"long"}
 *  ]
 * }
 * </pre>
 * Schema version 1 (REMOVED 2024-02-02 after 22.x)
 */
public class InvokeTwincodeIQ extends BinaryPacketIQ {

    static class InvokeTwincodeIQSerializer extends BinaryPacketIQSerializer {

        InvokeTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, InvokeTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            InvokeTwincodeIQ invokeTwincodeIQ = (InvokeTwincodeIQ) object;

            encoder.writeInt(invokeTwincodeIQ.invocationOptions);
            encoder.writeOptionalUUID(invokeTwincodeIQ.invocationId);
            encoder.writeUUID(invokeTwincodeIQ.twincodeId);
            encoder.writeString(invokeTwincodeIQ.actionName);
            serialize(encoder, invokeTwincodeIQ.attributes);
            if (invokeTwincodeIQ.data == null) {
                encoder.writeZero();
            } else {
                encoder.writeInt(1);
                encoder.writeBytes(invokeTwincodeIQ.data, 0, invokeTwincodeIQ.dataLength);
            }
            encoder.writeLong(invokeTwincodeIQ.deadline);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            int invocationOptions = decoder.readInt();
            UUID invocationId = decoder.readOptionalUUID();
            UUID twincodeId = decoder.readUUID();
            String actionName = decoder.readString();
            List<AttributeNameValue> attributes = deserializeAttributeList(decoder);
            byte[] data = decoder.readOptionalBytes(null);
            long deadline = decoder.readLong();

            return new InvokeTwincodeIQ(this, serviceRequestIQ.getRequestId(), invocationOptions, invocationId, twincodeId,
                    actionName, attributes, data, 0, deadline);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new InvokeTwincodeIQSerializer(schemaId, schemaVersion);
    }

    private final int invocationOptions;
    @Nullable
    private final UUID invocationId;
    @NonNull
    private final UUID twincodeId;
    @NonNull
    private final String actionName;
    @Nullable
    private final List<AttributeNameValue> attributes;
    private final byte[] data;
    private final long deadline;
    private final int dataLength;

    public int getInvocationOptions() {

        return invocationOptions;
    }

    @NonNull
    public UUID getTwincodeId() {

        return twincodeId;
    }

    @Nullable
    public UUID getInvocationId() {

        return invocationId;
    }

    @NonNull
    public String getActionName() {

        return actionName;
    }

    @Nullable
    public List<AttributeNameValue> getAttributes() {

        return attributes;
    }

    @Nullable
    public byte[] getData() {

        return data;
    }

    public long getDeadline() {

        return deadline;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" invocationOptions=");
            stringBuilder.append(invocationOptions);
            stringBuilder.append(" twincodeId=");
            stringBuilder.append(twincodeId);
            stringBuilder.append(" invocationId=");
            stringBuilder.append(invocationId);
            stringBuilder.append(" action=");
            stringBuilder.append(actionName);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("InvokeTwincodeIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public InvokeTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                            int invocationOptions, @Nullable UUID invocationId, @NonNull UUID twincodeId,
                            @NonNull String actionName, @Nullable List<AttributeNameValue> attributes,
                            @Nullable byte[] data, int dataLength, long deadline) {
        super(serializer, requestId);

        this.invocationOptions = invocationOptions;
        this.invocationId = invocationId;
        this.twincodeId = twincodeId;
        this.actionName = actionName;
        this.attributes = attributes;
        this.data = data;
        this.dataLength = dataLength;
        this.deadline = deadline;
    }
}
