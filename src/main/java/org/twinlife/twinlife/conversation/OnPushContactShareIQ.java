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
 * OnPushContactShare IQ.
 *
 * Schema version 1
 *  Date: 2026/07/02
 *
 * <pre>
 * {
 *  "schemaId":"fbb7a421-eef7-456f-b957-9aefce367726",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnPushContactShareIQ",
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
class OnPushContactShareIQ {

    static final int SCHEMA_VERSION_1 = 1;
    static final UUID SCHEMA_ID = UUID.fromString("fbb7a421-eef7-456f-b957-9aefce367726");
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_PUSH_CONTACT_SHARE_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

}
