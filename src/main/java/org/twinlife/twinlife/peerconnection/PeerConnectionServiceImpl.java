/*
 *  Copyright (c) 2012-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Tengfei Wang (Tengfei.Wang@twinlife-systems.com)
 *   Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *   Xiaobo Xie (Xiaobo.Xie@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.peerconnection;

import android.content.Context;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.Configuration;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.Hostname;
import org.twinlife.twinlife.JobService;
import org.twinlife.twinlife.ProxyDescriptor;
import org.twinlife.twinlife.PushNotificationContent;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.OfferToReceive;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.PeerSignalingListener;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.SdpType;
import org.twinlife.twinlife.SessionKeyPair;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.TransportCandidate;
import org.twinlife.twinlife.TransportCandidateList;
import org.twinlife.twinlife.TurnServer;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.calls.PeerCallServiceImpl;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utils;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CameraVideoCapturer.CameraSwitchHandler;
import org.webrtc.CameraVideoCapturer.CameraEventsHandler;
import org.webrtc.CryptoOptions;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnection.IceServer;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpTransceiver;
import org.webrtc.RtpTransceiver.RtpTransceiverDirection;
import org.webrtc.SessionDescription;
import org.webrtc.SessionDescription.Type;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public class PeerConnectionServiceImpl extends BaseServiceImpl<PeerConnectionService.ServiceObserver> implements PeerConnectionService, CameraEventsHandler, PeerSignalingListener {
    private static final String LOG_TAG = "PeerConnectionServic...";
    private static final boolean DEBUG = false;

    //
    // Event Ids
    //

    static final String EVENT_ID_PEER_CONNECTION_SERVICE = "twinlife::peerConnectionService";
    static final String EVENT_ID_REPORT_QUALITY = "twinlife::peerConnectionService::quality";

    private static final int MAX_VIDEO_WIDTH = 640;
    private static final int MAX_VIDEO_HEIGHT = 480;
    private static final int MAX_VIDEO_FRAME_RATE = 30;
    private static final int MIN_VIDEO_FRAME_RATE = 10;

    private static class PeerConnectionThreadFactory implements ThreadFactory {

        public Thread newThread(@NonNull Runnable runnable) {

            return new Thread(runnable, "twinlife-peer-connection");
        }
    }

    private final ScheduledExecutorService mPeerConnectionExecutor;
    private final ConcurrentHashMap<UUID, PeerConnectionImpl> mPeerConnectionImpls = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, ConnectionState> mPeerStates = new ConcurrentHashMap<>();
    private final PeerConnection.RTCConfiguration mPeerConnectionConfiguration;
    private final CryptoService mCryptoService;
    private final PeerCallServiceImpl mPeerCallServiceImpl;

    private volatile Configuration mConfiguration;
    private JobService.NetworkLock mNetworkLock;

    private int mVideoConnections;
    private VideoCapturer mVideoCapturer;
    private SurfaceTextureHelper mSurfaceTextureHelper;
    private VideoSource mVideoSource;
    private VideoTrack mVideoTrack;
    private int mVideoFrameWidth;
    private int mVideoFrameHeight;
    private int mVideoFrameRate;

    @Nullable
    private String mFrontCamera;
    @Nullable
    private String mBackCamera;
    @Nullable
    private CameraEnumerator mCameraEnumerator;
    @Nullable
    private PeerConnectionFactory mDataConnectionFactory;
    @Nullable
    private PeerConnectionFactory mMediaConnectionFactory;
    @Nullable
    private DefaultVideoDecoderFactory mVideoDecoderFactory;
    @Nullable
    private DefaultVideoEncoderFactory mVideoEncoderFactory;
    @Nullable
    private EglBase mEglBase;
    @Nullable
    private EglBase.Context mEglBaseContext;
    private boolean mCanUseHWAccousticEchoCanceler = false;
    private boolean mCanUseHWNoiseSuppressor = false;

    public PeerConnectionServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {

        super(twinlifeImpl, connection);

        PeerConnectionServiceConfiguration peerConnectionServiceConfiguration = new PeerConnectionServiceConfiguration();
        peerConnectionServiceConfiguration.acceptIncomingCalls = false;
        setServiceConfiguration(peerConnectionServiceConfiguration);
        mPeerConnectionExecutor = Executors.newSingleThreadScheduledExecutor(new PeerConnectionThreadFactory());
        mCryptoService = twinlifeImpl.getCryptoService();
        mPeerCallServiceImpl = mTwinlifeImpl.getPeerCallServiceImpl();

        List<IceServer> iceServers = new ArrayList<>(0);
        mPeerConnectionConfiguration = new PeerConnection.RTCConfiguration(iceServers);
        mPeerConnectionConfiguration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        mPeerConnectionConfiguration.enableImplicitRollback = true;
        mPeerConnectionConfiguration.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;

        // Prune relay ports to drop duplicates and keep highest priority.
        mPeerConnectionConfiguration.turnPortPrunePolicy = PeerConnection.PortPrunePolicy.PRUNE_BASED_ON_PRIORITY;
        mPeerConnectionConfiguration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;

        // Disable SRTP_AES128_CM_SHA1_32 and enable SRTP_AEAD_AES_256_GCM.
        CryptoOptions.Builder cryptoBuilder = CryptoOptions.builder();
        cryptoBuilder.setEnableAes128Sha1_32CryptoCipher(false);
        cryptoBuilder.setEnableGcmCryptoSuites(true);
        cryptoBuilder.setRequireFrameEncryption(false);
        cryptoBuilder.setEnableEncryptedRtpHeaderExtensions(false);
        mPeerConnectionConfiguration.cryptoOptions = cryptoBuilder.createCryptoOptions();
    }

    //
    // Override BaseService methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof PeerConnectionServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        PeerConnectionServiceConfiguration peerServiceConfiguration = new PeerConnectionServiceConfiguration();

        if (baseServiceConfiguration.serviceOn) {
            PeerConnectionServiceConfiguration serviceConfiguration = (PeerConnectionServiceConfiguration) baseServiceConfiguration;

            peerServiceConfiguration.acceptIncomingCalls = serviceConfiguration.acceptIncomingCalls;
        }

        setServiceConfiguration(peerServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
        mTwinlifeImpl.getPeerCallServiceImpl().setSignalingListener(this);
    }

    @Override
    public void onConfigure() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConfigure");
        }

        super.onConfigure();

        mConfiguration = mTwinlifeImpl.getManagementServiceImpl().getConfiguration();
    }

    public void onTwinlifeSuspend() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeSuspend");
        }

        super.onTwinlifeSuspend();

        ArrayList<UUID> activeSessions = new ArrayList<>(mPeerConnectionImpls.keySet());
        for (UUID sessionId : activeSessions) {
            PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(sessionId);
            if (peerConnectionImpl != null) {
                peerConnectionImpl.onTwinlifeSuspend();
            }
        }
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDestroy");
        }

        mPeerConnectionExecutor.shutdownNow();
    }

    @Override
    public void onUpdateConfiguration(@NonNull Configuration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateConfiguration configuration=" + configuration);
        }

        super.onUpdateConfiguration(configuration);

        final List<IceServer> iceServers = new ArrayList<>(configuration.turnServers.length);

        // If a proxy is used for the connection to the signaling server, look for a possible STUN
        // port and configure the ICE server.  We can only do this for stun and not turn/turns.
        final ProxyDescriptor activeProxyDescriptor = mConnection.getActiveProxyDescriptor();
        if (activeProxyDescriptor != null && activeProxyDescriptor.getSTUNPort() > 0) {
            IceServer.Builder serverBuilder = IceServer.builder("stun:" + activeProxyDescriptor.getAddress() + ":" + activeProxyDescriptor.getSTUNPort());
            serverBuilder.setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_INSECURE_NO_CHECK);
            iceServers.add(serverBuilder.createIceServer());
        }
        for (TurnServer turnServer : configuration.turnServers) {
            IceServer.Builder serverBuilder = IceServer.builder(turnServer.url);
            if (turnServer.url.startsWith("turns")) {
                serverBuilder.setTlsCertPolicy(PeerConnection.TlsCertPolicy.TLS_CERT_POLICY_SECURE);
            }
            serverBuilder.setUsername(turnServer.username);
            serverBuilder.setPassword(turnServer.password);
            iceServers.add(serverBuilder.createIceServer());
        }
        mPeerConnectionConfiguration.iceServers = iceServers;

        // Convert our Hostname class to the WebRTC ServerAddr (we must not use WebRTC ServerAddr in ManagementService
        // due to the transpiler and webapp proxy)
        List<PeerConnection.ServerAddr> hostnames = new ArrayList<>(configuration.hostnames.length);
        for (Hostname hostname : configuration.hostnames) {
            hostnames.add(new PeerConnection.ServerAddr(hostname.hostname, hostname.ipv4, hostname.ipv6));
        }
        mPeerConnectionConfiguration.hostAddresses = hostnames;
    }

    //
    // Implement PeerConnectionService interface
    //

    @Override
    public void sessionPing() {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionPing");
        }

        final List<PeerConnectionImpl> activeList = new ArrayList<>(mPeerConnectionImpls.values());
        for (PeerConnectionImpl peerConnection : activeList) {
            peerConnection.sessionPing();
        }
    }

    @Override
    public boolean hasPeerConnections() {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasPeerConnections");
        }

        return !mPeerStates.isEmpty();
    }

    @Nullable
    public EglBase.Context getEGLContext() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getEGLContext");
        }

        final EglBase.Context result;
        synchronized (this) {
            if (mEglBase == null) {
                mEglBase = EglBase.create();
                mEglBaseContext = mEglBase.getEglBaseContext();
            }
            result = mEglBaseContext;

            if (mVideoDecoderFactory != null) {
                mVideoDecoderFactory.setSharedContext(result);
            }
            if (mVideoEncoderFactory != null) {
                mVideoEncoderFactory.setSharedContext(result);
            }
        }

        return result;
    }

    @NonNull
    public ErrorCode listenPeerConnection(@NonNull UUID peerConnectionId, @NonNull PeerConnectionObserver observer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listenPeerConnection: peerConnectionId=" + peerConnectionId + " observer=" + observer );
        }

        if (!isServiceReady()) {

            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }
        peerConnectionImpl.setObserver(observer);

        return ErrorCode.SUCCESS;
    }

    @Override
    public void createIncomingPeerConnection(@NonNull UUID peerConnectionId, @NonNull Offer offer,
                                             @NonNull OfferToReceive offerToReceive,
                                             @Nullable DataChannelObserver consumer,
                                             @NonNull PeerConnectionObserver observer, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createIncomingPeerConnection: peerConnectionId=" + peerConnectionId +
                    " offer=" + offer + " offerToReceive=" + offerToReceive);
        }

        if (!isServiceReady() || isShutdown()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);

        if (peerConnectionImpl == null) {
            complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        if (Logger.INFO) {
            Log.e (LOG_TAG, "create P2P-in " + Utils.toLog(peerConnectionId) + " from unknown "
                    + " to unknown spd-encryption: " + peerConnectionImpl.getSdpEncryptionStatus());
        }
        final ErrorCode errorCode = createSecuredIncomingPeerConnection(peerConnectionImpl, null, offer, offerToReceive,
                consumer, observer, complete);
        if (errorCode != ErrorCode.QUEUED) {
            peerConnectionImpl.terminatePeerConnection(TerminateReason.fromErrorCode(errorCode), true);
            complete.onGet(errorCode, null);
        }
    }

    @Override
    public void createIncomingPeerConnection(@NonNull UUID peerConnectionId,
                                             @NonNull RepositoryObject subject, @Nullable TwincodeOutbound peerTwincodeOutbound,
                                             @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                                             @Nullable DataChannelObserver consumer,
                                             @NonNull PeerConnectionObserver observer, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createIncomingPeerConnection: peerConnectionId=" + peerConnectionId
                    + " subject=" + subject + " offer=" + offer + " offerToReceive=" + offerToReceive);
        }

        if (!isServiceReady() || isShutdown()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);

        if (peerConnectionImpl == null) {
            complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        final TwincodeOutbound twincodeOutbound = subject.getTwincodeOutbound();
        final ErrorCode errorCode;
        if (twincodeOutbound == null || peerTwincodeOutbound == null) {
            errorCode = ErrorCode.BAD_REQUEST;
        } else {
            final Pair<ErrorCode, SessionKeyPair> keyPair = mCryptoService.createSession(peerConnectionId, twincodeOutbound, peerTwincodeOutbound, false);
            final SdpEncryptionStatus sdpEncryptionStatus =  peerConnectionImpl.getSdpEncryptionStatus();
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "create P2P-in ", Utils.toLog(peerConnectionId), " to ", toLog(twincodeOutbound),
                        " from ", toLog(peerTwincodeOutbound), " with ", keyPair.first, "encryption status: ", sdpEncryptionStatus);
            }
            if (twincodeOutbound.isEncrypted() && peerTwincodeOutbound.isEncrypted() && keyPair.first != ErrorCode.SUCCESS) {
                mTwinlifeImpl.assertion(PeerConnectionAssertPoint.DECRYPT_ERROR_1,
                        AssertPoint.createPeerConnectionId(peerConnectionId).put(peerTwincodeOutbound).put(keyPair.first));
                errorCode = keyPair.first;
            } else if (keyPair.second == null && sdpEncryptionStatus != SdpEncryptionStatus.NONE) {
                errorCode = twincodeOutbound.isEncrypted() ? ErrorCode.NO_PUBLIC_KEY : ErrorCode.NO_PRIVATE_KEY;
                mTwinlifeImpl.assertion(PeerConnectionAssertPoint.DECRYPT_ERROR_2,
                        AssertPoint.createPeerConnectionId(peerConnectionId)
                                .put(peerTwincodeOutbound).put(keyPair.first).put(sdpEncryptionStatus).put(errorCode));
            }else if (sdpEncryptionStatus == SdpEncryptionStatus.NONE && keyPair.second != null) {
                errorCode = ErrorCode.NOT_ENCRYPTED;
            } else {
                errorCode = createSecuredIncomingPeerConnection(peerConnectionImpl, keyPair.second, offer, offerToReceive,
                        consumer, observer, complete);
            }
        }

        // If we failed to handle the incoming P2P connection, terminate the P2P with a specific terminate reason to inform the peer.
        if (errorCode != ErrorCode.QUEUED) {
            peerConnectionImpl.terminatePeerConnection(TerminateReason.fromErrorCode(errorCode), true);
            complete.onGet(errorCode, null);
        }
    }

    @NonNull
    private ErrorCode createSecuredIncomingPeerConnection(@NonNull PeerConnectionImpl peerConnectionImpl,
                                                          @Nullable SessionKeyPair sessionKeyPair, @NonNull Offer offer,
                                                          @NonNull OfferToReceive offerToReceive,
                                                          @Nullable DataChannelObserver consumer,
                                                          @NonNull PeerConnectionObserver observer, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSecuredIncomingPeerConnection: peerConnectionImpl=" + peerConnectionImpl +
                    " offer=" + offer + " offerToReceive=" + offerToReceive);
        }

        SessionDescription sessionDescription = null;
        if (sessionKeyPair != null) {
            List<Sdp> sdpList = peerConnectionImpl.setKeyPair(sessionKeyPair);
            if (sdpList != null) {

                for (Sdp sdp : sdpList) {
                    final Pair<ErrorCode, Sdp> result = decrypt(peerConnectionImpl, sdp);
                    if (result.first != ErrorCode.SUCCESS) {
                        return result.first;
                    }

                    final String sdpDescription = result.second.getSdp();
                    if (sdpDescription == null) {
                        return ErrorCode.BAD_REQUEST;
                    }
                    if (sessionDescription == null) {
                        sessionDescription = new SessionDescription(SessionDescription.Type.OFFER, sdpDescription);
                    } else {
                        final TransportCandidate[] candidates = result.second.getCandidates();
                        if (candidates == null) {
                            return ErrorCode.BAD_REQUEST;
                        }

                        peerConnectionImpl.addIceCandidate(candidates);
                    }
                }
                if (sessionDescription == null) {
                    return ErrorCode.BAD_REQUEST;
                }
            }
        } else if (peerConnectionImpl.getSdpEncryptionStatus() != SdpEncryptionStatus.NONE) {
            return ErrorCode.NO_PUBLIC_KEY;
        }

        peerConnectionImpl.createIncomingPeerConnection(mPeerConnectionConfiguration, sessionDescription, offer, offerToReceive,
                consumer, observer, complete);
        return ErrorCode.QUEUED;
    }

    @Override
    public void createOutgoingPeerConnection(@NonNull String peerId, @NonNull Offer offer,
                                             @NonNull OfferToReceive offerToReceive, @NonNull PushNotificationContent notificationContent,
                                             @Nullable DataChannelObserver consumer,
                                             @NonNull PeerConnectionObserver observer,
                                             @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createOutgoingPeerConnection: peerId=" + peerId + " offer=" + offer + " offerToReceive=" + offerToReceive +
                    " notificationContent=" + notificationContent);
        }

        if (!isServiceReady() || isShutdown()) {

            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final UUID sessionId = UUID.randomUUID();
        if (Logger.INFO) {
            Log.e (LOG_TAG, "create P2P-out " + Utils.toLog(sessionId) + " to " + peerId);
        }
        createSecuredOutgoingPeerConnection(sessionId, null, peerId, offer, offerToReceive,
                notificationContent, consumer, observer, complete);
    }

    @NonNull
    static String toLog(@NonNull TwincodeOutbound twincodeOutbound) {

        return Utils.toLog(twincodeOutbound.getId())
                + " {" + (twincodeOutbound.isSigned() ? "signed" : "")
                + (twincodeOutbound.isTrusted() ? ", trust " + twincodeOutbound.getTrustMethod() : "")
                + (twincodeOutbound.isEncrypted() ? ", encrypted" : "") + "}";
    }

    @Override
    public void createOutgoingPeerConnection(@NonNull RepositoryObject subject, @Nullable TwincodeOutbound peerTwincodeOutbound,
                                             @NonNull Offer offer, @NonNull OfferToReceive offerToReceive, @NonNull PushNotificationContent notificationContent,
                                             @Nullable DataChannelObserver consumer,
                                             @NonNull PeerConnectionObserver observer, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createOutgoingPeerConnection: subject=" + subject + " offer=" + offer +
                    " offerToReceive=" + offerToReceive + " notificationContent=" + notificationContent);
        }

        if (!isServiceReady() || isShutdown()) {

            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final TwincodeOutbound twincodeOutbound = subject.getTwincodeOutbound();
        if (twincodeOutbound == null || peerTwincodeOutbound == null) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        final UUID sessionId = UUID.randomUUID();
        final String peerId = mTwinlifeImpl.getTwincodeOutboundService().getPeerId(peerTwincodeOutbound.getId(), twincodeOutbound.getId());
        final Pair<ErrorCode, SessionKeyPair> keyPair = mCryptoService.createSession(sessionId, twincodeOutbound, peerTwincodeOutbound, true);
        if (Logger.INFO) {
            Logger.info(LOG_TAG, "create P2P-out ", Utils.toLog(sessionId), " from ", toLog(twincodeOutbound),
                    " to ", toLog(peerTwincodeOutbound), " with ", keyPair.first,
                    (keyPair.first == ErrorCode.SUCCESS && keyPair.second != null ? " encrypted SDP" : ""));
        }
        if (twincodeOutbound.isEncrypted() && peerTwincodeOutbound.isEncrypted() && keyPair.first != ErrorCode.SUCCESS) {
            mTwinlifeImpl.assertion(PeerConnectionAssertPoint.ENCRYPT_ERROR,
                    AssertPoint.create(subject).put(peerTwincodeOutbound).put(keyPair.first));
            complete.onGet(keyPair.first, null);
            return;
        }

        createSecuredOutgoingPeerConnection(sessionId, keyPair.second, peerId,
                offer, offerToReceive, notificationContent, consumer, observer, complete);
    }

    private void createSecuredOutgoingPeerConnection(@NonNull UUID sessionId, @Nullable SessionKeyPair sessionKeyPair,
                                                     @NonNull String peerId, @NonNull Offer offer,
                                                     @NonNull OfferToReceive offerToReceive,
                                                     @NonNull PushNotificationContent notificationContent,
                                                     @Nullable DataChannelObserver consumer,
                                                     @NonNull PeerConnectionObserver observer, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createOutgoingPeerConnection: peerId=" + peerId + " offer=" + offer + " offerToReceive=" + offerToReceive +
                    " notificationContent=" + notificationContent);
        }

        final PeerConnectionImpl peerConnectionImpl;
        synchronized (this) {
            peerConnectionImpl = new PeerConnectionImpl(mPeerConnectionExecutor, this, sessionId, sessionKeyPair, peerId, offer,
                    offerToReceive, notificationContent, observer);
            mPeerConnectionImpls.put(sessionId, peerConnectionImpl);
            mPeerStates.put(sessionId, ConnectionState.INIT);
            if (mNetworkLock == null) {
                mNetworkLock = mJobService.allocateNetworkLock();
            }
        }

        peerConnectionImpl.createOutgoingPeerConnection(mPeerConnectionConfiguration, consumer, complete);
    }

    @Override
    public void initSources(@NonNull final UUID peerConnectionId, boolean audioOn, boolean videoOn) {
        if (DEBUG) {
            Log.d(LOG_TAG, "initSources: peerConnectionId=" + peerConnectionId + " audioOn=" + audioOn
                    + " videoOn=" + videoOn);
        }

        if (!isServiceReady()) {

            return;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl == null) {

            return;
        }

        peerConnectionImpl.initSources(audioOn, videoOn);
    }

    @Override
    public void terminatePeerConnection(@NonNull UUID peerConnectionId, TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "terminatePeerConnection: peerConnectionId=" + peerConnectionId + " terminateReason=" + terminateReason);
        }

        if (!isServiceReady()) {

            return;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.remove(peerConnectionId);
        if (peerConnectionImpl != null) {
            peerConnectionImpl.terminatePeerConnection(terminateReason, true);
        }
    }

    @Override
    public boolean isTerminated(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isTerminated: peerConnectionId=" + peerConnectionId);
        }

        return !mPeerConnectionImpls.containsKey(peerConnectionId);
    }

    /**
     * Whether the SDP are encrypted when they are received or sent from the signaling server.
     *
     * @param peerConnectionId the P2P connection id.
     * @return the SDP encryption status.
     */
    @Override
    @Nullable
    public SdpEncryptionStatus getSdpEncryptionStatus(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "getSdpEncryptionStatus peerConnectionId=" + peerConnectionId);
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        return peerConnectionImpl != null ? peerConnectionImpl.getSdpEncryptionStatus() : null;
    }

    @Override
    @Nullable
    public Offer getPeerOffer(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "getPeerOffer peerConnectionId=" + peerConnectionId);
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl != null) {

            return peerConnectionImpl.getPeerOffer();

        }

        return null;
    }

    @Override
    @Nullable
    public OfferToReceive getPeerOfferToReceive(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "getPeerOfferToReceive peerConnectionId=" + peerConnectionId);
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl != null) {

            return peerConnectionImpl.getPeerOfferToReceive();
        }

        return null;
    }

    @Nullable
    public String getPeerId(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "getPeerId peerConnectionId=" + peerConnectionId);
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl != null) {

            return peerConnectionImpl.getPeerId();
        }

        return null;
    }

    @Override
    public void setAudioDirection(@NonNull UUID peerConnectionId, @NonNull RtpTransceiverDirection direction) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "setAudioDirection peerConnectionId=" + peerConnectionId + " direction=" + direction);
        }

        if (!isServiceReady()) {

            return;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl == null) {

            return;
        }

        peerConnectionImpl.setAudioDirection(direction);
    }

    @Override
    public void setVideoDirection(@NonNull UUID peerConnectionId, @NonNull RtpTransceiver.RtpTransceiverDirection direction) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "setCameraMute peerConnectionId=" + peerConnectionId + " direction=" + direction);
        }

        if (!isServiceReady()) {

            return;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl == null) {

            return;
        }

        peerConnectionImpl.setVideoDirection(direction);
    }

    @Override
    public void switchCamera(boolean front, @NonNull Consumer<Boolean> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "switchCamera front: " + front);
        }

        if (!isServiceReady()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final boolean hasCamera;
        synchronized (this) {
            hasCamera = mVideoCapturer instanceof CameraVideoCapturer;
        }
        if (!hasCamera) {
            complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        mPeerConnectionExecutor.execute(() -> switchCameraInternal(front, complete));
    }

    @Override
    public boolean isZoomSupported(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isZoomSupported: peerConnectionId=" + peerConnectionId);
        }

        if (!isServiceReady()) {

            return false;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        return peerConnectionImpl != null && isZoomSupported();
    }

    @Override
    public void setZoom(int progress) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setZoom: progress=" + progress);
        }

        if (!isServiceReady()) {

            return;
        }

        final boolean hasCamera;
        synchronized (this) {
            hasCamera = mVideoCapturer instanceof CameraVideoCapturer;
        }

        if (!hasCamera) {

            return;
        }

        mPeerConnectionExecutor.execute(() -> setZoomInternal(progress));
    }

    private boolean isZoomSupported() {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "isZoomSupported");
        }

        synchronized (this) {
            if (mVideoCapturer instanceof CameraVideoCapturer) {
                final CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) mVideoCapturer;

                return cameraVideoCapturer.isZoomSupported();
            }
        }

        return false;
    }

    @Override
    public void sendMessage(@NonNull UUID peerConnectionId, @NonNull StatType statType, @NonNull byte[] bytes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendMessage: peerConnectionId=" + peerConnectionId + " statType=" + statType + " bytes=" + Arrays.toString(bytes));
        }

        if (!isServiceReady()) {

            return;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl == null) {

            return;
        }

        peerConnectionImpl.sendMessage(statType, bytes);
    }

    public void sendPacket(@NonNull UUID peerConnectionId, @NonNull StatType statType, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendPacket: peerConnectionId=" + peerConnectionId + " statType=" + statType + " iq=" + iq);
        }

        if (!isServiceReady()) {

            return;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl == null) {

            return;
        }

        peerConnectionImpl.sendPacket(statType, iq);
    }


    @Override
    public void incrementStat(@NonNull UUID peerConnectionId, @NonNull StatType statType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "incrementStat: peerConnectionId=" + peerConnectionId + " statType=" + statType);
        }

        if (!isServiceReady()) {

            return;
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl == null) {

            return;
        }

        peerConnectionImpl.incrementStat(statType);
    }

    @Override
    public void sendCallQuality(@NonNull UUID peerConnectionId, int quality) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendCallQuality: peerConnectionId=" + peerConnectionId + " quality=" + quality);
        }

        final Map<String, String> attributes = new HashMap<>();
        attributes.put("p2pSessionId", peerConnectionId.toString());
        attributes.put("quality", String.valueOf(quality));

        mTwinlifeImpl.getManagementServiceImpl().logEvent(EVENT_ID_REPORT_QUALITY, attributes, true);
    }

    @Override
    public void sendDeviceRinging(@NonNull UUID peerConnectionId) {
        PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(peerConnectionId);
        if (peerConnectionImpl != null) {
            peerConnectionImpl.sendDeviceRinging();
        }
    }

    /**
     * Called when a session-initiate IQ is received.
     *
     * @param sessionId the P2P session id.
     * @param from the target identification string.
     * @param to the source/originator identification string.
     * @param sdp the sdp content (clear text | compressed | encrypted).
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max received frame size.
     * @param maxReceivedFrameRate the max received frame rate.
     * @return SUCCESS, NO_PERMISSION, ITEM_NOT_FOUND if the session id is not known.
     */
    @Override
    @Nullable
    public ErrorCode onSessionInitiate(@NonNull UUID sessionId, @NonNull String from, @NonNull String to,
                                       @NonNull Sdp sdp, @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                                       int maxReceivedFrameSize, int maxReceivedFrameRate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSessionInitiate: sessionId=" + sessionId + " sdp=" + sdp
                    + " offer=" + offer + " offerToReceive=" + offerToReceive + " maxReceivedFrameSize=" + maxReceivedFrameSize
                    + " maxReceivedFrameRate=" + maxReceivedFrameRate);
        }

        final PeerConnectionServiceConfiguration peerConnectionServiceConfiguration = (PeerConnectionServiceConfiguration) getServiceConfiguration();
        if (!peerConnectionServiceConfiguration.acceptIncomingCalls) {

            return ErrorCode.NO_PERMISSION;
        }

        PeerConnectionImpl peerConnectionImpl;
        synchronized (this) {
            peerConnectionImpl = mPeerConnectionImpls.get(sessionId);
            if (peerConnectionImpl != null) {

                return ErrorCode.SUCCESS;
            }
            // Note: at this step, if the SDP is encrypted, we cannot decrypt it until createIncomingPeerConnection() is called.
            peerConnectionImpl = new PeerConnectionImpl(mPeerConnectionExecutor, this, sessionId,
                    from, offer, offerToReceive, sdp);
            mPeerConnectionImpls.put(sessionId, peerConnectionImpl);
            mPeerStates.put(sessionId, ConnectionState.INIT);
            if (mNetworkLock == null) {
                mNetworkLock = mJobService.allocateNetworkLock();
            }
        }

        if (offerToReceive.video) {
            setPeerConstraints(maxReceivedFrameSize, maxReceivedFrameRate);
        }

        for (PeerConnectionService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onIncomingPeerConnection(sessionId, from, offer));
        }

        return ErrorCode.SUCCESS;
    }

    /**
     * Called when a session-accept IQ is received.
     *
     * @param sessionId the P2P session id.
     * @param sdp the sdp content (clear text | compressed | encrypted).
     * @param to the target identification string.
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max received frame size.
     * @param maxReceivedFrameRate the max received frame rate.
     * @return SUCCESS, NO_PERMISSION, ITEM_NOT_FOUND if the session id is not known.
     */
    @Override
    @NonNull
    public ErrorCode onSessionAccept(@NonNull UUID sessionId, @NonNull String to, @NonNull Sdp sdp, @NonNull Offer offer,
                                     @NonNull OfferToReceive offerToReceive, int maxReceivedFrameSize, int maxReceivedFrameRate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSessionAccept: sessionId=" + sessionId + " sdp=" + sdp
                    + " offer=" + offer + " offerToReceive=" + offerToReceive + " maxReceivedFrameSize=" + maxReceivedFrameSize
                    + " maxReceivedFrameRate=" + maxReceivedFrameRate);
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(sessionId);
        if (peerConnectionImpl == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        final Pair<ErrorCode, Sdp> result = decrypt(peerConnectionImpl, sdp);
        if (result.first != ErrorCode.SUCCESS) {
            return result.first;
        }

        final String sdpDescription = result.second.getSdp();
        if (sdpDescription == null) {

            return ErrorCode.BAD_REQUEST;
        }

        if (offerToReceive.video) {
            setPeerConstraints(maxReceivedFrameSize, maxReceivedFrameRate);
        }

        peerConnectionImpl.acceptRemoteDescription(offer, new SessionDescription(SessionDescription.Type.ANSWER, sdpDescription));

        return ErrorCode.SUCCESS;
    }

    /**
     * Called when a session-update IQ is received.
     *
     * @param sessionId the P2P session id.
     * @param updateType whether this is an offer or an answer.
     * @param sdp the sdp content (clear text | compressed | encrypted).
     * @return SUCCESS or ITEM_NOT_FOUND if the session id is not known.
     */
    @Override
    @NonNull
    public ErrorCode onSessionUpdate(@NonNull UUID sessionId, @NonNull SdpType updateType, @NonNull Sdp sdp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSessionUpdate: sessionId=" + sessionId + " type=" + updateType + " sdp=" + sdp);
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(sessionId);
        if (peerConnectionImpl == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        final Pair<ErrorCode, Sdp> result = decrypt(peerConnectionImpl, sdp);
        if (result.first != ErrorCode.SUCCESS) {
            return result.first;
        }

        final String sdpDescription = result.second.getSdp();
        if (sdpDescription == null) {

            return ErrorCode.BAD_REQUEST;
        }

        final Type sdpType = (updateType == SdpType.ANSWER) ? Type.ANSWER : Type.OFFER;
        peerConnectionImpl.updateRemoteDescription(new SessionDescription(sdpType, sdpDescription));

        return ErrorCode.SUCCESS;
    }

    /**
     * Called when a transport-info IQ is received with a list of candidates.
     *
     * @param sessionId the P2P session id.
     * @param sdp the SDP with candidates.
     */
    @Override
    @NonNull
    public ErrorCode onTransportInfo(@NonNull UUID sessionId, @NonNull Sdp sdp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTransportInfo: sessionId=" + sessionId + " sdp=" + sdp);
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(sessionId);
        if (peerConnectionImpl == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        // If the SDP is encrypted, we must queue it until we know the session key pair.
        if (sdp.isEncrypted() && peerConnectionImpl.queue(sdp)) {
            return ErrorCode.SUCCESS;
        }

        final Pair<ErrorCode, Sdp> result = decrypt(peerConnectionImpl, sdp);
        if (result.first != ErrorCode.SUCCESS) {
            return result.first;
        }

        final TransportCandidate[] candidates = result.second.getCandidates();
        if (candidates == null) {

            return ErrorCode.BAD_REQUEST;
        }

        peerConnectionImpl.addIceCandidate(candidates);

        return ErrorCode.SUCCESS;
    }

    /**
     * Called when a session-terminate IQ is received for the given P2P session.
     *
     * @param sessionId the P2P session id.
     * @param reason the terminate reason.
     */
    @Override
    public void onSessionTerminate(@NonNull UUID sessionId, @NonNull TerminateReason reason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSessionTerminate: sessionId=" + sessionId + " reason=" + reason);
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(sessionId);
        if (peerConnectionImpl != null) {
            peerConnectionImpl.terminatePeerConnection(reason, false);
        }
    }

    @Override
    public void onDeviceRinging(@NonNull UUID sessionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeviceRinging: sessionId=" + sessionId);
        }

        final PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.get(sessionId);
        if (peerConnectionImpl != null) {

            for (PeerConnectionService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onDeviceRinging(sessionId));
            }
        }
    }

    void sessionInitiate(@NonNull PeerConnectionImpl peerConnection, @NonNull Sdp sdp,
                         @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                         @NonNull PushNotificationContent notificationContent,
                         @NonNull Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionInitiate: peerConnection=" + peerConnection);
        }

        final Pair<ErrorCode, Sdp> result = encrypt(peerConnection, sdp);
        if (result.first != ErrorCode.SUCCESS) {
            onComplete.onGet(result.first, null);
            return;
        }

        mPeerCallServiceImpl.sessionInitiate(peerConnection.getId(), null, peerConnection.getPeerId(), result.second,
                offer, offerToReceive, mConfiguration.maxReceivedFrameSize,
                mConfiguration.maxReceivedFrameRate, notificationContent, onComplete);
    }

    void sessionAccept(@NonNull PeerConnectionImpl peerConnection, @NonNull Sdp sdp,
                       @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                       @NonNull Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionAccept: peerConnection=" + peerConnection);
        }

        final Pair<ErrorCode, Sdp> result = encrypt(peerConnection, sdp);
        if (result.first != ErrorCode.SUCCESS) {
            onComplete.onGet(result.first, null);
            return;
        }

        mPeerCallServiceImpl.sessionAccept(peerConnection.getId(), null, peerConnection.getPeerId(), result.second, offer, offerToReceive,
                mConfiguration.maxReceivedFrameSize, mConfiguration.maxReceivedFrameRate, onComplete);
    }

    void sessionUpdate(@NonNull PeerConnectionImpl peerConnection, @NonNull Sdp sdp, @NonNull SdpType type,
                       @NonNull Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sessionUpdate: peerConnection=" + peerConnection);
        }

        final Pair<ErrorCode, Sdp> result = encrypt(peerConnection, sdp);
        if (result.first != ErrorCode.SUCCESS) {
            onComplete.onGet(result.first, null);
            return;
        }

        mPeerCallServiceImpl.sessionUpdate(peerConnection.getId(), peerConnection.getPeerId(), result.second, type, onComplete);
    }

    void transportInfo(@NonNull PeerConnectionImpl peerConnection, @NonNull TransportCandidateList candidates,
                       @NonNull Consumer<Long> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "transportInfo: peerConnection=" + peerConnection);
        }

        final long requestId = newRequestId();
        final Sdp sdp = candidates.buildSdp(requestId);

        // The SDP can be empty if all candidates are already sent in a previous SDP transport info.
        if (sdp.getLength() == 0) {
            onComplete.onGet(ErrorCode.SUCCESS, requestId);
            return;
        }

        final Pair<ErrorCode, Sdp> result = encrypt(peerConnection, sdp);
        if (result.first != ErrorCode.SUCCESS) {
            onComplete.onGet(result.first, requestId);
            return;
        }

        mPeerCallServiceImpl.transportInfo(requestId, peerConnection.getId(), peerConnection.getPeerId(), result.second, onComplete);
    }

    @Nullable
    VideoTrack createVideoTrack() {
        if (DEBUG) {
            Log.d(LOG_TAG, "createVideoTrack");
        }

        getCameraInformation();
        VideoTrack videoTrack;
        synchronized (this) {
            if (mVideoTrack != null) {
                mVideoConnections++;
                return mVideoTrack;
            }

            if (mMediaConnectionFactory == null || mEglBaseContext == null) {

                return null;
            }

            mVideoCapturer = createVideoCapturer(CameraConstraints.ANY_CAMERA);
            if (mVideoCapturer == null) {
                return null;
            }

            mVideoSource = mMediaConnectionFactory.createVideoSource(false);
            if (mVideoSource == null) {
                mVideoCapturer.dispose();
                mVideoCapturer = null;
                return null;
            }

            mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mEglBaseContext);
            if (mSurfaceTextureHelper == null) {
                mVideoCapturer.dispose();
                mVideoSource.dispose();
                mVideoCapturer = null;
                mVideoSource = null;
                return null;
            }

            mVideoFrameWidth = MAX_VIDEO_WIDTH;
            mVideoFrameHeight = MAX_VIDEO_HEIGHT;
            mVideoFrameRate = MAX_VIDEO_FRAME_RATE;

            if (mConfiguration.maxSentFrameSize < mVideoFrameWidth * mVideoFrameHeight) {
                if (mConfiguration.maxSentFrameSize < 240 * 160) {
                    // QQVGA: 160 x 120
                    mVideoFrameWidth = 160;
                    mVideoFrameHeight = 120;
                } else if (mConfiguration.maxSentFrameSize < 320 * 240) {
                    // HQVGA: 240 x 160
                    mVideoFrameWidth = 240;
                    mVideoFrameHeight = 160;
                } else {
                    // QVGA: 320 x 240
                    mVideoFrameWidth = 320;
                    mVideoFrameHeight = 240;
                }
            }
            if (mConfiguration.maxSentFrameRate < mVideoFrameRate) {
                mVideoFrameRate = Math.max(mConfiguration.maxSentFrameRate, MIN_VIDEO_FRAME_RATE);
            }

            mVideoCapturer.initialize(mSurfaceTextureHelper, mTwinlifeImpl.getContext(), mVideoSource.getCapturerObserver());
            mVideoCapturer.startCapture(mVideoFrameWidth, mVideoFrameHeight, mVideoFrameRate);

            mVideoConnections = 1;
            mVideoTrack = mMediaConnectionFactory.createVideoTrack(UUID.randomUUID().toString(), mVideoSource);
            videoTrack = mVideoTrack;
        }

        // Notify the UI that we created the local video track and it can be used for rendering.
        for (PeerConnectionService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onCreateLocalVideoTrack(videoTrack));
        }
        return videoTrack;
    }

    void releaseVideoTrack(@NonNull VideoTrack videoTrack) {
        if (DEBUG) {
            Log.d(LOG_TAG, "releaseVideoTrack: videoTrack=" + videoTrack);
        }

        VideoCapturer videoCapturer;
        SurfaceTextureHelper surfaceTextureHelper;
        VideoSource videoSource;
        synchronized (this) {
            if (videoTrack != mVideoTrack) {
                return;
            }

            mVideoConnections--;
            if (mVideoConnections > 0) {
                return;
            }

            videoCapturer = mVideoCapturer;
            surfaceTextureHelper = mSurfaceTextureHelper;
            videoSource = mVideoSource;
            mVideoCapturer = null;
            mSurfaceTextureHelper = null;
            mVideoSource = null;
            mVideoTrack = null;
        }

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException exception) {
                Log.d(LOG_TAG, this + "releaseVideoSource exception=" + exception);
            }
            videoCapturer.dispose();
        }

        videoTrack.dispose();
        videoSource.dispose();
        if (surfaceTextureHelper != null) {
            surfaceTextureHelper.dispose();
        }

        // Notify the UI that the local video track is now removed.
        for (PeerConnectionService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(serviceObserver::onRemoveLocalVideoTrack);
        }
    }

    void setPeerConstraints(int maxReceivedFrameSize, int maxReceivedFrameRate) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "setPeerConstraints: maxReceivedFrameSize=" + maxReceivedFrameSize + " maxReceivedFrameRate=" + maxReceivedFrameRate);
        }

        boolean update = false;
        if (maxReceivedFrameSize < mVideoFrameWidth * mVideoFrameHeight) {
            update = true;

            if (maxReceivedFrameSize < 240 * 160) {
                // QQVGA: 160 x 120
                mVideoFrameWidth = 160;
                mVideoFrameHeight = 120;
            } else if (maxReceivedFrameSize < 320 * 240) {
                // HQVGA: 240 x 160
                mVideoFrameWidth = 240;
                mVideoFrameHeight = 160;
            } else if (maxReceivedFrameSize < 640 * 480) {
                // QVGA: 320 x 240
                mVideoFrameWidth = 320;
                mVideoFrameHeight = 240;
            } else if (maxReceivedFrameSize < 1280 * 720) {
                // VGA: 640 x 480
                mVideoFrameWidth = 640;
                mVideoFrameHeight = 480;
            }
        }
        if (maxReceivedFrameRate < mVideoFrameRate) {
            update = true;

            mVideoFrameRate = Math.max(maxReceivedFrameRate, MIN_VIDEO_FRAME_RATE);
        }

        if (update && mVideoSource != null) {
            mVideoSource.adaptOutputFormat(mVideoFrameWidth, mVideoFrameHeight, mVideoFrameRate);
        }
    }

    void onTimeout(@NonNull PeerConnectionImpl peerConnection) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "onTimeout: peerConnection=" + peerConnection);
        }
    }

    //
    // Package Specific Methods
    //

    /**
     * The RTCPeerConnection has been released properly and we can proceed with the cleanup
     * and we release the network lock if we don't have any WebRTC connection.
     *
     * @param peerConnectionId the peer connection id.
     */
    void dispose(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dispose: peerConnectionId=" + peerConnectionId);
        }

        final PeerConnectionFactory dataConnectionFactory;
        final PeerConnectionFactory mediaConnectionFactory;
        final EglBase eglBase;
        synchronized (this) {
            mPeerStates.remove(peerConnectionId);
            if (mNetworkLock != null) {
                if (mPeerStates.isEmpty()) {
                    mNetworkLock.release();
                    mNetworkLock = null;
                } else {
                    mNetworkLock.activePeers(mPeerStates.size());
                }
            }

            // Release the peer connection factories and the EGL base when they are no longer used.
            if (mDataConnectionFactory != null && !mDataConnectionFactory.isUsed()) {
                dataConnectionFactory = mDataConnectionFactory;
                mDataConnectionFactory = null;
            } else {
                dataConnectionFactory = null;
            }
            if (mMediaConnectionFactory != null && !mMediaConnectionFactory.isUsed()) {
                mediaConnectionFactory = mMediaConnectionFactory;
                mMediaConnectionFactory = null;
                mVideoDecoderFactory = null;
                mVideoEncoderFactory = null;
            } else {
                mediaConnectionFactory = null;
            }

            // We can release the EGL context when the local video track is no longer used.
            if (mMediaConnectionFactory == null && mEglBase != null && mVideoTrack == null) {
                eglBase = mEglBase;
                mEglBase = null;
                mEglBaseContext = null;
            } else {
                eglBase = null;
            }
        }

        // Release objects outside of the lock to avoid possible issues.
        if (dataConnectionFactory != null) {
            dataConnectionFactory.dispose();
        }
        if (mediaConnectionFactory != null) {
            mediaConnectionFactory.dispose();
        }
        if (eglBase != null) {
            eglBase.release();
        }
    }

    @NonNull
    PeerConnectionFactory getPeerConnectionFactory(boolean withMedia) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPeerConnectionFactory");
        }

        if (withMedia) {
            if (mMediaConnectionFactory == null) {
                // Configure hardware & software video factories:
                // - eglContext is not required but passing null will disable texture mode (see HardwareVideoEncoderFactory warning in logs).
                // - IntelVP8 is only for Intel devices.
                // - Enable H264 high profile to allow profile-level-id=640c1f
                mVideoEncoderFactory = new DefaultVideoEncoderFactory(null, false, true);
                mVideoDecoderFactory = new DefaultVideoDecoderFactory(null);
                PeerConnectionFactory.Builder builder = PeerConnectionFactory.builder();
                builder.setVideoEncoderFactory(mVideoEncoderFactory);
                builder.setVideoDecoderFactory(mVideoDecoderFactory);
                builder.setHostnames(mPeerConnectionConfiguration.hostAddresses);
                Logging.enableLogToDebugOutput(Logging.Severity.LS_NONE);

                final Context context = mPeerCallServiceImpl.getTwinlifeImpl().getContext();
                builder.setAudioDeviceModule(JavaAudioDeviceModule.builder(context)
                        .setEnableVolumeLogger(false).createAudioDeviceModule());
                mCanUseHWAccousticEchoCanceler = JavaAudioDeviceModule.isBuiltInAcousticEchoCancelerSupported();
                mCanUseHWNoiseSuppressor = JavaAudioDeviceModule.isBuiltInNoiseSuppressorSupported();
                mMediaConnectionFactory = builder.createPeerConnectionFactory();
            }

            return mMediaConnectionFactory;
        } else {

            if (mDataConnectionFactory == null) {
                Logging.enableLogToDebugOutput(Logging.Severity.LS_NONE);

                PeerConnectionFactory.Builder builder = PeerConnectionFactory.builder();
                builder.setHostnames(mPeerConnectionConfiguration.hostAddresses);
                mDataConnectionFactory = builder.createPeerConnectionFactory();
            }
            return mDataConnectionFactory;
        }
    }

    @NonNull
    String getAudioConfiguration() {

        return (mCanUseHWAccousticEchoCanceler ? ":hard" : ":soft") + (mCanUseHWNoiseSuppressor ? ":hard" : ":soft");
    }

    @Nullable
    List<PeerConnection.ServerAddr> getHostnames() {

        return mPeerConnectionConfiguration.hostAddresses;
    }

    void onChangeConnectionState(@NonNull UUID peerConnectionId, ConnectionState state) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onChangeConnectionState: peerConnectionId=" + peerConnectionId + " state=" + state);
        }

        if (state == ConnectionState.CONNECTED) {
            synchronized (this) {
                mPeerStates.put(peerConnectionId, state);
                if (mNetworkLock != null) {
                    mNetworkLock.activePeers(mPeerStates.size());
                }
            }
        }
    }

    @Override
    public void onCameraError(@Nullable String description) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCameraError: description=" + description);
        }

        for (PeerConnectionService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onCameraError(description));
        }
    }

    @Override
    public void onCameraOpening(String cameraName) {
        if (DEBUG) {
            Log.d(LOG_TAG, "CameraEventsHandler.onCameraOpening: cameraName=" + cameraName);
        }
    }

    @Override
    public void onFirstFrameAvailable() {
        if (DEBUG) {
            Log.d(LOG_TAG, "CameraEventsHandler.onFirstFrameAvailable");
        }
    }

    @Override
    public void onCameraClosed() {
        if (DEBUG) {
            Log.d(LOG_TAG, "CameraEventsHandler.onCameraClosed");
        }
    }

    @Override
    public void onCameraFreezed(String errorDescription) {
        if (DEBUG) {
            Log.d(LOG_TAG, "CameraEventsHandler.onCameraFreezed");
        }
    }

    @Override
    public void onCameraDisconnected() {
        if (DEBUG) {
            Log.d(LOG_TAG, "CameraEventsHandler.onCameraDisconnected");
        }
    }

    void onTerminatePeerConnection(@NonNull UUID peerConnectionId, TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTerminatePeerConnection: peerConnectionId=" + peerConnectionId + " terminateReason=" + terminateReason);
        }

        synchronized (this) {
            PeerConnectionImpl peerConnectionImpl = mPeerConnectionImpls.remove(peerConnectionId);
            if (peerConnectionImpl == null) {

                return;
            }
            mPeerStates.remove(peerConnectionId);
            if (mNetworkLock != null) {
                if (mPeerConnectionImpls.isEmpty()) {
                    mNetworkLock.release();
                    mNetworkLock = null;
                } else {
                    mNetworkLock.activePeers(mPeerStates.size());
                }
            }
        }
    }

    //
    // Private methods
    //

    @NonNull
    private Pair<ErrorCode, Sdp> encrypt(@NonNull PeerConnectionImpl peerConnectionImpl, @NonNull Sdp sdp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "encrypt peerConnectionImpl=" + peerConnectionImpl + " sdp=" + sdp);
        }

        final SessionKeyPair sessionKeyPair = peerConnectionImpl.getKeyPair();
        if (sessionKeyPair == null) {
            peerConnectionImpl.incrementStat(StatType.SDP_SEND_CLEAR);
            return new Pair<>(ErrorCode.SUCCESS, sdp);
        }

        peerConnectionImpl.incrementStat(StatType.SDP_SEND_ENCRYPTED);
        return mCryptoService.encrypt(sessionKeyPair, sdp);
    }

    @NonNull
    private Pair<ErrorCode, Sdp> decrypt(@NonNull PeerConnectionImpl peerConnectionImpl, @NonNull Sdp sdp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "decrypt peerConnectionImpl=" + peerConnectionImpl + " sdp=" + sdp);
        }

        final SessionKeyPair sessionKeyPair = peerConnectionImpl.getKeyPair();
        if (sdp.isEncrypted()) {
            peerConnectionImpl.incrementStat(StatType.SDP_RECEIVE_ENCRYPTED);
            if (sessionKeyPair == null) {
                return new Pair<>(ErrorCode.NO_PRIVATE_KEY, null);
            }
            return mCryptoService.decrypt(sessionKeyPair, sdp);

        } else {
            peerConnectionImpl.incrementStat(StatType.SDP_RECEIVE_CLEAR);
            if (sessionKeyPair != null) {
                return new Pair<>(ErrorCode.NOT_ENCRYPTED, null);
            }
            return new Pair<>(ErrorCode.SUCCESS, sdp);
        }
    }

    private void getCameraInformation() {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "getCameraInformation");
        }

        CameraEnumerator cameraEnumerator;
        synchronized (this) {
            if (mCameraEnumerator != null) {

                return;
            }

            if (Camera2Enumerator.isSupported(mTwinlifeImpl.getContext())) {
                cameraEnumerator = new Camera2Enumerator(mTwinlifeImpl.getContext());
            } else {
                cameraEnumerator = new Camera1Enumerator(true);
            }
        }

        String[] deviceNames = cameraEnumerator.getDeviceNames();
        String frontCamera = null;
        String backCamera = null;
        for (String deviceName : deviceNames) {
            if (cameraEnumerator.isFrontFacing(deviceName)) {
                if (frontCamera == null) {
                    frontCamera = deviceName;
                }
            } else if (cameraEnumerator.isBackFacing(deviceName)) {
                if (backCamera == null) {
                    backCamera = deviceName;
                }
            }
        }

        synchronized (this) {
            mCameraEnumerator = cameraEnumerator;
            mFrontCamera = frontCamera;
            mBackCamera = backCamera;
        }
    }

    @SuppressWarnings("SameParameterValue")
    @Nullable
    private VideoCapturer createVideoCapturer(@NonNull CameraConstraints cameraConstraints) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "createVideoCapturer: cameraConstraints=" + cameraConstraints);
        }

        if (cameraConstraints == CameraConstraints.NO_CAMERA || mCameraEnumerator == null) {

            return null;
        }

        if (mFrontCamera != null && (cameraConstraints == CameraConstraints.ANY_CAMERA || cameraConstraints == CameraConstraints.FACING_FRONT_CAMERA)) {

            VideoCapturer videoCapturer = mCameraEnumerator.createCapturer(mFrontCamera, this);

            if (videoCapturer != null) {

                return videoCapturer;
            }
            if (cameraConstraints == CameraConstraints.FACING_FRONT_CAMERA) {

                return null;
            }
        }

        if (mBackCamera != null) {

            return mCameraEnumerator.createCapturer(mBackCamera, this);
        }

        return null;
    }

    private void switchCameraInternal(boolean front, @NonNull Consumer<Boolean> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "switchCameraInternal front: " + front);
        }

        synchronized (this) {
            final String deviceName = front ? mFrontCamera : mBackCamera;
            if (deviceName != null && mVideoCapturer instanceof CameraVideoCapturer) {
                CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) mVideoCapturer;
                cameraVideoCapturer.switchCamera(new CameraSwitchHandler() {
                    @Override
                    public void onCameraSwitchDone(boolean isFrontCamera) {
                        complete.onGet(ErrorCode.SUCCESS, isFrontCamera);
                    }

                    @Override
                    public void onCameraSwitchError(String errorDescription) {
                        complete.onGet(ErrorCode.NO_PERMISSION, null);
                    }
                }, deviceName);
            }
        }
    }

    private void setZoomInternal(int progress) {
        if (DEBUG) {
            Log.d(LOG_TAG, this + "setZoomInternal: progress=" + progress);
        }

        synchronized (this) {
            if (mVideoCapturer instanceof CameraVideoCapturer) {
                CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) mVideoCapturer;
                cameraVideoCapturer.setZoom(progress);
            }
        }
    }
}
