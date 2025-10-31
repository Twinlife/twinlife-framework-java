/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

/**
 * The ImageId holds a unique local identifier to reference an image.  When a twincode is received with an
 * "avatarId", it is associated with an ImageId to reference the twincode image.  The "avatarId" is no longer
 * accessible directly from the twincode.  When we want to create an image, an ExternalImageId is provided
 * which indicates both the unique local identifier and the exported UUID which can be used for the "avatarId"
 * twincode attribute.
 */
public class ImageId {
    private final long mLocalId;

    public ImageId(long localId) {

        mLocalId = localId;
    }

    public long getId() {

        return mLocalId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        // object can be an ExternalImageId and they are equals.
        if (!(object instanceof ImageId)) {
            return false;
        }

        ImageId second = (ImageId) object;
        return mLocalId == second.mLocalId;
    }

    @Override
    public int hashCode() {

        return (int) mLocalId;
    }

    @NonNull
    @Override
    public String toString() {

        return "IMG-" + mLocalId;
    }
}
