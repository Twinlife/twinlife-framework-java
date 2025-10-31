/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.database.DatabaseObjectFactory;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.util.BinaryCompactDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Factory for the creation of high level repository objects such as Space, Profile, Contact, Group, ...
 *
 * @param <T>
 */
class RepositoryObjectFactoryImpl<T extends RepositoryObject> implements DatabaseObjectFactory<T>, RepositoryImportService {
    private static final String LOG_TAG = "RepositoryObjectFact";
    private static final boolean DEBUG = false;

    @NonNull
    private final DatabaseServiceImpl mDatabase;
    @NonNull
    private final RepositoryServiceProvider mServiceProvider;
    @NonNull
    private final RepositoryObjectFactory<T> mFactory;
    private Transaction mCurrentTransaction;

    RepositoryObjectFactoryImpl(@NonNull DatabaseServiceImpl databaseService,
                                @NonNull RepositoryServiceProvider serviceProvider,
                                @NonNull RepositoryObjectFactory<T> factory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "RepositoryObjectFactoryImpl: databaseService=" + databaseService);
        }

        mDatabase = databaseService;
        mServiceProvider = serviceProvider;
        mFactory = factory;
    }

    @Override
    @NonNull
    public DatabaseTable getKind() {

        return DatabaseTable.TABLE_REPOSITORY_OBJECT;
    }

    @Override
    @NonNull
    public UUID getSchemaId() {

        return mFactory.getSchemaId();
    }

    @Override
    public int getSchemaVersion() {

        return mFactory.getSchemaVersion();
    }

    public int getTwincodeUsage() {

        return mFactory.getTwincodeUsage();
    }

    @Override
    @NonNull
    public T createObject(@NonNull DatabaseIdentifier identifier,
                          @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObject: identifier=" + identifier + " offset=" + offset);
        }

        // r.uuid, r.creationDate, r.name, r.description, r.attributes, r.modificationDate, r.owner
        UUID uuid = cursor.getUUID(offset);
        long creationDate = cursor.getLong(offset + 1);
        String name = cursor.getString(offset + 2);
        String description = cursor.getString(offset + 3);
        byte[] content = cursor.getBlob(offset + 4);
        long modificationDate = cursor.getLong(offset + 5);
        long ownerId = cursor.getLong(offset + 6);
        List<AttributeNameValue> attributes = BinaryCompactDecoder.deserialize(content);
        T result = mFactory.createObject(identifier, uuid, creationDate, name, description, attributes, modificationDate);
        if (ownerId > 0) {
            final RepositoryObjectFactoryImpl<RepositoryObject> dbOwnerFactory = getOwnerFactory();
            if (dbOwnerFactory != null) {
                DatabaseIdentifier ownerIdentifier = new DatabaseIdentifier(dbOwnerFactory, ownerId);
                DatabaseObject ownerObject = mDatabase.getCache(ownerIdentifier);
                if (ownerObject == null) {
                    ownerObject = mServiceProvider.loadObject(ownerId, null, dbOwnerFactory);
                }

                if (ownerObject instanceof RepositoryObject) {
                    result.setOwner((RepositoryObject) ownerObject);
                }
            }
        }
        return result;
    }

    @Override
    public boolean loadObject(@NonNull T object, @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadObject: object=" + object + " offset=" + offset);
        }

        // r.uuid, r.creationDate, r.name, r.description, r.attributes, r.modificationDate, r.owner
        long modificationDate = cursor.getLong(offset + 5);
        if (object.getModificationDate() == modificationDate) {
            return false;
        }
        String name = cursor.getString(offset + 2);
        String description = cursor.getString(offset + 3);
        byte[] content = cursor.getBlob(offset + 4);
        long ownerId = cursor.getLong(offset + 6);
        List<AttributeNameValue> attributes = BinaryCompactDecoder.deserialize(content);
        mFactory.loadObject(object, name, description, attributes, modificationDate);
        if (ownerId > 0 && object.getOwner() == null) {
            final RepositoryObjectFactoryImpl<RepositoryObject> dbOwnerFactory = getOwnerFactory();
            if (dbOwnerFactory != null) {
                DatabaseIdentifier ownerIdentifier = new DatabaseIdentifier(dbOwnerFactory, ownerId);
                DatabaseObject ownerObject = mDatabase.getCache(ownerIdentifier);
                if (ownerObject == null) {
                    ownerObject = mServiceProvider.loadObject(ownerId, null, dbOwnerFactory);
                }

                if (ownerObject instanceof RepositoryObject) {
                    object.setOwner((RepositoryObject) ownerObject);
                }
            }
        }
        return true;
    }

    @Override
    public boolean isLocal() {

        return mFactory.isLocal();
    }

    @NonNull
    @Override
    public String toString() {

        return mFactory.toString();
    }

    //
    // Implementation for RepositoryImportService
    //

    @Override
    public void importObject(@NonNull RepositoryObject object, @Nullable UUID twincodeFactoryId, @Nullable UUID twincodeInboundId,
                             @Nullable UUID twincodeOutboundId, @Nullable UUID peerTwincodeOutboundId,
                             @Nullable UUID ownerId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "importObject: object=" + object + " twincodeFactoryId="
                    + " twincodeInboundId=" + twincodeInboundId + " twincodeOutboundId=" + twincodeOutboundId
                    + " peerTwincodeOutboundId=" + peerTwincodeOutboundId + " ownerId=" + ownerId);
        }

        try {
            if (twincodeOutboundId != null) {
                TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(twincodeOutboundId);
                object.setTwincodeOutbound(twincodeOutbound);

                if (twincodeInboundId != null) {
                    TwincodeInbound twincodeInbound = mDatabase.loadTwincodeInbound(twincodeInboundId);

                    // The inbound twincode is not in the database but we are doing a database migration.
                    // Look for the twincode in the old table and migrate that twincode to the new table.
                    if (twincodeInbound == null && mServiceProvider.isMigrationRunning()) {
                        twincodeInbound = mServiceProvider.loadLegacyTwincodeInbound(mCurrentTransaction, twincodeInboundId, twincodeOutbound, twincodeFactoryId);
                    }
                    if (twincodeInbound == null) {
                        List<AttributeNameValue> attributes = new ArrayList<>();
                        twincodeInbound = mCurrentTransaction.storeTwincodeInbound(twincodeInboundId, twincodeOutbound,
                                twincodeFactoryId, attributes, 0, System.currentTimeMillis());
                    }
                    object.setTwincodeInbound(twincodeInbound);
                }
            }
            if (peerTwincodeOutboundId != null) {
                TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(peerTwincodeOutboundId);
                object.setPeerTwincodeOutbound(twincodeOutbound);
            }
            if (ownerId != null) {
                DatabaseObject ownerObject = mDatabase.getCache(ownerId);
                if (ownerObject == null) {
                    final RepositoryObjectFactoryImpl<RepositoryObject> dbOwnerFactory = getOwnerFactory();
                    if (dbOwnerFactory != null) {
                        ownerObject = mServiceProvider.loadObject(0, ownerId, dbOwnerFactory);
                    }
                }
                if (ownerObject instanceof RepositoryObject) {
                    object.setOwner((RepositoryObject) ownerObject);
                }
            }
        } catch (DatabaseException exception) {
            Log.e(LOG_TAG, "Exception in importObject", exception);
        }
    }

    //
    // Internal methods
    //

    @Nullable
    synchronized RepositoryObject importObject(@NonNull Transaction transaction, @NonNull DatabaseIdentifier identifier,
                                               @NonNull UUID uuid, @Nullable UUID key,
                                               long creationDate, @NonNull List<BaseService.AttributeNameValue> attributes) {

        try {
            mCurrentTransaction = transaction;
            return mFactory.importObject(this, identifier, uuid, key, creationDate, attributes);

        } finally {
            mCurrentTransaction = null;
        }
    }

    @NonNull
    public RepositoryObject saveObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                                       @Nullable List<AttributeNameValue> attributes,
                                       long modificationDate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "storeObject: identifier=" + identifier + " twincodeId=" + uuid);
        }

        return mFactory.createObject(identifier, uuid, modificationDate, null, null, attributes, modificationDate);
    }

    @Nullable
    private RepositoryObjectFactoryImpl<RepositoryObject> getOwnerFactory() {

        final RepositoryObjectFactory<?> ownerFactory = mFactory.getOwnerFactory();
        if (ownerFactory == null) {
            return null;
        }

        return mServiceProvider.getFactory(ownerFactory.getSchemaId());
    }
}
