/*
 *  Copyright (c) 2014-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import androidx.annotation.NonNull;
import android.util.Log;

import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseObject;
import org.twinlife.twinlife.DatabaseTable;

import java.util.UUID;

/**
 * Database service provider helper class.
 */
public class DatabaseServiceProvider {
    private static final String LOG_TAG = "DatabaseServiceProvider";
    private static final boolean DEBUG = false;

    @NonNull
    protected final DatabaseServiceImpl mDatabase;
    @NonNull
    protected final String mCreateTable;
    @NonNull
    protected final DatabaseTable mTable;
    protected final BaseServiceImpl<?> mService;

    protected DatabaseServiceProvider(@NonNull BaseServiceImpl<?> service,
                                      @NonNull DatabaseServiceImpl database,
                                      @NonNull String sqlCreate,
                                      @NonNull DatabaseTable primaryTable) {
        if (DEBUG) {
            Log.d(LOG_TAG, "DatabaseServiceProvider: database=" + database);
        }

        mService = service;
        mDatabase = database;
        mCreateTable = sqlCreate;
        mTable = primaryTable;
        database.addServiceProvider(this);
    }

    protected void onCreate(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate: SQL=" + mCreateTable);
        }

        transaction.createSchema(mCreateTable);
    }

    protected void onUpgrade(@NonNull Transaction transaction, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade: oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        /*
         * <pre>
         * Database Version 20
         *  Date: 2023/08/29
         *   New database model with several tables that change the primary key
         * </pre>
         */
        transaction.createSchema(mCreateTable);
    }

    protected void onOpen() throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOpen");
        }
    }

    /**
     * Create a transaction to prepare for database insert/update/delete.
     *
     * @return the transaction.
     */
    @NonNull
    protected Transaction newTransaction() {

        return new Transaction(mDatabase);
    }

    @NonNull
    public DatabaseTable getKind() {

        return mTable;
    }

    /**
     * Delete the database object under a transaction.
     *
     * @param object the database object to delete.
     */
    public void deleteObject(@NonNull DatabaseObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObject: object=" + object);
        }

        try (Transaction transaction = newTransaction()) {
            transaction.deleteObject(object);
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Delete the object knowing the UUID.
     *
     * @param objectId the object id to delete.
     */
    public void deleteUUID(@NonNull UUID objectId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteUUID: objectId=" + objectId);
        }

        try (Transaction transaction = newTransaction()) {
            transaction.deleteObjectTable(objectId, DatabaseTable.TABLE_TWINCODE_OUTBOUND);
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }
}
