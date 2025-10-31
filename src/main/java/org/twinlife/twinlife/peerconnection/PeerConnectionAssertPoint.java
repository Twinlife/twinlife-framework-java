/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife.peerconnection;

import org.twinlife.twinlife.AssertPoint;

public enum PeerConnectionAssertPoint implements AssertPoint {
    CREATE_DATA_CHANNEL,
    CREATE_AUDIO_SOURCE,
    CREATE_AUDIO_TRACK,
    OFFER_FAILURE,
    SET_LOCAL_FAILURE,
    SET_REMOTE_FAILURE,
    ENCRYPT_ERROR,
    DECRYPT_ERROR_1,
    DECRYPT_ERROR_2,
    NOT_TERMINATED;

    public int getIdentifier() {

        return this.ordinal() + BASE_VALUE;
    }

    private static final int BASE_VALUE = 300;
}