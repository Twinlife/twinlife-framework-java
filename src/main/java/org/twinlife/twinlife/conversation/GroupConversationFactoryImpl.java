/*
 *  Copyright (c) 2023-2024 twinlife SA.
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
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.database.DatabaseObjectFactory;
import org.twinlife.twinlife.database.DatabaseServiceImpl;

import java.util.Map;
import java.util.UUID;

/**
 * Factory for the creation of GroupConversationImpl and GroupMemberConversationImpl objects.
 */
class GroupConversationFactoryImpl implements DatabaseObjectFactory<GroupConversationImpl> {
    private static final String LOG_TAG = "GroupConversationFact..";
    private static final boolean DEBUG = false;

    @NonNull
    private final DatabaseServiceImpl mDatabase;

    GroupConversationFactoryImpl(@NonNull DatabaseServiceImpl databaseService) {
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
        return GroupConversationImpl.SCHEMA_ID;
    }

    @Override
    public int getSchemaVersion() {

        // This is informational: the value is not stored in the database (but used by DatabaseIdentifier).
        return GroupConversationImpl.SCHEMA_VERSION;
    }

    @Override
    @Nullable
    public GroupConversationImpl createObject(@NonNull DatabaseIdentifier identifier,
                                              @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObject: identifier=" + identifier + " offset=" + offset);
        }

        UUID conversationId = cursor.getUUID(offset);
        long creationDate = cursor.getLong(offset + 1);
        long groupId = cursor.getLong(offset + 2);
        UUID schemaId = cursor.getUUID(offset + 3);
        // We don't use it for the group conversation since we use the GroupMemberConversation for each member.
        // long peerTwincodeOutbound = cursor.getLong(offset + 4);
        UUID resourceId = cursor.getUUID(offset + 5);
        // UUID peerResourceId = cursor.getUUID(offset + 6);
        long permissions = cursor.getLong(offset + 7);
        long joinPermissions = cursor.getLong(offset + 8);
        // long lastConnectDate = cursor.getLong(offset + 9);
        // long lastRetryDate = cursor.getLong(offset + 10);
        int flags = cursor.getInt(offset + 11);
        final RepositoryObject group = mDatabase.loadRepositoryObject(groupId, schemaId);
        if (group == null) {
            return null;
        }
        GroupConversationImpl result = new GroupConversationImpl(identifier, conversationId, group, creationDate,
                resourceId, permissions, joinPermissions, flags);
        loadMembers(result);
        return result;
    }

    @Override
    public boolean loadObject(@NonNull GroupConversationImpl object, @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadObject: object=" + object + " offset=" + offset);
        }

        // Ignore fields which are read-only.
        long permissions = cursor.getLong(offset + 7);
        long joinPermissions = cursor.getLong(offset + 8);
        // long lastConnectDate = cursor.getLong(offset + 9);
        // long lastRetryDate = cursor.getLong(offset + 10);
        int flags = cursor.getInt(offset + 11);
        object.update(permissions, joinPermissions, flags);
        loadMembers(object);
        return true;
    }

    @Override
    public boolean isLocal() {

        return true;
    }

    private void loadMembers(@NonNull GroupConversationImpl group) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadMembers: group=" + group);
        }

        long groupId = group.getDatabaseId().getId();
        String query = "SELECT c.id, c.uuid, c.creationDate,"
                + " c.peerTwincodeOutbound, c.resourceId, c.peerResourceId, c.permissions,"
                + " c.lastConnectDate, c.lastRetryDate, c.flags FROM conversation AS c"
                + " WHERE c.groupId=? AND c.id!=? ORDER BY c.id ASC";
        String[] params = {
                Long.toString(groupId),
                Long.toString(groupId)
        };

        try (DatabaseCursor cursor = mDatabase.rawQuery(query, params)) {

            // Get list of existing members in the group.
            final Map<UUID, GroupMemberConversationImpl> members = group.getMembers();
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                UUID conversationId = cursor.getUUID(1);
                long creationDate = cursor.getLong(2);
                long peerTwincodeOutboundId = cursor.getLong(3);
                UUID resourceId = cursor.getUUID(4);
                UUID peerResourceId = cursor.getUUID(5);
                long permissions = cursor.getLong(6);
                long lastConnectDate = cursor.getLong(7);
                long lastRetryDate = cursor.getLong(8);
                int flags = cursor.getInt(9);

                TwincodeOutbound peerTwincodeOutbound = mDatabase.loadTwincodeOutbound(peerTwincodeOutboundId);
                if (peerTwincodeOutbound != null) {
                    UUID memberTwincodeId = peerTwincodeOutbound.getId();
                    DatabaseIdentifier identifier = new DatabaseIdentifier(this, id);

                    GroupMemberConversationImpl member = members.remove(memberTwincodeId);
                    if (member != null) {
                        member.update(peerResourceId, permissions, lastConnectDate, lastRetryDate, flags);
                    } else {
                        member = new GroupMemberConversationImpl(identifier, conversationId, group,
                                creationDate, resourceId, peerResourceId, permissions, lastConnectDate,
                                lastRetryDate, flags, peerTwincodeOutbound, null);
                        group.addMember(memberTwincodeId, member);

                        // Make sure the group member is also part of the cache because
                        // we rely on it for getConversationWithId().
                        mDatabase.putCache(member);
                    }
                }
            }

            // Remove members that have been deleted from the database.
            if (!members.isEmpty()) {
                for (UUID memberTwincodeId : members.keySet()) {
                    GroupMemberConversationImpl member = group.delMember(memberTwincodeId);
                    if (member != null) {
                        mDatabase.evictCache(member.getDatabaseId());
                    }
                }
            }
        }
    }
}
