/*
 *  Copyright (c) 2023-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * A database interface that encapsulates the operations used by the twinlife-framework library
 * and which can be implemented on top of Android SQLcipher library or a Java JDBC connector.
 */
public interface DatabaseObject {

    /**
     * Get the internal database identifier.  This is unique across all database objects.
     *
     * @return the internal database id.
     */
    @NonNull
    DatabaseIdentifier getDatabaseId();

    /**
     * Get the id associated with the object.  This UUID is used to identify
     * the object on the server.
     *
     * @return the object uuid.
     */
    @NonNull
    UUID getId();

    /**
     * Check if this object is created by the given factory.
     *
     * @param factory the factory that creates objects.
     * @return true if the object was created by the factory.
     */
    default boolean isFromFactory(@NonNull DatabaseObjectIdentification factory) {

        return getDatabaseId().isFromFactory(factory);
    }
}
