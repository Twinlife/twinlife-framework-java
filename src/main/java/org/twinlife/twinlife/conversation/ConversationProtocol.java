/*
 *  Copyright (c) 2016-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

public class ConversationProtocol {

    //
    // Invoke Actions & Attributes
    //

    public static final String ACTION_CONVERSATION_SYNCHRONIZE = "twinlife::conversation::synchronize";
    public static final String PARAM_MEMBER_TWINCODE_OUTBOUND_ID = "memberTwincodeOutboundId";
    public static final String ACTION_CONVERSATION_NEED_SECRET = "twinlife::conversation::need-secret";
    public static final String ACTION_CONVERSATION_LEAVE = "twinlife::conversation::leave";
    public static final String ACTION_CONVERSATION_JOIN = "twinlife::conversation::join";
    public static final String ACTION_CONVERSATION_ON_JOIN = "twinlife::conversation::on-join";
    public static final String ACTION_REFRESH_SECRET = "twinlife::conversation::refresh-secret";
    public static final String ACTION_ON_REFRESH_SECRET = "twinlife::conversation::on-refresh-secret";
    public static final String ACTION_VALIDATE_SECRET = "twinlife::conversation::validate-secret";
    public static final String PARAM_MEMBER_TWINCODE_ID = "memberTwincodeId";
    public static final String PARAM_GROUP_TWINCODE_ID = "groupTwincodeId";
    public static final String PARAM_SIGNED_OFF_TWINCODE_ID = "signedOffTwincodeId";
    public static final String PARAM_PERMISSIONS = "permissions";
    public static final String PARAM_SIGNATURE = "signature";
    public static final String PARAM_PUBLIC_KEY = "pubKey";
    public static final String PARAM_MEMBERS = "members";
}