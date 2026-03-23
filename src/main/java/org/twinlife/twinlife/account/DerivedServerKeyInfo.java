/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.UUID;

public class DerivedServerKeyInfo {
    @NonNull
    public final byte[] derivedServerKey;
    @Nullable
    public final UUID lastBackupId;
    public final long lastBackupTimestamp;

    public DerivedServerKeyInfo(@NonNull byte[] derivedServerKey, @Nullable UUID lastBackupId, long lastBackupDate) {
        this.derivedServerKey = derivedServerKey;
        this.lastBackupId = lastBackupId;
        this.lastBackupTimestamp = lastBackupDate;
    }

    @NonNull
    @Override
    public String toString() {
        return "DerivedServerKeyInfo{" +
                "derivedServerKey=" + Arrays.toString(derivedServerKey) +
                ", lastBackupId=" + lastBackupId +
                ", lastBackupDate=" + lastBackupTimestamp +
                '}';
    }
}
