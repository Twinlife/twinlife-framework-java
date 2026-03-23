/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup.handlers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.backup.BackupHandler;
import org.twinlife.twinlife.backup.VerifyResult;
import org.twinlife.twinlife.repository.RepositoryServiceImpl;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RepositoryObjectHandler extends BackupHandler<RepositoryObject> {
    private static final String LOG_TAG = "RepositoryObjectHandler";
    private static final boolean DEBUG = false;

    public static final UUID SCHEMA_ID = UUID.fromString("a3764e50-4370-4486-8f7e-01927b2c4c71");
    private static final int SCHEMA_VERSION = 1;
    @NonNull
    private final RepositoryServiceImpl mRepositoryService;
    @NonNull
    private final List<UUID> mSupportedSchemaIds;
    @Nullable
    private List<RepositoryObject> mLocalObjects = null;


    @NonNull
    private final Map<UUID, Integer> mStats = new HashMap<>();

    public RepositoryObjectHandler(@NonNull RepositoryServiceImpl repositoryService, @NonNull List<UUID> supportedSchemaIds) {
        super();
        mRepositoryService = repositoryService;
        mSupportedSchemaIds = supportedSchemaIds;
    }

    @Override
    protected void initDeserializers() {
        mRestorers.put(RepositoryObjectRestorerV1.VERSION, new RepositoryObjectRestorerV1());
    }

    @Override
    public void backup(@NonNull BinaryEncoder encoder) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "backup: encoder=" + encoder);
        }

        List<RepositoryObject> objects = mRepositoryService.getLocalObjects(mSupportedSchemaIds);

        for (RepositoryObject object : objects) {
            encoder.writeUUID(SCHEMA_ID);
            encoder.writeInt(SCHEMA_VERSION);
            encoder.writeUUID(object.getDatabaseId().getSchemaId());
            encoder.writeLong(object.getDatabaseId().getId());
            encoder.writeUUID(object.getId());
            encoder.writeLong(object.getModificationDate()); // TODO BKP: creation date
            encoder.writeLong(object.getModificationDate());
            //TODO BKP: flags?
            encoder.writeAttributes(object.getAttributes(true));

            addStat(object.getDatabaseId().getSchemaId());

            if (DEBUG) {
                Log.d(LOG_TAG, "Backup repository object: " + object);
            }
        }
    }

    @NonNull
    @Override
    public Map<UUID, Integer> getStats() {
        return mStats;
    }

    private void addStat(UUID id) {
        Integer counter = mStats.get(id);

        if (counter == null) {
            counter = 1;
        } else {
            counter += 1;
        }

        mStats.put(id, counter);
    }

    private class RepositoryObjectRestorerV1 implements Restorer<RepositoryObject> {
        public static final int VERSION = 1;

        @Nullable
        @Override
        public RepositoryObject restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException {
            UUID schemaId = decoder.readUUID();
            long dbId = decoder.readLong();
            UUID objectId = decoder.readUUID();
            long creationDate = decoder.readLong();
            long modificationDate = decoder.readLong();
            //TODO BKP: flags?
            List<BaseService.AttributeNameValue> attrs = decoder.readAttributes();

            if (attrs == null) {
                attrs = Collections.emptyList();
            }

            if (!mSupportedSchemaIds.contains(schemaId)) {
                Log.w(LOG_TAG, "Restore not supported for objects with schema ID: " + schemaId);
                return null;
            }
            RepositoryObject repositoryObject;
            if (inPlace) {
                repositoryObject = mRepositoryService.restoreExistingObject(schemaId, dbId, objectId, creationDate, modificationDate, attrs);
            } else {

                repositoryObject = mRepositoryService.restoreObject(schemaId, dbId, objectId, creationDate, attrs);
            }

            if (repositoryObject == null) {
                if (!inPlace) {
                    throw new SerializerException("could not restore object " + objectId);
                }
                // In place restore: the object has been deleted, which is not an issue
                return null;
            }

            addStat(repositoryObject.getDatabaseId().getSchemaId());

            if (DEBUG) {
                Log.d(LOG_TAG, "Restored repository object: " + repositoryObject);
            }

            return repositoryObject;
        }

        @NonNull
        @Override
        public VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException {
            if (DEBUG) {
                Log.d(LOG_TAG, "verify: decoder=" + decoder);
            }

            UUID schemaId = decoder.readUUID();
            decoder.readLong(); // dbId
            UUID objectId = decoder.readUUID();
            decoder.readLong(); // creationDate
            decoder.readLong(); // modificationDate

            //TODO BKP: flags?
            List<BaseService.AttributeNameValue> backupAttributes = decoder.readAttributes();
            if (backupAttributes == null) {
                backupAttributes = Collections.emptyList();
            }

            if (!mSupportedSchemaIds.contains(schemaId)) {
                Log.w(LOG_TAG, "Restore not supported for objects with schema ID: " + schemaId);
                return new VerifyResult.Absent<>(objectId, schemaId, RepositoryObject.class);
            }

            if (mLocalObjects == null) {
                mLocalObjects = mRepositoryService.getLocalObjects(mSupportedSchemaIds);
            }

            RepositoryObject repositoryObject = null;

            for (RepositoryObject localObject : mLocalObjects) {
                if (localObject.getId().equals(objectId)) {
                    repositoryObject = localObject;
                    break;
                }
            }

            if (repositoryObject == null) {
                return new VerifyResult.Absent<>(objectId, schemaId, RepositoryObject.class);
            }

            List<BaseService.AttributeNameValue> dbAttributes = repositoryObject.getAttributes(true);

            boolean modified = dbAttributes.size() != backupAttributes.size() || new HashSet<>(dbAttributes).retainAll(backupAttributes);

            return new VerifyResult.Present<>(repositoryObject, modified);
        }
    }
}
