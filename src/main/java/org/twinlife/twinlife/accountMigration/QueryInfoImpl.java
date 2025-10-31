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

import org.twinlife.twinlife.AccountMigrationService;

import java.io.Serializable;

class QueryInfoImpl implements AccountMigrationService.QueryInfo, Serializable {

    private final long mDirectoryCount;
    private final long mFileCount;
    private final long mMaxFileSize;
    private final long mTotalFileSize;
    private final long mDatabaseFileSize;
    private final long mLocalDatabaseAvailableSize;
    private final long mLocalFileAvailableSize;

    QueryInfoImpl(long directoryCount, long fileCount, long maxFileSize, long totalFileSize, long databaseFileSize,
                  long localDatabaseAvailableSize, long localFileAvailableSize) {

        mDirectoryCount = directoryCount;
        mFileCount = fileCount;
        mMaxFileSize = maxFileSize;
        mTotalFileSize = totalFileSize;
        mDatabaseFileSize = databaseFileSize;
        mLocalDatabaseAvailableSize = localDatabaseAvailableSize;
        mLocalFileAvailableSize = localFileAvailableSize;
    }

    @Override
    public long getDirectoryCount() {

        return mDirectoryCount;
    }

    @Override
    public long getFileCount() {

        return mFileCount;
    }

    @Override
    public long getMaxFileSize() {

        return mMaxFileSize;
    }

    @Override
    public long getTotalFileSize() {

        return mTotalFileSize;
    }

    @Override
    public long getDatabaseFileSize() {

        return mDatabaseFileSize;
    }

    @Override
    public long getDatabaseAvailableSpace() {

        return mLocalDatabaseAvailableSize;
    }

    @Override
    public long getFilesystemAvailableSpace() {

        return mLocalFileAvailableSize;
    }

    @SuppressWarnings("StringBufferReplaceableByString")
    @NonNull
    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder();

        stringBuilder.append("QueryInfo\n");
        stringBuilder.append(" directoryCount=");
        stringBuilder.append(mDirectoryCount);
        stringBuilder.append(" fileCount=");
        stringBuilder.append(mFileCount);
        stringBuilder.append(" maxFileSize=");
        stringBuilder.append(mMaxFileSize);
        stringBuilder.append(" totalFileSize=");
        stringBuilder.append(mTotalFileSize);
        stringBuilder.append(" databaseFileSize=");
        stringBuilder.append(mDatabaseFileSize);
        stringBuilder.append(" localDatabaseAvailableSize=");
        stringBuilder.append(mLocalDatabaseAvailableSize);
        stringBuilder.append(" localFileAvailableSize=");
        stringBuilder.append(mLocalFileAvailableSize);
        return stringBuilder.toString();
    }
}
