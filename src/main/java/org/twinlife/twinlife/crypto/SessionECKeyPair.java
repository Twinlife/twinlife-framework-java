/*
 *  Copyright (c) 2024 twinlife SAS.
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package org.twinlife.twinlife.crypto;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
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
import java.security.SecureRandom;
import java.util.UUID;

/**
 * The session key pair implemented with Elliptic Curve private/public keys.
 */
class SessionECKeyPair implements SessionKeyPair {
    private static final String LOG_TAG = "SessionECKeyPair";
    private static final boolean DEBUG = false;

    @NonNull
    private final TwincodeOutbound mTwincodeOutbound;
    @NonNull
    private final CryptoKey mPrivateKey;
    @NonNull
    private final SecureRandom mRandom;
    @Nullable
    private CryptoKey mPeerKey;
    @NonNull
    private final UUID mSessionId;
    private final long mModificationDate;
    private long mNonceSequence;
    private long mSequenceCounter;

    SessionECKeyPair(@NonNull UUID sessionId, @NonNull TwincodeOutbound twincodeOutbound,
                     int privKeyFlags, long modificationDate, @Nullable byte[] encryptionKey,
                     long nonceSequence, @NonNull SecureRandom random) {
        if (DEBUG) {
            Log.d(LOG_TAG, "KeyPair: privKeyFlags=" + privKeyFlags + " privKeyFlags=" + privKeyFlags);
        }

        mSessionId = sessionId;
        mTwincodeOutbound = twincodeOutbound;
        mModificationDate = modificationDate;
        mNonceSequence = nonceSequence;
        mSequenceCounter = MAX_EXCHANGE;
        mRandom = random;

        final CryptoKey.Kind kind = KeyInfo.toCryptoKind(privKeyFlags, true);
        CryptoKey privKey = CryptoKey.importPrivateKey(kind, encryptionKey, false);
        if (privKey == null || !privKey.isValid()) {
            throw new SecurityException("invalid key");
        }
        mPrivateKey = privKey;
    }

    //
    // Package specific Methods
    //

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

    @Override
    public long getSequenceCount() {

        return mSequenceCounter;
    }

    public boolean needRenew() {

        return false;
    }

    @Override
    public void dispose() {

        mPrivateKey.dispose();
        if (mPeerKey != null) {
            mPeerKey.dispose();
        }
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
            final byte[] salt;

            // Not encrypted content which is authenticated by the private key:
            // - version identifying the encryption method
            // - nonce sequence (because it's easier to transmit that way),
            // - salt used for the key generation,
            // - the twincode used for the authenticate+encrypt,
            // - the public key used for authenticate+encrypt.

            salt = new byte[CryptoBox.KEY_LENGTH];
            mRandom.nextBytes(salt);
            encoder.writeBytes(salt, 0, salt.length);

            final byte[] rawKey = mPrivateKey.getPublicKey(false);
            if (rawKey == null) {
                return new Pair<>(BaseService.ErrorCode.NO_PRIVATE_KEY, null);
            }
            encoder.writeBytes(rawKey, 0, rawKey.length);
            encoder.writeUUID(getSessionId());
            encoder.writeLong(nonceSequence);

            final byte[] auth = outputStream.toByteArray();

            cipher = CryptoBox.create(CryptoBox.Kind.AES_GCM);
            if (mPeerKey == null) {
                return new Pair<>(BaseService.ErrorCode.NO_PUBLIC_KEY, null);
            }
            int status = cipher.bind(true, mPrivateKey, mPeerKey, salt);
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
            return new Pair<>(BaseService.ErrorCode.SUCCESS, new Sdp(result, len, sdp.isCompressed(), 1));

        } catch (Exception exception) {
            Log.e(LOG_TAG, "encrypt exception", exception);
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
        CryptoKey pubKey = null;
        try {
            final byte[] encrypted = sdp.getData();
            final ByteArrayInputStream inputStream = new ByteArrayInputStream(encrypted);
            final BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

            // Extract information from the clear content which is authenticated by the secret key:
            // - nonce sequence
            // - binary public key
            // - salt for ECDH
            // - P2P sessionId (which must match our P2P sessionId)
            final byte[] salt = decoder.readBytes(null).array();
            final byte[] rawKey = decoder.readBytes(null).array();
            pubKey = CryptoKey.importPublicKey(CryptoKey.Kind.ECDSA, rawKey, false);
            if (pubKey == null) {
                return new Pair<>(BaseService.ErrorCode.INVALID_PUBLIC_KEY, null);
            }

            final UUID peerSessionId = decoder.readUUID();
            if (!peerSessionId.equals(mSessionId)) {
                return new Pair<>(BaseService.ErrorCode.BAD_SIGNATURE, null);
            }
            final long nonceSequence = decoder.readLong();

            // Authenticate and decrypt the content.
            final int authLength = encrypted.length - inputStream.available();
            cipher = CryptoBox.create(CryptoBox.Kind.AES_GCM);
            int status = cipher.bind(false, mPrivateKey, pubKey, salt);
            if (status != 1) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "bind failed with error " + status);
                }
                return new Pair<>(BaseService.ErrorCode.NO_PRIVATE_KEY, null);
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
            Log.e(LOG_TAG, "decrypt exception", exception);
            return new Pair<>(BaseService.ErrorCode.LIBRARY_ERROR, null);

        } finally {
            if (cipher != null) {
                cipher.dispose();
            }
            if (pubKey != null) {
                pubKey.dispose();
            }
        }
    }

}
