/*
 *  Copyright (c) 2018-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 2
 *  Date: 2024/09/09
 * {
 *  "schemaId":"493e6d32-a023-455a-9952-c76162c319c9",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"GroupOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "fields":
 *  [
 *   {"name":"groupTwincodeId", "type":"UUID"}
 *   {"name":"memberTwincodeId", "type":"UUID"}
 *   {"name":"permissions", "type":"long"}
 *   {"name":"publicKey", "type":"String"}
 *   {"name":"signedOffTwincodeId", "type":[null, "UUID"]}
 *   {"name":"signature", "type":"String"}
 *  ]
 * }
 * </pre>
 *
 * Schema version 1
 *
 * {
 *  "schemaId":"493e6d32-a023-455a-9952-c76162c319c9",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"GroupOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "fields":
 *  [
 *   {"name":"groupTwincodeId", "type":"UUID"}
 *   {"name":"memberTwincodeId", "type":"UUID"}
 *   {"name":"permissions", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;

import java.util.UUID;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_18;

class GroupInviteOperation extends GroupOperation {
    private static final String LOG_TAG = "GroupJoinOperation";
    private static final boolean DEBUG = false;

    @Nullable
    private InvitationDescriptorImpl mInvitationDescriptorImpl;

    GroupInviteOperation(@NonNull Type type, @NonNull ConversationImpl conversationImpl,
                         @NonNull InvitationDescriptorImpl invitationDescriptor) {
        super(type, conversationImpl, invitationDescriptor);

        mInvitationDescriptorImpl = invitationDescriptor;
    }

    GroupInviteOperation(long id, @NonNull Type type, @NonNull DatabaseIdentifier conversationId,
                         long creationDate, long descriptor) {

        super(id, type, conversationId, creationDate, descriptor);

        mInvitationDescriptorImpl = null;
    }

    @Nullable
    InvitationDescriptorImpl getInvitationDescriptorImpl() {

        return mInvitationDescriptorImpl;
    }

    @Nullable
    public UUID getGroupId() {

        return mInvitationDescriptorImpl == null ? mGroupId : mInvitationDescriptorImpl.getGroupTwincodeId();
    }

    @Nullable
    public UUID getMemberId() {

        return mInvitationDescriptorImpl == null ? mMemberId : mInvitationDescriptorImpl.getMemberTwincodeId();
    }

    @Override
    @Nullable
    byte[] serialize() {

        // Nothing to serialize: information is in the invitation.
        return null;
    }

    public void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
        }
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        InvitationDescriptorImpl invitationDescriptorImpl = getInvitationDescriptorImpl();
        if (invitationDescriptorImpl == null) {
            DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (!(descriptorImpl instanceof InvitationDescriptorImpl)) {
                return ErrorCode.EXPIRED;
            }
            invitationDescriptorImpl = (InvitationDescriptorImpl) descriptorImpl;
            mInvitationDescriptorImpl = invitationDescriptorImpl;
        }

        if (!connection.preparePush(invitationDescriptorImpl)) {
            return ErrorCode.EXPIRED;
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (mType == Type.WITHDRAW_INVITE_GROUP) {
            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.RevokeInviteGroupIQ revokeInviteGroupIQ = new ConversationServiceIQ.RevokeInviteGroupIQ(connection.getFrom(), connection.getTo(),
                    requestId, majorVersion, minorVersion, invitationDescriptorImpl);

            final byte[] content = revokeInviteGroupIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_WITHDRAW_INVITE_GROUP, content);
            return ErrorCode.QUEUED;

        } else if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_18)) {
            final InviteGroupIQ inviteGroupIQ = new InviteGroupIQ(InviteGroupIQ.IQ_INVITE_GROUP_SERIALIZER_2, requestId, invitationDescriptorImpl);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_INVITE_GROUP, inviteGroupIQ);
            return ErrorCode.QUEUED;

        } else {
            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.InviteGroupIQ inviteGroupIQ = new ConversationServiceIQ.InviteGroupIQ(connection.getFrom(), connection.getTo(),
                    requestId, majorVersion, minorVersion, invitationDescriptorImpl);

            final byte[] content = inviteGroupIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_INVITE_GROUP, content);
            return ErrorCode.QUEUED;
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("GroupInviteOperation:");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Go";
        }
    }
}
