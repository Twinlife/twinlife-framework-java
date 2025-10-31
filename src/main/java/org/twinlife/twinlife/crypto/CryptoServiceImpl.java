/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.crypto;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;
import android.util.Pair;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.DatabaseException;
import org.twinlife.twinlife.ExportedImageId;
import org.twinlife.twinlife.ImageId;
import org.twinlife.twinlife.Sdp;
import org.twinlife.twinlife.SessionKeyPair;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.image.ImageInfo;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundImpl;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CryptoServiceImpl extends BaseServiceImpl<CryptoService.ServiceObserver> implements CryptoService {
    private static final String LOG_TAG = "CryptoServiceImpl...";
    private static final boolean DEBUG = false;

    private final CryptoServiceProvider mServiceProvider;
    private final SecureRandom mRandom;

    public static int getKeyType(@NonNull byte[] pubKey) {

        return pubKey.length == CryptoKey.ECDSA_PUBKEY_LENGTH ? KeyInfo.KEY_TYPE_ECDSA : KeyInfo.KEY_TYPE_25519;
    }

    public CryptoServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {

        super(twinlifeImpl, connection);

        setServiceConfiguration(new CryptoServiceServiceConfiguration());

        mServiceProvider = new CryptoServiceProvider(this, twinlifeImpl.getDatabaseService());
        mRandom = new SecureRandom();
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof CryptoServiceServiceConfiguration)) {
            setConfigured(false);
            return;
        }

        CryptoServiceServiceConfiguration cryptoServiceConfiguration = new CryptoServiceServiceConfiguration();
        setServiceConfiguration(cryptoServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    //
    // Implement CryptoService interface
    //
    @Override
    @Nullable
    public String getPublicKey(@NonNull TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPublicKey: twincodeOutbound=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            return null;
        }

        final KeyInfo keyInfo = mServiceProvider.loadTwincodeKey(twincodeOutbound);
        if (keyInfo == null) {
            return null;
        }

        try {
            final CryptoKey crypto = keyInfo.getSigningPublicKey();
            if (crypto == null) {
                return null;
            }

            final byte[] pubKey = crypto.getPublicKey(true);
            if (pubKey == null) {
                return null;
            }
            return new String(pubKey);
        } finally {
            keyInfo.dispose();
        }
    }

    /**
     * Get the secret key associated with the twincode.
     * <p>
     * NB: this method is package-private because we don't want to expose the secret key.
     * </p>
     *
     * @param twincodeOutbound the twincode.
     * @param peerTwincodeOutbound the peer twincode.
     * @return the raw bytes of the secret key, or null if it does not exist.
     */
    @Nullable
    public SignatureInfoIQ getSignatureInfoIQ(@NonNull TwincodeOutbound twincodeOutbound,
                                              @NonNull TwincodeOutbound peerTwincodeOutbound,
                                              boolean renew) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSignatureInfoIQ: twincodeOutbound=" + twincodeOutbound + " peerTwincodeOutbound=" + peerTwincodeOutbound);
        }

        if (!isServiceOn()) {
            return null;
        }

        final KeyInfo keyInfo = mServiceProvider.loadTwincodeKeyWithSecret((TwincodeOutboundImpl) twincodeOutbound, peerTwincodeOutbound,
                0, renew ? CryptoServiceProvider.CREATE_NEXT_SECRET : CryptoServiceProvider.CREATE_FIRST_SECRET);
        if (keyInfo == null) {
            return null;
        }

        byte[] secretKey;
        String publicKey;
        try {
            secretKey = keyInfo.getSecretKey();
            publicKey = keyInfo.getPublicSigningKey();
        } finally {
            keyInfo.dispose();
        }

        if (publicKey == null || secretKey.length == 0) {
            return null;
        }

        return new SignatureInfoIQ(SignatureInfoIQ.IQ_SIGNATURE_INFO_SERIALIZER, mTwinlifeImpl.newRequestId(),
                twincodeOutbound.getId(), publicKey, secretKey, keyInfo.getKeyIndex());

    }

    @Override
    @Nullable
    public byte[] sign(@NonNull TwincodeOutbound twincodeOutbound,
                       @NonNull List<AttributeNameValue> attributes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sign: twincodeOutbound=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            return null;
        }

        final KeyInfo keyInfo = mServiceProvider.loadTwincodeKey(twincodeOutbound);
        if (keyInfo == null) {
            return null;
        }

        try {
            final AttributeNameValue imageAttribute = AttributeNameValue.getAttribute(attributes, Twincode.AVATAR_ID);
            final CryptoKey signingKey = keyInfo.getSigningPrivateKey();
            final CryptoKey encryptionKey = keyInfo.getEncryptionPublicKey();
            if (signingKey == null) {
                return null;
            }

            final byte[] sha;
            if (imageAttribute != null) {
                ImageInfo image = mServiceProvider.loadImageInfo((ImageId) imageAttribute.value);
                if (image == null) {
                    return null;
                }
                sha = image.data;

            } else if (twincodeOutbound.getAvatarId() != null) {
                ImageInfo image = mServiceProvider.loadImageInfo(twincodeOutbound.getAvatarId());
                if (image == null) {
                    return null;
                }
                ExportedImageId imageId = new ExportedImageId(twincodeOutbound.getAvatarId(), image.imageId);
                attributes.add(new AttributeNameImageIdValue(Twincode.AVATAR_ID, imageId));
                sha = image.data;

            } else {
                sha = null;
            }

            final List<AttributeNameValue> signAttributes = new ArrayList<>(attributes);
            final byte[] encryptionPubKey = encryptionKey != null ? encryptionKey.getPublicKey(false) : null;
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);
            final int version;
            switch (keyInfo.getSigningKind()) {
                case ECDSA:
                    version = SIGNATURE_VERSION_ECDSA;
                    break;

                case ED25519:
                    version = SIGNATURE_VERSION_ED25519;
                    break;

                default:
                    return null;
            }

            encoder.writeInt(version);
            encoder.writeUUID(twincodeOutbound.getId());
            for (String name : PREDEFINED_LIST) {
                final AttributeNameValue attribute = AttributeNameListValue.removeAttribute(signAttributes, name);
                if (attribute instanceof AttributeNameStringValue) {
                    encoder.writeOptionalString((String) attribute.value);
                } else if (attribute instanceof AttributeNameUUIDValue) {
                    encoder.writeOptionalUUID((UUID) attribute.value);
                } else if (attribute instanceof AttributeNameImageIdValue) {
                    encoder.writeOptionalUUID(((ExportedImageId) attribute.value).getExportedId());
                } else {
                    encoder.writeOptionalString(null);
                }
            }

            encoder.writeOptionalBytes(sha);
            encoder.writeOptionalBytes(encryptionPubKey);
            encoder.writeAttributes(signAttributes);

            // Sign what is serialized with the private key.
            final byte[] data = outputStream.toByteArray();
            final byte[] signature = new byte[CryptoKey.MAX_SIG_LENGTH];
            final int len = signingKey.sign(data, signature, false);
            if (len <= 0) {
                return null;
            }

            // Build the final signature where we indicate the version and list of attributes
            // in the same order as we serialized them.
            outputStream.reset();
            encoder.writeInt(version);
            encoder.writeBytes(signature, 0, len);
            encoder.writeOptionalBytes(sha);
            encoder.writeOptionalBytes(encryptionPubKey);
            encoder.writeInt(signAttributes.size());
            for (AttributeNameValue attribute : signAttributes) {
                encoder.writeString(attribute.name);
            }
            return outputStream.toByteArray();

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "sign exception", exception);
            }
            return null;

        } finally {
            keyInfo.dispose();
        }
    }

    /**
     * Sign the content with the twincode private signing key.
     *
     * @param twincodeOutbound the twincode used to sign.
     * @param content the content to sign.
     * @return the base64URL signature or null if there is a problem.
     */
    @Nullable
    public String signContent(@NonNull TwincodeOutbound twincodeOutbound, @NonNull byte[] content) {
        if (DEBUG) {
            Log.d(LOG_TAG, "signContent: twincodeOutbound=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            return null;
        }

        final KeyInfo keyInfo = mServiceProvider.loadTwincodeKey(twincodeOutbound);
        if (keyInfo == null) {
            return null;
        }

        try {
            final CryptoKey signingKey = keyInfo.getSigningPrivateKey();
            if (signingKey == null) {
                return null;
            }

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);
            final int version;
            switch (keyInfo.getSigningKind()) {
                case ECDSA:
                    version = SIGNATURE_VERSION_ECDSA;
                    break;

                case ED25519:
                    version = SIGNATURE_VERSION_ED25519;
                    break;

                default:
                    return null;
            }

            encoder.writeInt(version);
            encoder.writeUUID(twincodeOutbound.getId());
            encoder.writeData(content);

            // Sign what is serialized with the private key.
            final byte[] data = outputStream.toByteArray();
            final byte[] signature = new byte[CryptoKey.MAX_SIG_LENGTH];
            final int len = signingKey.sign(data, signature, true);
            if (len <= 0) {
                return null;
            }
            return new String(signature, 0, len);

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "signContent", exception);
            }
            return null;

        } finally {
            keyInfo.dispose();
        }
    }

    /**
     * Verify the signature of the given content with the twincode public key.
     *
     * @param twincodeOutbound the twincode used to sign.
     * @param content the content to sign.
     * @param signature the signature to verify.
     * @return SUCCESS if the signature is valid or an error code.
     */
    @NonNull
    public ErrorCode verifyContent(@NonNull TwincodeOutbound twincodeOutbound, @NonNull byte[] content,
                                   @NonNull String signature) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verify twincodeOutbound=" + twincodeOutbound + " twincodeId=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        final KeyInfo keyInfo = mServiceProvider.loadTwincodeKey(twincodeOutbound);
        if (keyInfo == null) {
            return ErrorCode.NO_PUBLIC_KEY;
        }

        try {
            final CryptoKey crypto = keyInfo.getSigningPublicKey();
            if (crypto == null) {
                return ErrorCode.INVALID_PUBLIC_KEY;
            }

            final int version;
            switch (keyInfo.getSigningKind()) {
                case ECDSA:
                    version = SIGNATURE_VERSION_ECDSA;
                    break;

                case ED25519:
                    version = SIGNATURE_VERSION_ED25519;
                    break;

                default:
                    return ErrorCode.INVALID_PUBLIC_KEY;
            }

            // Build the data to be verified.
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);

            encoder.writeInt(version);
            encoder.writeUUID(twincodeOutbound.getId());
            encoder.writeData(content);

            final byte[] data = outputStream.toByteArray();
            final int len = crypto.verify(data, signature.getBytes(), true);
            if (len != 1) {
                return ErrorCode.BAD_SIGNATURE;
            }
            return ErrorCode.SUCCESS;

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "verifyContent", exception);
            }
            return ErrorCode.LIBRARY_ERROR;

        } finally {
            keyInfo.dispose();
        }
    }

    @Override
    @NonNull
    public CipherResult encrypt(@NonNull TwincodeOutbound cipherTwincode,
                                @NonNull TwincodeOutbound senderTwincode,
                                @NonNull TwincodeOutbound targetTwincode,
                                int options,
                                @NonNull List<AttributeNameValue> attributes) {
        if (DEBUG) {
            Log.d(LOG_TAG, "encrypt: cipherTwincode=" + cipherTwincode + " senderTwincode=" + senderTwincode
                    + " targetTwincode=" + targetTwincode);
        }

        if (!isServiceOn()) {
            return CipherResult.error(ErrorCode.SERVICE_UNAVAILABLE);
        }

        KeyInfo targetKey = null;
        KeyInfo keyInfo = null;
        KeyInfo senderInfo = null;
        CryptoBox cipher = null;
        try {
            targetKey = mServiceProvider.loadTwincodeKey(targetTwincode);
            if (targetKey == null) {
                return CipherResult.error(ErrorCode.NO_PUBLIC_KEY);
            }
            final CryptoKey targetPublicKey = targetKey.getEncryptionPublicKey();
            if (targetPublicKey == null) {
                return CipherResult.error(ErrorCode.INVALID_PUBLIC_KEY);
            }

            int createSecret = 0;
            if ((options & TwincodeOutboundService.CREATE_SECRET) != 0) {
                createSecret = CryptoServiceProvider.CREATE_SECRET;
            } else if ((options & TwincodeOutboundService.CREATE_NEW_SECRET) != 0) {
                createSecret = CryptoServiceProvider.CREATE_NEXT_SECRET;
            } else if ((options & TwincodeOutboundService.SEND_SECRET) != 0) {
                createSecret = CryptoServiceProvider.CREATE_FIRST_SECRET;
            }
            if (cipherTwincode != senderTwincode) {
                senderInfo = mServiceProvider.loadTwincodeKeyWithSecret((TwincodeOutboundImpl) senderTwincode, targetTwincode, 0, createSecret);
                if (senderInfo == null) {
                    return CipherResult.error(ErrorCode.NO_PRIVATE_KEY);
                }
                keyInfo = mServiceProvider.loadTwincodeKeyWithSecret((TwincodeOutboundImpl) cipherTwincode, targetTwincode, 1, 0);

            } else {
                keyInfo = mServiceProvider.loadTwincodeKeyWithSecret((TwincodeOutboundImpl) cipherTwincode, targetTwincode, 1, createSecret);
                senderInfo = keyInfo;
            }

            if (keyInfo == null) {
                return CipherResult.error(ErrorCode.NO_PRIVATE_KEY);
            }
            final long nonceSequence = keyInfo.getNonceSequence();

            final CryptoKey cipherPrivateKey = keyInfo.getEncryptionPrivateKey();
            if (cipherPrivateKey == null) {
                return CipherResult.error(ErrorCode.NO_PRIVATE_KEY);
            }

            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);
            final int version = getEncryptVersion(keyInfo.getEncryptionKind(), targetKey.getEncryptionKind());
            if (version <= 0) {
                return CipherResult.error(ErrorCode.INVALID_PRIVATE_KEY);
            }
            final byte[] salt = new byte[CryptoBox.KEY_LENGTH];
            mRandom.nextBytes(salt);

            // Not encrypted content which is authenticated by the private key:
            // - version identifying the encryption method
            // - nonce sequence (because it's easier to transmit that way),
            // - salt used for the key generation,
            // - the twincode used for the authenticate+encrypt,
            // - either:
            //   1 => the twincode used for encryption (the receiver MUST know and trust that twincode),
            //   2 => the public key used for authenticate+encrypt.
            encoder.writeInt(version);
            encoder.writeLong(nonceSequence);
            encoder.writeBytes(salt, 0, salt.length);
            encoder.writeOptionalUUID(senderTwincode.getId());
            if (cipherTwincode != senderTwincode) {
                encoder.writeEnum(1);
                encoder.writeUUID(cipherTwincode.getId());
            } else {
                encoder.writeEnum(2);
                encoder.writeOptionalString(keyInfo.getPublicEncryptionKey());
            }
            final byte[] auth = outputStream.toByteArray();

            outputStream.reset();
            encoder.writeOptionalString(senderInfo.getPublicSigningKey());
            if (options != 0) {
                encoder.writeInt(senderInfo.getKeyIndex());
                encoder.writeOptionalBytes(senderInfo.getSecretKey());
            } else {
                // Secure invocation without sending a secret.
                encoder.writeInt(0);
                encoder.writeOptionalBytes(null);
            }
            encoder.writeAttributes(attributes);

            final byte[] toCrypt = outputStream.toByteArray();

            cipher = CryptoBox.create(CryptoBox.Kind.AES_GCM);
            int status = cipher.bind(true, cipherPrivateKey, targetPublicKey, salt);
            if (status != 1) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "bind failed with error " + status);
                }
                return CipherResult.error(ErrorCode.INVALID_PRIVATE_KEY);
            }
            byte[] result = new byte[auth.length + toCrypt.length + 64];
            int len = cipher.encryptAEAD(nonceSequence, toCrypt, toCrypt.length, auth, result);
            if (len <= 0) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "encrypt failed with error " + len);
                }
                return CipherResult.error(ErrorCode.ENCRYPT_ERROR);
            }
            return CipherResult.ok(result, len);

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Log.e(LOG_TAG, "encrypt exception", exception);
            }
            return CipherResult.error(ErrorCode.LIBRARY_ERROR);

        } finally {
            if (targetKey != null) {
                targetKey.dispose();
            }
            if (senderInfo != null && senderInfo != keyInfo) {
                senderInfo.dispose();
            }
            if (keyInfo != null) {
                keyInfo.dispose();
            }
            if (cipher != null) {
                cipher.dispose();
            }
        }
    }

    static int getEncryptVersion(@NonNull CryptoKey.Kind privateKind, @NonNull CryptoKey.Kind publicKind) {

        if (privateKind != publicKind) {
            return -1;
        }
        switch (privateKind) {
            case ECDSA:
                return ENCRYPT_VERSION_ECDSA;
            case X25519:
                return ENCRYPT_VERSION_X25519;
            default:
                return -1;
        }
    }

    @NonNull
    public Pair<ErrorCode, SessionKeyPair> createSession(@NonNull UUID sessionId, @NonNull TwincodeOutbound twincodeOutbound,
                                                         @Nullable TwincodeOutbound peerTwincodeOutbound, boolean strict) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSession: sessionId=" + sessionId + " twincodeOutbound=" + twincodeOutbound + " strict=" + strict);
        }

        SessionKeyPair keyPair = mServiceProvider.prepareSession(sessionId, twincodeOutbound, peerTwincodeOutbound, strict);
        return new Pair<>(keyPair != null ? ErrorCode.SUCCESS : ErrorCode.NO_PRIVATE_KEY, keyPair);
    }

    @NonNull
    public DecipherResult decrypt(@NonNull TwincodeOutbound receiverTwincode, @NonNull byte[] encrypted) {
        if (DEBUG) {
            Log.d(LOG_TAG, "decrypt: receiverTwincode=" + receiverTwincode);
        }

        if (!isServiceOn()) {
            return DecipherResult.error(ErrorCode.SERVICE_UNAVAILABLE);
        }

        CryptoKey senderPublicKey = null;
        CryptoBox cipher = null;
        KeyInfo keyInfo = null;
        KeyInfo cipherKeyInfo = null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(encrypted);
            BinaryDecoder decoder = new BinaryCompactDecoder(inputStream);

            // Extract information from the clear content are which is authenticated by the private key:
            // - version identifying the encryption method
            // - the nonce,
            // - salt used for the key generation,
            // - the optional sender twincode,
            // - either:
            //   1 => the twincode used for encryption (the receiver MUST know and trust that twincode),
            //   2 => the public key used for authenticate+encrypt.
            //   * => error
            final int version = decoder.readInt();
            final long nonceSequence = decoder.readLong();
            final byte[] salt = decoder.readBytes(null).array();
            final UUID twincodeId = decoder.readOptionalUUID();
            final int encryptionKey = decoder.readEnum();
            final UUID cipherTwincodeId;
            String pubEncryptionKey;
            final TrustMethod trustMethod;
            switch (encryptionKey) {
                case 1:
                    cipherTwincodeId = decoder.readUUID();

                    // Get the sender encryption key since we must know the twincode.
                    // That twincode is a Profile or Invitation and the associated public key should be trusted.
                    cipherKeyInfo = mServiceProvider.loadPeerEncryptionKey(cipherTwincodeId);
                    if (cipherKeyInfo == null) {
                        return DecipherResult.error(ErrorCode.NO_PUBLIC_KEY);
                    }
                    trustMethod = cipherKeyInfo.getTrustMethod();
                    pubEncryptionKey = cipherKeyInfo.getPublicEncryptionKey();
                    break;

                case 2:
                    // This invocation is made with a public key that is not yet trusted.
                    // The `trusted` flag gets propagated up to ProcessInvocation which could retrieve
                    // a peer's twincode with getSignedTwincode() and that twincode will not be trusted yet.
                    pubEncryptionKey = decoder.readOptionalString();
                    if (twincodeId != null) {
                        // Look if we trust the twincode.
                        cipherKeyInfo = mServiceProvider.loadPeerEncryptionKey(twincodeId);
                        if (cipherKeyInfo != null) {
                            trustMethod = cipherKeyInfo.getTrustMethod();
                        } else {
                            trustMethod = TrustMethod.NONE;
                        }
                    } else {
                        trustMethod = TrustMethod.NONE;
                    }
                    break;

                default:
                    return DecipherResult.error(ErrorCode.BAD_ENCRYPTION_FORMAT);
            }
            if (pubEncryptionKey == null) {
                return DecipherResult.error(ErrorCode.INVALID_PUBLIC_KEY);
            }
            switch (version) {
                case ENCRYPT_VERSION_ECDSA:
                    senderPublicKey = CryptoKey.importPublicKey(CryptoKey.Kind.ECDSA, pubEncryptionKey.getBytes(), true);
                    break;

                case ENCRYPT_VERSION_X25519:
                    senderPublicKey = CryptoKey.importPublicKey(CryptoKey.Kind.X25519, pubEncryptionKey.getBytes(), true);
                    break;

                default:
                    return DecipherResult.error(ErrorCode.BAD_SIGNATURE);
            }
            if (senderPublicKey == null) {
                return DecipherResult.error(ErrorCode.INVALID_PUBLIC_KEY);
            }
            final int authLength = encrypted.length - inputStream.available();

            // Get the receiver twincode private key.
            keyInfo = mServiceProvider.loadTwincodeKey(receiverTwincode);
            if (keyInfo == null) {
                return DecipherResult.error(ErrorCode.NO_PRIVATE_KEY);
            }
            final CryptoKey receiverPrivateKey = keyInfo.getEncryptionPrivateKey();
            if (receiverPrivateKey == null) {
                return DecipherResult.error(ErrorCode.NO_PRIVATE_KEY);
            }

            // Authenticate and decrypt the content.
            cipher = CryptoBox.create(CryptoBox.Kind.AES_GCM);
            int status = cipher.bind(false, receiverPrivateKey, senderPublicKey, salt);
            if (status != 1) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "bind failed with error " + status);
                }
                return DecipherResult.error(ErrorCode.NO_PRIVATE_KEY);
            }
            final byte[] data = new byte[encrypted.length];
            int len = cipher.decryptAEAD(nonceSequence, encrypted, authLength, data);
            if (len <= 0 || len > data.length) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "decrypt failed with error " + len);
                }
                return DecipherResult.error(ErrorCode.DECRYPT_ERROR);
            }

            // Extract the attributes from the decrypted content.
            inputStream = new ByteArrayInputStream(data, 0, len);
            decoder = new BinaryCompactDecoder(inputStream);
            final String pubSigningKey = decoder.readOptionalString();
            final int keyIndex = decoder.readInt();
            final byte[] secretKey = decoder.readOptionalBytes(null);
            final List<AttributeNameValue> result = decoder.readAttributes();
            if (result == null) {
                return DecipherResult.error(ErrorCode.BAD_ENCRYPTION_FORMAT);
            }
            return DecipherResult.ok(result, twincodeId, keyIndex, secretKey, pubSigningKey, trustMethod);

        } catch (Exception exception) {
            Log.e(LOG_TAG, "decrypt exception", exception);
            return DecipherResult.error(ErrorCode.LIBRARY_ERROR);

        } finally {
            if (senderPublicKey != null) {
                senderPublicKey.dispose();
            }
            if (cipherKeyInfo != null) {
                cipherKeyInfo.dispose();
            }
            if (keyInfo != null) {
                keyInfo.dispose();
            }
            if (cipher != null) {
                cipher.dispose();
            }
        }
    }

    @Override
    @NonNull
    public Pair<ErrorCode, Sdp> encrypt(@NonNull SessionKeyPair sessionKeyPair, @NonNull Sdp sdp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "encrypt: sessionKeyPair=" + sessionKeyPair + " sdp=" + sdp);
        }

        if (!isServiceReady()) {
            return new Pair<>(ErrorCode.SERVICE_UNAVAILABLE, null);
        }

        Pair<ErrorCode, Sdp> result = sessionKeyPair.encrypt(sdp);

        // When there is no more nonce sequence, we get the NoPrivateKey error and we must get a new
        // block of nonce sequences from the secret key pair in the database.
        if (result.first == ErrorCode.NO_PRIVATE_KEY && sessionKeyPair instanceof SessionSecretKeyPair) {
            mServiceProvider.refreshSession((SessionSecretKeyPair) sessionKeyPair);
            result = sessionKeyPair.encrypt(sdp);
        }
        return result;
    }

    @NonNull
    public Pair<ErrorCode, Sdp> decrypt(@Nullable SessionKeyPair sessionKeyPair, @NonNull Sdp sdp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "decrypt: keyPair=" + sessionKeyPair);
        }

        if (!isServiceReady()) {
            return new Pair<>(ErrorCode.SERVICE_UNAVAILABLE, null);
        }

        if (sessionKeyPair == null) {
            return sdp.isEncrypted() ? new Pair<>(ErrorCode.NO_PRIVATE_KEY, null) : new Pair<>(ErrorCode.SUCCESS, sdp);
        }

        return sessionKeyPair.decrypt(sdp);
    }

    //
    // Implement TwincodeOutboundService interface
    //

    @Override
    @NonNull
    public VerifyResult verify(@NonNull String publicKey, @NonNull UUID twincodeId,
                               @NonNull List<AttributeNameValue> attributes,
                               @NonNull byte[] signature) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verify: publicKey=" + publicKey + " twincodeId=" + twincodeId);
        }

        if (!isServiceOn()) {
            return VerifyResult.error(ErrorCode.SERVICE_UNAVAILABLE);
        }

        final CryptoKey.Kind kind = publicKey.length() >= CryptoKey.ECDSA_PUBKEY_LENGTH ? CryptoKey.Kind.ECDSA : CryptoKey.Kind.ED25519;
        final CryptoKey cryptoPublicKey = CryptoKey.importPublicKey(kind, publicKey.getBytes(), true);
        if (cryptoPublicKey == null) {
            return VerifyResult.error(ErrorCode.INVALID_PUBLIC_KEY);
        }

        try {
            return verify(kind, cryptoPublicKey, twincodeId, attributes, signature);

        } finally {
            cryptoPublicKey.dispose();
        }
    }

    @Override
    @NonNull
    public VerifyResult verify(@NonNull TwincodeOutbound twincodeOutbound,
                               @NonNull List<AttributeNameValue> attributes,
                               @NonNull byte[] signature) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verify: twincodeOutbound=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            return VerifyResult.error(ErrorCode.SERVICE_UNAVAILABLE);
        }

        final KeyInfo keyInfo = mServiceProvider.loadTwincodeKey(twincodeOutbound);
        if (keyInfo == null) {
            return VerifyResult.error(ErrorCode.NO_PUBLIC_KEY);
        }

        try {
            return verify(keyInfo.getSigningKind(), keyInfo.getSigningPublicKey(), twincodeOutbound.getId(), attributes, signature);

        } finally {
            keyInfo.dispose();
        }
    }

    @NonNull
    public Pair<ErrorCode, String> signAuthenticate(@NonNull TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "signAuthenticate twincodeOutbound=" + twincodeOutbound);
        }

        final KeyPair keyPair = mServiceProvider.loadKeyPair(twincodeOutbound);
        if (keyPair == null) {
            return new Pair<>(ErrorCode.ITEM_NOT_FOUND, null);
        }

        try {
            final CryptoKey privKey = keyPair.getPrivateKey();
            final CryptoKey pubKey = keyPair.getPublicKey();
            if (privKey == null || pubKey == null) {
                return new Pair<>(ErrorCode.NO_PRIVATE_KEY, null);
            }

            final String signature = privKey.signAuth(pubKey, twincodeOutbound.getId().toString(), keyPair.getPeerTwincodeId().toString());
            if (signature == null || signature.isEmpty()) {
                return new Pair<>(ErrorCode.BAD_SIGNATURE, null);
            }
            return new Pair<>(ErrorCode.SUCCESS, signature);

        } finally {
            keyPair.dispose();
        }
    }

    public Pair<ErrorCode, UUID> verifyAuthenticate(@NonNull String signature) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verifyAuthenticate signature=" + signature);
        }

        final byte[] rawKey = CryptoKey.extractAuthPublicKey(signature);
        if (rawKey == null) {
            return new Pair<>(ErrorCode.BAD_SIGNATURE, null);
        }

        final KeyPair keyPair = mServiceProvider.loadKeyPair(rawKey);
        if (keyPair == null) {
            return new Pair<>(ErrorCode.ITEM_NOT_FOUND, null);
        }

        CryptoKey pubKey = null;
        try {
            final CryptoKey privKey = keyPair.getPrivateKey();
            final CryptoKey peerPubKey = keyPair.getPublicKey();
            if (privKey == null || peerPubKey == null) {
                return new Pair<>(ErrorCode.NO_PRIVATE_KEY, null);
            }
            final byte[] pubKeyData = privKey.getPublicKey(false);
            if (pubKeyData == null) {
                return new Pair<>(ErrorCode.NO_PRIVATE_KEY, null);
            }
            pubKey = CryptoKey.importPublicKey(CryptoKey.Kind.ED25519, pubKeyData, false);
            if (pubKey == null) {
                return new Pair<>(ErrorCode.NO_PRIVATE_KEY, null);
            }

            // The signature could be created with the peer's private key or our own private key.
            // Check for the peer's signature, otherwise check for our signature.
            int result = peerPubKey.verifyAuth(pubKey, keyPair.getTwincodeId().toString(),
                    keyPair.getPeerTwincodeId().toString(), signature);
            if (result != 1) {
                // Check for our own signature, if it's valid we must not change the trust method.
                result = pubKey.verifyAuth(peerPubKey, keyPair.getTwincodeId().toString(),
                        keyPair.getPeerTwincodeId().toString(), signature);
            }
            if (result != 1) {
                return new Pair<>(ErrorCode.BAD_SIGNATURE, null);
            }
            return new Pair<>(ErrorCode.SUCCESS, keyPair.getSubjectId());

        } finally {
            keyPair.dispose();
            if (pubKey != null) {
                pubKey.dispose();
            }
        }
    }

    //
    // Internal methods.
    //

    private static final int ENCRYPT_VERSION_ECDSA = 1;
    private static final int ENCRYPT_VERSION_X25519 = 2;
    private static final int SIGNATURE_VERSION_ECDSA = 1;
    private static final int SIGNATURE_VERSION_ED25519 = 2;
    private static final int MAX_ATTRIBUTES = 64;
    private static final String[] PREDEFINED_LIST = {
            Twincode.NAME,
            Twincode.DESCRIPTION,
            Twincode.CAPABILITIES,
            Twincode.AVATAR_ID
    };

    /**
     * Internal method to create the private keys when a new twincode is created.
     *
     * @param transaction the database transaction.
     * @param twincodeInbound the twincode inbound instance.
     * @param twincodeOutbound the twincode outbound instance.
     * @throws DatabaseException when some database error occurred.
     */
    public void createPrivateKey(@NonNull Transaction transaction,
                                 @NonNull TwincodeInbound twincodeInbound,
                                 @NonNull TwincodeOutbound twincodeOutbound) throws DatabaseException {
        if (DEBUG) {
            Log.d(LOG_TAG, "createPrivateKey: twincodeInbound=" + twincodeInbound + " twincodeOutbound=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            return;
        }

        mServiceProvider.insertKey(transaction, twincodeOutbound, KeyInfo.KEY_TYPE_25519);
    }

    /**
     * Internal method to create the private keys when a new twincode is created.
     *
     * @param twincodeInbound the twincode inbound instance.
     * @param twincodeOutbound the twincode outbound instance.
     * @return SUCCESS if private keys exists or have been created.
     */
    @NonNull
    public ErrorCode createPrivateKey(@NonNull TwincodeInbound twincodeInbound,
                                      @NonNull TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createPrivateKey: twincodeInbound=" + twincodeInbound + " twincodeOutbound=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        return mServiceProvider.insertKey(twincodeOutbound, KeyInfo.KEY_TYPE_25519);
    }

    public void saveSecretKey(@NonNull TwincodeOutbound twincodeOutbound, @NonNull TwincodeOutbound peerTwincodeOutbound,
                              @NonNull byte[] secretKey, int keyIndex) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveSecretKey twincodeOutbound=" + twincodeOutbound + " peerTwincodeOutbound=" + peerTwincodeOutbound
                    + " keyIndex=" + keyIndex);
        }

        mServiceProvider.saveSecretKey((TwincodeOutboundImpl) twincodeOutbound, (TwincodeOutboundImpl) peerTwincodeOutbound, secretKey, keyIndex);
    }

    public void validateSecrets(@NonNull TwincodeOutbound twincodeOutbound, @NonNull TwincodeOutbound peerTwincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "validateSecrets twincodeOutbound=" + twincodeOutbound + " peerTwincodeOutbound=" + peerTwincodeOutbound);
        }

        mServiceProvider.validateSecrets((TwincodeOutboundImpl) twincodeOutbound, (TwincodeOutboundImpl) peerTwincodeOutbound);
    }

    /**
     * Verify the signature of the twincode attributes.
     *
     * @param crypto the public key allowing to verify the signature.
     * @param twincodeId the twincode UUID.
     * @param attributes the attributes being signed.
     * @param signature the received signature.
     * @return SUCCESS if the signature is valid or an error code.
     */
    @NonNull
    private static VerifyResult verify(@NonNull CryptoKey.Kind kind, @Nullable CryptoKey crypto,
                                       @NonNull UUID twincodeId,
                                       @NonNull List<AttributeNameValue> attributes,
                                       @NonNull byte[] signature) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verify crypto=" + crypto + " twincodeId=" + twincodeId);
        }

        if (crypto == null) {
            return VerifyResult.error(ErrorCode.NO_PUBLIC_KEY);
        }

        final byte[] pubKey = crypto.getPublicKey(false);
        if (pubKey == null) {
            return VerifyResult.error(ErrorCode.INVALID_PUBLIC_KEY);
        }
        try {
            final int expectVersion;
            switch (kind) {
                case ECDSA:
                    expectVersion = SIGNATURE_VERSION_ECDSA;
                    break;

                case ED25519:
                    expectVersion = SIGNATURE_VERSION_ED25519;
                    break;

                default:
                    return VerifyResult.error(ErrorCode.INVALID_PRIVATE_KEY);
            }

            // Step 1: extract the ECDSA/ED25519 signature, the expected avatar SHA and number of attributes.
            final ByteArrayInputStream is = new ByteArrayInputStream(signature);
            final BinaryDecoder decoder = new BinaryCompactDecoder(is);
            final int version = decoder.readInt();
            if (version != expectVersion) {
                return VerifyResult.error(ErrorCode.BAD_SIGNATURE_FORMAT);
            }

            final byte[] ecdsaSignature = decoder.readBytes(null).array();
            final byte[] avatarSha = decoder.readOptionalBytes(null);
            final byte[] encryptionPubKey = decoder.readOptionalBytes(null);
            int attributeCount = decoder.readInt();
            if (attributeCount < 0 || attributeCount > MAX_ATTRIBUTES) {
                return VerifyResult.error(ErrorCode.BAD_SIGNATURE_FORMAT);
            }

            // Step 2: build the data to be verified.
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);
            final List<AttributeNameValue> signAttributes = new ArrayList<>(attributes);

            encoder.writeInt(expectVersion);
            encoder.writeUUID(twincodeId);
            for (String name : PREDEFINED_LIST) {
                final AttributeNameValue attribute = AttributeNameListValue.removeAttribute(signAttributes, name);
                if (attribute instanceof AttributeNameStringValue) {
                    encoder.writeOptionalString((String) attribute.value);
                } else if (attribute instanceof AttributeNameUUIDValue) {
                    encoder.writeOptionalUUID((UUID) attribute.value);
                } else if (attribute instanceof AttributeNameImageIdValue) {
                    encoder.writeOptionalUUID(((ExportedImageId) attribute.value).getExportedId());
                } else {
                    encoder.writeOptionalString(null);
                }
            }

            encoder.writeOptionalBytes(avatarSha);
            encoder.writeOptionalBytes(encryptionPubKey);
            encoder.writeInt(attributeCount);
            while (attributeCount > 0) {
                attributeCount--;
                String name = decoder.readString();
                AttributeNameValue attribute = AttributeNameListValue.removeAttribute(signAttributes, name);
                if (attribute == null) {
                    // This attribute is not found, no need to proceed: it is invalid, they must be present.
                    return VerifyResult.error(ErrorCode.BAD_SIGNATURE_MISS_ATTRIBUTE);
                }
                encoder.writeAttribute(attribute);
            }
            if (!signAttributes.isEmpty()) {
                // We still have some attributes to be signed: it is invalid they should have been removed.
                return VerifyResult.error(ErrorCode.BAD_SIGNATURE_NOT_SIGNED_ATTRIBUTE);
            }

            final byte[] data = outputStream.toByteArray();
            final int len = crypto.verify(data, ecdsaSignature, false);
            if (len != 1) {
                return VerifyResult.error(ErrorCode.BAD_SIGNATURE);
            }
            return VerifyResult.ok(encryptionPubKey, pubKey, avatarSha);

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "verify exception", exception);
            }
            return VerifyResult.error(ErrorCode.LIBRARY_ERROR);
        }
    }
}
