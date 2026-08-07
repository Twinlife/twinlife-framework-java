/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "schemaId":"9cee2613-e251-4b4f-9d71-f70b961f39ac",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AnswerContactShareOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
 *  "fields":
 *  [
 *   {"name":"twincodeOutboundId", "type":"UUID"}
 *   {"name":"sequenceId", "type":"long"}
 *  ]
 * }
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;

import java.util.UUID;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_22;

class AnswerContactShareOperation extends Operation {
    private static final String LOG_TAG = "AnswerContactShareOperation";
    private static final boolean DEBUG = false;

    @Nullable
    private volatile ContactShareDescriptorImpl mContactShareDescriptorImpl;

    AnswerContactShareOperation(@NonNull ConversationImpl conversationImpl,
                              @NonNull ContactShareDescriptorImpl contactShareDescriptor) {

        super(Type.ANSWER_CONTACT_SHARE, conversationImpl, contactShareDescriptor);

        mContactShareDescriptorImpl = contactShareDescriptor;
    }

    public AnswerContactShareOperation(long operationId, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptorId) {
        super(operationId, Type.ANSWER_CONTACT_SHARE, conversationId, creationDate, descriptorId);

        mContactShareDescriptorImpl = null;
    }

    @Nullable
    ContactShareDescriptorImpl getContactShareDescriptorImpl() {
        return mContactShareDescriptorImpl;
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        ContactShareDescriptorImpl contactShareDescriptor = getContactShareDescriptorImpl();

        if (contactShareDescriptor == null) {
            DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
            if (!(descriptorImpl instanceof ContactShareDescriptorImpl)) {
                return ErrorCode.EXPIRED;
            }
            contactShareDescriptor = (ContactShareDescriptorImpl) descriptorImpl;
            mContactShareDescriptorImpl = contactShareDescriptor;
        }

        if (!connection.preparePush(contactShareDescriptor)) {
            return ErrorCode.EXPIRED;
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_22)) {
            ConversationService.InvitationDescriptor.Status status = contactShareDescriptor.getStatus();
            boolean autoAnswer = contactShareDescriptor.isAutoAnswer();
            UUID invitationTwincodeOutboundId = contactShareDescriptor.getInvitationTwincodeOutboundId();
            CryptoService.PublicKeyData pubkey = contactShareDescriptor.getInvitationTwincodeOutboundPubkey();

            AnswerContactShareIQ answerContactShareIQ = new AnswerContactShareIQ(AnswerContactShareIQ.IQ_ANSWER_CONTACT_SHARE_SERIALIZER, requestId, contactShareDescriptor.getDescriptorId(), status, autoAnswer, invitationTwincodeOutboundId, pubkey);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_ANSWER_CONTACT_SHARE, answerContactShareIQ);
            return ErrorCode.QUEUED;

        } else {

            // Peer doesn't support contact sharing.
            return connection.operationNotSupported(contactShareDescriptor);
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        final StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("AnswerContactShareOperation:\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
