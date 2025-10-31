/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.crypto.SignatureInfoIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OnJoinGroupIQ IQ.
 * <p>
 * Schema version 2
 *  Date: 2024/08/28
 *
 * <pre>
 * {
 *  "schemaId":"3d175317-f1f7-4cd1-abd8-2f538b342e41",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"JoinGroupIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"deviceState", "type":"byte"},
 *     {"name":"status", "type":"int"}
 *     0 => {}
 *     1 => {{"name":"permissions", "type":"long"}
 *           {"name":"inviterMemberTwincodeOutboundId", "type":"uuid"},
 *           {"name":"inviterPermissions", "type":"long"},
 *           {"name":"inviterPublicKey", "type":[null, "String"]},
 *           {"name":"inviterSecret", "type":[null, "bytes"]}}
 *           {"name":"inviterSalt", "type":[null, "String"]},
 *           {"name":"inviterSignature", "type":[null, "String"]}}
 *           {"name":"count", "type":"int"},
 *            [{"name":"memberTwincodeId", "type":"uuid"},
 *             {"name":"permissions", "type":"long"},
 *             {"name":"publicKey", "type":[null, "String"}]}
 *  ]
 * }
 * </pre>
 */
class OnJoinGroupIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("3d175317-f1f7-4cd1-abd8-2f538b342e41");
    static final int SCHEMA_VERSION_2 = 2;
    public static final BinaryPacketIQSerializer IQ_ON_JOIN_GROUP_SERIALIZER_2 = new OnJoinGroupIQSerializer_2(SCHEMA_ID, SCHEMA_VERSION_2);

    public static class MemberInfo {
        @NonNull
        public final UUID memberTwincodeId;
        @Nullable
        public final String publicKey;
        public final long permissions;

        public MemberInfo(@NonNull UUID memberTwincodeId, @Nullable String publicKey, long permissions) {
            this.memberTwincodeId = memberTwincodeId;
            this.publicKey = publicKey;
            this.permissions = permissions;
        }
    }
    final int deviceState;
    @Nullable
    public final UUID inviterTwincodeId;
    public final long inviterPermissions;
    @Nullable
    public final String inviterPublicKey;
    @Nullable
    public final byte[] inviterSecretKey;
    public final long permissions;
    @Nullable
    public final String inviterSalt;
    @Nullable
    public final String inviterSignature;
    @Nullable
    public final List<MemberInfo> members;

    @NonNull
    static OnJoinGroupIQ ok(long requestId, int deviceState, @NonNull SignatureInfoIQ inviterInfo,
                            long inviterPermissions, long permissions,
                            @Nullable String inviterSalt, @Nullable String inviterSignature,
                            @Nullable List<MemberInfo> members) {
        return new OnJoinGroupIQ(IQ_ON_JOIN_GROUP_SERIALIZER_2, requestId, deviceState,
                inviterInfo.twincodeOutboundId, inviterPermissions, inviterInfo.publicKey,
                inviterInfo.secret, permissions, inviterSalt, inviterSignature, members);
    }

    @NonNull
    static OnJoinGroupIQ fail(long requestId, int deviceState) {
        return new OnJoinGroupIQ(IQ_ON_JOIN_GROUP_SERIALIZER_2, requestId, deviceState,
                null, 0, null,
                null, 0, null, null, null);
    }

    private OnJoinGroupIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                         int deviceState, @Nullable UUID inviterTwincodeId, long inviterPermissions,
                         @Nullable String inviterPublicKey, @Nullable byte[] inviterSecretKey, long permissions,
                         @Nullable String inviterSalt, @Nullable String inviterSignature,
                         @Nullable List<MemberInfo> members) {

        super(serializer, requestId);

        this.deviceState = deviceState;
        this.inviterPublicKey = inviterPublicKey;
        this.inviterSecretKey = inviterSecretKey;
        this.permissions = permissions;
        this.inviterTwincodeId = inviterTwincodeId;
        this.inviterPermissions = inviterPermissions;
        this.inviterSalt = inviterSalt;
        this.inviterSignature = inviterSignature;
        this.members = members;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" inviterTwincodeId=");
            stringBuilder.append(inviterTwincodeId);
            stringBuilder.append(" inviterPermissions=");
            stringBuilder.append(inviterPermissions);
            stringBuilder.append(" permissions=");
            stringBuilder.append(permissions);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnJoinGroupIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class OnJoinGroupIQSerializer_2 extends BinaryPacketIQSerializer {

        OnJoinGroupIQSerializer_2(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnJoinGroupIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnJoinGroupIQ onJoinGroupIQ = (OnJoinGroupIQ) object;
            encoder.writeInt(onJoinGroupIQ.deviceState);

            if (onJoinGroupIQ.inviterTwincodeId == null) {
                // Invitation rejected and refused by the peer.
                encoder.writeEnum(0);
            } else {
                // User joined the group.
                encoder.writeEnum(1);
                encoder.writeLong(onJoinGroupIQ.permissions);
                encoder.writeUUID(onJoinGroupIQ.inviterTwincodeId);
                encoder.writeLong(onJoinGroupIQ.inviterPermissions);
                encoder.writeOptionalString(onJoinGroupIQ.inviterPublicKey);
                encoder.writeOptionalBytes(onJoinGroupIQ.inviterSecretKey);
                encoder.writeOptionalString(onJoinGroupIQ.inviterSalt);
                encoder.writeOptionalString(onJoinGroupIQ.inviterSignature);
                if (onJoinGroupIQ.members == null) {
                    encoder.writeInt(0);
                } else {
                    encoder.writeInt(onJoinGroupIQ.members.size());
                    for (MemberInfo member : onJoinGroupIQ.members) {
                        encoder.writeUUID(member.memberTwincodeId);
                        encoder.writeLong(member.permissions);
                        encoder.writeOptionalString(member.publicKey);
                    }
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final int deviceState = decoder.readInt();
            final UUID inviterTwincodeId;
            final long inviterPermissions;
            final String publicKey;
            final byte[] secret;
            final String inviterSalt;
            final String inviterSignature;
            final long permissions;
            final List<MemberInfo> members;
            if (decoder.readEnum() != 0) {
                permissions = decoder.readLong();
                inviterTwincodeId = decoder.readUUID();
                inviterPermissions = decoder.readLong();
                publicKey = decoder.readOptionalString();
                secret = decoder.readOptionalBytes(null);
                inviterSalt = decoder.readOptionalString();
                inviterSignature = decoder.readOptionalString();
                int count = decoder.readInt();
                if (count == 0) {
                    members = null;
                } else {
                    members = new ArrayList<>(count);
                    while (count > 0) {
                        count--;
                        UUID memberTwincodeId = decoder.readUUID();
                        long memberPermission = decoder.readLong();
                        String memberPublicKey = decoder.readOptionalString();

                        members.add(new MemberInfo(memberTwincodeId, memberPublicKey, memberPermission));
                    }
                }
            } else {
                inviterTwincodeId = null;
                inviterPermissions = 0;
                publicKey = null;
                secret = null;
                inviterSalt = null;
                inviterSignature = null;
                permissions = 0;
                members = null;
            }

            return new OnJoinGroupIQ(this, requestId, deviceState, inviterTwincodeId, inviterPermissions,
                    publicKey, secret, permissions, inviterSalt, inviterSignature, members);
        }
    }
}
