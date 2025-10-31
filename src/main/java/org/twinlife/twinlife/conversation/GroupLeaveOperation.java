/*
 *  Copyright (c) 2018-2025 twinlife SA.
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
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.util.UUID;

class GroupLeaveOperation extends GroupOperation {
    private static final String LOG_TAG = "GroupLeaveOperation";
    private static final boolean DEBUG = false;

    GroupLeaveOperation(@NonNull ConversationImpl conversationImpl, @NonNull Type type,
                        @NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId) {

        super(type, conversationImpl, groupTwincodeId, memberTwincodeId);
    }

    GroupLeaveOperation(long id, @NonNull Type type, @NonNull DatabaseIdentifier conversationId,
                        long creationDate, long descriptor, @Nullable byte[] content) {

        super(id, type, conversationId, creationDate, descriptor);

        if (content != null) {
            try {
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                final BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

                final int schemaVersion = decoder.readInt();
                if (schemaVersion == SCHEMA_VERSION_1 || schemaVersion == SCHEMA_VERSION_2) {
                    mGroupId = decoder.readUUID();
                    mMemberId = decoder.readUUID();
                }

            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "deserialize", exception);
                }
            }
        }
    }

    @Override
    @Nullable
    byte[] serialize() {

        return serializeOperation(mGroupId, mMemberId, 0, null, null, null);
    }

    @Override
    public ErrorCode executeInvoke(@NonNull ConversationServiceImpl conversationService,
                                   @NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "executeInvoke: conversationImpl=" + conversationImpl);
        }

        if (mType == Type.INVOKE_LEAVE_GROUP) {
            return conversationService.invokeLeaveOperation(conversationImpl, this);
        }

        return ErrorCode.SUCCESS;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        // Before sending the leave, verify IFF this is our member twincode that we have not re-joined the group.
        final ConversationImpl conversationImpl = connection.getConversation();
        final GroupConversationImpl group = conversationImpl.getGroup();
        if (group != null && group.getState() == ConversationService.GroupConversation.State.JOINED
                && group.getTwincodeOutboundId().equals(getMemberId())) {
            return ErrorCode.EXPIRED;
        }

        final UUID groupId = getGroupId();
        final UUID memberId = getMemberId();
        if (groupId == null || memberId == null) {
            return ErrorCode.BAD_REQUEST;
        }
        final int majorVersion = connection.getMaxPeerMajorVersion();
        final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);
        final boolean needLeadingPadding = connection.needLeadingPadding();
        final long requestId = connection.newRequestId();
        updateRequestId(requestId);

        final ConversationServiceIQ.LeaveGroupIQ leaveGroupIQ = new ConversationServiceIQ.LeaveGroupIQ(connection.getFrom(), connection.getTo(),
                requestId, majorVersion, minorVersion, groupId, memberId);

        final byte[] content = leaveGroupIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion, needLeadingPadding);
        connection.sendMessage(PeerConnectionService.StatType.IQ_SET_LEAVE_GROUP, content);
        return ErrorCode.QUEUED;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("GroupLeaveOperation:");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Go";
        }
    }
}
