/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.TwincodeOutbound;

/**
 * Interface defined to cleanup a twincode.
 */
public interface TwincodesCleaner {

    /**
     * Delete the twincode from the database as well as the keys, secrets and image.
     *
     * @param transaction the transaction to use.
     * @param twincodeOutbound the twincode to remove.
     * @throws DatabaseException the database exception raised.
     */
    void deleteTwincode(@NonNull Transaction transaction, @NonNull TwincodeOutbound twincodeOutbound) throws DatabaseException;
}