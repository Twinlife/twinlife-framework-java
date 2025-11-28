/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.twincode.outbound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.InvitationCode;
import org.twinlife.twinlife.JobService;
import org.twinlife.twinlife.SNIProxyDescriptor;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwincodeURI;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.crypto.CryptoServiceImpl;
import org.twinlife.twinlife.twincode.CreateInvitationCodeIQ;
import org.twinlife.twinlife.twincode.GetInvitationCodeIQ;
import org.twinlife.twinlife.twincode.GetTwincodeIQ;
import org.twinlife.twinlife.twincode.InvokeTwincodeIQ;
import org.twinlife.twinlife.twincode.OnCreateInvitationCodeIQ;
import org.twinlife.twinlife.twincode.OnGetInvitationCodeIQ;
import org.twinlife.twinlife.twincode.OnGetTwincodeIQ;
import org.twinlife.twinlife.twincode.OnRefreshTwincodeIQ;
import org.twinlife.twinlife.twincode.OnUpdateTwincodeIQ;
import org.twinlife.twinlife.twincode.RefreshTwincodeIQ;
import org.twinlife.twinlife.twincode.RefreshTwincodeInfo;
import org.twinlife.twinlife.twincode.UpdateTwincodeIQ;
import org.twinlife.twinlife.twincode.InvocationIQ;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ.BinaryPacketIQSerializer;
import org.twinlife.twinlife.util.SerializerFactoryImpl;

import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class TwincodeOutboundServiceImpl extends BaseServiceImpl<TwincodeOutboundService.ServiceObserver> implements TwincodeOutboundService {
    private static final String LOG_TAG = "TwincodeOutboundServ...";
    private static final boolean DEBUG = false;

    private static final String TWINLIFE_SERVICE = SERVICE_NAMES[BaseServiceId.TWINCODE_OUTBOUND_SERVICE_ID.ordinal()];

    private static final long MIN_REFRESH_DELAY = 30 * 3600 * 1000L;

    private static final UUID GET_TWINCODE_SCHEMA_ID = UUID.fromString("4d06f636-6327-4c1d-b044-08227f4aa7cb");
    private static final UUID ON_GET_TWINCODE_SCHEMA_ID = UUID.fromString("76bdf639-65a3-41b9-9af9-87d622473d3f");
    private static final UUID UPDATE_TWINCODE_SCHEMA_ID = UUID.fromString("8efcb2a1-6607-4b06-964c-ec65ed459ffc");
    private static final UUID ON_UPDATE_TWINCODE_SCHEMA_ID = UUID.fromString("2b0ff6f7-75bb-44a6-9fac-0a9b28fc84dd");
    private static final UUID REFRESH_TWINCODE_SCHEMA_ID = UUID.fromString("e8028e21-e657-4240-b71a-21ea1367ebf2");
    private static final UUID ON_REFRESH_TWINCODE_SCHEMA_ID = UUID.fromString("2dc1c0bc-f4a1-4904-ac55-680ce11e43f8");
    private static final UUID INVOKE_TWINCODE_SCHEMA_ID = UUID.fromString("c74e79e6-5157-4fb4-bad8-2de545711fa0");
    private static final UUID ON_INVOKE_TWINCODE_SCHEMA_ID = UUID.fromString("35d11e72-84d7-4a3b-badd-9367ef8c9e43");
    private static final UUID CREATE_INVITATION_CODE_SCHEMA_ID = UUID.fromString("8dcfcba5-b8c0-4375-a501-d24534ed4a3b");
    private static final UUID ON_CREATE_INVITATION_CODE_SCHEMA_ID = UUID.fromString("93cf2a0c-82cb-43ea-98c6-43563807fadf");
    private static final UUID GET_INVITATION_CODE_SCHEMA_ID = UUID.fromString("95335487-91fa-4cdc-939b-e047a068e94d");
    private static final UUID ON_GET_INVITATION_CODE_SCHEMA_ID = UUID.fromString("a16cf169-81dd-4a47-8787-5856f409e017");

    private static final BinaryPacketIQSerializer IQ_GET_TWINCODE_SERIALIZER = GetTwincodeIQ.createSerializer(GET_TWINCODE_SCHEMA_ID, 2);
    private static final BinaryPacketIQSerializer IQ_ON_GET_TWINCODE_SERIALIZER = OnGetTwincodeIQ.createSerializer(ON_GET_TWINCODE_SCHEMA_ID, 2);
    private static final BinaryPacketIQSerializer IQ_UPDATE_TWINCODE_SERIALIZER = UpdateTwincodeIQ.createSerializer(UPDATE_TWINCODE_SCHEMA_ID, 2);
    private static final BinaryPacketIQSerializer IQ_ON_UPDATE_TWINCODE_SERIALIZER = OnUpdateTwincodeIQ.createSerializer(ON_UPDATE_TWINCODE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_REFRESH_TWINCODE_SERIALIZER = RefreshTwincodeIQ.createSerializer(REFRESH_TWINCODE_SCHEMA_ID, 2);
    private static final BinaryPacketIQSerializer IQ_ON_REFRESH_TWINCODE_SERIALIZER = OnRefreshTwincodeIQ.createSerializer(ON_REFRESH_TWINCODE_SCHEMA_ID, 2);
    private static final BinaryPacketIQSerializer IQ_INVOKE_TWINCODE_SERIALIZER = InvokeTwincodeIQ.createSerializer(INVOKE_TWINCODE_SCHEMA_ID, 2);
    private static final BinaryPacketIQSerializer IQ_ON_INVOKE_TWINCODE_SERIALIZER = InvocationIQ.createSerializer(ON_INVOKE_TWINCODE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_CREATE_INVITATION_CODE_SERIALIZER = CreateInvitationCodeIQ.createSerializer(CREATE_INVITATION_CODE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_CREATE_INVITATION_CODE_SERIALIZER = OnCreateInvitationCodeIQ.createSerializer(ON_CREATE_INVITATION_CODE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_GET_INVITATION_CODE_SERIALIZER = GetInvitationCodeIQ.createSerializer(GET_INVITATION_CODE_SCHEMA_ID, 1);
    private static final BinaryPacketIQSerializer IQ_ON_GET_INVITATION_CODE_SERIALIZER = OnGetInvitationCodeIQ.createSerializer(ON_GET_INVITATION_CODE_SCHEMA_ID, 1);

    private static class PendingRequest {
    }

    private static final class GetTwincodePendingRequest extends PendingRequest {
        @NonNull
        final UUID twincodeId;
        final long refreshPeriod;
        @NonNull
        final Consumer<TwincodeOutbound> complete;
        @Nullable
        final String pubKey;
        @Nullable
        final byte[] secretKey;
        @NonNull
        final TrustMethod trusted;
        final int keyIndex;

        GetTwincodePendingRequest(@NonNull UUID twincodeId, long refreshPeriod,
                                  @NonNull Consumer<TwincodeOutbound> complete) {
            this.twincodeId = twincodeId;
            this.refreshPeriod = refreshPeriod;
            this.complete = complete;
            this.pubKey = null;
            this.secretKey = null;
            this.trusted = TrustMethod.NONE;
            this.keyIndex = 0;
        }

        GetTwincodePendingRequest(@NonNull UUID twincodeId, @NonNull String pubKey, int keyIndex, @Nullable byte[] secretKey,
                                  @NonNull TrustMethod trusted,
                                  @NonNull Consumer<TwincodeOutbound> complete) {
            this.twincodeId = twincodeId;
            this.refreshPeriod = TwincodeOutboundService.REFRESH_PERIOD;
            this.complete = complete;
            this.pubKey = pubKey;
            this.secretKey = secretKey;
            this.trusted = trusted;
            this.keyIndex = keyIndex;
        }
    }

    private static final class RefreshTwincodePendingRequest extends PendingRequest {
        @NonNull
        final TwincodeOutboundImpl twincodeOutbound;
        @NonNull
        final Consumer<List<AttributeNameValue>> complete;

        RefreshTwincodePendingRequest(@NonNull TwincodeOutbound twincodeOutbound,
                                      @NonNull Consumer<List<AttributeNameValue>> complete) {
            this.twincodeOutbound = (TwincodeOutboundImpl) twincodeOutbound;
            this.complete = complete;
        }
    }

    private static final class UpdateTwincodePendingRequest extends PendingRequest {
        @NonNull
        final TwincodeOutboundImpl twincodeOutbound;
        @NonNull
        final List<AttributeNameValue> attributes;
        final boolean signed;
        @NonNull
        final Consumer<TwincodeOutbound> complete;

        UpdateTwincodePendingRequest(@NonNull TwincodeOutbound twincodeOutbound,
                                     @NonNull List<AttributeNameValue> attributes,
                                     boolean signed,
                                     @NonNull Consumer<TwincodeOutbound> complete) {
            this.attributes = attributes;
            this.twincodeOutbound = (TwincodeOutboundImpl) twincodeOutbound;
            this.signed = signed;
            this.complete = complete;
        }
    }

    private static final class RefreshTwincodesPendingRequest extends PendingRequest {
        @NonNull
        final Map<UUID, Long> refreshList;

        RefreshTwincodesPendingRequest(@NonNull Map<UUID, Long> refreshList) {

            this.refreshList = refreshList;
        }
    }

    private static final class InvokePendingRequest extends PendingRequest {
        @NonNull
        final TwincodeOutbound twincodeId;
        @NonNull
        final Consumer<UUID> complete;

        InvokePendingRequest(@NonNull TwincodeOutbound twincodeId, @NonNull Consumer<UUID> complete) {

            this.twincodeId = twincodeId;
            this.complete = complete;
        }
    }

    private static final class CreateInvitationCodePendingRequest extends PendingRequest {
        @NonNull
        final TwincodeOutbound twincodeOutbound;
        @NonNull
        final Consumer<InvitationCode> complete;

        CreateInvitationCodePendingRequest(@NonNull TwincodeOutbound twincodeOutbound, @NonNull Consumer<InvitationCode> complete) {

            this.twincodeOutbound = twincodeOutbound;
            this.complete = complete;
        }
    }

    private static final class GetInvitationCodePendingRequest extends PendingRequest {
        @NonNull
        final String code;
        @NonNull
        final Consumer<Pair<TwincodeOutbound, String>> complete;

        GetInvitationCodePendingRequest(@NonNull String code,
                                        @NonNull Consumer<Pair<TwincodeOutbound, String>> complete) {
            this.code = code;
            this.complete = complete;
        }
    }

    private final TwincodeOutboundServiceProvider mServiceProvider;
    private final String mServiceJid;
    private final HashMap<Long, PendingRequest> mPendingRequests = new HashMap<>();
    private final CryptoServiceImpl mCryptoService;
    private JobService.Job mRefreshJob;
    private long mPreviousRefreshDate = 0;

    public TwincodeOutboundServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {

        super(twinlifeImpl, connection);

        setServiceConfiguration(new TwincodeOutboundServiceConfiguration());
        SerializerFactoryImpl serializerFactory = mTwinlifeImpl.getSerializerFactoryImpl();
        serializerFactory.addSerializer(IQ_GET_TWINCODE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_GET_TWINCODE_SERIALIZER);
        serializerFactory.addSerializer(IQ_UPDATE_TWINCODE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_UPDATE_TWINCODE_SERIALIZER);
        serializerFactory.addSerializer(IQ_REFRESH_TWINCODE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_REFRESH_TWINCODE_SERIALIZER);
        serializerFactory.addSerializer(IQ_CREATE_INVITATION_CODE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_CREATE_INVITATION_CODE_SERIALIZER);
        serializerFactory.addSerializer(IQ_GET_INVITATION_CODE_SERIALIZER);
        serializerFactory.addSerializer(IQ_ON_GET_INVITATION_CODE_SERIALIZER);

        // Register the binary IQ handlers for the responses.
        connection.addPacketListener(IQ_ON_GET_TWINCODE_SERIALIZER, this::onGetTwincode);
        connection.addPacketListener(IQ_ON_UPDATE_TWINCODE_SERIALIZER, this::onUpdateTwincode);
        connection.addPacketListener(IQ_ON_REFRESH_TWINCODE_SERIALIZER, this::onRefreshTwincode);
        connection.addPacketListener(IQ_ON_INVOKE_TWINCODE_SERIALIZER, this::onInvokeTwincode);
        connection.addPacketListener(IQ_ON_CREATE_INVITATION_CODE_SERIALIZER, this::onCreateInvitationCode);
        connection.addPacketListener(IQ_ON_GET_INVITATION_CODE_SERIALIZER, this::onGetInvitationCode);

        mServiceProvider = new TwincodeOutboundServiceProvider(this, twinlifeImpl.getDatabaseService());
        mServiceJid = TWINLIFE_SERVICE + "." + connection.getDomain();
        mCryptoService = twinlifeImpl.getCryptoService();
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof TwincodeOutboundServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        TwincodeOutboundServiceConfiguration twincodeOutboundServiceConfiguration = new TwincodeOutboundServiceConfiguration();

        setServiceConfiguration(twincodeOutboundServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        super.onTwinlifeOnline();

        updateRefreshJob();
    }

    @Override
    public void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        super.onSignOut();

        if (mRefreshJob != null) {
            mRefreshJob.cancel();
            mRefreshJob = null;
        }
        synchronized (mPendingRequests) {
            mPendingRequests.clear();
        }
    }

    @Override
    public void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        super.onDisconnect();

        if (mRefreshJob != null) {
            mRefreshJob.cancel();
            mRefreshJob = null;
        }
    }

    //
    // Implement TwincodeOutboundService interface
    //

    @Override
    public void getTwincode(@NonNull UUID twincodeOutboundId, long refreshPeriod, @NonNull Consumer<TwincodeOutbound> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwincode: twincodeOutboundId=" + twincodeOutboundId + " refrehsPeriod=" + refreshPeriod);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final TwincodeOutbound twincodeOutbound = mServiceProvider.loadTwincode(twincodeOutboundId);
        if (twincodeOutbound != null && twincodeOutbound.isKnown()) {
            complete.onGet(ErrorCode.SUCCESS, twincodeOutbound);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new GetTwincodePendingRequest(twincodeOutboundId, refreshPeriod, complete));
        }

        final GetTwincodeIQ getTwincodeIQ = new GetTwincodeIQ(IQ_GET_TWINCODE_SERIALIZER, requestId, twincodeOutboundId);
        sendDataPacket(getTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void getSignedTwincode(@NonNull UUID twincodeOutboundId, @NonNull String publicKey,
                                  @NonNull TrustMethod trust, @NonNull Consumer<TwincodeOutbound> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSignedTwincode: twincodeOutboundId=" + twincodeOutboundId + " publicKey=" + publicKey);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new GetTwincodePendingRequest(twincodeOutboundId, publicKey, 0, null, trust, complete));
        }

        final GetTwincodeIQ getTwincodeIQ = new GetTwincodeIQ(IQ_GET_TWINCODE_SERIALIZER, requestId, twincodeOutboundId);
        sendDataPacket(getTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void getSignedTwincodeWithSecret(@NonNull UUID twincodeOutboundId, @NonNull String publicKey, int keyIndex, @Nullable byte[] secretKey,
                                            @NonNull TrustMethod trust, @NonNull Consumer<TwincodeOutbound> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSignedTwincodeWithSecret: twincodeOutboundId=" + twincodeOutboundId + " publicKey=" + publicKey);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new GetTwincodePendingRequest(twincodeOutboundId, publicKey, keyIndex, secretKey, trust, complete));
        }

        final GetTwincodeIQ getTwincodeIQ = new GetTwincodeIQ(IQ_GET_TWINCODE_SERIALIZER, requestId, twincodeOutboundId);
        sendDataPacket(getTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void refreshTwincode(@NonNull TwincodeOutbound twincodeOutbound, @NonNull Consumer<List<AttributeNameValue>> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshTwincode: twincodeOutbound=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new RefreshTwincodePendingRequest(twincodeOutbound, complete));
        }

        final GetTwincodeIQ getTwincodeIQ = new GetTwincodeIQ(IQ_GET_TWINCODE_SERIALIZER, requestId, twincodeOutbound.getId());
        sendDataPacket(getTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void updateTwincode(@NonNull TwincodeOutbound twincodeOutbound,
                               @NonNull List<AttributeNameValue> attributes,
                               @Nullable List<String> deleteAttributeNames,
                               @NonNull Consumer<TwincodeOutbound> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateTwincode: twincodeOutbound=" + twincodeOutbound +
                    " attributes=" + attributes + " deleteAttributeNames=" + deleteAttributeNames);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }
        if (!(twincodeOutbound instanceof TwincodeOutboundImpl)) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        // If the twincode is signed, build a signature from the final attributes, including
        // the image SHA if there is one.
        final TwincodeOutboundImpl twincodeOutboundImpl = (TwincodeOutboundImpl) twincodeOutbound;
        final byte[] signature;
        List<AttributeNameValue> finalAttributes;
        final boolean isSigned = twincodeOutbound.isSigned();
        if (isSigned) {
            finalAttributes = twincodeOutboundImpl.getAttributes(attributes, deleteAttributeNames);

            signature = mCryptoService.sign(twincodeOutbound, finalAttributes);
            if (signature == null) {
                complete.onGet(ErrorCode.BAD_SIGNATURE, null);
                return;
            }
        } else {
            signature = null;
            finalAttributes = attributes;
        }
        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new UpdateTwincodePendingRequest(twincodeOutbound, finalAttributes, isSigned, complete));
        }

        final UpdateTwincodeIQ updateTwincodeIQ = new UpdateTwincodeIQ(IQ_UPDATE_TWINCODE_SERIALIZER, requestId,
                twincodeOutbound.getId(), finalAttributes, deleteAttributeNames, signature);
        sendDataPacket(updateTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void invokeTwincode(@NonNull TwincodeOutbound twincodeOutbound, int options, @NonNull String action,
                               @Nullable List<AttributeNameValue> attributes, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "invokeTwincode: twincodeOutbound=" + twincodeOutbound
                    + " action=" + action + " attributes=" + attributes);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new InvokePendingRequest(twincodeOutbound, complete));
        }

        options = options & (INVOKE_URGENT | INVOKE_WAKEUP);
        final InvokeTwincodeIQ invokeTwincodeIQ = new InvokeTwincodeIQ(IQ_INVOKE_TWINCODE_SERIALIZER, requestId, options,null,
                twincodeOutbound.getId(), action, attributes, null, 0,0);
        sendDataPacket(invokeTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void secureInvokeTwincode(@NonNull TwincodeOutbound cipherTwincode,
                                     @NonNull TwincodeOutbound senderTwincode,
                                     @NonNull TwincodeOutbound receiverTwincode,
                                     int options, @NonNull String action,
                                     @NonNull List<AttributeNameValue> attributes, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "secureInvokeTwincode: senderTwincode=" + senderTwincode
                    + " receiverTwincode=" + receiverTwincode
                    + " action=" + action + " attributes=" + attributes);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }
        if (!(cipherTwincode instanceof TwincodeOutboundImpl)
                || !(senderTwincode instanceof TwincodeOutboundImpl)
                || !(receiverTwincode instanceof TwincodeOutboundImpl)
                || attributes.isEmpty()) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        final TwincodeOutboundImpl cipherTwincodeOutboundImpl = (TwincodeOutboundImpl) cipherTwincode;
        final TwincodeOutboundImpl senderTwincodeOutboundImpl = (TwincodeOutboundImpl) senderTwincode;
        final TwincodeOutboundImpl receiverTwincodeOutboundImpl = (TwincodeOutboundImpl) receiverTwincode;
        if (!cipherTwincodeOutboundImpl.isSigned() || !senderTwincodeOutboundImpl.isSigned() || !receiverTwincodeOutboundImpl.isSigned()) {
            complete.onGet(ErrorCode.NOT_AUTHORIZED_OPERATION, null);
            return;
        }

        final CryptoService.CipherResult cipherResult = mCryptoService.encrypt(cipherTwincodeOutboundImpl, senderTwincodeOutboundImpl,
                receiverTwincodeOutboundImpl, (options & (CREATE_SECRET | CREATE_NEW_SECRET | SEND_SECRET)), attributes);
        if (cipherResult.errorCode != ErrorCode.SUCCESS) {
            complete.onGet(cipherResult.errorCode, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new InvokePendingRequest(receiverTwincodeOutboundImpl, complete));
        }

        options = options & (INVOKE_URGENT | INVOKE_WAKEUP);
        final InvokeTwincodeIQ invokeTwincodeIQ = new InvokeTwincodeIQ(IQ_INVOKE_TWINCODE_SERIALIZER, requestId, options,null,
                receiverTwincode.getId(), action, null, cipherResult.data, cipherResult.length,0);
        sendDataPacket(invokeTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void createPrivateKey(@NonNull TwincodeInbound twincodeInbound,
                                 @NonNull Consumer<TwincodeOutbound> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createPrivateKey: twincodeInbound=" + twincodeInbound);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        // Create the private keys for the twincode if they don't exist yet.
        final TwincodeOutboundImpl twincodeOutboundImpl = (TwincodeOutboundImpl) twincodeInbound.getTwincodeOutbound();
        final ErrorCode errorCode = mCryptoService.createPrivateKey(twincodeInbound, twincodeOutboundImpl);
        if (errorCode != ErrorCode.SUCCESS) {
            complete.onGet(errorCode, null);
            return;
        }

        // Build a signature from the final attributes, including the image SHA if there is one.
        // The `sign()` method will update finalAttributes to insert the avatarId attribute for the server if there is one.
        final List<AttributeNameValue> attributes = new ArrayList<>();
        final List<AttributeNameValue> finalAttributes = twincodeOutboundImpl.getAttributes(attributes, null);
        final byte[] signature = mCryptoService.sign(twincodeOutboundImpl, finalAttributes);
        if (signature == null) {
            complete.onGet(ErrorCode.BAD_SIGNATURE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new UpdateTwincodePendingRequest(twincodeOutboundImpl, finalAttributes, true, complete));
        }

        final UpdateTwincodeIQ updateTwincodeIQ = new UpdateTwincodeIQ(IQ_UPDATE_TWINCODE_SERIALIZER, requestId,
                twincodeOutboundImpl.getId(), finalAttributes, null, signature);
        sendDataPacket(updateTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void associateTwincodes(@NonNull TwincodeOutbound twincodeOutbound,
                                   @Nullable TwincodeOutbound previousPeerTwincodeOutbound,
                                   @NonNull TwincodeOutbound peerTwincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "associateTwincodes twincodeOutbound=" + twincodeOutbound + " peerTwincodeOutbound=" + peerTwincodeOutbound);
        }

        mServiceProvider.associateTwincodes((TwincodeOutboundImpl) twincodeOutbound, previousPeerTwincodeOutbound, (TwincodeOutboundImpl) peerTwincodeOutbound);
    }

    /**
     * Mark the two twincode relation as certified by the given trust process.
     *
     * @param twincodeOutbound our twincode.
     * @param peerTwincodeOutbound the peer twincode.
     * @param trustMethod the certification method that was used.
     */
    @Override
    public void setCertified(@NonNull TwincodeOutbound twincodeOutbound,
                             @NonNull TwincodeOutbound peerTwincodeOutbound,
                             @NonNull TrustMethod trustMethod) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setCertified twincodeOutbound=" + twincodeOutbound + " peerTwincodeOutbound=" + peerTwincodeOutbound
                + " trustMethod=" + trustMethod);
        }

        mServiceProvider.setCertified((TwincodeOutboundImpl) twincodeOutbound, (TwincodeOutboundImpl) peerTwincodeOutbound, trustMethod);
    }

    @Override
    public String getPeerId(@NonNull UUID peerTwincodeOutboundId, @NonNull UUID twincodeOutboundId) {

        return peerTwincodeOutboundId + "@" + mServiceJid + "/" + twincodeOutboundId;
    }

    @Override
    public void evictTwincode(@NonNull UUID twincodeOutboundId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "evictTwincode twincodeOutboundId=" + twincodeOutboundId);
        }

        // Remove the twincode from our local database to make sure the next getTwincode()
        // will query the server to retrieve the new information.
        mServiceProvider.evictTwincode(null, twincodeOutboundId);
    }

    @Override
    public void evictTwincodeOutbound(@NonNull TwincodeOutbound twincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "evictTwincodeOutbound twincodeOutbound=" + twincodeOutbound);
        }

        // Remove the twincode from our local database to make sure the next getTwincode()
        // will query the server to retrieve the new information.
        mServiceProvider.evictTwincode(twincodeOutbound, null);
    }

    @Override
    public void createInvitationCode(@NonNull TwincodeOutbound twincodeOutbound, int validityPeriod, @NonNull Consumer<InvitationCode> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createInvitationCode: twincodeOutboundId=" + twincodeOutbound + " validityPeriod=" + validityPeriod);
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new CreateInvitationCodePendingRequest(twincodeOutbound, complete));
        }

        String pubKey = mCryptoService.getPublicKey(twincodeOutbound);

        final CreateInvitationCodeIQ iq = new CreateInvitationCodeIQ(IQ_CREATE_INVITATION_CODE_SERIALIZER, requestId, twincodeOutbound.getId(), validityPeriod, pubKey);

        sendDataPacket(iq, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void getInvitationCode(@NonNull String code, @NonNull Consumer<Pair<TwincodeOutbound, String>> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getInvitationCode: code=" + code);
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new GetInvitationCodePendingRequest(code, complete));
        }

        final GetInvitationCodeIQ iq = new GetInvitationCodeIQ(IQ_GET_INVITATION_CODE_SERIALIZER, requestId, code);
        sendDataPacket(iq, DEFAULT_REQUEST_TIMEOUT);
    }

    private void createAuthenticateURI(@NonNull TwincodeOutbound twincodeOutbound,
                                       @NonNull Consumer<TwincodeURI> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createAuthenticateURI twincodeOutbound=" + twincodeOutbound);
        }

        final Pair<ErrorCode, String> signature = mCryptoService.signAuthenticate(twincodeOutbound);
        if (signature.first != ErrorCode.SUCCESS) {
            complete.onGet(signature.first, null);
            return;
        }

        final int pos = signature.second.indexOf('.');
        final byte[] hash = Utils.decodeBase64URL(signature.second.substring(0, pos) + "=");
        if (hash == null || hash.length != 32) {
            complete.onGet(ErrorCode.LIBRARY_ERROR, null);
            return;
        }
        final String label = Utils.bytesToHex(hash);
        complete.onGet(ErrorCode.SUCCESS, new TwincodeURI(TwincodeURI.Kind.Authenticate, Twincode.NOT_DEFINED,
                null, signature.second, "https://" + TwincodeURI.AUTHENTICATE_ACTION + "/" + signature.second, label));
    }

    @Override
    public void createURI(@NonNull TwincodeURI.Kind kind, @NonNull TwincodeOutbound twincodeOutbound,
                          @NonNull Consumer<TwincodeURI> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createURI kind=" + kind + " twincodeOutbound=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }
        if (!(twincodeOutbound instanceof TwincodeOutboundImpl)) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        if (kind == TwincodeURI.Kind.Authenticate) {
            createAuthenticateURI(twincodeOutbound, complete);
            return;
        }

        final String pubKey;
        if (twincodeOutbound.isSigned()) {
            pubKey = mCryptoService.getPublicKey(twincodeOutbound);
        } else {
            pubKey = null;
        }

        final UUID twincodeId = twincodeOutbound.getId();
        String uri;
        final String label;
        switch (kind) {
            case Invitation:
                uri = TwincodeURI.INVITE_ACTION + "/" + Utils.toString(twincodeId);
                label = twincodeId.toString();
                if (pubKey != null) {
                    uri = uri + "." + pubKey;
                }
                break;

            case Call:
                uri = TwincodeURI.CALL_ACTION + TwincodeURI.CALL_PATH + Utils.toString(twincodeId);
                label = Utils.toString(twincodeId);
                break;

            case Transfer:
                uri = TwincodeURI.TRANSFER_ACTION + TwincodeURI.CALL_PATH + Utils.toString(twincodeId);
                label = Utils.toString(twincodeId);
                break;

            case AccountMigration:
                uri = TwincodeURI.ACCOUNT_MIGRATION_ACTION + "/?id=" + twincodeId;
                label = Utils.toString(twincodeId);
                if (pubKey != null) {
                    uri = uri + "&pubKey=" + pubKey;
                }
                break;

            case SpaceCard:
                uri = TwincodeURI.INVITE_ACTION + TwincodeURI.SPACE_PATH + "?id=" + twincodeId;
                label = Utils.toString(twincodeId);
                break;

            default:
                complete.onGet(ErrorCode.BAD_REQUEST, null);
                return;
        }

        complete.onGet(ErrorCode.SUCCESS, new TwincodeURI(kind, twincodeId, null, pubKey, "https://" + uri, label));
    }

    @Override
    public void parseURI(@NonNull Uri uri, @NonNull Consumer<TwincodeURI> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "parseURI uri=" + uri);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final String host = uri.getHost();
        String path = uri.getPath();
        UUID twincodeId;
        String label;
        String pubKey = null;
        String options = null;
        int sep;
        if (host == null) {
            if (path == null) {
                path = uri.toString();
            }

            sep = path.indexOf('.');
            if (sep > 0) {
                twincodeId = Utils.toUUID(path.substring(0, sep));
                options = path.substring(sep + 1);
                sep = options.indexOf('.');
                if (sep > 0) {
                    pubKey = options.substring(0, sep);
                    options = options.substring(sep + 1);
                } else if (options.length() >= 30) {
                    pubKey = options;
                    options = null;
                }
            } else {
                twincodeId = Utils.toUUID(path);
            }
            if (twincodeId == null) {
                SNIProxyDescriptor proxyDescriptor = SNIProxyDescriptor.create(path);
                if (proxyDescriptor != null && Utils.isValidHostname(proxyDescriptor.getAddress())) {
                    label = path;
                    options = proxyDescriptor.getDescriptor();
                    complete.onGet(ErrorCode.SUCCESS, new TwincodeURI(TwincodeURI.Kind.Proxy, Twincode.NOT_DEFINED, options, null, uri.toString(), label));
                } else {
                    complete.onGet(ErrorCode.BAD_REQUEST, null);
                }
                return;
            }
            label = path;
            complete.onGet(ErrorCode.SUCCESS, new TwincodeURI(TwincodeURI.Kind.Invitation, twincodeId, options, pubKey, uri.toString(), label));
            return;
        }

        TwincodeURI.Kind kind;
        switch (host) {
            case TwincodeURI.INVITE_ACTION:
                kind = TwincodeURI.Kind.Invitation;
                break;

            case TwincodeURI.CALL_ACTION:
                kind = TwincodeURI.Kind.Call;
                break;

            case TwincodeURI.TRANSFER_ACTION:
                kind = TwincodeURI.Kind.Transfer;
                break;

            case TwincodeURI.ACCOUNT_MIGRATION_ACTION:
                kind = TwincodeURI.Kind.AccountMigration;
                break;

            case TwincodeURI.AUTHENTICATE_ACTION:
                kind = TwincodeURI.Kind.Authenticate;
                break;

            case TwincodeURI.PROXY_ACTION:
                kind = TwincodeURI.Kind.Proxy;
                break;

            default:
                complete.onGet(ErrorCode.FEATURE_NOT_IMPLEMENTED, null);
                return;
        }

        if (kind == TwincodeURI.Kind.Authenticate) {
            if (path == null) {
                complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
            sep = path.indexOf('.');
            if (sep < 0) {
                complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
            int sep2 = path.indexOf('.', sep + 1);
            if (sep2 < 0) {
                complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
            int first = 0;
            while (first < path.length() && path.charAt(first) == '/') {
                first++;
            }
            pubKey = path.substring(first);
            label = pubKey.substring(0, sep);
            twincodeId = Twincode.NOT_DEFINED;
        } else if (kind == TwincodeURI.Kind.Proxy) {
            if (path == null) {
                complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
            int first = 0;
            while (first < path.length() && path.charAt(first) == '/') {
                first++;
            }
            label = path.substring(first);
            if (first + 1 < path.length() && path.charAt(first + 1) == '/') {
                first += 2;
            }
            if (path.indexOf('/', first + 1) > 0) {
                complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
            options = path.substring(first);

            // Verify the format of the proxy description.
            SNIProxyDescriptor proxyDescriptor = SNIProxyDescriptor.create(options);
            if (proxyDescriptor == null) {
                complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }

            // Verify the validity of the hostname (it must not be done by create() so we have to do it here).
            if (!Utils.isValidHostname(proxyDescriptor.getAddress())) {
                complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
            twincodeId = Twincode.NOT_DEFINED;

        } else if (path != null && path.startsWith(TwincodeURI.CALL_PATH)) {
            if (kind != TwincodeURI.Kind.Call && kind != TwincodeURI.Kind.Transfer) {
                complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
            String value = path.substring(TwincodeURI.CALL_PATH.length());
            twincodeId = Utils.toUUID(value);
            label = value;
        } else if (path != null && path.startsWith(TwincodeURI.SPACE_PATH)) {
            if (kind != TwincodeURI.Kind.Invitation) {
                complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
            kind = TwincodeURI.Kind.SpaceCard;
            String value = path.substring(TwincodeURI.SPACE_PATH.length());
            twincodeId = Utils.toUUID(value);
            label = value;
        } else {
            if (path != null && !"/".equals(path) && !path.isEmpty()) {
                int first = 0;
                while (first < path.length() && path.charAt(first) == '/') {
                    first++;
                }
                sep = path.indexOf('.');
                if (sep == 0) {
                    complete.onGet(ErrorCode.BAD_REQUEST, null);
                    return;
                }
                label = sep > 0 ? path.substring(first, sep) : path.substring(first);
                twincodeId = Utils.toUUID(label);
                if (sep > 0) {
                    pubKey = path.substring(sep + 1);
                    sep = pubKey.indexOf('.');
                    if (sep > 0) {
                        options = pubKey.substring(sep + 1);
                        pubKey = pubKey.substring(0, sep);
                    }
                }
            } else {
                String param = uri.getQueryParameter(TwincodeURI.PARAM_ID);
                if (param == null) {
                    param = uri.getQueryParameter("id");
                }
                if (param == null) {
                    complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                    return;
                }
                sep = param.indexOf('.');
                if (sep > 0) {
                    label = param.substring(0, sep);
                    twincodeId = Utils.toUUID(label);
                    // Check for a subscription option vs a public key.
                    param = param.substring(sep + 1);
                    sep = param.indexOf('.');
                    if (sep < 0 && param.length() < 30) {
                        options = param;
                    } else if (sep > 0) {
                        pubKey = param.substring(0, sep);
                        options = param.substring(sep + 1);
                    } else {
                        pubKey = param;
                    }
                } else {
                    twincodeId = Utils.toUUID(param);
                    label = param;
                }
            }
        }
        if (twincodeId == null) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }
        complete.onGet(ErrorCode.SUCCESS, new TwincodeURI(kind, twincodeId, options, pubKey, uri.toString(), label));
    }

    //
    // Private Methods
    //

    /**
     * Response received after GetTwincodeIQ operation.
     *
     * @param iq the GetTwincodeIQ response.
     */
    private void onGetTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetTwincode: iq=" + iq);
        }

        if (!(iq instanceof OnGetTwincodeIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final OnGetTwincodeIQ onGetTwincodeIQ = (OnGetTwincodeIQ) iq;
        final long modificationDate = onGetTwincodeIQ.getModificationDate();
        final List<AttributeNameValue> attributes = onGetTwincodeIQ.getAttributes();
        final byte[] signature = onGetTwincodeIQ.getSignature();

        if (request instanceof GetTwincodePendingRequest) {
            GetTwincodePendingRequest getRequest = (GetTwincodePendingRequest) request;

            byte[] pubKey = null;
            byte[] encryptKey = null;
            if (signature != null && getRequest.pubKey != null) {
                CryptoServiceImpl.VerifyResult result = mCryptoService.verify(getRequest.pubKey, getRequest.twincodeId, attributes, signature);
                if (result.errorCode != ErrorCode.SUCCESS) {
                    getRequest.complete.onGet(result.errorCode, null);
                    return;
                }
                pubKey = result.publicSigningKey;
                encryptKey = result.publicEncryptionKey;
            }
            final TwincodeOutbound twincodeOutbound = mServiceProvider.importTwincode(getRequest.twincodeId, attributes,
                    pubKey, encryptKey, getRequest.keyIndex, getRequest.secretKey, getRequest.trusted, modificationDate, getRequest.refreshPeriod);

            getRequest.complete.onGet(twincodeOutbound != null ? ErrorCode.SUCCESS : ErrorCode.NO_STORAGE_SPACE, twincodeOutbound);
        } else {
            RefreshTwincodePendingRequest refreshRequest = (RefreshTwincodePendingRequest) request;
            if (signature != null) {
                CryptoServiceImpl.VerifyResult result = mCryptoService.verify(refreshRequest.twincodeOutbound, attributes, signature);
                // Ignore TLBaseServiceErrorCodeNoPublicKey: we're just checking for twincode attributes updates, and if we don't have a public key yet we have no way to check the signature.
                if (result.errorCode != ErrorCode.SUCCESS && result.errorCode != ErrorCode.NO_PUBLIC_KEY) {
                    refreshRequest.complete.onGet(result.errorCode, null);
                    return;
                }
            }
            List<AttributeNameValue> previousAttributes = new ArrayList<>();
            mServiceProvider.refreshTwincodeOutbound(refreshRequest.twincodeOutbound, attributes,
                    previousAttributes, modificationDate);

            refreshRequest.complete.onGet(ErrorCode.SUCCESS, previousAttributes);
        }
    }

    /**
     * Response received after UpdateTwincodeIQ operation.
     *
     * @param iq the UpdateTwincodeIQ response.
     */
    private void onUpdateTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincode: iq=" + iq);
        }

        if (!(iq instanceof OnUpdateTwincodeIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final UpdateTwincodePendingRequest request;
        synchronized (mPendingRequests) {
            request = (UpdateTwincodePendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final OnUpdateTwincodeIQ onUpdateTwincodeIQ = (OnUpdateTwincodeIQ) iq;
        final long modificationDate = onUpdateTwincodeIQ.getModificationDate();
        final TwincodeOutboundImpl twincodeOutbound = request.twincodeOutbound;
        mServiceProvider.updateTwincode(twincodeOutbound, request.attributes, modificationDate, request.signed);
        request.complete.onGet(ErrorCode.SUCCESS, twincodeOutbound);
    }

    /**
     * Response received after RefreshTwincodeIQ operation.
     *
     * @param iq the RefreshTwincodeIQ response.
     */
    private void onRefreshTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onRefreshTwincode: iq=" + iq);
        }

        if (!(iq instanceof OnRefreshTwincodeIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final RefreshTwincodesPendingRequest request;
        synchronized (mPendingRequests) {
            request = (RefreshTwincodesPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final OnRefreshTwincodeIQ onRefreshTwincodeIQ = (OnRefreshTwincodeIQ) iq;
        final long timestamp = onRefreshTwincodeIQ.getTimestamp();

        // Delete the twincodes and cleanup repository objects, conversations and notifications.
        final List<UUID> deleteList = onRefreshTwincodeIQ.getDeleteTwincodeList();
        if (deleteList != null) {
            for (UUID twincodeId : deleteList) {
                Long id = request.refreshList.remove(twincodeId);
                if (id != null) {
                    mServiceProvider.deleteTwincode(id);
                }
            }
        }

        // Update the twincodes.
        List<RefreshTwincodeInfo> updateList = onRefreshTwincodeIQ.getUpdateTwincodeList();
        if (updateList != null) {
            for (RefreshTwincodeInfo twincode : updateList) {
                Long id = request.refreshList.remove(twincode.twincodeOutboundId);
                if (id == null) {
                    continue;
                }

                final List<AttributeNameValue> previousAttributes = new ArrayList<>();
                final TwincodeOutbound twincodeOutbound = mServiceProvider.refreshTwincode(id, twincode.attributes,
                        previousAttributes, timestamp);
                if (twincodeOutbound != null && !previousAttributes.isEmpty()) {
                    for (TwincodeOutboundService.ServiceObserver serviceObserver : getServiceObservers()) {
                        mTwinlifeExecutor.execute(() -> serviceObserver.onRefreshTwincode(twincodeOutbound, previousAttributes));
                    }
                }
            }
        }

        final long now = System.currentTimeMillis();
        mServiceProvider.updateRefreshTimestamp(request.refreshList.values(), timestamp, now);

        updateRefreshJob();
    }

    /**
     * Response received after InvokeTwincodeIQ operation.
     *
     * @param iq the InvokeTwincodeIQ response.
     */
    private void onInvokeTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode: iq=" + iq);
        }

        if (!(iq instanceof InvocationIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final InvokePendingRequest request;
        synchronized (mPendingRequests) {
            request = (InvokePendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        InvocationIQ onInvokeIQ = (InvocationIQ) iq;
        request.complete.onGet(ErrorCode.SUCCESS, onInvokeIQ.getInvocationId());
    }

    private void onCreateInvitationCode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateInvitationCode: iq=" + iq);
        }

        if (!(iq instanceof OnCreateInvitationCodeIQ)) {
            return;
        }

        OnCreateInvitationCodeIQ icIQ = (OnCreateInvitationCodeIQ) iq;

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final CreateInvitationCodePendingRequest request;
        synchronized (mPendingRequests) {
            request = (CreateInvitationCodePendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        String publicKey = mCryptoService.getPublicKey(request.twincodeOutbound);

        InvitationCode invitationCode = new InvitationCodeImpl(icIQ.getCreationDate(), icIQ.getValidityPeriod(), icIQ.getCode(), icIQ.getTwincodeId(), publicKey);

        request.complete.onGet(ErrorCode.SUCCESS, invitationCode);
    }

    private void onGetInvitationCode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetInvitationCode: iq=" + iq);
        }

        if (!(iq instanceof OnGetInvitationCodeIQ)) {
            return;
        }

        OnGetInvitationCodeIQ icIQ = (OnGetInvitationCodeIQ) iq;

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final GetInvitationCodePendingRequest request;
        synchronized (mPendingRequests) {
            request = (GetInvitationCodePendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        String publicKey = icIQ.getPublicKey();
        byte[] signature = icIQ.getSignature();

        TrustMethod trustMethod = TrustMethod.NONE;
        byte[] signingKey = null;
        byte[] encryptionKey = null;

        if (publicKey != null && signature != null) {
            CryptoService.VerifyResult result = mCryptoService.verify(publicKey, icIQ.getTwincodeId(), icIQ.getAttributes(), signature);

            if (result.errorCode != ErrorCode.SUCCESS) {
                request.complete.onGet(result.errorCode, new Pair<>(null, null));
                return;
            }

            signingKey = result.publicSigningKey;
            encryptionKey = result.publicEncryptionKey;
            trustMethod = TrustMethod.INVITATION_CODE;
        }

        TwincodeOutbound twincodeOutbound = mServiceProvider.importTwincode(icIQ.getTwincodeId(), icIQ.getAttributes(), signingKey, encryptionKey, 0, null, trustMethod, icIQ.getModificationDate(), REFRESH_PERIOD);

        request.complete.onGet(twincodeOutbound != null ? ErrorCode.SUCCESS : ErrorCode.NO_STORAGE_SPACE, new Pair<>(twincodeOutbound, publicKey));
    }

    private void refreshTwincodes() {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshTwincodes");
        }

        mRefreshJob = null;
        mPreviousRefreshDate = System.currentTimeMillis();

        final TwincodeOutboundServiceProvider.RefreshInfo refreshList = mServiceProvider.getRefreshList();
        if (refreshList.twincodes == null || refreshList.twincodes.isEmpty()) {

            updateRefreshJob();
            return;
        }

        long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new RefreshTwincodesPendingRequest(refreshList.twincodes));
        }

        RefreshTwincodeIQ refreshTwincodeIQ = new RefreshTwincodeIQ(IQ_REFRESH_TWINCODE_SERIALIZER, requestId, refreshList.twincodes, refreshList.timestamp);

        sendDataPacket(refreshTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    protected void onErrorPacket(@NonNull BinaryErrorPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
            if (request == null) {
                return;
            }
        }

        // The object no longer exists on the server, remove it from our local database.
        if (request instanceof GetTwincodePendingRequest) {
            final GetTwincodePendingRequest getRequest = (GetTwincodePendingRequest) request;
            if (iq.getErrorCode() == ErrorCode.ITEM_NOT_FOUND) {
                evictTwincode(getRequest.twincodeId);
            }
            getRequest.complete.onGet(iq.getErrorCode(), null);

        } else if (request instanceof RefreshTwincodePendingRequest) {
            final RefreshTwincodePendingRequest refreshRequest = (RefreshTwincodePendingRequest) request;
            if (iq.getErrorCode() == ErrorCode.ITEM_NOT_FOUND) {
                evictTwincodeOutbound(refreshRequest.twincodeOutbound);
            }
            refreshRequest.complete.onGet(iq.getErrorCode(), null);

        } else if (request instanceof UpdateTwincodePendingRequest) {
            final UpdateTwincodePendingRequest updateRequest = (UpdateTwincodePendingRequest) request;
            if (iq.getErrorCode() == ErrorCode.ITEM_NOT_FOUND) {
                mServiceProvider.deleteObject(updateRequest.twincodeOutbound);
            }
            updateRequest.complete.onGet(iq.getErrorCode(), null);

        } else if (request instanceof RefreshTwincodesPendingRequest) {
            updateRefreshJob();

        } else if (request instanceof InvokePendingRequest) {
            final InvokePendingRequest invokeRequest = (InvokePendingRequest) request;
            if (iq.getErrorCode() == ErrorCode.ITEM_NOT_FOUND) {
                evictTwincodeOutbound(invokeRequest.twincodeId);
            }
            invokeRequest.complete.onGet(iq.getErrorCode(), null);
        } else if (request instanceof GetInvitationCodePendingRequest) {
            final GetInvitationCodePendingRequest invitationCodePendingRequest = (GetInvitationCodePendingRequest) request;
            invitationCodePendingRequest.complete.onGet(iq.getErrorCode(), null);
        }
    }

    private void updateRefreshJob() {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateRefreshJob");
        }

        if (mRefreshJob != null) {
            mRefreshJob.cancel();
            mRefreshJob = null;
        }

        if (!isTwinlifeOnline()) {
            return;
        }

        long deadline = mServiceProvider.getRefreshDeadline();

        // Don't schedule the refresh twincode too often.
        if (mPreviousRefreshDate + MIN_REFRESH_DELAY < deadline) {
            deadline = mPreviousRefreshDate + MIN_REFRESH_DELAY;
        }

        if (deadline > 0) {
            mRefreshJob = mJobService.scheduleAfter("Refresh twincodes outbound", this::refreshTwincodes, deadline, JobService.Priority.REPORT);
        }
    }
}
