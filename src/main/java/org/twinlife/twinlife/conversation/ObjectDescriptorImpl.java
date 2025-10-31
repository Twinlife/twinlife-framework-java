/*
 *  Copyright (c) 2015-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 5
 *  Date: 2021/04/07
 *
 * {
 *  "schemaId":"9239451b-0193-4703-b98e-a487115e433a",
 *  "schemaVersion":"5",
 *
 *  "type":"record",
 *  "name":"ObjectDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.4"
 *  "fields":
 *  [
 *   {"name":"object", "type":"Object"}
 *   {"name":"copyAllowed", "type":"boolean"}
 *  ]
 * }
 *
 * Schema version 4
 *  Date: 2019/03/19
 *
 * {
 *  "schemaId":"9239451b-0193-4703-b98e-a487115e433a",
 *  "schemaVersion":"4",
 *
 *  "type":"record",
 *  "name":"ObjectDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.3"
 *  "fields":
 *  [
 *   {"name":"object", "type":"Object"}
 *   {"name":"copyAllowed", "type":"boolean"}
 *  ]
 * }
 *
 * Schema version 3
 *  Date: 2016/12/29
 *
 * {
 *  "schemaId":"9239451b-0193-4703-b98e-a487115e433a",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ObjectDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.3"
 *  "fields":
 *  [
 *   {"name":"object", "type":"Object"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class ObjectDescriptorImpl extends DescriptorImpl implements ConversationService.ObjectDescriptor {
    private static final String LOG_TAG = "ObjectDescriptorImpl";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("9239451b-0193-4703-b98e-a487115e433a");
    static final int SCHEMA_VERSION_5 = 5;

    static class ObjectDescriptorImplSerializer_5 extends DescriptorImplSerializer_4 {

        ObjectDescriptorImplSerializer_5() {

            super(SCHEMA_ID, SCHEMA_VERSION_5, ObjectDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ObjectDescriptorImpl objectDescriptorImpl = (ObjectDescriptorImpl) object;
            objectDescriptorImpl.serialize(encoder);
            encoder.writeBoolean(objectDescriptorImpl.mCopyAllowed);
        }

        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder,
                                  @NonNull UUID twincodeOutboundId, long sequenceId, long createdTimestamp) throws SerializerException {

            long expireTimeout = decoder.readLong();
            UUID sendTo = decoder.readOptionalUUID();
            DescriptorId replyTo = readOptionalDescriptorId(decoder);

            UUID schemaId = decoder.readUUID();
            int schemaVersion = decoder.readInt();
            // Serializer serializer = serializerFactory.getSerializer(schemaId, schemaVersion);
            if (!Message.SCHEMA_ID.equals(schemaId) || schemaVersion != Message.SCHEMA_VERSION) {
                throw new SerializerException();
            }
            String message = decoder.readString();
            boolean copyAllowed = decoder.readBoolean();

            return new ObjectDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, message, copyAllowed, createdTimestamp, 0);
        }
    }

    static final ObjectDescriptorImplSerializer_5 SERIALIZER_5 = new ObjectDescriptorImplSerializer_5();

    static final int SCHEMA_VERSION_4 = 4;
    static final int SCHEMA_VERSION_3 = 3;

    static class ObjectDescriptorImplSerializer_4 extends DescriptorImplSerializer_3 {

        ObjectDescriptorImplSerializer_4() {

            super(SCHEMA_ID, SCHEMA_VERSION_4, ObjectDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ObjectDescriptorImpl objectDescriptorImpl = (ObjectDescriptorImpl) object;
            objectDescriptorImpl.serialize(encoder);
            encoder.writeBoolean(objectDescriptorImpl.mCopyAllowed);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            UUID schemaId = decoder.readUUID();
            int schemaVersion = decoder.readInt();
            // Serializer serializer = serializerFactory.getSerializer(schemaId, schemaVersion);
            if (!Message.SCHEMA_ID.equals(schemaId) || schemaVersion != Message.SCHEMA_VERSION) {
                throw new SerializerException();
            }
            String message = decoder.readString();
            boolean copyAllowed = decoder.isEof() ? DEFAULT_COPY_ALLOWED : decoder.readBoolean();

            return new ObjectDescriptorImpl(descriptorImpl, message, copyAllowed);
        }
    }

    static final ObjectDescriptorImplSerializer_4 SERIALIZER_4 = new ObjectDescriptorImplSerializer_4();

    private boolean mCopyAllowed;
    private boolean mIsEdited;
    private String mContent;

    ObjectDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, long expireTimeout, @Nullable UUID sendTo,
                         @Nullable DescriptorId replyTo, @NonNull String message, boolean copyAllowed) {

        super(descriptorId, cid, expireTimeout, sendTo, replyTo);

        if (DEBUG) {
            Log.d(LOG_TAG, "ObjectDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " message=" + message);
        }

        mContent = message;
        mCopyAllowed = copyAllowed;
        mIsEdited = false;
    }

    ObjectDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                         @Nullable DescriptorId replyTo, @NonNull String message, boolean copyAllowed,
                         long createdTimestamp, long sentTimestamp) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, createdTimestamp, sentTimestamp);

        if (DEBUG) {
            Log.d(LOG_TAG, "ObjectDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId
                    + " message=" + message);
        }

        mContent = message;
        mCopyAllowed = copyAllowed;
        mIsEdited = false;
    }

    ObjectDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                         @Nullable DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                         long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                         int flags, String content) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout);

        if (DEBUG) {
            Log.d(LOG_TAG, "ObjectDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " content=" + content);
        }

        mContent = content;
        mCopyAllowed = (flags & FLAG_COPY_ALLOWED) != 0;
        mIsEdited = (flags & FLAG_UPDATED) != 0;
    }

    /*
     * Override Descriptor methods
     */

    @Override
    public Type getType() {

        return Type.OBJECT_DESCRIPTOR;
    }

    /*
     * Override ObjectDescriptor methods
     */

    @NonNull
    @Override
    public String getMessage() {

        return mContent;
    }

    @Override
    public boolean isCopyAllowed() {

        return mCopyAllowed;
    }

    @Override
    public boolean isEdited() {

        return mIsEdited;
    }

    @Override
    int getFlags() {

        return (mCopyAllowed ? FLAG_COPY_ALLOWED : 0) | (mIsEdited ? FLAG_UPDATED : 0);
    }

    @Override
    @Nullable
    String serialize() {

        return mContent;
    }

    boolean setMessage(@Nullable String message) {

        if (message == null || message.equals(mContent)) {
            return false;
        }
        mContent = message;
        return true;
    }

    boolean setCopyAllowed(@Nullable Boolean copyAllowed) {

        if (copyAllowed == null || copyAllowed == mCopyAllowed) {
            return false;
        }

        mCopyAllowed = copyAllowed;
        return true;
    }

    void setEdited() {

        mIsEdited = true;
    }

    @Override
    @Nullable
    DescriptorImpl createForward(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout,
                                 @Nullable UUID sendTo, boolean copyAllowed) {

        return new ObjectDescriptorImpl(descriptorId, conversationId, expireTimeout,
                sendTo, null, mContent, copyAllowed);
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append("\n");
            stringBuilder.append(" content=");
            stringBuilder.append(mContent);
            stringBuilder.append("\n");
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("ObjectDescriptorImpl\n");
            appendTo(stringBuilder);
        }
        return stringBuilder.toString();
    }

    void serialize(@NonNull Encoder encoder) throws SerializerException {

        encoder.writeUUID(Message.SCHEMA_ID);
        encoder.writeInt(Message.SCHEMA_VERSION);
        encoder.writeString(mContent);

        // mSerializer.serialize(serializerFactory, encoder, mObject);
    }

    //
    // Private Methods
    //

    private ObjectDescriptorImpl(@NonNull DescriptorImpl descriptorImpl, @NonNull String message, boolean copyAllowed) {

        super(descriptorImpl);

        if (DEBUG) {
            Log.d(LOG_TAG, "ObjectDescriptorImpl: descriptorImpl=" + descriptorImpl + " message=" + message);
        }

        mContent = message;
        mCopyAllowed = copyAllowed;
        mIsEdited = false;
    }
}
