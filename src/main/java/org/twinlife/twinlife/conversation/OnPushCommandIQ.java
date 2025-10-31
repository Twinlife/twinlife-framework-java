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
 * OnPushCommand IQ.
 *
 * Schema version 2
 *  Date: 2024/09/12
 *
 * <pre>
 * {
 *  "schemaId":"4453dbf3-1b26-4c13-956c-4b83fc1d0c49",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnPushCommandIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"deviceState", "type":"byte"},
 *     {"name":"receivedTimestamp", "type":"long"}
 *  ]
 * }
 * </pre>
 */
class OnPushCommandIQ {

    static final UUID SCHEMA_ID = UUID.fromString("4453dbf3-1b26-4c13-956c-4b83fc1d0c49");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_PUSH_COMMAND_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

}
