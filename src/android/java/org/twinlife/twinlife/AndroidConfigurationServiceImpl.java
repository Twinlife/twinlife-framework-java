/*
 *  Copyright (c) 2019-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Implementation of ConfigurationService for Android.
 */
public class AndroidConfigurationServiceImpl implements ConfigurationService {
    private static final String LOG_TAG = "AndroidConfServiceImpl";
    private static final boolean DEBUG = false;

    private final Context mContext;
    @Nullable
    private KeyChain mKeyChain;
    private Twinlife mTwinlife;

    public AndroidConfigurationServiceImpl(Context context) {
        if (DEBUG) {
            Log.d(LOG_TAG, "AndroidConfigurationServiceImpl");
        }

        mContext = context;
    }

    @Override
    public Configuration getConfiguration(String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfiguration name=" + name);
        }

        if (name == null || name.isEmpty()) {

            return new AndroidConfigurationImpl("", mContext.getSharedPreferences(mContext.getPackageName() + "_preferences", android.content.Context.MODE_PRIVATE));
        }

        return new AndroidConfigurationImpl(name, mContext.getSharedPreferences(name, android.content.Context.MODE_PRIVATE));
    }

    @Override
    public Configuration getConfiguration(@NonNull ConfigIdentifier config) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfiguration config=" + config);
        }

        return getConfiguration(config.getConfigName());
    }

    @Override
    public void deleteConfiguration(Configuration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteConfiguration configuration=" + configuration);
        }

        AndroidConfigurationImpl androidConfiguration = (AndroidConfigurationImpl)configuration;
        androidConfiguration.delete();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mContext.deleteSharedPreferences(androidConfiguration.getName());
        }
    }

    @Override
    @NonNull
    public SecuredConfiguration getSecuredConfiguration(@NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSecuredConfiguration name=" + name);
        }

        KeyChain keyChain = getKeyChain();
        byte[] data = keyChain.getKeyChainData(name);

        return new AndroidSecuredConfigurationImpl(name, data);
    }

    @Override
    public void saveSecuredConfiguration(SecuredConfiguration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveSecuredConfiguration configuration=" + configuration);
        }

        AndroidSecuredConfigurationImpl config = (AndroidSecuredConfigurationImpl)configuration;

        KeyChain keyChain = getKeyChain();
        if (config.getData() == null) {
            keyChain.removeKeyChain(config.getName());
        } else if (config.isCreated()) {
            keyChain.updateKeyChain(config.getName(), config.getData());
        } else {
            keyChain.createKeyChain(config.getName(), config.getData());
        }
    }

    @Override
    public void eraseAllSecuredConfiguration() {
        if (DEBUG) {
            Log.d(LOG_TAG, "eraseAllSecuredConfiguration");
        }

        KeyChain keyChain = getKeyChain();
        keyChain.removeAllKeyChain();
    }

    void initialize(@NonNull Twinlife twinlife) {
        if (DEBUG) {
            Log.d(LOG_TAG, "initialize twinlife=" + twinlife);
        }

        mTwinlife = twinlife;
        // Important note: we must not create the KeyChain instance here because the Twinlife
        // object is not fully initialized and assertions will not be taken into account.
    }

    @NonNull
    private KeyChain getKeyChain() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getKeyChain");
        }

        if (mKeyChain == null) {
            mKeyChain = new KeyChain(mContext, mTwinlife);
        }
        return mKeyChain;
    }
}
