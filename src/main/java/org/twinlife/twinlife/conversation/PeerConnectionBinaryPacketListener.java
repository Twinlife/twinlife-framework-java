/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.util.BinaryPacketIQ;

interface PeerConnectionBinaryPacketListener {

    /**
     * Process the IQ packet received on the P2P connection.  The operation is called from
     * the WebRTC thread so it's very important that implementations of this method do not
     * block for any extended period of time.
     *
     * @param connection the connection object onto which the IQ was received.
     * @param iq the packet to process.
     */
    void processPacket(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) throws SerializerException;
}
