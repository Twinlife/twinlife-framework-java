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
 * OnPushGeolocation IQ.
 *
 * Schema version 2
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"5fd82b6b-5b7f-42c1-976e-f3addf8c5e16",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnPushGeolocationIQ",
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
class OnPushGeolocationIQ {

    static final int SCHEMA_VERSION_2 = 2;
    static final UUID SCHEMA_ID = UUID.fromString("5fd82b6b-5b7f-42c1-976e-f3addf8c5e16");
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_PUSH_GEOLOCATION_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

}
