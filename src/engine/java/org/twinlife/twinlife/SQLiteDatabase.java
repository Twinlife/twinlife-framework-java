/*
 *  Copyright (c) 2022-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.content.ContentValues;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.database.SQLException;
import android.database.sqlite.SQLiteFullException;
import android.database.sqlite.SQLiteDiskIOException;

/**
 * Implement the database interface on top of Android SQLcipher API.
 */
public class SQLiteDatabase implements Database {
    private final android.database.sqlite.SQLiteDatabase mDatabase;

    SQLiteDatabase(@NonNull android.database.sqlite.SQLiteDatabase database) {

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
    public void endTransaction() {

        mDatabase.endTransaction();
    }

    @Override
    public void execSQL(@NonNull String sql) throws DatabaseException {

        try {
            mDatabase.execSQL(sql);
        } catch (SQLException exception) {

            raiseException(exception);
        }
    }

    @Override
    public void execSQLWithArgs(@NonNull String sql, String[] args) throws DatabaseException {

        try {
            mDatabase.execSQL(sql, args);
        } catch (SQLException exception) {

            raiseException(exception);
        }
    }

    @Override
    public long insert(@NonNull String tablename, @Nullable String sql,
                       @NonNull ContentValues values) throws DatabaseException {
        try {
            return (int) mDatabase.insert(tablename, sql, values);
        } catch (SQLException exception) {

            raiseException(exception);
        }
        return -1;
    }

    @Override
    public long insertOrThrow(@NonNull String tablename, @Nullable String sql,
                              @NonNull ContentValues values) throws DatabaseException {
        try {
            return mDatabase.insertOrThrow(tablename, sql, values);
        } catch (SQLException exception) {

            raiseException(exception);
        }
        return -1;
    }

    @Override
    public long insertOrReplace(@NonNull String tablename,
                                @NonNull ContentValues values) throws DatabaseException {
        try {
            return mDatabase.insertWithOnConflict(tablename, null, values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
        } catch (SQLException exception) {

            raiseException(exception);
        }
        return -1;
    }

    @Override
    public int update(@NonNull String tablename, @NonNull ContentValues values,
                      @NonNull String sql, String[] args) throws DatabaseException {
        try {
            return mDatabase.update(tablename, values, sql, args);
        } catch (SQLException exception) {

            raiseException(exception);
            return 0;
        }
    }

    @Override
    public int delete(@NonNull String tablename, @NonNull String sql, String[] args) throws DatabaseException {

        try {
            return mDatabase.delete(tablename, sql, args);
        } catch (SQLException exception) {

            raiseException(exception);
            return 0;
        }
    }

    @Override
    public DatabaseCursor rawQuery(@NonNull String sql, String[] args) throws DatabaseException {

        try {
            return new SQLiteDatabaseCursor(mDatabase.rawQuery(sql, args));
        } catch (SQLException exception) {

            raiseException(exception);
            return null;
        }
    }

    private void raiseException(@NonNull SQLException exception) throws DatabaseException {

        if (exception instanceof SQLiteFullException) {
            throw new DatabaseException(exception.getMessage()) {
                @Override
                public boolean isDatabaseFull() {
                    return true;
                }
            };
        } else if (exception instanceof SQLiteDiskIOException) {
            throw new DatabaseException(exception.getMessage()) {
                @Override
                public boolean isDiskError() {
                    return true;
                }
            };
        } else {
            throw new DatabaseException(exception.getMessage());
        }
    }
}
