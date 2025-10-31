/*
 *  Copyright (c) 2019-2025 twinlife SA.
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
 *
 * {
 *  "schemaId":"c8ac4c45-525c-44d4-bf44-f542c9928a7a",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushTwincodeOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
 *  "fields":
 *  [
 *   {"name":"twincodeOutboundId", "type":"UUID"}
 *   {"name":"sequenceId", "type":"long"}
 *  ]
 * }
 *
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
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_18;

public class PushTwincodeOperation extends Operation {
    private static final String LOG_TAG = "PushTwincodeOperation";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("c8ac4c45-525c-44d4-bf44-f542c9928a7a");

    @Nullable
    private volatile TwincodeDescriptorImpl mTwincodeDescriptorImpl;

    PushTwincodeOperation(@NonNull ConversationImpl conversationImpl,
                          @NonNull TwincodeDescriptorImpl twincodeDescriptorImpl) {

        super(Type.PUSH_TWINCODE, conversationImpl, twincodeDescriptorImpl);

        mTwincodeDescriptorImpl = twincodeDescriptorImpl;
    }

    PushTwincodeOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptorId) {
        super(id, Operation.Type.PUSH_TWINCODE, conversationId, creationDate, descriptorId);
    }

    TwincodeDescriptorImpl getTwincodeDescriptorImpl() {

        return mTwincodeDescriptorImpl;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        TwincodeDescriptorImpl twincodeDescriptorImpl = mTwincodeDescriptorImpl;
        if (twincodeDescriptorImpl == null) {
            DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (!(descriptorImpl instanceof TwincodeDescriptorImpl)) {
                return ErrorCode.EXPIRED;
            }
            twincodeDescriptorImpl = (TwincodeDescriptorImpl) descriptorImpl;
            mTwincodeDescriptorImpl = twincodeDescriptorImpl;
        }

        if (!connection.preparePush(twincodeDescriptorImpl)) {
            return ErrorCode.EXPIRED;
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_18)) {
            final PushTwincodeIQ pushTwincodeIQ = new PushTwincodeIQ(PushTwincodeIQ.IQ_PUSH_TWINCODE_SERIALIZER_3, requestId, twincodeDescriptorImpl);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_TWINCODE, pushTwincodeIQ);
            return ErrorCode.QUEUED;

        } else if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_12)) {
            final PushTwincodeIQ pushTwincodeIQ = new PushTwincodeIQ(PushTwincodeIQ.IQ_PUSH_TWINCODE_SERIALIZER_2, requestId, twincodeDescriptorImpl);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_TWINCODE, pushTwincodeIQ);
            return ErrorCode.QUEUED;

        } else if (twincodeDescriptorImpl.getSendTo() == null && twincodeDescriptorImpl.getExpireTimeout() == 0 && twincodeDescriptorImpl.getReplyToDescriptorId() == null) {

            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.PushTwincodeIQ pushTwincodeIQ = new ConversationServiceIQ.PushTwincodeIQ(connection.getFrom(), connection.getTo(),
                    requestId, majorVersion, minorVersion, twincodeDescriptorImpl);

            final byte[] content = pushTwincodeIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_PUSH_TWINCODE, content);
            return ErrorCode.QUEUED;

        } else {
            // The descriptor contains a parameter not supported by old versions.
            return connection.operationNotSupported(twincodeDescriptorImpl);
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushTwincodeOperation:\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "To";
        }
    }
}
