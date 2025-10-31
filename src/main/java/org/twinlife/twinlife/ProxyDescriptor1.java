/*
 *  Copyright (c) 2018-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ProxyDescriptor1 extends ProxyDescriptor {

    private final String mUsername;
    private final String mPassword;

    public ProxyDescriptor1(@NonNull String address, int port, @Nullable String username, @Nullable String password) {

        super(address, port);

        mUsername = username;
        mPassword = password;
    }

    @Nullable
    public String getUsername() {

        return mUsername;
    }

    @Nullable
    public String getPassword() {

        return mPassword;
    }
}
