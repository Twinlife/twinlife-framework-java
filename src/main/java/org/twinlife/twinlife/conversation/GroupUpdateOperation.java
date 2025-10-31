/*
 *  Copyright (c) 2018-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 2
 *  Date: 2024/09/09
 * {
 *  "schemaId":"493e6d32-a023-455a-9952-c76162c319c9",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"GroupOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "fields":
 *  [
 *   {"name":"groupTwincodeId", "type":"UUID"}
 *   {"name":"memberTwincodeId", "type":"UUID"}
 *   {"name":"permissions", "type":"long"}
 *   {"name":"publicKey", "type":"String"}
 *   {"name":"signedOffTwincodeId", "type":[null, "UUID"]}
 *   {"name":"signature", "type":"String"}
 *  ]
 * }
 * </pre>
 *
 * Schema version 1
 *
 * {
 *  "schemaId":"493e6d32-a023-455a-9952-c76162c319c9",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"GroupOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "fields":
 *  [
 *   {"name":"groupTwincodeId", "type":"UUID"}
 *   {"name":"memberTwincodeId", "type":"UUID"}
 *   {"name":"permissions", "type":"long"}
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
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_17;

class GroupUpdateOperation extends GroupOperation {
    private static final String LOG_TAG = "GroupUpdateOperation";
    private static final boolean DEBUG = false;

    private final long mPermissions;

    GroupUpdateOperation(@NonNull ConversationImpl conversationImpl,
                         @NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId, long permissions) {

        super(Type.UPDATE_GROUP_MEMBER, conversationImpl, groupTwincodeId, memberTwincodeId);

        mPermissions = permissions;
    }

    GroupUpdateOperation(long id, @NonNull Type type, @NonNull DatabaseIdentifier conversationId,
                         long creationDate, long descriptor, @Nullable byte[] content) {

        super(id, type, conversationId, creationDate, descriptor);

        long permissions = 0;
        if (content != null) {
            try {
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                final BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

                final int schemaVersion = decoder.readInt();
                if (schemaVersion == SCHEMA_VERSION_1 || schemaVersion == SCHEMA_VERSION_2) {
                    mGroupId = decoder.readUUID();
                    mMemberId = decoder.readUUID();
                    permissions = decoder.readLong();
                }

            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "deserialize", exception);
                }
            }
        }
        mPermissions = permissions;
    }

    @Override
    @Nullable
    byte[] serialize() {

        return serializeOperation(mGroupId, mMemberId, mPermissions, null, null, null);
    }

    public void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" permissions=");
            stringBuilder.append(mPermissions);
        }
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        if (mGroupId == null || mMemberId == null) {
            return ErrorCode.EXPIRED;
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_17)) {
            final UpdatePermissionsIQ updatePermissionsIQ = new UpdatePermissionsIQ(UpdatePermissionsIQ.IQ_UPDATE_PERMISSIONS_SERIALIZER, requestId,
                    mGroupId, mMemberId, mPermissions);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_UPDATE_GROUP_MEMBER, updatePermissionsIQ);
            return ErrorCode.QUEUED;

        } else {
            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.UpdateGroupMemberIQ updateGroupMemberIQ = new ConversationServiceIQ.UpdateGroupMemberIQ(connection.getFrom(), connection.getTo(),
                    requestId, majorVersion, minorVersion, mGroupId, mMemberId, mPermissions);

            final byte[] content = updateGroupMemberIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_UPDATE_GROUP_MEMBER, content);
            return ErrorCode.QUEUED;
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
            stringBuilder.append("GroupUpdateOperation:");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Go";
        }
    }
}
