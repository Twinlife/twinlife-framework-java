/*
 *  Copyright (c) 2015-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

public class Configuration {

    public static final int MAX_FRAME_SIZE = 921600; // HD: 1280x720
    public static final int MAX_FRAME_RATE = 60;

    public int maxSentFrameSize;
    public int maxSentFrameRate;
    public int maxReceivedFrameSize;
    public int maxReceivedFrameRate;
    @NonNull
    public final TurnServer[] turnServers;
    @NonNull
    public final Hostname[] hostnames;
    @Nullable
    public String features;
    @Nullable
    public UUID environmentId;

    @SuppressWarnings("WeakerAccess")
    public Configuration(@NonNull TurnServer[] turnServers, @NonNull Hostname[] hostnames) {

        maxSentFrameSize = MAX_FRAME_SIZE;
        maxSentFrameRate = MAX_FRAME_RATE;
        maxReceivedFrameSize = MAX_FRAME_SIZE;
        maxReceivedFrameRate = MAX_FRAME_RATE;
        this.turnServers = turnServers;
        this.hostnames = hostnames;
    }

    @NonNull
    public String toString() {

        return "Configuration:\n";
    }
}
