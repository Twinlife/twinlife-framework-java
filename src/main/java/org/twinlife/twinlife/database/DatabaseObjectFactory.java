/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;
import org.twinlife.twinlife.DatabaseObjectIdentification;

/**
 * Database object factory:
 * - createObject() is called when an instance of `T` (Space, Settings, Profile, TwincodeInbound, TwincodeOutbound, ...)
 *   must be created when loading some new object from the database.
 * - loadObject() is called to refresh an instance after an SQL query.
 */
public interface DatabaseObjectFactory<T extends DatabaseObject> extends DatabaseObjectIdentification {

    @Nullable
    T createObject(@NonNull DatabaseIdentifier identifier,
                   @NonNull DatabaseCursor cursor, int offset) throws DatabaseException;

    boolean loadObject(@NonNull T object, @NonNull DatabaseCursor cursor, int offset) throws DatabaseException;
}