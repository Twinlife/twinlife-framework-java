/*
 *  Copyright (c) 2023-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Twincode outbound which is used to send outgoing requests.
 */
public interface TwincodeOutbound extends Twincode, DatabaseObject {

    @Nullable
    String getName();

    @Nullable
    String getDescription();

    @Nullable
    ImageId getAvatarId();

    @Nullable
    String getCapabilities();

    boolean isKnown();

    /**
     * Whether the twincode attributes are signed by a public key.
     *
     * @return true if the twincode attributes are signed.
     */
    boolean isSigned();

    /**
     * Whether SDPs are encrypted when sending/receiving (secret keys are known).
     *
     * @return true if the SDP are encrypted.
     */
    boolean isEncrypted();

    /**
     * Whether the twincode public key is trusted.
     *
     * @return true if the twincode public key is trusted.
     */
    boolean isTrusted();

    /**
     * Whether the twincode public key is trusted and how.
     *
     * @return how we trusted the public key.
     */
    @NonNull
    TrustMethod getTrustMethod();

    /**
     * Whether the twincode attributes and the signature are verified and match.
     *
     * @return true if the twincode attributes correspond to the signature.
     */
    boolean isVerified();

    /**
     * Whether the twincode was certified as part of the contact certification process.
     *
     * @return true if the relation was certified.
     */
    boolean isCertified();
}
