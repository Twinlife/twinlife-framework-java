/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 1
 *  Date: 2021/02/09
 *
 * {
 *  "schemaId":"1ea153d1-35ce-4911-9602-6ba4aee25a57",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ClearDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor"
 *  "fields":
 *  [
 *   {"name":"clearTimestamp", "type":"long"}
 *  ]
 * }
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class ClearDescriptorImpl extends DescriptorImpl implements ConversationService.ClearDescriptor {
    private static final String LOG_TAG = "ClearDescriptorImpl...";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("1ea153d1-35ce-4911-9602-6ba4aee25a57");
    static final int SCHEMA_VERSION_1 = 1;

    static class CallDescriptorImplSerializer_1 extends DescriptorImplSerializer_3 {

        CallDescriptorImplSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, ClearDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            ClearDescriptorImpl clearDescriptorImpl = (ClearDescriptorImpl) object;

            encoder.writeLong(clearDescriptorImpl.mClearTimestamp);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            long clearTimestamp = decoder.readLong();

            return new ClearDescriptorImpl(descriptorImpl, clearTimestamp);
        }
    }

    static final CallDescriptorImplSerializer_1 SERIALIZER_1 = new CallDescriptorImplSerializer_1();

    private final long mClearTimestamp;

    ClearDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, long clearTimestamp) {

        super(descriptorId, cid, null, null, clearTimestamp, 0, 0, 0, 0, 0, 0, 0);

        if (DEBUG) {
            Log.d(LOG_TAG, "ClearDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid
                    + " clearTimestamp=" + clearTimestamp);
        }

        mClearTimestamp = clearTimestamp;
    }

    ClearDescriptorImpl(@NonNull DescriptorId descriptorId, long createdTimestamp, long sentTimestamp, long clearTimestamp) {

        super(descriptorId, 0, null, null, createdTimestamp, sentTimestamp, 0, 0, 0, 0, 0, 0);

        if (DEBUG) {
            Log.d(LOG_TAG, "ClearDescriptorImpl: descriptorId=" + descriptorId + " clearTimestamp=" + clearTimestamp);
        }

        mClearTimestamp = clearTimestamp;
    }

    ClearDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                       @Nullable DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                       long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                       long length) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout);

        if (DEBUG) {
            Log.d(LOG_TAG, "ClearDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " length=" + length);
        }

        mClearTimestamp = length;
    }

    /*
     * Override Descriptor methods
     */

    @Override
    public Type getType() {

        return Type.CLEAR_DESCRIPTOR;
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" clearTimestamp=");
        stringBuilder.append(mClearTimestamp);
        stringBuilder.append("\n");
    }

    /*
     * Override ClearDescriptor methods
     */
    @Override
    public long getClearTimestamp() {

        return mClearTimestamp;
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ClearDescriptor\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private ClearDescriptorImpl(@NonNull DescriptorImpl descriptorImpl, long clearTimestamp) {

        super(descriptorImpl);

        if (DEBUG) {
            Log.d(LOG_TAG, "ClearDescriptorImpl: descriptorImpl=" + descriptorImpl
                    + " clearTimestamp=" + clearTimestamp);
        }

        mClearTimestamp = clearTimestamp;
    }
}
