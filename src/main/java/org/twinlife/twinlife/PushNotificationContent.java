/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife;

public class PushNotificationContent {
    public PushNotificationPriority priority;
    public PushNotificationOperation operation;
    public int timeToLive;
    public long oldestTimestamp;
    public long newestTimestamp;
    public long estimatedSize;
    public long operationCount;
    public boolean synchronizeOp;
    public static final int DEFAULT_TIME_TO_LIVE = 25 * 1000; // ms

    public PushNotificationContent() {

        priority = PushNotificationPriority.NOT_DEFINED;
        operation = PushNotificationOperation.NOT_DEFINED;
        timeToLive = DEFAULT_TIME_TO_LIVE;
        oldestTimestamp = 0;
        newestTimestamp = 0;
        estimatedSize = 0;
        operationCount = 0;
        synchronizeOp = false;
    }

    public PushNotificationContent(PushNotificationPriority priority, PushNotificationOperation operation) {

        this.priority = priority;
        this.operation = operation;
        oldestTimestamp = 0;
        newestTimestamp = 0;
        estimatedSize = 0;
        operationCount = 0;
        timeToLive = DEFAULT_TIME_TO_LIVE;
        synchronizeOp = false;
    }
}
