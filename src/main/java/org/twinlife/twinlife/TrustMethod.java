/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

/**
 * Describes how the public key was trusted.
 */
public enum TrustMethod {
    // No public key or not trusted.
    NONE,

    // We are owner of the public key.
    OWNER,

    // Public key was received by scanning a QR-code
    QR_CODE,

    // Public key was received from an external link and the application was launched to handle it.
    LINK,

    // Public key was validated with the authenticate video protocol.
    VIDEO,

    // Public key received through P2P data channel from the P2P exchange public key protocol.
    AUTO,

    // Public key was received through P2P data channel
    PEER,

    // Public key was received from the server while adding the contact using a temporary invitation code.
    INVITATION_CODE
}
