/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.secureroster;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Add a member to a secure roster request IQ.
 * <p>
 * Schema version 1
 *  Date: 2026/03/26
 * <pre>
 * {
 *  "schemaId":"abf52b69-e9d4-47c1-864c-9f94cbfa5096",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AddSecureRosterMemberIQ",
 *  "namespace":"org.twinlife.schemas.secureroster",
 *  "super":"SecureRosterIQ"
 *  "fields": [
 *     {"name":"signingKeyId", "type":"uuid"},
 *     {"name":"members", [
 *       {"name":"newMemberTwincodeId", "type":"uuid"},
 *       {"name":"newMemberPermission", "type":"long"},
 *       {"name":"newMemberPublicKey", "type":"bytes"},
 *       {"name":"signature", "type":"bytes"},
 *       {"name":"rosterKeySignature", "type":"bytes"}
 *     ]}
 *  ]
 * }
 * </pre>
 */
class AddRosterMemberIQ extends SecureRosterIQ {

    private static class AddRosterMemberIQSerializer extends RosterIQSerializer {

        AddRosterMemberIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, AddRosterMemberIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final AddRosterMemberIQ addRosterMemberIQ = (AddRosterMemberIQ) object;
            encoder.writeUUID(addRosterMemberIQ.signingKeyId);
            encoder.writeInt(addRosterMemberIQ.members.size());
            for (MemberInfo member : addRosterMemberIQ.members) {
                encoder.writeUUID(member.newMemberTwincodeId);
                encoder.writeLong(member.newMemberPermission);
                encoder.writeBytes(member.newMemberPublicKey, 0, member.newMemberPublicKey.length);
                encoder.writeBytes(member.signature, 0, member.signature.length);
                if (member.rosterKeySignature == null) {
                    encoder.writeZero();
                } else {
                    encoder.writeBytes(member.rosterKeySignature, 0, member.rosterKeySignature.length);
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final SecureRosterIQ secureRosterIQ = (SecureRosterIQ) super.deserialize(serializerFactory, decoder);

            final UUID signingKeyId = decoder.readUUID();
            int memberCount = decoder.readInt();
            final List<MemberInfo> members = new ArrayList<>(memberCount);
            while (memberCount > 0) {
                memberCount--;
                final UUID newMemberTwincodeId = decoder.readUUID();
                final long newMemberPermission = decoder.readLong();
                final byte[] newMemberPublicKey = decoder.readBytes(null).array();
                final byte[] signature = decoder.readBytes(null).array();
                final byte[] rosterKeySignature = decoder.readBytes(null).array();

                members.add(new MemberInfo(newMemberTwincodeId, newMemberPermission, newMemberPublicKey, signature, rosterKeySignature));
            }
            return new AddRosterMemberIQ(this, secureRosterIQ.getRequestId(), secureRosterIQ.rosterId, signingKeyId, members);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new AddRosterMemberIQSerializer(schemaId, schemaVersion);
    }

    static final class MemberInfo {
        @NonNull
        final UUID newMemberTwincodeId;
        final long newMemberPermission;
        @NonNull
        final byte[] newMemberPublicKey;
        @NonNull
        final byte[] signature;
        @Nullable
        final byte[] rosterKeySignature;

        public MemberInfo(@NonNull UUID newMemberTwincodeId,
                          long newMemberPermission, @NonNull byte[] newMemberPublicKey,
                          @NonNull byte[] signature, @Nullable byte[] rosterKeySignature) {
            this.newMemberTwincodeId = newMemberTwincodeId;
            this.newMemberPermission = newMemberPermission;
            this.newMemberPublicKey = newMemberPublicKey;
            this.signature = signature;
            this.rosterKeySignature = rosterKeySignature;
        }
    }
    @NonNull
    final UUID signingKeyId;
    final List<MemberInfo> members;

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" rosterId=");
            stringBuilder.append(rosterId);
            stringBuilder.append(" signingKeyId=");
            stringBuilder.append(signingKeyId);
            stringBuilder.append(" members=");
            stringBuilder.append(members);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("AddRosterMemberIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    AddRosterMemberIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                      @NonNull UUID rosterId, @NonNull UUID signingKeyId, @NonNull List<MemberInfo> members) {

        super(serializer, requestId, rosterId);

        this.signingKeyId = signingKeyId;
        this.members = members;
    }
}
