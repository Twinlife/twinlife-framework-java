/*
 *  Copyright (c) 2016-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * ResetConversationOperation
 *
 * <pre>
 * Schema version 4
 *  Date: 2022/03/24
 *
 * {
 *  "schemaId":"16d83e7c-761a-4091-8946-59ef5f7903d3",
 *  "schemaVersion":"4",
 *
 *  "type":"record",
 *  "name":"ResetConversationOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
 *  "fields":
 *  [
 *   {"name":"minSequenceId", "type":"long"}
 *   {"name":"peerMinSequenceId", "type":"long"}
 *   {"name":"count", "type":"long"}
 *   [ {"name":"memberTwincodeOutboundId", "type":"uuid"},
 *     {"name":"peerMinSequenceId", "type":"long"}],
 *   {"name":"clearDescriptorId":["null", {
 *     {"name":"twincodeOutboundId", "type":"uuid"},
 *     {"name":"sequenceId", "type":"long"}
 *   }},
 *   {"name":"clearTimestamp", "type":"long"}
 *   {"name":"clearMode", "type":"int"}
 *   {"name":"createdTimestamp", "type":"long"}
 *  ]
 * }
 *
 * Schema version 3
 *  Date: 2022/02/09
 *
 * {
 *  "schemaId":"16d83e7c-761a-4091-8946-59ef5f7903d3",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"ResetConversationOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
 *  "fields":
 *  [
 *   {"name":"minSequenceId", "type":"long"}
 *   {"name":"peerMinSequenceId", "type":"long"}
 *   {"name":"count", "type":"long"}
 *   [ {"name":"memberTwincodeOutboundId", "type":"uuid"},
 *     {"name":"peerMinSequenceId", "type":"long"}],
 *   {"name":"clearDescriptorId":["null", {
 *     {"name":"twincodeOutboundId", "type":"uuid"},
 *     {"name":"sequenceId", "type":"long"}
 *   }},
 *   {"name":"clearTimestamp", "type":"long"}
 *   {"name":"clearMode", "type":"int"}
 *  ]
 * }
 *
 * Schema version 2
 *  Date: 2018/10/01
 *
 * {
 *  "schemaId":"16d83e7c-761a-4091-8946-59ef5f7903d3",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"ResetConversationOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
 *  "fields":
 *  [
 *   {"name":"minSequenceId", "type":"long"}
 *   {"name":"peerMinSequenceId", "type":"long"}
 *   {"name":"count", "type":"long"}
 *   [ {"name":"memberTwincodeOutboundId", "type":"uuid"},
 *     {"name":"peerMinSequenceId", "type":"long"}]
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
import org.twinlife.twinlife.ConversationService.ClearMode;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_13;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_15;

class ResetConversationOperation extends Operation {
    private static final String LOG_TAG = "ResetConversationOp...";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("16d83e7c-761a-4091-8946-59ef5f7903d3");
    static final int SCHEMA_VERSION_4 = 4;
    static final int SCHEMA_VERSION_3 = 3;
    static final int SCHEMA_VERSION_2 = 2;

    /**
     * Version 4 adds a clear descriptor that informs the peer the conversation was cleared.
     */
    static class ResetConversationOperationSerializer_4 {

        // @Override
        @NonNull
        public ResetConversationOperation deserialize(@NonNull Decoder decoder) throws SerializerException {

            // Operation operation = (Operation) super.deserialize(serializerFactory, decoder);
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

            DescriptorId descriptorId;
            if (decoder.readLong() != 0) {
                UUID twincodeOutboundId = decoder.readUUID();
                long sequenceId = decoder.readLong();
                descriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
            } else {
                descriptorId = null;
            }
            long clearTimestamp = decoder.readLong();
            ClearMode clearMode;
            switch (decoder.readEnum()) {
                case 0:
                    clearMode = ClearMode.CLEAR_LOCAL;
                    break;

                case 1:
                    clearMode = ClearMode.CLEAR_BOTH;
                    break;

                    // 2023-02-21: added this new clear mode but supported only starting with ConversationService 2.15.
                case 2:
                    clearMode = ClearMode.CLEAR_MEDIA;
                    break;

                default:
                    throw new SerializerException();
            }
            long createdTimestamp = decoder.readLong();
            return new ResetConversationOperation(minSequenceId, peerMinSequenceId, members, descriptorId, clearTimestamp, createdTimestamp, clearMode);
        }
    }

    static final ResetConversationOperationSerializer_4 SERIALIZER_4 = new ResetConversationOperationSerializer_4();

    /**
     * Version 3 adds a clear descriptor that informs the peer the conversation was cleared.
     */
    static class ResetConversationOperationSerializer_3 {

        // @Override
        @NonNull
        public ResetConversationOperation deserialize(@NonNull Decoder decoder) throws SerializerException {

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

            DescriptorId descriptorId;
            if (decoder.readLong() != 0) {
                UUID twincodeOutboundId = decoder.readUUID();
                long sequenceId = decoder.readLong();
                descriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
            } else {
                descriptorId = null;
            }
            long clearTimestamp = decoder.readLong();
            ClearMode clearMode;
            switch (decoder.readEnum()) {
                case 0:
                    clearMode = ClearMode.CLEAR_LOCAL;
                    break;

                case 1:
                    clearMode = ClearMode.CLEAR_BOTH;
                    break;

                default:
                    throw new SerializerException();
            }
            return new ResetConversationOperation(minSequenceId, peerMinSequenceId, members, descriptorId, clearTimestamp, clearTimestamp, clearMode);
        }
    }

    static final ResetConversationOperationSerializer_3 SERIALIZER_3 = new ResetConversationOperationSerializer_3();

    /**
     * Version 2 adds support for group reset conversation: the peerMinSequenceId is specific to each group member
     * but a given group member has the same peerMinSequenceId for every device in the group.
     */
    static class ResetConversationOperationSerializer_2 {

        // @Override
        @NonNull
        public ResetConversationOperation deserialize(@NonNull Decoder decoder) throws SerializerException {

            // Operation operation = (Operation) super.deserialize(serializerFactory, decoder);
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
            return new ResetConversationOperation(minSequenceId, peerMinSequenceId, members, null, 0, 0, ClearMode.CLEAR_BOTH);
        }
    }

    static final ResetConversationOperationSerializer_2 SERIALIZER_2 = new ResetConversationOperationSerializer_2();

    private long mMinSequenceId;
    private long mPeerMinSequenceId;

    @Nullable
    private List<DescriptorId> mResetMembers;
    @Nullable
    private ClearDescriptorImpl mClearDescriptorImpl;
    private long mClearTimestamp;
    private long mCreatedTimestamp;
    private ClearMode mClearMode;
    @Nullable
    private DescriptorId mClearDescriptorId;

    ResetConversationOperation(@NonNull ConversationImpl conversationImpl,
                               @Nullable ClearDescriptorImpl clearDescriptorImpl,
                               long minSequenceId, long peerMinSequenceId,
                               @Nullable List<DescriptorId> members,
                               long clearTimestamp, ClearMode clearMode) {

        super(Operation.Type.RESET_CONVERSATION, conversationImpl, clearDescriptorImpl);

        mClearDescriptorImpl = clearDescriptorImpl;
        if (clearDescriptorImpl != null) {
            mCreatedTimestamp = clearDescriptorImpl.getCreatedTimestamp();
            mClearDescriptorId = clearDescriptorImpl.getDescriptorId();
        } else {
            mCreatedTimestamp = 0;
            mClearDescriptorId = null;
        }
        mMinSequenceId = minSequenceId;
        mPeerMinSequenceId = peerMinSequenceId;
        mResetMembers = members;
        mClearTimestamp = clearTimestamp;
        mClearMode = clearMode;
    }

    private ResetConversationOperation(long minSequenceId, long peerMinSequenceId,
                                       @Nullable List<DescriptorId> members,
                                       @Nullable DescriptorId descriptorId,
                                       long clearTimestamp, long createdTimestamp, ClearMode clearMode) {

        super(0, Operation.Type.RESET_CONVERSATION, new DatabaseIdentifier(null, 0), createdTimestamp, 0);

        mClearDescriptorImpl = null;
        mClearDescriptorId = descriptorId;
        mCreatedTimestamp = 0;
        mMinSequenceId = minSequenceId;
        mPeerMinSequenceId = peerMinSequenceId;
        mResetMembers = members;
        mClearTimestamp = clearTimestamp;
        mClearMode = clearMode;
    }

    ResetConversationOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptorId,
                               @Nullable byte[] content) {
        super(id, Operation.Type.RESET_CONVERSATION, conversationId, creationDate, descriptorId);

        mMinSequenceId = 0;
        mPeerMinSequenceId = 0;
        mResetMembers = null;
        if (content != null) {
            try {
                ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

                int schemaVersion = decoder.readInt();
                if (schemaVersion == SCHEMA_VERSION_4) {
                    mMinSequenceId = decoder.readLong();
                    mPeerMinSequenceId = decoder.readLong();
                    long count = decoder.readLong();
                    if (count > 0) {
                        mResetMembers = new ArrayList<>();
                        while (count > 0) {
                            count--;
                            UUID memberTwincodeOutboundId = decoder.readUUID();
                            long memberPeerMinSeqenceId = decoder.readLong();
                            mResetMembers.add(new DescriptorId(0, memberTwincodeOutboundId, memberPeerMinSeqenceId));
                        }
                    }

                    if (decoder.readEnum() != 0) {
                        UUID twincodeOutboundId = decoder.readUUID();
                        long sequenceId = decoder.readLong();

                        mClearDescriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
                    }

                    mClearTimestamp = decoder.readLong();
                    switch (decoder.readEnum()) {
                        case 0:
                            mClearMode = ClearMode.CLEAR_LOCAL;
                            break;

                        case 1:
                            mClearMode = ClearMode.CLEAR_BOTH;
                            break;

                        // 2023-02-21: added this new clear mode but supported only starting with ConversationService 2.15.
                        case 2:
                            mClearMode = ClearMode.CLEAR_MEDIA;
                            break;

                        case 3:
                            mClearMode = ClearMode.CLEAR_BOTH_MEDIA;
                            break;

                        default:
                            throw new SerializerException();
                    }
                    mCreatedTimestamp = decoder.readLong();
                }

            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "deserialize", exception);
                }
            }
        }
    }

    long getMinSequenceId() {

        return mMinSequenceId;
    }

    long getPeerMinSequenceId() {

        return mPeerMinSequenceId;
    }

    ClearDescriptorImpl getClearDescriptorImpl() {

        return mClearDescriptorImpl;
    }

    long getCreatedTimestamp() {

        if (mCreatedTimestamp == 0) {
            return mClearTimestamp;
        } else {
            return mCreatedTimestamp;
        }
    }

    @Override
    @Nullable
    byte[] serialize() {

        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);

            encoder.writeInt(SCHEMA_VERSION_4);
            encoder.writeLong(mMinSequenceId);
            encoder.writeLong(mPeerMinSequenceId);
            if (mResetMembers == null) {
                encoder.writeLong(0);
            } else {
                encoder.writeLong(mResetMembers.size());
                for (DescriptorId member : mResetMembers) {
                    encoder.writeUUID(member.twincodeOutboundId);
                    encoder.writeLong(member.sequenceId);
                }
            }
            if (mClearDescriptorId == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(mClearDescriptorId.twincodeOutboundId);
                encoder.writeLong(mClearDescriptorId.sequenceId);
            }
            encoder.writeLong(mClearTimestamp);
            switch (mClearMode) {
                case CLEAR_LOCAL:
                    encoder.writeEnum(0);
                    break;

                case CLEAR_BOTH:
                    encoder.writeEnum(1);
                    break;

                // 2023-02-21: added this new clear mode but supported only starting with ConversationService 2.15.
                case CLEAR_MEDIA:
                    encoder.writeEnum(2);
                    break;

                // 2023-04-20: added to allow cleaning of media on both devices.
                case CLEAR_BOTH_MEDIA:
                    encoder.writeEnum(3);
                    break;
            }
            encoder.writeLong(mCreatedTimestamp);
            return outputStream.toByteArray();

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error("serialize", "serialize", exception);
            }
            return null;
        }
    }

    public void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" minSequenceId=");
            stringBuilder.append(mMinSequenceId);
            stringBuilder.append(" peerMinSequenceId=");
            stringBuilder.append(mPeerMinSequenceId);
            stringBuilder.append(" clearTimestamp=");
            stringBuilder.append(mClearTimestamp);
            stringBuilder.append(" clearMode=");
            stringBuilder.append(mClearMode);
        }
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        ClearDescriptorImpl clearDescriptorImpl = getClearDescriptorImpl();
        if (clearDescriptorImpl == null && getDescriptorId() != 0) {
            DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (descriptorImpl instanceof ClearDescriptorImpl) {
                clearDescriptorImpl = (ClearDescriptorImpl) descriptorImpl;
                mClearDescriptorImpl = clearDescriptorImpl;
            }
        }
        if (clearDescriptorImpl != null && clearDescriptorImpl.getSentTimestamp() <= 0) {
            clearDescriptorImpl.setSentTimestamp(System.currentTimeMillis());
            connection.updateDescriptorImplTimestamps(clearDescriptorImpl);
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_13)) {

            // The clear mode CLEAR_MEDIA is supported only starting with 2.15 version, drop the operation otherwise.
            if (mClearMode == ClearMode.CLEAR_MEDIA && !connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_15)) {
                return ErrorCode.SUCCESS;
            }

            // Note: the clear descriptor id is fake and not saved in the database.
            // It contains our twincode outbound UUID and a valid sequenceId which is send to the peer.
            if (clearDescriptorImpl == null && mClearDescriptorId != null) {
                long createdTimestamp = getCreatedTimestamp();
                long sentTimestamp = System.currentTimeMillis();
                clearDescriptorImpl = new ClearDescriptorImpl(mClearDescriptorId, createdTimestamp, sentTimestamp, mClearTimestamp);
            }
            final ResetConversationIQ resetConversationIQ = new ResetConversationIQ(ResetConversationIQ.IQ_RESET_CONVERSATION_SERIALIZER, requestId,
                    clearDescriptorImpl, mClearTimestamp, mClearMode);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_RESET_CONVERSATION, resetConversationIQ);
            return ErrorCode.QUEUED;

        } else if (mClearMode != ClearMode.CLEAR_BOTH) {

            // The descriptor contains a mode not supported by old versions.
            return ErrorCode.SUCCESS;

        } else {
            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.ResetConversationIQ resetConversationIQ = new ConversationServiceIQ.ResetConversationIQ(connection.getFrom(), connection.getTo(), requestId,
                    majorVersion, minorVersion, getMinSequenceId(), getPeerMinSequenceId(),
                    mResetMembers);
            final byte[] content = resetConversationIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_RESET_CONVERSATION, content);
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
            stringBuilder.append("ResetConversationOperation:");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Ro";
        }
    }
}
