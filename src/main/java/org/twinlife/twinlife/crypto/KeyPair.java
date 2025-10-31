/*
 *  Copyright (c) 2024 twinlife SAS.
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package org.twinlife.twinlife.crypto;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

class KeyPair {
    private static final String LOG_TAG = "KeyPair";
    private static final boolean DEBUG = false;

    @Nullable
    private final CryptoKey mPrivateKey;
    @Nullable
    private final CryptoKey mPeerKey;
    @NonNull
    private final UUID mTwincodeId;
    @NonNull
    private final UUID mPeerTwincodeId;
    @NonNull
    private final UUID mSubjectId;

    KeyPair(int privKeyFlags, @Nullable byte[] privateKey,
            int peerKeyFlags, @Nullable byte[] peerPublicKey,
            @NonNull UUID twincodeId, @NonNull UUID peerTwincodeId, @NonNull UUID subjectId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "KeyPair: privKeyFlags=" + privKeyFlags + " peerKeyFlags=" + peerKeyFlags);
        }

        final CryptoKey.Kind kind = KeyInfo.toCryptoKind(privKeyFlags, false);
        if (kind != KeyInfo.toCryptoKind(peerKeyFlags, false)
                || privateKey == null || peerPublicKey == null) {
            mPrivateKey = null;
            mPeerKey = null;
        } else {
            mPrivateKey = CryptoKey.importPrivateKey(kind, privateKey, false);
            mPeerKey = CryptoKey.importPublicKey(kind, peerPublicKey, false);
        }
        mTwincodeId = twincodeId;
        mPeerTwincodeId = peerTwincodeId;
        mSubjectId = subjectId;
    }

    //
    // Package specific Methods
    //

    @Nullable
    CryptoKey getPrivateKey() {

        return mPrivateKey;
    }

    @Nullable
    CryptoKey getPublicKey() {

        return mPeerKey;
    }

    @NonNull
    UUID getTwincodeId() {

        return mTwincodeId;
    }

    @NonNull
    UUID getPeerTwincodeId() {

        return mPeerTwincodeId;
    }

    @NonNull
    UUID getSubjectId() {

        return mSubjectId;
    }

    public void dispose() {

        if (mPrivateKey != null) {
            mPrivateKey.dispose();
        }
        if (mPeerKey != null) {
            mPeerKey.dispose();
        }
    }
}
