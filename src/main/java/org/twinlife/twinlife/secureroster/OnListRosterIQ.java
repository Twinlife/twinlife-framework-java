/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.secureroster;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.RosterMember;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.SignedRosterGroup;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * On list secure roster members response IQ.
 * <p>
 * Schema version 1
 *  Date: 2026/03/26
 * <pre>
 * {
 *  "schemaId":"299B036C-2589-4367-8DF3-3F61EF5B2268",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnListSecureRosterIQ",
 *  "namespace":"org.twinlife.schemas.secureroster",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"maxMemberCount", "type":"int"},
 *     {"name":"keys", "type":"array", "items": {
 *         "type":"record",
 *         "name":"RosterSignatureKey",
 *         "fields": [
 *             {"name":"keyId", "type":"uuid"},
 *             {"name":"signerKeyId", "type":"uuid"},
 *             {"name":"publicKey", "type":"bytes"},
 *             {"name":"signature", "type":"bytes"},
 *             {"name":"members", "type":"array", "items": {
 *                 "type":"record",
 *                 "name":"RosterMember",
 *                 "fields": [
 *                     {"name":"memberTwincodeId", "type":"uuid"},
 *                     {"name":"creationDate", "type":"long"},
 *                     {"name":"modificationDate", "type":"long"},
 *                     {"name":"permissions", "type":"long"},
 *                     {"name":"publicKey", "type":"bytes"},
 *                     {"name":"signature", "type":"bytes"}
 *                 ]
 *             }}
 *         ]
 *     }}
 *  ]
 * }
 * </pre>
 */
class OnListRosterIQ extends BinaryPacketIQ {

    private static class OnListRosterIQSerializer extends BinaryPacketIQSerializer {

        OnListRosterIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnListRosterIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final OnListRosterIQ onListRosterIQ = (OnListRosterIQ) object;
            encoder.writeInt(onListRosterIQ.maxMemberCount);
            encoder.writeInt(onListRosterIQ.keys.size());
            for (final SignedRosterGroup key : onListRosterIQ.keys) {
                encoder.writeUUID(key.keyId);
                encoder.writeUUID(key.signingKeyId);
                encoder.writeBytes(key.publicKey.asBytes(), 0, key.publicKey.asBytes().length);
                encoder.writeBytes(key.signature, 0, key.signature.length);

                encoder.writeInt(key.members.size());
                for (final RosterMember member : key.members) {
                    encoder.writeUUID(member.memberTwincodeId);
                    encoder.writeLong(member.creationDate);
                    encoder.writeLong(member.modificationDate);
                    encoder.writeLong(member.permissions);
                    encoder.writeBytes(member.publicKey.asBytes(), 0, member.publicKey.asBytes().length);
                    encoder.writeBytes(member.signature, 0, member.signature.length);
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            int maxMemberCount = decoder.readInt();
            int keyCount = decoder.readInt();
            final List<SignedRosterGroup> keys = new ArrayList<>();
            while (keyCount > 0) {
                keyCount--;
                final UUID keyId = decoder.readUUID();
                final UUID signerKeyId = decoder.readUUID();
                final byte[] publicKey = decoder.readBytes(null).array();
                final byte[] signature = decoder.readBytes(null).array();

                int memberCount = decoder.readInt();
                final List<RosterMember> members = new ArrayList<>();
                while (memberCount > 0) {
                    memberCount--;
                    final UUID memberTwincodeId = decoder.readUUID();
                    final long creationDate = decoder.readLong();
                    final long modificationDate = decoder.readLong();
                    final long permissions = decoder.readLong();
                    final byte[] memberPublicKey = decoder.readBytes(null).array();
                    final byte[] memberSignature = decoder.readBytes(null).array();
                    members.add(new RosterMember(memberTwincodeId, permissions, memberPublicKey, memberSignature, creationDate, modificationDate));
                }
                keys.add(new SignedRosterGroup(keyId, publicKey, signature, signerKeyId, members));
            }

            return new OnListRosterIQ(this, serviceRequestIQ.getRequestId(), maxMemberCount, keys);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnListRosterIQSerializer(schemaId, schemaVersion);
    }

    private final int maxMemberCount;
    @NonNull
    private final List<SignedRosterGroup> keys;

    public int getMaxMemberCount() {
        return maxMemberCount;
    }

    @NonNull
    public List<SignedRosterGroup> getKeys() {
        return keys;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" maxMemberCount");
            stringBuilder.append(maxMemberCount);
            stringBuilder.append(" keysCount=");
            stringBuilder.append(keys.size());
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnListRosterIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    OnListRosterIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                   int maxMemberCount, @NonNull List<SignedRosterGroup> keys) {

        super(serializer, requestId);

        this.maxMemberCount = maxMemberCount;
        this.keys = keys;
    }
}
