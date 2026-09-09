/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.secureroster;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.ErrorCode;
import org.twinlife.twinlife.RosterId;
import org.twinlife.twinlife.RosterMember;
import org.twinlife.twinlife.Permission;
import org.twinlife.twinlife.SecureRoster;
import org.twinlife.twinlife.SecureRosterService;
import org.twinlife.twinlife.SignedRosterGroup;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.conversation.GroupProtocol;
import org.twinlife.twinlife.util.BinaryCompactEncoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Logger;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SecureRosterServiceImpl extends BaseServiceImpl<BaseService.ServiceObserver> implements SecureRosterService {
    private static final String LOG_TAG = "SecureRosterServi...";
    private static final boolean DEBUG = false;

    private static final UUID CREATE_ROSTER_SCHEMA_ID = UUID.fromString("74a4430b-9910-4fad-bba3-85c92dc99c9e");
    private static final UUID ON_CREATE_ROSTER_SCHEMA_ID = UUID.fromString("796995be-2ec5-44c4-b016-7a277e3e9bd3");
    private static final UUID LIST_ROSTER_SCHEMA_ID = UUID.fromString("8da99a60-25e5-49f6-acc9-93c28c1d3314");
    private static final UUID ON_LIST_ROSTER_SCHEMA_ID = UUID.fromString("299b036c-2589-4367-8df3-3f61ef5b2268");
    private static final UUID ADD_ROSTER_MEMBER_SCHEMA_ID = UUID.fromString("abf52b69-e9d4-47c1-864c-9f94cbfa5096");
    private static final UUID ON_ADD_ROSTER_MEMBER_SCHEMA_ID = UUID.fromString("4ceec7bb-0ae9-462d-bd9f-cc84f33232b7");
    private static final UUID UPDATE_ROSTER_MEMBER_SCHEMA_ID = UUID.fromString("9d8dc720-be8e-4fb7-a5f1-4b1ecd0f539f");
    private static final UUID ON_UPDATE_ROSTER_MEMBER_SCHEMA_ID = UUID.fromString("6ec3a99c-6a5e-4554-812d-ed2791768383");
    private static final UUID DELETE_ROSTER_MEMBER_SCHEMA_ID = UUID.fromString("e10bbf8e-cab3-4817-9e22-2a8664135ab8");
    private static final UUID ON_DELETE_ROSTER_MEMBER_SCHEMA_ID = UUID.fromString("721ab261-9259-437e-bd2c-efe11b7eec8b");
    private static final UUID ADD_ROSTER_PUBLIC_KEY_SCHEMA_ID = UUID.fromString("a0c5183a-062e-412d-b31b-1a6c79b4c6f6");
    private static final UUID ON_ADD_ROSTER_PUBLIC_KEY_SCHEMA_ID = UUID.fromString("6e72fc01-d779-4872-b59e-0d387d3a2a88");
    private static final UUID DELETE_ROSTER_SCHEMA_ID = UUID.fromString("1ddcdf5c-c810-4b04-bbdb-4b9f14d41483");
    private static final UUID ON_DELETE_ROSTER_SCHEMA_ID = UUID.fromString("d400c87a-05c3-4354-9849-75160a107041");

    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_CREATE_ROSTER_SERIALIZER = CreateRosterIQ.createSerializer(CREATE_ROSTER_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_CREATE_ROSTER_SERIALIZER = OnCreateRosterIQ.createSerializer(ON_CREATE_ROSTER_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_LIST_ROSTER_SERIALIZER = ListRosterIQ.createSerializer(LIST_ROSTER_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_LIST_ROSTER_SERIALIZER = OnListRosterIQ.createSerializer(ON_LIST_ROSTER_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ADD_ROSTER_MEMBER_SERIALIZER = AddRosterMemberIQ.createSerializer(ADD_ROSTER_MEMBER_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_UPDATE_ROSTER_MEMBER_SERIALIZER = UpdateRosterMemberIQ.createSerializer(UPDATE_ROSTER_MEMBER_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_DELETE_ROSTER_MEMBER_SERIALIZER = DeleteRosterMemberIQ.createSerializer(DELETE_ROSTER_MEMBER_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ADD_ROSTER_PUBLIC_KEY_SERIALIZER = AddRosterPublicKeyIQ.createSerializer(ADD_ROSTER_PUBLIC_KEY_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_ADD_ROSTER_MEMBER_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_ADD_ROSTER_MEMBER_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_DELETE_ROSTER_MEMBER_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_DELETE_ROSTER_MEMBER_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_ADD_ROSTER_PUBLIC_KEY_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_ADD_ROSTER_PUBLIC_KEY_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UPDATE_ROSTER_MEMBER_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_UPDATE_ROSTER_MEMBER_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_DELETE_ROSTER_SERIALIZER = SecureRosterIQ.createSerializer(DELETE_ROSTER_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_DELETE_ROSTER_SERIALIZER = BinaryErrorPacketIQ.createSerializer(ON_DELETE_ROSTER_SCHEMA_ID, 1);

    private static class PendingRequest {
    }

    private static final class CreateRosterPendingRequest extends PendingRequest {
        @NonNull
        final UUID schemaId;
        @NonNull
        final Consumer<RosterId> complete;

        CreateRosterPendingRequest(@NonNull UUID schemaId, @NonNull Consumer<RosterId> complete) {
            this.schemaId = schemaId;
            this.complete = complete;
        }
    }

    private static final class ListRosterPendingRequest extends PendingRequest {
        @NonNull
        final RosterId rosterId;
        @NonNull
        final Consumer<SecureRoster> complete;

        ListRosterPendingRequest(@NonNull RosterId rosterId, @NonNull Consumer<SecureRoster> complete) {
            this.rosterId = rosterId;
            this.complete = complete;
        }
    }

    private static final class RosterPendingRequest extends PendingRequest {
        @NonNull
        final Consumer<Void> complete;

        RosterPendingRequest(@NonNull Consumer<Void> complete) {
            this.complete = complete;
        }
    }

    private final HashMap<Long, PendingRequest> mPendingRequests = new HashMap<>();

    public SecureRosterServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {

        super(twinlifeImpl, connection);

        setServiceConfiguration(new SecureRosterServiceConfiguration());

        // Register the binary IQ handlers.
        connection.addPacketListener(IQ_ON_CREATE_ROSTER_SERIALIZER, this::onCreateRoster);
        connection.addPacketListener(IQ_ON_LIST_ROSTER_SERIALIZER, this::onListRoster);
        connection.addPacketListener(IQ_ON_ADD_ROSTER_MEMBER_SERIALIZER, this::onAddMemberRoster);
        connection.addPacketListener(IQ_ON_DELETE_ROSTER_MEMBER_SERIALIZER, this::onDeleteMemberRoster);
        connection.addPacketListener(IQ_ON_ADD_ROSTER_PUBLIC_KEY_SERIALIZER, this::onAddRosterPublicKey);
        connection.addPacketListener(IQ_ON_UPDATE_ROSTER_MEMBER_SERIALIZER, this::onUpdateMemberRoster);
        connection.addPacketListener(IQ_ON_DELETE_ROSTER_SERIALIZER, this::onDeleteRoster);
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof SecureRosterServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        SecureRosterServiceConfiguration secureRosterServiceConfiguration = new SecureRosterServiceConfiguration();

        setServiceConfiguration(secureRosterServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    @Override
    public void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        super.onSignOut();

        synchronized (mPendingRequests) {
            mPendingRequests.clear();
        }
    }

    //
    // Implement SecureRosterService interface
    //

    @Override
    public void createRoster(int createOptions, @NonNull UUID rosterSchemaId, @NonNull UUID publicKeyId, @NonNull byte[] publicKey,
                             @NonNull Consumer<RosterId> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createRoster: rosterSchemaId=" + rosterSchemaId);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new CreateRosterPendingRequest(rosterSchemaId, complete));
        }

        final CreateRosterIQ createRosterIQ = new CreateRosterIQ(IQ_CREATE_ROSTER_SERIALIZER, requestId,
                createOptions, rosterSchemaId, publicKeyId, publicKey);
        sendDataPacket(createRosterIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void createRoster(int createOptions, @NonNull UUID rosterSchemaId, @NonNull TwincodeOutbound signingTwincode, @NonNull Consumer<RosterId> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createRoster: rosterSchemaId=" + rosterSchemaId + " twincodeOutbound=" + signingTwincode);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        byte[] publicKey = mTwinlifeImpl.getCryptoService().getRawPublicKey(signingTwincode);
        if (publicKey == null) {
            complete.onGet(ErrorCode.NO_PUBLIC_KEY, null);
            return;
        }

        createRoster(createOptions, rosterSchemaId, signingTwincode.getId(), publicKey, complete);
    }

    @Override
    public void listRoster(@NonNull RosterId rosterId, long afterCreationTime, @NonNull Consumer<SecureRoster> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listRoster: rosterId=" + rosterId + " afterCreationTime=" + afterCreationTime);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new ListRosterPendingRequest(rosterId, complete));
        }

        final ListRosterIQ listRosterIQ = new ListRosterIQ(IQ_LIST_ROSTER_SERIALIZER, requestId, rosterId.id, afterCreationTime);
        sendDataPacket(listRosterIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void addMember(@NonNull RosterId rosterId, @NonNull TwincodeOutbound currentMember,
                          @NonNull UUID newMemberTwincodeId, @NonNull List<Permission> newMemberPermission,
                          @NonNull CryptoService.PublicKeyData newMemberPublicKey, @NonNull Consumer<Void> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addMember: rosterId=" + rosterId + " newMemberTwincodeId=" + newMemberTwincodeId);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        long permissions = 0;
        for (Permission p : newMemberPermission) {
            if ((permissions & p.value) != 0) {
                complete.onGet(ErrorCode.BAD_REQUEST, null);
                return;
            }
            permissions |= p.value;
        }
        MemberIdentity member = new MemberIdentity(newMemberTwincodeId, new Permission(permissions), newMemberPublicKey);
        List<MemberIdentity> members = List.of(member);
        addMembers(rosterId, currentMember, members, complete);
    }

    @Override
    public void addMembers(@NonNull RosterId rosterId, @NonNull TwincodeOutbound currentMember,
                           @NonNull List<MemberIdentity> members, @NonNull Consumer<Void> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addMembers: rosterId=" + rosterId + " members=" + members);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final List<AddRosterMemberIQ.MemberInfo> addMembers = new ArrayList<>(members.size());
        final CryptoService cryptoService = mTwinlifeImpl.getCryptoService();
        for (MemberIdentity member : members) {
            final long permissions = member.memberPermission.value & Permission.ALL_PERMISSIONS.value;
            final byte[] rawPublicKey = member.memberPublicKey.asBytes();
            final byte[] content;
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);
                encoder.writeUUID(rosterId.id);
                encoder.writeUUID(rosterId.schemaId);
                encoder.writeUUID(member.memberTwincodeId);
                encoder.writeLong(permissions);
                encoder.writeBytes(rawPublicKey, 0, rawPublicKey.length);
                content = outputStream.toByteArray();
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "addMember content serialization failed", exception);
                }
                complete.onGet(ErrorCode.LIBRARY_ERROR, null);
                return;
            }

            final byte[] signature = cryptoService.signContentRaw(currentMember, content);
            if (signature == null) {
                complete.onGet(ErrorCode.LIBRARY_ERROR, null);
                return;
            }

            // If this member can invite other members, we also have to provide a valid signature
            // to verify that member's public key to sign other members.
            final byte[] rosterKeySignature;
            if (member.memberPermission.hasPermission(Permission.INVITE_MEMBER)) {
                final byte[] keyFingerprint = createKeyFingerprint(rosterId, member.memberTwincodeId, rawPublicKey);
                if (keyFingerprint == null) {
                    complete.onGet(ErrorCode.LIBRARY_ERROR, null);
                    return;
                }
                rosterKeySignature = cryptoService.signContentRaw(currentMember, keyFingerprint);
                if (rosterKeySignature == null) {
                    complete.onGet(ErrorCode.LIBRARY_ERROR, null);
                    return;
                }
            } else {
                rosterKeySignature = null;
            }

            addMembers.add(new AddRosterMemberIQ.MemberInfo(member.memberTwincodeId, permissions, rawPublicKey, signature, rosterKeySignature));
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new RosterPendingRequest(complete));
        }

        final AddRosterMemberIQ addRosterMemberIQ = new AddRosterMemberIQ(IQ_ADD_ROSTER_MEMBER_SERIALIZER, requestId,
                rosterId.id, currentMember.getId(), addMembers);
        sendDataPacket(addRosterMemberIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    public void addMembers(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                           @NonNull ConversationService.GroupConversation groupConversation, @NonNull Consumer<Void> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addMembers: rosterId=" + rosterId + " groupConversation=" + groupConversation);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final List<ConversationService.GroupMemberConversation> members = groupConversation.getGroupMembers(ConversationService.MemberFilter.JOINED_MEMBERS);
        final List<MemberIdentity> addMembers = new ArrayList<>(members.size() + 1);
        final CryptoService cryptoService = mTwinlifeImpl.getCryptoService();

        // Add the group owner (ie, ourselves).
        final TwincodeOutbound ownerTwincode = groupConversation.getTwincodeOutbound();
        if (ownerTwincode != null) {
            Permission permission = groupConversation.getPermission().restrictPermission(Permission.ALL_PERMISSIONS);
            final byte[] publicKey = cryptoService.getRawPublicKey(ownerTwincode);
            if (publicKey != null) {
                addMembers.add(new MemberIdentity(ownerTwincode.getId(), permission, CryptoService.PublicKeyData.create(publicKey)));
            } else if (rosterId.schemaId.equals(GroupProtocol.LEGACY_SCHEMA_ID)) {
                // If this is a legacy secure roster group, add this member with an empty public key.
                // We must also remove the right for that member to invite other members because it would not be able
                // to create a valid member signature.
                if (permission.hasPermission(Permission.INVITE_MEMBER)) {
                    permission = permission.removePermission(Permission.INVITE_MEMBER);
                    mTwinlifeImpl.getConversationService().setPermissions(groupConversation.getSubject(), ownerTwincode.getId(), List.of(permission));
                }
                addMembers.add(new MemberIdentity(ownerTwincode.getId(), permission, CryptoService.PublicKeyData.create(new byte[]{})));
            }
        }

        // Add each group member we know.
        for (ConversationService.GroupMemberConversation member : members) {
            final TwincodeOutbound memberTwincode = member.getPeerTwincodeOutbound();
            if (memberTwincode != null) {
                Permission permission = member.getPermission().restrictPermission(Permission.ALL_PERMISSIONS);
                final byte[] publicKey = cryptoService.getRawPublicKey(member.getPeerTwincodeOutbound());
                if (publicKey != null) {
                    addMembers.add(new MemberIdentity(memberTwincode.getId(), permission, CryptoService.PublicKeyData.create(publicKey)));
                } else if (rosterId.schemaId.equals(GroupProtocol.LEGACY_SCHEMA_ID)) {
                    // If this is a legacy secure roster group, add this member with an empty public key.
                    // We must also remove the right for that member to invite other members because it would not be able
                    // to create a valid member signature.
                    if (permission.hasPermission(Permission.INVITE_MEMBER)) {
                        permission = permission.removePermission(Permission.INVITE_MEMBER);
                        mTwinlifeImpl.getConversationService().setPermissions(groupConversation.getSubject(), memberTwincode.getId(), List.of(permission));
                    }
                    addMembers.add(new MemberIdentity(memberTwincode.getId(), permission, CryptoService.PublicKeyData.create(new byte[]{})));
                }
            }
        }

        addMembers(rosterId, signingMember, addMembers, complete);
    }

    @Override
    public void addMember(@NonNull RosterId rosterId,  @NonNull TwincodeOutbound currentMember,
                          @NonNull TwincodeOutbound newMemberTwincode, @NonNull List<Permission> newMemberPermission,
                          @NonNull Consumer<Void> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addMember: rosterId=" + rosterId + " newMemberTwincode=" + newMemberTwincode);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final byte[] publicKey = mTwinlifeImpl.getCryptoService().getRawPublicKey(newMemberTwincode);
        if (publicKey == null) {
            complete.onGet(ErrorCode.NO_PUBLIC_KEY, null);
            return;
        }

        addMember(rosterId, currentMember, newMemberTwincode.getId(), newMemberPermission, CryptoService.PublicKeyData.create(publicKey), complete);
    }

    @Override
    public void updateMembers(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                              @NonNull List<MemberIdentity> members, @NonNull Consumer<Void> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateMembers: rosterId=" + rosterId + " members=" + members);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final List<UpdateRosterMemberIQ.MemberPermission> updateMembers = new ArrayList<>(members.size());
        final CryptoService cryptoService = mTwinlifeImpl.getCryptoService();
        for (MemberIdentity member : members) {
            final long permissions = member.memberPermission.value & Permission.ALL_PERMISSIONS.value;
            final byte[] rawPublicKey = member.memberPublicKey.asBytes();
            final byte[] content;
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);
                encoder.writeUUID(rosterId.id);
                encoder.writeUUID(rosterId.schemaId);
                encoder.writeUUID(member.memberTwincodeId);
                encoder.writeLong(permissions);
                encoder.writeBytes(rawPublicKey, 0, rawPublicKey.length);
                content = outputStream.toByteArray();
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Log.e(LOG_TAG, "updateMember content serialization failed", exception);
                }
                complete.onGet(ErrorCode.LIBRARY_ERROR, null);
                return;
            }

            final byte[] signature = cryptoService.signContentRaw(signingMember, content);
            if (signature == null) {
                complete.onGet(ErrorCode.LIBRARY_ERROR, null);
                return;
            }

            // If this member can invite other members, we also have to provide a valid signature
            // to verify that member's public key to sign other members.
            final byte[] rosterKeySignature;
            if (member.memberPermission.hasPermission(Permission.INVITE_MEMBER)) {
                final byte[] keyFingerprint = createKeyFingerprint(rosterId, member.memberTwincodeId, rawPublicKey);
                if (keyFingerprint == null) {
                    complete.onGet(ErrorCode.LIBRARY_ERROR, null);
                    return;
                }
                rosterKeySignature = cryptoService.signContentRaw(signingMember, keyFingerprint);
                if (rosterKeySignature == null) {
                    complete.onGet(ErrorCode.LIBRARY_ERROR, null);
                    return;
                }
            } else {
                rosterKeySignature = null;
            }

            updateMembers.add(new UpdateRosterMemberIQ.MemberPermission(member.memberTwincodeId, permissions, signature, rosterKeySignature));
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new RosterPendingRequest(complete));
        }

        final UpdateRosterMemberIQ updateRosterMemberIQ = new UpdateRosterMemberIQ(IQ_UPDATE_ROSTER_MEMBER_SERIALIZER, requestId,
                rosterId.id, signingMember.getId(), updateMembers);
        sendDataPacket(updateRosterMemberIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void deleteMember(@NonNull UUID rosterId, @NonNull UUID memberId, @NonNull Consumer<Void> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteMember: rosterId=" + rosterId + " memberId=" + memberId);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new RosterPendingRequest(complete));
        }

        final DeleteRosterMemberIQ deleteRosterMemberIQ = new DeleteRosterMemberIQ(IQ_DELETE_ROSTER_MEMBER_SERIALIZER, requestId,
                rosterId, memberId);
        sendDataPacket(deleteRosterMemberIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void setRosterPublicKey(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                                   @NonNull UUID newKeyId, @NonNull byte[] newPublicKey, @NonNull Consumer<Void> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setRosterPublicKey: rosterId=" + rosterId + " newKeyId=" + newKeyId);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final byte[] content = createKeyFingerprint(rosterId, newKeyId, newPublicKey);
        if (content == null) {
            complete.onGet(ErrorCode.LIBRARY_ERROR, null);
            return;
        }

        final CryptoService cryptoService = mTwinlifeImpl.getCryptoService();
        final byte[] signature = cryptoService.signContentRaw(signingMember, content);
        if (signature == null) {
            complete.onGet(ErrorCode.LIBRARY_ERROR, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new RosterPendingRequest(complete));
        }

        final AddRosterPublicKeyIQ addRosterPublicKeyIQ = new AddRosterPublicKeyIQ(IQ_ADD_ROSTER_PUBLIC_KEY_SERIALIZER, requestId,
                rosterId.id, signingMember.getId(), newKeyId, newPublicKey, signature);
        sendDataPacket(addRosterPublicKeyIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Nullable
    private static byte[] createKeyFingerprint(@NonNull RosterId rosterId, @NonNull UUID newKeyId, @NonNull byte[] newPublicKey) {

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);
            encoder.writeUUID(rosterId.id);
            encoder.writeUUID(rosterId.schemaId);
            encoder.writeUUID(newKeyId);
            encoder.writeBytes(newPublicKey, 0, newPublicKey.length);
            return outputStream.toByteArray();

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Log.e(LOG_TAG, "createKeyFingerprint content serialization failed", exception);
            }
            return null;
        }
    }

    @Override
    public void setRosterPublicKey(@NonNull RosterId rosterId, @NonNull TwincodeOutbound signingMember,
                                   @NonNull TwincodeOutbound newSigningTwincode, @NonNull Consumer<Void> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setRosterPublicKey: rosterId=" + rosterId + " newSigningTwincode=" + newSigningTwincode);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final byte[] publicKey = mTwinlifeImpl.getCryptoService().getRawPublicKey(newSigningTwincode);
        if (publicKey == null) {
            complete.onGet(ErrorCode.NO_PUBLIC_KEY, null);
            return;
        }
        setRosterPublicKey(rosterId, signingMember, newSigningTwincode.getId(), publicKey, complete);
    }

    /**
     * Delete the secure roster if the user is member and owner of the secure roster.
     *
     * @param rosterId the secure roster ID.
     * @param complete the completion handler executed when the operation completes.
     */
    public void deleteRoster(@NonNull RosterId rosterId, @NonNull Consumer<Void> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteRoster: rosterId=" + rosterId);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new RosterPendingRequest(complete));
        }

        final SecureRosterIQ deleteRosterIQ = new SecureRosterIQ(IQ_DELETE_ROSTER_SERIALIZER, requestId, rosterId.id);
        sendDataPacket(deleteRosterIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Verify the signatures of the roster signature keys and the roster members.
     *
     * @param rosterId the roster UUID.
     * @param keys the list of signature keys and members to verify.
     */
    private void verifyRosterSignatures(@NonNull RosterId rosterId, @NonNull List<SignedRosterGroup> keys) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verifyRosterSignatures rosterId=" + rosterId);
        }

        final CryptoService cryptoService = mTwinlifeImpl.getCryptoService();

        Map<UUID, CryptoService.PublicKeyData> validatedKeys = new HashMap<>();
        for (SignedRosterGroup key : keys) {
            final CryptoService.PublicKeyData publicKey;
            if (validatedKeys.isEmpty() && key.signingKeyId.equals(key.keyId)) {
                publicKey = key.publicKey;
            } else {
                publicKey = validatedKeys.get(key.signingKeyId);
            }

            // Step 1: Verify the signature of the roster signature key itself.
            if (publicKey != null) {
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);
                    encoder.writeUUID(rosterId.id);
                    encoder.writeUUID(rosterId.schemaId);
                    encoder.writeUUID(key.keyId);
                    encoder.writeBytes(key.publicKey.asBytes(), 0, key.publicKey.asBytes().length);

                    final byte[] content = outputStream.toByteArray();
                    key.verified = cryptoService.verifyContent(key.signingKeyId, publicKey, content, key.signature) == ErrorCode.SUCCESS;
                    if (key.verified) {
                        validatedKeys.put(key.keyId, key.publicKey);
                    }

                } catch (Exception exception) {
                    if (Logger.ERROR) {
                        Log.e(LOG_TAG, "verifyRosterSignatures (key) failed", exception);
                    }
                    key.verified = false;
                }
            } else {
                key.verified = false;
            }

            // Step 2: Verify the signature of each member signed by this key.
            // Note: If the key itself is not verified, we might still want to verify the members if we have their public key.
            // But usually, members are verified against the public key they were signed with.
            // Here, we assume the 'signer' for members is the twincode associated with 'key.keyId'.
            for (RosterMember member : key.members) {
                try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    final BinaryEncoder encoder = new BinaryCompactEncoder(outputStream);
                    encoder.writeUUID(rosterId.id);
                    encoder.writeUUID(rosterId.schemaId);
                    encoder.writeUUID(member.memberTwincodeId);
                    encoder.writeLong(member.permissions);
                    encoder.writeBytes(member.publicKey.asBytes(), 0, member.publicKey.asBytes().length);

                    final byte[] content = outputStream.toByteArray();
                    member.verified = key.verified && cryptoService.verifyContent(key.keyId, key.publicKey, content, member.signature) == ErrorCode.SUCCESS;

                } catch (Exception exception) {
                    if (Logger.ERROR) {
                        Log.e(LOG_TAG, "verifyRosterSignatures (member) failed", exception);
                    }
                    member.verified = false;
                }
            }
        }
    }

    //
    // Private Methods
    //

    private void onCreateRoster(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateRoster iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final CreateRosterPendingRequest request;
        synchronized (mPendingRequests) {
            request = (CreateRosterPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final OnCreateRosterIQ onCreateRosterIQ = (OnCreateRosterIQ) iq;
        request.complete.onGet(ErrorCode.SUCCESS, new RosterId(onCreateRosterIQ.getRosterId(), request.schemaId));
    }

    private void onListRoster(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListRoster iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final ListRosterPendingRequest request;
        synchronized (mPendingRequests) {
            request = (ListRosterPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final OnListRosterIQ onListRosterIQ = (OnListRosterIQ) iq;
        verifyRosterSignatures(request.rosterId, onListRosterIQ.getKeys());
        request.complete.onGet(ErrorCode.SUCCESS, new SecureRoster(request.rosterId, onListRosterIQ.getMaxMemberCount(), onListRosterIQ.getKeys()));
    }

    private void onAddMemberRoster(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAddMemberRoster iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final RosterPendingRequest request;
        synchronized (mPendingRequests) {
            request = (RosterPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final BinaryErrorPacketIQ response = (BinaryErrorPacketIQ) iq;
        request.complete.onGet(response.getErrorCode(), null);
    }

    private void onUpdateMemberRoster(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateMemberRoster iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final RosterPendingRequest request;
        synchronized (mPendingRequests) {
            request = (RosterPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final BinaryErrorPacketIQ response = (BinaryErrorPacketIQ) iq;
        request.complete.onGet(response.getErrorCode(), null);
    }

    private void onDeleteMemberRoster(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteMemberRoster iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final RosterPendingRequest request;
        synchronized (mPendingRequests) {
            request = (RosterPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final BinaryErrorPacketIQ response = (BinaryErrorPacketIQ) iq;
        request.complete.onGet(response.getErrorCode(), null);
    }

    private void onAddRosterPublicKey(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAddRosterPublicKey iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final RosterPendingRequest request;
        synchronized (mPendingRequests) {
            request = (RosterPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final BinaryErrorPacketIQ response = (BinaryErrorPacketIQ) iq;
        request.complete.onGet(response.getErrorCode(), null);
    }

    private void onDeleteRoster(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteRoster iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final RosterPendingRequest request;
        synchronized (mPendingRequests) {
            request = (RosterPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final BinaryErrorPacketIQ response = (BinaryErrorPacketIQ) iq;
        request.complete.onGet(response.getErrorCode(), null);
    }

    @Override
    protected void onErrorPacket(@NonNull BinaryErrorPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onErrorPacket: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        if (request instanceof CreateRosterPendingRequest) {
            final CreateRosterPendingRequest createRequest = (CreateRosterPendingRequest) request;
            createRequest.complete.onGet(iq.getErrorCode(), null);
        } else if (request instanceof ListRosterPendingRequest) {
            final ListRosterPendingRequest listRequest = (ListRosterPendingRequest) request;
            listRequest.complete.onGet(iq.getErrorCode(), null);
        } else if (request instanceof RosterPendingRequest) {
            final RosterPendingRequest rosterRequest = (RosterPendingRequest) request;
            rosterRequest.complete.onGet(iq.getErrorCode(), null);
        }
    }
}
