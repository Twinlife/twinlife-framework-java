/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.debug.DebugServiceImpl;
import org.twinlife.twinlife.util.EventMonitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Produce a dump of pending operations.
 */
public class OperationsDump implements org.twinlife.twinlife.conversation.ConversationServiceImpl.ConversationVisitor, DebugServiceImpl.DumpListGenerator {

    private final TwinlifeImpl mTtwinlifeImpl;
    private List<String[]> mResult;
    private long mNow;

    public OperationsDump(TwinlifeImpl twinlifeImpl) {

        mTtwinlifeImpl = twinlifeImpl;
    }

    /**
     * Produce the dump.
     *
     * @return A list of row/colums representing the dump.
     */
    @Override
    public List<String[]> getDump() {

        final ConversationServiceImpl conversationService = mTtwinlifeImpl.getConversationServiceImpl();

        mNow = System.currentTimeMillis();
        mResult = new ArrayList<>();
        mResult.add(new String[]{ "", "CID", "Type", "Id", "Rid", "Time" });
        conversationService.iterateConversations(this);

        return mResult;
    }

    @Override
    public void visit(Conversation conversation, OperationList operations) {

        if (operations != null) {
            for (final Operation operation : operations.iterator()) {
                final List<String> info = dumpConversation(conversation, operation);

                if (info != null) {
                    mResult.add(info.toArray(new String[0]));
                }
            }
        }
    }

    private List<String> dumpConversation(Conversation conversation, Operation operation) {
        final List<String> result = new ArrayList<>();

        try {
            if (conversation instanceof GroupConversationImpl) {
                result.add("G");
            } else {
                final ConversationImpl conversationImpl = (ConversationImpl) conversation;

                if (conversationImpl.getGroup() != null) {
                    result.add("m");
                } else {
                    result.add("C");
                }
            }
            final String type = operation.getType().toString();
            // SCz result.add(Utils.toLog(operation.getConversationId()));
            result.add(type.length() > 14 ? type.substring(0, 14) : type);
            result.add(String.valueOf(operation.getId()));
            result.add(String.valueOf(operation.getRequestId()));
            result.add(EventMonitor.formatDuration(mNow - operation.getTimestamp()));

            return result;

        } catch (Exception ex) {
            return null;
        }
    }
}
