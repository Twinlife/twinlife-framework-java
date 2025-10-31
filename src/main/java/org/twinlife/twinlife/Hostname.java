/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife;

import androidx.annotation.NonNull;

/**
 * Simple hostname definition with associated IPv4 and IPv6.
 *
 * Note: we use only String for addresses due to the interaction with WebRTC stack.
 */
public class Hostname {
    @NonNull
    public final String hostname;
    @NonNull
    public final String ipv4;
    @NonNull
    public final String ipv6;

    public Hostname(@NonNull String hostname, @NonNull String ipv4, @NonNull String ipv6) {
        this.hostname = hostname;
        this.ipv4 = ipv4;
        this.ipv6 = ipv6;
    }
}
