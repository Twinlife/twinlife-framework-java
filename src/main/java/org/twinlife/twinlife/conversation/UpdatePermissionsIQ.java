/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * UpdatePermissions IQ.
 * <p>
 * Schema version 2
 *  Date: 2024/09/10
 * <pre>
 * {
 *  "schemaId":"3b5dc8a2-2679-43f2-badf-ec61c7eed9f0",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"UpdateGroupMemberIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields":[
 *   {"name":"group", "type":"uuid"}
 *   {"name":"member", "type":"uuid"}
 *   {"name":"permissions", "type":"long"}
 *  ]
 * }
 * </pre>
 */
class UpdatePermissionsIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("3b5dc8a2-2679-43f2-badf-ec61c7eed9f0");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQSerializer IQ_UPDATE_PERMISSIONS_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

    @NonNull
    final UUID groupTwincodeId;
    @NonNull
    final UUID memberTwincodeId;
    final long permissions;

    UpdatePermissionsIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull UUID groupTwincodeId,
                        @NonNull UUID memberTwincodeId, long permissions) {
        super(serializer, requestId);

        this.groupTwincodeId = groupTwincodeId;
        this.memberTwincodeId = memberTwincodeId;
        this.permissions = permissions;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new UpdatePermissionsIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" groupTwincodeId=");
            stringBuilder.append(groupTwincodeId);
            stringBuilder.append(" memberTwincodeId=");
            stringBuilder.append(memberTwincodeId);
            stringBuilder.append(" permissions=");
            stringBuilder.append(permissions);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("UpdatePermissionsIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class UpdatePermissionsIQSerializer extends BinaryPacketIQSerializer {

        UpdatePermissionsIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, UpdatePermissionsIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            UpdatePermissionsIQ updatePermissionsIQ = (UpdatePermissionsIQ) object;
            encoder.writeUUID(updatePermissionsIQ.groupTwincodeId);
            encoder.writeUUID(updatePermissionsIQ.memberTwincodeId);
            encoder.writeLong(updatePermissionsIQ.permissions);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID groupTwincodeId = decoder.readUUID();
            final UUID memberTwincodeId = decoder.readUUID();
            final long permissions = decoder.readLong();

            return new UpdatePermissionsIQ(this, requestId, groupTwincodeId, memberTwincodeId, permissions);
        }
    }
}
