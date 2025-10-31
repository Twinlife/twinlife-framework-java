/*
 *  Copyright (c) 2013-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributor: Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *  Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.outbound;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.AttributeNameStringValue;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.database.DatabaseObjectImpl;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TwincodeOutboundImpl extends DatabaseObjectImpl implements TwincodeOutbound {
    private static final String LOG_TAG = "TwincodeOutboundImpl";
    private static final boolean DEBUG = false;

    // When set, we must get the twincode attributes from the server.
    static final int FLAG_NEED_FETCH = 0x01;
    static final int TRUST_METHOD_SHIFT = 8;
    public static final int FLAG_SIGNED = 0x02;
    public static final int FLAG_TRUSTED = 0x04;
    public static final int FLAG_VERIFIED = 0x08;
    public static final int FLAG_ENCRYPT = 0x10;
    public static final int FLAG_CERTIFIED = 0x20;
    public static final int FLAG_OWNER = 0x100;

    // Twincodes created with the factory are signed, trusted and we are OWNER.
    public static final int CREATION_FLAGS = FLAG_SIGNED | FLAG_TRUSTED | FLAG_OWNER;

    static int toFlags(@NonNull TrustMethod trustMethod) {
        switch (trustMethod) {
            case NONE:
            default:
                return 0x00;
            case OWNER:
                return (0x01 << TRUST_METHOD_SHIFT) | FLAG_TRUSTED;
            case QR_CODE:
                return (0x02 << TRUST_METHOD_SHIFT) | FLAG_TRUSTED;
            case VIDEO:
                return (0x04 << TRUST_METHOD_SHIFT) | FLAG_TRUSTED;
            case LINK:
                return (0x08 << TRUST_METHOD_SHIFT) | FLAG_TRUSTED;
            case PEER:
                return (0x10 << TRUST_METHOD_SHIFT) | FLAG_TRUSTED;
            case AUTO:
                return (0x20 << TRUST_METHOD_SHIFT) | FLAG_TRUSTED;
            case INVITATION_CODE:
                return (0x40 << TRUST_METHOD_SHIFT); // The public key cannot be trusted for the invitation code.
        }
    }

    @NonNull
    public static TrustMethod toTrustMethod(int flags) {

        if ((flags & FLAG_TRUSTED) == 0) {
            if ((flags & 0x4000) != 0) {
                return TrustMethod.INVITATION_CODE;
            }
            return TrustMethod.NONE;
        }
        // Several trust method flags can be set, check according to the highest trust method.
        if ((flags & FLAG_OWNER) != 0) {
            return TrustMethod.OWNER;
        }
        if ((flags & 0x0200) != 0) {
            return TrustMethod.QR_CODE;
        }
        if ((flags & 0x0400) != 0) {
            return TrustMethod.VIDEO;
        }
        if ((flags & 0x0800) != 0) {
            return TrustMethod.LINK;
        }
        if ((flags & 0x1000) != 0) {
            return TrustMethod.PEER;
        }
        if ((flags & 0x2000) != 0) {
            return TrustMethod.AUTO;
        }
        if ((flags & 0x4000) != 0) {
            return TrustMethod.INVITATION_CODE;
        }
        return TrustMethod.NONE;
    }

    @NonNull
    private final UUID mTwincodeId;
    private long mModificationDate;
    @Nullable
    private List<AttributeNameValue> mAttributes;
    @Nullable
    private String mName;
    @Nullable
    private ImageId mAvatarId;
    @Nullable
    private String mCapabilities;
    @Nullable
    private String mDescription;
    private int mFlags;

    TwincodeOutboundImpl(@NonNull DatabaseIdentifier id, @NonNull UUID twincodeId, long modificationDate,
                         @Nullable String name, @Nullable String description, @Nullable ImageId avatarId,
                         @Nullable String capabilities, @Nullable byte[] content, int flags) {
        super(id);
        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeOutboundImpl: id=" + id + " id=" + twincodeId);
        }

        mTwincodeId = twincodeId;
        mFlags = 0;
        update(modificationDate, name, description, avatarId, capabilities, content, flags);
    }

    TwincodeOutboundImpl(@NonNull DatabaseIdentifier id, @NonNull UUID twincodeId, int flags, long modificationDate,
                         @Nullable List<AttributeNameValue> attributes) {
        super(id);
        if (DEBUG) {
            Log.d(LOG_TAG, "TwincodeOutboundImpl: id=" + id + " id=" + twincodeId);
        }

        mTwincodeId = twincodeId;
        mFlags = flags;
        importAttributes(attributes, modificationDate, null);
    }

    //
    // Package specific Methods
    //

    @Override
    @NonNull
    public UUID getId() {

        return mTwincodeId;
    }

    @Override
    public TwincodeFacet getFacet() {

        return TwincodeFacet.OUTBOUND;
    }

    @Override
    public boolean isTwincodeFactory() {

        return false;
    }

    @Override
    public boolean isTwincodeInbound() {

        return false;
    }

    @Override
    public boolean isTwincodeOutbound() {

        return true;
    }

    @Override
    public boolean isTwincodeSwitch() {

        return false;
    }

    @Nullable
    public String getName() {

        return mName;
    }

    @Nullable
    public String getDescription() {

        return mDescription;
    }

    @Nullable
    public ImageId getAvatarId() {

        return mAvatarId;
    }

    @Nullable
    public String getCapabilities() {

        return mCapabilities;
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

    @NonNull
    public synchronized List<AttributeNameValue> getAttributes(@NonNull List<AttributeNameValue> update,
                                                               @Nullable List<String> deletedAttributes) {

        final ArrayList<AttributeNameValue> attributes;
        synchronized (this) {
            if (mAttributes == null) {
                attributes = new ArrayList<>();
            } else {
                attributes = new ArrayList<>(mAttributes);
            }
            if (mName != null) {
                attributes.add(new AttributeNameStringValue(Twincode.NAME, mName));
            }
            if (mDescription != null) {
                attributes.add(new AttributeNameStringValue(Twincode.DESCRIPTION, mDescription));
            }
            if (mCapabilities != null) {
                attributes.add(new AttributeNameStringValue(Twincode.CAPABILITIES, mCapabilities));
            }
        }
        if (deletedAttributes != null) {
            for (String name : deletedAttributes) {
                AttributeNameValue.removeAttribute(attributes, name);
            }
        }

        for (AttributeNameValue attribute : update) {
            AttributeNameValue.removeAttribute(attributes, attribute.name);
            attributes.add(attribute);
        }
        return attributes;
    }

    public long getModificationDate() {

        return mModificationDate;
    }

    public void needFetch() {

        mFlags |= FLAG_NEED_FETCH;
    }

    @Override
    public synchronized boolean isKnown() {

        return (mFlags & FLAG_NEED_FETCH) == 0;
    }

    /**
     * Whether the twincode attributes are signed by a public key.
     *
     * @return true if the twincode attributes are signed.
     */
    @Override
    public synchronized boolean isSigned() {

        return (mFlags & FLAG_SIGNED) != 0;
    }

    /**
     * Whether the twincode public key is trusted.
     *
     * @return true if the twincode public key is trusted.
     */
    @Override
    public synchronized boolean isTrusted() {

        return (mFlags & FLAG_TRUSTED) != 0;
    }

    /**
     * Whether the twincode attributes and the signature are verified and match.
     *
     * @return true if the twincode attributes correspond to the signature.
     */
    @Override
    public synchronized boolean isVerified() {

        return (mFlags & FLAG_VERIFIED) != 0;
    }

    /**
     * Whether the twincode was certified as part of the contact certification process.
     *
     * @return true if the relation was certified.
     */
    public synchronized boolean isCertified() {

        return (mFlags & FLAG_CERTIFIED) != 0;
    }

    /**
     * Whether SDPs are encrypted when sending/receiving (secret keys are known).
     *
     * @return true if the SDP are encrypted.
     */
    public synchronized boolean isEncrypted() {

        return (mFlags & FLAG_ENCRYPT) != 0;
    }

    /**
     * Whether the twincode was created by this application.
     *
     * @return true if we are owner of the twincode.
     */
    public synchronized boolean isOwner() {

        return (mFlags & FLAG_OWNER) != 0;
    }

    /**
     * Whether the twincode public key is trusted and how.
     *
     * @return how we trusted the public key.
     */
    @Override
    @NonNull
    public synchronized TrustMethod getTrustMethod() {

        return toTrustMethod(mFlags);
    }

    public synchronized void setAvatarId(@Nullable ImageId avatarId) {

        mAvatarId = avatarId;
    }

    synchronized void update(long modificationDate, @Nullable String name, @Nullable String description,
                             @Nullable ImageId avatarId, @Nullable String capabilities, @Nullable byte[] content, int flags) {

        mModificationDate = modificationDate;
        mName = name;
        mDescription = description;
        mAvatarId = avatarId;
        mCapabilities = capabilities;
        mAttributes = BinaryCompactDecoder.deserialize(content);
        mFlags = flags;
    }

    synchronized void importAttributes(@Nullable List<AttributeNameValue> attributes, long modificationDate,
                                       @Nullable List<AttributeNameValue> previousAttributes) {

        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {
                switch (attribute.name) {
                    case Twincode.NAME:
                        String newName = String.valueOf(attribute.value);
                        if (previousAttributes != null && !Utils.equals(newName, mName) && mName != null) {
                            previousAttributes.add(new AttributeNameStringValue(Twincode.NAME, mName));
                        }
                        mName = newName;
                        break;
                    case Twincode.DESCRIPTION:
                        String newDescription = String.valueOf(attribute.value);
                        if (previousAttributes != null && !Utils.equals(newDescription, mDescription) && mDescription != null) {
                            previousAttributes.add(new AttributeNameStringValue(Twincode.DESCRIPTION, mDescription));
                        }
                        mDescription = newDescription;
                        break;
                    case Twincode.CAPABILITIES:
                        String newCapabilities = String.valueOf(attribute.value);
                        if (previousAttributes != null && !Utils.equals(newCapabilities, mCapabilities) && mCapabilities != null) {
                            previousAttributes.add(new AttributeNameStringValue(Twincode.CAPABILITIES, mCapabilities));
                        }
                        mCapabilities = newCapabilities;
                        break;
                    case Twincode.AVATAR_ID:
                        // Ignore this attribute: it was handled by storeTwincodeOutbound() with a relation to the image table;
                        break;
                    default:
                        boolean found = false;
                        if (mAttributes != null) {
                            for (AttributeNameValue existingAddr : mAttributes) {
                                if (attribute.name.equals(existingAddr.name)) {
                                    if (previousAttributes != null && !Utils.equals(existingAddr.value, attribute.value)) {
                                        previousAttributes.add(existingAddr);
                                    }
                                    mAttributes.remove(existingAddr);
                                    mAttributes.add(attribute);
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
                        break;
                }
            }
        }
        mModificationDate = modificationDate;
    }

    public int getFlags() {

        return mFlags;
    }

    public void setFlags(int flags) {

        mFlags = flags;
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

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("TwincodeOutboundImpl[");
        stringBuilder.append(getDatabaseId());
        stringBuilder.append(" twincode=");
        stringBuilder.append(mTwincodeId);
        stringBuilder.append(" flags=");
        stringBuilder.append(Integer.toString(mFlags, 16));
        stringBuilder.append(" modificationDate=");
        stringBuilder.append(mModificationDate);
        if (mAttributes != null) {
            stringBuilder.append(" attributes:");
            for (AttributeNameValue attribute : mAttributes) {
                stringBuilder.append(" ");
                stringBuilder.append(attribute.name);
                stringBuilder.append("=");
                stringBuilder.append(attribute.value);
            }
        }
        stringBuilder.append("]");

        return stringBuilder.toString();
    }
}
