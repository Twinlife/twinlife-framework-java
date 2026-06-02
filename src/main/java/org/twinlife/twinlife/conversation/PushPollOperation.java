/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "schemaId":"6c08dca7-4eb0-4eae-a01e-56456eef5a74",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushPollOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
 *  "fields":
 *  [
 *   {"name":"twincodeOutboundId", "type":"UUID"}
 *   {"name":"sequenceId", "type":"long"}
 *  ]
 * }
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;

import java.util.UUID;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_12;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_21;

class PushPollOperation extends Operation {
    private static final String LOG_TAG = "PushPollOperation";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("6c08dca7-4eb0-4eae-a01e-56456eef5a74");

    @Nullable
    private volatile PollDescriptorImpl mPollDescriptorImpl;

    PushPollOperation(@NonNull ConversationImpl conversationImpl,
                             @NonNull PollDescriptorImpl pollDescriptor) {

        super(Type.PUSH_POLL, conversationImpl, pollDescriptor);

        mPollDescriptorImpl = pollDescriptor;
    }

    PushPollOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptorId) {
        super(id, Type.PUSH_POLL, conversationId, creationDate, descriptorId);
    }

    @Nullable
    PollDescriptorImpl getPollDescriptorImpl() {
        return mPollDescriptorImpl;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        PollDescriptorImpl pollDescriptorImpl = getPollDescriptorImpl();

        if (pollDescriptorImpl == null) {
            DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (!(descriptorImpl instanceof PollDescriptorImpl)) {
                return ErrorCode.EXPIRED;
            }
            pollDescriptorImpl = (PollDescriptorImpl) descriptorImpl;
            mPollDescriptorImpl = pollDescriptorImpl;
        }

        if (!connection.preparePush(pollDescriptorImpl)) {
            return ErrorCode.EXPIRED;
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_21)) {
            final PushPollIQ pushPollIQ = new PushPollIQ(PushPollIQ.IQ_PUSH_POLL_SERIALIZER, requestId, pollDescriptorImpl);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_POLL, pushPollIQ);
            return ErrorCode.QUEUED;

        } else {

            // Peer doesn't support polls.
            return connection.operationNotSupported(pollDescriptorImpl);
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        final StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("PushPollOperation:\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
