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
import org.twinlife.twinlife.TwincodeOutbound;

class TwincodeKeyInfo extends KeyInfo {

    @NonNull
    private final TwincodeOutbound mTwincodeOutbound;

    TwincodeKeyInfo(@NonNull TwincodeOutbound twincodeOutbound, long modificationDate, int flags,
                    @Nullable byte[] signingKey, @Nullable byte[] encryptionKey,
                    long nonceSequence, int keyIndex, byte[] secret) {

        super(modificationDate, flags, signingKey, encryptionKey, nonceSequence, keyIndex, secret);

        mTwincodeOutbound = twincodeOutbound;
    }

    @Override
    @NonNull
    TrustMethod getTrustMethod() {

        return mTwincodeOutbound.getTrustMethod();
    }

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("TwincodeKeyInfo[");
        stringBuilder.append(mTwincodeOutbound);
        stringBuilder.append(" flags=");
        stringBuilder.append(mFlags);
        stringBuilder.append("]");

        return stringBuilder.toString();
    }
}
