/*
 *  Copyright (c) 2013-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.inbound;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.database.DatabaseObjectImpl;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryCompactEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TwincodeInboundImpl extends DatabaseObjectImpl implements TwincodeInbound {
    private static final String LOG_TAG = "TwincodeOutboundImpl";
    private static final boolean DEBUG = false;

    //
    // TwincodeInbound methods.
    //

    @Override
    @NonNull
    public UUID getId() {

        return mTwincodeId;
    }

    @Override
    public TwincodeFacet getFacet() {

        return TwincodeFacet.INBOUND;
    }

    @Override
    public boolean isTwincodeFactory() {

        return false;
    }

    @Override
    public boolean isTwincodeInbound() {

        return true;
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
    public List<AttributeNameValue> getAttributes() {

        return mAttributes == null ? new ArrayList<>() : mAttributes;
    }

    @Override
    @Nullable
    public synchronized Object getAttribute(@NonNull String name) {

        if (mAttributes != null) {
            for (AttributeNameValue attribute : mAttributes) {
                if (name.equals(attribute.name)) {

                    return attribute.value;
                }
            }
        }

        return null;
    }

    @Nullable
    public String getCapabilities() {

        return mCapabilities;
    }

    @NonNull
    public TwincodeOutbound getTwincodeOutbound() {

        return mTwincodeOutbound;
    }

    public void setTwincodeOutbound(@Nullable TwincodeOutbound twincodeOutbound) {

        mTwincodeOutbound = twincodeOutbound;
    }

    @NonNull
    private final UUID mTwincodeId;
    @Nullable
    private List<AttributeNameValue> mAttributes;
    private long mModificationDate;
    @Nullable
    private String mCapabilities;
    @Nullable
    private TwincodeOutbound mTwincodeOutbound;
    @Nullable
    private UUID mFactoryId;

    TwincodeInboundImpl(@NonNull DatabaseIdentifier id, @NonNull UUID twincodeId, @Nullable UUID factoryId,
                        @Nullable TwincodeOutbound twincodeOutbound, @Nullable String capabilities,
                        @Nullable byte[] content, long modificationDate) {
        super(id);
        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeInboundImpl: id=" + id + " id=" + twincodeId);
        }

        mTwincodeId = twincodeId;
        mFactoryId = factoryId;
        mTwincodeOutbound = twincodeOutbound;
        update(modificationDate, capabilities, content);
    }

    TwincodeInboundImpl(@NonNull DatabaseIdentifier id, @NonNull UUID twincodeId, long modificationDate,
                        @Nullable List<AttributeNameValue> attributes) {
        super(id);
        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeInboundImpl: id=" + id + " id=" + twincodeId);
        }

        mTwincodeId = twincodeId;
        importAttributes(attributes, modificationDate);
    }

    @Override
    @Nullable
    public UUID getTwincodeFactoryId() {

        return mFactoryId;
    }

    //
    // Package specific Methods
    //

    long getModificationDate() {

        return mModificationDate;
    }

    public void setTwincodeFactoryId(@Nullable UUID factoryId) {

        mFactoryId = factoryId;
    }

    synchronized void update(long modificationDate, @Nullable String capabilities, @Nullable byte[] content) {

        mModificationDate = modificationDate;
        mCapabilities = capabilities;
        mAttributes = BinaryCompactDecoder.deserialize(content);
    }

    synchronized void importAttributes(@Nullable List<AttributeNameValue> attributes, long modificationDate) {

        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {
                if ("capabilities".equals(attribute.name)) {
                    mCapabilities = String.valueOf(attribute.value);
                } else {
                    boolean found = false;
                    if (mAttributes != null) {
                        for (AttributeNameValue existingAddr : mAttributes) {
                            if (attribute.name.equals(existingAddr.name)) {
                                existingAddr.value = attribute.value;
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        if (mAttributes == null) {
                            mAttributes = new ArrayList<>();
                        }
                        mAttributes.add(attribute);
                    }
                }
            }
        }
        mModificationDate = modificationDate;
    }

    //
    // Serialization support
    //

    synchronized byte[] serialize() {
        if (DEBUG) {
            Log.d(LOG_TAG, "serialize");
        }

        return BinaryCompactEncoder.serialize(mAttributes);
    }

    //
    // Override Object methods
    //

    @NonNull
    @Override
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("TwincodeInboundImpl[id=");
        stringBuilder.append(mTwincodeId);
        stringBuilder.append(" modificationDate=");
        stringBuilder.append(mModificationDate);
        if (mCapabilities != null) {
            stringBuilder.append(" capabilities=");
            stringBuilder.append(mCapabilities);
        }
        if (mAttributes != null) {
            stringBuilder.append(" attributes:");
            for (AttributeNameValue attribute : mAttributes) {
                stringBuilder.append("  ");
                stringBuilder.append(attribute.name);
                stringBuilder.append("=");
                if (attribute.value instanceof Bitmap) {
                    stringBuilder.append("-");
                } else {
                    stringBuilder.append(attribute.value);
                }
            }
        }
        stringBuilder.append("]");

        return stringBuilder.toString();
    }
}