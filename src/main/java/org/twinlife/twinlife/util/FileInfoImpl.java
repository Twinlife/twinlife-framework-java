/*
 *  Copyright (c) 2020-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;

/**
 * Information about a file that must be sent or received.
 *
 * The path is relative to the application files directory.
 */
public class FileInfoImpl {

    @NonNull
    private final Integer mFileId;
    @NonNull
    private final String mPath;
    private final long mDate;
    private final long mSize;
    long mRemoteOffset;

    public FileInfoImpl(int fileId, @NonNull String path, long size, long date) {

        this.mFileId = fileId;
        this.mPath = path;
        this.mDate = date;
        this.mSize = size;
        this.mRemoteOffset = 0;
    }

    public long getSize() {

        return mSize;
    }

    @NonNull
    public String getPath() {

        return mPath;
    }

    public long getModificationDate() {

        return mDate;
    }

    @NonNull
    public Integer getIndex() {

        return mFileId;
    }

    public void setRemoteOffset(long value) {

        mRemoteOffset = value;
    }

    //
    // Override Object methods
    //
    @NonNull
    @Override
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();

        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("FileInfoImpl\n");
            stringBuilder.append(" path=");
            stringBuilder.append(mPath);
            stringBuilder.append(" date=");
            stringBuilder.append(mDate);
            stringBuilder.append(" size=");
            stringBuilder.append(mSize);
        }
        return stringBuilder.toString();
    }
}
