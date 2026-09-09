/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.UUID;

/**
 * Secure Roster service is used for groups and community spaces to represent members.
 *
 * - the secure roster is protected by a list of public keys which are used to sign other public keys or members,
 * - a secure roster key is protected by signing '{rosterId, keyId, publicKeyId}',
 * - the first secure roster key is signed by itself and the keyId is specified at the creation,
 * - each member are protected by signing '{rosterId, memberTwincodeId, memberPermission, publicKey}'
 * - a member is valid only when a valid secure roster key exists and verifies the member's signature.
 *   The device must enforce this rule (to make sure the server does not add spying members).
 *   The server also verifies this rule before insertion.
 */
public interface SecureRosterService extends BaseService<BaseService.ServiceObserver> {

    String VERSION = "1.0.0";

    int ALLOW_EMPTY_KEY = 0x01;

    class SecureRosterServiceConfiguration extends BaseServiceConfiguration {

        public SecureRosterServiceConfiguration() {

            super(BaseServiceId.valueOf("SECURE_ROSTER_SERVICE_ID"), VERSION, false);
        }
    }

    /**
     * Create a secure roster with the configuration options and the given roster schema id which
     * indicates the target purpose of the secure roster.
     * Note: the public key is saved but it is not validated, hence it cannot accept new members.
     * The public key must be updated by using setRosterPublicKey().
     *
     * @param createOptions the secure roster create options.
     * @param rosterSchemaId the secure roster schema ID.
     * @param publicKeyId the root public key ID.
     * @param publicKey the root public key (Ed25519).
     * @param complete the completion handler executed when the operation completes.
     */
    void createRoster(int createOptions, @NonNull UUID rosterSchemaId, @NonNull UUID publicKeyId, @NonNull byte[] publicKey,
                      @NonNull Consumer<RosterId> complete);
    void createRoster(int createOptions, @NonNull UUID rosterSchemaId, @NonNull TwincodeOutbound signingTwincode,
                      @NonNull Consumer<RosterId> complete);

    /**
     * List the secure roster members and public keys used to protect them.  Members are grouped
     * per signing public key to help in the verification.  Public keys are ordered so that its
     * signing key has always a lower index.  The first key is self signed.  It is assumed that
     * the caller trusts that first key (and it must therefore verify that it matches the trusted key).
     * This operation is accepted if:
     * - the device has created the secure roster,
     * - the device is member of the secure roster.
     *
     * @param rosterId the secure roster ID and roster schema ID.
     * @param afterCreationTime list only secure roster members created after the given timestamp.
     * @param complete the completion handler executed when the operation completes.
     */
    void listRoster(@NonNull RosterId rosterId, long afterCreationTime, @NonNull Consumer<SecureRoster> complete);

    final class MemberIdentity {
        public final UUID memberTwincodeId;
        public final Permission memberPermission;
        public final CryptoService.PublicKeyData memberPublicKey;

        public MemberIdentity(@NonNull UUID memberTwincodeId, @NonNull Permission memberPermission, @NonNull CryptoService.PublicKeyData memberPublicKey) {
            this.memberTwincodeId = memberTwincodeId;
            this.memberPermission = memberPermission;
            this.memberPublicKey = memberPublicKey;
        }
    }

    /**
     * Add or update a member in the secure roster.  The new member is signed by using the private
     * key associated with the twincode.  We assume that the caller trusts the new member's public
     * key.  This operation is accepted by the server if:
     * - the device has created the secure roster,
     * - the device is member of the secure roster AND the public key was registered,
     * - the device member has the Permission.ADD_MEMBER.
     *
     * @param rosterId the secure roster ID and roster schema ID.
     * @param signingMember the twincode identifying the private key to sign the new member.
     * @param newMemberTwincodeId the new member twincode ID.
     * @param newMemberPermission the new member permission.
     * @param newMemberPublicKey the new member public key.
     * @param complete the completion handler executed when the operation completes.
     */
    void addMember(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                   @NonNull UUID newMemberTwincodeId, @NonNull List<Permission> newMemberPermission,
                   @NonNull CryptoService.PublicKeyData newMemberPublicKey, @NonNull Consumer<Void> complete);
    void addMember(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                   @NonNull TwincodeOutbound newMemberTwincode, @NonNull List<Permission> newMemberPermission,
                   @NonNull Consumer<Void> complete);
    void addMembers(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                    @NonNull List<MemberIdentity> members, @NonNull Consumer<Void> complete);
    void addMembers(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                    @NonNull ConversationService.GroupConversation groupConversation, @NonNull Consumer<Void> complete);

    /**
     * Update the member permissions in the secure roster.
     * @param rosterId the secure roster ID and roster schema ID.
     * @param signingMember the twincode identifying the private key to sign the new member.
     * @param members the list of members to update.
     * @param complete the completion handler executed when the operation completes.
     */
    void updateMembers(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                       @NonNull List<MemberIdentity> members, @NonNull Consumer<Void> complete);

    /**
     * Remove a member from the secure roster. This operation is accepted by the server if:
     * - the device has created the secure roster,
     * - the device is member of the secure roster,
     * - the device member has the Permission.DELETE_MEMBER.
     *
     * @param rosterId the secure roster ID.
     * @param memberId the member twincode ID to remove.
     * @param complete the completion handler executed when the operation completes.
     */
    void deleteMember(@NonNull UUID rosterId, @NonNull UUID memberId, @NonNull Consumer<Void> complete);

    /**
     * Set the secure roster public key signed by the given twincode private key.
     * This operation is accepted by the server if:
     * - the device has created the secure roster,
     * - the device is member of the secure roster,
     * - the device member has the Permission.ADD_PUBLIC_KEY,
     * - the provided signature to add the new public key is valid,
     * - the public key matches exactly what the server knows.
     *
     * @param rosterId the secure roster ID and roster schema ID.
     * @param signingMember the twincode identifying the private key to sign the new public key.
     * @param newKeyId the key ID to sign.
     * @param newPublicKey the public key to sign.
     * @param complete the completion handler executed when the operation completes.
     */
    void setRosterPublicKey(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                            @NonNull UUID newKeyId, @NonNull byte[] newPublicKey, @NonNull Consumer<Void> complete);
    void setRosterPublicKey(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                            @NonNull TwincodeOutbound newSigningTwincode, @NonNull Consumer<Void> complete);

    /**
     * Delete the secure roster if the user is member and owner of the secure roster.
     *
     * @param rosterId the secure roster ID.
     * @param complete the completion handler executed when the operation completes.
     */
    void deleteRoster(@NonNull RosterId rosterId, @NonNull Consumer<Void> complete);
}
