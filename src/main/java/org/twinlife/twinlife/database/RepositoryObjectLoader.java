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

import org.twinlife.twinlife.RepositoryObject;

import java.util.UUID;

/**
 * Interface that gives access to the database repository object loader.
 */
public interface RepositoryObjectLoader {
    /**
     * Load the repository object with the given database id and using the given schema Id.
     * The schemaId is used to find the good repository object factory if the repository object
     * is not found in the cache and must be loaded.
     *
     * @param dbId the repository object id
     * @param schemaId the repository object schema
     * @return the repository object or null if it was not found.
     */
    @Nullable
    RepositoryObject loadRepositoryObject(long dbId, @NonNull UUID schemaId);
}