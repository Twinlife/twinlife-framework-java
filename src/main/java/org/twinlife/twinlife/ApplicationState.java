/*
 *  Copyright (c) 2021-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife;

/**
 * State of the application
 */
public enum ApplicationState {
    // Application is in foreground
    FOREGROUND,

    // Application is in background
    BACKGROUND,

    // Application is wakeup by an alarm (we are in background).
    WAKEUP_ALARM,

    // Application is wakeup by a push notification.
    WAKEUP_PUSH
}
