/*
 *  Copyright (c) 2023-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * A database identifier holds an internal ID and an identification of the object.
 * It can be used for a repository object, a twincode, a conversation, a descriptor,
 * a notification.
 */
public class DatabaseIdentifier implements Comparable<DatabaseIdentifier> {

    private final long mId;
    private final DatabaseObjectIdentification mFactory;

    public DatabaseIdentifier(@NonNull DatabaseObjectIdentification factory, long id) {

        mFactory = factory;
        mId = id;
    }

    public long getId() {

        return mId;
    }

    public UUID getSchemaId() {

        return mFactory.getSchemaId();
    }

    public int getSchemaVersion() {

        return mFactory.getSchemaVersion();
    }

    public boolean isLocal() {

        return mFactory.isLocal();
    }

    @NonNull
    public DatabaseTable getKind() {

        return mFactory.getKind();
    }

    public boolean isFromFactory(@NonNull DatabaseObjectIdentification factory) {

        // Same instance comparison is enough to prove that this is the same factory.
        return mFactory == factory;
    }

    @Override
    public boolean equals(@Nullable Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof DatabaseIdentifier)) {

            return false;
        }

        final DatabaseIdentifier id = (DatabaseIdentifier) object;
        return mId == id.mId && mFactory == id.mFactory;
    }

    @Override
    public int hashCode() {

        int result = 17;
        result = 31 * result + mFactory.hashCode();
        result = 31 * result + Long.hashCode(mId);

        return result;
    }

    @Override
    public int compareTo(@NonNull DatabaseIdentifier second) {

        if (mId == second.mId) {
            return 0;
        } else {
            return (int) (mId - second.mId);
        }
    }

    @Override
    @NonNull
    public String toString() {

        return mId + "." + mFactory.getKind();
    }
}
