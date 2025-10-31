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

import org.twinlife.twinlife.BaseService.ErrorCode;

import java.io.File;

@SuppressWarnings("unused")
public interface Twinlife {

    String VERSION = BuildConfig.VERSION;

    String DOMAIN = ServerConfig.DOMAIN;

    int MAX_AVATAR_HEIGHT = 256;
    int MAX_AVATAR_WIDTH = 256;

    // Common directory names used in 'files' directory.
    String CONVERSATIONS_DIR = "conversations";
    String LOCAL_IMAGES_DIR = "pictures";
    String TMP_DIR = "tmp";
    String OLD_TMP_DIR = "images"; // Legacy tmp directory: images are removed when application was restarted.

    interface ServiceFactory {

        BaseServiceImpl<?> createServices(TwinlifeImpl twinlife, Connection connection);
    }

    boolean isConfigured();

    boolean isDatabaseUpgraded();

    @NonNull
    ErrorCode configure(@NonNull TwinlifeConfiguration twinlifeConfiguration,
                        @NonNull Connection connection);

    @NonNull
    ErrorCode getDatabaseStatus();

    void stop();

    @NonNull
    AccountService getAccountService();

    @NonNull
    ConnectivityService getConnectivityService();

    @NonNull
    ConversationService getConversationService();

    @Nullable
    ManagementService getManagementService();

    @NonNull
    NotificationService getNotificationService();

    @NonNull
    PeerConnectionService getPeerConnectionService();

    @NonNull
    RepositoryService getRepositoryService();

    @NonNull
    TwincodeFactoryService getTwincodeFactoryService();

    @NonNull
    TwincodeInboundService getTwincodeInboundService();

    @NonNull
    TwincodeOutboundService getTwincodeOutboundService();

    @NonNull
    ConfigurationService getConfigurationService();

    @NonNull
    ImageService getImageService();

    @NonNull
    AccountMigrationService getAccountMigrationService();

    @NonNull
    PeerCallService getPeerCallService();

    @NonNull
    SerializerFactory getSerializerFactory();

    @NonNull
    JobService getJobService();

    @NonNull
    File getFilesDir();

    @Nullable
    File getCacheDir();

    @Nullable
    PackageInfo getPackageInfo();

    void exception(@NonNull AssertPoint assertPoint, @Nullable Throwable exception, @Nullable AssertPoint.Values values);

    void assertion(@NonNull AssertPoint assertPoint, @Nullable AssertPoint.Values values);
}
