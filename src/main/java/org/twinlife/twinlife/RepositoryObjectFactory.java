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

import java.util.List;
import java.util.UUID;

import org.twinlife.twinlife.BaseService.AttributeNameValue;

/**
 * Factory interface used by the repository service to create high level objects (Profile, Contact, Group, Space, ...)
 *
 * - `createObject` is called when the repository service must create an instance after loading the object from
 *   the database for the first time.  Once created, the object is put in a cache when `isValid()` has returned true.
 * - `loadObject` is called to update an object with a new content.
 * - `importObject` is called in three situations:
 *   - when the old repository database is migrated to the new implementation,
 *   - when we ask the server to create an object,
 *   - when we retrieve an object from the server.
 */
public interface RepositoryObjectFactory <T extends RepositoryObject> {

    int USE_INBOUND = 0x01;
    int USE_OUTBOUND = 0x02;
    int USE_PEER_OUTBOUND = 0x04;
    int USE_ALL = (USE_INBOUND | USE_OUTBOUND | USE_PEER_OUTBOUND);

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
     * A bitmap of flags which indicates whether inbound, outbound and peer outbound twincodes are used.
     *
     * @return the bitmap of twincode usage.
     */
    int getTwincodeUsage();

    /**
     * Objects created and managed by the factory are local only and must not be sent to the server.
     *
     * @return true if the object is local.
     */
    boolean isLocal();

    /**
     * Objects are immutable or not.
     *
     * @return true if the object is immutable.
     */
    boolean isImmutable();

    /**
     * Get the factory object that manages the optional owner object.  In many cases, this will
     * be the space object factory and this will be null if there is no owner.
     *
     * @return the owner factory instance.
     */
    @Nullable
    RepositoryObjectFactory<?> getOwnerFactory();

    /**
     * Create an object instance (Profile, Contact, Group, Space, ...) and initialize it with the
     * values passed as parameter.
     *
     * @param identifier the database identifier associated with the object.
     * @param uuid the object UUID.
     * @param creationDate the object creation date.
     * @param name the object name.
     * @param description the optional object description.
     * @param attributes the optional list of attributes.
     * @param modificationDate the object modification date.
     * @return the object instance.
     */
    @NonNull
    T createObject(@NonNull DatabaseIdentifier identifier,
                   @NonNull UUID uuid, long creationDate, @Nullable String name,
                   @Nullable String description, @Nullable List<AttributeNameValue> attributes,
                   long modificationDate);

    /**
     * Update the object attributes with the parameters.
     *
     * @param object the object to update.
     * @param name the new name to use.
     * @param description the new description to use.
     * @param attributes the list of attributes.
     * @param modificationDate the new modification date.
     */
    void loadObject(@NonNull T object, @Nullable String name, @Nullable String description,
                    @Nullable List<AttributeNameValue> attributes, long modificationDate);

    /**
     * Create an object instance (Profile, Contact, Group, Space, ...) to import either from
     * the old repository implementation or from the server.
     *
     * @param upgradeService the database upgrade service to perform repository object upgrade.
     * @param identifier the database identifier that will be assigned to the object.
     * @param uuid the object uuid.
     * @param key the key associated with the object (the twincode inbound).
     * @param creationDate the object creation date.
     * @param attributes the object attributes.
     * @return the object instance initialized with the above parameters.
     */
    @Nullable
    T importObject(@NonNull RepositoryImportService upgradeService,
                   @NonNull DatabaseIdentifier identifier, @NonNull UUID uuid, @Nullable UUID key,
                   long creationDate, @NonNull List<AttributeNameValue> attributes);
}
