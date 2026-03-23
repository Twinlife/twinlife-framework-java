/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.UUID;

public class BackupInfo implements Serializable {
    public final UUID id;
    public final long creationDate;

    public BackupInfo(UUID id, long creationDate) {
        this.id = id;
        this.creationDate = creationDate;
    }

    @NonNull
    @Override
    public String toString() {
        return "BackupInfo{" +
                "id=" + id +
                ", creationDate=" + creationDate +
                '}';
    }
}
