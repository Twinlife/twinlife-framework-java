/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * OnUpdateDescriptorIQ IQ.
 *
 * Schema version 1
 *  Date: 2025/05/21
 *
 * <pre>
 * {
 *  "schemaId":"2afd6f3b-1e96-40cd-836e-7644540246b9",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnUpdateDescriptorIQ",
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
class OnUpdateDescriptorIQ {

    static final UUID SCHEMA_ID = UUID.fromString("2afd6f3b-1e96-40cd-836e-7644540246b9");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UPDATE_DESCRIPTOR_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

}
