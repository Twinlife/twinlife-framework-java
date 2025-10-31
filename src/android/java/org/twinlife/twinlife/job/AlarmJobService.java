/*
 *  Copyright (c) 2021-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.job;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Job service launched when an alarm fired and conditions are met to try connecting and getting/sending messages.
 */
public class AlarmJobService extends JobService {
    private static final String LOG_TAG = "AlarmJobService";
    private static final boolean DEBUG = false;

    @Nullable
    private AndroidJobServiceImpl mJobService;

    @Override
    public boolean onStartJob(@NonNull JobParameters params) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onStartJob");
        }

        mJobService = AndroidJobServiceImpl.getInstance();
        if (mJobService == null) {

            return false;
        }

        mJobService.processJob(params.getJobId(), () -> jobFinished(params, false));

        return true;
    }

    @Override
    public boolean onStopJob(@NonNull JobParameters params) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onStopJob");
        }

        if (mJobService != null) {
            mJobService.cancelJob(params.getJobId());
        }

        return true;
    }

    @Override
    public void onNetworkChanged(@NonNull JobParameters params) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onNetworkChanged params=" + params);
        }
    }
}
