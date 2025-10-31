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
 * OnPushTwincode IQ.
 *
 * Schema version 2
 *  Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"e6726692-8fe6-4d29-ae64-ba321d44a247",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"OnPushTwincodeIQ",
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
class OnPushTwincodeIQ {

    static final UUID SCHEMA_ID = UUID.fromString("e6726692-8fe6-4d29-ae64-ba321d44a247");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_PUSH_TWINCODE_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

}
