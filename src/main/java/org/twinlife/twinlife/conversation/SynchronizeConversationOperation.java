/*
 *  Copyright (c) 2016-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "schemaId":"c8deaacf-8d08-4f3a-b0cf-385aff0ecc76",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"SynchronizeConversationOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
 *  "fields":
 *  []
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;

public class SynchronizeConversationOperation extends Operation {

    SynchronizeConversationOperation(@NonNull ConversationImpl conversationImpl) {

        super(Operation.Type.SYNCHRONIZE_CONVERSATION, conversationImpl);
    }

    SynchronizeConversationOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate) {

        super(id, Type.SYNCHRONIZE_CONVERSATION, conversationId, creationDate, 0);
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("SynchronizeConversationOperation:\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "So";
        }
    }
}
