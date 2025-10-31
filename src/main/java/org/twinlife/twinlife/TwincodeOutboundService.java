/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

@SuppressWarnings("unused")
public interface TwincodeOutboundService extends BaseService<TwincodeOutboundService.ServiceObserver> {

    String VERSION = "2.2.0";

    long LONG_REFRESH_PERIOD = 24 * 3600 * 1000; // 1 day (ms)

    long REFRESH_PERIOD = 3600 * 1000; // 1 hour (ms)

    long NO_REFRESH_PERIOD = 0;

    // Invoke the twincode and try to wakeup the peer immediately.
    int INVOKE_URGENT = 0x01;

    // Invoke the twincode with a persistent wakeup.
    int INVOKE_WAKEUP = 0x02;

    // Create a secret to send in the secureInvokeTwincode() unless it already exists for the twincode pair.
    // This option allows to have a secureInvokeTwincode() that is idempotent: we can repeat it several times
    // and the peer will always perform the same operation.  This is important for a contact creation but also
    // for the group join invocation.  It is possible that such secureInvokeTwincode are executed several times
    // due to retries or interruptions.  This implies SEND_SECRET.
    int CREATE_SECRET = 0x04;

    // Create a new secret to send in the secureInvokeTwincode().  This implies SEND_SECRET.
    int CREATE_NEW_SECRET = 0x08;

    // Send the current secret in the secureInvokeTwincode().
    int SEND_SECRET = 0x010;

    class TwincodeOutboundServiceConfiguration extends BaseServiceConfiguration {

        public TwincodeOutboundServiceConfiguration() {

            super(BaseServiceId.TWINCODE_OUTBOUND_SERVICE_ID, VERSION, false);
        }
    }

    interface ServiceObserver extends BaseService.ServiceObserver {

        default void onRefreshTwincode(@NonNull TwincodeOutbound twincodeOutbound,
                                       @NonNull List<AttributeNameValue> previousAttributes) {}
    }

    void getTwincode(@NonNull UUID twincodeOutboundId, long refreshPeriod, @NonNull Consumer<TwincodeOutbound> complete);

    /**
     * Get the twincode signed by the public key and verify the attribute signatures when we get it from the server.
     * This operation is not cached and requires a round-trip to the server.
     *
     * @param twincodeOutboundId the twincode id to get.
     * @param publicKey the public key encoded in Base64url.
     * @param trust whether the public key is trusted and how (ie, received or validated through an external channel).
     * @param complete completion handler called.
     */
    void getSignedTwincode(@NonNull UUID twincodeOutboundId, @NonNull String publicKey, @NonNull TrustMethod trust, @NonNull Consumer<TwincodeOutbound> complete);

    /**
     * Get the twincode signed by the public key, verify the attribute signatures when we get it from the server,
     * and if everything is correct save the secret key with the given key index.
     * This operation is not cached and requires a round-trip to the server.
     *
     * @param twincodeOutboundId the twincode id to get.
     * @param publicKey the public key encoded in Base64url.
     * @param keyIndex the key index associated with the optional secret (1 or 2).
     * @param secretKey the optional secret key provided by the peer to decrypt its messages.
     * @param trust whether the public key is trusted and how (ie, received or validated through an external channel).
     * @param complete completion handler called.
     */
    void getSignedTwincodeWithSecret(@NonNull UUID twincodeOutboundId, @NonNull String publicKey, int keyIndex, @Nullable byte[] secretKey,
                                     @NonNull TrustMethod trust, @NonNull Consumer<TwincodeOutbound> complete);

    /**
     * Refresh the twincode by getting the attributes from the server, checking that the twincode is still valid
     * and identify the attributes that have been modified.  The updated attributes are available on the TwincodeOutbound
     * instance while the previous values are passed to the lambda method.
     *
     * @param twincodeOutbound the twincode to refresh.
     * @param complete the completion handler called with the previous attributes.
     */
    void refreshTwincode(@NonNull TwincodeOutbound twincodeOutbound, @NonNull Consumer<List<AttributeNameValue>> complete);

    void updateTwincode(@NonNull TwincodeOutbound twincodeOutbound, @NonNull List<AttributeNameValue> attributes,
                        @Nullable List<String> deleteAttributeNames, @NonNull Consumer<TwincodeOutbound> complete);

    void createInvitationCode(@NonNull TwincodeOutbound twincodeOutbound, int validityPeriod, @NonNull Consumer<InvitationCode> complete);

    void getInvitationCode(@NonNull String code, @NonNull Consumer<Pair<TwincodeOutbound, String>> complete);

    /**
     * Create a URI for the twincode by using the given URI prefix.
     *
     * @param kind the kind of URI to create.
     * @param twincodeOutbound the twincode to use.
     * @param complete the completion handler called with the URI string.
     */
    void createURI(@NonNull TwincodeURI.Kind kind, @NonNull TwincodeOutbound twincodeOutbound,
                   @NonNull Consumer<TwincodeURI> complete);

    /**
     * Parse the URI and find the twincode information described by that URI.
     *
     * @param uri the uri to parse.
     * @param complete the completion handler with the twincode information when extraction is successful.
     */
    void parseURI(@NonNull Uri uri, @NonNull Consumer<TwincodeURI> complete);

    /**
     * Invoke an action on the outbound twincode with a set of attributes.  Call back the completion
     * handler with the invocation id or an error code.  The invoke action is received by the peer
     * on the inbound twincode and managed by the TwincodeInboundService.
     *
     * @param targetTwincodeOutbound the outbound twincode to invoke.
     * @param options the options to control the invocation.
     * @param action the action to invoke.
     * @param attributes the attributes to give for the invocation.
     * @param complete the completion handler.
     */
    void invokeTwincode(@NonNull TwincodeOutbound targetTwincodeOutbound, int options, @NonNull String action,
                        @Nullable List<AttributeNameValue> attributes, @NonNull Consumer<UUID> complete);

    /**
     * Invoke an action on the outbound twincode with a set of attributes.  Call back the completion
     * handler with the invocation id or an error code.  The invoke action is received by the peer
     * on the inbound twincode and managed by the TwincodeInboundService.
     *
     * @param cipherTwincode the twincode used to sign and encrypt the invocation.
     * @param senderTwincode the outbound twincode doing the invocation.
     * @param receiverTwincode the outbound twincode to invoke.
     * @param options the options to control the invocation.
     * @param action the action to invoke.
     * @param attributes the attributes to give for the invocation (must contain at least one attribute!).
     * @param complete the completion handler.
     */
    void secureInvokeTwincode(@NonNull TwincodeOutbound cipherTwincode,
                              @NonNull TwincodeOutbound senderTwincode,
                              @NonNull TwincodeOutbound receiverTwincode,
                              int options, @NonNull String action,
                              @NonNull List<AttributeNameValue> attributes, @NonNull Consumer<UUID> complete);

    /**
     * Create a private key for the twincode inbound and twincode outbound.
     * Note: we force to specify a twincode inbound to enforce the availability of the inbound
     * and link with the outbound twincode.
     *
     * @param twincodeInbound the twincode inbound.
     * @param complete the completion handler.
     */
    void createPrivateKey(@NonNull TwincodeInbound twincodeInbound,
                          @NonNull Consumer<TwincodeOutbound> complete);

    /**
     * Associate the two twincodes so that the secret used to encrypt our SDPs is specific to the peer twincode.
     * The secret was associated with `previousPeerTwincodeOutbound` and we want to associate it with another twincode.
     * The secret was sent through a `secureInvokeTwincode()` to a first twincode (a profile or a Contact conversation)
     * and we will communicate by using another twincode that we have received after (the contact peer or the group member
     * that invited us).
     *
     * @param twincodeOutbound our twincode.
     * @param previousPeerTwincodeOutbound the previous peer twincode.
     * @param peerTwincodeOutbound the peer twincode.
     */
    void associateTwincodes(@NonNull TwincodeOutbound twincodeOutbound,
                            @Nullable TwincodeOutbound previousPeerTwincodeOutbound,
                            @NonNull TwincodeOutbound peerTwincodeOutbound);

    /**
     * Mark the two twincode relation as certified by the given trust process.
     *
     * @param twincodeOutbound our twincode.
     * @param peerTwincodeOutbound the peer twincode.
     * @param trustMethod the certification method that was used.
     */
    void setCertified(@NonNull TwincodeOutbound twincodeOutbound,
                      @NonNull TwincodeOutbound peerTwincodeOutbound,
                      @NonNull TrustMethod trustMethod);

    String getPeerId(@NonNull UUID peerTwincodeOutboundId, @NonNull UUID twincodeOutboundId);

    void evictTwincode(@NonNull UUID twincodeOutboundId);
    void evictTwincodeOutbound(@NonNull TwincodeOutbound twincodeOutbound);
}
