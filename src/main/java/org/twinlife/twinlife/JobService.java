/*
 *  Copyright (c) 2019-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.concurrent.ScheduledFuture;

public interface JobService {

    String VERSION = "1.4.0";

    enum Priority {
        // Job is related to connection management and must be scheduled even if we are not connected.
        CONNECT,

        // Foreground job that is executed only in foreground mode.
        FOREGROUND,

        // Message job that is executed only when we are connected (in background or foreground).
        MESSAGE,

        // Housekeeping job that is executed only in foreground mode and when we are connected.
        UPDATE,

        // Report job that requires the application to be in foreground or when a foreground service is running and connected.
        REPORT
    }

    interface Job {

        /**
         * Get a deadline time for this job.
         *
         * @return the job deadline time.
         */
        long getDeadline();

        /**
         * Cancel the job.
         */
        void cancel();
    }

    /**
     * Keep a lock on the network.
     */
    interface NetworkLock {

        /**
         * Report the number of active P2P connection that are successfully connected.
         *
         * @param count the number of active P2P connections.
         */
        void activePeers(int count);

        void release();
    }

    /**
     * Keep a lock on the power manager for processing purposes.
     */
    interface ProcessingLock {

        void release();
    }

    /**
     * Keep a lock on the power manager with interactive mode (screen ON).
     */
    interface InteractiveLock {

        void release();
    }

    /**
     * Keep a lock to indicate a VoIP call is in progress.
     */
    interface VoIPLock {

        void release();
    }

    interface Observer {
        void onEnterForeground();

        void onEnterBackground();

        void onBackgroundNetworkStart();

        void onBackgroundNetworkStop();

        void onActivePeers(int count);
    }

    boolean isForeground();

    boolean isIdle();

    /**
     * Get the application state as seen and managed by the job service.
     *
     * @return the application state.
     */
    @NonNull
    ApplicationState getState();

    void setObserver(@NonNull Observer observer);

    void removeObserver(@NonNull Observer observer);

    @NonNull
    DeviceInfo getDeviceInfo(boolean checkpoint);

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
    Job startForegroundService(int priority, int originalPriority, long sentTime, @NonNull Runnable finish, long delay);

    /**
     * Schedule a job to be executed sometimes in the future.
     *
     * @param name the job name.
     * @param work the work to execute.
     * @param priority the priority of the work to decide when it can be executed.
     * @return a job instance that allows to cancel its execution.
     */
    @NonNull
    Job scheduleJob(@NonNull String name, @NonNull Runnable work, @NonNull Priority priority);

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
    Job scheduleIn(@NonNull String name, @NonNull Runnable work, long delay, @NonNull Priority priority);

    /**
     * Schedule a job to be executed sometimes in the future and after the specified deadline.
     *
     * @param name the job name.
     * @param work the work to execute.
     * @param deadline the deadline time to wait before executing the work.
     * @param priority the priority of the work to decide when it can be executed.
     * @return a job instance that allows to cancel its execution.
     */
    @NonNull
    Job scheduleAfter(@NonNull String name, @NonNull Runnable work, long deadline, @NonNull Priority priority);

    /**
     * Schedule a job to be executed sometimes in the future and after the specified delay repeatedly.
     * Times are in MILLISECONDS.
     *
     * @param work the work to execute.
     * @param delay the delay to wait before executing the work.
     * @param period the periodicity of the work to execute.
     * @return a ScheduleFuture instance that allows to cancel its execution.
     */
    @NonNull
    ScheduledFuture<?> scheduleAtFixedRate(@NonNull Runnable work, long delay, long period);

    /**
     * Schedule a job to be executed sometimes in the future and after the specified delay.
     * Times are in MILLISECONDS.
     *
     * @param work the work to execute.
     * @param delay the delay to wait before executing the work.
     * @return a ScheduleFuture instance that allows to cancel its execution.
     */
    @NonNull
    ScheduledFuture<?> schedule(@NonNull Runnable work, long delay);

    /**
     * Allocate a network lock to try keeping the service alive.
     * When the network lock is not needed anymore, its `release` operation must be called.
     *
     * @return the network lock instance.
     */
    @NonNull
    NetworkLock allocateNetworkLock();

    /**
     * Allocate a processing lock to tell the system we need the CPU.
     * When the processing lock is not needed anymore, its `release` operation must be called.
     *
     * @return the processing lock instance.
     */
    @NonNull
    ProcessingLock allocateProcessingLock();

    /**
     * Allocate an interactive lock to tell activate the screen and tell the system we need the CPU.
     * When the interactive lock is not needed anymore, its `release` operation must be called.
     *
     * @return the interactive lock instance.
     */
    @NonNull
    InteractiveLock allocateInteractiveLock();

    /**
     * Allocate a VoIP lock to tell the service a VoIP call is in progress and we must not disconnect
     * while we are in background.
     * @return the interactive lock instance.
     */
    @NonNull
    VoIPLock allocateVoIPLock();
}
