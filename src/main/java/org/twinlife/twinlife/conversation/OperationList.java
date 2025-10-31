/*
 *  Copyright (c) 2019-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.PushNotificationContent;
import org.twinlife.twinlife.PushNotificationPriority;
import org.twinlife.twinlife.PushNotificationOperation;
import org.twinlife.twinlife.conversation.Operation.Type;

import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

/**
 * List of operations for a conversation.
 *
 *
 */
class OperationList implements Comparable<OperationList> {

    private static final String LOG_TAG = "OperationList";
    private static final boolean DEBUG = false;

    private final PushNotificationContent mNotification;

    @NonNull
    private final DatabaseIdentifier mConversationId;

    @Nullable
    private TreeSet<Operation> mOperations;

    @Nullable
    private ConversationImpl mConversationImpl;

    private long mDeadline;

    OperationList(@NonNull DatabaseIdentifier conversationId) {

        mConversationId = conversationId;
        mNotification = new PushNotificationContent();
        mDeadline = 0;
    }

    OperationList(@NonNull ConversationImpl conversationImpl) {

        mNotification = new PushNotificationContent();
        mConversationImpl = conversationImpl;
        mConversationId = conversationImpl.getDatabaseId();
        mDeadline = 0;
    }

    PushNotificationContent getNotificationContent() {

        if (mOperations != null && mNotification.estimatedSize == 0) {
            update();
        }

        return mNotification;
    }

    @NonNull
    DatabaseIdentifier getConversationId() {

        return mConversationId;
    }

    @Nullable
    ConversationImpl getConversation() {

        return mConversationImpl;
    }

    void setConversation(@NonNull ConversationImpl conversationImpl) {

        mConversationImpl = conversationImpl;
    }

    void setNotificationContent(PushNotificationContent notificationContent) {

        if (mOperations == null) {
            mNotification.estimatedSize = notificationContent.estimatedSize;
            mNotification.newestTimestamp = notificationContent.newestTimestamp;
            mNotification.operationCount = notificationContent.operationCount;
            mNotification.oldestTimestamp = notificationContent.oldestTimestamp;
            mNotification.operation = notificationContent.operation;
            mNotification.priority = notificationContent.priority;
            mNotification.synchronizeOp = notificationContent.synchronizeOp;
        }
    }

    boolean isEmpty() {

        return mOperations != null ? mOperations.isEmpty() : mNotification.operationCount == 0;
    }

    boolean hasConversation() {

        return mConversationImpl != null;
    }

    boolean hasSynchronizeOperation() {

        if (mOperations != null && mNotification.estimatedSize == 0) {
            update();
        }
        return mNotification.synchronizeOp;
    }

    int getCount() {

        return mOperations == null ? 0 : mOperations.size();
    }

    long getDeadline() {

        return mDeadline;
    }

    void setDeadline(long deadline) {

        mDeadline = deadline;
    }

    void clearDeadline() {

        mDeadline = 0;
    }

    Iterable<Operation> iterator() {

        return mOperations;
    }

    Operation getFirstOperation() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFirstOperation");
        }

        return mOperations == null || mOperations.isEmpty() ? null : mOperations.first();
    }

    boolean resetOperations() {
        if (DEBUG) {
            Log.d(LOG_TAG, "resetOperations");
        }

        boolean synchronizePeerNotification = false;

        // If the operations are loaded, reset the request ids.
        if (mOperations != null) {
            for (Operation operation : mOperations) {
                operation.updateRequestId(Operation.NO_REQUEST_ID);
            }
            if (!mOperations.isEmpty()) {
                Operation operation = getFirstOperation();
                synchronizePeerNotification = operation != null && operation.getType() != Type.SYNCHRONIZE_CONVERSATION;
            }
        }
        return synchronizePeerNotification;
    }

    void addOperations(@NonNull List<Operation> list) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addOperations: list=" + list);
        }

        for (Operation op : list) {
            addOperation(op);
        }
    }

    void addOperation(@NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addOperation: operation=" + operation);
        }

        mNotification.estimatedSize = 0;
        if (mOperations == null) {
            mOperations = new TreeSet<>();
        }
        mOperations.add(operation);
    }

    void removeOperation(@NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeOperation: operation=" + operation);
        }

        mNotification.estimatedSize = 0;
        if (mOperations != null) {
            mOperations.remove(operation);
        }
    }

    /**
     * Remove all or the operations that use one of the descriptor from the set.
     *
     * @param deletedOperations optional list of operation ids that have been deleted.
     */
    void removeOperations(@NonNull List<Long> deletedOperations) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeOperations: deletedOperations=" + deletedOperations);
        }

        mNotification.estimatedSize = 0;
        if (mOperations == null) {

            return;
        }

        Iterator<Operation> iterator = mOperations.iterator();
        while (iterator.hasNext()) {
            Operation operation = iterator.next();

            if (deletedOperations.remove(operation.getId())) {
                iterator.remove();
            }
        }
    }

    @Nullable
    Operation getOperation(long requestId) {

        if (mOperations != null) {
            for (Operation operation : mOperations) {
                if (operation.getRequestId() == requestId) {

                    return operation;
                }
            }
        }

        return null;
    }

    private void update() {
        if (DEBUG) {
            Log.d(LOG_TAG, "update");
        }

        if (mOperations == null) {

            return;
        }

        mNotification.synchronizeOp = false;
        mNotification.estimatedSize = 0;
        mNotification.oldestTimestamp = 0;
        mNotification.newestTimestamp = 0;
        mNotification.operationCount = mOperations.size();

        for (Operation operation : mOperations) {
            switch (operation.getNotification()) {
                case PUSH_MESSAGE:
                    mNotification.operation = operation.getNotification();
                    mNotification.priority = PushNotificationPriority.HIGH;
                    break;

                case PUSH_FILE:
                case PUSH_IMAGE:
                case PUSH_AUDIO:
                case PUSH_VIDEO:
                    mNotification.priority = PushNotificationPriority.HIGH;
                    if (mNotification.operation != PushNotificationOperation.PUSH_MESSAGE) {
                        mNotification.operation = operation.getNotification();
                    }
                    break;

                default:
                    if (operation.getType() == Operation.Type.SYNCHRONIZE_CONVERSATION) {
                        mNotification.synchronizeOp = true;
                    }
                    break;
            }
            mNotification.estimatedSize += operation.getEstimatedSize();
            if (mNotification.oldestTimestamp > operation.getTimestamp()) {
                mNotification.oldestTimestamp = operation.getTimestamp();
            }
            if (mNotification.newestTimestamp < operation.getTimestamp()) {
                mNotification.newestTimestamp = operation.getTimestamp();
            }
        }
    }

    @Override
    public int compareTo(@NonNull OperationList list) {
        if (DEBUG) {
            Log.d(LOG_TAG, "compareTo");
        }

        // If there is a deadline, sort on it.
        if (mDeadline != 0 && list.mDeadline != 0) {
            int result = Long.compare(mDeadline, list.mDeadline);
            if (result != 0) {

                return result;
            }
        } else if (mDeadline != 0) {

            return 1;
        } else if (list.mDeadline != 0) {

            return -1;
        }

        // Look at the first operation.
        if (mOperations == null || mOperations.isEmpty()) {

            return -1;
        }
        if (list.mOperations == null || list.mOperations.isEmpty()) {

            return 1;
        }

        int result = Long.compare(mOperations.first().getTimestamp(), list.mOperations.first().getTimestamp());
        if (result != 0) {

            return result;
        }
        result = mOperations.size() - list.mOperations.size();
        if (result != 0) {

            return result;
        }
        return mConversationId.compareTo(list.mConversationId);
    }
}
