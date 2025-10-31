/*
 *  Copyright (c) 2012-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Tengfei Wang (Tengfei.Wang@twinlife-systems.com)
 *   Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *   Xiaobo Xie (Xiaobo.Xie@twinlife-systems.com)
 *   Chedi Baccari (Chedi.Baccari@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

/**
 * Android Service that runs Twinlife.
 */
public class TwinlifeService extends Service {
    private static final String LOG_TAG = "TwinlifeService";
    private static final boolean DEBUG = false;

    class LocalBinder extends Binder {

        public TwinlifeService getService() {

            return TwinlifeService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    public TwinlifeService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "TwinlifeService");
        }
    }

    public void stop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop");
        }

        stopSelf();
    }

    //
    // Override Service methods
    //

    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onStartCommand: intent=" + intent + " flags" + flags + " startId=" + startId);
        }

        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBind intent=" + intent);
        }

        return mBinder;
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDestroy");
        }
    }
}
