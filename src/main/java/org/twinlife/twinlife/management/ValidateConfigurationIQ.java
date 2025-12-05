/*
 *  Copyright (c) 2021-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.management;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.ApplicationState;

import java.util.Map;
import java.util.UUID;

/**
 * Validate configuration IQ.
 *
 * Schema version 1 AND Schema version 2
 * <pre>
 * {
 *  "schemaId":"437466BB-B2AC-4A53-9376-BFE263C98220",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ValidateConfigurationIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"deviceState", "type":"int"},
 *     {"name":"environmentId", [null, "type":"uuid"]},
 *     {"name":"pushVariant", [null, "type":"string"]},
 *     {"name":"pushToken", [null, "type":"string"]},
 *     {"name":"pushRemoteToken", [null, "type":"string"]},
 *     {"name":"serviceCount", "type":"int"}, [
 *        {"name":"serviceName", "type":"string"},
 *        {"name":"serviceVersion", "type":"string"}
 *     ]},
 *     {"name":"hardwareBrand", "type":"string"},
 *     {"name":"hardwareModel", "type":"string"},
 *     {"name":"osName", "type":"string"},
 *     {"name":"locale", "type":"string"},
 *     {"name":"capabilities", "type":"string"},
 *     {"name":"configCount", "type":"int"}, [
 *        {"name":"configName", "type":"string"},
 *        {"name":"configValue", "type":"string"}
 *     ]}
 *  ]
 * }
 *
 * </pre>
 */
class ValidateConfigurationIQ extends BinaryPacketIQ {

    private static class ValidateConfigurationIQSerializer extends BinaryPacketIQSerializer {

        ValidateConfigurationIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, ValidateConfigurationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ValidateConfigurationIQ validateConfigurationIQ = (ValidateConfigurationIQ) object;

            switch (validateConfigurationIQ.deviceState) {
                case FOREGROUND:
                    encoder.writeInt(0);
                    break;

                case BACKGROUND:
                case BACKGROUND_IDLE:
                case SUSPENDED:
                    encoder.writeInt(1);
                    break;

                case WAKEUP_PUSH:
                    encoder.writeInt(2);
                    break;

                case WAKEUP_ALARM:
                    encoder.writeInt(3);
                    break;
            }
            encoder.writeOptionalUUID(validateConfigurationIQ.environmentId);
            encoder.writeOptionalString(validateConfigurationIQ.pushVariant);
            encoder.writeOptionalString(validateConfigurationIQ.pushToken);
            encoder.writeOptionalString(validateConfigurationIQ.pushRemoteToken);

            Map<String, String> services = validateConfigurationIQ.services;
            encoder.writeInt(services.size());
            for (Map.Entry<String, String> item : services.entrySet()) {
                encoder.writeString(item.getKey());
                encoder.writeString(item.getValue());
            }

            encoder.writeString(validateConfigurationIQ.hardwareBrand);
            encoder.writeString(validateConfigurationIQ.hardwareModel);
            encoder.writeString(validateConfigurationIQ.osName);
            encoder.writeString(validateConfigurationIQ.locale);
            encoder.writeString(validateConfigurationIQ.capabilities);
            Map<String, String> configs = validateConfigurationIQ.configs;
            encoder.writeInt(configs.size());
            for (Map.Entry<String, String> item : configs.entrySet()) {
                encoder.writeString(item.getKey());
                encoder.writeString(item.getValue());
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new ValidateConfigurationIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final ApplicationState deviceState;
    @Nullable
    final UUID environmentId;
    @Nullable
    final String pushVariant;
    @Nullable
    final String pushToken;
    @Nullable
    final String pushRemoteToken;
    @NonNull
    final Map<String, String> services;
    @NonNull
    final String hardwareModel;
    @NonNull
    final String hardwareBrand;
    @NonNull
    final String osName;
    @NonNull
    final String locale;
    @NonNull
    final String capabilities;
    @NonNull
    final Map<String, String> configs;

    ValidateConfigurationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull ApplicationState deviceState,
                            @Nullable UUID environmentId, @Nullable String pushVariant, @Nullable String pushToken,
                            @NonNull Map<String, String> services,
                            @NonNull String hardwareBrand, @NonNull String hardwareModel, @NonNull String osName,
                            @NonNull String locale, @NonNull String capabilities, @NonNull Map<String, String> configs) {

        super(serializer, requestId);

        this.deviceState = deviceState;
        this.environmentId = environmentId;
        this.pushVariant = pushVariant;
        this.pushToken = pushToken;
        this.pushRemoteToken = null;
        this.services = services;
        this.hardwareBrand = hardwareBrand;
        this.hardwareModel = hardwareModel;
        this.osName = osName;
        this.locale = locale;
        this.capabilities = capabilities;
        this.configs = configs;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" state=");
            stringBuilder.append(deviceState);
            stringBuilder.append(" environmentId=");
            stringBuilder.append(environmentId);
            stringBuilder.append(" pushVariant=");
            stringBuilder.append(pushVariant);
            stringBuilder.append(" pushToken=");
            stringBuilder.append(pushToken);
            stringBuilder.append(" services=");
            stringBuilder.append(services);
            stringBuilder.append(" hardwareBrand=");
            stringBuilder.append(hardwareBrand);
            stringBuilder.append(" hardwareModel=");
            stringBuilder.append(hardwareModel);
            stringBuilder.append(" osName=");
            stringBuilder.append(osName);
            stringBuilder.append(" locale=");
            stringBuilder.append(locale);
            stringBuilder.append(" capabilities=");
            stringBuilder.append(capabilities);
            stringBuilder.append(" configs=");
            stringBuilder.append(configs);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("ValidateConfigurationIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
