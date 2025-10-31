/*
 *  Copyright (c) 2018 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

@SuppressWarnings("unused")
public interface DebugService extends BaseService {

    String VERSION = "1.0.0";

    class DebugServiceConfiguration extends BaseServiceConfiguration {

        public DebugServiceConfiguration() {

            super(BaseServiceId.ACCOUNT_SERVICE_ID, VERSION, true);
        }
    }

    /**
     * Get the list of possible dumps.
     *
     * @return a list of names representing each possible dump to ask.
     */
    @NonNull
    List<String> getDumpNames();

    /**
     * Get a dump by known its name.
     *
     * @param name the dump name to get.
     * @return the dump as a list of row/columns of strings, or null.
     */
    @Nullable
    List<String[]> getLogs(String name);

    /**
     * Make a backup of the SQLite database on the SD card.
     */
    void backupDatabase();

    /**
     * Restore a database backup and stop the application.
     */
    void restoreDatabase();
}
