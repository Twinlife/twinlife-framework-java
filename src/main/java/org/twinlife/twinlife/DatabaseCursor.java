/*
 *  Copyright (c) 2022-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import java.io.Closeable;
import java.util.UUID;

/**
 * A database cursor interface that encapsulates the operations used by the twinlife-framework library
 * and which can be implemented on top of Android SQLcipher library or a Java JDBC connector.
 */
public interface DatabaseCursor extends Closeable {

    /**
     * Returns whether the cursor is pointing to the position after the last
     * row.
     *
     * @return whether the cursor is after the last result.
     */
    boolean isAfterLast() throws DatabaseException;

    /**
     * Move the cursor to the first row.
     *
     * <p>This method will return false if the cursor is empty.
     *
     * @return whether the move succeeded.
     */
    boolean moveToFirst() throws DatabaseException;

    /**
     * Move the cursor to the next row.
     *
     * <p>This method will return false if the cursor is already past the
     * last entry in the result set.
     *
     * @return whether the move succeeded.
     */
    boolean moveToNext() throws DatabaseException;

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
    String getString(int columnIndex) throws DatabaseException;

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
    int getInt(int columnIndex) throws DatabaseException;

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
    long getLong(int columnIndex) throws DatabaseException;

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
    byte[] getBlob(int columnIndex) throws DatabaseException;

    UUID getUUID(int columnIndex) throws DatabaseException;

    /**
     * Returns <code>true</code> if the value in the indicated column is null.
     *
     * @param columnIndex the zero-based index of the target column.
     * @return whether the column value is null.
     */
    boolean isNull(int columnIndex) throws DatabaseException;

    /**
     * Closes the Cursor, releasing all of its resources and making it completely invalid.
     */
    void close();
}
