/*
 *  Copyright (c) 2024 twinlife SAS.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.TrustMethod;

class KeyInfo {

    static final int KEY_TYPE_MASK = 0x0FF;
    static final int KEY_TYPE_25519 = 1;
    static final int KEY_TYPE_ECDSA = 2;
    static final int KEY_PRIVATE_FLAG = 0x0100;

    private final CryptoKey.Kind mSignKind;
    private final CryptoKey.Kind mEncryptKind;
    private final long mModificationDate;
    protected final int mFlags;
    private final int mKeyIndex;
    @Nullable
    private final CryptoKey mEncryptionKey;
    @Nullable
    private final CryptoKey mSigningKey;
    private final byte[] mSecret;
    private final long mNonceSequence;

    @NonNull
    static CryptoKey.Kind toCryptoKind(int flags, boolean encryption) {

        switch (flags & KEY_TYPE_MASK) {
            case KEY_TYPE_25519:
                return encryption ? CryptoKey.Kind.X25519 : CryptoKey.Kind.ED25519;

            case KEY_TYPE_ECDSA:
            default:
                return CryptoKey.Kind.ECDSA;
        }
    }

    KeyInfo(long modificationDate, int flags,
            @Nullable byte[] signingKey, @Nullable byte[] encryptionKey,
            long nonceSequence, int keyIndex, byte[] secret) {

        mSignKind = toCryptoKind(flags, false);
        mEncryptKind = toCryptoKind(flags, true);
        mFlags = flags;
        mSecret = secret;
        mKeyIndex = keyIndex;
        mModificationDate = modificationDate;
        mNonceSequence = nonceSequence;
        if ((flags & KEY_PRIVATE_FLAG) != 0) {
            mSigningKey = signingKey == null ? null : CryptoKey.importPrivateKey(mSignKind, signingKey, false);
            mEncryptionKey = encryptionKey == null ? null : CryptoKey.importPrivateKey(mEncryptKind, encryptionKey, false);
        } else {
            mSigningKey = signingKey == null ? null : CryptoKey.importPublicKey(mSignKind, signingKey, false);
            mEncryptionKey = encryptionKey == null ? null : CryptoKey.importPublicKey(mEncryptKind, encryptionKey, false);
        }
    }

    //
    // Package specific Methods
    //

    @NonNull
    TrustMethod getTrustMethod() {

        return TrustMethod.NONE;
    }

    @NonNull
    CryptoKey.Kind getSigningKind() {

        return mSignKind;
    }

    @NonNull
    CryptoKey.Kind getEncryptionKind() {

        return mEncryptKind;
    }

    public long getModificationDate() {

        return mModificationDate;
    }

    long getNonceSequence() {

        return mNonceSequence;
    }

    @Nullable
    CryptoKey getSigningPrivateKey() {

        return ((mFlags & KEY_PRIVATE_FLAG) != 0) ? mSigningKey : null;
    }

    @Nullable
    CryptoKey getSigningPublicKey() {

        return mSigningKey;
    }

    @Nullable
    CryptoKey getEncryptionPrivateKey() {

        return ((mFlags & KEY_PRIVATE_FLAG) != 0) ? mEncryptionKey : null;
    }

    @Nullable
    CryptoKey getEncryptionPublicKey() {

        return mEncryptionKey;
    }

    @Nullable
    String getPublicSigningKey() {

        if (mSigningKey == null) {
            return null;
        }

        final byte[] raw = mSigningKey.getPublicKey(true);
        return raw == null ? null : new String(raw);
    }

    @Nullable
    String getPublicEncryptionKey() {

        if (mEncryptionKey == null) {
            return null;
        }

        final byte[] raw = mEncryptionKey.getPublicKey(true);
        return raw == null ? null : new String(raw);
    }

    @NonNull
    byte[] getSecretKey() {

        return mSecret != null ? mSecret : new byte[0];
    }

    int getKeyIndex() {

        return mKeyIndex;
    }

    void dispose() {

        if (mEncryptionKey != null) {
            mEncryptionKey.dispose();
        }
        if (mSigningKey != null) {
            mSigningKey.dispose();
        }
    }

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("KeyInfo[");
        stringBuilder.append(" flags=");
        stringBuilder.append(mFlags);
        stringBuilder.append(" modificationDate=");
        stringBuilder.append(mModificationDate);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }
}
