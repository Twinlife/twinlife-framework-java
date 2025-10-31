/*
 *  Copyright (c) 2020-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.exifinterface.media.ExifInterface;

import org.twinlife.twinlife.util.Utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class AndroidImageTools implements ImageTools {
    private static final String LOG_TAG = "AndroidImageTools";
    private static final boolean DEBUG = false;

    public static final int IMAGE_JPEG_QUALITY = 90;

    @Override
    public byte[] getImageData(@NonNull Bitmap image) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getImageData: image=" + image);
        }

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        if (image.hasAlpha()) {
            image.compress(Bitmap.CompressFormat.PNG, 0, byteArrayOutputStream);
        } else {
            image.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, byteArrayOutputStream);
        }
        return byteArrayOutputStream.toByteArray();
    }

    @Override
    public byte[] getFileImageData(@NonNull File sourcePath, int maxWidth, int maxHeight) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getImageData: sourcePath=" + sourcePath +
                    " maxWidth=" + maxWidth + " maxHeight=" + maxHeight);
        }

        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(sourcePath.getPath(), bmOptions);

        int width = bmOptions.outWidth;
        int height = bmOptions.outHeight;
        if (width > maxWidth || height > maxHeight) {
            // Decode the image file into a Bitmap and apply a scale factor while decoding.
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = calculateInSampleSize(bmOptions, maxWidth, maxHeight);

            int orientation = ExifInterface.ORIENTATION_NORMAL;

            try {
                ExifInterface exifInterface = new ExifInterface(sourcePath);
                orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } catch (Throwable exception) {
                Log.e(LOG_TAG, "ExifInterface sourcePath=" + sourcePath);
            }

            final Matrix matrix;
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix = new Matrix();
                    matrix.setScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix = new Matrix();
                    matrix.setRotate(180);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix = new Matrix();
                    matrix.setRotate(180);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix = new Matrix();
                    matrix.setRotate(90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix = new Matrix();
                    matrix.setRotate(90);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix = new Matrix();
                    matrix.setRotate(-90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix = new Matrix();
                    matrix.setRotate(-90);
                    break;
                default:
                    matrix = null;
                    break;
            }

            try (ByteArrayOutputStream out = new ByteArrayOutputStream(128 * 1024)) {
                Bitmap bitmap = BitmapFactory.decodeFile(sourcePath.getPath(), bmOptions);
                if (bitmap != null) {
                    if (matrix != null) {
                        Bitmap lBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                        bitmap.recycle();
                        bitmap = lBitmap;
                    }
                    bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out);
                    return out.toByteArray();
                } else {
                    return null;
                }

            } catch (Throwable exception) {
                // Catch a possible out of memory exception but also a failure to write on disk.
                Log.e(LOG_TAG, "Exception: " + exception);
                return null;
            }
        }

        try (FileInputStream fileStream = new FileInputStream(sourcePath)) {
            byte[] data = new byte[(int) sourcePath.length()];
            int size = fileStream.read(data);
            if (size != data.length) {
                return null;
            }

            return data;
        } catch (Exception exception) {

            return null;
        }
    }

    @Override
    public boolean copyImage(@NonNull File sourcePath, @NonNull File destinationPath,
                             int maxWidth, int maxHeight, boolean allowMove) throws IOException {
        if (DEBUG) {
            Log.d(LOG_TAG, "copyImage: sourcePath=" + sourcePath + " destinationPath=" + destinationPath +
                    " maxWidth=" + maxWidth + " maxHeight=" + maxHeight + " allowMove=" + allowMove);
        }

        File dirPath = destinationPath.getParentFile();
        if (dirPath != null && !dirPath.exists() && !dirPath.mkdirs()) {
            Log.e(LOG_TAG, "Cannot create " + dirPath);
        }

        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;

        BitmapFactory.decodeFile(sourcePath.getPath(), bmOptions);

        int width = bmOptions.outWidth;
        int height = bmOptions.outHeight;
        if (width > maxWidth || height > maxHeight) {
            // Decode the image file into a Bitmap and apply a scale factor while decoding.
            bmOptions.inJustDecodeBounds = false;
            bmOptions.inSampleSize = calculateInSampleSize(bmOptions, maxWidth, maxHeight);

            // Get the image that is a little bit bigger than expected and we will do a createScaledBitmap()
            // to get a better final resolution.
            if (bmOptions.inSampleSize > 1) {
                bmOptions.inSampleSize--;
            }

            int orientation = ExifInterface.ORIENTATION_NORMAL;

            try {
                ExifInterface exifInterface = new ExifInterface(sourcePath);
                orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            } catch (Throwable exception) {
                Log.e(LOG_TAG, "ExifInterface sourcePath=" + sourcePath);
            }

            final Matrix matrix;
            switch (orientation) {
                case ExifInterface.ORIENTATION_FLIP_HORIZONTAL:
                    matrix = new Matrix();
                    matrix.setScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    matrix = new Matrix();
                    matrix.setRotate(180);
                    break;
                case ExifInterface.ORIENTATION_FLIP_VERTICAL:
                    matrix = new Matrix();
                    matrix.setRotate(180);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_TRANSPOSE:
                    matrix = new Matrix();
                    matrix.setRotate(90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_90:
                    matrix = new Matrix();
                    matrix.setRotate(90);
                    break;
                case ExifInterface.ORIENTATION_TRANSVERSE:
                    matrix = new Matrix();
                    matrix.setRotate(-90);
                    matrix.postScale(-1, 1);
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    matrix = new Matrix();
                    matrix.setRotate(-90);
                    break;
                default:
                    matrix = null;
                    break;
            }

            try (FileOutputStream out = new FileOutputStream(destinationPath)) {
                Bitmap bitmap = BitmapFactory.decodeFile(sourcePath.getPath(), bmOptions);
                if (bitmap != null) {
                    float resizeScale;
                    if (bitmap.getWidth() > bitmap.getHeight()) {
                        resizeScale = maxWidth / (float) bitmap.getWidth();
                    } else {
                        resizeScale = maxHeight / (float) bitmap.getHeight();
                    }

                    // Rotate first (doing both didn't worked)
                    if (matrix != null) {
                        Bitmap lBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                        bitmap.recycle();
                        bitmap = lBitmap;
                    }
                    if (resizeScale < 1.0) {
                        Bitmap lBitmap = Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * resizeScale), (int) (bitmap.getHeight() * resizeScale), true);
                        bitmap.recycle();
                        bitmap = lBitmap;
                    }
                    bitmap.compress(Bitmap.CompressFormat.JPEG, IMAGE_JPEG_QUALITY, out);
                } else {
                    return false;
                }

            } catch (Throwable exception) {
                // Catch a possible out of memory exception but also a failure to write on disk.
                Log.e(LOG_TAG, "Exception: " + exception);
                destinationPath.delete();
                throw exception;
            }
            return true;
        }
        if (!allowMove || !sourcePath.renameTo(destinationPath)) {
            Utils.copyFile(sourcePath, destinationPath);
        }
        return false;
    }

    private static int calculateInSampleSize(@NonNull BitmapFactory.Options options, int maxWidth, int maxHeight) {

        final int width = options.outWidth;
        final int height = options.outHeight;
        int inSampleSize = 1;

        while ((width / inSampleSize) > maxHeight || (height / inSampleSize) > maxWidth) {
            inSampleSize++;
        }

        return inSampleSize;
    }
}
