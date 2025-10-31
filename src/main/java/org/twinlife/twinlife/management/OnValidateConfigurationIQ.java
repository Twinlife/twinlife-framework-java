/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.management;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.Hostname;
import org.twinlife.twinlife.TurnServer;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Validate configuration response IQ.
 *
 * Schema version 2
 * <pre>
 * {
 *  "schemaId":"A0589646-2B24-4D22-BE5B-6215482C8748",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnValidateConfigurationIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"environmentId", "type":"uuid"},
 *     {"name":"features", [null, "type":"string"]},
 *     {"name":"webrtcDisableAEC", [null, "type":"boolean"]},
 *     {"name":"webrtcDisableNS", [null, "type":"boolean"]},
 *     {"name":"turnTTL", "type":"int"},
 *     {"name":"turnServerCount", "type":"int"}, [
 *        {"name":"turnURL", "type":"string"},
 *        {"name":"turnUsername", "type":"string"},
 *        {"name":"turnPassword", "type":"string"},
 *     ]},
 *     {"name":"hostnames", "type":"int"}, [
 *        {"name":"hostname", "type":"string"},
 *        {"name":"ipv4", "type":"string"},
 *        {"name":"ipv6", "type":"string"},
 *     ]}
 *  ]
 * }
 *
 * </pre>
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"A0589646-2B24-4D22-BE5B-6215482C8748",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnValidateConfigurationIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"environmentId", "type":"uuid"},
 *     {"name":"features", [null, "type":"string"]},
 *     {"name":"webrtcDisableAEC", [null, "type":"boolean"]},
 *     {"name":"webrtcDisableNS", [null, "type":"boolean"]},
 *     {"name":"turnTTL", "type":"int"},
 *     {"name":"turnServerCount", "type":"int"}, [
 *        {"name":"turnURL", "type":"string"},
 *        {"name":"turnUsername", "type":"string"},
 *        {"name":"turnPassword", "type":"string"},
 *     ]}
 *  ]
 * }
 *
 * </pre>
 */
class OnValidateConfigurationIQ extends BinaryPacketIQ {

    private static class OnValidateConfigurationIQSerializer extends BinaryPacketIQSerializer {

        OnValidateConfigurationIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, OnValidateConfigurationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            OnValidateConfigurationIQ onValidateConfigurationIQ = (OnValidateConfigurationIQ) object;

            encoder.writeUUID(onValidateConfigurationIQ.environmentId);
            encoder.writeOptionalString(onValidateConfigurationIQ.features);
            if (onValidateConfigurationIQ.webrtcDisableAEC == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(onValidateConfigurationIQ.webrtcDisableAEC ? 1 : 2);
            }
            if (onValidateConfigurationIQ.webrtcDisableNS == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(onValidateConfigurationIQ.webrtcDisableNS ? 1 : 2);
            }

            encoder.writeInt(onValidateConfigurationIQ.turnTTL);
            encoder.writeInt(onValidateConfigurationIQ.turnServers.length);
            for (int i = 0; i < onValidateConfigurationIQ.turnServers.length; i++) {
                TurnServer turnServer = onValidateConfigurationIQ.turnServers[i];

                encoder.writeString(turnServer.url);
                encoder.writeString(turnServer.username);
                encoder.writeString(turnServer.password);
            }
            if (onValidateConfigurationIQ.hostnames == null) {
                encoder.writeInt(0);
            } else {
                encoder.writeInt(onValidateConfigurationIQ.hostnames.length);
                for (Hostname hostname : onValidateConfigurationIQ.hostnames) {
                    encoder.writeString(hostname.hostname);
                    encoder.writeString(hostname.ipv4);
                    encoder.writeString(hostname.ipv6);
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            BinaryPacketIQ serviceRequestIQ = (BinaryPacketIQ) super.deserialize(serializerFactory, decoder);

            UUID environmentId = decoder.readUUID();
            String features = decoder.readOptionalString();
            Boolean webrtcDisableAEC;
            switch (decoder.readInt()) {
                case 0:
                    webrtcDisableAEC = null;
                    break;

                case 1:
                    webrtcDisableAEC = Boolean.TRUE;
                    break;

                case 2:
                    webrtcDisableAEC = Boolean.FALSE;
                    break;

                default:
                    throw new SerializerException();
            }

            Boolean webrtcDisableNS;
            switch (decoder.readInt()) {
                case 0:
                    webrtcDisableNS = null;
                    break;

                case 1:
                    webrtcDisableNS = Boolean.TRUE;
                    break;

                case 2:
                    webrtcDisableNS = Boolean.FALSE;
                    break;

                default:
                    throw new SerializerException();
            }

            int turnTTL = decoder.readInt();
            int value = decoder.readInt();
            TurnServer[] turnServers = new TurnServer[value];
            for (int i = 0; i < value; i++) {
                String url = decoder.readString();
                String username = decoder.readString();
                String password = decoder.readString();

                turnServers[i] = new TurnServer(url, username, password);
            }

            int hostnameCount = decoder.readInt();
            Hostname[] hostnames = new Hostname[hostnameCount];
            for (int i = 0; i < hostnameCount; i++) {
                String hostname = decoder.readString();
                String ipv4 = decoder.readString();
                String ipv6 = decoder.readString();
                hostnames[i] = new Hostname(hostname, ipv4, ipv6);
            }
            return new OnValidateConfigurationIQ(this, serviceRequestIQ.getRequestId(), environmentId, features, webrtcDisableAEC,
                    webrtcDisableNS, turnTTL, turnServers, hostnames);
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new OnValidateConfigurationIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final UUID environmentId;
    @Nullable
    final String features;
    @Nullable
    final Boolean webrtcDisableAEC;
    @Nullable
    final Boolean webrtcDisableNS;
    final int turnTTL;
    @NonNull
    final TurnServer[] turnServers;
    @NonNull
    final Hostname[] hostnames;

    OnValidateConfigurationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
                              @NonNull UUID environmentId, @Nullable String features, @Nullable Boolean webrtcDisableAEC,
                              @Nullable Boolean webrtcDisableNS, int turnTTL,
                              @NonNull TurnServer[] turnServers,
                              @NonNull Hostname[] hostnames) {

        super(serializer, requestId);

        this.environmentId = environmentId;
        this.features = features;
        this.webrtcDisableAEC = webrtcDisableAEC;
        this.webrtcDisableNS = webrtcDisableNS;
        this.turnTTL = turnTTL;
        this.turnServers = turnServers;
        this.hostnames = hostnames;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" environmentId=");
            stringBuilder.append(environmentId);
            stringBuilder.append(" features=");
            stringBuilder.append(features);
            stringBuilder.append(" webrtcDisableAEC=");
            stringBuilder.append(webrtcDisableAEC);
            stringBuilder.append(" webrtcDisableNS=");
            stringBuilder.append(webrtcDisableNS);
            stringBuilder.append(" turnServers=");
            stringBuilder.append(turnServers);
            stringBuilder.append(" hostnames=");
            stringBuilder.append(hostnames);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("OnValidateConfigurationIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
