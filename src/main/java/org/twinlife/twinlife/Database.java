/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.content.ContentValues;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A database interface that encapsulates the operations used by the twinlife-framework library
 * and which can be implemented on top of Android SQLcipher library or a Java JDBC connector.
 */
public interface Database {

    String getPath();

    void beginTransaction();

    void setTransactionSuccessful();

    void endTransaction() throws DatabaseException;

    boolean hasTable(@NonNull String name);

    void execSQL(@NonNull String sql) throws DatabaseException;

    void execSQLWithArgs(@NonNull String sql, String[] args) throws DatabaseException;

    long insert(@NonNull String tablename, @Nullable String sql,
                @NonNull ContentValues values) throws DatabaseException;

    long insertOrThrow(@NonNull String tablename, @Nullable String sql,
                       @NonNull ContentValues values) throws DatabaseException;

    long insertOrIgnore(@NonNull String tablename, @Nullable String sql,
                       @NonNull ContentValues values) throws DatabaseException;

    long insertOrReplace(@NonNull String tablename,
                         @NonNull ContentValues values) throws DatabaseException;

    int update(@NonNull String tablename, @NonNull ContentValues values,
               @NonNull String sql, String[] args) throws DatabaseException;

    int delete(@NonNull String tablename, @NonNull String sql, Object[] args) throws DatabaseException;

    DatabaseCursor rawQuery(@NonNull String sql, String[] args) throws DatabaseException;

    @Nullable
    Long longQuery(@NonNull String sql, Object[] args) throws DatabaseException;
}
