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
 *
 * {
 *  "schemaId":"4fe07aed-f318-46e3-99d0-bb2953cef9ba",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"VideoDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.4"
 *  "fields":
 *  [
 *   {"name":"width", "type":"int"}
 *   {"name":"height", "type":"int"}
 *   {"name":"duration", "type":"long"}
 *  ]
 * }
 *
 * Schema version 2
 *
 * {
 *  "schemaId":"4fe07aed-f318-46e3-99d0-bb2953cef9ba",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"VideoDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.3"
 *  "fields":
 *  [
 *   {"name":"width", "type":"int"}
 *   {"name":"height", "type":"int"}
 *   {"name":"duration", "type":"long"}
 *  ]
 * }
 *
 * Schema version 1
 *
 * {
 *  "schemaId":"4fe07aed-f318-46e3-99d0-bb2953cef9ba",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"VideoDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.2"
 *  "fields":
 *  [
 *   {"name":"width", "type":"int"}
 *   {"name":"height", "type":"int"}
 *   {"name":"duration", "type":"long"}
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

class VideoDescriptorImpl extends FileDescriptorImpl implements ConversationService.VideoDescriptor {
    private static final String LOG_TAG = "VideoDescriptorImpl";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("4fe07aed-f318-46e3-99d0-bb2953cef9ba");
    static final int SCHEMA_VERSION_3 = 3;

    private final int mWidth;
    private final int mHeight;
    private final long mDuration;

    static class VideoDescriptorImplSerializer_3 extends FileDescriptorImplSerializer_4 {

        VideoDescriptorImplSerializer_3() {

            super(SCHEMA_ID, SCHEMA_VERSION_3, VideoDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            VideoDescriptorImpl videoDescriptorImpl = (VideoDescriptorImpl) object;
            encoder.writeInt(videoDescriptorImpl.mWidth);
            encoder.writeInt(videoDescriptorImpl.mHeight);
            encoder.writeLong(videoDescriptorImpl.mDuration);
        }

        @NonNull
        public Object deserialize(@NonNull Decoder decoder, @NonNull UUID twincodeOutboundId, long sequenceId, long createdTimestamp) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(decoder, twincodeOutboundId, sequenceId, createdTimestamp);

            int width = decoder.readInt();
            int height = decoder.readInt();
            long duration = decoder.readLong();

            return new VideoDescriptorImpl(fileDescriptorImpl, width, height, duration);
        }
    }

    static final VideoDescriptorImplSerializer_3 SERIALIZER_3 = new VideoDescriptorImplSerializer_3();

    static final int SCHEMA_VERSION_2 = 2;

    static class VideoDescriptorImplSerializer_2 extends FileDescriptorImplSerializer_3 {

        VideoDescriptorImplSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, VideoDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            VideoDescriptorImpl videoDescriptorImpl = (VideoDescriptorImpl) object;
            encoder.writeInt(videoDescriptorImpl.mWidth);
            encoder.writeInt(videoDescriptorImpl.mHeight);
            encoder.writeLong(videoDescriptorImpl.mDuration);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(serializerFactory, decoder);

            int width = decoder.readInt();
            int height = decoder.readInt();
            long duration = decoder.readLong();

            return new VideoDescriptorImpl(fileDescriptorImpl, width, height, duration);
        }
    }

    static final VideoDescriptorImplSerializer_2 SERIALIZER_2 = new VideoDescriptorImplSerializer_2();

    static final int SCHEMA_VERSION_1 = 1;

    static class VideoDescriptorImplSerializer_1 extends FileDescriptorImplSerializer_2 {

        VideoDescriptorImplSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, VideoDescriptorImpl.class);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(serializerFactory, decoder);

            int width = decoder.readInt();
            int height = decoder.readInt();
            long duration = decoder.readLong();

            return new VideoDescriptorImpl(fileDescriptorImpl, width, height, duration);
        }
    }

    static final VideoDescriptorImplSerializer_1 SERIALIZER_1 = new VideoDescriptorImplSerializer_1();

    VideoDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                        @Nullable DescriptorId replyTo, @NonNull String path,
                        String extension, long length, int width, int height, long duration, boolean copyAllowed, boolean hasThumbnail) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, path, extension, length, length, copyAllowed, hasThumbnail);

        mWidth = width;
        mHeight = height;
        mDuration = duration;
    }

    VideoDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                        @Nullable DescriptorId replyTo, boolean copyAllowed, boolean hasThumbnail,
                        long createdTimestamp, long sentTimestamp,
                        long length, String extension, int width, int height, long duration) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, copyAllowed, hasThumbnail, createdTimestamp, sentTimestamp, length, extension);

        mWidth = width;
        mHeight = height;
        mDuration = duration;
    }

    VideoDescriptorImpl(@NonNull VideoDescriptorImpl videoDescriptorImpl, @SuppressWarnings("SameParameterValue") boolean masked) {

        super(videoDescriptorImpl, masked);

        if (DEBUG) {
            Log.d(LOG_TAG, "VideoDescriptorImpl: videoDescriptorImpl=" + videoDescriptorImpl + " masked=" + masked);
        }

        mWidth = videoDescriptorImpl.mWidth;
        mHeight = videoDescriptorImpl.mHeight;
        mDuration = videoDescriptorImpl.mDuration;
    }

    VideoDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                        @Nullable DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                        long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                        int flags, String content, long length) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout, flags, length);

        if (DEBUG) {
            Log.d(LOG_TAG, "VideoDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " content=" + content);
        }

        final String[] args = extract(content);
        mWidth = (int) extractLong(args, 0, 0);
        mHeight = (int) extractLong(args, 1, 0);
        mDuration = (int) extractLong(args, 2, 0);
        mEnd = extractLong(args, 3, 0);
        mExtension = extractString(args, 4, null);
    }

    @Override
    @Nullable
    String serialize() {

        return mWidth + FIELD_SEPARATOR + mHeight + FIELD_SEPARATOR
                + mDuration + FIELD_SEPARATOR + super.serialize();
    }

    @Override
    @Nullable
    DescriptorImpl createForward(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout,
                                 @Nullable UUID sendTo, boolean copyAllowed) {

        return new VideoDescriptorImpl(descriptorId, conversationId, expireTimeout,
                sendTo, null, this, copyAllowed);
    }

    //
    // Override Descriptor methods
    //

    @Override
    public Type getType() {

        return Type.VIDEO_DESCRIPTOR;
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" width=");
        stringBuilder.append(mWidth);
        stringBuilder.append("\n");
        stringBuilder.append(" height=");
        stringBuilder.append(mHeight);
        stringBuilder.append("\n");
        stringBuilder.append(" duration=");
        stringBuilder.append(mDuration);
        stringBuilder.append("\n");
    }

    //
    // Override VideoDescriptor methods
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
    public long getDuration() {

        return mDuration;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("VideoDescriptorImpl:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private VideoDescriptorImpl(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout, @Nullable UUID sendTo,
                                @Nullable DescriptorId replyTo, @NonNull VideoDescriptorImpl source, boolean copyAllowed) {

        super(descriptorId, conversationId, expireTimeout, sendTo, replyTo, source, copyAllowed);

        mWidth = source.mWidth;
        mHeight = source.mHeight;
        mDuration = source.mDuration;
    }

    private VideoDescriptorImpl(@NonNull FileDescriptorImpl fileDescriptorImpl, int width, int height, long duration) {

        super(fileDescriptorImpl, false);

        if (DEBUG) {
            Log.d(LOG_TAG, "VideoDescriptorImpl: fileDescriptorImpl=" + fileDescriptorImpl + " width=" + width + " height=" + height + " duration=" + duration);
        }

        mWidth = width;
        mHeight = height;
        mDuration = duration;
    }
}
