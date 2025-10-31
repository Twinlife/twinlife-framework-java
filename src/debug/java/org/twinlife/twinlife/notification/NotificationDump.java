/*
 *  Copyright (c) 2018 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.notification;

import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.Notification;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.debug.DebugServiceImpl;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Produce a dump of notifications found in the database.
 */
public class NotificationDump implements DebugServiceImpl.DumpListGenerator {

    private final TwinlifeImpl mTtwinlifeImpl;

    public NotificationDump(TwinlifeImpl twinlifeImpl) {

        mTtwinlifeImpl = twinlifeImpl;
    }

    /**
     * Produce the dump.
     *
     * @return A list of row/colums representing the dump.
     */
    @Override
    public List<String[]> getDump() {

        final NotificationServiceImpl notificationService = mTtwinlifeImpl.getNotificationServiceImpl();
        final Filter filter = new Filter(null);
        final List<Notification> list = notificationService.getNotificationServiceProvider().loadNotifications(filter, 1000);
        final List<String[]> result = new ArrayList<>();

        final long now = System.currentTimeMillis();
        result.add(new String[]{ "Contact", "ID", "Type", "Time" });
        for (final Notification notification : list) {

            final String type = notification.getNotificationType().toString();
            final String[] row = {
                    Utils.toLog(notification.getOriginatorId()),
                    Utils.toLog(notification.getId()),
                    type.length() > 14 ? type.substring(0, 14) : type,
                    EventMonitor.formatDuration(now - notification.getTimestamp())
            };
            result.add(row);
        }

        return result;
    }
}
