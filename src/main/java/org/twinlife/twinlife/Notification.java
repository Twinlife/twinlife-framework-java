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

import java.util.UUID;

public interface Notification extends DatabaseObject {
    int NO_NOTIFICATION_ID = 0;

    @NonNull
    NotificationService.NotificationType getNotificationType();

    @NonNull
    UUID getOriginatorId();

    @NonNull
    RepositoryObject getSubject();

    long getTimestamp();

    boolean isAcknowledged();

    @Nullable
    ConversationService.DescriptorId getDescriptorId();

    int getSystemNotificationId();

    @Nullable
    TwincodeOutbound getUser();

    @Nullable
    ConversationService.AnnotationType getAnnotationType();

    int getAnnotationValue();
}
