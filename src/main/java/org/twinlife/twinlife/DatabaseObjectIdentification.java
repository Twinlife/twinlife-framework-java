/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * Interface that describes where a database object is stored as well as its schema.
 */
public interface DatabaseObjectIdentification {

    /**
     * Give information about the database table that contains the object.
     *
     * @return the database table.
     */
    @NonNull
    DatabaseTable getKind();

    /**
     * The schema ID identifies the object factory in the database.
     *
     * @return the schema ID.
     */
    @NonNull
    UUID getSchemaId();

    /**
     * The schema version identifies a specific version of the object representation.
     *
     * @return the schema version.
     */
    int getSchemaVersion();

    /**
     * Indicates whether the object is local only or also stored on the server.
     *
     * @return true if the object is local only.
     */
    boolean isLocal();
}
