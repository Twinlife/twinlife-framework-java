/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Configuration identifier that describes a configuration parameter which is recognized by the
 * account migration service and can migrate between two devices.
 * <p>
 * The UUID identifier is unique and must be the same between different implementations (Android, iOS, Desktop).
 * <p>
 * The configuration name and parameter name are specific to the implementation.
 * They can be different between Android, iOS and Desktop.
 * <p>
 * On Android, we must use the `ConfigurationService` to load and save values buy on iOS we don't have
 * the constraint of Android application Context and we can get/set on the configuration instance directly.
 */
@SuppressWarnings("rawtypes")
public class ConfigIdentifier {

    private static final Map<UUID, ConfigIdentifier> sIdentifiers = new HashMap<>();
    @NonNull
    private final String mGroupName;
    @NonNull
    private final String mParameterName;
    @NonNull
    private final UUID mIdentifier;
    @Nullable
    private final Class mClass;

    /**
     * Get the list of configuration identifiers indexed by their UUIDs.
     *
     * @return map configuration identifiers.
     */
    public static Map<UUID, ConfigIdentifier> getConfigs() {

        return sIdentifiers;
    }

    public ConfigIdentifier(@NonNull String groupName, @NonNull String configName, @NonNull String identifier) {

        mGroupName = groupName;
        mParameterName = configName;
        mIdentifier = UUID.fromString(identifier);
        mClass = null;

        ConfigIdentifier old = sIdentifiers.put(mIdentifier, this);
        if (old != null) {
            throw new IllegalArgumentException("Identifier " + identifier + " already registered");
        }
    }

    public ConfigIdentifier(@NonNull String groupName, @NonNull String configName, @NonNull String identifier, @NonNull Class clazz) {

        mGroupName = groupName;
        mParameterName = configName;
        mIdentifier = UUID.fromString(identifier);
        mClass = clazz;

        ConfigIdentifier old = sIdentifiers.put(mIdentifier, this);
        if (old != null) {
            throw new IllegalArgumentException("Identifier " + identifier + " already registered");
        }
    }

    @NonNull
    public String getConfigName() {

        return mGroupName;
    }

    @NonNull
    public String getParameterName() {

        return mParameterName;
    }

    @Nullable
    public Class getParameterClass() {

        return mClass;
    }
}
