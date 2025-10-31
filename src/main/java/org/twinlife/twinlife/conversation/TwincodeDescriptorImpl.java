/*
 *  Copyright (c) 2019-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

/*
 * <pre>
 *
 * Schema version 2
 *  Date: 2021/04/07
 *
 * {
 *  "schemaId":"1f0ad01a-9d6e-4157-8d50-e8cc9ce583be",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"TwincodeDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.4"
 *  "fields":
 *  [
 *   {"name":"twincode", "type":"UUID"}
 *   {"name":"schemaId", "type":"UUID"}
 *   {"name":"copyAllowed", "type":"boolean"}
 *  ]
 * }
 *
 * Schema version 1
 *  Date: 2019/03/29
 *
 * {
 *  "schemaId":"1f0ad01a-9d6e-4157-8d50-e8cc9ce583be",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"TwincodeDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor.3"
 *  "fields":
 *  [
 *   {"name":"twincode", "type":"UUID"}
 *   {"name":"schemaId", "type":"UUID"}
 *   {"name":"copyAllowed", "type":"boolean"}
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
import org.twinlife.twinlife.Twincode;

import java.util.UUID;

public class TwincodeDescriptorImpl extends DescriptorImpl implements ConversationService.TwincodeDescriptor {
    private static final String LOG_TAG = "TwincodeDescriptorImpl";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("1f0ad01a-9d6e-4157-8d50-e8cc9ce583be");
    static final int SCHEMA_VERSION_2 = 2;

    static class TwincodeDescriptorImplSerializer_2 extends DescriptorImplSerializer_4 {

        TwincodeDescriptorImplSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, TwincodeDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            TwincodeDescriptorImpl twincodeDescriptorImpl = (TwincodeDescriptorImpl) object;
            encoder.writeUUID(twincodeDescriptorImpl.mTwincodeId);
            encoder.writeUUID(twincodeDescriptorImpl.mSchemaId);
            encoder.writeBoolean(twincodeDescriptorImpl.mCopyAllowed);
        }

        @NonNull
        public Object deserialize(@NonNull Decoder decoder, @NonNull UUID twincodeOutboundId, long sequenceId, long createdTimestamp) throws SerializerException {

            long expireTimeout = decoder.readLong();
            UUID sendTo = decoder.readOptionalUUID();
            DescriptorId replyTo = readOptionalDescriptorId(decoder);

            UUID twincodeId = decoder.readUUID();
            UUID schemaId = decoder.readUUID();
            boolean copyAllowed = decoder.readBoolean();

            return new TwincodeDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, twincodeId, schemaId, null, copyAllowed, createdTimestamp, 0);
        }
    }

    static final TwincodeDescriptorImplSerializer_2 SERIALIZER_2 = new TwincodeDescriptorImplSerializer_2();

    static final int SCHEMA_VERSION_1 = 1;

    static class TwincodeDescriptorImplSerializer_1 extends DescriptorImplSerializer_3 {

        TwincodeDescriptorImplSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, TwincodeDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            TwincodeDescriptorImpl twincodeDescriptorImpl = (TwincodeDescriptorImpl) object;
            encoder.writeUUID(twincodeDescriptorImpl.mTwincodeId);
            encoder.writeUUID(twincodeDescriptorImpl.mSchemaId);
            encoder.writeBoolean(twincodeDescriptorImpl.mCopyAllowed);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            UUID twincodeId = decoder.readUUID();
            UUID schemaId = decoder.readUUID();
            boolean copyAllowed = decoder.readBoolean();

            return new TwincodeDescriptorImpl(descriptorImpl, twincodeId, schemaId, null, copyAllowed);
        }
    }

    static final TwincodeDescriptorImplSerializer_1 SERIALIZER_1 = new TwincodeDescriptorImplSerializer_1();

    @NonNull
    private final UUID mTwincodeId;
    @NonNull
    private final UUID mSchemaId;
    private final boolean mCopyAllowed;
    @Nullable
    private final String mPublicKey;

    TwincodeDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, long expireTimeout, @Nullable UUID sendTo,
                           @Nullable DescriptorId replyTo, @NonNull UUID twincodeId,
                           @NonNull UUID schemaId, @Nullable String publicKey, boolean copyAllowed) {

        super(descriptorId, cid, expireTimeout, sendTo, replyTo);

        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid
                    + " twincodeId=" + twincodeId + " schemaId=" + schemaId + " copyAllowed=" + copyAllowed);
        }

        mTwincodeId = twincodeId;
        mSchemaId = schemaId;
        mPublicKey = publicKey;
        mCopyAllowed = copyAllowed;
    }

    TwincodeDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                           @Nullable DescriptorId replyTo, @NonNull UUID twincodeId,
                           @NonNull UUID schemaId, @Nullable String publicKey,
                           boolean copyAllowed, long createdTimestamp, long sentTimestamp) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, createdTimestamp, sentTimestamp);

        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId
                    + " twincodeId=" + twincodeId + " schemaId=" + schemaId + " copyAllowed=" + copyAllowed);
        }

        mTwincodeId = twincodeId;
        mSchemaId = schemaId;
        mPublicKey = publicKey;
        mCopyAllowed = copyAllowed;
    }

    TwincodeDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                           @Nullable DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                           long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                           int flags, String content) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout);

        if (DEBUG) {
            Log.d(LOG_TAG, "ObjectDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " content=" + content);
        }

        mCopyAllowed = (flags & FLAG_COPY_ALLOWED) != 0;

        final String[] args = extract(content);
        mSchemaId = extractUUID(args, 0, Twincode.NOT_DEFINED);
        mTwincodeId = extractUUID(args, 1, Twincode.NOT_DEFINED);
        mPublicKey = extractString(args, 2, null);
    }

    /*
     * Override Descriptor methods
     */

    @Override
    public Type getType() {

        return Type.TWINCODE_DESCRIPTOR;
    }

    /*
     * Override TwincodeDescriptorImpl methods
     */

    @NonNull
    @Override
    public UUID getTwincodeId() {

        return mTwincodeId;
    }

    @NonNull
    @Override
    public UUID getSchemaId() {

        return mSchemaId;
    }

    @Override
    public boolean isCopyAllowed() {

        return mCopyAllowed;
    }

    @Override
    @Nullable
    public String getPublicKey() {

        return mPublicKey;
    }

    @Override
    int getFlags() {

        return mCopyAllowed ? FLAG_COPY_ALLOWED : 0;
    }

    @Override
    @Nullable
    String serialize() {

        if (mPublicKey != null) {
            return mSchemaId + FIELD_SEPARATOR + mTwincodeId + FIELD_SEPARATOR + mPublicKey;
        } else {
            return mSchemaId + FIELD_SEPARATOR + mTwincodeId;
        }
    }

    @Override
    @Nullable
    DescriptorImpl createForward(@NonNull DescriptorId descriptorId, long conversationId, long expireTimeout,
                                 @Nullable UUID sendTo, boolean copyAllowed) {

        return new TwincodeDescriptorImpl(descriptorId, conversationId, expireTimeout,
                sendTo, null, mTwincodeId, mSchemaId, mPublicKey, copyAllowed);
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" twincodeId=");
        stringBuilder.append(mTwincodeId);
        stringBuilder.append(" schemaId=");
        stringBuilder.append(mSchemaId);
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("TwincodeDescriptorImpl\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private TwincodeDescriptorImpl(@NonNull DescriptorImpl descriptorImpl, @NonNull UUID twincodeId,
                                   @NonNull UUID schemaId, @Nullable String publicKey, boolean copyAllowed) {

        super(descriptorImpl);

        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeDescriptorImpl: descriptorImpl=" + descriptorImpl + " twincodeId=" + twincodeId
                    + " schemaId=" + schemaId + " copyAllowed=" + copyAllowed);
        }

        mTwincodeId = twincodeId;
        mSchemaId = schemaId;
        mPublicKey = publicKey;
        mCopyAllowed = copyAllowed;
    }
}
