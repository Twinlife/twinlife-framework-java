/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.job;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import org.twinlife.twinlife.AndroidDeviceInfo;
import org.twinlife.twinlife.DeviceInfo;

import androidx.annotation.NonNull;

/**
 * Provide information about the device.
 */
public class DeviceInfoImpl extends AndroidDeviceInfo implements DeviceInfo {

    private final long mForegroundTime;
    private final long mBackgroundTime;
    private final float mBatteryPercent;
    private final boolean mIsCharging;
    private final long mFCMCount;
    private final long mFCMDowngradeCount;
    private final long mFCMTotalDelay;
    private final long mAlarmCount;
    private final long mNetworkLockCount;
    private final long mProcessingLockCount;
    private final long mInteractiveLockCount;

    DeviceInfoImpl(@NonNull Application application, long foregroundTime, long backgroundTime,
                   long fcmCount, long fcmDowngradeCount, long fcmTotalDelay, long alarmCount,
                   long networkLockCount, long processingLockCount, long interactiveLockCount) {
        super(application);

        mForegroundTime = foregroundTime;
        mBackgroundTime = backgroundTime;
        mFCMCount = fcmCount;
        mFCMDowngradeCount = fcmDowngradeCount;
        mFCMTotalDelay = fcmTotalDelay;
        mAlarmCount = alarmCount;
        mNetworkLockCount = networkLockCount;
        mProcessingLockCount = processingLockCount;
        mInteractiveLockCount = interactiveLockCount;

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = application.registerReceiver(null, ifilter);
        if (batteryStatus != null) {

            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            mBatteryPercent = level / (float) (scale);
            mIsCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;

        } else {

            mBatteryPercent = (float) 0.0;
            mIsCharging = false;
        }
    }

    @Override
    public long getForegroundTime() {

        return mForegroundTime;
    }

    @Override
    public long getBackgroundTime() {

        return mBackgroundTime;
    }

    @Override
    public float getBatteryLevel() {

        return mBatteryPercent;
    }

    @Override
    public boolean isCharging() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            BatteryManager batteryManager = (BatteryManager) mContext.getSystemService(Context.BATTERY_SERVICE);

            if (batteryManager != null) {
                return batteryManager.isCharging();
            }
        }

        return mIsCharging;
    }

    @Override
    public long getFCMCount() {

        return mFCMCount;
    }

    @Override
    public long getFCMDowngradeCount() {

        return mFCMDowngradeCount;
    }

    @Override
    public long getFCMTotalDelay() {

        return mFCMTotalDelay;
    }

    @Override
    public long getAlarmCount() {

        return mAlarmCount;
    }

    @Override
    public long getNetworkLockCount() {

        return mNetworkLockCount;
    }

    @Override
    public long getProcessingLockCount() {

        return mProcessingLockCount;
    }

    @Override
    public long getInteractiveLockCount() {

        return mInteractiveLockCount;
    }

    @Override
    public String getOsName() {

        return "android." + Build.VERSION.RELEASE;
    }
}
