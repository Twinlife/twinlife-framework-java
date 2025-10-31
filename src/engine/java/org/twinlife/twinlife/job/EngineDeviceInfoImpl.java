/*
 *  Copyright (c) 2018-2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.job;

import org.twinlife.twinlife.DeviceInfo;

/**
 * Provide information about the device.
 */
public class EngineDeviceInfoImpl implements DeviceInfo {

    private final long mForegroundTime;
    private final long mBackgroundTime;
    private final long mFCMCount;
    private final long mFCMDowngradeCount;
    private final long mFCMTotalDelay;
    private final String mOsName;

    EngineDeviceInfoImpl(long foregroundTime, long backgroundTime,
                         long fcmCount, long fcmDowngradeCount, long fcmTotalDelay,
                         String osName) {

        mForegroundTime = foregroundTime;
        mBackgroundTime = backgroundTime;
        mFCMCount = fcmCount;
        mFCMDowngradeCount = fcmDowngradeCount;
        mFCMTotalDelay = fcmTotalDelay;
        mOsName = osName;
    }

    @Override
    public int getAppStandbyBucket() {

        return 0;
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

        return (float) 100.0;
    }

    @Override
    public boolean isBackgroundRestricted() {

        return false;
    }

    @Override
    public boolean isIgnoringBatteryOptimizations() {

        return false;
    }

    @Override
    public boolean isLowRamDevice() {

        return false;
    }

    @Override
    public boolean isCharging() {

        return true;
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
    public String getOsName() {

        return mOsName;
    }
}
