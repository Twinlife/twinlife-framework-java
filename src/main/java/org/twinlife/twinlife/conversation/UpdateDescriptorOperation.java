/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
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
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_20;

/*
 * UpdateDescriptorOperation
 *
 * <pre>
 * Schema version 1
 *  Date: 2025/05/21
 *
 * {
 *  "schemaVersion":"1",
 *  "type":"record",
 *  "name":"UpdateDescriptorOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "fields":
 *  [
 *   {"name":"updateFlags", "type":"int"}
 *  ]
 * }
 */
public class UpdateDescriptorOperation extends Operation {
    private static final String LOG_TAG = "UpdateDescOperation";
    private static final boolean DEBUG = false;

    static final int UPDATE_MESSAGE = 0x01;
    static final int UPDATE_COPY_ALLOWED = 0x02;
    static final int UPDATE_EXPIRATION = 0x04;

    @Nullable
    private volatile DescriptorImpl mDescriptorImpl;
    private final int mUpdateFlags;

    static int buildFlags(@Nullable String message, @Nullable Boolean copyAllowed, @Nullable Long expiration) {

        return (message != null ? UPDATE_MESSAGE : 0)
                | (copyAllowed != null ? UPDATE_COPY_ALLOWED : 0)
                | (expiration != null ? UPDATE_EXPIRATION : 0);
    }

    UpdateDescriptorOperation(@NonNull ConversationImpl conversationImpl,
                              @NonNull DescriptorImpl descriptorImpl, int flags) {

        super(Operation.Type.UPDATE_OBJECT, conversationImpl, descriptorImpl);

        mDescriptorImpl = descriptorImpl;
        mUpdateFlags = flags & (UPDATE_MESSAGE | UPDATE_COPY_ALLOWED | UPDATE_EXPIRATION);
    }

    UpdateDescriptorOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptorId,
                              @Nullable byte[] content) {
        super(id, Operation.Type.UPDATE_OBJECT, conversationId, creationDate, descriptorId);

        int updateFlags = 0;
        if (content != null) {
            try {
                final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                final BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

                final int schemaVersion = decoder.readInt();
                if (schemaVersion == 1) {
                    updateFlags = decoder.readInt();
                }

            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "deserialize", exception);
                }
            }
        }

        mUpdateFlags = updateFlags & (UPDATE_MESSAGE | UPDATE_COPY_ALLOWED | UPDATE_EXPIRATION);
    }

    @Nullable
    DescriptorImpl getDescriptorImpl() {

        return mDescriptorImpl;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        DescriptorImpl descriptorImpl = mDescriptorImpl;
        if (descriptorImpl == null) {
            descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (descriptorImpl == null) {
                return ErrorCode.EXPIRED;
            }
            mDescriptorImpl = descriptorImpl;
        }

        if (!connection.preparePush(descriptorImpl)) {
            return ErrorCode.EXPIRED;
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_20)) {
            final String message;
            final Boolean copyAllowed;
            final Long expireTimeout;
            if (descriptorImpl instanceof ObjectDescriptorImpl) {
                final ObjectDescriptorImpl objectDescriptor = (ObjectDescriptorImpl) descriptorImpl;

                message = (mUpdateFlags & UPDATE_MESSAGE) != 0 ? objectDescriptor.getMessage() : null;
                copyAllowed = (mUpdateFlags & UPDATE_COPY_ALLOWED) != 0 ? objectDescriptor.isCopyAllowed() : null;
                expireTimeout = (mUpdateFlags & UPDATE_EXPIRATION) != 0 ? objectDescriptor.getExpireTimeout() : null;
            } else if (descriptorImpl instanceof FileDescriptorImpl) {
                final FileDescriptorImpl fileDescriptor = (FileDescriptorImpl) descriptorImpl;

                message = null;
                copyAllowed = (mUpdateFlags & UPDATE_COPY_ALLOWED) != 0 ? fileDescriptor.isCopyAllowed() : null;
                expireTimeout = (mUpdateFlags & UPDATE_EXPIRATION) != 0 ? fileDescriptor.getExpireTimeout() : null;
            } else {
                return ErrorCode.BAD_REQUEST;
            }
            final UpdateDescriptorIQ pushObjectIQ = new UpdateDescriptorIQ(UpdateDescriptorIQ.IQ_UPDATE_DESCRIPTOR_SERIALIZER, requestId,
                    descriptorImpl.getDescriptorId(), descriptorImpl.getUpdatedTimestamp(),
                    message, copyAllowed, expireTimeout);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_UPDATE_OBJECT, pushObjectIQ);
            return ErrorCode.QUEUED;

        } else {

            // The descriptor contains a parameter not supported by old versions.
            return ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER;
        }
    }

    @Override
    @Nullable
    byte[] serialize() {

        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);

            encoder.writeInt(1);
            encoder.writeInt(mUpdateFlags);
            return outputStream.toByteArray();

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error("serialize", "serialize", exception);
            }
            return null;
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
            stringBuilder.append("UpdateDescOperation:");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
