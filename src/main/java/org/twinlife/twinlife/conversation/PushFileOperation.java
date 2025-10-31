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
 *  "schemaId":"e8fb18fd-d221-4f25-8099-6f09745136a5",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushFileOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
 *  "fields":
 *  [
 *   {"name":"twincodeOutboundId", "type":"UUID"}
 *   {"name":"sequenceId", "type":"long"}
 *   {"name":"chunkStart", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.UUID;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.CHUNK_SIZE;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_1;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_12;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_19;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.SERIALIZER_BUFFER_DEFAULT_SIZE;

class PushFileOperation extends FileOperation {
    private static final String LOG_TAG = "PushFileOperation";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("e8fb18fd-d221-4f25-8099-6f09745136a5");

    PushFileOperation(@NonNull ConversationImpl conversationImpl,
                      @NonNull FileDescriptorImpl fileDescriptorImpl) {

        super(Operation.Type.PUSH_FILE, conversationImpl, fileDescriptorImpl);
    }

    PushFileOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptor, long chunkStart) {
        super(id, Operation.Type.PUSH_FILE, conversationId, creationDate, descriptor);

        mChunkStart = chunkStart;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        FileDescriptorImpl fileDescriptorImpl = mFileDescriptorImpl;
        if (fileDescriptorImpl == null) {
            final DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (!(descriptorImpl instanceof FileDescriptorImpl)) {
                return ErrorCode.EXPIRED;
            }
            fileDescriptorImpl = (FileDescriptorImpl) descriptorImpl;
            mFileDescriptorImpl = fileDescriptorImpl;
        }

        if (!connection.preparePush(fileDescriptorImpl)) {
            return ErrorCode.EXPIRED;
        }

        if (mChunkStart == PushFileOperation.NOT_INITIALIZED) {
            return sendPushFileIQ(connection, fileDescriptorImpl);
        } else {
            return sendPushFileChunkIQ(connection, fileDescriptorImpl);
        }
    }

    @NonNull
    private ErrorCode sendPushFileIQ(@NonNull ConversationConnection connection,
                                     @NonNull FileDescriptorImpl fileDescriptorImpl) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendPushFileIQ: connection=" + connection + " fileDescriptorImpl=" + fileDescriptorImpl);
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_12)) {
            byte[] thumbnail = fileDescriptorImpl.loadThumbnailData(connection.getFilesDir());

            // If the thumbnail is big, send it before the PushFileIQ as several PushFileChunkIQ with
            // a dedicated schemaId (there is be no ack for these IQs).  When the PushFileIQ is received
            // it will have a nil thumbnail but it was received before and we will get back the OnPushFileIQ
            // that valides the correct reception of the thumbnail+PushFileIQ.  We use 2xbestChunkSize
            // to send chunks in the range [32, 64, 128K] depending on the RTT.  We must not exceed 256K
            // otherwise WebRTC will not send the IQ.
            final int chunkSize = 2 * connection.getBestChunkSize();
            if (thumbnail != null && thumbnail.length > chunkSize && connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_19)) {
                int offset = 0;
                while (offset < thumbnail.length) {
                    int len = thumbnail.length - offset;
                    if (len > chunkSize) {
                        len = chunkSize;
                    }
                    PushFileChunkIQ pushThumbnailIQ = new PushFileChunkIQ(PushThumbnailIQ.IQ_PUSH_THUMBNAIL_SERIALIZER,
                            requestId, fileDescriptorImpl.getDescriptorId(), 0, offset, offset, thumbnail, len);
                    connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_FILE_CHUNK, pushThumbnailIQ);
                    offset += len;
                }
                // Send a 0 byte thumbnail.
                thumbnail = new byte[0];
            }
            PushFileIQ pushFileIQ = new PushFileIQ(PushFileIQ.IQ_PUSH_FILE_SERIALIZER, requestId, fileDescriptorImpl, thumbnail);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_FILE, pushFileIQ);
            return ErrorCode.QUEUED;

        } else if (fileDescriptorImpl.getSendTo() == null && fileDescriptorImpl.getExpireTimeout() == 0 && fileDescriptorImpl.getReplyToDescriptorId() == null) {

            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

            final ConversationServiceIQ.PushFileIQ pushFileIQ;
            switch (fileDescriptorImpl.getType()) {
                case FILE_DESCRIPTOR:
                    pushFileIQ = new ConversationServiceIQ.PushFileIQ(connection.getFrom(), connection.getTo(), requestId,
                            majorVersion, minorVersion, new FileDescriptorImpl(fileDescriptorImpl, true));
                    break;

                case IMAGE_DESCRIPTOR:
                    pushFileIQ = new ConversationServiceIQ.PushFileIQ(connection.getFrom(), connection.getTo(), requestId,
                            majorVersion, minorVersion, new ImageDescriptorImpl((ImageDescriptorImpl) fileDescriptorImpl, true));
                    break;

                case AUDIO_DESCRIPTOR:
                    pushFileIQ = new ConversationServiceIQ.PushFileIQ(connection.getFrom(), connection.getTo(), requestId,
                            majorVersion, minorVersion, new AudioDescriptorImpl((AudioDescriptorImpl) fileDescriptorImpl, true));
                    break;

                case VIDEO_DESCRIPTOR:
                    pushFileIQ = new ConversationServiceIQ.PushFileIQ(connection.getFrom(), connection.getTo(), requestId,
                            majorVersion, minorVersion, new VideoDescriptorImpl((VideoDescriptorImpl) fileDescriptorImpl, true));
                    break;

                case NAMED_FILE_DESCRIPTOR:
                    pushFileIQ = new ConversationServiceIQ.PushFileIQ(connection.getFrom(), connection.getTo(), requestId,
                            majorVersion, minorVersion, new NamedFileDescriptorImpl((NamedFileDescriptorImpl) fileDescriptorImpl, true));
                    break;

                default:
                    throw new SerializerException();
            }

            final byte[] content = pushFileIQ.serialize(connection.getSerializerFactory(), majorVersion, minorVersion);
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_PUSH_FILE, content);
            return ErrorCode.QUEUED;

        } else {

            // The descriptor contains a parameter not supported by old versions.
            return connection.operationNotSupported(fileDescriptorImpl);
        }
    }

    @NonNull
    private ErrorCode sendPushFileChunkIQ(@NonNull ConversationConnection connection,
                                          @NonNull FileDescriptorImpl fileDescriptorImpl) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendPushFileChunkIQ: connection=" + connection +
                    " fileDescriptorImpl=" + fileDescriptorImpl);
        }

        final File filesDir = connection.getFilesDir();
        if (filesDir == null) {
            return connection.operationNotSupported(fileDescriptorImpl);
        }

        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_12)) {

            if (getRequestId() < 0) {
                // This is the first push chunk request and we have to know the current state on the peer.
                // Send a first PushFileChunkIQ with an empty data chunk to ask the peer its current state.
                final long requestId = connection.newRequestId();
                final long now = System.currentTimeMillis();

                final PushFileChunkIQ pushFileChunkIQ = new PushFileChunkIQ(PushFileChunkIQ.IQ_PUSH_FILE_CHUNK_SERIALIZER,
                        requestId, fileDescriptorImpl.getDescriptorId(), now, 0, 0, null, 0);

                updateRequestId(requestId);
                mSentOffset = -1L;
                connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_FILE_CHUNK, pushFileChunkIQ);
                return ErrorCode.QUEUED;

            } else {
                final long requestId = getRequestId();
                int chunkSize = connection.getBestChunkSize();

                while (isReadyToSend(fileDescriptorImpl.getLength())) {
                    long offset = mSentOffset;
                    byte[] chunk = connection.readChunk(filesDir, fileDescriptorImpl, offset, chunkSize);
                    if (chunk == null) {
                        // File was removed, send a delete descriptor operation (current operation is deleted).
                        connection.deleteFileDescriptor(fileDescriptorImpl, this);
                        return ErrorCode.QUEUED;
                    }

                    final long now = System.currentTimeMillis();
                    final PushFileChunkIQ pushFileChunkIQ = new PushFileChunkIQ(PushFileChunkIQ.IQ_PUSH_FILE_CHUNK_SERIALIZER,
                            requestId, fileDescriptorImpl.getDescriptorId(), now, offset, 0, chunk, chunk.length);

                    offset = offset + chunk.length;
                    mSentOffset = offset;

                    connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_FILE_CHUNK, pushFileChunkIQ);
                }
                return ErrorCode.QUEUED;
            }

        } else if (fileDescriptorImpl.getSendTo() == null && fileDescriptorImpl.getExpireTimeout() == 0 && fileDescriptorImpl.getReplyToDescriptorId() == null) {

            final int majorVersion = connection.getMaxPeerMajorVersion();
            final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);
            if (majorVersion == MAJOR_VERSION_1) {
                throw new UnsupportedOperationException();
            }

            final byte[] chunk = connection.readChunk(filesDir, fileDescriptorImpl, getChunkStart(), CHUNK_SIZE);
            if (chunk == null) {
                // File was removed, send a delete descriptor operation.
                connection.deleteFileDescriptor(fileDescriptorImpl, this);
                return ErrorCode.QUEUED;
            }

            final ConversationServiceIQ.PushFileChunkIQ pushFileChunkIQ = new ConversationServiceIQ.PushFileChunkIQ(connection.getFrom(), connection.getTo(), connection.newRequestId(), majorVersion, minorVersion,
                    fileDescriptorImpl.getDescriptorId(), getChunkStart(), chunk);

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
            final BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            ConversationServiceIQ.PushFileChunkIQ.SERIALIZER.serialize(connection.getSerializerFactory(), binaryEncoder, pushFileChunkIQ);

            updateRequestId(pushFileChunkIQ.getRequestId());
            connection.sendMessage(PeerConnectionService.StatType.IQ_SET_PUSH_FILE_CHUNK, outputStream.toByteArray());
            return ErrorCode.QUEUED;

        } else {

            // The descriptor contains a parameter not supported by old versions.
            return connection.operationNotSupported(fileDescriptorImpl);
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
            stringBuilder.append("PushFileOperation:\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Fo";
        }
    }
}
