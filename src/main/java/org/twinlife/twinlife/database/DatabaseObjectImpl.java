/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;

/**
 * Helper class for the DatabaseObject interface implementation.
 */
public abstract class DatabaseObjectImpl implements DatabaseObject {

    @NonNull
    private final DatabaseIdentifier mId;

    public DatabaseObjectImpl(@NonNull DatabaseIdentifier id) {

        mId = id;
    }

    @Override
    @NonNull
    public DatabaseIdentifier getDatabaseId() {

        return mId;
    }


    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!(object instanceof DatabaseObjectImpl)) {
            return false;
        }

        DatabaseObjectImpl dbObject = (DatabaseObjectImpl) object;
        return mId.equals(dbObject.getDatabaseId());
    }

    @Override
    public int hashCode() {

        return mId.hashCode();
    }
}