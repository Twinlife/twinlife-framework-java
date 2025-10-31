/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.GroupMemberConversation;
import org.twinlife.twinlife.ConversationService.GroupConversation;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.database.DatabaseObjectImpl;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Group conversation
 * <p>
 * The group is associated with a twincode called the groupTwincodeId.  This uniquely identifies the group and this is
 * shared by every member.  The groupTwincodeId is not used to send messages.
 * <p>
 * A member of the group creates a twincode to receive messages and it communicates the twincode to other peers.
 * Messages are received on the group incomingConversation which uses the member twincode inbound as input identification.
 * <p>
 * Each peer is represented by a GroupMemberConversationImpl to hold the peer's member twincode and resource id.
 */
public class GroupConversationImpl extends DatabaseObjectImpl implements GroupConversation {

    static final UUID SCHEMA_ID = UUID.fromString("963f8d06-1a57-4c54-a6ce-0f1fec3064c6");
    static final int SCHEMA_VERSION = 2;

    static final int FLAG_JOINED = 0x01;
    static final int FLAG_LEAVING = 0x02;
    static final int FLAG_DELETED = 0x04;

    @NonNull
    private final RepositoryObject mGroup;
    private final long mCreationDate;
    @NonNull
    private final UUID mId;
    private int mFlags;

    /**
     * The group members to which we are connected.
     */
    private final Map<UUID, GroupMemberConversationImpl> mMembers;

    @NonNull
    private final GroupMemberConversationImpl mIncomingConversation;

    /**
     * A bitmap of permissions granted to the user on this conversation.
     */
    private long mPermissions;
    private long mJoinPermissions;

    GroupConversationImpl(@NonNull DatabaseIdentifier identifier, @NonNull UUID conversationId,
                          @NonNull RepositoryObject group, long creationDate, @NonNull UUID resourceId, long permissions,
                          long joinPermissions, int flags) {
        super(identifier);

        mId = conversationId;
        mGroup = group;
        mCreationDate = creationDate;
        mPermissions = permissions;
        mJoinPermissions = joinPermissions;
        mFlags = flags;

        mMembers = new HashMap<>();
        mIncomingConversation = new GroupMemberConversationImpl(identifier, conversationId, this, creationDate,
                resourceId, null, mPermissions, 0, 0, 0,
                group.getTwincodeOutbound(), null);
    }

    void update(long permissions, long joinPermissions, int flags) {

        mPermissions = permissions;
        mJoinPermissions = joinPermissions;
        mFlags = flags;
    }

    //
    // Implements Conversation methods
    //

    @Override
    @NonNull
    public UUID getId() {

        return mId;
    }

    @Override
    @NonNull
    public UUID getTwincodeOutboundId() {

        final TwincodeOutbound twincodeOutbound = mGroup.getTwincodeOutbound();
        return twincodeOutbound == null ? Twincode.NOT_DEFINED : twincodeOutbound.getId();
    }

    @Nullable
    @Override
    public TwincodeOutbound getTwincodeOutbound() {

        return mGroup.getTwincodeOutbound();
    }

    @Override
    @NonNull
    public UUID getPeerTwincodeOutboundId() {

        final TwincodeOutbound peerTwincodeOutbound = mGroup.getPeerTwincodeOutbound();
        return peerTwincodeOutbound == null ? Twincode.NOT_DEFINED : peerTwincodeOutbound.getId();
    }

    @Nullable
    @Override
    public TwincodeOutbound getPeerTwincodeOutbound() {

        return mGroup.getPeerTwincodeOutbound();
    }

    @Override
    @NonNull
    public UUID getTwincodeInboundId() {

        final TwincodeInbound twincodeInbound = mGroup.getTwincodeInbound();
        return twincodeInbound == null ? Twincode.NOT_DEFINED : twincodeInbound.getId();
    }

    @Override
    @Nullable
    public TwincodeInbound getTwincodeInbound() {

        return mGroup.getTwincodeInbound();
    }

    @Override
    @Nullable
    public UUID getPeerConnectionId() {

        return mIncomingConversation.getPeerConnectionId();
    }

    @Override
    @NonNull
    public UUID getContactId() {

        return mGroup.getId();
    }

    @Override
    @NonNull
    public RepositoryObject getSubject() {

        return mGroup;
    }

    @Override
    public boolean isActive() {

        final State state = getState();
        return state == State.JOINED || state == State.CREATED;
    }

    @Override
    public boolean isGroup() {

        return true;
    }

    @Override
    public boolean isConversation(UUID id) {

        return mId.equals(id);
    }

    @Override
    public boolean hasPermission(ConversationService.Permission p) {

        return (mPermissions & (1L << p.ordinal())) != 0;
    }

    @Override
    public boolean hasPeer() {

        return !isEmpty();
    }

    void setPermissions(long permissions) {

        mPermissions = permissions;
        mIncomingConversation.mPermissions = permissions;
    }

    long getPermissions() {

        return mPermissions;
    }

    void setJoinPermissions(long permissions) {

        mJoinPermissions = permissions;
    }

    /**
     * Update the group state to join it with the given permissions.
     *
     * @param permissions the member's permissions
     * @return true if the group is now joined and false if it is being deleted or leaving.
     */
    synchronized boolean join(long permissions) {

        if ((mFlags & (FLAG_DELETED | FLAG_LEAVING)) != 0) {
            return false;
        }

        // If we are already joined, keep the current permissions.
        if ((mFlags & FLAG_JOINED) == 0) {
            mFlags |= FLAG_JOINED;
            mPermissions = permissions;
            mIncomingConversation.setPermissions(permissions);
            mJoinPermissions = permissions;
        }
        return true;
    }

    int getFlags() {

        return mFlags;
    }

    long getLocalCid() {

        return getDatabaseId().getId();
    }

    public synchronized State getState() {

        if ((mFlags & FLAG_DELETED) != 0) {
            return State.DELETED;
        }
        if ((mFlags & FLAG_LEAVING) != 0) {
            return State.LEAVING;
        }
        if ((mFlags & FLAG_JOINED) != 0) {
            return State.JOINED;
        }
        return State.CREATED;
    }

    @Override
    public long getJoinPermissions() {

        return mJoinPermissions;
    }

    synchronized void rejoin() {

        mFlags = 0;
        mPermissions = -1L;
        mJoinPermissions = -1L;
    }

    @Nullable
    synchronized GroupMemberConversationImpl leaveGroup(@NonNull UUID memberTwincodeOutboundId) {

        if (getTwincodeOutboundId().equals(memberTwincodeOutboundId)) {
            if ((mFlags & (FLAG_LEAVING | FLAG_DELETED)) != 0) {
                return null;
            }
            mFlags |= FLAG_LEAVING;
            mPermissions = 0;
            mJoinPermissions = 0;
            return mIncomingConversation;
        } else {
            GroupMemberConversationImpl member = mMembers.get(memberTwincodeOutboundId);
            if (member != null) {
                // Mark this member as leaving the group.
                // After this, we don't send him anything and we don't accept any new message from him.
                member.markLeaving();
            }
            return member;
        }
    }

    synchronized void addMember(@NonNull UUID memberTwincodeOutboundId, GroupMemberConversationImpl conversation) {

        mMembers.put(memberTwincodeOutboundId, conversation);
    }

    synchronized GroupMemberConversationImpl getMember(@NonNull UUID memberTwincodeOutbountId) {

        return mMembers.get(memberTwincodeOutbountId);
    }

    synchronized GroupMemberConversationImpl delMember(@NonNull UUID memberTwincodeOutboundId) {

        return mMembers.remove(memberTwincodeOutboundId);
    }

    @Nullable
    synchronized GroupMemberConversationImpl getConversation(long cid) {

        for (GroupMemberConversationImpl member : mMembers.values()) {
            if (member.getDatabaseId().getId() == cid) {
                return member;
            }
        }
        return null;
    }

    /**
     * Get the list of conversations to which some operation must be made.  When the `sendTo` is
     * defined, we only return the conversation for the matching group member.
     *
     * @param sendTo the optional group member.
     * @return the list of converations.
     */
    synchronized List<ConversationImpl> getConversations(@Nullable UUID sendTo) {
        final List<ConversationImpl> result = new ArrayList<>();

        // Take into account group members which are not leaving or keep only the sendTo member.
        for (GroupMemberConversationImpl member : mMembers.values()) {
            if (!member.isLeaving() && (sendTo == null || member.getPeerTwincodeOutboundId().equals(sendTo))) {
                result.add(member);
            }
        }
        return result;
    }

    @NonNull
    GroupMemberConversationImpl getIncomingConversation() {
        return mIncomingConversation;
    }

    /**
     * Get the number of active members.
     * We exclude the members that are leaving but still in our list.
     *
     * @return the number of active members.
     */
    synchronized int getActiveMemberCount() {

        int count = 0;

        for (GroupMemberConversationImpl member : mMembers.values()) {
            if (!member.isLeaving()) {
                count++;
            }
        }
        return count;
    }

    synchronized Map<UUID, GroupMemberConversationImpl> getMembers() {

        return new HashMap<>(mMembers);
    }

    synchronized boolean isEmpty() {

        return mMembers.isEmpty();
    }

    /**
     * Get the first conversation for a peer member.
     * (used to iterate over the list of members and remove them)
     *
     * @return a member conversation or null.
     */
    synchronized GroupMemberConversationImpl getFirstMember() {

        return mMembers.isEmpty() ? null : mMembers.values().iterator().next();
    }

    /**
     * Get the group members to which we are connected.
     *
     * @param filter a simple filter allowing to retrieve specific members.
     * @return a list of twincode outbound ids for the members we are connected.
     */
    @Override
    public synchronized List<GroupMemberConversation> getGroupMembers(ConversationService.MemberFilter filter) {

        final List<ConversationService.GroupMemberConversation> result = new ArrayList<>();
        for (GroupMemberConversationImpl member : mMembers.values()) {
            if (filter == ConversationService.MemberFilter.ALL_MEMBERS || !member.isLeaving()) {
                result.add(member);
            }
        }
        return result;
    }

    //
    // Override Object methods
    //

    public String toLogLine() {
        return "G=" + Utils.toLog(mId) + " in=" + Utils.toLog(getTwincodeInboundId())
                + " out=" + Utils.toLog(getTwincodeOutboundId())
                + " group=" + Utils.toLog(getContactId());
    }

    @Override
    @NonNull
    public String toString() {
        return "GroupConversationImpl[" + getDatabaseId() + " uuid=" + mId +
                " state=" + getState() +
                " group=" + mGroup +
                "]";
    }
}
