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

public class OfferToReceive {

    public final boolean audio;
    public final boolean video;
    public final boolean data;

    public OfferToReceive(boolean audio, boolean video, boolean data) {

        this.audio = audio;
        this.video = video;
        this.data = data;
    }

    @NonNull
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("OfferToReceive[");
        if (audio) {
            sb.append("audio");
        }
        if (video) {
            sb.append(" video");
        }
        if (data) {
            sb.append(" data");
        }
        sb.append("]");
        return sb.toString();
    }
}
