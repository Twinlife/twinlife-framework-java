/*
 *  Copyright (c) 2017-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

/*
 * <pre>
 *
 * Schema version 2
 *  Date: 2019/10/07
 *
 * {
 *  "type":"record",
 *  "name":"TwinlifeSecuredConfiguration",
 *  "namespace":"org.twinlife.schemas",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"databaseKey", [null, "type":"string"]}
 *   {"name":"deviceIdentifier", [null, "type":"string"]}
 * }
 *
 * Schema version 1
 *  Date: 2017/06/21
 *
 * {
 *  "type":"record",
 *  "name":"TwinlifeSecuredConfiguration",
 *  "namespace":"org.twinlife.schemas",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"databaseKey", [null, "type":"string"]}
 * }
 * *
 * </pre>
 */

class TwinlifeSecuredConfiguration {
    private static final String LOG_TAG = "TwinlifeSecuredConfi...";
    private static final boolean DEBUG = false;

    private static final String TWINLIFE_SECURED_CONFIGURATION_KEY = "TwinlifeSecuredConfiguration";
    private static final int SERIALIZER_BUFFER_DEFAULT_SIZE = 1024;

    private static final UUID SCHEMA_ID = UUID.fromString("0e20d024-2dcb-4a60-9331-216849fc3065");
    private static final int SCHEMA_VERSION_2 = 2;
    private static final int SCHEMA_VERSION_1 = 1;
    private static final TwinlifeSecuredConfigurationSerializer_2 SERIALIZER_2 = new TwinlifeSecuredConfigurationSerializer_2();
    private static final TwinlifeSecuredConfigurationSerializer_1 SERIALIZER_1 = new TwinlifeSecuredConfigurationSerializer_1();

    private static class TwinlifeSecuredConfigurationSerializer_2 extends Serializer {

        TwinlifeSecuredConfigurationSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, TwinlifeSecuredConfiguration.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            TwinlifeSecuredConfiguration securedConfiguration = (TwinlifeSecuredConfiguration) object;
            if (securedConfiguration.databaseKey == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeString(securedConfiguration.databaseKey);
            }
            encoder.writeString(securedConfiguration.deviceIdentifier);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            String databaseKey = null;
            int position = decoder.readEnum();
            if (position == 1) {
                databaseKey = decoder.readString();
            }
            String deviceIdentifier = decoder.readString();

            return new TwinlifeSecuredConfiguration(databaseKey, deviceIdentifier, false);
        }
    }

    private static class TwinlifeSecuredConfigurationSerializer_1 extends Serializer {

        TwinlifeSecuredConfigurationSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, TwinlifeSecuredConfiguration.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            TwinlifeSecuredConfiguration securedConfiguration = (TwinlifeSecuredConfiguration) object;
            if (securedConfiguration.databaseKey == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeString(securedConfiguration.databaseKey);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            String databaseKey = null;
            int position = decoder.readEnum();
            if (position == 1) {
                databaseKey = decoder.readString();
            }
            String deviceIdentifier = UUID.randomUUID().toString();

            return new TwinlifeSecuredConfiguration(databaseKey, deviceIdentifier, false);
        }
    }

    @SuppressWarnings("WeakerAccess")
    final String databaseKey;
    @NonNull
    final String deviceIdentifier;
    final boolean createdKey;

    private TwinlifeSecuredConfiguration(String databaseKey, @NonNull String deviceIdentifier, boolean createdKey) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinlifeSecuredConfiguration: databaseKey=" + databaseKey + " deviceIdentifier=" + deviceIdentifier);
        }

        this.databaseKey = databaseKey;
        this.deviceIdentifier = deviceIdentifier;
        this.createdKey = createdKey;
    }

    @Nullable
    public static TwinlifeSecuredConfiguration init(SerializerFactory serializerFactory, ConfigurationService configurationService, TwinlifeConfiguration twinlifeConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "init: serializerFactory=" + serializerFactory + " twinlifeConfiguration=" + twinlifeConfiguration);
        }

        ConfigurationService.SecuredConfiguration config = configurationService.getSecuredConfiguration(TWINLIFE_SECURED_CONFIGURATION_KEY);
        byte[] content = config.getData();
        if (content != null) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
            BinaryDecoder binaryDecoder = new BinaryDecoder(inputStream);
            try {
                UUID schemaId = binaryDecoder.readUUID();
                int schemaVersion = binaryDecoder.readInt();

                if (TwinlifeSecuredConfiguration.SCHEMA_ID.equals(schemaId)) {
                    if (TwinlifeSecuredConfiguration.SCHEMA_VERSION_2 == schemaVersion) {
                        return (TwinlifeSecuredConfiguration) TwinlifeSecuredConfiguration.SERIALIZER_2.deserialize(serializerFactory, binaryDecoder);
                    }
                    if (TwinlifeSecuredConfiguration.SCHEMA_VERSION_1 == schemaVersion) {
                        TwinlifeSecuredConfiguration twinlifeSecuredConfiguration = (TwinlifeSecuredConfiguration) TwinlifeSecuredConfiguration.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);

                        // Save configuration because we have a new deviceIdentifier that must be saved.
                        try {
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
                            BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
                            TwinlifeSecuredConfiguration.SERIALIZER_2.serialize(serializerFactory, binaryEncoder, twinlifeSecuredConfiguration);
                            config.setData(outputStream.toByteArray());
                            configurationService.saveSecuredConfiguration(config);

                        } catch (Exception exception) {
                            if (Logger.ERROR) {
                                Logger.error(LOG_TAG, "init: serialize", exception);
                            }
                        }
                        return twinlifeSecuredConfiguration;
                    }
                }
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "init: deserialize", exception);
                }
            }
        }

        TwinlifeSecuredConfiguration twinlifeSecuredConfiguration = new TwinlifeSecuredConfiguration(UUID.randomUUID().toString(), UUID.randomUUID().toString(), true);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
        try {
            TwinlifeSecuredConfiguration.SERIALIZER_2.serialize(serializerFactory, binaryEncoder, twinlifeSecuredConfiguration);
            config.setData(outputStream.toByteArray());
            configurationService.saveSecuredConfiguration(config);

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "init: serialize", exception);
            }
            return null;
        }

        // New key created
        return twinlifeSecuredConfiguration;
    }

    void erase(ConfigurationService configurationService) {
        if (DEBUG) {
            Log.d(LOG_TAG, "erase");
        }

        ConfigurationService.SecuredConfiguration config = configurationService.getSecuredConfiguration(TWINLIFE_SECURED_CONFIGURATION_KEY);

        // Erase everything from the keychain to make sure we don't try to use the device secure information again.
        config.setData(null);
        configurationService.saveSecuredConfiguration(config);
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "TwinlifeSecuredConfiguration:\n" +
                " databaseKey: " + databaseKey + "\n" +
                " deviceIdentifier: " + deviceIdentifier + "\n";
    }
}
