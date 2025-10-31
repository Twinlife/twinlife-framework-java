/*
 *  Copyright (c) 2020 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.image;

import androidx.annotation.NonNull;

import java.util.UUID;

class UploadInfo {

    final long imageId;
    final UUID imagePublicId;
    final long remainNormalImage;
    final long remainLargeImage;

    UploadInfo(long imageId, @NonNull UUID imagePublicId, long remainNormalImage, long remainLargeImage) {
        this.imageId = imageId;
        this.imagePublicId = imagePublicId;
        this.remainNormalImage = remainNormalImage;
        this.remainLargeImage = remainLargeImage;
    }
}
