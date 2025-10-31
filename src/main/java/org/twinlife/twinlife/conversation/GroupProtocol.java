/*
 *  Copyright (c) 2019-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BaseService;

import java.util.List;
import java.util.UUID;

public class GroupProtocol {

    //
    // Invoke Actions & Attributes
    //

    public static final String ACTION_GROUP_SUBSCRIBE = "twinlife::conversation::subscribe";
    public static final String ACTION_GROUP_REGISTERED = "twinlife::conversation::registered";
    private static final String INVOKE_TWINCODE_ACTION_MEMBER_TWINCODE_ID = "memberTwincodeId";
    private static final String INVOKE_TWINCODE_ACTION_ADMIN_TWINCODE_ID = "adminTwincodeId";
    private static final String INVOKE_TWINCODE_ACTION_ADMIN_PERMISSIONS = "adminPermissions";
    private static final String INVOKE_TWINCODE_ACTION_MEMBER_PERMISSIONS = "memberPermissions";

    public static void setInvokeTwincodeActionGroupSubscribeMemberTwincodeId(@NonNull List<BaseService.AttributeNameValue> attributes, @NonNull UUID memberTwincodeId) {

        attributes.add(new BaseService.AttributeNameStringValue(INVOKE_TWINCODE_ACTION_MEMBER_TWINCODE_ID, memberTwincodeId.toString()));
    }

    public static void setInvokeTwincodeGroupAdminTwincodeId(@NonNull List<BaseService.AttributeNameValue> attributes, @NonNull UUID adminTwincodeId) {

        attributes.add(new BaseService.AttributeNameStringValue(INVOKE_TWINCODE_ACTION_ADMIN_TWINCODE_ID, adminTwincodeId.toString()));
    }

    public static void setInvokeTwincodeGroupAdminPermissions(@NonNull List<BaseService.AttributeNameValue> attributes, long permissions) {

        // Pass the long as a string to avoid problem in the server.
        attributes.add(new BaseService.AttributeNameStringValue(INVOKE_TWINCODE_ACTION_ADMIN_PERMISSIONS, Long.toString(permissions)));
    }

    public static void setInvokeTwincodeGroupMemberPermissions(@NonNull List<BaseService.AttributeNameValue> attributes, long permissions) {

        // Pass the long as a string to avoid problem in the server.
        attributes.add(new BaseService.AttributeNameStringValue(INVOKE_TWINCODE_ACTION_MEMBER_PERMISSIONS, Long.toString(permissions)));
    }

    public static String invokeTwincodeActionMemberTwincodeOutboundId() {

        return INVOKE_TWINCODE_ACTION_MEMBER_TWINCODE_ID;
    }

    public static String getInvokeTwincodeAdminTwincodeId() {

        return INVOKE_TWINCODE_ACTION_ADMIN_TWINCODE_ID;
    }

    public static String getInvokeTwincodeAdminPermissions() {

        return INVOKE_TWINCODE_ACTION_ADMIN_PERMISSIONS;
    }

    public static String getInvokeTwincodeMemberPermissions() {

        return INVOKE_TWINCODE_ACTION_MEMBER_PERMISSIONS;
    }
}