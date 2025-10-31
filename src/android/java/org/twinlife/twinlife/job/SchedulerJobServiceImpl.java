/*
 *  Copyright (c) 2018-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.job;

import androidx.annotation.NonNull;

import android.app.Application;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.util.Log;

import java.util.concurrent.TimeUnit;

/**
 * Job service implementation based on the Android Job Service.
 *
 * - A reconnect job (JOB_RECONNECT_ID) is setup with a short delay to try reconnecting to the
 *   server as soon as we have the network connection.  This job is scheduled when we loose the
 *   connection and the network: it waits for the network to be available again.  There are many
 *   cases when we don't detect this situation and sometimes this job is not scheduled.
 *
 * - A long running job (JOB_CONNECT_ID) is setup to trigger a connection several times a day.
 *   It is a backup job in case the JOB_RECONNECT_ID was not scheduled.  When a push mechanism
 *   exists (Firebase), we can expect a push to wakeup the application and we use a long timer (2h).
 *   Otherwise, we must probe the server for new messages more quickly (30mn).
 *
 * This job service is used on Android SDK >= 21 (Android 5.x and above).
 */
public class SchedulerJobServiceImpl extends AndroidJobServiceImpl {
    private static final String LOG_TAG = "SchedulerJobServiceImpl";
    private static final boolean DEBUG = false;

    public SchedulerJobServiceImpl(@NonNull Application application) {
        super(application);

        if (DEBUG) {
            Log.d(LOG_TAG, "SchedulerJobServiceImpl");
        }

        mExecutor.schedule(() -> schedule(JOB_CONNECT_ID), 1, TimeUnit.SECONDS);
    }

    /**
     * Schedule a wakeup by using the Android job service to have a chance to re-connect to the server:
     *
     * - if we have messages to send, the alarm wakeup can allow us to connect and send the pending messages,
     * - if the Firebase wakeup is not supported, we have to connect on regular basis to check if we have something available.
     *
     * OTOH, we must not schedule the alarm too often because we must not drain the battery for nothing.
     */
    public void scheduleAlarm() {
        if (DEBUG) {
            Log.d(LOG_TAG, "scheduleAlarm");
        }

        schedule(JOB_RECONNECT_ID);
    }

    /**
     * Schedule an Android job service to trigger the given job id.
     *
     * @param jobId the job id to schedule.
     */
    @Override
    protected void schedule(int jobId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "schedule jobId=" + jobId);
        }

        Context context = mApplication.getApplicationContext();

        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {

            return;
        }

        long deadline = computeAlarmDeadline();
        long now = System.currentTimeMillis();
        long wakeupDelay = deadline - now;
        if (wakeupDelay <= 0) {
            wakeupDelay = 10;
        }
        JobInfo.Builder builder = new JobInfo.Builder(jobId, new ComponentName(context, AlarmJobService.class));
        builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
        builder.setRequiresCharging(false);
        builder.setPersisted(false);
        builder.setMinimumLatency(wakeupDelay);
        builder.setOverrideDeadline(wakeupDelay + ALARM_ACTIVE_DELAY);
        builder.setBackoffCriteria(wakeupDelay, JobInfo.BACKOFF_POLICY_LINEAR);

        scheduler.cancel(jobId);
        int result = scheduler.schedule(builder.build());
        if (result != JobScheduler.RESULT_SUCCESS) {
            Log.e(LOG_TAG, "Job creation failed");
        }

        if (DEBUG) {
            Log.e(LOG_TAG, "Job " + jobId + " scheduled in " + wakeupDelay + " ms result=" + result);
        }
    }
}
