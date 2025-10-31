/*
 *  Copyright (c) 2017-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Chedi Baccari (Chedi.Baccari@twinlife-systems.com)
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.notification;

import android.content.ContentValues;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.AnnotationType;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.Notification;
import org.twinlife.twinlife.NotificationService;
import org.twinlife.twinlife.NotificationService.NotificationStat;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.conversation.ConversationServiceProvider;
import org.twinlife.twinlife.database.Columns;
import org.twinlife.twinlife.database.DatabaseObjectFactory;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.DatabaseServiceProvider;
import org.twinlife.twinlife.database.NotificationsCleaner;
import org.twinlife.twinlife.database.QueryBuilder;
import org.twinlife.twinlife.database.Tables;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.util.EventMonitor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NotificationServiceProvider extends DatabaseServiceProvider implements DatabaseObjectFactory<NotificationImpl>, NotificationsCleaner {
    private static final String LOG_TAG = "NotificationServiceP...";
    private static final boolean DEBUG = false;

    private static final UUID SCHEMA_ID = UUID.fromString("1840c20d-b017-48a7-ac20-7c5a16211883");

    /**
     * notification table:
     * id INTEGER: local database identifier (primary key)
     * notificationId INTEGER: the system notification id
     * uuid TEXT NOT NULL: the notification UUID
     * subject INTEGER: the repository object key
     * creationDate INTEGER: the notification creation date
     * descriptor INTEGER: the optional descriptor key associated with the notification
     * type INTEGER NOT NULL: the notification type
     * flags INTEGER NOT NULL: notification flags and status
     * Note:
     * - id, notificationId, uuid, creationDate, subject, descriptor, type are readonly.
     */
    private static final String NOTIFICATION_TABLE =
            "CREATE TABLE IF NOT EXISTS notification (id INTEGER PRIMARY KEY,"
                    + " notificationId INTEGER, uuid TEXT NOT NULL,"
                    + " subject INTEGER, creationDate INTEGER NOT NULL, descriptor INTEGER,"
                    + " type INTEGER NOT NULL, flags INTEGER NOT NULL"
                    + ")";

    /**
     * Table from V7 to V19:
     * "CREATE TABLE IF NOT EXISTS notificationNotification (uuid TEXT PRIMARY KEY NOT NULL, " +
     *                     "originatorId TEXT, timestamp INTEGER, acknowledged INTEGER, content BLOB);";
     */

    private static final String NOTIFICATION_CREATE_INDEX_1 =
            "CREATE INDEX IF NOT EXISTS idx_subject_notification ON notification (subject)";
    private static final String NOTIFICATION_CREATE_INDEX_2 =
            "CREATE INDEX IF NOT EXISTS idx_creationDate_notification ON notification (creationDate)";

    @NonNull
    private final NotificationServiceImpl mService;

    //
    // Implement BaseServiceProvider interface
    //

    NotificationServiceProvider(@NonNull NotificationServiceImpl service,
                                @NonNull DatabaseServiceImpl database) {
        super(service, database, NOTIFICATION_TABLE, DatabaseTable.TABLE_NOTIFICATION);
        if (DEBUG) {
            Log.d(LOG_TAG, "NotificationServiceProvider: service=" + service);
        }

        mService = service;
    }

    //
    // Implement BaseServiceProvider interface
    //

    @Override
    protected void onCreate(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate: transaction=" + transaction);
        }

        super.onCreate(transaction);
        transaction.createSchema(NOTIFICATION_CREATE_INDEX_1);
        transaction.createSchema(NOTIFICATION_CREATE_INDEX_2);
    }

    @Override
    protected void onUpgrade(@NonNull Transaction transaction, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade: transaction=" + transaction + " oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        // Note: migration for V20 is done by the MigrationConversation.
        onCreate(transaction);
    }

    //
    // Implement DatabaseObjectFactory interface
    //

    @Override
    @NonNull
    public UUID getSchemaId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSchemaId");
        }

        return SCHEMA_ID;
    }

    @Override
    public int getSchemaVersion() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSchemaVersion");
        }

        return 0;
    }

    @Override
    @Nullable
    public NotificationImpl createObject(@NonNull DatabaseIdentifier identifier,
                                         @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObject identifier=" + identifier + " offset=" + offset);
        }

        // n.id, n.notificationId, n.uuid, n.creationDate, n.type, n.flags, n.subject, r.schemaId,
        // n.descriptor, d.sequenceId, d.twincodeOutbound
        final int sysId = cursor.getInt(offset);
        final UUID notificationId = cursor.getUUID(offset + 1);
        final long creationDate = cursor.getLong(offset + 2);
        final NotificationService.NotificationType type = toNotificationType(cursor.getInt(offset + 3));
        if (type == null) {
            return null;
        }
        final int flags = cursor.getInt(offset + 4);
        final long subjectId = cursor.getLong(offset + 5);
        final UUID schemaId = cursor.getUUID(offset + 6);
        final long descriptor = cursor.getLong(offset + 7);
        final long sequenceId = cursor.getLong(offset + 8);
        final long twincodeOutboundId = cursor.getLong(offset + 9);
        final long userTwincodeId = cursor.getLong(offset + 10);
        final AnnotationType annotationType = ConversationServiceProvider.toAnnotationType(cursor.getInt(offset + 11));
        final int annotationValue = cursor.getInt(offset + 12);
        final RepositoryObject subject = mDatabase.loadRepositoryObject(subjectId, schemaId);
        if (subject == null) {
            // No object: this notification is now obsolete and must be removed.
            return null;
        }

        final DescriptorId descriptorId;
        if (descriptor > 0) {
            final TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(twincodeOutboundId);
            if (twincodeOutbound == null) {
                // No peer twincode: the contact or group member is revoked, the notification is obsolete and must be removed.
                return null;
            }
            descriptorId = new DescriptorId(descriptor, twincodeOutbound.getId(), sequenceId);
        } else {
            descriptorId = null;
        }
        final TwincodeOutbound userTwincode;
        if (userTwincodeId > 0) {
            userTwincode = mDatabase.loadTwincodeOutbound(userTwincodeId);
        } else {
            userTwincode = null;
        }
        // The annotation associated with this notification is no longer valid, we must drop that notification.
        if (type == NotificationService.NotificationType.UPDATED_ANNOTATION && userTwincode == null) {
            return null;
        }
        return new NotificationImpl(identifier, sysId, notificationId, creationDate, subject, type,
                descriptorId, flags, userTwincode, annotationType, annotationValue);
    }

    @Override
    public boolean loadObject(@NonNull NotificationImpl object, @NonNull DatabaseCursor cursor, int offset) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadObject object=" + object + " offset=" + offset);
        }

        // Not used
        throw new IllegalAccessError();
    }

    @Override
    public boolean isLocal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isLocal");
        }

        return true;
    }

    /**
     * Delete some notifications with the given transaction (commit must be done by the caller).
     * Identify the notifications that are not acknowledged and report them to the NotificationService.
     *
     * @param transaction the transaction to use.
     * @param subjectId the subject id that owns the notification (Contact, Group).
     * @param twincodeId the optional twincode to delete only the notifications of the given sender.
     * @param descriptorId the optional descriptor to only delete notifications associated with a descriptor.
     * @throws DatabaseException the database exception raised.
     */
    @Override
    public void deleteNotifications(@NonNull Transaction transaction, @NonNull Long subjectId,
                                    @Nullable Long twincodeId, @Nullable Long descriptorId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteNotifications: subjectId=" + subjectId + " twincodeId=" + twincodeId + " descriptorId=" + descriptorId);
        }

        final ArrayList<Long> deletedNotifications = new ArrayList<>();
        if (descriptorId != null) {
            final String[] params = {
                    subjectId.toString(),
                    descriptorId.toString()
            };
            mDatabase.loadIds("SELECT notificationId FROM notification WHERE subject=? AND flags=0 AND descriptor=?",
                    params, deletedNotifications);

            transaction.delete(Tables.NOTIFICATION, "subject=? AND descriptor=?", params);
        } else if (twincodeId != null) {
            final String[] params = {
                    subjectId.toString(),
                    twincodeId.toString()
            };
            mDatabase.loadIds("SELECT notificationId FROM notification AS n"
                            + " INNER JOIN descriptor AS d ON n.descriptor=d.id"
                            + " WHERE n.subject=? AND n.flags=0 AND d.twincodeOutbound=?",
                    params, deletedNotifications);

            transaction.delete(Tables.NOTIFICATION, "id IN (SELECT n.id FROM notification AS n"
                            + " INNER JOIN descriptor AS d ON n.descriptor=d.id"
                            + " WHERE n.subject=? AND d.twincodeOutbound=?)", params);
        } else {
            final String[] params = {
                    subjectId.toString()
            };
            mDatabase.loadIds("SELECT notificationId FROM notification WHERE subject=? AND flags=0",
                    params, deletedNotifications);

            transaction.delete(Tables.NOTIFICATION, "subject=?", params);
        }

        if (!deletedNotifications.isEmpty()) {
            mService.notifyCanceled(deletedNotifications);
        }
    }

    //
    // Package scoped methods
    //

    @Nullable
    Notification loadNotification(@NonNull UUID notificationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadNotification: notificationId=" + notificationId);
        }

        QueryBuilder query = new QueryBuilder("n.id, n.notificationId, n.uuid, n.creationDate, n.type,"
                + " n.flags, n.subject, r.schemaId, n.descriptor, d.sequenceId, d.twincodeOutbound, a.peerTwincodeOutbound, a.kind, a.value"
                + " FROM notification AS n INNER JOIN repository AS r ON r.id=n.subject"
                + " LEFT JOIN descriptor AS d ON n.descriptor=d.id"
                + " LEFT JOIN annotation AS a ON n.type=17 AND a.descriptor=d.id AND a.notificationId=n.id AND a.kind=4");
        query.filterUUID("n.uuid", notificationId);
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
            if (!cursor.moveToFirst()) {

                return null;
            }

            final DatabaseIdentifier identifier = new DatabaseIdentifier(this, cursor.getLong(0));
            return createObject(identifier, cursor, 1);

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    @NonNull
    List<Notification> loadNotifications(@NonNull Filter<Notification> filter, long maxDescriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadNotifications maxDescriptors=" + maxDescriptors);
        }

        QueryBuilder query = new QueryBuilder("n.id, n.notificationId, n.uuid, n.creationDate, n.type,"
                + " n.flags, n.subject, r.schemaId, n.descriptor, d.sequenceId, d.twincodeOutbound, a.peerTwincodeOutbound, a.kind, a.value"
                + " FROM notification AS n INNER JOIN repository AS r ON n.subject=r.id"
                + " LEFT JOIN descriptor AS d ON n.descriptor=d.id"
                + " LEFT JOIN annotation AS a ON n.type=17 AND a.descriptor=d.id AND a.notificationId=n.id AND a.kind=4");
        query.filterBefore("n.creationDate", filter.before);
        query.filterOwner("r.owner", filter.owner);
        query.filterName("r.name", filter.name);
        query.filterTwincode("r.twincodeOutbound", filter.twincodeOutbound);
        query.order("n.creationDate DESC");
        query.limit(maxDescriptors);
        return loadNotificationsInternal(query, filter);
    }

    @NonNull
    List<Notification> loadPendingNotifications(@NonNull RepositoryObject subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadPendingNotifications subject=" + subject);
        }

        QueryBuilder query = new QueryBuilder("n.id, n.notificationId, n.uuid, n.creationDate,"
                + " n.type, n.flags, n.subject, r.schemaId, n.descriptor, d.sequenceId, d.twincodeOutbound, a.peerTwincodeOutbound, a.kind, a.value"
                + " FROM notification AS n INNER JOIN repository AS r ON n.subject=r.id"
                + " LEFT JOIN descriptor AS d ON n.descriptor=d.id"
                + " LEFT JOIN annotation AS a ON n.type=17 AND a.descriptor=d.id AND a.notificationId=n.id AND a.kind=4");
        query.filterOwner("n.subject", subject);
        query.filterInt("n.flags", 0);
        return loadNotificationsInternal(query, null);
    }

    @NonNull
    private List<Notification> loadNotificationsInternal(@NonNull QueryBuilder query, @Nullable Filter<Notification> filter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadNotificationsInternal");
        }

        final List<Notification> notifications = new ArrayList<>();
        final long now = System.currentTimeMillis();
        List<Long> toBeDeletedNotificationIds = null;
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
            while (cursor.moveToNext()) {
                final DatabaseIdentifier identifier = new DatabaseIdentifier(this, cursor.getLong(0));
                final NotificationImpl notification = createObject(identifier, cursor, 1);
                if (notification != null) {
                    if (filter == null || filter.accept(notification)) {
                        notifications.add(notification);
                    }
                } else {
                    if (toBeDeletedNotificationIds == null) {
                        toBeDeletedNotificationIds = new ArrayList<>();
                    }
                    toBeDeletedNotificationIds.add(identifier.getId());
                }
            }
        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }

        // There are some obsolete notifications: delete them.
        if (toBeDeletedNotificationIds != null) {
            try (Transaction transaction = newTransaction()) {
                transaction.deleteWithList(Tables.NOTIFICATION, toBeDeletedNotificationIds);
                transaction.commit();

            } catch (Exception exception) {
                mService.onDatabaseException(exception);
            }
        }

        EventMonitor.event("Load notifications " +  notifications.size(), now);
        return notifications;
    }

    @NonNull
    Map<UUID, NotificationStat> getNotificationStats() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotificationStats");
        }

        String query = "SELECT owner.uuid, SUM(CASE WHEN n.flags = 1 THEN 1 ELSE 0 END),"
                + " SUM(case WHEN n.flags != 1 THEN 1 ELSE 0 END) FROM notification AS n"
                + " INNER JOIN repository AS r ON n.subject=r.id LEFT JOIN repository AS owner ON r.owner=owner.id"
                + " GROUP BY owner.uuid";
        final Map<UUID, NotificationStat> result = new HashMap<>();
        try (DatabaseCursor cursor = mDatabase.rawQuery(query, null)) {
            while (cursor.moveToNext()) {
                UUID originatorId = cursor.getUUID(0);
                long ackCount = cursor.getLong(1);
                long pendingCount = cursor.getLong(2);

                if (originatorId != null) {
                    result.put(originatorId, new NotificationStat(ackCount, pendingCount));
                }
            }

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }


        return result;
    }

    @Nullable
    Notification createNotification(int sysId, @NonNull NotificationService.NotificationType type,
                                    @NonNull RepositoryObject subject,
                                    @Nullable DescriptorId descriptorId,
                                    @Nullable TwincodeOutbound annotatingUser) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createNotification: sysId=" + sysId + " type=" + type + " subject=" + subject
                    + " annotatingUser=" + annotatingUser);
        }

        try (Transaction transaction = newTransaction()) {

            AnnotationType annotationType = null;
            int annotationValue = 0;
            if (annotatingUser != null && descriptorId != null) {
                Long value = mDatabase.longQuery("SELECT value FROM annotation"
                        + " WHERE descriptor=? AND peerTwincodeOutbound=? AND kind=4", new Object[] {
                                descriptorId.id, annotatingUser.getDatabaseId().getId()
                });
                if (value != null) {
                    annotationValue = value.intValue();
                    annotationType = AnnotationType.LIKE;
                }
            }
            final long now = System.currentTimeMillis();
            final UUID uuid = UUID.randomUUID();
            final long notificationId = transaction.allocateId(DatabaseTable.TABLE_NOTIFICATION);
            final DatabaseIdentifier identifier = new DatabaseIdentifier(this, notificationId);

            final ContentValues values = new ContentValues();
            values.put(Columns.ID, notificationId);
            values.put(Columns.NOTIFICATION_ID, sysId);
            values.put(Columns.UUID, uuid.toString());
            values.put(Columns.TYPE, fromNotificationType(type));
            values.put(Columns.SUBJECT, subject.getDatabaseId().getId());
            values.put(Columns.CREATION_DATE, now);
            values.put(Columns.FLAGS, 0);
            if (descriptorId != null) {
                values.put(Columns.DESCRIPTOR, descriptorId.id);
            }
            transaction.insertOrThrow(Tables.NOTIFICATION, null, values);

            // Associate the LIKE annotation with the notification so that we can retrieve it.
            if (annotatingUser != null && descriptorId != null) {
                values.clear();
                values.put(Columns.NOTIFICATION_ID, notificationId);
                transaction.update(Tables.ANNOTATION, values,
                        "descriptor=? AND peerTwincodeOutbound=? AND kind=4",
                        new String[] {
                                Long.toString(descriptorId.id),
                                Long.toString(annotatingUser.getDatabaseId().getId())
                        });
            }
            transaction.commit();
            return new NotificationImpl(identifier, sysId, uuid, now, subject, type,
                    descriptorId,0, annotatingUser, annotationType, annotationValue);

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        return null;
    }

    void acknowledgeNotification(@NonNull NotificationImpl notification) {
        if (DEBUG) {
            Log.d(LOG_TAG, "acknowledgeNotification: notification=" + notification);
        }

        try (Transaction transaction = newTransaction()) {
            final ContentValues values = new ContentValues();
            notification.acknowledge();
            values.put(Columns.FLAGS, notification.getFlags());
            transaction.updateWithId(Tables.NOTIFICATION, values, notification.getDatabaseId().getId());
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void deleteNotifications(@NonNull RepositoryObject subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteNotifications: subject=" + subject);
        }

        try (Transaction transaction = newTransaction()) {
            deleteNotifications(transaction, subject.getDatabaseId().getId(), null, null);
            transaction.commit();

        } catch (Exception ex) {
            mService.onDatabaseException(ex);
        }
    }

    @Nullable
    private static NotificationService.NotificationType toNotificationType(int value) {
        switch (value) {
            case 0:
                return NotificationService.NotificationType.NEW_CONTACT;

            case 1:
                return NotificationService.NotificationType.UPDATED_CONTACT;

            case 2:
                return NotificationService.NotificationType.DELETED_CONTACT;

            case 3:
                return NotificationService.NotificationType.MISSED_AUDIO_CALL;

            case 4:
                return NotificationService.NotificationType.MISSED_VIDEO_CALL;

            case 5:
                return NotificationService.NotificationType.RESET_CONVERSATION;

            case 6:
                return NotificationService.NotificationType.NEW_TEXT_MESSAGE;

            case 7:
                return NotificationService.NotificationType.NEW_IMAGE_MESSAGE;

            case 8:
                return NotificationService.NotificationType.NEW_AUDIO_MESSAGE;

            case 9:
                return NotificationService.NotificationType.NEW_VIDEO_MESSAGE;

            case 10:
                return NotificationService.NotificationType.NEW_FILE_MESSAGE;

            case 11:
                return NotificationService.NotificationType.NEW_GROUP_INVITATION;

            case 12:
                return NotificationService.NotificationType.NEW_GROUP_JOINED;

            case 13:
                return NotificationService.NotificationType.DELETED_GROUP;

            case 14:
                return NotificationService.NotificationType.UPDATED_AVATAR_CONTACT;

            case 15:
                return NotificationService.NotificationType.NEW_GEOLOCATION;

            case 16:
                return NotificationService.NotificationType.NEW_CONTACT_INVITATION;

            case 17:
                return NotificationService.NotificationType.UPDATED_ANNOTATION;

            default:
                return null;
        }
    }

    private static int fromNotificationType(@NonNull NotificationService.NotificationType type) {

        switch (type) {
            case NEW_CONTACT:
                return 0;

            case UPDATED_CONTACT:
                return 1;

            case DELETED_CONTACT:
                return 2;

            case MISSED_AUDIO_CALL:
                return 3;

            case MISSED_VIDEO_CALL:
                return 4;

            case RESET_CONVERSATION:
                return 5;

            case NEW_TEXT_MESSAGE:
                return 6;

            case NEW_IMAGE_MESSAGE:
                return 7;

            case NEW_AUDIO_MESSAGE:
                return 8;

            case NEW_VIDEO_MESSAGE:
                return 9;

            case NEW_FILE_MESSAGE:
                return 10;

            case NEW_GROUP_INVITATION:
                return 11;

            case NEW_GROUP_JOINED:
                return 12;

            case DELETED_GROUP:
                return 13;

            case UPDATED_AVATAR_CONTACT:
                return 14;

            case NEW_GEOLOCATION:
                return 15;

            case NEW_CONTACT_INVITATION:
                return 16;

            case UPDATED_ANNOTATION:
                return 17;
        }

        return 0;
    }
}
