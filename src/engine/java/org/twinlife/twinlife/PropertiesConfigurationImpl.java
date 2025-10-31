/*
 *  Copyright (c) 2019-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Log;
import androidx.annotation.NonNull;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.Properties;

/**
 * Configuration implemented on top of Java properties.
 */
public class PropertiesConfigurationImpl implements ConfigurationService.Configuration {
    private static final String LOG_TAG = "PropertiesConfigurationImpl";

    @NonNull
    private final File mPath;
    private final Properties mSettings = new Properties();

    PropertiesConfigurationImpl(@NonNull File path) {

        mPath = path;
        if (mPath.exists()) {
            try (InputStream file = new FileInputStream(path)) {
                mSettings.load(file);

            } catch (IOException ex) {
                Log.e(LOG_TAG, "Cannot load " + mPath.getPath() + ": " + ex.getMessage());

            }
        }
    }

    @Override
    public boolean exists(String parameter) {

        return mSettings.contains(parameter);
    }

    @Override
    public int getInt(String parameter, int defaultValue) {

        if (mSettings.containsKey(parameter)) {
            String value = mSettings.getProperty(parameter);
            try {
                return Integer.parseInt(value);
            } catch (Exception ex) {
                Log.e(LOG_TAG, "Bad integer parameter " + parameter + ": " + value);

            }
        }
        return defaultValue;
    }

    @Override
    public void setInt(String parameter, int value) {

        mSettings.put(parameter, String.valueOf(value));
    }

    @Override
    public long getLong(@NonNull String parameter, long defaultValue) {

        if (mSettings.containsKey(parameter)) {
            String value = mSettings.getProperty(parameter);
            try {
                return Long.parseLong(value);
            } catch (Exception ex) {
                Log.e(LOG_TAG, "Bad long parameter " + parameter + ": " + value);

            }
        }
        return defaultValue;
    }

    @Override
    public long getLongConfig(@NonNull ConfigIdentifier config, long defaultValue) {

        return getLong(config.getParameterName(), defaultValue);
    }

    @Override
    public void setLongConfig(@NonNull ConfigIdentifier config, long value) {

        setStringConfig(config, String.valueOf(value));
    }

    @Override
    public void setLong(@NonNull String parameter, long value) {

        setString(parameter, String.valueOf(value));
    }

    @Override
    public float getFloatConfig(@NonNull ConfigIdentifier config, float defaultValue) {

        return defaultValue;
    }

    @Override
    public void setFloatConfig(@NonNull ConfigIdentifier config, float value) {

        setStringConfig(config, String.valueOf(value));
    }

    @Override
    public boolean getBoolean(String parameter, boolean defaultValue) {

        return defaultValue;
    }

    @Override
    public void setBoolean(String parameter, boolean value) {

        mSettings.put(parameter, String.valueOf(value));
    }

    @Override
    public String getString(String parameter, String defaultValue) {

        return mSettings.getProperty(parameter, defaultValue);
    }

    public void setString(String parameter, String value) {

        if (value == null) {
            mSettings.remove(parameter);
        } else {
            mSettings.put(parameter, value);
        }
    }

    @Override
    public String getStringConfig(@NonNull ConfigIdentifier config, String defaultValue) {

        return mSettings.getProperty(config.getParameterName(), defaultValue);
    }

    @Override
    public void setStringConfig(@NonNull ConfigIdentifier config, String value) {

        mSettings.setProperty(config.getParameterName(), value);
    }

    @Override
    public void removeConfig(@NonNull ConfigIdentifier config) {

        mSettings.remove(config.getParameterName());
    }

    @Override
    public void remove(@NonNull String name) {

        mSettings.remove(name);
    }

    @Override
    public synchronized void save() {

        try (Writer output = new PrintWriter(new FileOutputStream(mPath))) {

            mSettings.store(output, "");

        } catch (IOException ex) {
            Log.e(LOG_TAG, "Cannot save " + mPath.getPath() + ": " + ex.getMessage());
        }
    }

    public synchronized void delete() {

        if (mPath.exists() && !mPath.delete()) {
            Log.e(LOG_TAG, "Cannot delete file: " + mPath);
        }
    }

    @Override
    @NonNull
    public String toString() {

        return mPath.toString();
    }
}
