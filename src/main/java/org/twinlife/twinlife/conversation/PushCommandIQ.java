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
 * PushCommand IQ.
 * <p>
 * Schema version 2
 *  Date: 2024/09/10
 * <pre>
 * {
 *  "schemaId":"e8a69b58-1014-4d3c-9357-8331c19c5f59",
 *  "schemaVersion":"2",
 *
 *  "type":"record",
 *  "name":"PushCommandIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields":
 *  [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"createdTimestamp", "type":"long"}
 *     {"name":"sentTimestamp", "type":"long"}
 *     {"name":"flags", "type":"int"}
 *     {"name":"object", "type":"Object"}
 *  ]
 * }
 * </pre>
 */
class PushCommandIQ {

    static final UUID SCHEMA_ID = UUID.fromString("e8a69b58-1014-4d3c-9357-8331c19c5f59");
    static final int SCHEMA_VERSION_2 = 2;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_PUSH_COMMAND_SERIALIZER = PushTransientIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_2);

}
