/*
 *  Copyright (c) 2025 twinlife SA.
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
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Create invitation code response IQ.
 * <p>
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"93cf2a0c-82cb-43ea-98c6-43563807fadf",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnCreateInvitationCodeIQ",
 *  "namespace":"org.twinlife.schemas.image",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"id", "type":"uuid"}
 *     {"name":"creationDate", "type":"long"}
 *     {"name":"validityPeriod", "type":"int"}
 *     {"name":"code", "type":"string"}
 *     {"name":"twincodeId", "type":"uuid"}
 *  ]
 * }
 * </pre>
 */
public class OnCreateInvitationCodeIQ extends BinaryPacketIQ {

    static class OnCreateInvitationCodeIQSerializer extends BinaryPacketIQSerializer {

        OnCreateInvitationCodeIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnCreateInvitationCodeIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnCreateInvitationCodeIQ onCreateInvitationCodeIQ = (OnCreateInvitationCodeIQ) object;

            encoder.writeUUID(onCreateInvitationCodeIQ.id);
            encoder.writeLong(onCreateInvitationCodeIQ.creationDate);
            encoder.writeInt(onCreateInvitationCodeIQ.validityPeriod);
            encoder.writeString(onCreateInvitationCodeIQ.code);
            encoder.writeUUID(onCreateInvitationCodeIQ.twincodeId);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID id = decoder.readUUID();
            long creationDate = decoder.readLong();
            int validityPeriod = decoder.readInt();
            String code = decoder.readString();
            UUID twincodeId = decoder.readUUID();

            return new OnCreateInvitationCodeIQ(this, serviceRequestIQ.getRequestId(), id, creationDate, validityPeriod, code, twincodeId);

        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnCreateInvitationCodeIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    private final UUID id;
    private final long creationDate;
    private final int validityPeriod;
    @NonNull
    private final String code;
    @NonNull
    private final UUID twincodeId;

    @NonNull
    public UUID getId() {
        return id;
    }

    public long getCreationDate() {
        return creationDate;
    }

    public int getValidityPeriod() {
        return validityPeriod;
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    public UUID getTwincodeId() {
        return twincodeId;
    }

    //
    // Override Object methods
    //

    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" id=").append(id)
                .append(" creationDate=").append(creationDate)
                .append(" validityPeriod=").append(validityPeriod)
                .append(" code=").append(code)
                .append(" twincodeId=").append(twincodeId);
    }

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("OnCreateInvitationCodeIQ[");
        appendTo(stringBuilder);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }

    public OnCreateInvitationCodeIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                                    @NonNull UUID id, long creationDate, int validityPeriod, @NonNull String code, @NonNull UUID twincodeId) {
        super(serializer, requestId);

        this.id = id;
        this.creationDate = creationDate;
        this.validityPeriod = validityPeriod;
        this.code = code;
        this.twincodeId = twincodeId;
    }
}
