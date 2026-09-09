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
 * Update the member's permission in a secure roster request IQ.
 * <p>
 * Schema version 1
 *  Date: 2026/08/10
 * <pre>
 * {
 *  "schemaId":"9d8dc720-be8e-4fb7-a5f1-4b1ecd0f539f",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"UpdateRosterMemberIQ",
 *  "namespace":"org.twinlife.schemas.secureroster",
 *  "super":"SecureRosterIQ"
 *  "fields": [
 *     {"name":"signingKeyId", "type":"uuid"},
 *     {"name":"members", [
 *       {"name":"memberTwincodeId", "type":"uuid"},
 *       {"name":"memberPermission", "type":"long"},
 *       {"name":"signature", "type":"bytes"},
 *       {"name":"rosterKeySignature", "type":"bytes"}
 *     ]}
 *  ]
 * }
 * </pre>
 */
class UpdateRosterMemberIQ extends SecureRosterIQ {

    private static class UpdateRosterMemberIQSerializer extends SecureRosterIQ.RosterIQSerializer {

        UpdateRosterMemberIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, UpdateRosterMemberIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final UpdateRosterMemberIQ updateRosterMemberIQ = (UpdateRosterMemberIQ) object;
            encoder.writeUUID(updateRosterMemberIQ.signingKeyId);
            encoder.writeInt(updateRosterMemberIQ.members.size());
            for (MemberPermission member : updateRosterMemberIQ.members) {
                encoder.writeUUID(member.memberTwincodeId);
                encoder.writeLong(member.memberPermission);
                if (member.signature == null) {
                    encoder.writeZero();
                } else {
                    encoder.writeBytes(member.signature, 0, member.signature.length);
                }
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
            final List<MemberPermission> members = new ArrayList<>(memberCount);
            while (memberCount > 0) {
                memberCount--;
                final UUID memberTwincodeId = decoder.readUUID();
                final long memberPermission = decoder.readLong();
                final byte[] signature = decoder.readBytes(null).array();
                final byte[] rosterKeySignature = decoder.readBytes(null).array();

                members.add(new MemberPermission(memberTwincodeId, memberPermission, signature, rosterKeySignature));
            }
            return new UpdateRosterMemberIQ(this, secureRosterIQ.getRequestId(), secureRosterIQ.rosterId, signingKeyId, members);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new UpdateRosterMemberIQSerializer(schemaId, schemaVersion);
    }

    static final class MemberPermission {
        @NonNull
        final UUID memberTwincodeId;
        final long memberPermission;
        @Nullable
        final byte[] signature;
        @Nullable
        final byte[] rosterKeySignature;

        public MemberPermission(@NonNull UUID memberTwincodeId, long memberPermission, @Nullable byte[] signature,
                                @Nullable byte[] rosterKeySignature) {
            this.memberTwincodeId = memberTwincodeId;
            this.memberPermission = memberPermission;
            this.signature = signature;
            this.rosterKeySignature = rosterKeySignature;
        }
    }
    @NonNull
    final UUID signingKeyId;
    final List<MemberPermission> members;

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
            stringBuilder.append("UpdateRosterMemberIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    UpdateRosterMemberIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                         @NonNull UUID rosterId, @NonNull UUID signingKeyId, @NonNull List<MemberPermission> members) {

        super(serializer, requestId, rosterId);

        this.signingKeyId = signingKeyId;
        this.members = members;
    }
}
