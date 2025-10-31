/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife.job;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Android application activity lifecycle callbacks to detect when we enter or leave foreground:
 *
 * - A counter of started activities is incremented in onActivityStarted() and decremented in onActivityStopped(),
 * - When the counter changes from 0 to 1, we enter foreground and call the onForeground(),
 * - When the counter changes from 1 to 0, a timer is started to call onBackground() 700ms after.
 *
 * The background timer is cleared if we re-enter in the foreground.
 * The onForeground() and onBackground() operations are called from the executor. They maintain the final state
 * and call the job service onEnterForeground() and onEnterBackground() when the final state is changed.
 *
 * When an application is started in background, the MainActivity is created and then stopped quite immediately.
 * During that time, we enter in foreground mode and then leave it 700ms after the main activity is stopped.
 * To prevent that, an initial delay to enter in the foreground mode is set.
 */
public class LifecycleCallbacksImpl implements Application.ActivityLifecycleCallbacks {
    private static final String LOG_TAG = "LifecycleCallbacksImpl";
    private static final boolean DEBUG = false;

    private static final int ENTER_FOREGROUND_FIRST_DELAY = 500; // ms
    private static final int ENTER_BACKGROUND_DELAY = 700; // ms

    enum State {
        FOREGROUND, BACKGROUND
    }

    private final AndroidJobServiceImpl mJobService;
    private State mState = State.BACKGROUND;
    private long mCount;
    private final ScheduledExecutorService mExecutor;
    private volatile ScheduledFuture<?> mBackgroundFuture;
    private volatile ScheduledFuture<?> mForegroundFuture;
    private long mEnterForegroundDelay = ENTER_FOREGROUND_FIRST_DELAY;
    private long mForegroundStartTime = 0;
    private long mBackgroundStartTime = 0;
    private long mTotalBackgroundTime = 0;
    private long mTotalForegroundTime = 0;

    public LifecycleCallbacksImpl(@NonNull AndroidJobServiceImpl jobService, @NonNull ScheduledExecutorService executor) {
        if (DEBUG) {
            Log.e(LOG_TAG, "Create LifecycleCallbacksImpl");
        }

        mJobService = jobService;
        mExecutor = executor;
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, Bundle bundle) {
        if (DEBUG) {
            Log.e(LOG_TAG, "Create activity " + activity.getClass().getName());
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (DEBUG) {
            Log.e(LOG_TAG, "Start activity " + activity.getClass().getName());
        }

        mCount++;
        if (mCount == 1 && !activity.isChangingConfigurations()) {
            if (mBackgroundFuture != null) {
                mBackgroundFuture.cancel(true);
                mBackgroundFuture = null;
            }
            if (mEnterForegroundDelay > 0) {
                mForegroundFuture = mExecutor.schedule(this::onForeground, mEnterForegroundDelay, TimeUnit.MILLISECONDS);
            } else {
                mExecutor.execute(this::onForeground);
            }
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        if (DEBUG) {
            Log.e(LOG_TAG, "Resume activity " + activity.getClass().getName());
        }
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        if (DEBUG) {
            Log.e(LOG_TAG, "Pause activity " + activity.getClass().getName());
        }
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        if (DEBUG) {
            Log.e(LOG_TAG, "Stop activity " + activity.getClass().getName());
        }

        mCount--;
        if (mCount == 0 && !activity.isChangingConfigurations()) {
            // If a onForeground() was scheduled during the startup, we can cancel it because we are now in background.
            // This occurs only when the application starts in background: the MainActivity is created and stopped.
            if (mForegroundFuture != null) {
                mForegroundFuture.cancel(true);
                mForegroundFuture = null;
            }
            mBackgroundFuture = mExecutor.schedule(this::onBackground, ENTER_BACKGROUND_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
        if (DEBUG) {
            Log.d(LOG_TAG, "Save activity " + activity.getClass().getName());
        }
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        if (DEBUG) {
            Log.e(LOG_TAG, "Destoy activity " + activity.getClass().getName());
        }
    }

    synchronized long getForegroundTime() {
        if (DEBUG) {
            Log.e(LOG_TAG, "getForegroundTime");
        }

        if (mState == State.FOREGROUND) {
            return mTotalForegroundTime + SystemClock.elapsedRealtime() - mForegroundStartTime;
        } else {
            return mTotalForegroundTime;
        }
    }

    synchronized long getBackgroundTime() {
        if (DEBUG) {
            Log.e(LOG_TAG, "getBackgroundTime");
        }

        if (mState == State.BACKGROUND && mBackgroundStartTime > 0) {
            return mTotalBackgroundTime + SystemClock.elapsedRealtime() - mBackgroundStartTime;
        } else {
            return mTotalBackgroundTime;
        }
    }

    synchronized void resetTime() {
        if (DEBUG) {
            Log.e(LOG_TAG, "resetTime");
        }

        mTotalBackgroundTime = 0;
        mTotalForegroundTime = 0;
        if (mState == State.BACKGROUND) {
            mBackgroundStartTime = SystemClock.elapsedRealtime();
        } else {
            mForegroundStartTime = SystemClock.elapsedRealtime();
        }
    }

    private void onForeground() {
        if (DEBUG) {
            Log.e(LOG_TAG, "onForeground");
        }

        mForegroundFuture = null;
        mEnterForegroundDelay = 0;

        final boolean changed;
        synchronized (this) {
            changed = mState != State.FOREGROUND;
            if (changed) {
                mState = State.FOREGROUND;
                mForegroundStartTime = SystemClock.elapsedRealtime();
                if (mBackgroundStartTime > 0) {
                    mTotalBackgroundTime += mForegroundStartTime - mBackgroundStartTime;
                }
            }
        }
        if (changed) {
            if (DEBUG) {
                Log.e(LOG_TAG, "Enter foreground mode");
            }

            mJobService.onEnterForeground();
        }
    }

    private void onBackground() {
        if (DEBUG) {
            Log.e(LOG_TAG, "onBackground");
        }

        mBackgroundFuture = null;
        mEnterForegroundDelay = 0;

        final boolean changed;
        synchronized (this) {
            changed = mState != State.BACKGROUND;
            if (changed) {
                mState = State.BACKGROUND;
                mBackgroundStartTime = SystemClock.elapsedRealtime();
                if (mForegroundStartTime > 0) {
                    mTotalForegroundTime += mBackgroundStartTime - mForegroundStartTime;
                }
            }
        }
        if (changed) {
            if (DEBUG) {
                Log.e(LOG_TAG, "Enter background mode");
            }

            mJobService.onEnterBackground();
        }
    }
}
