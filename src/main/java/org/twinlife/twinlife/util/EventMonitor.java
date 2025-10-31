/*
 *  Copyright (c) 2018-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;
import androidx.collection.CircularArray;

import android.os.SystemClock;
import android.util.Log;

import org.twinlife.twinlife.BuildConfig;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Provides a simple event monitor that collects interesting events in the application in
 * a circular buffer.  Event operations are static methods to make its usage simple and allow
 * to replace this class implementation with dummy empty methods.  The class also collects
 * some performance measurements that can be associated with events.
 * <p>
 * Events can be retrieved and displayed in a debug activity view.
 * <p>
 * Examples:
 * <p>
 * EventMonitor.event("Network connected");
 * ...
 * final long start = System.currentTimeMillis();
 * ...
 * EventMonitor.event("Connected to server", start);
 * ...
 * EventMonitor.event("Accept audio peer");
 * ...
 * EventMonitor.event("Close audio peer");
 *
 * The EventMonitor is enabled or disabled according to the build.gradle configuration by using:
 *
 * buildConfigField "boolean", "ENABLE_EVENT_MONITOR", "true"
 *
 * When disabled, the event() and info() operations are empty.
 */

public class EventMonitor {
    private static final String LOG_TAG = "EventMonitor";
    private static final boolean DEBUG = BuildConfig.ENABLE_EVENT_MONITOR;

    private final HashMap<String, Measure> mMeasures = new HashMap<>();
    private final CircularArray<Event> mEvents = new CircularArray<>();
    private static final int MAX_EVENT_COUNT = 200;
    private static final EventMonitor mMonitor = new EventMonitor();

    /**
     * Record an event that occurred at some time.
     */

    @SuppressWarnings("WeakerAccess")
    public static final class Event {
        private final long mStartTime;
        private final long mEndTime;
        private final String mTitle;

        Event(final String title, final long startTime, final long endTime) {

            mTitle = title;
            mStartTime = startTime;
            mEndTime = endTime;
        }

        public String getTitle() {

            return mTitle;
        }

        @NonNull
        public String toString() {

            final String title;
            final Date date;
            if (mEndTime != mStartTime && mEndTime != 0) {
                long dt = mEndTime - mStartTime;
                if (dt < 1000) {
                    title = String.format(Locale.ENGLISH, "%s [%3d ms]", mTitle, dt);
                } else {
                    title = String.format(Locale.ENGLISH, "%s [%3d.%03d s]", mTitle, dt / 1000, dt % 1000);
                }
                date = new Date(mEndTime);
            } else {
                title = mTitle;
                date = new Date(mStartTime);
            }
            final Calendar calendar = GregorianCalendar.getInstance();
            calendar.setTime(date);
            return String.format(Locale.ENGLISH, "%02d:%02d:%02d %s", calendar.get(Calendar.HOUR_OF_DAY),
                    calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND), title);
        }
    }

    /**
     * Record a performance measure.
     */

    private static final class Measure {
        private long mTotalTime;
        private long mMaxTime;
        private int mCount;

        Measure() {

            mCount = 0;
            mMaxTime = 0;
            mTotalTime = 0;
        }

        void add(final long startTime, final long endTime, final int count) {

            final long dt = endTime - startTime;
            if (dt > mMaxTime) {
                mMaxTime = dt;
            }
            mCount += count;
            mTotalTime = mTotalTime + dt;
        }
    }

    private synchronized void addEvent(final String title, final long startTime, final long endTime, final int count) {

        if (mEvents.size() >= MAX_EVENT_COUNT) {
            mEvents.popFirst();
        }
        mEvents.addLast(new Event(title, startTime, endTime));
        if (startTime != 0 && endTime != 0) {
            Measure m = mMeasures.get(title);
            if (m == null) {
                m = new Measure();
                mMeasures.put(title, m);
            }
            m.add(startTime, endTime, count);
        }
    }

    private synchronized CircularArray<Event> getEventsInternal() {

        CircularArray<Event> result = new CircularArray<>();
        for (int i = 0; i < mEvents.size(); i++) {
            result.addLast(mEvents.get(i));
        }
        return result;
    }

    private synchronized List<String[]> getMeasuresInternal() {

        final List<String[]> result = new ArrayList<>();
        result.add(new String[]{"Title", "Count", "Total time", "Call time"});
        for (final Map.Entry<String, Measure> measure : mMeasures.entrySet()) {
            final Measure m = measure.getValue();

            result.add(new String[]{
                    measure.getKey(),
                    String.valueOf(m.mCount),
                    formatDuration(m.mTotalTime),
                    formatDuration(m.mTotalTime / m.mCount)
            });
        }
        return result;
    }

    public static long start() {

        return BuildConfig.ENABLE_EVENT_MONITOR ? System.currentTimeMillis() : 0;
    }

    /**
     * Format a duration to have a printable user friendly representation.
     * <p>
     * Produces the following formats:
     * secs.msecs  if duration < 60
     * mins:secs   if duration < 3600
     * hours:mins:secs if duration >= 3600
     *
     * @param duration the duration to format.
     * @return the string representing the duration.
     */
    public static String formatDuration(long duration) {

        long secs = duration / 1000;
        long mins = secs / 60;
        long hours = mins / 60;
        if (hours > 0) {
            mins = mins % 60;
            secs = secs % 60;
            return String.format(Locale.ENGLISH, "%2d:%02d:%02d", hours, mins, secs);
        } else if (mins > 0) {
            return String.format(Locale.ENGLISH, "%2d:%02d", mins, secs);
        } else {
            duration = duration % 1000;
            return String.format(Locale.ENGLISH, "%2d.%03d", secs, duration);
        }
    }

    public static String formatNanoDuration(long duration) {

        long usec = duration / 1000L;
        long msec = usec / 1000;
        long secs = msec / 1000;
        if (secs > 0) {
            msec = msec % 1000;
            return String.format(Locale.ENGLISH, "%2d.%03d s", secs, msec);
        } else if (msec > 0) {
            usec = usec % 1000;
            return String.format(Locale.ENGLISH, "%3d.%03d ms", msec, usec);
        } else {
            duration = duration % 1000;
            return String.format(Locale.ENGLISH, "%3d.%03d us", usec, duration);
        }
    }

    public static void event(final String title, final long startTime) {

        if (DEBUG) {
            final long endTime = System.currentTimeMillis();
            Log.i(LOG_TAG, title + " " + formatDuration(endTime - startTime));
            mMonitor.addEvent(title, startTime, endTime, 1);
        }
    }

    public static void fevent(final String title, final long startTime) {

        if (DEBUG) {
            final long endTime = System.nanoTime();
            Log.i(LOG_TAG, title + " " + formatNanoDuration(endTime - startTime));
            mMonitor.addEvent(title, startTime, endTime, 1);
        }
    }

    public static void event(final String title, final long startTime, final int count) {

        if (DEBUG) {
            final long endTime = System.currentTimeMillis();
            Log.i(LOG_TAG, title + " " + formatDuration(endTime - startTime));
            mMonitor.addEvent(title, startTime, endTime, count);
        }
    }

    public static void event(final String title) {

        if (DEBUG) {
            final long now = System.currentTimeMillis();

            Log.i(LOG_TAG, title);
            mMonitor.addEvent(title, now, 0, 1);
        }
    }

    public static void event(final String title, final String arg1) {

        if (DEBUG) {
            final String msg = title + arg1;
            Log.i(LOG_TAG, msg);
            mMonitor.addEvent(msg, System.currentTimeMillis(), 0, 1);
        }
    }

    public static void event(final String title, final String arg1, final long startTime) {

        if (DEBUG) {
            final String msg = title + arg1;
            Log.i(LOG_TAG, msg);
            mMonitor.addEvent(msg, startTime, SystemClock.elapsedRealtime(), 1);
        }
    }

    public static void info(final String tag, final String title) {

        if (DEBUG) {
            Log.i(tag, title);
            mMonitor.addEvent(title, System.currentTimeMillis(), 0, 1);
        }
    }

    public static void info(final String tag, final String title, final Object... args) {

        if (DEBUG) {
            final StringBuilder msg = new StringBuilder();

            msg.append(title);
            for (Object arg : args) {
                if (arg == null) {
                    msg.append("null");
                } else if (arg instanceof UUID) {
                    msg.append(Utils.toLog((UUID) arg));
                } else {
                    msg.append(arg);
                }
            }
            info(tag, msg.toString());
        }
    }

    public static CircularArray<Event> getEvents() {

        return mMonitor.getEventsInternal();
    }

    public static List<String[]> getMeasures() {

        return mMonitor.getMeasuresInternal();
    }
}
