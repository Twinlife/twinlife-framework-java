/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * A roster member with its twincode, permission and public key.
 */
public class RosterMember {

    @NonNull
    public final UUID memberTwincodeId;
    public final long permissions;
    @NonNull
    public final CryptoService.PublicKeyData publicKey;
    @NonNull
    public final byte[] signature;
    public final long creationDate;
    public final long modificationDate;

    public boolean verified;

    public RosterMember(@NonNull UUID memberTwincodeId, long permissions,
                        @NonNull byte[] publicKey, @NonNull byte[] signature,
                        long creationDate, long modificationDate) {

        this.memberTwincodeId = memberTwincodeId;
        this.permissions = permissions;
        this.publicKey = CryptoService.PublicKeyData.create(publicKey);
        this.signature = signature;
        this.creationDate = creationDate;
        this.modificationDate = modificationDate;
    }

    /**
     * Check if the member has the given permission.
     *
     * @param permission the permission to check.
     * @return true if the member has the given permission.
     */
    public boolean hasPermission(@NonNull Permission permission) {

        return permission.hasPermission(permissions);
    }
}
