/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.job;

import android.os.Build;
import androidx.annotation.NonNull;

import android.util.Log;

import org.twinlife.twinlife.ApplicationState;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DeviceInfo;
import org.twinlife.twinlife.JobService;
import org.twinlife.twinlife.TwinlifeContext;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class EngineJobServiceImpl extends TwinlifeContext.DefaultObserver implements JobService {
    private static final String LOG_TAG = "EngineJobServiceImpl";
    private static final boolean DEBUG = false;
    private static final boolean INFO = BuildConfig.ENABLE_INFO_LOG;
    private static final int JOB_FOREGROUND_DELAY = 10; // ms
    private static final int JOB_UPDATE_DELAY = 500;  // ms
    private static final int JOB_REPORT_DELAY = 5000;   // ms
    private static final int FOREGROUND_RELEASE_DELAY = 1000; // ms

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
                Log.i(LOG_TAG, "Running job " + mName);
            }

            try {
                mWork.run();
                if (INFO) {
                    Log.i(LOG_TAG, "Job " + mName + " terminated");
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
        private final JobServiceImpl mJobService;
        private boolean mIsReleased = false;

        NetworkLockImpl(final JobServiceImpl jobService) {
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
            mForegroundServiceStopJob = null;
        }

        /**
         * Run the job and remove it from the list when it is finished.
         */
        @Override
        public void run() {
            if (INFO) {
                Log.i(LOG_TAG, "Running job ForegroundServiceJobImpl");
            }

            try {
                if (mScheduled != null) {
                    mScheduled.cancel(false);
                    mScheduled = null;
                }
                mFinish.run();
                if (INFO) {
                    Log.i(LOG_TAG, "Job ForegroundServiceJobImpl");
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

    private final ScheduledExecutorService mExecutor;
    private final List<JobImpl> mJobList;
    private final CopyOnWriteArrayList<Observer> mObservers = new CopyOnWriteArrayList<>();
    private ForegroundServiceJobImpl mForegroundServiceStopJob;
    private boolean mOnline;
    private boolean mInForeground;
    private boolean mForegroundServiceRunning;
    private long mFCMCount;
    private long mFCMDowngradeCount;
    private long mFCMTotalDelay;
    private long mNetworkLockCount;

    // When set to true, allow to disconnect from Twinlife if we are in background and have nothing to do.
    private boolean mDisconnectInBackground;
    private long mStartTime;

    static class JobThreadFactory implements ThreadFactory {

        public Thread newThread(@NonNull Runnable runnable) {

            return new Thread(runnable, "twinlife-jobs");
        }
    }

    public EngineJobServiceImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "JobServiceImpl");
        }

        mExecutor = Executors.newSingleThreadScheduledExecutor(new JobThreadFactory());
        mJobList = new ArrayList<>();
        mOnline = false;
        mInForeground = true;
        mForegroundServiceRunning = false;
        mNetworkLockCount = 0;
        mDisconnectInBackground = false;
        mStartTime = System.currentTimeMillis();
    }

    protected long getForegroundTime() {

        return System.currentTimeMillis() - mStartTime;
    }

    protected long getBackgroundTime() {

        return 0;
    }

    //
    // Implement JobService interface
    //

    @Override
    public synchronized boolean isForeground() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isForeground" + (mInForeground ? " YES" : " NO"));
        }

        return mInForeground;
    }

    @Override
    public synchronized boolean isIdle() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isIdle" + (!mInForeground && !mForegroundServiceRunning ? " YES" : " NO"));
        }

        return !mInForeground && !mForegroundServiceRunning && mDisconnectInBackground;
    }

    /**
     * Get the application state as seen and managed by the job service.
     *
     * @return the application state.
     */
    @Override
    @NonNull
    public ApplicationState getState() {

        return ApplicationState.FOREGROUND;
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

        long fcmCount, fcmDowngradeCount, fcmTotalDelay;
        synchronized (this) {
            fcmCount = mFCMCount;
            fcmDowngradeCount = mFCMDowngradeCount;
            fcmTotalDelay = mFCMTotalDelay;
            if (checkpoint) {
                mFCMCount = 0;
                mFCMTotalDelay = 0;
                mFCMDowngradeCount = 0;
            }
        }
        DeviceInfo result = new EngineDeviceInfoImpl(getForegroundTime(), getBackgroundTime(),
                fcmCount, fcmDowngradeCount, fcmTotalDelay, Build.getOsName());

        if (checkpoint) {
            mStartTime = System.currentTimeMillis();
        }
        return result;
    }

    /**
     * Prepare to start a foreground service triggered by a notification with the given priority.
     *
     * @param priority the firebase message priority.
     * @param originalPriority the original firebase message priority.
     * @param sentTime the time when the firebase message was sent.
     * @param finish the work to execute after the foreground service delay expires.
     * @param delay the maximum delay in milliseconds for the execution of the foreground service.
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
            if (now > sentTime) {
                mFCMTotalDelay += now - sentTime;
            }
            mForegroundServiceRunning = true;

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
     * @param name the job name.
     * @param work the work to execute.
     * @param priority the priority of the work to decide when it can be executed.
     * @return a job instance that allows to cancel its execution.
     */
    @NonNull
    public Job scheduleJob(@NonNull String name, @NonNull Runnable work, @NonNull Priority priority) {
        if (DEBUG) {
            Log.d(LOG_TAG, "scheduleJob: name=" + name + " work=" + work + " priority=" + priority);
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
     * @param name the job name.
     * @param work the work to execute.
     * @param delay the delay to wait before executing the work.
     * @param priority the priority of the work to decide when it can be executed.
     * @return a job instance that allows to cancel its execution.
     */
    @NonNull
    public Job scheduleIn(@NonNull String name, @NonNull Runnable work, long delay, @NonNull Priority priority) {
        if (DEBUG) {
            Log.d(LOG_TAG, "scheduleIn: name=" + name + " work=" + work + " delay=" + delay + " priority=" + priority);
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
     * @param work the work to execute.
     * @param initialDelay the delay to wait before executing the work.
     * @param period the periodicity of the work to execute.
     * @return a ScheduleFuture instance that allows to cancel its execution.
     */
    @NonNull
    public ScheduledFuture<?> scheduleAtFixedRate(@NonNull Runnable work, long initialDelay, long period) {

        return mExecutor.scheduleAtFixedRate(work, initialDelay, period, TimeUnit.MILLISECONDS);
    }

    /**
     * Schedule a job to be executed sometimes in the future and after the specified delay.
     *
     * Times are in MILLISECONDS.
     *
     * @param work the work to execute.
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
            Log.d(LOG_TAG, "allocateNetworkLock, lock counter=" + mNetworkLockCount + 1);
        }

        mNetworkLockCount++;
        return new NetworkLockImpl(this);
    }

    /**
     * Allocate a processing lock to tell the system we need the CPU.
     *
     * When the processing lock is not needed anymore, its `release` operation must be called.
     *
     * @return the processing lock instance.
     */
    @NonNull
    public ProcessingLock allocateProcessingLock() {

        return () -> {
        };
    }

    /**
     * Allocate an interactive lock to tell activate the screen and tell the system we need the CPU.
     *
     * When the interactive lock is not needed anymore, its `release` operation must be called.
     *
     * @return the interactive lock instance.
     */
    @NonNull
    public InteractiveLock allocateInteractiveLock() {

        return () -> {
        };
    }

    @Override
    synchronized public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        mOnline = true;
        scheduleJobs();
    }

    @Override
    synchronized public void onTwinlifeOffline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOffline");
        }

        mOnline = false;
        scheduleJobs();
    }

    @Override
    synchronized public void onShutdown() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onShutdown");
        }

        mOnline = false;
        synchronized (this) {
            mJobList.clear();
            mObservers.clear();
            mExecutor.shutdownNow();
        }
    }

    //
    // Private methods
    //

    private synchronized void releaseNetworkLock() {
        if (DEBUG) {
            Log.d(LOG_TAG, "releaseNetworkLock lock counter=" + (mNetworkLockCount - 1));
        }

        mNetworkLockCount--;
        if (mNetworkLockCount == 0 && !mExecutor.isShutdown()) {

            for (Observer observer : mObservers) {
                mExecutor.execute(() -> observer.onBackgroundNetworkStop());
            }
            mExecutor.schedule(this::stopForegroundService, FOREGROUND_RELEASE_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private void stopForegroundService() {
        if (DEBUG) {
            Log.d(LOG_TAG, "stopForegroundService");
        }

        ForegroundServiceJobImpl stopJob;
        long now = System.currentTimeMillis();
        synchronized (this) {
            stopJob = mForegroundServiceStopJob;

            if (mNetworkLockCount > 0 && (stopJob == null || now < stopJob.mDeadline)) {

                return;
            }
            mForegroundServiceStopJob = null;
            mForegroundServiceRunning = false;
        }

        // We can stop the foreground service.
        if (stopJob != null) {
            stopJob.run();
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
                return mInForeground || mForegroundServiceRunning;

            case UPDATE:
                return mOnline && mInForeground;

            case MESSAGE:
                return mOnline;

            case REPORT:
                return mOnline && (mInForeground || mForegroundServiceRunning);

            default:
                return false;
        }
    }

    private synchronized void scheduleJobs() {
        if (DEBUG) {
            Log.d(LOG_TAG, "scheduleJobs");
        }

        if (mExecutor.isShutdown()) {
            return;
        }

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
