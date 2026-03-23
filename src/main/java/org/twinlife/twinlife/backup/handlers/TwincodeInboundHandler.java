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

import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.backup.BackupHandler;
import org.twinlife.twinlife.backup.VerifyResult;
import org.twinlife.twinlife.twincode.inbound.TwincodeInboundImpl;
import org.twinlife.twinlife.twincode.inbound.TwincodeInboundServiceImpl;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundServiceImpl;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.util.List;
import java.util.UUID;

public class TwincodeInboundHandler extends BackupHandler<TwincodeInbound> {
    private static final String LOG_TAG = "TwincodeInboundHandler";
    private static final boolean DEBUG = false;

    public static final UUID SCHEMA_ID = UUID.fromString("592d44e0-a1fb-4451-b015-5f355406faae");
    private static final int SCHEMA_VERSION = 1;

    @NonNull
    private final TwincodeInboundServiceImpl mTwincodeInboundService;
    @NonNull
    private final TwincodeOutboundServiceImpl mTwincodeOutboundService;

    public TwincodeInboundHandler(@NonNull TwincodeInboundServiceImpl twincodeInboundService, @NonNull TwincodeOutboundServiceImpl cryptoService) {
        super();
        this.mTwincodeInboundService = twincodeInboundService;
        this.mTwincodeOutboundService = cryptoService;
    }

    @Override
    protected void initDeserializers() {
        mRestorers.put(TwincodeInboundRestorerV1.VERSION, new TwincodeInboundRestorerV1());
    }

    @Override
    public void backup(@NonNull BinaryEncoder encoder) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "backup: encoder=" + encoder);
        }
        List<TwincodeInbound> localTwincodes = mTwincodeInboundService.getLocalTwincodes();

        for (TwincodeInbound twincodeInbound : localTwincodes) {
            encoder.writeUUID(SCHEMA_ID);
            encoder.writeInt(SCHEMA_VERSION);
            encoder.writeLong(twincodeInbound.getDatabaseId().getId());
            encoder.writeUUID(twincodeInbound.getId());
            encoder.writeUUID(twincodeInbound.getTwincodeOutbound().getId());
            encoder.writeOptionalUUID(twincodeInbound.getTwincodeFactoryId());
            encoder.writeLong(((TwincodeInboundImpl) twincodeInbound).getModificationDate());

            if (DEBUG) {
                Log.d(LOG_TAG, "Backup TwincodeInbound: " + twincodeInbound);
            }
        }
    }


    private class TwincodeInboundRestorerV1 implements Restorer<TwincodeInbound> {
        public static final int VERSION = 1;

        @Nullable
        private List<TwincodeInbound> mLocalTwincodes = null;

        @Nullable
        @Override
        public TwincodeInbound restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException {
            long dbId = decoder.readLong();
            UUID twincodeId = decoder.readUUID();
            UUID twincodeOutboundId = decoder.readUUID();
            UUID twincodeFactoryId = decoder.readOptionalUUID();
            long modificationDate = decoder.readLong();

            if (inPlace) {
                return null;
            }

            TwincodeOutbound twincodeOutbound = mTwincodeOutboundService.getLocalTwincode(twincodeOutboundId);

            if (twincodeOutbound == null) {
                throw new SerializerException("No twincodeOutbound found for twincodeInbound: " + twincodeId + " (twincodeOutboundId: " + twincodeOutboundId + ")");
            }

            TwincodeInbound twincodeInbound = mTwincodeInboundService.restoreTwincode(dbId, twincodeId, twincodeOutbound, twincodeFactoryId, modificationDate);

            if (twincodeInbound == null) {
                throw new SerializerException("could not restore twincodeInbound " + twincodeId);
            }

            if (DEBUG) {
                Log.d(LOG_TAG, "Restored twincodeInbound: " + twincodeInbound);
            }

            return twincodeInbound;
        }

        @NonNull
        @Override
        public VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException {
            if (DEBUG) {
                Log.d(LOG_TAG, "verify: decoder=" + decoder);
            }

            decoder.readLong(); // dbId
            UUID twincodeId = decoder.readUUID();
            decoder.readUUID(); // twincodeOutboundId
            decoder.readOptionalUUID(); // twincodeFactoryId
            decoder.readLong(); // modificationDate

            for (TwincodeInbound twincodeInbound : getLocalTwincodes()) {
                if (twincodeInbound.getId().equals(twincodeId)) {
                    return new VerifyResult.Present<>(twincodeInbound, false);
                }
            }


            return new VerifyResult.Absent<>(twincodeId, TwincodeInbound.class);
        }

        private List<TwincodeInbound> getLocalTwincodes() {
            if (mLocalTwincodes == null) {
                mLocalTwincodes = mTwincodeInboundService.getLocalTwincodes();
            }
            return mLocalTwincodes;
        }
    }
}
