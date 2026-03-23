/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup.handlers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.account.AccountServiceImpl;
import org.twinlife.twinlife.backup.BackupHandler;
import org.twinlife.twinlife.backup.VerifyResult;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.UUID;

public class AccountSecuredConfigurationHandler extends BackupHandler<ConfigurationService.SecuredConfiguration> {
    private static final String LOG_TAG = "AccountConfHandler";
    private static final boolean DEBUG = false;

    public static final UUID SCHEMA_ID = UUID.fromString("83ff7730-08fd-4242-a9d3-36697be0d963");
    private static final int SCHEMA_VERSION = 1;

    private static final String ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY = "AccountServiceSecuredConfiguration";

    @NonNull
    private final ConfigurationService mConfigurationService;

    @Nullable
    private final AccountServiceImpl mAccountService;

    /**
     * Backup / restore constructor
     */
    public AccountSecuredConfigurationHandler(@NonNull ConfigurationService configurationService) {
        super();
        this.mConfigurationService = configurationService;
        this.mAccountService = null;
    }

    /**
     * Verify constructor
     */
    public AccountSecuredConfigurationHandler(@NonNull ConfigurationService configurationService, @NonNull AccountServiceImpl accountService) {
        super();
        this.mConfigurationService = configurationService;
        this.mAccountService = accountService;
    }

    @Override
    protected void initDeserializers() {
        mRestorers.put(AccountSecuredConfigurationRestorerV1.VERSION, new AccountSecuredConfigurationRestorerV1());
    }

    @Override
    public void backup(@NonNull BinaryEncoder encoder) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "backup: encoder=" + encoder);
        }

        final ConfigurationService.SecuredConfiguration secureConfig = mConfigurationService.getSecuredConfiguration(ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);

        if (secureConfig.getData() == null) {
            throw new SerializerException("Twinlife secured configuration not found");
        }

        encoder.writeUUID(SCHEMA_ID);
        encoder.writeInt(SCHEMA_VERSION);
        encoder.writeData(secureConfig.getData());

        if (DEBUG) {
            Log.d(LOG_TAG, "Backup account secured configuration: " + secureConfig.getName());
        }
    }

    private class AccountSecuredConfigurationRestorerV1 implements Restorer<ConfigurationService.SecuredConfiguration> {
        public static final int VERSION = 1;

        @NonNull
        @Override
        public ConfigurationService.SecuredConfiguration restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException {
            if (DEBUG) {
                Log.d(LOG_TAG, "deserialize: decoder=" + decoder);
            }

            ByteBuffer buffer = decoder.readBytes(null);

            final ConfigurationService.SecuredConfiguration secureConfig = mConfigurationService.getSecuredConfiguration(ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);
            secureConfig.setData(buffer.array());

            if (DEBUG) {
                Log.d(LOG_TAG, "restored secured configuration: " + secureConfig.getName() + " data=" + Arrays.toString(secureConfig.getData()));
            }

            return secureConfig;
        }

        @NonNull
        @Override
        public VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException {
            if (DEBUG) {
                Log.d(LOG_TAG, "verify: decoder=" + decoder);
            }

            if (mAccountService == null) {
                throw new IllegalStateException("mAccountService is null");
            }

            ByteBuffer buffer = decoder.readBytes(null);

            final ConfigurationService.SecuredConfiguration secureConfig = mConfigurationService.getSecuredConfiguration(ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);
            secureConfig.setData(buffer.array());

            boolean isCurrentAccount = mAccountService.isCurrentAccount(secureConfig);

            return new VerifyResult.Present<>(secureConfig, !isCurrentAccount);
        }
    }
}
