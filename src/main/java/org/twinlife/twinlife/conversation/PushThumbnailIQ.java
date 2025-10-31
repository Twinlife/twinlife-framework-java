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
 * PushThumbnail IQ.
 *
 * This is PushFileChunkIQ but targeted at sending/receiving a large thumbnail.
 *
 * Schema version 1
 *  Date: 2021/04/07
 *
 * {
 *  "schemaId":"b4ca7a06-f512-403a-b31f-58e19bf777a0",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"PushFileChunkIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields":
 *  [
 *   {"name":"twincodeOutboundId", "type":"uuid"}
 *   {"name":"sequenceId", "type":"long"}
 *   {"name":"timestamp", "type":"long"}
 *   {"name":"chunkStart", "type":"long"}
 *   {"name":"chunk", "type":[null, "bytes"]}
 *  ]
 * }
 * </pre>
 */
class PushThumbnailIQ {

    static final UUID SCHEMA_ID = UUID.fromString("b4ca7a06-f512-403a-b31f-58e19bf777a0");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_PUSH_THUMBNAIL_SERIALIZER = PushFileChunkIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

}
