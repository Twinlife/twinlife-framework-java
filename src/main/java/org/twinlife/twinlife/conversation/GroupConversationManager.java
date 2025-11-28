/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.InvitationDescriptor;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeInboundService;
import org.twinlife.twinlife.TwincodeInvocation;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.crypto.CryptoServiceImpl;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundServiceImpl;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utils;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.twinlife.twinlife.conversation.Operation.Type.INVOKE_ADD_MEMBER;
import static org.twinlife.twinlife.conversation.Operation.Type.INVOKE_JOIN_GROUP;
import static org.twinlife.twinlife.conversation.Operation.Type.JOIN_GROUP;

/**
 * Group management with:
 * - invitation process,
 * - join process,
 * - leave process
 */
class GroupConversationManager {
    private static final String LOG_TAG = "GroupConversationMgr";
    private static final boolean DEBUG = false;

    @NonNull
    private final ConversationServiceImpl mConversationService;
    private final ConversationServiceProvider mServiceProvider;
    private final ConversationServiceScheduler mScheduler;
    private final CryptoServiceImpl mCryptoService;
    private final TwincodeOutboundServiceImpl mTwincodeOutboundService;
    private final TwincodeInboundService mTwincodeInboundService;
    private final TwinlifeImpl mTwinlifeImpl;
    private final Executor mTwinlifeExecutor;
    private final TwincodeInboundService.InvocationListener mJoinHandler;
    private final TwincodeInboundService.InvocationListener mLeaveHandler;
    private final TwincodeInboundService.InvocationListener mOnJoinHandler;

    enum AddStatus {
        ERROR,
        NO_CHANGE,
        NEW_MEMBER
    }

    private final class JoinGroupInvocation implements TwincodeInboundService.InvocationListener {

        @Override
        public ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "JoinGroupInvocation.onInvokeTwincode: invocation=" + invocation);
            }

            // Perform the join group member invocation only when the invocation is encrypted and signed.
            // BUT it is not trusted yet.
            if (invocation.publicKey == null) {
                Log.e(LOG_TAG, "joinGroup no public Key");
                return ErrorCode.NOT_AUTHORIZED_OPERATION;
            }
            if (invocation.attributes == null) {
                return ErrorCode.BAD_REQUEST;
            }

            final UUID memberTwincodeId = invocation.peerTwincodeId;
            final UUID signedOffByTwincodeId = AttributeNameValue.getUUIDAttribute(invocation.attributes, ConversationProtocol.PARAM_SIGNED_OFF_TWINCODE_ID);
            final UUID groupTwincodeId = AttributeNameValue.getUUIDAttribute(invocation.attributes, ConversationProtocol.PARAM_GROUP_TWINCODE_ID);
            final String signature = AttributeNameValue.getStringAttribute(invocation.attributes, ConversationProtocol.PARAM_SIGNATURE);
            if (groupTwincodeId == null || memberTwincodeId == null || signedOffByTwincodeId == null || signature == null) {
                return ErrorCode.BAD_REQUEST;
            }

            if (Logger.INFO) {
                Logger.info(LOG_TAG, "receive invoke join ", Utils.toLog(groupTwincodeId), " for ", Utils.toLog(memberTwincodeId),
                        " signed-by ", Utils.toLog(signedOffByTwincodeId));
            }

            final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
            if (groupConversation == null) {
                return ErrorCode.EXPIRED;
            }

            // The join invocation request is made by a new member and we don't yet trust its public key.
            // This is why the join was signed by a member that we trust and we must verify the join signature.
            final GroupMemberConversationImpl signedOffBy = groupConversation.getMember(signedOffByTwincodeId);
            if (signedOffBy == null) {
                return ErrorCode.NO_PUBLIC_KEY;
            }

            final TwincodeOutbound signedOffTwincode = signedOffBy.getPeerTwincodeOutbound();
            if (signedOffTwincode == null || !signedOffTwincode.isSigned()) {
                return ErrorCode.NO_PUBLIC_KEY;
            }

            final long permissions = AttributeNameValue.getLongAttribute(invocation.attributes, ConversationProtocol.PARAM_PERMISSIONS, groupConversation.getJoinPermissions());
            final ErrorCode verifyResult = verifySignature(signedOffTwincode, groupTwincodeId, memberTwincodeId,
                    invocation.publicKey, permissions, signature);
            if (verifyResult != ErrorCode.SUCCESS) {
                return verifyResult;
            }

            // This join is verified, we can add the member after getting and verifying its attributes with the public key.
            // Now, we trust this member because it was signed by an existing member.
            mTwincodeOutboundService.getSignedTwincodeWithSecret(memberTwincodeId, invocation.publicKey, invocation.keyIndex, invocation.secretKey, TrustMethod.PEER, (ErrorCode errorCode, TwincodeOutbound twincodeOutbound) -> {

                // If we are offline or timed out don't acknowledge the invocation.
                if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                    return;
                }
                if (twincodeOutbound == null || errorCode != ErrorCode.SUCCESS) {
                    mTwincodeInboundService.acknowledgeInvocation(invocation.invocationId, errorCode);
                    return;
                }

                final List<OnJoinGroupIQ.MemberInfo> members = new ArrayList<>();
                addMember(groupConversation, twincodeOutbound, permissions, null, members, false, null, null);
                invokeOnJoin(groupConversation, twincodeOutbound, invocation.publicKey, members, invocation.invocationId);
            });
            return ErrorCode.QUEUED;
        }
    }

    private final class OnJoinGroupInvocation implements TwincodeInboundService.InvocationListener {

        @Override
        public ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "OnJoinGroupInvocation.onInvokeTwincode: invocation=" + invocation);
            }

            // Perform the join group member invocation only when the invocation is trusted (encrypted and signed).
            if (invocation.publicKey == null || invocation.trustMethod == TrustMethod.NONE) {
                Log.e(LOG_TAG, "on-joinGroup no public Key");
                return ErrorCode.NOT_AUTHORIZED_OPERATION;
            }
            if (invocation.attributes == null) {
                return ErrorCode.BAD_REQUEST;
            }

            final UUID signedOffByTwincodeId = invocation.peerTwincodeId;
            final UUID groupTwincodeId = AttributeNameValue.getUUIDAttribute(invocation.attributes, ConversationProtocol.PARAM_GROUP_TWINCODE_ID);
            final String signature = AttributeNameValue.getStringAttribute(invocation.attributes, ConversationProtocol.PARAM_SIGNATURE);
            if (groupTwincodeId == null || signedOffByTwincodeId == null || signature == null) {
                return ErrorCode.BAD_REQUEST;
            }

            if (Logger.INFO) {
                Logger.info(LOG_TAG, "receive invoke on-join ", Utils.toLog(groupTwincodeId), " from ",
                        Utils.toLog(signedOffByTwincodeId));
            }

            final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
            if (groupConversation == null) {
                return ErrorCode.EXPIRED;
            }

            final TwincodeOutbound twincodeOutbound = groupConversation.getTwincodeOutbound();
            if (twincodeOutbound == null) {
                return ErrorCode.EXPIRED;
            }
            final GroupMemberConversationImpl signedOffBy = groupConversation.getMember(signedOffByTwincodeId);
            if (signedOffBy == null) {
                return ErrorCode.NO_PUBLIC_KEY;
            }

            // The join was signed by a member and we must know it to verify its signature.
            final TwincodeOutbound signedOffTwincode = signedOffBy.getPeerTwincodeOutbound();
            if (signedOffTwincode == null || !signedOffTwincode.isSigned()) {
                return ErrorCode.NO_PUBLIC_KEY;
            }

            // Save the secret that was given by the peer to decrypt its SDPs and set the FLAG_ENCRYPT.
            if (invocation.secretKey != null) {
                mCryptoService.saveSecretKey(twincodeOutbound, signedOffTwincode, invocation.secretKey, invocation.keyIndex);
            }

            final ConversationImpl conversationImpl = groupConversation.getIncomingConversation();
            while (true) {
                final AttributeNameValue list = AttributeNameValue.removeAttribute(invocation.attributes, ConversationProtocol.PARAM_MEMBERS);
                if (!(list instanceof BaseService.AttributeNameListValue)) {
                    return ErrorCode.SUCCESS;
                }

                //noinspection unchecked
                final List<AttributeNameValue> memberInfo = (List<AttributeNameValue>) list.value;
                final UUID memberTwincodeId = AttributeNameValue.getUUIDAttribute(memberInfo, ConversationProtocol.PARAM_MEMBER_TWINCODE_ID);
                final String memberPubKey = AttributeNameValue.getStringAttribute(memberInfo, ConversationProtocol.PARAM_PUBLIC_KEY);
                final long permissions = AttributeNameValue.getLongAttribute(memberInfo, ConversationProtocol.PARAM_PERMISSIONS, 0);

                if (memberTwincodeId != null) {
                    final GroupMemberConversationImpl groupMember = groupConversation.getMember(memberTwincodeId);
                    if (groupMember == null) {
                        final GroupJoinOperation groupOperation = new GroupJoinOperation(conversationImpl, INVOKE_ADD_MEMBER,
                                groupConversation.getPeerTwincodeOutboundId(), memberTwincodeId,
                                permissions, memberPubKey, signedOffTwincode.getId(), signature);
                        mServiceProvider.storeOperation(groupOperation);

                        mScheduler.addOperation(conversationImpl, groupOperation, 0);
                    }
                }
            }
        }
    }

    private final class LeaveGroupInvocation implements TwincodeInboundService.InvocationListener {

        @Override
        public ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "LeaveGroupInvocation.onInvokeTwincode: invocation=" + invocation);
            }

            // Perform the leave group member invocation for an encrypted and signed invocation only.
            if (invocation.publicKey == null) {
                return ErrorCode.NOT_AUTHORIZED_OPERATION;
            }
            if (invocation.attributes == null) {
                return ErrorCode.BAD_REQUEST;
            }

            final UUID memberTwincodeId = AttributeNameValue.getUUIDAttribute(invocation.attributes, ConversationProtocol.PARAM_MEMBER_TWINCODE_ID);
            final UUID groupTwincodeId = AttributeNameValue.getUUIDAttribute(invocation.attributes, ConversationProtocol.PARAM_GROUP_TWINCODE_ID);
            if (groupTwincodeId == null || memberTwincodeId == null) {
                return ErrorCode.BAD_REQUEST;
            }
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "receive invoke leave ", Utils.toLog(groupTwincodeId), " member ",
                        Utils.toLog(memberTwincodeId));
            }

            // If we don't trust the member yet, still accept the leave only from itself.
            if (invocation.trustMethod == TrustMethod.NONE && !(memberTwincodeId.equals(invocation.peerTwincodeId))) {
                return ErrorCode.NOT_AUTHORIZED_OPERATION;
            }

            processLeaveGroup(groupTwincodeId, memberTwincodeId);
            return ErrorCode.SUCCESS;
        }
    }

    GroupConversationManager(@NonNull ConversationServiceImpl conversationService,
                             @NonNull ConversationServiceProvider serviceProvider,
                             @NonNull ConversationServiceScheduler scheduler) {
        if (DEBUG) {
            Log.d(LOG_TAG, "GroupConversationManager: conversationService=" + conversationService);
        }

        mTwinlifeImpl = conversationService.getTwinlifeImpl();
        mTwinlifeExecutor = mTwinlifeImpl.getTwinlifeExecutor();
        mConversationService = conversationService;
        mServiceProvider = serviceProvider;
        mCryptoService = mTwinlifeImpl.getCryptoService();
        mTwincodeOutboundService = mTwinlifeImpl.getTwincodeOutboundServiceImpl();
        mTwincodeInboundService = mTwinlifeImpl.getTwincodeInboundService();
        mScheduler = scheduler;
        mJoinHandler = new JoinGroupInvocation();
        mOnJoinHandler = new OnJoinGroupInvocation();
        mLeaveHandler = new LeaveGroupInvocation();
    }

    void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        mTwincodeInboundService.addListener(ConversationProtocol.ACTION_CONVERSATION_LEAVE, mLeaveHandler);
        mTwincodeInboundService.addListener(ConversationProtocol.ACTION_CONVERSATION_JOIN, mJoinHandler);
        mTwincodeInboundService.addListener(ConversationProtocol.ACTION_CONVERSATION_ON_JOIN, mOnJoinHandler);
    }

    /**
     * Create a new group conversation.
     *
     * @param group              the group repository object.
     * @param owner              when set the group is created in the JOINED state otherwise it is in the CREATED state.
     * @return the group conversation instance or null.
     */
    @Nullable
    GroupConversationImpl createGroup(@NonNull RepositoryObject group, boolean owner) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createGroup: group=" + group + " owner=" + owner);
        }

        final TwincodeOutbound groupTwincode = group.getPeerTwincodeOutbound();
        if (groupTwincode == null) {
            return null;
        }

        final UUID groupTwincodeId = groupTwincode.getId();
        GroupConversationImpl groupConversationImpl = mServiceProvider.findGroupConversation(groupTwincodeId);
        if (groupConversationImpl != null) {
            // If we were leaving the group and accepted a new invitation, change the state to joined.
            if (groupConversationImpl.getState() == GroupConversation.State.LEAVING) {
                groupConversationImpl.rejoin();
                mServiceProvider.updateGroupConversation(groupConversationImpl);
            }
            return groupConversationImpl;
        }
        groupConversationImpl = mServiceProvider.createGroupConversation(group, owner);
        if (groupConversationImpl == null) {
            return null;
        }

        // Notify upper layers about the new group conversation.
        final GroupConversationImpl newGroup = groupConversationImpl;
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onCreateGroupConversation(newGroup));
        }

        return groupConversationImpl;
    }

    @NonNull
    ErrorCode inviteGroup(long requestId, @NonNull ConversationService.Conversation conversation, @NonNull RepositoryObject group, @NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "inviteGroup: requestId=" + requestId + " conversation=" + conversation + " group=" + group + " name=" + name);
        }

        // This operation is not supported on a group.
        if (conversation.isGroup()) {

            return ErrorCode.BAD_REQUEST;
        }

        // Verify that the group exists and remember the peer to which we send the invitation.
        final ConversationService.Conversation targetConversation = mServiceProvider.loadConversationWithSubject(group);
        if (!(targetConversation instanceof GroupConversationImpl)) {

            return ErrorCode.ITEM_NOT_FOUND;
        }
        final GroupConversationImpl groupConversationImpl = (GroupConversationImpl) targetConversation;
        if (!groupConversationImpl.hasPermission(ConversationService.Permission.INVITE_MEMBER)) {

            return ErrorCode.NO_PERMISSION;
        }

        final TwincodeOutbound groupTwincode = groupConversationImpl.getPeerTwincodeOutbound();
        if (groupTwincode == null) {
            return ErrorCode.BAD_REQUEST;
        }

        final String publicKey = mCryptoService.getPublicKey(groupTwincode);

        // Create one invitation descriptor for the conversation.
        final ConversationImpl conversationImpl = (ConversationImpl) conversation;
        final InvitationDescriptorImpl invitation = mServiceProvider.createInvitation(conversationImpl, groupConversationImpl, name, publicKey);
        if (invitation == null) {

            return ErrorCode.LIMIT_REACHED;
        }

        conversationImpl.touch();
        conversationImpl.setIsActive(true);

        final GroupInviteOperation groupOperation = new GroupInviteOperation(Operation.Type.INVITE_GROUP, conversationImpl, invitation);
        mServiceProvider.storeOperation(groupOperation);
        mScheduler.addOperation(conversationImpl, groupOperation, 0);

        // Notify invitation was queued.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onInviteGroup(requestId, conversationImpl, invitation));
        }
        return ErrorCode.SUCCESS;
    }

    @NonNull
    ErrorCode joinGroup(long requestId, @NonNull ConversationService.DescriptorId descriptorId, @Nullable RepositoryObject group) {
        if (DEBUG) {
            Log.d(LOG_TAG, "joinGroup: requestId=" + requestId + " descriptorId=" + descriptorId + " group=" + group);
        }
        EventMonitor.info(LOG_TAG, "Join group as ", group);

        // Retrieve the descriptor for the invitation.
        final DescriptorImpl descriptor = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptor == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        // Make sure the descriptor is an invitation.
        if (!(descriptor instanceof InvitationDescriptorImpl)) {

            return ErrorCode.BAD_REQUEST;
        }
        final ConversationService.Conversation conversation = mServiceProvider.loadConversationWithId(descriptor.getConversationId());
        if (conversation == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }
        // This operation is not supported on a group.
        if (conversation.isGroup()) {

            return ErrorCode.BAD_REQUEST;
        }
        final ConversationImpl conversationImpl = (ConversationImpl) conversation;

        // Invitation must be pending to be able to join.
        final InvitationDescriptorImpl invitation = (InvitationDescriptorImpl) descriptor;
        if (invitation.getStatus() != ConversationService.InvitationDescriptor.Status.PENDING) {

            return ErrorCode.NO_PERMISSION;
        }

        // Verify that the user is not already member of this group or we have exactly the same member and groupId.
        final GroupConversationImpl groupConversation;
        if (group != null) {
            final TwincodeOutbound twincodeOutbound = group.getTwincodeOutbound();
            final TwincodeInbound twincodeInbound = group.getTwincodeInbound();
            final TwincodeOutbound groupTwincodeOutbound = group.getPeerTwincodeOutbound();
            if (twincodeOutbound == null || twincodeInbound == null || groupTwincodeOutbound == null) {
                return ErrorCode.NO_PERMISSION;
            }
            if (!groupTwincodeOutbound.getId().equals(invitation.getGroupTwincodeId())) {
                return ErrorCode.BAD_REQUEST;
            }

            // Create the local group conversation.
            groupConversation = createGroup(group, false);
            if (groupConversation == null) {
                return ErrorCode.BAD_REQUEST;
            }

            invitation.setStatus(ConversationService.InvitationDescriptor.Status.ACCEPTED);
            invitation.setMemberTwincodeId(twincodeOutbound.getId());

            // Update the invitation descriptor.
            invitation.setReadTimestamp(System.currentTimeMillis());
            mServiceProvider.acceptInvitation(invitation, groupConversation);
        } else {
            invitation.setStatus(ConversationService.InvitationDescriptor.Status.REFUSED);
            invitation.setReadTimestamp(System.currentTimeMillis());
            mServiceProvider.updateDescriptor(invitation);
            groupConversation = null;
        }

        conversationImpl.touch();

        final GroupJoinOperation groupOperation = new GroupJoinOperation(conversationImpl, invitation);
        mServiceProvider.storeOperation(groupOperation);
        mScheduler.addOperation(conversationImpl, groupOperation, 0);

        // Notify invitation was accepted or refused.
        ConversationService.GroupConversation lGroup = groupConversation;
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onJoinGroup(requestId, lGroup, invitation));
        }
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(requestId, conversationImpl, invitation, ConversationService.UpdateType.TIMESTAMPS));
        }

        return ErrorCode.SUCCESS;
    }

    @NonNull
    ErrorCode leaveGroup(long requestId, @NonNull RepositoryObject group, @NonNull UUID memberTwincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "leaveGroup: requestId=" + requestId + " group=" + group + " memberTwincodeId=" + memberTwincodeId);
        }

        EventMonitor.info(LOG_TAG, "Member ", memberTwincodeId, " leave group ", group);

        final ConversationService.Conversation conversation = mServiceProvider.loadConversationWithSubject(group);
        if (!(conversation instanceof GroupConversationImpl)) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        final GroupConversationImpl groupConversation = (GroupConversationImpl) conversation;
        final List<ConversationImpl> conversations = ConversationServiceImpl.getConversations(groupConversation, null);
        final GroupMemberConversationImpl member = groupConversation.leaveGroup(memberTwincodeId);
        if (member == null) {
            return ErrorCode.ITEM_NOT_FOUND;
        }

        final boolean leavingCurrentGroup = member == groupConversation.getIncomingConversation();
        final Map<UUID, ConversationService.InvitationDescriptor> pendingInvitations;
        if (leavingCurrentGroup) {
            pendingInvitations = mServiceProvider.listPendingInvitations(group);
        } else {
            pendingInvitations = null;
        }

        // User wants to leave the group, we have to setup the group conversation object in a special state.
        // - we need to send the leave to other members,
        // - we must not accept any pending invitation,
        // - we must free the system resources (files, messages),
        // - ideally, we should not accept new messages except the response to our leave operation.
        if (leavingCurrentGroup) {
            mServiceProvider.updateGroupConversation(groupConversation);

            // Drop the descriptors and files we have sent.
            Map<UUID, ConversationService.DescriptorId> resetList = mServiceProvider.listDescriptorsToDelete(conversation, null, Long.MAX_VALUE);
            mConversationService.resetConversation(groupConversation, resetList, ConversationService.ClearMode.CLEAR_BOTH);

            // Revoke the pending invitations.
            for (ConversationService.InvitationDescriptor invitation : pendingInvitations.values()) {
                withdrawInviteGroup(requestId, invitation);
            }
        } else {
            mServiceProvider.updateConversation(member, null);

            // Drop the descriptors, files and pending operations for this member.
            Map<UUID, ConversationService.DescriptorId> resetList = mServiceProvider.listDescriptorsToDelete(groupConversation, member.getMemberTwincodeOutboundId(), Long.MAX_VALUE);
            mConversationService.resetConversation(groupConversation, resetList, ConversationService.ClearMode.CLEAR_BOTH);
        }

        // Send the leave operation to each peer, including the member being removed:
        // - if the peer twincode is signed, we can do a secure invocation to notify about the leave,
        // - otherwise, we must queue a LEAVE_GROUP operation.
        final TwincodeOutbound groupTwincode = groupConversation.getPeerTwincodeOutbound();
        if (!conversations.isEmpty() && groupTwincode != null) {
            final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
            for (final ConversationImpl conversationImpl : conversations) {
                TwincodeOutbound peerTwincode = conversationImpl.getPeerTwincodeOutbound();
                final GroupOperation groupOperation;
                if (peerTwincode != null && peerTwincode.isSigned()) {
                    groupOperation = new GroupLeaveOperation(conversationImpl, Operation.Type.INVOKE_LEAVE_GROUP,
                            groupTwincode.getId(), memberTwincodeId);
                } else {
                    conversationImpl.touch();

                    groupOperation = new GroupLeaveOperation(conversationImpl, Operation.Type.LEAVE_GROUP,
                            groupTwincode.getId(), memberTwincodeId);
                }
                pendingOperations.put(conversationImpl, groupOperation);
            }
            mConversationService.addOperations(pendingOperations);
        }

        // Remove the member
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onLeaveGroup(requestId, groupConversation, memberTwincodeId));
        }

        // We are leaving a group with nobody: we must perform the last step now because nobody will tell us to do it.
        if (leavingCurrentGroup && conversations.isEmpty()) {

            // Delete the group conversation and notify upper layers.
            deleteGroupConversation(groupConversation);
        }
        return ErrorCode.SUCCESS;
    }

    @NonNull
    ErrorCode withdrawInviteGroup(long requestId, @NonNull ConversationService.InvitationDescriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "withdrawInviteGroup: requestId=" + requestId + " invitation=" + descriptor);
        }

        final InvitationDescriptorImpl invitation = (InvitationDescriptorImpl) descriptor;
        final ConversationService.Conversation conversation = mServiceProvider.loadConversationWithId(invitation.getConversationId());
        if (!(conversation instanceof ConversationImpl)) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        final ConversationImpl conversationImpl = (ConversationImpl) conversation;
        boolean needPeerUpdate = invitation.getSentTimestamp() > 0 && invitation.getStatus() == InvitationDescriptor.Status.PENDING;
        invitation.setDeletedTimestamp(System.currentTimeMillis());
        if (!needPeerUpdate) {
            invitation.setPeerDeletedTimestamp(System.currentTimeMillis());
        }
        invitation.setStatus(InvitationDescriptor.Status.WITHDRAWN);
        mServiceProvider.updateDescriptor(invitation);

        if (needPeerUpdate) {
            conversationImpl.touch();

            UpdateDescriptorTimestampOperation updateDescriptorTimestampOperation = new UpdateDescriptorTimestampOperation(conversationImpl,
                    UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType.DELETE, invitation.getDescriptorId(),
                    invitation.getDeletedTimestamp());
            mServiceProvider.storeOperation(updateDescriptorTimestampOperation);
            mScheduler.addOperation(conversationImpl, updateDescriptorTimestampOperation, 0);
        }
        return ErrorCode.SUCCESS;
    }

    ErrorCode subscribeGroup(long requestId, @NonNull RepositoryObject group, @NonNull TwincodeOutbound memberTwincode, long permissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "subscribeGroup: requestId=" + requestId + " group=" + group
                    + " memberTwincode=" + memberTwincode
                    + " permissions=" + permissions);
        }
        EventMonitor.info(LOG_TAG, "Subscribe group as ", memberTwincode);

        // Verify that the group exists.
        final ConversationService.Conversation conversation = mServiceProvider.loadConversationWithSubject(group);
        if (!(conversation instanceof GroupConversationImpl)) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        final GroupConversationImpl groupConversation = (GroupConversationImpl) conversation;
        final AddStatus status = addMember(groupConversation, memberTwincode, permissions, null, null, false, null, null);

        if (status == AddStatus.NEW_MEMBER) {
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onJoinGroupRequest(BaseService.DEFAULT_REQUEST_ID,
                        groupConversation, null, memberTwincode.getId()));
            }
        }
        if (status == AddStatus.ERROR) {

            return ErrorCode.BAD_REQUEST;
        }

        return ErrorCode.SUCCESS;
    }

    ErrorCode registeredGroup(long requestId, @NonNull RepositoryObject group, @NonNull TwincodeOutbound adminTwincode,
                                     long adminPermissions, long permissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "registeredGroup: requestId=" + requestId + " group=" + group
                    + " adminTwincode=" + adminTwincode + " permissions=" + permissions);
        }
        EventMonitor.info(LOG_TAG, "Registered in group with admin ", adminTwincode);

        // Verify that the group exists.
        final ConversationService.Conversation conversation = mServiceProvider.loadConversationWithSubject(group);
        if (!(conversation instanceof GroupConversationImpl)) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        final GroupConversationImpl groupConversation = (GroupConversationImpl) conversation;
        groupConversation.join(permissions);
        mServiceProvider.updateGroupConversation(groupConversation);

        // Add the admin member.
        AddStatus status = addMember(groupConversation, adminTwincode, adminPermissions, null, null, false, null, null);

        if (status == AddStatus.NEW_MEMBER) {
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onJoinGroupResponse(BaseService.DEFAULT_REQUEST_ID,
                        groupConversation, null));
            }
        }
        if (status == AddStatus.ERROR) {

            return ErrorCode.BAD_REQUEST;
        }

        return ErrorCode.SUCCESS;
    }

    @NonNull
    ErrorCode setPermissions(@NonNull RepositoryObject group, @Nullable UUID memberTwincodeId, long permissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setPermissions: group=" + group + " memberTwincodeId=" + memberTwincodeId);
        }

        if (memberTwincodeId == null) {
            EventMonitor.info(LOG_TAG, "Use join permissions ", permissions, " in group ", group);
        } else {
            EventMonitor.info(LOG_TAG, "Assign permissions ", permissions, " to ", memberTwincodeId, " in group ", group);
        }

        final TwincodeOutbound groupTwincode = group.getPeerTwincodeOutbound();
        if (groupTwincode == null) {
            return ErrorCode.ITEM_NOT_FOUND;
        }

        final ConversationService.Conversation conversation = mServiceProvider.loadConversationWithSubject(group);
        if (!(conversation instanceof GroupConversationImpl)) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        // User must have the update permission.
        final GroupConversationImpl groupConversation = (GroupConversationImpl) conversation;
        if (!groupConversation.hasPermission(ConversationService.Permission.UPDATE_MEMBER)) {

            return ErrorCode.NO_PERMISSION;
        }

        if (memberTwincodeId == null) {
            groupConversation.setJoinPermissions(permissions);

            mServiceProvider.updateGroupConversation(groupConversation);
        } else {
            ConversationImpl member = groupConversation.getMember(memberTwincodeId);
            if (member == null) {

                return ErrorCode.ITEM_NOT_FOUND;
            }
            member.setPermissions(permissions);
            mServiceProvider.updateConversation(member, null);
        }

        final List<ConversationImpl> conversations = ConversationServiceImpl.getConversations(groupConversation, null);

        // Send the update permission to each peer.
        for (final ConversationImpl conversationImpl : conversations) {
            conversationImpl.touch();

            if (memberTwincodeId != null) {
                final GroupUpdateOperation groupOperation = new GroupUpdateOperation(conversationImpl,
                        groupTwincode.getId(), memberTwincodeId, permissions);
                mServiceProvider.storeOperation(groupOperation);
                mScheduler.addOperation(conversationImpl, groupOperation, 0);
            } else {
                // Change this member's permissions and save it.
                conversationImpl.setPermissions(permissions);
                mServiceProvider.updateConversation(conversationImpl, null);

                // Propagate this member's permission to every members.
                for (final ConversationImpl peer : conversations) {
                    final GroupUpdateOperation groupOperation = new GroupUpdateOperation(conversationImpl,
                            groupTwincode.getId(), peer.getPeerTwincodeOutboundId(), permissions);
                    mServiceProvider.storeOperation(groupOperation);
                    mScheduler.addOperation(conversationImpl, groupOperation, 0);
                }
            }
        }

        return ErrorCode.SUCCESS;
    }

    static class JoinResult {
        final List<OnJoinGroupIQ.MemberInfo> members;
        final long memberPermissions;
        final TwincodeOutbound inviterMemberTwincode;
        final long inviterPermissions;
        final InvitationDescriptor.Status status;
        final String signature;

        JoinResult(@NonNull InvitationDescriptor.Status status,
                   final TwincodeOutbound inviterMemberTwincode, long inviterPermissions,
                   List<OnJoinGroupIQ.MemberInfo> members, long permissions, @Nullable String signature) {
            this.status = status;
            this.inviterMemberTwincode = inviterMemberTwincode;
            this.inviterPermissions = inviterPermissions;
            this.members = members;
            this.memberPermissions = permissions;
            this.signature = signature;
        }
    }

    @NonNull
    ErrorCode invokeJoinOperation(@NonNull ConversationImpl conversationImpl, @NonNull GroupJoinOperation groupOperation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "invokeJoinOperation: conversationImpl=" + conversationImpl + " groupOperation=" + groupOperation);
        }

        final TwincodeOutbound twincodeOutbound = conversationImpl.getTwincodeOutbound();
        final TwincodeOutbound peerTwincodeOutbound = conversationImpl.getPeerTwincodeOutbound();
        if (peerTwincodeOutbound == null || twincodeOutbound == null) {
            return ErrorCode.EXPIRED;
        }

        final UUID groupTwincodeId = groupOperation.getGroupId();
        final UUID memberTwincodeId = groupOperation.getMemberId();
        final String signature = groupOperation.getSignature();
        final UUID signedOffByTwincodeId = groupOperation.getSignedOffTwincodeId();
        if (!twincodeOutbound.getId().equals(memberTwincodeId) || signedOffByTwincodeId == null
                || signature == null || groupTwincodeId == null) {
            return ErrorCode.EXPIRED;
        }

        final List<AttributeNameValue> attributes = new ArrayList<>(2);
        BaseService.AttributeNameUUIDValue.add(attributes, ConversationProtocol.PARAM_GROUP_TWINCODE_ID, groupTwincodeId);
        BaseService.AttributeNameUUIDValue.add(attributes, ConversationProtocol.PARAM_SIGNED_OFF_TWINCODE_ID, signedOffByTwincodeId);
        BaseService.AttributeNameLongValue.add(attributes, ConversationProtocol.PARAM_PERMISSIONS, groupOperation.getPermissions());
        BaseService.AttributeNameStringValue.add(attributes, ConversationProtocol.PARAM_SIGNATURE, signature);

        Log.e(LOG_TAG, "invoke join: groupTwincodeId=" + groupTwincodeId + " signedOffByTwincodeId=" + signedOffByTwincodeId
            + " permissions=" + groupOperation.getPermissions());

        groupOperation.updateRequestId(mTwinlifeImpl.newRequestId());
        mTwincodeOutboundService.secureInvokeTwincode(twincodeOutbound, twincodeOutbound, peerTwincodeOutbound,
                TwincodeOutboundService.INVOKE_URGENT | TwincodeOutboundService.CREATE_SECRET,
                ConversationProtocol.ACTION_CONVERSATION_JOIN, attributes, (ErrorCode errorCode, UUID invocationId) -> {

            // If we are offline or timed out don't acknowledge the operation but clear the
            // request id so that we can retry it as soon as we are online.
            if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                groupOperation.updateRequestId(Operation.NO_REQUEST_ID);
                return;
            }

            // It could happen that the member we want to inform about our join has finally left the group and is invalid.
            // We must remove it.
            if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
                if (groupConversation != null) {
                    delMember(groupConversation, twincodeOutbound.getId());
                }
            }

            mScheduler.finishInvokeOperation(groupOperation, conversationImpl);
        });
        return ErrorCode.QUEUED;
    }

    @NonNull
    ErrorCode invokeLeaveOperation(@NonNull ConversationImpl conversationImpl, @NonNull GroupLeaveOperation groupOperation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "invokeLeaveOperation: conversationImpl=" + conversationImpl + " groupOperation=" + groupOperation);
        }

        final TwincodeOutbound twincodeOutbound = conversationImpl.getTwincodeOutbound();
        final TwincodeOutbound peerTwincodeOutbound = conversationImpl.getPeerTwincodeOutbound();
        final UUID groupTwincodeId = groupOperation.getGroupId();
        final UUID memberTwincodeId = groupOperation.getMemberId();
        if (peerTwincodeOutbound == null || twincodeOutbound == null || groupTwincodeId == null || memberTwincodeId == null) {
            return ErrorCode.EXPIRED;
        }

        final List<AttributeNameValue> attributes = new ArrayList<>(2);
        BaseService.AttributeNameUUIDValue.add(attributes, ConversationProtocol.PARAM_GROUP_TWINCODE_ID, groupTwincodeId);
        BaseService.AttributeNameUUIDValue.add(attributes, ConversationProtocol.PARAM_MEMBER_TWINCODE_ID, memberTwincodeId);

        groupOperation.updateRequestId(mTwinlifeImpl.newRequestId());
        mTwincodeOutboundService.secureInvokeTwincode(twincodeOutbound, twincodeOutbound, peerTwincodeOutbound, TwincodeOutboundService.INVOKE_URGENT, ConversationProtocol.ACTION_CONVERSATION_LEAVE, attributes, (ErrorCode errorCode, UUID invocationId) -> {

            // If we are offline or timed out don't acknowledge the operation but clear the
            // request id so that we can retry it as soon as we are online.
            if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                groupOperation.updateRequestId(Operation.NO_REQUEST_ID);
                return;
            }

            // Proceed with the removal as if we got the on-leave IQ from the peer (even if an error occurred).
            processOnLeaveGroup(groupTwincodeId, memberTwincodeId, conversationImpl.getPeerTwincodeOutboundId());

            mScheduler.finishInvokeOperation(groupOperation, conversationImpl);
        });
        return ErrorCode.QUEUED;
    }

    @NonNull
    ErrorCode invokeAddMemberOperation(@NonNull ConversationImpl conversationImpl, @NonNull GroupJoinOperation groupOperation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "invokeAddMemberOperation: conversationImpl=" + conversationImpl + " groupOperation=" + groupOperation);
        }

        final UUID groupTwincodeId = groupOperation.getGroupId();
        final UUID memberTwincodeId = groupOperation.getMemberId();
        final String publicKey = groupOperation.getPublicKey();
        if (groupTwincodeId == null || memberTwincodeId == null) {
            return ErrorCode.EXPIRED;
        }

        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
        if (groupConversation == null) {
            return ErrorCode.EXPIRED;
        }
        groupOperation.updateRequestId(mTwinlifeImpl.newRequestId());

        EventMonitor.info(LOG_TAG, "add group member ", memberTwincodeId, " in ", groupTwincodeId, " pubKey ", publicKey);

        if (publicKey == null) {
            mTwincodeOutboundService.getTwincode(memberTwincodeId, TwincodeOutboundService.REFRESH_PERIOD, (ErrorCode errorCode, TwincodeOutbound twincodeOutbound) -> {

                // If we are offline or timed out don't acknowledge the operation but clear the
                // request id so that we can retry it as soon as we are online.
                if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                    groupOperation.updateRequestId(Operation.NO_REQUEST_ID);
                    return;
                }

                if (twincodeOutbound != null) {
                    addMember(groupConversation, twincodeOutbound, groupOperation.getPermissions(),
                            null, null, true, groupOperation.getSignedOffTwincodeId(),
                            groupOperation.getSignature());
                }
                mScheduler.finishInvokeOperation(groupOperation, conversationImpl);
            });
        } else {
            mTwincodeOutboundService.getSignedTwincode(memberTwincodeId, publicKey, TrustMethod.PEER, (ErrorCode errorCode, TwincodeOutbound twincodeOutbound) -> {

                // If we are offline or timed out don't acknowledge the operation but clear the
                // request id so that we can retry it as soon as we are online.
                if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                    groupOperation.updateRequestId(Operation.NO_REQUEST_ID);
                    return;
                }

                if (twincodeOutbound != null) {
                    addMember(groupConversation, twincodeOutbound, groupOperation.getPermissions(), null, null, true,
                            groupOperation.getSignedOffTwincodeId(), groupOperation.getSignature());
                }
                mScheduler.finishInvokeOperation(groupOperation, conversationImpl);
            });
        }
        return ErrorCode.QUEUED;
    }

    private void invokeOnJoin(@NonNull GroupConversationImpl groupConversation, @NonNull TwincodeOutbound memberTwincode,
                              @NonNull String publicKey, @NonNull List<OnJoinGroupIQ.MemberInfo> members,
                              @NonNull UUID invocationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "invokeOnJoin: groupConversation=" + groupConversation + " memberTwincode=" + memberTwincode
                    + " invocationId=" + invocationId);
        }

        final TwincodeOutbound twincodeOutbound = groupConversation.getTwincodeOutbound();
        if (twincodeOutbound == null) {
            mTwincodeInboundService.acknowledgeInvocation(invocationId, ErrorCode.EXPIRED);
            return;
        }

        final UUID groupTwincodeId = groupConversation.getPeerTwincodeOutboundId();
        final UUID memberTwincodeId = memberTwincode.getId();
        final long permissions = groupConversation.getJoinPermissions();
        final String signature = signMember(twincodeOutbound, groupTwincodeId, memberTwincodeId, publicKey, permissions);
        if (signature == null) {
            mTwincodeInboundService.acknowledgeInvocation(invocationId, ErrorCode.BAD_REQUEST);
            return;
        }

        final List<AttributeNameValue> attributes = new ArrayList<>(2);
        BaseService.AttributeNameUUIDValue.add(attributes, ConversationProtocol.PARAM_GROUP_TWINCODE_ID, groupTwincodeId);
        BaseService.AttributeNameUUIDValue.add(attributes, ConversationProtocol.PARAM_SIGNED_OFF_TWINCODE_ID, twincodeOutbound.getId());
        BaseService.AttributeNameLongValue.add(attributes, ConversationProtocol.PARAM_PERMISSIONS, permissions);
        BaseService.AttributeNameStringValue.add(attributes, ConversationProtocol.PARAM_SIGNATURE, signature);

        for (OnJoinGroupIQ.MemberInfo member : members) {
            final List<AttributeNameValue> memberInfo = new ArrayList<>();

            BaseService.AttributeNameUUIDValue.add(memberInfo, ConversationProtocol.PARAM_MEMBER_TWINCODE_ID, member.memberTwincodeId);
            BaseService.AttributeNameLongValue.add(memberInfo, ConversationProtocol.PARAM_PERMISSIONS, member.permissions);
            if (member.publicKey != null) {
                BaseService.AttributeNameStringValue.add(memberInfo, ConversationProtocol.PARAM_PUBLIC_KEY, member.publicKey);
            }

            BaseService.AttributeNameListValue.add(attributes, ConversationProtocol.PARAM_MEMBERS, memberInfo);
        }

        mTwincodeOutboundService.secureInvokeTwincode(twincodeOutbound, twincodeOutbound, memberTwincode,
                TwincodeOutboundService.INVOKE_URGENT | TwincodeOutboundService.CREATE_SECRET,
                ConversationProtocol.ACTION_CONVERSATION_ON_JOIN, attributes, (ErrorCode errorCode, UUID joinInvocationId) -> {

            // If we are offline or timed out don't acknowledge the invocation: it will be retried.
            if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                return;
            }

            // It could happen that the member that invoked the join has invalidated its twincode (leave group or uninstall).
            // We must remove it.
            if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                delMember(groupConversation, twincodeOutbound.getId());
            } else {
                // Secrets and keys are known, we can set the FLAG_ENCRYPT on memberTwincode.
                mTwincodeOutboundService.associateTwincodes(twincodeOutbound, null, memberTwincode);
            }

            mTwincodeInboundService.acknowledgeInvocation(invocationId, ErrorCode.SUCCESS);
        });
    }

    long processInviteGroup(@NonNull ConversationImpl conversationImpl,
                            @NonNull InvitationDescriptorImpl invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processInviteGroupIQ: conversationImpl=" + conversationImpl + " invitation=" + invitation);
        }

        long receivedTimestamp = System.currentTimeMillis();
        invitation.setReceivedTimestamp(receivedTimestamp);
        ConversationServiceProvider.Result result = mServiceProvider.insertOrUpdateDescriptorImpl(conversationImpl, invitation);
        if (result == ConversationServiceProvider.Result.ERROR) {
            receivedTimestamp = -1L;
        }

        final GroupConversationImpl group = mServiceProvider.findGroupConversation(invitation.getGroupTwincodeId());
        if (group != null && group.getState() == GroupConversation.State.JOINED) {
            if (Logger.WARN) {
                Logger.warn(LOG_TAG, "User is already member of group ", invitation.getGroupTwincodeId());
            }

            invitation.setReadTimestamp(invitation.getReceivedTimestamp());
            invitation.setMemberTwincodeId(group.getTwincodeOutboundId());
            invitation.setStatus(InvitationDescriptor.Status.ACCEPTED);
            mServiceProvider.updateDescriptor(invitation);

            conversationImpl.setIsActive(true);

            // Send the join request immediately.
            final GroupOperation groupOperation = new GroupJoinOperation(conversationImpl, invitation);
            mServiceProvider.storeOperation(groupOperation);

            mScheduler.addOperation(conversationImpl, groupOperation, 0);

        } else if (result == ConversationServiceProvider.Result.STORED) {
            conversationImpl.setIsActive(true);

            // If the invitation was inserted, propagate it to upper layers through the onInviteGroupRequest callback.
            // Otherwise, we already know the invitation and we only need to acknowledge the sender.
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onInviteGroupRequest(BaseService.DEFAULT_REQUEST_ID,
                        conversationImpl, invitation));
            }
        }

        // Make the invitation visible in the conversation view (even if it was accepted).
        if (result == ConversationServiceProvider.Result.STORED) {
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onPopDescriptor(BaseService.DEFAULT_REQUEST_ID, conversationImpl, invitation));
            }
        }
        return receivedTimestamp;
    }

    void processRevokeInviteGroup(@NonNull ConversationImpl conversationImpl, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processRevokeInviteGroup: conversationImpl=" + conversationImpl + " descriptorId=" + descriptorId);
        }

        // Verify that the descriptor exists and is an invitation.
        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptorImpl instanceof InvitationDescriptorImpl) {

            final InvitationDescriptorImpl invitation = (InvitationDescriptorImpl) descriptorImpl;
            invitation.setDeletedTimestamp(System.currentTimeMillis());
            invitation.setStatus(InvitationDescriptor.Status.WITHDRAWN);
            mServiceProvider.updateDescriptor(invitation);
            mConversationService.deleteConversationDescriptor(BaseService.DEFAULT_REQUEST_ID, conversationImpl, invitation);
        }
    }

    /**
     * Process joining the group with an invitation and a valid member twincode.
     * Used by the secure join invocation as well as the legacy join IQ.
     *
     * @param conversationImpl the conversation onto which the invitation was sent.
     * @param groupTwincodeId the group twincode to join
     * @param descriptorId the invitation descriptor id.
     * @param memberTwincode the member twincode that joined the group.
     * @param memberPublicKey the member public key used to sign the twincode attributes (verified and trusted).
     * @return the join result to return to the peer.
     */
    @Nullable
    JoinResult processJoinGroup(@NonNull ConversationImpl conversationImpl, @NonNull UUID groupTwincodeId,
                                @NonNull DescriptorId descriptorId, @NonNull TwincodeOutbound memberTwincode,
                                @Nullable String memberPublicKey) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processJoinGroup: groupTwincodeId=" + groupTwincodeId + " memberTwincode=" + memberTwincode);
        }

        // Find the group.
        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
        if (groupConversation == null) {
            return null;
        }

        // Verify that the invitation is still available.
        final ConversationService.Descriptor descriptor = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (!(descriptor instanceof InvitationDescriptorImpl)) {
            return null;
        }

        // Verify the invitation is still pending, or, it has the same member and is joined.
        final InvitationDescriptorImpl invitation = (InvitationDescriptorImpl) descriptor;
        final long receivedTimestamp = System.currentTimeMillis();
        boolean invitationChanged = false;
        InvitationDescriptor.Status newStatus;
        if (invitation.getStatus() == InvitationDescriptor.Status.PENDING) {
            newStatus = InvitationDescriptor.Status.ACCEPTED;
            invitation.setMemberTwincodeId(memberTwincode.getId());
            invitation.setReadTimestamp(receivedTimestamp);
            invitationChanged = true;

            // We can receive the same join-group IQ several times and we must accept it if this is the same member.
        } else if (invitation.getStatus() == InvitationDescriptor.Status.JOINED
                && memberTwincode.getId().equals(invitation.getMemberTwincodeId())) {
            newStatus = InvitationDescriptor.Status.ACCEPTED;
        } else {
            newStatus = InvitationDescriptor.Status.WITHDRAWN;
        }

        final List<OnJoinGroupIQ.MemberInfo> members;
        final AddStatus result;

        // If the invitation is accepted, add the member in the group.
        long memberPermissions = groupConversation.getJoinPermissions();
        if (newStatus == InvitationDescriptor.Status.ACCEPTED) {

            // If we have an invitation, keep the contactId of the conversation to which the invitation was sent.
            UUID invitedContactId = conversationImpl.getContactId();
            members = new ArrayList<>();
            result = addMember(groupConversation, memberTwincode, memberPermissions, invitedContactId, members, false, null, null);
            if (result != AddStatus.ERROR) {
                newStatus = InvitationDescriptor.Status.JOINED;
            } else {
                memberPermissions = 0;
                newStatus = InvitationDescriptor.Status.WITHDRAWN;
                invitationChanged = true;
            }
        } else {
            members = null;
            memberPermissions = 0;
            result = AddStatus.NO_CHANGE;
        }

        if (invitationChanged) {
            invitation.setStatus(newStatus);
            mServiceProvider.updateDescriptor(invitation);

            // Notify that the invitation descriptor was changed.
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(BaseService.DEFAULT_REQUEST_ID,
                        conversationImpl, invitation, ConversationService.UpdateType.TIMESTAMPS));
            }
        }

        // The group is known and a new member joined the group, notify the upper layers.
        if (result == AddStatus.NEW_MEMBER) {
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onJoinGroupRequest(BaseService.DEFAULT_REQUEST_ID,
                        groupConversation, invitation, memberTwincode.getId()));
            }
        }

        final TwincodeOutbound twincodeOutbound = groupConversation.getTwincodeOutbound();
        final String signature = signMember(twincodeOutbound, groupTwincodeId, memberTwincode.getId(),
                memberPublicKey, memberPermissions);
        return new JoinResult(newStatus, twincodeOutbound, groupConversation.getPermissions(),
                members, memberPermissions, signature);
    }

    /**
     * Process a join group by a new member that was accepted after a processJoinGroup() with an invitation.
     * This join group is a legacy join received on a TLGroupMemberConversationImpl.
     *
     * @param groupTwincodeId the group to join.
     * @param memberTwincode the new member twincode to add.
     * @param memberPermissions the new member permission in the group.
     * @return the join result to return to the new member.
     */
    @Nullable
    JoinResult processJoinGroup(@NonNull UUID groupTwincodeId, @NonNull TwincodeOutbound memberTwincode, long memberPermissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processJoinGroup: groupTwincodeId=" + groupTwincodeId + " memberTwincode=" + memberTwincode);
        }

        // Find the group.
        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
        if (groupConversation == null) {
            return null;
        }

        final List<OnJoinGroupIQ.MemberInfo> members = new ArrayList<>();
        final AddStatus result = addMember(groupConversation, memberTwincode, memberPermissions, null, members, false, null, null);
        if (result == AddStatus.ERROR) {
            return null;
        }

        // The group is known and a new member joined the group, notify the upper layers.
        if (result == AddStatus.NEW_MEMBER) {
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onJoinGroupRequest(BaseService.DEFAULT_REQUEST_ID,
                        groupConversation, null, memberTwincode.getId()));
            }
        }

        return new JoinResult(InvitationDescriptor.Status.JOINED, groupConversation.getTwincodeOutbound(),
                groupConversation.getPermissions(), members, 0, null);
    }

    /**
     * Process a group invitation that was refused by the peer.
     *
     * @param conversationImpl the conversation onto which the group invitation was sent.
     * @param descriptorId the invitation descriptor.
     */
    void processRejectJoinGroup(@NonNull ConversationImpl conversationImpl, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processRejectJoinGroup: conversationImpl=" + conversationImpl + " descriptorId=" + descriptorId);
        }

        // Verify that the invitation is still available and pending.
        ConversationService.Descriptor descriptor = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (!(descriptor instanceof InvitationDescriptorImpl)) {
            return;
        }

        final InvitationDescriptorImpl invitation = (InvitationDescriptorImpl) descriptor;
        if (invitation.getStatus() != InvitationDescriptor.Status.PENDING) {
            return;
        }

        invitation.setReadTimestamp(System.currentTimeMillis());
        invitation.setStatus(InvitationDescriptor.Status.REFUSED);
        mServiceProvider.updateDescriptor(invitation);

        // Notify that the invitation descriptor was changed.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(BaseService.DEFAULT_REQUEST_ID,
                    conversationImpl, invitation, ConversationService.UpdateType.TIMESTAMPS));
        }
    }

    void processLeaveGroup(@NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLeaveGroup: groupTwincodeId=" + groupTwincodeId + " memberTwincodeId=" + memberTwincodeId);
        }

        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
        boolean memberDeleted = false;
        boolean groupDeleted = false;
        if (groupConversation != null) {
            if (groupConversation.getTwincodeOutboundId().equals(memberTwincodeId)) {
                // We have left the group, finish the process by deleting the group conversation.
                deleteGroupConversation(groupConversation);
                groupDeleted = true;
            } else {
                memberDeleted = delMember(groupConversation, memberTwincodeId);
            }
        }

        if (groupConversation != null) {
            // Report that the member has left the group.
            if (memberDeleted || groupDeleted) {
                for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                    mTwinlifeExecutor.execute(() -> serviceObserver.onLeaveGroup(BaseService.DEFAULT_REQUEST_ID,
                            groupConversation, memberTwincodeId));
                }
            }
        }
    }

    void processOnJoinGroup(@NonNull ConversationImpl conversationImpl,
                            @NonNull UUID groupTwincodeId, @Nullable InvitationDescriptorImpl invitation,
                            @Nullable TwincodeOutbound inviterTwincode, long inviterPermissions,
                            @Nullable List<OnJoinGroupIQ.MemberInfo> members, long permissions, @Nullable String signature) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnJoinGroup: groupTwincodeId=" + groupTwincodeId + " invitation=" + invitation);
        }
        Log.e(LOG_TAG, "on-join-group " + groupTwincodeId + " permissions=" + permissions);

        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
        if (groupConversation == null) {
            return;
        }

        final GroupConversation.State currentState = groupConversation.getState();
        final TwincodeOutbound memberTwincode = groupConversation.getTwincodeOutbound();
        if (currentState == GroupConversation.State.LEAVING || memberTwincode == null) {
            return;
        }

        // inviterTwincode can be null for the legacy onJoinGroup().
        if (inviterTwincode != null) {
            // If we have an invitation, keep the contactId of the conversation to which the invitation was sent.
            UUID invitedContactId = conversationImpl.getContactId();
            addMember(groupConversation, inviterTwincode, inviterPermissions, invitedContactId, null, false, null, null);

            final TwincodeOutbound previousPeerTwincode = conversationImpl.getPeerTwincodeOutbound();
            if (inviterTwincode.isSigned() && previousPeerTwincode != null) {
                mTwincodeOutboundService.associateTwincodes(memberTwincode, previousPeerTwincode, inviterTwincode);
                mCryptoService.validateSecrets(memberTwincode, inviterTwincode);
            }
        }

        // Join the group before adding the members to make sure our permissions are correct before
        // scheduling the INVOKE_ADD_MEMBER operation (could be executed by another thread).
        final boolean joinStatus;
        if (invitation != null) {
            joinStatus = groupConversation.join(permissions);
            if (joinStatus) {
                mServiceProvider.updateGroupConversation(groupConversation);
            }
        } else {
            joinStatus = groupConversation.getState() == GroupConversation.State.JOINED;
        }

        if (members != null) {
            final UUID signedOffTwincodeId = inviterTwincode != null ? inviterTwincode.getId() : null;
            for (OnJoinGroupIQ.MemberInfo member : members) {
                final GroupMemberConversationImpl groupMember = groupConversation.getMember(member.memberTwincodeId);
                if (groupMember == null) {
                    final GroupJoinOperation groupOperation = new GroupJoinOperation(conversationImpl, INVOKE_ADD_MEMBER,
                            groupConversation.getPeerTwincodeOutboundId(), member.memberTwincodeId,
                            member.permissions, member.publicKey, signedOffTwincodeId, signature);
                    mServiceProvider.storeOperation(groupOperation);

                    mScheduler.addOperation(conversationImpl, groupOperation, 0);
                }
            }
        }

        if (joinStatus) {
            if (invitation != null) {
                invitation.setStatus(InvitationDescriptor.Status.JOINED);
                mServiceProvider.updateDescriptor(invitation);
            }

            // With an invitation, notify upper layers that the join operation is finished.
            // But do this only once when we transition from CREATED to JOINED.
            if (currentState == GroupConversation.State.CREATED) {
                for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                    mTwinlifeExecutor.execute(() -> serviceObserver.onJoinGroupResponse(BaseService.DEFAULT_REQUEST_ID, groupConversation, invitation));
                }
            }
        }
    }

    void processOnJoinGroupWithdrawn(@NonNull InvitationDescriptorImpl invitation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnJoinGroupWithdrawn: invitation=" + invitation);
        }

        // The join was finally withdrawn, drop the group if it is still waiting to be joined.
        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(invitation.getGroupTwincodeId());
        if (groupConversation != null && groupConversation.getState() == GroupConversation.State.CREATED) {
            deleteGroupConversation(groupConversation);
        }

        invitation.setStatus(InvitationDescriptor.Status.WITHDRAWN);
        mServiceProvider.updateDescriptor(invitation);
    }

    void processOnLeaveGroup(@NonNull UUID groupTwincodeId, @NonNull UUID memberId, @NonNull UUID peerTwincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLeaveGroup: groupTwincodeId=" + groupTwincodeId + " memberId=" + memberId);
        }

        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
        if (groupConversation == null) {
            return;
        }

        boolean groupDeleted = false;
        final UUID deletedMemberId;

        // We have left the group, finish the process by deleting the conversation.
        // We have sent a leave-group operation for each peer we know.  We have to wait
        // for all the leave-group operation to proceed and we can delete the conversation
        // only at the end.  If we delete the conversation immediately, some peers will not
        // be informed that we left the group.
        if (groupConversation.getTwincodeOutboundId().equals(memberId)) {
            // Delete the member that received this leave-group request.
            final boolean deleted = delMember(groupConversation, peerTwincodeOutboundId);

            if (groupConversation.isEmpty()) {
                deleteGroupConversation(groupConversation);
                groupDeleted = true;
            }
            deletedMemberId = deleted ? peerTwincodeOutboundId : null;

            // We can delete the member only when it acknowledged the leave-group operation.
        } else if (memberId.equals(peerTwincodeOutboundId)) {
            final boolean deleted = delMember(groupConversation, memberId);
            deletedMemberId = deleted ? memberId : null;

        } else {
            // We informed another member that `groupOperation.memberTwincodeId` has left the group.
            deletedMemberId = null;
        }

        // A member was removed, notify upper layers.
        if (deletedMemberId != null && !groupDeleted) {
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onLeaveGroup(BaseService.DEFAULT_REQUEST_ID, groupConversation, deletedMemberId));
            }
        }
    }

    void processUpdateGroupMember(@NonNull ConversationImpl conversationImpl, @NonNull UUID groupTwincodeId,
                                  @NonNull UUID memberTwincodeId, long permissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processUpdateGroupMemberIQ: conversationImpl=" + conversationImpl
                    + " groupTwincodeId=" + groupTwincodeId + " memberTwincodeId=" + memberTwincodeId + " permissions=" + permissions);
        }

        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
        if (groupConversation != null && conversationImpl.isGroup() && conversationImpl.hasPermission(ConversationService.Permission.UPDATE_MEMBER)) {
            if (memberTwincodeId.equals(groupConversation.getTwincodeOutboundId())) {
                groupConversation.setPermissions(permissions);
                mServiceProvider.updateGroupConversation(groupConversation);
            } else {
                GroupMemberConversationImpl conversation = groupConversation.getMember(memberTwincodeId);
                if (conversation != null) {
                    conversation.setPermissions(permissions);
                    mServiceProvider.updateConversation(conversation, null);
                }
            }
        }
    }

    void deleteGroupConversation(@NonNull GroupConversationImpl groupConversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteGroupConversation: conversation=" + groupConversation);
        }

        EventMonitor.info(LOG_TAG, "Delete group conversation ", groupConversation.getId(),
                " to ", groupConversation.getPeerTwincodeOutboundId());

        // Delete the conversation we had for each member.
        while (true) {
            final ConversationImpl peer;

            peer = groupConversation.getFirstMember();
            if (peer == null) {
                break;
            }
            mConversationService.deleteConversation(peer);
        }

        mServiceProvider.deleteConversation(groupConversation);

        mConversationService.deleteFiles(groupConversation);

        // The group conversation was deleted, notify upper layers.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onDeleteGroupConversation(groupConversation.getId(), groupConversation.getContactId()));
        }
    }

    /**
     * Verify the member signature before adding it to the group.
     *
     * @param signerTwincode the twincode that signed the member.
     * @param groupTwincodeId the group that was joined.
     * @param memberTwincodeId the member who joined the group.
     * @param publicKey the member public key.
     * @param memberPermissions the member permission.
     * @param signature the signature to verify.
     * @return SUCCESS or an error code.
     */
    @NonNull
    private ErrorCode verifySignature(@NonNull TwincodeOutbound signerTwincode, @NonNull UUID groupTwincodeId,
                                      @NonNull UUID memberTwincodeId, @NonNull String publicKey, long memberPermissions,
                                      @NonNull String signature) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verifySignature: signerTwincode=" + signerTwincode + " groupTwincodeId=" + groupTwincodeId
                    + " memberTwincodeId=" + memberTwincodeId + " publicKey=" + publicKey + " memberPermissions=" + memberPermissions);
        }

        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);

            encoder.writeUUID(groupTwincodeId);
            encoder.writeUUID(memberTwincodeId);
            encoder.writeString(publicKey);
            encoder.writeLong(memberPermissions);

            return mCryptoService.verifyContent(signerTwincode, outputStream.toByteArray(), signature);

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Log.e(LOG_TAG, "Exception in verifySignature", exception);
            }
            return ErrorCode.LIBRARY_ERROR;
        }
    }

    /**
     * Sign the new member that joined the group so that other members can verify the new member's public key and trust it.
     *
     * @param twincodeOutbound our twincode used to signed the new member.
     * @param groupTwincodeId the group that is joined.
     * @param memberTwincodeId the member that joined the group.
     * @param publicKey the member public key.
     * @param memberPermissions the member permission.
     * @return the new member signature.
     */
    @Nullable
    private String signMember(@Nullable TwincodeOutbound twincodeOutbound, @NonNull UUID groupTwincodeId,
                              @NonNull UUID memberTwincodeId, @Nullable String publicKey, long memberPermissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "signMember: twincodeOutbound=" + twincodeOutbound + " memberTwincodeId=" + memberTwincodeId
                    + " publicKey=" + publicKey + " memberPermission=" + memberPermissions);
        }

        if (publicKey == null || twincodeOutbound == null) {
            return null;
        }

        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);

            encoder.writeUUID(groupTwincodeId);
            encoder.writeUUID(memberTwincodeId);
            encoder.writeString(publicKey);
            encoder.writeLong(memberPermissions);

            return mCryptoService.signContent(twincodeOutbound, outputStream.toByteArray());
        } catch (Exception exception) {
            if (Logger.ERROR) {
                Log.e(LOG_TAG, "Exception in signMember", exception);
            }
            return null;
        }
    }

    @NonNull
    private AddStatus addMember(@NonNull GroupConversationImpl groupConversation, @NonNull TwincodeOutbound memberTwincode,
                                long permissions, @Nullable UUID invitedContactId,
                                @Nullable List<OnJoinGroupIQ.MemberInfo> returnMembers, boolean propagate,
                                @Nullable UUID signedOffTwincodeId, @Nullable String signature) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addMember: groupConversation=" + groupConversation + " memberTwincode=" + memberTwincode);
        }

        final TwincodeOutbound twincodeOutbound = groupConversation.getTwincodeOutbound();
        if (groupConversation.getState() == ConversationService.GroupConversation.State.LEAVING || twincodeOutbound == null) {
            return AddStatus.ERROR;
        }

        final UUID memberTwincodeId = memberTwincode.getId();
        if (memberTwincodeId.equals(twincodeOutbound.getId())) {
            return AddStatus.NO_CHANGE;
        }

        AddStatus result;
        GroupMemberConversationImpl memberConversation = groupConversation.getMember(memberTwincodeId);
        if (memberConversation == null) {
            memberConversation = mServiceProvider.createGroupMemberConversation(groupConversation, memberTwincodeId,
                    permissions, invitedContactId);
            if (memberConversation == null) {
                // A limit is reached on the number of members, refuse the join.
                // The caller will get back a WITHDRAWN status.
                return AddStatus.ERROR;
            }
            result = AddStatus.NEW_MEMBER;

        } else {
            // Save the updated group member in the database.
            if (memberConversation.getPermissions() != permissions) {
                memberConversation.setPermissions(permissions);
                mServiceProvider.updateConversation(memberConversation, null);
            }
            result = AddStatus.NO_CHANGE;
        }

        if (returnMembers != null) {
            final Map<UUID, GroupMemberConversationImpl> otherMembers = groupConversation.getMembers();
            for (final GroupMemberConversationImpl member : otherMembers.values()) {

                final TwincodeOutbound peerTwincode = member.getPeerTwincodeOutbound();
                if (peerTwincode != null && !peerTwincode.getId().equals(memberTwincodeId) && !member.isLeaving()) {
                    final String publicKey = mCryptoService.getPublicKey(peerTwincode);
                    final UUID peerMemberId = peerTwincode.getId();
                    final long peerPermissions = member.getPermissions();

                    returnMembers.add(new OnJoinGroupIQ.MemberInfo(peerMemberId, publicKey, peerPermissions));
                }
            }
        }

        // This is a new member and we must propagate ourselves to him so that he knows us:
        // - for a legacy member, the twincode is not signed and we must do the join through a P2P connexion,
        // - for a signed member, we can invoke that member's twincode with secureInvokeTwincode().
        if (propagate && result == AddStatus.NEW_MEMBER) {
            final String publicKey = mCryptoService.getPublicKey(twincodeOutbound);

            final GroupOperation groupOperation = new GroupJoinOperation(memberConversation,
                    memberTwincode.isSigned() ? INVOKE_JOIN_GROUP : JOIN_GROUP,
                    groupConversation.getPeerTwincodeOutboundId(), twincodeOutbound.getId(),
                    groupConversation.getPermissions(), publicKey, signedOffTwincodeId, signature);
            mServiceProvider.storeOperation(groupOperation);
            Log.e(LOG_TAG, "ask-join-group " + groupOperation.getGroupId() + " memberTwincodeId=" + groupOperation.getMemberId()
                    + " permissions=" + groupConversation.getPermissions());

            mScheduler.addOperation(memberConversation, groupOperation, 0);
        }
        return result;
    }

    boolean delMember(@NonNull GroupConversationImpl groupConversation, @NonNull UUID memberTwincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "delMember: groupConversation=" + groupConversation + " memberTwincodeId=" + memberTwincodeOutboundId);
        }

        GroupMemberConversationImpl member = groupConversation.delMember(memberTwincodeOutboundId);
        if (member == null) {
            // This member is already removed.
            return false;
        }

        // Delete the conversation associated with the member that is removed.
        mConversationService.deleteConversation(member);
        return true;
    }

    @NonNull
    private List<ConversationService.ServiceObserver> getServiceObservers() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getServiceObservers");
        }

        return mConversationService.getObservers();
    }
}
