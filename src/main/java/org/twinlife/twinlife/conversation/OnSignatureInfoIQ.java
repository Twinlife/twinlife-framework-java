/*
 *  Copyright (c) 2021-2025 twinlife SA.
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
 * OnSignatureInfoIQ IQ.
 *
 * Schema version 1
 *  Date: 2024/10/03
 *
 * <pre>
 * {
 *  "schemaId":"cbe0bd4e-7e19-479e-a64d-b0ae6a792161",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"OnSignatureInfoIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"deviceState", "type":"byte"},
 *     {"name":"receivedTimestamp", "type":"long"}
 *  ]
 * }
 * </pre>
 *
 * AckSignatureInfoIQ IQ.
 *
 * Schema version 1
 *  Date: 2024/10/30
 *
 * <pre>
 * {
 *  "schemaId":"d09fbd4c-cb32-448d-916f-c124e247cd21",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"AckSignatureInfoIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"deviceState", "type":"byte"},
 *     {"name":"receivedTimestamp", "type":"long"}
 *  ]
 * }
 * </pre>
 */
class OnSignatureInfoIQ {

    static final UUID SCHEMA_ID = UUID.fromString("cbe0bd4e-7e19-479e-a64d-b0ae6a792161");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_SIGNATURE_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    static final UUID ACK_SIGNATURE_INFO_SCHEMA_ID = UUID.fromString("d09fbd4c-cb32-448d-916f-c124e247cd21");
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ACK_SIGNATURE_SERIALIZER = OnPushIQ.createSerializer(ACK_SIGNATURE_INFO_SCHEMA_ID, SCHEMA_VERSION_1);

}
