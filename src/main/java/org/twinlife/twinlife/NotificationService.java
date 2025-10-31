/*
 *  Copyright (c) 2017-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Chedi Baccari (Chedi.Baccari@twinlife-systems.com)
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@SuppressWarnings("unused")
public interface NotificationService extends BaseService<NotificationService.ServiceObserver> {

    String VERSION = "2.1.1";

    class NotificationServiceConfiguration extends BaseServiceConfiguration {

        public NotificationServiceConfiguration() {

            super(BaseServiceId.NOTIFICATION_SERVICE_ID, VERSION, false);
        }
    }

    enum NotificationType {
        NEW_CONTACT,
        UPDATED_CONTACT,
        UPDATED_AVATAR_CONTACT,
        DELETED_CONTACT,
        MISSED_AUDIO_CALL,
        MISSED_VIDEO_CALL,
        RESET_CONVERSATION,
        NEW_TEXT_MESSAGE,
        NEW_IMAGE_MESSAGE,
        NEW_AUDIO_MESSAGE,
        NEW_VIDEO_MESSAGE,
    	NEW_FILE_MESSAGE,
        NEW_GEOLOCATION,
        NEW_GROUP_INVITATION,
        NEW_GROUP_JOINED,
        NEW_CONTACT_INVITATION,
        DELETED_GROUP,
        UPDATED_ANNOTATION;

        private static final Set<NotificationType> MESSAGING_STYLE_NOTIFICATION_TYPES = Set.of(
                NotificationType.NEW_TEXT_MESSAGE,
                NotificationType.NEW_AUDIO_MESSAGE,
                NotificationType.NEW_IMAGE_MESSAGE,
                NotificationType.NEW_VIDEO_MESSAGE,
                NotificationType.NEW_FILE_MESSAGE,
                NotificationType.NEW_GEOLOCATION,
                NotificationType.UPDATED_ANNOTATION
        );

        public boolean isMessagingStyle() {
            return MESSAGING_STYLE_NOTIFICATION_TYPES.contains(this);
        }
    }

    class NotificationStat {

        public NotificationStat(long acknowledgedCount, long pendingCount) {
            this.acknowledgedCount = acknowledgedCount;
            this.pendingCount = pendingCount;
        }

        private long pendingCount;
        private long acknowledgedCount;

        public long getPendingCount() {

            return pendingCount;
        }

        public long getAcknowledgedCount() {

            return acknowledgedCount;
        }

        public void add(@NonNull final NotificationStat stat) {

            this.pendingCount += stat.pendingCount;
            this.acknowledgedCount += stat.acknowledgedCount;
        }
    }

    interface ServiceObserver extends BaseService.ServiceObserver {

        default void onCanceledNotifications(@NonNull List<Long> notificationIds) {}
    }

    /**
     * Get the notifications created before the given date.
     * The list is sorted on the creation date (newest first).
     *
     * @param filter the filter to apply on the list of notifications.
     * @param maxDescriptors the max number of descriptors.
     * @return the list of notifications.
     */
    @NonNull
    List<Notification> listNotifications(@NonNull Filter<Notification> filter, int maxDescriptors);

    /**
     * Get the active notifications associated with the given originator.
     *
     * @param subject the originator.
     * @return the list of notifications.
     */
    @NonNull
    List<Notification> getPendingNotifications(@NonNull RepositoryObject subject);

    /**
     * Get the notification with the given notification id.
     *
     * @param notificationId the notification id.
     * @return the notification or null if it was not found.
     */
    @Nullable
    Notification getNotification(@NonNull UUID notificationId);

    /**
     * Get for each originator the statistics about their notification.
     * It is intended to know if a given originator has some pending notifications.
     *
     * @return a map of the notification stats for each originator that has notifications.
     */
    @NonNull
    Map<UUID, NotificationStat> getNotificationStats();

    /**
     * Create a new notification with the given type for the subject.
     *
     * @param sysId the system assigned notification id.
     * @param type the notification type.
     * @param subject the subject associated with the notification.
     * @param annotatingUser the optional twincode indicating the user who put the annotation.
     * @return the new notification.
     */
    @Nullable
    Notification createNotification(int sysId, @NonNull NotificationType type, @NonNull RepositoryObject subject,
                                    @Nullable ConversationService.DescriptorId descriptorId,
                                    @Nullable TwincodeOutbound annotatingUser);

    /**
     * Acknowledge the notification.
     *
     * @param notification the notification to acknowledge.
     */
    void acknowledgeNotification(@NonNull Notification notification);

    /**
     * Delete the notification.
     *
     * @param notification the notification to delete.
     */
    void deleteNotification(@NonNull Notification notification);

    /**
     * Delete all the notifications associated with the given subject.
     *
     * @param subject the subject.
     */
    void deleteNotifications(@NonNull RepositoryObject subject);
}
