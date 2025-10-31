/*
 *  Copyright (c) 2022 twinlife SA.
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
 * OnResetConversationIQ IQ.
 *
 * Schema version 3
 *  Date: 2022/02/09
 *
 * <pre>
 * {
 *  "schemaId":"09e855f4-61d9-4acf-92ce-8f93c6951fb0",
 *  "schemaVersion":"3",
 *
 *  "type":"record",
 *  "name":"OnResetConversationIQ",
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
class OnResetConversationIQ {

    static final UUID SCHEMA_ID = UUID.fromString("09e855f4-61d9-4acf-92ce-8f93c6951fb0");
    static final int SCHEMA_VERSION_3 = 3;
    static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_RESET_CONVERSATION_SERIALIZER = OnPushIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_3);

}
