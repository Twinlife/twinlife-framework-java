/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * PushTransient IQ.
 * <p>
 * Schema version 3
 *  Date: 2024/09/10
 * <pre>
 * {
 *  "schemaId":"05617876-8419-4240-9945-08bf4106cb72",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"PushTransientIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields":
 *  [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"createdTimestamp", "type":"long"}
 *     {"name":"sentTimestamp", "type":"long"}
 *     {"name":"flags", "type":"int"}
 *     {"name":"object", "type":"Object"}
 *  ]
 * }
 * </pre>
 */
class PushTransientIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("05617876-8419-4240-9945-08bf4106cb72");
    static final int SCHEMA_VERSION_3 = 3;
    static final BinaryPacketIQSerializer IQ_PUSH_TRANSIENT_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_3);

    @NonNull
    final TransientObjectDescriptorImpl transientDescriptorImpl;
    final int flags;

    PushTransientIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                    @NonNull TransientObjectDescriptorImpl transientDescriptorImpl, int flags) {

        super(serializer, requestId);

        this.transientDescriptorImpl = transientDescriptorImpl;
        this.flags = flags;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new PushTransientIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" transientDescriptor=");
            stringBuilder.append(transientDescriptorImpl);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushTransientIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class PushTransientIQSerializer extends BinaryPacketIQSerializer {

        PushTransientIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PushTransientIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PushTransientIQ pushObjectIQ = (PushTransientIQ) object;

            TransientObjectDescriptorImpl transientDescriptor = pushObjectIQ.transientDescriptorImpl;
            encoder.writeUUID(transientDescriptor.getTwincodeOutboundId());
            encoder.writeLong(transientDescriptor.getSequenceId());
            encoder.writeLong(transientDescriptor.getCreatedTimestamp());
            encoder.writeLong(transientDescriptor.getSentTimestamp());
            encoder.writeInt(pushObjectIQ.flags);
            transientDescriptor.serialize(serializerFactory, encoder);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final long createdTimestamp = decoder.readLong();
            final long sentTimestamp = decoder.readLong();
            final int flags = decoder.readInt();

            final UUID schemaId = decoder.readUUID();
            final int schemaVersion = decoder.readInt();
            final Serializer serializer = serializerFactory.getSerializer(schemaId, schemaVersion);
            if (serializer == null) {
                throw new SerializerException();
            }
            final Object object = serializer.deserialize(serializerFactory, decoder);

            TransientObjectDescriptorImpl transientDescriptor = new TransientObjectDescriptorImpl(twincodeOutboundId, sequenceId,
                    serializer, object, createdTimestamp, sentTimestamp);
            return new PushTransientIQ(this, requestId, transientDescriptor, flags);
        }
    }
}
