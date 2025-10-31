/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * Same as the ImageId with the UUID that can be used to export the image id in a twincode attribute.
 */
public class ExportedImageId extends ImageId {
    @NonNull
    private final UUID mPublicId;

    public ExportedImageId(long localId, @NonNull UUID publicId) {
        super(localId);
        mPublicId = publicId;
    }

    public ExportedImageId(@NonNull ImageId imageId, @NonNull UUID publicId) {
        super(imageId.getId());
        mPublicId = publicId;
    }

    @NonNull
    public UUID getExportedId() {

        return mPublicId;
    }

    @NonNull
    @Override
    public String toString() {

        return super.toString() + ":" + mPublicId;
    }
}
