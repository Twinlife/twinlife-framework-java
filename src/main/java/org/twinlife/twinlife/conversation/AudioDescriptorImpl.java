/*
 *  Copyright (c) 2017-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Houssem Temanni (Houssem.Temanni@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 3
 *  Date: 2021/04/07
 *
 * {
 *  "schemaId":"f40eaf3b-69c2-4ad5-a4bf-41779b504956",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"AudioDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.4"
 *  "fields":
 *  [
 *   {"name":"duration", "type":"long"}
 *  ]
 * }
 *
 * Schema version 2
 *
 * {
 *  "schemaId":"f40eaf3b-69c2-4ad5-a4bf-41779b504956",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"AudioDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.3"
 *  "fields":
 *  [
 *   {"name":"duration", "type":"long"}
 *  ]
 * }
 *
 * Schema version 1
 *
 * {
 *  "schemaId":"f40eaf3b-69c2-4ad5-a4bf-41779b504956",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AudioDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.FileDescriptor.2"
 *  "fields":
 *  [
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
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.util.UUID;

class AudioDescriptorImpl extends FileDescriptorImpl implements ConversationService.AudioDescriptor {
    private static final String LOG_TAG = "AudioDescriptorImpl";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("f40eaf3b-69c2-4ad5-a4bf-41779b504956");
    static final int SCHEMA_VERSION_3 = 3;

    private final long mDuration;

    static class AudioDescriptorImplSerializer_3 extends FileDescriptorImplSerializer_4 {

        AudioDescriptorImplSerializer_3() {

            super(SCHEMA_ID, SCHEMA_VERSION_3, AudioDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            AudioDescriptorImpl audioDescriptorImpl = (AudioDescriptorImpl) object;
            encoder.writeLong(audioDescriptorImpl.mDuration);
        }

        @NonNull
        public Object deserialize(@NonNull Decoder decoder, @NonNull UUID twincodeOutboundId, long sequenceId, long createdTimestamp) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(decoder, twincodeOutboundId, sequenceId, createdTimestamp);

            long duration = decoder.readLong();

            return new AudioDescriptorImpl(fileDescriptorImpl, duration);
        }
    }

    static final AudioDescriptorImplSerializer_3 SERIALIZER_3 = new AudioDescriptorImplSerializer_3();

    static final int SCHEMA_VERSION_2 = 2;

    static class AudioDescriptorImplSerializer_2 extends FileDescriptorImplSerializer_3 {

        AudioDescriptorImplSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, AudioDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            AudioDescriptorImpl audioDescriptorImpl = (AudioDescriptorImpl) object;
            encoder.writeLong(audioDescriptorImpl.mDuration);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(serializerFactory, decoder);

            long duration = decoder.readLong();

            return new AudioDescriptorImpl(fileDescriptorImpl, duration);
        }
    }

    static final AudioDescriptorImplSerializer_2 SERIALIZER_2 = new AudioDescriptorImplSerializer_2();

    static final int SCHEMA_VERSION_1 = 1;

    static class AudioDescriptorImplSerializer_1 extends FileDescriptorImplSerializer_2 {

        AudioDescriptorImplSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, AudioDescriptorImpl.class);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) super.deserialize(serializerFactory, decoder);

            long duration = decoder.readLong();

            return new AudioDescriptorImpl(fileDescriptorImpl, duration);
        }
    }

    static final AudioDescriptorImplSerializer_1 SERIALIZER_1 = new AudioDescriptorImplSerializer_1();

    AudioDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                        @Nullable ConversationService.DescriptorId replyTo, @NonNull String path,
                        String extension, long length, long end,
                        long duration, boolean copyAllowed, boolean hasThumbnail) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, path, extension, length, end, copyAllowed, hasThumbnail);

        mDuration = duration;
    }

    AudioDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                        @Nullable ConversationService.DescriptorId replyTo, boolean copyAllowed, boolean hasThumbnail,
                        long createdTimestamp, long sentTimestamp, long length, String extension, long duration) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, copyAllowed, hasThumbnail, createdTimestamp, sentTimestamp, length, extension);

        if (DEBUG) {
            Log.d(LOG_TAG, "AudioDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId + " length=" + length + " extension=" + extension);
        }

        mDuration = duration;
    }

    AudioDescriptorImpl(@NonNull AudioDescriptorImpl audioDescriptorImpl, @SuppressWarnings("SameParameterValue") boolean masked) {

        super(audioDescriptorImpl, masked);

        if (DEBUG) {
            Log.d(LOG_TAG, "AudioDescriptorImpl: fileDescriptorImpl=" + audioDescriptorImpl + " masked=" + masked);
        }

        mDuration = audioDescriptorImpl.mDuration;
    }

    AudioDescriptorImpl(@NonNull ConversationService.DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                        @Nullable ConversationService.DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                        long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                        int flags, String content, long length) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout, flags, length);

        if (DEBUG) {
            Log.d(LOG_TAG, "AudioDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " content=" + content);
        }

        final String[] args = extract(content);
        mDuration = (int) extractLong(args, 0, 0);
        mEnd = extractLong(args, 1, 0);
        mExtension = extractString(args, 2, null);
    }

    //
    // Override Descriptor methods
    //

    @Override
    public Type getType() {

        return Type.AUDIO_DESCRIPTOR;
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" duration=");
        stringBuilder.append(mDuration);
        stringBuilder.append("\n");
    }

    //
    // Override AudioDescriptor methods
    //

    @Override
    public long getDuration() {

        return mDuration;
    }

    @Override
    void delete(@Nullable File filesDir) {
        if (DEBUG) {
            Log.d(LOG_TAG, "delete");
        }

        final String path = getPath();
        if (path != null) {
            File file = new File(filesDir, path);
            Utils.deleteFile(LOG_TAG, file);

            int pos = path.lastIndexOf('.');
            if (pos > 0) {
                String datPath = path.substring(0, pos) + ".dat";
                file = new File(filesDir, datPath);
                Utils.deleteFile(LOG_TAG, file);
            }
        }
    }

    @Override
    @Nullable
    String serialize() {

        return mDuration + FIELD_SEPARATOR + super.serialize();
    }

    @Override
    @Nullable
    DescriptorImpl createForward(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout,
                                 @Nullable UUID sendTo, boolean copyAllowed) {

        return new AudioDescriptorImpl(descriptorId, conversationId, expireTimeout,
                sendTo, null, this, copyAllowed);
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("AudioDescriptorImpl:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private AudioDescriptorImpl(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout, @Nullable UUID sendTo,
                                @Nullable DescriptorId replyTo, @NonNull AudioDescriptorImpl source, boolean copyAllowed) {

        super(descriptorId, conversationId, expireTimeout, sendTo, replyTo, source, copyAllowed);

        if (DEBUG) {
            Log.d(LOG_TAG, "AudioDescriptorImpl: descriptorId=" + descriptorId + " conversationId=" + conversationId + " source=" + source);
        }

        mDuration = source.mDuration;
    }

    private AudioDescriptorImpl(@NonNull FileDescriptorImpl fileDescriptorImpl, long duration) {

        super(fileDescriptorImpl, false);

        if (DEBUG) {
            Log.d(LOG_TAG, "AudioDescriptorImpl: fileDescriptorImpl=" + fileDescriptorImpl + " duration=" + duration);
        }

        mDuration = duration;
    }
}
