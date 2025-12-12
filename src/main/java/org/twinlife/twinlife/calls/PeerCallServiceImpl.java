/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.calls;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.PushNotificationPriority;
import org.twinlife.twinlife.PeerCallService;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.OfferToReceive;
import org.twinlife.twinlife.PushNotificationContent;
import org.twinlife.twinlife.SdpType;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.PeerSignalingListener;
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ.BinaryPacketIQSerializer;
import org.twinlife.twinlife.util.SerializerFactoryImpl;
import org.twinlife.twinlife.util.Version;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Peer call service implementation.
 */
public class PeerCallServiceImpl extends BaseServiceImpl<PeerCallService.ServiceObserver> implements PeerCallService {
    private static final String LOG_TAG = "PeerCallServiceImpl";
    private static final boolean DEBUG = false;

    private static final int MAJOR_VERSION = 2;
    private static final int MINOR_VERSION = 2;

    private static final UUID CREATE_CALL_ROOM_SCHEMA_ID = UUID.fromString("e53c8953-6345-4e77-bf4b-c1dc227d5d2f");
    private static final UUID ON_CREATE_CALL_ROOM_SCHEMA_ID = UUID.fromString("9e53e24a-acf3-4819-8539-2af37272254f");
    private static final UUID INVITE_CALL_ROOM_SCHEMA_ID = UUID.fromString("8974ff91-a6c6-42d7-b2a2-fc11041892bd");
    private static final UUID ON_INVITE_CALL_ROOM_SCHEMA_ID = UUID.fromString("274dd1fb-a983-4709-91b0-825152742e1e");
    private static final UUID JOIN_CALL_ROOM_SCHEMA_ID = UUID.fromString("f34ce0b8-8b1c-4384-b7a3-19fddcfd2789");
    private static final UUID ON_JOIN_CALL_ROOM_SCHEMA_ID = UUID.fromString("fd30c970-a16c-4346-936d-d541aa239cb8");
    private static final UUID MEMBER_NOTIFICATION_SCHEMA_ID = UUID.fromString("f7460e42-387c-41fe-97c3-18a5f2a97052");
    private static final UUID LEAVE_CALL_ROOM_SCHEMA_ID = UUID.fromString("ffc5b5d4-a5e7-471e-aef3-97fadfdbda94");
    private static final UUID ON_LEAVE_CALL_ROOM_SCHEMA_ID = UUID.fromString("ae2211fe-60ed-4518-ae90-e9dc5393f0d9");
    private static final UUID DESTROY_CALL_ROOM_SCHEMA_ID = UUID.fromString("f4e195c7-3f84-4e05-a268-b4e3a956a787");
    private static final UUID ON_DESTROY_CALL_ROOM_SCHEMA_ID = UUID.fromString("fac9a8de-c608-4d8f-b0e0-6c390584c41a");

    private static final UUID SESSION_INITIATE_SCHEMA_ID = UUID.fromString("0ac5f97d-0fa1-4e18-bd99-c13297086752");
    private static final UUID SESSION_ACCEPT_SCHEMA_ID = UUID.fromString("fd545960-d9ac-4e3e-bddf-76f381f163a5");
    private static final UUID SESSION_UPDATE_SCHEMA_ID = UUID.fromString("44f0c7d0-8d03-453d-8587-714ef92087ae");
    private static final UUID TRANSPORT_INFO_SCHEMA_ID = UUID.fromString("fdf1bba1-0c16-4b12-a59c-0f70cf4da1d9");
    private static final UUID SESSION_TERMINATE_SCHEMA_ID = UUID.fromString("342d4d82-d91f-437b-bcf2-a2051bd94ac1");
    private static final UUID DEVICE_RINGING_SCHEMA_ID = UUID.fromString("acd63138-bec7-402d-86d3-b82707d8b40c");
    private static final UUID SESSION_PING_SCHEMA_ID = UUID.fromString("f2cb4a52-7928-42cb-8439-248388b9a4c7");

    private static final UUID ON_SESSION_INITIATE_SCHEMA_ID = UUID.fromString("34469234-0f9b-48ea-88b1-f353808b6492");
    private static final UUID ON_SESSION_ACCEPT_SCHEMA_ID = UUID.fromString("39b4838a-857c-4d03-9a63-c226fab2cd01");
    private static final UUID ON_TRANSPORT_INFO_SCHEMA_ID = UUID.fromString("edf481e9-d584-4366-8c32-997cb33cf2c1");
    private static final UUID ON_SESSION_UPDATE_SCHEMA_ID = UUID.fromString("1bdb2a25-33a7-4caf-af96-b90af26a478f");
    private static final UUID ON_SESSION_TERMINATE_SCHEMA_ID = UUID.fromString("d9585220-4c8f-4a24-8e71-d7f81a4abe37");
    private static final UUID ON_SESSION_PING_SCHEMA_ID = UUID.fromString("6825a073-b8f0-469e-b283-16fb4d3d0f80");

    private static final BinaryPacketIQSerializer IQ_CREATE_CALL_ROOM_SERIALIZER = CreateCallRoomIQ.createSerializer(CREATE_CALL_ROOM_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_CREATE_CALL_ROOM_SERIALIZER = OnCreateCallRoomIQ.createSerializer(ON_CREATE_CALL_ROOM_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_INVITE_CALL_ROOM_SERIALIZER = InviteCallRoomIQ.createSerializer(INVITE_CALL_ROOM_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_INVITE_CALL_ROOM_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_INVITE_CALL_ROOM_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_JOIN_CALL_ROOM_SERIALIZER = JoinCallRoomIQ.createSerializer(JOIN_CALL_ROOM_SCHEMA_ID, 3);
    private static final BinaryPacketIQSerializer IQ_ON_JOIN_CALL_ROOM_SERIALIZER = OnJoinCallRoomIQ.createSerializer(ON_JOIN_CALL_ROOM_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_MEMBER_NOTIFICATION_SERIALIZER = MemberNotificationIQ.createSerializer(MEMBER_NOTIFICATION_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_LEAVE_CALL_ROOM_SERIALIZER = LeaveCallRoomIQ.createSerializer(LEAVE_CALL_ROOM_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_LEAVE_CALL_ROOM_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_LEAVE_CALL_ROOM_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_DESTROY_CALL_ROOM_SERIALIZER = DestroyCallRoomIQ.createSerializer(DESTROY_CALL_ROOM_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_DESTROY_CALL_ROOM_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_DESTROY_CALL_ROOM_SCHEMA_ID, 1);

    static final BinaryPacketIQSerializer IQ_SESSION_INITIATE_SERIALIZER = SessionInitiateIQ.createSerializer(SESSION_INITIATE_SCHEMA_ID, 1);
    static final BinaryPacketIQSerializer IQ_SESSION_ACCEPT_SERIALIZER = SessionAcceptIQ.createSerializer(SESSION_ACCEPT_SCHEMA_ID, 1);
    static final BinaryPacketIQSerializer IQ_SESSION_UPDATE_SERIALIZER = SessionUpdateIQ.createSerializer(SESSION_UPDATE_SCHEMA_ID, 1);
    static final BinaryPacketIQSerializer IQ_TRANSPORT_INFO_SERIALIZER = TransportInfoIQ.createSerializer(TRANSPORT_INFO_SCHEMA_ID, 1);
    static final BinaryPacketIQSerializer IQ_SESSION_TERMINATE_SERIALIZER = SessionTerminateIQ.createSerializer(SESSION_TERMINATE_SCHEMA_ID, 1);
    static final BinaryPacketIQSerializer IQ_SESSION_PING_SERIALIZER = SessionPingIQ.createSerializer(SESSION_PING_SCHEMA_ID, 1);
    static final BinaryPacketIQSerializer IQ_DEVICE_RINGING_SERIALIZER = DeviceRingingIQ.createSerializer(DEVICE_RINGING_SCHEMA_ID, 1);

    private static final BinaryPacketIQSerializer IQ_ON_SESSION_INITIATE_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_SESSION_INITIATE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_SESSION_ACCEPT_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_SESSION_ACCEPT_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_TRANSPORT_INFO_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_TRANSPORT_INFO_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_SESSION_UPDATE_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_SESSION_UPDATE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_SESSION_TERMINATE_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_SESSION_TERMINATE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_SESSION_PING_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_SESSION_PING_SCHEMA_ID, 1);

    private static class PendingRequest {
    }

    private static final class CallRoomPendingRequest extends PendingRequest {
        @NonNull
        final UUID callRoomId;

        CallRoomPendingRequest(@NonNull UUID callRoomId) {
            this.callRoomId = callRoomId;
        }
    }

    private static final class SessionPendingRequest extends PendingRequest {
        @NonNull
        final Consumer<Long> complete;
        @NonNull
        final UUID sessionId;

        SessionPendingRequest(@NonNull UUID sessionId, @NonNull Consumer<Long> complete) {

            this.sessionId = sessionId;
            this.complete = complete;
        }
    }

    private static final class DefaultSignalingListener implements PeerSignalingListener {

        @Override
        @Nullable
        public ErrorCode onSessionInitiate(@NonNull UUID sessionId, @NonNull String from, @NonNull String to, @NonNull Sdp sdp, @NonNull Offer offer, @NonNull OfferToReceive offerToReceive, int maxReceivedFrameSize, int maxReceivedFrameRate) {

            return ErrorCode.NO_PERMISSION;
        }

        @Override
        @NonNull
        public ErrorCode onSessionAccept(@NonNull UUID sessionId, @NonNull String to, @NonNull Sdp sdp, @NonNull Offer offer, @NonNull OfferToReceive offerToReceive, int maxReceivedFrameSize, int maxReceivedFrameRate) {

            return ErrorCode.NO_PERMISSION;
        }

        @Override
        @NonNull
        public ErrorCode onSessionUpdate(@NonNull UUID sessionId, @NonNull SdpType updateType, @NonNull Sdp sdp) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        @Override
        @NonNull
        public ErrorCode onTransportInfo(@NonNull UUID sessionId, @NonNull Sdp sdp) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        @Override
        public void onSessionTerminate(@NonNull UUID sessionId, @NonNull TerminateReason reason) {

        }

        @Override
        public void onDeviceRinging(@NonNull UUID sessionId) {
            //noop
        }
    }

    private final HashMap<Long, PendingRequest> mPendingRequests = new HashMap<>();
    @NonNull
    private PeerSignalingListener mPeerSignalingListener = new DefaultSignalingListener();

    public PeerCallServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {

        super(twinlifeImpl, connection);

        SerializerFactoryImpl serializerFactory = mTwinlifeImpl.getSerializerFactoryImpl();
        // Requests and associated responses.
        serializerFactory.addSerializer(IQ_CREATE_CALL_ROOM_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_CREATE_CALL_ROOM_SERIALIZER);
        serializerFactory.addSerializer(IQ_JOIN_CALL_ROOM_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_JOIN_CALL_ROOM_SERIALIZER);
        serializerFactory.addSerializer(IQ_LEAVE_CALL_ROOM_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_LEAVE_CALL_ROOM_SERIALIZER);
        serializerFactory.addSerializer(IQ_DESTROY_CALL_ROOM_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_DESTROY_CALL_ROOM_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_INVITE_CALL_ROOM_SERIALIZER);

        serializerFactory.addSerializer(IQ_SESSION_INITIATE_SERIALIZER);
        serializerFactory.addSerializer(IQ_SESSION_ACCEPT_SERIALIZER);
        serializerFactory.addSerializer(IQ_SESSION_UPDATE_SERIALIZER);
        serializerFactory.addSerializer(IQ_TRANSPORT_INFO_SERIALIZER);
        serializerFactory.addSerializer(IQ_SESSION_TERMINATE_SERIALIZER);
        serializerFactory.addSerializer(IQ_SESSION_PING_SERIALIZER);

        serializerFactory.addSerializer(IQ_ON_SESSION_INITIATE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_SESSION_ACCEPT_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_SESSION_UPDATE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_TRANSPORT_INFO_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_SESSION_TERMINATE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_SESSION_PING_SERIALIZER);
        serializerFactory.addSerializer(IQ_DEVICE_RINGING_SERIALIZER);

        // Server notifications.
        serializerFactory.addSerializer(IQ_INVITE_CALL_ROOM_SERIALIZER);
        serializerFactory.addSerializer(IQ_MEMBER_NOTIFICATION_SERIALIZER);

        // Register the binary IQ handlers for the responses and server notifications.
        connection.addPacketListener(IQ_ON_CREATE_CALL_ROOM_SERIALIZER, this::onCreateCallRoom);
        connection.addPacketListener(IQ_ON_JOIN_CALL_ROOM_SERIALIZER, this::onJoinCallRoom);
        connection.addPacketListener(IQ_ON_LEAVE_CALL_ROOM_SERIALIZER, this::onLeaveCallRoom);
        connection.addPacketListener(IQ_ON_DESTROY_CALL_ROOM_SERIALIZER, this::onDestroyCallRoom);
        connection.addPacketListener(IQ_INVITE_CALL_ROOM_SERIALIZER, this::onInviteCallRoom);
        connection.addPacketListener(IQ_MEMBER_NOTIFICATION_SERIALIZER, this::onMemberNotification);

        // Signaling IQ.
        connection.addPacketListener(IQ_SESSION_INITIATE_SERIALIZER, this::onSessionInitiate);
        connection.addPacketListener(IQ_SESSION_ACCEPT_SERIALIZER, this::onSessionAccept);
        connection.addPacketListener(IQ_SESSION_UPDATE_SERIALIZER, this::onSessionUpdate);
        connection.addPacketListener(IQ_TRANSPORT_INFO_SERIALIZER, this::onTransportInfo);
        connection.addPacketListener(IQ_SESSION_TERMINATE_SERIALIZER, this::onSessionTerminate);
        connection.addPacketListener(IQ_DEVICE_RINGING_SERIALIZER, this::onDeviceRinging);

        connection.addPacketListener(IQ_ON_SESSION_INITIATE_SERIALIZER, this::onAckPacket);
        connection.addPacketListener(IQ_ON_SESSION_ACCEPT_SERIALIZER, this::onAckPacket);
        connection.addPacketListener(IQ_ON_TRANSPORT_INFO_SERIALIZER, this::onAckPacket);
        connection.addPacketListener(IQ_ON_SESSION_UPDATE_SERIALIZER, this::onAckPacket);
        connection.addPacketListener(IQ_ON_SESSION_TERMINATE_SERIALIZER, this::onAckPacket);
        connection.addPacketListener(IQ_ON_SESSION_PING_SERIALIZER, this::onAckPacket);

    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof PeerCallServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        PeerCallServiceConfiguration peerCallServiceConfiguration = new PeerCallServiceConfiguration();

        setServiceConfiguration(peerCallServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    @Override
    public void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        super.onSignOut();

        synchronized (mPendingRequests) {
            mPendingRequests.clear();
        }
    }

    //
    // Implement PeerCallService interface
    //

    /**
     * Create a call room with the given twincode identification.  The user must be owner of that twincode.
     * A list of member twincode with their optional P2P session id can be passed and those members are invited
     * to join the room.  Members that are invited will receive a call to their `onInviteCallRoom` callback.
     *
     * @param requestId the request identifier.
     * @param twincodeOut the owner twincode.
     * @param members the list of members to invite.
     */
    @Override
    public void createCallRoom(long requestId, @NonNull UUID twincodeOut, @NonNull Map<String, UUID> members) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createCallRoom requestId=" + requestId + " twincodeOut=" + twincodeOut);
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new PendingRequest());
        }

        final MemberSessionInfo[] list = new MemberSessionInfo[members.size()];
        int pos = 0;
        for (Map.Entry<String, UUID> member : members.entrySet()) {
            list[pos] = new MemberSessionInfo(member.getKey(), member.getValue());
            pos++;
        }

        final int mode = CreateCallRoomIQ.ALLOW_AUDIO | CreateCallRoomIQ.ALLOW_DATA
                | CreateCallRoomIQ.ALLOW_INVITE | CreateCallRoomIQ.ALLOW_VIDEO | CreateCallRoomIQ.AUTO_DESTROY;
        final CreateCallRoomIQ createCallRoomIQ = new CreateCallRoomIQ(IQ_CREATE_CALL_ROOM_SERIALIZER, requestId, twincodeOut, twincodeOut, mode, list, null);
        sendDataPacket(createCallRoomIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Invite a new member in the call room.  Similar to `createCallRoom` to invite another member in the call room.
     *
     * @param requestId the request identifier.
     * @param callRoomId the call room.
     * @param twincodeOutboundId the member to invite.
     * @param p2pSessionId the optional P2P session with that member.
     */
    @Override
    public void inviteCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull UUID twincodeOutboundId, @Nullable UUID p2pSessionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "inviteCallRoom requestId=" + requestId + " callRoomId=" + callRoomId);
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new PendingRequest());
        }

        final InviteCallRoomIQ createCallRoomIQ = new InviteCallRoomIQ(IQ_INVITE_CALL_ROOM_SERIALIZER, requestId, callRoomId, twincodeOutboundId, p2pSessionId, 0, 0);
        sendDataPacket(createCallRoomIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Join the call room after having received an invitation through `onInviteCallRoom`.
     * The `twincodeOut` must be owned by the current user and represents the current user in the call room.
     *
     * @param requestId the request identifier.
     * @param callRoomId the call room to join.
     * @param twincodeOut the member twincode.
     * @param p2pSessions the optional P2P session that we have with the given twincode.
     */
    @Override
    public void joinCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull UUID twincodeOut,
                             @NonNull List<Pair<UUID, String>> p2pSessions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "joinCallRoom: requestId=" + requestId + " callRoomId=" + callRoomId + " p2pSessions=" + p2pSessions);
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new CallRoomPendingRequest(callRoomId));
        }

        JoinCallRoomIQ joinCallRoomIQ = new JoinCallRoomIQ(IQ_JOIN_CALL_ROOM_SERIALIZER, requestId, callRoomId, twincodeOut, p2pSessions);
        sendDataPacket(joinCallRoomIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Leave the call room.
     *
     * @param requestId the request identifier.
     * @param callRoomId the call room to leave.
     * @param memberId the member id to remove.
     */
    @Override
    public void leaveCallRoom(long requestId, @NonNull UUID callRoomId, @NonNull String memberId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "leaveCallRoom requestId=" + requestId + " callRoomId=" + callRoomId);
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new CallRoomPendingRequest(callRoomId));
        }

        LeaveCallRoomIQ leaveCallRoomIQ = new LeaveCallRoomIQ(IQ_LEAVE_CALL_ROOM_SERIALIZER, requestId, callRoomId, memberId);
        sendDataPacket(leaveCallRoomIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Destroy the call room.  Only the creator of the call room is allowed to destroy the call room.
     *
     * @param requestId the request identifier.
     * @param callRoomId the call room to destroy.
     */
    @Override
    public void destroyGroup(long requestId, @NonNull UUID callRoomId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "destroyGroup requestId=" + requestId + " callRoomId=" + callRoomId);
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new CallRoomPendingRequest(callRoomId));
        }

        DestroyCallRoomIQ destroyCallRoomIQ = new DestroyCallRoomIQ(IQ_DESTROY_CALL_ROOM_SERIALIZER, requestId, callRoomId);
        sendDataPacket(destroyCallRoomIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void transferDone() {
        for (final PeerCallService.ServiceObserver observer : getServiceObservers()) {
            mTwinlifeExecutor.execute(observer::onTransferDone);
        }
    }

    /**
     * Set a new signaling listener to handle incoming signaling IQs.
     * <p>
     * The signaling listener should be installed during application setup.  If a signaling listener is not
     * able to handle the received IQ, it should forward it to the next signaling listener.  Only one signaling
     * listener can be installed.
     *
     * @param listener the listener.
     * @return the current listener.
     */
    @Nullable
    public PeerSignalingListener setSignalingListener(@NonNull PeerSignalingListener listener) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setSignalingListener listener=" + listener);
        }

        final PeerSignalingListener previous = mPeerSignalingListener;
        mPeerSignalingListener = listener;
        return previous;
    }

    @NonNull
    static Offer createOffer(int offer, @NonNull Version version) {

        boolean audio = (offer & SessionInitiateIQ.OFFER_AUDIO) != 0;
        boolean video = (offer & SessionInitiateIQ.OFFER_VIDEO) != 0;
        boolean videoBell = (offer & SessionInitiateIQ.OFFER_VIDEO_BELL) != 0;
        boolean data = (offer & SessionInitiateIQ.OFFER_DATA) != 0;
        boolean transfer = (offer & SessionInitiateIQ.OFFER_TRANSFER) != 0;
        Offer result = new Offer(audio, video, videoBell, data, transfer);
        result.group = (offer & SessionInitiateIQ.OFFER_GROUP_CALL) != 0;
        result.version = version;
        return result;
    }

    @NonNull
    static OfferToReceive createOfferToReceive(int offer) {

        boolean audio = (offer & SessionInitiateIQ.OFFER_AUDIO) != 0;
        boolean video = (offer & SessionInitiateIQ.OFFER_VIDEO) != 0;
        boolean data = (offer & SessionInitiateIQ.OFFER_DATA) != 0;
        return new OfferToReceive(audio, video, data);
    }

    private static int getOfferValue(@NonNull Sdp sdp, @NonNull Offer offer) {
        int offerValue = (offer.audio ? SessionInitiateIQ.OFFER_AUDIO : 0);
        if (offer.video) {
            offerValue |= SessionInitiateIQ.OFFER_VIDEO;
        }
        if (offer.data) {
            offerValue |= SessionInitiateIQ.OFFER_DATA;
        }
        if (offer.videoBell) {
            offerValue |= SessionInitiateIQ.OFFER_VIDEO_BELL;
        }
        if (offer.group) {
            offerValue |= SessionInitiateIQ.OFFER_GROUP_CALL;
        }
        if(offer.transfer) {
            offerValue |= SessionInitiateIQ.OFFER_TRANSFER;
        }
        if (sdp.isCompressed()) {
            offerValue |= SessionInitiateIQ.OFFER_COMPRESSED;
        }
        if (sdp.isEncrypted()) {
            offerValue |= (sdp.getKeyIndex() << SessionInitiateIQ.OFFER_ENCRYPT_SHIFT) & SessionInitiateIQ.OFFER_ENCRYPT_MASK;
        }
        return offerValue;
    }

    /**
     * Send the session-initiate to start a P2P connection with the peer.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param sdp the SDP to send.
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max receive frame size that we accept.
     * @param maxReceivedFrameRate the max receive frame rate that we accept.
     * @param notificationContent information for the push notification.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    @Override
    public void sessionInitiate(@NonNull UUID sessionId, @Nullable String from, @NonNull String to, @NonNull Sdp sdp,
                                @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                                int maxReceivedFrameSize, int maxReceivedFrameRate,
                                @NonNull PushNotificationContent notificationContent,
                                @NonNull Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionInitiate sessionId=" + sessionId + " from=" + from + " to=" + to + " sdp=" + sdp
                    + " offer=" + offer + " offerToReceive=" + offerToReceive
                    + " maxFrameSize=" + maxReceivedFrameSize + " maxFrameRate=" + maxReceivedFrameRate);
        }

        final long expirationDeadline = System.currentTimeMillis() + notificationContent.timeToLive;
        final long requestId = newRequestId();

        if (from == null) {
            from = mTwinlifeImpl.getFullJid();
        }
        final int offerValue = getOfferValue(sdp, offer);

        int offerToReceiveValue = getOfferToReceiveValue(offerToReceive);

        final int priority = notificationContent.priority == PushNotificationPriority.HIGH ? 10 : 0;
        final SessionInitiateIQ sessionInitiateIQ = new SessionInitiateIQ(IQ_SESSION_INITIATE_SERIALIZER, requestId, from, to, sessionId,
                offerValue, offerToReceiveValue, priority, expirationDeadline,
                MAJOR_VERSION, MINOR_VERSION,
                maxReceivedFrameSize, maxReceivedFrameRate, (int) notificationContent.estimatedSize,
                (int) notificationContent.operationCount, sdp.getData(), sdp.getLength());

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new SessionPendingRequest(sessionId, onComplete));
        }

        sendDataPacket(sessionInitiateIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Send the session-accept to accept an incoming P2P connection with the peer.
     *
     * @param sessionId the P2P session id.
     * @param from the sender identification string (memberId when in a call room, null otherwise)
     * @param to the peer identification string.
     * @param sdpAnswer the SDP to send.
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max receive frame size that we accept.
     * @param maxReceivedFrameRate the max receive frame rate that we accept.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    @Override
    public void sessionAccept(@NonNull UUID sessionId, @Nullable String from, @NonNull String to, @NonNull Sdp sdpAnswer,
                              @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                              int maxReceivedFrameSize, int maxReceivedFrameRate,
                              @NonNull Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionAccept sessionId=" + sessionId + " from="+from+" to=" + to + " sdpAnswer=" + sdpAnswer
                + " offer=" + offer + " offerToReceive=" + offerToReceive + " maxFrameSize=" + maxReceivedFrameSize
                + " maxFrameRate=" + maxReceivedFrameRate);
        }

        final long expirationDeadline = System.currentTimeMillis() + 30 * 1000L;
        final long requestId = newRequestId();
        if(from == null) {
            from = mTwinlifeImpl.getFullJid();
        }

        int offerValue = getOfferValue(sdpAnswer, offer);

        int offerToReceiveValue = getOfferToReceiveValue(offerToReceive);

        final SessionAcceptIQ sessionAcceptIQ = new SessionAcceptIQ(IQ_SESSION_ACCEPT_SERIALIZER, requestId, from, to, sessionId,
                offerValue, offerToReceiveValue, 0, expirationDeadline,
                MAJOR_VERSION, MINOR_VERSION,
                maxReceivedFrameSize, maxReceivedFrameRate, 0, 0, sdpAnswer.getData(), sdpAnswer.getLength());

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new SessionPendingRequest(sessionId, onComplete));
        }

        sendDataPacket(sessionAcceptIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Send the transport info for the P2P session to the peer.
     *
     * @param requestId the request id.
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param sdp the SDP with the list of candidates.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    @Override
    public void transportInfo(long requestId, @NonNull UUID sessionId, @NonNull String to, @NonNull Sdp sdp,
                              @NonNull Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "transportInfo sessionId=" + sessionId + " to=" + to + " sdp=" + sdp);
        }

        final long expirationDeadline = System.currentTimeMillis() + 30 * 1000L;
        int mode = sdp.isCompressed() ? SessionInitiateIQ.OFFER_COMPRESSED : 0;
        if (sdp.isEncrypted()) {
            mode |= (sdp.getKeyIndex() << SessionInitiateIQ.OFFER_ENCRYPT_SHIFT) & SessionInitiateIQ.OFFER_ENCRYPT_MASK;
        }

        final TransportInfoIQ transportInfoIQ = new TransportInfoIQ(IQ_TRANSPORT_INFO_SERIALIZER, requestId, to, sessionId,
                expirationDeadline, mode, sdp.getData(), sdp.getLength(), null);

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new SessionPendingRequest(sessionId, onComplete));
        }

        sendDataPacket(transportInfoIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Send the session-update to ask for a renegotiation with the peer.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param sdp the sdp to send.
     * @param type the update type to indicate whether this is an offer or answer.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    @Override
    public void sessionUpdate(@NonNull UUID sessionId, @NonNull String to, @NonNull Sdp sdp, @NonNull SdpType type,
                              @NonNull Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionUpdate sessionId=" + sessionId + " to=" + to + " sdp=" + sdp + " type=" + type);
        }

        final long expirationDeadline = System.currentTimeMillis() + 30 * 1000L;
        final long requestId = newRequestId();
        int updateType = type == SdpType.ANSWER ? SessionInitiateIQ.OFFER_ANSWER : 0;
        if (sdp.isCompressed()) {
            updateType |= SessionInitiateIQ.OFFER_COMPRESSED;
        }
        if (sdp.isEncrypted()) {
            updateType |= (sdp.getKeyIndex() << SessionInitiateIQ.OFFER_ENCRYPT_SHIFT) & SessionInitiateIQ.OFFER_ENCRYPT_MASK;
        }

        final SessionUpdateIQ updateIQ = new SessionUpdateIQ(IQ_SESSION_UPDATE_SERIALIZER, requestId, to, sessionId,
                expirationDeadline, updateType, sdp.getData(), sdp.getLength());

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new SessionPendingRequest(sessionId, onComplete));
        }

        sendDataPacket(updateIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Send a session-ping with the session id and peer identification string.  The server will check the
     * validity of our session and return SUCCESS or EXPIRED.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param onComplete the completion handler executed when the server sends us its response.
     */
    @Override
    public void sessionPing(@NonNull UUID sessionId, @NonNull String to,
                            @NonNull Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionPing sessionId=" + sessionId + " to=" + to);
        }

        final long requestId = newRequestId();
        final String from = mTwinlifeImpl.getFullJid();

        final SessionPingIQ sessionPingIQ = new SessionPingIQ(IQ_SESSION_PING_SERIALIZER, requestId, from, to, sessionId);

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new SessionPendingRequest(sessionId, onComplete));
        }

        sendDataPacket(sessionPingIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Send the session-terminate to the peer to close the P2P connection.
     *
     * @param sessionId the P2P session id.
     * @param to the peer identification string.
     * @param reason the reason for the termination.
     * @param onComplete optional completion handler executed when the server sends us its response.
     */
    @Override
    public void sessionTerminate(@NonNull UUID sessionId, @NonNull String to, @NonNull TerminateReason reason,
                                 @Nullable Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionTerminate sessionId=" + sessionId + " to=" + to + " reason=" + reason);
        }

        final long requestId = newRequestId();
        final SessionTerminateIQ terminateIQ = new SessionTerminateIQ(IQ_SESSION_TERMINATE_SERIALIZER, requestId, to, sessionId, reason);

        if (onComplete != null) {
            synchronized (mPendingRequests) {
                mPendingRequests.put(requestId, new SessionPendingRequest(sessionId, onComplete));
            }
        }

        sendDataPacket(terminateIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void deviceRinging(@NonNull UUID sessionId, @NonNull String to){
        if (DEBUG) {
            Log.d(LOG_TAG, "deviceRinging sessionId=" + sessionId + " to=" + to);
        }

        final long requestId = newRequestId();
        DeviceRingingIQ ringingIQ = new DeviceRingingIQ(IQ_DEVICE_RINGING_SERIALIZER, requestId, to, sessionId);

        sendDataPacket(ringingIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    //
    // Private Methods
    //

    /**
     * Response received after CreateCallRoom operation.
     *
     * @param iq the OnCreateCallRoomIQ response.
     */
    private void onCreateCallRoom(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateCallRoom: iq=" + iq);
        }

        if (!(iq instanceof OnCreateCallRoomIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final OnCreateCallRoomIQ onCreateCallRoomIQ = (OnCreateCallRoomIQ) iq;
        for (final PeerCallService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onCreateCallRoom(requestId, onCreateCallRoomIQ.callRoomId, onCreateCallRoomIQ.memberId, onCreateCallRoomIQ.maxMemberCount));
        }
    }

    /**
     * Notification IQ received when we are invited to join a call room.
     *
     * @param iq the InviteCallRoom notification.
     */
    private void onInviteCallRoom(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInviteCallRoom: iq=" + iq);
        }

        if (!(iq instanceof InviteCallRoomIQ)) {
            return;
        }

        final InviteCallRoomIQ inviteCallRoomIQ = (InviteCallRoomIQ) iq;

        for (final PeerCallService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onInviteCallRoom(inviteCallRoomIQ.callRoomId, inviteCallRoomIQ.twincodeId, inviteCallRoomIQ.p2pSessionId, inviteCallRoomIQ.maxMemberCount));
        }
    }

    /**
     * Response received after we have asked to join the call room.
     *
     * @param iq the InviteCallRoom notification.
     */
    private void onJoinCallRoom(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onJoinCallRoom: iq=" + iq);
        }

        if (!(iq instanceof OnJoinCallRoomIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final CallRoomPendingRequest request;
        synchronized (mPendingRequests) {
            request = (CallRoomPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final OnJoinCallRoomIQ onJoinCallRoomIQ = (OnJoinCallRoomIQ) iq;
        final List<MemberInfo> members = new ArrayList<>();
        if (onJoinCallRoomIQ.members != null) {
            for (final MemberSessionInfo member : onJoinCallRoomIQ.members) {
                if (member.p2pSessionId == null) {
                    members.add(new MemberInfo(member.memberId));
                } else {
                    members.add(new MemberInfo(member.memberId, member.p2pSessionId));
                }
            }
        }

        for (final PeerCallService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onJoinCallRoom(requestId, request.callRoomId, onJoinCallRoomIQ.memberId, members));
        }
    }

    /**
     * Response received after LeaveCallRoom operation.
     *
     * @param iq the OnLeaveCallRoomIQ response.
     */
    private void onLeaveCallRoom(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onLeaveCallRoom: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final CallRoomPendingRequest request;
        synchronized (mPendingRequests) {
            request = (CallRoomPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        for (final PeerCallService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onLeaveCallRoom(requestId, request.callRoomId));
        }
    }

    /**
     * Response received after DestroyCallRoom operation.
     *
     * @param iq the OnDestroyCallRoomIQ response.
     */
    private void onDestroyCallRoom(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDestroyCallRoom: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final CallRoomPendingRequest request;
        synchronized (mPendingRequests) {
            request = (CallRoomPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        for (final PeerCallService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onDestroyCallRoom(requestId, request.callRoomId));
        }
    }

    /**
     * Notification IQ received when we are invited to join a call room.
     *
     * @param iq the InviteCallRoom notification.
     */
    private void onMemberNotification(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onMemberNotification: iq=" + iq);
        }

        if (!(iq instanceof MemberNotificationIQ)) {
            return;
        }

        final MemberNotificationIQ memberNotificationIQ = (MemberNotificationIQ) iq;

        for (final PeerCallService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onMemberJoinCallRoom(memberNotificationIQ.callRoomId, memberNotificationIQ.memberId, memberNotificationIQ.p2pSessionId, memberNotificationIQ.status));
        }
    }

    /**
     * Handle the ack packet sent back from the server after a session-initiate, session-accept, session-update,.
     *
     * @param iq the ack packet.
     */
    private void onAckPacket(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAckPacket: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (!(request instanceof SessionPendingRequest)) {
            return;
        }
        if (!(iq instanceof BinaryErrorPacketIQ)) {
            return;
        }

        final BinaryErrorPacketIQ errorPacketIQ = (BinaryErrorPacketIQ) iq;
        final SessionPendingRequest sessionPendingRequest = (SessionPendingRequest) request;
        sessionPendingRequest.complete.onGet(errorPacketIQ.getErrorCode(), requestId);
    }

    /**
     * Message received when a peer wants to setup a new P2P session.
     *
     * @param iq SessionInitiateIQ request.
     */
    private void onSessionInitiate(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSessionInitiate: iq=" + iq);
        }

        // Do not acknowledge if we are shutting down.
        if (!(iq instanceof SessionInitiateIQ) || isShutdown()) {
            return;
        }

        final SessionInitiateIQ sessionInitiateIQ = (SessionInitiateIQ) iq;
        final Version version = new Version(sessionInitiateIQ.majorVersion, sessionInitiateIQ.minorVersion);
        final Offer offer = createOffer(sessionInitiateIQ.offer, version);
        final Sdp sdp = sessionInitiateIQ.getSdp();
        final OfferToReceive offerToReceive = createOfferToReceive(sessionInitiateIQ.offerToReceive);
        final ErrorCode result = mPeerSignalingListener.onSessionInitiate(sessionInitiateIQ.sessionId, sessionInitiateIQ.from,
                sessionInitiateIQ.to, sdp, offer, offerToReceive, sessionInitiateIQ.frameSize,
                sessionInitiateIQ.frameRate);
        if (result != null) {
            //null => local session, ignore the ack from OpenFire
            sendResponse(new BinaryErrorPacketIQ(IQ_ON_SESSION_INITIATE_SERIALIZER, iq, result));
        }
    }

    /**
     * Message received when a peer wants to setup a new P2P session.
     *
     * @param iq SessionInitiateIQ request.
     */
    private void onSessionAccept(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSessionAccept: iq=" + iq);
        }

        // Do not acknowledge if we are shutting down.
        if (!(iq instanceof SessionAcceptIQ) || isShutdown()) {
            return;
        }

        final SessionAcceptIQ sessionAcceptIQ = (SessionAcceptIQ) iq;
        final Version version = new Version(sessionAcceptIQ.majorVersion, sessionAcceptIQ.minorVersion);
        final Offer offer = createOffer(sessionAcceptIQ.offer, version);
        final Sdp sdp = sessionAcceptIQ.getSdp();
        final OfferToReceive offerToReceive = createOfferToReceive(sessionAcceptIQ.offerToReceive);
        final ErrorCode result = mPeerSignalingListener.onSessionAccept(sessionAcceptIQ.sessionId, sessionAcceptIQ.to, sdp,
                offer, offerToReceive, sessionAcceptIQ.frameSize,
                sessionAcceptIQ.frameRate);

        sendResponse(new BinaryErrorPacketIQ(IQ_ON_SESSION_ACCEPT_SERIALIZER, iq, result));
    }

    /**
     * Message received when a peer wants to re-negotiate the SDP in an existing P2P session.
     *
     * @param iq SessionUpdateIQ request.
     */
    private void onSessionUpdate(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSessionUpdate: iq=" + iq);
        }

        // Do not acknowledge if we are shutting down.
        if (!(iq instanceof SessionUpdateIQ) || isShutdown()) {
            return;
        }

        final SessionUpdateIQ sessionUpdateIQ = (SessionUpdateIQ) iq;
        final SdpType type = (sessionUpdateIQ.updateType & SessionInitiateIQ.OFFER_ANSWER) != 0 ? SdpType.ANSWER : SdpType.OFFER;
        final Sdp sdp = sessionUpdateIQ.getSdp();
        final ErrorCode result = mPeerSignalingListener.onSessionUpdate(sessionUpdateIQ.sessionId, type, sdp);

        sendResponse(new BinaryErrorPacketIQ(IQ_ON_SESSION_UPDATE_SERIALIZER, iq, result));
    }

    /**
     * Message received to give the candidates for the P2P session.
     *
     * @param iq TransportInfoIQ request.
     */
    private void onTransportInfo(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTransportInfo: iq=" + iq);
        }

        // Do not acknowledge if we are shutting down.
        if (!(iq instanceof TransportInfoIQ) || isShutdown()) {
            return;
        }

        TransportInfoIQ transportInfoIQ = (TransportInfoIQ) iq;
        ErrorCode result;
        do {
            final Sdp sdp = transportInfoIQ.getSdp();
            result = mPeerSignalingListener.onTransportInfo(transportInfoIQ.sessionId, sdp);
            transportInfoIQ = transportInfoIQ.nextTransportIQ;
        } while (result == ErrorCode.SUCCESS && transportInfoIQ != null);

        sendResponse(new BinaryErrorPacketIQ(IQ_ON_TRANSPORT_INFO_SERIALIZER, iq, result));
    }

    /**
     * Message received when a peer terminates the P2P session.
     *
     * @param iq SessionTerminateIQ request.
     */
    private void onSessionTerminate(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSessionTerminate: iq=" + iq);
        }

        // Take into account even if we are shutting down.
        if (!(iq instanceof SessionTerminateIQ)) {
            return;
        }

        final SessionTerminateIQ sessionTerminateIQ = (SessionTerminateIQ) iq;
        mPeerSignalingListener.onSessionTerminate(sessionTerminateIQ.sessionId, sessionTerminateIQ.reason);

        sendResponse(new BinaryErrorPacketIQ(IQ_ON_SESSION_TERMINATE_SERIALIZER, iq, ErrorCode.SUCCESS));
    }

    private void onDeviceRinging(@NonNull BinaryPacketIQ iq){
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeviceRinging: iq=" + iq);
        }

        if (!(iq instanceof DeviceRingingIQ)) {
            return;
        }

        mPeerSignalingListener.onDeviceRinging(((DeviceRingingIQ) iq).sessionId);
    }

    @Override
    protected void onErrorPacket(@NonNull BinaryErrorPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
            if (request == null) {
                return;
            }
        }

        // The object no longer exists on the server, remove it from our local database.
        if (request instanceof CallRoomPendingRequest) {
            final CallRoomPendingRequest callRoomPendingRequest = (CallRoomPendingRequest) request;

            super.onError(requestId, iq.getErrorCode(), callRoomPendingRequest.callRoomId.toString());

        } else if (request instanceof SessionPendingRequest) {
            final SessionPendingRequest sessionPendingRequest = (SessionPendingRequest) request;
            sessionPendingRequest.complete.onGet(iq.getErrorCode(), requestId);

        } else {

            super.onError(requestId, iq.getErrorCode(), null);
        }
    }

    private int getOfferToReceiveValue(@NonNull OfferToReceive offerToReceive) {
        int offerToReceiveValue = (offerToReceive.video ? SessionInitiateIQ.OFFER_VIDEO : 0);
        offerToReceiveValue += (offerToReceive.audio ? SessionInitiateIQ.OFFER_AUDIO : 0);
        offerToReceiveValue += (offerToReceive.data ? SessionInitiateIQ.OFFER_DATA : 0);
        return offerToReceiveValue;
    }
}
