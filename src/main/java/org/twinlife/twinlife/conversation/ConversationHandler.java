/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Fabrice Trescartes (Fabrice.Trescartes@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BinaryPacketListener;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.GeolocationDescriptor;
import org.twinlife.twinlife.conversation.UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.PeerConnectionService.StatType;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.ByteBufferInputStream;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.SchemaKey;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Small conversation handler to handle sending/receiving some IQs within a WebRTC data channel.
 *
 * @todo may be try to use it from the ConversationImpl class to handle IQs.
 */
public abstract class ConversationHandler implements PeerConnectionService.DataChannelObserver{
    private static final String LOG_TAG = "ConversationHandler";
    private static final boolean DEBUG = false;

    @NonNull
    protected final PeerConnectionService mPeerConnectionService;
    @NonNull
    private final SerializerFactory mSerializerFactory;
    private final Map<SchemaKey, Pair<Serializer, BinaryPacketListener>> mBinaryListeners = new HashMap<>();
    private final Map<Long, DescriptorImpl> mRequests = new HashMap<>();
    @Nullable
    private GeolocationDescriptorImpl mPeerGeolocationDescriptor;

    @Nullable
    protected UUID mPeerConnectionId;

    public ConversationHandler(@NonNull PeerConnectionService peerConnectionService,
                               @NonNull SerializerFactory serializerFactory) {

        mPeerConnectionService = peerConnectionService;
        mSerializerFactory = serializerFactory;
        addListener(PushObjectIQ.IQ_PUSH_OBJECT_SERIALIZER, this::onPushObjectIQ);
        addListener(OnPushObjectIQ.IQ_ON_PUSH_OBJECT_SERIALIZER, this::onOnPushObjectIQ);
        addListener(PushTwincodeIQ.IQ_PUSH_TWINCODE_SERIALIZER_3, this::onPushTwincodeIQ);
        addListener(PushTwincodeIQ.IQ_PUSH_TWINCODE_SERIALIZER_2, this::onPushTwincodeIQ);
        addListener(OnPushTwincodeIQ.IQ_ON_PUSH_TWINCODE_SERIALIZER, this::onOnPushObjectIQ);
        addListener(PushGeolocationIQ.IQ_PUSH_GEOLOCATION_SERIALIZER, this::onPushGeolocationIQ);
        addListener(OnPushGeolocationIQ.IQ_ON_PUSH_GEOLOCATION_SERIALIZER, this::onOnPushObjectIQ);
        addListener(UpdateGeolocationIQ.IQ_UPDATE_GEOLOCATION_SERIALIZER, this::onUpdateGeolocationIQ);
        addListener(OnUpdateGeolocationIQ.IQ_ON_UPDATE_GEOLOCATION_SERIALIZER, this::onOnPushObjectIQ);
        addListener(UpdateTimestampIQ.IQ_UPDATE_TIMESTAMPS_SERIALIZER, this::onUpdateTimestampIQ);
        addListener(OnUpdateTimestampIQ.IQ_ON_UPDATE_TIMESTAMP_SERIALIZER, this::onOnPushObjectIQ);
    }

    public abstract void onPopDescriptor(@NonNull Descriptor descriptor);
    public abstract void onUpdateGeolocation(@NonNull GeolocationDescriptor descriptor);
    public abstract void onReadDescriptor(@NonNull ConversationService.DescriptorId descriptorId, long timestamp);
    public abstract void onDeleteDescriptor(@NonNull ConversationService.DescriptorId descriptorId);
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

    /**
     * Get the current peer geolocation descriptor.
     *
     * @return the current peer geolocation descriptor or null.
     */
    @Nullable
    public GeolocationDescriptor getCurrentGeolocation() {

        return mPeerGeolocationDescriptor;
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

        UUID schemaId;
        int schemaVersion;
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
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Schema key ", key, " not found");
                }
            }

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Internal error ", exception);
            }
        } catch (OutOfMemoryError error) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Out of memory", error);
            }
        }
    }

    protected void addListener(@NonNull Serializer serializer, @NonNull BinaryPacketListener listener) {

        SchemaKey key = new SchemaKey(serializer.schemaId, serializer.schemaVersion);
        mBinaryListeners.put(key, new Pair<>(serializer, listener));
    }

    public int getDeviceState() {

        return ConversationConnection.DEVICE_STATE_FOREGROUND | ConversationConnection.DEVICE_STATE_HAS_OPERATIONS;
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

    /**
     * Send the descriptor to the P2P data channel connection.
     *
     * @param descriptor the descriptor to send.
     * @return true if the descriptor was serialized and sent.
     */
    public boolean sendDescriptor(@NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendDescriptor: descriptor=" + descriptor);
        }

        final DescriptorImpl descriptorImpl = (DescriptorImpl) descriptor;
        final long requestId = newRequestId();
        final boolean sent;
        synchronized (mRequests) {
            mRequests.put(requestId, descriptorImpl);
        }
        if (descriptor instanceof ObjectDescriptorImpl) {
            PushObjectIQ pushObjectIQ = new PushObjectIQ(PushObjectIQ.IQ_PUSH_OBJECT_SERIALIZER,
                    requestId, (ObjectDescriptorImpl) descriptor);

            sent = sendMessage(pushObjectIQ, StatType.IQ_SET_PUSH_OBJECT);

        } else if (descriptor instanceof TwincodeDescriptorImpl) {
            PushTwincodeIQ pushTwincodeIQ = new PushTwincodeIQ(PushTwincodeIQ.IQ_PUSH_TWINCODE_SERIALIZER_2,
                    requestId, (TwincodeDescriptorImpl) descriptor);

            sent = sendMessage(pushTwincodeIQ, StatType.IQ_SET_PUSH_TWINCODE);

        } else if (descriptor instanceof GeolocationDescriptorImpl) {
            PushGeolocationIQ pushGeolocationIQ = new PushGeolocationIQ(PushGeolocationIQ.IQ_PUSH_GEOLOCATION_SERIALIZER,
                    requestId, (GeolocationDescriptorImpl) descriptor);

            sent = sendMessage(pushGeolocationIQ, StatType.IQ_SET_PUSH_GEOLOCATION);

        } else {

            sent = false;
        }

        if (!sent) {
            if (descriptorImpl.getSentTimestamp() == 0) {
                descriptorImpl.setSentTimestamp(-1L);
            }
            synchronized (mRequests) {
                mRequests.remove(requestId);
            }
        } else if (descriptorImpl.getSentTimestamp() <= 0) {
            descriptorImpl.setSentTimestamp(System.currentTimeMillis());
        }
        return sent;
    }

    /**
     * Update our geolocation descriptor to the P2P data channel connection.
     *
     * @param descriptor our geolocation descriptor to update.
     * @return true if the descriptor update was serialized and sent.
     */
    public boolean updateGeolocation(@NonNull GeolocationDescriptor descriptor,
                                     double longitude, double latitude, double altitude,
                                     double mapLongitudeDelta, double mapLatitudeDelta) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateGeolocation: descriptor=" + descriptor);
        }

        final GeolocationDescriptorImpl descriptorImpl = (GeolocationDescriptorImpl) descriptor;
        final long requestId = newRequestId();
        descriptorImpl.update(longitude, latitude, altitude, mapLongitudeDelta, mapLatitudeDelta);
        synchronized (mRequests) {
            mRequests.put(requestId, descriptorImpl);
        }

        UpdateGeolocationIQ updateGeolocationIQ = new UpdateGeolocationIQ(UpdateGeolocationIQ.IQ_UPDATE_GEOLOCATION_SERIALIZER,
                requestId, System.currentTimeMillis(), longitude, latitude, altitude,
                mapLongitudeDelta, mapLatitudeDelta);

        return sendMessage(updateGeolocationIQ, StatType.IQ_SET_PUSH_GEOLOCATION);
    }

    /**
     * Send a DELETE descriptor on the peer to remove our descriptor from the peer's conversation.
     *
     * @param descriptor the descriptor to delete.
     * @return true if the descriptor delete was serialized and sent.
     */
    public boolean deleteDescriptor(@NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteDescriptor: descriptor=" + descriptor);
        }

        final long requestId = newRequestId();
        synchronized (mRequests) {
            mRequests.put(requestId, (DescriptorImpl) descriptor);
        }

        UpdateTimestampIQ updateTimestampIQ = new UpdateTimestampIQ(UpdateTimestampIQ.IQ_UPDATE_TIMESTAMPS_SERIALIZER,
                requestId, descriptor.getDescriptorId(), UpdateDescriptorTimestampType.DELETE, System.currentTimeMillis());

        return sendMessage(updateTimestampIQ, StatType.IQ_SET_UPDATE_OBJECT);
    }

    public static void markDescriptorRead(@NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "markDescriptorRead: descriptor=" + descriptor);
        }

        DescriptorImpl descriptorImpl = (DescriptorImpl) descriptor;
        if (descriptorImpl.getReadTimestamp() <= 0) {
            descriptorImpl.setReadTimestamp(System.currentTimeMillis());
        }
    }

    /**
     * Handle the ParticipantInfoIQ packet.
     *
     * @param iq the participant info iq.
     */
    private void onPushObjectIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPushObjectIQ: iq=" + iq);
        }

        if (!(iq instanceof PushObjectIQ)) {
            return;
        }
        final PushObjectIQ pushObjectIQ = (PushObjectIQ) iq;

        ObjectDescriptorImpl objectDescriptorImpl = pushObjectIQ.objectDescriptorImpl;
        objectDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());
        onPopDescriptor(objectDescriptorImpl);

        int deviceState = getDeviceState();
        OnPushIQ onPushObjectIQ = new OnPushIQ(OnPushObjectIQ.IQ_ON_PUSH_OBJECT_SERIALIZER, pushObjectIQ.getRequestId(), deviceState, objectDescriptorImpl.getReceivedTimestamp());

        sendMessage(onPushObjectIQ, StatType.IQ_RESULT_PUSH_OBJECT);
    }

    private void onPushTwincodeIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPushTwincodeIQ: iq=" + iq);
        }

        if (!(iq instanceof PushTwincodeIQ)) {
            return;
        }
        final PushTwincodeIQ pushTwincodeIQ = (PushTwincodeIQ) iq;

        TwincodeDescriptorImpl twincodeDescriptorImpl = pushTwincodeIQ.twincodeDescriptorImpl;
        twincodeDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());
        onPopDescriptor(twincodeDescriptorImpl);

        int deviceState = getDeviceState();
        OnPushIQ onPushTwincodeIQ = new OnPushIQ(OnPushTwincodeIQ.IQ_ON_PUSH_TWINCODE_SERIALIZER, pushTwincodeIQ.getRequestId(), deviceState, twincodeDescriptorImpl.getReceivedTimestamp());
        sendMessage(onPushTwincodeIQ, StatType.IQ_RESULT_PUSH_TWINCODE);
    }

    private void onPushGeolocationIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPushGeolocationIQ: iq=" + iq);
        }

        if (!(iq instanceof PushGeolocationIQ)) {
            return;
        }
        final PushGeolocationIQ pushGeolocationIQ = (PushGeolocationIQ) iq;

        GeolocationDescriptorImpl geolocationDescriptorImpl = pushGeolocationIQ.geolocationDescriptorImpl;
        geolocationDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());
        mPeerGeolocationDescriptor = geolocationDescriptorImpl;
        onPopDescriptor(geolocationDescriptorImpl);

        int deviceState = getDeviceState();
        OnPushIQ onPushTwincodeIQ = new OnPushIQ(OnPushGeolocationIQ.IQ_ON_PUSH_GEOLOCATION_SERIALIZER, pushGeolocationIQ.getRequestId(), deviceState, geolocationDescriptorImpl.getReceivedTimestamp());
        sendMessage(onPushTwincodeIQ, StatType.IQ_RESULT_PUSH_GEOLOCATION);
    }

    private void onUpdateGeolocationIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateGeolocationIQ: iq=" + iq);
        }

        if (!(iq instanceof UpdateGeolocationIQ)) {
            return;
        }
        final UpdateGeolocationIQ updateGeolocationIQ = (UpdateGeolocationIQ) iq;
        final long receiveTimestamp;

        if (mPeerGeolocationDescriptor != null) {
            receiveTimestamp = System.currentTimeMillis();
            mPeerGeolocationDescriptor.setReceivedTimestamp(receiveTimestamp);
            mPeerGeolocationDescriptor.update(updateGeolocationIQ.longitude, updateGeolocationIQ.latitude,
                    updateGeolocationIQ.altitude, updateGeolocationIQ.mapLongitudeDelta, updateGeolocationIQ.mapLatitudeDelta);
            onUpdateGeolocation(mPeerGeolocationDescriptor);
        } else {
            receiveTimestamp = -1L;
        }

        int deviceState = getDeviceState();
        OnPushIQ onUpdateGeolocationIQ = new OnPushIQ(OnUpdateGeolocationIQ.IQ_ON_UPDATE_GEOLOCATION_SERIALIZER,
                updateGeolocationIQ.getRequestId(), deviceState, receiveTimestamp);
        sendMessage(onUpdateGeolocationIQ, StatType.IQ_RESULT_PUSH_GEOLOCATION);
    }

    private void onUpdateTimestampIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTimestampIQ: iq=" + iq);
        }

        if (!(iq instanceof UpdateTimestampIQ)) {
            return;
        }
        final UpdateTimestampIQ updateTimestampIQ = (UpdateTimestampIQ) iq;
        final long receiveTimestamp = System.currentTimeMillis();

        switch (updateTimestampIQ.timestampType) {
            case READ:
                onReadDescriptor(updateTimestampIQ.descriptorId, updateTimestampIQ.timestamp);
                break;

            case DELETE:
                if (mPeerGeolocationDescriptor != null
                        && mPeerGeolocationDescriptor.getDescriptorId().equals(updateTimestampIQ.descriptorId)) {
                    mPeerGeolocationDescriptor = null;
                }
                onDeleteDescriptor(updateTimestampIQ.descriptorId);
                break;

            case PEER_DELETE:
                break;
        }

        int deviceState = getDeviceState();
        OnPushIQ onUpdateTimestampIQ = new OnPushIQ(OnUpdateGeolocationIQ.IQ_ON_UPDATE_GEOLOCATION_SERIALIZER,
                updateTimestampIQ.getRequestId(), deviceState, receiveTimestamp);
        sendMessage(onUpdateTimestampIQ, StatType.IQ_RESULT_UPDATE_OBJECT);
    }

    private void onOnPushObjectIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOnPushObjectIQ: iq=" + iq);
        }

        if (!(iq instanceof OnPushIQ)) {
            return;
        }

        final OnPushIQ onPushIQ = (OnPushIQ) iq;
        final DescriptorImpl descriptorImpl;
        synchronized (mRequests) {
            descriptorImpl = mRequests.remove(onPushIQ.getRequestId());
        }
        if (descriptorImpl != null && descriptorImpl.getReceivedTimestamp() <= 0) {
            descriptorImpl.setReceivedTimestamp(onPushIQ.receivedTimestamp);
        }
    }
}
