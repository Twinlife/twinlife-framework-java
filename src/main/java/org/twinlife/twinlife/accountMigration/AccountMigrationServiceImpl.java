/*
 *  Copyright (c) 2020-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.AccountService;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.ConfigIdentifier;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.ConfigurationService.SecuredConfiguration;
import org.twinlife.twinlife.AccountMigrationService;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utf8;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of the AccountMigrationService.
 * <p>
 * The account migration is implemented by the AccountMigrationExecutor which manages the migration
 * steps.  It derives from the PeerConnectionObserver which manages the P2P connection and handles
 * the incoming and outgoing P2P connection with timeout and retry process.
 * <p>
 * The AccountMigrationExecutor is instantiated only the first time the migration is started
 * either by outgoingStartMigration() or by incomingStartMigration().  This allows to reduce the
 * memory impact of the account migration service since it is not used very often.
 * <p>
 * When the Twinlife framework starts, it calls finishMigration() very early during the initialization
 * process and this is intended to install the migrated files before the Account Service is started
 * and before opening the database.
 */
public class AccountMigrationServiceImpl extends BaseServiceImpl<AccountMigrationService.ServiceObserver> implements AccountMigrationService {
    private static final String LOG_TAG = "AccountMigrationSer..";
    private static final boolean DEBUG = false;

    @Nullable
    private volatile AccountMigrationExecutor mCurrentAccountMigration;
    private final DatabaseServiceImpl mDatabase;
    private File mDatabaseFile;
    @Nullable
    private UUID mActiveMigrationId;

    public AccountMigrationServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {

        super(twinlifeImpl, connection);

        setServiceConfiguration(new AccountMigrationServiceConfiguration());

        AccountMigrationExecutor.init(twinlifeImpl.getSerializerFactoryImpl());
        mDatabase = twinlifeImpl.getDatabaseService();
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof AccountMigrationServiceConfiguration)) {
            setConfigured(false);

            return;
        }
        AccountMigrationServiceConfiguration accountMigrationServiceConfiguration = new AccountMigrationServiceConfiguration();

        setServiceConfiguration(accountMigrationServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    /**
     * If a migration is finished but was not installed, do it now before starting the account service.
     *
     * @param context the context to build the database path.
     */
    public void finishMigration(@NonNull Context context) {
        if (DEBUG) {
            Log.d(LOG_TAG, "finishMigration context=" + context);
        }

        File rootDirectory = mTwinlifeImpl.getFilesDir();
        if (rootDirectory != null && hasMigrationPending(rootDirectory)) {
            // Note: Twinlife is starting and we are committing before the AccountService is started.
            // As soon as the commit is done, the Twinlife secure configuration and account
            // configuration contain the new values.
            if (commitConfiguration(context, rootDirectory, null)) {
                Log.w(LOG_TAG, "Account migrated during startup");
            } else {
                cancel(rootDirectory, mDatabaseFile.getParentFile());
                Log.w(LOG_TAG, "Account migration canceled due to commit error");
            }
        }
    }

    @Override
    protected void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        final File rootDir = mTwinlifeImpl.getFilesDir();
        mDatabaseFile = new File(mDatabase.getDatabasePath());
        mActiveMigrationId = checkActiveAccountMigrationId(rootDir, mDatabaseFile.getParentFile());
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        super.onTwinlifeOnline();

        final AccountMigrationExecutor accountMigration = mCurrentAccountMigration;
        if (accountMigration != null) {
            accountMigration.onTwinlifeOnline();
        }
    }

    @Override
    public void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        super.onDisconnect();

        final AccountMigrationExecutor accountMigration = mCurrentAccountMigration;
        if (accountMigration != null) {
            accountMigration.onDisconnect();
        }
    }

    /**
     * Check and get the active device migration id.
     *
     * @return the active device migration id or null.
     */
    @Nullable
    public UUID getActiveDeviceMigrationId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getActiveDeviceMigrationId");
        }

        return mActiveMigrationId;
    }

    /**
     * Start the device migration process by setting up and opening the P2P connection to the peer twincode outboundid.
     *
     * @param requestId the request identifier.
     * @param accountMigrationId the account migration identifier.
     * @param peerTwincodeOutboundId the peer twincode outbound id.
     */
    @Override
    public void outgoingStartMigration(long requestId, @NonNull UUID accountMigrationId,
                                       @NonNull UUID peerTwincodeOutboundId, @NonNull UUID twincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "outgoingStartMigration requestId=" + requestId + " accountMigrationId=" + accountMigrationId +
                    " peerTwincodeOutboundId=" + peerTwincodeOutboundId + " twincodeOutboundId=" + twincodeOutboundId);
        }

        if (!isServiceOn()) {
            throw new IllegalStateException("service is not configured");
        }

        final File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {
            onError(requestId, BaseService.ErrorCode.BAD_REQUEST, null);
            return;
        }

        // Force a database sync before starting the migration to flush the WAL file
        // (another one will be made before sending the database in case it was changed).
        mDatabase.syncDatabase();

        final AccountMigrationExecutor accountMigration;
        final String peerId = mTwinlifeImpl.getTwincodeOutboundService().getPeerId(peerTwincodeOutboundId, twincodeOutboundId);
        synchronized (this) {
            if (mCurrentAccountMigration != null) {
                accountMigration = null;
            } else {
                accountMigration = new AccountMigrationExecutor(mTwinlifeImpl, this,
                        mDatabase, accountMigrationId, peerId, filesDir);
                mCurrentAccountMigration = accountMigration;
                mActiveMigrationId = accountMigrationId;
            }
        }

        if (accountMigration == null) {
            onError(requestId, BaseService.ErrorCode.BAD_REQUEST, null);
            return;
        }

        accountMigration.startOutgoingConnection();
    }

    /**
     * Start the device migration process by accepting the incoming P2P connection from the peer.
     *
     * @param peerConnectionId the P2P incoming peer connection.
     * @param accountMigrationId the account migration identifier
     * @param peerTwincodeOutboundId the peer twincode outbound id.
     */
    @Override
    public void incomingStartMigration(@NonNull UUID peerConnectionId, @NonNull UUID accountMigrationId,
                                       @Nullable UUID peerTwincodeOutboundId, @NonNull UUID twincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "incomingStartMigration: peerConnectionId=" + peerConnectionId + " deviceMigrationId=" + accountMigrationId +
                    " peerTwincodeOutboundId=" + peerTwincodeOutboundId);
        }

        if (!isServiceOn()) {
            throw new IllegalStateException("service is not configured");
        }

        final PeerConnectionService peerConnectionService = mTwinlifeImpl.getPeerConnectionService();
        final Offer peerOffer = peerConnectionService.getPeerOffer(peerConnectionId);
        final File filesDir = mTwinlifeImpl.getFilesDir();
        if (peerOffer == null || !peerOffer.data || filesDir == null || peerTwincodeOutboundId == null) {

            peerConnectionService.terminatePeerConnection(peerConnectionId, TerminateReason.NOT_AUTHORIZED);

            return;
        }

        // Force a database sync before starting the migration to flush the WAL file
        // (another one will be made before sending the database in case it was changed).
        mDatabase.syncDatabase();

        final String peerId = mTwinlifeImpl.getTwincodeOutboundService().getPeerId(peerTwincodeOutboundId, twincodeOutboundId);

        AccountMigrationExecutor accountMigration;
        synchronized (this) {
            accountMigration = mCurrentAccountMigration;
            if (accountMigration == null) {
                accountMigration = new AccountMigrationExecutor(mTwinlifeImpl, this,
                        mDatabase, accountMigrationId, peerId, filesDir);
                mCurrentAccountMigration = accountMigration;
                mActiveMigrationId = accountMigrationId;

            } else if (!accountMigrationId.equals(accountMigration.getAccountMigrationId())) {
                accountMigration = null;
            }
        }

        if (accountMigration == null) {
            peerConnectionService.terminatePeerConnection(peerConnectionId, TerminateReason.BUSY);
            return;
        }

        accountMigration.startIncomingConnection(peerConnectionId);
    }

    /**
     * Query the peer device to obtain statistics about the files it provides.
     *
     * @param requestId the request identifier.
     * @param maxFileSize the maximum file size we accept.
     */
    @Override
    public void queryStats(long requestId, long maxFileSize) {
        if (DEBUG) {
            Log.d(LOG_TAG, "queryStats: requestId=" + requestId + " maxFileSize=" + maxFileSize);
        }

        if (!isServiceOn()) {
            throw new IllegalStateException("service is not configured");
        }

        final AccountMigrationExecutor accountMigration = mCurrentAccountMigration;
        if (accountMigration == null) {
            onError(requestId, BaseService.ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        if (!accountMigration.isConnected()) {
            onError(requestId, BaseService.ErrorCode.TWINLIFE_OFFLINE, null);
            return;
        }

        accountMigration.queryStats(requestId, maxFileSize);
    }

    /**
     * Start the migration by asking the peer device to send its files.
     *
     * @param requestId the request identifier.
     * @param maxFileSize the maximum file size we accept.
     */
    @Override
    public void startMigration(long requestId, long maxFileSize) {
        if (DEBUG) {
            Log.d(LOG_TAG, "startMigration requestId=" + requestId + " maxFileSize=" + maxFileSize);
        }

        if (!isServiceOn()) {
            throw new IllegalStateException("service is not configured");
        }

        final AccountMigrationExecutor accountMigration = mCurrentAccountMigration;
        if (accountMigration == null) {
            onError(requestId, BaseService.ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        if (!accountMigration.isConnected()) {
            onError(requestId, BaseService.ErrorCode.TWINLIFE_OFFLINE, null);
            return;
        }

        accountMigration.startMigration(requestId, maxFileSize);
    }

    /**
     * Terminate the migration by sending the termination IQ and closing the P2P connection.
     *
     * @param requestId the request identifier.
     * @param commit true if we commit or and false if we abort the migration.
     * @param done true if the DeviceMigration object and its twincode was deleted.
     */
    @Override
    public void terminateMigration(long requestId, boolean commit, boolean done) {
        if (DEBUG) {
            Log.d(LOG_TAG, "terminateMigration requestId=" + requestId + " commit=" + commit + " done=" + done);
        }

        if (!isServiceOn()) {
            throw new IllegalStateException("service is not configured");
        }

        final AccountMigrationExecutor accountMigration = mCurrentAccountMigration;
        if (accountMigration == null) {
            onError(requestId, BaseService.ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        // Migration is canceled: cleanup.
        if (!commit) {
            accountMigration.cancel();
        }

        if (!accountMigration.isConnected()) {
            onError(requestId, BaseService.ErrorCode.TWINLIFE_OFFLINE, null);
            return;
        }

        accountMigration.terminateMigration(requestId, commit, done);
    }

    /**
     * Shutdown the P2P connection gracefully after the terminate phase1 and phase2.
     *
     * @param requestId the request identifier.
     */
    @Override
    public void shutdownMigration(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "shutdownMigration requestId=" + requestId);
        }

        if (!isServiceOn()) {
            throw new IllegalStateException("service is not configured");
        }

        final AccountMigrationExecutor accountMigration = mCurrentAccountMigration;
        if (accountMigration == null) {
            onError(requestId, BaseService.ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        if (accountMigration.getState() != State.TERMINATE) {
            onError(requestId, BaseService.ErrorCode.BAD_REQUEST, null);
            return;
        }

        if (!accountMigration.isConnected()) {
            onError(requestId, BaseService.ErrorCode.TWINLIFE_OFFLINE, null);
            return;
        }

        // Invalidate the push variant and push notification token otherwise the server
        // can wakeup the other device after we switched.
        mTwinlifeImpl.getManagementServiceImpl().setPushNotificationToken("", "");
        accountMigration.shutdownMigration(requestId);
    }

    /**
     * Cancel a possible account migration:
     * - if there is a P2P connection close it,
     * - if there is an active (ie, opened) device migration engine stop it,
     * - if there are some migration files, remove them.
     * Last, notify a possible migration service that the migration was canceled.
     *
     * @param accountMigrationId the device migration identifier.
     * @return true if the migration was canceled and false if there is nothing to cancel.
     */
    @Override
    public boolean cancelMigration(@NonNull UUID accountMigrationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "cancelMigration accountMigrationId=" + accountMigrationId);
        }

        if (!isServiceOn()) {
            throw new IllegalStateException("service is not configured");
        }

        final AccountMigrationExecutor accountMigration;
        synchronized (this) {
            accountMigration = mCurrentAccountMigration;
            if (accountMigration != null) {
                // If this is another account migration object that is deleted/canceled, ignore it.
                if (!accountMigrationId.equals(accountMigration.getAccountMigrationId())) {
                    return false;
                }

                // If we are in terminate or terminated state, we can receive a cancelMigration() because the peer
                // has deleted the migration object+twincode but this is the normal termination process.
                State state = accountMigration.getState();
                if (state == State.TERMINATE || state == State.TERMINATED) {
                    return false;
                }

            }

            mCurrentAccountMigration = null;
            mActiveMigrationId = null;
        }

        if (accountMigration != null) {
            accountMigration.cancel();
        }

        final File rootDirectory = mTwinlifeImpl.getFilesDir();
        if (rootDirectory != null) {
            cancel(rootDirectory, mDatabaseFile);
        }
        return true;
    }

    /**
     * Update the progress state.
     *
     * @param accountMigrationId the device migration identifier.
     */
    void setProgress(@NonNull UUID accountMigrationId, @NonNull Status status) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setProgress accountMigrationId=" + accountMigrationId + " status=" + status);
        }

        for (AccountMigrationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onStatusChange(accountMigrationId, status));
        }

        if (status.getState() == State.STOPPED) {
            mCurrentAccountMigration = null;
        }
    }

    /**
     * Response received after query-stats operation.
     *
     * @param requestId the query request id.
     * @param peerInfo the query stats to summary what will be received.
     * @param localInfo the query stats to summary what will be sent.
     */
    void onQueryStats(long requestId, @NonNull QueryInfoImpl peerInfo, @Nullable QueryInfoImpl localInfo) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onQueryStats requestId=" + requestId + " peerInfo=" + peerInfo + " localInfo=" + localInfo);
        }

        for (AccountMigrationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onQueryStats(requestId, peerInfo, localInfo));
        }
    }

    /**
     * Request terminate-migration operation is received.
     *
     * @param requestId the request id.
     * @param accountMigrationId the account migration id.
     * @param commit true if the account migration is committed.
     * @param done true if the account migration is committed and done.
     */
    void onTerminateMigration(long requestId, @NonNull UUID accountMigrationId, boolean commit, boolean done) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTerminateMigrationIQ requestId=" + requestId + " accountMigrationId=" + accountMigrationId + " commit=" + commit);
        }

        // The terminate phase is handled by an upper service that must delete the migration twincode.
        for (AccountMigrationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onTerminateMigration(requestId, accountMigrationId, commit, done));
        }
    }

    /**
     * Cancel a possible account migration.
     *
     * @param rootDirectory the root directory.
     * @param databaseDirectory the database directory.
     */
    void cancel(@NonNull File rootDirectory, @Nullable File databaseDirectory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "cancel rootDirectory=" + rootDirectory + " databaseDirectory=" + databaseDirectory);
        }

        final File migrationDirectory = new File(rootDirectory, AccountMigrationExecutor.MIGRATION_DIR);
        Utils.deleteDirectory(migrationDirectory);

        // Cleanup a database that was migrated (use distinct code blocks to reduce errors).
        if (databaseDirectory != null) {
            {
                File migratedSQLCipher4File = new File(databaseDirectory, AccountMigrationExecutor.MIGRATION_DATABASE_CIPHER_V4_NAME);

                Utils.deleteFile(LOG_TAG, migratedSQLCipher4File);
            }
            {
                File migratedSQLCipher3File = new File(databaseDirectory, AccountMigrationExecutor.MIGRATION_DATABASE_CIPHER_V3_NAME);

                Utils.deleteFile(LOG_TAG, migratedSQLCipher3File);
            }
            {
                File migratedSQLFile = new File(databaseDirectory, AccountMigrationExecutor.MIGRATION_DATABASE_NAME);

                Utils.deleteFile(LOG_TAG, migratedSQLFile);
            }
        }

        final ConfigurationService configurationService = mTwinlifeImpl.getConfigurationService();
        SecuredConfiguration migratedConfig = configurationService.getSecuredConfiguration(AccountMigrationExecutor.MIGRATION_PREFIX + AccountMigrationExecutor.TWINLIFE_SECURED_CONFIGURATION_KEY);
        SecuredConfiguration migratedAccountConfig = configurationService.getSecuredConfiguration(AccountMigrationExecutor.MIGRATION_PREFIX + AccountMigrationExecutor.ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);

        migratedConfig.setData(null);
        migratedAccountConfig.setData(null);
        configurationService.saveSecuredConfiguration(migratedConfig);
        configurationService.saveSecuredConfiguration(migratedAccountConfig);

        mActiveMigrationId = null;
    }

    /**
     * Commit the account migration by:
     * - updating the Twinlife secure configuration,
     * - updating the account secure configuration,
     * - updating the environmentId,
     * - moving the new database to the database path,
     * - moving the Migrations/conversations files to the final place.
     *
     * @param context the application context.
     * @param rootDirectory the root directory.
     * @param accountService the optional account service to signout the current account before switching.
     * @return true if the commit succeeded.
     */
    boolean commitConfiguration(@NonNull Context context, @NonNull File rootDirectory,
                                @Nullable AccountService accountService) {
        if (DEBUG) {
            Log.d(LOG_TAG, "commitConfiguration");
        }

        final ConfigurationService configurationService = mTwinlifeImpl.getConfigurationService();

        //
        // Step 1: Verify we have everything before switching the account.
        //
        SecuredConfiguration migratedConfig = configurationService.getSecuredConfiguration(AccountMigrationExecutor.MIGRATION_PREFIX + AccountMigrationExecutor.TWINLIFE_SECURED_CONFIGURATION_KEY);
        SecuredConfiguration secureConfig = configurationService.getSecuredConfiguration(AccountMigrationExecutor.TWINLIFE_SECURED_CONFIGURATION_KEY);
        byte[] secureData = migratedConfig.getData();
        if (secureData == null) {
            return false;
        }

        SecuredConfiguration migratedAccountConfig = configurationService.getSecuredConfiguration(AccountMigrationExecutor.MIGRATION_PREFIX + AccountMigrationExecutor.ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);
        SecuredConfiguration accountConfig = configurationService.getSecuredConfiguration(AccountMigrationExecutor.ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);
        byte[] accountData = migratedAccountConfig.getData();
        if (accountData == null) {
            return false;
        }

        // Check that we have either the SQLcipher or the SQLite database.
        File migratedSQLFile = context.getDatabasePath(AccountMigrationExecutor.MIGRATION_DATABASE_NAME);
        File migratedSQLCipher3File = context.getDatabasePath(AccountMigrationExecutor.MIGRATION_DATABASE_CIPHER_V3_NAME);
        File migratedSQLCipher4File = context.getDatabasePath(AccountMigrationExecutor.MIGRATION_DATABASE_CIPHER_V4_NAME);
        if (!migratedSQLCipher4File.exists() && !migratedSQLCipher3File.exists() && !migratedSQLFile.exists()) {
            return false;
        }

        File migrationDirectory = new File(rootDirectory, AccountMigrationExecutor.MIGRATION_DIR);
        Map<UUID, String> settings = AccountMigrationExecutor.getMigrationSettings(migrationDirectory);
        if (settings == null) {
            return false;
        }

        File bkpConversationsDir = new File(rootDirectory, "oldConversations");
        if (bkpConversationsDir.exists()) {
            Utils.deleteDirectory(bkpConversationsDir);
        }

        //
        // Step 2: sign-out the current account, deleting almost everything except the keychain and secure configuration.
        //
        if (accountService != null) {
            // Switch to the new account and database.
            accountService.signOut();
        }

        //
        // Step 3: install the new data from the migration directory to the target place.
        //
        secureConfig.setData(secureData);
        accountConfig.setData(accountData);

        configurationService.saveSecuredConfiguration(secureConfig);
        configurationService.saveSecuredConfiguration(accountConfig);

        // Erase the existing database (if one of them remain, we could have some trouble when we restart).
        Utils.deleteFile(LOG_TAG, context.getDatabasePath(TwinlifeImpl.CIPHER_V4_DATABASE_NAME));
        Utils.deleteFile(LOG_TAG, context.getDatabasePath(TwinlifeImpl.CIPHER_V3_DATABASE_NAME));
        Utils.deleteFile(LOG_TAG, context.getDatabasePath(TwinlifeImpl.DATABASE_NAME));

        boolean result;
        if (migratedSQLCipher4File.exists()) {
            File databaseFile = context.getDatabasePath(TwinlifeImpl.CIPHER_V4_DATABASE_NAME);

            result = migratedSQLCipher4File.renameTo(databaseFile);
            if (Logger.WARN) {
                Logger.warn(LOG_TAG, "Switched SQLCipher ",migratedSQLCipher4File, " to ", databaseFile, " r=", result);
            }

        } else if (migratedSQLCipher3File.exists()) {
            File databaseFile = context.getDatabasePath(TwinlifeImpl.CIPHER_V3_DATABASE_NAME);

            result = migratedSQLCipher3File.renameTo(databaseFile);
            if (Logger.WARN) {
                Logger.warn(LOG_TAG, "Switched SQLCipher ",migratedSQLCipher3File, " to ", databaseFile, " r=", result);
            }

        } else {
            File databaseFile = context.getDatabasePath(TwinlifeImpl.DATABASE_NAME);

            result = migratedSQLFile.renameTo(databaseFile);
            if (Logger.WARN) {
                Logger.warn(LOG_TAG, "Switched SQLite ", migratedSQLFile, " to ", databaseFile, " r=", result);
            }
        }

        File conversationsDir = new File(rootDirectory, Twinlife.CONVERSATIONS_DIR);
        result = conversationsDir.renameTo(bkpConversationsDir);
        if (Logger.WARN) {
            Logger.warn(LOG_TAG, "Switched conversation ", conversationsDir, " to ", bkpConversationsDir, " r=", result);
        }
        if (!result) {
            Utils.deleteDirectory(conversationsDir);
        }

        // If 'conversations' does not exist, check for 'Conversations' which is used on iOS.
        File newConversations = new File(migrationDirectory, Twinlife.CONVERSATIONS_DIR);
        result = newConversations.renameTo(conversationsDir);
        if (Logger.WARN) {
            Logger.warn(LOG_TAG, "Switched conversation ",newConversations, " to ", conversationsDir, " r=", result);
        }

        // Install the new settings.
        Map<UUID, ConfigIdentifier> configs = ConfigIdentifier.getConfigs();
        Map<String, ConfigurationService.Configuration> configurationMap = new HashMap<>();
        for (Map.Entry<UUID, String> setting : settings.entrySet()) {
            ConfigIdentifier config = configs.get(setting.getKey());
            if (config != null) {
                final String configName = config.getConfigName();
                final Class<?> clazz = config.getParameterClass();
                final String configValue = setting.getValue();

                ConfigurationService.Configuration configuration = configurationMap.get(configName);
                if (configuration == null) {
                    configuration = configurationService.getConfiguration(config);
                    configurationMap.put(configName, configuration);
                }

                // Verify and then save the value according to its type.
                if (clazz == Boolean.class) {
                    if ("true".equals(configValue) || "1".equals(configValue)) {
                        configuration.setBoolean(config.getParameterName(), true);
                    } else if ("false".equals(configValue) || "0".equals(configValue)) {
                        configuration.setBoolean(config.getParameterName(), false);
                    } else {
                        // Ignore invalid setting.
                    }
                } else if (clazz == Long.class) {
                    try {
                        configuration.setLongConfig(config, Long.parseLong(configValue));
                    } catch (NumberFormatException exception) {
                        // Ignore invalid setting.
                    }
                } else if (clazz == Integer.class) {
                    try {
                        configuration.setInt(config.getParameterName(), Integer.parseInt(configValue));
                    } catch (NumberFormatException exception) {
                        // Ignore invalid setting.
                    }
                } else if (clazz == Float.class) {
                    try {
                        configuration.setFloatConfig(config, Float.parseFloat(configValue));
                    } catch (NumberFormatException exception) {
                        // Ignore invalid setting.
                    }
                } else {
                    configuration.setStringConfig(config, configValue);
                }
            }
        }

        // And save them.
        for (ConfigurationService.Configuration configuration : configurationMap.values()) {
            configuration.save();
        }

        File picturesDir = new File(rootDirectory, Twinlife.LOCAL_IMAGES_DIR);
        if (picturesDir.exists()) {
            Utils.deleteDirectory(picturesDir);
        }

        File newPicturesDir = new File(migrationDirectory, Twinlife.LOCAL_IMAGES_DIR);
        if (newPicturesDir.exists()) {
            result = newPicturesDir.renameTo(picturesDir);
            if (Logger.WARN) {
                Logger.warn(LOG_TAG, "Switched pictures ", newPicturesDir, " to ", picturesDir, " r=", result);
            }
        }

        //
        // Step 4: cleanup migration markers.
        //
        File hasMigration = new File(rootDirectory, AccountMigrationExecutor.MIGRATION_DONE);
        Utils.deleteFile(LOG_TAG, hasMigration);

        // Erase the migrated configuration.
        migratedConfig.setData(null);
        migratedAccountConfig.setData(null);
        configurationService.saveSecuredConfiguration(migratedConfig);
        configurationService.saveSecuredConfiguration(migratedAccountConfig);

        // Cleanup old conversation files.
        if (bkpConversationsDir.exists() && !Utils.deleteDirectory(bkpConversationsDir) && Logger.ERROR) {
            Logger.error(LOG_TAG, "Old conversations directory was not deleted!");
        }

        if (!Utils.deleteDirectory(migrationDirectory) && Logger.WARN) {
            Logger.warn(LOG_TAG, "Migration directory was not deleted!");
        }
        mActiveMigrationId = null;
        return true;
    }

    /**
     * Check if an account migration is in progress.
     *
     * @param rootDirectory the root directory.
     * @return true if a migration is in progress.
     */
    private static boolean hasMigrationPending(@NonNull File rootDirectory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasMigrationPending");
        }

        final File hasMigration = new File(rootDirectory, AccountMigrationExecutor.MIGRATION_DONE);
        return hasMigration.exists();
    }

    /**
     * Check and get the active account migration id.
     *
     * If we detect an inconsistent configuration, everything is removed.
     *
     * @param rootDirectory the root directory.
     * @return the account migration id or null.
     */
    @Nullable
    private UUID checkActiveAccountMigrationId(@Nullable File rootDirectory, @Nullable File databaseDirectory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkActiveAccountMigrationId");
        }

        if (rootDirectory == null) {
            return null;
        }

        final File migrationDirectory = new File(rootDirectory, AccountMigrationExecutor.MIGRATION_DIR);
        if (!migrationDirectory.exists()) {
            return null;
        }

        final File file = new File(migrationDirectory, AccountMigrationExecutor.MIGRATION_ID);
        if (!file.exists()) {
            cancel(rootDirectory, databaseDirectory);
            return null;
        }

        // Read the UUID stored in the file.
        try (FileInputStream input = new FileInputStream(file)) {

            byte[] data = new byte[256];
            int size = input.read(data);
            UUID result = Utils.UUIDFromString(Utf8.create(data, size));
            if (result == null) {
                cancel(rootDirectory, databaseDirectory);
                return null;
            }

            return result;

        } catch (Exception exception) {
            cancel(rootDirectory, databaseDirectory);
            return null;
        }
    }
}
