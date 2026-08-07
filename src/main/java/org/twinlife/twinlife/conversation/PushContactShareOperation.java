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
 *  "schemaId":"af8329f4-0955-42a9-be95-bb5f66a77240",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushContactShareOperation",
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

import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.AndroidImageTools;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.ImageService;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_22;

class PushContactShareOperation extends Operation {
    private static final String LOG_TAG = "PushContactShareOperation";
    private static final boolean DEBUG = false;

    @Nullable
    private volatile ContactShareDescriptorImpl mContactShareDescriptorImpl;

    @Nullable
    private volatile byte[] mAvatar;

    PushContactShareOperation(@NonNull ConversationImpl conversationImpl,
                              @NonNull ContactShareDescriptorImpl contactShareDescriptor, @NonNull byte[] avatar) {

        super(Type.PUSH_CONTACT_SHARE, conversationImpl, contactShareDescriptor);

        mContactShareDescriptorImpl = contactShareDescriptor;
        mAvatar = avatar;
    }

    public PushContactShareOperation(long operationId, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptorId) {
        super(operationId, Type.PUSH_CONTACT_SHARE, conversationId, creationDate, descriptorId);

        mAvatar = null;
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

        byte[] avatar = mAvatar;

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

        if (avatar == null) {
            avatar = contactShareDescriptor.loadAvatarData(connection.getFilesDir());
        }

        final long requestId = connection.newRequestId();
        updateRequestId(requestId);
        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_22)) {
            final PushContactShareIQ pushContactShareIQ = new PushContactShareIQ(PushContactShareIQ.IQ_PUSH_CONTACT_SHARE_SERIALIZER, requestId, contactShareDescriptor, avatar);

            connection.sendPacket(PeerConnectionService.StatType.IQ_SET_PUSH_CONTACT_SHARE, pushContactShareIQ);
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
            stringBuilder.append("PushContactShareOperation:\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }
}
