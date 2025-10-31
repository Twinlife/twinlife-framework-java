/*
 *  Copyright (c) 2017-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Chedi Baccari (Christian.Jacquemot@twinlife-systems.com)
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.notification;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.Notification;
import org.twinlife.twinlife.NotificationService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeImpl;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class NotificationServiceImpl extends BaseServiceImpl<NotificationService.ServiceObserver> implements NotificationService {

    private static final String LOG_TAG = "NotificationServiceImpl";
    private static final boolean DEBUG = false;

    private final NotificationServiceProvider mServiceProvider;

    public NotificationServiceImpl(TwinlifeImpl twinlifeImpl, Connection connection) {
        super(twinlifeImpl, connection);

        setServiceConfiguration(new NotificationServiceConfiguration());

        mServiceProvider = new NotificationServiceProvider(this, twinlifeImpl.getDatabaseService());
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof NotificationServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        NotificationServiceConfiguration notificationServiceConfiguration = new NotificationServiceConfiguration();

        setServiceConfiguration(notificationServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    //
    // Implement NotificationService interface
    //

    @Override
    @NonNull
    public List<Notification> listNotifications(@NonNull Filter<Notification> filter, int maxDescriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotifications: filter=" + filter + " maxDescriptors=" + maxDescriptors);
        }

        return mServiceProvider.loadNotifications(filter, maxDescriptors);
    }

    @Override
    @NonNull
    public List<Notification> getPendingNotifications(@NonNull RepositoryObject subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPendingNotifications: subject=" + subject);
        }

        return mServiceProvider.loadPendingNotifications(subject);
    }

    @Override
    @Nullable
    public Notification getNotification(@NonNull UUID notificationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotification: notificationId=" + notificationId);
        }

        return mServiceProvider.loadNotification(notificationId);
    }

    @Override
    @NonNull
    public Map<UUID, NotificationStat> getNotificationStats() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotificationStats");
        }

        return mServiceProvider.getNotificationStats();
    }

    @Override
    @Nullable
    public Notification createNotification(int sysId, @NonNull NotificationType type, @NonNull RepositoryObject subject,
                                           @Nullable ConversationService.DescriptorId descriptorId,
                                           @Nullable TwincodeOutbound annotatingUser) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createNotification: sysId=" + sysId + " type=" + type + " subject=" + subject
                    + " descriptorId=" + descriptorId + " annotatingUser=" + annotatingUser);
        }

        if (!isServiceOn()) {

            return null;
        }

        return mServiceProvider.createNotification(sysId, type, subject, descriptorId, annotatingUser);
    }

    @Override
    public void acknowledgeNotification(@NonNull Notification notification) {
        if (DEBUG) {
            Log.d(LOG_TAG, "acknowledgeNotification: notification=" + notification);
        }

        if (!isServiceOn() || !(notification instanceof NotificationImpl)) {

            return;
        }

        mServiceProvider.acknowledgeNotification((NotificationImpl) notification);
    }

    @Override
    public void deleteNotification(@NonNull Notification notification) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteNotification: rnotification=" + notification);
        }

        if (!isServiceOn()) {

            return;
        }

        mServiceProvider.deleteObject(notification);
    }

    @Override
    public void deleteNotifications(@NonNull RepositoryObject subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteNotifications: subject=" + subject);
        }

        if (!isServiceOn()) {

            return;
        }

        mServiceProvider.deleteNotifications(subject);
    }

    void notifyCanceled(@NonNull List<Long> notificationIds) {
        if (DEBUG) {
            Log.d(LOG_TAG, "notifyCanceled: notificationIds=" + notificationIds);
        }

        for (NotificationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onCanceledNotifications(notificationIds));
        }
    }

    /**
     * Package private operation to give access to the NotificationServiceProvider to the NotificationDump.
     *
     * @return the NotificationServiceProvider instance.
     */
    NotificationServiceProvider getNotificationServiceProvider() {

        return mServiceProvider;
    }
}
