/*
 *  Copyright (c) 2020-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife;

import androidx.annotation.NonNull;

public class TurnServer {
    @NonNull
    public final String url;
    @NonNull
    public final String username;
    @NonNull
    public final String password;

    public TurnServer(@NonNull String url, @NonNull String username, @NonNull String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }
}
