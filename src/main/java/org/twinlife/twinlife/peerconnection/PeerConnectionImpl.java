/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.peerconnection;

import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.ManagementService;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.PeerConnectionService.SdpEncryptionStatus;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.OfferToReceive;
import org.twinlife.twinlife.PeerConnectionService.StatType;
import org.twinlife.twinlife.SdpType;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SessionKeyPair;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.PushNotificationContent;
import org.twinlife.twinlife.PeerConnectionService.PeerConnectionObserver;
import org.twinlife.twinlife.PeerConnectionService.ConnectionState;
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.TransportCandidateList;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.calls.PeerCallServiceImpl;
import org.twinlife.twinlife.TransportCandidate;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Utils;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CandidatePairChangeEvent;
import org.webrtc.DataChannel;
import org.webrtc.DataChannel.Buffer;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceConnectionState;
import org.webrtc.PeerConnection.IceGatheringState;
import org.webrtc.PeerConnection.SignalingState;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RTCStats;
import org.webrtc.RTCStatsReport;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.RtpTransceiver.RtpTransceiverDirection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.VideoTrack;
import org.webrtc.MediaStreamTrack.MediaType;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class PeerConnectionImpl implements PeerConnection.Observer, DataChannel.Observer {

    private static final String LOG_TAG = "PeerConnectionImpl";
    private static final boolean DEBUG = false;

    private static final int CONNECT_REPORT_VERSION = 2;
    private static final int STATS_REPORT_VERSION = 2;
    private static final int IQ_REPORT_VERSION = 5;
    private static final int AUDIO_REPORT_VERSION = 2;
    private static final int VIDEO_REPORT_VERSION = 2;

    // Defines the content of the 'set_report' and order in which these counters are reported.
    private static final StatType[] SET_STAT_LIST = {
            StatType.IQ_SET_PUSH_OBJECT,
            StatType.IQ_SET_PUSH_TRANSIENT,
            StatType.IQ_SET_PUSH_FILE,
            StatType.IQ_SET_PUSH_FILE_CHUNK,
            StatType.IQ_SET_UPDATE_OBJECT,
            StatType.IQ_SET_RESET_CONVERSATION,
            StatType.IQ_SET_INVITE_GROUP,
            StatType.IQ_SET_JOIN_GROUP,
            StatType.IQ_SET_LEAVE_GROUP,
            StatType.IQ_SET_UPDATE_GROUP_MEMBER,
            StatType.IQ_SET_WITHDRAW_INVITE_GROUP,
            StatType.IQ_SET_PUSH_GEOLOCATION,
            StatType.IQ_SET_PUSH_TWINCODE,
            StatType.IQ_SET_SYNCHRONIZE,
            StatType.IQ_SET_SIGNATURE_INFO,
            StatType.IQ_ERROR
    };

    // Defines the content of the 'result_report' and order in which these counters are reported.
    private static final StatType[] RESULT_STAT_LIST = {
            StatType.IQ_RESULT_PUSH_OBJECT,
            StatType.IQ_RESULT_PUSH_TRANSIENT,
            StatType.IQ_RESULT_PUSH_FILE,
            StatType.IQ_RESULT_PUSH_FILE_CHUNK,
            StatType.IQ_RESULT_UPDATE_OBJECT,
            StatType.IQ_RESULT_RESET_CONVERSATION,
            StatType.IQ_RESULT_INVITE_GROUP,
            StatType.IQ_RESULT_JOIN_GROUP,
            StatType.IQ_RESULT_LEAVE_GROUP,
            StatType.IQ_RESULT_UPDATE_GROUP_MEMBER,
            StatType.IQ_RESULT_WITHDRAW_INVITE_GROUP,
            StatType.IQ_RESULT_PUSH_GEOLOCATION,
            StatType.IQ_RESULT_PUSH_TWINCODE,
            StatType.IQ_RESULT_SYNCHRONIZE,
            StatType.IQ_RESULT_SIGNATURE_INFO
    };

    // Defines the content of the 'recv_report' and order in which these counters are reported.
    private static final StatType[] RECEIVE_STAT_LIST = {
            StatType.IQ_RECEIVE_COUNT,
            StatType.IQ_RECEIVE_SET_COUNT,
            StatType.IQ_RECEIVE_RESULT_COUNT,
            StatType.IQ_RECEIVE_ERROR_COUNT,
    };

    // Defines the content of the 'sdp_report' and order in which these counters are reported.
    private static final StatType[] SDP_STAT_LIST = {
            StatType.SDP_RECEIVE_CLEAR,
            StatType.SDP_SEND_CLEAR,
            StatType.SDP_RECEIVE_ENCRYPTED,
            StatType.SDP_SEND_ENCRYPTED,
    };

    // Defines the content of the 'error_report' and order in which these counters are reported.
    private static final StatType[] ERROR_STAT_LIST = {
            StatType.SERIALIZE_ERROR,
            StatType.SEND_ERROR,
            StatType.AUDIO_TRACK_ERROR,
            StatType.VIDEO_TRACK_ERROR,
            StatType.FIRST_SEND_ERROR,
            StatType.FIRST_SEND_ERROR_TIME
    };

    /*
     * <pre>
     * Date: 2024/12/16
     *  changes: added error report and padding flag
     *  iqReport: version 5
     *  iq_report = version:set:set_report:result:result_report:recv:recv_report:sdp:sdp_report:padding_flag:error_report
     *  sdp_report = sdp-receive-clear:sdp-send-clear:sdp-receive-encrypted:sdp-send-encrypted
     *  padding_flag = ':P' if leading padding
     *  error_report = err:serialize-error-count:send-error-count:audio-track-error:video-track-error
     *
     * Date: 2024/10/18
     *  changes: added IQ stats synchronize-iq, signature-info-iq, sdp_report
     *  iqReport: version 4
     *  iq_report = version:set:set_report:result:result_report:recv:recv_report:sdp:sdp_report
     *  sdp_report = sdp-receive-clear:sdp-send-clear:sdp-receive-encrypted:sdp-send-encrypted
     *
     * Date: 2019/05/23
     * changes: added IQ stats for push-twincode
     *
     * iq_report = version:set:set_report:result:result_report:recv:recv_report
     * set_report = push-count:transient-count:file-count:chunk-count:update-count:reset-count:invite-count:join-count:leave-count:update-group-count:push-geolocation-count:push-twincode:error-count
     * result_report = push-count:transient-count:file-count:chunk-count:update-count:reset-count:invite-count:join-count:leave-count:update-group-count:push-geolocation-count:push-twincode
     * recv_report = total-count:set-count:result-count:error-count
     *
     * Date: 2019/04/15
     * changes: added audioReport and videoReport
     *
     * audio_report = version:audio-send:jitterBufferDelay:totalAudioEnergy:totalSamplesDuration
     *      :audio-recv:totalAudioEnergy:totalSamplesDuration
     * video_report = version:video-send:width:height:framesSent
     *      :video-recv:width:height:framesReceived:framesDecoded:framesDropped
     *
     * Date: 2019/02/21
     *
     * version: 2
     * changes: added IQ stats for push-geolocation
     *
     * iq_report = version:set:set_report:result:result_report:recv:recv_report
     * set_report = push-count:transient-count:file-count:chunk-count:update-count:reset-count:invite-count:join-count:leave-count:update-group-count:push-geolocation-count:error-count
     * result_report = push-count:transient-count:file-count:chunk-count:update-count:reset-count:invite-count:join-count:leave-count:update-group-count:push-geolocation-count
     * recv_report = total-count:set-count:result-count:error-count
     *
     * Date: 2018/12/13
     *
     * version: 1
     *
     * iq_report = version:set:set_report:result:result_report:recv:recv_report
     * set_report = push-count:transient-count:file-count:chunk-count:update-count:reset-count:invite-count:join-count:leave-count:update-group-count:error-count
     * result_report = push-count:transient-count:file-count:chunk-count:update-count:reset-count:invite-count:join-count:leave-count:update-group-count
     * recv_report = total-count:set-count:result-count:error-count
     *
     * Date: 2018/05/23
     *
     * version: 1
     *
     * connect_report = version::connect:connectmS::accept:acceptmS::iceRemote:value::iceLocal:value:
     * acceptmS: time to receive the session-accept or time to send the session-accept (in milliseconds)
     * connectmS: time to establish the P2P connection when the session-accept is sent/received (in milliseconds)
     * iceRemove: the number of ICE that were received for the P2P connection
     * iceLocal: the number of ICE that were sent to the peer
     *
     * Date: 2017/12/07
     *
     * version: 2
     *
     * stats_report = version::duration:durationS:[transport_report|outbound_rtp_report|inbound_rtp_report|datachannel_report]*
     *
     * transport_report = :transport:bytesSent:bytesReceived:localNetworkType:localProtocol:localCandidateType:remoteProtocol:remoteCandidateType:
     * outbound_rtp_report = audio_outbound_rtp_report|video_outbound_rtp_report
     * audio_outbound_rtp_report = :outbound-rtp:mimeType:clockRate:bytesSent:packetsSent:
     * video_outbound_rtp_report = :outbound-rtp:mimeType:clockRate:bytesSent:packetsSent:framesEncoded:
     * inbound_rtp_report = audio_inbound_rtp_report|video_inbound_rtp_report
     * audio_inbound_rtp_report = :inbound-rtp:mimeType:clockRate:bytesReceived:packetsReceived:packetsLost:
     * video_inbound_rtp_report = :inbound-rtp:mimeType:clockRate:bytesReceived:packetsReceived:packetsLost:framesDecoded:
     *
     * version: 1
     *
     * stats_report = version::duration::conn_report::audio_send_report::audio_recv_report:
     *  :video_send_report::video_recv_report::datachannel_report
     *
     * conn_report = bytesSent:bytesReceived:googLocalCandidateType:googRemoteCandidateType
     * audio_send_report = audio_send:bytesSent:googCodecName
     * audio_recv_report = audio_recv:bytesReceived:googCodecName
     * video_send_report = video_send:bytesSent:googCodecName:googFrameWidthInput:googFrameHeightInput:
     *  googFrameRateInput:googFrameWidthSent:googFrameHeightSent:googFrameRateSent:googAvgEncodeMs:
     *  googEncodeUsagePercent
     * video_recv_report = video_recv:bytesReceived:googCodecName:googFrameWidthReceived:googFrameHeightReceived:
     *  googFrameRateReceived:googFrameRateDecoded:googFrameRateOutput:googMaxDecodeMs
     * datachannel_report = datachannel:count
     *
     * </pre>
     */

    private static final String DATA_CHANNEL_LABEL = "twinlife:data:conversation";

    // MAX_FRAME_SIZE was 16K until 2022-07-22 (twinme 14.1) and changed to 128K.
    // It is no longer used after 2024-10-01 (twinme 27.0) since framing and leading padding is not used.
    private static final int MAX_FRAME_SIZE = 128 * 1024;

    /*
     * <pre>
     *
     * Frame format : derived from WebSocket frame format
     *
     *    0                   1                   2                   3
     *    0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
     *   +-+-+-+-+-------+-----------------------------------------------+
     *   |F|R|R|R| opcode|                                               |
     *   |I|S|S|S|  (4)  |                                               |
     *   |N|V|V|V|       |                 Payload Data                  |
     *   | |1|2|3|       |                                               |
     *   +-+-+-+-+-------+----------------- - - - - - -- - - - - - - - - +
     *   :                     Payload Data continued ...                :
     *   + - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
     *   |                     Payload Data continued ...                |
     *   +---------------------------------------------------------------+
     *
     * </pre>
     */

    private static final byte OP_CONTINUATION = 0x00;
    private static final byte OP_BINARY = 0x02;
    private static final byte FLAG_FIN = 0x8;

    //
    // Event Ids
    //

    private static final String EVENT_ID_PEER_CONNECTION = PeerConnectionServiceImpl.EVENT_ID_PEER_CONNECTION_SERVICE + "::peerConnection";
    private static final String STATS_REPORT = "statsReport";
    private static final String PEER_CONNECTION_ID = "p2pSessionId";
    private static final String ORIGIN = "origin";
    private static final String CONNECT_REPORT = "connectReport";
    private static final String IQ_REPORT = "iqReport";
    private static final String AUDIO_REPORT = "audioReport";
    private static final String VIDEO_REPORT = "videoReport";

    private class CreateOfferObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(@NonNull SessionDescription sessionDescription) {
            if (DEBUG) {
                Log.d(LOG_TAG, "onCreateSuccess: id=" + mId + " sdp=" + sessionDescription.description);
            }

            PeerConnectionImpl.this.onCreateOfferSuccessInternal(sessionDescription);
        }

        @Override
        public void onSetSuccess() {
            // never called
        }

        @Override
        public void onCreateFailure(@NonNull String error) {
            if (DEBUG) {
                Log.d(LOG_TAG, "onCreateFailure: id=" + mId + " error=" + error);
            }

            PeerConnectionImpl.this.onCreateOfferFailureInternal(error);
        }

        @Override
        public void onSetFailure(String error) {
            // never called
        }
    }

    private abstract class SetLocalDescriptionObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(@NonNull SessionDescription sdp) {
            // never called
        }

        @Override
        public void onCreateFailure(String error) {
            // never called
        }

        @Override
        public void onSetFailure(@NonNull String error) {
            if (DEBUG) {
                Log.d(LOG_TAG, "onSetFailure: id=" + mId + " error=" + error);
            }

            PeerConnectionImpl.this.onSetLocalDescriptionFailureInternal(error);
        }
    }

    private class SetRemoteDescriptionObserver implements SdpObserver {

        @Override
        public void onCreateSuccess(SessionDescription sdp) {
            // never called
        }

        @Override
        public void onSetSuccess() {
            if (DEBUG) {
                Log.d(LOG_TAG, "onSetSuccess id=" + mId);
            }

            PeerConnectionImpl.this.onSetRemoteDescriptionSuccessInternal();
        }

        @Override
        public void onCreateFailure(String error) {
            // never called
        }

        @Override
        public void onSetFailure(@NonNull String error) {
            if (DEBUG) {
                Log.d(LOG_TAG, "onSetFailure: id=" + mId + " error=" + error);
            }

            PeerConnectionImpl.this.onSetRemoteDescriptionFailureInternal(error);
        }
    }

    private final ScheduledExecutorService mPeerConnectionExecutor;
    private final PeerConnectionServiceImpl mPeerConnectionServiceImpl;
    private final PeerCallServiceImpl mPeerCallServiceImpl;
    private final TwinlifeImpl mTwinlifeImpl;

    private final UUID mId;
    @NonNull
    private final String mPeerId;
    private final boolean mInitiator;

    private final CreateOfferObserver mCreateOfferObserver = new CreateOfferObserver();
    private final SetRemoteDescriptionObserver mSetRemoteDescriptionObserver = new SetRemoteDescriptionObserver();
    private final long[] mStatCounters = new long[StatType.values().length];
    private final Map<String, RTCStats> mStats = new HashMap<>();
    private final List<RTCStats> mTrackStats = new ArrayList<>();
    private final TransportCandidateList mPendingCandidates = new TransportCandidateList();
    private final List<byte[]> mOutDataFrames = new ArrayList<>();
    private final PushNotificationContent mNotificationContent;
    private RTCStats mSelectedCandidateStats;
    @Nullable
    private SessionKeyPair mKeyPair;

    private volatile Offer mOffer;
    private volatile OfferToReceive mOfferToReceive;
    private volatile Offer mPeerOffer;
    private volatile OfferToReceive mPeerOfferToReceive;
    private volatile SessionDescription mSessionDescription;
    @Nullable
    private List<Sdp> mPendingSdp;
    @Nullable
    private PeerConnectionObserver mObserver;

    // Fields accessed/updated in PeerConnectionExecutor single thread
    @Nullable
    private PeerConnection mPeerConnection;
    @Nullable
    private PeerConnectionFactory mPeerConnectionFactory = null;
    private boolean mInitialized = false;
    private boolean mTerminated = false;
    private final AtomicInteger mRenegotiationNeeded = new AtomicInteger(1);
    private final AtomicInteger mRenegotiationPending = new AtomicInteger(0);
    @Nullable
    private List<TransportCandidate[]> mIceRemoteCandidates = new ArrayList<>();
    private boolean mAudioSourceOn = false;
    private boolean mVideoSourceOn = false;
    private boolean mDataSourceOn = false;
    private boolean mIgnoreOffer = false;
    private boolean mWithMedia = false;
    private volatile boolean mIsSettingRemoteAnswerPending = false;
    private AudioSource mAudioSource;
    private AudioTrack mAudioTrack;
    private VideoTrack mVideoTrack;
    @Nullable
    private DataChannel mInDataChannel;
    private PeerConnectionService.DataChannelObserver mDataChannelObserver;
    private boolean mLeadingPadding;
    private String mInDataChannelExtension;
    private DataChannel mOutDataChannel;
    private DataChannel.State mDataChannelState = DataChannel.State.CLOSED;

    private long mStartTimestamp = 0;
    private long mStopTimestamp = 0;
    private long mAcceptTimestamp = 0;
    private long mConnectedTimestamp = 0;
    private long mRestartIceTimestamp = 0;
    private int mRemoteIceCandidatesCount = 0;
    private int mLocalIceCandidatesCount = 0;
    private IceConnectionState mState = null;
    @Nullable
    private ScheduledFuture<?> mFlushCandidates = null;
    @Nullable
    private ScheduledFuture<?> mRestartIce = null;

    //
    // Package specific Methods
    //

    PeerConnectionImpl(@NonNull ScheduledExecutorService peerConnectionExecutor,
                       @NonNull PeerConnectionServiceImpl peerConnectionServiceImpl,
                       @NonNull UUID sessionId, @Nullable SessionKeyPair keyPair,
                       @NonNull String peerId, @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                       @NonNull PushNotificationContent notificationContent,
                       @NonNull PeerConnectionObserver observer) {

        mPeerConnectionExecutor = peerConnectionExecutor;
        mPeerConnectionServiceImpl = peerConnectionServiceImpl;
        mTwinlifeImpl = mPeerConnectionServiceImpl.getTwinlifeImpl();
        mPeerCallServiceImpl = mTwinlifeImpl.getPeerCallServiceImpl();
        mObserver = observer;

        mId = sessionId;
        mPeerId = peerId;
        mInitiator = true;
        mKeyPair = keyPair;
        mLeadingPadding = false;

        mOffer = offer;
        mOfferToReceive = offerToReceive;
        mNotificationContent = notificationContent;
    }

    PeerConnectionImpl(@NonNull ScheduledExecutorService peerConnectionExecutor,
                       @NonNull PeerConnectionServiceImpl peerConnectionServiceImpl,
                       @NonNull UUID sessionId, @NonNull String peerId,
                       @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                       @NonNull Sdp sdp) {

        mPeerConnectionExecutor = peerConnectionExecutor;
        mPeerConnectionServiceImpl = peerConnectionServiceImpl;
        mTwinlifeImpl = mPeerConnectionServiceImpl.getTwinlifeImpl();
        mPeerCallServiceImpl = mTwinlifeImpl.getPeerCallServiceImpl();

        mId = sessionId;
        mPeerId = peerId;
        mInitiator = false;
        mLeadingPadding = false;

        mPeerOfferToReceive = offerToReceive;
        mNotificationContent = null;
        mPeerOffer = offer;

        if (sdp.isEncrypted()) {
            mPendingSdp = new ArrayList<>();
            mPendingSdp.add(sdp);
            mSessionDescription = null;
        } else {
            mSessionDescription = new SessionDescription(SessionDescription.Type.OFFER, sdp.getSdp());
        }
    }

    @NonNull
    UUID getId() {

        return mId;
    }

    @Nullable
    SessionKeyPair getKeyPair() {

        return mKeyPair;
    }

    /**
     * Configure the peer connection to decrypt the received SDP.
     *
     * @param keyPair the keypair for the session encryption.
     * @return the list of SDP which were received and are encrypted.
     */
    @Nullable
    synchronized List<Sdp> setKeyPair(@NonNull SessionKeyPair keyPair) {

        final List<Sdp> result = mPendingSdp;
        mKeyPair = keyPair;
        mPendingSdp = null;
        return result;
    }

    /**
     * Queue the sdp which is encrypted if we have not yet the encryption keys.
     *
     * @param sdp the encrypted SDP to queue.
     * @return true if the SDP was queued and false if we can decrypt it immediately.
     */
    synchronized boolean queue(@NonNull Sdp sdp) {

        if (mKeyPair == null) {
            if (mPendingSdp == null) {
                mPendingSdp = new ArrayList<>();
            }
            mPendingSdp.add(sdp);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Check if this P2P connection is terminated.
     *
     * @return true when the P2P connection is terminated.
     */
    synchronized boolean isTerminated() {

        return mTerminated || mPeerConnection == null;
    }

    @Nullable
    Offer getPeerOffer() {

        return mPeerOffer;
    }

    @Nullable
    OfferToReceive getPeerOfferToReceive() {

        return mPeerOfferToReceive;
    }

    synchronized SdpEncryptionStatus getSdpEncryptionStatus() {

        // For an incoming P2P, we could have a null keypair but encrypted SDPs
        // which are queued until we know the decryption keys.
        if (mKeyPair != null) {
            return mKeyPair.needRenew() ? SdpEncryptionStatus.ENCRYPTED_NEED_RENEW : SdpEncryptionStatus.ENCRYPTED;
        } else {
            return mPendingSdp != null ? SdpEncryptionStatus.ENCRYPTED : SdpEncryptionStatus.NONE;
        }
    }

    @NonNull
    String getPeerId() {

        return mPeerId;
    }

    void setAudioDirection(@NonNull RtpTransceiverDirection direction) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setAudioDirection: id=" + mId + " direction=" + direction);
        }

        mPeerConnectionExecutor.execute(() -> setAudioDirectionInternal(direction));
    }

    void setVideoDirection(@NonNull RtpTransceiverDirection direction) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setVideoDirection: id=" + mId + " direction=" + direction);
        }

        mPeerConnectionExecutor.execute(() -> setVideoDirectionInternal(direction));
    }

    void setObserver(@NonNull PeerConnectionObserver observer) {

        mObserver = observer;
    }

    void createIncomingPeerConnection(@NonNull PeerConnection.RTCConfiguration rtcConfiguration,
                                      @Nullable SessionDescription sessionDescription,
                                      @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                                      @Nullable PeerConnectionService.DataChannelObserver consumer,
                                      @NonNull PeerConnectionObserver observer,
                                      @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createIncomingPeerConnection rtcConfiguration=" + rtcConfiguration);
        }

        mPeerConnectionExecutor.execute(() -> {
            mObserver = observer;
            mOffer = offer;
            mOfferToReceive = offerToReceive;
            if (sessionDescription != null) {
                mSessionDescription = sessionDescription;
            }
            complete.onGet(createPeerConnectionInternal(rtcConfiguration, consumer), mId);
        });
    }

    void createOutgoingPeerConnection(@NonNull PeerConnection.RTCConfiguration rtcConfiguration,
                                      @Nullable PeerConnectionService.DataChannelObserver observer,
                                      @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createOutgoingPeerConnection rtcConfiguration=" + rtcConfiguration);
        }

        mPeerConnectionExecutor.execute(() -> complete.onGet(createPeerConnectionInternal(rtcConfiguration, observer), mId));
    }

    void initSources(boolean audioOn, boolean videoOn) {
        if (DEBUG) {
            Log.d(LOG_TAG, "initSources: audioOn=" + audioOn + " videoOn=" + videoOn);
        }

        mPeerConnectionExecutor.execute(() -> {
            if (!initSourcesInternal(audioOn, videoOn)) {

                terminatePeerConnectionInternal(TerminateReason.GENERAL_ERROR, true);
            }
        });
    }

    void acceptRemoteDescription(@NonNull Offer offer, @NonNull SessionDescription sessionDescription) {
        if (DEBUG) {
            Log.d(LOG_TAG,  "acceptConnection: offer=" + offer + " sessionDescription=" + sessionDescription.description);
        }

        mPeerConnectionExecutor.execute(() -> acceptRemoteDescriptionInternal(offer, sessionDescription));
    }

    void updateRemoteDescription(@NonNull SessionDescription sessionDescription) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateRemoteDescription: sessionDescription=" + sessionDescription.description);
        }

        mPeerConnectionExecutor.execute(() -> updateRemoteDescriptionInternal(sessionDescription));
    }

    void addIceCandidate(@NonNull TransportCandidate[] candidates) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addIceCandidate: candidates.length=" + candidates.length);
        }

        mPeerConnectionExecutor.execute(() -> addIceCandidateInternal(candidates));
    }

    void sendMessage(@NonNull StatType statType, @NonNull byte[] bytes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendMessage: bytes=" + Arrays.toString(bytes));
        }

        mPeerConnectionExecutor.execute(() -> sendMessageInternal(statType, bytes, mLeadingPadding));
    }

    public void sendPacket(@NonNull StatType statType, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendPacket: statType=" + statType + " iq=" + iq);
        }

        mPeerConnectionExecutor.execute(() -> {
            try {
                final byte[] bytes = iq.serializeWithPadding(mTwinlifeImpl.getSerializerFactory(), mLeadingPadding);
                sendMessageInternal(statType, bytes, mLeadingPadding);

            } catch (SerializerException exception) {
                Log.e(LOG_TAG, "Serialize exception", exception);
                incrementStat(StatType.SERIALIZE_ERROR);
            }
        });
    }

    public void incrementStat(@NonNull StatType statType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "incrementStat: statType=" + statType);
        }

        switch (statType) {
            case IQ_RECEIVE_SET_COUNT:
            case IQ_RECEIVE_ERROR_COUNT:
            case IQ_RECEIVE_RESULT_COUNT:
            case SERIALIZE_ERROR:
            case SEND_ERROR:
            case SDP_SEND_CLEAR:
            case SDP_SEND_ENCRYPTED:
            case SDP_RECEIVE_CLEAR:
            case SDP_RECEIVE_ENCRYPTED:
                mStatCounters[statType.ordinal()]++;
                break;

            default:
                // Other counters are incremented by sendMessageInternal().
                break;
        }
    }

    void terminatePeerConnection(TerminateReason terminateReason, boolean notifyPeer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "terminatePeerConnection: terminateReason=" + terminateReason + " notifyPeer=" + notifyPeer);
        }

        mPeerConnectionExecutor.execute(() -> terminatePeerConnectionInternal(terminateReason, notifyPeer));
    }

    void onTwinlifeSuspend() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeSuspend " + mId);
        }

        mPeerConnectionExecutor.execute(() -> {
            terminatePeerConnectionInternal(mConnectedTimestamp > 0 ? TerminateReason.DISCONNECTED : TerminateReason.CANCEL, true);
        });
    }

    void sendDeviceRinging(){
        mPeerCallServiceImpl.deviceRinging(mId, mPeerId);
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {
        return "[id=" + mId + " peerId=" + mPeerId + "]";
    }

    //
    // Private Methods
    //

    private void setAudioDirectionInternal(@NonNull RtpTransceiverDirection direction) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setAudioDirectionInternal: id=" + mId + " direction=" + direction);
        }

        if (isTerminated()) {
            return;
        }

        initSourcesInternal(direction == RtpTransceiverDirection.SEND_RECV, mVideoSourceOn);
    }

    private void setVideoDirectionInternal(@NonNull RtpTransceiverDirection direction) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setVideoDirectionInternal: id=" + mId + " direction=" + direction);
        }

        if (isTerminated()) {
            return;
        }

        initSourcesInternal(mAudioSourceOn, direction == RtpTransceiverDirection.SEND_RECV);
    }

    @NonNull
    private ErrorCode createPeerConnectionInternal(@NonNull PeerConnection.RTCConfiguration rtcConfiguration,
                                                   @Nullable PeerConnectionService.DataChannelObserver observer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createPeerConnectionInternal id=" + mId + " rtcConfiguration=" + rtcConfiguration);
        }

        // Use a data only peer connection factory if this is a data-channel only connection.
        // We avoid the creation and initialization of audio threads, audio devices, codecs and WebRTC media engine.
        // However, if the media aware peer connection factory is available, we are going to use it.
        mWithMedia = mOffer.audio | mOffer.video | mOffer.videoBell;
        final PeerConnectionFactory peerConnectionFactory = mPeerConnectionServiceImpl.getPeerConnectionFactory(mWithMedia);
        mStartTimestamp = SystemClock.elapsedRealtime();
        mDataChannelObserver = observer;

        rtcConfiguration.tcpCandidatePolicy = mWithMedia ? PeerConnection.TcpCandidatePolicy.DISABLED : PeerConnection.TcpCandidatePolicy.ENABLED;
        mPeerConnection = peerConnectionFactory.createPeerConnection(rtcConfiguration, this);
        if (mPeerConnection == null) {

            return ErrorCode.WEBRTC_ERROR;
        }

        mPeerConnectionFactory = peerConnectionFactory;
        peerConnectionFactory.incrementUseCounter();

        if (mSessionDescription != null) {
            SessionDescription updatedSessionDescription = updateCodecs(mSessionDescription);
            mSessionDescription = null;
            mPeerConnection.setRemoteDescription(mSetRemoteDescriptionObserver, updatedSessionDescription);
        }

        mFlushCandidates = mPeerConnectionExecutor.schedule(this::onFlushCandidates, 1000, TimeUnit.MILLISECONDS);
        mDataSourceOn = mOffer.data;

        if (mDataSourceOn && observer != null) {
            final PeerConnectionService.DataChannelConfiguration channelConfiguration = observer.getConfiguration(mId, getSdpEncryptionStatus());
            mLeadingPadding = channelConfiguration.leadingPadding;

            String label = DATA_CHANNEL_LABEL + '.' + channelConfiguration.version;
            mOutDataChannel = mPeerConnection.createDataChannel(label, new DataChannel.Init());
            if (mOutDataChannel == null) {
                mTwinlifeImpl.assertion(PeerConnectionAssertPoint.CREATE_DATA_CHANNEL, AssertPoint.createPeerConnectionId(mId));
                return ErrorCode.WEBRTC_ERROR;
            }
            mOutDataChannel.registerObserver(this);

            // If this is a data-channel only WebRTC connection, start the offer or answer immediately.
            // For the audio/video, this will be done at the first call to initSources().
            if (!mWithMedia) {
                if (mInitiator) {
                    createOfferInternal();
                } else {
                    createAnswerInternal();
                }
            }
        }
        return ErrorCode.SUCCESS;
    }

    private boolean initSourcesInternal(boolean audioOn, boolean videoOn) {
        if (DEBUG) {
            Log.d(LOG_TAG, "initSourcesInternal: audioOn=" + audioOn + " videoOn=" + videoOn);
        }

        if (mPeerConnection == null || mPeerConnectionFactory == null) {
            if (!mTerminated) {
                mTwinlifeImpl.assertion(PeerConnectionAssertPoint.NOT_TERMINATED, AssertPoint.createPeerConnectionId(mId));
            }

            return false;
        }

        boolean updateAudio = mAudioSourceOn != audioOn || (audioOn && mAudioTrack == null);
        boolean updateVideo = mVideoSourceOn != videoOn || (videoOn && mVideoTrack == null);

        mAudioSourceOn = audioOn;
        mVideoSourceOn = videoOn;

        List<String> labels = new ArrayList<>();
        labels.add("media");

        // Block and track WebRTC observer calls to peerConnectionShouldNegotiate() while we update
        // the audio/video tracks.  The counter will be incremented from the WebRTC signaling thread
        // while we do the setDirection(), we handle the renegotation at the end if it was necessary.
        mRenegotiationNeeded.set(1);

        if (updateAudio) {
            if (mAudioSourceOn) {
                MediaConstraints mediaConstraints = new MediaConstraints();
                mAudioSource = mPeerConnectionFactory.createAudioSource(mediaConstraints);
                if (mAudioSource == null) {
                    mStatCounters[StatType.AUDIO_TRACK_ERROR.ordinal()]++;
                    mTwinlifeImpl.assertion(PeerConnectionAssertPoint.CREATE_AUDIO_SOURCE, AssertPoint.createPeerConnectionId(mId));

                    return false;
                }

                mAudioTrack = mPeerConnectionFactory.createAudioTrack(UUID.randomUUID().toString(), mAudioSource);
                if (mAudioTrack == null) {
                    mStatCounters[StatType.AUDIO_TRACK_ERROR.ordinal()]++;
                    mTwinlifeImpl.assertion(PeerConnectionAssertPoint.CREATE_AUDIO_TRACK, AssertPoint.createPeerConnectionId(mId));

                    return false;
                }

                // Enable the audio track only when we are connected.
                if ((mState == IceConnectionState.CONNECTED || mState == IceConnectionState.COMPLETED)) {
                    mAudioTrack.setEnabled(true);
                }

                RtpSender audioTrackSender = null;

                // Find an existing RtpTransceiver that is not used and which can send video.
                for (RtpTransceiver tr : mPeerConnection.getTransceivers()) {
                    if (!tr.isStopped() && tr.getMediaType() == MediaType.MEDIA_TYPE_AUDIO && (tr.getDirection() != RtpTransceiverDirection.SEND_RECV)) {
                        audioTrackSender = tr.getSender();
                        audioTrackSender.setTrack(mAudioTrack, false);
                        tr.setDirection(RtpTransceiverDirection.SEND_RECV);
                        break;
                    }
                }

                if (audioTrackSender == null) {
                    audioTrackSender = mPeerConnection.addTrack(mAudioTrack, labels);
                }

                final PeerConnectionObserver peerConnectionObserver = mObserver;
                if (peerConnectionObserver != null) {
                    // Keep the mAudioTrack locally because when onAddLocalVideoTrack() is called, the P2P connection
                    // could have been released.
                    final RtpSender lAudioTrackSender = audioTrackSender;
                    final AudioTrack lAudioTrack = mAudioTrack;
                    mPeerConnectionExecutor.execute(() -> peerConnectionObserver.onAddLocalAudioTrack(mId, lAudioTrackSender, lAudioTrack));
                }
            } else {
                mAudioTrack.setEnabled(false);
                mAudioTrack = null;

                // The audio is turned OFF and we have an audio track: clear the tracks with audio
                // and set the transceiver to the inactive state (but keep it).
                for (RtpTransceiver tr : mPeerConnection.getTransceivers()) {
                    if (!tr.isStopped() && tr.getMediaType() == MediaType.MEDIA_TYPE_AUDIO && tr.getDirection() == RtpTransceiverDirection.SEND_RECV) {
                        RtpSender sender = tr.getSender();
                        sender.setTrack(null, false);
                        tr.setDirection(RtpTransceiverDirection.RECV_ONLY);
                    }
                }
            }
        } else if (!mInitialized && mOffer.audio) {
            // If we add a participant while muted, we need to make sure to create an audio transceiver otherwise
            // we won't receive the peer's audio.
            RtpTransceiver transceiver = mPeerConnection.addTransceiver(MediaType.MEDIA_TYPE_AUDIO);
            if(transceiver != null){
                transceiver.setDirection(RtpTransceiverDirection.RECV_ONLY);
            }
        }

        if (updateVideo) {
            if (mVideoSourceOn) {
                mVideoTrack = mPeerConnectionServiceImpl.createVideoTrack();
                if (mVideoTrack == null) {
                    mStatCounters[StatType.VIDEO_TRACK_ERROR.ordinal()]++;

                    return false;
                }

                RtpSender videoTrackSender = null;

                // Find an existing RtpTransceiver that is not used and which can send video.
                for (RtpTransceiver tr : mPeerConnection.getTransceivers()) {
                    if (!tr.isStopped() && tr.getMediaType() == MediaType.MEDIA_TYPE_VIDEO && tr.getDirection() != RtpTransceiverDirection.SEND_RECV) {
                        videoTrackSender = tr.getSender();
                        videoTrackSender.setTrack(mVideoTrack, false);
                        tr.setDirection(RtpTransceiverDirection.SEND_RECV);
                        break;
                    }
                }

                // When no RtpTransceiver was found, use addTrack to add the new track.
                if (videoTrackSender == null) {
                    mPeerConnection.addTrack(mVideoTrack, labels);
                }

            } else {

                // The video is turned OFF and we have a video track: clear the tracks with video
                // and set the transceiver to the inactive state (but keep it).
                for (RtpTransceiver tr : mPeerConnection.getTransceivers()) {
                    if (!tr.isStopped() && tr.getMediaType() == MediaType.MEDIA_TYPE_VIDEO && tr.getDirection() == RtpTransceiverDirection.SEND_RECV) {
                        RtpSender sender = tr.getSender();
                        sender.setTrack(null, false);
                        tr.setDirection(RtpTransceiverDirection.RECV_ONLY);
                    }
                }

                // If the camera is opened, release it.
                if (mVideoTrack != null) {
                    mPeerConnectionServiceImpl.releaseVideoTrack(mVideoTrack);
                    mVideoTrack = null;
                }
            }
        }

        if (!mInitialized) {
            if (mInitiator) {
                createOfferInternal();
            } else {
                createAnswerInternal();
            }
        } else {
            checkRenegotiation(1);
        }

        return true;
    }

    private void checkRenegotiation(int counter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkRenegotiation: counter=" + counter);
        }

        // Handle renegotiation only when:
        // - we have sent the session-initiate,
        // - the WebRTC observer peerConnectionShouldNegotiate() was called.
        int updatedCounter = mRenegotiationNeeded.addAndGet(-counter);
        if (updatedCounter > 0) {
            // We can handle the renegotiation only if there is nothing in progress.
            // Check and update the pending counter as it will be used to decrement
            // the renegotationNeeded when we have sent our session-update.
            if (mRenegotiationPending.compareAndSet(0, updatedCounter)) {
                doRenegotiationInternal();
            }
        }
    }

    private void acceptRemoteDescriptionInternal(@NonNull Offer offer, @NonNull SessionDescription sessionDescription) {
        if (DEBUG) {
            Log.d(LOG_TAG, "acceptRemoteDescriptionInternal: sessionDescription=" + sessionDescription.description);
        }

        if (mTerminated) {

            return;
        }

        mPeerOffer = offer;

        if (mAcceptTimestamp == 0) {
            mAcceptTimestamp = SystemClock.elapsedRealtime();
        }
        if (mPeerConnection == null) {
            mSessionDescription = sessionDescription;
        } else if (mPeerConnection.signalingState() != SignalingState.STABLE) {
            SessionDescription updatedSessionDescription = updateCodecs(sessionDescription);
            mPeerConnection.setRemoteDescription(mSetRemoteDescriptionObserver, updatedSessionDescription);
        }

        if (!mPendingCandidates.isFlushed()) {
            onFlushCandidates();
        }

        final PeerConnectionObserver observer = mObserver;
        if (observer != null) {
            mPeerConnectionExecutor.execute(() -> observer.onAcceptPeerConnection(mId, offer));
        }
    }

    private void updateRemoteDescriptionInternal(@NonNull SessionDescription sessionDescription) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateRemoteDescriptionInternal: sessionDescription=" + sessionDescription.description);
        }

        if (isTerminated() || mPeerConnection == null) {

            return;
        }

        // See https://w3c.github.io/webrtc-pc/#perfect-negotiation-example
        // An offer may come in while we are busy processing SRD(answer).
        // In this case, we will be in "stable" by the time the offer is processed
        // so it is safe to chain it on our Operations Chain now.
        SignalingState state = mPeerConnection.signalingState();
        boolean isOffer = sessionDescription.type == SessionDescription.Type.OFFER;
        boolean readyForOffer = mRenegotiationPending.get() == 0 && (state == SignalingState.STABLE || mIsSettingRemoteAnswerPending);
        boolean offerCollision = isOffer && !readyForOffer;

        mIgnoreOffer = !mInitiator && offerCollision;
        if (mIgnoreOffer) {

            return;
        }

        mIsSettingRemoteAnswerPending = sessionDescription.type == SessionDescription.Type.ANSWER;
        SessionDescription updatedSessionDescription = updateCodecs(sessionDescription);
        mPeerConnection.setRemoteDescription(new SetRemoteDescriptionObserver() {

            @Override
            public void onSetSuccess() {

                mIsSettingRemoteAnswerPending = false;
                if (isOffer) {

                    createAnswerInternal();
                }
            }

            @Override
            public void onSetFailure(@NonNull String error) {

                // Create the answer even if this failed.
                mIsSettingRemoteAnswerPending = false;
                if (isOffer) {

                    createAnswerInternal();
                }
            }
        }, updatedSessionDescription);
    }

    private void addIceCandidateInternal(@NonNull TransportCandidate[] candidates) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addIceCandidateInternal: candidates.length=" + candidates.length);
        }

        synchronized (this) {
            if (mTerminated) {

                return;
            }

            // If the P2P connection is not yet created, record the ICE candidates.
            if (mPeerConnection == null || mIceRemoteCandidates != null) {
                if (mIceRemoteCandidates == null) {
                    mIceRemoteCandidates = new ArrayList<>();
                }

                mIceRemoteCandidates.add(candidates);
                return;
            }
        }

        long dt = SystemClock.elapsedRealtime() - mStartTimestamp;

        List<IceCandidate> removeList = null;
        for (TransportCandidate candidate : candidates) {
            IceCandidate iceCandidate = new IceCandidate(candidate.label, candidate.id, candidate.sdp);
            if (!candidate.removed) {
                mRemoteIceCandidatesCount++;
                EventMonitor.info(LOG_TAG, " PEER-ICE ", dt, " ms ", mId, " ", candidate.label, ": ", candidate.sdp);
                if (!mPeerConnection.addIceCandidate(iceCandidate) && !mIgnoreOffer) {
                    if (!isTerminated()) {
                        terminatePeerConnectionInternal(TerminateReason.GENERAL_ERROR, true);
                    }
                    return;
                }
            } else {
                mRemoteIceCandidatesCount--;
                EventMonitor.info(LOG_TAG, " DEL PEER-ICE", dt, " ms ", mId, " ", candidate.label, ": ", candidate.sdp);
                if (removeList == null) {
                    removeList = new ArrayList<>();
                }
                removeList.add(iceCandidate);
            }
        }

        // There are some ICE that are no longer valid, remove them.
        if (removeList != null) {
            EventMonitor.info(LOG_TAG, "Removed ", removeList.size(), " candidates");
            mPeerConnection.removeIceCandidates(removeList.toArray(new IceCandidate[0]));
        }
    }

    private void sendMessageInternal(@NonNull StatType statType, @NonNull byte[] bytes, boolean leadingPadding) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendMessageInternal: bytes=" + Arrays.toString(bytes) + " leadingPadding=" + leadingPadding);
        }

        if (mOutDataChannel != null) {
            if (leadingPadding) {
                if (bytes.length <= MAX_FRAME_SIZE) {
                    bytes[0] = OP_BINARY | (byte) (FLAG_FIN << 4);
                    boolean result = mOutDataChannel.send(bytes, true);
                    if (!result) {
                        mStatCounters[StatType.SEND_ERROR.ordinal()]++;
                        if (mStatCounters[StatType.FIRST_SEND_ERROR.ordinal()] == 0) {
                            mStatCounters[StatType.FIRST_SEND_ERROR.ordinal()] = statType.ordinal() + 1;
                            mStatCounters[StatType.FIRST_SEND_ERROR_TIME.ordinal()] = SystemClock.elapsedRealtime() - mConnectedTimestamp;
                        }
                    } else {
                        mStatCounters[statType.ordinal()]++;
                    }
                } else {
                    byte[] frame = new byte[MAX_FRAME_SIZE];
                    System.arraycopy(bytes, 0, frame, 0, MAX_FRAME_SIZE);
                    frame[0] = OP_BINARY;
                    boolean result = mOutDataChannel.send(frame, true);
                    if (!result) {
                        mStatCounters[StatType.SEND_ERROR.ordinal()]++;
                    } else {
                        mStatCounters[statType.ordinal()]++;
                    }
                    int start = MAX_FRAME_SIZE;
                    while (start < bytes.length) {
                        int length = Math.min(MAX_FRAME_SIZE - 1, bytes.length - start);
                        if (length < MAX_FRAME_SIZE - 1) {
                            frame = new byte[length + 1];
                        }

                        if (start + length < bytes.length) {
                            frame[0] = OP_CONTINUATION;
                        } else {
                            frame[0] = OP_CONTINUATION | (byte) (FLAG_FIN << 4);
                        }
                        System.arraycopy(bytes, start, frame, 1, length);
                        result = mOutDataChannel.send(frame, true);
                        if (!result) {
                            mStatCounters[StatType.SEND_ERROR.ordinal()]++;
                        }
                        start += length;
                    }
                }
            } else {
                boolean result = mOutDataChannel.send(bytes, true);
                if (!result) {
                    mStatCounters[StatType.SEND_ERROR.ordinal()]++;
                    if (mStatCounters[StatType.FIRST_SEND_ERROR.ordinal()] == 0) {
                        mStatCounters[StatType.FIRST_SEND_ERROR.ordinal()] = statType.ordinal() + 1;
                        mStatCounters[StatType.FIRST_SEND_ERROR_TIME.ordinal()] = SystemClock.elapsedRealtime() - mConnectedTimestamp;
                    }
                } else {
                    mStatCounters[statType.ordinal()]++;
                }
            }
        } else {
            mStatCounters[StatType.SEND_ERROR.ordinal()]++;
            Log.e(LOG_TAG, "There is no data channel");
        }
    }

    private void terminatePeerConnectionInternal(TerminateReason terminateReason, boolean notifyPeer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "terminatePeerConnectionInternal: terminateReason=" + terminateReason + " notifyPeer=" + notifyPeer);
        }

        synchronized (this) {
            if (mTerminated) {

                return;
            }
            mTerminated = true;
        }

        final String reason = terminateReason.toString();
        if (notifyPeer) {
            mPeerCallServiceImpl.sessionTerminate(mId, mPeerId, terminateReason, null);
        }

        if (mDataSourceOn) {
            EventMonitor.event("Close data " + Utils.toLog(mId) + ": ", reason, mStartTimestamp);
        } else if (mVideoSourceOn) {
            EventMonitor.event("Close video " + Utils.toLog(mId) + ": ", reason, mStartTimestamp);
        } else if (mAudioSourceOn) {
            EventMonitor.event("Close audio " + Utils.toLog(mId) + ": ", reason, mStartTimestamp);
        }
        getStatsAndDispose();

        mPeerConnectionServiceImpl.onTerminatePeerConnection(mId, terminateReason);

        final PeerConnectionObserver observer = mObserver;
        if (observer != null) {
            mPeerConnectionExecutor.execute(() -> observer.onTerminatePeerConnection(mId, terminateReason));
        }
    }

    private void restartIce() {
        if (DEBUG) {
            Log.d(LOG_TAG, "restartIce: id=" + mId + " state=" + mState);
        }

        if (isTerminated() || mPeerConnection == null) {
            return;
        }

        mRestartIce = null;
        if (mState != IceConnectionState.DISCONNECTED && mState != IceConnectionState.FAILED) {
            return;
        }

        mRestartIceTimestamp = SystemClock.elapsedRealtime();
        mRenegotiationNeeded.set(0);
        mPeerConnection.restartIce();

        // Give 5s to recover or fail.
        mPeerConnectionExecutor.schedule(() -> {
            if (mState == IceConnectionState.CONNECTED) {
                return;
            }
            terminatePeerConnectionInternal(TerminateReason.DISCONNECTED, true);
        }, 5000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onStandardizedIceConnectionChange(IceConnectionState iceConnectionChange) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onStandardizedIceConnectionChange: id=" + mId + " iceConnectionChange=" + iceConnectionChange);
        }
    }

    @Override
    public void onIceConnectionReceivingChange(boolean receiving) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onIceConnectionReceivingChange: id=" + mId + " receiving=" + receiving);
        }
    }

    @Override
    public void onIceGatheringChange(IceGatheringState iceGatheringState) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onIceGatheringChange: id=" + mId + " iceGatheringState=" + iceGatheringState);
        }
    }

    @Override
    public void onSignalingChange(SignalingState signalingState) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignalingChange: id=" + mId + " signalingState=" + signalingState);
        }

        if (isTerminated()) {

            return;
        }

        if (signalingState == SignalingState.CLOSED) {
            terminatePeerConnectionInternal(TerminateReason.CONNECTIVITY_ERROR, true);
        }
    }

    @Override
    public void onIceConnectionChange(IceConnectionState iceConnectionState) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onIceConnectionChange: id=" + mId + " iceConnectionState=" + iceConnectionState);
        }

        if (isTerminated()) {
            return;
        }

        final PeerConnectionObserver observer = mObserver;
        mState = iceConnectionState;
        switch (iceConnectionState) {
            case NEW:
                if (observer != null) {
                    mPeerConnectionExecutor.execute(() -> observer.onChangeConnectionState(mId, ConnectionState.CONNECTING));
                }
                break;

            case CHECKING:
                if (observer != null) {
                    mPeerConnectionExecutor.execute(() -> observer.onChangeConnectionState(mId, ConnectionState.CHECKING));
                }
                break;

            case CONNECTED:
                if (mConnectedTimestamp == 0) {
                    mConnectedTimestamp = SystemClock.elapsedRealtime();
                }
                mPeerConnectionServiceImpl.onChangeConnectionState(mId, PeerConnectionService.ConnectionState.CONNECTED);
                if (observer != null) {
                    mPeerConnectionExecutor.execute(() -> observer.onChangeConnectionState(mId, ConnectionState.CONNECTED));
                }

                // Now that we are connected, enable the audio and video tracks unless they are muted.
                if (mAudioTrack != null) {
                    mAudioTrack.setEnabled(mAudioSourceOn);
                }
                if (mVideoTrack != null) {
                    mVideoTrack.setEnabled(mVideoSourceOn);
                }
                if (mRestartIce != null) {
                    mRestartIce.cancel(false);
                    mRestartIce = null;
                }
                break;

            case DISCONNECTED:
            case FAILED:
                if (mConnectedTimestamp > 0 && mPeerConnection != null) {
                    final long now = SystemClock.elapsedRealtime();
                    if (mRestartIceTimestamp + 15000 < now) {
                        // Trigger the ICE restart in 2.5 (audio/video) or 5.0 (data) seconds in case it was a transient disconnect.
                        // We must be careful that the P2P connection could have been terminated and released.
                        if (mRestartIce != null) {
                            mRestartIce.cancel(false);
                        }
                        mRestartIce = mPeerConnectionExecutor.schedule(this::restartIce, mWithMedia ? 2500 : 5000, TimeUnit.MILLISECONDS);
                        return;
                    }
                }
                terminatePeerConnectionInternal(iceConnectionState == IceConnectionState.DISCONNECTED ? TerminateReason.DISCONNECTED : TerminateReason.CONNECTIVITY_ERROR, true);
                break;

            case CLOSED:
                // The ICE agent for this RTCPeerConnection has shut down and is no longer handling requests.
                // Not really an error but some proper shut down.
                terminatePeerConnectionInternal(TerminateReason.DISCONNECTED, true);
                break;

            default:
                break;
        }
    }

    @Override
    public void onIceCandidate(@NonNull IceCandidate candidate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onIceCandidate: id=" + mId + " candidate=" + candidate);
        }

        if (isTerminated()) {

            return;
        }

        mLocalIceCandidatesCount++;

        long dt = SystemClock.elapsedRealtime() - mStartTimestamp;
        EventMonitor.info(LOG_TAG, " ICE ", dt, " ms ", mId, " ", candidate.sdpMid, ": ", candidate.sdp);

        mPendingCandidates.addCandidate(candidate.sdpMLineIndex, candidate.sdpMid, candidate.sdp);

        if (mFlushCandidates == null) {
            onFlushCandidates();
        }
    }

    @Override
    public void onIceCandidatesRemoved(@NonNull IceCandidate[] candidates) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onIceCandidatesRemoved: id=" + mId + " candidates=" + Arrays.toString(candidates));
        }

        if (isTerminated()) {

            return;
        }

        for (IceCandidate candidate : candidates) {
            mLocalIceCandidatesCount--;
            mPendingCandidates.removeCandidate(candidate.sdpMLineIndex, candidate.sdpMid, candidate.sdp);
        }

        if (mFlushCandidates == null) {
            onFlushCandidates();
        }
    }

    @Override
    public void onSelectedCandidatePairChanged(CandidatePairChangeEvent event) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSelectedCandidatePairChanged: id=" + mId + " event=" + event);
        }
    }

    void sessionPing() {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionPing id=" + mId);
        }

        if (isTerminated()) {

            return;
        }

        if (canPing()) {
            mPeerCallServiceImpl.sessionPing(mId, mPeerId, (ErrorCode errorCode, Long requestId) -> {
                if (errorCode != ErrorCode.SUCCESS && errorCode != ErrorCode.TWINLIFE_OFFLINE) {
                    terminatePeerConnectionInternal(TerminateReason.TIMEOUT, false);
                }
            });
        }
        if (mFlushCandidates == null) {
            onFlushCandidates();
        }
    }

    /**
     * Check if we can send a session-ping to the server.
     *
     * @return true if the session is an Audio/Video session, we are the initiator or we have a resource id in our PeerId.
     */
    private boolean canPing() {
        if (DEBUG) {
            Log.d(LOG_TAG, "canPing");
        }

        if (!mOffer.audio && !mOffer.video) {

            return false;
        }

        if (mInitiator) {

            return true;
        }

        return mPeerId.indexOf('/') > 0;
    }

    private void onFlushCandidates() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onFlushCandidates id=" + mId);
        }

        mFlushCandidates = null;

        if (!isTerminated() && !mPendingCandidates.isFlushed()) {

            mPeerConnectionServiceImpl.transportInfo(this, mPendingCandidates, (ErrorCode errorCode, Long requestId) -> {

                if (requestId != null) {
                    if (errorCode == ErrorCode.TWINLIFE_OFFLINE || errorCode == ErrorCode.TIMEOUT_ERROR) {
                        mPendingCandidates.cancel(requestId);
                        mPeerConnectionServiceImpl.onTimeout(this);
                        return;
                    } else {
                        mPendingCandidates.remove(requestId);
                    }
                }

                onSendServer(errorCode, null);
            });
        }
    }

    @Override
    public void onTrack(@NonNull RtpTransceiver transceiver) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTrack: id=" + mId + " transceiver=" + transceiver);
        }

        if (isTerminated()) {

            return;
        }

        final RtpReceiver receiver = transceiver.getReceiver();
        final MediaStreamTrack mediaStreamTrack = receiver.track();
        final PeerConnectionObserver observer = mObserver;
        if (mediaStreamTrack != null && observer != null) {
            mPeerConnectionExecutor.execute(() -> observer.onAddRemoteMediaStreamTrack(mId, mediaStreamTrack));
        }
    }

    @Override
    public void onRemoveTrack(@NonNull RtpReceiver receiver) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRemoveTrack: id=" + mId + " receiver=" + receiver);
        }

        if (isTerminated()) {

            return;
        }

        final MediaStreamTrack mediaStreamTrack = receiver.track();
        final PeerConnectionObserver observer = mObserver;
        if (mediaStreamTrack != null && observer != null) {
            final String trackId = mediaStreamTrack.id();
            mPeerConnectionExecutor.execute(() -> observer.onRemoveRemoteTrack(mId, trackId));
        }
    }

    @Override
    public void onDataChannel(@NonNull DataChannel dataChannel) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDataChannelInternal: dataChannel=" + dataChannel);
        }

        if (isTerminated()) {

            return;
        }

        String label = dataChannel.label();
        int index = label.indexOf('.');
        if (index == -1) {
            mInDataChannelExtension = null;
        } else {
            mInDataChannelExtension = label.substring(index + 1);
        }
        mInDataChannel = dataChannel;
        dataChannel.registerObserver(this);

        if (mDataChannelState == DataChannel.State.OPEN && mDataChannelObserver != null) {
            mDataChannelObserver.onDataChannelOpen(mId, mInDataChannelExtension, mLeadingPadding);
        }
    }

    private void createAnswerInternal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "createAnswerInternal id=" + mId);
        }

        synchronized (this) {
            if (mPeerConnection == null || mTerminated) {

                return;
            }

            if (mAcceptTimestamp == 0) {
                mAcceptTimestamp = SystemClock.elapsedRealtime();
            }
        }

        // Create the answer by setting the local description and let Web-RTC define the correct answer.
        mPeerConnection.setLocalDescription(new SetLocalDescriptionObserver() {

            @Override
            public void onSetSuccess() {

                onSetLocalDescription(null);
            }
        });
    }

    private void createOfferInternal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "createOfferInternal id=" + mId);
        }

        synchronized (this) {
            if (mPeerConnection == null || mTerminated) {

                return;
            }
        }

        MediaConstraints mediaConstraints = new MediaConstraints();
        if (mOfferToReceive.video && mVideoTrack != null) {
            EventMonitor.info(LOG_TAG, "Start video peer ", mId);
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        }
        if (mOfferToReceive.audio && mAudioTrack != null) {
            if (!mOfferToReceive.video) {
                EventMonitor.info(LOG_TAG, "Start audio peer ", mId);
            }
            mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        }
        if (mDataSourceOn) {
            EventMonitor.info(LOG_TAG, "Start data peer ", mId);
        }

        mPeerConnection.createOffer(mCreateOfferObserver, mediaConstraints);
    }

    private void onCreateOfferSuccessInternal(@NonNull SessionDescription sessionDescription) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateOfferSuccessInternal: id=" + mId + " sdp=" + sessionDescription.description);
        }

        synchronized (this) {
            if (mPeerConnection == null || mTerminated) {

                return;
            }
        }

        // Filter the codecs before sending the SDP to the peer.
        final SessionDescription updatedSessionDescription = updateCodecs(sessionDescription);

        mPeerConnection.setLocalDescription(new SetLocalDescriptionObserver() {

            @Override
            public void onSetSuccess() {

                onSetLocalDescription(updatedSessionDescription);
            }

        }, updatedSessionDescription);
    }

    private void onCreateOfferFailureInternal(@NonNull String error) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateOfferFailureInternal: id=" + mId + " error=" + error);
        }

        mTwinlifeImpl.assertion(PeerConnectionAssertPoint.OFFER_FAILURE, AssertPoint.createPeerConnectionId(mId));

        terminatePeerConnectionInternal(TerminateReason.GENERAL_ERROR, true);
    }

    private void onSetLocalDescriptionFailureInternal(@NonNull String error) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSetLocalDescriptionFailureInternal: id=" + mId + " error=" + error);
        }

        mTwinlifeImpl.assertion(PeerConnectionAssertPoint.SET_LOCAL_FAILURE, AssertPoint.createPeerConnectionId(mId));

        terminatePeerConnectionInternal(TerminateReason.GENERAL_ERROR, true);
    }

    private void onSetLocalDescription(@Nullable SessionDescription sessionDescription) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSetLocalDescription id=" + mId + " sessionDescription=" + sessionDescription);
        }

        synchronized (this) {
            if (mTerminated || mPeerConnection == null) {

                return;
            }
        }

        if (sessionDescription == null) {
            sessionDescription = mPeerConnection.getLocalDescription();
            sessionDescription = updateCodecs(sessionDescription);
        }

        final Sdp sdp = new Sdp(sessionDescription.description);
        if (mInitialized) {
            final SdpType sdpType = sessionDescription.type == SessionDescription.Type.ANSWER ? SdpType.ANSWER : SdpType.OFFER;
            mPeerConnectionServiceImpl.sessionUpdate(this, sdp, sdpType, this::onSendServer);

            // Now we can decrement the renegotiation counter and handle a possible deferred renegotiation.
            int counter;
            do {
                counter = mRenegotiationPending.get();
            } while (!mRenegotiationPending.compareAndSet(counter, 0));
            checkRenegotiation(counter);

        } else if (sessionDescription.type == SessionDescription.Type.ANSWER) {
            mInitialized = true;
            mRenegotiationNeeded.set(0);
            mPeerConnectionServiceImpl.sessionAccept(this, sdp, mOffer, mOfferToReceive, this::onSendServer);

            final List<TransportCandidate[]> iceCandidates;
            synchronized (this) {
                iceCandidates = mIceRemoteCandidates;
                mIceRemoteCandidates = null;
            }

            // If we have some peer ICE candidates, give them to the WebRTC connection now it is ready.
            if (iceCandidates != null) {
                for (TransportCandidate[] candidates : iceCandidates) {
                    addIceCandidateInternal(candidates);
                }
            }
        } else {
            mInitialized = true;
            mRenegotiationNeeded.set(0);
            mPeerConnectionServiceImpl.sessionInitiate(this, sdp,
                    mOffer, mOfferToReceive, mNotificationContent, (ErrorCode status, Long requestId) -> {

                // An ITEM_NOT_FOUND on the session-initiate means the peer was revoked.
                if (status == ErrorCode.ITEM_NOT_FOUND) {
                    terminatePeerConnectionInternal(TerminateReason.REVOKED, false);
                    return;
                }

                onSendServer(status, requestId);
            });
        }
    }

    private void onSendServer(@NonNull ErrorCode errorCode, Long requestId) {

        switch (errorCode) {
            case QUEUED:
            case SUCCESS:
                if (mFlushCandidates != null ) {
                    mFlushCandidates.cancel(false);

                    long flushDelay;
                    if (errorCode == ErrorCode.SUCCESS) {
                        flushDelay = 300;
                    } else {
                        flushDelay = mAudioSourceOn || mVideoSourceOn ? 2000 : 700;
                    }
                    mFlushCandidates = mPeerConnectionExecutor.schedule(this::onFlushCandidates, flushDelay, TimeUnit.MILLISECONDS);
                }
                break;

            case QUEUED_NO_WAKEUP:
                // If this is a data only P2P session and we have no wait to wakeup the peer, cancel this P2P session
                if (!mAudioSourceOn && !mVideoSourceOn && mDataSourceOn) {
                    terminatePeerConnectionInternal(TerminateReason.CANCEL, true);
                }
                break;

            case NO_PERMISSION:
            case NOT_AUTHORIZED_OPERATION:
            case FEATURE_NOT_SUPPORTED_BY_PEER:
                terminatePeerConnectionInternal(TerminateReason.NOT_AUTHORIZED, false);
                break;

            case ITEM_NOT_FOUND:
                terminatePeerConnectionInternal(TerminateReason.CANCEL, false);
                break;

            case EXPIRED:
                terminatePeerConnectionInternal(TerminateReason.TIMEOUT, false);
                break;

            case TIMEOUT_ERROR:
            case TWINLIFE_OFFLINE:
                terminatePeerConnectionInternal(TerminateReason.CONNECTIVITY_ERROR, false);
                break;

            case BAD_REQUEST:
            case SERVER_ERROR:
                terminatePeerConnectionInternal(TerminateReason.GENERAL_ERROR, false);
                break;
        }
    }

    private void onSetRemoteDescriptionSuccessInternal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSetRemoteDescriptionSuccessInternal id=" + mId);
        }

        final List<TransportCandidate[]> iceCandidates;
        synchronized (this) {
            // WebRTC accepts ICE candidates only when it has both the local description
            // and the remote description.  If we call addIceCandidates too early, they are dropped.
            if (mPeerConnection == null || mTerminated || !mInitialized) {

                return;
            }

            mIsSettingRemoteAnswerPending = false;

            iceCandidates = mIceRemoteCandidates;
            if (iceCandidates == null) {

                return;
            }
            mIceRemoteCandidates = null;
        }

        // Setup to use the ICE candidates that we have received and put on hold.
        for (TransportCandidate[] iceCandidate : iceCandidates) {
            addIceCandidateInternal(iceCandidate);
        }
    }

    private void onSetRemoteDescriptionFailureInternal(@NonNull String error) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSetRemoteDescriptionFailureInternal: id=" + mId + " error=" + error);
        }

        mTwinlifeImpl.assertion(PeerConnectionAssertPoint.SET_REMOTE_FAILURE, AssertPoint.createPeerConnectionId(mId));
        mIsSettingRemoteAnswerPending = false;

        terminatePeerConnectionInternal(TerminateReason.GENERAL_ERROR, true);
    }

    @Override
    public void onRenegotiationNeeded() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRenegotiationNeededInternal id=" + mId);
        }

        // Check that we are allowed to make the renegotiation and update the counter to track the request.
        int updatedCounter = mRenegotiationNeeded.addAndGet(1);
        if (updatedCounter > 1) {
            return;
        }

        // We can handle this renegotiation immediately but before we must check and update the pending counter
        // as it will be used to decrement the renegotationNeeded when we have sent our session-update.
        if (mRenegotiationPending.compareAndSet(0, updatedCounter)) {
            doRenegotiationInternal();
        }
    }

    private void doRenegotiationInternal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRenegotiationNeededInternal id=" + mId);
        }

        synchronized (this) {
            if (mTerminated || mPeerConnection == null) {

                return;
            }
        }

        mPeerConnection.setLocalDescription(new SetLocalDescriptionObserver() {

            @Override
            public void onSetSuccess() {
                onSetLocalDescription(null);
            }
        });
    }

    @Override
    public void onStateChange() {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "onStateChange id=" + mId);
        }

        final DataChannel.State state;
        synchronized (this) {
            if (mTerminated || mInDataChannel == null) {

                return;
            }

            state = mInDataChannel.state();
            if (state == mDataChannelState) {

                return;
            }

            mDataChannelState = state;
            if (mDataChannelObserver == null) {

                return;
            }
        }

        if (state == DataChannel.State.OPEN) {
            mDataChannelObserver.onDataChannelOpen(mId, mInDataChannelExtension, mLeadingPadding);

        } else if (state == DataChannel.State.CLOSED) {
            mDataChannelObserver.onDataChannelClosed(mId);
        }
    }

    @Override
    public void onBufferedAmountChange(long previousAmount) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBufferedAmountChange:id=" + mId + " previousAmount=" + previousAmount);
        }
    }

    @Override
    public void onMessage(@NonNull Buffer buffer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onMessage: id=" + mId + " buffer=" + buffer);
        }

        if (!mLeadingPadding) {

            mStatCounters[StatType.IQ_RECEIVE_COUNT.ordinal()]++;
            if (mDataChannelObserver != null) {
                mDataChannelObserver.onDataChannelMessage(mId, buffer.data, false);
            }
            return;
        }

        if (buffer.data.capacity() < 1) {
            return;
        }

        byte leadingByte = buffer.data.get();
        byte opcode = (byte) (leadingByte & 0xf);
        byte flags = (byte) ((leadingByte >> 4) & 0xf);
        if (opcode == OP_BINARY) {
            if (flags == FLAG_FIN) {
                mStatCounters[StatType.IQ_RECEIVE_COUNT.ordinal()]++;
                if (mDataChannelObserver != null) {
                    mDataChannelObserver.onDataChannelMessage(mId, buffer.data, true);
                }
            } else {
                mOutDataFrames.clear();
                byte[] frame = new byte[buffer.data.capacity() - 1];
                buffer.data.get(frame, 0, frame.length);
                mOutDataFrames.add(frame);
            }
        } else if (opcode == OP_CONTINUATION) {
            byte[] frame = new byte[buffer.data.capacity() - 1];
            buffer.data.get(frame, 0, frame.length);
            mOutDataFrames.add(frame);

            if (flags == FLAG_FIN) {
                int length = 0;
                for (byte[] lFrame : mOutDataFrames) {
                    length += lFrame.length;
                }
                byte[] bytes = new byte[length];
                int start = 0;
                for (byte[] lFrame : mOutDataFrames) {
                    System.arraycopy(lFrame, 0, bytes, start, lFrame.length);
                    start += lFrame.length;
                }
                mOutDataFrames.clear();
                mStatCounters[StatType.IQ_RECEIVE_COUNT.ordinal()]++;
                if (mDataChannelObserver != null) {
                    mDataChannelObserver.onDataChannelMessage(mId, ByteBuffer.wrap(bytes), true);
                }
            }
        }
    }

    private void disposeInternal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "disposeInternal id=" + mId);
        }

        mStopTimestamp = SystemClock.elapsedRealtime();
        if (mStartTimestamp == 0) {
            mStartTimestamp = mStopTimestamp;
        }
        if (mRestartIce != null) {
            mRestartIce.cancel(false);
            mRestartIce = null;
        }
        if (mFlushCandidates != null) {
            mFlushCandidates.cancel(false);
            mFlushCandidates = null;
        }

        mAudioTrack = null;

        if (mInDataChannel != null) {
            mInDataChannel.unregisterObserver();
            mInDataChannel.dispose();
            mInDataChannel = null;
        }
        if (mOutDataChannel != null) {
            mOutDataChannel.unregisterObserver();
            mOutDataChannel.dispose();
            mOutDataChannel = null;
        }

        if (mPeerConnection != null) {
            mPeerConnection.dispose();
            mPeerConnection = null;
            if (mPeerConnectionFactory != null) {
                mPeerConnectionFactory.decrementUseCounter();
            }
        }

        if (mKeyPair != null) {
            mKeyPair.dispose();
            mKeyPair = null;
        }

        if (mAudioSource != null) {
            mAudioSource.dispose();
            mAudioSource = null;
        }

        if (mVideoTrack != null) {
            mPeerConnectionServiceImpl.releaseVideoTrack(mVideoTrack);
            mVideoTrack = null;
        }

        final ManagementService managementService = mTwinlifeImpl.getManagementService();
        if (managementService != null) {
            final Map<String, String> attributes = new HashMap<>();
            attributes.put(PEER_CONNECTION_ID, mId.toString());
            attributes.put(STATS_REPORT, statsReport());
            attributes.put(ORIGIN, mInitiator ? "outbound" : "inbound");
            attributes.put(CONNECT_REPORT, connectReport());
            if (mAudioSourceOn) {
                attributes.put(AUDIO_REPORT, audioReport());
            }
            if (mVideoSourceOn) {
                attributes.put(VIDEO_REPORT, videoReport());
            }

            // Report iq statistics if we were connected and it was a data channel.
            if (mConnectedTimestamp > 0 && mOffer.data) {
                attributes.put(IQ_REPORT, iqReport());
            }
            managementService.logEvent(EVENT_ID_PEER_CONNECTION, attributes, true);
        }

        mPeerConnectionServiceImpl.dispose(mId);
    }

    @NonNull
    private static SessionDescription updateCodecs(@NonNull SessionDescription sessionDescription) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateCodecs: sessionDescription=" + sessionDescription);
        }

        final String sdp = Sdp.filterCodecs(sessionDescription.description);
        if (sdp.equals(sessionDescription.description)) {
            return sessionDescription;
        } else {
            return new SessionDescription(sessionDescription.type, sdp);
        }
    }

    private void getStatsAndDispose() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getStatsAndDispose");
        }

        if (mPeerConnection == null) {
            // Now we can schedule sending the P2P stats and connection dispose.
            mPeerConnectionExecutor.execute(this::disposeInternal);
            return;
        }

        mPeerConnection.getStats(PeerConnectionImpl.this::onStatsDelivered);
    }

    private void onStatsDelivered(RTCStatsReport statsReport) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onStatsDelivered statsReport=" + statsReport);
        }

        for (RTCStats stat : statsReport.getStatsMap().values()) {
            mStats.put(stat.getId(), stat);
        }

        // Now we can schedule sending the P2P stats and connection dispose.
        mPeerConnectionExecutor.execute(this::disposeInternal);
    }

    private String connectReport() {
        if (DEBUG) {
            Log.d(LOG_TAG, "connectReport");
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(CONNECT_REPORT_VERSION);

        // Time to establish connection.
        stringBuilder.append("::connect:");
        if (mConnectedTimestamp != 0 && mAcceptTimestamp != 0) {
            stringBuilder.append(mConnectedTimestamp - mAcceptTimestamp);
        } else if (mConnectedTimestamp != 0) {
            stringBuilder.append(mConnectedTimestamp - mStartTimestamp);
        } else {
            stringBuilder.append(mStopTimestamp - mStartTimestamp);
        }

        stringBuilder.append("::accept:");
        if (mAcceptTimestamp != 0) {
            stringBuilder.append(mAcceptTimestamp - mStartTimestamp);
        } else {
            stringBuilder.append("0");
        }
        stringBuilder.append("::iceRemote:");
        stringBuilder.append(mRemoteIceCandidatesCount);
        stringBuilder.append("::iceLocal:");
        stringBuilder.append(mLocalIceCandidatesCount);
        stringBuilder.append(":");
        if (mSelectedCandidateStats != null) {
            Map<String, Object> members = mSelectedCandidateStats.getMembers();
            stringBuilder.append(":rtt:");
            stringBuilder.append(members.get("totalRoundTripTime"));
            stringBuilder.append(":");
            stringBuilder.append(members.get("currentRoundTripTime"));
            stringBuilder.append(":");
            stringBuilder.append(members.get("requestsReceived"));
            stringBuilder.append(":");
            stringBuilder.append(members.get("requestsSent"));
            stringBuilder.append(":");
            stringBuilder.append(members.get("responsesReceived"));
            stringBuilder.append(":");
            stringBuilder.append(members.get("responsesSent"));
            stringBuilder.append(":");
            stringBuilder.append(members.get("consentRequestsSent"));
            stringBuilder.append(":");
            stringBuilder.append(members.get("packetsDiscardedOnSend"));
            stringBuilder.append(":");
            stringBuilder.append(members.get("bytesDiscardedOnSend"));
            Object localCandidateId = members.get("localCandidateId");
            if (localCandidateId instanceof String) {
                RTCStats localCandiateStats = mStats.get((String)localCandidateId);
                if (localCandiateStats != null) {
                    String usedTurnConfig = (String) localCandiateStats.getMembers().get("url");
                    // Note: URL is not available if this is a host <-> host connection and we are on the same network.
                    if (usedTurnConfig != null) {
                        stringBuilder.append(":");
                        stringBuilder.append(usedTurnConfig);
                        // Append the IPv4 address of the used TURN server.
                        // (useful if the server gives different IPv4 addresses
                        // for a same TURN server name).
                        List<PeerConnection.ServerAddr> hostnames = mPeerConnectionServiceImpl.getHostnames();
                        if (hostnames != null) {
                            String[] items = usedTurnConfig.split(":");
                            if (items.length >= 2) {
                                for (PeerConnection.ServerAddr host : hostnames) {
                                    if (host.hostname.equals(items[1])) {
                                        stringBuilder.append(":");
                                        stringBuilder.append(host.ipv4);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return stringBuilder.toString();
    }

    private String audioReport() {
        if (DEBUG) {
            Log.d(LOG_TAG, "audioReport");
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(AUDIO_REPORT_VERSION);
        stringBuilder.append(mPeerConnectionServiceImpl.getAudioConfiguration());
        StringBuilder audioLocal = null;
        StringBuilder audioRemote = null;
        for (RTCStats stats : mTrackStats) {
            Map<String, Object> members = stats.getMembers();
            Object object = members.get("kind");
            if ("audio".equals(object)) {

                Object totalSamplesReceived = members.get("totalSamplesReceived");
                if (totalSamplesReceived != null) {
                    // See https://www.w3.org/TR/webrtc-stats/#inboundrtpstats-dict*
                    Object totalSamplesDuration = members.get("totalSamplesDuration");
                    Object totalAudioEnergy = members.get("totalAudioEnergy");
                    Object jitterBufferDelay = members.get("jitterBufferDelay");
                    Object audioLevel = members.get("audioLevel");
                    Object concealedSamples = members.get("concealedSamples");
                    Object concealmentEvents = members.get("concealmentEvents");
                    Object silentConcealedSamples = members.get("silentConcealedSamples");
                    audioRemote = new StringBuilder();
                    if (jitterBufferDelay instanceof Double && totalSamplesReceived instanceof BigInteger) {
                        BigInteger n = (BigInteger) totalSamplesReceived;

                        // jitterBufferDelay = sum(jitter for each sample)
                        // Audio jitter in seconds = jitterBufferDelay / totalSamplesReceived
                        double audioJitter = (double) jitterBufferDelay / n.doubleValue();
                        audioRemote.append(String.format(Locale.US, "%.3f", audioJitter));
                    } else {
                        audioRemote.append("0.0");
                    }
                    audioRemote.append(":");
                    if (totalAudioEnergy instanceof Double) {
                        audioRemote.append(String.format(Locale.US, "%.3f", totalAudioEnergy));
                    } else {
                        audioRemote.append("0");
                    }
                    audioRemote.append(":");
                    if (totalSamplesDuration instanceof Double) {
                        audioRemote.append(String.format(Locale.US, "%.3f", totalSamplesDuration));
                    } else {
                        audioRemote.append("0");
                    }
                    audioRemote.append(":");
                    if (audioLevel instanceof Double) {
                        audioRemote.append(String.format(Locale.US, "%.3f", audioLevel));
                    } else {
                        audioRemote.append("0");
                    }
                    audioRemote.append(":");
                    if (concealmentEvents instanceof Long) {
                        audioRemote.append(concealmentEvents);
                    } else {
                        audioRemote.append("0");
                    }
                    audioRemote.append(":");
                    if (concealedSamples instanceof Long) {
                        audioRemote.append(concealedSamples);
                    } else {
                        audioRemote.append("0");
                    }
                    audioRemote.append(":");
                    if (silentConcealedSamples instanceof Long) {
                        audioRemote.append(silentConcealedSamples);
                    } else {
                        audioRemote.append("0");
                    }

                } else {
                    // WebRTC 79 introduced the mediaSourceId and we have to look at that object to retrieve the local info.
                    // See https://www.w3.org/TR/webrtc-stats/#outboundrtpstats-dict*
                    Object mediaSource = members.get("mediaSourceId");
                    if (mediaSource instanceof String) {
                        RTCStats mediaStats = mStats.get(mediaSource);
                        if (mediaStats != null) {
                            members = mediaStats.getMembers();
                            Object totalSamplesDuration = members.get("totalSamplesDuration");
                            Object totalAudioEnergy = members.get("totalAudioEnergy");

                            audioLocal = new StringBuilder();
                            if (totalAudioEnergy instanceof Double) {
                                audioLocal.append(String.format(Locale.US, "%.3f", totalAudioEnergy));
                            } else {
                                audioLocal.append("0");
                            }
                            audioLocal.append(":");
                            if (totalSamplesDuration instanceof Double) {
                                audioLocal.append(String.format(Locale.US, "%.3f", totalSamplesDuration));
                            } else {
                                audioLocal.append("0");
                            }
                        }
                    }
                }
            }
        }
        if (audioLocal != null) {
            stringBuilder.append(":audio-send:");
            stringBuilder.append(audioLocal);
        }
        if (audioRemote != null) {
            stringBuilder.append(":audio-recv:");
            stringBuilder.append(audioRemote);
        }
        return stringBuilder.toString();
    }

    private String videoReport() {
        if (DEBUG) {
            Log.d(LOG_TAG, "videoReport");
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(VIDEO_REPORT_VERSION);

        StringBuilder videoLocal = null;
        StringBuilder videoRemote = null;
        for (RTCStats stats : mTrackStats) {
            Map<String, Object> members = stats.getMembers();
            Object object = members.get("kind");

            if ("video".equals(object)) {
                Object frameWidth = members.get("frameWidth");
                Object frameHeight = members.get("frameHeight");
                Object framesReceived = members.get("framesReceived");
                if (framesReceived != null) {
                    Object framesDecoded = members.get("framesDecoded");
                    Object framesDropped = members.get("framesDropped");
                    Object keyFramesDecoded = members.get("keyFramesDecoded");
                    Object freezeCount = members.get("freezeCount");
                    Object totalFreezesDuration = members.get("totalFreezesDuration");
                    videoRemote = new StringBuilder();
                    if (frameWidth instanceof Long) {
                        videoRemote.append(frameWidth);
                    } else {
                        videoRemote.append("0");
                    }
                    videoRemote.append(":");
                    if (frameHeight instanceof Long) {
                        videoRemote.append(frameHeight);
                    } else {
                        videoRemote.append("0");
                    }
                    videoRemote.append(":");
                    if (framesReceived instanceof Long) {
                        videoRemote.append(framesReceived);
                    } else {
                        videoRemote.append("0");
                    }
                    videoRemote.append(":");
                    if (framesDecoded instanceof Long) {
                        videoRemote.append(framesDecoded);
                    } else {
                        videoRemote.append("0");
                    }
                    videoRemote.append(":");
                    if (framesDropped instanceof Long) {
                        videoRemote.append(framesDropped);
                    } else {
                        videoRemote.append("0");
                    }
                    videoRemote.append(":");
                    if (keyFramesDecoded instanceof Long) {
                        videoRemote.append(keyFramesDecoded);
                    } else {
                        videoRemote.append("0");
                    }
                    videoRemote.append(":");
                    if (freezeCount instanceof Long) {
                        videoRemote.append(freezeCount);
                    } else {
                        videoRemote.append("0");
                    }
                    videoRemote.append(":");
                    if (totalFreezesDuration instanceof Double) {
                        videoRemote.append(totalFreezesDuration);
                    } else {
                        videoRemote.append("0");
                    }
                } else {
                    Object framesSent = members.get("framesSent");
                    videoLocal = new StringBuilder();
                    if (frameWidth instanceof Long) {
                        videoLocal.append(frameWidth);
                    } else {
                        videoLocal.append("0");
                    }
                    videoLocal.append(":");
                    if (frameHeight instanceof Long) {
                        videoLocal.append(frameHeight);
                    } else {
                        videoLocal.append("0");
                    }
                    videoLocal.append(":");
                    if (framesSent instanceof Long) {
                        videoLocal.append(framesSent);
                    } else {
                        videoLocal.append("0");
                    }
                }
            }
        }
        if (videoLocal != null) {
            stringBuilder.append(":video-send:");
            stringBuilder.append(videoLocal);
        }
        if (videoRemote != null) {
            stringBuilder.append(":video-recv:");
            stringBuilder.append(videoRemote);
        }
        return stringBuilder.toString();
    }

    private String iqReport() {
        if (DEBUG) {
            Log.d(LOG_TAG, "iqReport");
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(IQ_REPORT_VERSION);
        stringBuilder.append(":set");
        for (StatType set : SET_STAT_LIST) {
            stringBuilder.append(":");
            stringBuilder.append(mStatCounters[set.ordinal()]);
        }
        stringBuilder.append(":result");
        for (StatType result : RESULT_STAT_LIST) {
            stringBuilder.append(":");
            stringBuilder.append(mStatCounters[result.ordinal()]);
        }
        stringBuilder.append(":recv");
        for (StatType recv : RECEIVE_STAT_LIST) {
            stringBuilder.append(":");
            stringBuilder.append(mStatCounters[recv.ordinal()]);
        }
        stringBuilder.append(":sdp");
        for (StatType recv : SDP_STAT_LIST) {
            stringBuilder.append(":");
            stringBuilder.append(mStatCounters[recv.ordinal()]);
        }
        stringBuilder.append(mLeadingPadding ? ":P" : "");
        boolean hasErrors = false;
        for (StatType err : ERROR_STAT_LIST) {
            if (mStatCounters[err.ordinal()] > 0) {
                hasErrors = true;
                break;
            }
        }
        if (hasErrors) {
            stringBuilder.append(":err");
            for (StatType err : ERROR_STAT_LIST) {
                stringBuilder.append(":");
                stringBuilder.append(mStatCounters[err.ordinal()]);
            }
        }

        return stringBuilder.toString();
    }

    private String statsReport() {
        if (DEBUG) {
            Log.d(LOG_TAG, "statsReport");
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(STATS_REPORT_VERSION);

        // Duration of the call when we succeeded to connect (rounded up).
        stringBuilder.append("::duration:");
        if (mConnectedTimestamp != 0) {
            stringBuilder.append((mStopTimestamp - mConnectedTimestamp + 999) / 1000);
        } else {
            stringBuilder.append("0");
        }
        stringBuilder.append(":");

        for (RTCStats stats : mStats.values()) {
            Object object;
            // Log.e(LOG_TAG, "stat: " + stats.getType() + " " + stats.getMembers());
            switch (stats.getType()) {
                case "transport":
                    object = stats.getMembers().get("bytesSent");
                    String bytesSent = "0";
                    if (object instanceof BigInteger) {
                        bytesSent = object.toString();
                    }
                    object = stats.getMembers().get("bytesReceived");
                    String bytesReceived = "0";
                    if (object instanceof BigInteger) {
                        bytesReceived = object.toString();
                    }
                    if (!"0".equals(bytesReceived) || !"0".equals(bytesSent)) {
                        stringBuilder.append(":transport:");
                        stringBuilder.append(bytesSent);
                        stringBuilder.append(":");
                        stringBuilder.append(bytesReceived);
                        stringBuilder.append(":");
                        stringBuilder.append(getCandidates(stats.getMembers().get("selectedCandidatePairId")));
                        stringBuilder.append(":");
                    }
                    break;

                case "inbound-rtp":
                    object = stats.getMembers().get("kind");
                    if ("audio".equals(object)) {
                        String codec = getCodec(stats.getMembers().get("codecId"));
                        if (codec != null) {
                            stringBuilder.append(":inbound-rtp:");
                            stringBuilder.append(codec);
                            stringBuilder.append(":");
                            object = stats.getMembers().get("bytesReceived");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            object = stats.getMembers().get("packetsReceived");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            object = stats.getMembers().get("packetsLost");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            mTrackStats.add(stats);
                        }
                    } else if ("video".equals(object)) {
                        String codec = getCodec(stats.getMembers().get("codecId"));
                        if (codec != null) {
                            stringBuilder.append(":inbound-rtp:");
                            stringBuilder.append(codec);
                            stringBuilder.append(":");
                            object = stats.getMembers().get("bytesReceived");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            object = stats.getMembers().get("packetsReceived");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            object = stats.getMembers().get("packetsLost");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            object = stats.getMembers().get("framesDecoded");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            mTrackStats.add(stats);
                        }
                    }
                    break;

                case "outbound-rtp":
                    object = stats.getMembers().get("kind");
                    if ("audio".equals(object)) {
                        String codec = getCodec(stats.getMembers().get("codecId"));
                        if (codec != null) {
                            stringBuilder.append(":outbound-rtp:");
                            stringBuilder.append(codec);
                            stringBuilder.append(":");
                            object = stats.getMembers().get("bytesSent");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            object = stats.getMembers().get("packetsSent");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            mTrackStats.add(stats);
                        }
                    } else if ("video".equals(object)) {
                        String codec = getCodec(stats.getMembers().get("codecId"));
                        if (codec != null) {
                            stringBuilder.append(":outbound-rtp:");
                            stringBuilder.append(codec);
                            stringBuilder.append(":");
                            object = stats.getMembers().get("bytesSent");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            object = stats.getMembers().get("packetsSent");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            object = stats.getMembers().get("framesEncoded");
                            stringBuilder.append(object != null ? object.toString() : "0");
                            stringBuilder.append(":");
                            mTrackStats.add(stats);
                        }
                    }
                    break;

                case "data-channel":
                    stringBuilder.append(":data-channel:");
                    object = stats.getMembers().get("bytesSent");
                    stringBuilder.append(object != null ? object.toString() : "0");
                    stringBuilder.append(":");
                    object = stats.getMembers().get("bytesReceived");
                    stringBuilder.append(object != null ? object.toString() : "0");
                    stringBuilder.append(":");
                    object = stats.getMembers().get("messagesSent");
                    stringBuilder.append(object != null ? object.toString() : "0");
                    stringBuilder.append(":");
                    object = stats.getMembers().get("messagesReceived");
                    stringBuilder.append(object != null ? object.toString() : "0");
                    stringBuilder.append(":");
                    break;

                default:
                    break;
            }
        }

        return stringBuilder.toString();
    }

    @Nullable
    private String getCodec(@Nullable Object codecId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCodec: codecId=" + codecId);
        }

        if (codecId instanceof String) {
            RTCStats codecStats = mStats.get(codecId);
            if (codecStats != null) {
                Object mimeType = codecStats.getMembers().get("mimeType");
                Object clockRate = codecStats.getMembers().get("clockRate");

                return (mimeType != null ? mimeType.toString() : "") + ":" + (clockRate != null ? clockRate.toString() : "");
            }
        }

        return null;
    }

    @NonNull
    private String getCandidates(@Nullable Object candidatePairId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCandidates: candidatePairId=" + candidatePairId);
        }

        if (candidatePairId instanceof String) {
            String localNetworkType = null;
            String localProtocol = null;
            String localCandidateType = null;
            String remoteProtocol = null;
            String remoteCandidateType = null;
            mSelectedCandidateStats = mStats.get(candidatePairId);
            if (mSelectedCandidateStats != null) {
                Object localCandidateId = mSelectedCandidateStats.getMembers().get("localCandidateId");
                if (localCandidateId instanceof String) {
                    RTCStats localCandidateStats = mStats.get(localCandidateId);
                    if (localCandidateStats != null) {
                        Object object = localCandidateStats.getMembers().get("networkType");
                        if (object instanceof String) {
                            localNetworkType = (String) object;
                        }
                        object = localCandidateStats.getMembers().get("protocol");
                        if (object instanceof String) {
                            localProtocol = (String) object;
                        }
                        object = localCandidateStats.getMembers().get("candidateType");
                        if (object instanceof String) {
                            localCandidateType = (String) object;
                            if ("relay".equals(localCandidateType)) {
                                object = localCandidateStats.getMembers().get("relayProtocol");
                                if (object instanceof String) {
                                    localProtocol = (String) object;
                                }
                            }
                        }
                    }
                }
                Object remoteCandidateId = mSelectedCandidateStats.getMembers().get("remoteCandidateId");
                if (remoteCandidateId instanceof String) {
                    RTCStats remoteCandidateStats = mStats.get(remoteCandidateId);
                    if (remoteCandidateStats != null) {
                        Object object = remoteCandidateStats.getMembers().get("protocol");
                        if (object instanceof String) {
                            remoteProtocol = (String) object;
                        }
                        object = remoteCandidateStats.getMembers().get("candidateType");
                        if (object instanceof String) {
                            remoteCandidateType = (String) object;
                        }
                    }
                }

                return (localNetworkType != null ? localNetworkType : "-") + ":" + (localProtocol != null ? localProtocol : "-") + ":" +
                        (localCandidateType != null ? localCandidateType : "-") + ":" + (remoteProtocol != null ? remoteProtocol : "-") + ":" +
                        (remoteCandidateType != null ? remoteCandidateType : "-");
            }
        }

        return "-:-:-:-:-";
    }
}
