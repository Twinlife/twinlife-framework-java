/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup.handlers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.backup.BackupHandler;
import org.twinlife.twinlife.backup.VerifyResult;
import org.twinlife.twinlife.image.ImageInfo;
import org.twinlife.twinlife.image.ImageServiceImpl;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

public class ImageHandler extends BackupHandler<ImageInfo> {
    private static final String LOG_TAG = "ImageHandler";
    private static final boolean DEBUG = false;

    public static final String RESTORE_DIR = "restore";

    public static final UUID SCHEMA_ID = UUID.fromString("78aa0d24-95ec-4cd1-8cd9-6d962ed9f80c");
    private static final int SCHEMA_VERSION = 1;
    @NonNull
    private final ImageServiceImpl mImageService;
    @NonNull
    private final File mRestoreDir;

    public ImageHandler(@NonNull ImageServiceImpl imageService, @NonNull File filesDir) {
        super();
        this.mImageService = imageService;
        this.mRestoreDir = new File(filesDir, RESTORE_DIR);
    }

    @Override
    protected void initDeserializers() {
        mRestorers.put(ImageRestorerV1.VERSION, new ImageRestorerV1());
    }

    @Override
    public void backup(@NonNull BinaryEncoder encoder) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "backup: encoder=" + encoder);
        }

        for (ImageId imageId : mImageService.getLocalImageInfos()) {
            ImageInfo imageInfo = mImageService.getImageInfo(imageId);

            if (imageInfo == null || imageInfo.imageId == null) {
                Log.w(LOG_TAG, "Invalid imageInfo for imageId " + imageId);
                continue;
            }

            byte[] normalImageData = mImageService.getLocalImageData(imageId);

            encoder.writeUUID(SCHEMA_ID);
            encoder.writeInt(SCHEMA_VERSION);
            encoder.writeUUID(imageInfo.imageId);
            encoder.writeEnum(statusToCode(imageInfo.status));
            encoder.writeOptionalBytes(imageInfo.data);
            encoder.writeOptionalBytes(normalImageData);

            if (DEBUG) {
                Log.d(LOG_TAG, "Backup image: " + imageId);
            }
        }
    }


    private class ImageRestorerV1 implements Restorer<ImageInfo> {
        static final int VERSION = 1;

        @Nullable
        @Override
        public ImageInfo restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException {
            mRestoreDir.mkdirs();

            UUID imageId = decoder.readUUID();
            ImageInfo.Status status = codeToStatus(decoder.readInt());
            byte[] thumbnailData = decoder.readOptionalBytes(null);
            byte[] normalImageData = decoder.readOptionalBytes(null);

            ExportedImageId exportedImageId;

            if (inPlace) {
                exportedImageId = mImageService.getImageId(imageId);

                if (exportedImageId != null) {
                    if (DEBUG) {
                        Log.d(LOG_TAG, "In place restore: image already exists: " + exportedImageId);
                    }

                    return mImageService.getImageInfo(exportedImageId);
                }
            }

            Bitmap thumbnail = null;

            if (thumbnailData != null && thumbnailData.length > 0) {
                thumbnail = BitmapFactory.decodeByteArray(thumbnailData, 0, thumbnailData.length);
            }

            File normalImage = null;
            if (normalImageData != null && normalImageData.length > 0) {
                normalImage = new File(mRestoreDir, imageId + ".img");
                try (FileOutputStream outputStream = new FileOutputStream(normalImage)) {
                    outputStream.write(normalImageData, 0, normalImageData.length);
                } catch (IOException e) {
                    throw new SerializerException(e);
                }
            } else {
                Log.w(LOG_TAG, "no full size image found for image " + imageId);
            }

            if (thumbnail == null) {
                Log.w(LOG_TAG, "no thumbnail found for image " + imageId);
            }

            exportedImageId = mImageService.restoreLocalImage(imageId, status == ImageInfo.Status.LOCALE, normalImage, thumbnail);

            if (exportedImageId == null) {
                throw new SerializerException("could not restore local image " + imageId);
            }

            ImageInfo imageInfo = mImageService.getImageInfo(exportedImageId);

            if (imageInfo == null) {
                throw new SerializerException("no ImageInfo found in DB for image " + imageId);
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "Restored image: " + imageId);
            }

            return imageInfo;
        }

        @NonNull
        @Override
        public VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException {
            if (DEBUG) {
                Log.d(LOG_TAG, "verify: decoder=" + decoder);
            }

            UUID imageId = decoder.readUUID();
            ImageInfo.Status status = codeToStatus(decoder.readInt());
            byte[] thumbnailData = decoder.readOptionalBytes(null);
            decoder.readOptionalBytes(null); //normalImageData

            ExportedImageId exportedImageId = mImageService.getImageId(imageId);

            if (exportedImageId != null) {
                ImageInfo imageInfo = mImageService.getImageInfo(exportedImageId);
                if (imageInfo != null) {
                    boolean modified = imageInfo.status != status || !Arrays.equals(imageInfo.data, thumbnailData);
                    return new VerifyResult.Present<>(imageInfo, modified);
                }
            }

            return new VerifyResult.Absent<>(imageId, ImageInfo.class);
        }
    }

    private static final Map<ImageInfo.Status, Integer> STATUS_CODES = Map.of(
            ImageInfo.Status.LOCALE, 0,
            ImageInfo.Status.OWNER, 1,
            ImageInfo.Status.DELETED, 2,
            ImageInfo.Status.REMOTE, 3,
            ImageInfo.Status.MISSING, 4,
            ImageInfo.Status.NEED_FETCH, 5
    );

    private int statusToCode(@NonNull ImageInfo.Status status) {
        Integer code = STATUS_CODES.get(status);
        if (code != null) {
            return code;
        }
        return -1;
    }

    @Nullable
    private ImageInfo.Status codeToStatus(int code) {
        for (Map.Entry<ImageInfo.Status, Integer> entry : STATUS_CODES.entrySet()) {
            if (entry.getValue() == code) {
                return entry.getKey();
            }
        }
        return null;
    }
}
