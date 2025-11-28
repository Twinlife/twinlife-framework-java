/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

public class TwinlifeConfiguration {

    protected String serviceId;
    protected String applicationId;
    protected String applicationName;
    protected String applicationVersion;
    protected String certificateSerialNumber;
    protected Serializer[] serializers = new Serializer[0];
    protected String apiKey;
    protected RepositoryObjectFactory<?>[] factories;
    protected ProxyDescriptor[] proxies;
    protected String[] tokens;

    protected AccountService.AccountServiceConfiguration accountServiceConfiguration;
    protected ConversationService.ConversationServiceConfiguration conversationServiceConfiguration;
    protected ConnectivityService.ConnectivityServiceConfiguration connectivityServiceConfiguration;
    protected ManagementService.ManagementServiceConfiguration managementServiceConfiguration;
    protected NotificationService.NotificationServiceConfiguration notificationServiceConfiguration;
    protected PeerConnectionService.PeerConnectionServiceConfiguration peerConnectionServiceConfiguration;
    protected RepositoryService.RepositoryServiceConfiguration repositoryServiceConfiguration;
    protected TwincodeFactoryService.TwincodeFactoryServiceConfiguration twincodeFactoryServiceConfiguration;
    protected TwincodeInboundService.TwincodeInboundServiceConfiguration twincodeInboundServiceConfiguration;
    protected TwincodeOutboundService.TwincodeOutboundServiceConfiguration twincodeOutboundServiceConfiguration;
    protected ImageService.ImageServiceConfiguration imageServiceConfiguration;
    protected AccountMigrationService.AccountMigrationServiceConfiguration accountMigrationServiceConfiguration;
    protected PeerCallService.PeerCallServiceConfiguration peerCallServiceConfiguration;
    protected CryptoService.CryptoServiceServiceConfiguration cryptoServiceConfiguration;

    protected TwinlifeConfiguration() {

        accountServiceConfiguration = new AccountService.AccountServiceConfiguration();
        conversationServiceConfiguration = new ConversationService.ConversationServiceConfiguration();
        connectivityServiceConfiguration = new ConnectivityService.ConnectivityServiceConfiguration();
        managementServiceConfiguration = new ManagementService.ManagementServiceConfiguration();
        notificationServiceConfiguration = new NotificationService.NotificationServiceConfiguration();
        peerConnectionServiceConfiguration = new PeerConnectionService.PeerConnectionServiceConfiguration();
        repositoryServiceConfiguration = new RepositoryService.RepositoryServiceConfiguration();
        twincodeFactoryServiceConfiguration = new TwincodeFactoryService.TwincodeFactoryServiceConfiguration();
        twincodeInboundServiceConfiguration = new TwincodeInboundService.TwincodeInboundServiceConfiguration();
        twincodeOutboundServiceConfiguration = new TwincodeOutboundService.TwincodeOutboundServiceConfiguration();
        imageServiceConfiguration = new ImageService.ImageServiceConfiguration();
        accountMigrationServiceConfiguration = new AccountMigrationService.AccountMigrationServiceConfiguration();
        peerCallServiceConfiguration = new PeerCallService.PeerCallServiceConfiguration();
        cryptoServiceConfiguration = new CryptoService.CryptoServiceServiceConfiguration();
    }

    public void read(@NonNull byte[] data) throws SerializerException {
        final BinaryCompactDecoder reader = new BinaryCompactDecoder(new ByteArrayInputStream(data));
        applicationId = reader.readUUID().toString();
        serviceId = reader.readUUID().toString();
        apiKey = reader.readString();
        certificateSerialNumber = reader.readString();
        int keyProxyCount = reader.readInt();
        int sniProxyCount = reader.readInt();
        proxies = new ProxyDescriptor[keyProxyCount + sniProxyCount];
        for (int i = 0; i < keyProxyCount; i++) {
            int port = reader.readInt();
            int stunPort = reader.readInt();
            String addr = readIP(reader);
            String key = reader.readString();
            proxies[i] = new KeyProxyDescriptor(addr, port, stunPort, key);
        }
        for (int i = 0; i < sniProxyCount; i++) {
            int port = reader.readInt();
            int stunPort = reader.readInt();
            String addr = readIP(reader);
            proxies[i + keyProxyCount] = new SNIProxyDescriptor(addr, port, stunPort, null, false);
        }
        int tokenCount = reader.readInt();
        tokens = new String[tokenCount];
        for (int i = 0; i < tokenCount; i++) {
            tokens[i] = reader.readString();
        }
    }

    private static String readIP(@NonNull BinaryCompactDecoder decoder) throws SerializerException {
        int kind = decoder.readInt();
        if ((kind & 0x01) == 0) {
            return Utils.toIPv4(decoder.readLong());
        } else {
            return Utils.toIPv6(decoder.readLong(), decoder.readLong());
        }
    }
}
