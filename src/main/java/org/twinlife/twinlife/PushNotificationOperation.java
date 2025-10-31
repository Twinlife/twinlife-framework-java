/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife;

public enum PushNotificationOperation {
    NOT_DEFINED,
    AUDIO_CALL,
    VIDEO_CALL,
    VIDEO_BELL,
    PUSH_MESSAGE,
    PUSH_FILE,
    PUSH_IMAGE,
    PUSH_AUDIO,
    PUSH_VIDEO
}
