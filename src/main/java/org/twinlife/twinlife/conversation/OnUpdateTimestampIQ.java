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
 * OnUpdateTimestampIQ IQ.
 *
 * Schema version 2
 *  Date: 2024/06/07
 *
 * <pre>
 * {
 *  "schemaId":"87d33c5f-9b9b-49bf-a802-8bd24fb021a6",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnUpdateTimestampIQ",
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
class OnUpdateTimestampIQ {

    static final UUID SCHEMA_ID = UUID.fromString("87d33c5f-9b9b-49bf-a802-8bd24fb021a6");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UPDATE_TIMESTAMP_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

}
