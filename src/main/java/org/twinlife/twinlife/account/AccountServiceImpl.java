/*
 *  Copyright (c) 2012-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Tengfei Wang (Tengfei.Wang@twinlife-systems.com)
 *   Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *   Xiaobo Xie (Xiaobo.Xie@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Olivier Dupont (Oliver.Dupont@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.SystemClock;
import android.util.Log;

import org.twinlife.twinlife.BackupInfo;
import org.twinlife.twinlife.Configuration;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.AccountService;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utf8;
import org.twinlife.twinlife.util.Utils;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AccountServiceImpl extends BaseServiceImpl<AccountService.ServiceObserver> implements AccountService {
    private static final String LOG_TAG = "AccountServiceImpl";
    private static final boolean DEBUG = false;

    // The Openfire server truncates passwords to 32 chars when an account is created.
    private static final int MAX_PASSWORD_LENGTH = 32;
    private static final int AUTH_REQUEST_TIMEOUT = 16000; // 16s max to wait for an auth challenge/request.

    private static final UUID AUTH_CHALLENGE_SCHEMA_ID = UUID.fromString("91780AB7-016A-463B-9901-434E52C200AE");
    private static final UUID AUTH_REQUEST_SCHEMA_ID = UUID.fromString("BF0A6327-FD04-4DFF-998E-72253CFD91E5");
    private static final UUID CREATE_ACCOUNT_SCHEMA_ID = UUID.fromString("84449ECB-F09F-4C12-A936-038948C2D980");
    private static final UUID DELETE_ACCOUNT_SCHEMA_ID = UUID.fromString("60e72a89-c1ef-49fa-86a8-0793e5e662e4");
    private static final UUID CHANGE_PASSWORD_SCHEMA_ID = UUID.fromString("f7295462-019e-4bd5-b830-20f98f8a9735");
    private static final UUID SUBSCRIBE_FEATURE_SCHEMA_ID = UUID.fromString("eb420020-e55a-44b0-9e9e-9922ec055407");
    private static final UUID CANCEL_FEATURE_SCHEMA_ID = UUID.fromString("0B20EF35-A5D9-45F2-9B97-C6B3D15983FA");
    private static final UUID PONG_SCHEMA_ID = UUID.fromString("fc0e491c-d91b-43c6-a25c-46d566c788b7");
    private static final UUID TERMINATE_ACCOUNT_RESTORE_SCHEMA_ID = UUID.fromString("2810fd0c-3973-41f3-912b-57872d881b2d");
    private static final UUID GENERATE_BACKUP_KEY_SCHEMA_ID = UUID.fromString("3d6cef13-f703-415c-bb5c-38459d8e32e1");
    private static final UUID GENERATE_RESTORE_KEY_SCHEMA_ID = UUID.fromString("acbdbf61-c43f-48e6-a0bc-17ebf11aed1e");
    private static final UUID GET_ALL_BACKUPS_SCHEMA_ID = UUID.fromString("b2598bed-cce1-421e-8723-57b0a20564b2");
    private static final UUID DELETE_BACKUPS_SCHEMA_ID = UUID.fromString("07e5a262-59c5-486c-a6f7-d3e72dcdcd91");
    private static final UUID RESTORE_CHALLENGE_SCHEMA_ID = UUID.fromString("093b4e5c-3040-48d1-9981-cb1f20c16d89");
    private static final UUID RESTORE_REQUEST_SCHEMA_ID = UUID.fromString("8576bcf4-5901-4e54-b5d5-7e3b70622a7f");

    private static final UUID ON_AUTH_CHALLENGE_SCHEMA_ID = UUID.fromString("A5F47729-2FEE-4B38-AC91-3A67F3F9E1B6");
    private static final UUID ON_AUTH_REQUEST_SCHEMA_ID = UUID.fromString("9CEE4256-D2B7-4DE3-A724-1F61BB1454C8");
    private static final UUID ON_AUTH_ERROR_SCHEMA_ID = UUID.fromString("ed230b09-b9ff-4d9a-83c9-ddcc3ad686c6");
    private static final UUID ON_CREATE_ACCOUNT_SCHEMA_ID = UUID.fromString("3D8A1111-61F8-4B27-8229-43DE24A9709B");
    private static final UUID ON_DELETE_ACCOUNT_SCHEMA_ID = UUID.fromString("48e15279-8070-4c49-a71c-ce876cca579e");
    private static final UUID ON_CHANGE_PASSWORD_SCHEMA_ID = UUID.fromString("64bcd660-e13d-45c3-b953-d75a9a5bac25");
    private static final UUID ON_SUBSCRIBE_FEATURE_SCHEMA_ID = UUID.fromString("50FEC907-1D63-4617-A099-D495971930EF");
    private static final UUID ON_CANCEL_FEATURE_SCHEMA_ID = UUID.fromString("34F465EA-A459-423A-A270-2612DC72DAB4");
    private static final UUID ON_SERVER_PING_SCHEMA_ID = UUID.fromString("fb21d934-f3b4-4432-a82f-0d5a1f17e685");
    private static final UUID ON_GENERATE_BACKUP_KEY_SCHEMA_ID = UUID.fromString("e5ac97e6-bf8b-4054-9115-17edaee8de83");
    private static final UUID ON_GET_ALL_BACKUPS_SCHEMA_ID = UUID.fromString("088b1c90-f9ea-4d1b-8be3-00d6e9b3afe8");
    private static final UUID ON_DELETE_BACKUPS_SCHEMA_ID = UUID.fromString("ce07c381-45f1-425d-8f68-2dad60f51052");
    private static final UUID ON_TERMINATE_RESTORE_SCHEMA_ID = UUID.fromString("a9945fd0-7f68-42ea-8b41-f6bc4d22cfb4");
    private static final UUID ON_RESTORE_CHALLENGE_SCHEMA_ID = UUID.fromString("caccd8d7-67a8-4868-ae79-af9504cc1f54");
    private static final UUID ON_RESTORE_CHALLENGE_ERROR_SCHEMA_ID = UUID.fromString("601d27bb-8cff-4cbb-9194-637b52ac6b07");
    private static final UUID ON_RESTORE_REQUEST_SCHEMA_ID = UUID.fromString("cb6ec9af-8af9-4cea-9b84-b62a1f757f59");
    private static final UUID ON_RESTORE_REQUEST_ERROR_SCHEMA_ID = UUID.fromString("8687513b-c783-40a3-95e4-70edcd9a5fb1");

    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_AUTH_CHALLENGE_SERIALIZER = AuthChallengeIQ.createSerializer(AUTH_CHALLENGE_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_AUTH_REQUEST_SERIALIZER = AuthRequestIQ.createSerializer(AUTH_REQUEST_SCHEMA_ID, 3);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_CREATE_ACCOUNT_SERIALIZER = CreateAccountIQ.createSerializer(CREATE_ACCOUNT_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_DELETE_ACCOUNT_SERIALIZER = DeleteAccountIQ.createSerializer(DELETE_ACCOUNT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_CHANGE_PASSWORD_SERIALIZER = ChangePasswordIQ.createSerializer(CHANGE_PASSWORD_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_SUBSCRIBE_FEATURE_SERIALIZER = SubscribeFeatureIQ.createSerializer(SUBSCRIBE_FEATURE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_CANCEL_FEATURE_SERIALIZER = CancelFeatureIQ.createSerializer(CANCEL_FEATURE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_PONG_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(PONG_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_TERMINATE_ACCOUNT_RESTORE_SERIALIZER = TerminateAccountRestoreIQ.createSerializer(TERMINATE_ACCOUNT_RESTORE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_GENERATE_BACKUP_KEY_SERIALIZER = GenerateBackupKeyIQ.createSerializer(GENERATE_BACKUP_KEY_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_GENERATE_RESTORE_KEY_SERIALIZER = GenerateBackupKeyIQ.createSerializer(GENERATE_RESTORE_KEY_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_GET_ALL_BACKUPS_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(GET_ALL_BACKUPS_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_DELETE_BACKUPS_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(DELETE_BACKUPS_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_RESTORE_CHALLENGE_SERIALIZER = RestoreChallengeIQ.createSerializer(RESTORE_CHALLENGE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_RESTORE_REQUEST_SERIALIZER = RestoreRequestIQ.createSerializer(RESTORE_REQUEST_SCHEMA_ID, 1);

    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_AUTH_CHALLENGE_SERIALIZER = OnAuthChallengeIQ.createSerializer(ON_AUTH_CHALLENGE_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_AUTH_REQUEST_SERIALIZER = OnAuthRequestIQ.createSerializer(ON_AUTH_REQUEST_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_AUTH_ERROR_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_AUTH_ERROR_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_CREATE_ACCOUNT_SERIALIZER = OnCreateAccountIQ.createSerializer(ON_CREATE_ACCOUNT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_CHANGE_PASSWORD_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_CHANGE_PASSWORD_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_DELETE_ACCOUNT_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_DELETE_ACCOUNT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_SUBSCRIBE_FEATURE_SERIALIZER = OnSubscribeFeatureIQ.createSerializer(ON_SUBSCRIBE_FEATURE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_CANCEL_FEATURE_SERIALIZER = OnSubscribeFeatureIQ.createSerializer(ON_CANCEL_FEATURE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_SERVER_PING_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_SERVER_PING_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_GENERATE_BACKUP_KEY_SERIALIZER = OnGenerateBackupKeyIQ.createSerializer(ON_GENERATE_BACKUP_KEY_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_GET_ALL_BACKUPS_SERIALIZER = OnGetAllBackupsIQ.createSerializer(ON_GET_ALL_BACKUPS_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_DELETE_BACKUPS_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_DELETE_BACKUPS_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_TERMINATE_RESTORE_SERIALIZER = OnTerminateAccountRestoreIQ.createSerializer(ON_TERMINATE_RESTORE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_RESTORE_CHALLENGE_SERIALIZER = OnAuthChallengeIQ.createSerializer(ON_RESTORE_CHALLENGE_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_RESTORE_CHALLENGE_ERROR_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_RESTORE_CHALLENGE_ERROR_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_RESTORE_REQUEST_SERIALIZER = OnAuthRequestIQ.createSerializer(ON_RESTORE_REQUEST_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_RESTORE_REQUEST_ERROR_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_RESTORE_REQUEST_ERROR_SCHEMA_ID, 1);

    enum RequestKind {
        SUBSCRIBE_REQUEST,
        CANCEL_REQUEST,
        DELETE_ACCOUNT_REQUEST,
        CHANGE_PASSWORD_REQUEST,
        AUTH_CHALLENGE_REQUEST,
        AUTH_REQUEST_REQUEST,
        GENERATE_BACKUP_PASSWORD_REQUEST,
        GET_ALL_BACKUPS_REQUEST,
        DELETE_BACKUPS_REQUEST,
        RESTORE_CHALLENGE_REQUEST,
        RESTORE_REQUEST_REQUEST,
        TERMINATE_RESTORE_REQUEST;

        PendingRequest toPendingRequest() {
            return new PendingRequest(this);
        }
    }

    private static class PendingRequest {
        @NonNull
        final RequestKind requestKind;

        PendingRequest(@NonNull RequestKind requestKind) {
            this.requestKind = requestKind;
        }

        @NonNull
        @Override
        public String toString() {
            return "PendingRequest[" + requestKind + "]";
        }
    }

    private static class ConsumerPendingRequest<T> extends PendingRequest {
        @NonNull
        final Consumer<T> consumer;

        ConsumerPendingRequest(@NonNull RequestKind requestKind, @NonNull Consumer<T> consumer) {
            super(requestKind);
            this.consumer = consumer;
        }
    }

    private static class AuthChallengePendingRequest extends PendingRequest {
        @NonNull
        final AuthChallengeIQ authChallengeIQ;

        AuthChallengePendingRequest(@NonNull AuthChallengeIQ authChallengeIQ) {
            super(RequestKind.AUTH_CHALLENGE_REQUEST);
            this.authChallengeIQ = authChallengeIQ;
        }
    }

    private static class AuthRequestPendingRequest extends PendingRequest {
        @NonNull
        final AuthChallengeIQ authChallengeIQ;
        @NonNull
        final OnAuthChallengeIQ onAuthChallengeIQ;
        @NonNull
        final byte[] serverKey;
        final long authRequestTime;

        AuthRequestPendingRequest(@NonNull AuthChallengeIQ authChallengeIQ, @NonNull OnAuthChallengeIQ onAuthChallengeIQ, @NonNull byte[] serverKey, long authRequestTime) {
            super(RequestKind.AUTH_CHALLENGE_REQUEST);
            this.authChallengeIQ = authChallengeIQ;
            this.onAuthChallengeIQ = onAuthChallengeIQ;
            this.serverKey = serverKey;
            this.authRequestTime = authRequestTime;
        }
    }

    private static class ChangePasswordPendingRequest extends PendingRequest {

        @NonNull
        final String newDevicePassword;

        ChangePasswordPendingRequest(@NonNull String newDevicePassword) {
            super(RequestKind.CHANGE_PASSWORD_REQUEST);
            this.newDevicePassword = newDevicePassword;
        }
    }

    private static class GenerateBackupPasswordPendingRequest extends ConsumerPendingRequest<OnGenerateBackupKeyIQ> {

        GenerateBackupPasswordPendingRequest(@NonNull Consumer<OnGenerateBackupKeyIQ> consumer) {
            super(RequestKind.GENERATE_BACKUP_PASSWORD_REQUEST, consumer);
        }
    }

    private static class GetAllBackupsPendingRequest extends ConsumerPendingRequest<List<BackupInfo>> {

        GetAllBackupsPendingRequest(@NonNull Consumer<List<BackupInfo>> consumer) {
            super(RequestKind.GET_ALL_BACKUPS_REQUEST, consumer);
        }
    }

    private static class DeleteBackupsPendingRequest extends ConsumerPendingRequest<Void> {

        DeleteBackupsPendingRequest(@NonNull Consumer<Void> consumer) {
            super(RequestKind.DELETE_BACKUPS_REQUEST, consumer);
        }
    }

    private static class TerminateRestorePendingRequest extends ConsumerPendingRequest<Integer> {

        TerminateRestorePendingRequest(@NonNull Consumer<Integer> consumer) {
            super(RequestKind.TERMINATE_RESTORE_REQUEST, consumer);
        }
    }

    private static class RestoreChallengePendingRequest extends ConsumerPendingRequest<Void> {
        @NonNull
        final AccountSecuredConfiguration accountSecuredConfiguration;
        @NonNull
        final RestoreChallengeIQ restoreChallengeIQ;

        RestoreChallengePendingRequest(@NonNull AccountSecuredConfiguration accountSecuredConfiguration, @NonNull RestoreChallengeIQ restoreChallengeIQ, @NonNull Consumer<Void> consumer) {
            super(RequestKind.RESTORE_CHALLENGE_REQUEST, consumer);
            this.accountSecuredConfiguration = accountSecuredConfiguration;
            this.restoreChallengeIQ = restoreChallengeIQ;
        }
    }

    private static class RestoreRequestPendingRequest extends ConsumerPendingRequest<Void> {
        @NonNull
        final RestoreChallengeIQ restoreChallengeIQ;
        @NonNull
        final OnAuthChallengeIQ onRestoreChallengeIQ;
        @NonNull
        final byte[] serverKey;
        @NonNull
        final AccountSecuredConfiguration accountSecuredConfiguration;

        RestoreRequestPendingRequest(@NonNull RestoreChallengePendingRequest restoreChallengePendingRequest, @NonNull OnAuthChallengeIQ onRestoreChallengeIQ, @NonNull byte[] serverKey) {
            super(RequestKind.RESTORE_REQUEST_REQUEST, restoreChallengePendingRequest.consumer);

            this.restoreChallengeIQ = restoreChallengePendingRequest.restoreChallengeIQ;
            this.onRestoreChallengeIQ = onRestoreChallengeIQ;
            this.serverKey = serverKey;
            this.accountSecuredConfiguration = restoreChallengePendingRequest.accountSecuredConfiguration;
        }
    }


    private AccountSecuredConfiguration mAccountSecuredConfiguration;
    private AccountSecuredConfiguration mRestoreAccountSecuredConfiguration;

    private final Set<String> mAllowedFeatures;
    private final UUID mApplicationId;
    private final UUID mServiceId;
    private final String mApiKey;
    private final String mAccessToken;
    private final HashMap<Long, PendingRequest> mPendingRequests;
    private boolean mCreateAccountAllowed;
    @Nullable
    private volatile String mAuthUser;

    public AccountServiceImpl(@NonNull TwinlifeImpl service, @NonNull Connection connection,
                              @NonNull UUID applicationId, @NonNull UUID serviceId, @NonNull String apiKey, @NonNull String accessToken) {
        super(service, connection);

        mSerializerFactory.addSerializer(IQ_AUTH_CHALLENGE_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_AUTH_REQUEST_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_CREATE_ACCOUNT_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_DELETE_ACCOUNT_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_SUBSCRIBE_FEATURE_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_CANCEL_FEATURE_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_CHANGE_PASSWORD_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_AUTH_CHALLENGE_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_AUTH_REQUEST_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_AUTH_ERROR_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_CREATE_ACCOUNT_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_DELETE_ACCOUNT_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_CHANGE_PASSWORD_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_SUBSCRIBE_FEATURE_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_CANCEL_FEATURE_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_SERVER_PING_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_PONG_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_TERMINATE_ACCOUNT_RESTORE_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_GENERATE_BACKUP_KEY_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_GENERATE_RESTORE_KEY_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_GENERATE_BACKUP_KEY_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_GET_ALL_BACKUPS_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_GET_ALL_BACKUPS_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_DELETE_BACKUPS_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_DELETE_BACKUPS_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_TERMINATE_RESTORE_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_RESTORE_CHALLENGE_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_RESTORE_CHALLENGE_ERROR_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_RESTORE_REQUEST_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_RESTORE_REQUEST_ERROR_SERIALIZER);

        // Register the binary IQ handlers for the responses.
        connection.addPacketListener(IQ_ON_AUTH_CHALLENGE_SERIALIZER, this::onAuthChallenge);
        connection.addPacketListener(IQ_ON_AUTH_REQUEST_SERIALIZER, this::onAuthRequest);
        connection.addPacketListener(IQ_ON_AUTH_ERROR_SERIALIZER, this::onAuthError);
        connection.addPacketListener(IQ_ON_CREATE_ACCOUNT_SERIALIZER, this::onCreateAccount);
        connection.addPacketListener(IQ_ON_DELETE_ACCOUNT_SERIALIZER, this::onDeleteAccount);
        connection.addPacketListener(IQ_ON_CHANGE_PASSWORD_SERIALIZER, this::onChangePassword);
        connection.addPacketListener(IQ_ON_SUBSCRIBE_FEATURE_SERIALIZER, this::onSubscribeFeature);
        connection.addPacketListener(IQ_ON_CANCEL_FEATURE_SERIALIZER, this::onSubscribeFeature);
        connection.addPacketListener(IQ_ON_SERVER_PING_SERIALIZER, this::onServerPingIQ);
        connection.addPacketListener(IQ_ON_GENERATE_BACKUP_KEY_SERIALIZER, this::onGenerateBackupKey);
        connection.addPacketListener(IQ_ON_GET_ALL_BACKUPS_SERIALIZER, this::onGetAllBackups);
        connection.addPacketListener(IQ_ON_DELETE_BACKUPS_SERIALIZER, this::onDeleteBackups);
        connection.addPacketListener(IQ_ON_RESTORE_CHALLENGE_SERIALIZER, this::onOnRestoreChallenge);
        connection.addPacketListener(IQ_ON_RESTORE_REQUEST_SERIALIZER, this::onOnRestoreRequest);
        connection.addPacketListener(IQ_ON_TERMINATE_RESTORE_SERIALIZER, this::onTerminateRestoreIQ);
        connection.addPacketListener(IQ_ON_RESTORE_CHALLENGE_ERROR_SERIALIZER, this::onRestoreAuthError);
        connection.addPacketListener(IQ_ON_RESTORE_REQUEST_ERROR_SERIALIZER, this::onRestoreAuthError);

        mApplicationId = applicationId;
        mServiceId = serviceId;
        mApiKey = apiKey;
        mAccessToken = accessToken;
        mAllowedFeatures = new HashSet<>();
        mPendingRequests = new HashMap<>();
        AccountServiceConfiguration accountServiceConfiguration = new AccountServiceConfiguration();
        accountServiceConfiguration.defaultAuthenticationAuthority = AuthenticationAuthority.TWINLIFE;
        setServiceConfiguration(accountServiceConfiguration);
    }

    //
    // Override BaseServiceImpl methods
    //

    public synchronized void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof AccountServiceConfiguration)) {
            setConfigured(false);

            return;
        }
        AccountServiceConfiguration accountServiceConfiguration = new AccountServiceConfiguration();

        AccountServiceConfiguration serviceConfiguration = (AccountServiceConfiguration) baseServiceConfiguration;

        accountServiceConfiguration.defaultAuthenticationAuthority = serviceConfiguration.defaultAuthenticationAuthority;
        mCreateAccountAllowed = (accountServiceConfiguration.defaultAuthenticationAuthority == AuthenticationAuthority.DEVICE);

        setServiceConfiguration(accountServiceConfiguration);

        // Create the platform dependent credentials.
        mAccountSecuredConfiguration = AccountSecuredConfiguration.init(mTwinlifeImpl.getConfigurationService(), mSerializerFactory, accountServiceConfiguration);

        if (mAccountSecuredConfiguration.getSubscribedFeatures() != null) {
            String[] featureList = mAccountSecuredConfiguration.getSubscribedFeatures().split(",");
            mAllowedFeatures.addAll(Arrays.asList(featureList));
        }

        setServiceOn(true);
        setConfigured(true);
    }

    @Override
    public void onConnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConnect");
        }

        super.onConnect();

        AuthenticationAuthority authenticationAuthority = getAuthenticationAuthority();
        switch (authenticationAuthority) {
            case DEVICE:
                deviceSignIn();
                break;

            case UNREGISTERED:
                // Explicitly create the account for the first time, once the account is created the
                // authenticate authority will change to DEVICE.
                if (mCreateAccountAllowed) {
                    createAccount(newRequestId(), "");
                }
                break;

            case DISABLED:
                // This account has been deleted and we have no way to authenticate nor recover.
                for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
                    serviceObserver.onSignInError(ErrorCode.ACCOUNT_DELETED);
                }

                mTwinlifeImpl.disconnect();
                break;

            default:
                break;
        }
    }

    private static final Set<RequestKind> AUTH_REQUEST_KINDS = Set.of(
            RequestKind.AUTH_CHALLENGE_REQUEST,
            RequestKind.AUTH_REQUEST_REQUEST,
            RequestKind.RESTORE_CHALLENGE_REQUEST,
            RequestKind.RESTORE_REQUEST_REQUEST
    );

    @Override
    public void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        // Erase sensitive information in case an authentication has not finished.
        synchronized (mPendingRequests) {
            for (Iterator<Map.Entry<Long, PendingRequest>> iterator = mPendingRequests.entrySet().iterator(); iterator.hasNext(); ) {
                Map.Entry<Long, PendingRequest> entry = iterator.next();
                if (AUTH_REQUEST_KINDS.contains(entry.getValue().requestKind)) {
                    iterator.remove();
                }
            }
        }
        mAuthUser = null;

        super.onDisconnect();
    }

    @Override
    public void onSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignIn");
        }

        super.onSignIn();

        for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
            serviceObserver.onSignIn();
        }
    }

    @Override
    public void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        super.onSignOut();

        for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
            serviceObserver.onSignOut();
        }
    }

    @Override
    public void onUpdateConfiguration(@NonNull Configuration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateConfiguration configuration=" + configuration);
        }

        super.onUpdateConfiguration(configuration);

        AccountSecuredConfiguration accountSecuredConfiguration;
        synchronized (this) {
            accountSecuredConfiguration = getActiveAccountSecuredConfiguration();

            final String subscribedFeatures = accountSecuredConfiguration.getSubscribedFeatures();
            final UUID environmentId = accountSecuredConfiguration.getEnvironmentId();
            if (Utils.equals(subscribedFeatures, configuration.features) && Utils.equals(environmentId, configuration.environmentId)) {

                return;
            }
        }

        // When the allowed features or the environment is defined, update the list.
        synchronized (this) {
            if (configuration.environmentId != null) {
                accountSecuredConfiguration.setEnvironmentId(configuration.environmentId);
            }
            accountSecuredConfiguration.setSubscribedFeatures(configuration.features);
            mAllowedFeatures.clear();

            if (configuration.features != null) {
                String[] featureList = configuration.features.split(",");
                mAllowedFeatures.addAll(Arrays.asList(featureList));
            }

            if (!mTwinlifeImpl.getBackupService().isRestoreInProgress()) {
                // Save so that we can restore a default subscribedFeatures list when we don't have the network,
                // but only if we're not restoring a backup: we don't want to modify the config until
                // restore is successful and confirmed by the user.
                accountSecuredConfiguration.save(mTwinlifeImpl.getConfigurationService(), mSerializerFactory);
            }
        }
    }

    //
    // Implement AccountService interface
    //

    @Override
    public AuthenticationAuthority getAuthenticationAuthority() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getAuthenticationAuthority");
        }

        if (!isServiceOn()) {

            return AuthenticationAuthority.UNREGISTERED;
        }

        synchronized (this) {

            return getActiveAccountSecuredConfiguration().getAuthenticationAuthority();
        }
    }

    @Override
    public boolean isSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isSignIn");
        }

        return mConnection.isConnected() && mAuthUser != null;
    }

    @Override
    public boolean isReconnectable() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isReconnectable");
        }

        if (!isServiceOn()) {

            return false;
        }

        synchronized (this) {

            return getActiveAccountSecuredConfiguration().isReconnectable();
        }
    }

    @Override
    public boolean isFeatureSubscribed(@NonNull String feature) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isFeatureSubscribed feature=" + feature);
        }

        synchronized (this) {
            return mAllowedFeatures.contains(feature);
        }
    }

    @Override
    public void createAccount(long requestId, @NonNull String authToken) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createAccount requestId=" + requestId + " authToken=" + authToken);
        }

        if (!isServiceOn()) {

            return;
        }

        // createAccount is allowed if we are not registered yet.
        final String username;
        final String password;
        synchronized (this) {
            AccountSecuredConfiguration accountSecuredConfiguration = getActiveAccountSecuredConfiguration();

            username = accountSecuredConfiguration.getUsername();
            password = accountSecuredConfiguration.getPassword();

            if (accountSecuredConfiguration.getAuthenticationAuthority() != AuthenticationAuthority.UNREGISTERED
                    || username == null || password == null) {

                onError(requestId, ErrorCode.NOT_AUTHORIZED_OPERATION, null);
                return;
            }
        }

        CreateAccountIQ createAccountIQ = new CreateAccountIQ(IQ_CREATE_ACCOUNT_SERIALIZER, requestId,
                mApplicationId, mServiceId, mApiKey, mAccessToken, mTwinlifeImpl.getApplicationName(),
                mTwinlifeImpl.getApplicationVersion(), Twinlife.VERSION,
                mTwinlifeImpl.toBareJid(username), password, authToken);

        // We must not use BaseServiceImpl::sendPacket() because we are not signed-in yet!
        try {
            byte[] packet = createAccountIQ.serialize(mSerializerFactory);

            packetTimeout(requestId, DEFAULT_REQUEST_TIMEOUT, true);
            if (!mConnection.sendDataPacket(packet)) {
                throw new IOException("Offline");
            }

        } catch (Exception exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "sendDataPacket failed", exception);
            }

            receivedIQ(requestId);
            onError(requestId, ErrorCode.TWINLIFE_OFFLINE, null);
        }
    }

    @Override
    public void signOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "signOut");
        }

        if (!isServiceOn()) {

            return;
        }

        if (!mTwinlifeImpl.getBackupService().isRestoreInProgress()) {
            synchronized (this) {
                mAccountSecuredConfiguration.signOut();
                mAccountSecuredConfiguration.save(mTwinlifeImpl.getConfigurationService(), mSerializerFactory);
            }
        }

        mTwinlifeImpl.onSignOut();
    }

    @Override
    public void deleteAccount(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteAccount requestId=" + requestId);
        }

        if (!isServiceOn()) {

            return;
        }

        if (mTwinlifeImpl.getBackupService().isRestoreInProgress()) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Restore in progress, ignoring delete account request");
            }
            return;
        }

        // Check that the account information we have is valid, if not proceed with the deletion.
        final String accountIdentifier = mTwinlifeImpl.toBareJid(mAccountSecuredConfiguration.getUsername());
        final String accountPassword = mAccountSecuredConfiguration.getPassword();
        if (accountIdentifier == null || accountPassword == null || !isReconnectable()) {

            finishDeleteAccount(requestId);
            return;
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, RequestKind.DELETE_ACCOUNT_REQUEST.toPendingRequest());
        }

        DeleteAccountIQ deleteAccountIQ = new DeleteAccountIQ(IQ_DELETE_ACCOUNT_SERIALIZER, requestId, accountIdentifier, accountPassword);
        sendDataPacket(deleteAccountIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void subscribeFeature(long requestId, @NonNull MerchantIdentification merchantId,
                                 @NonNull String purchaseProductId, @NonNull String purchaseToken, @NonNull String purchaseOrderId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "subscribeFeature requestId=" + requestId + " merchantId=" + merchantId
                    + " purchaseProductId=" + purchaseProductId + " purchaseToken=" + purchaseToken + " purchaseOrderId=" + purchaseOrderId);
        }

        if (!isServiceOn()) {

            return;
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, RequestKind.SUBSCRIBE_REQUEST.toPendingRequest());
        }

        SubscribeFeatureIQ subscribeFeatureIQ = new SubscribeFeatureIQ(IQ_SUBSCRIBE_FEATURE_SERIALIZER, requestId, merchantId,
                purchaseProductId, purchaseToken, purchaseOrderId);
        sendDataPacket(subscribeFeatureIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void cancelFeature(long requestId, @NonNull MerchantIdentification merchantId,
                              @NonNull String purchaseToken, @NonNull String purchaseOrderId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "cancelFeature requestId=" + requestId + " merchantId=" + merchantId
                    + " purchaseToken=" + purchaseToken + " purchaseOrderId=" + purchaseOrderId);
        }

        if (!isServiceOn()) {

            return;
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, RequestKind.CANCEL_REQUEST.toPendingRequest());
        }

        CancelFeatureIQ cancelFeatureIQ = new CancelFeatureIQ(IQ_CANCEL_FEATURE_SERIALIZER, requestId, merchantId,
                purchaseToken, purchaseOrderId);
        sendDataPacket(cancelFeatureIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Backup / restore
     */

    public void restoreChallenge(@NonNull ConfigurationService.SecuredConfiguration accountConfiguration, @NonNull UUID backupId, @NonNull Consumer<Void> restoreAuthConsumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "restoreChallenge: backupId=" + backupId);
        }

        BaseServiceConfiguration serviceConfiguration = getServiceConfiguration();
        if (!(serviceConfiguration instanceof AccountServiceConfiguration)) {
            throw new IllegalStateException("AccountServiceImpl's serviceConfiguration is not an AccountServiceConfiguration");
        }

        AccountSecuredConfiguration accountSecuredConfiguration = AccountSecuredConfiguration.init(mTwinlifeImpl.getConfigurationService(), mSerializerFactory, (AccountServiceConfiguration) serviceConfiguration, accountConfiguration);

        String username = accountSecuredConfiguration.getUsername();
        String password = accountSecuredConfiguration.getPassword();

        if (username == null || password == null) {
            Log.e(LOG_TAG, "accountConfiguration has no username and/or password, aborting restore auth. username:" + username + ", password:" + password);
            restoreAuthConsumer.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        // Generate nonce for the authentication challenge.
        SecureRandom random = new SecureRandom();
        byte[] deviceNonce = new byte[32];
        random.nextBytes(deviceNonce);

        final long requestId = newRequestId();

        RestoreChallengeIQ restoreChallengeIQ = new RestoreChallengeIQ(IQ_RESTORE_CHALLENGE_SERIALIZER, requestId, backupId, mTwinlifeImpl.toBareJid(username), deviceNonce);

        ErrorCode sendResult = sendRestoreAuthPacket(restoreChallengeIQ);
        if (sendResult != ErrorCode.SUCCESS) {
            restoreAuthConsumer.onGet(sendResult, null);
            receivedIQ(requestId);
            return;
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new RestoreChallengePendingRequest(accountSecuredConfiguration, restoreChallengeIQ, restoreAuthConsumer));
        }
    }

    private void onOnRestoreChallenge(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOnRestoreChallenge: iq=" + iq);
        }

        if (!(iq instanceof OnAuthChallengeIQ)) {
            return;
        }

        OnAuthChallengeIQ onRestoreChallengeIQ = (OnAuthChallengeIQ) iq;

        receivedIQ(onRestoreChallengeIQ.getRequestId());

        RestoreChallengePendingRequest restoreChallenge = removePendingRequest(onRestoreChallengeIQ.getRequestId(), RestoreChallengePendingRequest.class);
        if (restoreChallenge == null) {
            return;
        }

        RestoreChallengeIQ restoreChallengeIQ = restoreChallenge.restoreChallengeIQ;

        final String resource = mTwinlifeImpl.getResource();
        final long requestId = newRequestId();

        try {
            // Build the auth message that must be signed.
            StringBuilder authMessage = new StringBuilder();
            authMessage.append(restoreChallengeIQ.getClientFirstMessageBare());
            authMessage.append(",");
            authMessage.append(onRestoreChallengeIQ.getServerFirstMessage());
            authMessage.append(",");
            authMessage.append(resource);

            // Compute everything according to RFC 5802 section 3. SCRAM Algorithm Overview
            //noinspection DataFlowIssue At this point we know the account secured configuration has a password (see restoreChallenge())
            byte[] saltedPassword = createSaltedPassword(onRestoreChallengeIQ.salt, restoreChallenge.accountSecuredConfiguration.getPassword(), onRestoreChallengeIQ.iteration);
            byte[] clientKey = computeHmac(saltedPassword, "Client Key");
            byte[] storedKey = MessageDigest.getInstance("SHA-1").digest(clientKey);
            byte[] clientSignature = computeHmac(storedKey, authMessage.toString());

            // Compute the server key for last step server signature verification.
            byte[] serverKey = computeHmac(saltedPassword, "Server Key");

            // Create the client proof to send.
            byte[] clientProof = clientKey.clone();
            for (int i = 0; i < clientProof.length; i++) {
                clientProof[i] ^= clientSignature[i];
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "Salt=" + Utils.bytesToHex(onRestoreChallengeIQ.salt) + " iterations=" + onRestoreChallengeIQ.iteration);
                Log.d(LOG_TAG, "ClientKey=" + Utils.bytesToHex(clientKey));
                Log.d(LOG_TAG, "StoredKey=" + Utils.bytesToHex(storedKey));
                Log.d(LOG_TAG, "AuthMessageSHA1=" + Utils.bytesToHex(MessageDigest.getInstance("SHA-1").digest(authMessage.toString().getBytes())));
                Log.d(LOG_TAG, "AuthMessage=" + authMessage);
                Log.d(LOG_TAG, "ClientSign=" + Utils.bytesToHex(clientSignature));
                Log.d(LOG_TAG, "ClientProof=" + Utils.bytesToHex(clientProof));
            }

            RestoreRequestIQ restoreRequestIQ = new RestoreRequestIQ(IQ_RESTORE_REQUEST_SERIALIZER, requestId,
                    restoreChallengeIQ.accountIdentifier, mTwinlifeImpl.getResource(), restoreChallengeIQ.nonce,
                    clientProof, restoreChallengeIQ.backupId);

            ErrorCode sendResult = sendRestoreAuthPacket(restoreRequestIQ);
            if (sendResult != ErrorCode.SUCCESS) {
                restoreChallenge.consumer.onGet(sendResult, null);
                receivedIQ(requestId);
                return;
            }

            RestoreRequestPendingRequest restoreRequestPendingRequest = new RestoreRequestPendingRequest(restoreChallenge, onRestoreChallengeIQ, serverKey);
            synchronized (mPendingRequests) {
                mPendingRequests.put(requestId, restoreRequestPendingRequest);
            }

        } catch (Exception exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "onOnRestoreChallenge", exception);
            }

            receivedIQ(requestId);

            restoreChallenge.consumer.onGet(ErrorCode.SERVER_ERROR, null);

            // We can do nothing if the SHA1 algorithm is not provided. In other cases, disconnect to trigger another try later.
            if (!(exception instanceof GeneralSecurityException)) {
                mTwinlifeImpl.disconnect();
            }
        }
    }
        private void onOnRestoreRequest(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOnRestoreRequest: iq=" + iq);
        }

        if (!(iq instanceof OnAuthRequestIQ)) {
            return;
        }

        receivedIQ(iq.getRequestId());

        RestoreRequestPendingRequest restoreRequest = removePendingRequest(iq.getRequestId(), RestoreRequestPendingRequest.class);
        if (restoreRequest == null) {
            return;
        }

        RestoreChallengeIQ restoreChallengeIQ = restoreRequest.restoreChallengeIQ;
        OnAuthChallengeIQ onRestoreChallengeIQ = restoreRequest.onRestoreChallengeIQ;
        byte[] serverKey = restoreRequest.serverKey;
        Consumer<Void> consumer = restoreRequest.consumer;

        try {
            OnAuthRequestIQ onAuthRequestIQ = (OnAuthRequestIQ) iq;
            String resource = mTwinlifeImpl.getResource();

            String authMessage = restoreChallengeIQ.getClientFirstMessageBare() +
                    "," +
                    onRestoreChallengeIQ.getServerFirstMessage() +
                    "," +
                    resource;
            byte[] serverSignature = computeHmac(serverKey, authMessage);

            // Compute the server signature.
            if (DEBUG) {
                Log.d(LOG_TAG, "ServerSign=" + Utils.bytesToHex(serverSignature));
            }

            // Verify the server signature.
            if (!Arrays.equals(serverSignature, onAuthRequestIQ.serverSignature)) {

                mTwinlifeImpl.disconnect();
                consumer.onGet(ErrorCode.SERVER_ERROR, null);

                return;
            }

            synchronized (this) {
                mRestoreAccountSecuredConfiguration = restoreRequest.accountSecuredConfiguration;
            }

            mAuthUser = restoreChallengeIQ.accountIdentifier + '/' + resource;
            mTwinlifeImpl.onSignIn();

            consumer.onGet(ErrorCode.SUCCESS, null);
        } catch (GeneralSecurityException exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "onOnRestoreRequest", exception);
            }

            mTwinlifeImpl.disconnect();
            consumer.onGet(ErrorCode.ENCRYPT_ERROR, null);
        }
    }

    private void onRestoreAuthError(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRestoreAuthError: iq=" + iq);
        }

        if (!(iq instanceof BinaryErrorPacketIQ)) {
            return;
        }

        BinaryErrorPacketIQ errorPacketIQ = (BinaryErrorPacketIQ) iq;

        Log.e(LOG_TAG, "Restore auth failed: " + errorPacketIQ);

        receivedIQ(iq.getRequestId());

        mAuthUser = null;
        onSignOut();

        ConsumerPendingRequest<?> pendingRequest = removePendingRequest(iq.getRequestId(), RestoreChallengePendingRequest.class);
        if (pendingRequest == null) {
            pendingRequest = removePendingRequest(iq.getRequestId(), RestoreRequestPendingRequest.class);
            if (pendingRequest == null) {
                return;
            }
        }

        pendingRequest.consumer.onGet(errorPacketIQ.getErrorCode(), null);
    }

    public synchronized void removeRestoreAccountSecuredConfiguration() {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeRestoreAccountSecuredConfiguration");
        }

        mRestoreAccountSecuredConfiguration = null;
    }

    @Nullable
    private AccountSecuredConfiguration getActiveAccountSecuredConfiguration() {
        return mRestoreAccountSecuredConfiguration != null ? mRestoreAccountSecuredConfiguration : mAccountSecuredConfiguration;
    }

    /**
     * Checks whether a SecuredAccountConfiguration has the same credentials as the active one.
     *
     * @param accountConfiguration The configuration extracted from the backup file
     * @return True if {@param accountConfiguration} has the same credentials as the active one.
     */
    public boolean isCurrentAccount(@NonNull ConfigurationService.SecuredConfiguration accountConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "isCurrentAccount: accountConfiguration=" + accountConfiguration);
        }

        BaseServiceConfiguration serviceConfiguration = getServiceConfiguration();
        if (!(serviceConfiguration instanceof AccountServiceConfiguration)) {
            throw new IllegalStateException("AccountServiceImpl's serviceConfiguration is not an AccountSecuredConfiguration");
        }

        AccountSecuredConfiguration accountSecuredConfiguration = AccountSecuredConfiguration.init(mTwinlifeImpl.getConfigurationService(), mSerializerFactory, (AccountServiceConfiguration) serviceConfiguration, accountConfiguration);

        if (!Objects.equals(accountSecuredConfiguration.getUsername(), mAccountSecuredConfiguration.getUsername())) {
            return false;
        }

        return Objects.equals(accountSecuredConfiguration.getPassword(), mAccountSecuredConfiguration.getPassword());
    }

    public void generateBackupKey(@NonNull UUID backupId, @NonNull byte[] password, @NonNull byte[] salt, boolean forRestore, @NonNull Consumer<DerivedServerKeyInfo> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "generateBackupKey: backupId=" + backupId + " password.length=" + password.length + " salt.length=" + salt.length + " onComplete=" + onComplete);
        }

        if (!isServiceOn()) {

            return;
        }

        byte[] derivedKey = mTwinlifeImpl.getCryptoService().deriveKey(password, salt);

        long requestId = newRequestId();

        Consumer<OnGenerateBackupKeyIQ> consumer = (errorCode, iq) -> {
            if (errorCode != ErrorCode.SUCCESS || iq == null) {
                onComplete.onGet(errorCode, null);
                return;
            }

            byte[] finalKey = mTwinlifeImpl.getCryptoService().deriveKey(iq.derivedServerKey, salt);

            onComplete.onGet(ErrorCode.SUCCESS, new DerivedServerKeyInfo(finalKey, iq.lastBackupId, iq.lastBackupTimestamp));
        };

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new GenerateBackupPasswordPendingRequest(consumer));
        }

        BinaryPacketIQ.BinaryPacketIQSerializer serializer = forRestore ? IQ_GENERATE_RESTORE_KEY_SERIALIZER : IQ_GENERATE_BACKUP_KEY_SERIALIZER;

        GenerateBackupKeyIQ generateBackupKeyIQ = new GenerateBackupKeyIQ(serializer, requestId, backupId, derivedKey);
        sendDataPacket(generateBackupKeyIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    public void onGenerateBackupKey(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGenerateBackupKey: iq=" + iq);
        }

        if (!(iq instanceof OnGenerateBackupKeyIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        GenerateBackupPasswordPendingRequest pendingRequest = removePendingRequest(requestId, GenerateBackupPasswordPendingRequest.class);
        if (pendingRequest == null) {
            return;
        }

        pendingRequest.consumer.onGet(ErrorCode.SUCCESS, ((OnGenerateBackupKeyIQ) iq));
    }

    public void getAllBackups(@NonNull Consumer<List<BackupInfo>> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getAllBackups");
        }

        if (!isServiceOn()) {

            return;
        }

        long requestId = newRequestId();

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new GetAllBackupsPendingRequest(onComplete));
        }

        BinaryPacketIQ iq = new BinaryPacketIQ(IQ_GET_ALL_BACKUPS_SERIALIZER, requestId);
        sendDataPacket(iq, DEFAULT_REQUEST_TIMEOUT);
    }

    private void onGetAllBackups(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetAllBackups: iq=" + iq);
        }

        if (!(iq instanceof OnGetAllBackupsIQ)) {
            return;
        }

        OnGetAllBackupsIQ onGetAllBackupsIQ = (OnGetAllBackupsIQ) iq;

        final long requestId = onGetAllBackupsIQ.getRequestId();
        receivedIQ(requestId);

        GetAllBackupsPendingRequest pendingRequest = removePendingRequest(requestId, GetAllBackupsPendingRequest.class);
        if (pendingRequest == null) {
            return;
        }

        pendingRequest.consumer.onGet(ErrorCode.SUCCESS, onGetAllBackupsIQ.backups);
    }

    public void deleteBackups(@NonNull Consumer<Void> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteBackups");
        }

        if (!isServiceOn()) {

            return;
        }

        long requestId = newRequestId();

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new DeleteBackupsPendingRequest(onComplete));
        }

        BinaryPacketIQ iq = new BinaryPacketIQ(IQ_DELETE_BACKUPS_SERIALIZER, requestId);
        sendDataPacket(iq, DEFAULT_REQUEST_TIMEOUT);
    }

    private void onDeleteBackups(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteBackups: iq=" + iq);
        }

        if (!(iq instanceof BinaryErrorPacketIQ)) {
            return;
        }

        BinaryErrorPacketIQ binaryErrorPacketIQ = (BinaryErrorPacketIQ) iq;

        final long requestId = binaryErrorPacketIQ.getRequestId();
        receivedIQ(requestId);

        DeleteBackupsPendingRequest pendingRequest = removePendingRequest(requestId, DeleteBackupsPendingRequest.class);
        if (pendingRequest == null) {
            return;
        }

        pendingRequest.consumer.onGet(binaryErrorPacketIQ.getErrorCode(), null);
    }

    public void commitRestore(@NonNull Consumer<Integer> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "commitRestore");
        }

        long requestId = newRequestId();

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new TerminateRestorePendingRequest(onComplete));
        }

        TerminateAccountRestoreIQ iq = new TerminateAccountRestoreIQ(IQ_TERMINATE_ACCOUNT_RESTORE_SERIALIZER, requestId, true);
        sendDataPacket(iq, DEFAULT_REQUEST_TIMEOUT);
    }

    public void rollbackRestore(@NonNull Consumer<Integer> onComplete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "rollbackRestore");
        }

        long requestId = newRequestId();

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new TerminateRestorePendingRequest(onComplete));
        }

        TerminateAccountRestoreIQ iq = new TerminateAccountRestoreIQ(IQ_TERMINATE_ACCOUNT_RESTORE_SERIALIZER, requestId, false);
        sendDataPacket(iq, DEFAULT_REQUEST_TIMEOUT);
    }

    private void onTerminateRestoreIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTerminateRestore: iq=" + iq);
        }

        if (!(iq instanceof OnTerminateAccountRestoreIQ)) {
            return;
        }

        OnTerminateAccountRestoreIQ onTerminateAccountRestoreIQ = (OnTerminateAccountRestoreIQ) iq;

        receivedIQ(iq.getRequestId());

        TerminateRestorePendingRequest pendingRequest = removePendingRequest(iq.getRequestId(), TerminateRestorePendingRequest.class);
        if (pendingRequest == null) {
            return;
        }

        pendingRequest.consumer.onGet(onTerminateAccountRestoreIQ.errorCode, onTerminateAccountRestoreIQ.restoreCount);
    }

    public void restoreAccountSecuredConfiguration(@NonNull ConfigurationService.SecuredConfiguration accountConfiguration, int restoreCount) {
        if (DEBUG) {
            Log.d(LOG_TAG, "restoreAccountSecuredConfiguration: accountConfiguration=" + accountConfiguration);
        }

        BaseServiceConfiguration serviceConfiguration = getServiceConfiguration();
        if (!(serviceConfiguration instanceof AccountServiceConfiguration)) {
            throw new IllegalStateException("AccountServiceImpl's serviceConfiguration is not an AccountServiceConfiguration");
        }

        AccountSecuredConfiguration accountSecuredConfiguration = AccountSecuredConfiguration.init(mTwinlifeImpl.getConfigurationService(), mSerializerFactory, (AccountServiceConfiguration) serviceConfiguration, accountConfiguration);

        accountSecuredConfiguration.setIncarnationCount(restoreCount);

        accountSecuredConfiguration.save(mTwinlifeImpl.getConfigurationService(), mSerializerFactory);
    }

    @Nullable
    public UUID getEnvironmentId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getEnvironmentId");
        }

        synchronized (this) {
            return getActiveAccountSecuredConfiguration().getEnvironmentId();
        }
    }

    @Nullable
    public String getUser() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getUser");
        }

        return mAuthUser;
    }

    @Nullable
    public byte[] exportForMigration(int version) {
        if (DEBUG) {
            Log.d(LOG_TAG, "exportForMigration: version=" + version);
        }

        synchronized (this) {
            return mAccountSecuredConfiguration.serialize(version, mSerializerFactory);
        }
    }

    @Override
    protected void onErrorPacket(@NonNull BinaryErrorPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final PendingRequest request = removePendingRequest(requestId, PendingRequest.class);

        // If we have a pending request, this is a subscribe, cancel, delete account or restore-related request, and we report the error.
        if (request != null) {
            if (request.requestKind == RequestKind.DELETE_ACCOUNT_REQUEST) {
                super.onError(requestId, iq.getErrorCode(), null);
            } else if (request instanceof ConsumerPendingRequest<?>) {
                ((ConsumerPendingRequest<?>) request).consumer.onGet(iq.getErrorCode(), null);
            } else {
                for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
                    serviceObserver.onSubscribeUpdate(requestId, iq.getErrorCode());
                }
            }
            return;
        }

        switch (iq.getErrorCode()) {
            // Application id, service id, api key are not recognized: user must uninstall.
            case BAD_REQUEST:
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "create account is refused for " + mApplicationId);
                }

                for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
                    serviceObserver.onSignInError(ErrorCode.WRONG_LIBRARY_CONFIGURATION);
                }

                // Keep the web socket connection opened (otherwise we will re-connect again and again).
                return;

            // Wrong account creation: user must uninstall.
            case NOT_AUTHORIZED_OPERATION:
                for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
                    serviceObserver.onSignInError(ErrorCode.NOT_AUTHORIZED_OPERATION);
                }

                return;

            // Oops from the server, close and try again.
            case SERVER_ERROR:

                mTwinlifeImpl.disconnect();
                return;

            case ITEM_NOT_FOUND:

                break;
        }
    }

    /**
     * Generate a new password and change it on the Openfire server.
     * The new password is saved by onChangePassword() when the response is received.
     */
    @SuppressWarnings("unused")
    private void changePassword() {
        if (DEBUG) {
            Log.d(LOG_TAG, "changePassword");
        }

        if (!isServiceOn()) {

            return;
        }

        // Check that the account information we have is valid, if not proceed with the deletion.
        final String accountIdentifier;
        final String accountPassword;

        synchronized (this) {
            accountIdentifier = mTwinlifeImpl.toBareJid(mAccountSecuredConfiguration.getUsername());
            accountPassword = mAccountSecuredConfiguration.getPassword();
        }

        if (accountIdentifier == null || accountPassword == null || !isReconnectable()) {

            return;
        }

        // Generate device password (160-bits is the max because the final string password is truncated to 32 chars).
        final SecureRandom random = new SecureRandom();
        final byte[] password = new byte[20];
        random.nextBytes(password);

        String newDevicePassword = Utils.encodeBase64(password);
        final long requestId = mTwinlifeImpl.newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new ChangePasswordPendingRequest(newDevicePassword));
        }

        final ChangePasswordIQ changePasswordIQ = new ChangePasswordIQ(IQ_CHANGE_PASSWORD_SERIALIZER, requestId, accountIdentifier, accountPassword, newDevicePassword);
        sendDataPacket(changePasswordIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Device sign in using SCRAM authentication challenge.
     */
    private void deviceSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "deviceSignIn");
        }

        if (mTwinlifeImpl.getBackupService().isRestoreInProgress()) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Restore in progress, abort signin");
            }
            return;
        }

        final String username;
        synchronized (this) {
            username = getActiveAccountSecuredConfiguration().getUsername();
        }

        // Generate nonce for the authentication challenge.
        SecureRandom random = new SecureRandom();
        byte[] deviceNonce = new byte[32];
        random.nextBytes(deviceNonce);

        final long requestId = newRequestId();

        AuthChallengeIQ authChallenge = new AuthChallengeIQ(IQ_AUTH_CHALLENGE_SERIALIZER, requestId,
                mApplicationId, mServiceId, mApiKey, mAccessToken, mTwinlifeImpl.getApplicationName(),
                mTwinlifeImpl.getApplicationVersion(), Twinlife.VERSION,
                mTwinlifeImpl.toBareJid(username), deviceNonce);

        // We must not use BaseServiceImpl::sendPacket() because we are not signed-in yet!
        try {
            synchronized (mPendingRequests) {
                mPendingRequests.put(requestId, new AuthChallengePendingRequest(authChallenge));
            }
            byte[] packet = authChallenge.serialize(mSerializerFactory);
            packetTimeout(requestId, AUTH_REQUEST_TIMEOUT, true);
            if (!mConnection.sendDataPacket(packet)) {
                throw new IOException("Offline");
            }
        } catch (Exception exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "sendDataPacket failed", exception);
            }

            receivedIQ(requestId);
            removePendingRequest(requestId, AuthChallengePendingRequest.class);
            mTwinlifeImpl.disconnect();
        }
    }

    /**
     * Response received after successful auth challenge request IQ.
     *
     * @param iq the on-auth-challenge response.
     */
    private void onAuthChallenge(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAuthChallenge: iq=" + iq);
        }

        if (!(iq instanceof OnAuthChallengeIQ)) {
            return;
        }

        final long receiveTime = SystemClock.elapsedRealtime();
        receivedIQ(iq.getRequestId());

        AuthChallengePendingRequest pendingRequest = removePendingRequest(iq.getRequestId(), AuthChallengePendingRequest.class);
        if (pendingRequest == null) {
            return;
        }

        // Make sure we have the password, if not abort this authentication.
        String password;
        int incarnationCount;
        synchronized (this) {
            password = mAccountSecuredConfiguration.getPassword();
            incarnationCount = mAccountSecuredConfiguration.getIncarnationCount();
        }
        if (password == null) {

            mTwinlifeImpl.disconnect();
            return;
        }

        // Truncate the password because old devices registered with a password > 32 chars but it was truncated by the server.
        // If we continue using that full password, the authentication will fail!
        if (password.length() > MAX_PASSWORD_LENGTH) {
            password = password.substring(0, MAX_PASSWORD_LENGTH);
        }

        OnAuthChallengeIQ onAuthChallenge = (OnAuthChallengeIQ) iq;

        final String resource = mTwinlifeImpl.getResource();
        final long requestId = newRequestId();

        try {
            // Build the auth message that must be signed.
            StringBuilder authMessage = new StringBuilder();
            authMessage.append(pendingRequest.authChallengeIQ.getClientFirstMessageBare());
            authMessage.append(",");
            authMessage.append(onAuthChallenge.getServerFirstMessage());
            authMessage.append(",");
            authMessage.append(resource);

            // Compute everything according to RFC 5802 section 3. SCRAM Algorithm Overview
            byte[] saltedPassword = createSaltedPassword(onAuthChallenge.salt, password, onAuthChallenge.iteration);
            byte[] clientKey = computeHmac(saltedPassword, "Client Key");
            byte[] storedKey = MessageDigest.getInstance("SHA-1").digest(clientKey);
            byte[] clientSignature = computeHmac(storedKey, authMessage.toString());

            // Compute the server key for last step server signature verification.
            byte[] serverKey = computeHmac(saltedPassword, "Server Key");

            // Create the client proof to send.
            byte[] clientProof = clientKey.clone();
            for (int i = 0; i < clientProof.length; i++) {
                clientProof[i] ^= clientSignature[i];
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "Salt=" + Utils.bytesToHex(onAuthChallenge.salt) + " iterations=" + onAuthChallenge.iteration);
                Log.d(LOG_TAG, "ClientKey=" + Utils.bytesToHex(clientKey));
                Log.d(LOG_TAG, "StoredKey=" + Utils.bytesToHex(storedKey));
                Log.d(LOG_TAG, "AuthMessageSHA1=" + Utils.bytesToHex(MessageDigest.getInstance("SHA-1").digest(authMessage.toString().getBytes())));
                Log.d(LOG_TAG, "AuthMessage=" + authMessage);
                Log.d(LOG_TAG, "ClientSign=" + Utils.bytesToHex(clientSignature));
                Log.d(LOG_TAG, "ClientProof=" + Utils.bytesToHex(clientProof));
            }

            final long deviceTimestamp = System.currentTimeMillis();
            final long sendTime = SystemClock.elapsedRealtime();
            int deviceLatency = (int) (sendTime - receiveTime);
            int deviceState = 0;// mTwinlifeImpl.getJobService().getState();

            AuthRequestIQ authRequestIQ = new AuthRequestIQ(IQ_AUTH_REQUEST_SERIALIZER, requestId,
                    pendingRequest.authChallengeIQ.accountIdentifier, resource, pendingRequest.authChallengeIQ.nonce, clientProof, deviceState, deviceLatency, deviceTimestamp, onAuthChallenge.serverTimestamp, incarnationCount);

            synchronized (mPendingRequests) {
                mPendingRequests.put(requestId, new AuthRequestPendingRequest(pendingRequest.authChallengeIQ, onAuthChallenge, serverKey, sendTime));
            }

            // We must not use BaseServiceImpl::sendPacket() because we are not signed-in yet!
            byte[] packet = authRequestIQ.serialize(mSerializerFactory);
            packetTimeout(requestId, AUTH_REQUEST_TIMEOUT, true);
            if (!mConnection.sendDataPacket(packet)) {
                throw new IOException("offline");
            }
        } catch (GeneralSecurityException exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "onAuthChallenge", exception);
            }

            // We can do nothing if the SHA1 algorithm is not provided.  Keep the connection opened and unauthenticated.
            receivedIQ(requestId);
            removePendingRequest(requestId, AuthRequestPendingRequest.class);
        } catch (Exception exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "onAuthChallenge", exception);
            }

            receivedIQ(requestId);
            removePendingRequest(requestId, AuthRequestPendingRequest.class);

            mTwinlifeImpl.disconnect();
        }
    }

    /**
     * Response received after successful auth request IQ.
     *
     * @param iq the on-auth-request response.
     */
    private void onAuthRequest(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAuthRequest: iq=" + iq);
        }

        if (!(iq instanceof OnAuthRequestIQ)) {
            return;
        }

        final long receiveTime = SystemClock.elapsedRealtime();
        final long deviceTimestamp = System.currentTimeMillis();
        receivedIQ(iq.getRequestId());

        AuthRequestPendingRequest pendingRequest = removePendingRequest(iq.getRequestId(), AuthRequestPendingRequest.class);
        if (pendingRequest == null) {
            return;
        }

        try {
            OnAuthRequestIQ onAuthRequestIQ = (OnAuthRequestIQ) iq;
            String resource = mTwinlifeImpl.getResource();

            String authMessage = pendingRequest.authChallengeIQ.getClientFirstMessageBare() +
                        "," +
                    pendingRequest.onAuthChallengeIQ.getServerFirstMessage() +
                        "," +
                        resource;
            byte[] serverSignature = computeHmac(pendingRequest.serverKey, authMessage);
            String user = pendingRequest.authChallengeIQ.accountIdentifier + '/' + resource;

                // Compute the server signature.
                if (DEBUG) {
                    Log.d(LOG_TAG, "ServerSign=" + Utils.bytesToHex(serverSignature));
                }

            // Verify the server signature.
            if (!Arrays.equals(serverSignature, onAuthRequestIQ.serverSignature)) {

                mTwinlifeImpl.disconnect();
                return;
            }

            mAuthUser = user;
            mTwinlifeImpl.adjustServerTime(onAuthRequestIQ.serverTimestamp, deviceTimestamp,
                    onAuthRequestIQ.serverLatency, receiveTime - pendingRequest.authRequestTime);
            mTwinlifeImpl.onSignIn();

        } catch (Exception exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "onAuthRequest", exception);
            }

            mTwinlifeImpl.disconnect();
        }
    }

    /**
     * Response received after an authenticate failure.
     *
     * @param iq the on-auth-request response.
     */
    private void onAuthError(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAuthError: iq=" + iq);
        }

        if (!(iq instanceof BinaryErrorPacketIQ)) {
            return;
        }

        BinaryErrorPacketIQ errorPacketIQ = (BinaryErrorPacketIQ)iq;

        removePendingRequest(iq.getRequestId(), PendingRequest.class);
        mAuthUser = null;

        switch (errorPacketIQ.getErrorCode()) {
            // Application id, service id, api key are not recognized: user must uninstall.
            case BAD_REQUEST:
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "authenticate is refused for " + mApplicationId);
                }

                for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
                    serviceObserver.onSignInError(ErrorCode.WRONG_LIBRARY_CONFIGURATION);
                }

                // Keep the web socket connection opened (otherwise we will re-connect again and again).
                return;

            // User account has been deleted: user must uninstall.
            case ITEM_NOT_FOUND:
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "user account was deleted");
                }

                for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
                    serviceObserver.onSignInError(ErrorCode.ACCOUNT_DELETED);
                }

                // Keep the web socket connection opened (otherwise we will re-connect again and again).
                return;

            case ACCOUNT_RESTORED:
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "user account was restored on another device");
                }

                for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
                    serviceObserver.onSignInError(ErrorCode.ACCOUNT_RESTORED);
                }

                // Keep the web socket connection opened (otherwise we will re-connect again and again).
                return;

            // Oops from the server, close and try again.
            case SERVER_ERROR:
            case NOT_AUTHORIZED_OPERATION:
            case LIMIT_REACHED:
            case RESTORE_IN_PROGRESS:
            default:
                mTwinlifeImpl.disconnect();
                break;
        }
    }

    private void onCreateAccount(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateAccount: iq=" + iq);
        }

        if (!(iq instanceof OnCreateAccountIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        OnCreateAccountIQ onCreateAccountIQ = (OnCreateAccountIQ) iq;

        // Account is now created, setup and save the new authentication authority.
        synchronized (this) {
            if (mAccountSecuredConfiguration.signIn(AuthenticationAuthority.DEVICE, onCreateAccountIQ.environmentId)) {
                mAccountSecuredConfiguration.save(mTwinlifeImpl.getConfigurationService(), mSerializerFactory);
            }
        }

        mTwinlifeExecutor.execute(this::deviceSignIn);

        for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onCreateAccount(requestId));
        }
    }

    private void onChangePassword(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onChangePassword: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        ChangePasswordPendingRequest pendingRequest = removePendingRequest(requestId, ChangePasswordPendingRequest.class);
        if (pendingRequest == null) {
            return;
        }

        synchronized (this) {
            mAccountSecuredConfiguration.changePassword(pendingRequest.newDevicePassword, mTwinlifeImpl.getConfigurationService(), mSerializerFactory);
        }
    }

    private void onDeleteAccount(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteAccount: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);
        removePendingRequest(requestId, PendingRequest.class);

        finishDeleteAccount(requestId);
    }

    private void onSubscribeFeature(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSubscribeFeature: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);
        if (!(iq instanceof OnSubscribeFeatureIQ)) {
            return;
        }

        if (removePendingRequest(requestId, PendingRequest.class) == null) {
            return;
        }

        final OnSubscribeFeatureIQ onSubscribeFeatureIQ = (OnSubscribeFeatureIQ) iq;
        final ErrorCode errorCode = onSubscribeFeatureIQ.getErrorCode();
        final String features = onSubscribeFeatureIQ.featureList;

        synchronized (this) {
            final String subscribedFeatures = mAccountSecuredConfiguration.getSubscribedFeatures();
            if (!Utils.equals(subscribedFeatures, features)) {

                // When the allowed features is changed, update the list.
                mAccountSecuredConfiguration.setSubscribedFeatures(features);
                mAllowedFeatures.clear();

                if (features != null) {
                    String[] featureList = features.split(",");
                    mAllowedFeatures.addAll(Arrays.asList(featureList));
                }

                // Save so that we can restore a default subscribedFeatures list when we don't have the network.
                mAccountSecuredConfiguration.save(mTwinlifeImpl.getConfigurationService(), mSerializerFactory);
            }
        }

        for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onSubscribeUpdate(requestId, errorCode));
        }
    }

    private void onServerPingIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onServerPingIQ: iq=" + iq);
        }

        final BinaryPacketIQ pongIQ = new BinaryPacketIQ(IQ_PONG_SERIALIZER, iq);
        try {
            final byte[] packet = pongIQ.serializeCompact(mSerializerFactory);
            mConnection.sendDataPacket(packet);

        } catch (Exception exception) {
            if (DEBUG) {
                Log.d(LOG_TAG, "Exception", exception);
            }
        }
    }

    private void finishDeleteAccount(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "finishDeleteAccount: requestId=" + requestId);
        }

        try {
            // Erase keystore before running the onSignOut() callbacks because one of them may exit the application.
            ConfigurationService configurationService = mTwinlifeImpl.getConfigurationService();
            synchronized (this) {
                mAccountSecuredConfiguration.erase(configurationService);
                configurationService.eraseAllSecuredConfiguration();
            }

            mTwinlifeImpl.onSignOut();
        } catch (Exception exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "finishDeleteAccount", exception);
            }
        }

        for (AccountService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onDeleteAccount(requestId));
        }
    }

    @NonNull
    public static byte[] createSaltedPassword(@NonNull byte[] salt, @NonNull String password, int iters) throws GeneralSecurityException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSaltedPassword");
        }

        Mac mac = createSha1Hmac(Utf8.getBytes(password));
        mac.update(salt);
        mac.update(new byte[]{0, 0, 0, 1});
        byte[] result = mac.doFinal();

        byte[] previous = null;
        for (int i = 1; i < iters; i++) {
            mac.update(previous != null ? previous : result);
            previous = mac.doFinal();
            for (int x = 0; x < result.length; x++) {
                result[x] ^= previous[x];
            }
        }

        return result;
    }

    @NonNull
    public static byte[] computeHmac(@NonNull final byte[] key, @NonNull final String string) throws GeneralSecurityException {
        if (DEBUG) {
            Log.d(LOG_TAG, "computeHmac");
        }

        Mac mac = createSha1Hmac(key);
        mac.update(Utf8.getBytes(string));
        return mac.doFinal();
    }

    @NonNull
    public static Mac createSha1Hmac(@NonNull final byte[] keyBytes) throws GeneralSecurityException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSha1Hmac");
        }

        SecretKeySpec key = new SecretKeySpec(keyBytes, "HmacSHA1");
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(key);
        return mac;
    }

    /**
     * Type-safe remove() for PendingRequests.
     *
     * @param requestId The pending request's ID.
     * @param type      The expected type of the pending request. Pass PendingRequest.class if you don't care about the type.
     * @return the PendingRequest associated with the given requestId if it exists and is of the expected type, null otherwise.
     */
    private <T extends PendingRequest> T removePendingRequest(long requestId, Class<T> type) {
        PendingRequest pendingRequest;
        synchronized (mPendingRequests) {
            pendingRequest = mPendingRequests.remove(requestId);
        }

        if (!type.isInstance(pendingRequest)) {
            Log.e(LOG_TAG, "No/Invalid request for requestId=" + requestId + ": expected=" + type.getName() + ", actual=" + (pendingRequest == null ? "null" : pendingRequest.getClass().getName()));
            return null;
        }

        return type.cast(pendingRequest);
    }

    /**
     * Dedicated sender for {@link RestoreChallengeIQ} and {@link RestoreRequestIQ}. These requests
     * can be made while we're authenticated (restore start) or not (device disconnected during restore).
     * In the former case we need to use the compact encoder, in the latter case the legacy one.
     *
     * @param iq the {@link RestoreChallengeIQ} or {@link RestoreRequestIQ} to send to the server.
     *
     * @return the result of the send operation.
     */
    private ErrorCode sendRestoreAuthPacket(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendRestoreAuthPacket: iq=" + iq);
        }

        packetTimeout(iq.getRequestId(), AUTH_REQUEST_TIMEOUT, true);
        byte[] packet;
        try {
            if (!isSignIn()) {
                packet = iq.serialize(mSerializerFactory);
            } else {
                packet = iq.serializeCompact(mSerializerFactory);
            }

        } catch (SerializerException e) {
            return ErrorCode.LIBRARY_ERROR;
        }

        if (!mConnection.sendDataPacket(packet)) {
            return ErrorCode.TWINLIFE_OFFLINE;
        }

        return ErrorCode.SUCCESS;
    }
}
