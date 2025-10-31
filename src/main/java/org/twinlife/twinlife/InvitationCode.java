/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * Invitation code (temporary code pointing to an invitation's twincode).
 */
public interface InvitationCode {

    @NonNull
    String getCode();

    @NonNull
    UUID getTwincodeOutboundId();

    long getCreationDate();

    int getValidityPeriod();

    String getPublicKey();
}
