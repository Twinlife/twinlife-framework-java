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

/**
 * Repository object interface that should be implemented by SpaceSettings, Space, Profile, Contact, Group, ...
 *
 * When the object is loaded from the database or imported from the server, the repository service will
 * automatically load the associated twincodeInbound, twincodeOutbound, peerTwincodeOutbound and owner object instance.
 * It will then call `isValid()` to verify that the object can still be used.  When an object becomes invalid,
 * it is passed through the `onInvalidObject()` observer.
 */
public interface RepositoryObject extends DatabaseObject {

    @NonNull
    String getName();

    @NonNull
    String getDescription();

    @NonNull
    List<BaseService.AttributeNameValue> getAttributes(boolean exportAll);

    /**
     * Check whether the object instance is valid.  After creating an instance with the RepositoryObjectFactory,
     * the RepositoryService will verify the validity of the object.  If it becomes invalid, it will notify
     * through the observer that the repository object is invalid and the twinme framework must destroy the
     * object with appropriate methods.
     *
     * @return true if the object is valid.
     */
    boolean isValid();

    /**
     * Check whether it is possible to make a P2P connection to this contact by using the peer twincode.
     *
     * @return true if a P2P connection can be established.
     */
    boolean canCreateP2P();

    long getModificationDate();

    @Nullable
    default TwincodeInbound getTwincodeInbound() { return null; }
    default void setTwincodeInbound(@Nullable TwincodeInbound twincodeInbound) {}

    @Nullable
    default TwincodeOutbound getTwincodeOutbound() { return null; }
    default void setTwincodeOutbound(@Nullable TwincodeOutbound twincodeOutbound) {}

    @Nullable
    default TwincodeOutbound getPeerTwincodeOutbound() { return null; }
    default void setPeerTwincodeOutbound(@Nullable TwincodeOutbound twincodeOutbound) {}

    @Nullable
    default RepositoryObject getOwner() { return null; }
    default void setOwner(@Nullable RepositoryObject owner) {}
}
