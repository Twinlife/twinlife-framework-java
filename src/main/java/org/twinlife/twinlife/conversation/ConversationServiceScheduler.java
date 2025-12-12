/*
 *  Copyright (c) 2019-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Houssem Temanni (Houssem.Temanni@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.JobService;
import org.twinlife.twinlife.PushNotificationContent;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.conversation.ConversationConnection.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Scheduler for running the conversation operations.
 * <p>
 * 1. Initialization
 * loadOperations() is called during startup to get the pending operations from the database.
 * prepareOperationsBeforeSchedule() is then called once we are connected to the Twinlife server.
 * It retrieves the conversation objects and prepares to schedule the operations.  It also handles
 * the askConversationSynchronizeWithConversation() during the Twinlife re-connection phase.
 * scheduleOperations() is then called to decide which conversation operations to execute.
 * <p>
 * 2. P2P connection
 * Before starting an outgoing P2P connection, the prepareNotificationWithConversation() method
 * builds the notification object that indicates what operations are queued.  The list of active
 * operations is updated.
 * <p>
 * When a P2P connection is started, the startOperationsWithConversation() must be called to
 * tell the scheduler there are some active P2P connection and operations.  If the P2P connection
 * is opened, the scheduler will let the conversation service execute the operations (if any).
 * The list of conversations with an opened P2P connection is updated.
 * <p>
 * When a P2P connection is closed (successfully, with error, with timeout, ...), the scheduler
 * must also be notified through the closeWithConversation() method.  It is then able to schedule
 * a new P2P connection if necessary.  The list of opened conversations is updated, the active
 * operations is also updated.
 * <p>
 * While the P2P connection is opened, the getFirstOperationWithConversationId() method is used
 * to get the first operation to execute.  The getOperationWithConversation() is then used when
 * the result IQ is processed and the operation is removed with removeOperation().
 * <p>
 * While the list of conversations with opened P2P connection is not empty, a job is scheduled
 * every 5 second to look at idle P2P connection and close them.
 */
public class ConversationServiceScheduler implements JobService.Observer {

    private static final String LOG_TAG = "ConversationServiceSch";
    private static final boolean INFO = BuildConfig.ENABLE_INFO_LOG;
    private static final boolean DEBUG = false;

    // Limit the number of P2P conversation that can be opened at a time.
    // If we are running in foreground, a higher limit is used.
    private static final int MAX_FOREGROUND_ACTIVE_CONVERSATIONS = 16;
    private static final int MAX_BACKGROUND_ACTIVE_CONVERSATIONS = 8;

    private static final int MAX_FOREGROUND_IDLE_TIME = 120 * 1000; // ms
    private static final int MAX_BACKGROUND_IDLE_TIME = 5 * 1000; // ms
    private static final int IDLE_FIRST_CHECK_DELAY = 10 * 1000; // First check on idle P2P connection after 10000ms.
    private static final int IDLE_CHECK_DELAY = 5 * 1000; // Then, other check for idle P2P connection each 5000 ms.
    private static final int DELAY_AFTER_ONLINE = 500; // ms to wait after we get online to schedule operations
    private static final int DELAY_BEFORE_SCHEDULE = 500; // ms to wait after scheduling again some operations
    private static final long EXPIRATION_DELAY = 14 * 24 * 3600 * 1000; // ms (14 days)

    private final ConversationServiceImpl mConversationService;
    private final JobService mJobService;
    private final ScheduledExecutorService mExecutor;
    private final ConversationServiceProvider mServiceProvider;
    private final List<OperationList> mActiveOperations;
    private final Set<ConversationConnection> mActiveConnections;
    private final TreeSet<OperationList> mWaitingOperations;
    private final Map<DatabaseIdentifier, OperationList> mConversationId2Operations;
    @Nullable
    private Map<ConversationImpl, List<Operation>> mDeferrableOperations;
    private long mNextIdleCheckTime;
    private JobService.Job mScheduleJob;
    private boolean mIsReschedulePending;
    private int mCurrentLimit;

    ConversationServiceScheduler(@NonNull TwinlifeImpl twinlifeImpl, @NonNull ConversationServiceImpl conversationService,
                                 @NonNull ConversationServiceProvider serviceProvider, @NonNull ScheduledExecutorService executor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "ConversationServiceScheduler");
        }

        mExecutor = executor;
        mServiceProvider = serviceProvider;
        mJobService = twinlifeImpl.getJobService();
        mConversationService = conversationService;
        mActiveOperations = new ArrayList<>();
        mActiveConnections = new HashSet<>();
        mWaitingOperations = new TreeSet<>();
        mConversationId2Operations = new HashMap<>();
        mNextIdleCheckTime = 0;
        mIsReschedulePending = false;
        mCurrentLimit = 0;

        // We want to be informed when we enter in background.
        mJobService.setObserver(this);
    }

    @Override
    public void onEnterForeground() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onEnterForeground");
        }
    }

    @Override
    public void onEnterBackground() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onEnterBackground");
        }

        boolean schedule = false;
        synchronized (this) {
            if (mDeferrableOperations == null) {

                return;
            }

            // Move the deferrable operations to their final operation queue.
            for (Map.Entry<ConversationImpl, List<Operation>> item : mDeferrableOperations.entrySet()) {
                final ConversationImpl conversationImpl = item.getKey();
                final DatabaseIdentifier conversationId = conversationImpl.getDatabaseId();
                final ConversationConnection connection = conversationImpl.getConnection();
                final boolean isActive = connection != null && mActiveConnections.contains(connection);

                OperationList operations = mConversationId2Operations.get(conversationId);
                if (operations == null) {
                    operations = new OperationList(conversationImpl);
                    mConversationId2Operations.put(conversationId, operations);
                } else {
                    // Temporarily remove the operations from the waiting list because adding an item may re-order the list.
                    if (!isActive) {
                        mWaitingOperations.remove(operations);
                    }
                }
                operations.addOperations(item.getValue());
                if (!isActive) {
                    mWaitingOperations.add(operations);
                }
                schedule = isActive || mActiveOperations.size() < MAX_BACKGROUND_ACTIVE_CONVERSATIONS;
            }
            mDeferrableOperations = null;
        }

        if (schedule) {
            scheduleOperations();
        }
    }

    @Override
    public void onBackgroundNetworkStart() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBackgroundNetworkStart");
        }
    }

    @Override
    public void onBackgroundNetworkStop() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBackgroundNetworkStop");
        }
    }

    @Override
    public void onActivePeers(int count) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onActivePeers count=" + count);
        }
    }

    public void loadOperations() {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadOperations");
        }

        mExecutor.execute(this::loadOperationsInternal);
    }

    /**
     * Load the pending operations from the database and setup the waiting operation queues.
     */
    private void loadOperationsInternal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadOperationsInternal");
        }

        List<Operation> operations = mServiceProvider.loadOperations();
        List<Operation> expiredOperations = null;
        long expireDeadline = System.currentTimeMillis() - EXPIRATION_DELAY;
        boolean hasOperations = false;
        synchronized (this) {
            for (Operation operation : operations) {
                final DatabaseIdentifier conversationId = operation.getConversationId();

                if (DEBUG) {
                    Log.d(LOG_TAG, "loadOperationsInternal operation " + operation.getType()
                            + " conversation " + conversationId);
                }

                // If the operation is queued for a very long time, update the associated descriptor and drop it.
                if (operation.getTimestamp() < expireDeadline) {
                    if (expiredOperations == null) {
                        expiredOperations = new ArrayList<>();
                    }
                    expiredOperations.add(operation);
                    continue;
                }

                OperationList lOperations = mConversationId2Operations.get(conversationId);
                if (lOperations == null) {
                    lOperations = new OperationList(conversationId);
                    mConversationId2Operations.put(conversationId, lOperations);
                }

                lOperations.addOperation(operation);
                hasOperations = true;
            }

            // Collect the waiting operations when all the operation queues are known and initialized (for the comparison).
            mWaitingOperations.addAll(mConversationId2Operations.values());
        }

        if (INFO) {
            Log.i(LOG_TAG, "loadOperationsInternal loaded " + operations.size() + " operations");
        }

        // If we are online and have some operation, prepare and schedule them.
        if (hasOperations && mConversationService.isTwinlifeOnline()) {
            prepareOperationsBeforeSchedule();
            scheduleOperations();
        }

        if (expiredOperations != null) {
            if (INFO) {
                Log.i(LOG_TAG, "loadOperationsInternal found " + expiredOperations.size() + " expired operations");
            }

            expireOperations(expiredOperations);
        }
    }

    /**
     * Prepare the operations by getting the conversation object.
     */
    private void prepareOperationsBeforeSchedule() {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareOperationsBeforeSchedule");
        }

        // Look at the pending operations which have no conversation object.
        List<OperationList> toUpdateList = null;
        synchronized (this) {
            for (OperationList operations : mWaitingOperations) {
                if (!operations.hasConversation()) {
                    if (toUpdateList == null) {
                        toUpdateList = new ArrayList<>();
                    }
                    toUpdateList.add(operations);
                }
            }
        }

        if (toUpdateList == null) {

            return;
        }

        if (INFO) {
            Log.i(LOG_TAG, "prepareOperationsBeforeSchedule prepare " + toUpdateList.size() + " operation queues");
        }

        // Find the conversation object and prepare it to start scheduling its operations.
        for (OperationList operations : toUpdateList) {
            Conversation conversation = mConversationService.getConversationWithId(operations.getConversationId());
            if (conversation instanceof ConversationImpl) {
                ConversationImpl conversationImpl = (ConversationImpl) conversation;

                operations.setConversation(conversationImpl);

                // Reset the retry delay to make sure we are trying to connect again.
                conversationImpl.resetDelay();

                // A twinlife::conversation::synchronize was asked in the past but it didn't complete.
                // do it immediately because we have the connection to Twinlife server and this may not
                // be the case if the P2P connection reaches the timeout.
                if (conversationImpl.needSynchronize()) {
                    mConversationService.askConversationSynchronize(conversationImpl);
                }
            } else {
                synchronized (this) {
                    mConversationId2Operations.remove(operations.getConversationId());
                    mWaitingOperations.remove(operations);
                }
                for (Operation operation : operations.iterator()) {
                    mServiceProvider.deleteOperation(operation);
                }
            }
        }
    }

    void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        // Invalidate a possible job that was created in the past.  If it is still enabled, the runJob()
        // could be executed immediately when that job has expired and because we are also online,
        // this would execute `scheduleOperations()` and we could try to start creating outgoing P2P
        // connections before the DELAY_AFTER_ONLINE below (we want to accept first incoming P2P).
        if (mScheduleJob != null) {
            mScheduleJob.cancel();
            mScheduleJob = null;
        }
        prepareOperationsBeforeSchedule();
        if (mJobService.isForeground()) {
            scheduleOperations();
        } else {
            // When we are in background and get the connection, there is a great chance that we are
            // awaken due to a push and we can receive an incoming P2P connection.
            // It's best to process them before trying to create outgoing P2P connections.  The goal is
            // to reduce the possible BUSY termination that occurs when the 2 devices open a P2P
            // connection at the same time.
            mJobService.schedule(this::scheduleOperations, DELAY_AFTER_ONLINE);
        }
    }

    private void expireOperation(long descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "expireOperation: descriptorId=" + descriptorId);
        }

        DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorWithId(descriptorId);
        if (descriptorImpl != null && descriptorImpl.getSentTimestamp() == 0) {
            // Mark the descriptor to show that the send operation failed.

            descriptorImpl.setSentTimestamp(-1);
            descriptorImpl.setReadTimestamp(-1);
            descriptorImpl.setReceivedTimestamp(-1);
            mServiceProvider.updateDescriptorImplTimestamps(descriptorImpl);
        }
    }

    private void expireOperations(@NonNull List<Operation> operations) {
        if (DEBUG) {
            Log.d(LOG_TAG, "expireOperations: operations=" + operations);
        }

        for (Operation operation : operations) {
            switch (operation.getType()) {
                case PUSH_FILE: {
                    PushFileOperation pushFileOperation = (PushFileOperation)operation;

                    expireOperation(pushFileOperation.getDescriptorId());
                    break;
                }

                case PUSH_OBJECT: {
                    PushObjectOperation pushObjectOperation = (PushObjectOperation)operation;

                    expireOperation(pushObjectOperation.getDescriptorId());
                    break;
                }

                case PUSH_TWINCODE: {
                    PushTwincodeOperation pushTwincodeOperation = (PushTwincodeOperation)operation;

                    expireOperation(pushTwincodeOperation.getDescriptorId());
                    break;
                }

                case PUSH_GEOLOCATION: {
                    PushGeolocationOperation pushGeolocationOperation = (PushGeolocationOperation)operation;

                    expireOperation(pushGeolocationOperation.getDescriptorId());
                    break;
                }

                case INVITE_GROUP:
                case WITHDRAW_INVITE_GROUP: {
                    GroupInviteOperation groupOperation = (GroupInviteOperation)operation;

                    if (groupOperation.getDescriptorId() != 0) {
                        expireOperation(groupOperation.getDescriptorId());
                    }
                    break;
                }

                case JOIN_GROUP:
                case LEAVE_GROUP:
                case UPDATE_DESCRIPTOR_TIMESTAMP:
                case UPDATE_GROUP_MEMBER:
                case RESET_CONVERSATION:
                case PUSH_TRANSIENT_OBJECT:
                case SYNCHRONIZE_CONVERSATION:
                case PUSH_COMMAND:
                case UPDATE_ANNOTATIONS:
                case INVOKE_JOIN_GROUP:
                case INVOKE_LEAVE_GROUP:
                case INVOKE_ADD_MEMBER:
                    break;
            }

            mServiceProvider.deleteOperation(operation);
        }
    }

    /**
     * Get the limit for the number of active P2P connections.
     *
     * @return the limit for active P2P connections.
     */
    private int getActiveConversationLimit() {

        int limit = mJobService.isForeground() ? MAX_FOREGROUND_ACTIVE_CONVERSATIONS : MAX_BACKGROUND_ACTIVE_CONVERSATIONS;

        if (INFO && limit != mCurrentLimit) {
            Log.i(LOG_TAG, "getActiveConversationLimit set to " + limit);
        }
        mCurrentLimit = limit;
        return limit;
    }

    /**
     * Schedule the operations associated with the conversation.
     * <p>
     * If the conversation has no pending operation, this operation does nothing.
     *
     * @param conversationImpl the conversation to schedule.
     */
    void scheduleConversationOperations(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "scheduleConversationOperations: conversationImpl=" + conversationImpl);
        }

        int limit = getActiveConversationLimit();
        boolean canExecute;
        boolean needReschedule = false;
        OperationList operations;
        synchronized (this) {
            operations = mConversationId2Operations.get(conversationImpl.getDatabaseId());
            if (operations == null || operations.isEmpty()) {
                return;
            }

            canExecute = conversationImpl.getConnection() != null;
            if (!canExecute && conversationImpl.hasPeer() && mConversationService.isTwinlifeOnline()) {
                canExecute = mActiveOperations.size() < limit
                    && (operations.getDeadline() == 0 || operations.getDeadline() < System.currentTimeMillis());

                // Check if we must trigger the scheduler for other pending operations.
                if (!canExecute && !mIsReschedulePending && !mWaitingOperations.isEmpty()) {
                    mIsReschedulePending = true;
                    needReschedule = true;
                }
            }
        }

        if (canExecute) {
            if (INFO) {
                Log.i(LOG_TAG, "Execute operations conversationId=" + conversationImpl.getId()
                        + " operations=" + operations.getCount());
            }
            mConversationService.executeOperation(conversationImpl);

        } else if (needReschedule) {
            mExecutor.schedule(this::scheduleOperations, 100, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Schedule the operations to be executed once we are online.
     */
    void scheduleOperations() {
        if (DEBUG) {
            Log.d(LOG_TAG, "scheduleOperations");
        }

        boolean isOnline = mConversationService.isTwinlifeOnline();
        int limit = getActiveConversationLimit();
        int scheduled = 0;
        int active;
        int pending;
        int nbConnections;

        // Look at the pending operations and get the closest deadline.
        long now = System.currentTimeMillis();
        long deadline = 0;
        long idleDelay = Long.MAX_VALUE;
        long nextDelay = Long.MAX_VALUE;
        synchronized (this) {
            mIsReschedulePending = false;

            Iterator<OperationList> pendingIterator = mWaitingOperations.iterator();

            // Run the operations for the conversation if the deadline has passed and the limit is not reached.
            nbConnections = mActiveConnections.size();
            active = mActiveOperations.size();
            pending = mWaitingOperations.size();
            if (isOnline) {
                for (OperationList operations : mActiveOperations) {
                    ConversationImpl conversationImpl = operations.getConversation();
                    Operation firstOperation = operations.getFirstOperation();
                    if (firstOperation != null && conversationImpl != null && firstOperation.canExecute(conversationImpl)) {
                        mConversationService.executeFirstOperation(conversationImpl, firstOperation);
                    }
                }
                while (active + scheduled < limit && pendingIterator.hasNext()) {
                    OperationList operations = pendingIterator.next();
                    ConversationImpl conversationImpl = operations.getConversation();

                    if (conversationImpl == null) {
                        break;
                    }

                    if (now < operations.getDeadline()) {
                        deadline = operations.getDeadline();
                        break;
                    }

                    if (conversationImpl.hasPeer()) {
                        mExecutor.schedule(() -> mConversationService.executeOperationInternal(conversationImpl),
                                scheduled * 50L, TimeUnit.MILLISECONDS);
                        scheduled++;
                    }
                }
            } else if (pendingIterator.hasNext()) {
                OperationList operations = pendingIterator.next();

                // When we are offline, get the next deadline so that we can schedule a job for it.
                // This allows the job scheduler to be aware that some operations are pending.
                deadline = operations.getDeadline();
                if (deadline == 0) {
                    deadline = now;
                }
            }

            if (mNextIdleCheckTime > 0) {
                idleDelay = mNextIdleCheckTime - now;
            }
            if (deadline != 0) {
                nextDelay = deadline - now;
            }
            if (idleDelay < nextDelay) {
                nextDelay = idleDelay;
            }
        }
        if (mScheduleJob != null) {
            mScheduleJob.cancel();
            mScheduleJob = null;
        }

        if (INFO) {
            if (nbConnections == 0 && active == 0 && scheduled == 0 && pending == 0) {
                Log.i(LOG_TAG, "no operations in queue deadline=" + deadline + " nextDelay=" + nextDelay);
            } else {
                Log.i(LOG_TAG, "scheduleOperations connections=" + nbConnections + " active=" + active + " scheduled=" + scheduled
                        + " pending=" + pending + " limit=" + limit + " deadline=" + deadline + " nextDelay=" + nextDelay + " isOnline=" + isOnline);
            }
        }
        if (nextDelay != Long.MAX_VALUE) {
            mScheduleJob = mJobService.scheduleIn("Conversation scheduler", this::runJob, nextDelay,
                    JobService.Priority.MESSAGE);
        }
    }

    private void runJob() {
        if (DEBUG) {
            Log.d(LOG_TAG, "runJob");
        }

        mScheduleJob = null;
        processIdleConnections();
        scheduleOperations();
    }

    /**
     * Get the operation with the given request ID.
     *
     * @param conversationId the conversation id.
     * @param requestId the request id.
     * @return the operation or null if the operation was not found.
     */
    @Nullable
    Operation getOperation(@NonNull DatabaseIdentifier conversationId, long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getOperation: conversationId=" + conversationId + " requestId=" + requestId);
        }

        synchronized (this) {
            OperationList operations = mConversationId2Operations.get(conversationId);
            if (operations != null) {
                Operation result = operations.getOperation(requestId);
                if (result != null) {
                    if (INFO) {
                        Log.i(LOG_TAG, "getOperation " + conversationId
                                + " requestId=" + requestId + " operation=" + result.getType());
                    }
                    return result;
                }
            }
        }
        if (INFO) {
            Log.i(LOG_TAG, "getOperation " + conversationId + " requestId=" + requestId + " operation not found");
        }
        return null;
    }

    /**
     * Get the first pending operation for the conversation.
     *
     * @param conversationImpl the conversation object.
     * @return the first operation or null.
     */
    @Nullable
    Operation getFirstOperation(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFirstOperation: conversationImpl=" + conversationImpl);
        }

        Operation operation;
        synchronized (this) {
            OperationList operations = mConversationId2Operations.get(conversationImpl.getDatabaseId());

            if (operations == null) {

                return null;
            }
            operation = operations.getFirstOperation();
            if (operation == null) {

                return null;
            }
        }

        if (operation.getRequestId() != Operation.NO_REQUEST_ID) {

            return null;
        }

        if (INFO) {
            Log.i(LOG_TAG, "getFirstOperation " + conversationImpl.getDatabaseId() + " operation=" + operation.getType());
        }
        return operation;
    }

    /**
     * Get the first active operation for the conversation.
     * <p>
     * Similar to getFirstOperation() but used exclusively to handle the ErrorIQ: we must get the first
     * operation which has a valid request id.
     *
     * @param conversationImpl the conversation object.
     * @return the first active operation or null.
     */
    @Nullable
    Operation getFirstActiveOperation(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFirstActiveOperation: conversationImpl=" + conversationImpl);
        }

        Operation operation;
        synchronized (this) {
            OperationList operations = mConversationId2Operations.get(conversationImpl.getDatabaseId());

            if (operations == null) {

                return null;
            }
            operation = operations.getFirstOperation();
            if (operation == null) {

                return null;
            }
        }

        if (operation.getRequestId() == Operation.NO_REQUEST_ID) {

            return null;
        }

        if (INFO) {
            Log.i(LOG_TAG, "getFirstActiveOperation " + conversationImpl.getDatabaseId() + " operation=" + operation.getType());
        }
        return operation;
    }

    /**
     * Before opening the P2P connection, get the notification according to the pending operations.
     *
     * @param conversationImpl the conversation object.
     * @return the notification content or null if there is no pending operation.
     */
    @Nullable
    PushNotificationContent prepareNotification(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareNotification: conversationId=" + conversationImpl.getId());
        }

        PushNotificationContent notificationContent;
        OperationList operations;
        synchronized (this) {
            operations = mConversationId2Operations.get(conversationImpl.getDatabaseId());
            if (operations == null) {
                if (INFO) {
                    Log.i(LOG_TAG, "prepareNotification: conversationId=" + conversationImpl.getId() + " no operation");
                }

                return null;
            }

            // Move the operations from the waiting list to the active list.
            mWaitingOperations.remove(operations);
            if (operations.isEmpty()) {
                mConversationId2Operations.remove(conversationImpl.getDatabaseId());
                mActiveOperations.remove(operations);
                if (INFO) {
                    Log.i(LOG_TAG, "prepareNotification: conversationId=" + conversationImpl.getId() + " empty list");
                }

                return null;
            }

            if (!mActiveOperations.contains(operations)) {
                operations.clearDeadline();
                mActiveOperations.add(operations);
            }

            notificationContent = operations.getNotificationContent();
        }

        if (INFO) {
            Log.i(LOG_TAG, "prepareNotification: conversationId=" + conversationImpl.getId()
                    + " operations=" + notificationContent.operationCount);
        }
        return notificationContent;
    }

    /**
     * Notify the scheduler that a connection is started or opened for the given conversation.
     *
     * @param connection the connection object.
     * @param state the conversation state.
     * @return the first operation to execute if the connection is now ready.
     */
    @Nullable
    Operation startOperation(@NonNull ConversationConnection connection, @NonNull State state) {
        if (DEBUG) {
            Log.d(LOG_TAG, "startOperation: connection=" + connection + " state=" + state);
        }

        boolean needScheduleUpdate = false;
        final ConversationImpl conversationImpl = connection.getConversation();
        final DatabaseIdentifier conversationId = conversationImpl.getDatabaseId();
        Operation operation = null;
        synchronized (this) {
            // Operations for the conversation are now active: move them from waiting to active list.
            OperationList operations = mConversationId2Operations.get(conversationId);
            if (operations != null) {
                if (!mActiveOperations.contains(operations)) {
                    mActiveOperations.add(operations);
                }
                mWaitingOperations.remove(operations);
                operations.clearDeadline();
            }

            // Keep track of opened conversations even if they have no operations.
            if (state == State.OPEN) {
                if (mDeferrableOperations != null) {
                    final List<Operation> deferredList = mDeferrableOperations.get(conversationImpl);
                    if (deferredList != null) {
                        // We have some deferred operations, move them to the active list of operations.
                        mDeferrableOperations.remove(conversationImpl);
                        if (mDeferrableOperations.isEmpty()) {
                            mDeferrableOperations = null;
                        }
                        if (operations == null) {
                            operations = new OperationList(conversationImpl);
                            mConversationId2Operations.put(conversationId, operations);
                        }
                        operations.addOperations(deferredList);
                    }
                }
                connection.touch();
                if (operations != null && !operations.isEmpty()) {
                    operation = operations.getFirstOperation();
                    if (operation.getRequestId() != Operation.NO_REQUEST_ID) {
                        operation = null;
                    }
                }
            }
            mActiveConnections.add(connection);

            // P2P connection is opened, setup the IDLE timer for the first opened connection.
            if (state == State.OPEN && mNextIdleCheckTime == 0) {
                mNextIdleCheckTime = System.currentTimeMillis() + IDLE_FIRST_CHECK_DELAY;
                if (!mIsReschedulePending) {
                    needScheduleUpdate = true;
                    mIsReschedulePending = true;
                }
            }
        }
        if (needScheduleUpdate) {
            mExecutor.schedule(this::scheduleOperations, DELAY_BEFORE_SCHEDULE, TimeUnit.MILLISECONDS);
        }
        if (state == State.OPEN) {
            if (INFO) {
                Log.i(LOG_TAG, "startOperation conversationId=" + conversationId + " operation=" + operation);
            }
            conversationImpl.resetDelay();
        }
        return operation;
    }

    /**
     * Notify the scheduler that the connection for the conversation has closed.
     *
     * @param connection the connection object being closed.
     * @param retryImmediately when true, try to re-connect as soon as possible.
     * @return true if a synchronize peer notification is required.
     */
    boolean close(@NonNull ConversationConnection connection, boolean retryImmediately) {
        if (DEBUG) {
            Log.d(LOG_TAG, "close: connection=" + connection);
        }

        OperationList operations;
        final ConversationImpl conversationImpl = connection.getConversation();
        final DatabaseIdentifier conversationId = conversationImpl.getDatabaseId();
        boolean synchronizePeerNotification = false;
        boolean needReschedule = false;
        synchronized (this) {
            if (!mActiveConnections.remove(connection) && INFO) {
                Log.i(LOG_TAG, "The conversation " + conversationId + " was not active");
            }

            operations = mConversationId2Operations.get(conversationId);
            if (operations != null) {
                mActiveOperations.remove(operations);
                mWaitingOperations.remove(operations);

                // Reset the operations so that we can restart them for the next P2P connection.
                synchronizePeerNotification = operations.resetOperations();

                // Put back the operations in the waiting queue with a new deadline.
                if (!operations.isEmpty()) {
                    final long delay;
                    if (retryImmediately) {
                        synchronizePeerNotification = false;
                        delay = 500;
                    } else {
                        delay = conversationImpl.getDelay();
                    }
                    operations.setDeadline(System.currentTimeMillis() + delay);
                    mWaitingOperations.add(operations);
                } else {
                    mConversationId2Operations.remove(conversationId);
                    operations = null;
                }
            }

            // Check if we must trigger the scheduler for other pending operations.
            if (!mIsReschedulePending && !mWaitingOperations.isEmpty()) {
                mIsReschedulePending = true;
                needReschedule = true;
            }

            // No active conversation, clear immediately the idle check delay to avoid a spurious schedule
            // created by scheduleOperations() for the IDLE detection check.
            if (mActiveConnections.isEmpty()) {
                mNextIdleCheckTime = 0;
            }
        }

        if (INFO) {
            Log.i(LOG_TAG, "close: conversationId=" + conversationId + " pending operations "
                    + (operations == null ? 0 : operations.getCount()) + " needReschedule=" + needReschedule
                    + " delay=" + conversationImpl.getDelay());
        }
        if (needReschedule) {
            mExecutor.schedule(this::scheduleOperations, DELAY_BEFORE_SCHEDULE, TimeUnit.MILLISECONDS);
        }
        return synchronizePeerNotification;
    }

    /**
     * Handle idle connections and close them.
     */
    private void processIdleConnections() {
        if (DEBUG) {
            Log.d(LOG_TAG, "processIdleConnections");
        }

        // Use a longer idle time if we are in foreground.
        long idleDelay = mJobService.isForeground() ? MAX_FOREGROUND_IDLE_TIME : MAX_BACKGROUND_IDLE_TIME;

        // Upon completion, holds a list of P2P connection Id that must be closed.
        boolean hasActive = false;
        List<ConversationConnection> toClose = null;
        synchronized (this) {

            // Look at active conversations to check if the conversation is idle.
            for (ConversationConnection connection : mActiveConnections) {

                // If there are pending operations, give move time for the idle delay and data transfer.
                OperationList operations = mConversationId2Operations.get(connection.getDatabaseId());
                long checkDelay = !connection.isTransferingFile() && (operations == null || operations.getCount() == 0) ? idleDelay : 2 * idleDelay;

                // Give 5s more if the peer has some pending operations.
                int deviceState = connection.getPeerDeviceState();
                if ((deviceState & ConversationConnection.DEVICE_STATE_HAS_OPERATIONS) != 0) {
                    checkDelay += MAX_BACKGROUND_IDLE_TIME;
                }

                if (connection.idleTime() > checkDelay) {
                    if (toClose == null) {
                        toClose = new ArrayList<>();
                    }
                    toClose.add(connection);
                } else {
                    hasActive = true;
                }
            }

            // Check in 5 seconds if the situation has changed (foreground and idle state).
            if (hasActive) {
                mNextIdleCheckTime = System.currentTimeMillis() + IDLE_CHECK_DELAY;
            } else {
                mNextIdleCheckTime = 0;
            }
        }

        // Close the P2P conversation which are idle.
        if (toClose != null) {
            for (ConversationConnection connection : toClose) {
                mConversationService.closeConnection(connection, TerminateReason.SUCCESS);
            }
        }

        if (INFO && !hasActive) {
            Log.i(LOG_TAG, "processIdleConnections stopping because there is no active operations");
        }
    }

    /**
     * Add the operation for the conversation and schedule its execution.
     *
     * @param conversationImpl the conversation object.
     * @param operation the operation to add.
     * @param delay the initial delay to wait before starting the operation
     */
    void addOperationAndSchedule(@NonNull ConversationImpl conversationImpl, @NonNull Operation operation, boolean schedule, long delay) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addOperation: conversationImpl=" + conversationImpl + " operation=" + operation);
        }

        OperationList operations;
        final boolean canExecute;
        final DatabaseIdentifier conversationId = conversationImpl.getDatabaseId();
        final long now = System.currentTimeMillis();
        synchronized (this) {
            final boolean isActive;
            operations = mConversationId2Operations.get(conversationId);
            if (operations == null) {
                operations = new OperationList(conversationImpl);
                mConversationId2Operations.put(conversationId, operations);

                // If we are connected, we can proceed with execution of this first operation.
                canExecute = operation.canExecute(conversationImpl);
                isActive = false;

            } else {
                isActive = mWaitingOperations.contains(operations);
                if (isActive) {
                    // We can execute if we are connected and this is a first operation.
                    canExecute = operations.isEmpty() && operation.canExecute(conversationImpl);

                } else {
                    // Temporarily remove the operations from the waiting list because adding an item may re-order the list.
                    mWaitingOperations.remove(operations);
                    canExecute = false;
                }
            }

            // When a delay is defined, we don't want to trigger an execution of the operations for that conversation immediately,
            // and we have to wait that delay before trying to connect.  This is used when a SYNCHRONIZE operation is added when we
            // received a conversation::synchronize invocation.  We may also receive after that invocation an incoming P2P
            // for the same conversation and if we try to execute the SYNCHRONIZE, we will create an outgoing P2P before
            // trying to accept the incoming P2P: it will be rejected with BUSY.  There is no way to be aware whether such
            // incoming P2P is pending or not and the small delay is here to avoid that.
            if (delay > 0 && operations.getDeadline() <= now) {
                operations.setDeadline(now + delay);
            }
            operations.addOperation(operation);
            if (!isActive) {
                mWaitingOperations.add(operations);
            }

            schedule = schedule && (isActive || mActiveOperations.size() < MAX_FOREGROUND_ACTIVE_CONVERSATIONS);
        }

        if (canExecute) {
            if (INFO) {
                Log.i(LOG_TAG, "Add operation execute conversationId=" + conversationId
                        + " operations=" + operations.getCount() + " first=" + operation.getType());
            }
            mConversationService.executeFirstOperation(conversationImpl, operation);

        } else if (schedule) {
            if (INFO) {
                Log.i(LOG_TAG, "addOperation " + conversationId + " count " + operations.getCount());
            }
            scheduleConversationOperations(conversationImpl);
        }
    }

    /**
     * Add the operation for the conversation and schedule its execution.
     *
     * @param conversationImpl the conversation object.
     * @param operation the operation to add.
     * @param delay the initial delay to wait before starting the operation
     */
    void addOperation(@NonNull ConversationImpl conversationImpl, @NonNull Operation operation, long delay) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addOperation: conversationImpl=" + conversationImpl + " operation=" + operation);
        }

        addOperationAndSchedule(conversationImpl, operation, true, delay);
    }

    /**
     * Add the operation for the conversation but don't schedule its execution immediately unless we are connected.
     *
     * @param conversationImpl the conversation object.
     * @param operation the operation to add.
     */
    void addDeferrableOperation(@NonNull ConversationImpl conversationImpl, @NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addDeferrableOperation: conversationImpl=" + conversationImpl + " operation=" + operation);
        }

        // If the conversation is opened, add the operation immediately.
        if (conversationImpl.getState() == State.OPEN) {
            addOperation(conversationImpl, operation, 0);
            return;
        }

        synchronized (this) {
            if (mDeferrableOperations == null) {
                mDeferrableOperations = new HashMap<>();
            }
            List<Operation> operations = mDeferrableOperations.get(conversationImpl);
            if (operations == null) {
                operations = new ArrayList<>();
                mDeferrableOperations.put(conversationImpl, operations);
            }
            operations.add(operation);
        }
    }

    /**
     * Remove the operation.
     *
     * @param operation operation to remove.
     */
    void removeOperation(@NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeOperation: operation=" + operation);
        }

        mServiceProvider.deleteOperation(operation);

        final DatabaseIdentifier conversationId = operation.getConversationId();
        synchronized (this) {
            final OperationList operations = mConversationId2Operations.get(conversationId);
            if (operations != null) {
                // We must remove the list of operations from the waiting TreeSet when we modify it.
                final boolean removed = mWaitingOperations.remove(operations);
                operations.removeOperation(operation);

                // Remove the list of operations when it becomes empty and it is in the waiting queue.
                if (operations.isEmpty() && !mActiveOperations.contains(operations)) {
                    mConversationId2Operations.remove(conversationId);
                } else if (removed) {
                    mWaitingOperations.add(operations);
                }
            }
        }
    }

    /**
     * Returns YES if the conversation has pending operations.
     *
     * @param conversationImpl the conversation.
     * @return true if we have pending operations.
     */
    boolean hasOperations(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasOperations: conversationImpl=" + conversationImpl.getDatabaseId());
        }

        synchronized (this) {
            final OperationList operations = mConversationId2Operations.get(conversationImpl.getDatabaseId());
            return operations != null && !operations.isEmpty();
        }
    }

    /**
     * Remove the operation and schedule the next execution if necessary for the associated conversation.
     *
     * @param operation operation to remove.
     * @param connection the conversation object.
     */
    void finishOperation(@Nullable Operation operation, @NonNull ConversationConnection connection) {
        if (DEBUG) {
            Log.d(LOG_TAG, "finishOperation: operation=" + operation);
        }

        if (operation != null) {
            mServiceProvider.deleteOperation(operation);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        DatabaseIdentifier conversationId = conversationImpl.getDatabaseId();
        Operation nextOperation = null;
        final boolean canExecute;
        synchronized (this) {
            final OperationList operations = mConversationId2Operations.get(conversationId);
            if (operations != null) {
                // We must remove the list of operations from the waiting TreeSet when we modify it.
                final boolean removed = mWaitingOperations.remove(operations);
                if (operation != null) {
                    operations.removeOperation(operation);
                }
                nextOperation = operations.getFirstOperation();
                if (nextOperation == null) {

                    // Remove the list of operations when it becomes empty and it is in the waiting queue.
                    if (!mActiveOperations.contains(operations)) {
                        mConversationId2Operations.remove(conversationId);
                    }
                } else if (removed) {
                    mWaitingOperations.add(operations);
                }
            }
            canExecute = nextOperation != null && nextOperation.canExecute(conversationImpl);
        }

        if (canExecute) {
            mConversationService.executeNextOperation(connection, nextOperation);

        } else {
            int deviceState = connection.getPeerDeviceState();

            // The device state is not valid: we use the default idle detection mechanism.
            if ((deviceState & ConversationConnection.DEVICE_STATE_VALID) == 0) {
                return;
            }

            // The peer has some operations: keep the P2P connection opened.
            if ((deviceState & (ConversationConnection.DEVICE_STATE_HAS_OPERATIONS | ConversationConnection.DEVICE_STATE_SYNCHRONIZE_KEYS)) != 0) {
                return;
            }

            // We and the peer are in foreground: keep the P2P connection opened.
            if ((deviceState & ConversationConnection.DEVICE_STATE_FOREGROUND) != 0 && mJobService.isForeground()) {
                return;
            }

            // We can terminate the P2P since there is no operation on both sides.
            mConversationService.closeConnection(connection, TerminateReason.SUCCESS);
        }
    }

    /**
     * Remove the operation and schedule the next execution if necessary for the associated conversation.
     *
     * @param operation operation to remove.
     * @param conversationImpl the conversation object.
     */
    void finishInvokeOperation(@Nullable Operation operation, @NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "finishOperation: operation=" + operation);
        }

        if (operation != null) {
            mServiceProvider.deleteOperation(operation);
        }

        final DatabaseIdentifier conversationId = conversationImpl.getDatabaseId();
        Operation nextOperation = null;
        final boolean canExecute;
        synchronized (this) {
            final OperationList operations = mConversationId2Operations.get(conversationId);
            if (operations != null) {
                // We must remove the list of operations from the waiting TreeSet when we modify it.
                final boolean removed = mWaitingOperations.remove(operations);
                if (operation != null) {
                    operations.removeOperation(operation);
                }
                nextOperation = operations.getFirstOperation();
                if (nextOperation == null) {

                    // Remove the list of operations when it becomes empty and it is in the waiting queue.
                    if (!mActiveOperations.contains(operations)) {
                        mConversationId2Operations.remove(conversationId);
                    }
                } else if (removed) {
                    mWaitingOperations.add(operations);
                }
            }
            canExecute = nextOperation != null && nextOperation.canExecute(conversationImpl);
        }

        if (canExecute) {
            mConversationService.executeFirstOperation(conversationImpl, nextOperation);
        }
    }

    /**
     * Delete the conversation.
     *
     * @param conversationImpl the conversation object.
     */
    void deleteConversation(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteConversation");
        }

        removeOperations(conversationImpl, null);
        final ConversationConnection connection = conversationImpl.getConnection();
        if (connection != null) {
            synchronized (this) {
                mActiveConnections.remove(connection);
            }
        }
    }

    /**
     * Remove all operations associated with the conversation.  When a map of descriptors indexed by twincodes is
     * passed, it indicates a set of descriptors that have been removed and we must remove all operations that
     * are using such past descriptor.
     *
     * @param conversationImpl the conversation object.
     * @param deletedOperations optional list of operation ids that have been deleted.
     */
    void removeOperations(@NonNull ConversationImpl conversationImpl, @Nullable List<Long> deletedOperations) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeOperations");
        }

        final DatabaseIdentifier id = conversationImpl.getDatabaseId();
        synchronized (this) {
            final OperationList operations = mConversationId2Operations.get(id);
            if (operations != null) {
                if (deletedOperations != null) {
                    operations.removeOperations(deletedOperations);
                    if (operations.isEmpty()) {
                        mConversationId2Operations.remove(id);
                        mActiveOperations.remove(operations);
                        mWaitingOperations.remove(operations);
                    }
                } else {
                    mConversationId2Operations.remove(id);
                    mActiveOperations.remove(operations);
                    mWaitingOperations.remove(operations);
                }
            }
        }
    }

    /**
     * Remove all operations from all conversations (called when we are doing a sign out).
     */
    void removeAllOperations() {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeAllOperations");
        }

        synchronized (this) {
            mActiveConnections.clear();
            mActiveOperations.clear();
            mWaitingOperations.clear();
            mConversationId2Operations.clear();
        }
    }
}
