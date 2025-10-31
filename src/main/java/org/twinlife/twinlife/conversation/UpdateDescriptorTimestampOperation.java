/*
 *  Copyright (c) 2017-2025 twinlife SA.
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
 *  "schemaId":"62e7fe3c-720c-4247-853a-8fca4bcf0e24",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"UpdateDescriptorTimestampOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
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

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_17;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.SERIALIZER_BUFFER_DEFAULT_SIZE;

class UpdateDescriptorTimestampOperation extends Operation {
    private static final String LOG_TAG = "UpdateDescriptorTim...";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("62e7fe3c-720c-4247-853a-8fca4bcf0e24");
    static final int SCHEMA_VERSION = 1;

    enum UpdateDescriptorTimestampType {
        READ, DELETE, PEER_DELETE
    }

    private final UpdateDescriptorTimestampType mTimestampType;
    // avoid collision with Operation mTimestamp field
    private final long mDescriptorTimestamp;
    @NonNull
    private final DescriptorId mDescriptorId;

    UpdateDescriptorTimestampOperation(@NonNull ConversationImpl conversationImpl,
                                       @NonNull UpdateDescriptorTimestampType timestampType,
                                       @NonNull DescriptorId descriptorId,
                                       long timestamp) {

        super(Operation.Type.UPDATE_DESCRIPTOR_TIMESTAMP, conversationImpl, descriptorId);

        mTimestampType = timestampType;
        mDescriptorTimestamp = timestamp;
        mDescriptorId = descriptorId;
    }

    UpdateDescriptorTimestampOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate,
                                       long descriptor, @Nullable byte[] content) {
        super(id, Operation.Type.UPDATE_DESCRIPTOR_TIMESTAMP, conversationId, creationDate, descriptor);

        long timestamp = creationDate;
        long sequenceId = 0;
        UpdateDescriptorTimestampType timestampType = UpdateDescriptorTimestampType.READ;
        UUID twincodeOutboundId = Twincode.NOT_DEFINED;
        if (content != null) {
            try {
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                final BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

                final int schemaVersion = decoder.readInt();
                if (schemaVersion == SCHEMA_VERSION) {
                    int value = decoder.readEnum();
                    switch (value) {
                        case 0:
                            // timestampType = UpdateDescriptorTimestampType.READ;
                            break;
                        case 1:
                            timestampType = UpdateDescriptorTimestampType.DELETE;
                            break;
                        case 2:
                            timestampType = UpdateDescriptorTimestampType.PEER_DELETE;
                            break;
                        default:
                            throw new SerializerException();
                    }
                    twincodeOutboundId = decoder.readUUID();
                    sequenceId = decoder.readLong();
                    timestamp = decoder.readLong();
                }

            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "deserialize", exception);
                }
            }
        }
        mTimestampType = timestampType;
        mDescriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
        mDescriptorTimestamp = timestamp;
    }

    @NonNull
    UpdateDescriptorTimestampType getTimestampType() {

        return mTimestampType;
    }

    @NonNull
    DescriptorId getUpdateDescriptorId() {

        return mDescriptorId;
    }

    @Nullable
    static byte[] serializeOperation(@NonNull UpdateDescriptorTimestampType timestampType, long timestamp,
                                     @NonNull DescriptorId descriptorId) {

        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);

            encoder.writeInt(SCHEMA_VERSION);
            switch (timestampType) {
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
            // Note: we must save the descriptor id as twincode and sequence Id because the descriptor
            // could have been removed from the database for the PEER_DELETE case and we must still
            // notify the peer for the deletion date.
            encoder.writeUUID(descriptorId.twincodeOutboundId);
            encoder.writeLong(descriptorId.sequenceId);
            encoder.writeLong(timestamp);
            return outputStream.toByteArray();

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error("serialize", "serialize", exception);
            }
            return null;
        }
    }

    @Override
    @Nullable
    byte[] serialize() {

        return serializeOperation(mTimestampType, mDescriptorTimestamp, mDescriptorId);
    }

    public void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append(" timestampType=");
            stringBuilder.append(mTimestampType);
            stringBuilder.append(" descriptorTimestamp=");
            stringBuilder.append(mDescriptorTimestamp);
        }
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_17)) {
            final UpdateTimestampIQ updateTimestampIQ = new UpdateTimestampIQ(UpdateTimestampIQ.IQ_UPDATE_TIMESTAMPS_SERIALIZER, requestId,
                    getUpdateDescriptorId(),
                    getTimestampType(),
                    getTimestamp());

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_UPDATE_OBJECT, updateTimestampIQ);
        } else {
            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.UpdateDescriptorTimestampIQ updateDescriptorTimestampIQ = new ConversationServiceIQ.UpdateDescriptorTimestampIQ(connection.getFrom(), connection.getTo(),
                    requestId, majorVersion, minorVersion, getTimestampType(),
                    getUpdateDescriptorId(),
                    mDescriptorTimestamp);

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            ConversationServiceIQ.UpdateDescriptorTimestampIQ.SERIALIZER.serialize(connection.getSerializerFactory(), binaryEncoder, updateDescriptorTimestampIQ);

            final byte[] content = outputStream.toByteArray();
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_UPDATE_OBJECT, content);
        }
        return ErrorCode.QUEUED;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        final StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("UpdateDescriptorTimestampOperation:");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
