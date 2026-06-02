/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.conversation;

import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * OnPushPoll IQ.
 *
 * Schema version 1
 *  Date: 2026/03/23
 *
 * <pre>
 * {
 *  "schemaId":"62d16671-6fc5-4b7d-9a35-c36e38b6275c",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnPushPollIQ",
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
class OnPushPollIQ {

    static final int SCHEMA_VERSION_1 = 1;
    static final UUID SCHEMA_ID = UUID.fromString("62d16671-6fc5-4b7d-9a35-c36e38b6275c");
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_PUSH_POLL_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

}
