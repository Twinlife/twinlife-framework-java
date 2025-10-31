/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.database.DatabaseObjectFactory;
import org.twinlife.twinlife.database.DatabaseServiceImpl;

import java.util.UUID;

/**
 * Factory for the creation of ConversationImpl objects.
 */
class ConversationFactoryImpl implements DatabaseObjectFactory<ConversationImpl> {
    private static final String LOG_TAG = "ConversationFactoryImpl";
    private static final boolean DEBUG = false;

    @NonNull
    private final DatabaseServiceImpl mDatabase;

    ConversationFactoryImpl(@NonNull DatabaseServiceImpl databaseService) {
        if (DEBUG) {
            Log.d(LOG_TAG, "ConversationFactoryImpl: databaseService=" + databaseService);
        }

        mDatabase = databaseService;
    }

    @Override
    @NonNull
    public DatabaseTable getKind() {

        return DatabaseTable.TABLE_CONVERSATION;
    }

    @Override
    @NonNull
    public UUID getSchemaId() {

        // This is informational: the value is not stored in the database (but used by DatabaseIdentifier).
        return ConversationImpl.SCHEMA_ID;
    }

    @Override
    public int getSchemaVersion() {

        // This is informational: the value is not stored in the database (but used by DatabaseIdentifier).
        return ConversationImpl.SCHEMA_VERSION;
    }

    @Override
    @Nullable
    public ConversationImpl createObject(@NonNull DatabaseIdentifier identifier,
                                         @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObject: identifier=" + identifier + " offset=" + offset);
        }

        UUID conversationId = cursor.getUUID(offset);
        long creationDate = cursor.getLong(offset + 1);
        long subjectId = cursor.getLong(offset + 2);
        UUID schemaId = cursor.getUUID(offset + 3);
        // We don't use it for the conversation since we have it on the subject
        // long peerTwincodeOutbound = cursor.getLong(offset + 4);
        UUID resourceId = cursor.getUUID(offset + 5);
        UUID peerResourceId = cursor.getUUID(offset + 6);
        long permissions = cursor.getLong(offset + 7);
        // long joinPermissions = cursor.getLong(offset + 8);
        long lastConnectDate = cursor.getLong(offset + 9);
        long lastRetryDate = cursor.getLong(offset + 10);
        int flags = cursor.getInt(offset + 11);
        long descriptorCount = cursor.getLong(offset + 12);
        final RepositoryObject subject = mDatabase.loadRepositoryObject(subjectId, schemaId);
        if (subject == null) {
            return null;
        }
        ConversationImpl result = new ConversationImpl(identifier, conversationId, subject, creationDate, resourceId,
                peerResourceId, permissions, lastConnectDate, lastRetryDate, flags);
        if (descriptorCount > 0) {
            result.setIsActive(true);
        }
        return result;
    }

    @Override
    public boolean loadObject(@NonNull ConversationImpl object, @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadObject: object=" + object + " offset=" + offset);
        }

        // Ignore fields which are read-only.
        UUID peerResourceId = cursor.getUUID(offset + 6);
        long permissions = cursor.getLong(offset + 7);
        // long joinPermissions = cursor.getLong(offset + 8);
        long lastConnectDate = cursor.getLong(offset + 9);
        long lastRetryDate = cursor.getLong(offset + 10);
        int flags = cursor.getInt(offset + 11);
        long descriptorCount = cursor.getLong(offset + 12);
        object.update(peerResourceId, permissions, lastConnectDate, lastRetryDate, flags);
        object.setIsActive(descriptorCount > 0);
        return true;
    }

    @Override
    public boolean isLocal() {

        return true;
    }
}
