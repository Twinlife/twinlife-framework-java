/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.TwincodeOutbound;

import java.util.UUID;

/**
 * Group member conversation.
 *
 * Each group member is represented by the GroupMemberConversationImpl.
 * When a group member is sent a leave-group operation, its permissions are cleared.
 * This prevents this members to do anything and hide it to upper layers with the JOINED_MEMBERS filter.
 */
public class GroupMemberConversationImpl extends ConversationImpl implements ConversationService.GroupMemberConversation {

    @NonNull
    private final GroupConversationImpl mGroup;
    @Nullable
    private final UUID mInvitedContactId;
    @NonNull
    private final TwincodeOutbound mPeerTwincodeOutbound;

    GroupMemberConversationImpl(@NonNull DatabaseIdentifier identifier, @NonNull UUID conversationId,
                                @NonNull GroupConversationImpl group, long creationDate,
                                @NonNull UUID resourceId, @Nullable UUID peerResourceId, long permissions,
                                long lastConnectDate, long lastRetryDate, int flags,
                                @NonNull TwincodeOutbound peerTwincodeOutbound,
                                @Nullable UUID invitedContactId) {
        super(identifier, conversationId, group.getSubject(), creationDate, resourceId, peerResourceId,
                permissions, lastConnectDate, lastRetryDate, flags);

        mGroup = group;
        mInvitedContactId = invitedContactId;
        mPeerTwincodeOutbound = peerTwincodeOutbound;
    }

    @Nullable
    @Override
    public TwincodeOutbound getPeerTwincodeOutbound() {

        return mPeerTwincodeOutbound;
    }

    @Override
    public boolean isGroup() {

        return true;
    }

    @Override
    public boolean isConversation(UUID id) {

        return mGroup.getId().equals(id);
    }

    @Override
    public boolean isActive() {

        return mGroup.isActive();
    }

    @Override
    public boolean hasPeer() {

        return true;
    }

    /**
     * Mark this group member as leaving, that is we sent him or scheduled a leave-group operation.
     */
    void markLeaving() {

        mPermissions = 0;
    }

    /**
     * Get the twincode that identifies the member within the group.
     *
     * @return the group member twincode.
     */
    @NonNull
    public UUID getMemberTwincodeOutboundId() {

        return mPeerTwincodeOutbound.getId();
    }

    /**
     * Check if we initiated a leaveGroup() operation for this member.
     * While in this state, we don't send message to this member anymore until
     * the leave-group operation is executed and the member removed.
     *
     * @return True if the member is leaving.
     */
    public boolean isLeaving() {

        return mPermissions == 0;
    }

    /**
     * Get the contact id that was used to invite this member.
     *
     * When the member was invited by another group member, null is returned.
     *
     * @return the contactId or null if the member was invited by another person.
     */
    @Nullable
    public UUID getInvitedContactId() {

        return mInvitedContactId;
    }

    /**
     * Get the group conversation that contains this member.
     *
     * @return the group conversation.
     */
    @Override
    @NonNull
    public GroupConversation getGroupConversation() {

        return mGroup;
    }

    @Nullable
    @Override
    GroupConversationImpl getGroup() {

        return mGroup;
    }

    @NonNull
    Conversation getMainConversation() {

        return mGroup;
    }

    /**
     * Get the group local conversation id.
     *
     * @return the group local conversation id.
     */
    long getGroupLocalCid() {

        return mGroup.getLocalCid();
    }
}
