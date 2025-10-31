/*
 *  Copyright (c) 2020 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Settings IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"09557d03-3af7-4151-aa60-c6a4b992e18b",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SettingsIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"hasPeerSettings", "type":"boolean"},
 *     {"name":"count", "type":"int"},
 *     [{"name":"settingId", "type":"uuid"},
 *      {"name":"value", "type":"string"}]
 *  ]
 * }
 *
 * </pre>
 */
class SettingsIQ extends BinaryPacketIQ {

    private static final int MAX_SIZE_PER_SETTING = 256;

    static class SettingsIQSerializer extends BinaryPacketIQSerializer {

        SettingsIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, SettingsIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            SettingsIQ settingsIQ = (SettingsIQ) object;

            encoder.writeBoolean(settingsIQ.hasPeerSettings);
            encoder.writeInt(settingsIQ.settings.size());
            for (Map.Entry<UUID, String> setting : settingsIQ.settings.entrySet()) {
                encoder.writeUUID(setting.getKey());
                encoder.writeString(setting.getValue());
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            boolean hasPeerSettings = decoder.readBoolean();

            Map<UUID, String> settings = new HashMap<>();
            int count = decoder.readInt();
            while (count > 0) {
                UUID identifier = decoder.readUUID();
                String value = decoder.readString();

                settings.put(identifier, value);
                count--;
            }

            return new SettingsIQ(this, serviceRequestIQ.getRequestId(), hasPeerSettings, settings);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new SettingsIQSerializer(schemaId, schemaVersion);
    }

    final boolean hasPeerSettings;
    @NonNull
    final Map<UUID, String> settings;

    SettingsIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, boolean hasPeerSettings, @NonNull Map<UUID, String> settings) {

        super(serializer, requestId);

        this.hasPeerSettings = hasPeerSettings;
        this.settings = settings;
    }

    protected int getBufferSize() {

        return SERIALIZER_BUFFER_DEFAULT_SIZE + settings.size() * MAX_SIZE_PER_SETTING;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" count=");
            stringBuilder.append(settings.size());
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("SettingsIQ\n");
            appendTo(stringBuilder);
        }
        return stringBuilder.toString();
    }
}
