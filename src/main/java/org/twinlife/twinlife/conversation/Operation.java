/*
 *  Copyright (c) 2016-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.PushNotificationOperation;
import org.twinlife.twinlife.SerializerException;

public class Operation implements Comparable<Operation> {
    private static final String LOG_TAG = "Operation";
    private static final boolean DEBUG = false;

    static final long NO_REQUEST_ID = -1L;
    public static final long ESTIMATED_SIZE = 256; // 16 + 4 + 4 + 4 + 16 + 8;

    public enum Type {
        RESET_CONVERSATION,
        SYNCHRONIZE_CONVERSATION,
        PUSH_OBJECT,
        PUSH_TRANSIENT_OBJECT,
        PUSH_FILE,
        UPDATE_DESCRIPTOR_TIMESTAMP,
        INVITE_GROUP,
        WITHDRAW_INVITE_GROUP,
        JOIN_GROUP,
        LEAVE_GROUP,
        UPDATE_GROUP_MEMBER,
        PUSH_GEOLOCATION,
        PUSH_TWINCODE,
        PUSH_COMMAND,
        UPDATE_ANNOTATIONS,
        UPDATE_OBJECT,

        // Operations that don't need the P2P connection to be opened.
        INVOKE_JOIN_GROUP,
        INVOKE_LEAVE_GROUP,
        INVOKE_ADD_MEMBER
    }

    private long mId;
    @NonNull
    protected final Type mType;
    @NonNull
    protected final DatabaseIdentifier mConversationId;
    protected final long mTimestamp;
    protected final long mDescriptor;
    protected volatile long mRequestId;

    protected Operation(@NonNull Type type, @NonNull ConversationImpl conversationImpl, @Nullable DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Operation: type=" + type + " conversationImpl=" + conversationImpl);
        }

        mId = 0;
        mType = type;
        mConversationId = conversationImpl.getDatabaseId();
        if (descriptorImpl != null) {
            mDescriptor = descriptorImpl.getDatabaseId();
        } else {
            mDescriptor = 0;
        }

        mTimestamp = System.currentTimeMillis();
        mRequestId = NO_REQUEST_ID;
    }

    protected Operation(@NonNull Type type, @NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Operation: type=" + type + " conversationImpl=" + conversationImpl);
        }

        mId = 0;
        mType = type;
        mConversationId = conversationImpl.getDatabaseId();
        mDescriptor = 0;
        mTimestamp = System.currentTimeMillis();
        mRequestId = NO_REQUEST_ID;
    }

    protected Operation(@NonNull Type type, @NonNull ConversationImpl conversationImpl, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Operation: type=" + type + " conversationImpl=" + conversationImpl);
        }

        mId = 0;
        mType = type;
        mConversationId = conversationImpl.getDatabaseId();
        mDescriptor = descriptorId.id;
        mTimestamp = System.currentTimeMillis();
        mRequestId = NO_REQUEST_ID;
    }

    protected Operation(long id, @NonNull Type type, @NonNull DatabaseIdentifier conversationId,
                        long creationDate, long descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Operation: id=" + id + " type=" + type + " conversationId=" + conversationId
                + " creationDate=" + creationDate + " descriptor=" + descriptor);
        }

        mId = id;
        mType = type;
        mConversationId = conversationId;
        mTimestamp = creationDate;
        mDescriptor = descriptor;
        mRequestId = NO_REQUEST_ID;
    }

    long getId() {

        return mId;
    }

    void setId(long id) {

        mId = id;
    }

    @NonNull
    Type getType() {

        return mType;
    }

    @NonNull
    DatabaseIdentifier getConversationId() {

        return mConversationId;
    }

    long getTimestamp() {

        return mTimestamp;
    }

    long getRequestId() {

        return mRequestId;
    }

    public void updateRequestId(long requestId) {

        mRequestId = requestId;
    }

    long getEstimatedSize() {

        return ESTIMATED_SIZE;
    }

    /**
     * Check if this operation is implemented by using a twincode invocation.
     * In that case, there is no need to setup a WebRTC data-channel.
     *
     * @return true if the operation is a twincode invocation.
     */
    boolean isInvoke() {

        return mType == Type.INVOKE_JOIN_GROUP || mType == Type.INVOKE_LEAVE_GROUP || mType == Type.INVOKE_ADD_MEMBER;
    }

    /**
     * Check if we can execute the operation immediately:
     * INVOKE_LEAVE_GROUP, INVOKE_JOIN_GROUP, INVOKE_ADD_MEMBER don't need a P2P connection.
     *
     * @param conversation the conversation onto which we must execute the operation.
     * @return true if we can execute the operation.
     */
    boolean canExecute(@NonNull ConversationImpl conversation) {

        return (mRequestId == Operation.NO_REQUEST_ID)
                && (mType == Type.INVOKE_JOIN_GROUP || mType == Type.INVOKE_LEAVE_GROUP || mType == Type.INVOKE_ADD_MEMBER
                || conversation.getState() == ConversationConnection.State.OPEN);
    }

    @NonNull
    PushNotificationOperation getNotification() {

        switch (getType()) {
            case PUSH_FILE:
                return PushNotificationOperation.PUSH_FILE;

            case PUSH_OBJECT:
                return PushNotificationOperation.PUSH_MESSAGE;

            default:
                return PushNotificationOperation.NOT_DEFINED;
        }
    }

    @Nullable
    byte[] serialize() {

        return null;
    }

    public ErrorCode executeInvoke(@NonNull ConversationServiceImpl conversationService, @NonNull ConversationImpl conversationImpl) {

        return ErrorCode.SUCCESS;
    }

    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {

        return ErrorCode.SUCCESS;
    }

    protected boolean isExpiredDescriptor(@NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isExpiredDescriptor: descriptorImpl=" + descriptorImpl);
        }

        return descriptorImpl.getDeletedTimestamp() > 0 || descriptorImpl.isExpired();
    }

    public void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append(" id=");
            stringBuilder.append(mId);
            stringBuilder.append(" type=");
            stringBuilder.append(mType);
            stringBuilder.append(" conversationId=");
            stringBuilder.append(mConversationId);
            stringBuilder.append(" timestamp=");
            stringBuilder.append(mTimestamp);
            stringBuilder.append(" requestId=");
            stringBuilder.append(mRequestId);
            stringBuilder.append(" descriptor=");
            stringBuilder.append(mDescriptor);
        }
    }

    public long getDescriptorId() {

        return mDescriptor;
    }

    @Override
    public int compareTo(@NonNull Operation operation2) {

        Type type2 = operation2.getType();
        if (mType == type2) {

            // Order by operation id when types are identical.
            return Long.compare(getId(), operation2.getId());
        }

        // Put invoke operations first (before synchronize).
        if (mType == Type.INVOKE_ADD_MEMBER || mType == Type.INVOKE_JOIN_GROUP || mType == Type.INVOKE_LEAVE_GROUP) {
            return -1;
        }
        if (type2 == Type.INVOKE_ADD_MEMBER || type2 == Type.INVOKE_JOIN_GROUP || type2 == Type.INVOKE_LEAVE_GROUP) {
            return 1;
        }
        if (mType == Type.SYNCHRONIZE_CONVERSATION) {
            return -1;
        }
        if (type2 == Type.SYNCHRONIZE_CONVERSATION) {
            return 1;
        }
        if (type2 == Type.PUSH_FILE) {

            return -1;
        }

        // Types are different but of equivalent, order by operation id.
        return Long.compare(getId(), operation2.getId());
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Operation:");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Op";
        }
    }
}
