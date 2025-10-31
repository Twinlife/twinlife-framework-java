/*
 *  Copyright (c) 2014-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Chedi Baccari (Chedi.Baccari@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.util.Logger;

import java.security.Key;


/**
 * Android twinlife context with management of Android twinlife service.
 */
public class TwinlifeServiceConnectionImpl implements ServiceConnection {
    private static final String LOG_TAG = "TwinlifeServiceConnImpl";
    private static final boolean DEBUG = false;

    @NonNull
    private final TwinlifeContextImpl mTwinlifeContext;
    @NonNull
    private final Context mContext;
    @Nullable
    private TwinlifeService mTwinlifeService;
    @NonNull
    private final AndroidTwinlifeImpl mTwinlifeImpl;

    public TwinlifeServiceConnectionImpl(@NonNull TwinlifeContextImpl twinlifeContext, @NonNull Context context,
                                         @NonNull AndroidConfigurationServiceImpl configurationService) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinlifeServiceConnectionImpl: twinlifeContext=" + twinlifeContext + " context=" + context);
        }

        mTwinlifeContext = twinlifeContext;
        mContext = context;
        mTwinlifeImpl = new AndroidTwinlifeImpl(twinlifeContext, mContext);
        configurationService.initialize(mTwinlifeImpl);
        mTwinlifeImpl.onCreate();
    }

    //
    // Override TwinlifeContext methods
    //

    public final void start() {
        if (DEBUG) {
            Log.d(LOG_TAG, "start");
        }

        Thread.setDefaultUncaughtExceptionHandler((Thread thread, Throwable exception) -> {
            if (DEBUG) {
                Log.d(LOG_TAG, "uncaught exception", exception);
            }

            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "uncaught exception", exception);
            }

            ManagementService managementService = mTwinlifeImpl.getManagementService();
            if (managementService != null && mTwinlifeImpl.isConnected()) {
                mTwinlifeImpl.exception(TwinlifeAssertPoint.UNEXPECTED_EXCEPTION, exception, null);
            }

            // Give little time for the problem report to be sent without blocking.
            try {
                Thread.sleep(100);
            } catch (Exception ex) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Exception in sleep", ex);
                }
            }
            System.exit(2);
        });

        mTwinlifeContext.onServiceConnected(mTwinlifeImpl);
    }

    public final void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        if (mTwinlifeService != null) {
            mTwinlifeService.stop();
            mTwinlifeService = null;
        }

        // We must now exit because the UI must not access the Twinlife services anymore.
        // stop() is called after DeleteAccount() in the final UI step after the account is deleted.
        System.exit(0);
    }

    //
    // Implementation of ServiceConnection class
    //

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onServiceConnected: name=" + name + " service=" + service);
        }

        mTwinlifeService = ((TwinlifeService.LocalBinder) service).getService();
     }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onServiceDisconnected: name=" + name);
        }

        mTwinlifeContext.onServiceDisconnected();
    }

    @Nullable
    public static byte[] decrypt(@NonNull byte[] ivEncryptedData, int length) {

        return KeyChain.decrypt(KeyChain.getDefaultSecretKey(), ivEncryptedData, length);
    }
    //
    // Private Methods
    //

    private void restart() {
        if (DEBUG) {
            Log.d(LOG_TAG, "restart");
        }

        mTwinlifeContext.getJobService().scheduleJob("Twinlife", this::restartInternal, JobService.Priority.FOREGROUND);
    }

    private void restartInternal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "restartInternal");
        }

        try {
            if (mTwinlifeService == null) {
                mContext.startService(new Intent(mContext, TwinlifeService.class));
            }

            if (!mContext.bindService(new Intent(mContext, TwinlifeService.class), this, Context.BIND_AUTO_CREATE)) {

                Log.e(LOG_TAG, "bindService: bindService failed");
            }
        } catch (IllegalStateException exception) {
            Log.d(LOG_TAG, "Exception " + exception);
            restart();
        }
    }
}
