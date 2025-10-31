/*
 *  Copyright (c) 2015-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Houssem Temanni (Houssem.Temanni@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import android.content.ContentValues;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.AnnotationType;
import org.twinlife.twinlife.ConversationService.DescriptorAnnotation;
import org.twinlife.twinlife.ConversationService.InvitationDescriptor;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DatabaseObject;
import org.twinlife.twinlife.DatabaseTable;
import org.twinlife.twinlife.DisplayCallsMode;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.database.Columns;
import org.twinlife.twinlife.database.ConversationsCleaner;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.DatabaseServiceProvider;
import org.twinlife.twinlife.database.QueryBuilder;
import org.twinlife.twinlife.database.Tables;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import static org.twinlife.twinlife.ConversationService.MAX_GROUP_MEMBERS;

public class ConversationServiceProvider extends DatabaseServiceProvider implements ConversationsCleaner {
    private static final String LOG_TAG = "ConversationServiceP...";
    private static final boolean DEBUG = false;

    public enum Result {
        STORED, UPDATED, ERROR
    }

    /**
     * conversation table:
     * id INTEGER: local database identifier (primary key)
     * groupId INTEGER: the group conversation key
     * uuid TEXT UNIQUE NOT NULL: conversation id
     * creationDate INTEGER NOT NULL: conversation creation date
     * subject INTEGER NOT NULL: the repository object key (allows to read the RepositoryObject)
     * invitedContact INTEGER: the repository object representing the invited contact
     * peerTwincodeOutbound INTEGER: the peer twincode outbound key (necessary for group member twincode)
     * resourceId TEXT: the resource UUID
     * peerResourceId TEXT: the peer resource UUID
     * permissions INTEGER: the peer's permissions
     * joinPermissions INTEGER DEFAULT 0: the join permissions for members.
     * lastConnectDate INTEGER: the last connection date
     * lastRetryDate INTEGER: the date of the last WebRTC connection retry
     * flags INTEGER DEFAULT 0: group and conversation state
     * lock INTEGER DEFAULT 0: exclusive lock to prevent multiple processes to create P2P connections.
     * Note:
     * - id, uuid, creationDate, subject, groupId, invitedContact are readonly.
     */
    private static final String CONVERSATION_TABLE =
            "CREATE TABLE IF NOT EXISTS conversation (id INTEGER PRIMARY KEY, groupId INTEGER,"
                    + " uuid TEXT UNIQUE NOT NULL, creationDate INTEGER NOT NULL,"
                    + " subject INTEGER NOT NULL, invitedContact INTEGER, peerTwincodeOutbound INTEGER,"
                    + " resourceId TEXT, peerResourceId TEXT, permissions INTEGER DEFAULT 0,"
                    + " joinPermissions INTEGER DEFAULT 0, lastConnectDate INTEGER, lastRetryDate INTEGER,"
                    + " flags INTEGER DEFAULT 0, lock INTEGER DEFAULT 0"
                    + ")";

    /**
     * descriptor table:
     * id INTEGER: local database identifier (primary key)
     * cid INTEGER NOT NULL: the conversation id
     * sequenceId INTEGER NOT NULL: the descriptor sequence id
     * twincodeOutbound INTEGER NOT NULL: the twincode outbound key
     * sentTo INTEGER: the optional twincode outbound key for sentTo
     * replyTo INTEGER: the optional reply to
     * descriptorType INTEGER NOT NULL: the descriptor type
     * creationDate INTEGER NOT NULL: descriptor creation date
     * sendDate INTEGER: the send date
     * receiveDate INTEGER: the receive date
     * readDate INTEGER: the read date
     * updateDate INTEGER: the update date
     * peerDeleteDate INTEGER: the peer deletion date
     * deleteDate INTEGER: the description deletion date
     * expireTimeout INTEGER: the expiration timeout
     * flags INTEGER: the copy flags
     * value INTEGER: an integer value (length of file, clear timestamp, duration, ...)
     * content TEXT: the message or the descriptor information (serialized in text form)
     * Note:
     * - id, cid, sequenceId, twincodeOutbound, sentTo, replyTo, descriptorType, creationDate are readonly.
     */
    private static final String DESCRIPTOR_TABLE =
            "CREATE TABLE IF NOT EXISTS descriptor (id INTEGER PRIMARY KEY,"
                    + " cid INTEGER NOT NULL, sequenceId INTEGER NOT NULL, twincodeOutbound INTEGER,"
                    + " sentTo INTEGER, replyTo INTEGER, descriptorType INTEGER NOT NULL,"
                    + " creationDate INTEGER, sendDate INTEGER, receiveDate INTEGER, readDate INTEGER,"
                    + " updateDate INTEGER, peerDeleteDate INTEGER, deleteDate INTEGER, expireTimeout INTEGER,"
                    + " flags INTEGER, value INTEGER, content TEXT"
                    + ")";
    private static final String DESCRIPTOR_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_descriptor_cid ON descriptor (cid, creationDate)";

    /**
     * invitation table:
     * id INTEGER NOT NULL: the invitation id == descriptor key (primary key)
     * groupId INTEGER: the group conversation id
     * inviterMember INTEGER: the member twincode that made the invitation (note: this is our twincode within the group)
     * joinedMember INTEGER: the joined member twincode
     * Note:
     * - id, groupId, inviterMember are readonly.
     */
    private static final String INVITATION_TABLE =
            "CREATE TABLE IF NOT EXISTS invitation (id INTEGER PRIMARY KEY,"
                    + " groupId INTEGER NOT NULL, inviterMember INTEGER NOT NULL, joinedMember INTEGER"
                    + ")";

    /**
     * annotation table:
     * cid INTEGER NOT NULL: the conversation key
     * descriptor INTEGER NOT NULL: the descriptor key
     * peerTwincodeOutbound INTEGER: the peer twincode key when the annotation is from a peer
     * creationDate INTEGER: the date when the annotation was created
     * notificationId INTEGER: the optional notification id
     * kind INTEGER NOT NULL: annotation kind
     * value INTEGER: annotation value
     * Note:
     * - cid, descriptor, peerTwincodeOutbound, kind are readonly.
     */
    private static final String ANNOTATION_TABLE =
            "CREATE TABLE IF NOT EXISTS annotation (cid INTEGER NOT NULL, descriptor INTEGER NOT NULL,"
                    + " peerTwincodeOutbound INTEGER, kind INTEGER NOT NULL, value INTEGER, creationDate INTEGER,"
                    + " notificationId INTEGER, PRIMARY KEY(cid, descriptor, peerTwincodeOutbound, kind)"
                    + ")";

    /**
     * operation table:
     * id INTEGER: local database identifier (primary key)
     * creationDate INTEGER NOT NULL: operation creation date
     * cid INTEGER NOT NULL: conversation key
     * type INTEGER: operation type
     * descriptor INTEGER: the descriptor key
     * chunkStart INTEGER: the upload file chunk start.
     * content BLOB: the optional operation data.
     *
     */
    private static final String OPERATION_TABLE =
            "CREATE TABLE IF NOT EXISTS operation (id INTEGER PRIMARY KEY,"
                    + " creationDate INTEGER NOT NULL, cid INTEGER NOT NULL, type INTEGER,"
                    + " descriptor INTEGER, chunkStart INTEGER, content BLOB"
                    + ")";

    /**
     * Tables from V7 to V19:
     *  "CREATE TABLE IF NOT EXISTS conversationConversation (uuid TEXT PRIMARY KEY NOT NULL,
     *   twincodeOutboundId TEXT, peerTwincodeOutboundId TEXT, content BLOB, cid INTEGER, groupId INTEGER,
     *   lastConnectDate INTEGER);";
     * <p>
     *  "CREATE TABLE IF NOT EXISTS conversationDescriptor (twincodeOutboundId TEXT NOT NULL, sequenceId INTEGER,
     *   timestamps BLOB, content BLOB, createdTimestamp INTEGER, cid INTEGER, descriptorType INTEGER,
     *   PRIMARY KEY (twincodeOutboundId, sequenceId));";
     *  CREATE INDEX IF NOT EXISTS idx_conversationDescriptor ON conversationDescriptor (cid, createdTimestamp);
     * <p>
     *  "CREATE TABLE IF NOT EXISTS conversationDescriptorAnnotation (twincodeOutboundId TEXT NOT NULL,
     *   sequenceId INTEGER, peerTwincodeOutboundId TEXT, kind INTEGER, value INTEGER,
     *   PRIMARY KEY (twincodeOutboundId, sequenceId, peerTwincodeOutboundId, kind));";
     */

    @NonNull
    private final ConversationServiceImpl mService;
    @NonNull
    private final TwinlifeImpl mTwinlifeImpl;
    private final WeakHashMap<DescriptorId, DescriptorImpl> mDescriptorCache;
    private final ConversationFactoryImpl mConversationFactory;
    private final GroupConversationFactoryImpl mGroupConversationFactory;

    //
    // Implement DatabaseServiceProvider interface
    //

    ConversationServiceProvider(@NonNull ConversationServiceImpl service, @NonNull DatabaseServiceImpl database) {
        super(service, database, CONVERSATION_TABLE, DatabaseTable.TABLE_CONVERSATION);

        if (DEBUG) {
            Log.d(LOG_TAG, "ConversationServiceProvider: service=" + service);
        }

        mTwinlifeImpl = service.getTwinlifeImpl();
        mService = service;
        mDescriptorCache = new WeakHashMap<>();
        mConversationFactory = new ConversationFactoryImpl(database);
        mGroupConversationFactory = new GroupConversationFactoryImpl(database);
    }

    protected void onCreate(@NonNull Transaction transaction) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate: SQL=" + mCreateTable);
        }

        transaction.createSchema(CONVERSATION_TABLE);
        transaction.createSchema(DESCRIPTOR_TABLE);
        transaction.createSchema(INVITATION_TABLE);
        transaction.createSchema(ANNOTATION_TABLE);
        transaction.createSchema(OPERATION_TABLE);
        transaction.createSchema(DESCRIPTOR_INDEX);
    }

    @Override
    protected void onUpgrade(@NonNull Transaction transaction, int oldVersion, int newVersion) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpgrade: transaction=" + transaction + " oldVersion=" + oldVersion + " newVersion=" + newVersion);
        }

        /*
         * <pre>
         *
         * Database Version 21
         *  Date: 2024/05/07
         *    Add columns creationDate and notificationId in the annotation table to record who annotates for the notification.
         *
         * Database Version 20
         *  Date: 2023/08/28
         *    New database schema optimized to allow loading repository objects and twincodes in a single SQL query.
         *
         * Database Version 16
         *  Date: 2022/02/25
         *
         *  ConversationService
         *   Update oldVersion [12]:
         *    Add table conversationDescriptorAnnotation
         *
         * Database Version 12
         *  Date: 2020/07/06
         *
         *  ConversationService
         *   Update oldVersion [11]:
         *    Add column 'cid INTEGER' in table conversationOperation
         *    Add column 'lastConnectDate INTEGER' in table conversationConversation
         *    Add column 'groupId INTEGER' in table conversationConversation
         *
         * Database Version 9
         *  Date: 2020/02/07
         *
         *  ConversationService
         *   Update oldVersion [7..10]:
         *    Add column 'createdTimestamp INTEGER' in table conversationDescriptor
         *    Add column 'cid INTEGER' in table conversationDescriptor
         *    Add column 'descriptorType INTEGER' in table conversationDescriptor
         *    Add column 'cid INTEGER' in table conversationConversation
         *   Update oldVersion [6]: -
         *   Update oldVersion [5]:
         *    Rename conversationObject table: conversationDescriptor
         *    Delete digest column from conversationDescriptor table
         *   Update oldVersion [0,4]: reset
         *
         * Database Version 7
         *  Date: 2017/04/10
         *
         *  ConversationService
         *   Update oldVersion [6]: -
         *   Update oldVersion [5]:
         *    Rename conversationObject table: conversationDescriptor
         *    Delete digest column from conversationDescriptor table
         *   Update oldVersion [0,4]: reset
         *
         * Database Version 6
         *  Date: 2016/09/09
         *
         *  ConversationService
         *   Update oldVersion [5]:
         *    Rename conversationObject table: conversationDescriptor
         *    Delete digest column from conversationDescriptor table
         *   Update oldVersion [0,4]: reset
         *
         * Database Version 5
         *  Date: 2015/12/02
         *
         *  ConversationService
         *   Update oldVersion [0,4]: reset
         *
         *  Conversation
         *   Schema Version 2
         *  ObjectDescriptor
         *   Schema Version 1
         *
         * Database Version 4
         *  Date: 2015/11/13
         *
         *  ConversationService
         *   Upgrade oldVersion <= 3: reset
         *
         *  Conversation
         *   Schema Version 2
         *  ObjectDescriptor
         *   Schema Version 1
         *
         * </pre>
         */

        onCreate(transaction);
        if (oldVersion < 20) {
            MigrateConversation migrateConversation = new MigrateConversation(mService, mDatabase);

            migrateConversation.onUpgrade(transaction, oldVersion, newVersion);
        } else if (oldVersion == 20) {
            transaction.createSchema("ALTER TABLE annotation ADD COLUMN creationDate INTEGER");
            transaction.createSchema("ALTER TABLE annotation ADD COLUMN notificationId INTEGER");
        }

        // The conversation table was not updated when the pair::bind invocation was received.
        // Repair the conversation table where we should always have:
        //  <conversation>.peerTwincodeOutbound = <conversation>.subject.peerTwincodeOutbound
        // when the <conversation>.groupId is null.
        // See https://www.sqlite.org/lang_update.html for the UPDATE+FROM query (be careful if it must be changed!).
        if (oldVersion <= 24) {
            transaction.execSQLWithArgs("UPDATE conversation AS c SET peerTwincodeOutbound=repo.peerId"
                    + " FROM (SELECT r.id AS id, r.peerTwincodeOutbound as peerId FROM repository AS r) AS repo"
                    + " WHERE c.groupId IS NULL AND c.subject=repo.id", new String[] {});
        }
    }

    //
    // Conversations
    //

    static final class DeleteInfo {
        final long cid;
        final long groupId;
        final long subjectId;

        DeleteInfo(long cid, long groupId, long subjectId) {
            this.cid = cid;
            this.groupId = groupId;
            this.subjectId = subjectId;
        }
    }

    @NonNull
    List<Conversation> listConversations(@Nullable Filter<Conversation> filter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listConversations filter=" + filter);
        }

        // Notes:
        // - the COUNT(d.id) and the left join on the descriptor is quite efficient due to
        //   the idx_descriptor_cid and this is better than a sub query.
        // - use use a LEFT JOIN on repository to find dead conversations.
        QueryBuilder query = new QueryBuilder("c.id, c.groupId, c.uuid, c.creationDate,"
                + " c.subject, r.schemaId, c.peerTwincodeOutbound, c.resourceId, c.peerResourceId,"
                + " c.permissions, c.joinPermissions, c.lastConnectDate, c.lastRetryDate, c.flags,"
                + " COUNT(d.id)"
                + " FROM conversation AS c LEFT JOIN repository AS r ON c.subject = r.id"
                + " LEFT JOIN descriptor AS d ON c.id=d.cid");
        query.where("(c.groupId IS NULL OR c.id = c.groupId)");
        if (filter != null) {
            query.filterOwner("r.owner", filter.owner);
            query.filterName("r.name", filter.name);
            query.filterTwincode("r.twincodeOutbound", filter.twincodeOutbound);
        }
        query.groupBy("c.id");

        final long startTime = System.currentTimeMillis();
        final List<Conversation> result = new ArrayList<>();
        List<DeleteInfo> toDeleteList = null;
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
            while (cursor.moveToNext()) {
                Conversation conversation = loadConversationWithCursor(cursor);
                if (conversation != null) {
                    if (filter == null || filter.accept(conversation)) {
                        result.add(conversation);
                    }
                } else {
                    if (toDeleteList == null) {
                        toDeleteList = new ArrayList<>();
                    }
                    long cid = cursor.getLong(0);
                    long groupId = cursor.getLong(1);
                    long subjectId = cursor.getLong(4);
                    toDeleteList.add(new DeleteInfo(cid, groupId, subjectId));
                }
            }
        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }
        EventMonitor.event("Load conversations", startTime);

        // Some conversations are now invalid (RepositoryObject was removed or became invalid).
        // Remove the conversation, its operations, descriptors, annotations.
        if (toDeleteList != null) {
            try (Transaction transaction = newTransaction()) {
                for (DeleteInfo deleteInfo : toDeleteList) {
                    if (deleteInfo.groupId > 0 && deleteInfo.groupId != deleteInfo.cid) {
                        internalDeleteGroupMemberConversation(transaction, deleteInfo.subjectId, deleteInfo.groupId, deleteInfo.cid, null);
                    } else {
                        internalDeleteConversation(transaction, deleteInfo.subjectId, deleteInfo.cid);
                    }
                }
                transaction.commit();

            } catch (Exception exception) {
                mService.onDatabaseException(exception);
            }
        }
        return result;
    }

    @Nullable
    Conversation loadConversationWithId(long cid) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadConversationWithId: cid=" + cid);
        }

        // Look in the cache for the Contact conversation.
        final DatabaseIdentifier conversationId = new DatabaseIdentifier(mConversationFactory, cid);
        DatabaseObject object = mDatabase.getCache(conversationId);
        if (object instanceof Conversation) {
            return (Conversation) object;
        }

        // Likewise for a Group conversation.
        final DatabaseIdentifier groupId = new DatabaseIdentifier(mGroupConversationFactory, cid);
        object = mDatabase.getCache(groupId);
        if (object instanceof Conversation) {
            return (Conversation) object;
        }

        // We must load it from the database: we can find a Contact conversation or a GroupConversation.
        object = internalLoadConversationWithId(cid);
        if (object == null) {
            return null;
        }

        // If the asked database id corresponds to the conversation which is loaded, return it.
        if (object.getDatabaseId().getId() == cid) {
            return (Conversation) object;
        }

        if (!(object instanceof GroupConversationImpl)) {
            return null;
        }

        // The cid corresponds to a group member and we must find it because we loaded the full GroupConversation.
        final GroupConversationImpl groupConversation = (GroupConversationImpl) object;
        return groupConversation.getConversation(cid);
    }

    @Nullable
    Conversation loadConversationWithSubject(@NonNull RepositoryObject subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadConversationWithSubject: subject=" + subject);
        }

        String query = "SELECT c.id, c.groupId, c.uuid, c.creationDate, c.subject, r.schemaId,"
                + " c.peerTwincodeOutbound, c.resourceId, c.peerResourceId, c.permissions,"
                + " c.joinPermissions, c.lastConnectDate, c.lastRetryDate, c.flags,"
                + " COUNT(d.id)"
                + " FROM conversation AS c"
                + " INNER JOIN repository AS r ON c.subject = r.id"
                + " LEFT JOIN descriptor AS d ON c.id=d.cid"
                + " WHERE c.subject=? AND (c.groupId IS NULL OR c.id = c.groupId)"
                + " GROUP BY c.id";
        return loadConversationWithQuery(query, new String[]{
                Long.toString(subject.getDatabaseId().getId())
        });
    }

    @Nullable
    private Conversation internalLoadConversationWithId(long cid) {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalLoadConversationWithId: cid=" + cid);
        }

        // Load a conversation by its conversation Id.  If this is a group member conversation id,
        // we must get the GroupConversation.  The first condition matches the contact conversation and
        // the second condition matches the group member conversation and then matches in C1 the group
        // conversation that we must return.
        String query = "SELECT c1.id, c1.groupId, c1.uuid, c1.creationDate, c1.subject, r.schemaId,"
                + " c1.peerTwincodeOutbound, c1.resourceId, c1.peerResourceId, c1.permissions,"
                + " c1.joinPermissions, c1.lastConnectDate, c1.lastRetryDate, c1.flags,"
                + " COUNT(d.id)"
                + " FROM conversation AS c1"
                + " INNER JOIN repository AS r ON c1.subject = r.id"
                + " LEFT JOIN conversation AS c2"
                + " LEFT JOIN descriptor AS d ON c1.id=d.cid"
                + " WHERE (c1.id=? AND c1.groupId IS NULL AND c1.id=c2.id) OR (c1.id=c2.groupId AND c2.id=?)"
                + " GROUP BY c1.id";
        return loadConversationWithQuery(query, new String[] {
                Long.toString(cid),
                Long.toString(cid)
        });
    }

    @Nullable
    private Conversation loadConversationWithCursor(@NonNull DatabaseCursor cursor) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadConversationWithCursor: cursor=" + cursor);
        }

        final long id = cursor.getLong(0);
        final long group = cursor.getLong(1);
        final DatabaseObject result;
        if (group == id) {
            final DatabaseIdentifier identifier = new DatabaseIdentifier(mGroupConversationFactory, id);
            final DatabaseObject object = mDatabase.getCache(identifier);
            if (object == null) {
                result = mGroupConversationFactory.createObject(identifier, cursor, 2);
                if (result != null) {
                    mDatabase.putCache(result);
                }
            } else {
                result = object;
                mGroupConversationFactory.loadObject((GroupConversationImpl) object, cursor, 2);
            }
        } else {
            final DatabaseIdentifier identifier = new DatabaseIdentifier(mConversationFactory, id);
            final DatabaseObject object = mDatabase.getCache(identifier);
            if (object == null) {
                result = mConversationFactory.createObject(identifier, cursor, 2);
                if (result != null) {
                    mDatabase.putCache(result);
                }
            } else {
                result = object;
                mConversationFactory.loadObject((ConversationImpl) object, cursor, 2);
            }
        }

        return (Conversation) result;
    }

    @Nullable
    private Conversation loadConversationWithQuery(@NonNull String query, @NonNull String[] params) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadConversationWithQuery: query=" + query + " params=" + params[0]);
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery(query, params)) {

            while (cursor.moveToNext()) {
                Conversation conversation = loadConversationWithCursor(cursor);
                if (conversation != null) {
                    return conversation;
                }
            }
        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }
        return null;
    }

    /**
     * Create a new conversation for a contact. If the contact already has a conversation, return it.
     *
     * @param contact the contact repository object.
     * @return the existing conversation or a new conversation for the contact.
     */
    @Nullable
    ConversationImpl createConversation(@NonNull RepositoryObject contact) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createConversation: contact=" + contact);
        }

        try (Transaction transaction = newTransaction()) {
            final long contactId = contact.getDatabaseId().getId();
            final Long cid = mDatabase.longQuery("SELECT"
                    + " c.id"
                    + " FROM conversation AS c"
                    + " WHERE c.subject=?", new Object[]{ contactId });
            if (cid != null) {
                final DatabaseObject object = mDatabase.getCache(new DatabaseIdentifier(mConversationFactory, cid));
                if (object instanceof ConversationImpl) {
                    return (ConversationImpl) object;
                }
                return (ConversationImpl) loadConversationWithId(cid);
            } else {
                final ContentValues values = new ContentValues();
                final long now = System.currentTimeMillis();
                final long id = transaction.allocateId(DatabaseTable.TABLE_CONVERSATION);
                final DatabaseIdentifier identifier = new DatabaseIdentifier(mConversationFactory, id);
                final UUID conversationId = UUID.randomUUID();
                final UUID resourceId = UUID.randomUUID();
                final TwincodeOutbound peerTwincodeOutbound = contact.getPeerTwincodeOutbound();

                final ConversationImpl conversation = new ConversationImpl(identifier, conversationId, contact,
                        now, resourceId, null,
                        -1L, 0, 0, 0);
                values.put(Columns.ID, id);
                values.put(Columns.UUID, conversationId.toString());
                values.put(Columns.SUBJECT, contactId);
                values.put(Columns.CREATION_DATE, now);
                values.put(Columns.PERMISSIONS, conversation.getPermissions());
                values.put(Columns.RESOURCE_ID, resourceId.toString());
                values.put(Columns.FLAGS, conversation.getFlags());
                if (peerTwincodeOutbound != null && contact.canCreateP2P()) {
                    values.put(Columns.PEER_TWINCODE_OUTBOUND, peerTwincodeOutbound.getDatabaseId().getId());
                }
                transaction.insertOrThrow(Tables.CONVERSATION, null, values);
                transaction.commit();
                mDatabase.putCache(conversation);
                return conversation;
            }

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        return null;
    }

    /**
     * Create a group conversation object and insert it in the database.  Before inserting the
     * new group conversation, verify in the database if a group conversation existed for the
     * repository object.  It is loaded and used if necessary.
     *
     * @param group the group repository object to associated with the conversation.
     * @return the group conversation instance (a new one or an existing one).
     */
    @Nullable
    GroupConversationImpl createGroupConversation(@NonNull RepositoryObject group, boolean owner) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createGroupConversation: group=" + group);
        }

        final TwincodeOutbound groupTwincode = group.getPeerTwincodeOutbound();
        if (groupTwincode == null) {
            return null;
        }
        try (Transaction transaction = newTransaction()) {
            final long groupId = group.getDatabaseId().getId();
            final Long cid = mDatabase.longQuery("SELECT"
                    + " c.id"
                    + " FROM conversation AS c"
                    + " WHERE c.subject=? AND (c.id=c.groupId OR c.groupId IS NULL)", new Object[]{ groupId });
            if (cid != null) {
                final DatabaseObject object = mDatabase.getCache(new DatabaseIdentifier(mGroupConversationFactory, cid));
                if (object instanceof GroupConversationImpl) {
                    return (GroupConversationImpl) object;
                }
                return (GroupConversationImpl) loadConversationWithId(cid);
            } else {
                final ContentValues values = new ContentValues();
                final long now = System.currentTimeMillis();
                final long id = transaction.allocateId(DatabaseTable.TABLE_CONVERSATION);
                final DatabaseIdentifier identifier = new DatabaseIdentifier(mGroupConversationFactory, id);

                final UUID conversationId = UUID.randomUUID();
                final UUID resourceId = UUID.randomUUID();
                final GroupConversationImpl groupConversation = new GroupConversationImpl(identifier, conversationId, group,
                        now, resourceId, -1L, -1L, 0);
                if (owner) {
                    groupConversation.join(-1L);
                }
                values.put(Columns.ID, id);
                values.put(Columns.GROUP_ID, id);
                values.put(Columns.UUID, conversationId.toString());
                values.put(Columns.SUBJECT, groupId);
                values.put(Columns.CREATION_DATE, now);
                values.put(Columns.PEER_TWINCODE_OUTBOUND, groupTwincode.getDatabaseId().getId());
                values.put(Columns.PERMISSIONS, groupConversation.getPermissions());
                values.put(Columns.JOIN_PERMISSIONS, groupConversation.getJoinPermissions());
                values.put(Columns.RESOURCE_ID, resourceId.toString());
                values.put(Columns.FLAGS, groupConversation.getFlags());
                transaction.insertOrThrow(Tables.CONVERSATION, null, values);
                transaction.commit();
                mDatabase.putCache(groupConversation);

                return groupConversation;

            }
        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        return null;
    }

    /**
     * Create a group member conversation object and insert it in the database.  Before inserting the
     * new group member conversation, check that the group member with the given twincode is not already
     * inserted and update and return it if necessary.  The member twincode may not be known yet and we
     * have to insert an entry in the database and mark it for the TwincodeOutboundService to fetch the
     * attributes on the server later on.
     *
     * @param groupConversation the group conversation.
     * @param memberTwincodeId the new member twincode to insert.
     * @param permissions the permissions to assign to the member.
     * @param invitedContactId the optional contact id that invited the new member.
     * @return the group conversation instance (a new one or an existing one).
     */
    @Nullable
    GroupMemberConversationImpl createGroupMemberConversation(@NonNull GroupConversationImpl groupConversation,
                                                              @NonNull UUID memberTwincodeId,
                                                              long permissions,
                                                              @Nullable UUID invitedContactId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createGroupMemberConversation: group=" + groupConversation);
        }

        final long groupId = groupConversation.getDatabaseId().getId();
        try (Transaction transaction = newTransaction()) {
            final Long cid = mDatabase.longQuery("SELECT"
                    + " c.id"
                    + " FROM conversation AS c INNER JOIN twincodeOutbound AS twout ON c.peerTwincodeOutbound=twout.id"
                    + " WHERE c.groupId=? AND twout.twincodeId=?", new String[]{ Long.toString(groupId), memberTwincodeId.toString() });
            if (cid != null) {
                final DatabaseObject object = mDatabase.getCache(new DatabaseIdentifier(mGroupConversationFactory, cid));
                if (object instanceof GroupMemberConversationImpl) {
                    return (GroupMemberConversationImpl) object;
                }
                final Conversation member = loadConversationWithId(cid);
                if (!(member instanceof GroupMemberConversationImpl)) {
                    return null;
                }
                final ContentValues values = new ContentValues();
                values.put(Columns.PERMISSIONS, permissions);
                transaction.updateWithId(Tables.CONVERSATION, values, cid);
                transaction.commit();
                return (GroupMemberConversationImpl) member;

            } else {
                // Too many members or pending invitation in the group, refuse the invitation.
                if (groupConversation.getActiveMemberCount() > MAX_GROUP_MEMBERS) {
                    return null;
                }

                final TwincodeOutbound memberTwincode = transaction.loadOrStoreTwincodeOutboundId(memberTwincodeId);
                if (memberTwincode == null) {
                    return null;
                }
                final ContentValues values = new ContentValues();
                final long now = System.currentTimeMillis();
                final long id = transaction.allocateId(DatabaseTable.TABLE_CONVERSATION);
                final DatabaseIdentifier identifier = new DatabaseIdentifier(mConversationFactory, id);

                final UUID conversationId = UUID.randomUUID();
                final UUID resourceId = UUID.randomUUID();
                final GroupMemberConversationImpl newMember = new GroupMemberConversationImpl(identifier, conversationId, groupConversation,
                        now, resourceId, null, permissions, 0, 0, 0, memberTwincode, invitedContactId);
                values.put(Columns.ID, identifier.getId());
                values.put(Columns.UUID, conversationId.toString());
                values.put(Columns.SUBJECT, groupConversation.getSubject().getDatabaseId().getId());
                values.put(Columns.GROUP_ID, groupId);
                values.put(Columns.CREATION_DATE, now);
                values.put(Columns.PERMISSIONS, permissions);
                values.put(Columns.RESOURCE_ID, resourceId.toString());
                values.put(Columns.FLAGS, newMember.getFlags());
                values.put(Columns.PEER_TWINCODE_OUTBOUND, memberTwincode.getDatabaseId().getId());
                transaction.insertOrThrow(Tables.CONVERSATION, null, values);
                transaction.commit();
                groupConversation.addMember(memberTwincodeId, newMember);
                mDatabase.putCache(newMember);

                return newMember;
            }

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        return null;
    }

    void updateConversation(@NonNull ConversationImpl conversation, @Nullable TwincodeOutbound peerTwincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateConversation: conversation=" + conversation + " peerTwincodeOutbound=" + peerTwincodeOutbound);
        }

        try (Transaction transaction = newTransaction()) {
            final ContentValues values = new ContentValues();
            values.put(Columns.LAST_CONNECT_DATE, conversation.getLastConnectDate());
            values.put(Columns.LAST_RETRY_DATE, conversation.getLastRetryDate());
            values.put(Columns.FLAGS, conversation.getFlags());
            values.put(Columns.PERMISSIONS, conversation.getPermissions());
            final UUID peerResourceId = conversation.getPeerResourceId();
            Log.e(LOG_TAG, "updateConversation: conversation=" + conversation.getDatabaseId() + " peerResource=" + peerResourceId);
            if (peerResourceId == null) {
                values.putNull(Columns.PEER_RESOURCE_ID);
            } else {
                values.put(Columns.PEER_RESOURCE_ID, peerResourceId.toString());
            }
            if (peerTwincodeOutbound != null) {
                values.put(Columns.PEER_TWINCODE_OUTBOUND, peerTwincodeOutbound.getDatabaseId().getId());
            }

            transaction.updateWithId(Tables.CONVERSATION, values, conversation.getDatabaseId().getId());
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void updateGroupConversation(@NonNull GroupConversationImpl conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateGroupConversation: conversation=" + conversation);
        }

        try (Transaction transaction = newTransaction()) {
            final ContentValues values = new ContentValues();
            values.put(Columns.FLAGS, conversation.getFlags());
            values.put(Columns.PERMISSIONS, conversation.getPermissions());
            values.put(Columns.JOIN_PERMISSIONS, conversation.getJoinPermissions());

            transaction.updateWithId(Tables.CONVERSATION, values, conversation.getDatabaseId().getId());

            // If we are leaving the group, remove the associated invitations immediately because the group
            // is no longer visible in the UI (we still have the GroupConversation until every member is notified).
            // We also remove any notification related to that group now to prevent re-entering in the group.
            if (conversation.getState() == ConversationService.GroupConversation.State.LEAVING) {
                final Long subjectId = conversation.getSubject().getDatabaseId().getId();
                internalDeleteGroupInvitations(transaction, subjectId, conversation.getDatabaseId());
                transaction.deleteNotifications(subjectId, null, null);
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Find the group conversation associated with the group twincode.
     *
     * @param groupTwincodeId the group twincode identifying the group.
     * @return the group conversation or null.
     */
    @Nullable
    GroupConversationImpl findGroupConversation(@NonNull UUID groupTwincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "findGroupConversation: groupTwincodeId=" + groupTwincodeId);
        }

        String query = "SELECT c.id, c.groupId, c.uuid, c.creationDate, c.subject, r.schemaId,"
                + " c.peerTwincodeOutbound, c.resourceId, c.peerResourceId, c.permissions,"
                + " c.joinPermissions, c.lastConnectDate, c.lastRetryDate, c.flags FROM conversation AS c"
                + " INNER JOIN repository AS r ON c.subject = r.id"
                + " INNER JOIN twincodeOutbound AS peerTwout ON r.peerTwincodeOutbound = peerTwout.id"
                + " WHERE peerTwout.twincodeId=? AND c.id = c.groupId";
        Conversation conversation = loadConversationWithQuery(query, new String[] { groupTwincodeId.toString() });
        if (conversation instanceof GroupConversationImpl) {
            return (GroupConversationImpl) conversation;
        }
        return null;
    }

    @Override
    public void deleteConversations(@NonNull Transaction transaction, @Nullable Long subjectId,
                                    @Nullable Long twincodeId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteConversations subjectId=" + subjectId);
        }

        final List<DeleteInfo> toDelete = new ArrayList<>();
        QueryBuilder query = new QueryBuilder("c.id, c.groupId, c.subject FROM conversation AS c");
        query.filterLong("c.peerTwincodeOutbound", twincodeId);
        query.filterLong("c.subject", subjectId);

        // Keep the list of conversation to delete in a list.  For a group, we get the group as well as all its members.
        // (because we are going to remove them and we can't iterate at the same time).
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                long groupId = cursor.getLong(1);
                long conversationSubjectId = cursor.getLong(2);
                toDelete.add(new DeleteInfo(id, groupId, conversationSubjectId));
            }
        }

        List<ConversationImpl> deletedList = new ArrayList<>();
        for (DeleteInfo deleteInfo : toDelete) {
            final DatabaseIdentifier identifier;
            if (deleteInfo.groupId > 0 && deleteInfo.groupId != deleteInfo.cid) {
                internalDeleteGroupMemberConversation(transaction, deleteInfo.subjectId, deleteInfo.groupId, deleteInfo.cid, twincodeId);
                identifier = new DatabaseIdentifier(mGroupConversationFactory, deleteInfo.cid);
            } else {
                internalDeleteConversation(transaction, deleteInfo.subjectId, deleteInfo.cid);
                identifier = new DatabaseIdentifier(deleteInfo.groupId == deleteInfo.cid ? mGroupConversationFactory : mConversationFactory, deleteInfo.cid);
            }

            final DatabaseObject object = mDatabase.getCache(identifier);
            if (object != null) {
                mDatabase.evictCache(identifier);

                // If the conversation was in the cache, there could be some pending operations
                // and we must notify the conversation scheduler.
                if (object instanceof ConversationImpl) {
                    deletedList.add((ConversationImpl) object);
                }
            }
        }

        if (!deletedList.isEmpty()) {
            mService.notifyDeletedConversation(deletedList);
        }
    }

    /**
     * Delete the conversation, the associated descriptors, annotations, invitations, operations and notifications (if any).
     *
     * @param conversation the conversation to delete.
     */
    void deleteConversation(@NonNull Conversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteConversation: conversation=" + conversation);
        }

        try (Transaction transaction = newTransaction()) {
            final DatabaseIdentifier identifier = conversation.getDatabaseId();
            final DatabaseIdentifier subject = conversation.getSubject().getDatabaseId();

            if (conversation instanceof GroupMemberConversationImpl) {
                // Delete only the group member descriptors and their notifications (if we know the twincode!)
                final GroupMemberConversationImpl groupMemberConversation = (GroupMemberConversationImpl) conversation;
                final TwincodeOutbound peerTwincodeOutbound = conversation.getPeerTwincodeOutbound();
                final Long twincodeId = peerTwincodeOutbound == null ? null : peerTwincodeOutbound.getDatabaseId().getId();
                internalDeleteGroupMemberConversation(transaction, subject.getId(),
                        groupMemberConversation.getGroupLocalCid(), identifier.getId(), twincodeId);
                if (peerTwincodeOutbound != null) {
                    // Also delete the peer twincode and avatar.
                    transaction.deleteTwincode(peerTwincodeOutbound);
                }
            } else {
                internalDeleteConversation(transaction, subject.getId(), identifier.getId());

                // Delete the invitations that allowed us to join this group.
                if (conversation instanceof GroupConversationImpl) {
                    internalDeleteGroupInvitations(transaction, subject.getId(), identifier);
                }
            }

            transaction.commit();
            mDatabase.evictCache(identifier);

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    private void internalDeleteConversation(@NonNull Transaction transaction, @NonNull Long subjectId,
                                            @NonNull Long cid) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalDeleteConversation: transaction=" + transaction + " cid=" + cid + " subjectId=" + subjectId);
        }

        // Delete all descriptors of the conversation
        final Object[] params = {
                cid
        };
        transaction.delete(Tables.OPERATION, "cid=?", params);
        transaction.delete(Tables.INVITATION, "id IN (SELECT invitation.id FROM invitation"
                + " INNER JOIN descriptor ON invitation.id=descriptor.id WHERE descriptor.cid=?)", params);
        transaction.delete(Tables.ANNOTATION, "cid=?", params);
        transaction.delete(Tables.DESCRIPTOR, "cid=?", params);
        transaction.deleteWithId(Tables.CONVERSATION, cid);
        transaction.deleteNotifications(subjectId, null, null);
    }

    private void internalDeleteGroupMemberConversation(@NonNull Transaction transaction, @NonNull Long subjectId,
                                                       @NonNull Long groupId, @NonNull Long cid,
                                                       @Nullable Long twincodeId) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalDeleteGroupMemberConversation: transaction=" + transaction + " cid=" + cid);
        }

        // When the group member's twincode is not known, try to get the peer twincode from the conversation.
        if (twincodeId == null) {
            twincodeId = mDatabase.longQuery("SELECT peerTwincodeOutbound FROM conversation"
                    + " WHERE id=?", new Object[] { cid });
        }
        if (twincodeId != null) {
            final Object[] params = {
                    groupId,
                    twincodeId
            };
            transaction.delete(Tables.INVITATION, "id IN (SELECT invitation.id FROM invitation"
                    + " INNER JOIN descriptor ON invitation.id=descriptor.id"
                    + " WHERE descriptor.cid=? AND descriptor.twincodeOutbound=?)", params);
            transaction.delete(Tables.ANNOTATION, "cid=? AND peerTwincodeOutbound=?", params);
            transaction.delete(Tables.DESCRIPTOR, "cid=? AND twincodeOutbound=?", params);
            transaction.deleteNotifications(subjectId, twincodeId, null);
        }

        // Delete operations for this group member conversation.
        transaction.delete(Tables.OPERATION, "cid=?", new Object[] { cid });
        transaction.deleteWithId(Tables.CONVERSATION, cid);
    }

    private void internalDeleteGroupInvitations(@NonNull Transaction transaction, @NonNull Long subjectId,
                                                @NonNull DatabaseIdentifier identifier) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalDeleteGroupInvitations: transaction=" + transaction
                    + " subjectId=" + subjectId + " identifier=" + identifier);
        }

        final String[] params = new String[] {
                Long.toString(identifier.getId())
        };
        final List<Long> ids = new ArrayList<>();
        mDatabase.loadIds("SELECT id FROM invitation WHERE groupId=?", params, ids);
        if (!ids.isEmpty()) {
            transaction.deleteWithList(Tables.INVITATION, ids);
            transaction.deleteWithList(Tables.DESCRIPTOR, ids);
            for (Long id : ids) {
                transaction.deleteNotifications(subjectId, null, id);
            }
        }
    }

    //
    // Descriptors
    //

    /**
     * Get the number of descriptors for the conversation.
     *
     * @param conversation the conversation object
     * @return the number of descriptors.
     */
    long getDescriptorCount(@NonNull Conversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDescriptorCount: conversation=" + conversation);
        }

        Long result = null;
        try {
            result = mDatabase.longQuery("SELECT COUNT(*) FROM descriptor WHERE cid=?", new String[] {
                    Long.toString(conversation.getDatabaseId().getId())
            });

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }

        return result == null ? 0 : result;
    }

    /**
     * Identify a list of descriptors that must be removed for the conversation and before the given date.
     * <p>
     * For each member of a conversation, we return the twincode and sequenceId that correspond to the last
     * descriptor of the member which is going to be removed.
     * <p>
     * When 'cid' is a contact conversation, it is associated with at most two twincodes: the conversation
     * twincode and the peerTwincode.  But, when 'cid' is a group conversation, it is associated with
     * each member of the group (if we receive a descriptor).
     *
     * @param conversation the local conversation id.
     * @param twincodeOutboundId the optional twincode to filter on the member.
     * @param resetDate the reset date.
     * @return the last descriptor of a twincode that must be deleted.
     */
    @NonNull
    Map<UUID, DescriptorId> listDescriptorsToDelete(@NonNull Conversation conversation, @Nullable UUID twincodeOutboundId, long resetDate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listDescriptorsToDelete: conversation=" + conversation + " resetDate=" + resetDate);
        }

        final Map<UUID, DescriptorId> result = new HashMap<>();
        final QueryBuilder query = new QueryBuilder("twout.twincodeId AS twincodeOutboundId,"
                + " MAX(d.sequenceId) FROM descriptor AS d"
                + " INNER JOIN twincodeOutbound AS twout ON d.twincodeOutbound=twout.id");
        query.filterLong("d.cid", conversation.getDatabaseId().getId());
        query.filterBefore("d.creationDate", resetDate);
        if (twincodeOutboundId != null) {
            query.filterUUID("twout.twincodeId", twincodeOutboundId);
        } else {
            query.groupBy("twincodeOutboundId");
        }

        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
            while (cursor.moveToNext()) {
                UUID uuid = cursor.getUUID(0);
                if (uuid != null) {
                    long sequenceId = cursor.getLong(1);
                    result.put(uuid, new DescriptorId(0, uuid, sequenceId));
                }
            }

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }
        return result;
    }

    /**
     * Get the list of pending invitations for the group.
     *
     * @param group the group for which invitation are returned.
     * @return the map of pending invitation indexed by the contact UUID.
     */
    @NonNull
    Map<UUID, InvitationDescriptor> listPendingInvitations(@NonNull RepositoryObject group) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listPendingInvitations: group=" + group);
        }

        QueryBuilder query = new QueryBuilder("d.id, d.cid, d.sequenceId, d.twincodeOutbound,"
                + " d.sentTo, replyTo.id, replyTo.sequenceId, replyTo.twincodeOutbound, d.descriptorType,"
                + " d.creationDate, d.sendDate, d.receiveDate, d.readDate, d.updateDate, d.peerDeleteDate,"
                + " d.deleteDate, d.expireTimeout, d.flags, d.content, d.value, r.uuid"
                + " FROM conversation AS g"
                + " INNER JOIN invitation AS i ON i.groupId=g.id"
                + " INNER JOIN descriptor AS d ON i.id = d.id"
                + " INNER JOIN twincodeOutbound AS twout ON twout.id=d.twincodeOutbound"
                + " INNER JOIN conversation AS c ON c.id=d.cid"
                + " INNER JOIN repository AS r ON c.subject=r.id"
                + " LEFT JOIN descriptor AS replyTo ON d.replyTo = replyTo.id");

        query.filterLong("g.subject", group.getDatabaseId().getId());
        query.append(" AND d.value=0");
        final Map<UUID, InvitationDescriptor> result = new HashMap<>();
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
            while (cursor.moveToNext()) {
                final UUID contactId = cursor.getUUID(20);
                if (contactId != null) {
                    final DescriptorImpl descriptor = loadDescriptorWithCursor(cursor);
                    if (descriptor instanceof InvitationDescriptor) {
                        result.put(contactId, (InvitationDescriptor) descriptor);
                    }
                }
            }

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }

        return result;
    }

    /**
     * Get the list of twincodes associated with all descriptors matching the condition.
     * A twincode appears only once if the user has send at least one message.
     *
     * @param conversation the optional conversation.
     * @param type the optional descriptor type.
     * @param beforeTimestamp the date before which we must consider the descriptor.
     * @return the set of twincodes.
     */
    @NonNull
    Set<UUID> listDescriptorTwincodes(@Nullable Conversation conversation, @Nullable Descriptor.Type type, long beforeTimestamp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listDescriptorTwincodes: conversation=" + conversation + " type=" + type + " beforeTimestamp=" + beforeTimestamp);
        }

        // Loading twincodes associated with descriptors.
        QueryBuilder query = new QueryBuilder("DISTINCT twout.twincodeId"
                + " FROM descriptor AS d INNER JOIN twincodeOutbound AS twout ON d.twincodeOutbound=twout.id");
        if (conversation != null) {
            query.filterLong("d.cid", conversation.getDatabaseId().getId());
        }
        query.filterBefore("d.creationDate", beforeTimestamp);
        if (type != null) {
            query.filterInt("d.descriptorType", fromDescriptorType(type));
        }

        final Set<UUID> twincodes = new HashSet<>();
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
            while (cursor.moveToNext()) {
                UUID twincodeOutboundId = cursor.getUUID(0);

                if (twincodeOutboundId == null) {

                    continue;
                }
                twincodes.add(twincodeOutboundId);
            }

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }
        return twincodes;
    }

    @Nullable
    DescriptorImpl loadDescriptorWithId(long descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadDescriptorWithId: descriptorId=" + descriptorId);
        }

        QueryBuilder query = new QueryBuilder("d.id, d.cid, d.sequenceId, d.twincodeOutbound,"
                + " d.sentTo, replyTo.id, replyTo.sequenceId, replyTo.twincodeOutbound, d.descriptorType,"
                + " d.creationDate, d.sendDate, d.receiveDate, d.readDate, d.updateDate, d.peerDeleteDate,"
                + " d.deleteDate, d.expireTimeout, d.flags, d.content, d.value FROM descriptor AS d"
                + " LEFT JOIN descriptor AS replyTo ON d.replyTo = replyTo.id");

        query.filterLong("d.id", descriptorId);
        return loadDescriptorWithQuery(query);
    }

    @Nullable
    DescriptorImpl loadDescriptorImpl(@NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadDescriptorImpl: descriptorId=" + descriptorId);
        }

        // Get the descriptor from the cache: it may be used by a current operation.
        DescriptorImpl descriptor;
        synchronized (mDescriptorCache) {
            descriptor = mDescriptorCache.get(descriptorId);
            if (descriptor != null) {
                return descriptor;
            }
        }

        QueryBuilder query = new QueryBuilder("d.id, d.cid, d.sequenceId, d.twincodeOutbound,"
                + " d.sentTo, replyTo.id, replyTo.sequenceId, replyTo.twincodeOutbound, d.descriptorType,"
                + " d.creationDate, d.sendDate, d.receiveDate, d.readDate, d.updateDate, d.peerDeleteDate,"
                + " d.deleteDate, d.expireTimeout, d.flags, d.content, d.value FROM descriptor AS d"
                + " LEFT JOIN descriptor AS replyTo ON d.replyTo = replyTo.id");

        if (descriptorId.id > 0) {
            query.filterLong("d.id", descriptorId.id);
        } else {
            query.append(" INNER JOIN twincodeOutbound AS twout ON d.twincodeOutbound=twout.id");
            query.filterLong("d.sequenceId", descriptorId.sequenceId);
            query.filterUUID("twout.twincodeId", descriptorId.twincodeOutboundId);
        }
        return loadDescriptorWithQuery(query);
    }

    @NonNull
    List<Descriptor> loadDescriptorImpls(@NonNull Collection<Long> ids) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadDescriptorImpls: ids.size()="+ids.size());
        }

        QueryBuilder query = new QueryBuilder("d.id, d.cid, d.sequenceId, d.twincodeOutbound, d.sentTo, replyTo.id,"
                + " replyTo.sequenceId, replyTo.twincodeOutbound, d.descriptorType, d.creationDate,"
                + " d.sendDate, d.receiveDate, d.readDate, d.updateDate, d.peerDeleteDate, d.deleteDate,"
                + " d.expireTimeout, d.flags, d.content, d.value FROM descriptor AS d"
                + " LEFT JOIN descriptor AS replyTo ON d.replyTo = replyTo.id");

        query.filterIn("d.id", ids);

        return internalListDescriptors(query, null, ids.size());
    }

    List<Descriptor> loadDescriptorImpls(@Nullable Conversation conversation, @Nullable Descriptor.Type[] types,
                                         @NonNull DisplayCallsMode callsMode, long beforeTimestamp, int maxDescriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadDescriptorImpls: conversation=" + conversation + " callsMode=" + callsMode
                    + " beforeTimestamp=" + beforeTimestamp + " maxDescriptors=" + maxDescriptors);
        }

        QueryBuilder query = new QueryBuilder("d.id, d.cid, d.sequenceId, d.twincodeOutbound, d.sentTo, replyTo.id,"
                + " replyTo.sequenceId, replyTo.twincodeOutbound, d.descriptorType, d.creationDate,"
                + " d.sendDate, d.receiveDate, d.readDate, d.updateDate, d.peerDeleteDate, d.deleteDate,"
                + " d.expireTimeout, d.flags, d.content, d.value FROM descriptor AS d"
                + " LEFT JOIN descriptor AS replyTo ON d.replyTo = replyTo.id");
        query.filterBefore("d.creationDate", beforeTimestamp);
        if (conversation != null) {
            query.filterLong("d.cid", conversation.getDatabaseId().getId());
        }
        if (types != null && types.length > 0) {
            query.where(filterTypes(types));
        }
        if (callsMode == DisplayCallsMode.NONE) {
            query.append(" AND d.descriptorType != 12");
        } else if (callsMode == DisplayCallsMode.MISSED) {
            // Missed call descriptors have the 0x20 flag set and the 0x40 flag cleared (See CallDescriptorImpl).
            query.append(" AND (d.descriptorType != 12 OR (d.flags & 0x60 = 0x20))");
        }
        query.append(" ORDER BY d.creationDate DESC");
        query.limit(maxDescriptors);

        return internalListDescriptors(query, conversation, maxDescriptors);
    }

    @Nullable
    List<Pair<Conversation, Descriptor>> searchDescriptors(@NonNull List<Conversation> conversations,
                                                           @NonNull String searchText,
                                                           long beforeTimestamp, int maxDescriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "searchDescriptors: conversations=" + conversations + " searchText=" + searchText
                    + " beforeTimestamp=" + beforeTimestamp + " maxDescriptors=" + maxDescriptors);
        }

        final List<Long> ids = new ArrayList<>(conversations.size());
        final Map<Long, Conversation> toConversation = new HashMap<>();
        for (Conversation c : conversations) {
            ids.add(c.getDatabaseId().getId());
            toConversation.put(c.getDatabaseId().getId(), c);
        }

        final long startTime = System.currentTimeMillis();
        final QueryBuilder query = new QueryBuilder("d.id, d.cid, d.sequenceId, d.twincodeOutbound, d.sentTo, replyTo.id,"
                + " replyTo.sequenceId, replyTo.twincodeOutbound, d.descriptorType, d.creationDate,"
                + " d.sendDate, d.receiveDate, d.readDate, d.updateDate, d.peerDeleteDate, d.deleteDate,"
                + " d.expireTimeout, d.flags, d.content, d.value FROM descriptor AS d"
                + " LEFT JOIN descriptor AS replyTo ON d.replyTo = replyTo.id");
        query.filterBefore("d.creationDate", beforeTimestamp);
        query.filterIn("d.cid", ids);
        query.filterInt("d.descriptorType", 2); // Search only on messages.
        query.filterName("d.content", searchText);
        query.append(" ORDER BY d.creationDate DESC");
        query.limit(maxDescriptors);

        final List<Descriptor> descriptors = internalListDescriptors(query, null, maxDescriptors);
        final List<Pair<Conversation, Descriptor>> result = new ArrayList<>(descriptors.size());
        for (Descriptor d : descriptors) {
            final DescriptorImpl descriptorImpl = (DescriptorImpl) d;
            final Conversation c = toConversation.get(descriptorImpl.getConversationId());
            if (c != null) {
                result.add(new Pair<>(c, d));
            }
        }
        EventMonitor.event("searchDescriptors", startTime);
        return result;
    }

    @NonNull
    Map<Conversation, Descriptor> listLastDescriptors(@NonNull Filter<Conversation> filter, @NonNull DisplayCallsMode callsMode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listLastDescriptors: filter=" + filter + " callsMode=" + callsMode);
        }

        final long startTime = System.currentTimeMillis();
        List<Conversation> conversations = listConversations(filter);
        QueryBuilder query = new QueryBuilder("d.id, d.cid, d.sequenceId, d.twincodeOutbound, d.sentTo, replyTo.id,"
                + " replyTo.sequenceId, replyTo.twincodeOutbound, d.descriptorType, d.creationDate,"
                + " d.sendDate, d.receiveDate, d.readDate, d.updateDate, d.peerDeleteDate, d.deleteDate,"
                + " d.expireTimeout, d.flags, d.content, d.value FROM descriptor AS d"
                + " LEFT JOIN descriptor AS replyTo ON d.replyTo = replyTo.id"
                + " WHERE d.id IN (SELECT id FROM (SELECT d2.id, ROW_NUMBER()"
                + " OVER (PARTITION BY cid ORDER BY creationDate DESC) AS rn FROM descriptor AS d2");
        List<Long> ids = new ArrayList<>(conversations.size());
        Map<Conversation, Descriptor> result = new HashMap<>();
        for (Conversation c : conversations) {
            ids.add(c.getDatabaseId().getId());
            result.put(c, null);
        }
        query.filterIn("d2.cid", ids);
        if (callsMode == DisplayCallsMode.NONE) {
            query.append(" AND d2.descriptorType != 12");
        } else if (callsMode == DisplayCallsMode.MISSED) {
            // Missed call descriptors have the 0x20 flag set and the 0x40 flag cleared (See CallDescriptorImpl).
            query.append(" AND (d2.descriptorType != 12 OR (d2.flags & 0x60 = 0x20))");
        }
        query.append(") lastDescriptorId WHERE rn = 1)");

        List<Descriptor> descriptors = internalListDescriptors(query, null, conversations.size());
        for (Conversation c : conversations) {
            final long cid = c.getDatabaseId().getId();
            for (Descriptor d : descriptors) {
                DescriptorImpl descriptorImpl = (DescriptorImpl) d;
                if (descriptorImpl.getConversationId() == cid) {
                    result.put(c, descriptorImpl);
                    descriptors.remove(d);
                    break;
                }
            }
        }
        EventMonitor.event("listLastDescriptors", startTime);
        return result;
    }

    @NonNull
    private List<Descriptor> internalListDescriptors(@NonNull QueryBuilder query, @Nullable Conversation conversation,
                                                     int maxDescriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalListDescriptors: query=" + query + " maxDescriptors=" + maxDescriptors);
        }

        final List<Descriptor> descriptorImpls = new ArrayList<>(maxDescriptors);
        List<Long> toDelete = null;
        final Map<Long, DescriptorImpl> descriptorMap = new HashMap<>();
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
            while (cursor.moveToNext()) {
                final DescriptorImpl descriptor = loadDescriptorWithCursor(cursor);
                if (descriptor != null && !descriptor.isExpired()) {
                    descriptorImpls.add(descriptor);
                    descriptorMap.put(descriptor.getDescriptorId().id, descriptor);
                } else {
                    if (toDelete == null) {
                        toDelete = new ArrayList<>();
                    }
                    toDelete.add(cursor.getLong(0));
                }
            }

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }

        // Get the descriptor annotations in a second query.
        if (!descriptorMap.isEmpty() && conversation != null) {

            query = new QueryBuilder("descriptor, kind, value, COUNT(*) FROM annotation");
            if (conversation != null) {
                query.filterLong("cid", conversation.getDatabaseId().getId());
            }
            query.filterIn("descriptor", descriptorMap.keySet());
            query.append("GROUP BY descriptor, kind, value");

            // Step 2: run the query and dispatch the annotation to the corresponding descriptor.
            try (DatabaseCursor annotationCursor = mDatabase.execQuery(query)) {

                while (annotationCursor.moveToNext()) {
                    long descriptorId = annotationCursor.getLong(0);
                    AnnotationType kind = toAnnotationType(annotationCursor.getInt(1));
                    if (kind != null) {
                        int value = annotationCursor.getInt(2);
                        int count = annotationCursor.getInt(3);

                        DescriptorImpl descriptorImpl = descriptorMap.get(descriptorId);
                        if (descriptorImpl != null) {
                            List<DescriptorAnnotation> annotations = descriptorImpl.getAnnotations();
                            if (annotations == null) {
                                annotations = new ArrayList<>();
                                descriptorImpl.setAnnotations(annotations);
                            }
                            annotations.add(new DescriptorAnnotation(kind, value, count));
                        }
                    }
                }
            } catch (DatabaseException exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "exception", exception);
                }
                mService.onDatabaseException(exception);
            }
        }

        if (toDelete != null) {
            try (Transaction transaction = mDatabase.newTransaction()) {
                transaction.deleteWithList(Tables.DESCRIPTOR, toDelete);
                transaction.commit();

            } catch (Exception exception) {
                mService.onDatabaseException(exception);
            }
        }
        return descriptorImpls;
    }

    @Nullable
    private DescriptorImpl loadDescriptorWithQuery(@NonNull QueryBuilder query) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadDescriptorWithQuery: query=" + query);
        }

        DescriptorImpl descriptor;
        try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
            if (!cursor.moveToFirst()) {
                return null;
            }

            descriptor = loadDescriptorWithCursor(cursor);
            if (descriptor == null) {
                return null;
            }
        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT kind, value, COUNT(*) FROM annotation WHERE"
                + " cid=? AND descriptor=? GROUP BY kind, value", new String[]{
                Long.toString(descriptor.getConversationId()),
                Long.toString(descriptor.getDatabaseId())})) {

            List<DescriptorAnnotation> annotations = null;
            while (cursor.moveToNext()) {
                AnnotationType type = toAnnotationType(cursor.getInt(0));
                if (type != null) {
                    int value = cursor.getInt(1);
                    int count = cursor.getInt(2);

                    if (annotations == null) {
                        annotations = new ArrayList<>();
                        descriptor.setAnnotations(annotations);
                    }
                    annotations.add(new DescriptorAnnotation(type, value, count));
                }
            }

        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
            return null;
        }

        // Keep the descriptor in the cache: it will be released when there is no strong reference to it.
        // Important note: we must use the DescriptorId instance owned by the DescriptorImpl so that the WeakHash map
        // keeps the reference until the descriptor is no longer used.
        synchronized (mDescriptorCache) {
            mDescriptorCache.put(descriptor.getDescriptorId(), descriptor);
        }
        return descriptor;
    }

    @Nullable
    private DescriptorImpl loadDescriptorWithCursor(@NonNull DatabaseCursor cursor) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadDescriptorWithCursor: cursor=" + cursor);
        }

        // d.id, d.cid, d.sequenceId, d.twincodeOutbound, d.sendTo, replyTo.id,
        //  replyTo.sequenceId, replyTo.twincodeOutbound, d.descriptorType, d.creationDate,
        //  d.sendDate, d.receiveDate, d.readDate, d.updateDate, d.peerDeleteDate, d.deleteDate,
        //  d.expireTimeout, d.flags, d.content, d.value

        final long id = cursor.getLong(0);
        final long cid = cursor.getLong(1);
        final long sequenceId = cursor.getLong(2);
        final long twincodeOutboundId = cursor.getLong(3);
        final long sendToId = cursor.getLong(4);
        final long replyToId = cursor.getLong(5);
        final int descriptorType = cursor.getInt(8);
        final long creationDate = cursor.getLong(9);
        final long sendDate = cursor.getLong(10);
        final long receiveDate = cursor.getLong(11);
        final long readDate = cursor.getLong(12);
        final long updateDate = cursor.getLong(13);
        final long peerDeleteDate = cursor.getLong(14);
        final long deleteDate = cursor.getLong(15);
        final long expireTimeout = cursor.getLong(16);
        final int flags = cursor.getInt(17);
        final String content = cursor.getString(18);
        final long value = cursor.getLong(19);

        final TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(twincodeOutboundId);
        if (twincodeOutbound == null) {
            return null;
        }
        final DescriptorId descriptorId = new DescriptorId(id, twincodeOutbound.getId(), sequenceId);
        UUID sendTo = null;
        if (sendToId > 0) {
            TwincodeOutbound sendTwincodeOutbound = mDatabase.loadTwincodeOutbound(sendToId);
            if (sendTwincodeOutbound != null) {
                sendTo = sendTwincodeOutbound.getId();
            }
        }
        DescriptorId replyTo = null;
        if (replyToId > 0) {
            long replyToSequenceId = cursor.getLong(6);
            long replyToTwincodeId = cursor.getLong(7);
            if (replyToSequenceId > 0 && replyToTwincodeId > 0) {
                TwincodeOutbound replyTwincodeOutbound = mDatabase.loadTwincodeOutbound(replyToTwincodeId);
                if (replyTwincodeOutbound != null) {
                    replyTo = new DescriptorId(replyToId, replyTwincodeOutbound.getId(), replyToSequenceId);
                }
            }
        }

        switch (descriptorType) {
            case 1: // Generic descriptor (not used)
                return null;

            case 2: // Message/Object descriptor
                return new ObjectDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, flags, content);

            case 3: // TransientDescriptor
                return null;

            case 4: // FileDescriptor (should not be used)
                return null;

            case 5: // ImageDescriptor
                return new ImageDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, flags, content, value);

            case 6: // AudioDescriptor
                return new AudioDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, flags, content, value);

            case 7: // VideoDescriptor
                return new VideoDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, flags, content, value);

            case 8: // NamedFileDescriptor
                return new NamedFileDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, flags, content, value);

            case 9: // Invitation descriptor
                return new InvitationDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, content, value);

            case 10: // Geolocation descriptor
                return new GeolocationDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, flags, content);

            case 11: // Twincode descriptor
                return new TwincodeDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, flags, content);

            case 12: // Call descriptor
                return new CallDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, flags, content, value);

            case 13: // Clear descriptor
                return new ClearDescriptorImpl(descriptorId, cid,
                        sendTo, replyTo, creationDate, sendDate, receiveDate, readDate, updateDate, peerDeleteDate, deleteDate,
                        expireTimeout, value);

            default:
                return null;
        }
    }

    @NonNull
    private static String filterTypes(@Nullable Descriptor.Type[] types) {

        if (types == null || types.length == 0) {
            return "";
        }
        if (types.length == 1) {
            return "d.descriptorType = " + fromDescriptorType(types[0]);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("d.descriptorType IN(");
        boolean needSep = false;
        for (Descriptor.Type type : types) {
            if (needSep) {
                sb.append(",");
            }
            needSep = true;
            sb.append(fromDescriptorType(type));
        }
        sb.append(")");
        return sb.toString();
    }

    long newSequenceId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "newSequenceId");
        }

        try (Transaction transaction = newTransaction()) {
            final long sequenceId = transaction.allocateId(DatabaseTable.SEQUENCE);
            transaction.commit();
            return sequenceId;

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        return -1L;
    }

    interface DescriptorFactory {
        DescriptorImpl create(long id, long sequenceId, long cid);
    }

    /**
     * Create a new descriptor for the conversation.  The factory create() method is called with the
     * assigned descriptor id, new sequence id and the conversation id.  It must create the final
     * descriptor instance and populate it with values.  The method is called within a database
     * transaction.
     *
     * @param conversation the conversation to which the descriptor is assigned.
     * @param factory the factory to create the descriptor.
     * @return the descriptor instance or null.
     */
    @Nullable
    DescriptorImpl createDescriptor(@NonNull Conversation conversation, @NonNull DescriptorFactory factory) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createDescriptor: conversation=" + conversation);
        }

        final long localCid = conversation.getDatabaseId().getId();
        try (Transaction transaction = newTransaction()) {
            final long descriptorId = transaction.allocateId(DatabaseTable.TABLE_DESCRIPTOR);
            final long sequenceId = transaction.allocateId(DatabaseTable.SEQUENCE);
            final DescriptorImpl result = factory.create(descriptorId, sequenceId, localCid);
            if (result != null) {
                internalInsertDescriptor(transaction, result, localCid);
                transaction.commit();
            }
            return result;

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    Result createDescriptor(@NonNull Conversation conversation, @NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObjectDescriptor: conversation=" + conversation);
        }

        final long localCid = conversation.getDatabaseId().getId();
        try (Transaction transaction = newTransaction()) {
            internalInsertDescriptor(transaction, descriptorImpl, localCid);

            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        return null;
    }

    private void internalInsertDescriptor(@NonNull Transaction transaction,
                                          @NonNull DescriptorImpl descriptorImpl,
                                          long cid) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "internalInsertDescriptor: transaction=" + transaction + " descriptorImpl=" + descriptorImpl);
        }

        final DescriptorId descriptorId = descriptorImpl.getDescriptorId();
        final ContentValues values = new ContentValues();
        values.put(Columns.ID, descriptorId.id);
        values.put(Columns.CID, cid);
        values.put(Columns.SEQUENCE_ID, descriptorImpl.getSequenceId());
        values.put(Columns.CREATION_DATE, descriptorImpl.getCreatedTimestamp());
        final TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(descriptorImpl.getTwincodeOutboundId());
        if (twincodeOutbound != null) {
            values.put(Columns.TWINCODE_OUTBOUND, twincodeOutbound.getDatabaseId().getId());
        }
        final UUID sendTo = descriptorImpl.getSendTo();
        if (sendTo != null) {
            final TwincodeOutbound sendToTwincode = mDatabase.loadTwincodeOutbound(sendTo);
            if (sendToTwincode != null) {
                values.put(Columns.SENT_TO, sendToTwincode.getDatabaseId().getId());
            }
        }
        final DescriptorId replyTo = descriptorImpl.getReplyToDescriptorId();
        if (replyTo != null) {
            long replyToDescriptorId = -1L;

            if (replyTo.id > 0) {
                replyToDescriptorId = replyTo.id;
            } else {
                TwincodeOutbound replyPeerTwincode = transaction.loadOrStoreTwincodeOutboundId(replyTo.twincodeOutboundId);
                if (replyPeerTwincode != null) {
                    try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT d.id FROM descriptor AS d"
                            + " WHERE d.cid=? AND d.sequenceId=? AND d.twincodeOutbound=?", new String[]{
                            Long.toString(cid), Long.toString(replyTo.sequenceId),
                            Long.toString(replyPeerTwincode.getDatabaseId().getId())})) {
                        if (cursor.moveToFirst()) {
                            replyToDescriptorId = cursor.getLong(0);
                        }
                    }
                }
            }
            if (replyToDescriptorId > 0) {
                values.put(Columns.REPLY_TO, replyToDescriptorId);
            }
        }
        values.put(Columns.DESCRIPTOR_TYPE, fromDescriptorType(descriptorImpl.getType()));
        values.put(Columns.EXPIRE_TIMEOUT, descriptorImpl.getExpireTimeout());
        values.put(Columns.FLAGS, descriptorImpl.getFlags());
        if (descriptorImpl.getSentTimestamp() < 0) {
            values.put(Columns.SEND_DATE, -1L);
            values.put(Columns.READ_DATE, -1L);
            values.put(Columns.RECEIVE_DATE, -1L);
            descriptorImpl.setReadTimestamp(-1);
            descriptorImpl.setReceivedTimestamp(-1);
        }
        values.put(Columns.VALUE, descriptorImpl.getValue());

        // Optional operation specific data.
        String content = descriptorImpl.serialize();
        if (content != null) {
            values.put(Columns.CONTENT, content);
        }
        // Note chunkStart is always 0 on insert.
        transaction.insertOrThrow(Tables.DESCRIPTOR, null, values);

        synchronized (mDescriptorCache) {
            mDescriptorCache.put(descriptorImpl.getDescriptorId(), descriptorImpl);
        }
    }

    /**
     * Create an invitation for the group and check if there are enough room for the new member.
     *
     * @param conversation the conversation where the invitation is sent.
     * @param groupConversation the group to invite.
     * @param name the group name sent in the invitation.
     * @param publicKey the optional group public key.
     * @return the invitation descriptor or null.
     */
    @Nullable
    InvitationDescriptorImpl createInvitation(@NonNull Conversation conversation, @NonNull GroupConversationImpl groupConversation,
                                              @NonNull String name, @Nullable String publicKey) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createInvitation: conversation=" + conversation);
        }

        final TwincodeOutbound groupTwincode = groupConversation.getPeerTwincodeOutbound();
        if (groupTwincode == null) {
            return null;
        }
        final TwincodeOutbound inviterTwincode = groupConversation.getTwincodeOutbound();
        if (inviterTwincode == null) {
            return null;
        }

        final long cid = conversation.getDatabaseId().getId();
        final long groupId = groupConversation.getDatabaseId().getId();
        try (Transaction transaction = newTransaction()) {
            final Long count = mDatabase.longQuery("SELECT COUNT(*) FROM invitation AS i"
                    + " INNER JOIN descriptor AS d ON i.id=d.id"
                    + " WHERE i.groupId=? AND d.value=0", new String[] { Long.toString(groupId) });

            // Too many members or pending invitation in the group, refuse the invitation.
            if (count != null && count + groupConversation.getActiveMemberCount() > MAX_GROUP_MEMBERS) {
                return null;
            }

            long id = transaction.allocateId(DatabaseTable.TABLE_DESCRIPTOR);
            long sequenceId = transaction.allocateId(DatabaseTable.SEQUENCE);
            final UUID twincodeOutboundId = conversation.getTwincodeOutboundId();
            final DescriptorId descriptorId = new DescriptorId(id, twincodeOutboundId, sequenceId);
            InvitationDescriptorImpl invitation = new InvitationDescriptorImpl(descriptorId, cid,
                    groupTwincode.getId(), inviterTwincode.getId(), name, publicKey);

            internalInsertDescriptor(transaction, invitation, cid);

            final ContentValues values = new ContentValues();
            values.put(Columns.ID, invitation.getDatabaseId());
            values.put(Columns.GROUP_ID, groupId);
            values.put(Columns.ID, invitation.getDatabaseId());
            values.put(Columns.INVITER_MEMBER, inviterTwincode.getDatabaseId().getId());
            transaction.insertOrThrow(Tables.INVITATION, null, values);
            transaction.commit();
            return invitation;

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return null;
        }
    }

    Result insertOrUpdateDescriptorImpl(@NonNull Conversation conversation, @NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "insertOrUpdateDescriptorImpl: descriptorImpl=" + descriptorImpl + " conversation=" + conversation);
        }

        final long cid = conversation.getDatabaseId().getId();
        Result result;
        try (Transaction transaction = newTransaction()) {
            TwincodeOutbound peerTwincode = transaction.loadOrStoreTwincodeOutboundId(descriptorImpl.getTwincodeOutboundId());
            if (peerTwincode == null) {
                return Result.ERROR;
            }

            long descriptorId = -1L;

            try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT d.id FROM descriptor AS d"
                    + " WHERE d.cid=? AND d.sequenceId=? AND d.twincodeOutbound=?", new String[] {
                            Long.toString(cid), Long.toString(descriptorImpl.getSequenceId()),
                            Long.toString(peerTwincode.getDatabaseId().getId())})) {
                if (cursor.moveToFirst()) {
                    descriptorId = cursor.getLong(0);
                }
            }

            if (descriptorId < 0) {
                final long did = transaction.allocateId(DatabaseTable.TABLE_DESCRIPTOR);
                descriptorImpl.updateDatabaseIds(cid, did);

                internalInsertDescriptor(transaction, descriptorImpl, cid);
                result = Result.STORED;
            } else {
                descriptorImpl.updateDatabaseIds(cid, descriptorId);
                result = Result.UPDATED;

                // Keep the descriptor in the cache: it will be released when there is no strong reference to it.
                // Important note: we must use the DescriptorId instance owned by the DescriptorImpl so that the WeakHash map
                // keeps the reference until the descriptor is no longer used.
                synchronized (this) {
                    mDescriptorCache.put(descriptorImpl.getDescriptorId(), descriptorImpl);
                }
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
            return Result.ERROR;
        }
        return result;
    }

    /**
     * Update the descriptor content, flags and timestamps.
     *
     * @param descriptorImpl the descriptor to update.
     */
    void updateDescriptor(@NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateDescriptor: descriptorImpl=" + descriptorImpl);
        }

        try (Transaction transaction = newTransaction()) {
            final ContentValues values = new ContentValues();
            values.put(Columns.CONTENT, descriptorImpl.serialize());
            values.put(Columns.VALUE, descriptorImpl.getValue());
            values.put(Columns.SEND_DATE, descriptorImpl.getSentTimestamp());
            values.put(Columns.RECEIVE_DATE, descriptorImpl.getReceivedTimestamp());
            values.put(Columns.READ_DATE, descriptorImpl.getReadTimestamp());
            values.put(Columns.UPDATE_DATE, descriptorImpl.getUpdatedTimestamp());
            values.put(Columns.PEER_DELETE_DATE, descriptorImpl.getPeerDeletedTimestamp());
            values.put(Columns.DELETE_DATE, descriptorImpl.getDeletedTimestamp());
            values.put(Columns.FLAGS, descriptorImpl.getFlags());
            transaction.updateWithId(Tables.DESCRIPTOR, values, descriptorImpl.getDatabaseId());

            if (descriptorImpl instanceof InvitationDescriptorImpl) {
                final InvitationDescriptorImpl invitation = (InvitationDescriptorImpl) descriptorImpl;
                final UUID memberId = invitation.getMemberTwincodeId();
                if (memberId != null) {
                    final TwincodeOutbound memberTwincode = transaction.loadOrStoreTwincodeOutboundId(memberId);

                    values.clear();
                    values.put(Columns.JOINED_MEMBER, memberTwincode.getDatabaseId().getId());
                    transaction.updateWithId(Tables.INVITATION, values, descriptorImpl.getDatabaseId());
                }
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Update the invitation descriptor content, flags and timestamps when it is accepted.
     * This is special because we create a row in the `invitation` table between the invitation
     * descriptor and the group conversation.
     *
     * @param descriptorImpl the descriptor to update.
     */
    void acceptInvitation(@NonNull InvitationDescriptorImpl descriptorImpl,
                          @NonNull GroupConversationImpl groupConversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateInvitationDescriptor: descriptorImpl=" + descriptorImpl);
        }

        try (Transaction transaction = newTransaction()) {
            final ContentValues values = new ContentValues();
            values.put(Columns.CONTENT, descriptorImpl.serialize());
            values.put(Columns.VALUE, descriptorImpl.getValue());
            values.put(Columns.SEND_DATE, descriptorImpl.getSentTimestamp());
            values.put(Columns.RECEIVE_DATE, descriptorImpl.getReceivedTimestamp());
            values.put(Columns.READ_DATE, descriptorImpl.getReadTimestamp());
            values.put(Columns.UPDATE_DATE, descriptorImpl.getUpdatedTimestamp());
            values.put(Columns.PEER_DELETE_DATE, descriptorImpl.getPeerDeletedTimestamp());
            values.put(Columns.DELETE_DATE, descriptorImpl.getDeletedTimestamp());
            values.put(Columns.FLAGS, descriptorImpl.getFlags());
            transaction.updateWithId(Tables.DESCRIPTOR, values, descriptorImpl.getDatabaseId());

            final TwincodeOutbound memberTwincode = groupConversation.getTwincodeOutbound();
            final TwincodeOutbound inviterMemberTwincode = transaction.loadOrStoreTwincodeOutboundId(descriptorImpl.getTwincodeOutboundId());

            if (memberTwincode != null && inviterMemberTwincode != null) {
                values.clear();
                values.put(Columns.ID, descriptorImpl.getDatabaseId());
                values.put(Columns.JOINED_MEMBER, memberTwincode.getDatabaseId().getId());
                values.put(Columns.INVITER_MEMBER, inviterMemberTwincode.getDatabaseId().getId());
                values.put(Columns.GROUP_ID, groupConversation.getDatabaseId().getId());
                transaction.insertOrReplace(Tables.INVITATION, values);
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Update only the descriptor timestamps.
     *
     * @param descriptorImpl the descriptor to update.
     */
    void updateDescriptorImplTimestamps(@NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateDescriptorImplTimestamps: descriptorImpl=" + descriptorImpl);
        }

        try (Transaction transaction = newTransaction()) {
            final ContentValues values = new ContentValues();
            values.put(Columns.SEND_DATE, descriptorImpl.getSentTimestamp());
            values.put(Columns.RECEIVE_DATE, descriptorImpl.getReceivedTimestamp());
            values.put(Columns.READ_DATE, descriptorImpl.getReadTimestamp());
            values.put(Columns.DELETE_DATE, descriptorImpl.getDeletedTimestamp());
            values.put(Columns.PEER_DELETE_DATE, descriptorImpl.getPeerDeletedTimestamp());
            transaction.updateWithId(Tables.DESCRIPTOR, values, descriptorImpl.getDatabaseId());
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * Delete the descriptors that have been identified by listDescriptorsToDelete.
     * They were not deleted there because we had to check and make sure the associated
     * files are removed first.
     *
     * @param conversation the conversation.
     * @param descriptorList list of descriptors to remove.
     * @param keepMediaMessages when true keep the messages, image, video file and invitation descriptors.
     * @param deletedOperations will contain a list of operation ids that have been deleted (if any).
     * @return true if some descriptors were removed.
     */
    boolean deleteDescriptors(@NonNull Conversation conversation,
                              @NonNull Map<UUID, DescriptorId> descriptorList,
                              boolean keepMediaMessages,
                              @NonNull List<Long> deletedOperations) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteDescriptors: conversation=" + conversation + " descriptorList=" + descriptorList.size()
                    + " keepMediaMessages=" + keepMediaMessages);
        }

        final long cid = conversation.getDatabaseId().getId();

        boolean deleted = false;
        try (Transaction transaction = newTransaction()) {
            for (Map.Entry<UUID, DescriptorId> toDelete : descriptorList.entrySet()) {
                final UUID twincodeOutboundId = toDelete.getKey();
                final TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(twincodeOutboundId);
                if (twincodeOutbound == null) {
                    continue;
                }

                final String[] params = {
                        Long.toString(cid),
                        Long.toString(twincodeOutbound.getDatabaseId().getId()),
                        Long.toString(toDelete.getValue().sequenceId)
                };
                int count;
                if (keepMediaMessages) {
                    // Step 1: identify the operations that must be removed before deleting the descriptors.
                    mDatabase.loadIds("SELECT o.id FROM descriptor AS d"
                            + " INNER JOIN operation AS o ON d.id = o.descriptor"
                            + " WHERE d.cid=? AND d.twincodeOutbound=? AND d.sequenceId<=?"
                            + " AND d.descriptorType != 2 AND d.descriptorType != 5 AND d.descriptorType != 7 "
                            + " AND d.descriptorType != 9 AND d.descriptorType != 11)", params, deletedOperations);

                    // Step 2: use a subquery to delete the annotations since DELETE+JOIN is not possible with SQLite.
                    // We only delete the annotations of deleted descriptors.
                    transaction.delete(Tables.ANNOTATION,
                            "descriptor IN (SELECT d.id FROM descriptor AS d WHERE"
                                    + " d.cid=? AND d.twincodeOutbound=? AND d.sequenceId<=?"
                                    + " AND d.descriptorType != 2 AND d.descriptorType != 5 AND d.descriptorType != 7 "
                                    + " AND d.descriptorType != 9 AND d.descriptorType != 11)", params);

                    // Step 3: delete the descriptors but keep messages, images, video, invitations.
                    count = transaction.delete(Tables.DESCRIPTOR, "cid=? AND twincodeOutbound=? AND sequenceId<=?"
                            + " AND descriptorType != 2 AND descriptorType != 5"
                            + " AND descriptorType != 7 AND descriptorType != 9 AND descriptorType != 11", params);

                    // Step 4: clear the length of image and video descriptors.
                    ContentValues values = new ContentValues();
                    values.put(Columns.VALUE, 0);
                    try {
                        transaction.update(Tables.DESCRIPTOR, values, "cid=? AND twincodeOutbound=? AND sequenceId <=?"
                                + " AND (descriptorType=5 OR descriptorType=7)", params);
                    } catch (DatabaseException sqlException) {
                        mService.onDatabaseException(sqlException);
                    }

                } else {
                    // Step 1: identify the operations that must be removed before deleting the descriptors.
                    mDatabase.loadIds("SELECT o.id FROM descriptor AS d"
                            + " INNER JOIN operation AS o ON d.id = o.descriptor"
                            + " WHERE d.cid=? AND d.twincodeOutbound=? AND d.sequenceId<=?", params, deletedOperations);

                    // Step 2: delete the annotations.
                    transaction.delete(Tables.ANNOTATION, "descriptor IN (SELECT d.id FROM descriptor AS d"
                            + " WHERE d.cid=? AND d.twincodeOutbound=? AND d.sequenceId<=?)", params);

                    // Step 3: delete the descriptors.
                    count = transaction.delete(Tables.DESCRIPTOR, "cid=? AND twincodeOutbound=? AND sequenceId<=?", params);
                }
                if (count != 0) {
                    deleted = true;
                }
            }

            // Last step, delete the operations associated with the deleted descriptors and return the list
            // so that the conversation scheduler can remove them.
            if (!deletedOperations.isEmpty()) {
                transaction.deleteWithList(Tables.OPERATION, deletedOperations);
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
        return deleted;
    }

    /**
     * Delete the media descriptors before a given date and return 3 lists:
     * - [0]: descriptors that are removed and can be removed immediately without creating any operation,
     * - [1]: descriptors created by the current conversation which must be removed on the peer,
     * - [2]: descriptors owned by the peer and which we must inform we have removed.
     *
     * @param conversation the conversation where media descriptors are deleted.
     * @param beforeDate the date to search
     * @return the array of 3 lists of media descriptors.
     */
    List<DescriptorId>[] deleteMediaDescriptors(@NonNull Conversation conversation, long beforeDate, long resetDate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteMediaDescriptors: conversation=" + conversation + " beforeDate=" + beforeDate +
                    " resetDate=" + resetDate);
        }

        List<DescriptorId> deleteList = null;
        List<DescriptorId> peerDeleteList = null;
        List<DescriptorId> ownerDeleteList = null;
        final TwincodeOutbound twincodeOutbound = conversation.getTwincodeOutbound();
        final long ownerTwincodeId = twincodeOutbound == null ? 0 : twincodeOutbound.getDatabaseId().getId();
        final long localCid = conversation.getDatabaseId().getId();
        final long subjectId = conversation.getSubject().getDatabaseId().getId();

        try (Transaction transaction = newTransaction()) {

            QueryBuilder query = new QueryBuilder("d.id, d.sequenceId, d.twincodeOutbound,"
                    + " twout.twincodeId, d.sendDate, d.deleteDate, d.peerDeleteDate FROM descriptor AS d"
                    + " LEFT JOIN twincodeOutbound AS twout ON twout.id = d.twincodeOutbound");
            query.filterLong("d.cid", localCid);
            query.filterBefore("d.creationDate", beforeDate);
            query.where("d.descriptorType >= 4 AND d.descriptorType <= 8 AND (");
            query.filterLong("d.twincodeOutbound", ownerTwincodeId);
            query.where(" OR d.peerDeleteDate=0)");

            try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final long sequenceId = cursor.getLong(1);
                    final long twincodeId = cursor.getLong(2);
                    final UUID descriptorTwincodeOutboundId = cursor.getUUID(3);
                    final long sendDate = cursor.getLong(4);
                    final long deleteDate = cursor.getLong(5);
                    final long peerDeleteDate = cursor.getLong(6);

                    // If this is one of our descriptor, we can delete it immediately if it was not sent
                    // or the peer has removed it.
                    final DescriptorId descriptorId = new DescriptorId(id, descriptorTwincodeOutboundId, sequenceId);
                    if (ownerTwincodeId == twincodeId) {
                        if (sendDate <= 0 || peerDeleteDate > 0) {
                            if (deleteList == null) {
                                deleteList = new ArrayList<>();
                            }
                            deleteList.add(descriptorId);
                        } else if (deleteDate == 0) {
                            // Keep the descriptor but mark the delete date to the resetDate.
                            if (ownerDeleteList == null) {
                                ownerDeleteList = new ArrayList<>();
                            }
                            ownerDeleteList.add(descriptorId);
                        }
                    } else {
                        // This is a media peer descriptor, we can delete it immediately and report a PEER_DELETE operation
                        // to the sender.
                        if (peerDeleteList == null) {
                            peerDeleteList = new ArrayList<>();
                        }
                        peerDeleteList.add(descriptorId);
                    }
                }

                if (ownerDeleteList != null) {
                    updateDescriptorTimestamps(transaction, ownerDeleteList, 0, resetDate);
                }
                if (deleteList != null) {
                    deleteDescriptorList(transaction, subjectId, deleteList);
                }
                if (peerDeleteList != null) {
                    deleteDescriptorList(transaction, subjectId, peerDeleteList);
                }
                transaction.commit();

            } catch (DatabaseException exception) {
                mService.onDatabaseException(exception);
            }

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }

        final List<DescriptorId>[] result = new List[3];

        result[0] = deleteList;
        result[1] = ownerDeleteList;
        result[2] = peerDeleteList;
        return result;
    }

    void deleteDescriptorImpl(@NonNull Conversation conversation, @NonNull DescriptorImpl descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteDescriptorImpl: conversation=" + conversation + " descriptor=" + descriptor);
        }

        descriptor.delete(mTwinlifeImpl.getFilesDir());

        deleteDescriptor(conversation, descriptor.getDescriptorId());
    }

    void deleteDescriptor(@NonNull Conversation conversation, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteDescriptor: descriptorId=" + descriptorId);
        }

        try (Transaction transaction = newTransaction()) {
            final Long subjectId = conversation.getSubject().getDatabaseId().getId();
            final List<DescriptorId> list = new ArrayList<>(1);
            list.add(descriptorId);
            deleteDescriptorList(transaction, subjectId, list);
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    /**
     * The peer has cleared the descriptors on its side and we have to mark those descriptors as deleted by
     * the peer.  If some descriptor are marked DELETED, it means we were waiting for the peer deletion and
     * we can remove them.  To notify upper layers, we return a list of DescriptorId that are really deleted now.
     * Note: we don't delete the peer descriptors since it was a local clear on its side.
     *
     * @param conversation the local conversation.
     * @param clearDate the clear date that identifies the descriptors to be marked.
     * @param resetDate the date when the reset/clear action was made by the peer (used to update the timestamps).
     * @param twincodeOutboundId the twincode used by the conversation to send messages.
     * @param keepMediaMessages when true keep the messages, image, video file and invitations descriptors.
     * @return the list of descriptors which are now removed on both sides.
     */
    @Nullable
    List<DescriptorId> markDescriptorDeleted(@NonNull Conversation conversation, long clearDate, long resetDate,
                                             @NonNull UUID twincodeOutboundId, boolean keepMediaMessages) {
        if (DEBUG) {
            Log.d(LOG_TAG, "markDescriptorDeleted: conversation=" + conversation + " clearDate=" + clearDate
                    + " resetDate=" + resetDate + " twincodeOutboundId=" + twincodeOutboundId
                    + " keepMediaMessages=" + keepMediaMessages);
        }

        List<DescriptorId> deleteList = null;
        try (Transaction transaction = newTransaction()) {
            TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(twincodeOutboundId);
            if (twincodeOutbound == null) {
                return null;
            }

            final long ownerTwincodeId = twincodeOutbound.getDatabaseId().getId();
            List<DescriptorId> updatePeerDeleteDate = null;
            List<DescriptorId> updateDeleteDate = null;
            QueryBuilder query = new QueryBuilder("d.id, d.sequenceId, d.twincodeOutbound,"
                    + " twout.twincodeId, d.deleteDate, d.peerDeleteDate FROM descriptor AS d"
                    + " LEFT JOIN twincodeOutbound AS twout ON d.twincodeOutbound=twout.id");
            query.filterLong("d.cid", conversation.getDatabaseId().getId());
            query.filterBefore("d.creationDate", clearDate);
            if (keepMediaMessages) {
                query.where("d.descriptorType != 2 AND d.descriptorType != 5"
                        + " AND d.descriptorType != 7 AND d.descriptorType != 9 AND d.descriptorType != 11");
            }
            query.append(" AND (");
            query.filterLong("d.twincodeOutbound", ownerTwincodeId);
            query.append(" OR d.deleteDate = 0)");

            try (DatabaseCursor cursor = mDatabase.execQuery(query)) {
                while (cursor.moveToNext()) {
                    final long id = cursor.getLong(0);
                    final long sequenceId = cursor.getLong(1);
                    final long twincodeId = cursor.getLong(2);
                    final UUID twincodeUUID = cursor.getUUID(3);
                    final long deleteDate = cursor.getLong(4);
                    final long peerDeleteDate = cursor.getLong(5);

                    // If this is one of our descriptor, check if it is not yet marked as deleted by the peer, set the mark now.
                    if (twincodeId == ownerTwincodeId) {
                        if (peerDeleteDate == 0) {

                            // Our descriptor is also deleted locally, we can remove it because the peer has deleted it.
                            if (deleteDate != 0 || twincodeUUID == null) {
                                if (deleteList == null) {
                                    deleteList = new ArrayList<>();
                                }
                                deleteList.add(new DescriptorId(id, Twincode.NOT_DEFINED, sequenceId));
                            } else {
                                if (updatePeerDeleteDate == null) {
                                    updatePeerDeleteDate = new ArrayList<>();
                                }
                                updatePeerDeleteDate.add(new DescriptorId(id, twincodeUUID, sequenceId));
                            }
                        }
                    } else {
                        // Mark the peer descriptor as deleted but keep it.
                        if (deleteDate == 0) {
                            if (updateDeleteDate == null) {
                                updateDeleteDate = new ArrayList<>();
                            }
                            updateDeleteDate.add(new DescriptorId(id, twincodeUUID, sequenceId));
                        }
                    }
                }
            } catch (DatabaseException exception) {
                mService.onDatabaseException(exception);
            }

            if (updatePeerDeleteDate != null) {
                updateDescriptorTimestamps(transaction, updatePeerDeleteDate, resetDate, 0);
            }
            if (updateDeleteDate != null) {
                updateDescriptorTimestamps(transaction, updateDeleteDate, 0, resetDate);
            }

            if (deleteList != null) {
                deleteDescriptorList(transaction, conversation.getSubject().getDatabaseId().getId(), deleteList);
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }

        return deleteList;
    }

    private void updateDescriptorTimestamps(@NonNull Transaction transaction,
                                            @NonNull List<DescriptorId> descriptors,
                                            long peerDeletedTime, long deletedTime) throws DatabaseException {

        // Update the timestamps.
        for (DescriptorId descriptorId : descriptors) {
            ContentValues values = new ContentValues();
            if (peerDeletedTime > 0) {
                values.put(Columns.PEER_DELETE_DATE, peerDeletedTime);
            }
            if (deletedTime > 0) {
                values.put(Columns.DELETE_DATE, deletedTime);
            }

            transaction.updateWithId(Tables.DESCRIPTOR, values, descriptorId.id);
        }
    }

    private void deleteDescriptorList(@NonNull Transaction transaction,
                                      @NonNull Long subjectId,
                                      @NonNull List<DescriptorId> deleteList) throws DatabaseException {

        for (DescriptorId descriptorId : deleteList) {
            String[] params = {
                    Long.toString(descriptorId.id)
            };

            transaction.delete(Tables.ANNOTATION, "descriptor=?", params);
            transaction.deleteWithId(Tables.INVITATION, descriptorId.id);
            transaction.deleteWithId(Tables.DESCRIPTOR, descriptorId.id);
            transaction.deleteNotifications(subjectId, null, descriptorId.id);
            synchronized (this) {
                mDescriptorCache.remove(descriptorId);
            }
        }
    }

    //
    // Descriptor Annotations
    //

    /**
     * Load the descriptor annotations which are local (ie, created by current user, ie with a
     * NULL peerTwincodeOutboundId) and associated with the descriptor.
     *
     * @param conversation the conversation id.
     * @param descriptorId the descriptor id to get the local only annotations.
     * @return the list of local annotations.
     */
    @NonNull
    List<DescriptorAnnotation> loadLocalAnnotations(@NonNull Conversation conversation, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadLocalAnnotations: conversation=" + conversation + " descriptorId=" + descriptorId);
        }

        final String[] params = {
                Long.toString(conversation.getDatabaseId().getId()),
                Long.toString(descriptorId.id)
        };
        final List<DescriptorAnnotation> annotations = new ArrayList<>();
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT kind, value"
                + " FROM annotation WHERE cid=? AND descriptor=? AND peerTwincodeOutbound IS NULL", params)) {
            while (cursor.moveToNext()) {
                final AnnotationType type = toAnnotationType(cursor.getInt(0));
                if (type != null) {
                    final int value = cursor.getInt(1);

                    annotations.add(new DescriptorAnnotation(type, value, 0));
                }
            }
        } catch (DatabaseException sqlException) {
            mService.onDatabaseException(sqlException);
        }
        return annotations;
    }

    /**
     * Set the descriptor annotations for a given descriptor and annotated by a given peer twincode.
     * Existing annotations of the given peer twincode are either updated or removed.
     *
     * @param descriptorImpl the descriptor to update.
     * @param peerTwincodeOutboundId the peer twincode.
     * @param annotations the list of annotations to set.
     * @param annotatingUsers update to indicate the user who annotated the descriptor.
     * @return true if the descriptor was modified (some annotations added, updated or removed).
     */
    boolean setAnnotations(@NonNull DescriptorImpl descriptorImpl, @NonNull UUID peerTwincodeOutboundId,
                           @NonNull List<DescriptorAnnotation> annotations,
                           @NonNull Set<TwincodeOutbound> annotatingUsers) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setAnnotations: descriptorImpl=" + descriptorImpl);
        }

        final String descriptorId = Long.toString(descriptorImpl.getDatabaseId());
        final String conversationId = Long.toString(descriptorImpl.getConversationId());

        final Map<AnnotationType, Integer> newList = new HashMap<>();
        for (DescriptorAnnotation annotation : annotations) {
            newList.put(annotation.getType(), annotation.getValue());
        }
        List<AnnotationType> deleteList = null;
        List<AnnotationType> updateList = null;
        boolean modified = false;

        try (Transaction transaction = newTransaction()) {
            final TwincodeOutbound twincodeOutbound = transaction.loadOrStoreTwincodeOutboundId(peerTwincodeOutboundId);
            if (twincodeOutbound == null) {
                return false;
            }

            final long twincodeId = twincodeOutbound.getDatabaseId().getId();

            // Step 1: from the current list of annotations on the descriptor for the peer twincode, identify those
            // that must be removed, updated and inserted.
            try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT kind, value"
                    + " FROM annotation WHERE cid=? AND descriptor=? AND peerTwincodeOutbound=?", new String[]{
                    conversationId,
                    descriptorId,
                    Long.toString(twincodeId)
            })) {
                while (cursor.moveToNext()) {
                    final AnnotationType type = toAnnotationType(cursor.getInt(0));
                    if (type != null) {
                        final int value = cursor.getInt(1);

                        final Integer newValue = newList.get(type);
                        if (newValue == null) {
                            if (deleteList == null) {
                                deleteList = new ArrayList<>();
                            }
                            deleteList.add(type);
                        } else if (newValue == value) {
                            newList.remove(type);
                        } else {
                            if (updateList == null) {
                                updateList = new ArrayList<>();
                            }
                            updateList.add(type);
                        }
                    }
                }
            } catch (DatabaseException sqlException) {
                mService.onDatabaseException(sqlException);
            }

            try {
                // Step 2: delete the annotations which are removed.
                if (deleteList != null) {
                    for (AnnotationType kind : deleteList) {
                        int count = transaction.delete(Tables.ANNOTATION,
                                "cid=? AND descriptor=? AND peerTwincodeOutbound=? AND kind=?",
                                new String[] {
                                        conversationId,
                                        descriptorId,
                                        Long.toString(twincodeId),
                                        Integer.toString(fromAnnotationType(kind))
                        });
                        if (count > 0) {
                            modified = true;
                        }
                    }
                }

                // Step 3: update existing annotations.
                if (updateList != null) {
                    final ContentValues values = new ContentValues();
                    for (AnnotationType kind : updateList) {
                        values.put(Columns.VALUE, newList.remove(kind));
                        values.put(Columns.CREATION_DATE, System.currentTimeMillis());
                        values.putNull(Columns.NOTIFICATION_ID);

                        transaction.update(Tables.ANNOTATION, values,
                                "cid=? AND descriptor=? AND peerTwincodeOutbound=? AND kind=?",
                                new String[] {
                                        conversationId,
                                        descriptorId,
                                        Long.toString(twincodeId),
                                        Integer.toString(fromAnnotationType(kind))
                        });
                        modified = true;
                    }
                    annotatingUsers.add(twincodeOutbound);
                }

                // Step 4: add the new ones.
                if (!newList.isEmpty()) {
                    final ContentValues values = new ContentValues();
                    for (final Map.Entry<AnnotationType, Integer> newAnnotation : newList.entrySet()) {
                        values.put(Columns.CID, conversationId);
                        values.put(Columns.DESCRIPTOR, descriptorId);
                        values.put(Columns.KIND, fromAnnotationType(newAnnotation.getKey()));
                        values.put(Columns.VALUE, newAnnotation.getValue());
                        values.put(Columns.PEER_TWINCODE_OUTBOUND, twincodeId);
                        values.put(Columns.CREATION_DATE, System.currentTimeMillis());

                        transaction.insertOrThrow(Tables.ANNOTATION, null, values);
                        modified = true;
                    }
                    annotatingUsers.add(twincodeOutbound);
                }

                if (modified) {
                    reloadAnnotations(descriptorImpl);
                }
            } catch (Exception exception) {
                mService.onDatabaseException(exception);
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }

        return modified;
    }

    /**
     * Set the descriptor annotation for the current user to a new value.  The descriptor annotation is
     * either inserted or updated if a previous annotation from the user was set.
     *
     * @param descriptorImpl the descriptor to annotate.
     * @param type the annotation type.
     * @param value the value to set on the annotation.
     * @return true if the annotation was inserted or updated and false if it existed and was not modified.
     */
    boolean setAnnotation(@NonNull DescriptorImpl descriptorImpl, @NonNull AnnotationType type, int value) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setAnnotation: descriptorImpl=" + descriptorImpl + " type=" + type + " value=" + value);
        }

        boolean modified = false;
        try (Transaction transaction = newTransaction()) {
            final long id = descriptorImpl.getDatabaseId();
            final long conversationId = descriptorImpl.getConversationId();
            final ContentValues values = new ContentValues();
            values.put(Columns.VALUE, value);
            long result;

            result = transaction.update(Tables.ANNOTATION, values,
                        "cid=? AND descriptor=? AND peerTwincodeOutbound IS NULL AND kind=? AND value != ?",
                        new String[]{
                                Long.toString(conversationId),
                                Long.toString(id),
                                Integer.toString(fromAnnotationType(type)),
                                Integer.toString(value)
                        });

            if (result > 0) {
                modified = true;
            } else {
                // Insert the annotation or override it.
                values.put(Columns.CID, conversationId);
                values.put(Columns.DESCRIPTOR, id);
                values.put(Columns.KIND, fromAnnotationType(type));
                values.put(Columns.CREATION_DATE, System.currentTimeMillis());
                values.putNull(Columns.PEER_TWINCODE_OUTBOUND);

                result = transaction.insert(Tables.ANNOTATION, values);

                modified = result > 0;
            }
            transaction.commit();

            if (modified) {
                reloadAnnotations(descriptorImpl);
            }
        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }

        return modified;
    }

    /**
     * Delete the descriptor annotation from current user only.
     *
     * @param descriptorImpl the descriptor to update.
     * @param type the annotation to remove.
     * @return true if an annotation was removed and false if there was not change.
     */
    boolean deleteAnnotation(@NonNull DescriptorImpl descriptorImpl, @NonNull AnnotationType type) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteAnnotation: descriptorImpl=" + descriptorImpl + " + type=" + type);
        }

        final String annotation = Integer.toString(fromAnnotationType(type));
        final long id = descriptorImpl.getDatabaseId();
        final long conversationId = descriptorImpl.getConversationId();
        final String[] params = {
                Long.toString(conversationId),
                Long.toString(id),
                annotation
        };
        boolean modified = false;
        try (Transaction transaction = newTransaction()) {
            modified = transaction.delete(Tables.ANNOTATION,
                        "cid=? AND descriptor=? AND peerTwincodeOutbound IS NULL AND kind=?", params) > 0;
            transaction.commit();

            if (modified) {
                reloadAnnotations(descriptorImpl);
            }

        } catch (Exception sqlException) {
            mService.onDatabaseException(sqlException);
        }

        return modified;
    }

    /**
     * Toggle the descriptor annotation for current user only:
     * - If the annotation with the value exists, it is removed.
     * - If the annotation with the value does not exist, it is either inserted or updated.
     *
     * @param descriptorImpl the descriptor to update.
     * @param type the annotation to remove or add.
     * @param value the annotation value
     * @return true if an annotation was removed and false if there was not change.
     */
    boolean toggleAnnotation(@NonNull DescriptorImpl descriptorImpl,
                             @NonNull AnnotationType type, int value) {
        if (DEBUG) {
            Log.d(LOG_TAG, "toggleAnnotation: descriptorImpl=" + descriptorImpl + " + type=" + type + " value=" + value);
        }

        final int kind = fromAnnotationType(type);
        boolean modified = false;
        try (Transaction transaction = newTransaction()) {
            long result;
            final long conversationId = descriptorImpl.getConversationId();
            final long did = descriptorImpl.getDatabaseId();
            final ContentValues values = new ContentValues();
            final long now = System.currentTimeMillis();
            final String[] params = {
                    Long.toString(conversationId),
                    Long.toString(did),
                    Integer.toString(kind)
            };

            // Get current annotation value for the descriptor.
            Long v = mDatabase.longQuery("SELECT value FROM annotation"
                        + " WHERE cid=? AND descriptor=? AND peerTwincodeOutbound IS NULL AND kind=?", params);

            if (v == null) {
                // Insert the annotation because it does not exist.
                values.put(Columns.VALUE, value);
                values.put(Columns.CREATION_DATE, now);
                values.put(Columns.CID, conversationId);
                values.put(Columns.DESCRIPTOR, did);
                values.put(Columns.KIND, kind);
                values.putNull(Columns.PEER_TWINCODE_OUTBOUND);

                result = transaction.insert(Tables.ANNOTATION, values);

            } else if (v == value) {
                // Remove annotation of the given kind because it has the value.
                result = transaction.delete(Tables.ANNOTATION,
                        "cid=? AND descriptor=? AND peerTwincodeOutbound IS NULL AND kind=?", params);

            } else {
                // Change annotation value to the new value.
                values.put(Columns.VALUE, value);
                values.put(Columns.CREATION_DATE, now);

                result = transaction.update(Tables.ANNOTATION, values,
                        "cid=? AND descriptor=? AND peerTwincodeOutbound IS NULL AND kind=?", params);
            }
            modified = result > 0;
            transaction.commit();

            if (modified) {
                reloadAnnotations(descriptorImpl);
            }

        } catch (Exception sqlException) {
            mService.onDatabaseException(sqlException);
        }
        return modified;
    }

    @Nullable
    Map<TwincodeOutbound, DescriptorAnnotation> listAnnotations(@NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listAnnotations: descriptorId=" + descriptorId);
        }

        final Map<TwincodeOutbound, DescriptorAnnotation> result = new HashMap<>();
        final QueryBuilder query = new QueryBuilder("tw.id, tw.twincodeId, tw.modificationDate,"
                + " tw.name, tw.avatarId, tw.description, tw.capabilities, tw.attributes, tw.flags, a.kind, a.value"
                + " FROM descriptor AS d"
                + " INNER JOIN annotation AS a ON d.cid=a.cid AND a.descriptor=d.id"
                + " INNER JOIN conversation AS c on d.cid=c.id"
                + " INNER JOIN repository AS r on r.id=c.subject"
                + " INNER JOIN twincodeOutbound AS tw ON"
                + " (a.peerTwincodeOutbound IS NULL AND tw.id=r.twincodeOutbound)"
                + " OR (a.peerTwincodeOutbound IS NOT NULL AND tw.id=a.peerTwincodeOutbound)");

        if (descriptorId.id > 0) {
            query.filterLong("d.id", descriptorId.id);
        } else {
            query.append(" INNER JOIN twincodeOutbound AS twout ON d.twincodeOutbound=twout.id");
            query.filterLong("d.sequenceId", descriptorId.sequenceId);
            query.filterUUID("twout.twincodeId", descriptorId.twincodeOutboundId);
        }
        try (DatabaseCursor cursor = mDatabase.rawQuery(query.getQuery(), query.getParams())) {
            while (cursor.moveToNext()) {
                final TwincodeOutbound twincodeOutbound = mDatabase.loadTwincodeOutbound(cursor, 0);
                final AnnotationType type = toAnnotationType(cursor.getInt(9));
                final int value = cursor.getInt(10);
                if (type != null && twincodeOutbound != null) {
                    result.put(twincodeOutbound, new DescriptorAnnotation(type, value, 1));
                }
            }
        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }
        return result;
    }

    /**
     * Internal method to reload the descriptor annotation summary when something was changed.
     *
     * @param descriptorImpl the descriptor instance.
     */
    private void reloadAnnotations(@NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "reloadAnnotations descriptorImpl=" + descriptorImpl);
        }

        List<DescriptorAnnotation> annotations = null;
        final String[] params = {
                Long.toString(descriptorImpl.getConversationId()),
                Long.toString(descriptorImpl.getDatabaseId())
        };

        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT kind, value, COUNT(*)"
                + " FROM annotation WHERE cid=? AND descriptor=? GROUP BY kind, value", params)) {

            while (cursor.moveToNext()) {
                final AnnotationType type = toAnnotationType(cursor.getInt(0));
                if (type != null) {
                    final int value = cursor.getInt(1);
                    final int count = cursor.getInt(2);

                    if (annotations == null) {
                        annotations = new ArrayList<>();
                    }

                    annotations.add(new DescriptorAnnotation(type, value, count));
                }
            }
        } catch (DatabaseException exception) {
            mService.onDatabaseException(exception);
        }
        descriptorImpl.setAnnotations(annotations);
    }

    //
    // Operations
    //

    /**
     * Load the operations from the database.
     *
     * @return the list of operations.
     */
    @NonNull
    List<Operation> loadOperations() {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadOperations");
        }

        List<Long> toDeleteList = null;
        final List<Operation> operations = new ArrayList<>();
        try (DatabaseCursor cursor = mDatabase.rawQuery("SELECT op.id, op.creationDate, op.cid, op.type,"
                + " op.descriptor, op.chunkStart, op.content, c.groupId FROM operation AS op"
                + " LEFT JOIN conversation AS c ON op.cid = c.id", null)) {
            while (cursor.moveToNext()) {
                final long operationId = cursor.getLong(0);
                final long creationDate = cursor.getLong(1);
                final long cid = cursor.getLong(2);
                final int type = cursor.getInt(3);
                final long descriptorId = cursor.isNull(4) ? 0 : cursor.getLong(4);
                final long chunkStart = cursor.getLong(5);
                final long groupId = cursor.getLong(7);
                final Operation operation;
                final DatabaseIdentifier conversationId = new DatabaseIdentifier(groupId > 0 ? mGroupConversationFactory : mConversationFactory, cid);
                switch (type) {
                    case 0:
                        operation = new ResetConversationOperation(operationId, conversationId, creationDate,
                                descriptorId, cursor.getBlob(6));
                        break;

                    case 1:
                        operation = new SynchronizeConversationOperation(operationId, conversationId, creationDate);
                        break;

                    case 2:
                        operation = new PushObjectOperation(operationId, conversationId, creationDate, descriptorId);
                        break;

                    case 4:
                        operation = new PushFileOperation(operationId, conversationId, creationDate, descriptorId, chunkStart);
                        break;

                    case 5:
                        operation = new UpdateDescriptorTimestampOperation(operationId, conversationId, creationDate,
                                descriptorId, cursor.getBlob(6));
                        break;

                    case 6:
                        operation = new GroupInviteOperation(operationId, Operation.Type.INVITE_GROUP, conversationId, creationDate,
                                descriptorId);
                        break;

                    case 7:
                        operation = new GroupInviteOperation(operationId, Operation.Type.WITHDRAW_INVITE_GROUP, conversationId, creationDate,
                                descriptorId);
                        break;

                    case 8:
                        operation = new GroupJoinOperation(operationId, Operation.Type.JOIN_GROUP, conversationId, creationDate,
                                descriptorId, cursor.getBlob(6));
                        break;

                    case 9:
                        operation = new GroupLeaveOperation(operationId, Operation.Type.LEAVE_GROUP, conversationId, creationDate,
                                descriptorId, cursor.getBlob(6));
                        break;

                    case 10:
                        operation = new GroupUpdateOperation(operationId, Operation.Type.UPDATE_GROUP_MEMBER, conversationId,
                                creationDate, descriptorId, cursor.getBlob(6));
                        break;

                    case 11:
                        operation = new PushGeolocationOperation(operationId, conversationId, creationDate, descriptorId);
                        break;

                    case 12:
                        operation = new PushTwincodeOperation(operationId, conversationId, creationDate, descriptorId);
                        break;

                    case 14:
                        operation = new UpdateAnnotationsOperation(operationId, conversationId, creationDate, descriptorId);
                        break;

                    case 15: // Added 2024-09-09
                        operation = new GroupJoinOperation(operationId, Operation.Type.INVOKE_JOIN_GROUP, conversationId, creationDate,
                                descriptorId, cursor.getBlob(6));
                        break;

                    case 16:
                        operation = new GroupLeaveOperation(operationId, Operation.Type.INVOKE_LEAVE_GROUP, conversationId, creationDate,
                                descriptorId, cursor.getBlob(6));
                        break;

                    case 17:
                        operation = new GroupJoinOperation(operationId, Operation.Type.INVOKE_ADD_MEMBER, conversationId, creationDate,
                                descriptorId, cursor.getBlob(6));
                        break;

                    case 18: // Added 2025-05-21
                        operation = new UpdateDescriptorOperation(operationId, conversationId, creationDate,
                                descriptorId, cursor.getBlob(6));
                        break;

                    case 3:  // Transient operation should never be saved!
                    case 13: // Push command
                    default:
                        operation = null;
                        break;
                }
                if (operation != null) {
                    operations.add(operation);
                } else {
                    if (toDeleteList == null) {
                        toDeleteList = new ArrayList<>();
                    }
                    toDeleteList.add(operationId);
                }
            }
        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }

        if (toDeleteList != null) {
            try (Transaction transaction = mDatabase.newTransaction()) {
                transaction.deleteWithList(Tables.OPERATION, toDeleteList);
                transaction.commit();

            } catch (Exception exception) {
                mService.onDatabaseException(exception);
            }
        }
        return operations;
    }

    void storeOperation(@NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "storeOperation: operation=" + operation);
        }

        try (Transaction transaction = newTransaction()) {
            storeOperationWithTransaction(transaction, operation);
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void storeOperations(@NonNull Collection<Object> list) {
        if (DEBUG) {
            Log.d(LOG_TAG, "storeOperations: list=" + list);
        }

        // Store a list of operations by using an SQL transaction (serious performance improvement).
        try (Transaction transaction = newTransaction()) {
            for (Object item : list) {
                if (item instanceof Operation) {
                    storeOperationWithTransaction(transaction, (Operation) item);
                } else if (item instanceof List) {
                    for (Object operation : (List<?>) item) {
                        if (operation instanceof Operation) {
                            storeOperationWithTransaction(transaction, (Operation) operation);
                        }
                    }
                }
            }
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void updateFileOperation(@NonNull FileOperation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateFileOperation: operation=" + operation);
        }

        try (Transaction transaction = newTransaction()) {
            final ContentValues values = new ContentValues();
            values.put(Columns.CHUNK_START, operation.getChunkStart());
            transaction.updateWithId(Tables.OPERATION, values, operation.getId());
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    void deleteOperation(@NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteOperation: operation=" + operation);
        }

        try (Transaction transaction = newTransaction()) {
            transaction.deleteWithId(Tables.OPERATION, operation.getId());
            transaction.commit();

        } catch (Exception exception) {
            mService.onDatabaseException(exception);
        }
    }

    private void storeOperationWithTransaction(@NonNull Transaction transaction,
                                               @NonNull Operation operation) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "storeOperationWithTransaction: operation=" + operation);
        }

        final long id = transaction.allocateId(DatabaseTable.TABLE_OPERATION);
        final ContentValues values = new ContentValues();
        final long descriptorId = operation.getDescriptorId();
        values.put(Columns.ID, id);
        values.put(Columns.CREATION_DATE, operation.getTimestamp());
        values.put(Columns.CID, operation.getConversationId().getId());
        values.put(Columns.TYPE, fromOperationType(operation.getType()));
        if (descriptorId != 0) {
            values.put(Columns.DESCRIPTOR, descriptorId);
        }
        if (operation instanceof FileOperation) {
            values.put(Columns.CHUNK_START, ((FileOperation)operation).getChunkStart());
        }

        // Optional operation specific data.
        final byte[] content = operation.serialize();
        if (content != null) {
            values.put(Columns.CONTENT, content);
        }

        // Note chunkStart is always 0 on insert.
        transaction.insertOrThrow(Tables.OPERATION, null, values);
        operation.setId(id);
    }

    static int fromDescriptorType(@NonNull Descriptor.Type type) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fromDescriptorType: type=" + type);
        }

        // Use a fix mapping to make sure we don't rely on the Enum order.
        switch (type) {
            case DESCRIPTOR:
                return 1;

            case OBJECT_DESCRIPTOR:
                return 2;

            case TRANSIENT_OBJECT_DESCRIPTOR:
                return 3;

            case FILE_DESCRIPTOR:
                return 4;

            case IMAGE_DESCRIPTOR:
                return 5;

            case AUDIO_DESCRIPTOR:
                return 6;

            case VIDEO_DESCRIPTOR:
                return 7;

            case NAMED_FILE_DESCRIPTOR:
                return 8;

            case INVITATION_DESCRIPTOR:
                return 9;

            case GEOLOCATION_DESCRIPTOR:
                return 10;

            case TWINCODE_DESCRIPTOR:
                return 11;

            case CALL_DESCRIPTOR:
                return 12;

            case CLEAR_DESCRIPTOR:
                return 13;
        }
        return 0;
    }

    public static int fromAnnotationType(@NonNull AnnotationType type) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fromAnnotationType: type=" + type);
        }

        // Use a fix mapping to make sure we don't rely on the Enum order.
        switch (type) {
            case FORWARD:
                return 1;

            case FORWARDED:
                return 2;

            case SAVE:
                return 3;

            case LIKE:
                return 4;

            case POLL:
                return 5;
        }
        return 0;
    }

    private static int fromOperationType(@NonNull Operation.Type type) {
        if (DEBUG) {
            Log.d(LOG_TAG, "fromOperationType: type=" + type);
        }

        // Use a fix mapping to make sure we don't rely on the Enum order.
        switch (type) {
            case RESET_CONVERSATION:
                return 0;

            case SYNCHRONIZE_CONVERSATION:
                return 1;

            case PUSH_OBJECT:
                return 2;

            case PUSH_TRANSIENT_OBJECT:
                return 3;

            case PUSH_FILE:
                return 4;

            case UPDATE_DESCRIPTOR_TIMESTAMP:
                return 5;

            case INVITE_GROUP:
                return 6;

            case WITHDRAW_INVITE_GROUP:
                return 7;

            case JOIN_GROUP:
                return 8;

            case LEAVE_GROUP:
                return 9;

            case UPDATE_GROUP_MEMBER:
                return 10;

            case PUSH_GEOLOCATION:
                return 11;

            case PUSH_TWINCODE:
                return 12;

            case PUSH_COMMAND:
                return 13;

            case UPDATE_ANNOTATIONS:
                return 14;

            case INVOKE_JOIN_GROUP: // Added 2024-09-09
                return 15;

            case INVOKE_LEAVE_GROUP:
                return 16;

            case INVOKE_ADD_MEMBER:
                return 17;

            case UPDATE_OBJECT: // Added 2025-05-21
                return 18;
        }
        return 0;
    }

    @Nullable
    public static AnnotationType toAnnotationType(int type) {
        if (DEBUG) {
            Log.d(LOG_TAG, "toAnnotationType: type=" + type);
        }

        switch (type) {
            case 1:
                return AnnotationType.FORWARD;

            case 2:
                return AnnotationType.FORWARDED;

            case 3:
                return AnnotationType.SAVE;

            case 4:
                return AnnotationType.LIKE;

            case 5:
                return AnnotationType.POLL;
        }

        return null;
    }
}
