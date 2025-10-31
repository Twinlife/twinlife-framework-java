/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * Twincode inbound which receives incoming requests.
 */
public interface TwincodeInbound extends Twincode, DatabaseObject {

    @Nullable
    String getCapabilities();

    @NonNull
    TwincodeOutbound getTwincodeOutbound();

    @Nullable
    UUID getTwincodeFactoryId();
}
