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

import org.twinlife.twinlife.util.Utils;

import java.util.List;
import java.util.Objects;
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
        CRYPTO_SERVICE_ID,
        BACKUP_SERVICE_ID,
        SECURE_ROSTER_SERVICE_ID
    }

    long UNDEFINED_REQUEST_ID = -1L;
    long DEFAULT_REQUEST_ID = 0L;
    long DEFAULT_REQUEST_TIMEOUT = 20000; // 20s
    long CACHE_EXPIRE_TIME_OUT = 1000000000L * 3600; // 1 hour

    class BaseServiceConfiguration {

        public final BaseServiceId id;
        @NonNull
        public final String version;
        public boolean serviceOn;
        public final long cacheExpireTimeout;

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

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) return false;
            AttributeNameValue that = (AttributeNameValue) o;
            return Objects.equals(name, that.name) && Objects.equals(value, that.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value);
        }

        @NonNull
        @Override
        public String toString() {
            return "AttributeNameValue[" +
                    "name='" + name + '\'' +
                    ", value=" + value +
                    ']';
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

        @Override
        public boolean equals(Object o) {
            if (o instanceof AttributeNameUUIDValue) {
                return super.equals(o);
            }

            if (o instanceof AttributeNameImageIdValue) {
                // The avatar ID is stored in a twincode attribute list as its public/exported UUID during a backup,
                // which may cause false positives when checking twincode modifications during a backup verify or restore.
                String otherName = ((AttributeNameImageIdValue) o).name;
                ExportedImageId otherValue = (ExportedImageId) ((AttributeNameImageIdValue) o).value;

                return Objects.equals(name, otherName) && Objects.equals(value, otherValue.getExportedId());
            }

            return false;
        }
    }

    class AttributeNameImageIdValue extends AttributeNameValue {

        public AttributeNameImageIdValue(@NonNull String name, @NonNull ExportedImageId value) {

            super(name, value);
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof AttributeNameImageIdValue) {
                return super.equals(o);
            }

            if (o instanceof AttributeNameUUIDValue && value instanceof ExportedImageId) {
                // The avatar ID is stored in a twincode attribute list as its public/exported UUID during a backup,
                // which may cause false positives when checking twincode modifications during a backup verify or restore.
                String otherName = ((AttributeNameUUIDValue) o).name;
                UUID otherValue = (UUID) ((AttributeNameUUIDValue) o).value;

                return Objects.equals(name, otherName) && Objects.equals(((ExportedImageId) value).getExportedId(), otherValue);
            }

            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, ((ExportedImageId) value).getExportedId());
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
