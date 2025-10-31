/*
 *  Copyright (c) 2020-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.image;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.AnyThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.ImageTools;
import org.twinlife.twinlife.JobService;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ.BinaryPacketIQSerializer;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.SerializerFactoryImpl;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the ImageService.
 */
public class ImageServiceImpl extends BaseServiceImpl<BaseService.ServiceObserver> implements ImageService {
    private static final String LOG_TAG = "ImageServiceImpl";
    private static final boolean DEBUG = false;

    private static final UUID COPY_IMAGE_SCHEMA_ID = UUID.fromString("6c2a932e-3dc6-47f2-b253-6975818d3a3c");
    private static final UUID CREATE_IMAGE_SCHEMA_ID = UUID.fromString("ea6b4372-3c7d-4ce8-92d8-87a589906a01");
    private static final UUID DELETE_IMAGE_SCHEMA_ID = UUID.fromString("22a99e04-6485-4808-9f08-4e421e2e5241");
    private static final UUID GET_IMAGE_SCHEMA_ID = UUID.fromString("3a9ca7c4-6153-426d-b716-d81fd625293c");
    private static final UUID PUT_IMAGE_SCHEMA_ID = UUID.fromString("6e0db5e2-318a-4a78-8162-ad88c6ae4b07");

    private static final UUID ON_COPY_IMAGE_SCHEMA_ID = UUID.fromString("9fe6e706-2442-455b-8c7e-384d371560c1");
    private static final UUID ON_CREATE_IMAGE_SCHEMA_ID = UUID.fromString("dfb67bd7-2e6a-4fd0-b05d-b34b916ea6cf");
    private static final UUID ON_DELETE_IMAGE_SCHEMA_ID = UUID.fromString("9e2f9bb9-b614-4674-b3a6-0474aefa961f");
    private static final UUID ON_GET_IMAGE_SCHEMA_ID = UUID.fromString("9ec1280e-a298-4c8b-b0fd-35383f7b5424");
    private static final UUID ON_PUT_IMAGE_SCHEMA_ID = UUID.fromString("f48fa894-a200-4aa8-a7d4-22ea21cfd008");

    private static final BinaryPacketIQSerializer IQ_COPY_IMAGE_SERIALIZER = CopyImageIQ.createSerializer(COPY_IMAGE_SCHEMA_ID, 2);
    private static final BinaryPacketIQSerializer IQ_CREATE_IMAGE_SERIALIZER = CreateImageIQ.createSerializer(CREATE_IMAGE_SCHEMA_ID, 2);
    private static final BinaryPacketIQSerializer IQ_DELETE_IMAGE_SERIALIZER = DeleteImageIQ.createSerializer(DELETE_IMAGE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_GET_IMAGE_SERIALIZER = GetImageIQ.createSerializer(GET_IMAGE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_PUT_IMAGE_SERIALIZER = PutImageIQ.createSerializer(PUT_IMAGE_SCHEMA_ID, 1);

    private static final BinaryPacketIQSerializer IQ_ON_COPY_IMAGE_SERIALIZER = OnCopyImageIQ.createSerializer(ON_COPY_IMAGE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_CREATE_IMAGE_SERIALIZER = OnCreateImageIQ.createSerializer(ON_CREATE_IMAGE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_DELETE_IMAGE_SERIALIZER = OnDeleteImageIQ.createSerializer(ON_DELETE_IMAGE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_GET_IMAGE_SERIALIZER = OnGetImageIQ.createSerializer(ON_GET_IMAGE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_PUT_IMAGE_SERIALIZER = OnPutImageIQ.createSerializer(ON_PUT_IMAGE_SCHEMA_ID, 1);

    // Send 2 PutImageIQ and queue the others until we get a response and then proceed with sending more.
    // - if the value is too big, this delays the execution of other operations (creation and update of twincode),
    // - if the value is too small (min is 1), sending the image will take more time.
    // 2 seems to give a good balance between the two.
    private static final int MAX_SEND_IMAGE_IQ = 2;

    static class PendingRequest {

    }

    static class GetImagePendingRequest extends PendingRequest {
        @NonNull
        final ImageId imageId;
        @NonNull
        final UUID imagePublicId;
        @NonNull
        final Kind kind;
        @NonNull
        final ImageInfo.Status status;
        @Nullable
        byte[] imageReceived;
        @NonNull
        final Consumer<Bitmap> consumer;
        GetImagePendingRequest nextRequest;

        GetImagePendingRequest(@NonNull ImageId imageId, @NonNull UUID imagePublicId,
                               @NonNull Kind kind, @NonNull Consumer<Bitmap> consumer) {

            this.imageId = imageId;
            this.imagePublicId = imagePublicId;
            this.kind = kind;
            this.status = ImageInfo.Status.REMOTE;
            this.consumer = consumer;
            this.nextRequest = null;
        }

        void dispatch(@NonNull ErrorCode errorCode, @Nullable Bitmap image) {
            GetImagePendingRequest request = this;
            do {
                request.consumer.onGet(errorCode, image);
                request = request.nextRequest;
            } while (request != null);
        }
    }

    static final class CreateImagePendingRequest extends PendingRequest {
        @NonNull
        final Consumer<ExportedImageId> consumer;
        @Nullable
        final File imagePath;
        @Nullable
        final File imageLargePath;
        @NonNull
        final byte[] thumbnailData;
        @NonNull
        final byte[] imageShas;
        final long total1Length;
        final long total2Length;

        CreateImagePendingRequest(@Nullable File imagePath, @Nullable File imageLargePath, @NonNull byte[] thumbnailData,
                                  @NonNull byte[] thumbnailSha, @Nullable byte[] imageSha, @Nullable byte[] largeImageSha,
                                  long total1Length, long total2Length, @NonNull Consumer<ExportedImageId> consumer) {
            this.imagePath = imagePath;
            this.imageLargePath = imageLargePath;
            this.thumbnailData = thumbnailData;
            this.total1Length = total1Length;
            this.total2Length = total2Length;
            this.consumer = consumer;

            int len = thumbnailSha.length + (imageSha == null ? 0 : imageSha.length) + (largeImageSha == null ? 0 : largeImageSha.length);
            this.imageShas = new byte[len];
            System.arraycopy(thumbnailSha, 0, this.imageShas, 0, thumbnailSha.length);
            if (imageSha != null) {
                System.arraycopy(imageSha, 0, this.imageShas, thumbnailSha.length, imageSha.length);
                if (largeImageSha != null) {
                    System.arraycopy(largeImageSha, 0, this.imageShas, thumbnailSha.length + imageSha.length, largeImageSha.length);
                }
            }
        }
    }

    static final class CopyImagePendingRequest extends PendingRequest {
        @NonNull
        final ImageId imageId;
        @NonNull
        final Consumer<ExportedImageId> consumer;

        CopyImagePendingRequest(@NonNull ImageId imageId, @NonNull Consumer<ExportedImageId> consumer) {

            this.imageId = imageId;
            this.consumer = consumer;
        }
    }

    static final class DeleteImagePendingRequest extends PendingRequest {
        @NonNull
        final ImageId imageId;
        @NonNull
        final UUID imagePublicId;
        @NonNull
        final Consumer<ImageId> consumer;

        DeleteImagePendingRequest(@NonNull ImageId imageId, @NonNull UUID imagePublicId, @NonNull Consumer<ImageId> consumer) {

            this.imageId = imageId;
            this.imagePublicId = imagePublicId;
            this.consumer = consumer;
        }
    }

    static final class UploadImagePendingRequest extends PendingRequest {
        @NonNull
        final ImageId imageId;
        @NonNull
        final Kind kind;
        final long length;
        final List<PutImageIQ> queue;
        long sendCount;

        UploadImagePendingRequest(@NonNull ImageId imageId, @NonNull Kind kind, long length) {

            this.imageId = imageId;
            this.kind = kind;
            this.length = length;
            this.queue = new ArrayList<>();
            this.sendCount = 0;
        }
    }

    private final ImageServiceProvider mServiceProvider;
    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, PendingRequest> mPendingRequests = new HashMap<>();
    private final File mCacheDir;
    private final File mLocalImagesDir;
    private final long mMaxImageSize = 4 * 1024 * 1024; // 4Mb PNG/JPG file max

    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_LARGE_ENTRIES = 4;
    private static final String CACHE_NAME = "images";

    @NonNull
    private final LruCache<ImageId, WeakReference<Bitmap>> mImageCache = new LruCache<>(MAX_LARGE_ENTRIES);
    @NonNull
    private final LruCache<ImageId, WeakReference<Bitmap>> mThumbnailCache = new LruCache<>(MAX_ENTRIES);
    @NonNull
    private final ImageTools mImageTools;
    private JobService.Job mUploadJob;
    private int mUploadChunkSize;
    private boolean mCheckUpload;
    @Nullable
    private UploadImagePendingRequest mUploadRequest;

    public ImageServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection,
                            @NonNull ImageTools imageTools) {

        super(twinlifeImpl, connection);

        setServiceConfiguration(new ImageServiceConfiguration());
        mServiceProvider = new ImageServiceProvider(this, twinlifeImpl.getDatabaseService());
        mCacheDir = new File(twinlifeImpl.getCacheDir(), CACHE_NAME);
        mLocalImagesDir = new File(twinlifeImpl.getFilesDir(), Twinlife.LOCAL_IMAGES_DIR);
        mImageTools = imageTools;
        mUploadChunkSize = DEFAULT_CHUNK_SIZE;
        mCheckUpload = true;

        SerializerFactoryImpl serializerFactory = mTwinlifeImpl.getSerializerFactoryImpl();
        serializerFactory.addSerializer(IQ_COPY_IMAGE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_COPY_IMAGE_SERIALIZER);
        serializerFactory.addSerializer(IQ_GET_IMAGE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_GET_IMAGE_SERIALIZER);
        serializerFactory.addSerializer(IQ_COPY_IMAGE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_COPY_IMAGE_SERIALIZER);
        serializerFactory.addSerializer(IQ_DELETE_IMAGE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_DELETE_IMAGE_SERIALIZER);
        serializerFactory.addSerializer(IQ_PUT_IMAGE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_PUT_IMAGE_SERIALIZER);

        // Register the binary IQ handlers for the responses.
        connection.addPacketListener(IQ_ON_CREATE_IMAGE_SERIALIZER, this::onCreateImage);
        connection.addPacketListener(IQ_ON_GET_IMAGE_SERIALIZER, this::onGetImage);
        connection.addPacketListener(IQ_ON_COPY_IMAGE_SERIALIZER, this::onCopyImage);
        connection.addPacketListener(IQ_ON_DELETE_IMAGE_SERIALIZER, this::onDeleteImage);
        connection.addPacketListener(IQ_ON_PUT_IMAGE_SERIALIZER, this::onPutImage);
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void addServiceObserver(@NonNull BaseService.ServiceObserver serviceObserver) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addServiceObserver serviceObserver=" + serviceObserver);
        }
    }

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof ImageServiceConfiguration)) {
            setConfigured(false);

            return;
        }
        ImageServiceConfiguration imageServiceConfiguration = new ImageServiceConfiguration();

        setServiceConfiguration(imageServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    @Override
    public void onSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignIn");
        }

        super.onSignIn();

        if (mCheckUpload) {
            if (mUploadJob != null) {
                mUploadJob.cancel();
            }
            mUploadJob = mJobService.scheduleJob("uploadImages", this::backgroundUpload, JobService.Priority.UPDATE);
        }
    }

    @AnyThread
    @Nullable
    @Override
    public Bitmap getCachedImage(@NonNull ImageId imageId, @NonNull Kind kind) {
        WeakReference<Bitmap> cachedImage;
        if (kind == Kind.THUMBNAIL) {
            cachedImage = mThumbnailCache.get(imageId);
        } else {
            cachedImage = mImageCache.get(imageId);
        }

        if (cachedImage != null) {
            return cachedImage.get();
        }

        return null;
    }
    /**
     * Get the image identified by the UUID from the local cache.  If the image is not found
     * locally, it is necessary to call getImage and the image will be loaded asynchronously.
     *
     * @param imageId the image identifier.
     * @param kind    the image to retrieve between thumbnail or normal.
     * @return the cached image bitmap or null.
     */
    @WorkerThread
    @Nullable
    @Override
    public Bitmap getImage(@NonNull ImageId imageId, @NonNull ImageService.Kind kind) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getImage: imageId=" + imageId + " kind=" + kind);
        }

        // Look at the cache first if the image was already loaded.
        final Bitmap cachedImage = getCachedImage(imageId, kind);
        if (cachedImage != null) {
            return cachedImage;
        }

        final ImageInfo info = mServiceProvider.loadImage(imageId);
        if (info == null) {
            return null;
        }

        if (kind == Kind.THUMBNAIL) {
            // Look in the database if the image was already loaded.
            if (info.data != null) {
                Bitmap image = BitmapFactory.decodeByteArray(info.data, 0, info.data.length);
                mThumbnailCache.put(imageId, new WeakReference<>(image));
                return image;
            }
        } else {
            // Look in the database if the image is known.
            if (info.status == ImageInfo.Status.MISSING) {
                return null;
            }

            final File imagePath;
            final UUID uuid = info.copiedImageId != null ? info.copiedImageId : info.imageId;
            if (info.status == ImageInfo.Status.LOCALE) {
                imagePath = getLocalImagePath(uuid);
                if (!imagePath.exists()) {
                    // Old versions were saving local images in the Cache, check if the local image
                    // was present in the cache and move it to the new location.
                    File oldImagePath = getCachedImagePath(info.imageId, kind);
                    if (!oldImagePath.exists()) {
                        return null;
                    }
                    if (!mLocalImagesDir.exists() && mLocalImagesDir.mkdirs()) {
                        Logger.warn("image", "creation of " + mLocalImagesDir + " failed");
                    }
                    if (!oldImagePath.renameTo(imagePath)) {
                        Logger.warn("image", "cannot move image");
                        return null;
                    }
                }
            } else {
                imagePath = getCachedImagePath(uuid, kind);
                if (!imagePath.exists()) {
                    return null;
                }
            }
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = false;
                Bitmap image = BitmapFactory.decodeFile(imagePath.getPath(), opts);
                if (image != null) {
                    mImageCache.put(imageId, new WeakReference<>(image));
                    return image;
                }
            } catch (Throwable throwable) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "getCachedImage", throwable);
                }

                // the image cannot be decoded on this device - return thumbnail if available
                return getImage(imageId, Kind.THUMBNAIL);
            }
        }
        return null;
    }

    /**
     * Get the image identified by the UUID and call the consumer onGet operation with it.
     * If the image is not found locally, download it from the server.
     * When the image info was not found, or its status is MISSING, the onGet() receives the ITEM_NOT_FOUND error and a null image.
     *
     * @param imageId  the image identifier.
     * @param kind     the image to retrieve between thumbnail or normal.
     * @param consumer the consumer handler.
     */
    @Override
    public void getImageFromServer(@NonNull ImageId imageId, @NonNull ImageService.Kind kind, @NonNull Consumer<Bitmap> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getImage: imageId=" + imageId + " kind=" + kind);
        }

        // Look at the cache first if the image was already loaded.
        final Bitmap cachedImage = getCachedImage(imageId, kind);
        if (cachedImage != null) {
            consumer.onGet(ErrorCode.SUCCESS, cachedImage);
            return;
        }

        final ImageInfo info = mServiceProvider.loadImage(imageId);
        if (info == null) {
            consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        if (kind == Kind.THUMBNAIL) {
            // Look in the database if the image was already loaded.
            if (info.data != null) {
                Bitmap image = BitmapFactory.decodeByteArray(info.data, 0, info.data.length);
                if (image != null) {
                    mThumbnailCache.put(imageId, new WeakReference<>(image));
                    consumer.onGet(ErrorCode.SUCCESS, image);
                    return;
                }
                consumer.onGet(ErrorCode.NO_STORAGE_SPACE, null);
                return;
            }

        } else {
            if (info.status == ImageInfo.Status.MISSING) {
                consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }

            File imagePath;
            final UUID uuid = info.copiedImageId != null ? info.copiedImageId : info.imageId;
            if (info.status == ImageInfo.Status.LOCALE) {
                imagePath = getLocalImagePath(uuid);
                if (!imagePath.exists()) {
                    // Old versions were saving local images in the Cache, check if the local image
                    // was present in the cache and move it to the new location.
                    File oldImagePath = getCachedImagePath(info.imageId, kind);
                    if (!oldImagePath.exists()) {
                        consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                        return;
                    }
                    if (!mLocalImagesDir.exists() && mLocalImagesDir.mkdirs()) {
                        Logger.warn("image", "creation of " + mLocalImagesDir + " failed");
                    }
                    if (!oldImagePath.renameTo(imagePath)) {
                        Logger.warn("image", "cannot move image");
                        consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                        return;
                    }
                }
            } else {
                imagePath = getCachedImagePath(uuid, kind);
                // If the large image does not exist, try to look for the normal image size.
                if (kind == Kind.LARGE && !imagePath.exists()) {
                    imagePath = getCachedImagePath(uuid, Kind.NORMAL);
                }
            }
            if (imagePath.exists()) {
                try {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = false;
                    Bitmap image = BitmapFactory.decodeFile(imagePath.getPath(), opts);
                    if (image != null) {
                        mImageCache.put(imageId, new WeakReference<>(image));
                        consumer.onGet(ErrorCode.SUCCESS, image);
                        return;
                    }
                } catch (Throwable throwable) {
                    if (Logger.ERROR) {
                        Logger.error(LOG_TAG, "getCachedImage", throwable);
                    }

                    // the image cannot be decoded on this device - return thumbnail if available
                    getImageFromServer(imageId, Kind.THUMBNAIL, consumer);
                    return;
                }
            }
        }

        // Get the image from the server.
        long requestId = newRequestId();
        GetImageIQ getImageIQ = new GetImageIQ(IQ_GET_IMAGE_SERIALIZER, requestId, info.imageId, kind);

        GetImagePendingRequest request = new GetImagePendingRequest(imageId, info.imageId, kind, consumer);
        synchronized (mPendingRequests) {
            // Look for the pending requests and if we are already asking for this same image
            // don't make a new request to the server but keep it in the chain.
            for (PendingRequest pendingRequest : mPendingRequests.values()) {
                if (pendingRequest instanceof GetImagePendingRequest) {
                    GetImagePendingRequest imagePendingRequest = (GetImagePendingRequest) pendingRequest;
                    if (imagePendingRequest.kind == kind && imageId.equals(imagePendingRequest.imageId)) {
                        request.nextRequest = imagePendingRequest.nextRequest;
                        imagePendingRequest.nextRequest = request;
                        return;
                    }
                }
            }
            mPendingRequests.put(requestId, request);
        }
        sendDataPacket(getImageIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Create an image identifier associated with the given image and its thumbnail.
     * The image can be retrieved through `getImage`.  Once the image is saved and an identifier
     * allocated, the consumer onGet operation is called with the new image identifier.
     *
     * @param imagePath the path to the file holding the image.
     * @param thumbnail the thumbnail image bitmap.
     * @param consumer  the consumer handler.
     */
    @Override
    public void createImage(@Nullable File imagePath, @NonNull Bitmap thumbnail, @NonNull Consumer<ExportedImageId> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createImage: imagePath=" + imagePath + " thumbnail=" + thumbnail);
        }

        File largeFile = null;
        File normalFile = null;

        if (imagePath != null) {
            ExportedImageId tempId = new ExportedImageId(0, UUID.randomUUID());
            normalFile = getCachedImagePath(tempId.getExportedId(), Kind.NORMAL);
            boolean scaled = false;
            try {
                scaled = mImageTools.copyImage(imagePath, normalFile, NORMAL_IMAGE_WIDTH, NORMAL_IMAGE_HEIGHT, false);
                if (normalFile.length() > mMaxImageSize) {
                    mTwinlifeImpl.assertion(ImageAssertPoint.COPY_IMAGE, AssertPoint.createLength(normalFile.length()));

                    Utils.deleteFile(LOG_TAG, normalFile);
                    normalFile = null;
                }
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "copyImage 1", exception);
                }
                normalFile = null;
            }
            if (scaled && normalFile != null && imagePath.length() < mMaxImageSize) {
                try {
                    largeFile = getCachedImagePath(tempId.getExportedId(), Kind.LARGE);
                    mImageTools.copyImage(imagePath, largeFile, LARGE_IMAGE_WIDTH, LARGE_IMAGE_HEIGHT, false);
                    if (largeFile.length() > mMaxImageSize) {
                        mTwinlifeImpl.assertion(ImageAssertPoint.COPY_IMAGE, AssertPoint.createLength(largeFile.length()));

                        Utils.deleteFile(LOG_TAG, largeFile);
                        largeFile = null;
                    }
                } catch (Exception exception) {
                    if (Logger.ERROR) {
                        Logger.error(LOG_TAG, "copyImage 2", exception);
                    }
                    largeFile = null;
                }
            }
        }

        byte[] imageSha;
        byte[] largeImageSha;
        long total1Length;
        long total2Length;

        if (normalFile != null) {
            imageSha = computeFileSHA256(normalFile);
            total1Length = normalFile.length();
        } else {
            imageSha = null;
            total1Length = 0;
        }

        if (largeFile != null) {
            largeImageSha = computeFileSHA256(largeFile);
            total2Length = largeFile.length();
        } else {
            largeImageSha = null;
            total2Length = 0;
        }

        byte[] thumbnailData = mImageTools.getImageData(thumbnail);
        byte[] thumbnailSha = computeSHA256(thumbnailData);
        if (thumbnailSha == null) {
            // Delete the image file when the creation failed.
            if (imagePath != null) {
                Utils.deleteFile("image", imagePath);
            }
            consumer.onGet(ErrorCode.NO_STORAGE_SPACE, null);
            return;
        }

        // Create the image on the server.
        long requestId = newRequestId();
        CreateImageIQ createImageIQ = new CreateImageIQ(IQ_CREATE_IMAGE_SERIALIZER, requestId, thumbnailSha, imageSha, largeImageSha, thumbnailData);

        CreateImagePendingRequest request = new CreateImagePendingRequest(normalFile, largeFile, thumbnailData,
                thumbnailSha, imageSha, largeImageSha, total1Length, total2Length, consumer);
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, request);
        }
        sendDataPacket(createImageIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Store locally an image that can be retrieved with getImage and the given image id.
     *
     * @param imagePath the path to the file holding the image.
     * @param thumbnail the thumbnail image bitmap.
     * @param consumer  the consumer handler.
     */
    @Override
    public void createLocalImage(@Nullable File imagePath, @NonNull Bitmap thumbnail, @NonNull Consumer<ExportedImageId> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createImage: imagePath=" + imagePath + " thumbnail=" + thumbnail);
        }

        final byte[] thumbnailData = mImageTools.getImageData(thumbnail);

        final ExportedImageId imageId = mServiceProvider.createImage(UUID.randomUUID(), true, thumbnailData,
                new byte[] {}, 0, 0);
        if (imageId == null) {
            // Delete the image file when the creation failed.
            if (imagePath != null) {
                Utils.deleteFile("image", imagePath);
            }
            consumer.onGet(ErrorCode.NO_STORAGE_SPACE, null);
        } else {
            if (imagePath != null) {
                final File saveImagePath = getLocalImagePath(imageId.getExportedId());
                try {
                    mImageTools.copyImage(imagePath, saveImagePath, LOCAL_IMAGE_WIDTH, LOCAL_IMAGE_HEIGHT, true);

                } catch (Exception exception) {
                    if (Logger.ERROR) {
                        Logger.error(LOG_TAG, "createLocalImage 1", exception);
                    }
                    consumer.onGet(ErrorCode.NO_STORAGE_SPACE, null);
                    return;
                }
                Utils.deleteFile("image", imagePath);
            }
            consumer.onGet(ErrorCode.SUCCESS, imageId);
        }
    }

    /**
     * Create a copy of an existing image.  Once the server has copied the image and allocated
     * a new image identifier, the consumer onGet operation is called with the new identifier.
     * When the image identified was not found, the onGet operation receives the ITEM_NOT_FOUND
     * error and a null identifier.
     *
     * @param imageId  the image identifier.
     * @param consumer the consumer handler.
     */
    @Override
    public void copyImage(@NonNull ImageId imageId, @NonNull Consumer<ExportedImageId> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "copyImage: imageId=" + imageId);
        }

        // We must know the original image Id (it can be different than imageId).
        final ImageInfo image = mServiceProvider.loadImage(imageId);
        if (image == null) {
            consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        // This is a local image and the server does not know it, create an image with the bitmap.
        if (image.status == ImageInfo.Status.LOCALE) {
            byte[] thumbnailSha = computeSHA256(image.data);
            long requestId = newRequestId();
            if (thumbnailSha == null) {
                consumer.onGet(ErrorCode.NO_STORAGE_SPACE, null);
                return;
            }
            CreateImageIQ createImageIQ = new CreateImageIQ(IQ_CREATE_IMAGE_SERIALIZER, requestId, thumbnailSha, null, null, image.data);

            CreateImagePendingRequest request = new CreateImagePendingRequest(null, null, image.data,
                    thumbnailSha, null, null, 0, 0, consumer);
            synchronized (mPendingRequests) {
                mPendingRequests.put(requestId, request);
            }
            sendDataPacket(createImageIQ, DEFAULT_REQUEST_TIMEOUT);
            return;
        }

        // Now we can ask the server to copy the specified image Id.
        long requestId = newRequestId();
        CopyImageIQ copyImageIQ = new CopyImageIQ(IQ_COPY_IMAGE_SERIALIZER, requestId, image.imageId);

        CopyImagePendingRequest request = new CopyImagePendingRequest(imageId, consumer);
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, request);
        }
        sendDataPacket(copyImageIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Delete the image identified by the UUID.  The image is first removed from the local
     * cache and if it was created by the current user it is also removed on the server.
     * The onGet operation is called when the image is removed.
     *
     * @param imageId  the image identifier.
     * @param consumer the consumer handler.
     */
    @Override
    public void deleteImage(@NonNull ImageId imageId, @NonNull Consumer<ImageId> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteImage: imageId=" + imageId);
        }

        final DeleteImageInfo info = mServiceProvider.deleteImage(imageId, true);
        if (info == null) {
            consumer.onGet(ErrorCode.ITEM_NOT_FOUND, imageId);
            return;
        }

        if (info.status == DeleteImageInfo.Status.DELETE_NONE) {
            // We must keep the image data because a copy exist.
            mThumbnailCache.remove(imageId);
            mImageCache.remove(imageId);

            consumer.onGet(ErrorCode.SUCCESS, imageId);
            return;
        }

        if (info.status == DeleteImageInfo.Status.DELETE_LOCAL) {
            // No copy exist, we can remove the image.
            removeCachedImagePath(info.publicId);

            mThumbnailCache.remove(imageId);
            mImageCache.remove(imageId);

            consumer.onGet(ErrorCode.SUCCESS, imageId);
            return;
        }

        final long requestId = newRequestId();
        final DeleteImageIQ deleteImageIQ = new DeleteImageIQ(IQ_DELETE_IMAGE_SERIALIZER, requestId, info.publicId);
        final DeleteImagePendingRequest request = new DeleteImagePendingRequest(imageId, info.publicId, consumer);
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, request);
        }
        sendDataPacket(deleteImageIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Remove the image identified by the UUID from the local cache.
     *
     * @param imageId the image identifier.
     */
    @Override
    public void evictImage(@NonNull ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "evictImage: imageId=" + imageId);
        }

        // Remove local images from the database.
        final UUID result = mServiceProvider.evictImage(imageId);
        if (result != null) {
            removeCachedImagePath(result);
        }

        mThumbnailCache.remove(imageId);
        mImageCache.remove(imageId);
    }

    @Override
    @NonNull
    public Map<ImageId, ImageId> listCopiedImages() {
        if (DEBUG) {
            Log.d(LOG_TAG, "listCopiedImages");
        }

        return mServiceProvider.listCopiedImages();
    }

    @Override
    @Nullable
    public ExportedImageId getPublicImageId(@NonNull ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPublicImageId");
        }

        return mServiceProvider.getPublicImageId(imageId);
    }

    @Override
    @Nullable
    public ExportedImageId getImageId(@NonNull UUID imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getImageId");
        }

        return mServiceProvider.getImageId(imageId);
    }

    void notifyDeleted(@NonNull ImageId imageId, @NonNull UUID publicImageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "notifyDeleted imageId=" + imageId + " publicImageId=" + publicImageId);
        }

        mTwinlifeExecutor.execute(() -> {
            removeCachedImagePath(publicImageId);

            // Cleanup the cache.
            mThumbnailCache.remove(imageId);
            mImageCache.remove(imageId);
        });
    }

    private void uploadImage(@NonNull UploadInfo uploadInfo, @NonNull File file, @NonNull ImageService.Kind kind, long serverChunkSize) {
        if (DEBUG) {
            Log.d(LOG_TAG, "uploadImage: uploadInfo=" + uploadInfo + " file=" + file + " kind=" + kind + " serverChunkSize=" + serverChunkSize);
        }

        ImageId imageId = new ImageId(uploadInfo.imageId);
        try (FileInputStream fs = new FileInputStream(file)) {
            long length = file.length();

            long remainSize = mServiceProvider.getUploadRemainSize(imageId, kind);

            // Upload the image on the server.
            long requestId = newRequestId();
            UploadImagePendingRequest request = new UploadImagePendingRequest(imageId, kind, length);
            synchronized (mPendingRequests) {
                if (mUploadRequest != null) {
                    return;
                }
                mPendingRequests.put(requestId, request);
                mUploadRequest = request;
            }

            // Send the image in chunks, we don't wait for server to acknowledge the upload.
            int chunkSize = computeChunkSize((int) length, (int) serverChunkSize);

            long offset;
            if (remainSize >= length) {
                offset = 0;
            } else {
                offset = length - remainSize;
                if (fs.skip(offset) != offset && Logger.ERROR) {
                    Logger.error(LOG_TAG, "Skip failed at ", offset);
                }
            }

            while (offset < length) {
                int size = (int) (length - offset);
                if (size > chunkSize) {
                    size = chunkSize;
                }
                byte[] data = new byte[size];
                int ret = fs.read(data, 0, size);
                if (ret <= 0) {
                    break;
                }
                PutImageIQ iq = new PutImageIQ(IQ_PUT_IMAGE_SERIALIZER, requestId, uploadInfo.imagePublicId, kind, data, 0, offset, ret, length);
                if (DEBUG) {
                    Log.d(LOG_TAG, "uploadImage: imageId=" + uploadInfo.imagePublicId + " file=" + file + " kind=" + kind + " iq=" + iq);
                }
                boolean queued;
                synchronized (mPendingRequests) {
                    queued = request.sendCount > MAX_SEND_IMAGE_IQ;
                    if (queued) {
                        request.queue.add(iq);
                    } else {
                        request.sendCount++;
                    }
                }
                if (!queued) {
                    sendDataPacket(iq, DEFAULT_REQUEST_TIMEOUT);
                }
                offset += ret;
            }
        } catch (Exception ex) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Exception while uploading image: ", ex);
            }
        }
    }

    /**
     * Response received after create-image operation.
     *
     * @param iq the create-image response.
     */
    private void onCreateImage(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateImage: iq=" + iq);
        }

        if (!(iq instanceof OnCreateImageIQ)) {
            return;
        }

        receivedIQ(iq.getRequestId());

        OnCreateImageIQ onCreateImageIQ = (OnCreateImageIQ) iq;
        long requestId = onCreateImageIQ.getRequestId();

        // Get the pending request or terminate.
        CreateImagePendingRequest request;
        synchronized (mPendingRequests) {
            request = (CreateImagePendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        ExportedImageId imageId = mServiceProvider.createImage(onCreateImageIQ.imageId, false, request.thumbnailData,
                request.imageShas, request.total1Length, request.total2Length);
        if (imageId == null) {
            // Delete the image file when the creation failed.
            if (request.imagePath != null) {
                Utils.deleteFile("image", request.imagePath);
            }
            request.consumer.onGet(ErrorCode.NO_STORAGE_SPACE, null);
            return;
        }

        // Save the large image to the cache.
        boolean needUpload = false;
        if (request.imagePath != null) {
            File imagePath = getCachedImagePath(imageId.getExportedId(), Kind.NORMAL);
            if (!request.imagePath.renameTo(imagePath)) {
                Utils.copyFile(request.imagePath, imagePath);
            }

            needUpload = true;
        }
        if (request.imageLargePath != null) {
            File imagePath = getCachedImagePath(imageId.getExportedId(), Kind.LARGE);
            if (!request.imageLargePath.renameTo(imagePath)) {
                Utils.copyFile(request.imageLargePath, imagePath);
            }

            needUpload = true;
        }

        if (needUpload) {
            synchronized (this) {
                mUploadChunkSize = (int) onCreateImageIQ.chunkSize;
            }
            backgroundUpload();
        }

        request.consumer.onGet(ErrorCode.SUCCESS, imageId);
    }

    /**
     * Response received after get-image operation.
     *
     * @param iq the get-image response.
     */
    private void onGetImage(BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetImage: iq=" + iq);
        }

        if (!(iq instanceof OnGetImageIQ)) {
            return;
        }

        receivedIQ(iq.getRequestId());

        OnGetImageIQ onGetImageIQ = (OnGetImageIQ) iq;
        long requestId = onGetImageIQ.getRequestId();

        // Get the pending request or terminate.
        GetImagePendingRequest request;
        synchronized (mPendingRequests) {
            if (onGetImageIQ.imageSha != null) {
                request = (GetImagePendingRequest) mPendingRequests.remove(requestId);
            } else {
                request = (GetImagePendingRequest) mPendingRequests.get(requestId);
            }
        }
        if (request == null) {
            return;
        }

        // Don't accept an image that is too big for us.
        if (onGetImageIQ.totalSize > mMaxImageSize) {
            request.dispatch(ErrorCode.NO_STORAGE_SPACE, null);
            return;
        }

        // We can receive the image in several chunks, the last one contains the image signature.
        byte[] imageData;
        if (request.imageReceived == null && onGetImageIQ.imageSha != null) {
            // Only one chunk.
            imageData = onGetImageIQ.imageData;
            byte[] sha256 = computeSHA256(imageData);
            if (!Arrays.equals(sha256, onGetImageIQ.imageSha)) {
                request.dispatch(ErrorCode.NO_STORAGE_SPACE, null);
                return;
            }
        } else /* if (request.kind == Kind.THUMBNAIL) */ {

            if (request.imageReceived == null) {
                // We received the first chunk, allocate the data buffer.
                try {
                    request.imageReceived = new byte[(int) onGetImageIQ.totalSize];

                } catch (OutOfMemoryError ex) {
                    request.dispatch(ErrorCode.NO_STORAGE_SPACE, null);
                    return;
                }
            }
            System.arraycopy(onGetImageIQ.imageData, 0, request.imageReceived, (int) onGetImageIQ.offset, onGetImageIQ.imageData.length);
            if (onGetImageIQ.imageSha == null) {
                // Restart timer for next chunk.
                packetTimeout(requestId, DEFAULT_REQUEST_TIMEOUT, true);
                return;
            }

            // Check the image signature.
            imageData = request.imageReceived;
            byte[] sha256 = computeSHA256(imageData);
            if (!Arrays.equals(sha256, onGetImageIQ.imageSha)) {
                request.dispatch(ErrorCode.NO_STORAGE_SPACE, null);
                return;
            }
        }

        Bitmap image = null;

        // Save the image either in the database or in the cache directory.
        if (request.kind == Kind.THUMBNAIL) {
            boolean updated = mServiceProvider.importImage(request.imageId, request.status, imageData, onGetImageIQ.imageSha);

            image = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            if (image == null || !updated) {
                request.dispatch(ErrorCode.NO_STORAGE_SPACE, null);
                return;
            }
            mThumbnailCache.put(request.imageId, new WeakReference<>(image));
        } else {
            File imagePath = getCachedImagePath(request.imagePublicId, request.kind);
            File parentDir = imagePath.getParentFile();
            if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs() && Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot create cache directory: ", parentDir);
            }
            try (FileOutputStream out = new FileOutputStream(imagePath)) {
                out.write(imageData, 0, imageData.length);
                image = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Cannot save image: ", exception);
                }
            }

            if (image == null) {
                request.dispatch(ErrorCode.NO_STORAGE_SPACE, null);
                return;
            }
            mImageCache.put(request.imageId, new WeakReference<>(image));
        }

        request.dispatch(ErrorCode.SUCCESS, image);
    }

    /**
     * Response received after copy-image operation.
     *
     * @param iq the copy-image response.
     */
    private void onCopyImage(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCopyImage: iq=" + iq);
        }

        if (!(iq instanceof OnCopyImageIQ)) {
            return;
        }

        receivedIQ(iq.getRequestId());

        OnCopyImageIQ onCopyImageIQ = (OnCopyImageIQ) iq;
        long requestId = onCopyImageIQ.getRequestId();

        // Get the pending request or terminate.
        CopyImagePendingRequest request;
        synchronized (mPendingRequests) {
            request = (CopyImagePendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final ExportedImageId copiedImageId = mServiceProvider.copyImage(onCopyImageIQ.imageId, request.imageId);
        request.consumer.onGet(copiedImageId != null ? ErrorCode.SUCCESS : ErrorCode.NO_STORAGE_SPACE, copiedImageId);
    }

    /**
     * Response received after delete-image operation.
     *
     * @param iq the delete-image response.
     */
    private void onDeleteImage(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteImage: iq=" + iq);
        }

        if (!(iq instanceof OnDeleteImageIQ)) {
            return;
        }

        receivedIQ(iq.getRequestId());

        OnDeleteImageIQ onDeleteImageIQ = (OnDeleteImageIQ) iq;
        long requestId = onDeleteImageIQ.getRequestId();

        // Get the pending request or terminate.
        DeleteImagePendingRequest request;
        synchronized (mPendingRequests) {
            request = (DeleteImagePendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        // Remove from the database.
        DeleteImageInfo info = mServiceProvider.deleteImage(request.imageId, false);
        if (info != null && info.status == DeleteImageInfo.Status.DELETE_LOCAL_REMOTE) {
            removeCachedImagePath(request.imagePublicId);
        }

        // Cleanup the cache.
        mThumbnailCache.remove(request.imageId);
        mImageCache.remove(request.imageId);

        request.consumer.onGet(ErrorCode.SUCCESS, request.imageId);
    }

    /**
     * Response received after delete-image operation.
     *
     * @param iq the delete-image response.
     */
    private void onPutImage(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPutImage: iq=" + iq);
        }

        if (!(iq instanceof OnPutImageIQ)) {
            return;
        }

        receivedIQ(iq.getRequestId());

        OnPutImageIQ onPutImageIQ = (OnPutImageIQ) iq;
        long requestId = onPutImageIQ.getRequestId();

        // Get the pending request or terminate.
        UploadImagePendingRequest request;
        PutImageIQ nextIQ = null;
        synchronized (mPendingRequests) {
            if (onPutImageIQ.status != OnPutImageIQ.Status.INCOMPLETE) {
                request = (UploadImagePendingRequest) mPendingRequests.remove(requestId);
                mUploadRequest = null;
            } else {
                request = mUploadRequest;
                if (request != null) {
                    if (request.sendCount <= MAX_SEND_IMAGE_IQ && !request.queue.isEmpty()) {
                        nextIQ = request.queue.remove(0);
                    } else {
                        request.sendCount--;
                    }
                }
            }
        }
        if (request == null) {
            return;
        }

        // Record what remains for the upload so that we can recover a partial upload.
        if (onPutImageIQ.status == OnPutImageIQ.Status.ERROR) {
            mServiceProvider.saveRemainUploadSize(request.imageId, request.kind, request.length);
        } else {
            mServiceProvider.saveRemainUploadSize(request.imageId, request.kind, request.length - onPutImageIQ.offset);
        }
        if (nextIQ != null) {
            sendDataPacket(nextIQ, DEFAULT_REQUEST_TIMEOUT);
        } else if (onPutImageIQ.status != OnPutImageIQ.Status.INCOMPLETE) {
            backgroundUpload();
        }
    }

    //
    // Implement RepositoryService interface
    //

    @Override
    protected void onErrorPacket(@NonNull BinaryErrorPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: iq=" + iq);
        }

        receivedIQ(iq.getRequestId());

        PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(iq.getRequestId());
            if (request == null) {
                return;
            }
            if (request == mUploadRequest) {
                mUploadRequest = null;
            }
        }

        if (request instanceof GetImagePendingRequest) {
            GetImagePendingRequest imagePendingRequest = (GetImagePendingRequest)request;
            imagePendingRequest.dispatch(iq.getErrorCode(), null);

        } else if (request instanceof CreateImagePendingRequest) {
            ((CreateImagePendingRequest) request).consumer.onGet(iq.getErrorCode(), null);

        } else if (request instanceof CopyImagePendingRequest) {
            ((CopyImagePendingRequest) request).consumer.onGet(iq.getErrorCode(), null);

        } else if (request instanceof DeleteImagePendingRequest) {
            ((DeleteImagePendingRequest) request).consumer.onGet(iq.getErrorCode(), null);

        }
    }

    @Nullable
    UploadInfo getNextUpload() {

        return mServiceProvider.getNextUpload();
    }

    private void backgroundUpload() {
        if (DEBUG) {
            Log.d(LOG_TAG, "backgroundUpload");
        }

        final UploadInfo info = getNextUpload();
        synchronized (this) {
            mUploadJob = null;

            mCheckUpload = info != null;
            if (info == null) {
                return;
            }
            if (mUploadRequest != null) {
                return;
            }
        }

        if (info.remainNormalImage > 0) {
            File imagePath = getCachedImagePath(info.imagePublicId, Kind.NORMAL);
            if (imagePath.exists()) {
                uploadImage(info, imagePath, Kind.NORMAL, mUploadChunkSize);
            } else {
                mServiceProvider.saveRemainUploadSize(new ImageId(info.imageId), Kind.NORMAL, 0);
            }
        }

        if (info.remainLargeImage > 0) {
            File imagePath = getCachedImagePath(info.imagePublicId, Kind.LARGE);
            if (imagePath.exists()) {
                uploadImage(info, imagePath, Kind.LARGE, mUploadChunkSize);
            } else {
                mServiceProvider.saveRemainUploadSize(new ImageId(info.imageId), Kind.LARGE, 0);
            }
        }
    }

    private void removeCachedImagePath(@NonNull UUID imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCachedImagePath: imageId=" + imageId);
        }

        File file;

        file = getCachedImagePath(imageId, Kind.NORMAL);
        Utils.deleteFile(LOG_TAG, file);
        file = getCachedImagePath(imageId, Kind.LARGE);
        Utils.deleteFile(LOG_TAG, file);
    }

    @NonNull
    private File getLocalImagePath(@NonNull UUID imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getLocalImagePath: imageId=" + imageId);
        }

        // Note: we must use the same format as on iOS: UUID in lower case with .img extension.
        return new File(mLocalImagesDir, imageId + ".img");
    }

    @NonNull
    private File getCachedImagePath(@NonNull UUID imageId, @NonNull ImageService.Kind kind) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getCachedImagePath: imageId=" + imageId + " kind=" + kind);
        }

        switch (kind) {
            case THUMBNAIL:
                return new File(mCacheDir, imageId + "-thumb.jpg");

            case LARGE:
                return new File(mCacheDir, imageId + "-large.jpg");

            case NORMAL:
            default:
                return new File(mCacheDir, imageId + "-normal.jpg");
        }
    }

    private byte[] computeFileSHA256(@NonNull File imagePath) {
        try (FileInputStream input = new FileInputStream(imagePath)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] data = new byte[4096];
            while (true) {
                int count = input.read(data);
                if (count <= 0) {
                    break;
                }
                digest.update(data, 0, count);
            }
            return digest.digest();

        } catch (Exception ex) {
            return null;
        }
    }

    // Be conservative and use a default < 64K.
    private static final int DEFAULT_CHUNK_SIZE = 32768;

    private int computeChunkSize(int total, int serverChunkSize) {

        // Todo: look at the session to choose a specific chunk for this device/network connection.
        if (serverChunkSize <= 0) {
            serverChunkSize = DEFAULT_CHUNK_SIZE;
        }

        int defaultChunkCount = (total + serverChunkSize - 1) / serverChunkSize;

        // Make each chunk the same size but aligned on 4 bytes upper boundary.
        int chunkSize = 1 + total / (4 * defaultChunkCount);
        return 4 * chunkSize;
    }

    private byte[] computeSHA256(@NonNull byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);

        } catch (Exception ex) {
            return null;
        }
    }

}
