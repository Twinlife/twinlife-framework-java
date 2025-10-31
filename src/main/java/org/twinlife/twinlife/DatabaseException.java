/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import java.io.IOException;

/**
 * A database exception that can be raised while executing some database operation.
 */
public class DatabaseException extends IOException {

    public DatabaseException() {

        super();
    }

    public DatabaseException(Exception exception) {

        super(exception);
    }

    public DatabaseException(String message) {

        super(message);
    }

    /**
     * Check if the error is caused by a database full.
     *
     * @return true if the database is full.
     */
    public boolean isDatabaseFull() {

        return false;
    }

    /**
     * Check if the error is caused by some disk IO error.
     *
     * @return true if there was a disk IO error.
     */
    public boolean isDiskError() {

        return false;
    }
}
