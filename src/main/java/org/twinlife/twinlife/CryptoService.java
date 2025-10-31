/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public interface CryptoService extends BaseService<BaseService.ServiceObserver> {

    String VERSION = "1.0.0";

    int USE_SECRET1 = 0x01;
    int USE_SECRET2 = 0x02;
    int NEW_SECRET1 = 0x10;
    int NEW_SECRET2 = 0x20;

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
    VerifyResult verify(@NonNull String publicKey, @NonNull UUID twincodeId,
                        @NonNull List<AttributeNameValue> attributes,
                        @NonNull byte[] signature);
    @NonNull
    VerifyResult verify(@NonNull TwincodeOutbound twincodeOutbound,
                        @NonNull List<AttributeNameValue> attributes,
                        @NonNull byte[] signature);

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
        public final String publicKey;
        @NonNull
        public final TrustMethod trustMethod;

        @NonNull
        public static DecipherResult error(@NonNull ErrorCode errorCode) {

            return new DecipherResult(errorCode, null, null, 0, null, null, TrustMethod.NONE);
        }

        @NonNull
        public static DecipherResult ok(@Nullable List<AttributeNameValue> attributes, @Nullable UUID peerTwincodeId, int keyIndex,
                                        @Nullable byte[] secretKey, @Nullable String publicKey, @NonNull TrustMethod trustMethod) {

            return new DecipherResult(ErrorCode.SUCCESS, attributes, peerTwincodeId, keyIndex, secretKey, publicKey, trustMethod);
        }

        private DecipherResult(@NonNull ErrorCode errorCode, @Nullable List<AttributeNameValue> attributes,
                               @Nullable UUID peerTwincodeId, int keyIndex, @Nullable byte[] secretKey,
                               @Nullable String publicKey, @NonNull TrustMethod trustMethod) {
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
