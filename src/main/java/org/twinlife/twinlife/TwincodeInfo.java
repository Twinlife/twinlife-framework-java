/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.UUID;

public class TwincodeInfo {
    @NonNull
    public final UUID twincodeFactoryId;
    @NonNull
    public final UUID twincodeOutboundId;
    @NonNull
    public final UUID twincodeInboundId;

    public TwincodeInfo(@NonNull UUID twincodeFactoryId, @NonNull UUID twincodeOutboundId, @NonNull UUID twincodeInboundId) {
        this.twincodeOutboundId = twincodeOutboundId;
        this.twincodeFactoryId = twincodeFactoryId;
        this.twincodeInboundId = twincodeInboundId;
    }

    @NonNull
    @Override
    public String toString() {
        return "{" +
                "twincodeFactoryId=" + twincodeFactoryId +
                ", twincodeOutboundId=" + twincodeOutboundId +
                ", twincodeInboundId=" + twincodeInboundId +
                '}';
    }
}
