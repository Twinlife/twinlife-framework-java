/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Fabrice Trescartes (Fabrice.Trescartes@twin.life)
 */
package org.twinlife.twinlife;

import androidx.annotation.NonNull;

public enum ShareInvitationMode implements ConfigIdentifier.Enum<ShareInvitationMode> {
    NEVER,
    ASK,
    AUTOMATIC;

    @Override
    public int toInteger() {
        // Do not use ordinal(), Enum <-> integer mapping is frozen
        // and must not be changed if new values are added or values are re-ordered.
        // Mapping must be compatible with iOS because this configuration is saved.
        switch (this) {
            case NEVER:
                return 0;
            case ASK:
                return 1;
            case AUTOMATIC:
                return 2;
        }
        return 0;
    }

    @Override
    @NonNull
    public ShareInvitationMode fromInteger(int value) {
        switch (value) {
            case 1:
                return ASK;
            case 2:
                return AUTOMATIC;
            case 0:
            default:
                return NEVER;
        }
    }
}
