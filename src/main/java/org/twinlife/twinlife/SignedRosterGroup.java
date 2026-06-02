/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.UUID;

/**
 * Group of members signed by the same public key.
 */
public class SignedRosterGroup {

    @NonNull
    public final UUID keyId;
    @NonNull
    public final CryptoService.PublicKeyData publicKey;
    @NonNull
    public final byte[] signature;
    @NonNull
    public final UUID signingKeyId;
    @NonNull
    public final List<RosterMember> members;

    public boolean verified;

    public SignedRosterGroup(@NonNull UUID keyId, @NonNull byte[] publicKey,
                             @NonNull byte[] signature, @NonNull UUID signingKeyId,
                             @NonNull List<RosterMember> members) {

        this.keyId = keyId;
        this.publicKey = CryptoService.PublicKeyData.create(publicKey);
        this.signature = signature;
        this.signingKeyId = signingKeyId;
        this.members = members;
    }
}
