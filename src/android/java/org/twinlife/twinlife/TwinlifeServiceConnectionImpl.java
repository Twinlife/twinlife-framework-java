/*
 *  Copyright (c) 2014-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Chedi Baccari (Chedi.Baccari@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.content.Context;
import androidx.annotation.NonNull;

import android.util.Log;

import org.twinlife.twinlife.util.Logger;


/**
 * Android twinlife context with management of Android twinlife service.
 */
public class TwinlifeServiceConnectionImpl {
    private static final String LOG_TAG = "TwinlifeServiceConnImpl";
    private static final boolean DEBUG = false;

    @NonNull
    private final TwinlifeContextImpl mTwinlifeContext;
    @NonNull
    private final AndroidTwinlifeImpl mTwinlifeImpl;

    public TwinlifeServiceConnectionImpl(@NonNull TwinlifeContextImpl twinlifeContext, @NonNull Context context,
                                         @NonNull AndroidConfigurationServiceImpl configurationService) {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinlifeServiceConnectionImpl: twinlifeContext=" + twinlifeContext + " context=" + context);
        }

        mTwinlifeContext = twinlifeContext;
        mTwinlifeImpl = new AndroidTwinlifeImpl(twinlifeContext, context);
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
                Logger.exception(LOG_TAG, exception, "uncaught exception", exception.getMessage());
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
                    Logger.exception(LOG_TAG, ex, "Exception in sleep", ex.getMessage());
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

        mTwinlifeImpl.stop();

        // We must now exit because the UI must not access the Twinlife services anymore.
        // stop() is called after DeleteAccount() in the final UI step after the account is deleted.
        System.exit(0);
    }
}
