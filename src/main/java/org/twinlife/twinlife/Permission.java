/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Helper class to manage permissions.
 *
 * To keep the implementation simple, permissions are represented by a bitmask.
 * They are basically used by the conversation service to check if a user has right
 * to perform a given operation (send a message, delete a message, etc.).
 *
 * Permission are also used by the SecureRosterService and the service to check if
 * a user is allowed to add, remove members in the secure roster.
 */
public class Permission {
    public final long value;

    public Permission(int bit) {
        this.value = (1L << bit);
    }

    public Permission(@NonNull Permission p1, @NonNull Permission p2) {
        this.value = p1.value | p2.value;
    }

    public Permission(long bitmask) {
        this.value = bitmask;
    }

    public Permission(@NonNull List<Permission> list) {
        this.value = toLong(list);
    }

    public final boolean hasPermission(long permissions) {
        return (permissions & value) != 0;
    }

    public final boolean hasPermission(@NonNull Permission permission) {

        return (permission.value & value) != 0;
    }

    /**
     * Restrict the permission to remove permissions not defined in the restricted list.
     * @param restricted the list of permission to restrict.
     * @return the new permission
     */
    @NonNull
    public final Permission restrictPermission(@NonNull Permission restricted) {

        return new Permission(this.value & restricted.value);
    }

    /**
     * Get a new permission that removes a given permission.
     * @param permission the permission to remove.
     * @return the new permission.
     */
    @NonNull
    public final Permission removePermission(@NonNull Permission permission) {

        return new Permission(this.value & ~permission.value);
    }

    public static long toLong(@Nullable List<Permission> permissions) {
        long result = 0;
        if (permissions != null) {
            for (Permission p : permissions) {
                result |= p.value;
            }
        }
        return result;
    }

    // Permissions recognized by the server.
    public static final Permission INVITE_MEMBER = new Permission(0);
    public static final Permission UPDATE_MEMBER = new Permission(1);
    public static final Permission REMOVE_MEMBER = new Permission(2);
    public static final Permission ADD_PUBLIC_KEY = new Permission(18);

    // Permissions used by the ConversationService
    public static final Permission SEND_MESSAGE = new Permission(3);
    public static final Permission SEND_IMAGE = new Permission(4);
    public static final Permission SEND_AUDIO = new Permission(5);
    public static final Permission SEND_VIDEO = new Permission(6);
    public static final Permission SEND_FILE = new Permission(7);
    public static final Permission DELETE_MESSAGE = new Permission(8);
    public static final Permission DELETE_IMAGE = new Permission(9);
    public static final Permission DELETE_AUDIO = new Permission(10);
    public static final Permission DELETE_VIDEO = new Permission(11);
    public static final Permission DELETE_FILE = new Permission(12);
    public static final Permission RESET_CONVERSATION = new Permission(13);
    public static final Permission SEND_GEOLOCATION = new Permission(14);
    public static final Permission SEND_TWINCODE = new Permission(15);
    public static final Permission RECEIVE_MESSAGE = new Permission(16);
    public static final Permission SEND_COMMAND = new Permission(17);

    public static final Permission MANAGE_MEMBER = new Permission(0, 2);
    public static final Permission ALLOW_POST = new Permission(new Permission(3, 7), SEND_GEOLOCATION);
    public static final Permission ALLOW_DELETE = new Permission(8, 12);
    public static final Permission MEMBER_PERMISSION = new Permission(ALLOW_POST, ALLOW_DELETE);
    public static final Permission ALL_PERMISSIONS = new Permission(0, 18);
    public static final Permission ADMIN_PERMISSIONS = new Permission(MANAGE_MEMBER, ADD_PUBLIC_KEY);

    private Permission(int fromBit, int toBit) {
        this.value = ((1L << (toBit + 1)) - 1L) & (-(1L << fromBit));
    }
}
