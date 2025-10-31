/*
 *  Copyright (c) 2022-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.content.ContentValues;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.sqlcipher.Cursor;
import net.sqlcipher.SQLException;
import net.sqlcipher.database.SQLiteDatabase;
import net.sqlcipher.database.SQLiteFullException;
import net.sqlcipher.database.SQLiteDiskIOException;

/**
 * Implement the database interface on top of Android SQLcipher API.
 */
public class AndroidDatabase implements Database {
    private static final String LOG_TAG = "AndroidDatabase";
    private static final boolean DEBUG = false;
    private final SQLiteDatabase mDatabase;

    AndroidDatabase(@NonNull SQLiteDatabase database) {

        mDatabase = database;
    }

    @Override
    public String getPath() {

        return mDatabase.getPath();
    }

    @Override
    public void beginTransaction() {

        mDatabase.beginTransaction();
    }

    @Override
    public void setTransactionSuccessful() {

        mDatabase.setTransactionSuccessful();
    }

    @Override
    public void endTransaction() throws DatabaseException {

        try {
            mDatabase.endTransaction();
        } catch (SQLException exception) {

            raiseException(exception);
        }
    }

    @Override
    public boolean hasTable(@NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasTable: name=" + name);
        }

        try (Cursor cursor = mDatabase.rawQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name=?",
                new String[] { name })) {
            if (cursor.moveToFirst()) {
                if (cursor.isNull(0)) {
                    return false;
                }
                return cursor.getLong(0) != 0;
            }
            return false;

        } catch (SQLException exception) {

            return false;
        }
    }

    @Override
    public void execSQL(@NonNull String sql) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execSQL: sql=" + sql);
        }

        try {
            mDatabase.rawExecSQL(sql);
        } catch (SQLException exception) {

            raiseException(exception);
        }
    }

    @Override
    public void execSQLWithArgs(@NonNull String sql, String[] args) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "execSQLWithArgs: sql=" + sql);
        }

        try {
            mDatabase.execSQL(sql, args);
        } catch (SQLException exception) {

            raiseException(exception);
        }
    }

    @Override
    public long insert(@NonNull String tablename, @Nullable String sql,
                       @NonNull ContentValues values) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "insert: tablename=" + tablename + " sql=" + sql);
        }

        return mDatabase.insert(tablename, sql, values);
    }

    @Override
    public long insertOrThrow(@NonNull String tablename, @Nullable String sql,
                              @NonNull ContentValues values) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "insertOrThrow: tablename=" + tablename + " sql=" + sql);
        }

        try {
            return mDatabase.insertWithOnConflict(tablename, sql, values, SQLiteDatabase.CONFLICT_NONE);
        } catch (SQLException exception) {

            raiseException(exception);
        }
        return -1;
    }

    @Override
    public long insertOrIgnore(@NonNull String tablename, @Nullable String sql,
                               @NonNull ContentValues values) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "insertOrIgnore: tablename=" + tablename + " sql=" + sql);
        }

        try {
            return mDatabase.insertWithOnConflict(tablename, sql, values, SQLiteDatabase.CONFLICT_IGNORE);
        } catch (SQLException exception) {

            raiseException(exception);
        }
        return -1;
    }

    @Override
    public long insertOrReplace(@NonNull String tablename,
                                @NonNull ContentValues values) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "insertOrReplace: tablename=" + tablename + " values=" + values);
        }

        try {
            return mDatabase.insertWithOnConflict(tablename, null, values, SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException exception) {

            raiseException(exception);
        }
        return -1;
    }

    @Override
    public int update(@NonNull String tablename, @NonNull ContentValues values,
                      @NonNull String sql, String[] args) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "update: tablename=" + tablename + " values=" + values + " sql=" + sql);
        }

        try {
            return mDatabase.update(tablename, values, sql, args);
        } catch (SQLException exception) {

            raiseException(exception);
            return 0;
        }
    }

    @Override
    public int delete(@NonNull String tablename, @NonNull String sql, Object[] args) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "delete: tablename=" + tablename + " sql=" + sql);
        }

        try {
            return mDatabase.delete(tablename, sql, args);
        } catch (SQLException exception) {

            raiseException(exception);
            return 0;
        }
    }

    @Override
    public DatabaseCursor rawQuery(@NonNull String sql, String[] args) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "rawQuery: sql=" + sql);
        }

        try {
            return new AndroidDatabaseCursor(mDatabase.rawQuery(sql, args));
        } catch (SQLException exception) {

            raiseException(exception);
            return null;
        }
    }

    @Override
    @Nullable
    public Long longQuery(@NonNull String sql, Object[] args) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "longQuery: sql=" + sql);
        }

        try (Cursor cursor = mDatabase.rawQuery(sql, args)) {
            if (cursor.moveToFirst()) {
                if (cursor.isNull(0)) {
                    return null;
                }
                return cursor.getLong(0);
            }
            return null;

        } catch (SQLException exception) {

            raiseException(exception);
            return null;
        }
    }

    static void raiseException(@NonNull SQLException exception) throws DatabaseException {

        if (exception instanceof SQLiteFullException) {
            throw new DatabaseException(exception) {
                @Override
                public boolean isDatabaseFull() {
                    return true;
                }
            };
        } else if (exception instanceof SQLiteDiskIOException) {
            throw new DatabaseException(exception) {
                @Override
                public boolean isDiskError() {
                    return true;
                }
            };
        } else {
            throw new DatabaseException(exception);
        }
    }
}
