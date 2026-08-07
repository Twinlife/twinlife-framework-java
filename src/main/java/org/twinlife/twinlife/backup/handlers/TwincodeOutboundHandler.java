/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup.handlers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.backup.BackupHandler;
import org.twinlife.twinlife.backup.VerifyResult;
import org.twinlife.twinlife.crypto.CryptoServiceImpl;
import org.twinlife.twinlife.crypto.RawKeyInfo;
import org.twinlife.twinlife.image.ImageInfo;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundImpl;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundServiceImpl;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public class TwincodeOutboundHandler extends BackupHandler<TwincodeOutbound> {
    private static final String LOG_TAG = "TwincodeOutboundHandler";
    private static final boolean DEBUG = false;

    public static final UUID SCHEMA_ID = UUID.fromString("b1a6f07f-364d-451d-a03b-054afcb3d61a");
    private static final int SCHEMA_VERSION = 1;

    @NonNull
    private final TwincodeOutboundServiceImpl mTwincodeOutboundService;
    @NonNull
    private final CryptoServiceImpl mCryptoService;

    public TwincodeOutboundHandler(@NonNull TwincodeOutboundServiceImpl twincodeOutboundService, @NonNull CryptoServiceImpl cryptoService) {
        super();
        this.mTwincodeOutboundService = twincodeOutboundService;
        this.mCryptoService = cryptoService;
    }

    @Override
    protected void initDeserializers() {
        mRestorers.put(TwincodeOutboundRestorerV1.VERSION, new TwincodeOutboundRestorerV1());
    }

    @Override
    public void backup(@NonNull BinaryEncoder encoder) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "backup: encoder=" + encoder);
        }
        List<TwincodeOutbound> localTwincodes = mTwincodeOutboundService.getLocalTwincodes();

        for (TwincodeOutbound twincodeOutbound : localTwincodes) {
            TwincodeOutboundImpl twincodeOutboundImpl = (TwincodeOutboundImpl) twincodeOutbound;
            encoder.writeUUID(SCHEMA_ID);
            encoder.writeInt(SCHEMA_VERSION);
            encoder.writeLong(twincodeOutboundImpl.getDatabaseId().getId());
            encoder.writeUUID(twincodeOutboundImpl.getId());
            encoder.writeLong(twincodeOutboundImpl.getCreationDate());
            encoder.writeLong(twincodeOutboundImpl.getModificationDate());

            List<BaseService.AttributeNameValue> avatarAttribute = new ArrayList<>();

            if (twincodeOutboundImpl.getAvatarId() != null) {
                if (twincodeOutboundImpl.getAvatarId() instanceof ExportedImageId) {
                    avatarAttribute.add(new BaseService.AttributeNameImageIdValue(Twincode.AVATAR_ID, (ExportedImageId) twincodeOutboundImpl.getAvatarId()));
                } else {
                    ImageInfo imageInfo = mTwincodeOutboundService.getTwinlifeImpl().getImageServiceImpl().getImageInfo(twincodeOutboundImpl.getAvatarId());
                    if (imageInfo != null) {
                        avatarAttribute.add(new BaseService.AttributeNameUUIDValue(Twincode.AVATAR_ID, imageInfo.imageId));
                    }
                }
            }
            encoder.writeAttributes(twincodeOutboundImpl.getAttributes(avatarAttribute, Collections.emptyList()));

            encoder.writeInt(twincodeOutboundImpl.getFlags());

            if (DEBUG) {
                Log.d(LOG_TAG, "Backup Twincode: "+twincodeOutbound);
            }

            RawKeyInfo keyInfo = mCryptoService.getRawKeyInfo(twincodeOutbound);

            if (keyInfo == null) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "No key found");
                }
                encoder.writeBoolean(false);
            } else {
                encoder.writeBoolean(true);
                encoder.writeData(keyInfo.signingKey);
                encoder.writeOptionalBytes(keyInfo.encryptionKey);
                encoder.writeLong(keyInfo.creationDate);
                encoder.writeLong(keyInfo.modificationDate);
                encoder.writeInt(keyInfo.flags);

                if (DEBUG) {
                    Log.d(LOG_TAG, "Backup key");
                }
            }
        }
    }


    private class TwincodeOutboundRestorerV1 implements Restorer<TwincodeOutbound> {
        public static final int VERSION = 1;

        @Nullable
        @Override
        public TwincodeOutbound restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException {
            if (inPlace) {
                return restoreInPlace(decoder);
            } else {
                return fullyRestore(decoder);
            }
        }

        @NonNull
        private TwincodeOutbound fullyRestore(@NonNull BinaryDecoder decoder) throws SerializerException {
            if (DEBUG) {
                Log.d(LOG_TAG, "fullyRestore: decoder=" + decoder);
            }

            long dbId = decoder.readLong();
            UUID twincodeId = decoder.readUUID();
            long creationDate = decoder.readLong();
            long modificationDate = decoder.readLong();
            List<BaseService.AttributeNameValue> attributes = decoder.readAttributes();
            int flags = decoder.readInt();

            // Make sure the twincode will be refreshed from the server (see RestoreExecutor.syncTwincodes())
            flags |= TwincodeOutboundImpl.FLAG_NEED_FETCH;

            TwincodeOutbound twincodeOutbound = mTwincodeOutboundService.restoreTwincode(dbId, twincodeId, creationDate, modificationDate, attributes, flags);

            if (twincodeOutbound == null) {
                throw new SerializerException("could not restore twincodeOutbound " + twincodeId);
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "Restored twincodeOutbound: " + twincodeOutbound);
                if (attributes != null) {
                    Log.d(LOG_TAG, "   attributes: " + Arrays.toString(attributes.toArray()));
                } else {
                    Log.d(LOG_TAG, "   attributes: null");
                }
            }

            boolean hasKey = decoder.readBoolean();
            if (hasKey) {
                byte[] signingKey = decoder.readBytes(null).array();
                byte[] encryptionKey = decoder.readOptionalBytes(null);
                long keyCreationDate = decoder.readLong();
                long keyModificationDate = decoder.readLong();
                int keyFlags = decoder.readInt();

                RawKeyInfo keyInfo = new RawKeyInfo(keyCreationDate, keyModificationDate, signingKey, encryptionKey, keyFlags);
                ErrorCode errorCode = mCryptoService.restoreKeyInfo(twincodeOutbound, keyInfo);

                if (errorCode != ErrorCode.SUCCESS) {
                    throw new SerializerException("Could not restore key for twincode " + twincodeId + ": " + errorCode);
                }

                if (DEBUG) {
                    Log.d(LOG_TAG, "Restored key for twincodeId: " + twincodeId);
                }
            }

            return twincodeOutbound;
        }

        @Nullable
        private TwincodeOutbound restoreInPlace(@NonNull BinaryDecoder decoder) throws SerializerException {
            if (DEBUG) {
                Log.d(LOG_TAG, "restoreInPlace: decoder=" + decoder);
            }

            // We only want the twincodeId, the modification date and the attributes, but we still need to decode all data.
            decoder.readLong();
            UUID twincodeId = decoder.readUUID();
            decoder.readLong();
            long modificationDate = decoder.readLong();
            List<BaseService.AttributeNameValue> attributes = decoder.readAttributes();
            decoder.readInt();

            boolean hasKey = decoder.readBoolean();
            if (hasKey) {
                decoder.readBytes(null).array();
                decoder.readOptionalBytes(null);
                decoder.readLong();
                decoder.readLong();
                decoder.readInt();
            }

            TwincodeOutboundImpl existingTwincode = (TwincodeOutboundImpl) mTwincodeOutboundService.getLocalTwincode(twincodeId);

            if (existingTwincode == null) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "Twincode " + twincodeId + " doesn't exist anymore, ignoring.");
                }
                return null;
            }

            if (attributes == null) {
                Log.e(LOG_TAG, "No attributes found for twincode" + twincodeId);
                return null;
            }

            if (!existingTwincode.isOwner()) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "Twincode " + twincodeId + " is not ours, skipping update");
                }
                return existingTwincode;
            }

            List<String> deleteAttributeNames = getDeleteAttributeNames(existingTwincode, attributes);

            return mTwincodeOutboundService.restoreExistingTwincode(existingTwincode, modificationDate, attributes, deleteAttributeNames);
        }

        @NonNull
        private List<String> getDeleteAttributeNames(@NonNull TwincodeOutboundImpl existingTwincode, @NonNull List<BaseService.AttributeNameValue> attributes) {
            List<String> deleteAttributeNames = new ArrayList<>();

            for (BaseService.AttributeNameValue attr : existingTwincode.getAttributes()) {
                boolean found = false;

                for (BaseService.AttributeNameValue bkpAttr : attributes) {
                    if (attr.name.equals(bkpAttr.name)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    deleteAttributeNames.add(attr.name);
                }
            }
            return deleteAttributeNames;
        }

        // Verify


        @NonNull
        @Override
        public VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException {
            if (DEBUG) {
                Log.d(LOG_TAG, "verify: decoder=" + decoder);
            }

            decoder.readLong(); //dbId
            UUID twincodeId = decoder.readUUID();
            decoder.readLong(); //creationDate
            decoder.readLong(); //modificationDate
            List<BaseService.AttributeNameValue> backupAttributes = decoder.readAttributes();
            if (backupAttributes == null) {
                backupAttributes = Collections.emptyList();
            }

            decoder.readInt(); //flags

            boolean hasKey = decoder.readBoolean();
            if (hasKey) {
                decoder.readBytes(null).array(); //signingKey
                decoder.readOptionalBytes(null); //encryptionKey
                decoder.readLong(); //keyCreationDate
                decoder.readLong(); //keyModificationDate
                decoder.readInt(); //keyFlags
            }

            TwincodeOutboundImpl localTwincode = (TwincodeOutboundImpl) mTwincodeOutboundService.getLocalTwincode(twincodeId);

            if (localTwincode == null) {
                return new VerifyResult.Absent<>(twincodeId, TwincodeOutbound.class);
            }

            List<BaseService.AttributeNameValue> avatarAttribute = new ArrayList<>();

            if (localTwincode.getAvatarId() != null) {
                if (localTwincode.getAvatarId() instanceof ExportedImageId) {
                    avatarAttribute.add(new BaseService.AttributeNameImageIdValue(Twincode.AVATAR_ID, (ExportedImageId) localTwincode.getAvatarId()));
                } else {
                    ImageInfo imageInfo = mTwincodeOutboundService.getTwinlifeImpl().getImageServiceImpl().getImageInfo(localTwincode.getAvatarId());
                    if (imageInfo != null) {
                        avatarAttribute.add(new BaseService.AttributeNameUUIDValue(Twincode.AVATAR_ID, imageInfo.imageId));
                    }
                }
            }

            List<BaseService.AttributeNameValue> dbAttributes = localTwincode.getAttributes(avatarAttribute, Collections.emptyList());

            boolean modified = dbAttributes.size() != backupAttributes.size() || new HashSet<>(dbAttributes).retainAll(backupAttributes);

            return new VerifyResult.Present<>(localTwincode, modified);
        }
    }
}
