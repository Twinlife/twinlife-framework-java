/*
 *  Copyright (c) 2019-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "schemaId":"705be6f2-c157-4f75-8325-e0e70bd04312",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushGeolocationOperation",
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

class PushGeolocationOperation extends Operation {
    private static final String LOG_TAG = "PushGeolocationOp..";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("705be6f2-c157-4f75-8325-e0e70bd04312");

    @Nullable
    private volatile GeolocationDescriptorImpl mGeolocationDescriptorImpl;

    PushGeolocationOperation(@NonNull ConversationImpl conversationImpl,
                             @NonNull GeolocationDescriptorImpl geolocationDescriptorImpl) {

        super(Type.PUSH_GEOLOCATION, conversationImpl, geolocationDescriptorImpl);

        mGeolocationDescriptorImpl = geolocationDescriptorImpl;
    }

    PushGeolocationOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptorId) {
        super(id, Operation.Type.PUSH_GEOLOCATION, conversationId, creationDate, descriptorId);
    }

    @Nullable
    GeolocationDescriptorImpl getGeolocationDescriptorImpl() {

        return mGeolocationDescriptorImpl;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        GeolocationDescriptorImpl geolocationDescriptorImpl = getGeolocationDescriptorImpl();
        if (geolocationDescriptorImpl == null) {
            DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (!(descriptorImpl instanceof GeolocationDescriptorImpl)) {
                return ErrorCode.EXPIRED;
            }
            geolocationDescriptorImpl = (GeolocationDescriptorImpl) descriptorImpl;
            mGeolocationDescriptorImpl = geolocationDescriptorImpl;
        }

        if (!connection.preparePush(geolocationDescriptorImpl)) {
            return ErrorCode.EXPIRED;
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_12)) {
            final PushGeolocationIQ pushGeolocationIQ = new PushGeolocationIQ(PushGeolocationIQ.IQ_PUSH_GEOLOCATION_SERIALIZER, requestId, geolocationDescriptorImpl);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_GEOLOCATION, pushGeolocationIQ);
            return ErrorCode.QUEUED;

        } else if (geolocationDescriptorImpl.getSendTo() == null && geolocationDescriptorImpl.getExpireTimeout() == 0 && geolocationDescriptorImpl.getReplyToDescriptorId() == null) {

            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.PushGeolocationIQ pushGeolocationIQ = new ConversationServiceIQ.PushGeolocationIQ(connection.getFrom(), connection.getTo(),
                    requestId, majorVersion, minorVersion, geolocationDescriptorImpl);
            final byte[] content = pushGeolocationIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_PUSH_GEOLOCATION, content);
            return ErrorCode.QUEUED;

        } else {

            // The descriptor contains a parameter not supported by old versions.
            return connection.operationNotSupported(geolocationDescriptorImpl);
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
            stringBuilder.append("PushGeolocationOperation:\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
