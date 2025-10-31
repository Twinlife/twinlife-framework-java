/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import org.twinlife.twinlife.BaseService.ErrorCode;

import java.util.UUID;

/**
 * Peer signaling listener interface.
 *
 */
public interface PeerSignalingListener {

    /**
     * Called when a session-initiate IQ is received.
     *
     * @param sessionId the P2P session id.
     * @param from the target identification string.
     * @param to the source/originator identification string.
     * @param sdp the sdp content (clear text | compressed | encrypted).
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max received frame size.
     * @param maxReceivedFrameRate the max received frame rate.
     * @return SUCCESS, NO_PERMISSION, ITEM_NOT_FOUND if the session id is not known.
     */
    @Nullable
    ErrorCode onSessionInitiate(@NonNull UUID sessionId, @NonNull String from, @NonNull String to,
                                @NonNull Sdp sdp, @NonNull Offer offer, @NonNull OfferToReceive offerToReceive,
                                int maxReceivedFrameSize, int maxReceivedFrameRate);

    /**
     * Called when a session-accept IQ is received.
     *
     * @param sessionId the P2P session id.
     * @param to the target identification string.
     * @param sdp the sdp content (clear text | compressed | encrypted).
     * @param offer the offer.
     * @param offerToReceive the offer to receive.
     * @param maxReceivedFrameSize the max received frame size.
     * @param maxReceivedFrameRate the max received frame rate.
     * @return SUCCESS, NO_PERMISSION, ITEM_NOT_FOUND if the session id is not known.
     */
    @NonNull
    ErrorCode onSessionAccept(@NonNull UUID sessionId,  @NonNull String to, @NonNull Sdp sdp, @NonNull Offer offer,
                              @NonNull OfferToReceive offerToReceive, int maxReceivedFrameSize, int maxReceivedFrameRate);

    /**
     * Called when a session-update IQ is received.
     *
     * @param sessionId the P2P session id.
     * @param updateType whether this is an offer or an answer.
     * @param sdp the sdp content (clear text | compressed | encrypted).
     * @return SUCCESS or ITEM_NOT_FOUND if the session id is not known.
     */
    @NonNull
    ErrorCode onSessionUpdate(@NonNull UUID sessionId, @NonNull SdpType updateType, @NonNull Sdp sdp);

    /**
     * Called when a transport-info IQ is received with a list of candidates.
     *
     * @param sessionId the P2P session id.
     * @param sdp the SDP with candidates.
     * @return SUCCESS or ITEM_NOT_FOUND if the session id is not known.
     */
    @NonNull
    ErrorCode onTransportInfo(@NonNull UUID sessionId, @NonNull Sdp sdp);

    /**
     * Called when a session-terminate IQ is received for the given P2P session.
     *
     * @param sessionId the P2P session id.
     * @param reason the terminate reason.
     */
    void onSessionTerminate(@NonNull UUID sessionId, @NonNull TerminateReason reason);

    void onDeviceRinging(@NonNull UUID sessionId);
}
