/*
 *  Copyright (c) 2021 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * OnPushObject IQ.
 *
 * Schema version 3
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"f95ac4b5-d20f-4e1f-8204-6d146dd5291e",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"OnPushObjectIQ",
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
class OnPushObjectIQ {

    static final UUID SCHEMA_ID = UUID.fromString("f95ac4b5-d20f-4e1f-8204-6d146dd5291e");
    static final int SCHEMA_VERSION_3 = 3;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_PUSH_OBJECT_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_3);

}
