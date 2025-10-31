/*
 *  Copyright (c) 2020-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.graphics.Bitmap;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.util.Map;
import java.util.UUID;

public interface ImageService {

    String VERSION = "2.0.3";

    // Maximum dimension for the normal image, above that we consider this is a large image.
    int NORMAL_IMAGE_WIDTH = 1280;
    int NORMAL_IMAGE_HEIGHT = 1280;
    int LARGE_IMAGE_WIDTH = 3000;
    int LARGE_IMAGE_HEIGHT = 3000;
    int LOCAL_IMAGE_WIDTH = 2048;  // Should not exceed 2048 for background usage.
    int LOCAL_IMAGE_HEIGHT = 2048;

    class ImageServiceConfiguration extends BaseService.BaseServiceConfiguration {

        public ImageServiceConfiguration() {

            super(BaseService.BaseServiceId.IMAGE_SERVICE_ID, VERSION, false);
        }
    }

    enum Kind {
        THUMBNAIL,
        NORMAL,
        LARGE
    }

    @AnyThread
    @Nullable
    Bitmap getCachedImage(@NonNull ImageId imageId, @NonNull ImageService.Kind kind);

    /**
     * Get the image identified by the imageId from the local cache.  If the image is not found
     * locally, it is necessary to call getImage and the image will be loaded asynchronously.
     *
     * @param imageId the image identifier.
     * @param kind the image to retrieve between thumbnail or normal.
     * @return the cached image bitmap or null.
     */
    @WorkerThread
    @Nullable
    Bitmap getImage(@NonNull ImageId imageId, @NonNull ImageService.Kind kind);

    /**
     * Get the image identified by the UUID and call the consumer onGet operation with it.
     * If the image is not found locally, download it from the server.
     * When the image info was not found, or its status is MISSING, the onGet() receives the ITEM_NOT_FOUND error and a null image.
     *
     * @param imageId the image identifier.
     * @param kind the image to retrieve between thumbnail or normal.
     * @param consumer the consumer handler.
     */
    void getImageFromServer(@NonNull ImageId imageId, @NonNull Kind kind, @NonNull Consumer<Bitmap> consumer);

    /**
     * Create an image identifier associated with the given image and its thumbnail.
     * The image can be retrieved through `getImage`.  Once the image is saved and an identifier
     * allocated, the consumer onGet operation is called with the new image identifier.
     * The image file is either moved to the target location or removed.
     *
     * @param imagePath the path to the file holding the image.
     * @param thumbnail the thumbnail image bitmap.
     * @param consumer the consumer handler.
     */
    void createImage(@Nullable File imagePath, @NonNull Bitmap thumbnail,
                     @NonNull Consumer<ExportedImageId> consumer);

    /**
     * Store locally an image that can be retrieved with getImage and the given image id.
     * The image file is either moved to the target location or removed.
     *
     * @param imagePath the path to the file holding the image.
     * @param thumbnail the thumbnail image bitmap.
     * @param consumer the consumer handler.
     */
    void createLocalImage(@Nullable File imagePath, @NonNull Bitmap thumbnail,
                          @NonNull Consumer<ExportedImageId> consumer);

    /**
     * Create a copy of an existing image.  Once the server has copied the image and allocated
     * a new image identifier, the consumer onGet operation is called with the new identifier.
     * When the image identified was not found, the onGet operation receives the ITEM_NOT_FOUND
     * error and a null identifier.
     *
     * @param imageId the image identifier.
     * @param consumer the consumer handler.
     */
    void copyImage(@NonNull ImageId imageId, @NonNull Consumer<ExportedImageId> consumer);

    /**
     * Delete the image identified by the imageId.  The image is first removed from the local
     * cache and if it was created by the current user it is also removed on the server.
     * The onGet operation is called when the image is removed.
     *
     * @param imageId the image identifier.
     * @param consumer the consumer handler.
     */
    void deleteImage(@NonNull ImageId imageId, @NonNull Consumer<ImageId> consumer);

    /**
     * Remove the image identified by the imageId from the local cache.
     *
     * @param imageId the image identifier.
     */
    void evictImage(@NonNull ImageId imageId);

    /**
     * Get a list of image IDs that have been copied by the device from an image we created.
     * The map is keyed with the UUID of the image copied with `copyImage` and indicates the
     * original image Id that was used for the copy.  It can be used to identify images
     * that are identical.  Note: this only works for the images we have created and copied ourselves.
     *
     * @return the map of copied image ids.
     */
    @NonNull
    Map<ImageId, ImageId> listCopiedImages();

    /**
     * Get the public image ID associated with the given image.
     *
     * @param imageId the image id (local).
     * @return the public image ID or null if the image does not exist.
     */
    @Nullable
    ExportedImageId getPublicImageId(@NonNull ImageId imageId);
    ExportedImageId getImageId(@NonNull UUID imageId);
}
