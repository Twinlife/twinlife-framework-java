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
 * OnUpdateGeolocation IQ.
 *
 * Schema version 1
 *  Date: 2024/06/07
 *
 * <pre>
 * {
 *  "schemaId":"09466194-bd50-4c1f-a59e-62a03cecba9e",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnUpdateGeolocationIQ",
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
class OnUpdateGeolocationIQ {

    static final UUID SCHEMA_ID = UUID.fromString("09466194-bd50-4c1f-a59e-62a03cecba9e");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UPDATE_GEOLOCATION_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

}
