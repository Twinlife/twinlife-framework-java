/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

/**
 * Helper for database column names.
 */
public class Columns {

    public static final String ID = "id";
    public static final String UUID = "uuid";
    public static final String CREATION_DATE = "creationDate";
    public static final String MODIFICATION_DATE = "modificationDate";
    public static final String FLAGS = "flags";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String STATS = "stats";
    public static final String OWNER = "owner";
    public static final String TWINCODE_INBOUND = "twincodeInbound";
    public static final String SCHEMA_ID = "schemaId";
    public static final String SCHEMA_VERSION = "schemaVersion";

    // TwincodeInbound, TwincodeOutbound
    public static final String ATTRIBUTES = "attributes";
    public static final String CAPABILITIES = "capabilities";
    public static final String AVATAR_ID = "avatarId";
    public static final String TWINCODE_ID = "twincodeId";
    public static final String REFRESH_PERIOD = "refreshPeriod";
    public static final String REFRESH_DATE = "refreshDate";
    public static final String REFRESH_TIMESTAMP = "refreshTimestamp";
    public static final String FACTORY_ID = "factoryId";

    // Conversation
    public static final String GROUP_ID = "groupId";
    public static final String PERMISSIONS = "permissions";
    public static final String JOIN_PERMISSIONS = "joinPermissions";
    public static final String LAST_CONNECT_DATE = "lastConnectDate";
    public static final String LAST_RETRY_DATE = "lastRetryDate";
    public static final String RESOURCE_ID = "resourceId";
    public static final String PEER_RESOURCE_ID = "peerResourceId";
    public static final String SUBJECT = "subject";
    public static final String PEER_TWINCODE_OUTBOUND = "peerTwincodeOutbound";
    public static final String INVITED_CONTACT = "invitedContact";

    // Descriptor
    public static final String SEQUENCE_ID = "sequenceId";
    public static final String EXPIRE_TIMEOUT = "expireTimeout";
    public static final String DESCRIPTOR_TYPE = "descriptorType";
    public static final String REPLY_TO = "replyTo";
    public static final String SENT_TO = "sentTo";
    public static final String SEND_DATE = "sendDate";
    public static final String READ_DATE = "readDate";
    public static final String RECEIVE_DATE = "receiveDate";
    public static final String UPDATE_DATE = "updateDate";
    public static final String DELETE_DATE = "deleteDate";
    public static final String PEER_DELETE_DATE = "peerDeleteDate";

    // Descriptor, Operation, Annotation.
    public static final String CID = "cid";
    public static final String KIND = "kind";
    public static final String TWINCODE_OUTBOUND = "twincodeOutbound";
    public static final String VALUE = "value";
    public static final String TYPE = "type";

    // Notification
    public static final String NOTIFICATION_ID = "notificationId";

    // Invitation
    public static final String JOINED_MEMBER = "joinedMember";
    public static final String INVITER_MEMBER = "inviterMember";

    // TwincodeImage
    public static final String COPIED_FROM_ID = "copiedFrom";
    public static final String UPLOAD_REMAIN1 = "uploadRemain1";
    public static final String UPLOAD_REMAIN2 = "uploadRemain2";
    public static final String THUMBNAIL = "thumbnail";
    public static final String IMAGE_SHAS = "imageSHAs";

    // Operation
    public static final String CHUNK_START = "chunkStart";
    public static final String DESCRIPTOR = "descriptor";
    public static final String CONTENT = "content";

    // Keys
    public static final String SECRET_UPDATE_DATE = "secretUpdateDate";
    public static final String SIGNING_KEY = "signingKey";
    public static final String ENCRYPTION_KEY = "encryptionKey";
    public static final String NONCE_SEQUENCE = "nonceSequence";
    public static final String SECRET1 = "secret1";
    public static final String SECRET2 = "secret2";
    public static final String PEER_TWINCODE_ID = "peerTwincodeId";
}