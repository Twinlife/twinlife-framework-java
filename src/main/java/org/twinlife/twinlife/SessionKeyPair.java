/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Pair;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * Holds the keys to encrypt and decrypt SPDs for a given session.
 */
public interface SessionKeyPair {

    long MAX_EXCHANGE = 100;

    /**
     * The P2P session id for which the session key-pair is valid.
     *
     * @return the P2P session id.
     */
    @NonNull
    UUID getSessionId();

    /**
     * Get the number of nonce allocation which are allowed for encryption.
     * (ie, number of times we can call encrypt()).
     *
     * @return the nonce remaining sequence count.
     */
    long getSequenceCount();

    /**
     * Get the next nonce sequence to encrypt a content.
     *
     * @return the next nonce sequence or 0 if there is no more available sequence.
     */
    long allocateNonce();

    /**
     * Check whether the secret keys must be re-newed.
     *
     * @return true if the secret keys should be re-newed.
     */
    boolean needRenew();

    @NonNull
    Pair<BaseService.ErrorCode, Sdp> encrypt(@NonNull Sdp sdp);

    @NonNull
    Pair<BaseService.ErrorCode, Sdp> decrypt(@NonNull Sdp sdp);

    void dispose();
}
