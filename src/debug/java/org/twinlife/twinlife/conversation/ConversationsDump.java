/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.debug.DebugServiceImpl;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Produce a dump of existing conversations.
 */
public class ConversationsDump implements ConversationServiceImpl.ConversationVisitor, DebugServiceImpl.DumpListGenerator {
    private static final String LOG_TAG = "ConversationsDump";

    private final TwinlifeImpl mTtwinlifeImpl;
    private List<String[]> mResult;

    public ConversationsDump(TwinlifeImpl twinlifeImpl) {

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

        mResult = new ArrayList<>();
        mResult.add(new String[]{ "Type", "CID", "T-IN  ", "T-OUT ", "Peer  ", "Contact", "In", "Out", "Ver"});
        conversationService.iterateConversations(this);

        return mResult;
    }

    @Override
    public void visit(Conversation conversation, OperationList operations) {

        final List<String> info = dumpConversation(conversation, operations);

        if (info != null) {
            mResult.add(info.toArray(new String[0]));

            // Dump the result in logcat.
            Log.e(LOG_TAG, "" + info);
        } else {
            mResult.add(new String[]{ "E", "?", "?", "?", "?", "?", "?", "?", "?"});
        }
    }

    private List<String> dumpConversation(Conversation conversation, OperationList operations) {
        final List<String> result = new ArrayList<>();
        final ConversationImpl conversationImpl;
        final GroupConversationImpl groupConversation;

        try {
            if (conversation instanceof GroupConversationImpl) {
                groupConversation = (GroupConversationImpl) conversation;
                result.add("G");
                conversationImpl = groupConversation.getIncomingConversation();
            } else {
                conversationImpl = (ConversationImpl) conversation;
                groupConversation = conversationImpl.getGroup();
                if (!(conversation instanceof GroupMemberConversationImpl)) {
                    result.add("C");
                } else if (((GroupMemberConversationImpl) conversation).isLeaving()) {
                    result.add("l");
                } else {
                    result.add("m");
                }
            }
            result.add(Utils.toLog(conversation.getId()));
            result.add(Utils.toLog(conversation.getTwincodeInboundId()));
            result.add(Utils.toLog(conversation.getTwincodeOutboundId()));
            result.add(Utils.toLog(conversation.getPeerTwincodeOutboundId()));
            result.add(Utils.toLog(conversation.getContactId()));
            /* switch (conversationImpl.getIncomingState()) {
                case OPEN:
                    result.add("I");
                    break;
                case CLOSED:
                    result.add("F");
                    break;
                case OPENING:
                    result.add("o");
                    break;
            }
            switch (conversationImpl.getOutgoingState()) {
                case OPEN:
                    result.add("O");
                    break;
                case CLOSED:
                    result.add("F");
                    break;
                case OPENING:
                    result.add("o");
                    break;
            }
            UUID peerConnectionId = conversationImpl.getOutgoingPeerConnectionId();
            if (peerConnectionId != null) {
                result.add(Utils.toLog(peerConnectionId));
            } else {
                result.add("");
            }
            if (conversationImpl.getPeerMajorVersion() != 1 && conversationImpl.getPeerMinorVersion() != 0) {
                result.add(conversationImpl.getPeerMajorVersion() + "." + conversationImpl.getPeerMinorVersion());
            } else {
                result.add("");
            }
            if (groupConversation != null) {
                result.add(groupConversation.getMembers().size() + " M");
            } else {
                result.add("");
            }*/
            if (operations != null) {
                result.add(Integer.toString(operations.getCount()));
            } else {
                result.add("");
            }
            return result;

        } catch (Exception ex) {
            return null;
        }
    }
}
