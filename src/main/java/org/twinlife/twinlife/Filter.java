/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Basic filter definition for the RepositoryService, ConversationService and NotificationService.
 * The filter is used to build the SQL query to find the objects.  Then, for each object found
 * it calls `accept()` to check if the object matches other more complex rules.
 * <p>
 * - filter objects with a given owner (space),
 * - filter objects before a given date,
 * - filter objects matching a name (limited SQL LIKE),
 * - filter objects using a given twincode.
 * <p>
 * Example of filter to get the spaces contacts which are not a twinroom and are not revoked.
 * <pre>
 * final Filter<RepositoryObject> filter = new Filter<RepositoryObject>(mSpace) {
 *                     public boolean accept(@NonNull RepositoryObject object) {
 *                         if (!(object instanceof Contact)) {
 *                             return false;
 *                         }
 *
 *                         final Contact contact = (Contact) object;
 *                         return !contact.isTwinroom() && contact.hasPeer();
 *                     }
 *                 };
 * </pre>
 */
public class Filter <T> {
    @Nullable
    public RepositoryObject owner;
    @Nullable
    public TwincodeOutbound twincodeOutbound;
    @Nullable
    public String name;
    @Nullable
    public Long before;

    public Filter(@Nullable RepositoryObject owner) {
        this.owner = owner;
    }

    @NonNull
    public final Filter<T> withTwincode(@NonNull TwincodeOutbound twincodeOutbound) {

        this.twincodeOutbound = twincodeOutbound;
        return this;
    }

    @NonNull
    public final Filter<T> withName(@NonNull String name) {

        this.name = name;
        return this;
    }

    @NonNull
    public final Filter<T> before(@NonNull Long time) {

        this.before = time;
        return this;
    }

    public boolean accept(@NonNull T object) {

        return true;
    }

    @Override
    @NonNull
    public String toString() {
        StringBuilder sb = new StringBuilder("Filter");
        if (owner != null) {
            sb.append("owner=").append(owner);
        }
        if (before != null) {
            sb.append(" before=").append(before);
        }
        if (name != null) {
            sb.append(" name=").append(name);
        }
        if (twincodeOutbound != null) {
            sb.append(" twincode=").append(twincodeOutbound);
        }
        return sb.toString();
    }
}
