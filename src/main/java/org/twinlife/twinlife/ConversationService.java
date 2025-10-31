/*
 *  Copyright (c) 2015-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Houssem Temanni (Houssem.Temanni@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import android.graphics.Bitmap;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("unused")
public interface ConversationService extends BaseService<ConversationService.ServiceObserver> {

    String VERSION = "2.20.1";

    class ConversationServiceConfiguration extends BaseServiceConfiguration {

        public ConversationServiceConfiguration() {

            super(BaseServiceId.CONVERSATION_SERVICE_ID, VERSION, false);
        }
    }

    enum Permission {
        INVITE_MEMBER,
        UPDATE_MEMBER,
        REMOVE_MEMBER,
        SEND_MESSAGE,
        SEND_IMAGE,
        SEND_AUDIO,
        SEND_VIDEO,
        SEND_FILE,
        DELETE_MESSAGE,
        DELETE_IMAGE,
        DELETE_AUDIO,
        DELETE_VIDEO,
        DELETE_FILE,
        RESET_CONVERSATION,
        SEND_GEOLOCATION,
        SEND_TWINCODE,
        RECEIVE_MESSAGE,
        SEND_COMMAND
    }

    interface Conversation extends DatabaseObject {

        @NonNull
        UUID getId();

        @NonNull
        UUID getTwincodeOutboundId();
        @Nullable
        TwincodeOutbound getTwincodeOutbound();

        @NonNull
        UUID getPeerTwincodeOutboundId();
        @Nullable
        TwincodeOutbound getPeerTwincodeOutbound();

        @NonNull
        UUID getContactId();
        @NonNull
        RepositoryObject getSubject();

        @NonNull
        UUID getTwincodeInboundId();
        @Nullable
        TwincodeInbound getTwincodeInbound();

        @Nullable
        UUID getPeerConnectionId();

        boolean isActive();

        boolean isGroup();

        boolean isConversation(UUID id);

        boolean hasPermission(Permission p);

        boolean hasPeer();
    }

    interface GroupMemberConversation extends Conversation {

        /**
         * Get the twincode that identifies the member within the group.
         *
         * @return the group member twincode.
         */
        @NonNull
        UUID getMemberTwincodeOutboundId();

        /**
         * Check if we initiated a leaveGroup() operation for this member.
         * While in this state, we don't send message to this member anymore until
         * the leave-group operation is executed and the member removed.
         *
         * @return True if the member is leaving.
         */
        boolean isLeaving();

        /**
         * Get the contact id that was used to invite this member.
         *
         * When the member was invited by another group member, null is returned.
         *
         * @return the contactId or null if the member was invited by another person.
         */
        @Nullable
        UUID getInvitedContactId();

        /**
         * Get the group conversation that contains this member.
         *
         * @return the group conversation.
         */
        @NonNull
        GroupConversation getGroupConversation();
    }

    enum MemberFilter {
        ALL_MEMBERS,

        JOINED_MEMBERS
    }

    /**
     * The maximum number of members within the group.
     * - this limit is checked when a member joins/accepts an invitation,
     * - before sending an invitation.
     * Due to the distributed and offline nature of peers, it is still possible to have
     * groups with more members.
     */
    int MAX_GROUP_MEMBERS = BuildConfig.MAX_GROUP_MEMBERS;

    interface GroupConversation extends Conversation {
        enum State {
            CREATED,
            JOINED,
            LEAVING,
            DELETED
        }

        /**
         * Get the current state of the group conversation.
         *
         * @return the group conversation state.
         */
        State getState();

        /**
         * Get the group members to which we are connected.
         *
         * @param filter a simple filter allowing to retrieve specific members.
         * @return a set of twincode outbound ids for the members we are connected.
         */
        List<GroupMemberConversation> getGroupMembers(MemberFilter filter);

        /**
         * Get the permissions map for users that will join the group.
         *
         * @return the permission bitmap for users that will join the group.
         */
        long getJoinPermissions();
    }

    class DescriptorId implements Serializable {
        public final long id;
        @NonNull
        public final UUID twincodeOutboundId;
        public final long sequenceId;

        public DescriptorId(long id, @NonNull UUID twincodeOutboundId, long sequenceId) {

            this.id = id;
            this.twincodeOutboundId = twincodeOutboundId;
            this.sequenceId = sequenceId;
        }

        @Override
        public boolean equals(Object object) {

            if (!(object instanceof DescriptorId)) {

                return false;
            }

            DescriptorId descriptorId = (DescriptorId) object;

            return descriptorId.twincodeOutboundId.equals(twincodeOutboundId) && descriptorId.sequenceId == sequenceId;
        }

        @Override
        public int hashCode() {

            int result = 17;
            result = 31 * result + twincodeOutboundId.hashCode();
            result = 31 * result + Long.hashCode(sequenceId);

            return result;
        }

        @Override
        @NonNull
        public String toString() {

            return twincodeOutboundId + ":" + sequenceId;
        }

        /**
         * Convert a string into a descriptor id in the form UUID:sequenceId
         *
         * @param value the string to convert.
         * @return the descriptor id or null if it was invalid.
         */
        public static DescriptorId fromString(String value) {

            if (value == null) {
                return null;
            }
            int pos = value.indexOf(':');
            if (pos < 0) {
                return null;
            }
            try {
                UUID uuid = UUID.fromString(value.substring(0, pos));
                long sequence = Long.parseLong(value.substring(pos + 1));
                return new DescriptorId(0, uuid, sequence);
            } catch (Exception ex) {
                return null;
            }
        }
    }

    enum AnnotationType {
        // The descriptor is the result of a forward.
        FORWARD,

        // The descriptor was forwarded.
        FORWARDED,

        // The descriptor was saved.
        SAVE,

        // The descriptor is marked by a like annotation: the getValue() returns the like code.
        LIKE,

        // The descriptor is marked by an answer of a poll: the getValue() gives the vote entry.
        POLL
    }

    class DescriptorAnnotation {
        @NonNull
        private final AnnotationType mType;
        private final int mCount;
        private final int mValue;

        public DescriptorAnnotation(@NonNull AnnotationType type, int value, int count) {
            mType = type;
            mValue = value;
            mCount = count;
        }

        @NonNull
        public AnnotationType getType() {

            return mType;
        }

        public int getCount() {

            return mCount;
        }

        public int getValue() {

            return mValue;
        }
    }

    interface Descriptor {

        enum Type {
            DESCRIPTOR,
            OBJECT_DESCRIPTOR,
            TRANSIENT_OBJECT_DESCRIPTOR,
            FILE_DESCRIPTOR,
            IMAGE_DESCRIPTOR,
            AUDIO_DESCRIPTOR,
            VIDEO_DESCRIPTOR,
	        NAMED_FILE_DESCRIPTOR,
	        INVITATION_DESCRIPTOR,
            GEOLOCATION_DESCRIPTOR,
            TWINCODE_DESCRIPTOR,
            CALL_DESCRIPTOR,
            CLEAR_DESCRIPTOR
        }

        Type getType();

        @NonNull
        UUID getTwincodeOutboundId();

        long getSequenceId();

        @NonNull
        DescriptorId getDescriptorId();

        // The optional descriptor id we are responding.
        @Nullable
        DescriptorId getReplyToDescriptorId();

        void setReplyToDescriptor(@Nullable Descriptor replyToDescriptor);

        @Nullable
        Descriptor getReplyToDescriptor();

        // The optional twincode outbound to which the descriptor was sent.
        @Nullable
        UUID getSendTo();

        long getCreatedTimestamp();

        long getUpdatedTimestamp();

        long getSentTimestamp();

        long getReceivedTimestamp();

        long getReadTimestamp();

        long getDeletedTimestamp();

        long getPeerDeletedTimestamp();

        // The expiration deadline (-1 if there is no deadline or the message was not yet read).
        long getExpireTimestamp();

        // The expiration timeout in ms for this descriptor.
        long getExpireTimeout();

        // Returns true if this descriptor has expired.
        boolean isExpired();

        // Get the annotation of a given type.
        @Nullable
        DescriptorAnnotation getAnnotation(@NonNull AnnotationType type);

        // Get the list of annotations on this descriptor.
        @Nullable
        List<DescriptorAnnotation> getAnnotations(@NonNull AnnotationType type);
    }

    interface ObjectDescriptor extends Descriptor {

        @NonNull
        String getMessage();

        boolean isCopyAllowed();

        boolean isEdited();

        boolean DEFAULT_COPY_ALLOWED = BuildConfig.DEFAULT_COPY_ALLOWED;
    }

    interface TransientObjectDescriptor extends Descriptor {

        @NonNull
        Object getObject();
    }

    interface FileDescriptor extends Descriptor {

        String getPath();

        String getExtension();

        long getLength();

        long getEnd();

        boolean isAvailable();

        boolean isCopyAllowed();

        boolean DEFAULT_COPY_ALLOWED = BuildConfig.DEFAULT_COPY_ALLOWED;
    }

    interface ImageDescriptor extends FileDescriptor {

        int getWidth();

        int getHeight();

        /**
         * Check if this is a gif image.
         *
         * @return true if this is a gif image.
         */
        boolean isGif();
    }

    interface AudioDescriptor extends FileDescriptor {

        long getDuration();
    }

    interface VideoDescriptor extends FileDescriptor {

        int getWidth();

        int getHeight();

        long getDuration();
    }

    interface NamedFileDescriptor extends FileDescriptor {

        String getName();
    }

    interface InvitationDescriptor extends Descriptor {

        enum Status {
            // Invitation is pending.
            PENDING,

            // Invitation was accepted by user (waiting to become group member).
            ACCEPTED,

            // Invitation was accepted by user and s/he is now member of the group.
            JOINED,

            // Invitation was refused by user.
            REFUSED,

            // Invitation was withdrawn.
            WITHDRAWN
        }

        @NonNull
        UUID getGroupTwincodeId();

        @Nullable
        UUID getMemberTwincodeId();

        @Nullable
        UUID getInviterTwincodeId() ;

        @NonNull
        String getName();

        @Nullable
        String getPublicKey();

        @NonNull
        Status getStatus();
    }

    interface TwincodeDescriptor extends Descriptor {
        @NonNull
        UUID getTwincodeId();

        @NonNull
        UUID getSchemaId();

        @Nullable
        String getPublicKey();

        boolean isCopyAllowed();
    }

    enum UpdateType {
        CONTENT,
        TIMESTAMPS,
        LOCAL_ANNOTATIONS,
        PEER_ANNOTATIONS,
        PROTECTION
    }

    interface GeolocationDescriptor extends Descriptor {

        double getLongitude();

        double getLatitude();

        double getAltitude();

        double getMapLongitudeDelta();

        double getMapLatitudeDelta();

        String getLocalMapPath();

        boolean isValidLocalMap();
    }

    // Audio and Video call descriptor.
    interface CallDescriptor extends Descriptor {

        boolean isVideo();

        boolean isIncoming();

        boolean isAccepted();

        long getDuration();

        @Nullable
        TerminateReason getTerminateReason();
    }

    // Descriptor created when a resetConversation() is made.
    interface ClearDescriptor extends Descriptor {

        long getClearTimestamp();
    }

    interface ServiceObserver extends BaseService.ServiceObserver {

        void onCreateConversation(@NonNull Conversation conversation);

        void onResetConversation(@NonNull Conversation conversation, @NonNull ClearMode clearMode);

        void onDeleteConversation(@NonNull UUID conversationId);

        void onPushDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor);

        void onPopDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor);

        void onUpdateDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor, UpdateType updateType);

        void onUpdateAnnotation(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor,
                                @NonNull TwincodeOutbound annotatingUser);

        void onMarkDescriptorRead(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor);

        void onMarkDescriptorDeleted(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor);

        void onDeleteDescriptors(long requestId, @NonNull Conversation conversation, @NonNull DescriptorId[] descriptorList);

        void onCreateGroupConversation(@NonNull GroupConversation conversation);

        void onInviteGroup(long requestId, @NonNull Conversation conversation, @NonNull InvitationDescriptor invitation);

        void onInviteGroupRequest(long requestId, @NonNull Conversation conversation, @NonNull InvitationDescriptor invitation);

        void onJoinGroup(long requestId, @Nullable GroupConversation conversation, @NonNull InvitationDescriptor invitation);

        void onJoinGroupResponse(long requestId, @NonNull GroupConversation conversation, @Nullable InvitationDescriptor invitation);

        void onJoinGroupRequest(long requestId, @NonNull GroupConversation conversation, @Nullable InvitationDescriptor invitation, @NonNull UUID memberId);

        void onLeaveGroup(long requestId, @NonNull GroupConversation conversation, @NonNull UUID memberId);

        void onDeleteGroupConversation(@NonNull UUID conversationId, @NonNull UUID groupId);

        void onRevoked(@NonNull Conversation conversation);

        void onSignatureInfo(@NonNull Conversation conversation, @NonNull TwincodeOutbound twincodeOutbound);
    }

    @SuppressWarnings("EmptyMethod")
    class DefaultServiceObserver extends BaseService.DefaultServiceObserver implements ServiceObserver {

        @Override
        public void onCreateConversation(@NonNull Conversation conversation) {
        }

        @Override
        public void onResetConversation(@NonNull Conversation conversation, @NonNull ClearMode clearMode) {
        }

        @Override
        public void onDeleteConversation(@NonNull UUID conversationId) {
        }

        @Override
        public void onPushDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        }

        @Override
        public void onPopDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        }

        @Override
        public void onUpdateDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor, UpdateType updateType) {
        }

        @Override
        public void onUpdateAnnotation(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor,
                                       @NonNull TwincodeOutbound annotatingUser) {
        }

        @Override
        public void onMarkDescriptorRead(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        }

        @Override
        public void onMarkDescriptorDeleted(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        }

        @Override
        public void onDeleteDescriptors(long requestId, @NonNull Conversation conversation, @NonNull DescriptorId[] descriptorList) {
        }

        @Override
        public void onCreateGroupConversation(@NonNull GroupConversation conversation) {

        }

        @Override
        public void onInviteGroup(long requestId, @NonNull Conversation conversation, @NonNull InvitationDescriptor invitation) {

        }

        @Override
        public void onJoinGroup(long requestId, @Nullable GroupConversation conversation, @NonNull InvitationDescriptor invitation) {

        }

        @Override
        public void onJoinGroupResponse(long requestId, @NonNull GroupConversation conversation, @Nullable InvitationDescriptor invitation) {

        }

        @Override
        public void onJoinGroupRequest(long requestId, @NonNull GroupConversation conversation, @Nullable InvitationDescriptor invitation, @NonNull UUID memberId) {

        }

        @Override
        public void onLeaveGroup(long requestId, @NonNull GroupConversation conversation, @NonNull UUID memberId) {

        }

        @Override
        public void onInviteGroupRequest(long requestId, @NonNull Conversation conversation, @NonNull InvitationDescriptor invitation) {

        }

        @Override
        public void onDeleteGroupConversation(@NonNull UUID conversationId, @NonNull UUID groupId) {

        }

        @Override
        public void onRevoked(@NonNull Conversation conversation) {
        }

        @Override
        public void onSignatureInfo(@NonNull Conversation conversation, @NonNull TwincodeOutbound twincodeOutbound) {

        }
    }

    @Nullable
    Conversation getConversation(@NonNull RepositoryObject object);

    void incomingPeerConnection(@NonNull UUID peerConnectionId, @NonNull RepositoryObject object,
                                @NonNull TwincodeOutbound peerTwincodeOutbound, boolean create);

    /**
     * Update the 1-1 conversation when the peer twincode is received.  This is called when the peer invoked the `pair::bind`
     * we must update the conversation if it was created with the previous twincode if that conversation exists.
     * Then, we can also trigger the synchronizeConversation to execute the pending operation.
     *
     * @param subject the subject that was updated.
     * @param peerTwincodeOutbound the new peer twincode.
     */
    void updateConversation(@NonNull RepositoryObject subject, @NonNull TwincodeOutbound peerTwincodeOutbound);

    @NonNull
    List<Conversation> listConversations(@NonNull Filter<Conversation> filter);

    @Nullable
    Conversation getOrCreateConversation(@NonNull RepositoryObject subject);

    @Nullable
    GroupConversation getGroupConversationWithGroupTwincodeId(@NonNull UUID groupTwincodeId);

    @Nullable
    GroupMemberConversation getGroupMemberConversation(@NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId);

    /**
     * Get the pending invitations that were sent to contacts for the given group.
     *
     * The key is the contactId associated with the conversation to which the invitation was sent.
     *
     * @return the contacts with their pending invitations.
     */
    @NonNull
    Map<UUID, InvitationDescriptor> listPendingInvitations(@NonNull RepositoryObject group);

    enum ClearMode {
        // Clear local media but keep messages and thumbnails.
        CLEAR_MEDIA,

        // Clear local only messages and files including thumbnails.
        CLEAR_LOCAL,

        // Clear only media on both sides (no clear descriptor inserted).
        CLEAR_BOTH_MEDIA,

        // Clear on both sides (a clear descriptor is added).
        CLEAR_BOTH
    }

    /**
     * Clear the conversation descriptors locally and optionally on the peer's device.
     * When the conversation is cleared on both sides, a ClearDescriptor is created.
     * This operation triggers:
     *  onPushDescriptor() if a ClearDescriptor is created (clearMode = CLEAR_BOTH),
     *  onResetConversation() if some descriptors were removed.
     *
     * @param conversation the conversation to clear.
     * @param clearDate descriptors older than the clear date are removed.
     * @param clearMode the clear mode to control local or local+remote clear.
     * @return SUCCESS if the clear succeeded.
     */
    @NonNull
    ErrorCode clearConversation(@NonNull Conversation conversation, long clearDate, @NonNull ClearMode clearMode);

    @NonNull
    ErrorCode deleteConversation(@NonNull RepositoryObject subject);

    void forwardDescriptor(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                           @NonNull DescriptorId descriptorId, boolean copyAllowed, long expireTimeout);

    void pushMessage(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo, @Nullable DescriptorId replyTo,
                     @NonNull String message, boolean copyAllowed, long expiration);

    void pushTransientObject(long requestId, @NonNull Conversation conversation, @NonNull Object object);

    void pushCommand(long requestId, @NonNull Conversation conversation, @NonNull Object object);

    void pushFile(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo, @Nullable DescriptorId replyTo,
                  @NonNull Uri path, @NonNull String name, @NonNull Descriptor.Type type,
                  boolean toBeDeleted, boolean copyAllowed, long expiration);

    void pushGeolocation(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                         @Nullable DescriptorId replyTo, double longitude, double latitude, double altitude,
                         double mapLongitudeDelta, double mapLatitudeDelta, @Nullable Uri localMapPath, long expiration);

    void updateGeolocation(long requestId, @NonNull Conversation conversation, @NonNull DescriptorId descriptorId,
                           double longitude, double latitude, double altitude,
                           double mapLongitudeDelta, double mapLatitudeDelta, @Nullable Uri localMapPath);

    void saveGeolocationMap(long requestId, @NonNull Conversation conversation, @NonNull DescriptorId descriptorId,
                            @Nullable Uri localMapPath);

    void pushTwincode(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo, @Nullable DescriptorId replyTo,
                      @NonNull UUID twincodeId, @NonNull UUID schemaId, @Nullable String publicKey, boolean copyAllowed, long expiration);

    void updateDescriptor(long requestId, @NonNull DescriptorId descriptorId, @Nullable String message,
                          @Nullable Boolean copyAllowed, @Nullable Long expiration);

    void startCall(long requestId, @NonNull RepositoryObject subject, boolean isVideo, boolean isIncoming);

    void acceptCall(long requestId, @NonNull UUID twincodeOutboundId, @NonNull DescriptorId descriptorId);

    void terminateCall(long requestId, @NonNull UUID twincodeOutboundId, @NonNull DescriptorId descriptorId, @NonNull TerminateReason terminateReason);

    void acceptPushTwincode(@NonNull UUID schemaId);

    /**
     * Get the descriptor that was sent with the given descriptor Id.
     *
     * @param descriptorId the descriptor id.
     * @return the descriptor or null if it is not valid or does not exist.
     */
    @Nullable
    Descriptor getDescriptor(@NonNull DescriptorId descriptorId);

    /**
     * Get the geolocation that was sent with the given descriptor Id.
     *
     * @param descriptorId the geolocation descriptor id.
     * @return the geolocation descriptor or null if it is not valid or does not exist.
     */
    void getGeolocation(@NonNull DescriptorId descriptorId, @NonNull Consumer<GeolocationDescriptor> consumer);

    void markDescriptorRead(long requestId, @NonNull DescriptorId descriptorId);

    void markDescriptorDeleted(long requestId, @NonNull DescriptorId descriptorId);

    /**
     * Set the annotation with the value on the descriptor.  If the annotation already exists, the value
     * is updated.
     *
     * @param descriptorId the descriptor to mark.
     * @param type the annotation type (except FORWARD and FORWARDED which are managed internally).
     * @param value the value to associate.
     * @return SUCCESS if the annotation is updated.
     */
    @NonNull
    ErrorCode setAnnotation(@NonNull DescriptorId descriptorId,
                            @NonNull AnnotationType type, int value);

    /**
     * Remove the annotation on the descriptor.  The operation can only remove the annotations that the current
     * device has set.
     *
     * @param descriptorId the descriptor id to unmark.
     * @param type the annotation type to remove (except FORWARD and FORWARDED).
     * @return SUCCESS if the annotation is removed.
     */
    @NonNull
    ErrorCode deleteAnnotation(@NonNull DescriptorId descriptorId, @NonNull AnnotationType type);

    /**
     * Toggle the descriptor annotation:
     *
     * - If the annotation with the same value exists, it is removed,
     * - If the annotation with another value exists, the annotation is updated with the new value,
     * - If the annotation does not exist, it is added as a local annotation.
     *
     * When the annotation is inserted, modified or removed, the annotation update is propagated to the peers.
     *
     * @param descriptorId the descriptor to mark.
     * @param type the annotation type (except FORWARD and FORWARDED which are managed internally).
     * @param value the value to associate.
     * @return SUCCESS if the annotation is updated.
     */
    @NonNull
    ErrorCode toggleAnnotation(@NonNull DescriptorId descriptorId,
                               @NonNull AnnotationType type, int value);

    /**
     * Get the descriptor annotation indexed by the owner twincode id.
     *
     * @param descriptorId the descriptor id.
     * @return a map keyed on the twincode id of the user who annotated and giving the annotation.
     */
    @Nullable
    Map<TwincodeOutbound, DescriptorAnnotation> listAnnotations(@NonNull DescriptorId descriptorId);

    void deleteDescriptor(long requestId, @NonNull DescriptorId descriptorId);

    @Nullable
    List<Descriptor> getConversationDescriptors(@NonNull Conversation conversation,  @NonNull DisplayCallsMode callsMode,
                                                long beforeTimestamp, int maxDescriptors);

    /**
     * For each {@link Descriptor} with a replyToDescriptorId, fetch the corresponding Descriptor
     * and set replyToDescriptor of the original Descriptor.
     * @param descriptors the descriptors to augment with their replyToDescriptor.
     */
    void getReplyTos(@NonNull List<Descriptor> descriptors);

    @Nullable
    List<Descriptor> getConversationTypeDescriptors(@NonNull Conversation conversation, @NonNull Descriptor.Type[] types,
                                                    @NonNull DisplayCallsMode callsMode, long beforeTimestamp, int maxDescriptors);

    @Nullable
    List<Descriptor> getDescriptors(@NonNull Descriptor.Type[] types, @NonNull DisplayCallsMode callsMode,
                                    long beforeTimestamp, int maxDescriptors);

    /**
     * Search the descriptors from a list of conversations and matching a given search text.
     * The final list is composed of `{ conversation, descriptor }` pairs and sorted on the
     * descriptor creation date.
     *
     * @param conversations the list of conversations
     * @param beforeTimestamp consider only descriptors created before the given timestamp.
     * @param maxDescriptors the maximum number of descriptors to return.
     * @return the list of descriptors or null if the service is disabled.
     */
    @Nullable
    List<Pair<Conversation, Descriptor>> searchDescriptors(@NonNull List<Conversation> conversations,
                                                           @NonNull String searchText,
                                                           long beforeTimestamp, int maxDescriptors);

    /**
     * Get a map of conversations filtered by the given filter and for each of them, get the last descriptor
     * sent or received.  If a conversation has no descriptor, a null entry is added for it.
     *
     * @param filter the filter for the conversation.
     * @param callsMode the mode to filter the call descriptors.
     * @return the map of conversations with their last descriptor.
     */
    @Nullable
    Map<Conversation, Descriptor> getLastConversationDescriptors(@NonNull Filter<Conversation> filter,
                                                                 @NonNull DisplayCallsMode callsMode);

    /**
     * Get the twincode of descriptors used in the conversation and before the specified date.
     * When a type is given, look only for descriptors of the given type.
     *
     * @param conversation the conversation to look.
     * @param type the optional type to filter descriptors.
     * @param beforeTimestamp the date before which we consider the descriptors.
     * @return a list of twincodes identifying users that sent a content (message, file, ...).
     */
    @Nullable
    Set<UUID> getConversationTwincodes(@NonNull Conversation conversation, @Nullable Descriptor.Type type, long beforeTimestamp);

    /**
     * Create a new group conversation.
     *
     * @param group the group repository object.
     * @param owner when set the group is created in the JOINED state otherwise it is in the CREATED state.
     * @return the group conversation instance or null.
     */
    @Nullable
    GroupConversation createGroup(@NonNull RepositoryObject group, boolean owner);

    /**
     * Send an invitation to the peer to join the group.
     *
     * On SUCCESS, invoke the onInviteGroup callback with the invitation that was queued.
     *
     * When the invitation request is processed by the peer, the onInviteGroupResponse callback is called with
     * the P2P conversation and the invitation.
     *
     * @param requestId the request id.
     * @param conversation the peer conversation to which the invitation is sent.
     * @param group the group to invite.
     * @param name the group name.
     * @return SUCCESS if the invitation is queued.
     */
    ErrorCode inviteGroup(long requestId, @NonNull Conversation conversation, @NonNull RepositoryObject group, @NonNull String name);

    /**
     * Withdraw an invitation that was sent for the peer to join the group.
     *
     * @param requestId the request id.
     * @param descriptor the invitation descriptor.
     * @return SUCCESS if the invitation is removed.
     */
    ErrorCode withdrawInviteGroup(long requestId, @NonNull InvitationDescriptor descriptor);

    /**
     * Accept or refuse to join a group described by the invitation.  The invitation status is changed
     * to ACCEPTED when the memberTwincode(s) and groupId are valid, otherwise it is changed to REFUSED.
     *
     * On SUCCESS, invoke the onJoinGroup callback with the invitation.
     *
     * When the join request is processed by the peer, the onJoinGroupResponse callback is called with
     * the group and invitation.
     *
     * @param requestId the request id.
     * @param descriptorId the invitation descriptor id.
     * @param group the group repository object if the invitation is accepted or NULL to refuse.
     * @return SUCCESS if the join request is queued.
     */
    ErrorCode joinGroup(long requestId, @NonNull DescriptorId descriptorId, @Nullable RepositoryObject group);

    /**
     * Member subscribes to the group.
     *
     * @param requestId the request id.
     * @param group the group twincode to subscribe.
     * @param memberTwincode the new group member twincode to subscribe in the group.
     * @param permissions the member's permission.
     * @return SUCCESS if the new member is added.
     */
    ErrorCode subscribeGroup(long requestId, @NonNull RepositoryObject group, @NonNull TwincodeOutbound memberTwincode, long permissions);

    /**
     * Update the group conversation after we are registered in the group.
     *
     * @param requestId the request id.
     * @param group the group identification.
     * @param adminTwincode the twincode that identified the admin/engine.
     * @param adminPermissions the admin permissions.
     * @param permissions the current member's permission.
     * @return SUCCESS if the group registration succeeded.
     */
    ErrorCode registeredGroup(long requestId, @NonNull RepositoryObject group, @NonNull TwincodeOutbound adminTwincode,
                              long adminPermissions, long permissions);

    /**
     * Leave the member from the group.
     *
     * @param requestId the request id.
     * @param group the group to leave.
     * @param memberTwincodeId the group member to remove from the group.
     * @return SUCCESS if the leave request is queued.
     */
    ErrorCode leaveGroup(long requestId, @NonNull RepositoryObject group, @NonNull UUID memberTwincodeId);

    /**
     * Get the invitation that was sent with the given descriptor Id.
     *
     * @param descriptorId the invitation descriptor id.
     * @return the invitation descriptor or null if it is not valid or does not exist.
     */
    InvitationDescriptor getInvitation(@NonNull DescriptorId descriptorId);

    /**
     * Change the permissions assigned to the given user in the group.
     *
     * @param group the group to update.
     * @param memberTwincodeId when not null the group member to assign the permissions.
     * @param permissions the new permission map to assign.
     * @return SUCCESS if the permissions are updated.
     */
    @NonNull
    ErrorCode setPermissions(@NonNull RepositoryObject group, @Nullable UUID memberTwincodeId, long permissions);

    /**
     * Get the thumbnail associated with the image or video descriptor.
     *
     * @param descriptor the image or video descriptor.
     * @return the optional thumbnail bitmap.
     */
    @Nullable
    Bitmap getDescriptorThumbnail(@NonNull FileDescriptor descriptor);

    /**
     * Get the thumbnail file  associated with the image or video descriptor.
     *
     * @param descriptor the image or video descriptor.
     * @return the optional thumbnail file.
     */
    @Nullable
    File getDescriptorThumbnailFile(@NonNull FileDescriptor descriptor);
}
