/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.crypto;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SessionKeyPair;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

/**
 * A session key pair that is protected by two secrets exchanged at some time in the past by the relation.
 *
 */
class SessionSecretKeyPair implements SessionKeyPair {
    private static final String LOG_TAG = "SessionSecretKeyPair";
    private static final boolean DEBUG = false;

    static final long SECRET_RENEW_DELAY = BuildConfig.SECRET_RENEW_DELAY * 1000L; // 30 days for prod, 5mn for dev.

    @NonNull
    private final TwincodeOutbound mTwincodeOutbound;
    @NonNull
    private final TwincodeOutbound mPeerTwincodeOutbound;
    @NonNull
    private final byte[] mPeerSecret1;
    @Nullable
    private final byte[] mPeerSecret2;
    @NonNull
    private final byte[] mSecret;
    @NonNull
    private final UUID mSessionId;
    private final int mKeyIndex;
    private final boolean mNeedRenew;
    private long mNonceSequence;
    private long mSequenceCounter;

    SessionSecretKeyPair(@NonNull UUID sessionId, @NonNull TwincodeOutbound twincodeOutbound,
                         @NonNull TwincodeOutbound peerTwincodeOutbound, long secretUpdateDate,
                         long nonceSequence, @NonNull byte[] secret, int keyIndex,
                         @NonNull byte[] peerSecret1, @Nullable byte[] peerSecret2) {
        if (DEBUG) {
            Log.d(LOG_TAG, "KeyPair: keyIndex=" + keyIndex);
        }

        mSessionId = sessionId;
        mTwincodeOutbound = twincodeOutbound;
        mPeerTwincodeOutbound = peerTwincodeOutbound;
        mNonceSequence = nonceSequence;
        mSequenceCounter = MAX_EXCHANGE;
        mPeerSecret1 = peerSecret1;
        mPeerSecret2 = peerSecret2;
        mSecret = secret;
        mKeyIndex = keyIndex;

        final long now = System.currentTimeMillis();
        mNeedRenew = secretUpdateDate + SECRET_RENEW_DELAY < now;
    }

    //
    // Package specific Methods
    //
    @NonNull
    TwincodeOutbound getTwincodeOutbound() {

        return mTwincodeOutbound;
    }

    @NonNull
    TwincodeOutbound getPeerTwincodeOutbound() {

        return mPeerTwincodeOutbound;
    }

    void refreshNonceSequence(long nonceSequence) {

        mNonceSequence = nonceSequence;
        mSequenceCounter = MAX_EXCHANGE;
    }

    @Nullable
    byte[] getSecret(int index) {

        return index == 1 ? mPeerSecret1 : mPeerSecret2;
    }

    @Override
    @NonNull
    public UUID getSessionId() {

        return mSessionId;
    }

    @Override
    public synchronized long allocateNonce() {

        if (mSequenceCounter <= 0) {
            return 0;
        }
        mSequenceCounter--;
        mNonceSequence++;
        return mNonceSequence;
    }

    int getKeyIndex() {

        return mKeyIndex;
    }

    @Override
    public long getSequenceCount() {

        return mSequenceCounter;
    }

    public boolean needRenew() {

        return mNeedRenew;
    }

    @Override
    @NonNull
    public Pair<BaseService.ErrorCode, Sdp> encrypt(@NonNull Sdp sdp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "encrypt: sdp=" + sdp);
        }

        final long nonceSequence = allocateNonce();
        if (nonceSequence == 0) {
            return new Pair<>(BaseService.ErrorCode.NO_PRIVATE_KEY, null);
        }

        CryptoBox cipher = null;
        try {
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);

            // Not encrypted content which is authenticated by the private key:
            // - P2P session id (also acts as random nonce),
            // - nonce sequence (because it's easier to transmit that way),
            encoder.writeUUID(getSessionId());
            encoder.writeLong(nonceSequence);

            final byte[] auth = outputStream.toByteArray();

            cipher = CryptoBox.create(CryptoBox.Kind.AES_GCM);
            int status = cipher.bind(mSecret);
            if (status != 1) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "bind failed with error " + status);
                }
                return new Pair<>(BaseService.ErrorCode.INVALID_PRIVATE_KEY, null);
            }
            byte[] result = new byte[auth.length + sdp.getLength() + 64];
            int len = cipher.encryptAEAD(nonceSequence, sdp.getData(), sdp.getLength(), auth, result);
            if (len <= 0) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "encrypt failed with error " + len);
                }
                return new Pair<>(BaseService.ErrorCode.ENCRYPT_ERROR, null);
            }
            return new Pair<>(BaseService.ErrorCode.SUCCESS, new Sdp(result, len, sdp.isCompressed(), getKeyIndex()));

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Log.e(LOG_TAG, "encrypt exception", exception);
            }
            return new Pair<>(BaseService.ErrorCode.LIBRARY_ERROR, null);

        } finally {
            if (cipher != null) {
                cipher.dispose();
            }
        }
    }

    @NonNull
    public Pair<BaseService.ErrorCode, Sdp> decrypt(@NonNull Sdp sdp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "decrypt: sdp=" + sdp);
        }

        if (!sdp.isEncrypted()) {
            return new Pair<>(BaseService.ErrorCode.NO_PUBLIC_KEY, sdp);
        }

        CryptoBox cipher = null;
        try {
            final byte[] encrypted = sdp.getData();
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(encrypted);
            final BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

            // Extract information from the clear content which is authenticated by the secret key:
            // - P2P sessionId (which must match our P2P sessionId)
            // - nonce sequence
            final UUID peerSessionId = decoder.readUUID();
            if (!peerSessionId.equals(getSessionId())) {
                return new Pair<>(BaseService.ErrorCode.BAD_SIGNATURE, null);
            }
            final long nonceSequence = decoder.readLong();

            // Authenticate and decrypt the content.
            final int authLength = encrypted.length - inputStream.available();
            cipher = CryptoBox.create(CryptoBox.Kind.AES_GCM);
            final int keyIndex = sdp.getKeyIndex();
            final byte[] key = getSecret(keyIndex);
            if (key == null) {
                return new Pair<>(BaseService.ErrorCode.NO_SECRET_KEY, null);
            }

            int status = cipher.bind(key);
            if (status != 1) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "bind failed with error " + status);
                }
                return new Pair<>(BaseService.ErrorCode.DECRYPT_ERROR, null);
            }

            final byte[] data = new byte[encrypted.length];
            int len = cipher.decryptAEAD(nonceSequence, encrypted, authLength, data);
            if (len <= 0 || len > data.length) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "decrypt failed with error " + len);
                }
                return new Pair<>(BaseService.ErrorCode.DECRYPT_ERROR, null);
            }

            return new Pair<>(BaseService.ErrorCode.SUCCESS, new Sdp(data, len, sdp.isCompressed(), 0));

        } catch (SerializerException serializerException) {
            return new Pair<>(BaseService.ErrorCode.BAD_ENCRYPTION_FORMAT, null);

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Log.e(LOG_TAG, "decrypt exception", exception);
            }
            return new Pair<>(BaseService.ErrorCode.LIBRARY_ERROR, null);

        } finally {
            if (cipher != null) {
                cipher.dispose();
            }
        }
    }

    @Override
    public void dispose() {

    }
}
