/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface PeerCallService extends BaseService<PeerCallService.ServiceObserver> {

    String VERSION = "1.4.1";

    enum MemberStatus {
        NEW_MEMBER,
        NEW_MEMBER_NEED_SESSION,
        DEL_MEMBER
    }

    class MemberInfo {
        public MemberStatus status;
        public String memberId;
        public UUID p2pSessionId;

        public MemberInfo(@NonNull String memberId) {
            this.memberId = memberId;
            this.p2pSessionId = null;
            this.status = MemberStatus.NEW_MEMBER_NEED_SESSION;
        }

        public MemberInfo(@NonNull String memberId, @NonNull UUID p2pSessionId) {
            this.memberId = memberId;
            this.p2pSessionId = p2pSessionId;
            this.status = MemberStatus.NEW_MEMBER;
        }
    }

    interface ServiceObserver extends BaseService.ServiceObserver {

        default void onCreateCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull String memberId, int maxMemberCount){}

        default void onJoinCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull String memberId,
                            @NonNull List<MemberInfo> members){}

        default void onLeaveCallRoom(long requestId, @NonNull UUID callRoomId){}

        default void onDestroyCallRoom(long requestId, @NonNull UUID callRoomId){}

        default void onInviteCallRoom(@NonNull UUID callRoomId, @NonNull UUID twincodeId, @Nullable UUID p2pSession, int maxCount) {}

        default void onMemberJoinCallRoom(@NonNull UUID callRoomId, @NonNull String memberId, @Nullable UUID p2pSession,
                                          @NonNull MemberStatus status) {}

        default void onTransferDone(){}
    }

    class PeerCallServiceConfiguration extends BaseServiceConfiguration {


        public PeerCallServiceConfiguration() {

            super(BaseServiceId.PEER_CALL_SERVICE_ID, VERSION, true);
        }
    }

    /**
     * Create a call room with the given twincode identification.  The user must be owner of that twincode.
     * A list of member twincode with their optional P2P session id can be passed and those members are invited
     * to join the room.  Members that are invited will receive a call to their `onInviteCallRoom` callback.
     *
     * @param requestId the request identifier.
     * @param twincodeOut the owner twincode.
     * @param members the list of members to invite.
     */
    void createCallRoom(long requestId, @NonNull UUID twincodeOut, @NonNull Map<String, UUID> members);

    /**
     * Invite a new member in the call room.  Similar to `createCallRoom` to invite another member in the call room.
     *
     * @param requestId the request identifier.
     * @param callRoomId the call room.
     * @param twincodeOutboundId the member to invite.
     * @param p2pSessionId the optional P2P session with that member.
     */
    void inviteCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull UUID twincodeOutboundId, @Nullable UUID p2pSessionId);

    /**
     * Join the call room after having received an invitation through `onInviteCallRoom`.
     * The `twincodeOut` must be owned by the current user and represents the current user in the call room.
     * Each existing p2p session id must be associated with a peer id this is used in the case the p2p
     * session id is not already known by the server.
     *
     * @param requestId the request identifier.
     * @param callRoomId the call room to join.
     * @param twincodeOut the member twincode.
     * @param p2pSessions the optional P2P sessions that we have with the call room members.
     */
    void joinCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull UUID twincodeOut,
                      @NonNull List<Pair<UUID, String>> p2pSessions);

    /**
     * Leave the call room.
     *
     * @param requestId the request identifier.
     * @param callRoomId the call room to leave.
     * @param memberId the member id to remove.
     */
    void leaveCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull String memberId);

    /**
     * Destroy the call room.  Only the creator of the call room is allowed to destroy the call room.
     *
     * @param requestId the request identifier.
     * @param callRoomId the call room to destroy.
     */
    void destroyGroup(long requestId, @NonNull UUID callRoomId);

    void transferDone();

    /**
     * Set a new signaling listener to handle incoming signaling IQs.
     *
     * The signaling listener should be installed during application setup.  If a signaling listener is not
     * able to handle the received IQ, it should forward it to the next signaling listener.  Only one signaling
     * listener can be installed.
     *
     * @param listener the listener.
     * @return the current listener.
     */
    @Nullable
    PeerSignalingListener setSignalingListener(@NonNull PeerSignalingListener listener);

    /**
     * Send the session-initiate to start a P2P connection with the peer.
     *
     * @param sessionId the P2P session id.
     * @param from the sender identification string.
     * @param to the peer identification string.
     * @param sdp the SDP to send.
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max receive frame size that we accept.
     * @param maxReceivedFrameRate the max receive frame rate that we accept.
     * @param notificationContent information for the push notification.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    void sessionInitiate(@NonNull UUID sessionId, @Nullable String from, @NonNull String to, @NonNull Sdp sdp,
                         @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                         int maxReceivedFrameSize, int maxReceivedFrameRate,
                         @NonNull PushNotificationContent notificationContent,
                         @NonNull Consumer<Long> onComplete);

    /**
     * Send the session-accept to accept an incoming P2P connection with the peer.
     *
     * @param sessionId the P2P session id.
     * @param from the sender identification string.
     * @param to the peer identification string.
     * @param sdpAnswer the SDP to send.
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max receive frame size that we accept.
     * @param maxReceivedFrameRate the max receive frame rate that we accept.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    void sessionAccept(@NonNull UUID sessionId, String from, @NonNull String to, @NonNull Sdp sdpAnswer,
                       @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                       int maxReceivedFrameSize, int maxReceivedFrameRate,
                       @NonNull Consumer<Long> onComplete);

    /**
     * Send the transport info for the P2P session to the peer.
     *
     * @param requestId the request id.
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param sdp the SDP with the list of candidates.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    void transportInfo(long requestId, @NonNull UUID sessionId, @NonNull String to, @NonNull Sdp sdp,
                       @NonNull Consumer<Long> onComplete);

    /**
     * Send the session-update to ask for a renegotiation with the peer.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param sdp the sdp to send.
     * @param type the update type to indicate whether this is an offer or answer.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    void sessionUpdate(@NonNull UUID sessionId, @NonNull String to, @NonNull Sdp sdp, @NonNull SdpType type,
                       @NonNull Consumer<Long> onComplete);

    /**
     * Send a session-ping with the session id and peer identification string.  The server will check the
     * validity of our session and return SUCCESS or EXPIRED.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    void sessionPing(@NonNull UUID sessionId, @NonNull String to,
                     @NonNull Consumer<Long> onComplete);

    /**
     * Send the session-terminate to the peer to close the P2P connection.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param reason the reason for the termination.
     * @param onComplete optional completion handler executed when the server sends us its response.
     */
    void sessionTerminate(@NonNull UUID sessionId, @NonNull String to, @NonNull TerminateReason reason,
                          @Nullable Consumer<Long> onComplete);

    void deviceRinging(@NonNull UUID sessionId, @NonNull String to);
}
