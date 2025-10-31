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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

abstract class GroupOperation extends Operation {
    // private static final String LOG_TAG = "GroupOperation";
    // private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("493e6d32-a023-455a-9952-c76162c319c9");
    static final int SCHEMA_VERSION_1 = 1;
    static final int SCHEMA_VERSION_2 = 2;

    @Nullable
    protected UUID mMemberId;
    @Nullable
    protected UUID mGroupId;

    GroupOperation(@NonNull Type type, @NonNull ConversationImpl conversationImpl,
                   @NonNull InvitationDescriptorImpl invitationDescriptor) {
        super(type, conversationImpl, invitationDescriptor);

        mGroupId = null;
        mMemberId = null;
    }

    GroupOperation(@NonNull Type type, @NonNull ConversationImpl conversationImpl,
                   @NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId) {
        super(type, conversationImpl);

        mGroupId = groupTwincodeId;
        mMemberId = memberTwincodeId;
    }

    GroupOperation(long id, @NonNull Type type, @NonNull DatabaseIdentifier conversationId,
                   long creationDate, long descriptor) {
        super(id, type, conversationId, creationDate, descriptor);

        mGroupId = null;
        mMemberId = null;
    }

    @Nullable
    public UUID getGroupId() {

        return mGroupId;
    }

    @Nullable
    public UUID getMemberId() {

        return mMemberId;
    }

    @Nullable
    static byte[] serializeOperation(@Nullable UUID groupId, @Nullable UUID memberId, long permissions,
                                     @Nullable String publicKey, @Nullable UUID signedOffTwincodeId, @Nullable String signature) {

        if (groupId == null || memberId == null) {
            return null;
        }

        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);

            encoder.writeInt(publicKey == null ? SCHEMA_VERSION_1 : SCHEMA_VERSION_2);
            encoder.writeUUID(groupId);
            encoder.writeUUID(memberId);
            encoder.writeLong(permissions);
            if (publicKey != null) {
                encoder.writeString(publicKey);
                encoder.writeOptionalUUID(signedOffTwincodeId);
                encoder.writeOptionalString(signature);
            }
            return outputStream.toByteArray();

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error("serializeOperation", "serialize", exception);
            }
            return null;
        }
    }

    public void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" groupTwincodeId=");
            stringBuilder.append(mGroupId);
            stringBuilder.append(" memberTwincodeId=");
            stringBuilder.append(mMemberId);
        }
    }

    /*@Override
    public ErrorCode execute(@NonNull ConversationServiceImpl conversationService,
                             @NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        return ErrorCode.QUEUED;
    }*/

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("GroupOperation:");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Go";
        }
    }
}
