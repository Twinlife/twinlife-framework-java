/*
 *  Copyright (c) 2016-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 2
 *  Date: 2016/12/29
 *
 * {
 *  "type":"record",
 *  "name":"TransientObjectDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor"
 *  "fields":
 *  [
 *   {"name":"object", "type":"Object"}
 *  ]
 * }
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import android.util.Log;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class TransientObjectDescriptorImpl extends DescriptorImpl implements ConversationService.TransientObjectDescriptor {
    private static final String LOG_TAG = "TransientObjectDescr...";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("43125f6e-aaf0-4985-a363-1aa1d813db46");
    static final int SCHEMA_VERSION_2 = 2;

    static class TransientObjectDescriptorImplSerializer_2 extends DescriptorImplSerializer_3 {

        TransientObjectDescriptorImplSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, TransientObjectDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            TransientObjectDescriptorImpl transientObjectDescriptorImpl = (TransientObjectDescriptorImpl) object;
            transientObjectDescriptorImpl.mSerializer.serialize(serializerFactory, encoder, transientObjectDescriptorImpl.mObject);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            UUID schemaId = decoder.readUUID();
            int schemaVersion = decoder.readInt();
            Serializer serializer = serializerFactory.getSerializer(schemaId, schemaVersion);
            if (serializer == null) {
                throw new SerializerException();
            }
            Object object = serializer.deserialize(serializerFactory, decoder);

            return new TransientObjectDescriptorImpl(descriptorImpl, serializer, object);
        }
    }

    static final TransientObjectDescriptorImplSerializer_2 SERIALIZER_2 = new TransientObjectDescriptorImplSerializer_2();

    @NonNull
    private final Serializer mSerializer;
    @NonNull
    private final Object mObject;

    TransientObjectDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long cid, @NonNull Serializer serializer, @NonNull Object object) {
        super(new DescriptorId(0, twincodeOutboundId, sequenceId), cid, 0, null, null);

        if (DEBUG) {
            Log.d(LOG_TAG, "TransientObjectDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " cid=" + cid + " serializer=" + serializer + " object=" + object);
        }

        mSerializer = serializer;
        mObject = object;
    }

    TransientObjectDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, @NonNull Serializer serializer,
                                  @NonNull Object object, long createdTimestamp, long sentTimestamp) {
        super(twincodeOutboundId, sequenceId, 0, null, null, createdTimestamp, sentTimestamp);

        if (DEBUG) {
            Log.d(LOG_TAG, "TransientObjectDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " serializer=" + serializer + " object=" + object);
        }

        mSerializer = serializer;
        mObject = object;
    }

    void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder) throws SerializerException {

        mSerializer.serialize(serializerFactory, encoder, mObject);
    }

    /*
     * Override Descriptor methods
     */

    @Override
    public Type getType() {

        return Type.TRANSIENT_OBJECT_DESCRIPTOR;
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append(" serializer=");
            stringBuilder.append(mSerializer);
            stringBuilder.append("\n");
            stringBuilder.append(" object=");
            stringBuilder.append(mObject);
            stringBuilder.append("\n");
        }
    }

    /*
     * Override TransientObjectDescriptor methods
     */

    @NonNull
    @Override
    public Object getObject() {

        return mObject;
    }

    //
    // Override Object methods
    //

    @NonNull
    @Override
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("TransientObjectDescriptorImpl:\n");
            appendTo(stringBuilder);
        }
        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private TransientObjectDescriptorImpl(@NonNull DescriptorImpl descriptorImpl, @NonNull Serializer serializer, @NonNull Object object) {

        super(descriptorImpl);

        if (DEBUG) {
            Log.d(LOG_TAG, "TransientObjectDescriptorImpl: descriptorImpl=" + descriptorImpl + " serializer=" + serializer + " object=" + object);
        }

        mSerializer = serializer;
        mObject = object;
    }
}
