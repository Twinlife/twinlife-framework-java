/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import android.util.Log;

import org.twinlife.twinlife.BuildConfig;

import java.util.Locale;
import java.util.UUID;

/**
 * Static logger
 */

public class Logger {
    public static final boolean DEBUG = BuildConfig.ENABLE_EVENT_MONITOR;
    public static final boolean INFO = BuildConfig.ENABLE_EVENT_MONITOR;
    public static final boolean WARN = BuildConfig.ENABLE_EVENT_MONITOR;
    public static final boolean ERROR = BuildConfig.ENABLE_EVENT_MONITOR;

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

    private static String format(final String title, final Object[] args) {
        final StringBuilder msg = new StringBuilder();

        msg.append(title);
        for (Object arg : args) {
            if (arg == null) {
                msg.append("null");
            } else if (arg instanceof UUID) {
                msg.append(Utils.toLog((UUID) arg));
            } else if (arg instanceof Exception) {
                Exception exception = (Exception) arg;
                String exceptionMessage = exception.getMessage();
                if (exceptionMessage != null) {
                    msg.append(" ");
                    msg.append(exceptionMessage);
                } else {
                    msg.append(" exception: ");
                    msg.append(exception);
                }
            } else {
                msg.append(arg);
            }
        }

        return msg.toString();
    }

    public static void debug(final String tag, final String title, final Object... args) {

        if (DEBUG) {
            Log.d(tag, format(title, args));
        }
    }

    public static void info(final String tag, final String title, final Object... args) {

        if (INFO) {
            Log.i(tag, format(title, args));
        }
    }

    public static void warn(final String tag, final String title, final Object... args) {

        if (WARN) {
            Log.w(tag, format(title, args));
        }
    }

    public static void error(final String tag, final String title, final Object... args) {

        if (ERROR) {
            Log.e(tag, format(title, args));
        }
    }
}
