/*
 *  Copyright (c) 2020-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Base class of binary packets.
 */
public class BinaryPacketIQ {

    public static final int SERIALIZER_BUFFER_DEFAULT_SIZE = 1024;

    public static class BinaryPacketIQSerializer extends Serializer {

        public BinaryPacketIQSerializer(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) object;
            encoder.writeLong(serviceRequestIQ.mRequestId);
        }

        protected void serialize(@NonNull Encoder encoder, @Nullable List<BaseService.AttributeNameValue> attributes) throws SerializerException {

            encoder.writeAttributes(attributes);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            long requestId = decoder.readLong();

            return new BinaryPacketIQ(this, requestId);
        }

        @NonNull
        protected List<BaseService.AttributeNameValue> deserializeAttributeList(@NonNull Decoder decoder) throws SerializerException {

            List<BaseService.AttributeNameValue> attributes = decoder.readAttributes();
            if (attributes == null) {
                return new ArrayList<>();
            } else {
                return attributes;
            }
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createDefaultSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new BinaryPacketIQSerializer(schemaId, schemaVersion, BinaryPacketIQ.class);
    }

    protected final long mRequestId;
    @NonNull
    protected final BinaryPacketIQSerializer mSerializer;

    public BinaryPacketIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId) {

        mSerializer = serializer;
        mRequestId = requestId;
    }

    public BinaryPacketIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ) {

        mSerializer = serializer;
        mRequestId = serviceRequestIQ.mRequestId;
    }

    public long getRequestId() {

        return mRequestId;
    }

    protected int getBufferSize() {

        return SERIALIZER_BUFFER_DEFAULT_SIZE;
    }

    public byte[] serializeCompact(SerializerFactory serializerFactory) throws SerializerException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(getBufferSize());
        final BinaryEncoder binaryEncoder = new BinaryCompactEncoder(outputStream);

        mSerializer.serialize(serializerFactory, binaryEncoder, this);
        return outputStream.toByteArray();
    }

    public byte[] serialize(SerializerFactory serializerFactory) throws SerializerException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(getBufferSize());
        final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);

        mSerializer.serialize(serializerFactory, binaryEncoder, this);
        return outputStream.toByteArray();
    }

    public byte[] serializeWithPadding(SerializerFactory serializerFactory, boolean withLeadingPadding) throws SerializerException {
        final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(getBufferSize());
        final BinaryEncoder binaryEncoder;
        if (withLeadingPadding) {
            binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);
        } else {
            binaryEncoder = new BinaryCompactEncoder(outputStream);
        }
        mSerializer.serialize(serializerFactory, binaryEncoder, this);
        return outputStream.toByteArray();
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        stringBuilder.append(" requestId=");
        stringBuilder.append(mRequestId);
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("BinaryPacketIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }
}
