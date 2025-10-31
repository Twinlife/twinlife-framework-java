/*
 *  Copyright (c) 2020 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.AccountMigrationService;
import org.twinlife.twinlife.AccountMigrationService.ErrorCode;
import org.twinlife.twinlife.AccountMigrationService.State;

/**
 * Information about a file that must be sent or received.
 *
 * The path is relative to the application files directory.
 */
class StatusImpl implements AccountMigrationService.Status {

    @NonNull
    private final State mState;
    private final boolean mIsConnected;
    private final long mBytesSent;
    private final long mEstimatedBytesRemainSend;
    private final long mBytesReceived;
    private final long mEstimatedBytesRemainReceive;
    private final int mReceiveErrorCount;
    private final int mSendErrorCount;
    private final ErrorCode mErrorCode;

    StatusImpl(@NonNull State state, boolean isConnected,
               long bytesSent, long estimatedBytesRemainSend, long bytesReceived,
               long estimatedBytesRemainReceive, int receiveErrorCount, int sendErrorCount,
               @Nullable ErrorCode errorCode) {

        boolean isFinished = state == State.TERMINATE || state == State.TERMINATED || state == State.STOPPED;
        this.mState = state;
        this.mIsConnected = isConnected;
        this.mBytesSent = bytesSent;
        if (estimatedBytesRemainSend <= 0 || isFinished) {
            this.mEstimatedBytesRemainSend = 0;
        } else {
            this.mEstimatedBytesRemainSend = estimatedBytesRemainSend;
        }
        this.mBytesReceived = bytesReceived;
        if (estimatedBytesRemainReceive <= 0 || isFinished) {
            this.mEstimatedBytesRemainReceive = 0;
        } else {
            this.mEstimatedBytesRemainReceive = estimatedBytesRemainReceive;
        }
        this.mReceiveErrorCount = receiveErrorCount;
        this.mSendErrorCount = sendErrorCount;
        this.mErrorCode = errorCode;
    }

    @Override
    @NonNull
    public AccountMigrationService.State getState() {

        return mState;
    }

    @Override
    public boolean isConnected() {

        return mIsConnected;
    }

    @Override
    public long getBytesSent() {

        return mBytesSent;
    }

    @Override
    public long getEstimatedBytesRemainSend() {

        return mEstimatedBytesRemainSend;
    }

    @Override
    public long getBytesReceived() {

        return mBytesReceived;
    }

    @Override
    public long getEstimatedBytesRemainReceive() {

        return mEstimatedBytesRemainReceive;
    }

    @Override
    public int getSendErrorCount() {

        return mSendErrorCount;
    }

    @Override
    public int getReceiveErrorCount() {

        return mReceiveErrorCount;
    }

    @Override
    public double getSendProgress() {

        long total = mBytesSent + mEstimatedBytesRemainSend;
        if (total == 0) {

            return 0;
        } else {
            return (mBytesSent * 100.0) / (double) total;
        }
    }

    @Override
    public double getReceiveProgress() {

        long total = mBytesReceived + mEstimatedBytesRemainReceive;
        if (total == 0) {

            return 0;
        } else {
            return (mBytesReceived * 100.0) / (double) total;
        }
    }

    @Override
    public double getProgress() {

        long total = mBytesReceived + mEstimatedBytesRemainReceive + mBytesSent + mEstimatedBytesRemainSend;
        if (total == 0) {

            return 0;
        } else {
            return ((mBytesReceived + mBytesSent) * 100.0) / (double) total;
        }
    }

    @Override
    @Nullable
    public ErrorCode getErrorCode() {

        return mErrorCode;
    }

    //
    // Override Object methods
    //
    @SuppressWarnings("StringBufferReplaceableByString")
    @NonNull
    @Override
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("StatusImpl\n");
        stringBuilder.append(" state=");
        stringBuilder.append(mState);
        stringBuilder.append(" isConnected=");
        stringBuilder.append(mIsConnected);
        stringBuilder.append(" bytesSent=");
        stringBuilder.append(mBytesSent);
        stringBuilder.append(" bytesReceived=");
        stringBuilder.append(mBytesReceived);
        stringBuilder.append(" estimatedBytesRemainSend=");
        stringBuilder.append(mEstimatedBytesRemainSend);
        stringBuilder.append(" estimatedBytesRemainReceive=");
        stringBuilder.append(mEstimatedBytesRemainReceive);
        stringBuilder.append(" errorCode=");
        stringBuilder.append(mErrorCode);
        stringBuilder.append(" progress=");
        stringBuilder.append(getProgress());
        return stringBuilder.toString();
    }
}
