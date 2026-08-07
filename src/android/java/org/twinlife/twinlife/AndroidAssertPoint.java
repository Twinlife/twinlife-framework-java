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
    KEYCHAIN_ENCRYPT,
    KEYCHAIN_REMOVE,
    KEYCHAIN_BAD_JELLY_BEAN,
    KEYCHAIN_CREATE,
    KEYCHAIN_CREATE_GCM,
    KEYCHAIN_CREATE_CBC,
    KEYCHAIN_LOAD_GCM,
    KEYCHAIN_LOAD_CBC,
    KEYCHAIN_LOAD_CBC_RSA,
    KEYCHAIN_LOAD_KEY,
    KEYCHAIN_VERIFY,
    KEYCHAIN_VERIFY_FAILED,
    KEYCHAIN_GCM_BROKEN,
    KEYCHAIN_DECRYPT_LEGACY,
    KEYCHAIN_MIGRATION,
    KEYCHAIN_MIGRATION_LEGACY;

    public int getIdentifier() {

        return this.ordinal() + BASE_VALUE;
    }

    private static final int BASE_VALUE = 800;
}