/*
 *  Copyright (c) 2014-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
public interface Twincode {

    UUID NOT_DEFINED = new UUID(0L, 0L);

    String NAME = "name";
    String DESCRIPTION = "description";
    String AVATAR_ID = "avatarId";
    String CAPABILITIES = "capabilities";
    String ROSTER_ID = "rosterId";

    enum TwincodeFacet {
        FACTORY, INBOUND, OUTBOUND, SWITCH
    }

    TwincodeFacet getFacet();

    boolean isTwincodeFactory();

    boolean isTwincodeInbound();

    boolean isTwincodeOutbound();

    boolean isTwincodeSwitch();

    @NonNull
    UUID getId();

    @NonNull
    List<AttributeNameValue> getAttributes();

    @Nullable
    Object getAttribute(@NonNull String name);
}
