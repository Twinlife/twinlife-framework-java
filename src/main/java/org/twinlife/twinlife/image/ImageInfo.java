/*
 *  Copyright (c) 2020-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.image;

import androidx.annotation.Nullable;

import java.util.UUID;

public class ImageInfo {

    public enum Status {
        LOCALE,     // Image is locale and not stored on the server (ex: Space settings).
        OWNER,      // Image is created by us.
        DELETED,    // Image is created by us and was deleted.
        REMOTE,     // Image is remote and available.
        MISSING,    // Image is remote but was not found.
        NEED_FETCH  // Image must be queried from the server.
    }

    public final UUID imageId;
    public final byte[] data;
    public final Status status;
    public final UUID copiedImageId;

    public ImageInfo(@Nullable UUID imageId, Status status, byte[] thumbnail, @Nullable UUID copiedImageId) {
        this.imageId = imageId;
        this.status = status;
        this.data = thumbnail;
        this.copiedImageId = copiedImageId;
    }
}
