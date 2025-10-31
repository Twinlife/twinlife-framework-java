/*
 *  Copyright (c) 2013-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.factory;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeFactory;

import java.util.List;
import java.util.UUID;

class TwincodeFactoryImpl implements TwincodeFactory {

    @NonNull
    private final UUID mId;
    private final long mModificationDate;
    @NonNull
    private final TwincodeInbound mTwincodeInbound;
    @NonNull
    private final TwincodeOutbound mTwincodeOutbound;
    @NonNull
    private final UUID mTwincodeSwitchId;
    @NonNull
    private final List<AttributeNameValue> mAttributes;

    @Override
    @NonNull
    public UUID getId() {

        return mId;
    }

    @Override
    public TwincodeFacet getFacet() {

        return TwincodeFacet.FACTORY;
    }

    @Override
    public boolean isTwincodeFactory() {

        return true;
    }

    @Override
    public boolean isTwincodeInbound() {

        return false;
    }

    @Override
    public boolean isTwincodeOutbound() {

        return false;
    }

    @Override
    public boolean isTwincodeSwitch() {

        return false;
    }

    @Override
    @NonNull
    public TwincodeInbound getTwincodeInbound() {

        return mTwincodeInbound;
    }

    @Override
    @NonNull
    public TwincodeOutbound getTwincodeOutbound() {

        return mTwincodeOutbound;
    }

    @Override
    @NonNull
    public UUID getTwincodeSwitchId() {

        return mTwincodeSwitchId;
    }

    @Override
    @NonNull
    public List<AttributeNameValue> getAttributes() {

        return mAttributes;
    }

    @Override
    @Nullable
    public Object getAttribute(@NonNull String name) {

        for (AttributeNameValue attribute : mAttributes) {
            if (name.equals(attribute.name)) {

                return attribute.value;
            }
        }

        return null;
    }

    TwincodeFactoryImpl(@NonNull UUID id, long modificationDate, @NonNull TwincodeInbound twincodeInbound,
                        @NonNull TwincodeOutbound twincodeOutbound, @NonNull UUID twincodeSwitchId,
                        @NonNull List<AttributeNameValue> attributes) {

        mId = id;
        mModificationDate = modificationDate;
        mTwincodeInbound = twincodeInbound;
        mTwincodeOutbound = twincodeOutbound;
        mTwincodeSwitchId = twincodeSwitchId;

        mAttributes = attributes;
    }

    public long getModificationDate() {

        return mModificationDate;
    }

    //
    // Override Object methods
    //

    @Override
    public boolean equals(Object object) {

        if (!(object instanceof TwincodeFactoryImpl)) {

            return false;
        }

        TwincodeFactoryImpl twincodeFactoryImpl = (TwincodeFactoryImpl) object;

        return twincodeFactoryImpl.mId.equals(mId) && twincodeFactoryImpl.mModificationDate == mModificationDate;
    }

    @Override
    public int hashCode() {

        int result = 17;
        result = 31 * result + mId.hashCode();
        result = 31 * result + Long.hashCode(mModificationDate);

        return result;
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        if (BuildConfig.ENABLE_DUMP) {
            stringBuilder.append("TwincodeFactoryImpl:\n");
            stringBuilder.append(" id=");
            stringBuilder.append(mId);
            stringBuilder.append("\n");
            stringBuilder.append(" modificationDate=");
            stringBuilder.append(mModificationDate);
            stringBuilder.append("\n");
            stringBuilder.append(" twincodeInbound=");
            stringBuilder.append(mTwincodeInbound);
            stringBuilder.append("\n");
            stringBuilder.append(" twincodeOutbound=");
            stringBuilder.append(mTwincodeOutbound);
            stringBuilder.append("\n");
            stringBuilder.append(" twincodeSwitchId=");
            stringBuilder.append(mTwincodeSwitchId);
            stringBuilder.append("\n");
            stringBuilder.append(" attributes:");
            stringBuilder.append("\n");
            for (AttributeNameValue attribute : mAttributes) {
                stringBuilder.append("  ");
                stringBuilder.append(attribute.name);
                stringBuilder.append("=");
                if (attribute.value instanceof Bitmap) {
                    stringBuilder.append("-");
                } else {
                    stringBuilder.append(attribute.value);
                }
                stringBuilder.append("\n");
            }
        }

        return stringBuilder.toString();
    }
}
