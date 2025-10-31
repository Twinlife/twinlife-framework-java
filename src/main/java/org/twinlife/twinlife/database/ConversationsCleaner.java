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
 * Interface defined to ask conversation cleanup from another service.
 */
public interface ConversationsCleaner {

    /**
     * Delete some conversation (and related data) with the given transaction (commit must be done by the caller).
     * By deleting the conversation, we also trigger deletion of notifications.
     *
     * @param transaction the transaction to use.
     * @param subjectId the subject id that identifis the conversation (Contact, Group).
     * @param twincodeId the optional twincode to delete only the conversation of the given sender.
     * @throws DatabaseException the database exception raised.
     */
    void deleteConversations(@NonNull Transaction transaction, @Nullable Long subjectId,
                             @Nullable Long twincodeId) throws DatabaseException;
}