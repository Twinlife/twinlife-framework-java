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
import org.twinlife.twinlife.crypto.SignatureInfoIQ;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.util.UUID;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_18;

class GroupJoinOperation extends GroupOperation {
    private static final String LOG_TAG = "GroupJoinOperation";
    private static final boolean DEBUG = false;

    @Nullable
    private InvitationDescriptorImpl mInvitationDescriptorImpl;
    private long mPermissions;
    @Nullable
    private String mPublicKey;
    @Nullable
    private String mSignature;
    @Nullable
    private UUID mSignedOffTwincodeId;

    GroupJoinOperation(@NonNull ConversationImpl conversationImpl,
                       @NonNull InvitationDescriptorImpl invitationDescriptor) {
        super(Type.JOIN_GROUP, conversationImpl, invitationDescriptor);

        mInvitationDescriptorImpl = invitationDescriptor;
        mPermissions = 0;
        mPublicKey = null;
    }

    GroupJoinOperation(@NonNull ConversationImpl conversationImpl, Type type,
                       @NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId, long permissions,
                       @Nullable String publicKey, @Nullable UUID signedOffTwincodeId, @Nullable String signature) {

        super(type, conversationImpl, groupTwincodeId, memberTwincodeId);

        mPermissions = permissions;
        mInvitationDescriptorImpl = null;
        mPublicKey = publicKey;
        mSignedOffTwincodeId = signedOffTwincodeId;
        mSignature = signature;
    }

    GroupJoinOperation(long id, @NonNull Type type, @NonNull DatabaseIdentifier conversationId,
                       long creationDate, long descriptor, @Nullable byte[] content) {

        super(id, type, conversationId, creationDate, descriptor);

        mPermissions = 0;
        mInvitationDescriptorImpl = null;
        if (content != null) {
            try {
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                final BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

                final int schemaVersion = decoder.readInt();
                if (schemaVersion == SCHEMA_VERSION_1 || schemaVersion == SCHEMA_VERSION_2) {
                    mGroupId = decoder.readUUID();
                    mMemberId = decoder.readUUID();
                    mPermissions = decoder.readLong();
                    if (schemaVersion == SCHEMA_VERSION_2) {
                        mPublicKey = decoder.readString();
                        mSignedOffTwincodeId = decoder.readOptionalUUID();
                        mSignature = decoder.readOptionalString();
                    }
                }

            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "deserialize", exception);
                }
            }
        }
    }

    @Nullable
    InvitationDescriptorImpl getInvitationDescriptorImpl() {

        return mInvitationDescriptorImpl;
    }

    @Nullable
    public UUID getGroupId() {

        return mInvitationDescriptorImpl == null ? mGroupId : mInvitationDescriptorImpl.getGroupTwincodeId();
    }

    @Nullable
    public UUID getMemberId() {

        return mInvitationDescriptorImpl == null ? mMemberId : mInvitationDescriptorImpl.getMemberTwincodeId();
    }

    long getPermissions() {

        return mPermissions;
    }

    @Nullable
    String getPublicKey() {

        return mPublicKey;
    }

    @Nullable
    UUID getSignedOffTwincodeId() {

        return mSignedOffTwincodeId;
    }

    @Nullable
    String getSignature() {

        return mSignature;
    }

    @Override
    @Nullable
    byte[] serialize() {

        return serializeOperation(mGroupId, mMemberId, mPermissions, mPublicKey, mSignedOffTwincodeId, mSignature);
    }

    public void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" groupTwincodeId=");
            stringBuilder.append(mGroupId);
            stringBuilder.append(" memberTwincodeId=");
            stringBuilder.append(mMemberId);
            stringBuilder.append(" publicKey=");
            stringBuilder.append(mPublicKey);
        }
    }

    @Override
    public ErrorCode executeInvoke(@NonNull ConversationServiceImpl conversationService,
                                   @NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "executeInvoke: conversationImpl=" + conversationImpl);
        }

        if (mType == Type.INVOKE_JOIN_GROUP) {
            return conversationService.invokeJoinOperation(conversationImpl, this);
        }
        if (mType == Type.INVOKE_ADD_MEMBER) {
            return conversationService.invokeAddMemberOperation(conversationImpl, this);
        }
        return ErrorCode.SUCCESS;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        InvitationDescriptorImpl invitationDescriptorImpl = getInvitationDescriptorImpl();
        if (invitationDescriptorImpl == null && getDescriptorId() != 0) {
            final DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (!(descriptorImpl instanceof InvitationDescriptorImpl)) {
                return ErrorCode.EXPIRED;
            }
            invitationDescriptorImpl = (InvitationDescriptorImpl) descriptorImpl;
            mInvitationDescriptorImpl = invitationDescriptorImpl;
        }

        final UUID groupTwincodeId = getGroupId();
        if (groupTwincodeId == null) {
            return ErrorCode.EXPIRED;
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_18)) {
            if (invitationDescriptorImpl == null) {
                return ErrorCode.EXPIRED;
            }

            final SignatureInfoIQ signatureInfoIQ = connection.createSignature(groupTwincodeId);
            final JoinGroupIQ joinGroupIQ;
            if (signatureInfoIQ != null) {
                joinGroupIQ = new JoinGroupIQ(JoinGroupIQ.IQ_JOIN_GROUP_SERIALIZER_2, requestId,
                        invitationDescriptorImpl.getDescriptorId(), groupTwincodeId, signatureInfoIQ.twincodeOutboundId,
                        signatureInfoIQ.publicKey, signatureInfoIQ.secret);
            } else {
                joinGroupIQ = new JoinGroupIQ(JoinGroupIQ.IQ_JOIN_GROUP_SERIALIZER_2, requestId,
                        invitationDescriptorImpl.getDescriptorId(), groupTwincodeId, null, null, null);
            }
            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_JOIN_GROUP, joinGroupIQ);

        } else {
            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.JoinGroupIQ joinGroupIQ;
            if (invitationDescriptorImpl != null) {
                joinGroupIQ = new ConversationServiceIQ.JoinGroupIQ(connection.getFrom(), connection.getTo(),
                        requestId, majorVersion, minorVersion, invitationDescriptorImpl);
            } else {
                joinGroupIQ = new ConversationServiceIQ.JoinGroupIQ(connection.getFrom(), connection.getTo(),
                        requestId, majorVersion, minorVersion, groupTwincodeId,
                        getMemberId(), getPermissions());
            }
            final byte[] content = joinGroupIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_JOIN_GROUP, content);
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
            stringBuilder.append("GroupJoinOperation:");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Go";
        }
    }
}
