/*
 *  Copyright (c) 2012-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Tengfei Wang (Tengfei.Wang@twinlife-systems.com)
 *   Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *   Xiaobo Xie (Xiaobo.Xie@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Olivier Dupont (Oliver.Dupont@twin.life)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.SystemClock;
import android.util.Log;

import org.twinlife.twinlife.Configuration;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.AccountService;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utf8;
import org.twinlife.twinlife.util.Utils;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class AccountServiceImpl extends BaseServiceImpl<AccountService.ServiceObserver> implements AccountService {
    private static final String LOG_TAG = "AccountServiceImpl";
    private static final boolean DEBUG = false;

    // The Openfire server truncates passwords to 32 chars when an account is created.
    private static final int MAX_PASSWORD_LENGTH = 32;
    private static final int AUTH_REQUEST_TIMEOUT = 16; // 16s max to wait for an auth challenge/request.

    private static final UUID AUTH_CHALLENGE_SCHEMA_ID = UUID.fromString("91780AB7-016A-463B-9901-434E52C200AE");
    private static final UUID AUTH_REQUEST_SCHEMA_ID = UUID.fromString("BF0A6327-FD04-4DFF-998E-72253CFD91E5");
    private static final UUID CREATE_ACCOUNT_SCHEMA_ID = UUID.fromString("84449ECB-F09F-4C12-A936-038948C2D980");
    private static final UUID DELETE_ACCOUNT_SCHEMA_ID = UUID.fromString("60e72a89-c1ef-49fa-86a8-0793e5e662e4");
    private static final UUID CHANGE_PASSWORD_SCHEMA_ID = UUID.fromString("f7295462-019e-4bd5-b830-20f98f8a9735");
    private static final UUID SUBSCRIBE_FEATURE_SCHEMA_ID = UUID.fromString("eb420020-e55a-44b0-9e9e-9922ec055407");
    private static final UUID CANCEL_FEATURE_SCHEMA_ID = UUID.fromString("0B20EF35-A5D9-45F2-9B97-C6B3D15983FA");
    private static final UUID PONG_SCHEMA_ID = UUID.fromString("fc0e491c-d91b-43c6-a25c-46d566c788b7");

    private static final UUID ON_AUTH_CHALLENGE_SCHEMA_ID = UUID.fromString("A5F47729-2FEE-4B38-AC91-3A67F3F9E1B6");
    private static final UUID ON_AUTH_REQUEST_SCHEMA_ID = UUID.fromString("9CEE4256-D2B7-4DE3-A724-1F61BB1454C8");
    private static final UUID ON_AUTH_ERROR_SCHEMA_ID = UUID.fromString("ed230b09-b9ff-4d9a-83c9-ddcc3ad686c6");
    private static final UUID ON_CREATE_ACCOUNT_SCHEMA_ID = UUID.fromString("3D8A1111-61F8-4B27-8229-43DE24A9709B");
    private static final UUID ON_DELETE_ACCOUNT_SCHEMA_ID = UUID.fromString("48e15279-8070-4c49-a71c-ce876cca579e");
    private static final UUID ON_CHANGE_PASSWORD_SCHEMA_ID = UUID.fromString("64bcd660-e13d-45c3-b953-d75a9a5bac25");
    private static final UUID ON_SUBSCRIBE_FEATURE_SCHEMA_ID = UUID.fromString("50FEC907-1D63-4617-A099-D495971930EF");
    private static final UUID ON_CANCEL_FEATURE_SCHEMA_ID = UUID.fromString("34F465EA-A459-423A-A270-2612DC72DAB4");
    private static final UUID ON_SERVER_PING_SCHEMA_ID = UUID.fromString("fb21d934-f3b4-4432-a82f-0d5a1f17e685");

    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_AUTH_CHALLENGE_SERIALIZER = AuthChallengeIQ.createSerializer(AUTH_CHALLENGE_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_AUTH_REQUEST_SERIALIZER = AuthRequestIQ.createSerializer(AUTH_REQUEST_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_CREATE_ACCOUNT_SERIALIZER = CreateAccountIQ.createSerializer(CREATE_ACCOUNT_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_DELETE_ACCOUNT_SERIALIZER = DeleteAccountIQ.createSerializer(DELETE_ACCOUNT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_CHANGE_PASSWORD_SERIALIZER = ChangePasswordIQ.createSerializer(CHANGE_PASSWORD_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_SUBSCRIBE_FEATURE_SERIALIZER = SubscribeFeatureIQ.createSerializer(SUBSCRIBE_FEATURE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_CANCEL_FEATURE_SERIALIZER = CancelFeatureIQ.createSerializer(CANCEL_FEATURE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_PONG_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(PONG_SCHEMA_ID, 1);

    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_AUTH_CHALLENGE_SERIALIZER = OnAuthChallengeIQ.createSerializer(ON_AUTH_CHALLENGE_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_AUTH_REQUEST_SERIALIZER = OnAuthRequestIQ.createSerializer(ON_AUTH_REQUEST_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_AUTH_ERROR_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_AUTH_ERROR_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_CREATE_ACCOUNT_SERIALIZER = OnCreateAccountIQ.createSerializer(ON_CREATE_ACCOUNT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_CHANGE_PASSWORD_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_CHANGE_PASSWORD_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_DELETE_ACCOUNT_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_DELETE_ACCOUNT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_SUBSCRIBE_FEATURE_SERIALIZER = OnSubscribeFeatureIQ.createSerializer(ON_SUBSCRIBE_FEATURE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_CANCEL_FEATURE_SERIALIZER = OnSubscribeFeatureIQ.createSerializer(ON_CANCEL_FEATURE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_SERVER_PING_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_SERVER_PING_SCHEMA_ID, 1);

    enum RequestKind {
        SUBSCRIBE_REQUEST,
        CANCEL_REQUEST,
        DELETE_ACCOUNT_REQUEST,
        CHANGE_PASSWORD_REQUEST
    }

    private AccountSecuredConfiguration mAccountSecuredConfiguration;

    private final Set<String> mAllowedFeatures;
    private final UUID mApplicationId;
    private final UUID mServiceId;
    private final String mApiKey;
    private final String mAccessToken;
    private final HashMap<Long, RequestKind> mPendingRequests;
    @Nullable
    private AuthChallengeIQ mAuthChallenge;
    @Nullable
    private OnAuthChallengeIQ mOnAuthChallenge;
    @Nullable
    private byte[] mServerKey;
    private boolean mCreateAccountAllowed;
    @Nullable
    private volatile String mAuthUser;
    @Nullable
    private String mNewDevicePassword;
    private long mAuthRequestTime;

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

    @Override
    public void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        // Erase sensitive information in case an authentication has not finished.
        mOnAuthChallenge = null;
        mAuthChallenge = null;
        mServerKey = null;
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

        final String subscribedFeatures = mAccountSecuredConfiguration.getSubscribedFeatures();
        final UUID environmentId = mAccountSecuredConfiguration.getEnvironmentId();
        if (Utils.equals(subscribedFeatures, configuration.features) && Utils.equals(environmentId, configuration.environmentId)) {

            return;
        }

        // When the allowed features or the environment is defined, update the list.
        synchronized (this) {
            if (configuration.environmentId != null) {
                mAccountSecuredConfiguration.setEnvironmentId(configuration.environmentId);
            }
            mAccountSecuredConfiguration.setSubscribedFeatures(configuration.features);
            mAllowedFeatures.clear();

            if (configuration.features != null) {
                String[] featureList = configuration.features.split(",");
                mAllowedFeatures.addAll(Arrays.asList(featureList));
            }

            // Save so that we can restore a default subscribedFeatures list when we don't have the network.
            mAccountSecuredConfiguration.save(mTwinlifeImpl.getConfigurationService(), mSerializerFactory);
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

            return mAccountSecuredConfiguration.getAuthenticationAuthority();
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

            return mAccountSecuredConfiguration.isReconnectable();
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
    public void createAccount(long requestId, @NonNull String etoken) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createAccount requestId=" + requestId + " etoken=" + etoken);
        }

        if (!isServiceOn()) {

            return;
        }

        // createAccount is allowed if we are not registered yet.
        final String username;
        final String password;
        synchronized (this) {

            username = mAccountSecuredConfiguration.getUsername();
            password = mAccountSecuredConfiguration.getPassword();

            if (mAccountSecuredConfiguration.getAuthenticationAuthority() != AuthenticationAuthority.UNREGISTERED
                    || username == null || password == null) {

                onError(requestId, ErrorCode.NOT_AUTHORIZED_OPERATION, null);
                return;
            }
        }

        CreateAccountIQ createAccountIQ = new CreateAccountIQ(IQ_CREATE_ACCOUNT_SERIALIZER, requestId,
                mApplicationId, mServiceId, mApiKey, mAccessToken, mTwinlifeImpl.getApplicationName(),
                mTwinlifeImpl.getApplicationVersion(), Twinlife.VERSION,
                mTwinlifeImpl.toBareJid(username), password, etoken);

        // We must not use BaseServiceImpl::sendPacket() because we are not signed-in yet!
        try {
            byte[] packet = createAccountIQ.serialize(mSerializerFactory);

            packetTimeout(requestId, DEFAULT_REQUEST_TIMEOUT, true);
            mConnection.sendDataPacket(packet);

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

        synchronized (this) {
            mAccountSecuredConfiguration.signOut();
            mAccountSecuredConfiguration.save(mTwinlifeImpl.getConfigurationService(), mSerializerFactory);
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

        // Check that the account information we have is valid, if not proceed with the deletion.
        final String accountIdentifier = mTwinlifeImpl.toBareJid(mAccountSecuredConfiguration.getUsername());
        final String accountPassword = mAccountSecuredConfiguration.getPassword();
        if (accountIdentifier == null || accountPassword == null || !isReconnectable()) {

            finishDeleteAccount(requestId);
            return;
        }

        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, RequestKind.DELETE_ACCOUNT_REQUEST);
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
            mPendingRequests.put(requestId, RequestKind.SUBSCRIBE_REQUEST);
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
            mPendingRequests.put(requestId, RequestKind.CANCEL_REQUEST);
        }

        CancelFeatureIQ cancelFeatureIQ = new CancelFeatureIQ(IQ_CANCEL_FEATURE_SERIALIZER, requestId, merchantId,
                purchaseToken, purchaseOrderId);
        sendDataPacket(cancelFeatureIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Nullable
    public UUID getEnvironmentId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getEnvironmentId");
        }

        return mAccountSecuredConfiguration.getEnvironmentId();
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

        return mAccountSecuredConfiguration.serialize(version, mSerializerFactory);
    }

    @Override
    protected void onErrorPacket(@NonNull BinaryErrorPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final RequestKind request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }

        // If we have a pending request, this is a subscribe, cancel or delete account and we report the error.
        if (request != null) {
            if (request == RequestKind.DELETE_ACCOUNT_REQUEST) {
                super.onError(requestId, iq.getErrorCode(), null);
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
     *
     * The new password is saved by onChangePassword() when the response is received.
     */
    private void changePassword() {
        if (DEBUG) {
            Log.d(LOG_TAG, "changePassword");
        }

        if (!isServiceOn()) {

            return;
        }

        // Check that the account information we have is valid, if not proceed with the deletion.
        final String accountIdentifier = mTwinlifeImpl.toBareJid(mAccountSecuredConfiguration.getUsername());
        final String accountPassword = mAccountSecuredConfiguration.getPassword();
        if (accountIdentifier == null || accountPassword == null || !isReconnectable()) {

            return;
        }

        // Generate device password (160-bits is the max because the final string password is truncated to 32 chars).
        final SecureRandom random = new SecureRandom();
        final byte[] password = new byte[20];
        random.nextBytes(password);

        mNewDevicePassword = Utils.encodeBase64(password);
        final long requestId = mTwinlifeImpl.newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, RequestKind.CHANGE_PASSWORD_REQUEST);
        }

        final ChangePasswordIQ changePasswordIQ = new ChangePasswordIQ(IQ_CHANGE_PASSWORD_SERIALIZER, requestId, accountIdentifier, accountPassword, mNewDevicePassword);
        sendDataPacket(changePasswordIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Device sign in using SCRAM authentication challenge.
     */
    private void deviceSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "deviceSignIn");
        }

        // Log.e(LOG_TAG, "device sign in DISABLED 3WAY DATABASE MIGRATION!");

        final String username = mAccountSecuredConfiguration.getUsername();

        // Generate nonce for the authentication challenge.
        SecureRandom random = new SecureRandom();
        byte[] deviceNonce = new byte[32];
        random.nextBytes(deviceNonce);

        final long requestId = newRequestId();

        mAuthChallenge = new AuthChallengeIQ(IQ_AUTH_CHALLENGE_SERIALIZER, requestId,
                mApplicationId, mServiceId, mApiKey, mAccessToken, mTwinlifeImpl.getApplicationName(),
                mTwinlifeImpl.getApplicationVersion(), Twinlife.VERSION,
                mTwinlifeImpl.toBareJid(username), deviceNonce);

        // We must not use BaseServiceImpl::sendPacket() because we are not signed-in yet!
        try {
            byte[] packet = mAuthChallenge.serialize(mSerializerFactory);

            packetTimeout(requestId, AUTH_REQUEST_TIMEOUT, true);
            mConnection.sendDataPacket(packet);

        } catch (Exception exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "sendDataPacket failed", exception);
            }

            receivedIQ(requestId);
            mAuthChallenge = null;
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

        // Verify that this is our challenge request.
        if (mAuthChallenge == null || iq.getRequestId() != mAuthChallenge.getRequestId()) {

            mAuthChallenge = null;
            mOnAuthChallenge = null;
            mServerKey = null;
            mTwinlifeImpl.disconnect();
            return;
        }

        // Make sure we have the password, if not abort this authentication.
        String password = mAccountSecuredConfiguration.getPassword();
        if (password == null) {

            mAuthChallenge = null;
            mTwinlifeImpl.disconnect();
            return;
        }

        // Truncate the password because old devices registered with a password > 32 chars but it was truncated by the server.
        // If we continue using that full password, the authentication will fail!
        if (password.length() > MAX_PASSWORD_LENGTH) {
            password = password.substring(0, MAX_PASSWORD_LENGTH);
        }

        mOnAuthChallenge = (OnAuthChallengeIQ) iq;

        final String resource = mTwinlifeImpl.getResource();
        final long requestId = newRequestId();

        try {
            // Build the auth message that must be signed.
            StringBuilder authMessage = new StringBuilder();
            authMessage.append(mAuthChallenge.getClientFirstMessageBare());
            authMessage.append(",");
            authMessage.append(mOnAuthChallenge.getServerFirstMessage());
            authMessage.append(",");
            authMessage.append(resource);

            // Compute everything according to RFC 5802 section 3. SCRAM Algorithm Overview
            byte[] saltedPassword = createSaltedPassword(mOnAuthChallenge.salt, password, mOnAuthChallenge.iteration);
            byte[] clientKey = computeHmac(saltedPassword, "Client Key");
            byte[] storedKey = MessageDigest.getInstance("SHA-1").digest(clientKey);
            byte[] clientSignature = computeHmac(storedKey, authMessage.toString());

            // Compute the server key for last step server signature verification.
            mServerKey = computeHmac(saltedPassword, "Server Key");

            // Create the client proof to send.
            byte[] clientProof = clientKey.clone();
            for (int i = 0; i < clientProof.length; i++) {
                clientProof[i] ^= clientSignature[i];
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "Salt=" + Utils.bytesToHex(mOnAuthChallenge.salt) + " iterations=" + mOnAuthChallenge.iteration);
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
            mAuthRequestTime = sendTime;
            AuthRequestIQ authRequestIQ = new AuthRequestIQ(IQ_AUTH_REQUEST_SERIALIZER, requestId,
                    mAuthChallenge.accountIdentifier, resource, mAuthChallenge.nonce, clientProof, deviceState, deviceLatency, deviceTimestamp, mOnAuthChallenge.serverTimestamp);

            // We must not use BaseServiceImpl::sendPacket() because we are not signed-in yet!
            byte[] packet = authRequestIQ.serialize(mSerializerFactory);
            packetTimeout(requestId, AUTH_REQUEST_TIMEOUT, true);
            mConnection.sendDataPacket(packet);

        } catch (GeneralSecurityException exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "onAuthChallenge", exception);
            }

            // We can do nothing if the SHA1 algorithm is not provided.  Keep the connection opened and unauthenticated.
            receivedIQ(requestId);

        } catch (Exception exception) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "onAuthChallenge", exception);
            }

            receivedIQ(requestId);

            mAuthChallenge = null;
            mOnAuthChallenge = null;
            mServerKey = null;
            mAuthUser = null;
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

        try {
            OnAuthRequestIQ onAuthRequestIQ = (OnAuthRequestIQ) iq;
            String resource = mTwinlifeImpl.getResource();
            String user = null;

            byte[] serverSignature = null;
            if (mAuthChallenge != null && mOnAuthChallenge != null && mServerKey != null) {

                String authMessage = mAuthChallenge.getClientFirstMessageBare() +
                        "," +
                        mOnAuthChallenge.getServerFirstMessage() +
                        "," +
                        resource;
                serverSignature = computeHmac(mServerKey, authMessage);
                user = mAuthChallenge.accountIdentifier + '/' + resource;

                // Compute the server signature.
                if (DEBUG) {
                    Log.d(LOG_TAG, "ServerSign=" + Utils.bytesToHex(serverSignature));
                }
            }

            mAuthChallenge = null;
            mOnAuthChallenge = null;
            mServerKey = null;

            // Verify the server signature.
            if (!Arrays.equals(serverSignature, onAuthRequestIQ.serverSignature) && user != null) {

                mAuthUser = null;
                mTwinlifeImpl.disconnect();
                return;
            }

            mAuthUser = user;
            mTwinlifeImpl.adjustServerTime(onAuthRequestIQ.serverTimestamp, deviceTimestamp,
                    onAuthRequestIQ.serverLatency, receiveTime - mAuthRequestTime);
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
        mAuthChallenge = null;
        mOnAuthChallenge = null;
        mServerKey = null;
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

            // Oops from the server, close and try again.
            case SERVER_ERROR:
            case NOT_AUTHORIZED_OPERATION:
            case LIMIT_REACHED:
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

        if (mNewDevicePassword != null) {
            synchronized (this) {
                mAccountSecuredConfiguration.changePassword(mNewDevicePassword, mTwinlifeImpl.getConfigurationService(), mSerializerFactory);
            }
            mNewDevicePassword = null;
        }
    }

    private void onDeleteAccount(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteAccount: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

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

        synchronized (mPendingRequests) {
            if (mPendingRequests.remove(requestId) == null) {
                return;
            }
        }

        final OnSubscribeFeatureIQ onSubscribeFeatureIQ = (OnSubscribeFeatureIQ) iq;
        final ErrorCode errorCode = onSubscribeFeatureIQ.getErrorCode();
        final String features = onSubscribeFeatureIQ.featureList;

        final String subscribedFeatures = mAccountSecuredConfiguration.getSubscribedFeatures();
        if (!Utils.equals(subscribedFeatures, features)) {

            // When the allowed features is changed, update the list.
            synchronized (this) {
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
}
