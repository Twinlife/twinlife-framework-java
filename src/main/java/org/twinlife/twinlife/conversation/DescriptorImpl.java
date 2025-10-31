/*
 *  Copyright (c) 2015-2023 twinlife SA.
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
 *  "schemaId":"6aa12d75-04db-4994-8c01-eecb6e1a0cf7",
 *  "schemaVersion":"4",
 *
 *  "type":"record",
 *  "name":"Descriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"expireTimeout", "type":"long"}
 *   {"name":"sendTo", "type":[null, "UUID"]}
 *   {"name":"replyTo", "type":["null", {
 *       {"name":"twincodeOutboundId", "type":"uuid"},
 *       {"name":"sequenceId", "type":"long"}
 *     }
 *   }
 *  ]
 * }
 *
 * Schema version 3
 *  Date: 2017/07/29
 *
 * {
 *  "schemaId":"6aa12d75-04db-4994-8c01-eecb6e1a0cf7",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"Descriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"twincodeOutboundId", "type":"uuid"}
 *   {"name":"sequenceId", "type":"long"}
 *   {"name":"createdTimestamp", "type":"long"}
 *   {"name":"updatedTimestamp", "type":"long"}
 *   {"name":"sentTimestamp", "type":"long"}
 *   {"name":"receivedTimestamp", "type":"long"}
 *   {"name":"readTimestamp", "type":"long"}
 *   {"name":"deletedTimestamp", "type":"long"}
 *   {"name":"peerDeletedTimestamp", "type":"long"}
 *  ]
 * }
 *
 * {
 *  "type":"record",
 *  "name":"DescriptorTimestamps",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "fields":
 *  [
 *   {"name":"updatedTimestamp", "type":"long"}
 *   {"name":"sentTimestamp", "type":"long"}
 *   {"name":"receivedTimestamp", "type":"long"}
 *   {"name":"readTimestamp", "type":"long"}
 *   {"name":"deletedTimestamp", "type":"long"}
 *   {"name":"peerDeletedTimestamp", "type":"long"}
 *  ]
 * }
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorAnnotation;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class DescriptorImpl implements ConversationService.Descriptor {
    private static final String LOG_TAG = "DescriptorImpl";
    private static final boolean DEBUG = false;

    static final int FLAG_COPY_ALLOWED = 0x01;
    static final int FLAG_HAS_THUMBNAIL = 0x02;
    static final int FLAG_UPDATED = 0x04;

    // Flags used by the CallDescriptor
    static final int FLAG_VIDEO = 0x10;
    static final int FLAG_INCOMING_CALL = 0x20;
    static final int FLAG_ACCEPTED_CALL = 0x40;

    static final String FIELD_SEPARATOR = "\n";

    static class DescriptorImplSerializer_4 extends Serializer {

        DescriptorImplSerializer_4(@NonNull UUID schemaId, int schemaVersion, @NonNull Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            DescriptorImpl descriptorImpl = (DescriptorImpl) object;
            encoder.writeLong(descriptorImpl.mExpireTimeout);
            encoder.writeOptionalUUID(descriptorImpl.mSendTo);
            if (descriptorImpl.mReplyTo != null) {
                encoder.writeEnum(1);
                encoder.writeUUID(descriptorImpl.mReplyTo.twincodeOutboundId);
                encoder.writeLong(descriptorImpl.mReplyTo.sequenceId);
            } else {
                encoder.writeEnum(0);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }

        @Nullable
        static DescriptorId readOptionalDescriptorId(@NonNull Decoder decoder) throws SerializerException {

            if (decoder.readEnum() == 1) {
                UUID twincodeOutboundId = decoder.readUUID();
                long sequenceId = decoder.readLong();
                return new DescriptorId(0, twincodeOutboundId, sequenceId);
            } else {
                return null;
            }
        }
    }

    static class DescriptorImplSerializer_3 extends Serializer {

        DescriptorImplSerializer_3(@NonNull UUID schemaId, int schemaVersion, @NonNull Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            DescriptorImpl descriptorImpl = (DescriptorImpl) object;
            encoder.writeUUID(descriptorImpl.mDescriptorId.twincodeOutboundId);
            encoder.writeLong(descriptorImpl.mDescriptorId.sequenceId);
            encoder.writeLong(descriptorImpl.mCreatedTimestamp);
            encoder.writeLong(descriptorImpl.mUpdatedTimestamp);
            encoder.writeLong(descriptorImpl.mSentTimestamp);
            encoder.writeLong(descriptorImpl.mReceivedTimestamp);
            encoder.writeLong(descriptorImpl.mReadTimestamp);
            encoder.writeLong(descriptorImpl.mDeletedTimestamp);
            encoder.writeLong(descriptorImpl.mPeerDeletedTimestamp);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            UUID twincodeOutboundId = decoder.readUUID();
            long sequenceId = decoder.readLong();
            long createdTimestamp = decoder.readLong();
            long updatedTimestamp = decoder.readLong();
            long sentTimestamp = decoder.readLong();
            long receivedTimestamp = decoder.readLong();
            long readTimestamp = decoder.readLong();
            long deletedTimestamp = decoder.readLong();
            long peerDeletedTimestamp = decoder.readLong();

            return new DescriptorImpl(twincodeOutboundId, sequenceId, createdTimestamp, updatedTimestamp, sentTimestamp, receivedTimestamp, readTimestamp, deletedTimestamp,
                    peerDeletedTimestamp);
        }
    }

    @NonNull
    private DescriptorId mDescriptorId;
    private long mExpireTimeout;
    @Nullable
    private final UUID mSendTo;
    @Nullable
    private final DescriptorId mReplyTo;
    @Nullable
    private ConversationService.Descriptor mReplyToDescriptor;
    private long mConversationId;
    private long mCreatedTimestamp;
    private volatile long mUpdatedTimestamp;
    private long mSentTimestamp;
    private volatile long mReceivedTimestamp;
    private volatile long mReadTimestamp;
    private volatile long mDeletedTimestamp;
    private volatile long mPeerDeletedTimestamp;
    private List<DescriptorAnnotation> mAnnotations;

    private DescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long createdTimestamp, long updatedTimestamp, long sentTimestamp,
                           long receivedTimestamp, long readTimestamp, long deletedTimestamp, long peerDeletedTimestamp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "DescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId + " createdTimestamp=" + createdTimestamp +
                    " updatedTimestamp=" + updatedTimestamp + " sentTimestamp=" + sentTimestamp + " receivedTimestamp=" + receivedTimestamp +
                    " readTimestamp=" + readTimestamp + " deletedTimestamp=" + deletedTimestamp + " peerDeletedTimestamp=" + peerDeletedTimestamp);
        }

        mDescriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
        mExpireTimeout = 0L;
        mSendTo = null;
        mReplyTo = null;

        mCreatedTimestamp = createdTimestamp; // Math.min(createdTimestamp, System.currentTimeMillis());
        mUpdatedTimestamp = updatedTimestamp;
        mSentTimestamp = sentTimestamp;
        mReceivedTimestamp = receivedTimestamp;
        mReadTimestamp = readTimestamp;
        mDeletedTimestamp = deletedTimestamp;
        mPeerDeletedTimestamp = peerDeletedTimestamp;
    }

    DescriptorImpl(@NonNull DescriptorId descriptorId, long cid, long expireTimeout, @Nullable UUID sendTo,
                   @Nullable DescriptorId replyTo) {
        if (DEBUG) {
            Log.d(LOG_TAG, "DescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid);
        }

        mDescriptorId = descriptorId;
        mConversationId = cid;
        mExpireTimeout = expireTimeout;
        mSendTo = sendTo;
        mReplyTo = replyTo;

        mCreatedTimestamp = System.currentTimeMillis();
        mUpdatedTimestamp = 0L;
        mSentTimestamp = 0L;
        mReceivedTimestamp = 0L;
        mReadTimestamp = 0L;
        mDeletedTimestamp = 0L;
        mPeerDeletedTimestamp = 0L;
    }

    public DescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                          @Nullable DescriptorId replyTo, long createdTimestamp, long sentTimestamp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "DescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId);
        }

        mDescriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
        mSendTo = sendTo;
        mReplyTo = replyTo;

        mCreatedTimestamp = createdTimestamp;
        mUpdatedTimestamp = 0L;
        mSentTimestamp = sentTimestamp;
        mReceivedTimestamp = 0L;
        mReadTimestamp = 0L;
        mDeletedTimestamp = 0L;
        mPeerDeletedTimestamp = 0L;
        mExpireTimeout = expireTimeout;
    }

    DescriptorImpl(@NonNull DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                   @Nullable DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                   long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "DescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid);
        }

        mDescriptorId = descriptorId;
        mSendTo = sendTo;
        mReplyTo = replyTo;
        mConversationId = cid;

        mCreatedTimestamp = creationDate;
        mUpdatedTimestamp = updateDate;
        mSentTimestamp = sendDate;
        mReceivedTimestamp = receiveDate;
        mReadTimestamp = readDate;
        mDeletedTimestamp = deleteDate;
        mPeerDeletedTimestamp = peerDeleteDate;
        mExpireTimeout = expireTimeout;
    }

    DescriptorImpl(@NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "DescriptorImpl: descriptorImpl=" + descriptorImpl);
        }

        mDescriptorId = descriptorImpl.mDescriptorId;
        mSendTo = descriptorImpl.mSendTo;
        mReplyTo = descriptorImpl.mReplyTo;
        mConversationId = descriptorImpl.mConversationId;
        mReplyToDescriptor = descriptorImpl.mReplyToDescriptor;

        mCreatedTimestamp = descriptorImpl.mCreatedTimestamp;
        mUpdatedTimestamp = descriptorImpl.mUpdatedTimestamp;
        mSentTimestamp = descriptorImpl.mSentTimestamp;
        mReceivedTimestamp = descriptorImpl.mReceivedTimestamp;
        mReadTimestamp = descriptorImpl.mReadTimestamp;
        mDeletedTimestamp = descriptorImpl.mDeletedTimestamp;
        mPeerDeletedTimestamp = descriptorImpl.mPeerDeletedTimestamp;
        mExpireTimeout = descriptorImpl.mExpireTimeout;
        mAnnotations = descriptorImpl.mAnnotations;
    }

    public long getDatabaseId() {

        return mDescriptorId.id;
    }

    long getConversationId() {

        return mConversationId;
    }

    @NonNull
    ConversationService.Permission getPermission() {

        return ConversationService.Permission.SEND_MESSAGE;
    }

    @Nullable
    DescriptorImpl createForward(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout,
                                 @Nullable UUID sendTo, boolean copyAllowed) {

        throw new IllegalArgumentException("invalid call");
    }

    /*
     * Override Descriptor methods
     */

    @Override
    public Type getType() {

        return Type.DESCRIPTOR;
    }

    @Override
    @NonNull
    public UUID getTwincodeOutboundId() {

        return mDescriptorId.twincodeOutboundId;
    }

    @Override
    public long getSequenceId() {

        return mDescriptorId.sequenceId;
    }

    @Override
    public long getCreatedTimestamp() {

        return mCreatedTimestamp;
    }

    @Override
    @NonNull
    public DescriptorId getDescriptorId() {

        return mDescriptorId;
    }

    @Override
    @Nullable
    public DescriptorId getReplyToDescriptorId() {

        return mReplyTo;
    }

    @Override
    public void setReplyToDescriptor(@Nullable ConversationService.Descriptor replyToDescriptor) {
        mReplyToDescriptor = replyToDescriptor;
    }

    @Override
    @Nullable
    public ConversationService.Descriptor getReplyToDescriptor() {
        return mReplyToDescriptor;
    }

    @Override
    @Nullable
    public UUID getSendTo() {

        return mSendTo;
    }

    @Override
    public long getUpdatedTimestamp() {

        return mUpdatedTimestamp;
    }

    @Override
    public long getSentTimestamp() {

        return mSentTimestamp;
    }

    @Override
    public long getReceivedTimestamp() {

        return mReceivedTimestamp;
    }

    @Override
    public long getReadTimestamp() {

        return mReadTimestamp;
    }

    @Override
    public long getDeletedTimestamp() {

        return mDeletedTimestamp;
    }

    @Override
    public long getPeerDeletedTimestamp() {

        return mPeerDeletedTimestamp;
    }

    @Override
    public long getExpireTimestamp() {

        if (mExpireTimeout <= 0 || mReadTimestamp <= 0) {
            return -1;

        } else {

            return mReadTimestamp + mExpireTimeout;
        }
    }

    @Override
    public long getExpireTimeout() {

        return mExpireTimeout;
    }

    @Override
    @Nullable
    public DescriptorAnnotation getAnnotation(@NonNull ConversationService.AnnotationType type) {

        if (mAnnotations != null) {
            for (DescriptorAnnotation annotation : mAnnotations) {
                if (annotation.getType() == type) {

                    return annotation;
                }
            }
        }
        return null;
    }

    @Override
    public List<DescriptorAnnotation> getAnnotations(@NonNull ConversationService.AnnotationType type) {

        List<DescriptorAnnotation> result = null;
        if (mAnnotations != null) {
            for (DescriptorAnnotation annotation : mAnnotations) {
                if (annotation.getType() == type) {

                    if (result == null) {
                        result = new ArrayList<>();
                    }
                    result.add(annotation);
                }
            }
        }
        return result;
    }

    @Nullable
    public List<DescriptorAnnotation> getAnnotations() {

        return mAnnotations;
    }

    //
    // Package specific Methods
    //

    /**
     * Set the conversationId and database descriptor Id for a descriptor that was received from a peer.
     *
     * @param cid the conversation database id.
     * @param did the descriptor database id.
     */
    void updateDatabaseIds(long cid, long did) {

        mConversationId = cid;
        mDescriptorId = new DescriptorId(did, mDescriptorId.twincodeOutboundId, mDescriptorId.sequenceId);
    }

    void setUpdatedTimestamp(long timestamp) {

        mUpdatedTimestamp = timestamp;
    }

    public void setSentTimestamp(long timestamp) {

        mSentTimestamp = timestamp;
    }

    public void setReceivedTimestamp(long timestamp) {

        mReceivedTimestamp = timestamp;
    }

    void setReadTimestamp(long timestamp) {

        mReadTimestamp = timestamp;
    }

    void setDeletedTimestamp(long timestamp) {

        mDeletedTimestamp = timestamp;
    }

    void setPeerDeletedTimestamp(long timestamp) {

        mPeerDeletedTimestamp = timestamp;
    }

    void adjustCreatedAndSentTimestamps(long offset) {

        mCreatedTimestamp += offset;
        mSentTimestamp += offset;

        // Insure that createdTimestamp is not in the future
        long now = System.currentTimeMillis();
        if (mCreatedTimestamp > now) {
            mCreatedTimestamp = now;
        }
        if (mSentTimestamp > now) {
            mSentTimestamp = now;
        }
    }

    boolean setExpireTimeout(@Nullable Long timeout) {

        if (timeout == null || timeout == mExpireTimeout) {
            return false;
        }

        mExpireTimeout = timeout;
        return true;
    }

    @Override
    public boolean isExpired() {

        // No expiration timeout or message not yet read.
        if (mExpireTimeout <= 0 || mReadTimestamp == 0) {

            return false;
        }

        // Message was not delivered: consider it has expired.
        if (mReadTimestamp < 0) {

            return true;
        }

        // Message was delivered and read: check the deadline.
        long now = System.currentTimeMillis();
        return mExpireTimeout + mReadTimestamp < now;
    }

    void delete(@Nullable File filesDir) {
        if (DEBUG) {
            Log.d(LOG_TAG, "delete");
        }
    }

    void addAnnotation(@NonNull DescriptorAnnotation annotation) {

        if (mAnnotations == null) {
            mAnnotations = new ArrayList<>();
        }

        mAnnotations.add(annotation);
    }

    void setAnnotations(@Nullable List<DescriptorAnnotation> annotations) {

        mAnnotations = annotations;
    }

    void deserializeTimestamps(@NonNull byte[] bytes) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deserializeTimestamps: bytes=" + Arrays.toString(bytes));
        }

        ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
        BinaryDecoder binaryDecoder = new BinaryDecoder(inputStream);
        mUpdatedTimestamp = binaryDecoder.readLong();
        mSentTimestamp = binaryDecoder.readLong();
        mReceivedTimestamp = binaryDecoder.readLong();
        mReadTimestamp = binaryDecoder.readLong();
        mDeletedTimestamp = binaryDecoder.readLong();
        mPeerDeletedTimestamp = binaryDecoder.readLong();
    }

    @SuppressWarnings("WeakerAccess")
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        stringBuilder.append(" descriptorId=");
        stringBuilder.append(mDescriptorId);
        stringBuilder.append("\n");
        stringBuilder.append(" createdTimestamp=");
        stringBuilder.append(mCreatedTimestamp);
        stringBuilder.append("\n");
        stringBuilder.append(" updatedTimestamp=");
        stringBuilder.append(mUpdatedTimestamp);
        stringBuilder.append("\n");
        stringBuilder.append(" sentTimestamp=");
        stringBuilder.append(mSentTimestamp);
        stringBuilder.append("\n");
        stringBuilder.append(" receivedTimestamp=");
        stringBuilder.append(mReceivedTimestamp);
        stringBuilder.append("\n");
        stringBuilder.append(" readTimestamp=");
        stringBuilder.append(mReadTimestamp);
        stringBuilder.append("\n");
        stringBuilder.append(" deletedTimestamp=");
        stringBuilder.append(mDeletedTimestamp);
        stringBuilder.append("\n");
        stringBuilder.append(" peerDeletedTimestamp=");
        stringBuilder.append(mPeerDeletedTimestamp);
        stringBuilder.append("\n");
    }

    @Nullable
    static DescriptorImpl extractDescriptor(@NonNull SerializerFactory serializerFactory, @NonNull UUID twincodeOutboundId,
                                            long sequenceId, long createdTimestamp,
                                            @NonNull byte[] content, @Nullable byte[] timestamps) throws SerializerException {

        ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
        BinaryDecoder binaryDecoder = new BinaryDecoder(inputStream);
        DescriptorImpl descriptorImpl = null;
        final UUID schemaId = binaryDecoder.readUUID();
        final int schemaVersion = binaryDecoder.readInt();

        if (ObjectDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            switch (schemaVersion) {
                case ObjectDescriptorImpl.SCHEMA_VERSION_5:
                    descriptorImpl = (DescriptorImpl) ObjectDescriptorImpl.SERIALIZER_5.deserialize(serializerFactory, binaryDecoder, twincodeOutboundId, sequenceId, createdTimestamp);
                    if (timestamps != null) {
                        descriptorImpl.deserializeTimestamps(timestamps);
                    }
                    break;

                case ObjectDescriptorImpl.SCHEMA_VERSION_4:
                case ObjectDescriptorImpl.SCHEMA_VERSION_3:
                    descriptorImpl = (DescriptorImpl) ObjectDescriptorImpl.SERIALIZER_4.deserialize(serializerFactory, binaryDecoder);
                    if (timestamps != null) {
                        descriptorImpl.deserializeTimestamps(timestamps);
                    }
                    break;

                default:
                    Log.e(LOG_TAG, "Old version " + schemaVersion);
                    break;
            }

        } else if (FileDescriptorImpl.FILE_DESCRIPTOR_SCHEMA_ID.equals(schemaId)) {
            if (FileDescriptorImpl.FILE_SCHEMA_VERSION_4 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) FileDescriptorImpl.FILE_SERIALIZER_4.deserialize(binaryDecoder, twincodeOutboundId, sequenceId, createdTimestamp);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (FileDescriptorImpl.FILE_SCHEMA_VERSION_3 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) FileDescriptorImpl.FILE_SERIALIZER_3.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else {
                Log.e(LOG_TAG, "Old version " + schemaVersion);
            }

        } else if (ImageDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            if (ImageDescriptorImpl.SCHEMA_VERSION_4 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) ImageDescriptorImpl.SERIALIZER_4.deserialize(binaryDecoder, twincodeOutboundId, sequenceId, createdTimestamp);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (ImageDescriptorImpl.SCHEMA_VERSION_3 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) ImageDescriptorImpl.SERIALIZER_3.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (ImageDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) ImageDescriptorImpl.SERIALIZER_2.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else {
                Log.e(LOG_TAG, "Old version " + schemaVersion);
            }

        } else if (AudioDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            if (AudioDescriptorImpl.SCHEMA_VERSION_3 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) AudioDescriptorImpl.SERIALIZER_3.deserialize(binaryDecoder, twincodeOutboundId, sequenceId, createdTimestamp);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (AudioDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) AudioDescriptorImpl.SERIALIZER_2.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (AudioDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) AudioDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else {
                Log.e(LOG_TAG, "Old version " + schemaVersion);
            }

        } else if (VideoDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            if (VideoDescriptorImpl.SCHEMA_VERSION_3 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) VideoDescriptorImpl.SERIALIZER_3.deserialize(binaryDecoder, twincodeOutboundId, sequenceId, createdTimestamp);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (VideoDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) VideoDescriptorImpl.SERIALIZER_2.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (VideoDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) VideoDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else {
                Log.e(LOG_TAG, "Old version " + schemaVersion);
            }

        } else if (NamedFileDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            if (NamedFileDescriptorImpl.SCHEMA_VERSION_3 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) NamedFileDescriptorImpl.SERIALIZER_3.deserialize(binaryDecoder, twincodeOutboundId, sequenceId, createdTimestamp);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (NamedFileDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) NamedFileDescriptorImpl.SERIALIZER_2.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (NamedFileDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) NamedFileDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else {
                Log.e(LOG_TAG, "Old version " + schemaVersion);
            }

        } else if (InvitationDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            if (InvitationDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) InvitationDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            }
        } else if (GeolocationDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            if (GeolocationDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) GeolocationDescriptorImpl.SERIALIZER_2.deserialize(binaryDecoder, twincodeOutboundId, sequenceId, createdTimestamp);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (GeolocationDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) GeolocationDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else {
                Log.e(LOG_TAG, "Old version " + schemaVersion);
            }
        } else if (TwincodeDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            if (TwincodeDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) TwincodeDescriptorImpl.SERIALIZER_2.deserialize(binaryDecoder, twincodeOutboundId, sequenceId, createdTimestamp);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else if (TwincodeDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) TwincodeDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else {
                Log.e(LOG_TAG, "Old version " + schemaVersion);
            }
        } else if (CallDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            if (CallDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) CallDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            }
        } else if (ClearDescriptorImpl.SCHEMA_ID.equals(schemaId)) {
            if (ClearDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                descriptorImpl = (DescriptorImpl) ClearDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);
                if (timestamps != null) {
                    descriptorImpl.deserializeTimestamps(timestamps);
                }
            } else {
                Log.e(LOG_TAG, "Old version " + schemaVersion);
            }
        }

        return descriptorImpl;
    }

    @Nullable
    String serialize() {

        return null;
    }

    int getFlags() {

        return 0;
    }

    long getValue() {

        return 0;
    }

    @Nullable
    static String[] extract(@Nullable String content) {
        if (DEBUG) {
            Log.d(LOG_TAG, "extract: content=" + content);
        }

        if (content == null) {
            return null;
        }

        return content.split(FIELD_SEPARATOR);
    }

    static long extractLong(@Nullable String[] args, int pos, long defaultValue) {
        if (DEBUG) {
            Log.d(LOG_TAG, "extractLong: args=" + Arrays.toString(args) + " pos=" + pos + " defaultValue=" + defaultValue);
        }

        if (args == null || pos < 0 || pos >= args.length) {
            return defaultValue;
        }

        try {
            return Long.parseLong(args[pos]);

        } catch (Exception exception) {
            return defaultValue;
        }
    }

    static double extractDouble(@Nullable String[] args, int pos, double defaultValue) {
        if (DEBUG) {
            Log.d(LOG_TAG, "extractDouble: args=" + Arrays.toString(args) + " pos=" + pos + " defaultValue=" + defaultValue);
        }

        if (args == null || pos < 0 || pos >= args.length) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(args[pos]);

        } catch (Exception exception) {
            return defaultValue;
        }
    }

    @Nullable
    static String extractString(@Nullable String[] args, int pos, @Nullable String defaultValue) {
        if (DEBUG) {
            Log.d(LOG_TAG, "extractString: args=" + Arrays.toString(args) + " pos=" + pos + " defaultValue=" + defaultValue);
        }

        if (args == null || pos < 0 || pos >= args.length) {
            return defaultValue;
        }

        return args[pos];
    }

    @NonNull
    static UUID extractUUID(@Nullable String[] args, int pos, @NonNull UUID defaultValue) {
        if (DEBUG) {
            Log.d(LOG_TAG, "extractUUID: args=" + Arrays.toString(args) + " pos=" + pos + " defaultValue=" + defaultValue);
        }

        if (args == null || pos < 0 || pos >= args.length) {
            return defaultValue;
        }

        UUID uuid = Utils.UUIDFromString(args[pos]);
        return uuid == null ? defaultValue : uuid;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("DescriptorImpl ");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }
}
