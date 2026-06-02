/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode;

import org.twinlife.twinlife.AssertPoint;

public enum TwincodesAssertPoint implements AssertPoint {
    RECOVER_FACTORY_ID,
    RECOVER_TWINCODE_IN,
    UNKNOWN_TWINCODE;

    public int getIdentifier() {

        return this.ordinal() + BASE_VALUE;
    }

    private static final int BASE_VALUE = 500;
}