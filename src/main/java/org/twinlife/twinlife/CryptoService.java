/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.util.Utils;

import java.util.List;
import java.util.UUID;

public interface CryptoService extends BaseService<BaseService.ServiceObserver> {

    String VERSION = "1.0.0";

    int USE_SECRET1 = 0x01;
    int USE_SECRET2 = 0x02;
    int NEW_SECRET1 = 0x10;
    int NEW_SECRET2 = 0x20;

    class PublicKeyData {
        @NonNull
        public final Object publicKey;

        public byte[] asBytes() {
            return publicKey instanceof byte[] ? (byte[])publicKey : Utils.decodeBase64URL(publicKey.toString());
        }

        @Nullable
        public String asString() {
            return publicKey instanceof String ? (String) publicKey : Utils.encodeBase64URL((byte[])publicKey);
        }

        public boolean isEmpty() {
            return publicKey instanceof byte[] ? ((byte[])publicKey).length == 0 : asString() == null;
        }

        public static PublicKeyData create(@Nullable String publicKey) {
            return publicKey == null ? null : new PublicKeyData(publicKey);
        }

        public static PublicKeyData create(@Nullable byte[] publicKey) {
            return publicKey == null ? null : new PublicKeyData(publicKey);
        }

        private PublicKeyData(@NonNull String publicKey) {
            this.publicKey = publicKey;
        }
        private PublicKeyData(@NonNull byte[] publicKey) {
            this.publicKey = publicKey;
        }
    }

    class CryptoServiceServiceConfiguration extends BaseServiceConfiguration {

        public CryptoServiceServiceConfiguration() {

            super(BaseServiceId.CRYPTO_SERVICE_ID, VERSION, false);
        }
    }

    class VerifyResult {
        public final ErrorCode errorCode;
        public final byte[] publicSigningKey;
        public final byte[] publicEncryptionKey;
        public final byte[] imageSha;

        @NonNull
        public static VerifyResult error(@NonNull ErrorCode errorCode) {

            return new VerifyResult(errorCode, null, null, null);
        }

        @NonNull
        public static VerifyResult ok(@Nullable byte[] publicEncryptionKey, @NonNull byte[] publicSigningKey, @Nullable byte[] imageSha) {

            return new VerifyResult(ErrorCode.SUCCESS, publicEncryptionKey, publicSigningKey, imageSha);
        }

        private VerifyResult(ErrorCode errorCode, byte[] publicEncryptionKey, byte[] publicSigningKey, byte[] imageSha) {
            this.errorCode = errorCode;
            this.publicEncryptionKey = publicEncryptionKey;
            this.publicSigningKey = publicSigningKey;
            this.imageSha = imageSha;
        }
    }

    /**
     * Get the public key encoded in Base64url associated with the twincode.
     *
     * @param twincodeOutbound the twincode.
     * @return the Base64 public key or null if it does not exist.
     */
    @Nullable
    String getPublicKey(@NonNull TwincodeOutbound twincodeOutbound);

    /**
     * Get the public key as raw content.
     *
     * @param twincodeOutbound the twincode.
     * @return the public key or null if it does not exist.
     */
    @Nullable
    byte[] getRawPublicKey(@NonNull TwincodeOutbound twincodeOutbound);

    /**
     * Sign the twincode attributes by using the twincode private key.
     *
     * @param twincodeOutbound the twincode to use.
     * @param attributes       the list of attributes to sign.
     * @return the signature that can be sent to the server and can be verified
     * by clients with verify().
     */
    @Nullable
    byte[] sign(@NonNull TwincodeOutbound twincodeOutbound,
                @NonNull List<AttributeNameValue> attributes);

    /**
     * Verify the signature of the twincode attributes by using the public key encoded in Base64url
     * or by using the public key already associated with the twincodeOutbound object.
     *
     * @param publicKey the public key encoded in Base64url.
     * @param twincodeId the twincode outbound id that signed the attributes.
     * @param attributes the list of attributes.
     * @param signature the signature to verify.
     * @return the object giving the verification status as well as the SHA for images used by
     * the twincode (that SHA is extracted from the signature) and the public encryption key if there is one.
     */
    @NonNull
    VerifyResult verify(@NonNull PublicKeyData publicKey, @NonNull UUID twincodeId,
                        @NonNull List<AttributeNameValue> attributes,
                        @NonNull byte[] signature);
    @NonNull
    VerifyResult verify(@NonNull TwincodeOutbound twincodeOutbound,
                        @NonNull List<AttributeNameValue> attributes,
                        @NonNull byte[] signature);

    /**
     * Sign the content with the twincode private signing key.
     *
     * @param twincodeOutbound the twincode used to sign.
     * @param content the content to sign.
     * @return the base64URL signature or null if there is a problem.
     */
    @Nullable
    String signContent(@NonNull TwincodeOutbound twincodeOutbound, @NonNull byte[] content);

    /**
     * Sign the content with the twincode private signing key.
     *
     * @param twincodeOutbound the twincode used to sign.
     * @param content the content to sign.
     * @return the base64URL signature or null if there is a problem.
     */
    @Nullable
    byte[] signContentRaw(@NonNull TwincodeOutbound twincodeOutbound, @NonNull byte[] content);

    /**
     * Verify the signature of the given content with the twincode public key.
     *
     * @param twincodeOutbound the twincode used to sign.
     * @param content the content to sign.
     * @param signature the signature to verify.
     * @return SUCCESS if the signature is valid or an error code.
     */
    @NonNull
    ErrorCode verifyContent(@NonNull TwincodeOutbound twincodeOutbound, @NonNull byte[] content,
                            @NonNull String signature);
    /**
     * Verify the signature of the given content with the twincode public key.
     *
     * @param keyId the key identifier that was used to sign.
     * @param publicKey the public key that was used (Ed25519).
     * @param content the content to sign.
     * @param signature the signature to verify.
     * @return SUCCESS if the signature is valid or an error code.
     */
    @NonNull
    ErrorCode verifyContent(@NonNull UUID keyId, @NonNull PublicKeyData publicKey, @NonNull byte[] content, @NonNull byte[] signature);

    class CipherResult {
        @NonNull
        public final ErrorCode errorCode;
        @Nullable
        public final byte[] data;
        public final int length;

        @NonNull
        public static CipherResult error(@NonNull ErrorCode errorCode) {

            return new CipherResult(errorCode, null, 0);
        }

        @NonNull
        public static CipherResult ok(@Nullable byte[] data, int length) {

            return new CipherResult(ErrorCode.SUCCESS, data, length);
        }

        private CipherResult(@NonNull ErrorCode errorCode, @Nullable byte[] data, int length) {
            this.errorCode = errorCode;
            this.data = data;
            this.length = length;
        }
    }

    class DecipherResult {
        @NonNull
        public final ErrorCode errorCode;
        @Nullable
        public final List<AttributeNameValue> attributes;
        @Nullable
        public final UUID peerTwincodeId;
        public final int keyIndex;
        @Nullable
        public final byte[] secretKey;
        @Nullable
        public final PublicKeyData publicKey;
        @NonNull
        public final TrustMethod trustMethod;

        @NonNull
        public static DecipherResult error(@NonNull ErrorCode errorCode) {

            return new DecipherResult(errorCode, null, null, 0, null, null, TrustMethod.NONE);
        }

        @NonNull
        public static DecipherResult ok(@Nullable List<AttributeNameValue> attributes, @Nullable UUID peerTwincodeId, int keyIndex,
                                        @Nullable byte[] secretKey, @Nullable PublicKeyData publicKey, @NonNull TrustMethod trustMethod) {

            return new DecipherResult(ErrorCode.SUCCESS, attributes, peerTwincodeId, keyIndex, secretKey, publicKey, trustMethod);
        }

        private DecipherResult(@NonNull ErrorCode errorCode, @Nullable List<AttributeNameValue> attributes,
                               @Nullable UUID peerTwincodeId, int keyIndex, @Nullable byte[] secretKey,
                               @Nullable PublicKeyData publicKey, @NonNull TrustMethod trustMethod) {
            this.errorCode = errorCode;
            this.attributes = attributes;

            this.peerTwincodeId = peerTwincodeId;
            this.keyIndex = keyIndex;
            this.secretKey = secretKey;
            this.publicKey = publicKey;
            this.trustMethod = trustMethod;
        }
    }

    /**
     * Encrypt by using the encryption keys defined for the `cipherTwincode` for a message to the
     * `targetTwincode`.  Give in the message the public keys used by the `senderTwincode`
     * (which can be the `cipherTwincode`).
     *
     * @param cipherTwincode the twincode used for encryption.
     * @param senderTwincode the twincode to get the keys and add in the message
     * @param targetTwincode the twincode that will receive the message (to get its public key).
     * @param options options to control the creation or re-creation of secret associated with (senderTwincode, targetTwincode).
     * @param attributes the list of attributes to protect (must contain at least one attribute).
     * @return the encryption result with the binary data representing the message to send.
     */
    @NonNull
    CipherResult encrypt(@NonNull TwincodeOutbound cipherTwincode,
                         @NonNull TwincodeOutbound senderTwincode,
                         @NonNull TwincodeOutbound targetTwincode,
                         int options,
                         @NonNull List<AttributeNameValue> attributes);

    /**
     * Decrypt and authenticate the message received by using the private key associated with the twincode.
     *
     * @param receiverTwincode the twincode that received the encrypted message.
     * @param encrypted the data to decrypt.
     * @return the decryption result with the list of attributes when successful.
     */
    @NonNull
    DecipherResult decrypt(@NonNull TwincodeOutbound receiverTwincode,
                           @NonNull byte[] encrypted);

    @NonNull
    Pair<ErrorCode, SessionKeyPair> createSession(@NonNull UUID sessionId, @NonNull TwincodeOutbound twincodeOutbound,
                                                  @Nullable TwincodeOutbound peerTwincodeOutbound, boolean strict);

    @NonNull
    Pair<ErrorCode, Sdp> encrypt(@NonNull SessionKeyPair keyPair, @NonNull Sdp sdp);

    @NonNull
    Pair<ErrorCode, Sdp> decrypt(@Nullable SessionKeyPair keyPair, @NonNull Sdp sdp);
}
