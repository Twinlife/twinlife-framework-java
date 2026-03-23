/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.Arrays;
import java.util.UUID;

public class BackupHeaderInfo implements Serializable {
    public final long date;
    @NonNull
    public final UUID backupId;
    @NonNull
    public final byte[] salt;
    @NonNull
    public final String applicationName;
    @NonNull
    public final String version;

    public BackupHeaderInfo(long date, @NonNull UUID backupId, @NonNull byte[] salt, @NonNull String applicationName, @NonNull String version) {
        this.date = date;
        this.backupId = backupId;
        this.salt = salt;
        this.applicationName = applicationName;
        this.version = version;
    }

    @NonNull
    @Override
    public String toString() {
        return "BackupHeaderInfo[" +
                "date=" + date +
                ", backupId=" + backupId +
                ", applicationName='" + applicationName + '\'' +
                ", version='" + version + '\'' +
                ", salt=" + Arrays.toString(salt) +
                ']';
    }
}
