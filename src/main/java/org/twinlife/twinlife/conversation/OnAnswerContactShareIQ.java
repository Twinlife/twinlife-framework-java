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
 * OnAnswerContactShareIQ IQ.
 *
 * Schema version 1
 *  Date: 2026/07/02
 *
 * <pre>
 * {
 *  "schemaId":"48245700-ddf2-49b5-991f-300ee7df96a1",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnAnswerContactShareIQ",
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
class OnAnswerContactShareIQ {

    static final int SCHEMA_VERSION_1 = 1;
    static final UUID SCHEMA_ID = UUID.fromString("48245700-ddf2-49b5-991f-300ee7df96a1");
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_ANSWER_CONTACT_SHARE_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

}
