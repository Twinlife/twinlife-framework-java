/*
 *  Copyright (c) 2020-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.image;

import android.content.ContentValues;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.database.Columns;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.DatabaseServiceProvider;
import org.twinlife.twinlife.database.ImagesCleaner;
import org.twinlife.twinlife.database.Tables;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.util.EventMonitor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

class ImageServiceProvider extends DatabaseServiceProvider implements ImagesCleaner {
    private static final String LOG_TAG = "ImageServicePro...";
    private static final boolean DEBUG = false;

    /**
     * Image table:
     * id INTEGER PRIMARY KEY: image id
     * copiedFrom INTEGER: image id of the original image
     * uuid TEXT NOT NULL: the public image ID
     * creationDate INTEGER NOT NULL: image creation date
     * flags INTEGER: image flags { LOCAL, OWNER, DELETED, REMOTE, MISSING }
     * modificationDate INTEGER: image last check date
     * uploadRemain1 INTEGER: number of bytes to upload for the normal image
     * uploadRemain2 INTEGER: number of bytes to upload for the large image
     * imageSHAs BLOB: image SHA256 (thumbnail or thumbnail+image or thumbnail+image+largeImage)
     * thumbnail BLOB: image thumbnail data
     */
    private static final String IMAGE_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS image (id INTEGER PRIMARY KEY NOT NULL, copiedFrom INTEGER, uuid TEXT NOT NULL,"
                    + " creationDate INTEGER NOT NULL, flags INTEGER, modificationDate INTEGER,"
                    + " uploadRemain1 INTEGER, uploadRemain2 INTEGER, shaThumbnail BLOB, imageSHAs BLOB, thumbnail BLOB);";

    /**
     * Table from V7 to V19:
     * CREATE TABLE IF NOT EXISTS twincodeImage (id TEXT PRIMARY KEY NOT NULL,
     *                     status INTEGER, copiedFrom TEXT, createDate INTEGER, updateDate INTEGER,
     *                     uploadRemain1 INTEGER, uploadRemain2 INTEGER, thumbnail BLOB);
     */

    @NonNull
    private final ImageServiceImpl mService;

    ImageServiceProvider(@NonNull ImageServiceImpl service, @NonNull DatabaseServiceImpl database) {
        super(service, database, IMAGE_CREATE_TABLE, DatabaseTable.TABLE_IMAGE);
        if (DEBUG) {
            Log.d(LOG_TAG, "ImageServiceProvider: service=" + service);
        }

        mService = service;
    }

    @Override
    protected void onUpgrade(@NonNull Transaction transaction, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade: oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        /*
         * <pre>
         * Database Version 20
         *  Date: 2023/08/29
         *   New database model with image table and change of primary key
         * </pre>
         */
        super.onUpgrade(transaction, oldVersion, newVersion);
        if (oldVersion < 20 && transaction.hasTable("twincodeImage")) {
            upgrade20(transaction);
        }
    }

    //
    // Package scoped methods
    //

    @Nullable
    ExportedImageId createImage(@NonNull UUID imageId, boolean locale, @NonNull byte[] thumbnail,
                                @NonNull byte[] imageSha, long remain1Size, long remain2Size) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createImage: imageId=" + imageId
                    + " remain1Size=" + remain1Size + " remain2Size=" + remain2Size);
        }

        try (Transaction transaction = newTransaction()) {
            long id = transaction.allocateId(DatabaseTable.TABLE_IMAGE);
            ContentValues values = new ContentValues();
            values.put(Columns.ID, id);
            values.put(Columns.UUID, imageId.toString());
            values.put(Columns.FLAGS, fromImageStatus(locale ? ImageInfo.Status.LOCALE : ImageInfo.Status.OWNER));
            values.put(Columns.CREATION_DATE, System.currentTimeMillis());
            values.put(Columns.UPLOAD_REMAIN1, remain1Size);
            values.put(Columns.UPLOAD_REMAIN2, remain2Size);
            values.put(Columns.THUMBNAIL, thumbnail);
            values.put(Columns.IMAGE_SHAS, imageSha);
            transaction.insertOrThrow(Tables.IMAGE, null, values);
            transaction.commit();
            return new ExportedImageId(id, imageId);

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    boolean importImage(@NonNull ImageId imageId, @NonNull ImageInfo.Status status, @NonNull byte[] thumbnail,
                        @NonNull byte[] thumbnailSha) {
        if (DEBUG) {
            Log.d(LOG_TAG, "importImage: imageId=" + imageId );
        }

        final String[] params = { String.valueOf(imageId.getId()) };
        try (Transaction transaction = newTransaction()) {
            ContentValues values = new ContentValues();
            values.put(Columns.FLAGS, fromImageStatus(status));
            values.put(Columns.UPLOAD_REMAIN1, 0);
            values.put(Columns.UPLOAD_REMAIN2, 0);
            values.put(Columns.THUMBNAIL, thumbnail);
            values.put(Columns.IMAGE_SHAS, thumbnailSha);
            transaction.update(Tables.IMAGE, values, "id=?", params);
            transaction.commit();
            return true;

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return false;
        }
    }

    @Nullable
    ExportedImageId copyImage(@NonNull UUID imageId, @NonNull ImageId copiedImageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "copyImage: imageId=" + imageId + " copiedImageId=" + copiedImageId);
        }

        try (Transaction transaction = newTransaction()) {
            long id = transaction.allocateId(DatabaseTable.TABLE_IMAGE);
            ContentValues values = new ContentValues();
            values.put(Columns.ID, id);
            values.put(Columns.UUID, imageId.toString());
            values.put(Columns.FLAGS, fromImageStatus(ImageInfo.Status.OWNER));
            values.put(Columns.CREATION_DATE, System.currentTimeMillis());
            values.put(Columns.COPIED_FROM_ID, copiedImageId.getId());
            transaction.insertOrThrow(Tables.IMAGE, null, values);
            transaction.commit();
            return new ExportedImageId(id, imageId);

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    @Nullable
    ImageInfo loadImage(@NonNull ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadImage: imageId=" + imageId);
        }

        final String[] params = { String.valueOf(imageId.getId()) };
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT img.uuid, img.flags, img.thumbnail, origin.flags AS origFlags,"
                + " origin.thumbnail AS originThumbnail, origin.uuid AS originUuid FROM image AS img"
                + " LEFT JOIN image AS origin ON img.copiedFrom = origin.id"
                + " WHERE img.id=?", params)) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            final UUID uuid = cursor.getUUID(0);
            final byte[] thumbnail = cursor.getBlob(2);
            if (thumbnail != null) {
                final ImageInfo.Status status = toImageStatus(cursor.isNull(1), cursor.getInt(1));
                return new ImageInfo(uuid, status, thumbnail, null);
            } else {
                final ImageInfo.Status originStatus = toImageStatus(cursor.isNull(3), cursor.getInt(3));
                final byte[] copiedFrom = cursor.getBlob(4);
                final UUID originUuid = cursor.getUUID(5);

                return new ImageInfo(uuid, originStatus, copiedFrom, originUuid);
            }
        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    void saveRemainUploadSize(@NonNull ImageId imageId, @NonNull ImageService.Kind kind, long remainSize) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveRemainUploadSize: imageId=" + imageId + " kind=" + kind + " remainSize=" + remainSize);
        }

        final String[] params = { String.valueOf(imageId.getId()) };
        try (Transaction transaction = newTransaction()) {
            ContentValues values = new ContentValues();
            if (kind == ImageService.Kind.NORMAL) {
                values.put(Columns.UPLOAD_REMAIN1, remainSize);
            } else {
                values.put(Columns.UPLOAD_REMAIN2, remainSize);
            }
            transaction.update(Tables.IMAGE, values, "id=?", params);
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    long getUploadRemainSize(@NonNull ImageId imageId, @NonNull ImageService.Kind kind) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getUploadRemainSize: imageId=" + imageId + " kind=" + kind);
        }

        final String[] params = { String.valueOf(imageId.getId()) };
        String query = (kind == ImageService.Kind.NORMAL) ? "SELECT uploadRemain1 FROM image WHERE id=?"
                : "SELECT uploadRemain2 FROM image WHERE id =?";
        try {
            Long count = mDatabase.longQuery(query, params);
            return count == null ? 0 : count;

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return 0;
        }
    }

    @Nullable
    UploadInfo getNextUpload() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNextUpload");
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id, uuid, uploadRemain1, uploadRemain2"
                + " FROM image WHERE uploadRemain1 > 0 OR uploadRemain2 > 0 LIMIT 1", null)) {
            if (cursor.moveToFirst()) {
                long imageId = cursor.getLong(0);
                UUID imagePublicId = cursor.getUUID(1);
                if (imagePublicId != null) {
                    long remain1 = cursor.getLong(2);
                    long remain2 = cursor.getLong(3);

                    return new UploadInfo(imageId, imagePublicId, remain1, remain2);
                }
            }
            return null;

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    @Nullable
    DeleteImageInfo deleteImage(@NonNull ImageId imageId, boolean localOnly) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteImage: imageId=" + imageId + " localOnly=" + localOnly);
        }

        int count = 0;
        ImageInfo.Status status = null;
        UUID uuid = null;
        final String[] params = { String.valueOf(imageId.getId()) };
        try (Transaction transaction = newTransaction()) {

            // Get the image UUID, status and count of copiedFrom references the image has
            try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT img.uuid, img.flags, COUNT(c.id) FROM image AS img"
                    + " LEFT JOIN image AS c ON c.copiedFrom=img.id WHERE img.id=?", params)) {

                if (cursor.moveToFirst()) {
                    uuid = cursor.getUUID(0);
                    status = toImageStatus(cursor.isNull(1), cursor.getInt(1));
                    count = cursor.getInt(2);
                }
            }

            if (uuid != null && (!localOnly || status != ImageInfo.Status.OWNER)) {
                if (count == 0) {
                    transaction.delete(Tables.IMAGE, "id=?", params);
                } else {
                    // Mark this image as deleted but the content can be accessed from another imageId.
                    ContentValues values = new ContentValues();
                    values.put(Columns.FLAGS, fromImageStatus(ImageInfo.Status.DELETED));
                    transaction.update(Tables.IMAGE, values, "id=?", params);
                }
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        if (uuid == null) {
            return null;
        }

        // Cache files associated with the image can be removed when there is no more reference.
        // PR 3318:
        //  When a user creates a contact with its own profile deleteImage is called on the peerAvatarId
        //   that is indeed the avatarId of the profile
        //   Using DeleteStatus.DELETE_NONE instead of DeleteStatus.DELETE_REMOTE solved this problem and does
        //   not generated phantom image in the server
        //
        if (status == ImageInfo.Status.OWNER) {
            return new DeleteImageInfo(uuid, count == 0 ? DeleteImageInfo.Status.DELETE_LOCAL_REMOTE : DeleteImageInfo.Status.DELETE_NONE);
        }

        // Image was locale or is owned by someone else.
        return new DeleteImageInfo(uuid, count == 0 ? DeleteImageInfo.Status.DELETE_LOCAL : DeleteImageInfo.Status.DELETE_NONE);
    }

    @Nullable
    UUID evictImage(@NonNull ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "evictImage: imageId=" + imageId);
        }

        final String[] params = { String.valueOf(imageId.getId()) };
        try (Transaction transaction = newTransaction()) {
            UUID imagePublicId = null;
            try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT uuid FROM image WHERE id=?", params)) {
                if (cursor.moveToFirst()) {
                    imagePublicId = cursor.getUUID(0);
                }
            }

            int count = transaction.delete(Tables.IMAGE, "id=? AND (flags=3 OR flags=5)", params);
            transaction.commit();

            return count > 0 ? null : imagePublicId;


        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    @NonNull
    Map<ImageId, ImageId> listCopiedImages() {
        if (DEBUG) {
            Log.d(LOG_TAG, "listCopiedImages");
        }

        final Map<ImageId, ImageId> result = new HashMap<>();
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id, copiedFrom FROM image WHERE copiedFrom IS NOT NULL", null)) {
            while (cursor.moveToNext()) {
                long imageId = cursor.getLong(0);
                long copiedFromId = cursor.getLong(1);
                if (imageId != 0 && copiedFromId != 0) {
                    result.put(new ImageId(imageId), new ImageId(copiedFromId));
                }
            }
            return result;

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return result;
        }
    }

    /**
     * Delete the image from the database, remove it from the image cache and if the big image was downloaded
     * remove it from the file system.
     *
     * @param transaction the transaction to use.
     * @param imageId the image id to remove.
     * @throws DatabaseException the database exception raised.
     */
    @Override
    public void deleteImage(@NonNull Transaction transaction, @NonNull ImageId imageId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteImage imageId=" + imageId);
        }

        final String[] params = { String.valueOf(imageId.getId()) };
        UUID imagePublicId = null;
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT uuid FROM image WHERE id=?", params)) {
            if (cursor.moveToFirst()) {
                imagePublicId = cursor.getUUID(0);
            }
        }

        int count = transaction.delete(Tables.IMAGE, "id=? AND (flags=3 OR flags=5)", params);
        if (count > 0 && imagePublicId != null) {
            mService.notifyDeleted(imageId, imagePublicId);
        }
    }

    @Nullable
    public ExportedImageId getPublicImageId(@NonNull ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPublicImageId imageId=" + imageId);
        }

        try {
            return mDatabase.getPublicImageId(imageId);

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    @Nullable
    public ExportedImageId getImageId(@NonNull UUID publicId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getImageId publicId=" + publicId);
        }

        final String[] params = { publicId.toString() };
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id FROM image WHERE uuid=?", params)) {
            if (cursor.moveToNext()) {
                long imageId = cursor.getLong(0);
                return new ExportedImageId(imageId, publicId);
            }
            return null;

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    private static int fromImageStatus(@NonNull ImageInfo.Status status) {
        switch (status) {
            case OWNER:
                return 0;
            case LOCALE:
                return 1;
            case DELETED:
                return 2;
            case REMOTE:
                return 3;
            case MISSING:
                return 4;
            case NEED_FETCH:
                return 5;
        }
        return 2;
    }

    @NonNull
    private static ImageInfo.Status toImageStatus(boolean isNull, int value) {
        if (isNull) {
            return ImageInfo.Status.NEED_FETCH;
        }
        switch (value) {
            case 0:
                return ImageInfo.Status.OWNER;
            case 1:
                return ImageInfo.Status.LOCALE;
            case 2:
            default:
                return ImageInfo.Status.DELETED;
            case 3:
                return ImageInfo.Status.REMOTE;
            case 4:
                return ImageInfo.Status.MISSING;
            case 5:
                return ImageInfo.Status.NEED_FETCH;
        }
    }

    /**
     * Migrate the twincodeImage table to the images new table format.
     *
     * @throws DatabaseException when a database error occurred.
     */
    private void upgrade20(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "upgrade20");
        }

        final long startTime = EventMonitor.start();
        int count = 0;

        // Step 1: migrate images which are not a copy of one of our image.
        // Do this one by one and remove the image from the old table to free up some space.
        // A thumbnail is stored in several database pages and the deletion allows to re-use
        // a freed page and avoid to grow the database file too much.
        // Copying is necessary to due the primary key that is changed.
        final Map<UUID, Long> imageMap = new HashMap<>();
        while (true) {
            try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id, status, createDate, updateDate, uploadRemain1,"
                    + " uploadRemain2, thumbnail FROM twincodeImage WHERE copiedFrom IS NULL LIMIT 1", null)) {
                if (!cursor.moveToNext()) {
                    break;
                }
                UUID imageId = cursor.getUUID(0);
                int status = cursor.getInt(1);
                long createDate = cursor.getLong(2);
                long updateDate = cursor.getLong(3);
                long uploadRemain1 = cursor.getLong(4);
                long uploadRemain2 = cursor.getLong(5);
                byte[] thumbnail = cursor.getBlob(6);
                if (imageId != null) {
                    long id = transaction.allocateId(DatabaseTable.TABLE_IMAGE);
                    imageMap.put(imageId, id);

                    ContentValues values = new ContentValues();
                    values.put(Columns.ID, id);
                    values.put(Columns.UUID, imageId.toString());
                    values.put(Columns.FLAGS, status);
                    values.put(Columns.CREATION_DATE, createDate);
                    values.put(Columns.MODIFICATION_DATE, updateDate);
                    values.put(Columns.UPLOAD_REMAIN1, uploadRemain1);
                    values.put(Columns.UPLOAD_REMAIN2, uploadRemain2);
                    values.put(Columns.THUMBNAIL, thumbnail);
                    transaction.insert(Tables.IMAGE, values);
                    transaction.delete("twincodeImage", "id=?", new String[]{imageId.toString()});
                    count++;

                    // Because images use several pages, commit after each insert+delete.
                    transaction.commit();
                }
            }
        }

        // Step 2: migrate images which are a copy of an image (we don't need the thumbnail, uploadRemainX).
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT id, copiedFrom, status, createDate, updateDate"
                + " FROM twincodeImage WHERE copiedFrom IS NOT NULL", null)) {
            while (cursor.moveToNext()) {
                UUID imageId = cursor.getUUID(0);
                UUID copiedFromId = cursor.getUUID(1);
                int status = cursor.getInt(2);
                long createDate = cursor.getLong(3);
                long updateDate = cursor.getLong(4);
                if (imageId != null && copiedFromId != null) {
                    Long copiedId = imageMap.get(copiedFromId);
                    if (copiedId != null) {
                        long id = transaction.allocateId(DatabaseTable.TABLE_IMAGE);

                        ContentValues values = new ContentValues();
                        values.put(Columns.ID, id);
                        values.put(Columns.UUID, imageId.toString());
                        values.put(Columns.COPIED_FROM_ID, copiedId);
                        values.put(Columns.FLAGS, status);
                        values.put(Columns.CREATION_DATE, createDate);
                        values.put(Columns.MODIFICATION_DATE, updateDate);
                        transaction.insert(Tables.IMAGE, values);
                        count++;
                    }
                }
            }
        }

        transaction.dropTable("twincodeImage");
        if (BuildConfig.ENABLE_EVENT_MONITOR) {
            EventMonitor.event("Migrated " + count + " images", startTime);
        }
    }
}
