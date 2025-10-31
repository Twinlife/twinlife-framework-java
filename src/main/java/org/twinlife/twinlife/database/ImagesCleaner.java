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
import org.twinlife.twinlife.ImageId;

/**
 * Interface defined to cleanup an image when a service knows it is no longer referenced.
 */
public interface ImagesCleaner {

    /**
     * Delete the image from the database, remove it from the image cache and if the big image was downloaded
     * remove it from the file system.  It is called from the twincode outbound service when a twincode is removed.
     *
     * @param transaction the transaction to use.
     * @param imageId the image id to remove.
     * @throws DatabaseException the database exception raised.
     */
    void deleteImage(@NonNull Transaction transaction, @NonNull ImageId imageId) throws DatabaseException;
}