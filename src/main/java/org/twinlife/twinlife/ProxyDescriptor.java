/*
 *  Copyright (c) 2021-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.libwebsockets.ErrorCategory;

public class ProxyDescriptor {

    private final String mAddress;
    private final int mPort;
    private ErrorCategory mLastError;

    public ProxyDescriptor(@NonNull String address, int port) {

        mAddress = address;
        mPort = port;
    }

    @NonNull
    public String getAddress() {

        return mAddress;
    }

    public int getPort() {

        return mPort;
    }

    @NonNull
    public String getDescriptor() {

        if (mPort == 443) {
            return mAddress;
        } else {
            return mAddress + ":" + mPort;
        }
    }

    public boolean isUserProxy() {

        return false;
    }

    @Nullable
    public ErrorCategory getLastError() {

        return mLastError;
    }

    public void setLastError(@Nullable ErrorCategory error) {

        mLastError = error;
    }

    /**
     * Check if the two proxies are almost the same.  This is not a isEqual() we only want to
     * compare the address and port.
     * @param second the second proxy to compare.
     * @return true if they are almost the same.
     */
    public boolean isSame(@NonNull ProxyDescriptor second) {

        return mAddress.equals(second.mAddress) && mPort == second.mPort;
    }
}
