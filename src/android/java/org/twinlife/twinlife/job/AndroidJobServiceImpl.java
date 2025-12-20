/*
 *  Copyright (c) 2018-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.job;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.annotation.SuppressLint;
import android.app.Application;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DeviceInfo;
import org.twinlife.twinlife.JobService;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.twinlife.ApplicationState;
import org.twinlife.twinlife.TwinlifeContextImpl;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.twinlife.twinlife.JobService.Priority.MESSAGE;

public abstract class AndroidJobServiceImpl extends TwinlifeContext.DefaultObserver implements JobService {
    private static final String LOG_TAG = "AndroidJobServiceImpl";
    private static final boolean DEBUG = false;
    private static final boolean INFO = BuildConfig.ENABLE_INFO_LOG;
    private static final int JOB_FOREGROUND_DELAY = 10; // ms
    private static final int JOB_UPDATE_DELAY = 10000;  // ms
    private static final int JOB_REPORT_DELAY = 5000;   // ms
    private static final int FOREGROUND_RELEASE_DELAY = 1000; // ms
    private static final int STOP_FOREGROUND_DISCONNECT_DELAY = 500; // ms
    private static final int BACKGROUND_DISCONNECT_DELAY = 10_000; // ms
    private static final long MIN_FOREGROUND_TIME = 4000; // ms

    protected static final int ALARM_MIN_DELAY = 10 * 60 * 1000;       // 10mn
    protected static final int ALARM_ACTIVE_DELAY = 60 * 60 * 1000;   // 60mn (Firebase not available)
    private static final int ALARM_PASSIVE_DELAY = 4 * 3600 * 1000; // 4h (Firebase available)

    private static final int ALARM_SERVICE_BACKGROUND_DELAY = 25 * 1000; // 25s (should be aligned with the PeerService timeout).
    private static final int ALARM_FIRST_CHECK_DELAY = 4000; // 4s.
    private static final int ALARM_CHECK_DELAY = 1500; // 1.5s, check if we can stop the alarm job before the 25s.

    protected static final int JOB_RECONNECT_ID = 123; // Job id used to reconnect as soon as we can.
    protected static final int JOB_CONNECT_ID = 1234;  // Job id to connect after a long delay.

    private ScheduledFuture<?> mDisconnectTask = null;

    class JobImpl implements Job, Runnable {
        final Runnable mWork;
        final Priority mPriority;
        final String mName;
        final long mDeadline;
        ScheduledFuture<?> mScheduled;

        JobImpl(String name, Runnable work, Priority priority) {
            mName = name;
            mWork = work;
            mPriority = priority;
            if (priority == Priority.UPDATE) {
                mDeadline = System.currentTimeMillis() + JOB_UPDATE_DELAY;
            } else {
                mDeadline = System.currentTimeMillis();
            }
        }

        JobImpl(String name, Runnable work, Priority priority, long deadline) {
            mName = name;
            mWork = work;
            mPriority = priority;
            mDeadline = deadline;
        }

        /**
         * Get a deadline time for this job.
         *
         * @return the job deadline time.
         */
        @Override
        public long getDeadline() {

            return mDeadline;
        }

        /**
         * Cancel the job.
         */
        @Override
        public void cancel() {

            terminateJob(this);
        }

        /**
         * Run the job and remove it from the list when it is finished.
         */
        @Override
        public void run() {
            if (INFO) {
                Log.i(LOG_TAG, "Running job " + mName + " state " + mApplicationState);
            }

            try {
                mWork.run();
                if (INFO) {
                    Log.i(LOG_TAG, "Job " + mName + " terminated state " + mApplicationState);
                }

            } catch (Exception ex) {
                Log.e(LOG_TAG, "Exception " + ex + " when running job " + mName);
            }

            terminateJob(this);
        }

        @NonNull
        public String toString() {

            return "Job " + mName + " deadline=" + mDeadline + " priority=" + mPriority;
        }
    }

    static final class NetworkLockImpl implements NetworkLock {
        private final AndroidJobServiceImpl mJobService;
        private boolean mIsReleased = false;

        NetworkLockImpl(final AndroidJobServiceImpl jobService) {
            mJobService = jobService;
        }

        @Override
        public void activePeers(int count) {

            mJobService.activePeers(count);
        }

        @Override
        public void release() {

            if (!mIsReleased) {
                mIsReleased = true;
                mJobService.releaseNetworkLock();
            }
        }
    }

    static final class ProcessingLockImpl implements ProcessingLock {
        private final AndroidJobServiceImpl mJobService;
        private boolean mIsReleased = false;

        ProcessingLockImpl(final AndroidJobServiceImpl jobService) {
            mJobService = jobService;
        }

        @Override
        public void release() {

            if (!mIsReleased) {
                mIsReleased = true;
                mJobService.releaseProcessingLock();
            }
        }
    }

    static final class InteractiveLockImpl implements InteractiveLock {
        private final AndroidJobServiceImpl mJobService;
        private boolean mIsReleased = false;

        InteractiveLockImpl(final AndroidJobServiceImpl jobService) {
            mJobService = jobService;
        }

        @Override
        public void release() {

            if (!mIsReleased) {
                mIsReleased = true;
                mJobService.releaseInteractiveLock();
            }
        }
    }

    static final class VoIPLockImpl implements VoIPLock {
        private final AndroidJobServiceImpl mJobService;
        private boolean mIsReleased = false;

        VoIPLockImpl(final AndroidJobServiceImpl jobService) {
            mJobService = jobService;
        }

        @Override
        public void release() {

            if (!mIsReleased) {
                mIsReleased = true;
                mJobService.releaseVoIPLock();
            }
        }
    }

    class ForegroundServiceJobImpl implements Job, Runnable {
        final Runnable mFinish;
        final long mDeadline;
        ScheduledFuture<?> mScheduled;

        ForegroundServiceJobImpl(Runnable work, long deadline) {
            mFinish = work;
            mDeadline = deadline;
        }

        /**
         * Get a deadline time for this job.
         *
         * @return the job deadline time.
         */
        @Override
        public long getDeadline() {

            return mDeadline;
        }

        /**
         * Cancel the job.
         */
        @Override
        public void cancel() {

            if (mScheduled != null) {
                mScheduled.cancel(false);
                mScheduled = null;
            }
            cancelForegroundService();
        }

        /**
         * Run the job and remove it from the list when it is finished.
         */
        @Override
        public void run() {
            if (INFO) {
                Log.i(LOG_TAG, "Running job ForegroundServiceJobImpl state " + mApplicationState);
            }

            try {
                if (mScheduled != null) {
                    mScheduled.cancel(false);
                    mScheduled = null;
                }
                mFinish.run();
                if (INFO) {
                    Log.i(LOG_TAG, "Job ForegroundServiceJobImpl state " + mApplicationState);
                }

            } catch (Exception ex) {
                Log.e(LOG_TAG, "Exception " + ex + " when running job ForegroundServiceJobImpl");
            }
        }

        @NonNull
        public String toString() {

            return "Job ForegroundServiceJob ";
        }
    }

    private final LifecycleCallbacksImpl mLifecycleCallbacks;
    protected final Application mApplication;
    protected final ScheduledExecutorService mExecutor;
    private final List<JobImpl> mJobList;
    private final CopyOnWriteArrayList<Observer> mObservers = new CopyOnWriteArrayList<>();
    private ForegroundServiceJobImpl mForegroundServiceStopJob;
    protected boolean mOnline;
    private boolean mForegroundServiceRunning;
    private long mForegroundServiceStartTime;
    private ApplicationState mApplicationState;
    protected boolean mAlarmServiceRunning;
    private long mFCMCount;
    private long mFCMDowngradeCount;
    private long mFCMTotalDelay;
    private long mMessageDeadline;
    protected long mNextAlarm = 0;
    @Nullable
    private volatile TwinlifeContext mTwinlifeContext;
    private long mNetworkLockCount;
    private long mProcessingLockCount;
    private long mInteractiveLockCount;
    private long mVoIPLockCount;
    @Nullable
    private final PowerManager.WakeLock mProcessingLock;
    @Nullable
    private final PowerManager.WakeLock mInteractiveLock;
    @Nullable
    private final WifiManager.WifiLock mWifiLock;

    @Nullable
    private ScheduledFuture<?> mWakeupTimer;
    @Nullable
    private NetworkLock mNetworkLock;
    @Nullable
    private Runnable mPendingAlarm;
    private int mPendingAlarmId;
    @Nullable
    private ScheduledFuture<?> mCheckActivityTimer;
    private long mAlarmCount;
    private static AndroidJobServiceImpl jobService;

    static class JobThreadFactory implements ThreadFactory {

        public Thread newThread(@NonNull Runnable runnable) {

            return new Thread(runnable, "twinlife-jobs");
        }
    }

    @SuppressLint("HardwareIds")
    public AndroidJobServiceImpl(@NonNull Application application) {
        if (DEBUG) {
            Log.d(LOG_TAG, "JobServiceImpl");
        }

        mExecutor = Executors.newSingleThreadScheduledExecutor(new JobThreadFactory());
        mJobList = new ArrayList<>();
        mLifecycleCallbacks = new LifecycleCallbacksImpl(this, mExecutor);
        mApplication = application;
        mApplication.registerActivityLifecycleCallbacks(mLifecycleCallbacks);
        mOnline = false;
        mForegroundServiceRunning = false;
        mAlarmServiceRunning = false;
        mNetworkLockCount = 0;
        mMessageDeadline = Long.MAX_VALUE;
        mAlarmCount = 0;
        jobService = this;
        mApplicationState = ApplicationState.BACKGROUND;

        PowerManager powerManager = (PowerManager) application.getSystemService(Context.POWER_SERVICE);
        if (powerManager != null) {
            mProcessingLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, LOG_TAG + ":");
            mInteractiveLock = powerManager.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP, LOG_TAG + ":");
            mProcessingLock.setReferenceCounted(false);
            mInteractiveLock.setReferenceCounted(false);
        } else {
            mProcessingLock = null;
            mInteractiveLock = null;
        }

        WifiManager wifiManager = (WifiManager) application.getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            mWifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "TwinlifeNet");
            mWifiLock.setReferenceCounted(false);
        } else {
            mWifiLock = null;
        }
    }

    public static AndroidJobServiceImpl getInstance() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getInstance");
        }

        return jobService;
    }

    public void setTwinlifeContext(@NonNull TwinlifeContext twinlifeContext) {

        mTwinlifeContext = twinlifeContext;
    }

    //
    // Implement JobService interface
    //

    @Override
    public synchronized boolean isForeground() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isForeground state=" + mApplicationState);
        }

        return mApplicationState == ApplicationState.FOREGROUND;
    }

    @Override
    public synchronized boolean isIdle() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isIdle state=" + mApplicationState);
        }

        return mApplicationState != ApplicationState.FOREGROUND && mNetworkLockCount == 0;
    }

    /**
     * Get the application state as seen and managed by the job service.
     *
     * @return the application state.
     */
    @Override
    @NonNull
    public synchronized ApplicationState getState() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getState " + mApplicationState);
        }

        return mApplicationState;
    }

    @Override
    public void setObserver(@NonNull Observer observer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setObserver observer=" + observer);
        }

        mObservers.addIfAbsent(observer);
    }

    @Override
    public void removeObserver(@NonNull Observer observer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeObserver observer=" + observer);
        }

        mObservers.remove(observer);
    }

    @Override
    @NonNull
    public DeviceInfo getDeviceInfo(boolean checkpoint) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDeviceInfo checkpoint=" + checkpoint);
        }

        long fcmCount, fcmDowngradeCount, fcmTotalDelay, alarmCount;
        long networkLockCount, processingLockCount, interactiveLockCount;
        synchronized (this) {
            fcmCount = mFCMCount;
            fcmDowngradeCount = mFCMDowngradeCount;
            fcmTotalDelay = mFCMTotalDelay;
            alarmCount = mAlarmCount;
            networkLockCount = mNetworkLockCount;
            processingLockCount = mProcessingLockCount;
            interactiveLockCount = mInteractiveLockCount;
            if (checkpoint) {
                mFCMCount = 0;
                mFCMTotalDelay = 0;
                mFCMDowngradeCount = 0;
            }
        }
        DeviceInfo result = new DeviceInfoImpl(mApplication, mLifecycleCallbacks.getForegroundTime(), mLifecycleCallbacks.getBackgroundTime(),
                fcmCount, fcmDowngradeCount, fcmTotalDelay, alarmCount, networkLockCount, processingLockCount, interactiveLockCount);

        if (checkpoint) {
            mLifecycleCallbacks.resetTime();
        }
        return result;
    }

    /**
     * Prepare to start a foreground service triggered by a notification with the given priority.
     *
     * @param priority         the firebase message priority.
     * @param originalPriority the original firebase message priority.
     * @param sentTime         the time when the firebase message was sent.
     * @param finish           the work to execute after the foreground service delay expires.
     * @param delay            the maximum delay in milliseconds for the execution of the foreground service.
     * @return the job instance that will be called when the foreground service must be terminated.
     */
    @Override
    public Job startForegroundService(int priority, int originalPriority, long sentTime, @NonNull Runnable finish, long delay) {
        if (DEBUG) {
            Log.d(LOG_TAG, "startForegroundService: priority=" + priority + " originalPriority=" + originalPriority
                    + " sentTime=" + sentTime + " finish=" + finish + " delay=" + delay);
        }

        synchronized (this) {
            if (priority == originalPriority) {
                mFCMCount++;
            } else {
                mFCMDowngradeCount++;
            }
            long now = System.currentTimeMillis();
            if (now > sentTime && sentTime > 0) {
                mFCMTotalDelay += now - sentTime;
            }
            mForegroundServiceRunning = true;
            mForegroundServiceStartTime = now;
            if (INFO) {
                Log.i(LOG_TAG, "startForeground in state " + mApplicationState + " for " + delay + " ms");
            }
            if (mApplicationState != ApplicationState.FOREGROUND && mApplicationState != ApplicationState.WAKEUP_ALARM) {
                mApplicationState = ApplicationState.WAKEUP_PUSH;
            }

            // If we have a FOREGROUND priority job, schedule it immediately.
            for (final JobImpl job : mJobList) {
                if (job.mScheduled == null && job.mPriority == Priority.FOREGROUND) {
                    job.mScheduled = mExecutor.schedule(job, JOB_FOREGROUND_DELAY, TimeUnit.MILLISECONDS);
                }
            }
            mForegroundServiceStopJob = new ForegroundServiceJobImpl(finish, now + delay);
            mForegroundServiceStopJob.mScheduled = mExecutor.schedule(this::stopForegroundService, delay, TimeUnit.MILLISECONDS);
            return mForegroundServiceStopJob;
        }
    }

    /**
     * Schedule a job to be executed sometimes in the future.
     *
     * @param name     the job name.
     * @param work     the work to execute.
     * @param priority the priority of the work to decide when it can be executed.
     * @return a job instance that allows to cancel its execution.
     */
    @NonNull
    public Job scheduleJob(@NonNull String name, @NonNull Runnable work, @NonNull Priority priority) {
        if (DEBUG) {
            Log.d(LOG_TAG, "schedule: name=" + name + " work=" + work + " priority=" + priority);
        }

        JobImpl job = new JobImpl(name, work, priority);

        synchronized (this) {
            mJobList.add(job);
            scheduleJobs();
        }
        return job;
    }

    /**
     * Schedule a job to be executed sometimes in the future and after the specified delay.
     *
     * @param name     the job name.
     * @param work     the work to execute.
     * @param delay    the delay to wait before executing the work.
     * @param priority the priority of the work to decide when it can be executed.
     * @return a job instance that allows to cancel its execution.
     */
    @NonNull
    public Job scheduleIn(@NonNull String name, @NonNull Runnable work, long delay, @NonNull Priority priority) {
        if (DEBUG) {
            Log.d(LOG_TAG, "schedule: name=" + name + " work=" + work + " delay=" + delay + " priority=" + priority);
        }

        JobImpl result = new JobImpl(name, work, priority, System.currentTimeMillis() + (delay > 0 ? delay : 0));

        synchronized (this) {
            mJobList.add(result);
            scheduleJobs();
        }
        return result;
    }

    @NonNull
    public Job scheduleAfter(@NonNull String name, @NonNull Runnable work, long deadline, @NonNull Priority priority) {
        if (DEBUG) {
            Log.d(LOG_TAG, "schedule: name=" + name + " work=" + work + " delay=" + deadline + " priority=" + priority);
        }

        JobImpl result = new JobImpl(name, work, priority, deadline);

        synchronized (this) {
            mJobList.add(result);
            scheduleJobs();
        }
        return result;
    }

    /**
     * Schedule a job to be executed sometimes in the future and after the specified delay repeatedly.
     *
     * Times are in MILLISECONDS.
     *
     * @param work         the work to execute.
     * @param initialDelay the delay to wait before executing the work.
     * @param period       the periodicity of the work to execute.
     * @return a ScheduleFuture instance that allows to cancel its execution.
     */
    @NonNull
    public ScheduledFuture<?> scheduleAtFixedRate(@NonNull Runnable work, long initialDelay, long period) {

        return mExecutor.scheduleAtFixedRate(work, initialDelay, period, TimeUnit.SECONDS);
    }

    /**
     * Schedule a job to be executed sometimes in the future and after the specified delay.
     *
     * Times are in MILLISECONDS.
     *
     * @param work  the work to execute.
     * @param delay the delay to wait before executing the work.
     * @return a ScheduleFuture instance that allows to cancel its execution.
     */
    @NonNull
    public ScheduledFuture<?> schedule(@NonNull Runnable work, long delay) {

        return mExecutor.schedule(work, delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Allocate a network lock to try keeping the service alive.
     *
     * When the network lock is not needed anymore, its `release` operation must be called.
     *
     * @return the network lock instance.
     */
    @Override
    @NonNull
    synchronized public NetworkLock allocateNetworkLock() {
        if (DEBUG) {
            Log.d(LOG_TAG, "allocateNetworkLock, lock counter=" + (mNetworkLockCount + 1));
        }

        boolean needLock;
        ApplicationState state;
        synchronized (this) {
            mNetworkLockCount++;
            if (mNetworkLockCount == 1 && mWifiLock != null) {
                needLock = !mWifiLock.isHeld();
            } else {
                needLock = false;
            }
            state = mApplicationState;
        }
        if (needLock) {
            mWifiLock.acquire();

            if (state != ApplicationState.FOREGROUND) {
                for (Observer observer : mObservers) {
                    mExecutor.execute(observer::onBackgroundNetworkStart);
                }
            }
        }

        return new NetworkLockImpl(this);
    }

    /**
     * Allocate a processing lock to tell the system we need the CPU.
     *
     * When the processing lock is not needed anymore, its `release` operation must be called.
     *
     * @return the processing lock instance.
     */
    @Override
    @NonNull
    public ProcessingLock allocateProcessingLock() {
        if (DEBUG) {
            Log.d(LOG_TAG, "allocateProcessingLock, lock counter=" + mNetworkLockCount + 1);
        }

        boolean needLock;
        synchronized (this) {
            mProcessingLockCount++;
            if (mProcessingLockCount == 1 && mProcessingLock != null) {
                needLock = !mProcessingLock.isHeld();
            } else {
                needLock = false;
            }
        }
        if (needLock) {
            mProcessingLock.acquire();
        }

        return new ProcessingLockImpl(this);
    }

    /**
     * Allocate an interactive lock to tell activate the screen and tell the system we need the CPU.
     *
     * When the interactive lock is not needed anymore, its `release` operation must be called.
     *
     * @return the interactive lock instance.
     */
    @Override
    @NonNull
    public InteractiveLock allocateInteractiveLock() {
        if (DEBUG) {
            Log.d(LOG_TAG, "allocateInteractiveLock, lock counter=" + mInteractiveLockCount + 1);
        }

        boolean needLock;
        synchronized (this) {
            mInteractiveLockCount++;
            if (mInteractiveLockCount == 1 && mInteractiveLock != null) {
                needLock = !mInteractiveLock.isHeld();
            } else {
                needLock = false;
            }
        }
        if (needLock) {
            mInteractiveLock.acquire();
        }

        return new InteractiveLockImpl(this);
    }

    /**
     * Allocate a VoIP lock to tell the service a VoIP call is in progress and we must not disconnect
     * while we are in background.
     * @return the VoIP lock instance.
     */
    @Override
    @NonNull
    public VoIPLock allocateVoIPLock() {
        if (DEBUG) {
            Log.d(LOG_TAG, "allocateVoIPLock");
        }

        synchronized (this) {
            mVoIPLockCount++;
        }
        return new VoIPLockImpl(this);
    }

    @Override
    synchronized public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        mOnline = true;
        scheduleJobs();

        // If we are connected and running an alarm job, setup a timer to stop that job if there is no work to do.
        if (mAlarmServiceRunning && mCheckActivityTimer == null) {
            mCheckActivityTimer = mExecutor.schedule(this::checkFinishAlarm, ALARM_FIRST_CHECK_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    synchronized public void onTwinlifeOffline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOffline");
        }

        mOnline = false;
        scheduleJobs();
    }

    /**
     * Get the next deadline for the MESSAGE priority job.
     *
     * @return the message deadline or Long.MAX_VALUE.
     */
    synchronized public long getMessageDeadline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getMessageDeadline");
        }

        return mMessageDeadline;
    }

    /**
     * Check if the VoIP lock is active.
     * @return true if some VoIP is active.
     */
    synchronized public boolean isVoIPActive() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isVoIPActive " + mVoIPLockCount);
        }

        return mVoIPLockCount > 0;
    }

    /**
     * Wakeup handler called to process the job.
     *
     * @param jobId    the job unique id.
     * @param complete completion handler called to terminate the wakeup.
     */
    void processJob(int jobId, @NonNull Runnable complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processJob jobId=" + jobId);
        }

        final TwinlifeContext twinlifeContext = getTwinlifeContext();
        if (twinlifeContext == null) {

            return;
        }

        // Prepare to handle the alarm, get the network lock and setup a 1.5s timer to check if the alarm job is still necessary.
        synchronized (this) {
            mAlarmServiceRunning = true;
            mPendingAlarmId = jobId;
            mPendingAlarm = complete;
            mAlarmCount++;

            if (mApplicationState != ApplicationState.FOREGROUND && mApplicationState != ApplicationState.WAKEUP_PUSH) {
                mApplicationState = ApplicationState.WAKEUP_ALARM;
            }
            if (mNetworkLock == null) {
                mNetworkLock = allocateNetworkLock();
            }

            if (mOnline && mCheckActivityTimer == null) {
                mCheckActivityTimer = mExecutor.schedule(this::checkFinishAlarm, ALARM_FIRST_CHECK_DELAY, TimeUnit.MILLISECONDS);
            }
        }

        // Connect and schedule a first timer in 5s to check if we have the network connectivity.
        // Note: if we check network connectivity now, there are cases when we don't have this connectivity
        // due to doze mode which is not yet unfrozen for us.
        twinlifeContext.connect(); // first try if we are lucky
        mExecutor.schedule(() -> {

            // If the network is available, try to connect and remain connected for 30s.
            // But if there is no network, there is no need to hold the power lock for 30s.
            if (twinlifeContext.getConnectivityService().isConnectedNetwork()) {

                // Connect and schedule a job termination in 25s.  We could improve this by
                // reducing the delay if there is no pending work.
                twinlifeContext.connect();
                mWakeupTimer = mExecutor.schedule(this::finishAlarm, ALARM_SERVICE_BACKGROUND_DELAY, TimeUnit.SECONDS);

            } else {
                finishAlarm();
            }

        }, 5, TimeUnit.SECONDS);
    }

    /**
     * Cancel the job.
     *
     * @param jobId the job unique id.
     */
    void cancelJob(int jobId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "cancelJob jobId=" + jobId);
        }

        final NetworkLock networkLock;
        synchronized (this) {
            if (mWakeupTimer != null) {
                mWakeupTimer.cancel(false);
                mWakeupTimer = null;
            }
            if (mCheckActivityTimer != null) {
                mCheckActivityTimer.cancel(false);
                mCheckActivityTimer = null;
            }

            networkLock = mNetworkLock;
            mNetworkLock = null;
            mPendingAlarm = null;
            mAlarmServiceRunning = false;
        }
        if (networkLock != null) {
            networkLock.release();
        }

        if (jobId == JOB_CONNECT_ID) {
            schedule(jobId);
        }
    }

    /**
     * Schedule an alarm to wakeup the application when network conditions and alarm time are met.
     */
    public abstract long scheduleAlarm();

    protected abstract long schedule(int jobId);

    /**
     * Get the twinlife context and make sure Twinlife library is initialized.
     *
     * @return the twinlife context or null.
     */
    protected TwinlifeContext getTwinlifeContext() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwinlifeContext");
        }

        for (int retry = 0; retry < 10; retry++) {
            TwinlifeContext twinlifeContext = mTwinlifeContext;
            if (twinlifeContext != null && twinlifeContext.hasTwinlife()) {
                return twinlifeContext;
            }

            // Wait a little to let the Twinlife executor's thread complete the library configuration.
            try {
                Thread.sleep(200);
            } catch (Exception exception) {
                Log.d(LOG_TAG, "Exception: " + exception);
            }
        }

        return null;
    }

    protected boolean hasPush() {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasPush");
        }

        final TwinlifeContext twinlifeContext = getTwinlifeContext();
        if (twinlifeContext == null) {
            return false;
        } else {
            return twinlifeContext.getManagementService().hasPushNotification();
        }
    }

    /**
     * Compute a deadline to schedule an alarm to wakeup the application.
     *
     * @return the deadline for the alarm.
     */
    protected long computeAlarmDeadline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "computeAlarmDeadline");
        }

        // Get the current message scheduler deadline.
        long messageDeadline = getMessageDeadline();
        long now = System.currentTimeMillis();
        long messageDelay = messageDeadline - now;

        if (messageDelay < 0) {
            messageDelay = ALARM_MIN_DELAY;
        }

        // If we have no message to send, the messageDelay is > 1h:
        // - setup a 30mn alarm if there is no Firebase support
        // - setup a 2h alarm if we have Firebase support.
        if (messageDelay > 60 * 3600 * 1000) {
            if (hasPush()) {
                messageDelay = ALARM_PASSIVE_DELAY;
            } else {
                messageDelay = ALARM_ACTIVE_DELAY;
            }
        }

        long deadline = now + messageDelay;
        synchronized (this) {
            if (Math.abs(mNextAlarm - deadline) < ALARM_MIN_DELAY && mNextAlarm > now) {
                deadline = mNextAlarm;
            } else {
                mNextAlarm = deadline;
            }
        }

        if (DEBUG) {
            Log.e(LOG_TAG, "JobScheduler messageDelay=" + messageDelay + " nextAlarm=" + deadline + " in " + (deadline - now));
        }

        return deadline;
    }

    //
    // Private methods
    //

    private synchronized void releaseNetworkLock() {
        if (DEBUG) {
            Log.d(LOG_TAG, "releaseNetworkLock lock counter=" + (mNetworkLockCount - 1));
        }

        mNetworkLockCount--;
        if (mNetworkLockCount == 0) {

            if (mApplicationState != ApplicationState.FOREGROUND) {
                for (Observer observer : mObservers) {
                    mExecutor.execute(observer::onBackgroundNetworkStop);
                }
            }

            mExecutor.schedule(this::stopForegroundService, FOREGROUND_RELEASE_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void releaseProcessingLock() {
        if (DEBUG) {
            Log.d(LOG_TAG, "releaseProcessingLock lock counter=" + (mProcessingLockCount - 1));
        }

        mProcessingLockCount--;
        if (mProcessingLockCount == 0 && mProcessingLock != null) {

            mExecutor.schedule(this::stopProcessing, FOREGROUND_RELEASE_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void releaseInteractiveLock() {
        if (DEBUG) {
            Log.d(LOG_TAG, "releaseInteractiveLock lock counter=" + (mInteractiveLockCount - 1));
        }

        mInteractiveLockCount--;
        if (mInteractiveLockCount == 0 && mInteractiveLock != null) {

            mExecutor.schedule(this::stopInteractive, FOREGROUND_RELEASE_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private synchronized void releaseVoIPLock() {
        if (DEBUG) {
            Log.d(LOG_TAG, "releaseVoIPLock lock counter=" + (mVoIPLockCount - 1));
        }

        mVoIPLockCount--;
        if (mVoIPLockCount == 0) {

            mExecutor.schedule(this::stopForegroundService, FOREGROUND_RELEASE_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private void stopProcessing() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stopProcessing");
        }

        synchronized (this) {
            if (mProcessingLockCount > 0 || mProcessingLock == null || !mProcessingLock.isHeld()) {
                return;
            }

            mProcessingLock.release();
        }
    }

    private void stopInteractive() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stopInteractive");
        }

        synchronized (this) {
            if (mInteractiveLockCount > 0 || mInteractiveLock == null || !mInteractiveLock.isHeld()) {
                return;
            }

            mInteractiveLock.release();
        }
    }

    private void cancelForegroundService() {
        if (INFO) {
            Log.i(LOG_TAG, "cancelForegroundService state=" + mApplicationState + " voipLock=" + mVoIPLockCount);
        }

        synchronized (this) {
            mForegroundServiceStopJob = null;
            mForegroundServiceRunning = false;
            mForegroundServiceStartTime = 0;
            if (mApplicationState != ApplicationState.FOREGROUND) {
                mExecutor.execute(this::stopForegroundService);
            }
        }
    }

    private void stopForegroundService() {
        if (INFO) {
            Log.i(LOG_TAG, "stopForegroundService state=" + mApplicationState + " voipLock=" + mVoIPLockCount);
        }

        ForegroundServiceJobImpl stopJob;
        boolean mustDisconnect;
        long now = System.currentTimeMillis();
        synchronized (this) {
            stopJob = mForegroundServiceStopJob;

            if (mNetworkLockCount == 0 && mWifiLock != null && mWifiLock.isHeld()) {
                mWifiLock.release();
            }
            if (mVoIPLockCount == 0) {
                if (mApplicationState == ApplicationState.FOREGROUND) {
                    // In foreground, we must never disconnect.
                    mustDisconnect = false;
                    if (stopJob != null && now < stopJob.mDeadline) {
                        return;
                    }
                    mForegroundServiceStopJob = null;
                } else if (mNetworkLockCount == 0 && (now - mForegroundServiceStartTime < MIN_FOREGROUND_TIME)) {
                    // Give at least 4s for the foreground service to connect.
                    return;
                } else if (mNetworkLockCount > 0 && stopJob != null && now < stopJob.mDeadline) {
                    // If the foreground service has not expired, keep connection and continue.
                    return;
                } else {
                    mustDisconnect = true;
                }
                if (mApplicationState == ApplicationState.WAKEUP_PUSH) {
                    mApplicationState = ApplicationState.BACKGROUND_IDLE;
                }
            } else {
                mustDisconnect = false;
                mForegroundServiceStopJob = null;
            }
            mForegroundServiceRunning = false;
        }

        // Disconnect from Twinlife server if there is no need to be connected.
        if (mustDisconnect) {
            disconnect();
        } else if (stopJob != null) {
            // We can stop the foreground service.
            stopJob.run();
        }
    }

    /**
     * Disconnect from Twinlife server if there is no need to be connected.
     */
    private void disconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "disconnect");
        }

        final TwinlifeContextImpl twinlifeContext = (TwinlifeContextImpl) mTwinlifeContext;
        if (twinlifeContext != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasPush()) {
            if (mDisconnectTask != null) {
                mDisconnectTask.cancel(false);
            }

            // Android O introduced restrictions on starting foreground services
            // which prevents us from processing incoming calls reliably.
            // So we disconnect from the server a few seconds after entering background,
            // this way the device will receive a firebase push which can start the service without
            // hitting these restrictions.
            mDisconnectTask = mExecutor.schedule(() -> {
                ForegroundServiceJobImpl stopJob;
                synchronized (this) {
                    mDisconnectTask = null;
                    // Be careful: if a VoIP call is in progress, do not disconnect!
                    if (mApplicationState == ApplicationState.FOREGROUND || mVoIPLockCount > 0) {
                        return;
                    }
                    mApplicationState = ApplicationState.SUSPENDED;
                    stopJob = mForegroundServiceStopJob;
                    mForegroundServiceStopJob = null;
                }
                if (INFO) {
                    Log.i(LOG_TAG, "Disconnecting state " + mApplicationState);
                }

                // Suspend the service and the opened P2P data connections.
                twinlifeContext.suspend();

                // Give 500ms to close and disconnect from the server.
                mExecutor.schedule(() -> {
                    twinlifeContext.disconnect();
                    if (stopJob != null) {
                        stopJob.run();
                    }

                    // In the SUSPENDED state, we must not try to skip the disconnection, but instead
                    // re-connect immediately if we received a push in between.
                    if (mApplicationState != ApplicationState.SUSPENDED) {
                        twinlifeContext.connect();
                    }
                }, STOP_FOREGROUND_DISCONNECT_DELAY, TimeUnit.MILLISECONDS);

            }, STOP_FOREGROUND_DISCONNECT_DELAY, TimeUnit.MILLISECONDS);
        } else {
            ForegroundServiceJobImpl stopJob;
            synchronized (this) {
                stopJob = mForegroundServiceStopJob;
                mForegroundServiceStopJob = null;
            }
            if (stopJob != null) {
                stopJob.run();
            }
        }
    }

    void onEnterForeground() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onEnterForeground");
        }

        synchronized (this) {
            if (mDisconnectTask != null) {
                mDisconnectTask.cancel(false);
                mDisconnectTask = null;
            }
            mApplicationState = ApplicationState.FOREGROUND;
        }

        for (Observer observer : mObservers) {
            mExecutor.execute(observer::onEnterForeground);
        }

        scheduleJobs();

        // Connect each time we enter in foreground.
        final TwinlifeContext twinlifeContext = mTwinlifeContext;
        if (twinlifeContext != null) {
            twinlifeContext.connect();
        }
    }

    synchronized void onEnterBackground() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onEnterBackground");
        }

        mApplicationState = ApplicationState.BACKGROUND;
        for (Observer observer : mObservers) {
            mExecutor.execute(observer::onEnterBackground);
        }

        scheduleJobs();

        if (!mForegroundServiceRunning && mVoIPLockCount == 0) {
            mExecutor.schedule(this::stopForegroundService, FOREGROUND_RELEASE_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * When the alarm job is running, check if we still need it and stop it.
     */
    private void checkFinishAlarm() {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkFinishAlarm");
        }

        final TwinlifeContext twinlifeContext = getTwinlifeContext();
        if (twinlifeContext == null) {

            mCheckActivityTimer = null;
            return;
        }

        final PeerConnectionService peerConnectionService = twinlifeContext.getPeerConnectionService();
        synchronized (this) {
            if (peerConnectionService.hasPeerConnections()) {

                mCheckActivityTimer = mExecutor.schedule(this::checkFinishAlarm, ALARM_CHECK_DELAY, TimeUnit.MILLISECONDS);
                return;
            }

            mCheckActivityTimer = null;
        }

        finishAlarm();
    }

    /**
     * Finish the alarm job, release locks and cleanup.
     */
    private void finishAlarm() {
        if (DEBUG) {
            Log.d(LOG_TAG, "finishAlarm");
        }

        final int alarmId;
        final Runnable complete;
        final NetworkLock networkLock;
        synchronized (this) {
            if (mWakeupTimer != null) {
                mWakeupTimer.cancel(false);
                mWakeupTimer = null;
            }
            if (mCheckActivityTimer != null) {
                mCheckActivityTimer.cancel(false);
                mCheckActivityTimer = null;
            }

            alarmId = mPendingAlarmId;
            complete = mPendingAlarm;
            networkLock = mNetworkLock;
            mAlarmServiceRunning = false;
            mPendingAlarmId = 0;
            mPendingAlarm = null;
            mNetworkLock = null;
            if (mApplicationState == ApplicationState.WAKEUP_ALARM) {
                mApplicationState = ApplicationState.BACKGROUND_IDLE;
            }
        }

        schedule(alarmId);
        if (networkLock != null) {
            networkLock.release();
        }
        if (complete != null) {
            complete.run();
        }
    }

    private void activePeers(int count) {
        if (DEBUG) {
            Log.d(LOG_TAG, "activePeers count=" + count);
        }

        for (Observer observer : mObservers) {
            mExecutor.execute(() -> observer.onActivePeers(count));
        }
    }

    private boolean isScheduleAllowed(@NonNull JobImpl job) {

        switch (job.mPriority) {
            case CONNECT:
                return true;

            case FOREGROUND:
                return mApplicationState == ApplicationState.FOREGROUND
                        || mApplicationState == ApplicationState.WAKEUP_PUSH
                        || mApplicationState == ApplicationState.WAKEUP_ALARM;

            case UPDATE:
                return mOnline && mApplicationState == ApplicationState.FOREGROUND;

            case MESSAGE:
                return mOnline;

            case REPORT:
                return mOnline && (mApplicationState == ApplicationState.FOREGROUND
                        || mApplicationState == ApplicationState.WAKEUP_PUSH
                        || mApplicationState == ApplicationState.WAKEUP_ALARM);

            default:
                return false;
        }
    }

    private synchronized void scheduleJobs() {
        if (DEBUG) {
            Log.d(LOG_TAG, "scheduleJobs");
        }

        mMessageDeadline = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        for (final JobImpl job : mJobList) {
            boolean allowed = isScheduleAllowed(job);
            long dt;

            if (job.mScheduled == null && allowed) {
                switch (job.mPriority) {
                    case FOREGROUND:
                        // Schedule the FOREGROUND job even if we are not online and in 10ms/immediately.
                        job.mScheduled = mExecutor.schedule(job, JOB_FOREGROUND_DELAY, TimeUnit.MILLISECONDS);
                        break;

                    case UPDATE:
                    case MESSAGE:
                    case CONNECT:
                        dt = job.getDeadline() - now;
                        if (dt <= 0) {
                            dt = 0;
                        }
                        job.mScheduled = mExecutor.schedule(job, dt, TimeUnit.MILLISECONDS);
                        break;

                    case REPORT:
                        dt = job.getDeadline() - now;
                        if (dt <= 0) {
                            // Schedule the report that have ellapsed in 5 seconds if we are still in a push.
                            dt = JOB_REPORT_DELAY;
                        }
                        job.mScheduled = mExecutor.schedule(job, dt, TimeUnit.MILLISECONDS);
                        break;
                }
            } else if (job.mScheduled != null && !allowed) {
                // Cancel the pending jobs if they are scheduled.  Don't interrupt them if they are running.
                job.mScheduled.cancel(false);
                job.mScheduled = null;
            }

            // Keep the deadline for the earliest message priority.
            if (job.mPriority == MESSAGE) {
                long deadline = job.getDeadline();

                if (mMessageDeadline > deadline) {
                    if (DEBUG) {
                        Log.e(LOG_TAG, "Using message deadline " + deadline
                                + " (" + (deadline - System.currentTimeMillis()) + " ms) from job " + job);
                    }
                    mMessageDeadline = deadline;
                }
            }
        }

        if (DEBUG) {
            Log.e(LOG_TAG, "Message deadline in " + (mMessageDeadline - now) + " alarm in " + (mNextAlarm - now));
        }

        // If the next alarm is not yet scheduled or too far in the future, update it.
        if (mMessageDeadline > now && (mMessageDeadline < mNextAlarm || mNextAlarm == 0)) {
            mExecutor.execute(this::scheduleAlarm);
        }
    }

    private synchronized void terminateJob(@NonNull JobImpl job) {
        if (DEBUG) {
            Log.d(LOG_TAG, "terminateJob job=" + job);
        }

        if (job.mScheduled != null) {
            job.mScheduled.cancel(false);
        }
        mJobList.remove(job);
    }

}
