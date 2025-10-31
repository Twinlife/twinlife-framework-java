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
 * OnUpdatePermissions IQ.
 *
 * Schema version 1
 *  Date: 2024/06/07
 *
 * <pre>
 * {
 *  "schemaId":"f9a9c212-3364-491e-b559-34cf8b6c6a44",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnUpdatePermissionsIQ",
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
class OnUpdatePermissionsIQ {

    static final UUID SCHEMA_ID = UUID.fromString("f9a9c212-3364-491e-b559-34cf8b6c6a44");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UPDATE_PERMISSIONS_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

}
