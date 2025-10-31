/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * OnInviteGroup IQ.
 *
 * Schema version 2
 *  Date: 2024/08/28
 *
 * <pre>
 * {
 *  "schemaId":"afa81c21-beb5-4829-a5d0-8816afda602f",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnInviteGroupIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"deviceState", "type":"byte"},
 *     {"name":"receivedTimestamp", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */
class OnInviteGroupIQ {

    static final UUID SCHEMA_ID = UUID.fromString("afa81c21-beb5-4829-a5d0-8816afda602f");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_INVITE_GROUP_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

}
