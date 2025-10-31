/*
 *  Copyright (c) 2016-2025 twinlife SA.
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
 *  "schemaId":"50c7142b-bc18-4592-89fc-eaecf55ac38d",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushObjectOperation",
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

public class PushObjectOperation extends Operation {
    private static final String LOG_TAG = "PushObjectOperation";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("50c7142b-bc18-4592-89fc-eaecf55ac38d");

    @Nullable
    private volatile ObjectDescriptorImpl mObjectDescriptorImpl;

    PushObjectOperation(@NonNull ConversationImpl conversationImpl,
                        @NonNull ObjectDescriptorImpl objectDescriptorImpl) {

        super(Operation.Type.PUSH_OBJECT, conversationImpl, objectDescriptorImpl);

        mObjectDescriptorImpl = objectDescriptorImpl;
    }

    PushObjectOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptorId) {
        super(id, Operation.Type.PUSH_OBJECT, conversationId, creationDate, descriptorId);
    }

    ObjectDescriptorImpl getObjectDescriptorImpl() {

        return mObjectDescriptorImpl;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        ObjectDescriptorImpl objectDescriptorImpl = mObjectDescriptorImpl;
        if (objectDescriptorImpl == null) {
            DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (!(descriptorImpl instanceof ObjectDescriptorImpl)) {
                return ErrorCode.EXPIRED;
            }
            objectDescriptorImpl = (ObjectDescriptorImpl) descriptorImpl;
            mObjectDescriptorImpl = objectDescriptorImpl;
        }

        if (!connection.preparePush(objectDescriptorImpl)) {
            return ErrorCode.EXPIRED;
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_12)) {
            final PushObjectIQ pushObjectIQ = new PushObjectIQ(PushObjectIQ.IQ_PUSH_OBJECT_SERIALIZER, requestId, objectDescriptorImpl);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_OBJECT, pushObjectIQ);
            return ErrorCode.QUEUED;

        } else if (objectDescriptorImpl.getSendTo() == null && objectDescriptorImpl.getExpireTimeout() == 0 && objectDescriptorImpl.getReplyToDescriptorId() == null) {

            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.PushObjectIQ pushObjectIQ = new ConversationServiceIQ.PushObjectIQ(connection.getFrom(), connection.getTo(), requestId, majorVersion, minorVersion,
                    objectDescriptorImpl);

            final byte[] content = pushObjectIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_PUSH_OBJECT, content);
            return ErrorCode.QUEUED;

        } else {

            // The descriptor contains a parameter not supported by old versions.
            return connection.operationNotSupported(objectDescriptorImpl);
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
            stringBuilder.append("PushObjectOperation:");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
