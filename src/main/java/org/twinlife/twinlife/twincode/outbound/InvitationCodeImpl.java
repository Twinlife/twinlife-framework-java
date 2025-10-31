/*
 *  Copyright (c) 2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.twincode.outbound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.InvitationCode;

import java.util.UUID;

public class InvitationCodeImpl implements InvitationCode {

    private final long mCreationDate;

    private final int mValidityPeriod;

    private final String mCode;

    @NonNull
    private final UUID mTwincodeId;

    @Nullable
    private final String mPublicKey;

    public InvitationCodeImpl(long mCreationDate, int validityPeriod, @NonNull String mCode, @NonNull UUID twincodeId, @Nullable String publicKey) {
        this.mCreationDate = mCreationDate;
        this.mValidityPeriod = validityPeriod;
        this.mCode = mCode;
        this.mTwincodeId = twincodeId;
        this.mPublicKey = publicKey;
    }

    @NonNull
    @Override
    public String getCode() {
        return mCode;
    }

    @NonNull
    @Override
    public UUID getTwincodeOutboundId() {
        return mTwincodeId;
    }

    @Override
    public long getCreationDate() {
        return mCreationDate;
    }

    @Override
    public int getValidityPeriod() {
        return mValidityPeriod;
    }

    @Nullable
    public String getPublicKey() {
        return mPublicKey;
    }

    @NonNull
    @Override
    public String toString() {
        if (!BuildConfig.ENABLE_DUMP) {
            return "";
        }

        return " InvitationCode[creationDate=" + mCreationDate +
                " creationDate=" + mCreationDate +
                " validityPeriod=" + mValidityPeriod +
                " code=" + mCode +
                " twincodeId=" + mTwincodeId +
                " publicKey=" + mPublicKey +
                "]";

    }
}
