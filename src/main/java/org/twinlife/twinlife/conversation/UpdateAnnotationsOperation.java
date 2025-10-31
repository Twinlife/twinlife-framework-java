/*
 *  Copyright (c) 2023-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 1
 *  Date: 2023-01-10
 *
 * {
 *  "schemaId":"dc513717-c843-40e8-8b04-0d8016052935",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"UpdateAnnotationsOperation",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.Operation"
 *  "fields":
 *  [
 *   {"name":"twincodeOutboundId", "type":"UUID"},
 *   {"name":"sequenceId", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.SerializerException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MAJOR_VERSION_2;
import static org.twinlife.twinlife.conversation.ConversationServiceImpl.MINOR_VERSION_14;

class UpdateAnnotationsOperation extends Operation {
    private static final String LOG_TAG = "UpdateAnnotationsOp..";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("dc513717-c843-40e8-8b04-0d8016052935");

    UpdateAnnotationsOperation(@NonNull ConversationImpl conversationImpl,
                               @NonNull DescriptorId descriptorId) {

        super(Type.UPDATE_ANNOTATIONS, conversationImpl, descriptorId);
    }

    UpdateAnnotationsOperation(long id, @NonNull DatabaseIdentifier conversationId, long creationDate, long descriptorId) {
        super(id, Operation.Type.UPDATE_ANNOTATIONS, conversationId, creationDate, descriptorId);
    }

    @Override
    public ErrorCode execute(@NonNull ConversationConnection connection) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execute: connection=" + connection);
        }

        final DescriptorImpl descriptorImpl = connection.loadDescriptorWithId(getDescriptorId());
        if (descriptorImpl == null || isExpiredDescriptor(descriptorImpl)) {
            return ErrorCode.EXPIRED;
        }

        // If annotations are not supported, finish the operation and don't report any error because that's confusing.
        final DescriptorId descriptorId = descriptorImpl.getDescriptorId();
        if (!connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_14)) {
            return ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER;
        }

        // For this version, we only send our own annotations.  They are all associated with the same twincode.
        final long requestId = connection.newRequestId();
        final List<ConversationService.DescriptorAnnotation> list = connection.loadLocalAnnotations(descriptorId);
        final Map<UUID, List<ConversationService.DescriptorAnnotation>> annotations = new HashMap<>();
        updateRequestId(requestId);
        annotations.put(connection.getConversation().getTwincodeOutboundId(), list);

        final UpdateAnnotationIQ updateAnnotationIQ = new UpdateAnnotationIQ(UpdateAnnotationIQ.IQ_UPDATE_ANNOTATION_SERIALIZER,
                requestId, descriptorId, UpdateAnnotationIQ.UpdateType.SET_ANNOTATION, annotations);

        connection.sendPacket(PeerConnectionService.StatType.IQ_SET_UPDATE_OBJECT, updateAnnotationIQ);
        return ErrorCode.QUEUED;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("UpdateAnnotationsOperation:\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "Ao";
        }
    }
}
