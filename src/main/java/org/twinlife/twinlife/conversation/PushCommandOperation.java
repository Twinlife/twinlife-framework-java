/*
 *  Copyright (c) 2020-2025 twinlife SA.
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

class PushCommandOperation extends Operation {
    private static final String LOG_TAG = "PushCommandOperation";
    private static final boolean DEBUG = false;

    @NonNull
    private final TransientObjectDescriptorImpl mCommandDescriptorImpl;

    PushCommandOperation(@NonNull ConversationImpl conversationImpl, @NonNull TransientObjectDescriptorImpl commandDescriptorImpl) {

        super(Operation.Type.PUSH_COMMAND, conversationImpl, commandDescriptorImpl);

        mCommandDescriptorImpl = commandDescriptorImpl;
    }

    @NonNull
    TransientObjectDescriptorImpl getCommandDescriptorImpl() {

        return mCommandDescriptorImpl;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        final TransientObjectDescriptorImpl commandDescriptorImpl = getCommandDescriptorImpl();
        final long requestId = connection.newRequestId();
        updateRequestId(requestId);

        commandDescriptorImpl.setSentTimestamp(System.currentTimeMillis());
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_17)) {
            final PushTransientIQ pushCommandIQ = new PushTransientIQ(PushCommandIQ.IQ_PUSH_COMMAND_SERIALIZER, requestId,
                    commandDescriptorImpl, 1);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_TRANSIENT, pushCommandIQ);
        } else {
            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.PushCommandIQ pushCommandIQ = new ConversationServiceIQ.PushCommandIQ(connection.getFrom(),
                    connection.getTo(), requestId, majorVersion, minorVersion, commandDescriptorImpl);

            final byte[] content = pushCommandIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_PUSH_TRANSIENT, content);
        }
        return ErrorCode.QUEUED;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushCommandOperation:\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Co";
        }
    }
}
