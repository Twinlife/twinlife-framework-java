/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class RawKeyInfo {

    public final long creationDate;
    public final long modificationDate;
    @NonNull
    public final byte[] signingKey;
    @Nullable
    public final byte[] encryptionKey;
    public final int flags;

    public RawKeyInfo(long creationDate, long modificationDate, @NonNull byte[] signingKey, @Nullable byte[] encryptionKey, int flags) {
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
        this.signingKey = signingKey;
        this.encryptionKey = encryptionKey;
        this.flags = flags;
    }
}
