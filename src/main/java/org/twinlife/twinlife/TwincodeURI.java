/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * Information extracted after parsing of a URI by the TwincodeOutboundService.parseURI().
 */
public class TwincodeURI {
    public static final String PARAM_ID = BuildConfig.INVITATION_PARAM_ID;
    public static final String CALL_ACTION = "call." + Twinlife.DOMAIN;
    public static final String TRANSFER_ACTION = "transfer." + Twinlife.DOMAIN;
    // https://invite.twin.me/?twincodeId=...
    public static final String INVITE_ACTION = "invite." + Twinlife.DOMAIN;
    public static final String AUTHENTICATE_ACTION = "authenticate." + Twinlife.DOMAIN;
    public static final String ACCOUNT_MIGRATION_ACTION = "account.migration." + Twinlife.DOMAIN;
    public static final String PROXY_ACTION = "proxy." + Twinlife.DOMAIN;
    public static final String CALL_PATH = "/call/";
    public static final String SPACE_PATH = "/space/";

    public enum Kind {
        Invitation,
        Call,
        Transfer,
        AccountMigration,
        Authenticate,
        SpaceCard,
        Proxy
    }

    @NonNull
    public final Kind kind;
    @Nullable
    public final UUID twincodeId;
    @Nullable
    public final String twincodeOptions;
    @NonNull
    public final String uri;
    @NonNull
    public final String label;
    @Nullable
    public final String pubKey;

    public TwincodeURI(@NonNull Kind kind, @Nullable UUID twincodeId,
                       @Nullable String twincodeOptions, @Nullable String pubKey,
                       @NonNull String uri, @NonNull String label) {

        this.kind = kind;
        this.twincodeId = twincodeId;
        this.twincodeOptions = twincodeOptions;
        this.pubKey = pubKey;
        this.uri = uri;
        this.label = label;
    }
}
