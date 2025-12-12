/*
 *  Copyright (c) 2019-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import android.content.SharedPreferences;

public class AndroidConfigurationImpl implements ConfigurationService.Configuration {

    private final String mName;
    private final SharedPreferences mSharedPreferences;
    private SharedPreferences.Editor mEditor = null;

    AndroidConfigurationImpl(@NonNull String name, @NonNull SharedPreferences sharedPreferences) {

        mName = name;
        mSharedPreferences = sharedPreferences;
    }

    public String getName() {

        return mName;
    }

    @Override
    public boolean exists(String parameter) {

        return mSharedPreferences.contains(parameter);
    }

    @Override
    public int getInt(String parameter, int defaultValue) {

        try {
            return mSharedPreferences.getInt(parameter, defaultValue);

        } catch (ClassCastException ex) {

            return defaultValue;
        }
    }

    @Override
    public void setInt(String parameter, int value) {

        getEditor().putInt(parameter, value);
    }

    @Override
    public long getLong(@NonNull String parameter, long defaultValue) {

        try {
            return mSharedPreferences.getLong(parameter, defaultValue);

        } catch (ClassCastException ex) {

            return defaultValue;
        }
    }

    @Override
    public long getLongConfig(@NonNull ConfigIdentifier config, long defaultValue) {

        try {
            return mSharedPreferences.getLong(config.getParameterName(), defaultValue);

        } catch (ClassCastException ex) {

            return defaultValue;
        }
    }

    @Override
    public void setLongConfig(@NonNull ConfigIdentifier config, long value) {

        getEditor().putLong(config.getParameterName(), value);
    }

    @Override
    public void setLong(@NonNull String parameter, long value) {

        getEditor().putLong(parameter, value);
    }

    @Override
    public float getFloat(@NonNull String parameter, float defaultValue) {

        try {
            return mSharedPreferences.getFloat(parameter, defaultValue);

        } catch (ClassCastException ex) {

            return defaultValue;
        }
    }

    @Override
    public float getFloatConfig(@NonNull ConfigIdentifier config, float defaultValue) {

        try {
            return mSharedPreferences.getFloat(config.getParameterName(), defaultValue);

        } catch (ClassCastException ex) {

            return defaultValue;
        }
    }

    @Override
    public void setFloatConfig(@NonNull ConfigIdentifier config, float value) {

        getEditor().putFloat(config.getParameterName(), value);
    }

    @Override
    public void setFloat(@NonNull String parameter, float value) {

        getEditor().putFloat(parameter, value);
    }

    @Override
    public boolean getBoolean(String parameter, boolean defaultValue) {

        try {
            return mSharedPreferences.getBoolean(parameter, defaultValue);

        } catch (ClassCastException ex) {

            return defaultValue;
        }
    }

    @Override
    public void setBoolean(String parameter, boolean value) {

        getEditor().putBoolean(parameter, value);
    }

    @Override
    public String getString(String parameter, String defaultValue) {

        return mSharedPreferences.getString(parameter, defaultValue);
    }

    @Override
    public void setString(String parameter, String value) {

        getEditor().putString(parameter, value);
    }

    @Override
    public String getStringConfig(@NonNull ConfigIdentifier config, String defaultValue) {

        Class<?> clazz = config.getParameterClass();
        if (clazz == Integer.class) {
            return String.valueOf(getInt(config.getParameterName(), 0));
        }

        if (clazz == Long.class) {
            return String.valueOf(getLong(config.getParameterName(), 0));
        }

        if (clazz == Boolean.class) {
            return String.valueOf(getBoolean(config.getParameterName(), false));
        }

        if (clazz == Float.class) {
            return String.valueOf(getFloatConfig(config, 0.0f));
        }
        try {
            return mSharedPreferences.getString(config.getParameterName(), defaultValue);
        } catch (ClassCastException exception1) {
            try {
                long value = mSharedPreferences.getLong(config.getParameterName(), 0);
                return String.valueOf(value);
            } catch (ClassCastException exception2) {
                try {
                    float value = mSharedPreferences.getFloat(config.getParameterName(), (float) 0.0);
                    return String.valueOf(value);
                } catch (ClassCastException exception3) {
                    try {
                        boolean value = mSharedPreferences.getBoolean(config.getParameterName(), false);
                        return String.valueOf(value);

                    } catch (ClassCastException exception4) {
                        try {
                            int value = mSharedPreferences.getInt(config.getParameterName(), 0);
                            return String.valueOf(value);

                        } catch (ClassCastException exception5) {
                            return "";
                        }
                    }
                }
            }
        }
    }

    @Override
    public void setStringConfig(@NonNull ConfigIdentifier config, String value) {

        getEditor().putString(config.getParameterName(), value);
    }

    @Override
    public void removeConfig(@NonNull ConfigIdentifier config) {

        getEditor().remove(config.getParameterName());
    }

    @Override
    public void remove(@NonNull String parameter) {

        getEditor().remove(parameter);
    }

    @Override
    public void save() {

        if (mEditor != null) {
            mEditor.apply();
        }
    }

    void delete() {

        getEditor().clear().commit();
    }

    private SharedPreferences.Editor getEditor() {

        if (mEditor == null) {
            mEditor = mSharedPreferences.edit();
        }
        return mEditor;
    }
}
