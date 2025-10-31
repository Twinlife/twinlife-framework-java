/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.crypto;

import android.content.ContentValues;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.SessionKeyPair;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.database.Columns;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.DatabaseServiceProvider;
import org.twinlife.twinlife.database.Tables;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.image.ImageInfo;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundImpl;
import org.twinlife.twinlife.util.Logger;

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.UUID;

class CryptoServiceProvider extends DatabaseServiceProvider {
    private static final String LOG_TAG = "CryptoServiceProv...";
    private static final boolean DEBUG = false;

    // Create a secret and mark it is as ready to be used.
    // Flags are immediately set with USE_SECRET1
    static final int CREATE_SECRET = 0x01;

    // Create the next secret for the relation:
    // - if there is no secret, secret1 is allocated
    // - if secret1 is in use, the next secret is allocated in secret2
    // - if secret2 is in use, the next secret is allocated in secret1
    // Note: secret1 and secret2 cannot be used at the same time.
    // Flags are set with NEW_SECRET1 or NEW_SECRET2 and a call to validateSecrets()
    // is necessary to turn the NEW_SECRETx into the USE_SECRETx flag.
    static final int CREATE_NEXT_SECRET = 0x02;

    // Create the first secret to be exchanged when upgrading a non-encrypted relation to an encrypted one.
    static final int CREATE_FIRST_SECRET = 0x04;

    /**
     * twincodeKeys table:
     * id INTEGER: local database identifier (primary key) == twincode outbound id
     * creationDate INTEGER: key creation date
     * modificationDate INTEGER: key modification date
     * flags INTEGER NOT NULL: various control flags
     * nonceSequence INTEGER: sequence number
     * signingKey BLOB: private or public key for signing
     * encryptionKey BLOB: private or public key for encryption
     */
    private static final String TWINCODE_KEYS_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS twincodeKeys (id INTEGER PRIMARY KEY,"
                    + " creationDate INTEGER NOT NULL, modificationDate INTEGER NOT NULL,"
                    + " flags INTEGER NOT NULL, nonceSequence INTEGER NOT NULL DEFAULT 0,"
                    + " signingKey BLOB, encryptionKey BLOB"
                    + ")";

    /**
     * secretKeys table:
     * id INTEGER NOT NULL: local twincode identifier (primary key)
     * peerTwincodeId INTEGER: peer twincode identifier (primary key)
     * creationDate INTEGER: secret creation date
     * modificationDate INTEGER: secret modification date
     * secretUpdateDate INTEGER: last update date of secret1 or secret2
     * flags INTEGER NOT NULL: various control flags
     * nonceSequence INTEGER NOT NULL: sequence number
     * secret1 BLOB: secret 1
     * secret2 BLOB: secret 2
     * <p>
     * Note:
     *  - we have only one secret for a peer twincode.
     *    a secretKeys for a peerTwincode will have peerTwincodeId == NULL
     *  - we have only one secret for a 1-1 contact.
     *  - we have one secret for each member of a group we are talking to
     *    the 'id' is our identity and the 'peerTwincodeId' is the peer twincode.
     */
    private static final String SECRET_KEYS_CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS secretKeys (id INTEGER NOT NULL,"
                    + " peerTwincodeId INTEGER,"
                    + " creationDate INTEGER NOT NULL, modificationDate INTEGER NOT NULL,"
                    + " secretUpdateDate INTEGER NOT NULL,"
                    + " flags INTEGER NOT NULL DEFAULT 0,"
                    + " nonceSequence INTEGER NOT NULL DEFAULT 0,"
                    + " secret1 BLOB, secret2 BLOB,"
                    + " PRIMARY KEY(id, peerTwincodeId)"
                    + ")";

    private final SecureRandom mRandom;

    CryptoServiceProvider(@NonNull CryptoServiceImpl service,
                          @NonNull DatabaseServiceImpl database) {
        super(service, database, TWINCODE_KEYS_CREATE_TABLE, DatabaseTable.TABLE_TWINCODE_KEYS);
        if (DEBUG) {
            Log.d(LOG_TAG, "CryptoServiceProvider: service=" + service);
        }

        mRandom = new SecureRandom();
    }

    protected void onCreate(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate: SQL=" + mCreateTable);
        }

        super.onCreate(transaction);
        transaction.createSchema(SECRET_KEYS_CREATE_TABLE);
    }

    @Override
    protected void onUpgrade(@NonNull Transaction transaction, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade: oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        /*
         * <pre>
         * Database Version 23
         *  Date: 2024/09/27
         *   New database model with twincodeKeys and secretKeys table
         * </pre>
         */
        onCreate(transaction);
    }
/*
    protected void onOpen() throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOpen");
        }

        try (Transaction transaction = newTransaction()) {

            transaction.execSQLWithArgs("UPDATE twincodeOutbound SET flags = 0 WHERE flags > 1;", new String[] {});
            transaction.execSQLWithArgs("DELETE FROM secretKeys;", new String[] {} );
            transaction.execSQLWithArgs("DELETE FROM twincodeKeys;", new String[] {} );
            transaction.commit();

        } catch (Exception ex) {
            Log.e(LOG_TAG, "Exception when invalidating the keys");

        }
    }*/

    //
    // Package scoped methods
    //

    @Nullable
    KeyInfo loadPeerEncryptionKey(@NonNull UUID twincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadPeerEncryptionKey: twincodeId=" + twincodeId);
        }

        final String[] params = { twincodeId.toString() };
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT k.flags, k.modificationDate, k.signingKey,"
                + " k.encryptionKey,"
                + " twout.id, twout.twincodeId, twout.modificationDate, twout.name,"
                + " twout.avatarId, twout.description, twout.capabilities, twout.attributes, twout.flags"
                + " FROM twincodeOutbound AS twout"
                + " INNER JOIN twincodeKeys AS k ON k.id=twout.id"
                + " WHERE twout.twincodeId=?", params)) {
            if (!cursor.moveToFirst()) {

                return null;
            }

            int flags = cursor.getInt(0);
            long modificationDate = cursor.getLong(1);
            byte[] signingKey = cursor.getBlob(2);
            byte[] encryptionKey = cursor.getBlob(3);
            TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(cursor, 4);
            if (twincodeOutbound == null) {
                return null;
            }

            return new KeyInfo(twincodeOutbound, modificationDate, flags,
                    signingKey, encryptionKey, 0, 0, null);

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    /**
     * Load the twincode signing and encryption keys with flags.
     *
     * @param twincodeOutbound the twincode to load
     * @return the keyinfo with the signing and encryption keys or null if the keys are not found.
     */
    @Nullable
    KeyInfo loadTwincodeKey(@NonNull TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadTwincodeKey: twincodeOutbound=" + twincodeOutbound);
        }

        final long id = twincodeOutbound.getDatabaseId().getId();
        final String[] params = { Long.toString(id) };
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT k.flags, k.modificationDate, k.signingKey,"
                + " k.encryptionKey FROM twincodeKeys AS k WHERE k.id=?", params)) {
            if (!cursor.moveToFirst()) {

                return null;
            }

            int flags = cursor.getInt(0);
            long modificationDate = cursor.getLong(1);
            byte[] signingKey = cursor.getBlob(2);
            byte[] encryptionKey = cursor.getBlob(3);
            return new KeyInfo(twincodeOutbound, modificationDate, flags,
                    signingKey, encryptionKey, 0, 0, null);

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    /**
     * Load the twincode signing and encryption keys with the secret key to talk to the given peer twincode.
     * The `options` controls whether and how a new secret is created and associated with the pair (twincode, peerTwincode).
     *
     * @param twincodeOutbound the twincode to get the signing and encryption keys.
     * @param peerTwincodeOutbound the peer twincode.
     * @param useSequenceCount the number of sequences that we want to use from the nonceSequence
     * @param options options to control the creation or re-creation of secret associated with (twincode, peerTwincode).
     * @return the keyinfo with the signing and encryption keys and secrets or null if the keys are not found.
     */
    @Nullable
    KeyInfo loadTwincodeKeyWithSecret(@NonNull TwincodeOutboundImpl twincodeOutbound, @NonNull TwincodeOutbound peerTwincodeOutbound,
                                      long useSequenceCount, int options) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadTwincodeKeyWithSecret: twincodeOutbound=" + twincodeOutbound
                    + " peerTwincodeOutbound=" + peerTwincodeOutbound + " useSequenceCount=" + useSequenceCount + " options=" + options);
        }

        final byte[] secret;
        if ((options & (CREATE_SECRET | CREATE_NEXT_SECRET | CREATE_FIRST_SECRET)) != 0) {
            secret = new byte[CryptoBox.KEY_LENGTH];
            mRandom.nextBytes(secret);
        } else {
            secret = null;
        }

        final long id = twincodeOutbound.getDatabaseId().getId();
        final long peerId = peerTwincodeOutbound.getDatabaseId().getId();
        final String[] params = { Long.toString(peerId), Long.toString(id) };
        while (true) {
            KeyInfo result;
            long nonceSequence;
            boolean createSecret = false;
            int secretFlags;
            Long secretId;
            try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT k.flags, k.modificationDate, k.signingKey,"
                    + " k.encryptionKey, k.nonceSequence, s.id, s.flags, s.secret1, s.secret2"
                    + " FROM twincodeKeys AS k"
                    + " LEFT JOIN secretKeys AS s ON k.id = s.id AND s.peerTwincodeId=?"
                    + " WHERE k.id=?", params)) {
                if (!cursor.moveToFirst()) {

                    return null;
                }

                int flags = cursor.getInt(0);
                long modificationDate = cursor.getLong(1);
                byte[] signingKey = cursor.getBlob(2);
                byte[] encryptionKey = cursor.getBlob(3);
                nonceSequence = cursor.getLong(4);
                secretId = cursor.isNull(5) ? null : cursor.getLong(5);
                secretFlags = cursor.isNull(6) ? 0 : cursor.getInt(6);
                byte[] secret1 = cursor.getBlob(7);
                byte[] secret2 = cursor.getBlob(8);
                int keyIndex;

                // Create a new secret 1 or secret2 depending on current configuration.  This must be idempotent
                // so that if we want to send a new secret X to the peer, we can repeat the send operation but
                // we create the secret only once.  The new secret is marked with `NEW_SECRET1` or `NEW_SECRET2`
                // until the peer acknowledged its good reception.  At that time, we clear the flag and update
                // the `USE_SECRETx` to reflect the change (see `validateSecrets()`).
                byte[] useSecret;
                if ((options & CREATE_FIRST_SECRET) != 0) {
                    // If secret1 was already created, use it.  If the USE_SECRET1 flag is set, we must not
                    // override the secret with a new one but we must continue using it and sent it even if
                    // the peer already has it.
                    keyIndex = 1;
                    if ((secretFlags & CryptoService.NEW_SECRET1) != 0 || (secretFlags & CryptoService.USE_SECRET1) != 0) {
                        useSecret = secret1;
                    } else {
                        secretFlags = CryptoService.NEW_SECRET1;
                        createSecret = true;
                        useSecret = secret;
                    }
                } else if ((secretFlags & CryptoService.USE_SECRET1) != 0) {
                    keyIndex = 1;
                    useSecret = secret1;
                    if ((options & (CREATE_SECRET | CREATE_NEXT_SECRET)) != 0) {
                        keyIndex = 2;
                        if ((options & CREATE_NEXT_SECRET) != 0) {
                            // Prepare for the new secret 2 (don't change until we clear the NEW_SECRET2 flag).
                            if ((secretFlags & CryptoService.NEW_SECRET2) == 0) {
                                secretFlags |= CryptoService.NEW_SECRET2;
                                useSecret = secret;
                                createSecret = true;
                            } else {
                                useSecret = secret2;
                            }
                        } else {
                            // Switch to use secret2 (without waiting for the peer to acknowledge).
                            secretFlags |= CryptoService.USE_SECRET2;
                            secretFlags &= ~CryptoService.USE_SECRET1;
                            useSecret = secret;
                            createSecret = true;
                        }
                    }
                } else if ((secretFlags & CryptoService.USE_SECRET2) != 0) {
                    keyIndex = 2;
                    useSecret = secret2;
                    if ((options & (CREATE_SECRET | CREATE_NEXT_SECRET)) != 0) {
                        keyIndex = 1;
                        if ((options & CREATE_NEXT_SECRET) != 0) {
                            // Prepare for the new secret 1 (don't change until we clear the NEW_SECRET1 flag).
                            if ((secretFlags & CryptoService.NEW_SECRET1) == 0) {
                                secretFlags |= CryptoService.NEW_SECRET1;
                                useSecret = secret;
                                createSecret = true;
                            } else {
                                useSecret = secret1;
                            }
                        } else {
                            // Switch to use secret1 (without waiting for the peer to acknowledge).
                            secretFlags |= CryptoService.USE_SECRET1;
                            secretFlags &= ~CryptoService.USE_SECRET2;
                            useSecret = secret;
                            createSecret = true;
                        }
                    }
                } else if ((options & CREATE_NEXT_SECRET) != 0) {
                    secretFlags = CryptoService.NEW_SECRET1;
                    createSecret = true;
                    useSecret = secret;
                    keyIndex = 1;
                } else if ((options & CREATE_SECRET) != 0) {
                    secretFlags = CryptoService.USE_SECRET1;
                    createSecret = true;
                    useSecret = secret;
                    keyIndex = 1;
                } else {
                    keyIndex = 0;
                    useSecret = null;
                }

                result = new KeyInfo(twincodeOutbound, modificationDate, flags,
                        signingKey, encryptionKey, nonceSequence, keyIndex, useSecret);

            } catch (DatabaseException exception) {
                mService.onDatabaseException(exception);
                return null;
            }
            if (useSequenceCount == 0 && !createSecret) {
                return result;
            }

            // Update the nonceSequence
            try (Transaction transaction = newTransaction()) {
                final long now = System.currentTimeMillis();
                final ContentValues values = new ContentValues();
                final String[] updateParams = { Long.toString(id), Long.toString(nonceSequence) };
                values.put(Columns.NONCE_SEQUENCE, nonceSequence + useSequenceCount);
                values.put(Columns.MODIFICATION_DATE, now);
                if (transaction.update(Tables.TWINCODE_KEYS, values, "id=? AND nonceSequence=?", updateParams) > 0) {

                    if (createSecret) {
                        values.clear();
                        if (secretId == null) {
                            values.put(Columns.ID, id);
                            values.put(Columns.PEER_TWINCODE_ID, peerId);
                            values.put(Columns.CREATION_DATE, now);
                            values.put(Columns.MODIFICATION_DATE, now);
                            values.put(Columns.SECRET_UPDATE_DATE, now);
                            values.put(Columns.SECRET1, secret);
                            values.put(Columns.FLAGS, secretFlags);
                            transaction.insert(Tables.SECRET_KEYS, values);
                        } else {
                            // Update flags, secrets and modification date but not the secretUpdateDate:
                            // it will be updated by validateSecrets() when the peer acknowledges the update.
                            values.put(Columns.MODIFICATION_DATE, now);
                            values.put(Columns.FLAGS, secretFlags);
                            if (result.getKeyIndex() == 1) {
                                values.put(Columns.SECRET1, result.getSecretKey());
                            } else {
                                values.put(Columns.SECRET2, result.getSecretKey());
                            }
                            transaction.update(Tables.SECRET_KEYS, values, "id=? AND peerTwincodeId=?",
                                    new String[]{Long.toString(secretId), Long.toString(peerId)});
                        }

                        // Now, make sure the twincode has FLAG_ENCRYPT set iff USE_SECRETx is set
                        // (otherwise, it must be handled by validateSecrets().
                        int twincodeFlags = twincodeOutbound.getFlags();
                        if ((twincodeFlags & TwincodeOutboundImpl.FLAG_ENCRYPT) == 0
                                && (secretFlags & (CryptoService.USE_SECRET1 | CryptoService.USE_SECRET2)) != 0) {
                            twincodeFlags |= TwincodeOutboundImpl.FLAG_ENCRYPT;
                            values.clear();
                            values.put(Columns.FLAGS, twincodeFlags);
                            values.put(Columns.MODIFICATION_DATE, now);
                            transaction.updateWithId(Tables.TWINCODE_OUTBOUND, values, id);
                            twincodeOutbound.setFlags(twincodeFlags);
                        }

                        if (Logger.INFO) {
                            Logger.info(LOG_TAG, "Created secret ", result.getKeyIndex(), " for twincode ",
                                    twincodeOutbound.getId(), " twincode flags ", twincodeFlags);
                        }
                    }
                    transaction.commit();
                    return result;
                }

            } catch (Exception exception) {
                mService.onDatabaseException(exception);
                return null;
            }
        }
    }

    /**
     * Prepare to encrypt/decrypt the SDPs to establish a WebRTC session:
     * - if we have the peer twincode, the encryption is based on secrets that were exchanged when the relation was established.
     * - if there is no peer twincode (ex: click-to-call), we use the encryption key.
     * A nonce sequence is allocated and allows to make up to SessionKeyPairImpl.MAX_EXCHANGE encryptions.
     * After that, the `prepareSession` must be called again to allocate a new nonce sequence.
     *
     * @param sessionId the P2P session id being protected.
     * @param twincodeOutbound the twincode used for encryption.
     * @param peerTwincodeOutbound the twincode used for decryption.
     * @param strict true for a P2P-OUT to force strict mode and make sure we use the keys that are known by the peer.
     * @return the session key pair or null if some items are missing.
     */
    @Nullable
    SessionKeyPair prepareSession(@NonNull UUID sessionId, @NonNull TwincodeOutbound twincodeOutbound,
                                  @Nullable TwincodeOutbound peerTwincodeOutbound, boolean strict) {
        if (DEBUG) {
            Log.d(LOG_TAG, "prepareSession: sessionId=" + sessionId
                    + " twincodeOutbound=" + twincodeOutbound + " peerTwincodeOutbound=" + peerTwincodeOutbound);
        }

        final long id = twincodeOutbound.getDatabaseId().getId();
        while (true) {
            SessionKeyPair result;
            long nonceSequence;
            if (peerTwincodeOutbound != null) {
                final long peerId = peerTwincodeOutbound.getDatabaseId().getId();
                final String[] params = { Long.toString(id), Long.toString(peerId) };

                // Note: there is only one row to get the peer secret BUT we could have several
                // rows for our own twincode: for a 1-1 relation, there will be only one but for a 1-N group
                // there will be one dedicated secret for each member: our secret is shared with only one group member.
                try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT s.flags, s.secretUpdateDate,"
                        + " s.nonceSequence, s.secret1, s.secret2, peer.secret1, peer.secret2"
                        + " FROM secretKeys AS s, secretKeys AS peer"
                        + " WHERE s.id=? AND peer.id=? AND s.peerTwincodeId=peer.id", params)) {
                    if (!cursor.moveToFirst()) {

                        return null;
                    }

                    int flags = cursor.getInt(0);
                    long secretUpdateDate = cursor.getLong(1);
                    nonceSequence = cursor.getLong(2);
                    byte[] secret1 = cursor.getBlob(3);
                    byte[] secret2 = cursor.getBlob(4);
                    byte[] peerSecret1 = cursor.getBlob(5);
                    byte[] peerSecret2 = cursor.getBlob(6);
                    int keyIndex;
                    byte[] secret;

                    // Get the secret that we are sure the peer has.
                    if ((flags & CryptoService.USE_SECRET1) != 0) {
                        keyIndex = 1;
                        secret = secret1;
                    } else if ((flags & CryptoService.USE_SECRET2) != 0) {
                        keyIndex = 2;
                        secret = secret2;
                    } else if (flags != 0 && strict) {
                        // The peer does not know our secret and we are in strict mode (P2P-OUT).
                        return null;
                    } else {
                        secret = secret1;
                        keyIndex = 1;
                    }

                    // Some secret are missing, we cannot proceed.
                    if (secret == null || (peerSecret1 == null && peerSecret2 == null)) {
                        return null;
                    }
                    result = new SessionSecretKeyPair(sessionId, twincodeOutbound, peerTwincodeOutbound, secretUpdateDate,
                            nonceSequence, secret, keyIndex, peerSecret1, peerSecret2);

                } catch (DatabaseException exception) {
                    mService.onDatabaseException(exception);
                    return null;
                }

                // Update the nonceSequence
                try (Transaction transaction = newTransaction()) {
                    final ContentValues values = new ContentValues();
                    values.put(Columns.NONCE_SEQUENCE, nonceSequence + result.getSequenceCount());
                    values.put(Columns.MODIFICATION_DATE, System.currentTimeMillis());

                    final String[] updateParams = { Long.toString(id), Long.toString(peerId), Long.toString(nonceSequence) };
                    final int updateResult = transaction.update(Tables.SECRET_KEYS, values,
                            "id=? AND peerTwincodeId=? AND nonceSequence=?", updateParams);
                    if (updateResult > 0) {
                        transaction.commit();
                        return result;
                    }

                } catch (Exception exception) {
                    mService.onDatabaseException(exception);
                }
            } else {
                final String[] params = { Long.toString(id) };

                try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT k.flags, k.modificationDate, "
                        + " k.nonceSequence, k.encryptionKey"
                        + " FROM twincodeKeys AS k WHERE k.id=?", params)) {
                    if (!cursor.moveToFirst()) {

                        return null;
                    }

                    int flags = cursor.getInt(0);
                    long modificationDate = cursor.getLong(1);
                    nonceSequence = cursor.getLong(2);
                    byte[] encryptionKey = cursor.getBlob(3);
                    if (encryptionKey == null) {
                        return null;
                    }

                    result = new SessionECKeyPair(sessionId, twincodeOutbound, flags, modificationDate,
                            encryptionKey, nonceSequence, mRandom);

                } catch (DatabaseException exception) {
                    mService.onDatabaseException(exception);
                    return null;

                } catch (SecurityException exception) {
                    return null;
                }

                // Update the nonceSequence
                try (Transaction transaction = newTransaction()) {
                    final ContentValues values = new ContentValues();
                    final String[] updateParams = { Long.toString(id), Long.toString(nonceSequence) };
                    values.put(Columns.NONCE_SEQUENCE, nonceSequence + result.getSequenceCount());
                    values.put(Columns.MODIFICATION_DATE, System.currentTimeMillis());
                    if (transaction.update(Tables.TWINCODE_KEYS, values, "id=? AND nonceSequence=?",
                            updateParams) > 0) {
                        transaction.commit();
                        return result;
                    }

                } catch (Exception exception) {
                    mService.onDatabaseException(exception);
                }
            }
        }
    }

    /**
     * Refresh the session key pair nonce sequence to get a new block of MAX_EXCHANGE sequences.
     *
     * @param sessionKeyPair the session key pair to refresh.
     * @return SUCCESS or an error code.
     */
    @NonNull
    ErrorCode refreshSession(@NonNull SessionSecretKeyPair sessionKeyPair) {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshSession: sessionKeyPair=" + sessionKeyPair);
        }

        final long id = sessionKeyPair.getTwincodeOutbound().getDatabaseId().getId();
        final long peerId = sessionKeyPair.getPeerTwincodeOutbound().getDatabaseId().getId();
        final String[] params = { Long.toString(id), Long.toString(peerId) };
        for (int retry = 1; retry < 5; retry++) {

            try (Transaction transaction = newTransaction()) {

                // Only get the nonce sequence associated with the twincode pair.
                final Long nonceSequence = mDatabase.longQuery("SELECT s.nonceSequence"
                        + " FROM secretKeys AS s"
                        + " WHERE s.id=? AND s.peerTwincodeId=?", params);
                if (nonceSequence == null) {
                    return ErrorCode.DATABASE_ERROR;
                }

                sessionKeyPair.refreshNonceSequence(nonceSequence);
                final ContentValues values = new ContentValues();
                values.put(Columns.NONCE_SEQUENCE, nonceSequence + sessionKeyPair.getSequenceCount());
                values.put(Columns.MODIFICATION_DATE, System.currentTimeMillis());

                // Update the nonceSequence
                final String[] updateParams = { Long.toString(id), Long.toString(peerId), Long.toString(nonceSequence) };
                final int updateResult = transaction.update(Tables.SECRET_KEYS, values,
                        "id=? AND peerTwincodeId=? AND nonceSequence=?", updateParams);
                if (updateResult > 0) {
                    transaction.commit();
                    return ErrorCode.SUCCESS;
                }

            } catch (Exception exception) {
                mService.onDatabaseException(exception);
                return ErrorCode.DATABASE_ERROR;
            }
        }
        return ErrorCode.DATABASE_ERROR;
    }

    @Nullable
    KeyPair loadKeyPair(@NonNull TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadKeyPair: twincodeOutbound=" + twincodeOutbound);
        }

        final long id = twincodeOutbound.getDatabaseId().getId();
        final String[] params = { Long.toString(id) };
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT privKey.flags, privKey.signingKey,"
                + " pubKey.flags, pubKey.signingKey, twout.twincodeId, peerTwout.twincodeId, r.uuid"
                + " FROM repository AS r"
                + " INNER JOIN twincodeKeys AS privKey ON r.twincodeOutbound=privKey.id"
                + " INNER JOIN twincodeKeys AS pubKey ON r.peerTwincodeOutbound=pubKey.id"
                + " INNER JOIN twincodeOutbound AS twout ON r.twincodeOutbound=twout.id"
                + " INNER JOIN twincodeOutbound AS peerTwout ON r.peerTwincodeOutbound=peerTwout.id"
                + " WHERE r.twincodeOutbound=?", params)) {
            while (cursor.moveToNext()) {
                int privFlags = cursor.getInt(0);
                byte[] privKey = cursor.getBlob(1);
                int peerPubFlags = cursor.getInt(2);
                byte[] peerPubKey = cursor.getBlob(3);
                UUID twincodeId = cursor.getUUID(4);
                UUID peerTwincodeId = cursor.getUUID(5);
                UUID subjectId = cursor.getUUID(6);
                if (twincodeId == null || peerTwincodeId == null || privKey == null || peerPubKey == null || subjectId == null) {
                    continue;
                }

                return new KeyPair(privFlags, privKey, peerPubFlags, peerPubKey, twincodeId, peerTwincodeId, subjectId);
            }
            return null;

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    @Nullable
    KeyPair loadKeyPair(@NonNull byte[] pubKey) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadKeyPair: pubKey.length=" + pubKey.length);
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT privKey.flags, privKey.signingKey,"
                + " pubKey.flags, pubKey.signingKey, twout.twincodeId, peerTwout.twincodeId, r.uuid"
                + " FROM repository AS r"
                + " INNER JOIN twincodeKeys AS privKey ON r.twincodeOutbound=privKey.id"
                + " INNER JOIN twincodeKeys AS pubKey ON r.peerTwincodeOutbound=pubKey.id"
                + " INNER JOIN twincodeOutbound AS twout ON r.twincodeOutbound=twout.id"
                + " INNER JOIN twincodeOutbound AS peerTwout ON r.peerTwincodeOutbound=peerTwout.id", null)) {
            while (cursor.moveToNext()) {
                int privFlags = cursor.getInt(0);
                byte[] privKey = cursor.getBlob(1);
                int peerPubFlags = cursor.getInt(2);
                byte[] peerPubKey = cursor.getBlob(3);
                UUID twincodeId = cursor.getUUID(4);
                UUID peerTwincodeId = cursor.getUUID(5);
                UUID subjectId = cursor.getUUID(6);
                if (twincodeId == null || peerTwincodeId == null || privKey == null || peerPubKey == null || subjectId == null) {
                    continue;
                }

                if (Arrays.equals(pubKey, peerPubKey)) {
                    return new KeyPair(privFlags, privKey, peerPubFlags, peerPubKey, twincodeId, peerTwincodeId, subjectId);
                }

                final CryptoKey cryptoKey = CryptoKey.importPrivateKey(KeyInfo.toCryptoKind(privFlags, false), privKey, false);
                if (cryptoKey != null && cryptoKey.isValid()) {
                    byte[] twincodePubKey = cryptoKey.getPublicKey(false);
                    cryptoKey.dispose();

                    if (Arrays.equals(pubKey, twincodePubKey)) {
                        return new KeyPair(privFlags, privKey, peerPubFlags, peerPubKey, twincodeId, peerTwincodeId, subjectId);
                    }
                }
            }
            return null;

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    @Nullable
    ImageInfo loadImageInfo(@NonNull ImageId imageId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadImageInfo: imageId=" + imageId);
        }

        final String[] params = { String.valueOf(imageId.getId()) };
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT img.uuid, img.imageSHAs"
                + " FROM image AS img WHERE img.id=?", params)) {
            if (!cursor.moveToFirst()) {

                return null;
            }

            UUID uuid = cursor.getUUID(0);
            byte[] sha = cursor.getBlob(1);
            return new ImageInfo(uuid, null, sha, null);

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    /**
     * Save the peer secret key with the given key index for the { <twincode>, <peer-twincode> } key association.
     *
     * @param twincodeOutbound our identity twincode.
     * @param peerTwincodeOutbound the peer twincode.
     * @param secretKey the peer secret key.
     * @param keyIndex the peer secret key index.
     */
    void saveSecretKey(@NonNull TwincodeOutboundImpl twincodeOutbound, @NonNull TwincodeOutboundImpl peerTwincodeOutbound, @NonNull byte[] secretKey, int keyIndex) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveSecretKey twincodeOutbound=" + twincodeOutbound + " peerTwincodeOutbound=" + peerTwincodeOutbound + " keyIndex=" + keyIndex);
        }

        try (Transaction transaction = newTransaction()) {
            final long now = System.currentTimeMillis();
            final long peerId = peerTwincodeOutbound.getDatabaseId().getId();
            final String[] params = { Long.toString(peerId) };
            final ContentValues values = new ContentValues();

            values.put(Columns.SECRET_UPDATE_DATE, now);
            if (keyIndex == 1) {
                values.put(Columns.SECRET1, secretKey);
            } else if (keyIndex == 2) {
                values.put(Columns.SECRET2, secretKey);
            }

            // Either insert or update the secret key.
            // Flags are always 0 and peerTwincodeId is always NULL because this is the peer secret.
            // Note: we cannot use the insert() to detect if the row existed because the secretKeys table
            // is using a primary key on two columns and SQLite is using a rowid as primary column, hence
            // it will always successfully insert any row.
            Long secretFlags = mDatabase.longQuery("SELECT flags FROM secretKeys WHERE id=? AND peerTwincodeId IS NULL", params);
            if (secretFlags == null) {
                values.put(Columns.ID, peerId);
                values.putNull(Columns.PEER_TWINCODE_ID);
                values.put(Columns.CREATION_DATE, now);
                values.put(Columns.MODIFICATION_DATE, now);

                transaction.insert(Tables.SECRET_KEYS, values);
            } else {
                transaction.update(Tables.SECRET_KEYS, values, "id=? AND peerTwincodeId IS NULL", params);
            }

            // Check if the FLAG_ENCRYPT flags is set on the peer twincode:
            // - we must have the { <twincode>, <peer-twincode> } key association,
            // - we must know the peer secret { <peer-twincode>, null } association (this is now true).
            if (!peerTwincodeOutbound.isEncrypted()) {
                transaction.updateTwincodeEncryptFlags(twincodeOutbound, peerTwincodeOutbound, now);
            }
            transaction.commit();

            if (Logger.INFO) {
                Logger.info(LOG_TAG, "Stored peer secret ", keyIndex, " for twincode ",
                        peerTwincodeOutbound.getId(), " twincode flags ", peerTwincodeOutbound.getFlags());
            }

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void validateSecrets(@NonNull TwincodeOutboundImpl twincodeOutbound, @NonNull TwincodeOutboundImpl peerTwincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "validateSecrets: twincodeOutbound=" + twincodeOutbound + " peerTwincodeOutbound=" + peerTwincodeOutbound);
        }

        try (Transaction transaction = newTransaction()) {
            final long twincodeId = twincodeOutbound.getDatabaseId().getId();
            final long peerId = peerTwincodeOutbound.getDatabaseId().getId();
            final String[] params = { Long.toString(twincodeId), Long.toString(peerId) };

            Long flags = mDatabase.longQuery("SELECT flags FROM secretKeys WHERE id=? AND peerTwincodeId=?", params);
            if (flags == null) {
                return;
            }

            int secretFlags = flags.intValue();
            if ((secretFlags & CryptoService.NEW_SECRET1) != 0) {
                secretFlags = CryptoService.USE_SECRET1;
            } else if ((secretFlags & CryptoService.NEW_SECRET2) != 0) {
                secretFlags = CryptoService.USE_SECRET2;
            }
            final long now = System.currentTimeMillis();
            final ContentValues values = new ContentValues();
            values.put(Columns.FLAGS, secretFlags);
            values.put(Columns.SECRET_UPDATE_DATE, now);

            transaction.update(Tables.SECRET_KEYS, values, "id=? AND peerTwincodeId=?", params);
            transaction.updateTwincodeEncryptFlags(twincodeOutbound, peerTwincodeOutbound, now);
            transaction.commit();

            if (Logger.INFO) {
                Logger.info(LOG_TAG, "Validated sent secret flags ", secretFlags, " for twincode ",
                        twincodeOutbound.getId(), " twincode flags ", twincodeOutbound.getFlags(), " with peer ", peerTwincodeOutbound.getId());
            }
        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Create a signing and encryption key defined by the given type (ED25519 or ECDSA) and insert
     * the raw private key in the twincodeKeys table associated with the twincode outbound.
     * This method can be called several times for a same twincode and if a previous key existed,
     * we must keep it => we use an INSERT OR IGNORE INTO.
     *
     * @param transaction the transaction under which the operation is made.
     * @param twincodeOutbound the twincode to associated the private keys.
     * @param type the type of key to create.
     * @return SUCCESS if the operation succeeded.
     * @throws DatabaseException if something wrong occurred.
     */
    @NonNull
    ErrorCode insertKey(@NonNull Transaction transaction,
                        @NonNull TwincodeOutbound twincodeOutbound, int type) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "insertKey: twincodeOutbound=" + twincodeOutbound + " type=" + type);
        }

        final CryptoKey signCrypto = CryptoKey.create(KeyInfo.toCryptoKind(type, false));
        final CryptoKey cryptoKey = CryptoKey.create(KeyInfo.toCryptoKind(type, true));
        try {
            final byte[] signingKey = signCrypto.getPrivateKey(false);
            final byte[] encryptionKey = cryptoKey.getPrivateKey(false);
            if (signingKey == null || encryptionKey == null) {
                return ErrorCode.LIBRARY_ERROR;
            }

            final long now = System.currentTimeMillis();
            final ContentValues values = new ContentValues();
            values.put(Columns.ID, twincodeOutbound.getDatabaseId().getId());
            values.put(Columns.SIGNING_KEY, signingKey);
            values.put(Columns.ENCRYPTION_KEY, encryptionKey);
            values.put(Columns.FLAGS, KeyInfo.KEY_PRIVATE_FLAG | (type & KeyInfo.KEY_TYPE_MASK));
            values.put(Columns.CREATION_DATE, now);
            values.put(Columns.MODIFICATION_DATE, now);
            values.put(Columns.NONCE_SEQUENCE, 0);
            transaction.insertOrIgnore(Tables.TWINCODE_KEYS, null, values);
            return ErrorCode.SUCCESS;

        } finally {
            signCrypto.dispose();
            cryptoKey.dispose();
        }

    }

    @NonNull
    ErrorCode insertKey(@NonNull TwincodeOutbound twincodeOutbound, int type) {
        if (DEBUG) {
            Log.d(LOG_TAG, "insertKey: twincodeOutbound=" + twincodeOutbound + " type=" + type);
        }

        try (Transaction transaction = newTransaction()) {
            final ErrorCode result = insertKey(transaction, twincodeOutbound, type);
            if (result == ErrorCode.SUCCESS) {
                transaction.commit();
            }
            return result;

        } catch (Exception exception) {
            return mService.onDatabaseException(exception);
        }
    }
}
