/*
 *  Copyright (c) 2020-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConfigIdentifier;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.ConfigurationService.SecuredConfiguration;
import org.twinlife.twinlife.AccountMigrationService;
import org.twinlife.twinlife.AccountMigrationService.State;
import org.twinlife.twinlife.AccountMigrationService.ErrorCode;
import org.twinlife.twinlife.PeerConnectionService.StatType;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.FileInfoImpl;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.ReceivingFileInfo;
import org.twinlife.twinlife.util.SendingFileInfo;
import org.twinlife.twinlife.util.SerializerFactoryImpl;
import org.twinlife.twinlife.util.Utils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.twinlife.twinlife.BaseService.DEFAULT_REQUEST_ID;

/**
 * This class holds the state of the migration process between the two devices.
 * <p>
 * 1/ Scan the directory to export recursively the files
 *    => mListFiles is populated with a list of files, paths are relative to the application directory.
 * 2/ Statistics are exchanged so that the user can decide whether we define a limit for a file size.
 *    => mListFiles is looked to compute such stats.
 * 3/ Send list of files with information in batch of 64-files max taking into account the file size limit
 *    => mListFiles is cleaned while the list is sent to the peer
 *       mSendingFiles is populated with the list of files
 *       files bigger that mMaxFileSize are dropped.
 * 4/ Send data blocks.
 * 5/ Send the application settings.
 * 6/ Send the database.
 *    IMPORTANT NOTE: the database file must be copied in the location pointed to by Android getDatabasePath()
 *    otherwise, the renameTo() that we are doing can fail.  We must also handle the copy of either twinlife.db
 *    or twinlife.cipher or twinlife-4.cipher: it can happen that there was not enough space for the SQLcipher
 *    database migration.
 * 7/ Send the account information.
 * 8/ Terminate and prepare the commit phase.
 * 9/ Do the commit by replacing files, database and settings.
 * <p>
 * The account migration progress is made with the following variables:
 * <p>
 * - mSent indicates the number of bytes for files that have been successfully transferred (updated by processOnPutFile).
 * - mSendTotal is the total number of bytes that must be transferred including the database (updated by processOnListFiles).
 * - mSendPending defines the number of bytes that have been sent but not yet fully acknowledged.  If a file transfer aborts
 *   and produces an error, it is decremented.  It is incremented by sendFileChunk() and decremented by processOnPutFile().
 * - mReceived is the total number of bytes received (similar to mSent, updated by processPutFile).
 * - mReceiveTotal is the total number of bytes that we must receive including the database (updated by processListFiles).
 * - mReceivePending is the total number of bytes received but not yet acknowledged.  It is incremented or decremented
 *   by processPutFile() according to the file transfer.
 * <p>
 * A FileInfoImpl instances are moved across the Map as follows:
 * <pre>
 * [scanDirectory() => mListFiles] -> [sendListFiles() => mWaitListFiles] -> [processOnListFiles() => mSendingFiles]
 * </pre>
 * During a file transfer, the FileInfoImpl can move as follows:
 * <pre>
 * [sendFileChunk() => mWaitAckFiles] -> [processOnPutFile() => deleted if file transfer OK]
 *                                    -> [processOnPutFile() => mSendingFiles if file transfer KO]
 * </pre>
 * For the receiving side, the FileInfoImpl instances are inserted by processListFiles() and removed when a successful
 * transfer is made by processPutFile().
 * <p>
 * When we are disconnected and we re-connect, the account migration executor must invalide all the above information
 * and start with a new fresh state.  This is done in three steps:
 * <p>
 * - cleanup() is called at disconnection time to close the opened files, cleanup the FileInfoImpl maps,
 * - scanDirectory() is called at disconnection time to get a fresh new accurate mListFiles map,
 * - progress counters are cleared by onOpenDataChannel() when we are re-connected.
 */
class AccountMigrationExecutor extends PeerConnectionObserver {
    private static final String LOG_TAG = "AccountMigrationExec..";
    private static final boolean DEBUG = false;

    private static final int MAX_FILES_PER_IQ = 64;       // Max files reported for ListFilesIQ
    private static final int MAX_PENDING_REQUESTS = 64;   // We can send 64 PutFileIQ without waiting the corresponding OnPutFileIQ response.
    private static final int DATA_CHUNK_SIZE = 64 * 1024; // DATA_CHUNK_SIZE * MAX_PENDING_REQUEST must be <= 16Mb (WebRTC SCTP constraint).

    static final String TWINLIFE_SECURED_CONFIGURATION_KEY = "TwinlifeSecuredConfiguration";
    static final String ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY = "AccountServiceSecuredConfiguration";
    static final String MIGRATION_PREFIX = "Migration";

    static final String MIGRATION_DATABASE_NAME = "migration.db";
    static final String MIGRATION_DATABASE_CIPHER_V3_NAME = "migration-3.sqlcipher";
    static final String MIGRATION_DATABASE_CIPHER_V4_NAME = "migration-4.sqlcipher";

    static final String MIGRATION_DIR = "Migration";
    static final String MIGRATION_DONE = "migration-done";
    static final String MIGRATION_ID = "migration-id";

    // Web-RTC can hang while transmitting data (See webrtc 11824 and 11547).  We setup a timer that
    // fires to detect regularly if we received responses to our requests.  The timer fires two times
    // before we decide the P2P connection is stuck and closing it.
    private static final int REQUEST_TIMEOUT = 30 / 2;

    // Specific file indexes for the database file transfer.
    private static final int DATABASE_FILE_INDEX = 1;
    private static final int DATABASE_CIPHER_3_FILE_INDEX = 2;
    private static final int DATABASE_CIPHER_4_FILE_INDEX = 3;
    private static final int DATABASE_CIPHER_5_FILE_INDEX = 4;
    private static final int FIRST_FILE_INDEX = 10; // Note we can have holes in file indexes.

    // The two devices must use request Ids that don't overlap because we wait
    // for some specific responses sent to the peer and we could clear the
    // pendingIQRequests when we receive another message.  This is critical
    // for the SendAccount/WaitAccount phase.  Since requestIds are 64-bits,
    // set the upper part for one of them.
    private static final long REQUEST_ID_OFFSET_INITIATOR = (1L << 32);
    private static final long REQUEST_ID_OFFSET_CLIENT = (1L << 33);

    // Map some statistics
    private static final StatType IQ_STAT_QUERY = StatType.IQ_SET_PUSH_OBJECT;
    private static final StatType IQ_STAT_ON_QUERY = StatType.IQ_RESULT_PUSH_OBJECT;
    private static final StatType IQ_STAT_LIST_FILES = StatType.IQ_SET_PUSH_FILE;
    private static final StatType IQ_STAT_ON_LIST_FILES = StatType.IQ_RESULT_PUSH_FILE;
    private static final StatType IQ_STAT_PUT_FILE = StatType.IQ_SET_PUSH_FILE_CHUNK;
    private static final StatType IQ_STAT_ON_PUT_FILE = StatType.IQ_RESULT_PUSH_FILE_CHUNK;
    private static final StatType IQ_STAT_SETTINGS = StatType.IQ_SET_INVITE_GROUP;
    private static final StatType IQ_STAT_START = StatType.IQ_SET_PUSH_TWINCODE;
    private static final StatType IQ_STAT_ACCOUNT = StatType.IQ_SET_PUSH_GEOLOCATION;
    private static final StatType IQ_STAT_TERMINATE = StatType.IQ_SET_RESET_CONVERSATION;
    private static final StatType IQ_STAT_SHUTDOWN = StatType.IQ_SET_LEAVE_GROUP;
    private static final StatType IQ_STAT_ERROR = StatType.IQ_RESULT_PUSH_TWINCODE;

    private static final UUID QUERY_STAT_SCHEMA_ID = UUID.fromString("4b201b06-7952-43a4-8157-96b9aeffa667");
    private static final UUID LIST_FILES_SCHEMA_ID = UUID.fromString("5964dbf0-5620-4c78-963b-c6e08665fc33");
    private static final UUID START_SCHEMA_ID = UUID.fromString("8a26fefe-6bd5-45e2-9098-3d736d8a1c4e");
    private static final UUID PUT_FILE_SCHEMA_ID = UUID.fromString("ccc791c2-3a5c-4d83-ab06-48137a4ad262");
    private static final UUID SETTINGS_SCHEMA_ID = UUID.fromString("09557d03-3af7-4151-aa60-c6a4b992e18b");
    private static final UUID SWAP_ACCOUNT_SCHEMA_ID = UUID.fromString("11161f66-68e9-4cb4-8c12-241f4e071af4");
    private static final UUID TERMINATE_MIGRATION_SCHEMA_ID = UUID.fromString("a35089f8-326f-4f25-b160-e0f9f2c9795c");
    private static final UUID SHUTDOWN_SCHEMA_ID = UUID.fromString("05c90756-d56c-4e2f-92bf-36b2d3f31b76");
    private static final UUID ERROR_SCHEMA_ID = UUID.fromString("42705574-8e05-47fd-9742-ffd86a923cea");

    private static final UUID ON_QUERY_STAT_SCHEMA_ID = UUID.fromString("0906f883-6adf-4d90-9252-9ab401fbe531");
    private static final UUID ON_LIST_FILES_SCHEMA_ID = UUID.fromString("e74fea73-abc7-42ca-ad37-b636f6c4df2b");
    private static final UUID ON_PUT_FILE_SCHEMA_ID = UUID.fromString("ef7b3c03-33d5-49c2-8644-79ea2688403e");

    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_QUERY_STAT_SERIALIZER = QueryStatsIQ.createSerializer(QUERY_STAT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_LIST_FILES_SERIALIZER = ListFilesIQ.createSerializer(LIST_FILES_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_START_SERIALIZER = StartIQ.createSerializer(START_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_PUT_FILE_SERIALIZER = PutFileIQ.createSerializer(PUT_FILE_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_SETTINGS_SERIALIZER = SettingsIQ.createSerializer(SETTINGS_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_SWAP_ACCOUNT_SERIALIZER = AccountIQ.createSerializer(SWAP_ACCOUNT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_TERMINATE_MIGRATION_SERIALIZER = TerminateMigrationIQ.createSerializer(TERMINATE_MIGRATION_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_SHUTDOWN_SERIALIZER = ShutdownIQ.createSerializer(SHUTDOWN_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ERROR_SERIALIZER = ErrorIQ.createSerializer(ERROR_SCHEMA_ID, 1);

    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_QUERY_STAT_SERIALIZER = OnQueryStatsIQ.createSerializer(ON_QUERY_STAT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_LIST_FILES_SERIALIZER = OnListFilesIQ.createSerializer(ON_LIST_FILES_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_PUT_FILE_SERIALIZER = OnPutFileIQ.createSerializer(ON_PUT_FILE_SCHEMA_ID, 1);

    private final AccountMigrationServiceImpl mAccountMigrationService;
    private final Set<Long> mPendingIQRequests;
    private final List<FileInfoImpl> mListFiles;
    private final Map<Integer, FileInfoImpl> mWaitListFiles;
    private final Map<Integer, FileInfoImpl> mSendingFiles;
    private final Map<Integer, FileInfoImpl> mReceivingFiles;
    private final Map<Integer, FileInfoImpl> mWaitAckFiles;
    private final Map<Integer, ReceivingFileInfo> mReceivingStreams;
    private final ConfigurationService mConfigurationService;
    private final File mRootDirectory;
    private final File mMigrationDirectory;
    @NonNull
    private final UUID mAccountMigrationId;
    @NonNull
    private final File mDatabaseFile;
    @NonNull
    private final DatabaseServiceImpl mDatabase;
    private final int mDatabaseFileIndex;
    private int mFileIndex = FIRST_FILE_INDEX;
    private long mMaxFileSize = Long.MAX_VALUE;
    private long mSent = 0;
    private long mReceived = 0;
    private long mSendTotal = 0;
    private long mReceiveTotal = 0;
    private long mSendPending = 0;
    private long mReceivePending = 0;
    private boolean mAccountReceived = false;
    private boolean mAccountSent = false;
    private boolean mSettingsReceived = false;
    private boolean mSettingsSent = false;
    private boolean mRequestTimeoutExpired = false;
    private boolean mNeedRestart = false;
    private int mReceiveErrorCount = 0;
    private int mSendErrorCount = 0;
    private SendingFileInfo mSendingFile;
    private volatile AccountMigrationService.State mState = State.STARTING;
    private long mLastReport = 0;
    private QueryInfoImpl mPeerInfo;
    private QueryInfoImpl mLocalInfo;
    private ErrorCode mCurrentError;
    @SuppressWarnings("rawtypes")
    private volatile ScheduledFuture mRequestTimeout;
    private long mOffsetRequestId;

    static void init(@NonNull SerializerFactoryImpl serializerFactory) {

        serializerFactory.addSerializer(IQ_QUERY_STAT_SERIALIZER);
        serializerFactory.addSerializer(IQ_LIST_FILES_SERIALIZER);
        serializerFactory.addSerializer(IQ_START_SERIALIZER);
        serializerFactory.addSerializer(IQ_PUT_FILE_SERIALIZER);
        serializerFactory.addSerializer(IQ_SETTINGS_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_QUERY_STAT_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_PUT_FILE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_LIST_FILES_SERIALIZER);
        serializerFactory.addSerializer(IQ_SWAP_ACCOUNT_SERIALIZER);
        serializerFactory.addSerializer(IQ_TERMINATE_MIGRATION_SERIALIZER);
        serializerFactory.addSerializer(IQ_SHUTDOWN_SERIALIZER);
        serializerFactory.addSerializer(IQ_ERROR_SERIALIZER);
    }

    AccountMigrationExecutor(@NonNull TwinlifeImpl twinlifeImpl,
                             @NonNull AccountMigrationServiceImpl accountMigrationService,
                             @NonNull DatabaseServiceImpl database,
                             @NonNull UUID accountMigrationId, @NonNull String peerId,
                             @NonNull File rootDirectory) {
        super(twinlifeImpl, peerId);
        if (DEBUG) {
            Log.d(LOG_TAG, "AccountMigrationExecutor accountMigrationId=" + accountMigrationId);
        }

        mAccountMigrationService = accountMigrationService;
        mConfigurationService = twinlifeImpl.getConfigurationService();
        mAccountMigrationId = accountMigrationId;
        mSendingFiles = new HashMap<>();
        mReceivingFiles = new HashMap<>();
        mListFiles = new ArrayList<>();
        mWaitListFiles = new HashMap<>();
        mWaitAckFiles = new HashMap<>();
        mReceivingStreams = new HashMap<>();
        mPendingIQRequests = new HashSet<>();
        mRootDirectory = rootDirectory;
        mDatabase = database;

        String path = database.getDatabasePath();
        mDatabaseFile = new File(path);
        mOffsetRequestId = 0;

        if (!path.endsWith(".cipher")) {
            mDatabaseFileIndex = DATABASE_FILE_INDEX;
        } else if (path.endsWith("-4.cipher")) {
            mDatabaseFileIndex = DATABASE_CIPHER_4_FILE_INDEX;
        } else {
            mDatabaseFileIndex = DATABASE_CIPHER_3_FILE_INDEX;
        }
        mMigrationDirectory = new File(mRootDirectory, MIGRATION_DIR);

        // Register the binary IQ handlers for the query and responses.
        addPacketListener(IQ_QUERY_STAT_SERIALIZER, this::onQueryStatsIQ);
        addPacketListener(IQ_ON_QUERY_STAT_SERIALIZER, this::onOnQueryStatsIQ);

        addPacketListener(IQ_START_SERIALIZER, this::onStartIQ);

        addPacketListener(IQ_LIST_FILES_SERIALIZER, this::onListFilesIQ);
        addPacketListener(IQ_ON_LIST_FILES_SERIALIZER, this::onOnListFilesIQ);

        addPacketListener(IQ_PUT_FILE_SERIALIZER, this::onPutFileIQ);
        addPacketListener(IQ_ON_PUT_FILE_SERIALIZER, this::onOnPutFileIQ);

        addPacketListener(IQ_SETTINGS_SERIALIZER, this::onSettingsIQ);

        addPacketListener(IQ_SWAP_ACCOUNT_SERIALIZER, this::onAccountIQ);

        addPacketListener(IQ_TERMINATE_MIGRATION_SERIALIZER, this::onTerminateMigrationIQ);

        addPacketListener(IQ_SHUTDOWN_SERIALIZER, this::onShutdownIQ);

        addPacketListener(IQ_ERROR_SERIALIZER, this::onErrorIQ);

        // Create the migration id file so that we can easily find the current migration id if we are interrupted.
        if (!mMigrationDirectory.exists() && !mMigrationDirectory.mkdirs()) {
            Log.w(LOG_TAG, "Cannot create " + mMigrationDirectory.getPath());
            mReceiveErrorCount++;
        }

        File f = new File(mMigrationDirectory, MIGRATION_ID);
        try (FileOutputStream outputStream = new FileOutputStream(f)) {
            outputStream.write(mAccountMigrationId.toString().getBytes());

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot create ", f);
            }
            mReceiveErrorCount++;
        }

        // Scan the directory which contains files.
        mExecutor.execute(this::scanRootDirectory);
    }

    @NonNull
    UUID getAccountMigrationId() {

        return mAccountMigrationId;
    }

    @NonNull
    synchronized State getState() {

        return mState;
    }

    synchronized boolean canTerminate() {

        return mState == State.TERMINATE || (mState == State.WAIT_ACCOUNT && mAccountReceived && mAccountSent);
    }

    private long newRequestId() {

        return mTwinlifeImpl.newRequestId() + mOffsetRequestId;
    }

    /**
     * Stop the executor (synchronized to make sure we stop, commit and change the state without being annoyed by another thread).
     */
    private synchronized void stop(@NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "stop terminateReason=" + terminateReason);
        }

        boolean commit = (terminateReason != TerminateReason.CANCEL && mState == State.TERMINATED);

        if (commit) {
            mAccountMigrationService.commitConfiguration(mTwinlifeImpl.getContext(), mRootDirectory, mTwinlifeImpl.getAccountService());
        }

        setState(State.STOPPED);
        finish();

        cleanup();
    }

    private synchronized void cleanup() {
        if (DEBUG) {
            Log.d(LOG_TAG, "cleanup");
        }

        // Cleanup so that we can restart the account migration after a P2P network interruption.
        mListFiles.clear();
        mWaitListFiles.clear();
        mReceivingFiles.clear();
        mWaitAckFiles.clear();
        mReceivingFiles.clear();
        mSendingFiles.clear();
        mPendingIQRequests.clear();
        mNeedRestart = true;

        // Cancel the request timeout if it is running.
        if (mRequestTimeout != null) {
            mRequestTimeout.cancel(false);
            mRequestTimeout = null;
        }

        // Cancel receiving files.
        while (!mReceivingStreams.isEmpty()) {
            Map.Entry<Integer, ReceivingFileInfo> fileInfoEntry = mReceivingStreams.entrySet().iterator().next();

            mReceivingStreams.remove(fileInfoEntry.getKey());
            fileInfoEntry.getValue().cancel();
        }

        // Cancel sending file (there is only one at a time).
        if (mSendingFile != null) {
            mSendingFile.cancel();
            mSendingFile = null;
        }
    }

    /**
     * Cancel the migration and cleanup the migration temporary area.
     */
    synchronized void cancel() {
        if (DEBUG) {
            Log.d(LOG_TAG, "cancel");
        }

        setState(State.CANCELED);

        mAccountMigrationService.cancel(mRootDirectory, mDatabaseFile.getParentFile());

        stop(TerminateReason.CANCEL);
    }

    /**
     * Called when the P2P data channel is opened and validated: we can write on it.
     */
    @Override
    protected synchronized void onDataChannelOpen() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDataChannelOpen");
        }

        State state = getState();
        if (state == State.STARTING) {
            setState(State.NEGOTIATE);

        } else if (mNeedRestart && state != State.CANCELED && state != State.TERMINATED && state != State.ERROR && state != State.NEGOTIATE) {
            mNeedRestart = false;
            mSent = 0;
            mReceived = 0;
            mSendPending = 0;
            mReceivePending = 0;
            if (mPeerInfo != null) {
                mReceiveTotal = mPeerInfo.getDatabaseFileSize();
            } else {
                mReceiveTotal = 0;
            }
            if (mLocalInfo != null) {
                mSendTotal = mLocalInfo.getDatabaseFileSize();
            } else {
                mSendTotal = 0;
            }
            setState(State.LIST_FILES);

        } else {
            updateProgress();
        }

        mExecutor.execute(this::processMigration);
    }

    /**
     * Called when the P2P data channel is closed.
     *
     * @param terminateReason the terminate reason.
     */
    @Override
    protected synchronized void onTerminate(@NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTerminate terminateReason=" + terminateReason);
        }

        if (terminateReason == TerminateReason.REVOKED) {
            mCurrentError = ErrorCode.REVOKED;
        } else if (terminateReason == TerminateReason.NOT_AUTHORIZED) {
            mCurrentError = ErrorCode.BAD_PEER_VERSION;
        }
        updateProgress();

        State state = getState();
        if (state == State.TERMINATED) {
            stop(terminateReason);

        } else if (terminateReason == TerminateReason.REVOKED || terminateReason == TerminateReason.DECLINE
                || terminateReason == TerminateReason.CANCEL || terminateReason == TerminateReason.NOT_AUTHORIZED
                || state == State.CANCELED) {
            cancel();

        } else if (state != State.STOPPED) {
            mExecutor.execute(this::cleanup);
            mExecutor.execute(this::scanRootDirectory);
        }
    }

    /**
     * Called when there is a timeout trying to open the P2P data channel.
     */
    @Override
    protected void onTimeout() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTimeout");
        }

        updateProgress();
    }

    //
    // Operations used by the AccountMigrationService.
    //

    /**
     * Query the peer device to obtain statistics about the files it provides.
     *
     * @param requestId the request identifier.
     * @param maxFileSize the maximum file size we accept.
     */
    void queryStats(long requestId, long maxFileSize) {
        if (DEBUG) {
            Log.d(LOG_TAG, "queryStats: requestId=" + requestId + " maxFileSize=" + maxFileSize);
        }

        QueryStatsIQ queryStatsIQ = new QueryStatsIQ(IQ_QUERY_STAT_SERIALIZER, requestId, maxFileSize);
        sendPeerPacket(IQ_STAT_QUERY, queryStatsIQ);
    }

    /**
     * Start the migration by asking the peer device to send its files.
     *
     * @param requestId the request identifier.
     * @param maxFileSize the maximum file size we accept.
     */
    void startMigration(long requestId, long maxFileSize) {
        if (DEBUG) {
            Log.d(LOG_TAG, "startMigration requestId=" + requestId + " maxFileSize=" + maxFileSize);
        }

        if (mOffsetRequestId == 0) {
            mOffsetRequestId = REQUEST_ID_OFFSET_INITIATOR;
        }

        BinaryPacketIQ startIQ = sendStart(requestId, maxFileSize);

        // Not enough space locally to receive the files, abort the migration with an error.
        if (startIQ instanceof ErrorIQ) {
            sendPeerPacket(StatType.IQ_ERROR, startIQ);
            setState(State.ERROR);
            return;
        }

        if (mLocalInfo != null) {
            mSendTotal = mLocalInfo.getDatabaseFileSize();
        }
        if (mPeerInfo != null) {
            mReceiveTotal = mPeerInfo.getDatabaseFileSize();
        }
        sendPeerPacket(IQ_STAT_START, startIQ);
    }

    /**
     * Terminate the migration by sending the termination IQ and closing the P2P connection.
     *
     * @param requestId the request identifier.
     * @param commit true if we commit or and false if we abort the migration.
     * @param done true if the DeviceMigration object and its twincode was deleted.
     */
    void terminateMigration(long requestId, boolean commit, boolean done) {
        if (DEBUG) {
            Log.d(LOG_TAG, "terminateMigration requestId=" + requestId + " commit=" + commit + " done=" + done);
        }

        TerminateMigrationIQ terminateMigrationIQ = new TerminateMigrationIQ(IQ_TERMINATE_MIGRATION_SERIALIZER, requestId, commit, done);
        sendPeerPacket(IQ_STAT_TERMINATE, terminateMigrationIQ);
    }

    /**
     * Shutdown the P2P connection gracefully after the terminate phase1 and phase2.
     *
     * @param requestId the request identifier.
     */
    void shutdownMigration(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "shutdownMigration requestId=" + requestId);
        }

        ShutdownIQ shutdownIQ = new ShutdownIQ(IQ_SHUTDOWN_SERIALIZER, requestId, false);
        sendPeerPacket(IQ_STAT_SHUTDOWN, shutdownIQ);
    }

    //
    // Handling IQ request and responses.
    //

    /**
     * Request query-stats operation is received.
     *
     * Get the stats about the files to be transferred taking into account a max file size constraint.
     *
     * @param iq the query-stat request.
     */
    private void onQueryStatsIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onQueryStatsIQ iq=" + iq);
        }

        if (!(iq instanceof QueryStatsIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processQueryStats((QueryStatsIQ)iq));
    }

    /**
     * Response received after query-stats operation.
     *
     * Propagate the query response to upper services.
     *
     * @param iq the on-list-file response.
     */
    private void onOnQueryStatsIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOnQueryStatsIQ iq=" + iq);
        }

        if (!(iq instanceof OnQueryStatsIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processOnQueryStats((OnQueryStatsIQ)iq));
    }

    /**
     * Request list-files operation is received.
     *
     * Look at the list of files sent by the peer and check if we know them to prepare receiving their content.
     *
     * @param iq the list-file request.
     */
    private void onListFilesIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListFilesIQ iq=" + iq);
        }

        if (!(iq instanceof ListFilesIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processListFiles((ListFilesIQ)iq));
    }

    /**
     * Response received after list-files operation.
     *
     * @param iq the on-list-file response.
     */
    private void onOnListFilesIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOnListFilesIQ iq=" + iq);
        }

        if (!(iq instanceof OnListFilesIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processOnListFiles((OnListFilesIQ)iq));
    }

    /**
     * Request start operation is received.
     *
     * Start sending the list of files, their content, the settings and finish by the account.
     *
     * @param iq the start request.
     */
    private void onStartIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onStartIQ iq=" + iq);
        }

        if (!(iq instanceof StartIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processStart((StartIQ)iq));
    }

    /**
     * Request put-file operation.
     *
     * Receive a chunk for a file and append that chunk to the file computing the SHA256 on the fly.
     *
     * @param iq the put-file response.
     */
    private void onPutFileIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPutFileIQ iq=" + iq);
        }

        if (!(iq instanceof PutFileIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processPutFile((PutFileIQ)iq));
    }

    /**
     * Response received after put-file operation.
     *
     * Receive the response of the put-file chunk that was sent.
     *
     * @param iq the on-put-file response.
     */
    private void onOnPutFileIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPutFileIQ iq=" + iq);
        }

        if (!(iq instanceof OnPutFileIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processOnPutFile((OnPutFileIQ)iq));
    }

    /**
     * Request settings operation.
     *
     * Get the settings and save them in the migration place.
     *
     * @param iq the settings request.
     */
    private void onSettingsIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSettingsIQ iq=" + iq);
        }

        if (!(iq instanceof SettingsIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processSettings((SettingsIQ)iq));
    }

    /**
     * Request swap-account operation.
     *
     * Get the account information with the twinlife secure configuration, account configuration.
     *
     * @param iq the swap-account request.
     */
    private void onAccountIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSwapAccountIQ iq=" + iq);
        }

        if (!(iq instanceof AccountIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processAccount((AccountIQ)iq));
    }

    /**
     * Request terminate-migration operation is received.
     *
     * @param iq the terminate-migration request.
     */
    private void onTerminateMigrationIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTerminateMigrationIQ iq=" + iq);
        }

        if (!(iq instanceof TerminateMigrationIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processTerminateMigration((TerminateMigrationIQ)iq));
    }

    /**
     * Request shutdown operation is received.
     *
     * @param iq the shutdown request.
     */
    private void onShutdownIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onShutdownIQ iq=" + iq);
        }

        if (!(iq instanceof ShutdownIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processShutdown((ShutdownIQ)iq));
    }

    /**
     * Error packet is received.
     *
     * @param iq the error packet.
     */
    private void onErrorIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onErrorIQ iq=" + iq);
        }

        if (!(iq instanceof ErrorIQ)) {
            throw new IllegalArgumentException("Invalid IQ");
        }

        mExecutor.execute(() -> processError((ErrorIQ)iq));
    }

    //
    // Process some operation from the executor's thread only.
    //

    private void processQueryStats(@NonNull QueryStatsIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processQueryStats iq=" + iq);
        }

        long fileCount = 0;
        long maxFileSize = 0;
        long totalFileSize = 0;
        Set<String> directories = new HashSet<>();

        mMaxFileSize = iq.maxFileSize;
        for (FileInfoImpl fileInfo : mListFiles) {
            long size = fileInfo.getSize();
            if (size <= mMaxFileSize) {
                String dir = fileInfo.getPath();
                int pos = dir.lastIndexOf('/');
                if (pos >= 0) {
                    dir = dir.substring(pos);
                }
                directories.add(dir);

                fileCount++;
                totalFileSize += size;
                if (maxFileSize < size) {
                    maxFileSize = size;
                }
            }
        }

        long directoryCount = directories.size();
        long databaseFileSize = mDatabaseFile.length();
        long dbSpaceAvailable = Utils.getDiskSpace(mDatabaseFile);
        long fileSpaceAvailable = Utils.getDiskSpace(mMigrationDirectory);

        mLocalInfo = new QueryInfoImpl(directoryCount, fileCount, maxFileSize, totalFileSize, databaseFileSize, dbSpaceAvailable, fileSpaceAvailable);

        sendPeerPacket(IQ_STAT_ON_QUERY, new OnQueryStatsIQ(IQ_ON_QUERY_STAT_SERIALIZER, iq, mLocalInfo));

        // Setup the local information and propagate to the onQueryStats() observer if necessary (only if we have the peer info).
        // This is not one of our request so use the DEFAULT_REQUEST_ID.
        if (mPeerInfo == null) {
            queryStats(DEFAULT_REQUEST_ID, mMaxFileSize);
        } else {
            mAccountMigrationService.onQueryStats(DEFAULT_REQUEST_ID, mPeerInfo, mLocalInfo);
        }
    }

    private void processOnQueryStats(@NonNull OnQueryStatsIQ statsIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnQueryStats statsIQ=" + statsIQ);
        }

        mPeerInfo = statsIQ.queryInfo;

        mAccountMigrationService.onQueryStats(statsIQ.getRequestId(), mPeerInfo, mLocalInfo);
    }

    /**
     * Start the migration.
     *
     * @param iq the start IQ.
     */
    private void processStart(@NonNull StartIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processStart iq=" + iq);
        }

        if (iq.maxFileSize < 0) {
            setState(State.ERROR);
            return;
        }

        if (mOffsetRequestId == 0) {
            mOffsetRequestId = REQUEST_ID_OFFSET_CLIENT;
        }

        // Check that we have enough space to receive the files.
        if (getState() == AccountMigrationService.State.NEGOTIATE) {

            BinaryPacketIQ resultIQ = sendStart(iq.getRequestId(), iq.maxFileSize);

            // Not enough space locally to receive the files, abort the migration with an error.
            if (resultIQ instanceof ErrorIQ) {
                sendPeerPacket(IQ_STAT_ERROR, resultIQ);
                setState(State.ERROR);
                return;
            }

            sendPeerPacket(IQ_STAT_START, resultIQ);
        }

        if (mPeerInfo != null) {
            mReceiveTotal = mPeerInfo.getDatabaseFileSize();
        }
        if (mLocalInfo != null) {
            mSendTotal = mLocalInfo.getDatabaseFileSize();
        }
        setState(State.LIST_FILES);
        mMaxFileSize = iq.maxFileSize;

        processMigration();
    }

    @NonNull
    private File toLocalPath(@NonNull FileInfoImpl fileInfo) {
        if (DEBUG) {
            Log.d(LOG_TAG, "toLocalPath fileInfo=" + fileInfo);
        }

        final int fileIndex = fileInfo.getIndex();
        if (fileIndex >= FIRST_FILE_INDEX) {
            return new File(mMigrationDirectory, fileInfo.getPath().toLowerCase());
        }

        if (fileIndex == DATABASE_FILE_INDEX) {
            return new File(mDatabaseFile.getParentFile(), MIGRATION_DATABASE_NAME);

        } else if (fileIndex == DATABASE_CIPHER_3_FILE_INDEX) {
            return new File(mDatabaseFile.getParentFile(), MIGRATION_DATABASE_CIPHER_V3_NAME);

        } else if (fileIndex == DATABASE_CIPHER_4_FILE_INDEX) {
            return new File(mDatabaseFile.getParentFile(), MIGRATION_DATABASE_CIPHER_V4_NAME);

        } else if (fileIndex == DATABASE_CIPHER_5_FILE_INDEX) {
            return new File(mDatabaseFile.getParentFile(), MIGRATION_DATABASE_CIPHER_V4_NAME);

        } else {
            return null;
        }
    }

    /**
     * Look at the list of files sent by the peer and check if we know them to prepare receiving their content.
     *
     * @param iq the list files IQ.
     */
    private void processListFiles(@NonNull ListFilesIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processListFiles iq=" + iq);
        }

        mRequestTimeoutExpired = false;

        // The peer started.
        if (getState() == State.NEGOTIATE) {
            setState(State.LIST_FILES);
        }

        List<FileState> result = new ArrayList<>();
        List<FileInfoImpl> list = iq.files;
        for (FileInfoImpl fileInfo : list) {
            File file = toLocalPath(fileInfo);
            long offset;
            if (!file.exists()) {
                offset = 0;
            } else if (file.length() == fileInfo.getSize() && !sameDate(file, fileInfo)) {
                offset = 0;
                Log.w(LOG_TAG, "File '" + fileInfo.getPath() + "' was modified");
            } else {
                offset = file.length();
            }
            mReceivingFiles.put(fileInfo.getIndex(), fileInfo);
            if (fileInfo.getIndex() >= FIRST_FILE_INDEX) {
                mReceiveTotal += fileInfo.getSize();
            }

            FileState state = new FileState(fileInfo.getIndex(), offset);
            result.add(state);
        }

        sendPeerPacket(IQ_STAT_ON_LIST_FILES, new OnListFilesIQ(IQ_ON_LIST_FILES_SERIALIZER, iq, result));
    }

    /**
     * Compare the file dates.  On iOS, we get a millisecond precision date for the file but on most
     * Android, the filesystem only gives a second precision (the `setLastModified()` may also loose some precision).
     * Make sure to compare the two values on the second boundary, ignoring the milliseconds.
     *
     * @param file the local file.
     * @param fileInfo the remote file information.
     * @return true if the two files have the same date.
     */
    private static boolean sameDate(@NonNull File file, @NonNull FileInfoImpl fileInfo) {

        // Get values in local variables for debugging...
        final long fileDate = file.lastModified();
        final long peerDate = fileInfo.getModificationDate();
        return (fileDate / 1000L) == (peerDate / 1000L);
    }

    /**
     * After sending the list of files, take into account the offset status for each file
     * as reported by the peer.
     *
     * @param iq the list files result for each file.
     */
    private void processOnListFiles(@NonNull OnListFilesIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnListFiles iq=" + iq);
        }

        mRequestTimeoutExpired = false;

        mPendingIQRequests.remove(iq.getRequestId());

        List<FileState> list = iq.files;
        for (FileState state : list) {
            FileInfoImpl fileInfo = mWaitListFiles.remove(state.mFileId);

            if (fileInfo != null) {
                if (state.mOffset <= fileInfo.getSize()) {
                    fileInfo.setRemoteOffset(state.mOffset);
                } else {
                    fileInfo.setRemoteOffset(0);
                }
                mSendTotal += fileInfo.getSize();
                mSendingFiles.put(fileInfo.getIndex(), fileInfo);
            } else {
                Log.w(LOG_TAG, "File " + state.mFileId + " was not found");
            }
        }

        // Continue the migration process.
        if (mState != AccountMigrationService.State.STOPPED) {
            mExecutor.execute(this::processMigration);
        }
    }

    /**
     * Receive a chunk for a file and append that chunk to the file computing the SHA256 on the fly.
     *
     * @param iq the file chunk IQ
     */
    private void processPutFile(@NonNull PutFileIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "receiveFileChunk iq=" + iq);
        }

        mRequestTimeoutExpired = false;

        ReceivingFileInfo fileStream = mReceivingStreams.get(iq.fileId);
        if (fileStream == null) {
            FileInfoImpl fileInfo = mReceivingFiles.get(iq.fileId);
            if (fileInfo == null) {
                Log.w(LOG_TAG, "File " + iq.fileId + " not registered in receiving list");

                mReceiveErrorCount++;
                sendPeerPacket(IQ_STAT_ON_PUT_FILE, new OnPutFileIQ(IQ_ON_PUT_FILE_SERIALIZER, iq, iq.fileId, -1));
                return;
            }

            final File file = toLocalPath(fileInfo);

            Log.e(LOG_TAG, "Receiving file " + fileInfo.getPath());
            try {
                fileStream = new ReceivingFileInfo(file, fileInfo, iq.offset);
                mReceivingStreams.put(fileInfo.getIndex(), fileStream);

            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Fatal IO error for ", iq.fileId, ": ", exception.getMessage());
                }

                sendPeerPacket(IQ_STAT_ERROR, sendError(iq.getRequestId(), ErrorCode.IO_ERROR));

                mReceiveErrorCount++;
                sendPeerPacket(IQ_STAT_ON_PUT_FILE, new OnPutFileIQ(IQ_ON_PUT_FILE_SERIALIZER, iq, iq.fileId, -1));
                return;
            }

            mReceivePending += fileStream.getPosition();
        }

        long offset;
        try {
            offset = fileStream.getPosition();
            if (iq.offset > offset) {
                // This error occurs when a file transfer is interrupted and a miss-match occurs due to some IQs
                // that are not taken into account and must be discarded.
                // Log.e(LOG_TAG, "Invalid offset: " + offset);
                mReceivingStreams.remove(fileStream.getFileIndex());
                fileStream.cancel();
            } else if (iq.offset == offset) {
                if (iq.size > 0) {
                    mReceivePending -= offset;
                    fileStream.write(iq.fileData, iq.size);
                    offset = fileStream.getPosition();
                    mReceivePending += offset;
                }

                if (iq.sha256 != null) {
                    mReceivingStreams.remove(fileStream.getFileIndex());
                    mReceivePending -= offset;

                    if (!fileStream.close(iq.sha256)) {
                        offset = 0;
                        if (Logger.ERROR) {
                            Logger.error(LOG_TAG, "Bad receipt for ", fileStream.getFileIndex());
                        }
                    } else {
                        // Log.e(LOG_TAG, "File " + fileStream.getFileIndex() + " received successfully");
                        mReceivingFiles.remove(fileStream.getFileIndex());
                        mReceived += offset;
                    }
                }

                long now = System.currentTimeMillis();
                if (mLastReport + 500 < now) {
                    mLastReport = now;
                    updateProgress();
                }
            }
        } catch (IOException exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Fatal IO Error: ", exception.getMessage());
            }
            offset = -1; // IO error means we cannot retry.
            mReceiveErrorCount++;
            sendPeerPacket(IQ_STAT_ERROR, sendError(iq.getRequestId(), ErrorCode.IO_ERROR));

        } catch (Exception exception) {
            offset = 0; // We could retry
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Exception ", exception);
            }
        }
        sendPeerPacket(IQ_STAT_ON_PUT_FILE, new OnPutFileIQ(IQ_ON_PUT_FILE_SERIALIZER, iq, iq.fileId, offset));
    }

    /**
     * Receive the response of the put-file chunk that was sent.
     *
     * @param iq the IQ response.
     */
    private void processOnPutFile(@NonNull OnPutFileIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnPutFile iq=" + iq);
        }

        mPendingIQRequests.remove(iq.getRequestId());
        mRequestTimeoutExpired = false;

        FileInfoImpl fileInfo = mWaitAckFiles.get(iq.fileId);
        if (fileInfo != null) {
            // Log.d(LOG_TAG, "processOnPutFile iq=" + iq + " fileSize=" + fileInfo.getSize());

            // File was received completely and the integrity was verified.
            if (fileInfo.getSize() == iq.offset) {
                mWaitAckFiles.remove(iq.fileId);
                mSent += fileInfo.getSize();
                mSendPending -= fileInfo.getSize();
                if (mSendPending < 0) {
                    mSendPending = 0;
                }

                // Log.e(LOG_TAG, "Ack received for file " + iq.fileId + " size=" + fileInfo.getSize());

            } else if (iq.offset < 0) {
                mWaitAckFiles.remove(iq.fileId);
                mSendErrorCount++;
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "IO Error on the peer for file ", iq.fileId);
                }

            } else if (iq.offset == 0 || iq.offset > fileInfo.getSize()
                    || (mSendingFile != null && !mSendingFile.isAcceptedDataChunk(fileInfo, iq.offset, MAX_PENDING_REQUESTS * DATA_CHUNK_SIZE))) {
                mWaitAckFiles.remove(iq.fileId);
                mSendingFiles.put(fileInfo.getIndex(), fileInfo);
                fileInfo.setRemoteOffset(iq.offset);
                mSendPending -= fileInfo.getSize();
                if (mSendPending < 0) {
                    mSendPending = 0;
                }

                // If we are sending this file, stop immediately so that we restart at new position.
                if (mSendingFile != null && mSendingFile.getFileIndex().equals(fileInfo.getIndex())) {
                    mSendingFile.cancel();
                    mSendingFile = null;
                }
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Bad file must resend ", fileInfo.getIndex(), " iq.offset=", iq.offset, " size=", fileInfo.getSize());
                }

            } else {
                fileInfo.setRemoteOffset(iq.offset);
            }

            long now = System.currentTimeMillis();
            if (mLastReport + 500 < now) {
                mLastReport = now;
                updateProgress();
            }
        // } else {
            // This is not a fatal error.  It occurs after an error is returned by the peer and we must re-send the file.
            // Log.e(LOG_TAG, "processOnPutFile iq=" + iq + " waitAckFiles does not contain file");
        }

        // Continue the migration process.
        if (mState != AccountMigrationService.State.STOPPED) {
            mExecutor.execute(this::processMigration);
        }
    }

    /**
     * Receive the device settings and save them in the migration area.
     *
     * @param iq the settings IQ
     */
    private void processSettings(@NonNull SettingsIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processSettings iq=" + iq);
        }

        mRequestTimeoutExpired = false;

        File settings = new File(mMigrationDirectory, "settings.iq");
        try (FileOutputStream outFile = new FileOutputStream(settings)) {
            byte[] packet = iq.serialize(mSerializerFactory);
            outFile.write(packet);

        } catch (Exception exception) {
            Utils.deleteFile(LOG_TAG, settings);

            sendPeerPacket(IQ_STAT_ERROR, sendError(iq.getRequestId(), ErrorCode.IO_ERROR));
            mReceiveErrorCount++;
            return;
        }

        if (mPendingIQRequests.remove(iq.getRequestId())) {
            mSettingsSent = true;
        }

        mSettingsReceived = true;
        if (!iq.hasPeerSettings) {
            iq = sendSettings();
            sendPeerPacket(IQ_STAT_SETTINGS, iq);
        }

        // Continue the migration process.
        if (mState != AccountMigrationService.State.STOPPED) {
            mExecutor.execute(this::processMigration);
        }
    }

    /**
     * Receive the account request.
     *
     * @param iq the IQ response.
     */
    private void processAccount(@NonNull AccountIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processAccount iq=" + iq);
        }
        Log.e(LOG_TAG, "processAccount iq=" + iq);

        mRequestTimeoutExpired = false;

        boolean isKnown = mPendingIQRequests.remove(iq.getRequestId());
        if (isKnown || iq.hasPeerAccount) {
            mAccountSent = true;
        }
        mAccountReceived = true;

        SecuredConfiguration twinlifeConfig = mConfigurationService.getSecuredConfiguration(MIGRATION_PREFIX + TWINLIFE_SECURED_CONFIGURATION_KEY);
        SecuredConfiguration accountConfig = mConfigurationService.getSecuredConfiguration(MIGRATION_PREFIX + ACCOUNT_SERVICE_SECURED_CONFIGURATION_KEY);
        twinlifeConfig.setData(iq.securedConfiguration);
        accountConfig.setData(iq.accountConfiguration);
        mConfigurationService.saveSecuredConfiguration(twinlifeConfig);
        mConfigurationService.saveSecuredConfiguration(accountConfig);

        if (!isKnown && mState == State.WAIT_ACCOUNT) {
            AccountIQ replyIQ = sendAccount(iq.getRequestId());
            Log.e(LOG_TAG, "Reply with account iq=" + replyIQ);
            if (replyIQ != null) {
                sendPeerPacket(IQ_STAT_ACCOUNT, replyIQ);
            }
        }

        // Continue the migration process if we have successfully sent our account and we received the peer account.
        if (mState == State.WAIT_ACCOUNT && mAccountSent && mAccountReceived) {
            setState(State.TERMINATE);
        }

        if (mState != State.STOPPED) {
            mExecutor.execute(this::processMigration);
        }
    }

    /**
     * Receive the terminate migration request.
     *
     * @param iq the IQ response.
     */
    private void processTerminateMigration(@NonNull TerminateMigrationIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processTerminateMigration iq=" + iq);
        }

        // Migration is canceled: cleanup and close the P2P connection.
        if (!iq.commit) {
            cancel();
            return;
        }

        // We can accept the terminate-migration IQ only when we reached the WAIT_TERMINATE phase
        // (we have received all files, database, settings, account and the peer also received all this information).
        if (!canTerminate()) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "onTerminateMigrationIQ received while in state ", getState());
            }
            return;
        }

        // The terminate phase is handled by an upper service that must delete the migration twincode.
        mAccountMigrationService.onTerminateMigration(iq.getRequestId(), mAccountMigrationId, true, iq.done);
    }

    /**
     * Receive the account request.
     *
     * @param iq the IQ response.
     */
    private void processShutdown(@NonNull ShutdownIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processShutdown iq=" + iq);
        }

        if (!iq.close) {
            ShutdownIQ shutdownIQ = new ShutdownIQ(IQ_SHUTDOWN_SERIALIZER, iq.getRequestId(), true);
            sendPeerPacket(IQ_STAT_SHUTDOWN, shutdownIQ);
        }

        // Invalidate the push variant and push notification token otherwise the server
        // can wakeup the other device after we switched.
        mTwinlifeImpl.getManagementServiceImpl().setPushNotificationToken("", "");
        setState(State.TERMINATED);

        if (iq.close) {
            closeConnection();
        }
    }

    /**
     * Receive the error packet.
     *
     * @param iq the error iq.
     */
    private void processError(@NonNull ErrorIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processError iq=" + iq);
        }

        setState(State.ERROR);
    }

    private void processMigration() {
        if (DEBUG) {
            Log.d(LOG_TAG, "processMigration state=" + mState);
        }

        while (mPendingIQRequests.size() < MAX_PENDING_REQUESTS) {
            switch (mState) {
                case LIST_FILES: {
                    ListFilesIQ listFilesIQ = sendListFiles();
                    if (listFilesIQ != null) {
                        sendIQRequest(IQ_STAT_LIST_FILES, listFilesIQ);
                    } else {
                        setState(State.SEND_FILES);
                    }
                    break;
                }

                case SEND_FILES: {
                    PutFileIQ putFileIQ = sendFileChunk();
                    if (putFileIQ != null) {
                        sendIQRequest(IQ_STAT_PUT_FILE, putFileIQ);
                    } else if (!mWaitListFiles.isEmpty()) {
                        // Log.e(LOG_TAG, "Wait files: " + mWaitListFiles);
                        return;
                    } else if (!mSendingFiles.isEmpty()) {
                        // Log.e(LOG_TAG, "Still having files to send: " + mSendingFiles);
                        return;
                    } else if (!mWaitAckFiles.isEmpty()) {
                        // Log.e(LOG_TAG, "Wait ack files: " + mWaitAckFiles);
                        return;
                    } else {
                        setState(State.SEND_SETTINGS);
                    }
                    break;
                }

                case SEND_SETTINGS: {
                    SettingsIQ settingsIQ = sendSettings();
                    sendIQRequest(IQ_STAT_SETTINGS, settingsIQ);

                    setState(State.SEND_DATABASE);

                    // Force another database sync to flush the WAL file before sending the database file in the next step.
                    mDatabase.syncDatabase();

                    FileInfoImpl fileInfo = new FileInfoImpl(mDatabaseFileIndex, "fake.db", mDatabaseFile.length(), mDatabaseFile.lastModified());
                    mListFiles.add(fileInfo);

                    // Log.e(LOG_TAG, "Adding database file " + mDatabaseFile.getPath());
                    ListFilesIQ listFilesIQ = sendListFiles();
                    if (listFilesIQ != null) {
                        sendIQRequest(IQ_STAT_LIST_FILES, listFilesIQ);
                    }
                    break;
                }

                case SEND_DATABASE: {
                    // Log.e(LOG_TAG, "Sending database");
                    PutFileIQ putFileIQ = sendFileChunk();
                    if (putFileIQ != null) {
                        sendIQRequest(IQ_STAT_PUT_FILE, putFileIQ);
                    } else {
                        setState(State.WAIT_FILES);
                        // Log.e(LOG_TAG, "Switch to wait files");
                    }
                    break;
                }

                case WAIT_FILES: {
                    if (!mWaitListFiles.isEmpty()) {
                        // Log.e(LOG_TAG, "Waiting database list: " + mWaitListFiles);
                        return;
                    } else if (!mSendingFiles.isEmpty()) {
                        // Log.e(LOG_TAG, "Waiting database send : " + mSendingFiles);
                        setState(State.SEND_DATABASE);
                    } else if (!mWaitAckFiles.isEmpty()) {
                        // Log.e(LOG_TAG, "Waiting database ack : " + mWaitAckFiles);
                        return;
                    } else if (!mReceivingFiles.isEmpty()) {
                        // Log.e(LOG_TAG, "Waiting database receive : " + mReceivingFiles);
                        return;
                    } else {
                        setState(State.SEND_ACCOUNT);
                        // Log.e(LOG_TAG, "Switch to account");
                    }
                    break;
                }

                case SEND_ACCOUNT: {
                    AccountIQ accountIQ = sendAccount(newRequestId());
                    Log.e(LOG_TAG, "Send account iq=" + accountIQ);
                    if (accountIQ != null) {
                        sendIQRequest(IQ_STAT_ACCOUNT, accountIQ);
                        setState(State.WAIT_ACCOUNT);
                        return;
                    } else {
                        if (Logger.ERROR) {
                            Logger.error(LOG_TAG, "SendAccount failed");
                        }
                    }
                    return;
                }

                case TERMINATE: {
                    return;
                }

                default:
                    return;
            }
        }
    }

    /**
     * Send a request IQ and update the request timeout.
     *
     * @param statType the packet stat counter to increment.
     * @param iq the IQ to send.
     */
    private void sendIQRequest(@NonNull StatType statType, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendIQRequest statType=" + statType + " iq=" + iq);
        }

        long requestId = iq.getRequestId();

        mPendingIQRequests.add(requestId);
        if (mRequestTimeout == null) {
            mRequestTimeout = mExecutor.schedule(this::activityTimeout, REQUEST_TIMEOUT, TimeUnit.SECONDS);
        }
        mRequestTimeoutExpired = false;

        sendPeerPacket(statType, iq);
    }

    /**
     * Request activity timeout handler fired to verify we are not stuck waiting for a response.
     */
    private void activityTimeout() {
        if (DEBUG) {
            Log.d(LOG_TAG, "activityTimeout");
        }

        mRequestTimeout = null;
        if (mPendingIQRequests.isEmpty()) {
            mRequestTimeoutExpired = false;
            return;
        }

        if (mRequestTimeoutExpired) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "activityTimeout timeout on pending requests!");
            }
            closeConnection();
            return;
        }

        mRequestTimeoutExpired = true;
        mRequestTimeout = mExecutor.schedule(this::activityTimeout, REQUEST_TIMEOUT, TimeUnit.SECONDS);
    }

    //
    // Send IQs.
    //

    /**
     * Build an IQ to send a list of files.
     *
     * @return the list IQ or null if there is no more files in the list.
     */
    @Nullable
    private ListFilesIQ sendListFiles() {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendListFiles");
        }

        int index = mListFiles.size();
        if (index == 0) {
            // Log.e(LOG_TAG, "There is no file");
            return null;
        }

        List<FileInfoImpl> list = new ArrayList<>();
        for (int i = 0; i < MAX_FILES_PER_IQ && index >= 1;) {
            index--;
            FileInfoImpl fileInfo = mListFiles.remove(index);
            if (fileInfo.getSize() <= mMaxFileSize) {
                list.add(fileInfo);
                mWaitListFiles.put(fileInfo.getIndex(), fileInfo);
                i++;
            }
        }

        updateProgress();
        long requestId = newRequestId();
        return new ListFilesIQ(IQ_LIST_FILES_SERIALIZER, requestId, list);
    }

    /**
     * Send the start packet IQ to start the account migration.
     *
     * If there is not enough space to receive the peer files, an error IQ is sent.
     *
     * @param requestId the request id.
     * @param maxFileSize the maximum file size.
     * @return the IQ for the start command.
     */
    @NonNull
    private BinaryPacketIQ sendStart(long requestId, long maxFileSize) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendStart requestId=" + requestId + " maxFileSize=" + maxFileSize);
        }

        if (mPeerInfo == null) {
            return sendError(requestId, ErrorCode.INTERNAL_ERROR);
        } else {
            long dbFs = mPeerInfo.getDatabaseAvailableSpace() - mPeerInfo.getDatabaseFileSize();
            long fileFs = mPeerInfo.getFilesystemAvailableSpace() - mPeerInfo.getTotalFileSize();

            if (fileFs < 0 || dbFs < 0) {
                return sendError(requestId, ErrorCode.NO_SPACE_LEFT);
            } else {
                return new StartIQ(IQ_START_SERIALIZER, requestId, maxFileSize);
            }
        }
    }

    /**
     * Send an error iq and prepare to abort the migration.
     *
     * The error code is recorded and propagated through the setProgress() operation in the Status interface.
     *
     * @param requestId the request id.
     * @param errorCode the error to send.
     * @return the error iq.
     */
    @NonNull
    private ErrorIQ sendError(long requestId, @NonNull ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendError requestId=" + requestId + " errorCode=" + errorCode);
        }

        mCurrentError = errorCode;
        return new ErrorIQ(IQ_ERROR_SERIALIZER, requestId, errorCode);
    }

    /**
     * Identify a file that must be sent and make a chunk IQ to send the content.
     *
     * @return the file chunk IQ to send or null if there is nothing to send.
     */
    @Nullable
    private PutFileIQ sendFileChunk() {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendFileChunk");
        }

        try {
            // Get the next file to send.
            if (mSendingFile == null) {
                int size = mSendingFiles.size();
                if (size == 0) {
                    // Log.e(LOG_TAG, "No file to send, we are done!");
                    return null;
                }

                Map.Entry<Integer, FileInfoImpl> fileInfoEntry = mSendingFiles.entrySet().iterator().next();

                mSendingFiles.remove(fileInfoEntry.getKey());

                FileInfoImpl sendFile = fileInfoEntry.getValue();
                mWaitAckFiles.put(sendFile.getIndex(), sendFile);

                File file;
                if (sendFile.getIndex() == DATABASE_FILE_INDEX
                        || sendFile.getIndex() == DATABASE_CIPHER_3_FILE_INDEX
                        || sendFile.getIndex() == DATABASE_CIPHER_4_FILE_INDEX) {
                    file = mDatabaseFile;
                } else {
                    file = new File(mRootDirectory, sendFile.getPath());
                }
                mSendingFile = new SendingFileInfo(file, sendFile);
                if (mSendingFile.isFinished()) {
                    long requestId = newRequestId();

                    byte[] sha256 = mSendingFile.getDigest();
                    mSendingFile = null;
                    return new PutFileIQ(IQ_PUT_FILE_SERIALIZER, requestId, sendFile.getIndex(), null, 0, sendFile.getSize(), 0, sha256);
                }
            }

            byte[] data = new byte[DATA_CHUNK_SIZE];
            long offset = mSendingFile.getPosition();
            int size = mSendingFile.read(data);
            int fileId = mSendingFile.getFileIndex();
            byte[] sha256;
            if (mSendingFile.isFinished() || size <= 0) {
                sha256 = mSendingFile.getDigest();
                mSendingFile = null;
                if (size < 0) {
                    size = 0;
                }
            } else {
                sha256 = null;
            }
            mSendPending += size;

            long requestId = newRequestId();
            return new PutFileIQ(IQ_PUT_FILE_SERIALIZER, requestId, fileId, data, 0, offset, size, sha256);

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Exception ", exception);
            }
            return null;
        }
    }

    /**
     * Collect the application settings and build the IQ to send them.
     *
     * @return the settings IQ to send or null if there is nothing to send.
     */
    @NonNull
    private SettingsIQ sendSettings() {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendSettings");
        }

        Map<UUID, ConfigIdentifier> list = ConfigIdentifier.getConfigs();
        Map<UUID, String> settings = new HashMap<>();

        for (Map.Entry<UUID, ConfigIdentifier> setting : list.entrySet()) {
            ConfigIdentifier config = setting.getValue();

            ConfigurationService.Configuration configuration = mConfigurationService.getConfiguration(config);

            // Send the configuration parameter only when it is defined.
            if (configuration.exists(config.getParameterName())) {
                settings.put(setting.getKey(), configuration.getStringConfig(config, ""));
            }
        }

        long requestId = newRequestId();
        return new SettingsIQ(IQ_SETTINGS_SERIALIZER, requestId, mSettingsReceived, settings);
    }

    /**
     * Build the account IQ to send the secure configuration.
     *
     * @return the account IQ or null if there is a problem getting the secure configuration.
     */
    @Nullable
    private AccountIQ sendAccount(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendAccount requestId=" + requestId);
        }

        // For protocol 2.0, we send the account using schema version 3 (peer is an Android which could be running twinme 17.3).
        // For others, use schema version 4 which is compatible with iOS.
        final int version = mPeerVersion != null && mPeerVersion.major == 2 && mPeerVersion.minor == 0 ? 3 : 4;
        final SecuredConfiguration secureConfig = mConfigurationService.getSecuredConfiguration(TWINLIFE_SECURED_CONFIGURATION_KEY);
        final byte[] secureData = secureConfig.getData();
        final byte[] accountData = mTwinlifeImpl.getAccountServiceImpl().exportForMigration(version);
        if (secureData == null || accountData == null) {
            return null;
        }

        return new AccountIQ(IQ_SWAP_ACCOUNT_SERIALIZER, requestId, secureData, accountData, mAccountReceived);
    }

    private void scanRootDirectory() {
        if (DEBUG) {
            Log.d(LOG_TAG, "scanRootDirectory");
        }

        scanDirectory(new File(mRootDirectory, Twinlife.CONVERSATIONS_DIR), Twinlife.CONVERSATIONS_DIR);
        scanDirectory(new File(mRootDirectory, Twinlife.LOCAL_IMAGES_DIR), Twinlife.LOCAL_IMAGES_DIR);
    }

    /**
     * Scan the directory recursively and identify the files that must be copied.
     *
     * @param directory the directory to scan.
     * @param basePath the relative base of the directory.
     */
    private void scanDirectory(@NonNull File directory, @NonNull String basePath) {
        if (DEBUG) {
            Log.d(LOG_TAG, "scanDirectory directory=" + directory + " basePath=" + basePath);
        }

        File[] list = directory.listFiles();
        if (list != null) {
            for (File file : list) {
                if (file.isDirectory()) {
                    scanDirectory(file, basePath + "/" + file.getName());
                } else {
                    long size = file.length();
                    long date = file.lastModified();

                    mFileIndex++;
                    FileInfoImpl fileInfo = new FileInfoImpl(mFileIndex,basePath + "/" + file.getName(), size, date);
                    mListFiles.add(fileInfo);
                }
            }
        }
    }

    private synchronized void setState(@NonNull State state) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setState state=" + state);
        }

        if (state == State.TERMINATED) {
            File hasMigration = new File(mRootDirectory, MIGRATION_DONE);

            try {
                hasMigration.createNewFile();
            } catch (IOException exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Cannot create migration stamp file");
                }
            }
        }

        if (mState != state) {
            mState = state;
            mLastReport = System.currentTimeMillis();
            updateProgress();
        }
    }

    private void updateProgress() {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateProgress state=" + mState);
        }

        long sent = mSent + mSendPending;
        long received = mReceived + mReceivePending;
        AccountMigrationService.Status status = new StatusImpl(mState, isConnected(), sent, mSendTotal - sent, received,
                mReceiveTotal - received, mReceiveErrorCount, mSendErrorCount, mCurrentError);
        mAccountMigrationService.setProgress(mAccountMigrationId, status);
    }

    /**
     * The application settings IQ is saved in a file, load it to get the settings.
     *
     * @param migrationDirectory the migration directory.
     * @return the application settings or null if we can't read the settings IQ.
     */
    @Nullable
    static Map<UUID, String> getMigrationSettings(@NonNull File migrationDirectory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getMigrationSettings migrationDirectory=" + migrationDirectory);
        }

        File settingsFile = new File(migrationDirectory, "settings.iq");
        try (FileInputStream fileStream = new FileInputStream(settingsFile)) {
            byte[] data = new byte[(int) settingsFile.length()];
            int size = fileStream.read(data);
            if (size != data.length) {
                return null;
            }

            ByteArrayInputStream inputStream = new ByteArrayInputStream(data);
            BinaryDecoder binaryDecoder = new BinaryDecoder(inputStream);
            UUID schemaId = binaryDecoder.readUUID();
            int schemaVersion = binaryDecoder.readInt();
            if (!SETTINGS_SCHEMA_ID.equals(schemaId) || schemaVersion != 1) {
                return null;
            }

            SettingsIQ iq = (SettingsIQ) IQ_SETTINGS_SERIALIZER.deserialize(new SerializerFactory() {
                @Nullable
                @Override
                public Serializer getObjectSerializer(@NonNull Object object) {
                    return null;
                }

                @Nullable
                @Override
                public Serializer getSerializer(@NonNull UUID schemaId, int schemaVersion) {
                    return null;
                }
            }, binaryDecoder);
            return iq.settings;

        } catch (Exception exception) {
            return null;
        }
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        return "AccountMigrationExecutor state=" + mState;
    }
}
