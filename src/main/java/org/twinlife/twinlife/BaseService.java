/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.util.Utils;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
public interface BaseService <Observer extends BaseService.ServiceObserver> {
    enum BaseServiceId {
        ACCOUNT_SERVICE_ID,
        CONVERSATION_SERVICE_ID,
        CONNECTIVITY_SERVICE_ID,
        MANAGEMENT_SERVICE_ID,
        NOTIFICATION_SERVICE_ID,
        PEER_CONNECTION_SERVICE_ID,
        REPOSITORY_SERVICE_ID,
        TWINCODE_FACTORY_SERVICE_ID,
        TWINCODE_INBOUND_SERVICE_ID,
        TWINCODE_OUTBOUND_SERVICE_ID,
        IMAGE_SERVICE_ID,
        ACCOUNT_MIGRATION_SERVICE_ID,
        PEER_CALL_SERVICE_ID,
        CRYPTO_SERVICE_ID
    }

    long UNDEFINED_REQUEST_ID = -1L;
    long DEFAULT_REQUEST_ID = 0L;
    long DEFAULT_REQUEST_TIMEOUT = 20000; // 20s
    long CACHE_EXPIRE_TIME_OUT = 1000000000L * 3600; // 1 hour

    enum ErrorCode {
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
        EXISTS;

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

                case 8:
                    errorCode = ErrorCode.LIBRARY_TOO_OLD;
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

                default:
                    errorCode = ErrorCode.LIBRARY_TOO_OLD;
                    break;
            }

            return errorCode;
        }
    }

    class BaseServiceConfiguration {

        public final BaseServiceId id;
        @NonNull
        public final String version;
        public boolean serviceOn;
        public long cacheExpireTimeout;

        public BaseServiceConfiguration(BaseServiceId baseServiceId, @NonNull String version, boolean serviceOn) {

            id = baseServiceId;
            this.version = version;
            this.serviceOn = serviceOn;
            cacheExpireTimeout = CACHE_EXPIRE_TIME_OUT;
        }
    }

    class ServiceStats {
        public long sendPacketCount;
        public long sendErrorCount;
        public long sendDisconnectedCount;
        public long sendTimeoutCount;
        public long databaseFullCount;
        public long databaseIOCount;
        public long databaseErrorCount;
    }

    abstract class AttributeNameValue {

        @NonNull
        public final String name;
        @NonNull
        public Object value;

        public AttributeNameValue(@NonNull String name, @NonNull Object value) {

            this.name = name;
            this.value = value;
        }

        @Nullable
        public static AttributeNameValue getAttribute(@NonNull List<AttributeNameValue> list, @NonNull String name) {

            for (AttributeNameValue attribute : list) {
                if (name.equals(attribute.name)) {
                    return attribute;
                }
            }
            return null;
        }

        @Nullable
        public static String getStringAttribute(@NonNull List<AttributeNameValue> list, @NonNull String name) {

            final AttributeNameValue attribute = getAttribute(list, name);
            if (attribute != null && (attribute.value instanceof String)) {
                return (String) attribute.value;
            } else {
                return null;
            }
        }

        @Nullable
        public static UUID getUUIDAttribute(@NonNull List<AttributeNameValue> list, @NonNull String name) {

            final AttributeNameValue attribute = getAttribute(list, name);
            if (attribute != null && (attribute.value instanceof UUID)) {
                return (UUID) attribute.value;
            } else if (attribute != null && (attribute.value instanceof String)) {
                return Utils.toUUID((String) attribute.value);
            } else {
                return null;
            }
        }

        public static long getLongAttribute(@NonNull List<AttributeNameValue> list, @NonNull String name, long defaultValue) {

            final AttributeNameValue attribute = getAttribute(list, name);
            if (attribute != null && (attribute.value instanceof Long)) {
                return (Long) attribute.value;
            } else {
                return defaultValue;
            }
        }

        @Nullable
        public static AttributeNameValue removeAttribute(@NonNull List<AttributeNameValue> list, @NonNull String name) {

            for (int i = 0; i < list.size(); i++) {
                AttributeNameValue attribute = list.get(i);
                if (name.equals(attribute.name)) {
                    list.remove(i);
                    return attribute;
                }
            }
            return null;
        }
    }

    class AttributeNameBooleanValue extends AttributeNameValue {

        public AttributeNameBooleanValue(@NonNull String name, @NonNull Boolean value) {

            super(name, value);
        }
    }

    class AttributeNameLongValue extends AttributeNameValue {

        public AttributeNameLongValue(@NonNull String name, @NonNull Long value) {

            super(name, value);
        }

        public static void add(@NonNull List<AttributeNameValue> list, @NonNull String name, long value) {

            list.add(new AttributeNameLongValue(name, value));
        }
    }

    class AttributeNameStringValue extends AttributeNameValue {

        public AttributeNameStringValue(@NonNull String name, @NonNull String value) {

            super(name, value);
        }

        public static void add(@NonNull List<AttributeNameValue> list, @NonNull String name, @NonNull String value) {

            list.add(new AttributeNameStringValue(name, value));
        }
    }

    class AttributeNameUUIDValue extends AttributeNameValue {

        public AttributeNameUUIDValue(@NonNull String name, @NonNull UUID value) {

            super(name, value);
        }

        public static void add(@NonNull List<AttributeNameValue> list, @NonNull String name, @NonNull UUID value) {

            list.add(new AttributeNameUUIDValue(name, value));
        }
    }

    class AttributeNameImageIdValue extends AttributeNameValue {

        public AttributeNameImageIdValue(@NonNull String name, @NonNull ExportedImageId value) {

            super(name, value);
        }
    }

    class AttributeNameVoidValue extends AttributeNameValue {

        public AttributeNameVoidValue(@NonNull String name) {

            super(name, new Object());
        }
    }

    class AttributeNameBytesValue extends AttributeNameValue {

        public AttributeNameBytesValue(@NonNull String name, @NonNull byte[] value) {

            super(name, value);
        }
    }

    class AttributeNameListValue extends AttributeNameValue {

        public AttributeNameListValue(@NonNull String name, @NonNull List<AttributeNameValue> value) {

            super(name, value);
        }

        public static void add(@NonNull List<AttributeNameValue> list, @NonNull String name, @NonNull List<AttributeNameValue> value) {

            list.add(new AttributeNameListValue(name, value));
        }
    }

    interface ServiceObserver {

        default void onError(long requestId, ErrorCode errorCode, @Nullable String errorParameter) {}
    }

    class DefaultServiceObserver implements ServiceObserver {
    }

    BaseServiceId getId();

    @NonNull
    String getVersion();

    boolean isServiceOn();

    boolean isSignIn();

    boolean isTwinlifeOnline();

    void addServiceObserver(@NonNull Observer serviceObserver);

    void removeServiceObserver(@NonNull Observer serviceObserver);

    @NonNull
    String getServiceName();

    @NonNull
    ServiceStats getServiceStats();
}
