/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import android.util.Log;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;

import java.util.UUID;

/**
 * Helper class to define static methods to dump some database tables (only for debugging).
 *
 * Method code must be enclosed with if (ENABLE_DUMP) {} to make sure the code is not present in production builds.
 */
public class DatabaseDump {
    private static final String LOG_TAG = "DatabaseDump";
    private static final boolean DEBUG = BuildConfig.ENABLE_EVENT_MONITOR;
    private static final boolean ENABLE_DUMP = BuildConfig.ENABLE_EVENT_MONITOR;

    public static void printTwincodeOutbound(@NonNull DatabaseServiceImpl database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dump twincodes");
        }

        if (ENABLE_DUMP) {
            String sql = "SELECT id, twincodeId, name FROM twincodeOutbound";
            Log.d(LOG_TAG, "Twincodes OUT:");
            try (DatabaseCursor cursor = database.rawQuery(sql, null)) {

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    UUID uuid = cursor.getUUID(1);
                    String name = cursor.getString(2);

                    Log.d(LOG_TAG, "TOUT " + id + " id=" + uuid + " n=" + name);
                }
            } catch (DatabaseException exception) {
                Log.e(LOG_TAG, "Database exception", exception);
            }
        }
    }

    public static void printRepository(@NonNull DatabaseServiceImpl database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dump repository");
        }

        if (ENABLE_DUMP) {
            String sql = "SELECT id, twincodeId, name FROM twincodeOutbound";
            Log.d(LOG_TAG, "Twincodes OUT:");
            try (DatabaseCursor cursor = database.rawQuery(sql, null)) {

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    UUID uuid = cursor.getUUID(1);
                    String name = cursor.getString(2);

                    Log.d(LOG_TAG, "TOUT " + id + " id=" + uuid + " n=" + name);
                }
            } catch (DatabaseException exception) {
                Log.e(LOG_TAG, "Database exception", exception);
            }
            String sql2 = "SELECT id, twincodeId FROM twincodeInbound";
            Log.d(LOG_TAG, "Twincodes IN:");
            try (DatabaseCursor cursor = database.rawQuery(sql2, null)) {

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    UUID uuid = cursor.getUUID(1);

                    Log.d(LOG_TAG, "TIN " + id + " id=" + uuid);
                }
            } catch (DatabaseException exception) {
                Log.e(LOG_TAG, "Database exception", exception);
            }

            Log.d(LOG_TAG, "Repository");
            String query = "SELECT "
                    + " r.id, r.uuid, r.schemaId, r.name, r.owner,"
                    + " r.twincodeInbound, r.twincodeOutbound, r.peerTwincodeOutbound"
                    + " FROM repository AS r";

            try (DatabaseCursor cursor = database.rawQuery(query, null)) {

                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    UUID uuid = cursor.getUUID(1);
                    UUID schemaId = cursor.getUUID(2);
                    String name = cursor.getString(3);
                    long owner = cursor.getLong(4);
                    long twin = cursor.getLong(5);
                    long twout = cursor.getLong(6);
                    long pwout = cursor.getLong(7);
                    Log.d(LOG_TAG, "" + id + " id=" + uuid + " s=" + schemaId + " " + name + " o=" + owner + " in=" + twin
                            + " out=" + twout + " peer=" + pwout);
                }
            } catch (DatabaseException exception) {
                Log.e(LOG_TAG, "Database exception", exception);
            }
        }
    }

}