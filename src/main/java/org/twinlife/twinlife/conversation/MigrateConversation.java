/*
 *  Copyright (c) 2015-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Houssem Temanni (Houssem.Temanni@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import android.content.ContentValues;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.database.Columns;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.Tables;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Migrate the database from an old version to a new one.
 */
class MigrateConversation {
    private static final String LOG_TAG = "MigrateConversation...";
    private static final boolean DEBUG = false;

    private final DatabaseServiceImpl mDatabase;
    private final SerializerFactory mSerializerFactory;
    private final Map<UUID, Long> mObjectMap;
    private final Map<UUID, Long> mTwincodeToConversationIdMap;
    protected final BaseServiceImpl<?> mService;

    MigrateConversation(@NonNull BaseServiceImpl<?> service, @NonNull DatabaseServiceImpl database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "MigrateConversation: service=" + service + " database=" + database);
        }

        mService = service;
        mObjectMap = new HashMap<>();
        mTwincodeToConversationIdMap = new HashMap<>();

        mDatabase = database;
        mSerializerFactory = service.getSerializerFactoryImpl();
    }

    protected void onUpgrade(@NonNull Transaction transaction, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade: transaction=" + transaction + " oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        /*
         * <pre>
         *
         * Database Version 16
         *  Date: 2022/02/25
         *
         *  ConversationService
         *   Update oldVersion [12]:
         *    Add table conversationDescriptorAnnotation
         *
         * Database Version 12
         *  Date: 2020/07/06
         *
         *  ConversationService
         *   Update oldVersion [11]:
         *    Add column 'cid INTEGER' in table conversationOperation
         *    Add column 'lastConnectDate INTEGER' in table conversationConversation
         *    Add column 'groupId INTEGER' in table conversationConversation
         *
         * Database Version 9
         *  Date: 2020/02/07
         *
         *  ConversationService
         *   Update oldVersion [7..10]:
         *    Add column 'createdTimestamp INTEGER' in table conversationDescriptor
         *    Add column 'cid INTEGER' in table conversationDescriptor
         *    Add column 'descriptorType INTEGER' in table conversationDescriptor
         *    Add column 'cid INTEGER' in table conversationConversation
         *   Update oldVersion [6]: -
         *   Update oldVersion [5]:
         *    Rename conversationObject table: conversationDescriptor
         *    Delete digest column from conversationDescriptor table
         *   Update oldVersion [0,4]: reset
         *
         * Database Version 7
         *  Date: 2017/04/10
         *
         *  ConversationService
         *   Update oldVersion [6]: -
         *   Update oldVersion [5]:
         *    Rename conversationObject table: conversationDescriptor
         *    Delete digest column from conversationDescriptor table
         *   Update oldVersion [0,4]: reset
         * </pre>
         */

        if (oldVersion < 20) {
            prepareObjectMap();
            // Do the migration in several steps in case it is interrupted by the user.
            if (transaction.hasTable("conversationConversation")) {
                upgradeConversation_V20(transaction, oldVersion);
                upgradeGroupMemberConversation_V20(transaction, oldVersion);
                transaction.dropTable("conversationConversation");
                transaction.commit();
            }

            if (transaction.hasTable("conversationDescriptor")) {
                upgradeDescriptors_V20(transaction);
                transaction.dropTable("conversationDescriptor");
                transaction.commit();
            }

            // Annotations are introduced in V16.
            if (transaction.hasTable("conversationDescriptorAnnotation")) {
                if (oldVersion >= 16) {
                    upgradeAnnotations_V20(transaction);
                }
                transaction.dropTable("conversationDescriptorAnnotation");
                transaction.commit();
            }
            if (transaction.hasTable("conversationOperation")) {
                upgradeOperation_V20(transaction);
                transaction.dropTable("conversationOperation");
                transaction.commit();
            }
            if (transaction.hasTable("notificationNotification")) {
                upgradeNotifications_V20(transaction);
                transaction.dropTable("notificationNotification");
            }
            // Last commit is done by DatabaseService.
        }
    }

    /**
     * Find the descriptor database id which corresponds to the {twincodeOutboundId, sequenceId} pair.
     *
     * @param sequenceId the sequence id.
     * @param twincodeOutboundId the twincode outbound id.
     * @return the descriptor database id or null if it was not found.
     * @throws DatabaseException when a database exception occurred.
     */
    @Nullable
    private Long findDescriptor(long sequenceId, @NonNull UUID twincodeOutboundId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "findDescriptor sequenceId=" + sequenceId + " twincodeOutboundId=" + twincodeOutboundId);
        }

        final Long cid = mTwincodeToConversationIdMap.get(twincodeOutboundId);
        final Long twincodeId = mObjectMap.get(twincodeOutboundId);
        if (twincodeId == null || cid == null) {
            return null;
        }

        return mDatabase.longQuery("SELECT d.id FROM descriptor AS d"
                + " WHERE d.cid=? AND d.twincodeOutbound=? AND d.sequenceId=?", new String[]{
                Long.toString(cid),
                Long.toString(twincodeId),
                Long.toString(sequenceId)
        });
    }

    /**
     * Find the repositoryObject database id knowing either the object UUID or the twincode outbound (contact's identity).
     *
     * @param objectId the optional object UUID.
     * @param twincodeOutboundId the optional twincode outbound UUID.
     * @return the object id in the repository table or null.
     * @throws DatabaseException when a database exception occurred.
     */
    @Nullable
    private Long findRepositoryObjectId(@Nullable UUID objectId, @Nullable UUID twincodeOutboundId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "findRepositoryObjectId objectId=" + objectId + " twincodeOutboundId=" + twincodeOutboundId);
        }

        if (objectId != null) {
            Long id = mObjectMap.get(objectId);
            if (id != null) {
                return id;
            }
        }

        if (twincodeOutboundId == null) {
            return null;
        }

        return mDatabase.longQuery("SELECT r.id"
                + " FROM twincodeOutbound AS twout"
                + " INNER JOIN repository AS r ON r.twincodeOutbound=twoud.id"
                + " WHERE twout.twincode=?", new String[]{twincodeOutboundId.toString()});
    }

    private void prepareObjectMap() throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareObjectMap");
        }

        // Pre-load the list of contact/groups to populate the mObjectMap table by using the new repository table.
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id, uuid FROM repository", null)) {
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(0);
                final UUID subjectId = cursor.getUUID(1);
                if (subjectId != null) {
                    mObjectMap.put(subjectId, id);
                }
            }
        }

        // Pre-load the list of twincode outbound to populate the mObjectMap table by using the new twincodeOutbound table.
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id, twincodeId FROM twincodeOutbound", null)) {
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(0);
                final UUID twincodeId = cursor.getUUID(1);
                if (twincodeId != null) {
                    mObjectMap.put(twincodeId, id);
                }
            }
        }

        // Pre-load the list of conversation to populate the mObjectMap table by using the new conversation table.
        // Note: this table is empty for a normal upgrade but it can contain conversations if the upgrade was interrupted!
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT c.id, c.groupId,"
                + " c.uuid, twout.twincodeId, peerTwout.twincodeId FROM conversation AS c"
                + " LEFT JOIN repository AS r ON c.subject = r.id"
                + " LEFT JOIN twincodeOutbound AS twout ON r.twincodeOutbound=twout.id"
                + " LEFT JOIN twincodeOutbound AS peerTwout ON c.peerTwincodeOutbound = peerTwout.id", null)) {
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(0);
                final long groupId = cursor.getLong(1);
                final UUID conversationId = cursor.getUUID(2);
                final UUID twincodeId = cursor.getUUID(3);
                final UUID peerTwincodeId = cursor.getUUID(4);
                if (conversationId != null) {
                    mObjectMap.put(conversationId, id);
                }
                if (twincodeId != null) {
                    mTwincodeToConversationIdMap.put(twincodeId, groupId > 0 ? groupId : id);
                }
                if (peerTwincodeId != null) {
                    mTwincodeToConversationIdMap.put(peerTwincodeId, groupId > 0 ? groupId : id);
                }
            }
        }
    }

    /**
     * Migrate the notification from V7 to V20.
     * This migration must be executed after migrating the descriptors.
     *
     * @param transaction the current transaction.
     */
    private void upgradeNotifications_V20(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgradeNotifications_V20");
        }

        // Load the notifications to know the timestamp, originatorId and acknowledge.
        final ContentValues values = new ContentValues();
        final long startTime = EventMonitor.start();
        int count = 0;
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT uuid, content FROM notificationNotification", null)) {
            while (cursor.moveToNext()) {
                final UUID notificationId = cursor.getUUID(0);
                final byte[] content = cursor.getBlob(1);

                if (notificationId != null && content != null) {
                    try {
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                        BinaryDecoder decoder = new BinaryDecoder(inputStream);

                        /* UUID schemaId =*/ decoder.readUUID();
                        /* int schemaVersion =*/ decoder.readInt();
                        UUID id = decoder.readUUID();
                        int notificationType = decoder.readEnum();
                        UUID originatorId = decoder.readUUID();
                        long timestamp = decoder.readLong();
                        boolean acknowledge = decoder.readBoolean();
                        /* boolean isGroup = */ decoder.readBoolean();
                        long sequenceId = decoder.readLong();
                        UUID twincodeOutboundId = null;
                        if (sequenceId != 0) {
                            twincodeOutboundId = decoder.readUUID();
                        }
                        // int notificationId = decoder.readInt();
                        Long descriptorId = null;
                        Long subjectId = findRepositoryObjectId(originatorId, twincodeOutboundId);
                        if (twincodeOutboundId != null && !mObjectMap.containsKey(twincodeOutboundId)) {
                            descriptorId = findDescriptor(sequenceId, twincodeOutboundId);
                        }

                        if (subjectId != null) {
                            values.put(Columns.ID, transaction.allocateId(DatabaseTable.TABLE_NOTIFICATION));
                            values.put(Columns.UUID, id.toString());
                            values.put(Columns.TYPE, notificationType);
                            values.put(Columns.CREATION_DATE, timestamp);
                            values.put(Columns.FLAGS, acknowledge ? 1 : 0);
                            values.put(Columns.SUBJECT, subjectId);
                            if (descriptorId != null) {
                                values.put(Columns.DESCRIPTOR, descriptorId);
                            }
                            transaction.insert(Tables.NOTIFICATION, values);
                            count++;
                        }

                    } catch (Exception exception) {
                        if (Logger.ERROR) {
                            Logger.error(LOG_TAG, "deserialize", exception);
                        }
                    }
                }
            }
        }
        if (BuildConfig.ENABLE_EVENT_MONITOR) {
            EventMonitor.event("Migrated " + count + " notifications", startTime);
        }
    }

    /**
     * Upgrade the conversationDescriptor table from V7/V8 to V20.
     * <p>
     * Called by onUpgrade() within a transaction and after the migration of conversations.
     */
    private void upgradeDescriptors_V20(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgradeDescriptors_V20");
        }

        // Upgrade each conversation one by one and delete the descriptors after each migration
        // and commit the transaction after each conversation.  If we are interrupted, we can restart
        // the migration, and we also reclaim storage from the original table.
        final long startTime = EventMonitor.start();
        final Set<Long> conversationIds = new HashSet<>(mTwincodeToConversationIdMap.values());
        int count = 0;
        for (Long cid : conversationIds) {
            count += upgradeConversationDescriptors_V20(transaction, cid);
        }
        if (BuildConfig.ENABLE_EVENT_MONITOR) {
            EventMonitor.event("Migrated " + count + " descriptors", startTime);
        }
    }

    /**
     * Upgrade the conversationDescriptor table from V7/V8 to V20.
     * <p>
     * Called by onUpgrade() within a transaction and after the migration of conversations.
     */
    private int upgradeConversationDescriptors_V20(@NonNull Transaction transaction, long cid) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgradeDescriptors_V20");
        }

        // Scan each descriptor, extract the content and timestamps and insert in the new table.
        // We do this from oldest descriptor to the newest ones so that the `replyTo` references can be re-constructed.
        int count = 0;
        String[] args = new String[] {
                Long.toString(cid)
        };
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT sequenceId, createdTimestamp, twincodeOutboundId, "
                + " content, timestamps FROM conversationDescriptor WHERE cid=? ORDER BY createdTimestamp ASC", args)) {
            while (cursor.moveToNext()) {
                final long sequenceId = cursor.getLong(0);
                final long createdTimestamp = cursor.getLong(1);
                final UUID twincodeOutboundId = cursor.getUUID(2);
                final byte[] content = cursor.getBlob(3);
                final byte[] timestamps = cursor.getBlob(4);

                if (twincodeOutboundId == null) {
                    continue;
                }
                final Long twincodeId = mObjectMap.get(twincodeOutboundId);
                if (twincodeId == null) {
                    continue;
                }
                try {
                    final DescriptorImpl descriptorImpl = DescriptorImpl.extractDescriptor(mSerializerFactory, twincodeOutboundId, sequenceId,
                            createdTimestamp, content, timestamps);
                    if (descriptorImpl != null) {
                        // If the twincode is not associated with a conversation ID, register it.
                        // This is necessary for twinrooms.
                        if (!mTwincodeToConversationIdMap.containsKey(twincodeOutboundId)) {
                            mTwincodeToConversationIdMap.put(twincodeOutboundId, cid);
                        }
                        final ContentValues values = new ContentValues();
                        final long id = transaction.allocateId(DatabaseTable.TABLE_DESCRIPTOR);
                        values.put(Columns.ID, id);
                        values.put(Columns.CID, cid);
                        values.put(Columns.CREATION_DATE, descriptorImpl.getCreatedTimestamp());
                        values.put(Columns.DESCRIPTOR_TYPE, ConversationServiceProvider.fromDescriptorType(descriptorImpl.getType()));
                        values.put(Columns.SEQUENCE_ID, descriptorImpl.getSequenceId());
                        values.put(Columns.EXPIRE_TIMEOUT, descriptorImpl.getExpireTimeout());
                        values.put(Columns.FLAGS, descriptorImpl.getFlags());
                        values.put(Columns.SEND_DATE, descriptorImpl.getSentTimestamp());
                        values.put(Columns.READ_DATE, descriptorImpl.getReadTimestamp());
                        values.put(Columns.RECEIVE_DATE, descriptorImpl.getReceivedTimestamp());
                        values.put(Columns.PEER_DELETE_DATE, descriptorImpl.getPeerDeletedTimestamp());
                        values.put(Columns.DELETE_DATE, descriptorImpl.getDeletedTimestamp());
                        values.put(Columns.VALUE, descriptorImpl.getValue());
                        values.put(Columns.TWINCODE_OUTBOUND, twincodeId);
                        final UUID sendTo = descriptorImpl.getSendTo();
                        if (sendTo != null) {
                            values.put(Columns.SENT_TO, mObjectMap.get(sendTo));
                        }
                        final DescriptorId replyTo = descriptorImpl.getReplyToDescriptorId();
                        if (replyTo != null) {
                            values.put(Columns.REPLY_TO, findDescriptor(replyTo.sequenceId, replyTo.twincodeOutboundId));
                        }

                        // Optional operation specific data.
                        final String newContent = descriptorImpl.serialize();
                        if (newContent != null) {
                            values.put(Columns.CONTENT, newContent);
                        }
                        transaction.insert(Tables.DESCRIPTOR, values);
                        count++;
                        if (descriptorImpl instanceof InvitationDescriptorImpl) {
                            final InvitationDescriptorImpl invitationDescriptor = (InvitationDescriptorImpl) descriptorImpl;
                            final Long groupId = mTwincodeToConversationIdMap.get(invitationDescriptor.getGroupTwincodeId());
                            final Long inviterId = mObjectMap.get(invitationDescriptor.getInviterTwincodeId());
                            final Long joinedId = mObjectMap.get(invitationDescriptor.getMemberTwincodeId());
                            values.clear();
                            values.put(Columns.GROUP_ID, groupId);
                            values.put(Columns.DESCRIPTOR, id);
                            values.put(Columns.INVITER_MEMBER, inviterId);
                            values.put(Columns.JOINED_MEMBER, joinedId);
                            transaction.insert(Tables.INVITATION, values);
                        }
                    } else {
                        Logger.error(LOG_TAG, "extract failed");
                    }
                } catch (DatabaseException dbException) {
                    throw dbException;

                } catch (Exception exception) {
                    if (Logger.ERROR) {
                        Logger.error(LOG_TAG, "extractDescriptor", exception, twincodeOutboundId, " " + sequenceId);
                    }
                }
            }
        }
        transaction.delete("conversationDescriptor", "cid=?", args);
        transaction.commit();
        return count;
    }

    /**
     * Upgrade the conversationDescriptorAnnotation table from V16 to V20.
     *
     * @param transaction the current transaction.
     */
    private void upgradeAnnotations_V20(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgradeAnnotations_V20");
        }

        // Scan each annotation, apply mapping and insert in the new table.
        final long startTime = EventMonitor.start();
        int count = 0;
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT sequenceId, twincodeOutboundId,"
                + " peerTwincodeOutboundId, kind, value FROM conversationDescriptorAnnotation", null)) {
            while (cursor.moveToNext()) {
                final long sequenceId = cursor.getLong(0);
                final UUID twincodeOutboundId = cursor.getUUID(1);
                final UUID peerTwincodeOutboundId = cursor.getUUID(2);
                final int kind = cursor.getInt(3);
                final long value = cursor.getLong(4);

                if (twincodeOutboundId == null) {
                    continue;
                }
                final Long cid = mTwincodeToConversationIdMap.get(twincodeOutboundId);
                if (cid == null) {
                    continue;
                }
                final Long descriptor = findDescriptor(sequenceId, twincodeOutboundId);
                if (descriptor == null) {
                    continue;
                }
                try {
                    final Long peerTwincode = peerTwincodeOutboundId == null ? null : mObjectMap.get(peerTwincodeOutboundId);
                    final ContentValues values = new ContentValues();
                    values.put(Columns.CID, cid);
                    values.put(Columns.DESCRIPTOR, descriptor);
                    values.put(Columns.KIND, kind);
                    values.put(Columns.VALUE, value);
                    values.put(Columns.PEER_TWINCODE_OUTBOUND, peerTwincode);
                    transaction.insert(Tables.ANNOTATION, values);
                    count++;
                } catch (Exception exception) {
                    Log.e(LOG_TAG, "Migration of annotation " + sequenceId + " failed: ", exception);
                }
            }
        }
        if (BuildConfig.ENABLE_EVENT_MONITOR) {
            EventMonitor.event("Migrated " + count + " annotations", startTime);
        }
    }

    /**
     * Migrate the operations from V12 to V20 format.
     *
     * @param transaction the database transaction.
     */
    private void upgradeOperation_V20(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgradeOperation_V20");
        }

        final long startTime = EventMonitor.start();
        int count = 0;

        // Use only 'id' and 'content' so that we can also migrate V12 at the same time.
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id, content FROM conversationOperation", null)) {
            while (cursor.moveToNext()) {
                final long id = cursor.getLong(0);
                final byte[] content = cursor.getBlob(1);

                try {
                    final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                    final BinaryDecoder decoder = new BinaryDecoder(inputStream);

                    final UUID schemaId = decoder.readUUID();
                    final int schemaVersion = decoder.readInt();

                    /* long id = */ decoder.readLong();
                    final int operationType = decoder.readEnum();
                    final UUID conversationId = decoder.readUUID();
                    final long timestamp = decoder.readLong();
                    long chunkStart = 0;
                    Long descriptor = null;
                    byte[] newContent = null;
                    final Long cid = mObjectMap.get(conversationId);

                    if (ResetConversationOperation.SCHEMA_ID.equals(schemaId)) {
                        ResetConversationOperation operation = null;
                        if (ResetConversationOperation.SCHEMA_VERSION_4 == schemaVersion) {
                            operation = ResetConversationOperation.SERIALIZER_4.deserialize(decoder);
                        } else if (ResetConversationOperation.SCHEMA_VERSION_3 == schemaVersion) {
                            operation = ResetConversationOperation.SERIALIZER_3.deserialize(decoder);
                        } else if (ResetConversationOperation.SCHEMA_VERSION_2 == schemaVersion) {
                            operation = ResetConversationOperation.SERIALIZER_2.deserialize(decoder);
                        }
                        if (operation != null) {
                            newContent = operation.serialize();
                        }
                    // Nothing to do for } else if (SynchronizeConversationOperation.SCHEMA_ID.equals(schemaId)) {

                    } else if (PushObjectOperation.SCHEMA_ID.equals(schemaId)
                            || PushGeolocationOperation.SCHEMA_ID.equals(schemaId)
                            || PushTwincodeOperation.SCHEMA_ID.equals(schemaId)
                            || UpdateAnnotationsOperation.SCHEMA_ID.equals(schemaId)) {
                        UUID twincodeOutboundId = decoder.readUUID();
                        long sequenceId = decoder.readLong();
                        descriptor = findDescriptor(sequenceId, twincodeOutboundId);

                    } else if (PushFileOperation.SCHEMA_ID.equals(schemaId)) {
                        UUID twincodeOutboundId = decoder.readUUID();
                        long sequenceId = decoder.readLong();
                        descriptor = findDescriptor(sequenceId, twincodeOutboundId);
                        chunkStart = decoder.readLong();

                    } else if (UpdateDescriptorTimestampOperation.SCHEMA_ID.equals(schemaId)) {
                        UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType timestampType;
                        int value = decoder.readEnum();
                        switch (value) {
                            case 0:
                                timestampType = UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType.READ;
                                break;
                            case 1:
                                timestampType = UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType.DELETE;
                                break;
                            case 2:
                                timestampType = UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType.PEER_DELETE;
                                break;
                            default:
                                throw new SerializerException();
                        }
                        UUID twincodeOutboundId = decoder.readUUID();
                        long sequenceId = decoder.readLong();
                        long updateTimestamp = decoder.readLong();
                        descriptor = findDescriptor(sequenceId, twincodeOutboundId);
                        newContent = UpdateDescriptorTimestampOperation.serializeOperation(timestampType, updateTimestamp,
                                new DescriptorId(0, twincodeOutboundId, sequenceId));

                    } else if (GroupOperation.SCHEMA_ID.equals(schemaId)) {
                        int mode = decoder.readInt();
                        if (mode == 0) {
                            UUID groupId = decoder.readUUID();
                            UUID memberId = decoder.readUUID();
                            long permissions = decoder.readLong();
                            newContent = GroupOperation.serializeOperation(groupId, memberId, permissions, null, null, null);

                        } else {
                            long sequenceId = decoder.readLong();
                            UUID twincodeOutboundId = decoder.readUUID();
                            descriptor = findDescriptor(sequenceId, twincodeOutboundId);
                        }
                    }

                    if (cid != null) {
                        final ContentValues values = new ContentValues();
                        values.put(Columns.ID, id);
                        values.put(Columns.CID, cid);
                        values.put(Columns.CREATION_DATE, timestamp);
                        values.put(Columns.TYPE, operationType);
                        if (descriptor != null) {
                            values.put(Columns.DESCRIPTOR, descriptor);
                        }
                        if (chunkStart > 0) {
                            values.put(Columns.CHUNK_START, chunkStart);
                        }
                        if (newContent != null) {
                            values.put(Columns.CONTENT, newContent);
                        }
                        transaction.insert(Tables.OPERATION, values);
                        count++;
                    } else {
                        Log.w(LOG_TAG, "No conversation id " + conversationId + " associated with operation " + id);
                    }
                } catch (Exception exception) {
                    Log.e(LOG_TAG, "Migration of operation " + id + " failed: ", exception);
                }
            }
        }
        if (BuildConfig.ENABLE_EVENT_MONITOR) {
            EventMonitor.event("Migrated " + count + " operations", startTime);
        }
    }

    /**
     * Migrate the contact and group conversation to the V20 format.
     *
     * @param transaction the database transaction.
     */
    private void upgradeConversation_V20(@NonNull Transaction transaction, int oldVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgradeConversation_V20");
        }

        final long startTime = EventMonitor.start();
        int count = 0;
        final String sql;

        // lastConnectDate was introduced in V12 on Android (V10 on iOS).
        if (oldVersion < 12) {
            sql = "SELECT cid, content FROM conversationConversation";
        } else {
            sql = "SELECT cid, content, lastConnectDate FROM conversationConversation WHERE groupId IS NULL OR cid=groupId";
        }
        try (DatabaseCursor cursor = mDatabase.rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                long cid = cursor.getLong(0);
                final byte[] content = cursor.getBlob(1);
                final long lastConnectDate = oldVersion < 12 ? 0 : cursor.getLong(2);

                try {
                    final ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                    final BinaryDecoder decoder = new BinaryDecoder(inputStream);

                    final UUID schemaId = decoder.readUUID();
                    /* int schemaVersion =*/ decoder.readInt();
                    UUID conversationId = null;
                    UUID objectId = null;
                    UUID resourceId = null;
                    UUID peerResourceId = null;
                    UUID twincodeOutboundId = null;
                    UUID peerTwincodeOutboundId = null;
                    long group = 0;
                    int flags = 0;
                    long permissions = -1L;
                    long joinPermissions = 0L;
                    if (ConversationImpl.SCHEMA_ID.equals(schemaId)) {
                        /*
                         * ConversationImpl schema version 4
                         *  Date: 2016/05/13
                         * {
                         *  "type":"record",
                         *  "name":"Conversation",
                         *  "namespace":"org.twinlife.schemas.conversation",
                         *  "fields":
                         *  [
                         *   {"name":"schemaId", "type":"uuid"},
                         *   {"name":"schemaVersion", "type":"int"}
                         *   {"name":"id", "type":"uuid"}
                         *   {"name":"twincodeOutboundId", "type":"uuid"}
                         *   {"name":"peerTwincodeOutboundId", "type":"uuid"}
                         *   {"name":"twincodeInboundId", "type":"uuid"}
                         *   {"name":"contactId", "type":"uuid"}
                         *   {"name":"resourceId", "type":"uuid"}
                         *   {"name":"peerResourceId", [null, "type":"uuid"]}
                         *   {"name":"minSequenceId", "type":"long"}
                         *   {"name":"peerMinSequenceId", "type":"long"}
                         *  ]
                         * }
                         */
                        conversationId = decoder.readUUID();
                        twincodeOutboundId = decoder.readUUID();
                        peerTwincodeOutboundId = decoder.readUUID();
                        /* UUID twincodeInboundId = */ decoder.readUUID();
                        objectId = decoder.readUUID();
                        resourceId = decoder.readUUID();
                        peerResourceId = decoder.readOptionalUUID();
                        /* long minSequenceId = */ decoder.readLong();
                        /* long peerMinSequenceId = */ decoder.readLong();

                    } else if (GroupConversationImpl.SCHEMA_ID.equals(schemaId)) {
                        /*
                         * GroupConversationImpl schema version 2
                         *  Date: 2020/06/19
                         * {
                         *  "type":"record",
                         *  "name":"GroupConversation",
                         *  "namespace":"org.twinlife.schemas.conversation",
                         *  "fields":
                         *  [
                         *   {"name":"schemaId", "type":"uuid"},
                         *   {"name":"schemaVersion", "type":"int"}
                         *   {"name":"id", "type":"uuid"}
                         *   {"name":"twincodeOutboundId", "type":"uuid"}
                         *   {"name":"twincodeInboundId", "type":"uuid"}
                         *   {"name":"groupTwincodeId", "type":"uuid"}
                         *   {"name":"groupId", "type":"uuid"}
                         *   {"name":"minSequenceId", "type":"long"}
                         *   {"name":"peerMinSequenceId", "type":"long"}
                         *   {"name":"permissions", "type":"long"}
                         *   {"name":"joinPermissions", "type":"long"}
                         *   {"name":"state", "type":"int"}
                         *   {"name":"invitations", [
                         *      {"name":"contactId", "type": "uuid"}
                         *      {"name":"twincodeOutboundId", "type": "uuid"}
                         *      {"name":"sequenceId", "type": "long"}
                         *   ]}
                         *  ]
                         * }
                         */
                        conversationId = decoder.readUUID();
                        twincodeOutboundId = decoder.readUUID();
                        decoder.readUUID(); // twincodeInboundId
                        peerTwincodeOutboundId = decoder.readUUID();
                        objectId = decoder.readUUID();
                        decoder.readLong(); // old min sequenceId
                        decoder.readLong(); // old peer min sequenceId
                        permissions = decoder.readLong();
                        joinPermissions = decoder.readLong();
                        flags = decoder.readInt();
                        group = cid;
                        // Drop the invitations because we rely on the descriptors and the invitation table.
                        // The resourceId and peerResourceId are nil for the group because they are
                        // defined on the GroupMemberConversation.
                    }

                    final Long peerTwincode;
                    if (peerTwincodeOutboundId != null && !Twincode.NOT_DEFINED.equals(peerTwincodeOutboundId)) {
                        peerTwincode = mObjectMap.get(peerTwincodeOutboundId);
                    } else {
                        peerTwincode = null;
                    }

                    final Long repositoryObjectId = findRepositoryObjectId(objectId, twincodeOutboundId);
                    if (repositoryObjectId != null && conversationId != null) {
                        if (cid == 0) {
                            cid = transaction.allocateId(DatabaseTable.TABLE_CONVERSATION);
                        }
                        final ContentValues values = new ContentValues();
                        values.put(Columns.ID, cid);
                        values.put(Columns.UUID, conversationId.toString());
                        values.put(Columns.CREATION_DATE, 0);
                        values.put(Columns.SUBJECT, repositoryObjectId);
                        if (group > 0) {
                            values.put(Columns.GROUP_ID, group);
                        }
                        if (peerTwincode != null) {
                            values.put(Columns.PEER_TWINCODE_OUTBOUND, peerTwincode);
                        }
                        if (resourceId != null) {
                            values.put(Columns.RESOURCE_ID, resourceId.toString());
                        }
                        if (peerResourceId != null) {
                            values.put(Columns.PEER_RESOURCE_ID, peerResourceId.toString());
                        }
                        values.put(Columns.PERMISSIONS, permissions);
                        values.put(Columns.JOIN_PERMISSIONS, joinPermissions);
                        values.put(Columns.LAST_CONNECT_DATE, lastConnectDate);
                        values.put(Columns.FLAGS, flags);
                        transaction.insert(Tables.CONVERSATION, values);
                        count++;
                        mObjectMap.put(conversationId, cid);

                        if (twincodeOutboundId != null) {
                            mTwincodeToConversationIdMap.put(twincodeOutboundId, cid);
                        }
                        if (peerTwincodeOutboundId != null) {
                            mTwincodeToConversationIdMap.put(peerTwincodeOutboundId, cid);
                        }

                    } else {
                        Log.w(LOG_TAG, "No contact associated with conversation " + cid);
                    }
                } catch (Exception exception) {
                    Log.e(LOG_TAG, "Migration of " + cid + " failed: ", exception);
                }
            }
        }
        if (BuildConfig.ENABLE_EVENT_MONITOR) {
            EventMonitor.event("Migrated " + count + " conversations", startTime);
        }
    }

    /**
     * Migrate the group member conversation to the V20 format.
     *
     * @param transaction the database transaction.
     */
    private void upgradeGroupMemberConversation_V20(@NonNull Transaction transaction, int oldVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgradeGroupMemberConversation_V20 oldVersion=" + oldVersion);
        }

        final long startTime = EventMonitor.start();
        int count = 0;
        final String sql;

        // lastConnectDate was introduced in V12.
        if (oldVersion < 12) {
            sql = "SELECT m.cid, m.groupId, g.subject, m.content FROM conversationConversation AS m"
                    + " INNER JOIN conversation AS g ON m.groupId = g.id AND m.groupId = g.groupId";
        } else {
            sql = "SELECT m.cid, m.groupId, g.subject, m.content, m.lastConnectDate FROM conversationConversation AS m"
                    + " INNER JOIN conversation AS g ON m.groupId = g.id AND m.groupId = g.groupId";
        }
        try (DatabaseCursor cursor = mDatabase.rawQuery(sql, new String[]{})) {
            while (cursor.moveToNext()) {
                final long cid = cursor.getLong(0);
                final long groupId = cursor.getLong(1);
                final long groupRepositoryObjectId = cursor.getLong(2);
                final byte[] content = cursor.getBlob(3);
                final long lastConnectDate = oldVersion < 12 ? 0 : cursor.getLong(4);

                try {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
                    BinaryDecoder decoder = new BinaryDecoder(inputStream);

                    /*
                     * GroupMemberConversation schema version 1
                     *  Date: 2020/06/19
                     *
                     * {
                     *  "type":"record",
                     *  "name":"GroupMemberConversation",
                     *  "namespace":"org.twinlife.schemas.conversation",
                     *  "fields":
                     *  [
                     *   {"name":"schemaId", "type":"uuid"},
                     *   {"name":"schemaVersion", "type":"int"}
                     *   {"name":"id", "type":"uuid"}
                     *   {"name":"peerTwincodeOutboundId", "type": "uuid"}
                     *   {"name":"resourceId", "type": "uuid"}
                     *   {"name":"minSequenceId", "type":"long"}
                     *   {"name":"peerMinSequenceId", "type":"long"}
                     *   {"name":"peerResourceId", ["null", "type":"uuid"]}]
                     *   {"name":"invitedContactId", ["null", "type":"uuid"]}]
                     *   {"name":"permissions", "type":"long"}
                     *  ]
                     * }
                     */
                    /* UUID schemaId = */ decoder.readUUID();
                    /* int schemaVersion = */ decoder.readInt();
                    final UUID conversationId = decoder.readUUID();
                    final UUID peerTwincodeOutboundId = decoder.readUUID();
                    final UUID resourceId = decoder.readUUID();
                    decoder.readLong(); // old min sequenceId
                    decoder.readLong(); // old peer min sequenceId
                    UUID peerResourceId = decoder.readOptionalUUID();
                    UUID invitedContactId = decoder.readOptionalUUID();
                    long memberPermissions = decoder.readLong();

                    final Long peerTwincode = mObjectMap.get(peerTwincodeOutboundId);
                    if (groupRepositoryObjectId > 0) {
                        final ContentValues values = new ContentValues();
                        values.put(Columns.ID, cid);
                        values.put(Columns.UUID, conversationId.toString());
                        values.put(Columns.CREATION_DATE, 0);
                        values.put(Columns.SUBJECT, groupRepositoryObjectId);
                        values.put(Columns.GROUP_ID, groupId);
                        if (peerTwincode != null) {
                            values.put(Columns.PEER_TWINCODE_OUTBOUND, peerTwincode);
                        }
                        values.put(Columns.RESOURCE_ID, resourceId.toString());
                        if (peerResourceId != null) {
                            values.put(Columns.PEER_RESOURCE_ID, peerResourceId.toString());
                        }
                        values.put(Columns.PERMISSIONS, memberPermissions);
                        values.put(Columns.JOIN_PERMISSIONS, 0L);
                        values.put(Columns.LAST_CONNECT_DATE, lastConnectDate);
                        values.put(Columns.FLAGS, 0);
                        if (invitedContactId != null) {
                            values.put(Columns.INVITED_CONTACT, mObjectMap.get(invitedContactId));
                        }
                        transaction.insert(Tables.CONVERSATION, values);
                        count++;

                        mObjectMap.put(conversationId, groupId);
                        if (peerTwincode != null) {
                            mTwincodeToConversationIdMap.put(peerTwincodeOutboundId, groupId);
                        }
                    } else {
                        Log.w(LOG_TAG, "No group associated with conversation " + cid);
                    }
                } catch (Exception exception) {
                    Log.e(LOG_TAG, "Migration of " + cid + " failed: ", exception);
                }
            }
        }

        if (BuildConfig.ENABLE_EVENT_MONITOR) {
            EventMonitor.event("Migrated " + count + " group members", startTime);
        }
    }
}
