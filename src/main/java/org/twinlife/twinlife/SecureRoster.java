/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Represents a secure roster with its definition and list of signed roster groups:
 * - the rosterId indicates the unique ID that identifies the secure roster
 *   (the schemaId in the RosterId defines for what purpose the roster is used)
 * - the maxMemberCount indicates the maximum number of members that can be added to the roster.
 *   (if we try to add more members, the server will refuse adding them)
 * - the list of signed roster groups: for each signing key we get the list of
 *   members that are signed by that key.  This also indicates the list of Ed25519 keys
 *   that are allowed to signed members.
 */
public class SecureRoster {

    @NonNull
    private final RosterId mRosterId;
    private final int mMaxMemberCount;
    @NonNull
    private final List<SignedRosterGroup> mGroups;

    public SecureRoster(@NonNull RosterId rosterId, int maxMemberCount, @NonNull List<SignedRosterGroup> groups) {

        this.mRosterId = rosterId;
        this.mMaxMemberCount = maxMemberCount;
        this.mGroups = groups;
    }

    public int getMaxMemberCount() {

        return mMaxMemberCount;
    }

    @NonNull
    public RosterId getRosterId() {

        return mRosterId;
    }

    public boolean isEmpty() {

        return mGroups.isEmpty();
    }

    @NonNull
    public List<SignedRosterGroup> getGroups() {

        return mGroups;
    }
}
