/*
 *  Copyright (c) 2020-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Refresh twincode response IQ.
 * <p>
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"2dc1c0bc-f4a1-4904-ac55-680ce11e43f8",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnRefreshTwincodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"refreshTimestamp", "type":"long"},
 *     {"name":"deleteTwincodes", [
 *      {"name":"twincode", "type": "uuid"}
 *     ]},
 *     {"name":"updateTwincodes", [
 *      {"name":"twincode", "type": "uuid"}
 *      {"name":"attributeCount", "type":"int"},
 *      {"name":"attributes", [
 *        {"name":"name", "type": "string"}
 *        {"name":"type", ["long", "string", "uuid"]}
 *        {"name":"value", "type": ["long", "string", "uuid"]}
 *      ]}
 *      {"name": "signature": [null, "type":"bytes"]}
 *    ]}
 * }
 * </pre>
 * Schema version 1 (REMOVED 2024-02-02 after 22.x)
 */
public class OnRefreshTwincodeIQ extends BinaryPacketIQ {

    static class OnRefreshTwincodeIQSerializer extends BinaryPacketIQSerializer {

        OnRefreshTwincodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnRefreshTwincodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnRefreshTwincodeIQ onRefreshTwincodeIQ = (OnRefreshTwincodeIQ) object;

            encoder.writeLong(onRefreshTwincodeIQ.timestamp);
            if (onRefreshTwincodeIQ.deleteTwincodeList == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(onRefreshTwincodeIQ.deleteTwincodeList.size());
                for (UUID twincodeId : onRefreshTwincodeIQ.deleteTwincodeList) {
                    encoder.writeUUID(twincodeId);
                }
            }

            if (onRefreshTwincodeIQ.updateTwincodeList == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(onRefreshTwincodeIQ.updateTwincodeList.size());
                for (RefreshTwincodeInfo twincode : onRefreshTwincodeIQ.updateTwincodeList) {
                    encoder.writeUUID(twincode.twincodeOutboundId);
                    serialize(encoder, twincode.attributes);
                    encoder.writeOptionalBytes(twincode.signature);
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            long timestamp = decoder.readLong();

            List<UUID> deleteTwincodeList = null;
            int deleteCount = decoder.readInt();
            if (deleteCount > 0) {
                deleteTwincodeList = new ArrayList<>();
                for (int i = 0; i < deleteCount; i++) {
                    deleteTwincodeList.add(decoder.readUUID());
                }
            }

            List<RefreshTwincodeInfo> updateTwincodeList = null;
            int updateCount = decoder.readInt();
            if (updateCount > 0) {
                updateTwincodeList = new ArrayList<>(updateCount);
                for (int i = 0; i < updateCount; i++) {
                    UUID twincodeId = decoder.readUUID();
                    List<AttributeNameValue> attributes = deserializeAttributeList(decoder);
                    byte[] signature = decoder.readOptionalBytes(null);
                    updateTwincodeList.add(new RefreshTwincodeInfo(twincodeId, attributes, signature));
                }
            }

            return new OnRefreshTwincodeIQ(this, serviceRequestIQ, timestamp,
                    deleteTwincodeList, updateTwincodeList);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new OnRefreshTwincodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final Serializer serializer;
    final long timestamp;
    @Nullable
    final List<UUID> deleteTwincodeList;
    @Nullable
    final List<RefreshTwincodeInfo> updateTwincodeList;

    OnRefreshTwincodeIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ iq, long timestamp,
                        @Nullable List<UUID> deleteTwincodeList, @Nullable List<RefreshTwincodeInfo> updateTwincodeList) {

        super(serializer, iq);

        this.serializer = serializer;
        this.timestamp = timestamp;
        this.deleteTwincodeList = deleteTwincodeList;
        this.updateTwincodeList = updateTwincodeList;
    }

    public long getTimestamp() {

        return timestamp;
    }

    @Nullable
    public List<UUID> getDeleteTwincodeList() {

        return deleteTwincodeList;
    }

    @Nullable
    public List<RefreshTwincodeInfo> getUpdateTwincodeList() {

        return updateTwincodeList;
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnRefreshTwincodeIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
