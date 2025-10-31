/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.factory;

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
 * Create twincode IQ.
 * <p>
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"8184d22a-980c-40a3-90c3-02ff4732e7b9",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"CreateTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"createOptions", "type": "int"}
 *     {"name":"factoryAttributes", [
 *      {"name":"name", "type": "string"}
 *      {"name":"type", ["long", "string", "uuid"]}
 *      {"name":"value", "type": ["long", "string", "uuid"]}
 *     ]}
 *     {"name":"inboundAttributes", [
 *      {"name":"name", "type": "string"}
 *      {"name":"type", ["long", "string", "uuid"]}
 *      {"name":"value", "type": ["long", "string", "uuid"]}
 *     ]}
 *     {"name":"outboundAttributes", [
 *      {"name":"name", "type": "string"}
 *      {"name":"type", ["long", "string", "uuid"]}
 *      {"name":"value", "type": ["long", "string", "uuid"]}
 *     ]}
 *     {"name":"switchAttributes", [
 *      {"name":"name", "type": "string"}
 *      {"name":"type", ["long", "string", "uuid"]}
 *      {"name":"value", "type": ["long", "string", "uuid"]}
 *     ]},
 *     {"name":"schemaId", [null, "uuid"]}
 *  ]
 * }
 * </pre>
 * Schema version 1 (REMOVED 2024-02-02 after 22.x)
 */
public class CreateTwincodeIQ extends BinaryPacketIQ {

    static class CreateTwincodeIQSerializer extends BinaryPacketIQSerializer {

        CreateTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, CreateTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            CreateTwincodeIQ createTwincodeIQ = (CreateTwincodeIQ) object;

            encoder.writeInt(createTwincodeIQ.createOptions);
            serialize(encoder, createTwincodeIQ.factoryAttributes);
            serialize(encoder, createTwincodeIQ.inboundAttributes);
            serialize(encoder, createTwincodeIQ.outboundAttributes);
            serialize(encoder, createTwincodeIQ.switchAttributes);
            encoder.writeOptionalUUID(createTwincodeIQ.schemaId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new CreateTwincodeIQSerializer(schemaId, schemaVersion);
    }

    private final int createOptions;
    @NonNull
    private final List<AttributeNameValue> factoryAttributes;
    @Nullable
    private final List<AttributeNameValue> inboundAttributes;
    @Nullable
    private final List<AttributeNameValue> outboundAttributes;
    @Nullable
    private final List<AttributeNameValue> switchAttributes;
    @Nullable
    private final UUID schemaId;

    public static final int BIND_INBOUND_OPTION = 0x01;

    public int getCreateOptions() {

        return createOptions;
    }

    @NonNull
    public List<AttributeNameValue> getFactoryAttributes() {

        return factoryAttributes;
    }

    @Nullable
    public List<AttributeNameValue> getInboundAttributes() {

        return inboundAttributes;
    }

    @Nullable
    public List<AttributeNameValue> getOutboundAttributes() {

        return outboundAttributes;
    }

    @Nullable
    public List<AttributeNameValue> getSwitchAttributes() {

        return switchAttributes;
    }

    @Nullable
    public UUID getSchemaId() {

        return schemaId;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" createOptions=");
            stringBuilder.append(createOptions);
            stringBuilder.append(" factoryAttributes=");
            stringBuilder.append(factoryAttributes);
            stringBuilder.append(" inboundAttributes=");
            stringBuilder.append(inboundAttributes);
            stringBuilder.append(" outboundAttributes=");
            stringBuilder.append(outboundAttributes);
            stringBuilder.append(" switchAttributes=");
            stringBuilder.append(switchAttributes);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("CreateTwincodeIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public CreateTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                            int createOptions,
                            @NonNull List<AttributeNameValue> factoryAttributes,
                            @Nullable List<AttributeNameValue> inboundAttributes,
                            @Nullable List<AttributeNameValue> outboundAttributes,
                            @Nullable List<AttributeNameValue> switchAttributes,
                            @Nullable UUID schemaId) {
        super(serializer, requestId);

        this.createOptions = createOptions;
        this.factoryAttributes = factoryAttributes;
        this.inboundAttributes = inboundAttributes;
        this.outboundAttributes = outboundAttributes;
        this.switchAttributes = switchAttributes;
        this.schemaId = schemaId;
    }
}
