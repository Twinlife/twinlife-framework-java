/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife.conversation;

import org.twinlife.twinlife.AssertPoint;

public enum ConversationAssertPoint implements AssertPoint {
    RESET_CONVERSATION,
    SEND_OP,
    PROCESS_SIGNATURE_ERROR,
    PROCESS_IQ,
    PROCESS_RESULT_IQ,
    PROCESS_RESET_CONVERSATION_IQ,
    PROCESS_PUSH_OBJECT_IQ,
    PROCESS_PUSH_FILE_IQ,
    PROCESS_UPDATE_DESCRIPTOR_IQ,
    SEND_ERROR_IQ,
    ON_DATA_CHANNEL_IQ;

    public int getIdentifier() {

        return this.ordinal() + BASE_VALUE;
    }

    private static final int BASE_VALUE = 100;
}