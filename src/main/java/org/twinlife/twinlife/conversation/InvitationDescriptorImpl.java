/*
 *  Copyright (c) 2018-2024 twinlife SA.
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
 *  Date: 2018/07/09
 *
 * {
 *  "schemaId":"751761ce-2d1c-4af4-ba85-6c0764f21ed0",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"InvitationDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor"
 *  "fields":
 *  [
 *   {"name":"groupTwincode", "type":"uuid"}
 *   {"name":"memberTwincode", "type":"uuid"}
 *   {"name":"inviterTwincode", "type":"uuid"}
 *   {"name":"name", "type":"String"}
 *   {"name":"status", "type":"InvitationDescriptor.Status"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.Twincode;

import java.util.UUID;

public class InvitationDescriptorImpl extends DescriptorImpl implements ConversationService.InvitationDescriptor {
    private static final String LOG_TAG = "InvitationDescriptor...";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("751761ce-2d1c-4af4-ba85-6c0764f21ed0");
    static final int SCHEMA_VERSION_1 = 1;

    static class InvitationDescriptorImplSerializer_1 extends DescriptorImplSerializer_3 {

        InvitationDescriptorImplSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, ObjectDescriptorImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            InvitationDescriptorImpl inviteDescriptorImpl = (InvitationDescriptorImpl) object;
            encoder.writeUUID(inviteDescriptorImpl.mGroupTwincodeId);
            encoder.writeUUID(inviteDescriptorImpl.mMemberTwincodeId);
            encoder.writeUUID(inviteDescriptorImpl.mInviterTwincodeId);
            encoder.writeString(inviteDescriptorImpl.mName);
            encoder.writeEnum(fromInvitationStatus(inviteDescriptorImpl.mStatus));
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            DescriptorImpl descriptorImpl = (DescriptorImpl) super.deserialize(serializerFactory, decoder);

            UUID groupTwincodeId = decoder.readUUID();
            UUID memberTwincodeId = decoder.readUUID();
            UUID inviterTwincodeId = decoder.readUUID();
            String name = decoder.readString();
            Status status = toInvitationStatus(decoder.readEnum());
            return new InvitationDescriptorImpl(descriptorImpl, groupTwincodeId, memberTwincodeId, inviterTwincodeId, name, status);
        }
    }

    static final InvitationDescriptorImplSerializer_1 SERIALIZER_1 = new InvitationDescriptorImplSerializer_1();

    static Status toInvitationStatus(int value) {
        switch (value) {
            case 0:
                return Status.PENDING;

            case 1:
                return Status.ACCEPTED;

            case 2:
                return Status.REFUSED;

            case 4:
                return Status.JOINED;

            case 3:
            default:
                return Status.WITHDRAWN;
        }
    }

    static int fromInvitationStatus(@NonNull Status status) {
        switch (status) {
            case PENDING:
                return 0;

            case ACCEPTED:
                return 1;

            case REFUSED:
                return 2;

            case WITHDRAWN:
                return 3;

            case JOINED:
                return 4;

            default:
                return -1;
        }
    }

    @NonNull
    private final UUID mGroupTwincodeId;
    @NonNull
    private final String mName;
    @NonNull
    private Status mStatus;
    @NonNull
    private UUID mMemberTwincodeId;
    @NonNull
    private final UUID mInviterTwincodeId;
    @Nullable
    private final String mPublicKey;

    InvitationDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, @NonNull UUID groupTwincodeId,
                             @NonNull UUID inviterTwincodeId, @NonNull String name, @Nullable String publicKey) {

        super(descriptorId, cid, 0, null, null);

        if (DEBUG) {
            Log.d(LOG_TAG, "InvitationDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid
                    + " groupTwincodeId=" + groupTwincodeId);
        }

        mGroupTwincodeId = groupTwincodeId;
        mInviterTwincodeId = inviterTwincodeId;
        mName = name;
        mMemberTwincodeId = Twincode.NOT_DEFINED;
        mStatus = Status.PENDING;
        mPublicKey = publicKey;
    }

    InvitationDescriptorImpl(@NonNull ConversationService.DescriptorId descriptorId,
                             @NonNull UUID groupTwincodeId, @NonNull UUID inviterTwincodeId, @NonNull String name,
                             @Nullable String publicKey, long creationDate, long sendDate,
                             long expireTimeout) {

        super(descriptorId, 0, null, null, creationDate, sendDate, 0, 0,
                0, 0, 0, expireTimeout);

        if (DEBUG) {
            Log.d(LOG_TAG, "InvitationDescriptorImpl: descriptorId=" + descriptorId);
        }

        mGroupTwincodeId = groupTwincodeId;
        mMemberTwincodeId = Twincode.NOT_DEFINED;
        mInviterTwincodeId = inviterTwincodeId;
        mName = name;
        mPublicKey = publicKey;
        mStatus = Status.PENDING;
    }

    InvitationDescriptorImpl(@NonNull ConversationService.DescriptorId descriptorId, long cid, @Nullable UUID sendTo,
                              @Nullable ConversationService.DescriptorId replyTo, long creationDate, long sendDate, long receiveDate,
                              long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                              String content, long value) {

        super(descriptorId, cid, sendTo, replyTo, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout);

        if (DEBUG) {
            Log.d(LOG_TAG, "InvitationDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " content=" + content);
        }

        mStatus = toInvitationStatus((int) value);

        final String[] args = extract(content);
        mGroupTwincodeId = extractUUID(args, 0, Twincode.NOT_DEFINED);
        mMemberTwincodeId = extractUUID(args, 1, Twincode.NOT_DEFINED);
        mInviterTwincodeId = extractUUID(args, 2, Twincode.NOT_DEFINED);

        final String name = extractString(args, 3, null);
        mName = name == null ? "" : name;
        mPublicKey = extractString(args, 4, null);
    }

    /*
     * Override Descriptor methods
     */

    @Override
    public Type getType() {

        return Type.INVITATION_DESCRIPTOR;
    }

    @Override
    @NonNull
    public UUID getGroupTwincodeId() {

        return mGroupTwincodeId;
    }

    @Override
    @Nullable
    public UUID getMemberTwincodeId() {

        return mMemberTwincodeId;
    }

    @Override
    @Nullable
    public UUID getInviterTwincodeId() {

        return mInviterTwincodeId;
    }

    @Override
    @NonNull
    public String getName() {


        return mName;
    }

    @Override
    @Nullable
    public String getPublicKey() {

        return mPublicKey;
    }

    @Override
    @NonNull
    public Status getStatus() {

        return mStatus;
    }

    public void setStatus(Status status) {

        mStatus = status;
    }

    public void setMemberTwincodeId(UUID memberTwincodeId) {

        mMemberTwincodeId = memberTwincodeId;
    }

    @Override
    @Nullable
    String serialize() {
        StringBuilder sb = new StringBuilder();

        sb.append(mGroupTwincodeId);
        sb.append(FIELD_SEPARATOR);
        if (mMemberTwincodeId.equals(Twincode.NOT_DEFINED)) {
            sb.append("?");
        } else {
            sb.append(mMemberTwincodeId);
        }
        sb.append(FIELD_SEPARATOR);
        if (mInviterTwincodeId.equals(Twincode.NOT_DEFINED)) {
            sb.append("?");
        } else {
            sb.append(mInviterTwincodeId);
        }
        sb.append(FIELD_SEPARATOR);
        sb.append(mName);
        if (mPublicKey != null) {
            sb.append(FIELD_SEPARATOR);
            sb.append(mPublicKey);
        }
        return sb.toString();
    }

    long getValue() {

        return fromInvitationStatus(mStatus);
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" memberTwincodeId=");
        stringBuilder.append(mMemberTwincodeId);
        stringBuilder.append(" groupTwincodeId=");
        stringBuilder.append(mGroupTwincodeId);
        stringBuilder.append(" inviterTwincodeId=");
        stringBuilder.append(mInviterTwincodeId);
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append(" name=");
            stringBuilder.append(mName);
            stringBuilder.append(" publicKey=");
            stringBuilder.append(mPublicKey);
        }
        stringBuilder.append(" status=");
        stringBuilder.append(mStatus);
        stringBuilder.append("\n");
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("InvitationDescriptorImpl\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private InvitationDescriptorImpl(@NonNull DescriptorImpl descriptorImpl,
                                     @NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId,
                                     @NonNull UUID inviterTwincodeId, @NonNull String name,
                                     @NonNull Status status) {

        super(descriptorImpl);

        if (DEBUG) {
            Log.d(LOG_TAG, "InvitationDescriptorImpl: descriptorImpl=" + descriptorImpl
                    + " groupTwincodeId=" + groupTwincodeId);
        }

        mGroupTwincodeId = groupTwincodeId;
        mMemberTwincodeId = memberTwincodeId;
        mInviterTwincodeId = inviterTwincodeId;
        mName = name;
        mPublicKey = null;
        mStatus = status;
    }
}
