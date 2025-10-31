/*
 *  Copyright (c) 2020-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.util.BinaryPacketIQ;

public interface BinaryPacketListener {

    /**
     * Process the next packet sent to this packet listener.
     * <p>
     * <p>
     * A single thread is responsible for invoking all listeners, so it's very
     * important that implementations of this method not block for any extended
     * period of time.
     *
     * @param iq the packet to process.
     */
    void processPacket(@NonNull BinaryPacketIQ iq);
}
