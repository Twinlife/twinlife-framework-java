/*
 *  Copyright (c) 2016-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 * Schema version 4
 *  Date: 2021/04/07
 *
 * {
 *  "schemaId":"9b9490f0-5620-4a38-8022-d215e45797ec",
 *  "schemaVersion":"4",
 *
 *  "type":"record",
 *  "name":"ImageDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.4"
 *  "fields":
 *  [
 *   {"name":"width", "type":"int"}
 *   {"name":"height", "type":"int"}
 *  ]
 * }
 *
 * Schema version 3
 *
 * {
 *  "schemaId":"9b9490f0-5620-4a38-8022-d215e45797ec",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"ImageDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.3"
 *  "fields":
 *  [
 *   {"name":"width", "type":"int"}
 *   {"name":"height", "type":"int"}
 *  ]
 * }
 *
 * Schema version 2
 *
 * {
 *  "schemaId":"9b9490f0-5620-4a38-8022-d215e45797ec",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"ImageDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.2"
 *  "fields":
 *  [
 *   {"name":"width", "type":"int"}
 *   {"name":"height", "type":"int"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class ImageDescriptorImpl extends FileDescriptorImpl implements ConversationService.ImageDescriptor {
    private static final String LOG_TAG = "ImageDescriptorImpl";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("9b9490f0-5620-4a38-8022-d215e45797ec");
    static final int SCHEMA_VERSION_4 = 4;

    static class ImageDescriptorImplSerializer_4 extends FileDescriptorImplSerializer_4 {

        ImageDescriptorImplSerializer_4() {

            super(SCHEMA_ID, SCHEMA_VERSION_4, ImageDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ImageDescriptorImpl imageDescriptorImpl = (ImageDescriptorImpl) object;
            encoder.writeInt(imageDescriptorImpl.mWidth);
            encoder.writeInt(imageDescriptorImpl.mHeight);
        }

        @NonNull
        public Object deserialize(@NonNull Decoder decoder, @NonNull UUID twincodeOutboundId, long sequenceId, long createdTimestamp) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(decoder, twincodeOutboundId, sequenceId, createdTimestamp);

            int width = decoder.readInt();
            int height = decoder.readInt();

            return new ImageDescriptorImpl(fileDescriptorImpl, width, height);
        }
    }

    static final ImageDescriptorImplSerializer_4 SERIALIZER_4 = new ImageDescriptorImplSerializer_4();

    static final int SCHEMA_VERSION_3 = 3;

    static class ImageDescriptorImplSerializer_3 extends FileDescriptorImplSerializer_3 {

        ImageDescriptorImplSerializer_3() {

            super(SCHEMA_ID, SCHEMA_VERSION_3, ImageDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ImageDescriptorImpl imageDescriptorImpl = (ImageDescriptorImpl) object;
            encoder.writeInt(imageDescriptorImpl.mWidth);
            encoder.writeInt(imageDescriptorImpl.mHeight);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(serializerFactory, decoder);

            int width = decoder.readInt();
            int height = decoder.readInt();

            return new ImageDescriptorImpl(fileDescriptorImpl, width, height);
        }
    }

    static final ImageDescriptorImplSerializer_3 SERIALIZER_3 = new ImageDescriptorImplSerializer_3();
    static final int SCHEMA_VERSION_2 = 2;

    static class ImageDescriptorImplSerializer_2 extends FileDescriptorImplSerializer_2 {

        ImageDescriptorImplSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, ImageDescriptorImpl.class);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(serializerFactory, decoder);

            int width = decoder.readInt();
            int height = decoder.readInt();

            return new ImageDescriptorImpl(fileDescriptorImpl, width, height);
        }
    }

    static final ImageDescriptorImplSerializer_2 SERIALIZER_2 = new ImageDescriptorImplSerializer_2();

    private final int mWidth;
    private final int mHeight;

    ImageDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                        @Nullable DescriptorId replyTo, @NonNull String path,
                        String extension, long length,
                        int width, int height, boolean copyAllowed, boolean hasThumbnail) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, path, extension, length, length, copyAllowed, hasThumbnail);

        if (DEBUG) {
            Log.d(LOG_TAG, "ImageDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId + " path=" + path + " extension=" + extension +
                    " length=" + length + " width=" + width + " height=" + height);
        }

        mWidth = width;
        mHeight = height;
    }

    ImageDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                        @Nullable DescriptorId replyTo, boolean copyAllowed, boolean hasThumbnail,
                        long createdTimestamp, long sentTimestamp, long length, String extension, int width, int height) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, copyAllowed, hasThumbnail, createdTimestamp, sentTimestamp, length, extension);

        if (DEBUG) {
            Log.d(LOG_TAG, "ImageDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId + " length=" + length + " extension=" + extension);
        }

        mWidth = width;
        mHeight = height;
    }

    ImageDescriptorImpl(@NonNull ImageDescriptorImpl imageDescriptorImpl, @SuppressWarnings("SameParameterValue") boolean masked) {

        super(imageDescriptorImpl, masked);

        if (DEBUG) {
            Log.d(LOG_TAG, "ImageDescriptorImpl: fileDescriptorImpl=" + imageDescriptorImpl + " masked=" + masked);
        }

        mWidth = imageDescriptorImpl.mWidth;
        mHeight = imageDescriptorImpl.mHeight;
    }

    ImageDescriptorImpl(@NonNull ConversationService.DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                        @Nullable ConversationService.DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                        long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                        int flags, String content, long length) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout, flags, length);

        if (DEBUG) {
            Log.d(LOG_TAG, "ImageDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " content=" + content);
        }

        final String[] args = extract(content);
        mWidth = (int) extractLong(args, 0, 0);
        mHeight = (int) extractLong(args, 1, 0);
        mEnd = extractLong(args, 2, 0);
        mExtension = extractString(args, 3, null);
    }

    //
    // Override Descriptor methods
    //

    @Override
    public Type getType() {

        return Type.IMAGE_DESCRIPTOR;
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" width=");
        stringBuilder.append(mWidth);
        stringBuilder.append(" height=");
        stringBuilder.append(mHeight);
    }

    //
    // Override ImageDescriptor methods
    //

    @Override
    public int getWidth() {

        return mWidth;
    }

    @Override
    public int getHeight() {

        return mHeight;
    }

    @Override
    public boolean isGif() {

        return "gif".equalsIgnoreCase(mExtension);
    }

    @Override
    @Nullable
    String serialize() {

        return mWidth + FIELD_SEPARATOR + mHeight + FIELD_SEPARATOR + super.serialize();
    }

    @Override
    @Nullable
    DescriptorImpl createForward(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout,
                                 @Nullable UUID sendTo, boolean copyAllowed) {

        return new ImageDescriptorImpl(descriptorId, conversationId, expireTimeout,
                sendTo, null, this, copyAllowed);
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ImageDescriptorImpl\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private ImageDescriptorImpl(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout, @Nullable UUID sendTo,
                                @Nullable DescriptorId replyTo, @NonNull ImageDescriptorImpl source, boolean copyAllowed) {

        super(descriptorId, conversationId, expireTimeout, sendTo, replyTo, source, copyAllowed);

        if (DEBUG) {
            Log.d(LOG_TAG, "ImageDescriptorImpl: descriptorId=" + descriptorId + " conversationId=" + conversationId + " source=" + source);
        }

        mWidth = source.getWidth();
        mHeight = source.getHeight();
    }

    private ImageDescriptorImpl(@NonNull FileDescriptorImpl fileDescriptorImpl, int width, int height) {

        super(fileDescriptorImpl, false);

        if (DEBUG) {
            Log.d(LOG_TAG, "ImageDescriptorImpl: fileDescriptorImpl=" + fileDescriptorImpl + " width=" + width + " height=" + height);
        }

        mWidth = width;
        mHeight = height;
    }
}
