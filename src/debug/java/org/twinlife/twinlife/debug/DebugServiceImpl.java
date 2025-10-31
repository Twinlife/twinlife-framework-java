/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.debug;


import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import net.sqlcipher.database.SQLiteDatabase;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.DebugService;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.conversation.ConversationsDump;
import org.twinlife.twinlife.conversation.OperationsDump;
import org.twinlife.twinlife.notification.NotificationDump;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A simple debug service that produces dumps of various interesting parts to help in identifying issues.
 *
 * The DebugServiceImpl and all the related dump classes are available only when the project is compiled
 * with the "dev" flavor.  Hence, it has no impact on production flavor.
 */
public class DebugServiceImpl extends BaseServiceImpl implements DebugService {
    private static final String LOG_TAG = "DebugServiceImpl";
    private static final boolean DEBUG = false;

    private SQLiteDatabase mDatabase;
    private final Map<String, DumpListGenerator> mDumpGenerators = new HashMap<>();

    public static final String EVENTS_NAME = "Events";
    public static final String PERFORMANCE_NAME = "Perf events";
    public static final String CONVERSATIONS_NAME = "Conversations";
    public static final String NOTIFICATIONS_NAME = "Notifications";
    public static final String OPERATIONS_NAME = "Operations";
    public static final String DB_CONVERSATIONS_NAME = "DB Conversations";

    /**
     * Interface for a debug generator to produce a dump of interesting items.
     */
    public interface DumpListGenerator {

        /**
         * Produce the dump.
         *
         * @return A list of row/colums representing the dump.
         */
        List<String[]> getDump();
    }

    public DebugServiceImpl(TwinlifeImpl twinlifeImpl, Connection connection) {

        super(twinlifeImpl, connection);

        mDumpGenerators.put(CONVERSATIONS_NAME, new ConversationsDump(twinlifeImpl));
        mDumpGenerators.put(NOTIFICATIONS_NAME, new NotificationDump(twinlifeImpl));
        mDumpGenerators.put(DB_CONVERSATIONS_NAME, new DatabaseConversationDump());
        mDumpGenerators.put(OPERATIONS_NAME, new OperationsDump(twinlifeImpl));
        mDumpGenerators.put(EVENTS_NAME, new EventMonitorDump());
        mDumpGenerators.put(PERFORMANCE_NAME, new PerfMonitorDump());
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        setServiceConfiguration(baseServiceConfiguration);
        setServiceOn(true);
        setConfigured(true);
    }

    protected void onOpenDatabase(@NonNull SQLiteDatabase db) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOpen db=" + db);
        }

        // Keep track of the database so that we can make debugging queries on it.
        mDatabase = db;
    }

    static String LIST_CONVERSATION_QUERY =
            "SELECT C.uuid, C.twincodeOutboundId, C.peerTwincodeOutboundId, "
                    + " (SELECT COUNT(*) FROM conversationDescriptor AS D WHERE C.twincodeOutboundId = D.twincodeOutboundId), "
                    + " C.content "
                    + " FROM conversationConversation AS C";

    class DatabaseConversationDump implements DumpListGenerator {

        public List<String[]> getDump() {

            List<String[]> result = new ArrayList<>();

            String[] row = new String[5];
            row[0] = "ID";
            row[1] = "T-OUT";
            row[2] = "P-OUT";
            row[3] = "# msg";
            row[4] = "C-size";
            result.add(row);

            Cursor cursor;
            synchronized (this) {
                cursor = mDatabase.rawQuery(LIST_CONVERSATION_QUERY, new String[]{});
                while (cursor.moveToNext()) {
                    row = new String[5];
                    UUID uuid = Utils.UUIDFromString(cursor.getString(0));
                    UUID twincodeOutboundId = Utils.UUIDFromString(cursor.getString(1));
                    UUID peerTwincodeOutboundId = Utils.UUIDFromString(cursor.getString(2));
                    int count = cursor.getInt(3);
                    byte[] content = cursor.getBlob(4);

                    row[0] = Utils.toLog(uuid);
                    row[1] = Utils.toLog(twincodeOutboundId);
                    row[2] = Utils.toLog(peerTwincodeOutboundId);
                    row[3] = Integer.toString(count);
                    row[4] = Integer.toString(content.length);
                    result.add(row);
                }
                cursor.close();
            }

            return result;
        }
    }

    /**
     * Get the list of possible dumps.
     *
     * @return a list of names representing each possible dump to ask.
     */
    @NonNull
    @Override
    public List<String> getDumpNames() {

        return new ArrayList<>(mDumpGenerators.keySet());
    }

    /**
     * Get a dump by known its name.
     *
     * @param name the dump name to get.
     * @return the dump as a list of row/columns of strings, or null.
     */
    @Override
    @Nullable
    public List<String[]> getLogs(String name) {

        final DumpListGenerator generator = mDumpGenerators.get(name);
        if (generator == null) {
            return null;
        }
        return generator.getDump();
    }

    /**
     * Make a backup of the SQLite database on the SD card.
     */
    @Override
    public void backupDatabase() {
        String path = mDatabase.getPath();

        EventMonitor.info(LOG_TAG, "Backup ", path);
        try {
            File dbFile = new File(path);
            FileInputStream fis = new FileInputStream(dbFile);

            File dir = mTwinlifeImpl.getFilesDir();
            File outFile = new File(dir, "twinme_backup.db");

            // Open the empty db as the output stream
            OutputStream output = new FileOutputStream(outFile);

            // Transfer bytes from the inputfile to the outputfile
            byte[] buffer = new byte[1024];
            int length;
            while ((length = fis.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

            // Close the streams
            output.flush();
            output.close();
            fis.close();
            EventMonitor.info(LOG_TAG, "Backup done in ", outFile.getPath());
        } catch (IOException ex) {
            EventMonitor.info(LOG_TAG, "Backup failed ", ex.getMessage());
        }
    }

    /**
     * Restore a database backup and stop the application.
     */
    @Override
    public void restoreDatabase() {
        String path = mDatabase.getPath();

        EventMonitor.info(LOG_TAG, "Restore ", path);
        try {
            File dir = mTwinlifeImpl.getFilesDir();
            File inFile = new File(dir, "twinme_backup.db");

            // Open the empty db as the output stream
            FileInputStream input = new FileInputStream(inFile);

            // Close the database to make sure SQLite will not write on the file anymore.
            mDatabase.close();

            File dbFile = new File(path);
            FileOutputStream output = new FileOutputStream(dbFile);

            // Transfer bytes from the inputfile to the outputfile
            byte[] buffer = new byte[1024];
            int length;
            while ((length = input.read(buffer)) > 0) {
                output.write(buffer, 0, length);
            }

            // Close the streams
            output.flush();
            output.close();
            input.close();
            EventMonitor.info(LOG_TAG, "Restore done in ", dbFile.getPath());
        } catch (IOException ex) {
            EventMonitor.info(LOG_TAG, "Restore failed ", ex.getMessage());
        }
    }
}
