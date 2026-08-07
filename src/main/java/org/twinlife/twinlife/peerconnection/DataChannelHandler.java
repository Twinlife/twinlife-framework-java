/*
 *  Copyright (c) 2022-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Fabrice Trescartes (Fabrice.Trescartes@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.peerconnection;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.BinaryPacketListener;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.SdpType;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.calls.PeerCallServiceImpl;
import org.twinlife.twinlife.calls.SessionUpdateIQ;
import org.twinlife.twinlife.calls.TransportInfoIQ;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.PeerConnectionService.StatType;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.ByteBufferInputStream;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.SchemaKey;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Small data channel handler to handle sending/receiving some IQs within a WebRTC data channel.
 * This data channel handler supports exchanging WebRTC session update and transport info
 * when the data channel is opened and operational.  For these two IQs we expect an acknowledge
 * from the peer.  If the acknowledge is not received within a short timeframe, the WebRTC
 * SDPs are sent again but through the signaling server.
 *
 */
public abstract class DataChannelHandler implements PeerConnectionService.DataChannelObserver{
    private static final String LOG_TAG = "DataChannelHandler";
    private static final boolean DEBUG = false;

    @NonNull
    protected final PeerConnectionServiceImpl mPeerConnectionService;
    @NonNull
    protected final SerializerFactory mSerializerFactory;
    protected final Map<SchemaKey, Pair<Serializer, BinaryPacketListener>> mBinaryListeners = new HashMap<>();

    @Nullable
    protected UUID mPeerConnectionId;

    public static boolean processSdpPacket(@NonNull PeerConnectionServiceImpl peerConnectionService,
                                           @NonNull SchemaKey key, @NonNull UUID peerConnectionId,
                                           @NonNull BinaryDecoder binaryDecoder) throws SerializerException {

        if (key.isSerializer(PeerCallServiceImpl.IQ_SESSION_UPDATE_SERIALIZER)) {
            final BinaryPacketIQ iq = (BinaryPacketIQ) PeerCallServiceImpl.IQ_SESSION_UPDATE_SERIALIZER.deserialize(peerConnectionService.getSerializerFactoryImpl(), binaryDecoder);
            peerConnectionService.incrementStat(peerConnectionId, StatType.IQ_RECEIVE_SET_COUNT);
            onSessionUpdateIQ(iq, peerConnectionService);
            return true;

        } else if (key.isSerializer(PeerCallServiceImpl.IQ_TRANSPORT_INFO_SERIALIZER)) {
            final BinaryPacketIQ iq = (BinaryPacketIQ) PeerCallServiceImpl.IQ_TRANSPORT_INFO_SERIALIZER.deserialize(peerConnectionService.getSerializerFactoryImpl(), binaryDecoder);
            peerConnectionService.incrementStat(peerConnectionId, StatType.IQ_RECEIVE_SET_COUNT);
            onTransportInfoIQ(iq, peerConnectionService);
            return true;

        } else if (key.isSerializer(PeerCallServiceImpl.IQ_ON_SESSION_UPDATE_SERIALIZER)) {
            final BinaryPacketIQ iq = (BinaryPacketIQ) PeerCallServiceImpl.IQ_ON_SESSION_UPDATE_SERIALIZER.deserialize(peerConnectionService.getSerializerFactoryImpl(), binaryDecoder);
            peerConnectionService.incrementStat(peerConnectionId, StatType.IQ_RECEIVE_SET_COUNT);
            onAckSDPIQ(iq, peerConnectionService, peerConnectionId);
            return true;

        } else if (key.isSerializer(PeerCallServiceImpl.IQ_ON_TRANSPORT_INFO_SERIALIZER)) {
            final BinaryPacketIQ iq = (BinaryPacketIQ) PeerCallServiceImpl.IQ_ON_TRANSPORT_INFO_SERIALIZER.deserialize(peerConnectionService.getSerializerFactoryImpl(), binaryDecoder);
            peerConnectionService.incrementStat(peerConnectionId, StatType.IQ_RECEIVE_SET_COUNT);
            onAckSDPIQ(iq, peerConnectionService, peerConnectionId);
            return true;

        } else {
            return false;
        }
    }

    public DataChannelHandler(@NonNull PeerConnectionService peerConnectionService,
                              @NonNull SerializerFactory serializerFactory) {

        mPeerConnectionService = (PeerConnectionServiceImpl) peerConnectionService;
        mSerializerFactory = serializerFactory;

        // Set IQ listeners to handle WebRTC SDP exchanges through data-channel when we are connected.
        addPacketListener(PeerCallServiceImpl.IQ_SESSION_UPDATE_SERIALIZER, (iq) -> onSessionUpdateIQ(iq, mPeerConnectionService));
        addPacketListener(PeerCallServiceImpl.IQ_TRANSPORT_INFO_SERIALIZER, (iq) -> onTransportInfoIQ(iq, mPeerConnectionService));
        addPacketListener(PeerCallServiceImpl.IQ_ON_SESSION_UPDATE_SERIALIZER, (iq) -> onAckSDPIQ(iq, mPeerConnectionService, mPeerConnectionId));
        addPacketListener(PeerCallServiceImpl.IQ_ON_TRANSPORT_INFO_SERIALIZER, (iq) -> onAckSDPIQ(iq, mPeerConnectionService, mPeerConnectionId));
    }

    public abstract long newRequestId();

    /**
     * Get the peer connection id or null.
     *
     * @return the peer connectin id or null.
     */
    @Nullable
    public UUID getPeerConnectionId() {

        return mPeerConnectionId;
    }

    /*
     * Internal methods.
     */

    @Override
    public void onDataChannelOpen(@NonNull UUID peerConnectionId, @Nullable String peerVersion, boolean leadingPadding) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Data channel opened " + peerConnectionId + " v=" + peerVersion);
        }
    }

    @Override
    public void onDataChannelClosed(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Data channel closed " + peerConnectionId);
        }
    }

    @Override
    public void onDataChannelMessage(@NonNull UUID peerConnectionId, @NonNull ByteBuffer buffer, boolean leadingPadding) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Data channel message " + peerConnectionId + " len=" + buffer);
        }

        UUID schemaId = null;
        int schemaVersion = 0;
        try {
            mPeerConnectionService.incrementStat(peerConnectionId, StatType.IQ_RECEIVE_SET_COUNT);

            final ByteBufferInputStream inputStream = new ByteBufferInputStream(buffer);
            final BinaryDecoder binaryDecoder;
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
                mPeerConnectionService.getTwinlifeImpl().assertion(PeerConnectionAssertPoint.UNKNOWN_DATA_CHANNEL_IQ,
                        AssertPoint.createPeerConnectionId(peerConnectionId)
                                .putSchemaId(schemaId)
                                .putSchemaVersion(schemaVersion));
            }

        } catch (Exception exception) {
            mPeerConnectionService.getTwinlifeImpl().exception(PeerConnectionAssertPoint.DATA_CHANNEL_EXCEPTION, exception,
                    AssertPoint.createPeerConnectionId(peerConnectionId)
                            .putSchemaId(schemaId)
                            .putSchemaVersion(schemaVersion));

            // Something very bad happened: terminate the P2P connection because we don't want to proceed
            // with a broken account migration.
            terminatePeerConnection(peerConnectionId, TerminateReason.GENERAL_ERROR);

        } catch (OutOfMemoryError error) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Out of memory", error);
            }
        }
    }

    protected void terminatePeerConnection(@NonNull UUID peerConnectionId, @NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "terminatePeerConnection peerConnectionId=" + peerConnectionId + " terminateReason=" + terminateReason);
        }

        mPeerConnectionService.terminatePeerConnection(peerConnectionId, terminateReason);
    }

    protected void addPacketListener(@NonNull Serializer serializer, @NonNull BinaryPacketListener listener) {

        final SchemaKey key = new SchemaKey(serializer.schemaId, serializer.schemaVersion);
        mBinaryListeners.put(key, new Pair<>(serializer, listener));
    }

    /**
     * Set the peer connection id for this peer connection.
     *
     * @param peerConnectionId the peer connection id.
     */
    public synchronized void setPeerConnectionId(@NonNull UUID peerConnectionId) {

        if (mPeerConnectionId == null) {
            mPeerConnectionId = peerConnectionId;
        }
    }

    /**
     * Serialize the IQ in binary form and send it to the peer connection.
     * Increment the P2P connection stat corresponding to statType.
     *
     * @param packetIQ the packet to serialize and send.
     * @param statType the stat type to increment.
     * @return true if the packet was serialized and sent.
     */
    public boolean sendMessage(@NonNull BinaryPacketIQ packetIQ, @NonNull StatType statType) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendMessage packetIQ=" + packetIQ);
        }

        if (mPeerConnectionId == null) {

            return false;
        }

        mPeerConnectionService.sendPacket(mPeerConnectionId, statType, packetIQ);
        return true;
    }

    private static void onSessionUpdateIQ(@NonNull BinaryPacketIQ iq, @NonNull PeerConnectionServiceImpl peerConnectionService) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSessionUpdateIQ: iq=" + iq);
        }

        if (!(iq instanceof SessionUpdateIQ)) {
            return;
        }

        // Send the ACK immediately.
        final SessionUpdateIQ sessionUpdateIQ = (SessionUpdateIQ) iq;
        peerConnectionService.sendPacket(sessionUpdateIQ.sessionId, StatType.IQ_RESULT_SDP_UPDATE, new BinaryErrorPacketIQ(PeerCallServiceImpl.IQ_ON_SESSION_UPDATE_SERIALIZER, iq, ErrorCode.SUCCESS));

        final SdpType type = sessionUpdateIQ.getType();
        final Sdp sdp = sessionUpdateIQ.getSdp();
        final long sequenceId = sessionUpdateIQ.getSequenceId();
        peerConnectionService.onSessionUpdate(sessionUpdateIQ.sessionId, type, sdp, sequenceId);
    }

    private static void onTransportInfoIQ(@NonNull BinaryPacketIQ iq, @NonNull PeerConnectionServiceImpl peerConnectionService) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTransportInfoIQ: iq=" + iq);
        }

        if (!(iq instanceof TransportInfoIQ)) {
            return;
        }

        // Send the ACK immediately.
        final TransportInfoIQ transportInfoIQ = (TransportInfoIQ) iq;
        peerConnectionService.sendPacket(transportInfoIQ.getSessionId(), StatType.IQ_RESULT_SDP_TRANSPORT_INFO, new BinaryErrorPacketIQ(PeerCallServiceImpl.IQ_ON_TRANSPORT_INFO_SERIALIZER, iq, ErrorCode.SUCCESS));

        final Sdp sdp = transportInfoIQ.getSdp();
        peerConnectionService.onTransportInfo(transportInfoIQ.getSessionId(), sdp);
    }

    private static void onAckSDPIQ(@NonNull BinaryPacketIQ iq, @NonNull PeerConnectionServiceImpl peerConnectionService, @NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAckSDPIQ: iq=" + iq);
        }

        peerConnectionService.ackPacket(peerConnectionId, iq.getRequestId());
    }
}
