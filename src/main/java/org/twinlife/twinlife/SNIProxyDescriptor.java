/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.util.Logger;

public class SNIProxyDescriptor extends ProxyDescriptor {
    private static final String LOG_TAG = "SNIProxyDescriptor";
    private final boolean mIsUser;
    @Nullable
    private final String mCustomSNI;

    public SNIProxyDescriptor(@NonNull String address, int port, @Nullable String customSNI, boolean isUser) {

        super(address, port);
        mIsUser = isUser;
        mCustomSNI = customSNI;
    }

    @Nullable
    public static SNIProxyDescriptor create(@NonNull String proxy) {
        try {
            final String[] parts = proxy.split("[,/]");
            if (parts.length > 2) {
                return null;
            }
            final String customSNI = parts.length == 2 ? parts[1] : null;
            int sep = parts[0].indexOf(':');
            int port = 443;
            String address = parts[0];
            if (sep > 0) {
                port = Integer.parseInt(parts[0].substring(sep + 1));
                if (port <= 0 || port >= 65536) {
                    return null;
                }
                address = parts[0].substring(0, sep);
            }
            if (!address.isEmpty()) {
                return new SNIProxyDescriptor(address, port, customSNI, true);
            }
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "User proxy: ",proxy, " has no address");
            }
            return null;

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Invalid user proxy: ",proxy, ": ", exception);
            }
            return null;
        }
    }

    @NonNull
    public String getDescriptor() {

        final String proxy = super.getDescriptor();
        return (mCustomSNI != null) ? proxy + "," + mCustomSNI : proxy;
    }

    @Nullable
    public String getCustomSNI() {

        return mCustomSNI;
    }

    public boolean isUserProxy() {

        return mIsUser;
    }
}
