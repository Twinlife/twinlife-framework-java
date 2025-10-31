/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * The twincode invocation contains information when we receive a peer's invocation that was made with
 * the `invokeTwincode` or `secureInvokeTwincode` operation.  It contains a first part provided and
 * filled by the invoker twincode and a second part filled locally when the invocation content was
 * encrypted and is decrypted and verified.
 */
public class TwincodeInvocation {

    @NonNull
    public final UUID invocationId;
    @NonNull
    public final RepositoryObject subject;
    @NonNull
    public final String action;
    @Nullable
    public final List<BaseService.AttributeNameValue> attributes;

    // Information setup by the CryptoService
    @Nullable
    public final UUID peerTwincodeId;
    @Nullable
    public final byte[] secretKey;
    @Nullable
    public final String publicKey;
    public final int keyIndex;

    // Whether and how the twincode used to encrypt the invocation was trusted.
    @NonNull
    public final TrustMethod trustMethod;

    public TwincodeInvocation(@NonNull UUID invocationId, @NonNull RepositoryObject subject,
                              @NonNull String action, @Nullable List<BaseService.AttributeNameValue> attributes,
                              @Nullable UUID peerTwincodeId, int keyIndex, @Nullable byte[] secretKey, @Nullable String publicKey,
                              @NonNull TrustMethod trustMethod) {
        this.invocationId = invocationId;
        this.subject = subject;
        this.action = action;
        this.attributes = attributes;

        // Information provided only when the invokeTwincode is encrypted (extracted and verified by CryptoService).
        this.peerTwincodeId = peerTwincodeId;
        this.keyIndex = keyIndex;
        this.secretKey = secretKey;
        this.publicKey = publicKey;
        this.trustMethod = trustMethod;
    }
}
