/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;

import java.util.List;
import java.util.UUID;

/**
 * Twincode object factory specialises the database object factory with:
 * - storeObject() is called to create a new instance of `T` and prepare it *before* the insertion in
 *   the database (the caller will handle the SQL INSERT).  The transaction instance is used in some specific cases.
 */
public interface TwincodeObjectFactory<T extends DatabaseObject> extends DatabaseObjectFactory<T> {

    interface Initializer<T> {
        void initialize(@NonNull T object) throws DatabaseException;
    }

    T storeObject(@NonNull Transaction transaction, @NonNull DatabaseIdentifier identifier,
                  @NonNull UUID twincodeId, @Nullable List<BaseService.AttributeNameValue> attributes,
                  int flags,
                  long modificationDate, long refreshPeriod,
                  long refreshDate, long refreshTimestamp,
                  @Nullable Initializer<T> initializer) throws DatabaseException;
}