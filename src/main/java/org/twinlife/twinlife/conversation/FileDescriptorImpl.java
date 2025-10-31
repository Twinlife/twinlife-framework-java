/*
 *  Copyright (c) 2016-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 4
 *  Date: 2021/04/07
 *
 * {
 *  "schemaId":"e9341f60-0594-4877-b375-39bb3a836de4",
 *  "schemaVersion":"4",
 *
 *  "type":"record",
 *  "name":"FileDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.4"
 *  "fields":
 *  [
 *   {"name":"path", "type": ["null", "string"]},
 *   {"name":"extension", "type": ["null", "string"]},
 *   {"name":"length", "type":"long"},
 *   {"name":"end", "type":"long"}
 *   {"name":"copyAllowed", "type":"boolean"}
 *   {"name":"hasThumbnail", "type":"boolean"}
 *  ]
 * }
 *
 * Schema version 3
 *  Date: 2019/03/19
 *
 * {
 *  "schemaId":"e9341f60-0594-4877-b375-39bb3a836de4",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"FileDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.3"
 *  "fields":
 *  [
 *   {"name":"path", "type": ["null", "string"]},
 *   {"name":"extension", "type": ["null", "string"]},
 *   {"name":"length", "type":"long"},
 *   {"name":"end", "type":"long"}
 *   {"name":"copyAllowed", "type":"boolean"}
 *  ]
 * }
 *
 * Schema version 2
 *  Date: 2016/12/29
 *
 * {
 *  "schemaId":"e9341f60-0594-4877-b375-39bb3a836de4",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"FileDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.3"
 *  "fields":
 *  [
 *   {"name":"path", "type": ["null", "string"]},
 *   {"name":"extension", "type": ["null", "string"]},
 *   {"name":"length", "type":"long"},
 *   {"name":"end", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.util.UUID;

public class FileDescriptorImpl extends DescriptorImpl implements ConversationService.FileDescriptor {
    private static final String LOG_TAG = "FileObjectDescriptor...";
    private static final boolean DEBUG = false;

    static final UUID FILE_DESCRIPTOR_SCHEMA_ID = UUID.fromString("e9341f60-0594-4877-b375-39bb3a836de4");
    static final int FILE_SCHEMA_VERSION_4 = 4;

    static class FileDescriptorImplSerializer_4 extends DescriptorImplSerializer_4 {

        FileDescriptorImplSerializer_4() {

            super(FILE_DESCRIPTOR_SCHEMA_ID, FILE_SCHEMA_VERSION_4, FileDescriptorImpl.class);
        }

        FileDescriptorImplSerializer_4(@NonNull UUID schemaId, int schemaVersion, @NonNull Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) object;
            encoder.writeOptionalString(fileDescriptorImpl.mPath);
            encoder.writeOptionalString(fileDescriptorImpl.mExtension);
            encoder.writeLong(fileDescriptorImpl.mLength);
            encoder.writeLong(fileDescriptorImpl.mEnd);
            encoder.writeBoolean(fileDescriptorImpl.mCopyAllowed);
            encoder.writeBoolean(fileDescriptorImpl.mHasThumbnail);
        }

        @NonNull
        public Object deserialize(@NonNull Decoder decoder, @NonNull UUID twincodeOutboundId, long sequenceId, long createdTimestamp) throws SerializerException {

            long expireTimeout = decoder.readLong();
            UUID sendTo = decoder.readOptionalUUID();
            DescriptorId replyTo = readOptionalDescriptorId(decoder);

            String path = decoder.readOptionalString();
            String extension = decoder.readOptionalString();
            long length = decoder.readLong();
            long end = decoder.readLong();
            boolean copyAllowed = decoder.readBoolean();
            boolean hasThumbnail = decoder.readBoolean();

            return new FileDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, createdTimestamp, path, extension, length, end, copyAllowed, hasThumbnail);
        }
    }

    static final FileDescriptorImplSerializer_4 FILE_SERIALIZER_4 = new FileDescriptorImplSerializer_4();

    static final int FILE_SCHEMA_VERSION_3 = 3;

    static class FileDescriptorImplSerializer_3 extends DescriptorImplSerializer_3 {

        FileDescriptorImplSerializer_3() {

            super(FILE_DESCRIPTOR_SCHEMA_ID, FILE_SCHEMA_VERSION_3, FileDescriptorImpl.class);
        }

        FileDescriptorImplSerializer_3(@NonNull UUID schemaId, int schemaVersion, @NonNull Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) object;
            encoder.writeOptionalString(fileDescriptorImpl.mPath);
            encoder.writeOptionalString(fileDescriptorImpl.mExtension);
            encoder.writeLong(fileDescriptorImpl.mLength);
            encoder.writeLong(fileDescriptorImpl.mEnd);
            encoder.writeBoolean(fileDescriptorImpl.mCopyAllowed);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            String path = decoder.readOptionalString();
            String extension = decoder.readOptionalString();
            long length = decoder.readLong();
            long end = decoder.readLong();
            boolean copyAllowed = decoder.readBoolean();

            return new FileDescriptorImpl(descriptorImpl, path, extension, length, end, copyAllowed);
        }
    }

    static final FileDescriptorImplSerializer_3 FILE_SERIALIZER_3 = new FileDescriptorImplSerializer_3();

    static class FileDescriptorImplSerializer_2 extends DescriptorImplSerializer_3 {

        FileDescriptorImplSerializer_2(@NonNull UUID schemaId, int schemaVersion, @NonNull Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            throw new SerializerException();
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            String path = decoder.readOptionalString();
            String extension = decoder.readOptionalString();
            long length = decoder.readLong();
            long end = decoder.readLong();

            return new FileDescriptorImpl(descriptorImpl, path, extension, length, end, DEFAULT_COPY_ALLOWED);
        }
    }

    // static final FileDescriptorImplSerializer_2 FILE_SERIALIZER_2 = new FileDescriptorImplSerializer_2();

    private volatile String mPath;
    @Nullable
    protected String mExtension;
    protected final long mLength;
    protected volatile long mEnd;
    protected boolean mCopyAllowed;
    protected final boolean mHasThumbnail;

    FileDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                       @Nullable DescriptorId replyTo, long createdTimestamp, String path, @Nullable String extension,
                       long length, long end, boolean copyAllowed, boolean hasThumbnail) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, createdTimestamp, 0);

        if (DEBUG) {
            Log.d(LOG_TAG, "FileDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId
                    + " path=" + path + " extension=" + extension + " length=" + length
                    + " end=" + end + " copyAllowed=" + copyAllowed);
        }

        mPath = path;
        mExtension = extension;
        mLength = length;
        mEnd = end;
        mCopyAllowed = copyAllowed;
        mHasThumbnail = hasThumbnail;
    }

    FileDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                       @Nullable DescriptorId replyTo, boolean copyAllowed, boolean hasThumbnail, long createdTimestamp, long sentTimestamp,
                       long length, @Nullable String extension) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, createdTimestamp, sentTimestamp);

        if (DEBUG) {
            Log.d(LOG_TAG, "FileDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId + " length=" + length + " extension=" + extension);
        }

        mLength = length;
        mEnd = 0;
        mExtension = extension;
        mCopyAllowed = copyAllowed;
        mHasThumbnail = hasThumbnail;
    }

    FileDescriptorImpl(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout, @Nullable UUID sendTo,
                       @Nullable DescriptorId replyTo, @NonNull FileDescriptorImpl source, boolean copyAllowed) {

        super(descriptorId, conversationId, expireTimeout, sendTo, replyTo);

        if (DEBUG) {
            Log.d(LOG_TAG, "FileDescriptorImpl: descriptorId=" + descriptorId + " conversationId=" + conversationId + " source=" + source);
        }

        mLength = source.mLength;
        mEnd = source.mLength;
        mExtension = source.mExtension;
        mCopyAllowed = copyAllowed;
        mHasThumbnail = source.mHasThumbnail;
    }

    FileDescriptorImpl(@NonNull FileDescriptorImpl fileDescriptorImpl, boolean masked) {

        super(fileDescriptorImpl);

        if (DEBUG) {
            Log.d(LOG_TAG, "FileDescriptorImpl: fileDescriptorImpl=" + fileDescriptorImpl + " masked=" + masked);
        }

        if (masked) {
            mPath = null;
        } else {
            mPath = fileDescriptorImpl.mPath;
        }
        mExtension = fileDescriptorImpl.mExtension;
        mLength = fileDescriptorImpl.mLength;
        mCopyAllowed = fileDescriptorImpl.mCopyAllowed;
        mHasThumbnail = fileDescriptorImpl.mHasThumbnail;
        if (masked) {
            mEnd = 0L;
        } else {
            mEnd = fileDescriptorImpl.mEnd;
        }
    }

    FileDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                       @Nullable DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                       long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                       int flags, long value) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout);

        if (DEBUG) {
            Log.d(LOG_TAG, "FileDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid);
        }

        mCopyAllowed = (flags & FLAG_COPY_ALLOWED) != 0;
        mHasThumbnail = (flags & FLAG_HAS_THUMBNAIL) != 0;
        mLength = value;
    }

    /*
     * Override Descriptor methods
     */

    @Override
    public Type getType() {

        return Type.FILE_DESCRIPTOR;
    }

    @Override
    public boolean isCopyAllowed() {

        return mCopyAllowed;
    }

    boolean setCopyAllowed(@Nullable Boolean copyAllowed) {

        if (copyAllowed == null || copyAllowed == mCopyAllowed) {
            return false;
        }

        mCopyAllowed = copyAllowed;
        return true;
    }

    @Nullable
    File getThumbnailFile(@Nullable File filesDir) {

        String path = getPath();
        if (!mHasThumbnail) {
            return null;
        }

        int pos = path.lastIndexOf('.');
        if (pos < 0) {

            return null;
        }

        String thumbPath = path.substring(0, pos) + "-thumbnail.jpg";
        return new File(filesDir, thumbPath);
    }

    @Nullable
    byte[] loadThumbnailData(@Nullable File filesDir) {

        File thumbnail = getThumbnailFile(filesDir);
        if (thumbnail == null || !thumbnail.exists()) {

            return null;
        }

        try (FileInputStream input = new FileInputStream(thumbnail)) {
            byte[] data = new byte[(int) thumbnail.length()];
            input.read(data);
            return data;

        } catch (Exception exception) {

            return null;
        }
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append(" path=");
            stringBuilder.append(mPath);
            stringBuilder.append("\n");
            stringBuilder.append(" extension=");
            stringBuilder.append(mExtension);
            stringBuilder.append("\n");
        }
        stringBuilder.append(" length=");
        stringBuilder.append(mLength);
        stringBuilder.append("\n");
        stringBuilder.append(" available=");
        stringBuilder.append(isAvailable());
        stringBuilder.append("\n");
    }

    /*
     * Override FileDescriptor methods
     */

    @Override
    @NonNull
    public String getPath() {

        if (mPath != null) {
            int pos = mPath.indexOf(Twinlife.CONVERSATIONS_DIR + "/");
            if (pos > 0) {
                return mPath.substring(pos);
            }
        }

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append(Twinlife.CONVERSATIONS_DIR);
        stringBuilder.append('/');
        stringBuilder.append(getTwincodeOutboundId());
        stringBuilder.append('/');
        stringBuilder.append(Long.valueOf(getSequenceId()));

        if (mExtension != null) {
            stringBuilder.append('.');
            stringBuilder.append(mExtension);
        }

        return stringBuilder.toString();
    }

    @Override
    public String getExtension() {

        return mExtension;
    }

    @Override
    public long getLength() {

        return mLength;
    }

    @Override
    public boolean isAvailable() {

        return mLength == mEnd;
    }

    @Override
    long getValue() {

        return mLength;
    }

    @Override
    @Nullable
    String serialize() {

        return mEnd + FIELD_SEPARATOR + (mExtension == null ? "" : mExtension);
    }

    @Override
    @NonNull
    ConversationService.Permission getPermission() {

        return ConversationService.Permission.SEND_FILE;
    }

    @Override
    int getFlags() {

        int flags = 0;
        if (mCopyAllowed) {
            flags |= FLAG_COPY_ALLOWED;
        }
        if (mHasThumbnail) {
            flags |= FLAG_HAS_THUMBNAIL;
        }
        return flags;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("FileDescriptorImpl\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Package specific Methods
    //

    public void setPath(String path) {

        mPath = path;
    }

    public long getEnd() {

        return mEnd;
    }

    public void setEnd(long end) {

        mEnd = end;
    }

    @Nullable
    Bitmap getThumbnail(@NonNull File filesDir) {

        if (!mHasThumbnail) {

            return null;
        }

        File thumbFile = getThumbnailFile(filesDir);
        if (thumbFile == null || !thumbFile.exists()) {

            return null;
        }

        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = false;
            return BitmapFactory.decodeFile(thumbFile.getPath(), opts);

        } catch (Throwable throwable) {

            return null;
        }
    }

    @Override
    void delete(@Nullable File filesDir) {
        if (DEBUG) {
            Log.d(LOG_TAG, "delete");
        }

        File file = new File(filesDir, getPath());
        Utils.deleteFile(LOG_TAG, file);

        // Check and delete the optional thumbnail.
        file = getThumbnailFile(filesDir);
        if (file != null) {
            Utils.deleteFile(LOG_TAG, file);
        }
    }

    //
    // Private Methods
    //

    protected FileDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                                 @Nullable DescriptorId replyTo, @Nullable String path,
                                 @Nullable String extension, long length, long end, boolean copyAllowed, boolean hasThumbnail) {

        super(new DescriptorId(0, twincodeOutboundId, sequenceId), 0, expireTimeout, sendTo, replyTo);

        if (DEBUG) {
            Log.d(LOG_TAG, "FileDescriptorImpl: expireTimeout=" + expireTimeout + " sendTo=" + sendTo + " path=" + path
                    + " extension=" + extension + " length=" + length + " available=" + end + " copyAllowed=" + copyAllowed);
        }

        mPath = path;
        mExtension = extension;
        mLength = length;
        mEnd = end;
        mCopyAllowed = copyAllowed;
        mHasThumbnail = hasThumbnail;
    }

    private FileDescriptorImpl(@NonNull DescriptorImpl descriptorImpl, String path, @Nullable String extension,
                               long length, long end, boolean copyAllowed) {

        super(descriptorImpl);

        if (DEBUG) {
            Log.d(LOG_TAG, "FileDescriptorImpl: descriptorImpl=" + descriptorImpl + " path=" + path
                    + " extension=" + extension + " length=" + length + " available=" + end + " copyAllowed=" + copyAllowed);
        }

        mPath = path;
        mExtension = extension;
        mLength = length;
        mEnd = end;
        mCopyAllowed = copyAllowed;
        mHasThumbnail = false;
    }
}
