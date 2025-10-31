/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * OnUpdateAnnotation IQ.
 * <p>
 * Schema version 1
 *  Date: 2023/01/10
 *
 * <pre>
 * {
 *  "schemaId":"1db860bd-f84c-48c0-b2dd-17fea1e683bd",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnUpdateAnnotationIQ",
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
class OnUpdateAnnotationIQ {

    static final UUID SCHEMA_ID = UUID.fromString("1db860bd-f84c-48c0-b2dd-17fea1e683bd");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UPDATE_ANNOTATION_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

}
