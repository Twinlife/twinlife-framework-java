/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.Database;
import org.twinlife.twinlife.DatabaseCursor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The database service that gives access to the database to other services.  There is only one instance
 * which is shared by every service (RepositoryService, TwincodeXXXService, ...).
 * - it maintains a cache of database objects to make sure we have single instance of RepositoryObject,
 *   TwincodeInbound and TwincodeOutbound,
 * - it provides helper operations to load twincodes from the database (used by several services),
 * - it provides raw query (SELECT only) methods,
 * - updating the database must be made by the Transaction class by calling `newTransaction()`,
 * - it provides the `Allocator` used by the Transaction class to allocate database identifiers.
 */
class DatabaseCheck {
    private static final String LOG_TAG = "DatabaseCheck";
    private static final boolean DEBUG = false;

    private final String mName;
    private final String mMessage;

    DatabaseCheck(@NonNull String name, @NonNull StringBuilder content) {
        if (DEBUG) {
            Log.d(LOG_TAG, "DatabaseServiceImpl");
        }

        mName = name;
        mMessage = content.toString();
    }

    DatabaseCheck(@NonNull String name, @NonNull Exception exception) {
        if (DEBUG) {
            Log.d(LOG_TAG, "DatabaseServiceImpl");
        }

        mName = name;
        mMessage = "Exception:" + exception;
    }

    @NonNull
    private static String toTimeDisplay(long timestamp) {
        long ms = timestamp % 1000L;

        return String.format(Locale.ENGLISH, "%10d %3d", timestamp / 1000L, ms);
    }

    private static final String CHECK_CONVERSATION_WITH_REPOSITORY = "Check consistency (PEER != PID):";

    // Query to list conversation not consistent with the repository
    // We should have: c.peerTwincodeOutbound == c.subject.peerTwincodeOutbound
    @Nullable
    private static DatabaseCheck checkConversationRepository(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkConversationRepository");
        }

        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                    + " c.id, c.groupId, c.subject, c.peerTwincodeOutbound,"
                    + " r.twincodeOutbound, r.peerTwincodeOutbound, r.schemaId,"
                    + " c.flags, r2.id, r2.schemaId"
                    + " FROM repository AS r"
                    + " LEFT JOIN conversation AS c on c.subject = r.id"
                    + " LEFT JOIN repository AS r2 ON r2.peerTwincodeOutbound = c.peerTwincodeOutbound"
                    + " WHERE (c.groupId IS NULL OR c.id = c.groupId) AND c.peerTwincodeOutbound != r.peerTwincodeOutbound", new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("|  CID |  GRP | SUBJ | PEER |  TID |  PID | SCHEMA | FLGS | SUBJ2 | SCHEMA2 |\n");
                }
                long cid = cursor.getLong(0);
                long gid = cursor.getLong(1);
                long subjectId = cursor.getLong(2);
                long peerTwincodeId = cursor.getLong(3);
                long twincodeId = cursor.getLong(4);
                long repoPeerTwincodeId = cursor.getLong(5);
                String rSchemaId = cursor.getString(6);
                int flags = cursor.getInt(7);
                long subject2Id = cursor.getLong(8);
                String r2SchemaId = cursor.getString(9);

                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %4d | %4d | %4d | %4d | %4d | %6.6s |  %03x | %5d |  %6.6s |\n",
                        cid, gid, subjectId, peerTwincodeId, twincodeId, repoPeerTwincodeId, rSchemaId,
                        flags, subject2Id, r2SchemaId));
            }
            return content != null ? new DatabaseCheck(CHECK_CONVERSATION_WITH_REPOSITORY, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(CHECK_CONVERSATION_WITH_REPOSITORY, exception);
        }
    }

    private static final String CHECK_ORPHANED_GROUP_CONVERSATION = "Orphaned group member (GRP not found)";

    // Query to list the group members without a group conversation.
    @Nullable
    private static DatabaseCheck checkOrphanedGroupMemberConversation(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkOrphanedGroupMemberConversation");
        }

        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                + " c.id, c.groupId, c.subject, c.peerTwincodeOutbound, c.flags, twout.twincodeId,"
                + " (SELECT COUNT(d.id) FROM descriptor AS d WHERE d.cid = c.id),"
                + " (SELECT COUNT(op.id) FROM operation AS op WHERE op.cid = c.id)"
                + " FROM conversation AS c"
                + " LEFT JOIN conversation AS g ON c.groupId = g.id"
                + " LEFT JOIN twincodeOutbound AS twout ON c.peerTwincodeOutbound = twout.id"
                + " WHERE c.groupId  IS  NOT NULL AND g.id IS NULL", new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("| CID  |  GRP | SUBJ | PEER | UUID   | FLGS | DCNT | OCNT |\n");
                }

                long cid = cursor.getLong(0);
                long gid = cursor.getLong(1);
                long subjectId = cursor.getLong(2);
                long peerTwincode = cursor.getLong(3);
                int flags = cursor.getInt(4);
                String twincodeId = cursor.getString(5);
                int descriptorCount = cursor.getInt(6);
                int opCount = cursor.getInt(7);

                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %4d | %4d | %4d | %6.6s |  %03x | %4d | %4d |\n",
                        cid, gid, subjectId, peerTwincode, twincodeId, flags, descriptorCount, opCount));
            }
            return content != null ? new DatabaseCheck(CHECK_ORPHANED_GROUP_CONVERSATION, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(CHECK_CONVERSATION_WITH_REPOSITORY, exception);
        }
    }

    private static final String CHECK_MISSING_PEER_TWINCODE_CONVERSATION = "Missing peer twincode (PEER not found):";

    // Missing peer twincode in conversation
    @NonNull
    private static DatabaseCheck checkMissingPeerTwincodeConversation(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkMissingPeerTwincodeConversation");
        }

        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                + " c.id, c.groupId, c.flags, c.subject, c.peerTwincodeOutbound"
                + " FROM conversation AS c"
                + " LEFT JOIN twincodeOutbound AS twout ON c.peerTwincodeOutbound = twout.id"
                + " WHERE twout.id IS NULL", new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("| CID  |  GRP | FLGS | SUBJ | PEER |\n");
                }

                long cid = cursor.getLong(0);
                long gid = cursor.getLong(1);
                int flags = cursor.getInt(2);
                long subjectId = cursor.getLong(3);
                long peerTwincodeId = cursor.getLong(4);

                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %4d |  %03x | %4d | %4d |\n",
                        cid, gid, flags, subjectId, peerTwincodeId));
            }
            return content != null ? new DatabaseCheck(CHECK_MISSING_PEER_TWINCODE_CONVERSATION, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(CHECK_MISSING_PEER_TWINCODE_CONVERSATION, exception);
        }
    }

    private static final String CHECK_MISSING_IDENTITY_TWINCODE_CONVERSATION = "Missing twincode in repository (TID not found)";

    // Missing peer twincode in conversation
    @Nullable
    private static DatabaseCheck checkMissingIdentityTwincode(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkMissingIdentityTwincode");
        }

        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                + " r.id, r.owner, r.schemaId, r.flags, r.twincodeOutbound"
                + " FROM repository AS r"
                + " LEFT JOIN twincodeOutbound AS twout ON r.twincodeOutbound = twout.id"
                + " WHERE (r.twincodeOutbound IS NOT NULL AND twout.id IS NULL)", new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("| SUBJ | OWNER | SCHEMA |  FLGS |  TID |\n");
                }
                long subjectId = cursor.getLong(0);
                long ownerId = cursor.getLong(1);
                String schemaId = cursor.getString(2);
                int flags = cursor.getInt(3);
                long twincodeId = cursor.getLong(4);

                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %4d | %6.6s | %03x | %4d |\n",
                        subjectId, ownerId, schemaId, flags, twincodeId));
            }
            return content != null ? new DatabaseCheck(CHECK_MISSING_IDENTITY_TWINCODE_CONVERSATION, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(CHECK_MISSING_IDENTITY_TWINCODE_CONVERSATION, exception);
        }
    }

    private static final String CHECK_MISSING_PEER_TWINCODE_REPOSITORY = "Missing peer in repository (PID not found):";

    // Missing peer twincode in conversation
    @Nullable
    private static DatabaseCheck checkMissingPeerTwincodeRepository(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkMissingPeerTwincodeRepository");
        }

        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                + " r.id, r.owner, r.schemaId, r.flags, r.twincodeOutbound, r.peerTwincodeOutbound,"
                + " c.id, c.groupId, c.peerTwincodeOutbound, c.flags"
                + " FROM repository AS r"
                + " LEFT JOIN conversation AS c on r.id = c.subject"
                + " LEFT JOIN twincodeOutbound AS peerOut ON r.peerTwincodeOutbound = peerOut.id"
                + " WHERE (r.peerTwincodeOutbound IS NOT NULL AND peerOut.id IS NULL)", new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("| SUBJ | OWNR | SCHEMA | RFLG |  TID |  PID |  CID |  GRP | PEER | CFLG |\n");
                }
                long subjectId = cursor.getLong(0);
                long ownerId = cursor.getLong(1);
                String schemaId = cursor.getString(2);
                int flags = cursor.getInt(3);
                long twincodeId = cursor.getLong(4);
                long peerTwincodeId = cursor.getLong(5);
                long cid = cursor.getLong(6);
                long gid = cursor.getLong(7);
                long conversationPeerTwincodeId = cursor.getLong(8);
                int conversationFlags = cursor.getInt(9);

                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %4d | %6.6s |  %03x | %4d | %4d | %4d | %4d | %4d |  %03x |\n",
                        subjectId, ownerId, schemaId, flags, twincodeId, peerTwincodeId, cid, gid,
                        conversationPeerTwincodeId, conversationFlags));
            }
            return content != null ? new DatabaseCheck(CHECK_MISSING_PEER_TWINCODE_REPOSITORY, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(CHECK_MISSING_PEER_TWINCODE_REPOSITORY, exception);
        }
    }

    private static final String DUMP_REPOSITORY = "Repository:";

    @Nullable
    private static DatabaseCheck dumpRepository(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dumpRepository");
        }

        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                + " r.id, r.owner, r.schemaId, r.twincodeInbound, r.twincodeOutbound, r.peerTwincodeOutbound,"
                + " r.creationDate, r.modificationDate - r.creationDate"
                + " FROM repository AS r", new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("| SUBJ | OWNR | SCHEMA |  TIN |  TID |  PID |    CREATE DATE |         MODIF |\n");
                }
                long subjectId = cursor.getLong(0);
                long ownerId = cursor.getLong(1);
                String schemaId = cursor.getString(2);
                long twincodeInId = cursor.getLong(3);
                long twincodeId = cursor.getLong(4);
                long peerTwincodeId = cursor.getLong(5);
                long creationDate = cursor.getLong(6);
                long modifDelta = cursor.getLong(7);

                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %4d | %6.6s | %4d | %4d | %4d | %s | %13d |\n",
                        subjectId, ownerId, schemaId, twincodeInId, twincodeId, peerTwincodeId, toTimeDisplay(creationDate), modifDelta));
            }
            return content != null ? new DatabaseCheck(DUMP_REPOSITORY, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(DUMP_REPOSITORY, exception);
        }
    }

    @Nullable
    private static DatabaseCheck dumpTwincodes(@NonNull Database database, boolean secure, String name, String sql) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dumpTwincodes");
        }

        try (DatabaseCursor cursor = database.rawQuery(sql, new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("|  TID |   UUID |  IMG | FLGS |    CREATE DATE |         MODIF |       REFRESH |\n");
                }
                long tid = cursor.getLong(0);
                String twincodeId = cursor.getString(1);
                long avatarId = cursor.getLong(2);
                int flags = cursor.getInt(3);
                long creationDate = cursor.getLong(4);
                long modifDelta = cursor.getLong(5);
                long refreshDelta = cursor.getLong(6);

                if (secure) {
                    twincodeId = "...";
                }
                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %6.6s | %4d | %04x | %s | %13d | %13d |\n",
                        tid, twincodeId, avatarId, flags, toTimeDisplay(creationDate), modifDelta, refreshDelta));
            }
            return content != null ? new DatabaseCheck(name, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(name, exception);
        }
    }

    private static final String DUMP_TWINCODES = "TOUT:";

    @Nullable
    private static DatabaseCheck dumpTwincodes(@NonNull Database database, boolean secure) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dumpTwincodes");
        }

        return dumpTwincodes(database, secure, DUMP_TWINCODES, "SELECT"
                + " t.id, t.twincodeId, t.avatarId, t.flags,"
                + " t.creationDate,"
                + " t.modificationDate - t.creationDate,"
                + " (CASE WHEN t.refreshDate = 0 THEN 0 ELSE t.refreshDate - t.creationDate END)"
                + " FROM twincodeOutbound AS t"
                + " LEFT JOIN repository AS r1 ON t.id = r1.twincodeOutbound"
                + " LEFT JOIN repository AS r2 ON t.id = r2.peerTwincodeOutbound"
                + " LEFT JOIN conversation AS c ON t.id = c.peerTwincodeOutbound"
                + " LEFT JOIN descriptor AS d ON t.id = d.twincodeOutbound"
                + " WHERE (r1.id IS NOT NULL) OR (r2.id IS NOT NULL) OR (c.id IS NOT NULL) OR (d.id IS NOT NULL)"
                + " GROUP BY t.id");
    }

    private static final String DUMP_ORPHANED_TWINCODES = "Twincode OUT (TID not referenced):";

    private static DatabaseCheck dumpOrphanedTwincodes(@NonNull Database database, boolean secure) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dumpOrphanedTwincodes");
        }

        return dumpTwincodes(database, secure, DUMP_ORPHANED_TWINCODES, "SELECT"
                + " t.id, t.twincodeId, t.avatarId, t.flags,"
                + " t.creationDate,"
                + " t.modificationDate - t.creationDate,"
                + " (CASE WHEN t.refreshDate = 0 THEN 0 ELSE t.refreshDate - t.creationDate END)"
                + " FROM twincodeOutbound AS t"
                + " LEFT JOIN repository AS r1 ON t.id = r1.twincodeOutbound"
                + " LEFT JOIN repository AS r2 ON t.id = r2.peerTwincodeOutbound"
                + " LEFT JOIN conversation AS c ON t.id = c.peerTwincodeOutbound"
                + " LEFT JOIN descriptor AS d ON t.id = d.twincodeOutbound"
                + " WHERE r1.id IS NULL AND r2.id IS NULL AND c.id IS NULL AND d.id IS NULL");
    }

    private static final String CHECK_TWINCODES_MISSING_IMAGE = "Missing image for twincodes (IMG not found):";

    private static DatabaseCheck checkMissingImagesTwincodes(@NonNull Database database, boolean secure) {
        if (DEBUG) {
            Log.d(LOG_TAG, "checkMissingImagesTwincodes");
        }

        return dumpTwincodes(database, secure, CHECK_TWINCODES_MISSING_IMAGE, "SELECT"
                + " t.id, t.twincodeId, t.avatarId, t.flags,"
                + " t.creationDate,"
                + " t.modificationDate - t.creationDate,"
                + " (CASE WHEN t.refreshDate = 0 THEN 0 ELSE t.refreshDate - t.creationDate END)"
                + " FROM twincodeOutbound AS t"
                + " LEFT JOIN image AS img ON t.avatarId = img.id"
                + " WHERE t.avatarId IS NOT NULL AND t.avatarId > 0 AND img.id IS NULL");
    }

    private static final String DUMP_TWINCODE_IN = "TIN:";

    @Nullable
    private static DatabaseCheck dumpTwincodesIn(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dumpTwincodesIn");
        }

        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                + " tin.id, tin.twincodeOutbound, tin.twincodeId, tin.factoryId,"
                + " tout.creationDate, tin.modificationDate"
                + " FROM twincodeInbound AS tin"
                + " LEFT JOIN twincodeOutbound AS tout ON tin.twincodeOutbound = tout.id", new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("|  TIN |  TID |  UUID  |  FACT  |    CREATE DATE |        MODIF  |\n");
                }
                long tid = cursor.getLong(0);
                long twincodeId = cursor.getLong(1);
                String twincodeInId = cursor.getString(2);
                String factoryId = cursor.getString(3);
                long creationDate = cursor.getLong(4);
                long modificationDate = cursor.getLong(5);
                long modifDelta = creationDate > 0 && modificationDate > creationDate ? modificationDate - creationDate : modificationDate;

                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %4d | %6.6s | %6.6s | %s | %13d |\n",
                        tid, twincodeId, twincodeInId, factoryId, toTimeDisplay(creationDate), modifDelta));
            }
            return content != null ? new DatabaseCheck(DUMP_TWINCODE_IN, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(DUMP_TWINCODE_IN, exception);
        }
    }

    private static final String DUMP_TWINCODE_KEYS = "KID:";

    // List keys and secrets without leaking sensitive information.
    @Nullable
    private static DatabaseCheck dumpTwincodeKeys(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dumpTwincodeKeys");
        }

        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                + " k.id, s.peerTwincodeId, k.creationDate, k.modificationDate - k.creationDate,"
                + " s.creationDate - k.creationDate, s.modificationDate - s.creationDate,"
                + " s.secretUpdateDate - s.creationDate,"
                + " k.flags, k.nonceSequence,"
                + " s.flags, s.nonceSequence,"
                + " LENGTH(k.signingKey), LENGTH(k.encryptionKey),"
                + " LENGTH(s.secret1), LENGTH(s.secret2)"
                + " FROM twincodeKeys AS k"
                + " LEFT JOIN secretKeys  AS  s ON k.id = s.id", new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("|   KID   | KEYS |  KFLG |  SFLG |    CREATE DATE |      MODIF |   SEC DATE |    SEC MOD |    SEC UPD |  K-Nonce |  S-Nonce |\n");
                }
                long kid = cursor.getLong(0);
                long peerTwincode = cursor.getLong(1);
                long creationDate = cursor.getLong(2);
                long modifDelta = cursor.getLong(3);
                long secretCreateDelta = cursor.getLong(4);
                long secretModifDelta = cursor.getLong(5);
                long secretUpdateDelta = cursor.getLong(6);
                int kFlags = cursor.getInt(7);
                long kNonce = cursor.getLong(8);
                int sFlags = cursor.getInt(9);
                long sNonce = cursor.getLong(10);
                int l1 = cursor.getInt(11);
                int l2 = cursor.getInt(12);
                int l3 = cursor.getInt(13);
                int l4 = cursor.getInt(14);
                StringBuilder sb = new StringBuilder();
                if (l1 == 32) {
                    sb.append('S');
                } else if (l1 > 0) {
                    sb.append('s');
                } else {
                    sb.append(' ');
                }
                if (l2 == 32) {
                    sb.append('E');
                } else if (l1 > 0) {
                    sb.append('e');
                } else {
                    sb.append(' ');
                }
                if (l3 == 32) {
                    sb.append('S');
                } else if (l3 > 0) {
                    sb.append('s');
                } else {
                    sb.append(' ');
                }
                if (l4 == 32) {
                    sb.append('E');
                } else if (l4 > 0) {
                    sb.append('e');
                } else {
                    sb.append(' ');
                }
                content.append(String.format(Locale.ENGLISH,
                        "| %3d.%-3d | %s | %05x | %05x | %s | %10d | %10d | %10d | %10d | %8d | %8d |\n",
                        kid, peerTwincode, sb, kFlags, sFlags, toTimeDisplay(creationDate), modifDelta, secretCreateDelta,
                        secretModifDelta, secretUpdateDelta, kNonce,sNonce));
            }
            return content != null ? new DatabaseCheck(DUMP_TWINCODE_KEYS, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(DUMP_TWINCODE_KEYS, exception);
        }
    }

    private static final String DUMP_SECURE_CONVERSATIONS = "CID:";

    // List secure conversation.
    @Nullable
    private static DatabaseCheck dumpSecureConversations(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dumpTwincodeKeys");
        }

        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                + " c.id, c.groupId, c.subject, r.twincodeOutbound, r.peerTwincodeOutbound, c.peerTwincodeOutbound,"
                + " t1.id, t2.id,"
                + " (SELECT COUNT(d.id) FROM descriptor AS d WHERE d.cid = c.id),"
                + " (SELECT COUNT(op.id) FROM operation AS op WHERE op.cid = c.id)"
                + " FROM conversation  AS  c"
                + " LEFT JOIN repository  AS  r  ON c.subject = r.id"
                + " LEFT JOIN twincodeKeys AS t1 ON t1.id = r.twincodeOutbound"
                + " LEFT JOIN twincodeKeys AS t2 ON t2.id = c.peerTwincodeOutbound"
                + " WHERE (t1.id IS NOT NULL) OR (t2.id IS NOT NULL)", new String[]{})) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("|  CID |  GRP | SUBJ |  TID |  PID | PEER |    KID  | DCNT | OCNT |\n");
                }
                long cid = cursor.getLong(0);
                long gid = cursor.getLong(1);
                long subjectId = cursor.getLong(2);
                long twincodeId = cursor.getLong(3);
                long peerTwincodeId = cursor.getLong(4);
                long convTwincodeId = cursor.getLong(5);
                int k1 = cursor.getInt(6);
                int k2 = cursor.getInt(7);
                int descriptorCount = cursor.getInt(8);
                int opCount = cursor.getInt(9);

                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %4d | %4d | %4d | %4d | %4d | %3d.%-3d | %4d | %4d |\n",
                        cid, gid, subjectId, twincodeId, peerTwincodeId, convTwincodeId, k1, k2, descriptorCount, opCount));
            }
            return content != null ? new DatabaseCheck(DUMP_SECURE_CONVERSATIONS, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(DUMP_SECURE_CONVERSATIONS, exception);
        }
    }

    private static final String DUMP_CONVERSATION_SPEED = "Metrics:";
    private static final long REPORT_SPEED_TIMEFRAME = (7L*86400L*1000L);

    // List secure conversation.
    @Nullable
    private static DatabaseCheck dumpConversationsSpeed(@NonNull Database database) {
        if (DEBUG) {
            Log.d(LOG_TAG, "dumpConversationsSpeed");
        }

        String[] params = {
                Long.toString(System.currentTimeMillis() - REPORT_SPEED_TIMEFRAME)
        };
        try (DatabaseCursor cursor = database.rawQuery("SELECT"
                + " d.cid, d.twincodeOutbound,"
                + " SUM(CASE WHEN d.receiveDate - d.creationDate < 20000 THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN d.receiveDate - d.creationDate < 70000 THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN d.receiveDate - d.creationDate < 310000 THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN d.receiveDate - d.creationDate > 310000 THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN d.receiveDate - d.creationDate > 310000 THEN d.receiveDate - d.creationDate ELSE 0 END),"
                + " SUM(CASE WHEN d.sendDate - d.creationDate < 20000 THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN d.sendDate - d.creationDate < 70000 THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN d.sendDate - d.creationDate < 310000 THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN d.sendDate - d.creationDate > 310000 THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN d.sendDate - d.creationDate > 310000 THEN d.sendDate - d.creationDate ELSE 0 END)"
                + " FROM descriptor AS d"
                + " INNER JOIN conversation AS c on d.cid = c.id"
                + " WHERE d.creationDate > ?"
                + " GROUP BY d.cid, d.twincodeOutbound", params)) {

            StringBuilder content = null;
            while (cursor.moveToNext()) {
                if (content == null) {
                    content = new StringBuilder();
                    content.append("|  CID |  TID |  REC-1 |  REC-2 |  REC-3 |  REC-4 | REC-TIME |  SND-1 |  SND-2 |  SND-3 |  SND-4 | SND-TIME |\n");
                }
                long cid = cursor.getLong(0);
                long twincodeId = cursor.getLong(1);
                long recvFastCount = cursor.getLong(2);
                long recvRetry2Count = cursor.getLong(3);
                long recvRetry3Count = cursor.getLong(4);
                long recvCount = cursor.getLong(5);
                long recvTime = cursor.getLong(6) / 1000L;
                long sendFastCount = cursor.getLong(7);
                long sendRetry2Count = cursor.getLong(8);
                long sendRetry3Count = cursor.getLong(9);
                long sendCount = cursor.getLong(10);
                long sendTime = cursor.getLong(11) / 1000L;

                content.append(String.format(Locale.ENGLISH,
                        "| %4d | %4d | %6d | %6d | %6d | %6d | %8d | %6d | %6d | %6d | %6d | %8d |\n",
                        cid, twincodeId, recvFastCount, recvRetry2Count - recvFastCount,
                        recvRetry3Count - recvRetry2Count, recvCount, recvTime,
                        sendFastCount, sendRetry2Count - sendFastCount, sendRetry3Count - sendRetry2Count,
                        sendCount, sendTime));
            }
            return content != null ? new DatabaseCheck(DUMP_CONVERSATION_SPEED, content) : null;

        } catch (Exception exception) {

            return new DatabaseCheck(DUMP_CONVERSATION_SPEED, exception);
        }
    }

    @NonNull
    public static String checkConsistency(@NonNull Database database) {

        List<DatabaseCheck> checks = new ArrayList<>();

        DatabaseCheck check = checkMissingIdentityTwincode(database);
        if (check != null) {
            checks.add(check);
        }

        check = checkMissingPeerTwincodeRepository(database);
        if (check != null) {
            checks.add(check);
        }

        check = checkMissingPeerTwincodeConversation(database);
        if (check != null) {
            checks.add(check);
        }

        check = checkMissingImagesTwincodes(database, false);
        if (check != null) {
            checks.add(check);
        }

        check = checkConversationRepository(database);
        if (check != null) {
            checks.add(check);
        }

        check = checkOrphanedGroupMemberConversation(database);
        if (check != null) {
            checks.add(check);
        }

        check = dumpRepository(database);
        if (check != null) {
            checks.add(check);
        }

        check = dumpTwincodesIn(database);
        if (check != null) {
            checks.add(check);
        }

        check = dumpTwincodes(database, false);
        if (check != null) {
            checks.add(check);
        }

        check = dumpOrphanedTwincodes(database, false);
        if (check != null) {
            checks.add(check);
        }

        check = dumpTwincodeKeys(database);
        if (check != null) {
            checks.add(check);
        }

        check = dumpSecureConversations(database);
        if (check != null) {
            checks.add(check);
        }

        check = dumpConversationsSpeed(database);
        if (check != null) {
            checks.add(check);
        }

        StringBuilder content = new StringBuilder();
        for (DatabaseCheck c : checks) {
            content.append(c.mName);
            content.append("\n");
            content.append(c.mMessage);
            content.append("\n");
        }

        return content.toString();
    }
}
