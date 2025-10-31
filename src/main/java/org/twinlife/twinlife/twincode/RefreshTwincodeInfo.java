/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;

import java.util.List;
import java.util.UUID;

public class RefreshTwincodeInfo {

    public final UUID twincodeOutboundId;
    public final List<AttributeNameValue> attributes;
    public final byte[] signature;

    public RefreshTwincodeInfo(@NonNull UUID twincodeOutboundId, @NonNull List<AttributeNameValue> attributes,
                               @Nullable byte[] signature) {
        this.twincodeOutboundId = twincodeOutboundId;
        this.attributes = attributes;
        this.signature = signature;
    }
}
