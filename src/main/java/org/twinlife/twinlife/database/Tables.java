/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.DatabaseTable;

/**
 * Helper for database table names.
 */
public class Tables {

    public static final String TWINCODE_OUTBOUND = "twincodeOutbound";
    public static final String TWINCODE_INBOUND = "twincodeInbound";
    public static final String TWINCODE_KEYS = "twincodeKeys";
    public static final String SECRET_KEYS = "secretKeys";
    public static final String REPOSITORY = "repository";
    public static final String NOTIFICATION = "notification";
    public static final String CONVERSATION = "conversation";
    public static final String OPERATION = "operation";
    public static final String DESCRIPTOR = "descriptor";
    public static final String ANNOTATION = "annotation";
    public static final String INVITATION = "invitation";
    public static final String SEQUENCE = "sequence";
    public static final String IMAGE = "image";
    public static final String SEQUENCE_ID = "sequenceId";

    @Nullable
    static String getTable(@NonNull DatabaseTable kind) {
        switch (kind) {
            case TABLE_TWINCODE_INBOUND:
                return TWINCODE_INBOUND;

            case TABLE_TWINCODE_OUTBOUND:
                return TWINCODE_OUTBOUND;

            case TABLE_TWINCODE_KEYS:
                return TWINCODE_KEYS;

            case TABLE_SECRET_KEYS:
                return SECRET_KEYS;

            case TABLE_REPOSITORY_OBJECT:
                return REPOSITORY;

            case TABLE_NOTIFICATION:
                return NOTIFICATION;

            case TABLE_CONVERSATION:
                return CONVERSATION;

            case TABLE_DESCRIPTOR:
                return DESCRIPTOR;

            case TABLE_ANNOTATION:
                return ANNOTATION;

            case TABLE_OPERATION:
                return OPERATION;

            case TABLE_INVITATION:
                return INVITATION;

            case TABLE_IMAGE:
                return IMAGE;

            case SEQUENCE:
                return SEQUENCE_ID;
        }
        return null;
    }
}