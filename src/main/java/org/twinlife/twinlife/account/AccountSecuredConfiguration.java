/*
 *  Copyright (c) 2017-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Olivier Dupont (Oliver.Dupont@twin.life)
 */

package org.twinlife.twinlife.account;

import androidx.annotation.NonNull;
import android.util.Log;

import androidx.annotation.Nullable;
import org.twinlife.twinlife.AccountService.AccountServiceConfiguration;
import org.twinlife.twinlife.AccountService.AuthenticationAuthority;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.ConfigurationService.SecuredConfiguration;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * <pre>
 *
 * Schema version 4: same as version 3 (required for iOS compatibility)
 *  Date: 2024/07/09
 *
 * Schema version 3
 *  Date: 2021/12/01
 *
 * {
 *  "type":"enum",
 *  "name":"AccountServiceAuthenticationAuthority",
 *  "namespace":"org.twinlife.schemas",
 *  "symbols" : ["Device", "Twinlife", "Unregistered", "Disabled"]
 * }
 * {
 *  "type":"record",
 *  "name":"AccountServiceSecuredConfiguration",
 *  "namespace":"org.twinlife.schemas.services",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"authenticationAuthority", "type":"org.twinlife.schemas.AccountServiceAuthenticationAuthority"}
 *   {"name":"isSignOut", "type":"boolean"}
 *   {"name":"deviceUsername", [null, "type":"string"]}
 *   {"name":"devicePassword", [null, "type":"string"]}
 *   {"name":"subscribedFeatures", [null, "type":"string"]}
 *   {"name":"environmentId", [null, "type":"uuid"]}
 * }
 *
 * Schema version 2
 *  Date: 2019/11/07
 *
 * {
 *  "type":"enum",
 *  "name":"AccountServiceAuthenticationAuthority",
 *  "namespace":"org.twinlife.schemas",
 *  "symbols" : ["Device", "Twinlife", "Unregistered", "Disabled"]
 * }
 *
 *
 * {
 *  "type":"record",
 *  "name":"AccountServiceSecuredConfiguration",
 *  "namespace":"org.twinlife.schemas.services",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"authenticationAuthority", "type":"org.twinlife.schemas.AccountServiceAuthenticationAuthority"}
 *   {"name":"isSignOut", "type":"boolean"}
 *   {"name":"deviceUsername", [null, "type":"string"]}
 *   {"name":"devicePassword", [null, "type":"string"]}
 *   {"name":"twinlifeUsername", [null, "type":"string"]}
 *   {"name":"twinlifePassword", [null, "type":"string"]}
 *   {"name":"twinlifeRememberPassword", "type":"boolean"}
 *   {"name":"subscribedFeatures", [null, "type":"string"]}
 * }
 *
 * Schema version 1
 *  Date: 2017/06/19
 *
 * {
 *  "type":"enum",
 *  "name":"AccountServiceAuthenticationAuthority",
 *  "namespace":"org.twinlife.schemas",
 *  "symbols" : ["Device", "Twinlife", "Facebook"]
 * }
 *
 * {
 *  "type":"record",
 *  "name":"AccountServiceSecuredConfiguration",
 *  "namespace":"org.twinlife.schemas.services",
 *  "fields":
 *  [
 *   {"name":"schemaId", "type":"uuid"},
 *   {"name":"schemaVersion", "type":"int"}
 *   {"name":"authenticationAuthority", "type":"org.twinlife.schemas.AccountServiceAuthenticationAuthority"}
 *   {"name":"isSignOut", "type":"boolean"}
 *   {"name":"deviceUsername", [null, "type":"string"]}
 *   {"name":"devicePassword", [null, "type":"string"]}
 *   {"name":"twinlifeUsername", [null, "type":"string"]}
 *   {"name":"twinlifePassword", [null, "type":"string"]}
 *   {"name":"twinlifeRememberPassword", "type":"boolean"}
 * }
 *
 * </pre>
 */

class AccountSecuredConfiguration {
    private static final String LOG_TAG = "SecuredConfiguration";
    private static final boolean DEBUG = false;

    private static final String ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY = "AccountServiceSecuredConfiguration";

    private static final String DEVICE_USERNAME_PREFIX = "device/";

    private static final String ACCOUNT_SERVICE_PREFERENCES = "AccountService";
    private static final String ACCOUNT_SERVICE_PREFERENCES_AUTHENTICATION_AUTHORITY = "AuthenticationAuthority";
    private static final String ACCOUNT_SERVICE_PREFERENCES_SIGN_OUT = "SignOut";

    // DEVICE
    private static final String ACCOUNT_SERVICE_PREFERENCES_DEVICE_USERNAME = "DeviceUsername";
    private static final String ACCOUNT_SERVICE_PREFERENCES_DEVICE_PASSWORD = "DevicePassword";

    private static final int SERIALIZER_BUFFER_DEFAULT_SIZE = 1024;

    private static final UUID SCHEMA_ID = UUID.fromString("17a04202-d50a-4150-a490-de671e639dc4");
    private static final int SCHEMA_VERSION_4 = 4;
    private static final int SCHEMA_VERSION_3 = 3;
    private static final int SCHEMA_VERSION_2 = 2;
    private static final int SCHEMA_VERSION_1 = 1;
    private static final AccountSecuredConfigurationSerializer_34 SERIALIZER_4 = new AccountSecuredConfigurationSerializer_34(SCHEMA_VERSION_4);
    private static final AccountSecuredConfigurationSerializer_34 SERIALIZER_3 = new AccountSecuredConfigurationSerializer_34(SCHEMA_VERSION_3);
    private static final AccountSecuredConfigurationSerializer_2 SERIALIZER_2 = new AccountSecuredConfigurationSerializer_2();
    private static final AccountSecuredConfigurationSerializer_1 SERIALIZER_1 = new AccountSecuredConfigurationSerializer_1();

    @NonNull
    private AuthenticationAuthority authenticationAuthority;
    private boolean isSignOut;
    @Nullable
    private String deviceUsername;
    @Nullable
    private String devicePassword;
    @Nullable
    private String subscribedFeatures;
    @Nullable
    private UUID environmentId;

    // Used for version 3 and version 4 (iOS support).
    static class AccountSecuredConfigurationSerializer_34 extends Serializer {

        AccountSecuredConfigurationSerializer_34(int version) {

            super(SCHEMA_ID, version, AccountSecuredConfiguration.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            AccountSecuredConfiguration accountSecuredConfiguration = (AccountSecuredConfiguration) object;
            switch (accountSecuredConfiguration.authenticationAuthority) {
                case DEVICE:
                    encoder.writeEnum(0);
                    break;

                case TWINLIFE:
                    encoder.writeEnum(1);
                    break;

                case UNREGISTERED:
                    encoder.writeEnum(2);
                    break;

                case DISABLED:
                    encoder.writeEnum(3);
                    break;
            }
            encoder.writeBoolean(accountSecuredConfiguration.isSignOut);
            encoder.writeOptionalString(accountSecuredConfiguration.deviceUsername);
            encoder.writeOptionalString(accountSecuredConfiguration.devicePassword);
            encoder.writeOptionalString(accountSecuredConfiguration.subscribedFeatures);
            encoder.writeOptionalUUID(accountSecuredConfiguration.environmentId);
        }

        @NonNull
        @Override
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            int value = decoder.readEnum();
            AuthenticationAuthority authenticationAuthority;
            switch (value) {
                case 0:
                default:
                    authenticationAuthority = AuthenticationAuthority.DEVICE;
                    break;

                case 1:
                    authenticationAuthority = AuthenticationAuthority.TWINLIFE;
                    break;

                case 2:
                    authenticationAuthority = AuthenticationAuthority.UNREGISTERED;
                    break;

                case 3:
                    authenticationAuthority = AuthenticationAuthority.DISABLED;
                    break;

            }
            boolean isSignOut = decoder.readBoolean();
            String deviceUsername = decoder.readOptionalString();
            String devicePassword = decoder.readOptionalString();
            String subscribedFeatures = decoder.readOptionalString();
            UUID environmentId = decoder.readOptionalUUID();
            return new AccountSecuredConfiguration(authenticationAuthority, isSignOut, deviceUsername, devicePassword, subscribedFeatures, environmentId);
        }
    }

    static class AccountSecuredConfigurationSerializer_2 extends Serializer {

        AccountSecuredConfigurationSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, AccountSecuredConfiguration.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            AccountSecuredConfiguration accountSecuredConfiguration = (AccountSecuredConfiguration) object;
            switch (accountSecuredConfiguration.authenticationAuthority) {
                case DEVICE:
                    encoder.writeEnum(0);
                    break;

                case TWINLIFE:
                    encoder.writeEnum(1);
                    break;

                case UNREGISTERED:
                    encoder.writeEnum(2);
                    break;

                case DISABLED:
                    encoder.writeEnum(3);
                    break;
            }
            encoder.writeBoolean(accountSecuredConfiguration.isSignOut);
            encoder.writeOptionalString(accountSecuredConfiguration.deviceUsername);
            encoder.writeOptionalString(accountSecuredConfiguration.devicePassword);
            // There is no twinlifeUsername, twinlifePassword
            encoder.writeEnum(0);
            encoder.writeEnum(0);
            encoder.writeBoolean(false);
            encoder.writeOptionalString(accountSecuredConfiguration.subscribedFeatures);
        }

        @NonNull
        @Override
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            int value = decoder.readEnum();
            AuthenticationAuthority authenticationAuthority;
            switch (value) {
                case 0:
                default:
                    authenticationAuthority = AuthenticationAuthority.DEVICE;
                    break;

                case 1:
                    authenticationAuthority = AuthenticationAuthority.TWINLIFE;
                    break;

                case 2:
                    authenticationAuthority = AuthenticationAuthority.UNREGISTERED;
                    break;

                case 3:
                    authenticationAuthority = AuthenticationAuthority.DISABLED;
                    break;

            }
            boolean isSignOut = decoder.readBoolean();
            String deviceUsername = decoder.readOptionalString();
            String devicePassword = decoder.readOptionalString();
            // Skip the twinlifeUsername
            decoder.readOptionalString();
            // Skip the twinlifePassword
            decoder.readOptionalString();
            // Skip the rememberPassword
            decoder.readBoolean();
            String subscribedFeatures = decoder.readOptionalString();
            return new AccountSecuredConfiguration(authenticationAuthority, isSignOut, deviceUsername, devicePassword, subscribedFeatures, null);
        }
    }

    private static class AccountSecuredConfigurationSerializer_1 extends Serializer {

        AccountSecuredConfigurationSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, AccountSecuredConfiguration.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            throw new SerializerException();
        }

        @NonNull
        @Override
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            int value = decoder.readEnum();
            AuthenticationAuthority authenticationAuthority;
            switch (value) {
                case 0:
                default:
                    authenticationAuthority = AuthenticationAuthority.DEVICE;
                    break;

                case 1:
                    authenticationAuthority = AuthenticationAuthority.TWINLIFE;
                    break;

            }
            boolean isSignOut = decoder.readBoolean();
            String deviceUsername = decoder.readOptionalString();
            String devicePassword = decoder.readOptionalString();
            // Skip the twinlifeUsername
            decoder.readOptionalString();
            // Skip the twinlifePassword
            decoder.readOptionalString();
            // Skip the rememberPassword
            decoder.readBoolean();

            return new AccountSecuredConfiguration(/*serializerFactory, */authenticationAuthority, isSignOut, deviceUsername, devicePassword,
                    null, null);
        }
    }

    // private final SerializerFactory mSerializerFactory;

    private AccountSecuredConfiguration(@NonNull AuthenticationAuthority authenticationAuthority, boolean isSignOut,
                                        @Nullable String deviceUsername, @Nullable String devicePassword, @Nullable String subscribedFeatures,
                                        @Nullable UUID environmentId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "SecuredConfiguration: authenticationAuthority=" + authenticationAuthority +
                    " isSignOut=" + isSignOut + " deviceUsername=" + deviceUsername + " devicePassword=" + devicePassword +
                    " environmentId=" + environmentId);
        }

        this.authenticationAuthority = authenticationAuthority;
        this.isSignOut = isSignOut;
        this.deviceUsername = deviceUsername;
        this.devicePassword = devicePassword;
        this.subscribedFeatures = subscribedFeatures;
        this.environmentId = environmentId;
    }

    @NonNull
    AuthenticationAuthority getAuthenticationAuthority() {

        return authenticationAuthority;
    }

    @Nullable
    String getUsername() {

        switch (authenticationAuthority) {
            case DEVICE:
            case UNREGISTERED:
                return deviceUsername;

            default:
                return null;
        }
    }

    @Nullable
    String getPassword() {

        switch (authenticationAuthority) {
            case DEVICE:
            case UNREGISTERED:
                return devicePassword;

            default:
                return null;
        }
    }

    @Nullable
    String getSubscribedFeatures() {

        return subscribedFeatures;
    }

    void setSubscribedFeatures(@Nullable String features) {

        subscribedFeatures = features;
    }

    @Nullable
    UUID getEnvironmentId() {

        return environmentId;
    }

    void setEnvironmentId(@NonNull UUID environmentId) {

        this.environmentId = environmentId;
    }

    public boolean isReconnectable() {

        return (authenticationAuthority == AuthenticationAuthority.DEVICE);
    }

    public boolean signIn(@NonNull AuthenticationAuthority authenticationAuthority, @Nullable UUID environmentId) {

        if (!isSignOut && this.authenticationAuthority.equals(authenticationAuthority) && Utils.equals(this.environmentId, environmentId)) {
            return false;
        }

        this.authenticationAuthority = authenticationAuthority;
        this.environmentId = environmentId;
        isSignOut = false;
        return true;
    }

    public void signOut() {

        authenticationAuthority = AuthenticationAuthority.DISABLED;
        isSignOut = true;
    }

    @NonNull
    static AccountSecuredConfiguration init(@NonNull ConfigurationService configurationService, @NonNull SerializerFactory serializerFactory,
                                            @NonNull AccountServiceConfiguration accountServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "init: configurationService=" + configurationService + " serializerFactory=" + serializerFactory + " accountServiceConfiguration=" + accountServiceConfiguration);
        }

        SecuredConfiguration config = configurationService.getSecuredConfiguration(ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);
        byte[] content = config.getData();
        if (content != null) {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(content);
            BinaryDecoder binaryDecoder = new BinaryDecoder(inputStream);
            try {
                UUID schemaId = binaryDecoder.readUUID();
                int schemaVersion = binaryDecoder.readInt();

                if (AccountSecuredConfiguration.SCHEMA_ID.equals(schemaId)) {
                    if (AccountSecuredConfiguration.SCHEMA_VERSION_4 == schemaVersion) {

                        return (AccountSecuredConfiguration) AccountSecuredConfiguration.SERIALIZER_4.deserialize(serializerFactory, binaryDecoder);
                    }
                    if (AccountSecuredConfiguration.SCHEMA_VERSION_3 == schemaVersion) {

                        return (AccountSecuredConfiguration) AccountSecuredConfiguration.SERIALIZER_3.deserialize(serializerFactory, binaryDecoder);
                    }
                    if (AccountSecuredConfiguration.SCHEMA_VERSION_2 == schemaVersion) {

                        return (AccountSecuredConfiguration) AccountSecuredConfiguration.SERIALIZER_2.deserialize(serializerFactory, binaryDecoder);
                    }
                    if (AccountSecuredConfiguration.SCHEMA_VERSION_1 == schemaVersion) {

                        return (AccountSecuredConfiguration) AccountSecuredConfiguration.SERIALIZER_1.deserialize(serializerFactory, binaryDecoder);
                    }
                }
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "init: deserialize", exception);
                }
            }
        }

        // A new account is now always unregistered.
        AccountSecuredConfiguration accountSecuredConfiguration = new AccountSecuredConfiguration(AuthenticationAuthority.UNREGISTERED, false, null, null, null, null);

        // Migration from SharedPreferences
        ConfigurationService.Configuration oldConfig = configurationService.getConfiguration(ACCOUNT_SERVICE_PREFERENCES);

        String deviceUsername = oldConfig.getString(ACCOUNT_SERVICE_PREFERENCES_DEVICE_USERNAME, null);
        String devicePassword = oldConfig.getString(ACCOUNT_SERVICE_PREFERENCES_DEVICE_PASSWORD, null);
        if (deviceUsername != null || devicePassword != null) {
            boolean sanityCheck = deviceUsername != null && devicePassword != null;
            sanityCheck = sanityCheck && deviceUsername.startsWith(DEVICE_USERNAME_PREFIX);
            sanityCheck = sanityCheck && deviceUsername.length() == 43;
            sanityCheck = sanityCheck && devicePassword.length() == 40;
            if (sanityCheck) {
                int authenticationAuthority = oldConfig.getInt(ACCOUNT_SERVICE_PREFERENCES_AUTHENTICATION_AUTHORITY, -1);
                switch (authenticationAuthority) {
                    case 0:
                        accountSecuredConfiguration.authenticationAuthority = AuthenticationAuthority.DEVICE;
                        break;

                    case 1:
                    case 2:
                        accountSecuredConfiguration.authenticationAuthority = AuthenticationAuthority.TWINLIFE;
                        break;

                    default:
                        accountSecuredConfiguration.authenticationAuthority = accountServiceConfiguration.defaultAuthenticationAuthority;
                        break;
                }
                accountSecuredConfiguration.isSignOut = oldConfig.getBoolean(ACCOUNT_SERVICE_PREFERENCES_SIGN_OUT, false);
                accountSecuredConfiguration.deviceUsername = deviceUsername;
                accountSecuredConfiguration.devicePassword = devicePassword;
            }
        }

        if (accountSecuredConfiguration.deviceUsername == null || accountSecuredConfiguration.devicePassword == null) {
            accountSecuredConfiguration.deviceUsername = DEVICE_USERNAME_PREFIX + UUID.randomUUID().toString();

            // Generate device password (160-bits is the max because the final string password is truncated to 32 chars).
            SecureRandom random = new SecureRandom();
            byte[] password = new byte[20];
            random.nextBytes(password);

            accountSecuredConfiguration.devicePassword = Utils.encodeBase64(password);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
        try {
            AccountSecuredConfiguration.SERIALIZER_4.serialize(serializerFactory, binaryEncoder, accountSecuredConfiguration);
            config.setData(outputStream.toByteArray());
            configurationService.saveSecuredConfiguration(config);
            configurationService.deleteConfiguration(oldConfig);

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "init: serialize", exception);
            }
        }

        return accountSecuredConfiguration;
    }

    public void erase(@NonNull ConfigurationService configurationService) {
        if (DEBUG) {
            Log.d(LOG_TAG, "erase");
        }

        deviceUsername = null;
        devicePassword = null;
        isSignOut = true;
        authenticationAuthority = AuthenticationAuthority.UNREGISTERED;

        SecuredConfiguration config = configurationService.getSecuredConfiguration(ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);
        // Erase everything from the keychain to make sure we don't try to use the device secure information again.
        config.setData(null);
        configurationService.saveSecuredConfiguration(config);
    }

    void save(@NonNull ConfigurationService configurationService, @NonNull SerializerFactory serializerFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "synchronize");
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
        try {
            AccountSecuredConfiguration.SERIALIZER_4.serialize(serializerFactory, binaryEncoder, this);
            SecuredConfiguration config = configurationService.getSecuredConfiguration(ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);
            config.setData(outputStream.toByteArray());
            configurationService.saveSecuredConfiguration(config);

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "init: serialize", exception);
            }
        }
    }

    void changePassword(@NonNull String devicePassword, @NonNull ConfigurationService configurationService, @NonNull SerializerFactory serializerFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "changePassword");
        }

        this.devicePassword = devicePassword;
        save(configurationService, serializerFactory);
    }

    @Nullable
    byte[] serialize(int version, @NonNull SerializerFactory serializerFactory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "serialize version=" + version);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
        try {
            if (version == 4) {
                AccountSecuredConfiguration.SERIALIZER_4.serialize(serializerFactory, binaryEncoder, this);
            } else {
                AccountSecuredConfiguration.SERIALIZER_3.serialize(serializerFactory, binaryEncoder, this);
            }
            return outputStream.toByteArray();

        } catch (Exception exception) {
            return null;
        }
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        return "SecuredConfiguration:\n" +
                " isSignOut=" + isSignOut + "\n" +
                " deviceUsername=" + deviceUsername + "\n" +
                " devicePassword=" + devicePassword + "\n" +
                " environmentId=" + environmentId + "\n" +
                " subscribedFeatures=" + subscribedFeatures + "\n";
    }
}
