/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

public enum AndroidAssertPoint implements AssertPoint {
    KEYCHAIN,
    KEYCHAIN_USE_DEFAULT,
    KEYCHAIN_DECRYPT,
    KEYCHAIN_ENCRYPT;

    public int getIdentifier() {

        return this.ordinal() + BASE_VALUE;
    }

    private static final int BASE_VALUE = 800;
}