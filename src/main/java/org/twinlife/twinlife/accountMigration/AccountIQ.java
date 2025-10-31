/*
 *  Copyright (c) 2020-2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 * Account IQ.
 *
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"04A8EFC7-F261-4D19-A0E0-0248359CB4DF",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"AccountIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"securedConfiguration", "type":"bytes"},
 *     {"name":"accountConfiguration", "type":"bytes"},
 *     {"name":"hasPeerAccount", "type":"boolean"}
 *  ]
 * }
 *
 * Important note: Old version with the 'environmentId' is not supported by new versions because
 * the environment ID is now embedded within the accountConfiguration which now has an incompatible
 * format with past version: to simplify, we can migrate only between versions with the schema 3 of accountConfiguration.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"04A8EFC7-F261-4D19-A0E0-0248359CB4DF",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AccountIQ",
 *  "namespace":"org.twinlife.schemas.deviceMigration",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"securedConfiguration", "type":"bytes"},
 *     {"name":"accountConfiguration", "type":"bytes"},
 *     {"name":"environmentId", "type":"uuid"},
 *     {"name":"hasPeerAccount", "type":"boolean"}
 *  ]
 * }
 *
 * </pre>
 */
class AccountIQ extends BinaryPacketIQ {

    static class SwapAccountIQSerializer extends BinaryPacketIQSerializer {

        SwapAccountIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, AccountIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            AccountIQ accountIQ = (AccountIQ) object;

            encoder.writeBytes(accountIQ.securedConfiguration, 0, accountIQ.securedConfiguration.length);
            encoder.writeBytes(accountIQ.accountConfiguration, 0, accountIQ.accountConfiguration.length);
            encoder.writeBoolean(accountIQ.hasPeerAccount);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            ByteBuffer data = decoder.readBytes(null);
            byte[] securedConfiguration = data.array();

            data = decoder.readBytes(null);
            byte[] accountConfiguration = data.array();

            boolean hasPeerAccount = decoder.readBoolean();
            return new AccountIQ(this, serviceRequestIQ, securedConfiguration, accountConfiguration, hasPeerAccount);
        }
    }

    public static BinaryPacketIQSerializer createSerializer(UUID schemaId, int schemaVersion) {

        return new SwapAccountIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final byte[] securedConfiguration;
    @NonNull
    final byte[] accountConfiguration;
    final boolean hasPeerAccount;

    AccountIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
              @NonNull byte[] securedConfiguration, @NonNull byte[] accountConfiguration, boolean hasPeerAccount) {

        super(serializer, requestId);

        this.securedConfiguration = securedConfiguration;
        this.accountConfiguration = accountConfiguration;
        this.hasPeerAccount = hasPeerAccount;
    }

    protected int getBufferSize() {

        return SERIALIZER_BUFFER_DEFAULT_SIZE + securedConfiguration.length + accountConfiguration.length;
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {
        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);

            stringBuilder.append(" securedConfiguration=");
            stringBuilder.append(securedConfiguration.length);
            stringBuilder.append(" accountConfiguration=");
            stringBuilder.append(accountConfiguration.length);
            stringBuilder.append(" hasPeerAccount=");
            stringBuilder.append(hasPeerAccount);
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("AccountIQ\n");
            appendTo(stringBuilder);
        }

        return stringBuilder.toString();
    }

    //
    // Private Methods
    //

    private AccountIQ(@NonNull BinaryPacketIQSerializer serializer, @NonNull BinaryPacketIQ serviceRequestIQ,
                      @NonNull byte[] securedConfiguration, @NonNull byte[] accountConfiguration, boolean hasPeerAccount) {

        super(serializer, serviceRequestIQ);

        this.securedConfiguration = securedConfiguration;
        this.accountConfiguration = accountConfiguration;
        this.hasPeerAccount = hasPeerAccount;
    }
}
