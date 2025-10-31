/*
 *  Copyright (c) 2016-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;

public class FileOperation extends Operation {

    private static final long ESTIMATED_SIZE = Operation.ESTIMATED_SIZE + 16 + 8 + 8;
    private static final long DATA_WINDOW_SIZE = 256 * 1024;

    static final long NOT_INITIALIZED = -1L;

    protected volatile long mChunkStart;
    @Nullable
    protected volatile FileDescriptorImpl mFileDescriptorImpl;
    protected long mSentOffset;

    protected FileOperation(@NonNull Type type, @NonNull ConversationImpl conversationImpl,
                            @NonNull FileDescriptorImpl fileDescriptorImpl) {
        super(type, conversationImpl, fileDescriptorImpl);

        mChunkStart = NOT_INITIALIZED;
        mFileDescriptorImpl = fileDescriptorImpl;
        mSentOffset = 0;
    }

    protected FileOperation(long id, @NonNull Type type, @NonNull DatabaseIdentifier conversationId,
                            long creationDate, long descriptor) {
        super(id, type, conversationId, creationDate, descriptor);

        mSentOffset = 0;
    }

    @Nullable
    FileDescriptorImpl getFileDescriptorImpl() {

        return mFileDescriptorImpl;
    }

    long getChunkStart() {

        return mChunkStart;
    }

    void setChunkStart(long chunkStart) {

        mChunkStart = chunkStart;
        if (mSentOffset < chunkStart) {
            mSentOffset = chunkStart;
        }
    }

    boolean isReadyToSend(long length) {

        // Check if we have sent all our data chunks.
        if (mSentOffset >= length) {

            return false;
        }

        // Check if we know where to start (otherwise we are waiting for the peer to tell us its position).
        if (mSentOffset < 0) {

            return false;
        }

        // Compute the chunk size that is not yet acknowledged and don't send if we exceed the data window (1Mb).
        final long sentNotAckwnoledged = mSentOffset - mChunkStart;
        return sentNotAckwnoledged >= 0 && sentNotAckwnoledged < DATA_WINDOW_SIZE;
    }

    long getEstimatedSize() {

        if (mFileDescriptorImpl != null) {
            return ESTIMATED_SIZE + mFileDescriptorImpl.getLength();
        } else {
            return ESTIMATED_SIZE;
        }
    }

    public void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" chunkStart=");
            stringBuilder.append(mChunkStart);
            stringBuilder.append(" sendOffset=");
            stringBuilder.append(mSentOffset);
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
            stringBuilder.append("FileOperation:\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Fo";
        }
    }
}
