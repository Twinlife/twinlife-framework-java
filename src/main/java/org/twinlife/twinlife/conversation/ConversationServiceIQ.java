/*
 *  Copyright (c) 2015-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Houssem Temanni (Houssem.Temanni@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.SerializerFactoryImpl;
import org.twinlife.twinlife.util.ServiceRequestIQ;
import org.twinlife.twinlife.util.ServiceResultIQ;
import org.twinlife.twinlife.util.Utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class ConversationServiceIQ {
    public static final String TWINLIFE_NAME = "conversation";

    static final int SERIALIZER_BUFFER_DEFAULT_SIZE = 1024;

    /*
     * <pre>
     *
     * Schema version 3
     *  Date: 2018/10/01
     *  Add support for group reset conversation
     *
     * {
     *  "schemaId":"412f43fa-bee9-4268-ac6f-98e99e457d03",
     *  "schemaVersion":"3",
     *
     *  "type":"record",
     *  "name":"ResetConversationIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {"name":"minSequenceId", "type":"long"},
     *   {"name":"peerMinSequenceId", "type":"long"},
     *   {"name":"count", "type":"long"},
     *   [{"name":"memberTwincodeOutboundId","type":"uuid"},
     *    {"name":"peerMinSequenceId","type","long"}]
     *  ]
     * }
     * </pre>
     */

    static final String RESET_CONVERSATION_ACTION = "reset-conversation";

    static final String RESET_CONVERSATION_ACTION_1 = "twinlife:conversation:reset-conversation";

    static class ResetConversationIQ extends ServiceRequestIQ {
        static final int SCHEMA_VERSION = 3;

        static class ResetConversationIQSerializer extends ServiceRequestIQSerializer {

            ResetConversationIQSerializer() {

                super(org.twinlife.twinlife.conversation.ResetConversationIQ.SCHEMA_ID, SCHEMA_VERSION, ResetConversationIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                ResetConversationIQ resetConversationIQ = (ResetConversationIQ) object;
                encoder.writeLong(resetConversationIQ.minSequenceId);
                encoder.writeLong(resetConversationIQ.peerMinSequenceId);
                if (resetConversationIQ.resetMembers == null) {
                    encoder.writeLong(0);
                } else {
                    encoder.writeLong(resetConversationIQ.resetMembers.size());
                    for (DescriptorId member : resetConversationIQ.resetMembers) {
                        encoder.writeUUID(member.twincodeOutboundId);
                        encoder.writeLong(member.sequenceId);
                    }
                }
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);
                long minSequenceId = decoder.readLong();
                long peerMinSequenceId = decoder.readLong();
                long count = decoder.readLong();
                List<DescriptorId> members = null;
                if (count > 0) {
                    members = new ArrayList<>();
                    while (count > 0) {
                        count--;
                        UUID memberTwincodeOutboundId = decoder.readUUID();
                        long memberPeerMinSeqenceId = decoder.readLong();
                        members.add(new DescriptorId(0, memberTwincodeOutboundId, memberPeerMinSeqenceId));
                    }
                }
                return new ResetConversationIQ(serviceRequestIQ, minSequenceId, peerMinSequenceId, members);
            }
        }

        static final ResetConversationIQSerializer SERIALIZER = new ResetConversationIQSerializer();

        final long minSequenceId;
        final long peerMinSequenceId;
        @Nullable
        final List<DescriptorId> resetMembers;

        ResetConversationIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, long minSequenceId, long peerMinSequenceId,
                            @Nullable List<DescriptorId> members) {

            super(from, to, requestId, TWINLIFE_NAME, majorVersion == ConversationServiceImpl.MAJOR_VERSION_1 ? RESET_CONVERSATION_ACTION_1 : RESET_CONVERSATION_ACTION,
                    majorVersion, minorVersion);

            this.minSequenceId = minSequenceId;
            this.peerMinSequenceId = peerMinSequenceId;
            this.resetMembers = members;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" minSequenceId=");
            stringBuilder.append(minSequenceId);
            stringBuilder.append("\n");
            stringBuilder.append(" peerMinSequenceId=");
            stringBuilder.append(peerMinSequenceId);
            stringBuilder.append("\n");
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion == ConversationServiceImpl.MAJOR_VERSION_2 && minorVersion >= ConversationServiceImpl.MINOR_VERSION_7) {
                ResetConversationIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            } else {
                throw new UnsupportedOperationException();
            }
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("ResetConversationIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private ResetConversationIQ(@NonNull ServiceRequestIQ serviceRequestIQ, long minSequenceId, long peerMinSequenceId,
                                    @Nullable List<DescriptorId> members) {

            super(serviceRequestIQ);

            this.minSequenceId = minSequenceId;
            this.peerMinSequenceId = peerMinSequenceId;
            this.resetMembers = members;
        }
    }

    /*
     * <pre>
     *
     * Schema version 2
     *  Date: 2016/09/08
     *
     * {
     *  "schemaId":"09e855f4-61d9-4acf-92ce-8f93c6951fb0",
     *  "schemaVersion":"2",
     *
     *  "type":"record",
     *  "name":"OnResetConversationIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":
     *  []
     * }
     * </pre>
     */

    static final String ON_RESET_CONVERSATION_ACTION = "on-reset-conversation";

    static final String ON_RESET_CONVERSATION_ACTION_1 = "twinlife:conversation:on-reset-conversation";

    static class OnResetConversationIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 2;

        static class OnResetConversationIQSerializer extends ServiceResultIQSerializer {

            OnResetConversationIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnResetConversationIQ.SCHEMA_ID, SCHEMA_VERSION, OnResetConversationIQ.class);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                return new OnResetConversationIQ(serviceResultIQ);
            }
        }

        static final OnResetConversationIQSerializer SERIALIZER = new OnResetConversationIQSerializer();

        OnResetConversationIQ(@NonNull String id, @NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion) {

            super(id, from, to, requestId, TWINLIFE_NAME, ON_RESET_CONVERSATION_ACTION, majorVersion, minorVersion);
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnResetConversationIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnResetConversationIQ(@NonNull ServiceResultIQ serviceResultIQ) {

            super(serviceResultIQ);
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2018/05/30
     *
     * {
     *  "schemaId":"55e698ff-b429-425f-bcaa-0b21d4620621",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"InviteGroupIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":[
     *   {
     *    "name":"invitationDescriptor",
     *    "type":"org.twinlife.schemas.conversation.InvitationDescriptor"
     *   }
     * }
     * </pre>
     */

    static final String INVITE_GROUP_ACTION = "invite-group";

    static class InviteGroupIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 1;

        static class InviteGroupIQSerializer extends ServiceRequestIQSerializer {

            InviteGroupIQSerializer() {

                super(org.twinlife.twinlife.conversation.InviteGroupIQ.SCHEMA_ID, SCHEMA_VERSION, InviteGroupIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                InviteGroupIQ inviteGroupIQ = (InviteGroupIQ) object;
                InvitationDescriptorImpl.SERIALIZER_1.serialize(serializerFactory, encoder, inviteGroupIQ.invitationDescriptor);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);

                UUID schemaId = decoder.readUUID();
                int schemaVersion = decoder.readInt();
                if (InvitationDescriptorImpl.SCHEMA_ID.equals(schemaId) && InvitationDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                    InvitationDescriptorImpl invitationDescriptorImpl = (InvitationDescriptorImpl) InvitationDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, decoder);

                    return new InviteGroupIQ(serviceRequestIQ, invitationDescriptorImpl);
                }

                throw new SerializerException();
            }
        }

        static final InviteGroupIQSerializer SERIALIZER = new InviteGroupIQSerializer();

        @NonNull
        final InvitationDescriptorImpl invitationDescriptor;

        InviteGroupIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, @NonNull InvitationDescriptorImpl invitationDescriptor) {

            super(from, to, requestId, TWINLIFE_NAME, INVITE_GROUP_ACTION, majorVersion, minorVersion);

            this.invitationDescriptor = invitationDescriptor;
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("InviteGroupIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_7) {
                throw new UnsupportedOperationException("Need 2.7 version at least");
            }
            InviteGroupIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Private Methods
        //

        private InviteGroupIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull InvitationDescriptorImpl invitationDescriptor) {

            super(serviceRequestIQ);
            this.invitationDescriptor = invitationDescriptor;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2018/07/26
     *
     * {
     *  "schemaId":"f04f5123-b42d-456b-ac5c-45af7b26e6a0",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"RevokeInviteGroupIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":[
     *   {
     *    "name":"invitationDescriptor",
     *    "type":"org.twinlife.schemas.conversation.InvitationDescriptor"
     *   }
     * }
     * </pre>
     */

    static final String REVOKE_INVITE_GROUP_ACTION = "revoke-invite-group";

    static class RevokeInviteGroupIQ extends ServiceRequestIQ {

        static final UUID SCHEMA_ID = UUID.fromString("f04f5123-b42d-456b-ac5c-45af7b26e6a0");
        static final int SCHEMA_VERSION = 1;

        static class RevokeInviteGroupIQSerializer extends ServiceRequestIQSerializer {

            RevokeInviteGroupIQSerializer() {

                super(SCHEMA_ID, SCHEMA_VERSION, RevokeInviteGroupIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                RevokeInviteGroupIQ revokeInviteGroupIQ = (RevokeInviteGroupIQ) object;
                InvitationDescriptorImpl.SERIALIZER_1.serialize(serializerFactory, encoder, revokeInviteGroupIQ.invitationDescriptor);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);

                UUID schemaId = decoder.readUUID();
                int schemaVersion = decoder.readInt();
                if (InvitationDescriptorImpl.SCHEMA_ID.equals(schemaId) && InvitationDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                    InvitationDescriptorImpl invitationDescriptorImpl = (InvitationDescriptorImpl) InvitationDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, decoder);

                    return new RevokeInviteGroupIQ(serviceRequestIQ, invitationDescriptorImpl);
                }

                throw new SerializerException();
            }
        }

        static final RevokeInviteGroupIQSerializer SERIALIZER = new RevokeInviteGroupIQSerializer();

        final InvitationDescriptorImpl invitationDescriptor;

        RevokeInviteGroupIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, @NonNull InvitationDescriptorImpl invitationDescriptor) {

            super(from, to, requestId, TWINLIFE_NAME, REVOKE_INVITE_GROUP_ACTION, majorVersion, minorVersion);

            this.invitationDescriptor = invitationDescriptor;
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("RevokeInviteGroupIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_7) {
                throw new UnsupportedOperationException("Need 2.7 version at least");
            }
            RevokeInviteGroupIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Private Methods
        //

        private RevokeInviteGroupIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull InvitationDescriptorImpl invitationDescriptor) {

            super(serviceRequestIQ);
            this.invitationDescriptor = invitationDescriptor;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2018/07/25
     *
     * {
     *  "schemaId":"3b5dc8a2-2679-43f2-badf-ec61c7eed9f0",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"UpdateGroupMemberIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":[
     *   {"name":"group", "type":"uuid"}
     *   {"name":"member", "type":"uuid"}
     *   {"name":"permissions", "type":"long"}
     *  ]
     * }
     * </pre>
     */

    static final String UPDATE_GROUP_MEMBER_ACTION = "update-group-member";

    static class UpdateGroupMemberIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 1;

        static class UpdateGroupMemberIQSerializer extends ServiceRequestIQSerializer {

            UpdateGroupMemberIQSerializer() {

                super(org.twinlife.twinlife.conversation.UpdatePermissionsIQ.SCHEMA_ID, SCHEMA_VERSION, UpdateGroupMemberIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                UpdateGroupMemberIQ updateGroupMemberIQ = (UpdateGroupMemberIQ) object;
                encoder.writeUUID(updateGroupMemberIQ.groupTwincodeId);
                encoder.writeUUID(updateGroupMemberIQ.memberTwincodeId);
                encoder.writeLong(updateGroupMemberIQ.permissions);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);

                UUID groupId = decoder.readUUID();
                UUID memberId = decoder.readUUID();
                long permissions = decoder.readLong();

                return new UpdateGroupMemberIQ(serviceRequestIQ, groupId, memberId, permissions);
            }
        }

        static final UpdateGroupMemberIQSerializer SERIALIZER = new UpdateGroupMemberIQSerializer();

        final UUID groupTwincodeId;
        final UUID memberTwincodeId;
        final long permissions;

        UpdateGroupMemberIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion,
                            @NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId, long permissions) {

            super(from, to, requestId, TWINLIFE_NAME, UPDATE_GROUP_MEMBER_ACTION, majorVersion, minorVersion);

            this.groupTwincodeId = groupTwincodeId;
            this.memberTwincodeId = memberTwincodeId;
            this.permissions = permissions;
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("UpdateGroupMemberIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_7) {
                throw new UnsupportedOperationException("Need 2.7 version at least");
            }
            UpdateGroupMemberIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Private Methods
        //

        private UpdateGroupMemberIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId, long permissions) {

            super(serviceRequestIQ);
            this.groupTwincodeId = groupTwincodeId;
            this.memberTwincodeId = memberTwincodeId;
            this.permissions = permissions;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2018/09/04
     *
     * {
     *  "schemaId":"3d175317-f1f7-4cd1-abd8-2f538b342e41",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"OnResultJoinIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":[
     *   {"name":"status", "type":"integer"},
     *   {"name":"permissions", "type":"long"},
     *   {"members":
     *     {"name":"count", "type":"integer"}
     *     [{"name":"member", "type":"UUID"},
     *      {"name":"permissions", "type":"long"}]
     *   }
     *  ]
     * }
     * </pre>
     */

    static final String ON_JOIN_GROUP_ACTION = "on-join-group";

    static class OnResultJoinIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 1;

        static class OnResultJoinIQSerializer extends ServiceResultIQSerializer {

            OnResultJoinIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnJoinGroupIQ.SCHEMA_ID, SCHEMA_VERSION, OnResultJoinIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                OnResultJoinIQ onResultJoinIQ = (OnResultJoinIQ) object;
                if (onResultJoinIQ.status == ConversationService.InvitationDescriptor.Status.JOINED) {
                    encoder.writeEnum(1);
                } else {
                    encoder.writeEnum(0);
                }
                encoder.writeLong(onResultJoinIQ.permissions);
                if (onResultJoinIQ.members == null) {
                    encoder.writeLong(0);
                } else {
                    encoder.writeLong(onResultJoinIQ.members.size());
                    for (OnJoinGroupIQ.MemberInfo member : onResultJoinIQ.members) {
                        encoder.writeUUID(member.memberTwincodeId);
                        encoder.writeLong(member.permissions);
                    }
                }
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                int value = decoder.readEnum();
                ConversationService.InvitationDescriptor.Status status;
                if (value == 1) {
                    status = ConversationService.InvitationDescriptor.Status.JOINED;
                } else {
                    status = ConversationService.InvitationDescriptor.Status.WITHDRAWN;
                }
                long permissions = decoder.readLong();

                List<OnJoinGroupIQ.MemberInfo> members = null;
                long count = decoder.readLong();
                if (count > 0) {
                    members = new ArrayList<>();
                    while (count > 0) {
                        UUID memberTwincodeId = decoder.readUUID();
                        long memberPermissions = decoder.readLong();
                        OnJoinGroupIQ.MemberInfo member = new OnJoinGroupIQ.MemberInfo(memberTwincodeId, null, memberPermissions);
                        members.add(member);
                        count--;
                    }
                }
                return new OnResultJoinIQ(serviceResultIQ, status, permissions, members);
            }
        }

        static final OnResultJoinIQSerializer SERIALIZER = new OnResultJoinIQSerializer();

        final ConversationService.InvitationDescriptor.Status status;
        final List<OnJoinGroupIQ.MemberInfo> members;
        final long permissions;

        @SuppressWarnings("SameParameterValue")
        OnResultJoinIQ(@NonNull String id, @NonNull String from, @NonNull String to, @NonNull String action, long requestId, int majorVersion, int minorVersion,
                       ConversationService.InvitationDescriptor.Status status, long permissions, @Nullable List<OnJoinGroupIQ.MemberInfo> members) {

            super(id, from, to, requestId, TWINLIFE_NAME, action, majorVersion, minorVersion);
            this.status = status;
            this.permissions = permissions;
            this.members = members;
        }

        public byte[] serialize(SerializerFactoryImpl serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_7) {
                throw new UnsupportedOperationException("Need 2.7 version at least");
            }
            OnResultJoinIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnResultJoinIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnResultJoinIQ(@NonNull ServiceResultIQ serviceResultIQ, ConversationService.InvitationDescriptor.Status status,
                               long permissions, @Nullable List<OnJoinGroupIQ.MemberInfo> members) {

            super(serviceResultIQ);
            this.status = status;
            this.permissions = permissions;
            this.members = members;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2018/05/30
     *
     * {
     *  "schemaId":"afa81c21-beb5-4829-a5d0-8816afda602f",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"OnResultGroupIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":
     *  []
     * }
     * </pre>
     */
    static final String ON_INVITE_GROUP_ACTION = "on-invite-group";
    static final String ON_REVOKE_INVITE_GROUP_ACTION = "on-revoke-invite-group";
    static final String ON_LEAVE_GROUP_ACTION = "on-leave-group";
    static final String ON_UPDATE_GROUP_MEMBER_ACTION = "on-update-group-member";

    static class OnResultGroupIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 1;

        static class OnResultGroupIQSerializer extends ServiceResultIQSerializer {

            OnResultGroupIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnInviteGroupIQ.SCHEMA_ID, SCHEMA_VERSION, OnResultGroupIQ.class);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                return new OnResultGroupIQ(serviceResultIQ);
            }
        }

        static final OnResultGroupIQSerializer SERIALIZER = new OnResultGroupIQSerializer();

        OnResultGroupIQ(@NonNull String id, @NonNull String from, @NonNull String to, @NonNull String action, long requestId, int majorVersion, int minorVersion) {

            super(id, from, to, requestId, TWINLIFE_NAME, action, majorVersion, minorVersion);
        }

        public byte[] serialize(SerializerFactoryImpl serializerFactory, int majorVersion, int minorVersion, boolean needLeadingPadding) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder;
            if (needLeadingPadding) {
                binaryEncoder = new BinaryEncoder(outputStream);
                binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);
            } else {
                binaryEncoder = new BinaryCompactEncoder(outputStream);
            }

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_7) {
                throw new UnsupportedOperationException("Need 2.7 version at least");
            }
            OnResultGroupIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnResultGroupIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnResultGroupIQ(@NonNull ServiceResultIQ serviceResultIQ) {

            super(serviceResultIQ);
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2018/05/30
     *
     * {
     *  "schemaId":"c1315d7f-bf10-4cec-811b-84c44302e7bd",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"JoinGroupIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":[
     *   {
     *    "name":"invitationDescriptor",
     *    "type":"org.twinlife.schemas.conversation.InvitationDescriptor"
     *   }
     *  ]
     * }
     * </pre>
     */

    static final String JOIN_GROUP_ACTION = "join-group";

    static class JoinGroupIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 1;

        static class JoinGroupIQSerializer extends ServiceRequestIQSerializer {

            JoinGroupIQSerializer() {

                super(org.twinlife.twinlife.conversation.JoinGroupIQ.SCHEMA_ID, SCHEMA_VERSION, JoinGroupIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                JoinGroupIQ joinGroupIQ = (JoinGroupIQ) object;
                if (joinGroupIQ.invitationDescriptor != null) {
                    encoder.writeInt(1);
                    InvitationDescriptorImpl.SERIALIZER_1.serialize(serializerFactory, encoder, joinGroupIQ.invitationDescriptor);
                } else {
                    encoder.writeInt(0);
                    encoder.writeUUID(joinGroupIQ.groupId);
                    encoder.writeUUID(joinGroupIQ.memberId);
                    encoder.writeLong(joinGroupIQ.permissions);
                }
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);

                long mode = decoder.readInt();
                if (mode == 1) {
                    UUID schemaId = decoder.readUUID();
                    int schemaVersion = decoder.readInt();
                    if (InvitationDescriptorImpl.SCHEMA_ID.equals(schemaId) && InvitationDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                        InvitationDescriptorImpl invitationDescriptorImpl = (InvitationDescriptorImpl) InvitationDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, decoder);

                        return new JoinGroupIQ(serviceRequestIQ, invitationDescriptorImpl);
                    }

                    throw new SerializerException();
                } else if (mode == 0) {
                    UUID groupId = decoder.readUUID();
                    UUID memberId = decoder.readUUID();
                    long permissions = decoder.readLong();

                    return new JoinGroupIQ(serviceRequestIQ, groupId, memberId, permissions);
                } else {
                    throw new SerializerException();
                }
            }
        }

        static final JoinGroupIQSerializer SERIALIZER = new JoinGroupIQSerializer();

        final InvitationDescriptorImpl invitationDescriptor;
        final UUID groupId;
        final UUID memberId;
        final long permissions;

        UUID getGroupTwincodeId() {

            if (invitationDescriptor != null) {
                return invitationDescriptor.getGroupTwincodeId();
            } else {
                return groupId;
            }
        }

        UUID getMemberTwincodeId() {

            if (invitationDescriptor != null) {
                return invitationDescriptor.getMemberTwincodeId();
            } else {
                return memberId;
            }
        }

        JoinGroupIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, @NonNull InvitationDescriptorImpl invitationDescriptor) {

            super(from, to, requestId, TWINLIFE_NAME, JOIN_GROUP_ACTION, majorVersion, minorVersion);

            this.invitationDescriptor = invitationDescriptor;
            this.groupId = null;
            this.memberId = null;
            this.permissions = 0;
        }

        JoinGroupIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, @NonNull UUID groupId, @NonNull UUID memberId, long permissions) {

            super(from, to, requestId, TWINLIFE_NAME, JOIN_GROUP_ACTION, majorVersion, minorVersion);

            this.groupId = groupId;
            this.memberId = memberId;
            this.invitationDescriptor = null;
            this.permissions = permissions;
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_7) {
                throw new UnsupportedOperationException("Need 2.7 version at least");
            }
            JoinGroupIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("JoinGroupIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private JoinGroupIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull InvitationDescriptorImpl invitationDescriptor) {

            super(serviceRequestIQ);
            this.invitationDescriptor = invitationDescriptor;
            this.memberId = null;
            this.groupId = null;
            this.permissions = 0;
        }

        private JoinGroupIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull UUID groupId, @NonNull UUID memberId, long permissions) {

            super(serviceRequestIQ);
            this.invitationDescriptor = null;
            this.memberId = memberId;
            this.groupId = groupId;
            this.permissions = permissions;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2018/06/25
     *
     * {
     *  "schemaId":"fae66d0a-ce05-423d-b5fa-6019b24ea924",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"LeaveGroupIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":[
     *   {"name":"group", "type":"uuid"}
     *   {"name":"member", "type":"uuid"}
     *  ]
     * }
     * </pre>
     */

    static final String LEAVE_GROUP_ACTION = "leave-group";

    static class LeaveGroupIQ extends ServiceRequestIQ {

        static final UUID SCHEMA_ID = UUID.fromString("fae66d0a-ce05-423d-b5fa-6019b24ea924");
        static final int SCHEMA_VERSION = 1;

        static class LeaveGroupIQSerializer extends ServiceRequestIQSerializer {

            LeaveGroupIQSerializer() {

                super(SCHEMA_ID, SCHEMA_VERSION, LeaveGroupIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                LeaveGroupIQ leaveGroupIQ = (LeaveGroupIQ) object;
                encoder.writeUUID(leaveGroupIQ.groupTwincodeId);
                encoder.writeUUID(leaveGroupIQ.memberTwincodeId);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);
                UUID groupId = decoder.readUUID();
                UUID memberId = decoder.readUUID();

                return new LeaveGroupIQ(serviceRequestIQ, groupId, memberId);
            }
        }

        static final LeaveGroupIQSerializer SERIALIZER = new LeaveGroupIQSerializer();

        final UUID groupTwincodeId;
        final UUID memberTwincodeId;

        LeaveGroupIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, @NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId) {

            super(from, to, requestId, TWINLIFE_NAME, LEAVE_GROUP_ACTION, majorVersion, minorVersion);

            this.groupTwincodeId = groupTwincodeId;
            this.memberTwincodeId = memberTwincodeId;
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion, boolean needLeadingPadding) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder;
            if (needLeadingPadding) {
                binaryEncoder = new BinaryEncoder(outputStream);
                binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);
            } else {
                binaryEncoder = new BinaryCompactEncoder(outputStream);
            }
            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_7) {
                throw new UnsupportedOperationException("Need 2.7 version at least");
            }
            LeaveGroupIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("LeaveGroupIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private LeaveGroupIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId) {

            super(serviceRequestIQ);
            this.groupTwincodeId = groupTwincodeId;
            this.memberTwincodeId = memberTwincodeId;
        }
    }

    /*
     * <pre>
     * Schema version 1
     *  Date: 2019/02/14
     *
     * {
     *  "schemaId":"7a9772c3-5f99-468d-87af-d67fdb181295",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"PushGeolocationIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"geolocationDescriptor",
     *    "type":"org.twinlife.schemas.conversation.GeolocationDescriptor"
     *   }
     *  ]
     * }
     * </pre>
     */

    static final String PUSH_GEOLOCATION_ACTION = "push-geolocation";

    static class PushGeolocationIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 1;

        static class PushGeolocationIQSerializer extends ServiceRequestIQSerializer {

            PushGeolocationIQSerializer() {

                super(org.twinlife.twinlife.conversation.PushGeolocationIQ.SCHEMA_ID, SCHEMA_VERSION, PushGeolocationIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                PushGeolocationIQ pushGeolocationIQ = (PushGeolocationIQ) object;
                GeolocationDescriptorImpl.SERIALIZER_1.serialize(serializerFactory, encoder, pushGeolocationIQ.geolocationDescriptorImpl);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);

                UUID schemaId = decoder.readUUID();
                int schemaVersion = decoder.readInt();
                if (GeolocationDescriptorImpl.SCHEMA_ID.equals(schemaId) && GeolocationDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                    GeolocationDescriptorImpl geolocationDescriptorImpl = (GeolocationDescriptorImpl) GeolocationDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, decoder);

                    return new PushGeolocationIQ(serviceRequestIQ, geolocationDescriptorImpl);
                }

                throw new SerializerException();
            }
        }

        static final PushGeolocationIQSerializer SERIALIZER = new PushGeolocationIQSerializer();

        final GeolocationDescriptorImpl geolocationDescriptorImpl;

        PushGeolocationIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion,
                          @NonNull GeolocationDescriptorImpl geolocationDescriptorImpl) {

            super(from, to, requestId, TWINLIFE_NAME, PUSH_GEOLOCATION_ACTION, majorVersion, minorVersion);

            this.geolocationDescriptorImpl = geolocationDescriptorImpl;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" geolocationDescriptorImpl=");
            stringBuilder.append(geolocationDescriptorImpl);
            stringBuilder.append("\n");
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_8) {
                throw new UnsupportedOperationException("Need 2.8 version at least");
            }
            PushGeolocationIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushGeolocationIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private PushGeolocationIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull GeolocationDescriptorImpl geolocationDescriptorImpl) {

            super(serviceRequestIQ);

            this.geolocationDescriptorImpl = geolocationDescriptorImpl;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2019/02/14
     *
     * {
     *  "schemaId":"5fd82b6b-5b7f-42c1-976e-f3addf8c5e16",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"OnPushGeolocationIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":
     *  [
     *   {"name":"receivedTimestamp", "type":"long"}
     *  ]
     * }
     * </pre>
     */

    static final String ON_PUSH_GEOLOCATION_ACTION = "on-push-geolocation";

    static class OnPushGeolocationIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 1;

        static class OnPushGeolocationIQSerializer extends ServiceResultIQSerializer {

            OnPushGeolocationIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnPushGeolocationIQ.SCHEMA_ID, SCHEMA_VERSION, OnPushGeolocationIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                OnPushGeolocationIQ onPushGeolocationIQ = (OnPushGeolocationIQ) object;
                encoder.writeLong(onPushGeolocationIQ.receivedTimestamp);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                long receivedTimestamp = decoder.readLong();
                return new OnPushGeolocationIQ(serviceResultIQ, receivedTimestamp);
            }
        }

        static final OnPushGeolocationIQSerializer SERIALIZER = new OnPushGeolocationIQSerializer();

        final long receivedTimestamp;

        OnPushGeolocationIQ(@NonNull String id, @NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, long receivedTimestamp) {

            super(id, from, to, requestId, TWINLIFE_NAME, ON_PUSH_GEOLOCATION_ACTION, majorVersion, minorVersion);

            this.receivedTimestamp = receivedTimestamp;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" receivedTimestamp=");
            stringBuilder.append(receivedTimestamp);
            stringBuilder.append("\n");
        }

        public byte[] serialize(SerializerFactoryImpl serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_8) {
                throw new UnsupportedOperationException("Need 2.8 version at least");
            }
            OnPushGeolocationIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnPushGeolocationIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnPushGeolocationIQ(@NonNull ServiceResultIQ serviceResultIQ, long receivedTimestamp) {

            super(serviceResultIQ);

            this.receivedTimestamp = receivedTimestamp;
        }
    }

    /*
     * <pre>
     *
     * Schema version 4
     *  Date: 2019/03/19
     *
     * {
     *  "schemaId":"26e3a3bd-7db0-4fc5-9857-bbdb2032960e",
     *  "schemaVersion":"4",
     *
     *  "type":"record",
     *  "name":"PushObjectIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"objectDescriptor",
     *    "type":"org.twinlife.schemas.conversation.ObjectDescriptor"
     *   }
     *  ]
     * }
     *
     * Schema version 3
     *  Date: 2016/12/29
     *
     * {
     *  "schemaId":"26e3a3bd-7db0-4fc5-9857-bbdb2032960e",
     *  "schemaVersion":"3",
     *
     *  "type":"record",
     *  "name":"PushObjectIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"objectDescriptor",
     *    "type":"org.twinlife.schemas.conversation.ObjectDescriptor.3"
     *   }
     *  ]
     * }
     *
     *
     * Schema version 2
     *  Date: 2016/09/08
     *
     * {
     *  "schemaId":"26e3a3bd-7db0-4fc5-9857-bbdb2032960e",
     *  "schemaVersion":"2",
     *
     *  "type":"record",
     *  "name":"PushObjectIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"objectDescriptor",
     *    "type":"org.twinlife.schemas.conversation.ObjectDescriptor.2"
     *   }
     *  ]
     * }
     *
     * Schema version 1
     *
     * {
     *  "schemaId":"26e3a3bd-7db0-4fc5-9857-bbdb2032960e",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"PushObjectIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ.1"
     *  "fields":
     *  [
     *   {
     *    "name":"objectDescriptor",
     *    "type":"org.twinlife.schemas.conversation.ObjectDescriptor.1"
     *   }
     *  ]
     * }
     *
     * </pre>
     */

    static final String PUSH_OBJECT_ACTION = "push-object";

    static final String PUSH_OBJECT_ACTION_1 = "twinlife:conversation:push-object";

    static class PushObjectIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 4;

        static class PushObjectIQSerializer extends ServiceRequestIQSerializer {

            PushObjectIQSerializer() {

                super(org.twinlife.twinlife.conversation.PushObjectIQ.SCHEMA_ID, SCHEMA_VERSION, PushObjectIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                PushObjectIQ pushObjectIQ = (PushObjectIQ) object;
                ObjectDescriptorImpl.SERIALIZER_4.serialize(serializerFactory, encoder, pushObjectIQ.objectDescriptorImpl);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);

                UUID schemaId = decoder.readUUID();
                int schemaVersion = decoder.readInt();
                if (ObjectDescriptorImpl.SCHEMA_ID.equals(schemaId) && ObjectDescriptorImpl.SCHEMA_VERSION_4 == schemaVersion) {
                    ObjectDescriptorImpl objectDescriptorImpl = (ObjectDescriptorImpl) ObjectDescriptorImpl.SERIALIZER_4.deserialize(serializerFactory, decoder);

                    return new PushObjectIQ(serviceRequestIQ, objectDescriptorImpl);
                }

                throw new SerializerException();
            }
        }

        static final PushObjectIQSerializer SERIALIZER = new PushObjectIQSerializer();

        final ObjectDescriptorImpl objectDescriptorImpl;

        PushObjectIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, @NonNull ObjectDescriptorImpl objectDescriptorImpl) {

            super(from, to, requestId, TWINLIFE_NAME, majorVersion == ConversationServiceImpl.MAJOR_VERSION_1 ? PUSH_OBJECT_ACTION_1 : PUSH_OBJECT_ACTION,
                    majorVersion, minorVersion);

            this.objectDescriptorImpl = objectDescriptorImpl;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" objectDescriptorImpl=");
            stringBuilder.append(objectDescriptorImpl);
            stringBuilder.append("\n");
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushObjectIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion == ConversationServiceImpl.MAJOR_VERSION_2 && minorVersion >= ConversationServiceImpl.MINOR_VERSION_9) {
                PushObjectIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            } else {
                throw new UnsupportedOperationException();
            }
            return outputStream.toByteArray();
        }

        //
        // Private Methods
        //

        private PushObjectIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull ObjectDescriptorImpl objectDescriptorImpl) {

            super(serviceRequestIQ);

            this.objectDescriptorImpl = objectDescriptorImpl;
        }
    }

    /*
     * <pre>
     *
     * Schema version 2
     *  Date: 2016/09/08
     *
     * {
     *  "schemaId":"f95ac4b5-d20f-4e1f-8204-6d146dd5291e",
     *  "schemaVersion":"2",
     *
     *  "type":"record",
     *  "name":"OnPushObjectIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":
     *  [
     *   {"name":"receivedTimestamp", "type":"long"}
     *  ]
     * }
     *
     *
     * Schema version 1
     *
     * {
     *  "schemaId":"f95ac4b5-d20f-4e1f-8204-6d146dd5291e",
     *  "schemaVersion":"1",
     *  "type":"record",
     *  "name":"OnPushObjectIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ.1"
     *  "fields":
     *  [
     *   {"name":"receivedTimestamp", "type":"long"}
     *  ]
     * }
     *
     * </pre>
     */

    static final String ON_PUSH_OBJECT_ACTION = "on-push-object";

    static final String ON_PUSH_OBJECT_ACTION_1 = "twinlife:conversation:on-push-object";

    static class OnPushObjectIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 2;

        static class OnPushObjectIQSerializer extends ServiceResultIQSerializer {

            OnPushObjectIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnPushObjectIQ.SCHEMA_ID, SCHEMA_VERSION, OnPushObjectIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                OnPushObjectIQ onPushObjectIQ = (OnPushObjectIQ) object;
                encoder.writeLong(onPushObjectIQ.receivedTimestamp);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                long receivedTimestamp = decoder.readLong();
                return new OnPushObjectIQ(serviceResultIQ, receivedTimestamp);
            }
        }

        static final OnPushObjectIQSerializer SERIALIZER = new OnPushObjectIQSerializer();

        static final int SCHEMA_VERSION_1 = 1;

        static class OnPushObjectIQSerializer_1 extends ServiceResultIQSerializer_1 {

            OnPushObjectIQSerializer_1() {

                super(org.twinlife.twinlife.conversation.OnPushObjectIQ.SCHEMA_ID, SCHEMA_VERSION_1, OnPushObjectIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                OnPushObjectIQ onPushObjectIQ = (OnPushObjectIQ) object;
                encoder.writeLong(onPushObjectIQ.receivedTimestamp);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                long receivedTimestamp = decoder.readLong();
                return new OnPushObjectIQ(serviceResultIQ, receivedTimestamp);
            }
        }

        static final OnPushObjectIQSerializer_1 SERIALIZER_1 = new OnPushObjectIQSerializer_1();

        final long receivedTimestamp;

        OnPushObjectIQ(@NonNull String id, @NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, long receivedTimestamp) {

            super(id, from, to, requestId, TWINLIFE_NAME, majorVersion == ConversationServiceImpl.MAJOR_VERSION_1 ? ON_PUSH_OBJECT_ACTION_1 : ON_PUSH_OBJECT_ACTION,
                    majorVersion, minorVersion);

            this.receivedTimestamp = receivedTimestamp;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" receivedTimestamp=");
            stringBuilder.append(receivedTimestamp);
            stringBuilder.append("\n");
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnPushObjectIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnPushObjectIQ(@NonNull ServiceResultIQ serviceResultIQ, long receivedTimestamp) {

            super(serviceResultIQ);

            this.receivedTimestamp = receivedTimestamp;
        }
    }

    /*
     * <pre>
     *
     * Schema version 2
     *  Date: 2016/12/29
     *
     * {
     *  "schemaId":"05617876-8419-4240-9945-08bf4106cb72",
     *  "schemaVersion":"2",
     *
     *  "type":"record",
     *  "name":"PushTransientObjectIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"transientObjectDescriptor",
     *    "type":"org.twinlife.schemas.conversation.TransientObjectDescriptor"
     *   }
     *  ]
     * }
     *
     *
     * Schema version 1
     *
     * {
     *  "schemaId":"05617876-8419-4240-9945-08bf4106cb72",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"PushTransientObjectIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"transientObjectDescriptor",
     *    "type":"org.twinlife.schemas.conversation.TransientObjectDescriptor.1"
     *   }
     *  ]
     * }
     *
     * </pre>
     */

    static final String PUSH_TRANSIENT_OBJECT_ACTION = "push-transient-object";

    static class PushTransientObjectIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 2;

        static class PushTransientObjectIQSerializer extends ServiceRequestIQSerializer {

            PushTransientObjectIQSerializer() {

                super(org.twinlife.twinlife.conversation.PushTransientIQ.SCHEMA_ID, SCHEMA_VERSION, PushTransientObjectIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                PushTransientObjectIQ pushTransientObjectIQ = (PushTransientObjectIQ) object;
                TransientObjectDescriptorImpl.SERIALIZER_2.serialize(serializerFactory, encoder, pushTransientObjectIQ.transientObjectDescriptorImpl);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);
                UUID schemaId = decoder.readUUID();
                int schemaVersion = decoder.readInt();
                if (TransientObjectDescriptorImpl.SCHEMA_ID.equals(schemaId) && TransientObjectDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                    TransientObjectDescriptorImpl transientObjectDescriptorImpl =
                            (TransientObjectDescriptorImpl) TransientObjectDescriptorImpl.SERIALIZER_2.deserialize(serializerFactory, decoder);

                    return new PushTransientObjectIQ(serviceRequestIQ, transientObjectDescriptorImpl);
                }

                throw new SerializerException();
            }
        }

        static final PushTransientObjectIQSerializer SERIALIZER = new PushTransientObjectIQSerializer();

        final TransientObjectDescriptorImpl transientObjectDescriptorImpl;

        PushTransientObjectIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion,
                              @NonNull TransientObjectDescriptorImpl transientObjectDescriptorImpl) {

            super(from, to, requestId, TWINLIFE_NAME, PUSH_TRANSIENT_OBJECT_ACTION, majorVersion, minorVersion);

            this.transientObjectDescriptorImpl = transientObjectDescriptorImpl;
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2) {
                throw new UnsupportedOperationException("Need 2.1 version at least");
            }
            PushTransientObjectIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" commandDescriptorImpl=");
            stringBuilder.append(transientObjectDescriptorImpl);
            stringBuilder.append("\n");
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushTransientObjectIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private PushTransientObjectIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull TransientObjectDescriptorImpl transientObjectDescriptorImpl) {

            super(serviceRequestIQ);

            this.transientObjectDescriptorImpl = transientObjectDescriptorImpl;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2020/04/16
     *
     * {
     *  "schemaId":"e8a69b58-1014-4d3c-9357-8331c19c5f59",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"PushCommandIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"transientObjectDescriptor",
     *    "type":"org.twinlife.schemas.conversation.TransientObjectDescriptor"
     *   }
     *  ]
     * }
     * </pre>
     */

    static final String PUSH_COMMAND_ACTION = "push-command";

    static class PushCommandIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 1;

        static class PushCommandIQSerializer extends ServiceRequestIQSerializer {

            PushCommandIQSerializer() {

                super(org.twinlife.twinlife.conversation.PushCommandIQ.SCHEMA_ID, SCHEMA_VERSION, PushCommandIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                PushCommandIQ pushCommandIQ = (PushCommandIQ) object;
                TransientObjectDescriptorImpl.SERIALIZER_2.serialize(serializerFactory, encoder, pushCommandIQ.commandDescriptorImpl);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);
                UUID schemaId = decoder.readUUID();
                int schemaVersion = decoder.readInt();
                if (TransientObjectDescriptorImpl.SCHEMA_ID.equals(schemaId) && TransientObjectDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                    TransientObjectDescriptorImpl transientObjectDescriptorImpl =
                            (TransientObjectDescriptorImpl) TransientObjectDescriptorImpl.SERIALIZER_2.deserialize(serializerFactory, decoder);

                    return new PushCommandIQ(serviceRequestIQ, transientObjectDescriptorImpl);
                }

                throw new SerializerException();
            }
        }

        static final PushCommandIQSerializer SERIALIZER = new PushCommandIQSerializer();

        final TransientObjectDescriptorImpl commandDescriptorImpl;

        PushCommandIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion,
                              @NonNull TransientObjectDescriptorImpl commandDescriptorImpl) {

            super(from, to, requestId, TWINLIFE_NAME, PUSH_COMMAND_ACTION, majorVersion, minorVersion);

            this.commandDescriptorImpl = commandDescriptorImpl;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" commandDescriptorImpl=");
            stringBuilder.append(commandDescriptorImpl);
            stringBuilder.append("\n");
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_11) {
                throw new UnsupportedOperationException("Need 2.11 version at least");
            }
            PushCommandIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushCommandIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private PushCommandIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull TransientObjectDescriptorImpl commandDescriptorImpl) {

            super(serviceRequestIQ);

            this.commandDescriptorImpl = commandDescriptorImpl;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2020/04/16
     *
     * {
     *  "schemaId":"4453dbf3-1b26-4c13-956c-4b83fc1d0c49",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"OnPushCommandIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":
     *  [
     *   {"name":"receivedTimestamp", "type":"long"}
     *  ]
     * }
     * </pre>
     */

    static final String ON_PUSH_COMMAND_ACTION = "on-push-command";

    static class OnPushCommandIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 1;

        static class OnPushCommandIQSerializer extends ServiceResultIQSerializer {

            OnPushCommandIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnPushCommandIQ.SCHEMA_ID, SCHEMA_VERSION, OnPushCommandIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                OnPushCommandIQ onPushObjectIQ = (OnPushCommandIQ) object;
                encoder.writeLong(onPushObjectIQ.receivedTimestamp);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                long receivedTimestamp = decoder.readLong();
                return new OnPushCommandIQ(serviceResultIQ, receivedTimestamp);
            }
        }

        static final OnPushCommandIQSerializer SERIALIZER = new OnPushCommandIQSerializer();

        final long receivedTimestamp;

        OnPushCommandIQ(@NonNull String id, @NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, long receivedTimestamp) {

            super(id, from, to, requestId, TWINLIFE_NAME, ON_PUSH_COMMAND_ACTION, majorVersion, minorVersion);

            this.receivedTimestamp = receivedTimestamp;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" receivedTimestamp=");
            stringBuilder.append(receivedTimestamp);
            stringBuilder.append("\n");
        }

        public byte[] serialize(SerializerFactoryImpl serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_11) {
                throw new UnsupportedOperationException("Need 2.11 version at least");
            }
            OnPushCommandIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnPushCommandIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnPushCommandIQ(@NonNull ServiceResultIQ serviceResultIQ, long receivedTimestamp) {

            super(serviceResultIQ);

            this.receivedTimestamp = receivedTimestamp;
        }
    }

    /*
     * <pre>
     *
     * Schema version 6
     *  Date: 2019/03/19
     *
     * {
     *  "schemaId":"8359efba-fb7e-4378-a054-c4a9e2d37f8f",
     *  "schemaVersion":"6",
     *
     *  "type":"record",
     *  "name":"PushFileIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"fileDescriptor",
     *    [
     *     "type":"org.twinlife.schemas.conversation.FileDescriptor",      // V3
     *     "type":"org.twinlife.schemas.conversation.ImageDescriptor",     // V3
     *     "type":"org.twinlife.schemas.conversation.AudioDescriptor",     // V2
     *     "type":"org.twinlife.schemas.conversation.VideoDescriptor"      // V2
     *     "type":"org.twinlife.schemas.conversation.NamedFileDescriptor", // V2
     *    ]
     *   }
     *  ]
     * }
     *
     * Schema version 5
     *  Date: 2018/09/17
     *
     * {
     *  "schemaId":"8359efba-fb7e-4378-a054-c4a9e2d37f8f",
     *  "schemaVersion":"5",
     *
     *  "type":"record",
     *  "name":"PushFileIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"fileDescriptor",
     *    [
     *     "type":"org.twinlife.schemas.conversation.FileDescriptor.2",
     *     "type":"org.twinlife.schemas.conversation.ImageDescriptor.2",
     *     "type":"org.twinlife.schemas.conversation.AudioDescriptor.1",
     *     "type":"org.twinlife.schemas.conversation.VideoDescriptor.1"
     *     "type":"org.twinlife.schemas.conversation.NamedFileDescriptor.1",
     *    ]
     *   }
     *  ]
     * }
     *
     * Schema version 4
     *  Date: 2018/05/29
     *
     * {
     *  "schemaId":"8359efba-fb7e-4378-a054-c4a9e2d37f8f",
     *  "schemaVersion":"4",
     *
     *  "type":"record",
     *  "name":"PushFileIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"fileDescriptor",
     *    [
     *     "type":"org.twinlife.schemas.conversation.FileDescriptor.2",
     *     "type":"org.twinlife.schemas.conversation.ImageDescriptor.2",
     *     "type":"org.twinlife.schemas.conversation.AudioDescriptor.1"
     *     "type":"org.twinlife.schemas.conversation.VideoDescriptor.1"
     *    ]
     *   }
     *  ]
     * }
     *
     *
     * Schema version 3
     *  Date: 2017/01/26
     *
     * {
     *  "schemaId":"8359efba-fb7e-4378-a054-c4a9e2d37f8f",
     *  "schemaVersion":"3",
     *
     *  "type":"record",
     *  "name":"PushFileIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"fileDescriptor",
     *    [
     *     "type":"org.twinlife.schemas.conversation.FileDescriptor.2",
     *     "type":"org.twinlife.schemas.conversation.ImageDescriptor.2",
     *     "type":"org.twinlife.schemas.conversation.AudioDescriptor.1"
     *    ]
     *   }
     *  ]
     * }
     *
     *
     * Schema version 2
     *  Date: 2016/12/29
     *
     * {
     *  "schemaId":"8359efba-fb7e-4378-a054-c4a9e2d37f8f",
     *  "schemaVersion":"2",
     *
     *  "type":"record",
     *  "name":"PushFileIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"fileDescriptor",
     *    [
     *     "type":"org.twinlife.schemas.conversation.FileDescriptor.2",
     *     "type":"org.twinlife.schemas.conversation.ImageDescriptor.2"
     *    ]
     *   }
     *  ]
     * }
     *
     *
     * Schema version 1
     *
     * {
     *  "schemaId":"8359efba-fb7e-4378-a054-c4a9e2d37f8f",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"PushFileIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"fileDescriptor",
     *    [
     *     "type":"org.twinlife.schemas.conversation.FileDescriptor.1",
     *     "type":"org.twinlife.schemas.conversation.ImageDescriptor.1"
     *    ]
     *   }
     *  ]
     * }
     *
     * </pre>
     */

    static final String PUSH_FILE_ACTION = "push-file";

    static class PushFileIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 6;

        static class PushFileIQSerializer extends ServiceRequestIQSerializer {

            PushFileIQSerializer() {

                super(org.twinlife.twinlife.conversation.PushFileIQ.SCHEMA_ID, SCHEMA_VERSION, PushFileIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                PushFileIQ pushFileIQ = (PushFileIQ) object;
                FileDescriptorImpl fileDescriptorImpl = pushFileIQ.fileDescriptorImpl;
                switch (fileDescriptorImpl.getType()) {
                    case FILE_DESCRIPTOR:
                        encoder.writeEnum(0);
                        FileDescriptorImpl.FILE_SERIALIZER_3.serialize(serializerFactory, encoder, fileDescriptorImpl);
                        break;

                    case IMAGE_DESCRIPTOR:
                        encoder.writeEnum(1);
                        ImageDescriptorImpl.SERIALIZER_3.serialize(serializerFactory, encoder, fileDescriptorImpl);
                        break;

                    case AUDIO_DESCRIPTOR:
                        encoder.writeEnum(2);
                        AudioDescriptorImpl.SERIALIZER_2.serialize(serializerFactory, encoder, fileDescriptorImpl);
                        break;

                    case VIDEO_DESCRIPTOR:
                        encoder.writeEnum(3);
                        VideoDescriptorImpl.SERIALIZER_2.serialize(serializerFactory, encoder, fileDescriptorImpl);
                        break;

                    case NAMED_FILE_DESCRIPTOR:
                        encoder.writeEnum(4);
                        NamedFileDescriptorImpl.SERIALIZER_2.serialize(serializerFactory, encoder, fileDescriptorImpl);
                        break;

                    default:
                        throw new SerializerException();
                }
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);

                int position = decoder.readEnum();

                UUID schemaId = decoder.readUUID();
                int schemaVersion = decoder.readInt();
                switch (position) {
                    case 0:
                        if (FileDescriptorImpl.FILE_DESCRIPTOR_SCHEMA_ID.equals(schemaId) && FileDescriptorImpl.FILE_SCHEMA_VERSION_3 == schemaVersion) {
                            FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) FileDescriptorImpl.FILE_SERIALIZER_3.deserialize(serializerFactory, decoder);

                            return new PushFileIQ(serviceRequestIQ, fileDescriptorImpl);
                        }
                        break;

                    case 1:
                        if (ImageDescriptorImpl.SCHEMA_ID.equals(schemaId) && ImageDescriptorImpl.SCHEMA_VERSION_3 == schemaVersion) {
                            ImageDescriptorImpl imageDescriptorImpl = (ImageDescriptorImpl) ImageDescriptorImpl.SERIALIZER_3.deserialize(serializerFactory, decoder);

                            return new PushFileIQ(serviceRequestIQ, imageDescriptorImpl);
                        }
                        break;

                    case 2:
                        if (AudioDescriptorImpl.SCHEMA_ID.equals(schemaId) && AudioDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                            AudioDescriptorImpl audioDescriptorImpl = (AudioDescriptorImpl) AudioDescriptorImpl.SERIALIZER_2.deserialize(serializerFactory, decoder);

                            return new PushFileIQ(serviceRequestIQ, audioDescriptorImpl);
                        }
                        break;

                    case 3:
                        if (VideoDescriptorImpl.SCHEMA_ID.equals(schemaId) && VideoDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                            VideoDescriptorImpl videoDescriptorImpl = (VideoDescriptorImpl) VideoDescriptorImpl.SERIALIZER_2.deserialize(serializerFactory, decoder);

                            return new PushFileIQ(serviceRequestIQ, videoDescriptorImpl);
                        }
                        break;

                    case 4:
                        if (NamedFileDescriptorImpl.SCHEMA_ID.equals(schemaId) && NamedFileDescriptorImpl.SCHEMA_VERSION_2 == schemaVersion) {
                            NamedFileDescriptorImpl namedFileDescriptorImpl = (NamedFileDescriptorImpl) NamedFileDescriptorImpl.SERIALIZER_2.deserialize(serializerFactory, decoder);

                            return new PushFileIQ(serviceRequestIQ, namedFileDescriptorImpl);
                        }
                        break;

                    default:
                        break;
                }

                throw new SerializerException();
            }
        }

        static final PushFileIQSerializer SERIALIZER = new PushFileIQSerializer();

        final FileDescriptorImpl fileDescriptorImpl;

        PushFileIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, @NonNull FileDescriptorImpl fileDescriptorImpl) {

            super(from, to, requestId, TWINLIFE_NAME, PUSH_FILE_ACTION, majorVersion, minorVersion);

            this.fileDescriptorImpl = fileDescriptorImpl;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" fileDescriptorImpl=");
            stringBuilder.append(fileDescriptorImpl);
            stringBuilder.append("\n");
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushFileIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion == ConversationServiceImpl.MAJOR_VERSION_2) {
                if (minorVersion >= ConversationServiceImpl.MINOR_VERSION_9) {
                    PushFileIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
                } else {
                    throw new SerializerException();
                }
            } else if (majorVersion == ConversationServiceImpl.MAJOR_VERSION_1) {
                throw new UnsupportedOperationException();
            } else {
                throw new SerializerException();
            }

            return outputStream.toByteArray();
        }

        //
        // Private Methods
        //

        private PushFileIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull FileDescriptorImpl fileDescriptorImpl) {

            super(serviceRequestIQ);

            this.fileDescriptorImpl = fileDescriptorImpl;
        }
    }

    /*
     * <pre>
     *
     * Schema version
     *  Date: 2016/09/08
     *
     * {
     *  "schemaId":"3d4e8b77-bca3-477d-a949-5ec4f36e01a3",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"OnPushFileIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":
     *  [
     *   {"name":"receivedTimestamp", "type":"long"}
     *  ]
     * }
     *
     * </pre>
     */

    static final String ON_PUSH_FILE_ACTION = "on-push-file";

    static class OnPushFileIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 1;

        static class OnPushFileIQSerializer extends ServiceResultIQSerializer {

            OnPushFileIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnPushFileIQ.SCHEMA_ID, SCHEMA_VERSION, OnPushFileIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                OnPushFileIQ onPushFileIQ = (OnPushFileIQ) object;
                encoder.writeLong(onPushFileIQ.receivedTimestamp);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                long receivedTimestamp = decoder.readLong();
                return new OnPushFileIQ(serviceResultIQ, receivedTimestamp);
            }
        }

        static final OnPushFileIQSerializer SERIALIZER = new OnPushFileIQSerializer();

        final long receivedTimestamp;

        OnPushFileIQ(@NonNull String id, @NonNull String from, String to, long requestId, int majorVersion, int minorVersion, long receivedTimestamp) {

            super(id, from, to, requestId, TWINLIFE_NAME, ON_PUSH_FILE_ACTION, majorVersion, minorVersion);

            this.receivedTimestamp = receivedTimestamp;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" receivedTimestamp=");
            stringBuilder.append(receivedTimestamp);
            stringBuilder.append("\n");
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnPushFileIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnPushFileIQ(@NonNull ServiceResultIQ serviceResultIQ, long receivedTimestamp) {

            super(serviceResultIQ);

            this.receivedTimestamp = receivedTimestamp;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *
     * {
     *  "schemaId":"ae5192f5-f505-4211-84c5-76cb5bf9b147",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"PushFileChunkIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {"name":"twincodeOutboundId", "type":"uuid"}
     *   {"name":"sequenceId", "type":"long"}
     *   {"name":"chunkStart", "type":"long"}
     *   {"name":"chunk", "type":"bytes"}
     *  ]
     * }
     *
     * </pre>
     */

    static final String PUSH_FILE_CHUNK_ACTION = "push-file-chunk";

    static class PushFileChunkIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 1;

        static class PushFileChunkIQSerializer extends ServiceRequestIQSerializer {

            PushFileChunkIQSerializer() {

                super(org.twinlife.twinlife.conversation.PushFileChunkIQ.SCHEMA_ID, SCHEMA_VERSION, PushFileChunkIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                PushFileChunkIQ pushChunkFileIQ = (PushFileChunkIQ) object;
                encoder.writeUUID(pushChunkFileIQ.descriptorId.twincodeOutboundId);
                encoder.writeLong(pushChunkFileIQ.descriptorId.sequenceId);
                encoder.writeLong(pushChunkFileIQ.chunkStart);
                encoder.writeData(pushChunkFileIQ.chunk);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);

                UUID twincodeOutboundId = decoder.readUUID();
                long sequenceId = decoder.readLong();
                long chunkStart = decoder.readLong();
                ByteBuffer chunk = decoder.readBytes(null);
                return new PushFileChunkIQ(serviceRequestIQ, new DescriptorId(0, twincodeOutboundId, sequenceId), chunkStart, chunk.array());
            }
        }

        static final PushFileChunkIQSerializer SERIALIZER = new PushFileChunkIQSerializer();

        final DescriptorId descriptorId;
        final long chunkStart;
        final byte[] chunk;

        PushFileChunkIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion,
                        @NonNull DescriptorId descriptorId,
                        long chunkStart, @NonNull byte[] chunk) {

            super(from, to, requestId, TWINLIFE_NAME, PUSH_FILE_CHUNK_ACTION, majorVersion, minorVersion);

            this.descriptorId = descriptorId;
            this.chunkStart = chunkStart;
            this.chunk = chunk;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" descriptorId=");
            stringBuilder.append(descriptorId);
            stringBuilder.append("\n");
            stringBuilder.append(" chunkStart=");
            stringBuilder.append(chunkStart);
            stringBuilder.append("\n");
            stringBuilder.append(" chunk=");
            stringBuilder.append(Utils.bytesToHex(chunk, 64));
            stringBuilder.append("\n");
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushFileChunkIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private PushFileChunkIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull DescriptorId descriptorId,
                                long chunkStart, @NonNull byte[] chunk) {

            super(serviceRequestIQ);

            this.descriptorId = descriptorId;
            this.chunkStart = chunkStart;
            this.chunk = chunk;
        }
    }

    /*
     * <pre>
     *
     * Schema version
     *  Date: 2016/09/08
     *
     * {
     *  "schemaId":"af9e04d2-88c5-4054-8707-ad5f06ce9fc4",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"OnPushFileChunkIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":
     *  [
     *   {"name":"receivedTimestamp", "type":"long"}
     *   {"name":"nextChunkStart", "type":"long"}
     *  ]
     * }
     *
     * </pre>
     */

    static final String ON_PUSH_FILE_CHUNK_ACTION = "on-push-file-chunk";

    static class OnPushFileChunkIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 1;

        static class OnPushFileChunkIQSerializer extends ServiceResultIQSerializer {

            OnPushFileChunkIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnPushFileChunkIQ.SCHEMA_ID, SCHEMA_VERSION, OnPushFileChunkIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                OnPushFileChunkIQ onPushFileChunkIQ = (OnPushFileChunkIQ) object;
                encoder.writeLong(onPushFileChunkIQ.receivedTimestamp);
                encoder.writeLong(onPushFileChunkIQ.nextChunkStart);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                long receivedTime = decoder.readLong();
                long nextChunkStart = decoder.readLong();
                return new OnPushFileChunkIQ(serviceResultIQ, receivedTime, nextChunkStart);
            }
        }

        static final OnPushFileChunkIQSerializer SERIALIZER = new OnPushFileChunkIQSerializer();

        final long receivedTimestamp;
        final long nextChunkStart;

        OnPushFileChunkIQ(@NonNull String id, @NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, long receivedTimestamp,
                          long nextChunkStart) {

            super(id, from, to, requestId, TWINLIFE_NAME, ON_PUSH_FILE_CHUNK_ACTION, majorVersion, minorVersion);

            this.receivedTimestamp = receivedTimestamp;
            this.nextChunkStart = nextChunkStart;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" receivedTimestamp=");
            stringBuilder.append(receivedTimestamp);
            stringBuilder.append("\n");
            stringBuilder.append(" nextChunkStart=");
            stringBuilder.append(nextChunkStart);
            stringBuilder.append("\n");
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnPushFileChunkIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnPushFileChunkIQ(@NonNull ServiceResultIQ serviceResultIQ, long receivedTimestamp, long nextChunkStart) {

            super(serviceResultIQ);

            this.receivedTimestamp = receivedTimestamp;
            this.nextChunkStart = nextChunkStart;
        }
    }

    /*
     * <pre>
     * Schema version 1
     *  Date: 2019/04/10
     *
     * {
     *  "schemaId":"72863c61-c0a9-437b-8b88-3b78354e54b8",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"PushTwincodeIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {
     *    "name":"twincodeDescriptor",
     *    "type":"org.twinlife.schemas.conversation.TwincodeDescriptor"
     *   }
     *  ]
     * }
     * </pre>
     */

    static final String PUSH_TWINCODE_ACTION = "push-twincode";

    static class PushTwincodeIQ extends ServiceRequestIQ {

        static final int SCHEMA_VERSION = 1;

        static class PushTwincodeIQSerializer extends ServiceRequestIQSerializer {

            PushTwincodeIQSerializer() {

                super(org.twinlife.twinlife.conversation.PushTwincodeIQ.SCHEMA_ID, SCHEMA_VERSION, PushTwincodeIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                PushTwincodeIQ pushTwincodeIQ = (PushTwincodeIQ) object;
                TwincodeDescriptorImpl.SERIALIZER_1.serialize(serializerFactory, encoder, pushTwincodeIQ.twincodeDescriptorImpl);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);

                UUID schemaId = decoder.readUUID();
                int schemaVersion = decoder.readInt();
                if (TwincodeDescriptorImpl.SCHEMA_ID.equals(schemaId) && TwincodeDescriptorImpl.SCHEMA_VERSION_1 == schemaVersion) {
                    TwincodeDescriptorImpl twincodeDescriptorImpl = (TwincodeDescriptorImpl) TwincodeDescriptorImpl.SERIALIZER_1.deserialize(serializerFactory, decoder);

                    return new PushTwincodeIQ(serviceRequestIQ, twincodeDescriptorImpl);
                }

                throw new SerializerException();
            }
        }

        static final PushTwincodeIQSerializer SERIALIZER = new PushTwincodeIQSerializer();

        final TwincodeDescriptorImpl twincodeDescriptorImpl;

        PushTwincodeIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion,
                       @NonNull TwincodeDescriptorImpl twincodeDescriptorImpl) {

            super(from, to, requestId, TWINLIFE_NAME, PUSH_TWINCODE_ACTION, majorVersion, minorVersion);

            this.twincodeDescriptorImpl = twincodeDescriptorImpl;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" twincodeDescriptorImpl=");
            stringBuilder.append(twincodeDescriptorImpl);
            stringBuilder.append("\n");
        }

        public byte[] serialize(SerializerFactory serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_10) {
                throw new UnsupportedOperationException("Need 2.10 version at least");
            }
            PushTwincodeIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushTwincodeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private PushTwincodeIQ(@NonNull ServiceRequestIQ serviceRequestIQ, @NonNull TwincodeDescriptorImpl twincodeDescriptorImpl) {

            super(serviceRequestIQ);

            this.twincodeDescriptorImpl = twincodeDescriptorImpl;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2019/04/10
     *
     * {
     *  "schemaId":"e6726692-8fe6-4d29-ae64-ba321d44a247",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"OnPushTwincodeIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":
     *  [
     *   {"name":"receivedTimestamp", "type":"long"}
     *  ]
     * }
     * </pre>
     */

    static final String ON_PUSH_TWINCODE_ACTION = "on-push-twincode";

    static class OnPushTwincodeIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 1;

        static class OnPushTwincodeIQSerializer extends ServiceResultIQSerializer {

            OnPushTwincodeIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnPushTwincodeIQ.SCHEMA_ID, SCHEMA_VERSION, OnPushTwincodeIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                OnPushTwincodeIQ onPushTwincodeIQ = (OnPushTwincodeIQ) object;
                encoder.writeLong(onPushTwincodeIQ.receivedTimestamp);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                long receivedTimestamp = decoder.readLong();
                return new OnPushTwincodeIQ(serviceResultIQ, receivedTimestamp);
            }
        }

        static final OnPushTwincodeIQSerializer SERIALIZER = new OnPushTwincodeIQSerializer();

        final long receivedTimestamp;

        OnPushTwincodeIQ(@NonNull String id, @NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion, long receivedTimestamp) {

            super(id, from, to, requestId, TWINLIFE_NAME, ON_PUSH_TWINCODE_ACTION, majorVersion, minorVersion);

            this.receivedTimestamp = receivedTimestamp;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" receivedTimestamp=");
            stringBuilder.append(receivedTimestamp);
            stringBuilder.append("\n");
        }

        public byte[] serialize(SerializerFactoryImpl serializerFactory, int majorVersion, int minorVersion) throws SerializerException, UnsupportedOperationException {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion != ConversationServiceImpl.MAJOR_VERSION_2 || minorVersion < ConversationServiceImpl.MINOR_VERSION_10) {
                throw new UnsupportedOperationException("Need 2.10 version at least");
            }
            OnPushTwincodeIQ.SERIALIZER.serialize(serializerFactory, binaryEncoder, this);
            return outputStream.toByteArray();
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnPushTwincodeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnPushTwincodeIQ(@NonNull ServiceResultIQ serviceResultIQ, long receivedTimestamp) {

            super(serviceResultIQ);

            this.receivedTimestamp = receivedTimestamp;
        }
    }


    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2017/01/13
     *
     * {
     *  "type":"enum",
     *  "name":"UpdateDescriptorTimestampType",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "symbols" : ["READ", "DELETE", "PEER_DELETE"]
     * }
     *
     * {
     *  "schemaId":"b814c454-299b-48c0-aa40-19afa72ccef8",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"UpdateDescriptorTimestampIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceRequestIQ"
     *  "fields":
     *  [
     *   {"name":"type", "type":"org.twinlife.schemas.conversation.UpdateDescriptorTimestampType"}
     *   {"name":"twincodeOutboundId", "type":"UUID"},
     *   {"name":"sequenceId", "type":"long"}
     *   {"name":"timestamp", "type":"long"}
     *  ]
     * }
     *
     * </pre>
     */

    static final String UPDATE_DESCRIPTOR_TIMESTAMP_ACTION = "update-descriptor-timestamp";

    static class UpdateDescriptorTimestampIQ extends ServiceRequestIQ {
        static final int SCHEMA_VERSION = 1;

        static class UpdateDescriptorTimestampIQSerializer extends ServiceRequestIQSerializer {

            UpdateDescriptorTimestampIQSerializer() {

                super(org.twinlife.twinlife.conversation.UpdateTimestampIQ.SCHEMA_ID, SCHEMA_VERSION, UpdateDescriptorTimestampIQ.class);
            }

            @Override
            public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

                super.serialize(serializerFactory, encoder, object);

                UpdateDescriptorTimestampIQ resetConversationIQ = (UpdateDescriptorTimestampIQ) object;
                switch (resetConversationIQ.timestampType) {
                    case READ:
                        encoder.writeEnum(0);
                        break;
                    case DELETE:
                        encoder.writeEnum(1);
                        break;
                    case PEER_DELETE:
                        encoder.writeEnum(2);
                        break;
                }
                encoder.writeUUID(resetConversationIQ.descriptorId.twincodeOutboundId);
                encoder.writeLong(resetConversationIQ.descriptorId.sequenceId);
                encoder.writeLong(resetConversationIQ.timestamp);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) super.deserialize(serializerFactory, decoder);
                UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType timestampType;
                int value = decoder.readEnum();
                switch (value) {
                    case 0:
                        timestampType = UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType.READ;
                        break;
                    case 1:
                        timestampType = UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType.DELETE;
                        break;
                    case 2:
                        timestampType = UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType.PEER_DELETE;
                        break;

                    default:
                        throw new SerializerException();
                }
                UUID twincodeOutboundId = decoder.readUUID();
                long sequenceId = decoder.readLong();
                long timestamp = decoder.readLong();

                return new UpdateDescriptorTimestampIQ(serviceRequestIQ, timestampType, new DescriptorId(0, twincodeOutboundId, sequenceId), timestamp);
            }
        }

        static final UpdateDescriptorTimestampIQSerializer SERIALIZER = new UpdateDescriptorTimestampIQSerializer();

        final UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType timestampType;
        final DescriptorId descriptorId;
        final long timestamp;

        UpdateDescriptorTimestampIQ(@NonNull String from, @NonNull String to, long requestId, int majorVersion, int minorVersion,
                                    UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType timestampType,
                                    @NonNull DescriptorId descriptorId,
                                    long timestamp) {

            super(from, to, requestId, TWINLIFE_NAME, UPDATE_DESCRIPTOR_TIMESTAMP_ACTION, majorVersion, minorVersion);

            this.timestampType = timestampType;
            this.descriptorId = descriptorId;
            this.timestamp = timestamp;
        }

        protected void appendTo(@NonNull StringBuilder stringBuilder) {

            super.appendTo(stringBuilder);

            stringBuilder.append(" timestampType=");
            stringBuilder.append(timestampType);
            stringBuilder.append("\n");
            stringBuilder.append(" descriptorId=");
            stringBuilder.append(descriptorId);
            stringBuilder.append("\n");
            stringBuilder.append(" timestamp=");
            stringBuilder.append(timestamp);
            stringBuilder.append("\n");
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("UpdateDescriptorTimestampIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private UpdateDescriptorTimestampIQ(@NonNull ServiceRequestIQ serviceRequestIQ, UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType type,
                                            @NonNull DescriptorId descriptorId, long timestamp) {

            super(serviceRequestIQ);

            this.timestampType = type;
            this.descriptorId = descriptorId;
            this.timestamp = timestamp;
        }
    }

    /*
     * <pre>
     *
     * Schema version 1
     *  Date: 2017/01/13
     *
     * {
     *  "schemaId":"87d33c5f-9b9b-49bf-a802-8bd24fb021a6",
     *  "schemaVersion":"1",
     *
     *  "type":"record",
     *  "name":"OnUpdateDescriptorTimestampIQ",
     *  "namespace":"org.twinlife.schemas.conversation",
     *  "super":"org.twinlife.schemas.ServiceResultIQ"
     *  "fields":
     *  []
     * }
     *
     * </pre>
     */

    static final String ON_UPDATE_DESCRIPTOR_TIMESTAMP_ACTION = "on-update-descriptor-timestamp";

    static class OnUpdateDescriptorTimestampIQ extends ServiceResultIQ {

        static final int SCHEMA_VERSION = 1;

        static class OnUpdateDescriptorTimestampIQSerializer extends ServiceResultIQSerializer {

            OnUpdateDescriptorTimestampIQSerializer() {

                super(org.twinlife.twinlife.conversation.OnUpdateTimestampIQ.SCHEMA_ID, SCHEMA_VERSION, OnResetConversationIQ.class);
            }

            @Override
            @NonNull
            public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

                ServiceResultIQ serviceResultIQ = (ServiceResultIQ) super.deserialize(serializerFactory, decoder);

                return new OnUpdateDescriptorTimestampIQ(serviceResultIQ);
            }
        }

        static final OnUpdateDescriptorTimestampIQSerializer SERIALIZER = new OnUpdateDescriptorTimestampIQSerializer();

        OnUpdateDescriptorTimestampIQ(String id, String from, String to, long requestId, int majorVersion, int minorVersion) {

            super(id, from, to, requestId, TWINLIFE_NAME, ON_UPDATE_DESCRIPTOR_TIMESTAMP_ACTION, majorVersion, minorVersion);
        }

        //
        // Override Object methods
        //

        @NonNull
        public String toString() {

            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnUpdateDescriptorTimestampIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        }

        //
        // Private Methods
        //

        private OnUpdateDescriptorTimestampIQ(ServiceResultIQ serviceResultIQ) {

            super(serviceResultIQ);
        }
    }
}
