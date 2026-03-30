/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.TwincodeInfo;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.*;

/**
 * Contains all the currently active twincodes owned by the account, grouped by schemaId.
 * Used by the app to synchronize twincodeOutbounds during a backup restore.
 * <p>
 * Schema version 1
 *  Date: 2025/08/25
 *
 * <pre>
 * {
 *  "schemaId":"ca422038-7ae9-4dd3-829d-f8107b817f9a",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnGetAllTwincodesIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeIds", [
 *         {"name":"schemaId", "type": "uuid", [
 *          "name": "twincodeOutboundId", "type": "uuid",
 *          "name": "twincodeFactoryId", "type": "uuid",
 *          "name": "twincodeInboundId", "type": "uuid"
 *          ]}
 *      ]}
 *  ]
 * }
 *
 * </pre>
 */
public class OnGetAllTwincodesIQ extends BinaryPacketIQ {

    static class OnGetAllTwincodesIQSerializer extends BinaryPacketIQSerializer {

        OnGetAllTwincodesIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnGetAllTwincodesIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

           throw new SerializerException();
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            Map<UUID, List<TwincodeInfo>> twincodeIdsBySchema = new HashMap<>();

            int nbSchemas = decoder.readInt();
            for (int i = 0; i < nbSchemas; i++) {
                List<TwincodeInfo> twincodeInfos = new ArrayList<>();

                UUID schemaId = decoder.readUUID();
                int nbTwincodeIds = decoder.readInt();
                for (int j = 0; j < nbTwincodeIds; j++) {
                    UUID twincodeFactoryId = decoder.readUUID();
                    UUID twincodeOutboundId = decoder.readUUID();
                    UUID twincodeInboundId = decoder.readUUID();

                    twincodeInfos.add(new TwincodeInfo(twincodeFactoryId, twincodeOutboundId, twincodeInboundId));
                }
                twincodeIdsBySchema.put(schemaId, twincodeInfos);
            }

            return new OnGetAllTwincodesIQ(this, serviceRequestIQ, twincodeIdsBySchema);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnGetAllTwincodesIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final Map<UUID, List<TwincodeInfo>> twincodeIdsBySchema;

    public @NonNull Map<UUID, List<TwincodeInfo>> getTwincodeIdsBySchema() {
        return twincodeIdsBySchema;
    }

    public OnGetAllTwincodesIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ, @NonNull Map<UUID, List<TwincodeInfo>> twincodeIdsBySchema) {

        super(serializer, serviceRequestIQ);

        this.twincodeIdsBySchema = twincodeIdsBySchema;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        if (twincodeIdsBySchema.isEmpty()) {
            stringBuilder.append(" []");
            return;
        }

        stringBuilder.append(" [");
        for ( Map.Entry<UUID, List<TwincodeInfo>> entry : twincodeIdsBySchema.entrySet()) {
            stringBuilder.append("schemaId=");
            stringBuilder.append(entry.getKey());

            stringBuilder.append(", twincodeIds= [");
            if (entry.getValue().isEmpty()) {
                stringBuilder.append("], ");
            } else {
                for (TwincodeInfo twincodeInfo : entry.getValue()) {
                    stringBuilder.append(twincodeInfo);
                    stringBuilder.append(", ");
                }
                stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
                stringBuilder.append("], ");
            }
        }
        stringBuilder.delete(stringBuilder.length() - 2, stringBuilder.length());
        stringBuilder.append("]");
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("GetTwincodeIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }
}
