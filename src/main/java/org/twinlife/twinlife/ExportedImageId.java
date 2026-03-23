/*
 *  Copyright (c) 2023-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.Objects;
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

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        ExportedImageId that = (ExportedImageId) o;
        return Objects.equals(mPublicId, that.mPublicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), mPublicId);
    }

    @NonNull
    @Override
    public String toString() {

        return super.toString() + ":" + mPublicId;
    }
}
