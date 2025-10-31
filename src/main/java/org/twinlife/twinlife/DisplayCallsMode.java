/*
 *  Copyright (c) 2014-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Fabrice Trescartes (Fabrice.Trescartes@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */
package org.twinlife.twinlife;

import androidx.annotation.NonNull;

public enum DisplayCallsMode {
    NONE,
    MISSED,
    ALL;

    public static int toInteger(@NonNull DisplayCallsMode mode) {
        // Do not use ordinal(), Enum <-> integer mapping is frozen
        // and must not be changed if new values are added or values are re-ordered.
        switch (mode) {
            case NONE:
                return 0;
            case MISSED:
                return 1;
            case ALL:
                return 2;
        }
        return 2;
    }

    @NonNull
    public static DisplayCallsMode fromInteger(int value) {
        switch (value) {
            case 0:
                return NONE;
            case 1:
                return MISSED;
            case 2:
            default:
                return ALL;
        }
    }
}
