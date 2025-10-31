/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

/**
 * Transport Candidate
 */
public class TransportCandidate {

    public final int id;
    @NonNull
    public final String label;
    @NonNull
    public final String sdp;
    public final boolean removed;
    public long requestId;

    public TransportCandidate(int id, @NonNull String label, @NonNull String sdp, boolean removed) {
        this.id = id;
        this.label = label;
        this.sdp = sdp;
        this.removed = removed;
        this.requestId = 0L;
    }
}
