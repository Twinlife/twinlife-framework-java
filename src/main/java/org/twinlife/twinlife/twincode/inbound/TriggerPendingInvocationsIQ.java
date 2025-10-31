/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.inbound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Trigger Pending Invocations IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"266f3d93-1782-491c-b6cb-28cc23df4fdf",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"TriggerPendingInvocationsIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"filters", [
 *       {"name":"filterName", "type": "string"}
 *    ]}
 *  ]
 * }
 * </pre>
 */
public class TriggerPendingInvocationsIQ extends BinaryPacketIQ {

    static class TriggerPendingInvocationsIQSerializer extends BinaryPacketIQSerializer {

        TriggerPendingInvocationsIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, TriggerPendingInvocationsIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            TriggerPendingInvocationsIQ triggerIQ = (TriggerPendingInvocationsIQ) object;

            if (triggerIQ.filters == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(triggerIQ.filters.size());
                for (String filterName : triggerIQ.filters) {
                    encoder.writeString(filterName);
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            List<String> filters = null;
            int count = decoder.readInt();
            if (count > 0) {
                filters = new ArrayList<>(count);
                while (count > 0) {
                    count--;
                    filters.add(decoder.readString());
                }
            }

            return new TriggerPendingInvocationsIQ(this, serviceRequestIQ.getRequestId(), filters);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new TriggerPendingInvocationsIQSerializer(schemaId, schemaVersion);
    }

    @Nullable
    private final List<String> filters;

    @Nullable
    public List<String> getFilters() {

        return filters;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" filters=");
            stringBuilder.append(filters);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("TriggerPendingInvocationsIQ[");
            appendTo(stringBuilder);
            stringBuilder.append("]");

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    public TriggerPendingInvocationsIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                                       @Nullable List<String> filters) {
        super(serializer, requestId);

        this.filters = filters;
    }
}
