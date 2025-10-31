/*
 *  Copyright (c) 2022-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import android.database.Cursor;

/**
 * Implement the database cursor interface on top of Android SQLcipher API.
 */
public class SQLiteDatabaseCursor implements DatabaseCursor {
    @NonNull
    private final Cursor mCursor;

    SQLiteDatabaseCursor(@NonNull Cursor cursor) {

        mCursor = cursor;
    }

    /**
     * Returns whether the cursor is pointing to the position after the last
     * row.
     *
     * @return whether the cursor is after the last result.
     */
    @Override
    public boolean isAfterLast() {

        return mCursor.isAfterLast();
    }

    /**
     * Move the cursor to the first row.
     *
     * <p>This method will return false if the cursor is empty.
     *
     * @return whether the move succeeded.
     */
    @Override
    public boolean moveToFirst() {

        return mCursor.moveToFirst();
    }

    /**
     * Move the cursor to the next row.
     *
     * <p>This method will return false if the cursor is already past the
     * last entry in the result set.
     *
     * @return whether the move succeeded.
     */
    @Override
    public boolean moveToNext() {

        return mCursor.moveToNext();
    }

    /**
     * Returns the value of the requested column as a String.
     *
     * <p>The result and whether this method throws an exception when the
     * column value is null or the column type is not a string type is
     * implementation-defined.
     *
     * @param columnIndex the zero-based index of the target column.
     * @return the value of that column as a String.
     */
    @Override
    public String getString(int columnIndex) {

        return mCursor.getString(columnIndex);
    }

    /**
     * Returns the value of the requested column as an int.
     *
     * <p>The result and whether this method throws an exception when the
     * column value is null, the column type is not an integral type, or the
     * integer value is outside the range [<code>Integer.MIN_VALUE</code>,
     * <code>Integer.MAX_VALUE</code>] is implementation-defined.
     *
     * @param columnIndex the zero-based index of the target column.
     * @return the value of that column as an int.
     */
    @Override
    public int getInt(int columnIndex) {

        return mCursor.getInt(columnIndex);
    }

    /**
     * Returns the value of the requested column as a long.
     *
     * <p>The result and whether this method throws an exception when the
     * column value is null, the column type is not an integral type, or the
     * integer value is outside the range [<code>Long.MIN_VALUE</code>,
     * <code>Long.MAX_VALUE</code>] is implementation-defined.
     *
     * @param columnIndex the zero-based index of the target column.
     * @return the value of that column as a long.
     */
    @Override
    public long getLong(int columnIndex) {

        return mCursor.getLong(columnIndex);
    }

    /**
     * Returns the value of the requested column as a byte array.
     *
     * <p>The result and whether this method throws an exception when the
     * column value is null or the column type is not a blob type is
     * implementation-defined.
     *
     * @param columnIndex the zero-based index of the target column.
     * @return the value of that column as a byte array.
     */
    @Override
    public byte[] getBlob(int columnIndex) {

        return mCursor.getBlob(columnIndex);
    }

    /**
     * Returns <code>true</code> if the value in the indicated column is null.
     *
     * @param columnIndex the zero-based index of the target column.
     * @return whether the column value is null.
     */
    @Override
    public boolean isNull(int columnIndex) {

        return mCursor.isNull(columnIndex);
    }

    /**
     * Closes the Cursor, releasing all of its resources and making it completely invalid.
     */
    @Override
    public void close() {

        mCursor.close();
    }
}
