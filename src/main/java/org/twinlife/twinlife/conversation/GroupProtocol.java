/*
 *  Copyright (c) 2019-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.RosterId;
import org.twinlife.twinlife.TwincodeOutbound;

import java.util.List;
import java.util.UUID;

public class GroupProtocol {

    // The LEGACY_SCHEMA_ID was used by groups that were created without a secure roster.  This group
    // allows to have members in the secure roster that have an empty public key.
    // The ROSTER_SCHEMA_ID is the new schema ID used for groups after 2026-04-16.  Every member
    // in the secure roster must have a public key.
    public static final UUID LEGACY_SCHEMA_ID = UUID.fromString("e3eab04a-263f-4e5d-95b8-e18252f49f7b");
    public static final UUID ROSTER_SCHEMA_ID = UUID.fromString("a70f964c-7147-4825-afe2-d14da222f181");

    //
    // Invoke Actions & Attributes
    //

    public static final String ACTION_ROSTER_UPDATE = "roster::update";
    public static final String ACTION_ROSTER_LEAVE = "roster::leave";
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

    /**
     * Get the secure roster ID associated with the group. For a legacy group, there is no secure roster ID.
     * @return null or the secure roster ID.
     */
    @Nullable
    public static RosterId getSecureRosterId(@Nullable TwincodeOutbound twincodeOutbound) {

        if (twincodeOutbound == null) {
            return null;
        }
        final String rosterId = (String)twincodeOutbound.getAttribute(TwincodeOutbound.ROSTER_ID);
        if (rosterId == null) {
            return null;
        }
        final String[] parts = rosterId.split(":");
        if (parts.length != 2) {
            return null;
        }
        final UUID id = UUID.fromString(parts[0]);
        final UUID schemaId = UUID.fromString(parts[1]);

        return id != null && schemaId != null ? new RosterId(id, schemaId) : null;
    }
}