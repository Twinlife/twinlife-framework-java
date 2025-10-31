/*
 *  Copyright (c) 2016-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_17;

class PushTransientObjectOperation extends Operation {
    private static final String LOG_TAG = "PushTransientObjectOp..";
    private static final boolean DEBUG = false;

    @NonNull
    private final TransientObjectDescriptorImpl mTransientObjectDescriptorImpl;

    PushTransientObjectOperation(@NonNull ConversationImpl conversationImpl,
                                 @NonNull TransientObjectDescriptorImpl transientObjectDescriptorImpl) {

        super(Operation.Type.PUSH_TRANSIENT_OBJECT, conversationImpl, transientObjectDescriptorImpl);

        mTransientObjectDescriptorImpl = transientObjectDescriptorImpl;
    }

    public void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" transientObjectDescriptorImpl=");
            stringBuilder.append(mTransientObjectDescriptorImpl);
            stringBuilder.append("\n");
        }
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        final TransientObjectDescriptorImpl transientObjectDescriptorImpl = mTransientObjectDescriptorImpl;
        final long requestId = connection.newRequestId();
        transientObjectDescriptorImpl.setSentTimestamp(System.currentTimeMillis());
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_17)) {
            final PushTransientIQ pushTransientIQ = new PushTransientIQ(PushTransientIQ.IQ_PUSH_TRANSIENT_SERIALIZER, requestId,
                    transientObjectDescriptorImpl, 0);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_TRANSIENT, pushTransientIQ);

        } else {
            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.PushTransientObjectIQ pushTransientIQ = new ConversationServiceIQ.PushTransientObjectIQ(connection.getFrom(),
                    connection.getTo(), requestId, majorVersion, minorVersion, transientObjectDescriptorImpl);

            final byte[] content = pushTransientIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_PUSH_TRANSIENT, content);
        }

        // The PushTransientOperation has no acknowledge: we must remove the operation now.
        return ErrorCode.SUCCESS;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushTransientObjectOperation:\n");
            appendTo(stringBuilder);
            return stringBuilder.toString();
        } else {
            return "To";
        }
    }
}
