/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 3
 *  Date: 2021/04/07
 *
 * {
 *  "schemaId":"49fc3005-af8e-43da-925a-00d40889dc98",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"NamedFileDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.4"
 *  "fields":
 *  [
 *   {"name":"name", "type":"string"},
 *  ]
 * }
 *
 * Schema version 2
 *  Date: 2019/03/19
 *
 * {
 *  "schemaId":"49fc3005-af8e-43da-925a-00d40889dc98",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"NamedFileDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.3"
 *  "fields":
 *  [
 *   {"name":"name", "type":"string"},
 *  ]
 * }
 *
 * Schema version 1
 *  Date: 2018/09/17
 *
 * {
 *  "schemaId":"49fc3005-af8e-43da-925a-00d40889dc98",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"NamedFileDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.2"
 *  "fields":
 *  [
 *   {"name":"name", "type":"string"},
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class NamedFileDescriptorImpl extends FileDescriptorImpl implements ConversationService.NamedFileDescriptor {
    private static final String LOG_TAG = "NamedFileDescriptorImpl";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("49fc3005-af8e-43da-925a-00d40889dc98");
    static final int SCHEMA_VERSION_3 = 3;

    static class NamedFileDescriptorImplSerializer_3 extends FileDescriptorImplSerializer_4 {

        NamedFileDescriptorImplSerializer_3() {

            super(SCHEMA_ID, SCHEMA_VERSION_3, NamedFileDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            NamedFileDescriptorImpl fileDescriptorImpl = (NamedFileDescriptorImpl) object;
            encoder.writeString(fileDescriptorImpl.getName());
        }

        @NonNull
        public Object deserialize(@NonNull Decoder decoder, @NonNull UUID twincodeOutboundId, long sequenceId, long createdTimestamp) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(decoder, twincodeOutboundId, sequenceId, createdTimestamp);

            String name = decoder.readString();

            return new NamedFileDescriptorImpl(fileDescriptorImpl, name);
        }
    }

    static final NamedFileDescriptorImplSerializer_3 SERIALIZER_3 = new NamedFileDescriptorImplSerializer_3();
    static final int SCHEMA_VERSION_2 = 2;

    static class NamedFileDescriptorImplSerializer_2 extends FileDescriptorImplSerializer_3 {

        NamedFileDescriptorImplSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, NamedFileDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            NamedFileDescriptorImpl fileDescriptorImpl = (NamedFileDescriptorImpl) object;
            encoder.writeString(fileDescriptorImpl.getName());
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(serializerFactory, decoder);

            String name = decoder.readString();

            return new NamedFileDescriptorImpl(fileDescriptorImpl, name);
        }
    }

    static final NamedFileDescriptorImplSerializer_2 SERIALIZER_2 = new NamedFileDescriptorImplSerializer_2();

    static final int SCHEMA_VERSION_1 = 1;

    static class NamedFileDescriptorImplSerializer_1 extends FileDescriptorImplSerializer_2 {

        NamedFileDescriptorImplSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, NamedFileDescriptorImpl.class);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(serializerFactory, decoder);

            String name = decoder.readString();

            return new NamedFileDescriptorImpl(fileDescriptorImpl, name);
        }
    }

    static final NamedFileDescriptorImplSerializer_1 SERIALIZER_1 = new NamedFileDescriptorImplSerializer_1();

    @NonNull
    private final String mName;

    NamedFileDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                            @Nullable DescriptorId replyTo, @NonNull String path,
                            String extension, long length, long end,
                            @NonNull String name, boolean copyAllowed, boolean hasThumbnail) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, path, extension, length, end, copyAllowed, hasThumbnail);

        if (DEBUG) {
            Log.d(LOG_TAG, "NamedFileDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId + " path=" + path + " extension=" + extension +
                    " length=" + length + " end=" + end + " name=" + name + " copyAllowed=" + copyAllowed);
        }

        mName = name;
    }

    NamedFileDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                            @Nullable ConversationService.DescriptorId replyTo, boolean copyAllowed, boolean hasThumbnail,
                            long createdTimestamp, long sentTimestamp,
                            long length, String extension, @NonNull String name) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, copyAllowed, hasThumbnail, createdTimestamp, sentTimestamp, length, extension);

        mName = name;
    }

    NamedFileDescriptorImpl(@NonNull NamedFileDescriptorImpl imageDescriptorImpl, @SuppressWarnings("SameParameterValue") boolean masked) {

        super(imageDescriptorImpl, masked);

        if (DEBUG) {
            Log.d(LOG_TAG, "NamedFileDescriptorImpl: fileDescriptorImpl=" + imageDescriptorImpl + " masked=" + masked);
        }

        mName = imageDescriptorImpl.mName;
    }

    NamedFileDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                            @Nullable DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                            long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                            int flags, String content, long length) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout, flags, length);

        if (DEBUG) {
            Log.d(LOG_TAG, "NamedFileDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " content=" + content);
        }

        final String[] args = extract(content);
        String name = extractString(args, 0, "");
        mName = name == null ? "" : name;
        mEnd = extractLong(args, 1, 0);
        mExtension = extractString(args, 2, null);
    }

    //
    // Override Descriptor methods
    //

    @Override
    public Type getType() {

        return Type.NAMED_FILE_DESCRIPTOR;
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append(" name=");
            stringBuilder.append(mName);
            stringBuilder.append("\n");
        }
    }

    //
    // Override NamedFileDescriptor methods
    //

    @Override
    @NonNull
    public String getName() {

        return mName;
    }

    @Override
    @Nullable
    String serialize() {

        return mName + FIELD_SEPARATOR + super.serialize();
    }

    @Override
    @Nullable
    DescriptorImpl createForward(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout,
                                 @Nullable UUID sendTo, boolean copyAllowed) {

        return new NamedFileDescriptorImpl(descriptorId, conversationId, expireTimeout,
                sendTo, null, this, copyAllowed);
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("NamedFileDescriptorImpl\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private NamedFileDescriptorImpl(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout, @Nullable UUID sendTo,
                                    @Nullable DescriptorId replyTo, @NonNull NamedFileDescriptorImpl source, boolean copyAllowed) {

        super(descriptorId, conversationId, expireTimeout, sendTo, replyTo, source, copyAllowed);

        mName = source.mName;
    }

    private NamedFileDescriptorImpl(@NonNull FileDescriptorImpl fileDescriptorImpl, @NonNull String name) {

        super(fileDescriptorImpl, false);

        if (DEBUG) {
            Log.d(LOG_TAG, "NamedFileDescriptorImpl: fileDescriptorImpl=" + fileDescriptorImpl + " name=" + name);
        }

        mName = name;
    }
}
