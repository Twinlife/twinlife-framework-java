/*
 *  Copyright (c) 2013-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */
package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public enum ErrorCode {
    SUCCESS,
    BAD_REQUEST,
    @SuppressWarnings("unused") CANCELED_OPERATION,
    FEATURE_NOT_IMPLEMENTED,
    FEATURE_NOT_SUPPORTED_BY_PEER,
    SERVER_ERROR,
    ITEM_NOT_FOUND,
    LIBRARY_ERROR,
    LIBRARY_TOO_OLD,
    NOT_AUTHORIZED_OPERATION,
    SERVICE_UNAVAILABLE,
    TWINLIFE_OFFLINE,
    WEBRTC_ERROR,
    WRONG_LIBRARY_CONFIGURATION,
    NO_STORAGE_SPACE,
    NO_PERMISSION,
    LIMIT_REACHED,
    DATABASE_ERROR,
    TIMEOUT_ERROR,
    ACCOUNT_DELETED,
    QUEUED,
    QUEUED_NO_WAKEUP,
    EXPIRED,
    INVALID_PUBLIC_KEY,
    INVALID_PRIVATE_KEY,
    NO_PUBLIC_KEY,
    NO_PRIVATE_KEY,
    NO_SECRET_KEY,
    NOT_ENCRYPTED,
    BAD_SIGNATURE,
    BAD_SIGNATURE_FORMAT,
    BAD_SIGNATURE_MISS_ATTRIBUTE,
    BAD_SIGNATURE_NOT_SIGNED_ATTRIBUTE,
    ENCRYPT_ERROR,
    DECRYPT_ERROR,
    BAD_ENCRYPTION_FORMAT,
    FILE_NOT_FOUND,
    FILE_NOT_SUPPORTED,
    DATABASE_CORRUPTION,
    RESTORE_IN_PROGRESS,  // The account is currently being restored on another device. Retry later to see if the restore failed/was rolled back.
    ACCOUNT_RESTORED,     // The account was successfully restored on another device. This device can no longer be used to access it.
    EXISTS,
    KEYSTORE_ERROR;       // The Android keystore is not secure enough or has errors during startup.

    public static int fromErrorCode(@Nullable ErrorCode errorCode) {
        if (errorCode != null) {
            switch (errorCode) {
                case SUCCESS:
                    return 0;

                case BAD_REQUEST:
                    return 1;

                case CANCELED_OPERATION:
                    return 2;

                case FEATURE_NOT_IMPLEMENTED:
                    return 3;

                case FEATURE_NOT_SUPPORTED_BY_PEER:
                    return 4;

                case SERVER_ERROR:
                    return 5;

                case ITEM_NOT_FOUND:
                    return 6;

                case LIBRARY_ERROR:
                    return 7;

                case LIBRARY_TOO_OLD:
                    return 8;

                case NOT_AUTHORIZED_OPERATION:
                    return 9;

                case SERVICE_UNAVAILABLE:
                    return 10;

                case TWINLIFE_OFFLINE:
                    return 11;

                case WEBRTC_ERROR:
                    return 12;

                case WRONG_LIBRARY_CONFIGURATION:
                    return 13;

                case NO_STORAGE_SPACE:
                    return 14;

                case NO_PERMISSION:
                    return 15;

                case LIMIT_REACHED:
                    return 16;

                case DATABASE_ERROR:
                    return 17;

                case QUEUED:
                    return 18;

                case QUEUED_NO_WAKEUP:
                    return 19;

                case EXPIRED:
                    return 20;

                case INVALID_PUBLIC_KEY:
                    return 21;

                case INVALID_PRIVATE_KEY:
                    return 22;

                case NO_PUBLIC_KEY:
                    return 23;

                case NO_PRIVATE_KEY:
                    return 24;

                case BAD_SIGNATURE:
                    return 25;

                case BAD_SIGNATURE_FORMAT:
                    return 26;

                case BAD_SIGNATURE_MISS_ATTRIBUTE:
                    return 27;

                case BAD_SIGNATURE_NOT_SIGNED_ATTRIBUTE:
                    return 28;

                case ENCRYPT_ERROR:
                    return 29;

                case DECRYPT_ERROR:
                    return 30;

                case BAD_ENCRYPTION_FORMAT:
                    return 31;

                case NO_SECRET_KEY:
                    return 32;

                case NOT_ENCRYPTED:
                    return 33;

                case FILE_NOT_FOUND:
                    return 34;

                case FILE_NOT_SUPPORTED:
                    return 35;

                case DATABASE_CORRUPTION:
                    return 36;

                case RESTORE_IN_PROGRESS:
                    return 37;

                case ACCOUNT_RESTORED:
                    return 38;

                case EXISTS:
                    return 39;

                case KEYSTORE_ERROR:
                    return 40;
            }
        }
        return 7;
    }

    @NonNull
    public static ErrorCode toErrorCode(int value) {
        ErrorCode errorCode;
        switch (value) {
            case 0:
                errorCode = ErrorCode.SUCCESS;
                break;

            case 1:
                errorCode = ErrorCode.BAD_REQUEST;
                break;

            case 2:
                errorCode = ErrorCode.CANCELED_OPERATION;
                break;

            case 3:
                errorCode = ErrorCode.FEATURE_NOT_IMPLEMENTED;
                break;

            case 4:
                errorCode = ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER;
                break;

            case 5:
                errorCode = ErrorCode.SERVER_ERROR;
                break;

            case 6:
                errorCode = ErrorCode.ITEM_NOT_FOUND;
                break;

            case 7:
                errorCode = ErrorCode.LIBRARY_ERROR;
                break;

            case 9:
                errorCode = ErrorCode.NOT_AUTHORIZED_OPERATION;
                break;

            case 10:
                errorCode = ErrorCode.SERVICE_UNAVAILABLE;
                break;

            case 11:
                errorCode = ErrorCode.TWINLIFE_OFFLINE;
                break;

            case 12:
                errorCode = ErrorCode.WEBRTC_ERROR;
                break;

            case 13:
                errorCode = ErrorCode.WRONG_LIBRARY_CONFIGURATION;
                break;

            case 14:
                errorCode = ErrorCode.NO_STORAGE_SPACE;
                break;

            case 15:
                errorCode = ErrorCode.NO_PERMISSION;
                break;

            case 16:
                errorCode = ErrorCode.LIMIT_REACHED;
                break;

            case 17:
                errorCode = ErrorCode.DATABASE_ERROR;
                break;

            case 18:
                errorCode = ErrorCode.QUEUED;
                break;

            case 19:
                errorCode = ErrorCode.QUEUED_NO_WAKEUP;
                break;

            case 20:
                errorCode = ErrorCode.EXPIRED;
                break;

            case 21:
                errorCode = ErrorCode.INVALID_PUBLIC_KEY;
                break;

            case 22:
                errorCode = ErrorCode.INVALID_PRIVATE_KEY;
                break;

            case 23:
                errorCode = ErrorCode.NO_PUBLIC_KEY;
                break;

            case 24:
                errorCode = ErrorCode.NO_PRIVATE_KEY;
                break;

            case 25:
                errorCode = ErrorCode.BAD_SIGNATURE;
                break;

            case 26:
                errorCode = ErrorCode.BAD_SIGNATURE_FORMAT;
                break;

            case 27:
                errorCode = ErrorCode.BAD_SIGNATURE_MISS_ATTRIBUTE;
                break;

            case 28:
                errorCode = ErrorCode.BAD_SIGNATURE_NOT_SIGNED_ATTRIBUTE;
                break;

            case 29:
                errorCode = ErrorCode.ENCRYPT_ERROR;
                break;

            case 30:
                errorCode = ErrorCode.DECRYPT_ERROR;
                break;

            case 31:
                errorCode = ErrorCode.BAD_ENCRYPTION_FORMAT;
                break;

            case 32:
                errorCode = ErrorCode.NO_SECRET_KEY;
                break;

            case 33:
                errorCode = ErrorCode.NOT_ENCRYPTED;
                break;

            case 34:
                errorCode = ErrorCode.FILE_NOT_FOUND;
                break;

            case 35:
                errorCode = ErrorCode.FILE_NOT_SUPPORTED;
                break;

            case 36:
                errorCode = ErrorCode.DATABASE_CORRUPTION;
                break;

            case 37:
                errorCode = ErrorCode.RESTORE_IN_PROGRESS;
                break;

            case 38:
                errorCode = ErrorCode.ACCOUNT_RESTORED;
                break;

            case 39:
                errorCode = ErrorCode.EXISTS;
                break;

            case 40:
                errorCode = ErrorCode.KEYSTORE_ERROR;
                break;

            case 8:
            default:
                errorCode = ErrorCode.LIBRARY_TOO_OLD;
                break;
        }

        return errorCode;
    }

    /**
     * Indicate whether the error is a transient error and can be retried:
     * - TWINLIFE_OFFLINE and TIMEOUT_ERROR can be retried on a new server connection,
     * - SERVER_ERROR is transient and in general caused by a database connection issue
     * on its side.
     *
     * @return true if the error is transient.
     */
    public boolean isTransient() {

        return this == ErrorCode.TWINLIFE_OFFLINE || this == ErrorCode.TIMEOUT_ERROR || this == ErrorCode.SERVER_ERROR;
    }
}
