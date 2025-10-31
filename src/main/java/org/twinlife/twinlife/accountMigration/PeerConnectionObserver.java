/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BinaryPacketListener;
import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.OfferToReceive;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.PeerConnectionService.StatType;
import org.twinlife.twinlife.PushNotificationContent;
import org.twinlife.twinlife.PushNotificationPriority;
import org.twinlife.twinlife.PushNotificationOperation;
import org.twinlife.twinlife.PeerConnectionService.DataChannelObserver;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.conversation.ConversationAssertPoint;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.ByteBufferInputStream;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.SchemaKey;
import org.twinlife.twinlife.util.Version;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpSender;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * P2P connection management for the account migration service between two peers.
 *
 * Protocol version 2.1.0 - iOS support
 *  Date: 2024/07/09
 *    AccountSecuredConfiguration has a new schema version 4 that we must use if the peer supports 2.1.0
 *    but we should send the schema version 3 if the peer is using 2.0.0.
 *    The goal is to allow users which are sticked to Android 4.x (twinme 17.3) to be able to migrate to a newer version.
 *
 * Protocol version 2.0.0
 *  Date: 2021/12/01
 *    AccountSecuredConfiguration has a new schema version 3.
 *    Incrementing the protocol version allows to refuse the migration with an old device that only supports schema version 2.
 *    (if we do this, the account transfer will not work).
 *    The user must first upgrade the old device.
 *
 * Protocol version 1.0.0
 *  Date: 2020/11/23
 *    Android migration Twinme, Twinme+
 */
@SuppressWarnings("rawtypes")
abstract class PeerConnectionObserver extends PeerConnectionService.DefaultServiceObserver implements DataChannelObserver, PeerConnectionService.PeerConnectionObserver {
    private static final String LOG_TAG = "PeerConnectionObserver";
    private static final boolean DEBUG = false;

    private static final String VERSION_PREFIX = "AccountMigration.";
    private static final String VERSION = "2.1.0";
    private static final int MIN_PROTOCOL_VERSION = 2;
    private static final int CONNECT_TIMEOUT = 20;
    private static final int RECONNECT_TIMEOUT = 10;

    static class ObserverThreadFactory implements ThreadFactory {

        public Thread newThread(@NonNull Runnable runnable) {

            return new Thread(runnable, "twinlife-migration");
        }
    }

    @NonNull
    protected final TwinlifeImpl mTwinlifeImpl;
    @NonNull
    protected final SerializerFactory mSerializerFactory;
    @NonNull
    private final PeerConnectionService mPeerConnectionService;
    private final Map<SchemaKey, Pair<Serializer, BinaryPacketListener>> mBinaryListeners = new HashMap<>();
    private final Set<Long> mPendingRequests;
    private final String mPeerId;
    protected final ScheduledExecutorService mExecutor;
    @Nullable
    private UUID mIncomingPeerConnectionId;
    @Nullable
    private UUID mOutgoingPeerConnectionId;
    @Nullable
    private volatile UUID mPeerConnectionId;
    private volatile ScheduledFuture mOpenTimeout;
    private volatile ScheduledFuture mReconnectTimeout;
    private boolean mIsOnline;
    @Nullable
    protected Version mPeerVersion;

    PeerConnectionObserver(@NonNull TwinlifeImpl twinlifeImpl, @NonNull String peerId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "PeerConnectionObserver peerId=" + peerId);
        }

        mTwinlifeImpl = twinlifeImpl;
        mExecutor = Executors.newSingleThreadScheduledExecutor(new ObserverThreadFactory());
        mPeerConnectionService = twinlifeImpl.getPeerConnectionService();
        mSerializerFactory = twinlifeImpl.getSerializerFactoryImpl();
        mPendingRequests = new HashSet<>();
        mPeerId = peerId;
        mIsOnline = twinlifeImpl.getAccountService().isTwinlifeOnline();

        mPeerConnectionService.addServiceObserver(this);
    }

    protected void addPacketListener(@NonNull Serializer serializer, @NonNull BinaryPacketListener packetListener) {

        SchemaKey key = new SchemaKey(serializer.schemaId, serializer.schemaVersion);
        mBinaryListeners.put(key, new Pair<>(serializer, packetListener));
    }

    @NonNull
    @Override
    public PeerConnectionService.DataChannelConfiguration getConfiguration(@NonNull UUID peerConnectionId, @NonNull PeerConnectionService.SdpEncryptionStatus encryptionStatus) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfiguration: peerConnectionId=" + peerConnectionId);
        }

        return new PeerConnectionService.DataChannelConfiguration(VERSION_PREFIX + VERSION, false);
    }

    /**
     * Called when the P2P data channel is opened and validated: we can write on it.
     */
    protected abstract void onDataChannelOpen();

    /**
     * Called when the P2P data channel is closed.
     *
     * @param terminateReason the terminate reason.
     */
    protected abstract void onTerminate(@NonNull TerminateReason terminateReason);

    /**
     * Called when there is a timeout trying to open the P2P data channel.
     */
    protected abstract void onTimeout();

    boolean isConnected() {

        return mPeerConnectionId != null;
    }

    /**
     * Start the device migration process by setting up and opening the P2P connection to the peer twincode outboundid.
     *
     */
    void startOutgoingConnection() {
        if (DEBUG) {
            Log.d(LOG_TAG, "startOutgoingConnection");
        }

        long requestId = mTwinlifeImpl.newRequestId();
        Offer offer = new Offer(false, false, false, true);
        OfferToReceive offerToReceive = new OfferToReceive(false, false, true);

        PushNotificationContent notificationContent = new PushNotificationContent(PushNotificationPriority.HIGH, PushNotificationOperation.PUSH_FILE);

        synchronized (mPendingRequests) {

            if (mReconnectTimeout != null) {
                mReconnectTimeout.cancel(false);
                mReconnectTimeout = null;
            }

            if (mOpenTimeout != null) {
                mOpenTimeout.cancel(false);
                mOpenTimeout = null;
            }
            if (!mIsOnline) {
                return;
            }

            mPendingRequests.add(requestId);
            mOpenTimeout = mExecutor.schedule(this::onOpenTimeout, CONNECT_TIMEOUT, TimeUnit.SECONDS);
        }

        notificationContent.timeToLive = CONNECT_TIMEOUT * 1000;
        mPeerConnectionService.createOutgoingPeerConnection(mPeerId, offer, offerToReceive, notificationContent,
                 this, this, this::onCreateOutgoingPeerConnection);
    }

    /**
     * Start the device migration process by setting up and opening the P2P connection to the peer twincode outboundid.
     *
     */
    void startIncomingConnection(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "startIncomingConnection");
        }

        synchronized (mPendingRequests) {
            mIncomingPeerConnectionId = peerConnectionId;
        }

        Offer offer = new Offer(false, false, false, true);
        OfferToReceive offerToReceive = new OfferToReceive(false, false, true);
        mPeerConnectionService.createIncomingPeerConnection(peerConnectionId, offer, offerToReceive,
                this, this, (ErrorCode errorCode, UUID id) -> {
            if (errorCode == ErrorCode.SUCCESS) {
            }
        });
    }

    /**
     * Close the P2P connection during the shutdown step.
     */
    protected void closeConnection() {
        if (DEBUG) {
            Log.d(LOG_TAG, "closeConnection");
        }

        mExecutor.schedule(() -> {
            UUID peerConnectionId;

            synchronized (mPendingRequests) {
                peerConnectionId = mPeerConnectionId;
            }

            if (peerConnectionId != null) {
                terminatePeerConnection(peerConnectionId, TerminateReason.SUCCESS);
            }
        }, 1000, TimeUnit.MILLISECONDS);
    }

    void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        boolean isConnected;
        synchronized (mPendingRequests) {
            mIsOnline = true;
            isConnected = mPeerConnectionId != null;
        }

        if (!isConnected) {
            startOutgoingConnection();
        }
    }

    void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        synchronized (mPendingRequests) {
            mIsOnline = false;

            if (mReconnectTimeout != null) {
                mReconnectTimeout.cancel(true);
                mReconnectTimeout = null;
            }
        }
    }

    /**
     * Stop the executor.
     */
    protected void finish() {
        if (DEBUG) {
            Log.d(LOG_TAG, "finish");
        }

        UUID incomingPeerConnectionId;
        UUID outgoingPeerConnectionId;
        synchronized (mPendingRequests) {
            incomingPeerConnectionId = mIncomingPeerConnectionId;
            outgoingPeerConnectionId = mOutgoingPeerConnectionId;
            mPeerConnectionId = null;
            mIncomingPeerConnectionId = null;
            mOutgoingPeerConnectionId = null;

            if (mOpenTimeout != null) {
                mOpenTimeout.cancel(true);
                mOpenTimeout = null;
            }
            if (mReconnectTimeout != null) {
                mReconnectTimeout.cancel(true);
                mReconnectTimeout = null;
            }
        }
        mPeerConnectionService.removeServiceObserver(this);

        if (incomingPeerConnectionId != null) {
            mPeerConnectionService.terminatePeerConnection(incomingPeerConnectionId, TerminateReason.CANCEL);
        }
        if (outgoingPeerConnectionId != null) {
            mPeerConnectionService.terminatePeerConnection(outgoingPeerConnectionId, TerminateReason.CANCEL);
        }

        mExecutor.shutdownNow();
    }

    //
    // Implement PeerConnectionService interface
    //

    private void onCreateOutgoingPeerConnection(@NonNull ErrorCode errorCode, @Nullable UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateOutgoingPeerConnection errorCode=" + errorCode + " peerConnectionId=" + peerConnectionId);
        }

        if (peerConnectionId != null) {
            synchronized (mPendingRequests) {
                mOutgoingPeerConnectionId = peerConnectionId;
            }
        }
    }

    @Override
    public void onAcceptPeerConnection(@NonNull UUID peerConnectionId, @NonNull Offer offer) {

    }

    @Override
    public void onChangeConnectionState(@NonNull UUID peerConnectionId, @NonNull PeerConnectionService.ConnectionState state) {

    }

    @Override
    public void onAddLocalAudioTrack(@NonNull UUID peerConnectionId, @NonNull RtpSender sender, @NonNull AudioTrack audioTrack) {

    }

    @Override
    public void onAddRemoteMediaStreamTrack(@NonNull UUID peerConnectionId, @NonNull MediaStreamTrack mediaStream) {

    }

    @Override
    public void onRemoveRemoteTrack(@NonNull UUID peerConnectionId, @NonNull String trackId) {

    }

    @Override
    public void onPeerHoldCall(@NonNull UUID peerConnectionId) {
        //NOOP
    }

    @Override
    public void onPeerResumeCall(@NonNull UUID peerConnectionId) {
        //NOOP
    }

    @Override
    public void onTerminatePeerConnection(@NonNull UUID peerConnectionId, @NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTerminatePeerConnection peerConnectionId=" + peerConnectionId + " terminateReason=" + terminateReason);
        }

        boolean isConnected;
        synchronized (mPendingRequests) {
            boolean initiator = mOutgoingPeerConnectionId != null;
            // Check for the incoming or outgoing P2P connection.
            if (peerConnectionId.equals(mIncomingPeerConnectionId)) {
                mIncomingPeerConnectionId = null;

            } else if (peerConnectionId.equals(mOutgoingPeerConnectionId)) {
                mOutgoingPeerConnectionId = null;

            } else {
                return;
            }

            // If this is the active P2P connection, invalidate it.
            if (peerConnectionId.equals(mPeerConnectionId)) {
                mPeerConnectionId = null;
            }

            if (mOpenTimeout != null) {
                mOpenTimeout.cancel(false);
                mOpenTimeout = null;
            }

            // Schedule an automatic reconnection in 10 seconds.
            if (mReconnectTimeout != null) {
                mReconnectTimeout.cancel(false);
                mReconnectTimeout = null;
            }

            // If we are disconnected, still online and we're the initiator, setup the reconnection timer.
            isConnected = mPeerConnectionId != null;
            if (mIsOnline && !isConnected && initiator) {
                mReconnectTimeout = mExecutor.schedule(this::startOutgoingConnection, RECONNECT_TIMEOUT, TimeUnit.SECONDS);
            }
        }

        if (!isConnected) {
            onTerminate(terminateReason);
        }
    }

    @Override
    public void onDataChannelOpen(@NonNull UUID peerConnectionId, @NonNull String peerVersion, boolean leadingPadding) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDataChannelOpen peerConnectionId=" + peerConnectionId + " peerVersion=" + peerVersion);
        }

        if (!peerVersion.startsWith(VERSION_PREFIX)) {

            return;
        }

        boolean isValid = setPeerVersion(peerVersion.substring(VERSION_PREFIX.length()));

        synchronized (mPendingRequests) {
            // Check for the incoming or outgoing P2P connection and remember which connection we are connected.
            if (peerConnectionId.equals(mIncomingPeerConnectionId)) {
                if (isValid) {
                    mPeerConnectionId = mIncomingPeerConnectionId;
                }

            } else if (peerConnectionId.equals(mOutgoingPeerConnectionId)) {
                if (isValid) {
                    mPeerConnectionId = mOutgoingPeerConnectionId;
                }

            } else {
                return;
            }

            if (mOpenTimeout != null) {
                mOpenTimeout.cancel(false);
                mOpenTimeout = null;
            }
        }

        if (!isValid) {
            terminatePeerConnection(peerConnectionId, TerminateReason.NOT_AUTHORIZED);
            return;
        }

        // Enter in first state once the data channel is verified and connected.

        onDataChannelOpen();
    }

    @Override
    public void onDataChannelClosed(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDataChannelClosed peerConnectionId=" + peerConnectionId);
        }

        synchronized (mPendingRequests) {
            if (!peerConnectionId.equals(mIncomingPeerConnectionId) && !peerConnectionId.equals(mOutgoingPeerConnectionId)) {
                return;
            }
        }

        terminatePeerConnection(peerConnectionId, TerminateReason.CONNECTIVITY_ERROR);
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @Override
    public void onDataChannelMessage(@NonNull UUID peerConnectionId, @NonNull ByteBuffer buffer, boolean leadingPadding) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDataChannelMessage peerConnectionId=" + peerConnectionId + " bytes=" + buffer);
        }

        synchronized (mPendingRequests) {
            if (!peerConnectionId.equals(mIncomingPeerConnectionId) && !peerConnectionId.equals(mOutgoingPeerConnectionId)) {
                return;
            }
        }

        UUID schemaId = null;
        int schemaVersion = 0;
        try {
            ByteBufferInputStream inputStream = new ByteBufferInputStream(buffer);
            BinaryDecoder binaryDecoder;
            if (leadingPadding) {
                binaryDecoder = new BinaryDecoder(inputStream);
            } else {
                binaryDecoder = new BinaryCompactDecoder(inputStream);
            }
            schemaId = binaryDecoder.readUUID();
            schemaVersion = binaryDecoder.readInt();
            SchemaKey key = new SchemaKey(schemaId, schemaVersion);
            Pair<Serializer, BinaryPacketListener> listener = mBinaryListeners.get(key);
            if (listener != null) {
                BinaryPacketIQ iq = (BinaryPacketIQ) listener.first.deserialize(mSerializerFactory, binaryDecoder);
                listener.second.processPacket(iq);

            } else {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Schema key ", key, " not found");
                }
            }

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Internal error ", exception);
            }

            mTwinlifeImpl.exception(ConversationAssertPoint.PROCESS_UPDATE_DESCRIPTOR_IQ, exception,
                    AssertPoint.createPeerConnectionId(peerConnectionId)
                            .putSchemaId(schemaId)
                            .putSchemaVersion(schemaVersion));

            // Something very bad happened: terminate the P2P connection because we don't want to proceed
            // with a broken account migration.
            terminatePeerConnection(peerConnectionId, TerminateReason.GENERAL_ERROR);
        }
    }

    void sendPeerPacket(@NonNull StatType statType, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendPeerPacket statType=" + statType + " iq=" + iq);
        }

        final UUID peerConnectionId = mPeerConnectionId;
        if (peerConnectionId == null) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot send ", iq, " no peerConnection");
            }
            return;
        }

        mPeerConnectionService.sendPacket(peerConnectionId, statType, iq);
    }

    private void onOpenTimeout() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOpenTimeout");
        }

        UUID peerConnectionId;
        boolean isConnected;
        synchronized (mPendingRequests) {
            mOpenTimeout = null;

            if (mOutgoingPeerConnectionId != null) {
                peerConnectionId = mOutgoingPeerConnectionId;

            } else if (mIncomingPeerConnectionId != null) {
                peerConnectionId = mIncomingPeerConnectionId;

            } else {
                peerConnectionId = null;
            }

            // Schedule an automatic reconnection in 10 seconds.
            if (mReconnectTimeout != null) {
                mReconnectTimeout.cancel(true);
                mReconnectTimeout = null;
            }

            // If we are disconnected and still online, setup the reconnection timer.
            isConnected = mPeerConnectionId != null;
            if (mIsOnline && !isConnected) {
                mReconnectTimeout = mExecutor.schedule(this::startOutgoingConnection, RECONNECT_TIMEOUT, TimeUnit.SECONDS);
            }
        }

        if (peerConnectionId != null) {
            terminatePeerConnection(peerConnectionId, TerminateReason.TIMEOUT);
        }

        if (!isConnected) {
            onTimeout();
        }
    }

    private boolean setPeerVersion(@Nullable String peerVersion) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setPeerVersion peerVersion=" + peerVersion);
        }

        if (peerVersion != null) {
            mPeerVersion = new Version(peerVersion);
            if (mPeerVersion.major < MIN_PROTOCOL_VERSION) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Protocol version " + peerVersion + " is not supported");
                }
                return false;
            }
            return true;
        }
        return false;
    }

    private void terminatePeerConnection(@NonNull UUID peerConnectionId, @NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "terminatePeerConnection peerConnectionId=" + peerConnectionId + " terminateReason=" + terminateReason);
        }

        mPeerConnectionService.terminatePeerConnection(peerConnectionId, terminateReason);

        onTerminatePeerConnection(peerConnectionId, terminateReason);
    }

    //
    // Override Object methods
    //

    @SuppressWarnings("StringBufferReplaceableByString")
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("PeerConnectionObserver\n");
        stringBuilder.append(" state=");

        return stringBuilder.toString();
    }
}
