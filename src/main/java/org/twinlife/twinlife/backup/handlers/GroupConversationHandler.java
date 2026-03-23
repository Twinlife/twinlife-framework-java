/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup.handlers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.backup.BackupHandler;
import org.twinlife.twinlife.backup.VerifyResult;
import org.twinlife.twinlife.conversation.ConversationServiceImpl;
import org.twinlife.twinlife.conversation.GroupConversationImpl;
import org.twinlife.twinlife.conversation.GroupMemberConversationImpl;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.util.UUID;

import kotlin.NotImplementedError;

public class GroupConversationHandler extends BackupHandler<ConversationService.Conversation> {
    private static final String LOG_TAG = "GroupConversationHandlr";
    private static final boolean DEBUG = false;

    public static final UUID SCHEMA_ID = UUID.fromString("f1bba3af-1432-47d0-9cc9-e91ff533f2a4");
    private static final int SCHEMA_VERSION = 1;
    @NonNull
    private final ConversationServiceImpl mConversationService;

    public GroupConversationHandler(@NonNull ConversationServiceImpl conversationService) {
        super();
        this.mConversationService = conversationService;
    }

    @Override
    protected void initDeserializers() {
        mRestorers.put(GroupConversationRestorerV1.VERSION, new GroupConversationRestorerV1());
    }

    @Override
    public void backup(@NonNull BinaryEncoder encoder) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "backup: encoder=" + encoder);
        }

        for (ConversationService.GroupConversation conversation : mConversationService.listGroupConversations()) {
            TwincodeOutbound peerTwincodeOutbound = conversation.getPeerTwincodeOutbound();
            if (peerTwincodeOutbound == null) {
                Log.w(LOG_TAG, "Conversation has no peerTwincodeOutbound, skipping: "+conversation);
                continue;
            }

            encoder.writeUUID(SCHEMA_ID);
            encoder.writeInt(SCHEMA_VERSION);
            encoder.writeLong(conversation.getDatabaseId().getId());
            encoder.writeUUID(conversation.getId());
            encoder.writeLong(conversation.getCreationDate());
            encoder.writeLong(conversation.getSubject().getDatabaseId().getId()); // groupId
            encoder.writeLong(conversation.getSubject().getDatabaseId().getId()); // subjectId
            encoder.writeLong(peerTwincodeOutbound.getDatabaseId().getId());

            GroupMemberConversationImpl incomingConversation = ((GroupConversationImpl) conversation).getIncomingConversation();
            encoder.writeUUID(incomingConversation.getResourceId());
            encoder.writeOptionalUUID(incomingConversation.getPeerResourceId());
            encoder.writeOptionalUUID(incomingConversation.getInvitedContactId());
            encoder.writeLong(((GroupConversationImpl) conversation).getPermissions());
            encoder.writeLong(conversation.getJoinPermissions());
            encoder.writeInt(((GroupConversationImpl) conversation).getFlags());

            for (ConversationService.GroupMemberConversation groupMemberConversation : conversation.getGroupMembers(ConversationService.MemberFilter.ALL_MEMBERS)) {
                peerTwincodeOutbound = groupMemberConversation.getPeerTwincodeOutbound();
                if (peerTwincodeOutbound == null) {
                    Log.w(LOG_TAG, "Conversation has no peerTwincodeOutbound, skipping: "+conversation);
                    continue;
                }

                encoder.writeUUID(SCHEMA_ID);
                encoder.writeInt(SCHEMA_VERSION);
                encoder.writeLong(groupMemberConversation.getDatabaseId().getId());
                encoder.writeUUID(groupMemberConversation.getId());
                encoder.writeLong(groupMemberConversation.getCreationDate());
                encoder.writeLong(conversation.getSubject().getDatabaseId().getId()); // groupId
                encoder.writeLong(groupMemberConversation.getSubject().getDatabaseId().getId()); // subjectId
                encoder.writeLong(peerTwincodeOutbound.getDatabaseId().getId());

                encoder.writeUUID(((GroupMemberConversationImpl) groupMemberConversation).getResourceId());
                encoder.writeOptionalUUID(((GroupMemberConversationImpl) groupMemberConversation).getPeerResourceId());
                encoder.writeOptionalUUID(groupMemberConversation.getInvitedContactId());
                encoder.writeLong(((GroupMemberConversationImpl) groupMemberConversation).getPermissions());
                encoder.writeLong(0L); // join permissions
                encoder.writeInt(((GroupMemberConversationImpl) groupMemberConversation).getFlags());
            }
        }
    }


    private class GroupConversationRestorerV1 implements Restorer<ConversationService.Conversation> {
        public static final int VERSION = 1;

        @Nullable
        @Override
        public ConversationService.Conversation restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException {
            long dbId = decoder.readLong();
            UUID conversationId = decoder.readUUID();
            long creationDate = decoder.readLong();
            long groupId = decoder.readLong();
            long subjectId = decoder.readLong();
            long peerTwincodeOutboundId = decoder.readLong();
            UUID resourceId = decoder.readUUID();
            UUID peerResourceId = decoder.readOptionalUUID();
            UUID invitedContactId = decoder.readOptionalUUID();
            long permissions = decoder.readLong();
            long joinPermissions = decoder.readLong();
            int flags = decoder.readInt();

            if (inPlace) {
                return null;
            }

            ConversationService.Conversation conversation = mConversationService.restoreGroupConversation(dbId, conversationId, creationDate, groupId, subjectId, peerTwincodeOutboundId,
                    resourceId, peerResourceId, invitedContactId, permissions, joinPermissions, flags);

            if (conversation == null) {
                throw new SerializerException("could not restore conversation " + conversationId);
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "Restored conversation: " + conversation);
            }

            return conversation;
        }

        @NonNull
        @Override
        public VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException {
            // TODO BKP: implement before activating group backup/restore.
            throw new NotImplementedError();
        }
    }
}
