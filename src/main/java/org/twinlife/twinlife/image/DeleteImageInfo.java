/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.image;

import androidx.annotation.NonNull;

import java.util.UUID;

class DeleteImageInfo {

    enum Status {
        DELETE_NONE,            // No action to perform.
        DELETE_REMOTE,          // Image must be deleted remotely.
        DELETE_LOCAL,           // Image must be deleted locally.
        DELETE_LOCAL_REMOTE     // Image must be deleted locally and remotely.
    }

    @NonNull
    final UUID publicId;
    final Status status;

    DeleteImageInfo(@NonNull UUID publicId, Status status) {
        this.publicId = publicId;
        this.status = status;
    }
}
