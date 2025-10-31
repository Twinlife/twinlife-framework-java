/*
 *  Copyright (c) 2021-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import org.libwebsockets.ErrorCategory;

public interface ConnectionListener {
    void onConnect();

    /**
     * Called when the server connection was closed.
     */
    void onDisconnect(@NonNull ErrorCategory errorCategory);
}