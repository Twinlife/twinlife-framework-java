/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.crypto.SignatureInfoIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.FileInfoImpl;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.ReceivingFileInfo;
import org.twinlife.twinlife.util.SendingFileInfo;

import java.io.File;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAX_MAJOR_VERSION;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAX_MINOR_VERSION_1;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAX_MINOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_12;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_13;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_15;

public class ConversationConnection {
    private static final String LOG_TAG = "ConversationConnection";
    private static final boolean DEBUG = false;

    /**
     * Device state flag indicating the device is in foreground: we can keep the P2P connection opened.
     */
    static final int DEVICE_STATE_FOREGROUND = 0x01;

    /**
     * Device state flag indicating the device has some pending operations: we must keep the P2P connection opened.
     */
    static final int DEVICE_STATE_HAS_OPERATIONS = 0x02;

    /**
     * Device state flag indicating the device is synchronizing its secret keys with the peer.
     */
    static final int DEVICE_STATE_SYNCHRONIZE_KEYS = 0x04;

    /**
     * Device state flags that we accept from the peer through the OnPush/OnSynchronize IQs.
     */
    static final int DEVICE_STATE_MASK = (DEVICE_STATE_FOREGROUND | DEVICE_STATE_HAS_OPERATIONS | DEVICE_STATE_SYNCHRONIZE_KEYS);

    /**
     * Internal device state indicating that the above two flags are valid and come from the peer device.
     */
    static final int DEVICE_STATE_VALID = 0x10;

    /**
     * On high latency networks it is best to use small chunks for data transfer because we maximize the chance to
     * receive the full chunk and save it.  On low latency networks, sending bigger data chunks provides better performance.
     */
    static final int NETWORK_HIGH_RTT = 1000;
    static final int NETWORK_NORMAL_RTT = 500;
    static final int CHUNK_HIGH_RTT = (16 * 1024);    // Used when RTT > 1000ms
    static final int CHUNK_NORMAL_RTT = (32 * 1024);  // Used when RTT in 500ms .. 1000ms
    static final int CHUNK_LOW_RTT = (64 * 1024);     // Used when RTT < 500ms

    public enum State {
        CLOSED,   // P2P is closed
        CREATING, // PeerConnection is being created
        OPENING,  // PeerConnection is created and is trying to connect
        OPEN      // PeerConnection is now opened.
    }

    private static final int MAX_ADJUST_TIME = 3600 * 1000; // Absolute maximum wallclock time adjustment in ms made.

    private final ConversationImpl mConversation;
    private final ConversationServiceImpl mConversationService;
    private final PeerConnectionService mPeerConnectionService;
    private final SerializerFactory mSerializerFactory;
    private final TwinlifeImpl mTwinlifeImpl;

    private boolean mLeadingPadding;
    private volatile int mPeerMajorVersion;
    private volatile int mPeerMinorVersion;
    private boolean mSynchronizeKeys;

    /**
     * There is a connection state associated with each direction and we
     * also keep the peer conversation ID in both directions.
     * When one of the state becomes opened, the peerConnectionId is updated
     * to refer to the peer conversation ID that is opened and all the
     * communication operations will use it.
     */
    @NonNull
    private volatile State mIncomingState;
    @NonNull
    private volatile State mOutgoingState;
    @Nullable
    private volatile UUID mIncomingPeerConnectionId;
    @Nullable
    private volatile UUID mOutgoingPeerConnectionId;
    @Nullable
    private volatile UUID mPeerConnectionId;
    @Nullable
    private volatile ScheduledFuture<?> mOpenTimeout;

    private long mPeerTimeCorrection;
    private int mPeerDeviceState;
    private int mEstimatedRTT;
    private long mAccessedTime;

    @Nullable
    private Map<String, ReceivingFileInfo> mReceivingFiles;
    @Nullable
    private Map<String, SendingFileInfo> mSendingFiles;

    ConversationConnection(@NonNull TwinlifeImpl twinlife, @NonNull ConversationImpl conversation, boolean incoming) {

        mTwinlifeImpl = twinlife;
        mConversation = conversation;
        mSerializerFactory = twinlife.getSerializerFactory();
        mPeerConnectionService = twinlife.getPeerConnectionService();
        mConversationService = twinlife.getConversationServiceImpl();

        mPeerMajorVersion = ConversationServiceImpl.MAJOR_VERSION_1;
        mPeerMinorVersion = 0;
        mIncomingState = State.CLOSED;
        mOutgoingState = State.CLOSED;
        mSynchronizeKeys = false;
        mPeerTimeCorrection = 0;
        mPeerDeviceState = 0;
        if (incoming) {
            mIncomingState = State.CREATING;
        } else {
            mOutgoingState = State.CREATING;
        }
    }

    @NonNull
    public DatabaseIdentifier getDatabaseId() {

        return mConversation.getDatabaseId();
    }

    @NonNull
    public ConversationImpl getConversation() {

        return mConversation;
    }

    public long newRequestId() {

        return mTwinlifeImpl.newRequestId();
    }

    @NonNull
    public SerializerFactory getSerializerFactory() {

        return mSerializerFactory;
    }

    public void sendMessage(@NonNull PeerConnectionService.StatType statType, @NonNull byte[] bytes) {

        final UUID peerConnectionId = mPeerConnectionId;
        if (peerConnectionId != null) {
            mPeerConnectionService.sendMessage(peerConnectionId, statType, bytes);
        }
    }

    public void sendPacket(@NonNull PeerConnectionService.StatType statType, @NonNull BinaryPacketIQ iq) {

        final UUID peerConnectionId = mPeerConnectionId;
        if (peerConnectionId != null) {
            mPeerConnectionService.sendPacket(peerConnectionId, statType, iq);
        }
    }

    @Nullable
    public UUID getPeerConnectionId() {

        return mPeerConnectionId;
    }

    //
    // Package specific Methods
    //

    @SuppressWarnings("SameParameterValue")
    public boolean isSupported(int majorVersion, int minorVersion) {

        if (mPeerMajorVersion < majorVersion) {

            return false;
        }

        if (mPeerMajorVersion > majorVersion) {

            return true;
        }

        return mPeerMinorVersion >= minorVersion;
    }

    public int getMaxPeerMajorVersion() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getMaxPeerMajorVersion");
        }

        return Math.min(getPeerMajorVersion(), MAX_MAJOR_VERSION);
    }

    public int getMaxPeerMinorVersion(int majorVersion) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getMaxPeerMinorVersion: majorVersion=" + majorVersion);
        }

        if (majorVersion >= MAJOR_VERSION_2) {

            int min = Math.min(getPeerMinorVersion(), MAX_MINOR_VERSION_2);

            // Version 2.1..2.7 have a bug where the reply IQ indicates a major+minor that comes from the conversation
            // peer version, which means that a response can contain a 2.15 while the request contained 2.5.
            // Version 2.13 introduced a bug where a SET IQ with version 2.13 is not handled but the peer
            // which returns an error IQ (the MAX_MINOR_VERSION_2 was incorrectly set to 12 when it should be 13).
            // Version 2.14 and 2.15 introduced another bug where a response IQ with a minor 14 or 15 where ignored
            // and generate a problem report if we communicate with a ConversationService <= 2.7
            // Downgrade to 2.12 to avoid falling in these bugs.  Note: it is OK to use the 2.12 version if we use binary IQ.
            if (min >= MINOR_VERSION_13 && min <= MINOR_VERSION_15 && majorVersion == MAJOR_VERSION_2) {
                min = MINOR_VERSION_12;
            }
            return min;
        }

        return Math.min(getPeerMinorVersion(), MAX_MINOR_VERSION_1);
    }

    /**
     * Methods used by the Operation classes for the implementation of execute().
     */

    @Nullable
    public File getFilesDir() {

        return mTwinlifeImpl.getFilesDir();
    }

    @Nullable
    public DescriptorImpl loadDescriptorWithId(long descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadDescriptorWithId descriptorId=" + descriptorId);
        }

        return mConversationService.loadDescriptorWithId(descriptorId);
    }

    public void updateDescriptorImplTimestamps(@NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateDescriptorImplTimestamps descriptorImpl=" + descriptorImpl);
        }

        mConversationService.updateDescriptorImplTimestamps(descriptorImpl);
    }

    public boolean preparePush(@NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "preparePush descriptorImpl=" + descriptorImpl);
        }

        if (descriptorImpl.getDeletedTimestamp() > 0 || descriptorImpl.isExpired()) {
            return false;
        }

        if (descriptorImpl.getSentTimestamp() <= 0) {
            descriptorImpl.setSentTimestamp(System.currentTimeMillis());
            mConversationService.updateDescriptorImplTimestamps(descriptorImpl);
        }
        return true;
    }

    @NonNull
    List<ConversationService.DescriptorAnnotation> loadLocalAnnotations(@NonNull ConversationService.DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadLocalAnnotations descriptorId=" + descriptorId);
        }

        return mConversationService.loadLocalAnnotations(getMainConversation(), descriptorId);
    }

    void deleteFileDescriptor(@NonNull FileDescriptorImpl fileDescriptorImpl,
                              @NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteFileDescriptor fileDescriptorImpl=" + fileDescriptorImpl);
        }

        mConversationService.deleteFileDescriptor(this, fileDescriptorImpl, operation);
    }

    @Nullable
    SignatureInfoIQ createSignature(@NonNull UUID groupTwincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSignature groupTwincodeId=" + groupTwincodeId);
        }

        return mConversationService.createSignature(this, groupTwincodeId);
    }

    @NonNull
    BaseService.ErrorCode operationNotSupported(@Nullable DescriptorImpl descriptorImpl) {

        return mConversationService.operationNotSupported(this, descriptorImpl);
    }

    void updateLeadingPadding(boolean leadingPadding) {

        mLeadingPadding = leadingPadding;
    }

    boolean needLeadingPadding() {

        return mLeadingPadding;
    }

    @NonNull
    Conversation getMainConversation() {

        return mConversation.getMainConversation();
    }

    long getPeerTimeCorrection() {

        return mPeerTimeCorrection;
    }

    long getAdjustedTime(long timestamp) {

        if (timestamp <= 0) {
            return timestamp;
        } else {
            return timestamp + mPeerTimeCorrection;
        }
    }

    void touch() {

        mAccessedTime = System.currentTimeMillis();
    }

    long idleTime() {

        return System.currentTimeMillis() - mAccessedTime;
    }

    boolean isTransferingFile() {

        return (mSendingFiles != null && !mSendingFiles.isEmpty())
                || (mReceivingFiles != null && !mReceivingFiles.isEmpty());
    }

    void setOpenTimeout(ScheduledFuture<?> timeout) {

        mOpenTimeout = timeout;
    }

    int getPeerMajorVersion() {

        return mPeerMajorVersion;
    }

    int getPeerMinorVersion() {

        return mPeerMinorVersion;
    }

    void setPeerVersion(String peerVersion) {

        if (peerVersion != null) {
            // peerVersion = i.j.k
            try {
                String version = peerVersion;
                int index = version.indexOf('.');
                mPeerMajorVersion = Integer.parseInt(peerVersion.substring(0, index));
                version = version.substring(index + 1);
                index = version.indexOf('.');
                mPeerMinorVersion = Integer.parseInt(version.substring(0, index));
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "setPeerVersion: peerVersion=", peerVersion, exception);
                }
            }
        }
    }

    @NonNull
    State getIncomingState() {

        return mIncomingState;
    }

    @NonNull
    State getOutgoingState() {

        return mOutgoingState;
    }

    @NonNull
    State getState() {

        if (mIncomingState == State.CLOSED && mOutgoingState != State.CLOSED) {
            return mOutgoingState;
        } else {
            return mIncomingState;
        }
    }

    boolean isOpened() {

        return mIncomingState == State.OPEN || mOutgoingState == State.OPEN;
    }

    /**
     * Check if we can accept an incoming P2P connection.
     *
     * @return true if we can accept, false if we must reject, null if we don't know.
     */
    @Nullable
    Boolean canAcceptIncoming() {
        if (DEBUG) {
            Log.d(LOG_TAG, "canAcceptIncoming");
        }

        // If one of the IN/OUT connection is opened, we are busy.
        if (mOutgoingState == State.OPEN || mIncomingState == State.OPEN
                || mOutgoingState == State.CREATING || mIncomingState == State.CREATING) {
            return false;
        }

        // IN and OUT are not opened and they were created more than 20s ago, they are dead.
        if (mOpenTimeout == null) {
            mIncomingState = State.CREATING;
            return true;
        }
        return null;
    }

    @Nullable
    UUID incomingPeerConnection(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "incomingPeerConnection: peerConnectionId=" + peerConnectionId);
        }

        final UUID previous = mIncomingPeerConnectionId;
        mIncomingState = State.CREATING;
        mIncomingPeerConnectionId = peerConnectionId;
        final ScheduledFuture<?> openTimeout = mOpenTimeout;
        if (openTimeout != null) {
            mOpenTimeout = null;
            openTimeout.cancel(false);
        }
        return previous;
    }

    void setIncomingPeerConnectionOpening() {

        mIncomingState = State.OPENING;
    }

    boolean canStartOutgoing() {
        if (DEBUG) {
            Log.d(LOG_TAG, "canStartOutgoing");
        }

        if (mOutgoingState != State.CLOSED) {
            return false;
        }

        // We must not have an incoming P2P connection active or being setup.
        // (except if that incoming P2P is older than 30s)
        switch (mIncomingState) {
            case OPEN:
                return false;

            case OPENING:
                if (mOpenTimeout != null) {
                    return false;
                }
                // fallback to creating state.

            case CLOSED:
            default:
                mOutgoingState = State.CREATING;
                return true;
        }
    }

    void outgoingPeerConnection(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "outgoingPeerConnection: peerConnectionId=" + peerConnectionId);
        }

        mOutgoingState = State.OPENING;
        mOutgoingPeerConnectionId = peerConnectionId;
    }

    @NonNull
    ConversationConnection transferPeerConnection(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "transferPeerConnection: conversationImpl=" + conversationImpl);
        }

        final ConversationConnection result = new ConversationConnection(mTwinlifeImpl, conversationImpl, true);
        result.mIncomingState = mIncomingState;
        result.mOutgoingState = mOutgoingState;
        result.mOpenTimeout = null;
        result.mAccessedTime = mAccessedTime;
        result.mIncomingPeerConnectionId = mIncomingPeerConnectionId;
        result.mPeerConnectionId = mPeerConnectionId;
        result.mPeerMajorVersion = mPeerMajorVersion;
        result.mPeerMinorVersion = mPeerMinorVersion;
        result.mPeerTimeCorrection = mPeerTimeCorrection;
        result.mPeerDeviceState = mPeerDeviceState;
        result.mLeadingPadding = mLeadingPadding;

        final ScheduledFuture<?> openTimeout = mOpenTimeout;
        if (openTimeout != null) {
            mOpenTimeout = null;
            openTimeout.cancel(false);
        }
        mIncomingState = State.CLOSED;
        mOutgoingState = State.CLOSED;
        mIncomingPeerConnectionId = null;
        mPeerConnectionId = null;
        return result;
    }

    boolean openPeerConnection(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "openPeerConnection: peerConnectionId=" + peerConnectionId);
        }

        if (peerConnectionId.equals(mIncomingPeerConnectionId)) {
            mIncomingState = State.OPEN;

        } else if (peerConnectionId.equals(mOutgoingPeerConnectionId)) {
            mOutgoingState = State.OPEN;

        } else {
            return false;
        }

        final ScheduledFuture<?> openTimeout = mOpenTimeout;
        if (openTimeout != null) {
            openTimeout.cancel(false);
        }
        mOpenTimeout = null;
        mPeerConnectionId = peerConnectionId;
        mPeerTimeCorrection = 0;
        mPeerDeviceState = 0;
        touch();

        return true;
    }

    @Nullable
    Boolean closePeerConnection(@Nullable UUID peerConnectionId, boolean isIncoming) {
        if (DEBUG) {
            Log.d(LOG_TAG, "closePeerConnection: peerConnectionId=" + peerConnectionId);
        }

        if (peerConnectionId == null) {
            // Creation of PeerConnection failed.
            if (isIncoming) {
                mIncomingState = State.CLOSED;
            } else {
                mOutgoingState = State.CLOSED;
            }
        } else if (peerConnectionId.equals(mIncomingPeerConnectionId)) {
            mIncomingState = State.CLOSED;
            mIncomingPeerConnectionId = null;
            isIncoming = true;
        } else if (peerConnectionId.equals(mOutgoingPeerConnectionId)) {
            mOutgoingState = State.CLOSED;
            mOutgoingPeerConnectionId = null;
            isIncoming = false;
        }
        if (mIncomingState != State.CLOSED || mOutgoingState != State.CLOSED) {

            return null;
        }
        final ScheduledFuture<?> openTimeout = mOpenTimeout;
        if (openTimeout != null) {
            openTimeout.cancel(false);
            mOpenTimeout = null;
        }
        mPeerConnectionId = null;
        mPeerDeviceState = 0;

        // If there are some open files being received or sent, close them.
        if (mSendingFiles != null) {
            for (Map.Entry<String, SendingFileInfo> item : mSendingFiles.entrySet()) {
                item.getValue().cancel();
            }
            mSendingFiles = null;
        }

        if (mReceivingFiles != null) {
            for (Map.Entry<String, ReceivingFileInfo> item : mReceivingFiles.entrySet()) {
                item.getValue().cancel();
            }
            mReceivingFiles = null;
        }
        mConversation.closeConnection();
        return isIncoming;
    }

    /**
     * Compute the wall clock adjustment between the peer and our local clock.
     * This time correction is applied to times that we received from that peer.
     * We continue to send our times not-adjusted: the peer will do its own correction.
     * The algorithm is inspired from NTP but it is simplified:
     * - we compute the RTT between the two devices,
     * - we compute the time difference between the two devices,
     * - the difference is corrected by RTT/2.
     *
     * @param peerTime the peer time when it processed the SYNCHRONIZE_IQ.
     * @param startTime the time when we created the SYNCHRONIZED_IQ.
     */
    void adjustPeerTime(long peerTime, long startTime) {
        if (DEBUG) {
            Log.d(LOG_TAG, "adjustPeerTime: peerTime=" + peerTime + " startTime=" + startTime);
        }

        long now = System.currentTimeMillis();

        // Compute the propagation time: RTT (ignore excessive values).
        long tp = (now - startTime);
        if (tp < 0 || tp > 60000) {
            return;
        }

        // Compute the time correction.
        long tc = (peerTime - (startTime + (tp / 2)));
        if (tc > MAX_ADJUST_TIME) {
            tc = MAX_ADJUST_TIME;
        } else if (tc < -MAX_ADJUST_TIME) {
            tc = -MAX_ADJUST_TIME;
        }

        mPeerTimeCorrection = -tc;
        mEstimatedRTT = (int) tp;

        if (DEBUG) {
            Log.d(LOG_TAG, "Propagation=" + tp + " time correction=" + tc);
        }
    }

    void updateEstimatedRTT(long timestamp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateEstimatedRTT: timestamp=" + timestamp);
        }

        long now = System.currentTimeMillis();

        // Compute the propagation time: RTT (ignore excessive values).
        long tp = (now - timestamp);
        if (tp < 0 || tp > 60000) {
            return;
        }

        mEstimatedRTT = (mEstimatedRTT + (int) tp) / 2;
    }

    int getBestChunkSize() {

        if (mEstimatedRTT > NETWORK_HIGH_RTT) {
            return CHUNK_HIGH_RTT;

        } else if (mEstimatedRTT > NETWORK_NORMAL_RTT) {
            return CHUNK_NORMAL_RTT;

        } else {
            return CHUNK_LOW_RTT;
        }
    }

    void setSynchronizeKeys(boolean value) {

        mSynchronizeKeys = value;
    }

    boolean isSynchronizingKeys() {

        return mSynchronizeKeys;
    }

    int getPeerDeviceState() {

        return mPeerDeviceState;
    }

    void setPeerDeviceState(int deviceState) {

        mPeerDeviceState = (deviceState & DEVICE_STATE_MASK) | DEVICE_STATE_VALID;
    }

    UUID getOutgoingPeerConnectionId() {

        return mOutgoingPeerConnectionId;
    }

    UUID getIncomingPeerConnectionId() {

        return mIncomingPeerConnectionId;
    }

    String getFrom() {

        return mConversation.getFrom();
    }

    String getTo() {

        return mConversation.getTo();
    }

    /**
     * Read the next data chunk to be sent for the file.
     *
     * @param fileDescriptorImpl file descriptor being sent.
     * @param chunkStart the current chunk offset position.
     * @param chunkSize the current chunk size to read.
     * @return the data chunk to send ot null.
     */
    @Nullable
    byte[] readChunk(@Nullable File filesDir, @NonNull FileDescriptorImpl fileDescriptorImpl, long chunkStart, int chunkSize) {
        if (DEBUG) {
            Log.d(LOG_TAG, "readChunk: fileDescriptorImpl=" + fileDescriptorImpl + " chunkStart=" + chunkStart + " chunkSize=" + chunkSize);
        }

        try {
            String path = fileDescriptorImpl.getPath();
            if (mSendingFiles == null) {
                mSendingFiles = new HashMap<>();
            }

            SendingFileInfo sendingFileInfo = mSendingFiles.get(path);
            if (sendingFileInfo == null) {

                if (filesDir == null) {

                    return null;
                }

                File file = new File(filesDir, path);
                FileInfoImpl fileInfo = new FileInfoImpl(1, path, fileDescriptorImpl.getLength(), 0);
                fileInfo.setRemoteOffset(chunkStart);
                sendingFileInfo = new SendingFileInfo(file, fileInfo);
                mSendingFiles.put(path, sendingFileInfo);
            }

            long position = sendingFileInfo.getPosition();
            long remaining = sendingFileInfo.getLength() - position;
            if (remaining <= 0) {

                return null;
            }
            if (position != chunkStart) {

                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Incorrect offset ", chunkStart, " expecting ", position);
                }
                return null;
            }
            if (remaining < chunkSize) {
                chunkSize = (int) remaining;
            }

            byte[] bytes = new byte[chunkSize];
            if (sendingFileInfo.read(bytes) != chunkSize) {

                return null;
            }

            return bytes;

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot read file: ", exception);
            }
            return null;
        }
    }

    long writeChunk(@Nullable File filesDir, @NonNull FileDescriptorImpl fileDescriptorImpl, long chunkStart, @Nullable byte[] chunk) {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeChunk: fileDescriptorImpl=" + fileDescriptorImpl + " chunkStart=" + chunkStart + " chunk=" + Arrays.toString(chunk));
        }

        try {
            String path = fileDescriptorImpl.getPath();
            if (mReceivingFiles == null) {
                mReceivingFiles = new HashMap<>();
            }

            ReceivingFileInfo receivingFileInfo = mReceivingFiles.get(path);
            if (receivingFileInfo == null) {

                if (filesDir == null) {

                    return -1;
                }

                File file = new File(filesDir, path);
                FileInfoImpl fileInfo = new FileInfoImpl(1, path, fileDescriptorImpl.getLength(), 0);
                if (chunk == null) {
                    receivingFileInfo = new ReceivingFileInfo(file, fileInfo, Long.MAX_VALUE);
                    mReceivingFiles.put(path, receivingFileInfo);
                    fileInfo.setRemoteOffset(receivingFileInfo.getPosition());
                    return receivingFileInfo.getPosition();
                } else {
                    fileInfo.setRemoteOffset(chunkStart);
                    receivingFileInfo = new ReceivingFileInfo(file, fileInfo, chunkStart);
                    mReceivingFiles.put(path, receivingFileInfo);
                }
            }
            if (chunk == null) {

                return -1;
            }
            if (chunkStart != receivingFileInfo.getPosition()) {

                return -1;
            }

            receivingFileInfo.write(chunk, chunk.length);
            if (receivingFileInfo.getPosition() == fileDescriptorImpl.getLength()) {
                receivingFileInfo.close();
                mReceivingFiles.remove(path);
            }

            return receivingFileInfo.getPosition();

        } catch (Exception exception) {
            return -1;
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder("Connection[");
        stringBuilder.append(" peerConnectionId=").append(mPeerConnectionId);
        if (mIncomingPeerConnectionId != null || mOutgoingPeerConnectionId != null) {
            stringBuilder.append(" peerVersion=").append(mPeerMajorVersion).append(".").append(mPeerMinorVersion);
            stringBuilder.append(" incomingState=").append(mIncomingState);
            stringBuilder.append(" outgoingState=").append(mOutgoingState);
            stringBuilder.append(" incomingPeerConnectionId=").append(mIncomingPeerConnectionId);
            stringBuilder.append(" outgoingPeerConnectionId=").append(mOutgoingPeerConnectionId);
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }
}
