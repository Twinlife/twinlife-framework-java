/*
 *  Copyright (c) 2019-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

/**
 * Configuration service to load and store various configuration parameters.
 */
public interface ConfigurationService {

    /**
     * Property based configuration stored in a private storage (may not be secure).
     */
    interface Configuration {

        boolean exists(String parameter);

        int getInt(String parameter, int defaultValue);

        void setInt(String parameter, int value);

        long getLong(@NonNull String parameter, long defaultValue);

        long getLongConfig(@NonNull ConfigIdentifier config, long defaultValue);

        void setLongConfig(@NonNull ConfigIdentifier config, long value);

        void setLong(@NonNull String parameter, long value);

        float getFloatConfig(@NonNull ConfigIdentifier config, float defaultValue);

        void setFloatConfig(@NonNull ConfigIdentifier config, float value);

        boolean getBoolean(String parameter, boolean defaultValue);

        void setBoolean(String parameter, boolean value);

        String getString(String parameter, String defaultValue);

        void setString(String parameter, String value);

        String getStringConfig(@NonNull ConfigIdentifier config, String defaultValue);

        void setStringConfig(@NonNull ConfigIdentifier config, String value);

        void removeConfig(@NonNull ConfigIdentifier config);

        void remove(@NonNull String name);

        void save();
    }

    /**
     * Binary configuration stored in a secure storage.
     */
    interface SecuredConfiguration {

        String getName();

        byte[] getData();

        void setData(byte[] data);
    }

    Configuration getConfiguration(String name);

    Configuration getConfiguration(@NonNull ConfigIdentifier config);

    void deleteConfiguration(Configuration configuration);

    @NonNull
    SecuredConfiguration getSecuredConfiguration(String name);

    void saveSecuredConfiguration(SecuredConfiguration configuration);

    void eraseAllSecuredConfiguration();
}
