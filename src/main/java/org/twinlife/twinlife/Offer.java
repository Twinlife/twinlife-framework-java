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

import org.twinlife.twinlife.util.Version;

public class Offer {

    public boolean audio;
    public boolean video;
    public boolean videoBell;
    public boolean data;
    public boolean group;
    public boolean transfer;
    public Version version;

    public Offer(boolean audio, boolean video, boolean videoBell, boolean data){
        this(audio, video, videoBell, data, false);
    }

    public Offer(boolean audio, boolean video, boolean videoBell, boolean data, boolean transfer) {

        this.audio = audio;
        this.video = video;
        this.videoBell = videoBell;
        this.data = data;
        this.group = false;
        this.transfer = transfer;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Offer[");
        if (audio) {
            sb.append("audio ");
        }
        if (video) {
            sb.append("video ");
        }
        if (group) {
            sb.append("group ");
        }
        if (transfer) {
            sb.append("transfer ");
        }
        if (data) {
            sb.append("data ");
        }
        sb.append(version);
        sb.append("]");
        return sb.toString();
    }
}
