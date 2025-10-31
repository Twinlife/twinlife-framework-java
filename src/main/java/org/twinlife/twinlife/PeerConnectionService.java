/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.webrtc.AudioTrack;
import org.webrtc.EglBase;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpSender;
import org.webrtc.VideoTrack;
import org.webrtc.RtpTransceiver.RtpTransceiverDirection;

import java.nio.ByteBuffer;
import java.util.UUID;

@SuppressWarnings("unused")
public interface PeerConnectionService extends BaseService<PeerConnectionService.ServiceObserver> {

    int MAJOR_VERSION = 2;
    int MINOR_VERSION = 2;

    String VERSION = "2.3.0";

    byte[] LEADING_PADDING = new byte[1];

    class PeerConnectionServiceConfiguration extends BaseServiceConfiguration {

        public boolean acceptIncomingCalls;

        public PeerConnectionServiceConfiguration() {

            super(BaseServiceId.PEER_CONNECTION_SERVICE_ID, VERSION, false);

            acceptIncomingCalls = false;
        }
    }

    enum ConnectionState {
        INIT,
        CONNECTING,
        RINGING,
        CHECKING,
        CONNECTED
    }

    /**
     * Status to indicate whether the SDP are encrypted when they are sent/received with the signalling server.
     */
    enum SdpEncryptionStatus {
        NONE,
        ENCRYPTED,
        ENCRYPTED_NEED_RENEW
    }

    /**
     * Statistics which are collected on the P2P session.
     */
    enum StatType {
        IQ_SET_PUSH_OBJECT,
        IQ_SET_PUSH_TRANSIENT,
        IQ_SET_PUSH_FILE,
        IQ_SET_PUSH_FILE_CHUNK,
        IQ_SET_UPDATE_OBJECT,
        IQ_SET_RESET_CONVERSATION,
        IQ_SET_INVITE_GROUP,
        IQ_SET_JOIN_GROUP,
        IQ_SET_LEAVE_GROUP,
        IQ_SET_UPDATE_GROUP_MEMBER,
        IQ_SET_WITHDRAW_INVITE_GROUP,
        IQ_SET_PUSH_GEOLOCATION,
        IQ_SET_PUSH_TWINCODE,
        IQ_SET_SYNCHRONIZE,
        IQ_SET_SIGNATURE_INFO,
        IQ_ERROR,

        IQ_RESULT_PUSH_OBJECT,
        IQ_RESULT_PUSH_TRANSIENT,
        IQ_RESULT_PUSH_FILE,
        IQ_RESULT_PUSH_FILE_CHUNK,
        IQ_RESULT_UPDATE_OBJECT,
        IQ_RESULT_RESET_CONVERSATION,
        IQ_RESULT_INVITE_GROUP,
        IQ_RESULT_JOIN_GROUP,
        IQ_RESULT_LEAVE_GROUP,
        IQ_RESULT_UPDATE_GROUP_MEMBER,
        IQ_RESULT_WITHDRAW_INVITE_GROUP,
        IQ_RESULT_PUSH_GEOLOCATION,
        IQ_RESULT_PUSH_TWINCODE,
        IQ_RESULT_SYNCHRONIZE,
        IQ_RESULT_SIGNATURE_INFO,

        IQ_RECEIVE_COUNT,
        IQ_RECEIVE_SET_COUNT,
        IQ_RECEIVE_RESULT_COUNT,
        IQ_RECEIVE_ERROR_COUNT,

        SDP_SEND_CLEAR,
        SDP_SEND_ENCRYPTED,
        SDP_RECEIVE_CLEAR,
        SDP_RECEIVE_ENCRYPTED,

        // Stats about errors
        SERIALIZE_ERROR,
        SEND_ERROR,
        AUDIO_TRACK_ERROR,
        VIDEO_TRACK_ERROR,
        FIRST_SEND_ERROR,
        FIRST_SEND_ERROR_TIME
    }

    class DataChannelConfiguration {
        public final String version;
        public final boolean leadingPadding;

        public DataChannelConfiguration(@NonNull String version, boolean leadingPadding) {
            this.version = version;
            this.leadingPadding = leadingPadding;
        }
    }

    interface DataChannelObserver {

        @NonNull
        DataChannelConfiguration getConfiguration(@NonNull UUID peerConnectionId, @NonNull SdpEncryptionStatus encryptionStatus);

        void onDataChannelOpen(@NonNull UUID peerConnectionId, @NonNull String peerVersion, boolean leadingPadding);

        void onDataChannelClosed(@NonNull UUID peerConnectionId);

        void onDataChannelMessage(@NonNull UUID peerConnectionId, @NonNull ByteBuffer buffer, boolean leadingPadding);
    }

    interface PeerConnectionObserver {

        void onAcceptPeerConnection(@NonNull UUID peerConnectionId, @NonNull Offer offer);

        void onChangeConnectionState(@NonNull UUID peerConnectionId, @NonNull ConnectionState state);

        void onTerminatePeerConnection(@NonNull UUID peerConnectionId, @NonNull TerminateReason terminateReason);

        void onAddLocalAudioTrack(@NonNull UUID peerConnectionId, @NonNull RtpSender sender, @NonNull AudioTrack audioTrack);

        void onAddRemoteMediaStreamTrack(@NonNull UUID peerConnectionId, @NonNull MediaStreamTrack mediaStream);

        void onRemoveRemoteTrack(@NonNull UUID peerConnectionId, @NonNull String trackId);

        void onPeerHoldCall(@NonNull UUID peerConnectionId);
        void onPeerResumeCall(@NonNull UUID peerConnectionId);
    }

    interface ServiceObserver extends BaseService.ServiceObserver {

        void onIncomingPeerConnection(@NonNull UUID peerConnectionId, @NonNull String peerId, @NonNull Offer offer);

        @SuppressWarnings("EmptyMethod")
        void onCameraError(@Nullable String description);

        void onCreateLocalVideoTrack(@NonNull VideoTrack videoTrack);

        void onRemoveLocalVideoTrack();

        @SuppressWarnings("EmptyMethod")
        void onDeviceRinging(UUID peerConnectionId);
    }

    @SuppressWarnings("EmptyMethod")
    class DefaultServiceObserver extends BaseService.DefaultServiceObserver implements ServiceObserver {

        @Override
        public void onIncomingPeerConnection(@NonNull UUID peerConnectionId, @NonNull String peerId,
                                             @NonNull Offer offer) {
        }

        @Override
        public void onCameraError(@Nullable String description) {
        }

        @Override
        public void onCreateLocalVideoTrack(@NonNull VideoTrack videoTrack) {
        }

        @Override
        public void onRemoveLocalVideoTrack() {
        }

        @Override
        public void onDeviceRinging(UUID peerConnectionId) {
        }
    }

    /**
     * Check if there are some P2P connections which are either in progress or active.
     *
     * @return true if the service has some P2P connections.
     */
    boolean hasPeerConnections();

    @NonNull
    ErrorCode listenPeerConnection(@NonNull UUID peerConnectionId, @NonNull PeerConnectionObserver observer);

    void createIncomingPeerConnection(@NonNull UUID peerConnectionId, @NonNull Offer offer,
                                      @NonNull OfferToReceive offerToReceive,
                                      @Nullable DataChannelObserver consumer,
                                      @NonNull PeerConnectionObserver observer, @NonNull Consumer<UUID> complete);

    void createIncomingPeerConnection(@NonNull UUID peerConnectionId, @NonNull RepositoryObject subject,
                                      @Nullable TwincodeOutbound peerTwincodeOutbound, @NonNull Offer offer,
                                      @NonNull OfferToReceive offerToReceive,
                                      @Nullable DataChannelObserver consumer,
                                      @NonNull PeerConnectionObserver observer, @NonNull Consumer<UUID> complete);

    void createOutgoingPeerConnection(@NonNull String peerId, @NonNull Offer offer,
                                      @NonNull OfferToReceive offerToReceive, @NonNull PushNotificationContent notificationContent,
                                      @Nullable DataChannelObserver consumer,
                                      @NonNull PeerConnectionObserver observer, @NonNull Consumer<UUID> complete);

    void createOutgoingPeerConnection(@NonNull RepositoryObject subject, @Nullable TwincodeOutbound peerTwincodeOutbound,
                                      @NonNull Offer offer, @NonNull OfferToReceive offerToReceive, @NonNull PushNotificationContent notificationContent,
                                      @Nullable DataChannelObserver consumer,
                                      @NonNull PeerConnectionObserver observer, @NonNull Consumer<UUID> complete);

    void initSources(@NonNull UUID peerConnectionId, boolean audioOn, boolean videoOn);

    /**
     * Create the EGL base context if it does not exist.  It can be called from any thread before we call the
     * PeerConnectionService.initSources().
     */
    @Nullable
    EglBase.Context getEGLContext();

    void terminatePeerConnection(@NonNull UUID peerConnectionId, TerminateReason terminateReason);

    @Nullable
    Offer getPeerOffer(@NonNull UUID peerConnectionId);

    @Nullable
    OfferToReceive getPeerOfferToReceive(@NonNull UUID peerConnectionId);

    @Nullable
    String getPeerId(@NonNull UUID peerConnectionId);

    boolean isTerminated(@NonNull UUID peerConnectionId);

    /**
     * Whether the SDP are encrypted when they are received or sent from the signaling server.
     *
     * @param peerConnectionId the P2P connection id.
     * @return true when SDP are encrypted.
     */
    @Nullable
    SdpEncryptionStatus getSdpEncryptionStatus(@NonNull UUID peerConnectionId);

    void setAudioDirection(@NonNull UUID peerConnectionId, @NonNull RtpTransceiverDirection direction);

    void setVideoDirection(@NonNull UUID peerConnectionId, @NonNull RtpTransceiverDirection direction);

    void switchCamera(boolean front, @NonNull Consumer<Boolean> complete);

    boolean isZoomSupported(@NonNull UUID peerConnectionId);

    void setZoom(int progress);

    void sendMessage(@NonNull UUID peerConnectionId, @NonNull StatType statType, @NonNull byte[] bytes);

    void sendPacket(@NonNull UUID peerConnectionId, @NonNull StatType statType, @NonNull BinaryPacketIQ iq);

    void incrementStat(@NonNull UUID peerConnectionId, @NonNull StatType statType);

    void sendCallQuality(@NonNull UUID peerConnectionId, int quality);

    void sendDeviceRinging(@NonNull UUID peerConnectionId);

    /**
     * Trigger a session ping on every active P2P connection.
     */
    void sessionPing();
}
