/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 1
 *  Date: 2020/01/24
 *
 * {
 *  "schemaId":"ca15db2f-beda-40a3-84d9-7c3fee25dc5d",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"CallDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor"
 *  "fields":
 *  [
 *   {"name":"video", "type":"boolean"]},
 *   {"name":"incoming", "type":"boolean"},
 *   {"name":"accepted", "type":"boolean"},
 *   {"name":"duration", "type":"long"},
 *   {"name":"terminateReason", "type": ["null", "busy", "cancel", "connectivity_error", "decline", "disconnected",
 *       "general_error", "gone", "not_authorized", "success", "revoked", "timeout", "unkown"]}
 *  ]
 * }
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.TerminateReason;

import java.util.UUID;

public class CallDescriptorImpl extends DescriptorImpl implements ConversationService.CallDescriptor {
    private static final String LOG_TAG = "CallDescriptorImpl...";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("ca15db2f-beda-40a3-84d9-7c3fee25dc5d");
    static final int SCHEMA_VERSION_1 = 1;

    static class CallDescriptorImplSerializer_1 extends DescriptorImplSerializer_3 {

        CallDescriptorImplSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, CallDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            CallDescriptorImpl callDescriptorImpl = (CallDescriptorImpl) object;

            encoder.writeBoolean(callDescriptorImpl.mVideo);
            encoder.writeBoolean(callDescriptorImpl.mIncomingCall);
            encoder.writeBoolean(callDescriptorImpl.mAcceptedCall);
            encoder.writeLong(callDescriptorImpl.mDuration);
            encoder.writeEnum(fromTerminateReason(callDescriptorImpl.mTerminateReason));
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            boolean video = decoder.readBoolean();
            boolean incomingCall = decoder.readBoolean();
            boolean acceptedCall = decoder.readBoolean();
            long duration = decoder.readLong();
            TerminateReason terminateReason = toTerminateReason(decoder.readEnum());

            return new CallDescriptorImpl(descriptorImpl, video, incomingCall, acceptedCall, duration, terminateReason);
        }
    }

    static int fromTerminateReason(@Nullable TerminateReason terminateReason) {
        if (terminateReason != null) {
            switch (terminateReason) {
                case BUSY:
                    return 1;
                case CANCEL:
                    return 2;
                case CONNECTIVITY_ERROR:
                    return 3;
                case DECLINE:
                    return 4;
                case DISCONNECTED:
                    return 5;
                case GENERAL_ERROR:
                    return 6;
                case GONE:
                    return 7;
                case NOT_AUTHORIZED:
                    return 8;
                case SUCCESS:
                    return 9;
                case REVOKED:
                    return 10;
                case TIMEOUT:
                    return 11;
                case UNKNOWN:
                    return 12;
                case TRANSFER_DONE:
                    return 13;
            }
        }
        return 0;
    }

    @Nullable
    static TerminateReason toTerminateReason(int value) {
        switch (value) {
            case 0:
                return null;
            case 1:
                return TerminateReason.BUSY;
            case 2:
                return TerminateReason.CANCEL;
            case 3:
                return TerminateReason.CONNECTIVITY_ERROR;
            case 4:
                return TerminateReason.DECLINE;
            case 5:
                return TerminateReason.DISCONNECTED;
            case 6:
                return TerminateReason.GENERAL_ERROR;
            case 7:
                return TerminateReason.GONE;
            case 8:
                return TerminateReason.NOT_AUTHORIZED;
            case 9:
                return TerminateReason.SUCCESS;
            case 10:
                return TerminateReason.REVOKED;
            case 11:
                return TerminateReason.TIMEOUT;
            case 12:
            default:
                return TerminateReason.UNKNOWN;
            case 13:
                return TerminateReason.TRANSFER_DONE;
        }
    }

    static final CallDescriptorImplSerializer_1 SERIALIZER_1 = new CallDescriptorImplSerializer_1();

    private final boolean mVideo;
    private final boolean mIncomingCall;
    private volatile boolean mAcceptedCall;
    private volatile long mDuration;
    private volatile TerminateReason mTerminateReason;

    CallDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, boolean video, boolean incomingCall) {

        super(descriptorId, cid, 0, null, null);

        if (DEBUG) {
            Log.d(LOG_TAG, "CallDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid
                    + " video=" + video + " incomingCall=" + incomingCall);
        }
        mVideo = video;
        mIncomingCall = incomingCall;
        mAcceptedCall = false;
        mDuration = 0;
        mTerminateReason = null;
    }

    CallDescriptorImpl(@NonNull ConversationService.DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                       @Nullable ConversationService.DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                       long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                       int flags, String content, long length) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout);

        if (DEBUG) {
            Log.d(LOG_TAG, "CallDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " length=" + length);
        }

        mDuration = length;
        mVideo = (flags & FLAG_VIDEO) != 0;
        mIncomingCall = (flags & FLAG_INCOMING_CALL) != 0;
        mAcceptedCall = (flags & FLAG_ACCEPTED_CALL) != 0;

        final String[] args = extract(content);
        mTerminateReason = toTerminateReason((int) extractLong(args, 0, 0));
    }

    /*
     * Override Descriptor methods
     */

    @Override
    public Type getType() {

        return Type.CALL_DESCRIPTOR;
    }

    @Override
    @Nullable
    String serialize() {

        return Integer.toString(fromTerminateReason(mTerminateReason));
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" video=");
        stringBuilder.append(mVideo);
        stringBuilder.append("\n");
        stringBuilder.append(" incomingCall=");
        stringBuilder.append(mIncomingCall);
        stringBuilder.append("\n");
        stringBuilder.append(" acceptedCall=");
        stringBuilder.append(mAcceptedCall);
        stringBuilder.append("\n");
        stringBuilder.append(" duration=");
        stringBuilder.append(mDuration);
        stringBuilder.append("\n");
    }

    /*
     * Override CallDescriptor methods
     */
    @Override
    public boolean isIncoming() {

        return mIncomingCall;
    }

    @Override
    public boolean isAccepted() {

        return mAcceptedCall;
    }

    @Override
    public boolean isVideo() {

        return mVideo;
    }

    @Override
    public long getDuration() {

        return mDuration;
    }

    @Override
    public TerminateReason getTerminateReason() {

        return mTerminateReason;
    }

    @Override
    int getFlags() {
        int result = mVideo ? FLAG_VIDEO : 0;
        result |= mIncomingCall ? FLAG_INCOMING_CALL : 0;
        result |= mAcceptedCall ? FLAG_ACCEPTED_CALL : 0;

        return result;
    }

    long getValue() {

        return mDuration;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("CallDescriptorImpl\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Package specific Methods
    //

    public void setTerminateReason(TerminateReason terminateReason) {

        if (getReadTimestamp() > 0) {
            mDuration = System.currentTimeMillis() - getReadTimestamp();
        } else {
            mDuration = 0;
        }
        mTerminateReason = terminateReason;
    }

    void setAcceptedCall() {

        setReadTimestamp(System.currentTimeMillis());
        mAcceptedCall = true;
    }

    //
    // Private Methods
    //

    private CallDescriptorImpl(@NonNull DescriptorImpl descriptorImpl, boolean video, boolean incomingCall, boolean acceptedCall, long duration,
                               TerminateReason terminateReason) {

        super(descriptorImpl);

        if (DEBUG) {
            Log.d(LOG_TAG, "CallDescriptorImpl: descriptorImpl=" + descriptorImpl
                    + " video=" + video + " incomingCall=" + incomingCall + " acceptedCall=" + acceptedCall
                    + " duration=" + duration + " terminateReason=" + terminateReason);
        }

        mVideo = video;
        mIncomingCall = incomingCall;
        mAcceptedCall = acceptedCall;
        mDuration = duration;
        mTerminateReason = terminateReason;
    }
}
