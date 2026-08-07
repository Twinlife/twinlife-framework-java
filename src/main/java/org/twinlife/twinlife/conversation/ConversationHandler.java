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

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.GeolocationDescriptor;
import org.twinlife.twinlife.conversation.UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.PeerConnectionService.StatType;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.peerconnection.DataChannelHandler;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.HashMap;
import java.util.Map;

/**
 * Small conversation handler to handle sending/receiving some IQs within a WebRTC data channel.
 *
 * @todo may be try to use it from the ConversationImpl class to handle IQs.
 */
public abstract class ConversationHandler extends DataChannelHandler {
    private static final String LOG_TAG = "ConversationHandler";
    private static final boolean DEBUG = false;

    private final Map<Long, DescriptorImpl> mRequests = new HashMap<>();
    @Nullable
    private GeolocationDescriptorImpl mPeerGeolocationDescriptor;

    public ConversationHandler(@NonNull PeerConnectionService peerConnectionService,
                               @NonNull SerializerFactory serializerFactory) {
        super(peerConnectionService, serializerFactory);

        addPacketListener(PushObjectIQ.IQ_PUSH_OBJECT_SERIALIZER, this::onPushObjectIQ);
        addPacketListener(OnPushObjectIQ.IQ_ON_PUSH_OBJECT_SERIALIZER, this::onOnPushObjectIQ);
        addPacketListener(PushTwincodeIQ.IQ_PUSH_TWINCODE_SERIALIZER_3, this::onPushTwincodeIQ);
        addPacketListener(PushTwincodeIQ.IQ_PUSH_TWINCODE_SERIALIZER_2, this::onPushTwincodeIQ);
        addPacketListener(OnPushTwincodeIQ.IQ_ON_PUSH_TWINCODE_SERIALIZER, this::onOnPushObjectIQ);
        addPacketListener(PushGeolocationIQ.IQ_PUSH_GEOLOCATION_SERIALIZER_2, this::onPushGeolocationIQ);
        addPacketListener(PushGeolocationIQ.IQ_PUSH_GEOLOCATION_SERIALIZER_3, this::onPushGeolocationIQ);
        addPacketListener(OnPushGeolocationIQ.IQ_ON_PUSH_GEOLOCATION_SERIALIZER, this::onOnPushObjectIQ);
        addPacketListener(PushPollIQ.IQ_PUSH_POLL_SERIALIZER, this::onPushPollIQ);
        addPacketListener(OnPushPollIQ.IQ_ON_PUSH_POLL_SERIALIZER, this::onOnPushObjectIQ);
        addPacketListener(UpdateGeolocationIQ.IQ_UPDATE_GEOLOCATION_SERIALIZER, this::onUpdateGeolocationIQ);
        addPacketListener(OnUpdateGeolocationIQ.IQ_ON_UPDATE_GEOLOCATION_SERIALIZER, this::onOnPushObjectIQ);
        addPacketListener(UpdateTimestampIQ.IQ_UPDATE_TIMESTAMPS_SERIALIZER, this::onUpdateTimestampIQ);
        addPacketListener(OnUpdateTimestampIQ.IQ_ON_UPDATE_TIMESTAMP_SERIALIZER, this::onOnPushObjectIQ);
    }

    public abstract void onPopDescriptor(@NonNull Descriptor descriptor);
    public abstract void onUpdateGeolocation(@NonNull GeolocationDescriptor descriptor);
    public abstract void onReadDescriptor(@NonNull ConversationService.DescriptorId descriptorId, long timestamp);
    public abstract void onDeleteDescriptor(@NonNull ConversationService.DescriptorId descriptorId);
    public abstract long newRequestId();

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

    public int getDeviceState() {

        return ConversationConnection.DEVICE_STATE_FOREGROUND | ConversationConnection.DEVICE_STATE_HAS_OPERATIONS;
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
            PushGeolocationIQ pushGeolocationIQ = new PushGeolocationIQ(PushGeolocationIQ.IQ_PUSH_GEOLOCATION_SERIALIZER_2,
                    requestId, (GeolocationDescriptorImpl) descriptor);

            sent = sendMessage(pushGeolocationIQ, StatType.IQ_SET_PUSH_GEOLOCATION);

        } else if (descriptor instanceof PollDescriptorImpl) {
            PushPollIQ pushPollIQ = new PushPollIQ(PushPollIQ.IQ_PUSH_POLL_SERIALIZER,
                    requestId, (PollDescriptorImpl) descriptor);

            sent = sendMessage(pushPollIQ, StatType.IQ_SET_PUSH_POLL);

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

    private void onPushPollIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPushPollIQ: iq=" + iq);
        }

        if (!(iq instanceof PushPollIQ)) {
            return;
        }
        final PushPollIQ pushPollIQ = (PushPollIQ) iq;

        PollDescriptorImpl pollDescriptorImpl = pushPollIQ.pollDescriptorImpl;
        pollDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());
        onPopDescriptor(pollDescriptorImpl);

        int deviceState = getDeviceState();
        OnPushIQ onPushObjectIQ = new OnPushIQ(OnPushPollIQ.IQ_ON_PUSH_POLL_SERIALIZER, pushPollIQ.getRequestId(), deviceState, pollDescriptorImpl.getReceivedTimestamp());

        sendMessage(onPushObjectIQ, StatType.IQ_RESULT_PUSH_POLL);
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
