/*
 *  Copyright (c) 2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Log;
import androidx.annotation.NonNull;
import org.libwebsockets.api.ProxyDescriptor;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration service that uses Java property files to store the configuration.
 */
public class PropertiesConfigurationServiceImpl implements ConfigurationService {
    private static final String LOG_TAG = "PropertiesConfigurationServiceImpl";
    private static final boolean DEBUG = false;

    private final Map<String, PropertiesConfigurationImpl> mConfigurations;
    @NonNull
    private final File mRootDirectory;
    private final String mApplicationId;
    private final String mServiceId;
    private final String mApplicationVersion;
    private ProxyDescriptor mProxyDescriptor;

    public PropertiesConfigurationServiceImpl(@NonNull String name, @NonNull File directory) {

        mConfigurations = new HashMap<>();
        mRootDirectory = directory;
        if (!mRootDirectory.exists()) {
            if (!mRootDirectory.mkdirs()) {
                Log.e(LOG_TAG, "Cannot create config directory: " + directory);
            }
        }

        Properties props = new Properties();

        String propFile = name + ".properties";
        try (InputStream propStream = PropertiesConfigurationServiceImpl.class.getClassLoader().getResourceAsStream(propFile)) {
            props.load(propStream);
        } catch (IOException e) {
            Log.e("Could not load properties from {}", propFile, e);
        }

        mApplicationId = props.getProperty("application.id", "");
        mServiceId = props.getProperty("service.id", "");
        mApplicationVersion = props.getProperty("application.version", "");
    }

    public String getApplicationId() {

        return mApplicationId;
    }

    public String getServiceId() {

        return mServiceId;
    }

    public String getApplicationVersion() {

        return mApplicationVersion;
    }

    public String getName() {

        File parent = mRootDirectory.getParentFile();
        return parent.getName();
    }

    @Override
    public Configuration getConfiguration(String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfiguration");
        }

        PropertiesConfigurationImpl configuration;
        synchronized (mConfigurations) {
            configuration = mConfigurations.get(name);
            if (configuration == null) {
                configuration = new PropertiesConfigurationImpl(new File(mRootDirectory, name + ".properties"));
                mConfigurations.put(name, configuration);
            }
        }
        return configuration;
    }

    @Override
    public Configuration getConfiguration(@NonNull ConfigIdentifier config) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfiguration");
        }

        return getConfiguration(config.getConfigName());
    }

    @Override
    public ProxyDescriptor getProxyDescriptor() {

        return mProxyDescriptor;
    }

    @Override
    public void setProxyDescriptor(ProxyDescriptor proxyDescriptor) {

        mProxyDescriptor = proxyDescriptor;
    }

    @Override
    public void deleteConfiguration(Configuration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteConfiguration configuration=" + configuration);
        }

        PropertiesConfigurationImpl propertiesConfigurationImpl = (PropertiesConfigurationImpl)configuration;
        propertiesConfigurationImpl.delete();
    }

    @Override
    public SecuredConfiguration getSecuredConfiguration(String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSecuredConfiguration");
        }

        return new FileSecuredConfigurationImpl(new File(mRootDirectory, name + ".dat"));
    }

    @Override
    public void saveSecuredConfiguration(SecuredConfiguration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveSecuredConfiguration");
        }

        FileSecuredConfigurationImpl fileSecuredConfiguration = (FileSecuredConfigurationImpl) configuration;
        fileSecuredConfiguration.save();
    }

    @Override
    public void eraseAllSecuredConfiguration() {

    }
}
