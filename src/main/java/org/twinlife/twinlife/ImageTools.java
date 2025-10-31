/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

/**
 * Image tool operations used by the ImageService to perform some image specific operations.
 * <p>
 * The ImageService has a default implementation for the ImageTools based on Android API but that API
 * is not suitable for good image manipulation (resizing is not of good quality).  The Java and Javascript engines
 * can provide specific implementations that are optimized for them and provide a better resizing quality.
 */
public interface ImageTools {

    /**
     * Get the image data in the best format suitable for the image (either PNG or JPEG).
     *
     * @param image the image bitmap.
     * @return the image data bytes that we can either save in the database or send to the server.
     */
    byte[] getImageData(@NonNull Bitmap image);

    /**
     * Get the image data in the best format after resizing the image if it is bigger than the maxWidth x maxHeight
     * dimension.  It is desirable that this operation removes any Exif sensitive information such as GPS coordinates.
     *
     * @param sourcePath the source image path.
     * @param maxWidth the maximum width.
     * @param maxHeight the maximum height.
     * @return null if there is a problem in reading or resizing or the image data.
     */
    byte[] getFileImageData(@NonNull File sourcePath, int maxWidth, int maxHeight);

    /**
     * Copy and make a clean image before copying it to the server.  The target image must be resized if it
     * is bigger than the maxWidth x maxHeight dimensions.  It is desirable that this operation removes any
     * Exif sensitive information such as GPS coordinates.
     * <p>
     * If the copy fails, we expect some exception to be raised.
     *
     * @param sourcePath the source image path.
     * @param destinationPath the target image path.
     * @param maxWidth the maximum width.
     * @param maxHeight the maximum height.
     * @param allowMove allow to move the source path to the destination if the image fits the max width and height.
     * @return true if the image was scaled and false otherwise.
     */
    boolean copyImage(@NonNull File sourcePath, @NonNull File destinationPath,
                      int maxWidth, int maxHeight, boolean allowMove) throws IOException;
 }
