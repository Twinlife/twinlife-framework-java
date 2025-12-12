/*
 *  Copyright (c) 2015-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

public enum TerminateReason {
    BUSY,
    CANCEL,
    CONNECTIVITY_ERROR,
    DECLINE,
    DISCONNECTED,
    GENERAL_ERROR,
    GONE,
    NOT_AUTHORIZED,
    SUCCESS,
    REVOKED,
    TIMEOUT,
    TRANSFER_DONE,
    SCHEDULE,
    MERGE,
    UNKNOWN,
    // Specific errors raised for encryption/decryption of SDPs.
    NOT_ENCRYPTED,
    NO_PUBLIC_KEY,
    NO_PRIVATE_KEY,
    NO_SECRET_KEY,
    DECRYPT_ERROR,
    ENCRYPT_ERROR;

    @Override
    @NonNull
    public String toString() {

        switch (this) {
            case BUSY:
                return "busy";

            case CANCEL:
                return "cancel";

            case CONNECTIVITY_ERROR:
                return "connectivity-error";

            case DECLINE:
                return "decline";

            case DISCONNECTED:
                return "disconnected";

            case GENERAL_ERROR:
                return "general-error";

            case GONE:
                return "gone";

            case REVOKED:
                return "revoked";

            case SUCCESS:
                return "success";

            case TIMEOUT:
                return "expired";

            case NOT_AUTHORIZED:
                return "not-authorized";

            case TRANSFER_DONE:
                return "transfer-done";

            case SCHEDULE:
                return "schedule";

            case MERGE:
                return "merge";

            case NOT_ENCRYPTED:
                return "not-encrypted";

            case NO_PUBLIC_KEY:
                return "no-public-key";

            case NO_PRIVATE_KEY:
                return "no-private-key";

            case NO_SECRET_KEY:
                return "no-secret-key";

            case DECRYPT_ERROR:
                return "decrypt-error";

            case ENCRYPT_ERROR:
                return "encrypt-error";

            case UNKNOWN:
            default:
                return "unknown";
        }
    }

    public static TerminateReason fromString(@NonNull String value) {
        switch (value) {
            case "busy":
                return BUSY;

            case "cancel":
                return CANCEL;

            case "connectivity-error":
                return CONNECTIVITY_ERROR;

            case "decline":
                return DECLINE;

            case "disconnected":
                return DISCONNECTED;

            case "general-error":
                return GENERAL_ERROR;

            case "gone":
                return GONE;

            case "not-authorized":
                return NOT_AUTHORIZED;

            case "success":
                return SUCCESS;

            case "revoked":
                return REVOKED;

            case "expired":
                return TIMEOUT;

            case "transfer-done":
                return TRANSFER_DONE;

            case "merge":
                return MERGE;

            default:
                return UNKNOWN;
        }
    }

    @NonNull
    public static TerminateReason fromErrorCode(@NonNull BaseService.ErrorCode errorCode) {

        /*
         * Errors returned by createIncomingPeerConnection():
         * - ITEM_NOT_FOUND: P2P session not found
         * - NO_PUBLIC_KEY: No session key pair to decrypt the encrypted IQ, twincode.isEncrypted() && !peer.isEncrypted()
         * - NOT_ENCRYPTED: the P2P session must be encrypted and we received an unencrypted SDP
         *
         * Errors returned by onSessionAccept(), onSessionUpdate(), onTransportInfo():
         * - ITEM_NOT_FOUND: P2P session not found
         * - BAD_REQUEST: Decrypted SDP is empty or null.
         * - NO_PRIVATE_KEY: No session key pair to decrypt the encrypted IQ
         * - BAD_SIGNATURE: Invalid session ID in encrypted payload
         * - NO_SECRET_KEY: Used secret key is not known to decrypt
         * - DECRYPT_ERROR: Decrypt error
         * - BAD_ENCRYPTION_FORMAT: exception raised when deserializing the decrypted data.
         * - LIBRARY_ERROR: Internal exception raised.
         */
        switch (errorCode) {
            case SUCCESS:
                return SUCCESS;

            case INVALID_PUBLIC_KEY:
            case INVALID_PRIVATE_KEY:
            case NO_PRIVATE_KEY:
                return NO_PRIVATE_KEY;

            case NO_PUBLIC_KEY:
                return NO_PUBLIC_KEY;

            case NO_SECRET_KEY:
                return NO_SECRET_KEY;

            case NOT_ENCRYPTED:
                return NOT_ENCRYPTED;

            case ENCRYPT_ERROR:
                return ENCRYPT_ERROR;

            case BAD_SIGNATURE:
            case BAD_SIGNATURE_FORMAT:
            case BAD_SIGNATURE_MISS_ATTRIBUTE:
            case BAD_SIGNATURE_NOT_SIGNED_ATTRIBUTE:
            case BAD_ENCRYPTION_FORMAT:
            case DECRYPT_ERROR:
                return DECRYPT_ERROR;

            case SERVICE_UNAVAILABLE:
                // If we are suspending, the service will return the unavailable status
                // and must map it to DISCONNECTED to inform the peer that we are not connected.
                return DISCONNECTED;

            default:
                return UNKNOWN;
        }
    }
}
