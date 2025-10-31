/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

/**
 * Provide information about the device.
 */
public interface DeviceInfo {

    int getAppStandbyBucket();

    long getForegroundTime();

    long getBackgroundTime();

    boolean isBackgroundRestricted();

    boolean isIgnoringBatteryOptimizations();

    float getBatteryLevel();

    boolean isLowRamDevice();

    boolean isCharging();

    long getFCMCount();

    long getFCMDowngradeCount();

    long getFCMTotalDelay();

    long getAlarmCount();

    long getNetworkLockCount();

    long getProcessingLockCount();

    long getInteractiveLockCount();

    String getOsName();
}
