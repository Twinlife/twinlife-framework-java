/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.DatabaseException;

/**
 * Interface defined to ask notification cleanup from another service.
 */
public interface NotificationsCleaner {

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
    void deleteNotifications(@NonNull Transaction transaction, @NonNull Long subjectId,
                             @Nullable Long twincodeId, @Nullable Long descriptorId) throws DatabaseException;
}