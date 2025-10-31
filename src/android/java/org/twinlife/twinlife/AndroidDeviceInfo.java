/*
 *  Copyright (c) 2018-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.app.ActivityManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;

/**
 * Provide information about the device.
 */
public class AndroidDeviceInfo {

    protected final Context mContext;

    public AndroidDeviceInfo(@NonNull Context context) {

        mContext = context;
    }

    public int getAppStandbyBucket() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            UsageStatsManager usageStatsManager = (UsageStatsManager) mContext.getSystemService(Context.USAGE_STATS_SERVICE);

            if (usageStatsManager != null) {
                return usageStatsManager.getAppStandbyBucket();
            }
        }
        return 0;
    }

    public boolean isBackgroundRestricted() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

            if (activityManager != null) {
                return activityManager.isBackgroundRestricted();
            }
        }

        return false;
    }

    public boolean isIgnoringBatteryOptimizations() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

            if (powerManager != null) {
                return powerManager.isIgnoringBatteryOptimizations(mContext.getPackageName());
            }
        }

        return true;
    }

    public boolean isNetworkRestricted() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (connectivityManager != null) {
                return connectivityManager.getRestrictBackgroundStatus() == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED;
            }
        }

        return false;
    }

    public boolean isLowRamDevice() {

        ActivityManager activityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

        if (activityManager != null) {
            return activityManager.isLowRamDevice();
        }

        return true;
    }
}
