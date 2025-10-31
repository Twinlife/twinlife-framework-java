/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

public enum TwinlifeAssertPoint implements AssertPoint {
    SERVICE,
    SERIALIZER_EXCEPTION,
    UNEXPECTED_EXCEPTION,
    ENVIRONMENT_ID;

    public int getIdentifier() {

        return this.ordinal() + BASE_VALUE;
    }

    private static final int BASE_VALUE = 10;
}