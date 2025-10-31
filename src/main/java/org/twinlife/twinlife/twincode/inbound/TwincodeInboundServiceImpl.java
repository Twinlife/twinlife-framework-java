/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.inbound;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeInboundService;
import org.twinlife.twinlife.TwincodeInvocation;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.twincode.AcknowledgeInvocationIQ;
import org.twinlife.twinlife.twincode.GetTwincodeIQ;
import org.twinlife.twinlife.twincode.InvokeTwincodeIQ;
import org.twinlife.twinlife.twincode.OnGetTwincodeIQ;
import org.twinlife.twinlife.twincode.OnUpdateTwincodeIQ;
import org.twinlife.twinlife.twincode.UpdateTwincodeIQ;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Logger;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class TwincodeInboundServiceImpl extends BaseServiceImpl<BaseService.ServiceObserver> implements TwincodeInboundService {
    private static final String LOG_TAG = "TwincodeInboundServi...";
    private static final boolean DEBUG = false;

    private static final UUID GET_TWINCODE_SCHEMA_ID = UUID.fromString("22903c9e-545f-44f4-948b-908b3153cfc2");
    private static final UUID ON_GET_TWINCODE_SCHEMA_ID = UUID.fromString("177b0d15-2d19-4e89-8e16-701f7266ab48");
    private static final UUID UPDATE_TWINCODE_SCHEMA_ID = UUID.fromString("77a5bf4e-8f4c-4772-b100-4344d44fadde");
    private static final UUID ON_UPDATE_TWINCODE_SCHEMA_ID = UUID.fromString("887BF747-7995-456E-AA72-34B7E7C53160");
    private static final UUID TRIGGER_PENDING_INVOCATIONS_SCHEMA_ID = UUID.fromString("266f3d93-1782-491c-b6cb-28cc23df4fdf");
    private static final UUID ON_TRIGGER_PENDING_INVOCATIONS_SCHEMA_ID = UUID.fromString("b70ac369-54c9-4f42-8217-59e6f52bb8fc");
    private static final UUID ACKNOWLEDGE_INVOCATION_SCHEMA_ID = UUID.fromString("eee63e5e-8af1-41e9-9a1b-79806a0056a2");
    private static final UUID ON_ACKNOWLEDGE_INVOCATION_SCHEMA_ID = UUID.fromString("5d57d54b-2d03-4ad7-9a77-75b9b3373715");
    private static final UUID INVOKE_TWINCODE_SCHEMA_ID = UUID.fromString("c74e79e6-5157-4fb4-bad8-2de545711fa0");
    // private static final UUID ON_INVOKE_TWINCODE_SCHEMA_ID = UUID.fromString("35d11e72-84d7-4a3b-badd-9367ef8c9e43");
    private static final UUID BIND_TWINCODE_SCHEMA_ID = UUID.fromString("afa1a19e-2af9-409d-8502-4a77e29b1d91");
    private static final UUID ON_BIND_TWINCODE_SCHEMA_ID = UUID.fromString("4ffd7362-498d-4584-9d93-49d7514a6c32");
    private static final UUID UNBIND_TWINCODE_SCHEMA_ID = UUID.fromString("7fad2e67-c6b9-4925-96ed-9af3bb83d19f");
    private static final UUID ON_UNBIND_TWINCODE_SCHEMA_ID = UUID.fromString("3d791a6d-6ad0-438c-89cf-92a822a85846");

    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_GET_TWINCODE_SERIALIZER = GetTwincodeIQ.createSerializer(GET_TWINCODE_SCHEMA_ID, 2);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_GET_TWINCODE_SERIALIZER = OnGetTwincodeIQ.createSerializer(ON_GET_TWINCODE_SCHEMA_ID, 2);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_UPDATE_TWINCODE_SERIALIZER = UpdateTwincodeIQ.createSerializer(UPDATE_TWINCODE_SCHEMA_ID, 2);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UPDATE_TWINCODE_SERIALIZER = OnUpdateTwincodeIQ.createSerializer(ON_UPDATE_TWINCODE_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_TRIGGER_PENDING_INVOCATIONS_SERIALIZER = TriggerPendingInvocationsIQ.createSerializer(TRIGGER_PENDING_INVOCATIONS_SCHEMA_ID, 2);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_TRIGGER_PENDING_INVOCATIONS_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_TRIGGER_PENDING_INVOCATIONS_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ACKNOWLEDGE_INVOCATION_SERIALIZER = AcknowledgeInvocationIQ.createSerializer_2(ACKNOWLEDGE_INVOCATION_SCHEMA_ID, 2);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_ACKNOWLEDGE_INVOCATION_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_ACKNOWLEDGE_INVOCATION_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_INVOKE_TWINCODE_SERIALIZER = InvokeTwincodeIQ.createSerializer(INVOKE_TWINCODE_SCHEMA_ID, 2);
    // public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_INVOKE_TWINCODE_SERIALIZER = InvocationIQ.createSerializer(ON_INVOKE_TWINCODE_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_BIND_TWINCODE_SERIALIZER = GetTwincodeIQ.createSerializer(BIND_TWINCODE_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_UNBIND_TWINCODE_SERIALIZER = GetTwincodeIQ.createSerializer(UNBIND_TWINCODE_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_BIND_TWINCODE_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_BIND_TWINCODE_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UNBIND_TWINCODE_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_UNBIND_TWINCODE_SCHEMA_ID, 1);

    // Record a list of invocations being processed for a specific twincode inbound id.
    // A list of code blocks is recorded in case the `waitInvocationsForTwincode()` wants
    // to execute some code and it will be executed when every invocation for that twincode
    // has been processed.
    private static class PendingInvocation {
        final UUID twincodeId;
        final WeakReference<RepositoryObject> subject;
        final List<UUID> invocationList;
        @Nullable
        List<Runnable> waitingRunnables;

        PendingInvocation(@NonNull UUID twincodeId, @NonNull UUID invocationId, @NonNull RepositoryObject subject) {
            this.twincodeId = twincodeId;
            this.subject = new WeakReference<>(subject);
            this.invocationList = new ArrayList<>();
            this.invocationList.add(invocationId);
            this.waitingRunnables = null;
        }
    }

    private static class PendingRequest {
    }

    private static final class BindUnbindPendingRequest extends PendingRequest {
        @NonNull
        final TwincodeInbound twincode;
        @NonNull
        final Consumer<UUID> complete;

        BindUnbindPendingRequest(@NonNull TwincodeInbound twincode, @NonNull Consumer<UUID> complete) {
            this.twincode = twincode;
            this.complete = complete;
        }
    }

    private static final class GetTwincodePendingRequest extends PendingRequest {
        @NonNull
        final UUID twincodeId;
        @NonNull
        final TwincodeOutbound twincodeOutbound;
        @NonNull
        final Consumer<TwincodeInbound> complete;

        GetTwincodePendingRequest(@NonNull UUID twincodeId, @NonNull TwincodeOutbound twincodeOutbound,
                                  @NonNull Consumer<TwincodeInbound> complete) {
            this.twincodeId = twincodeId;
            this.twincodeOutbound = twincodeOutbound;
            this.complete = complete;
        }
    }

    private static final class UpdateTwincodePendingRequest extends PendingRequest {
        @NonNull
        final TwincodeInboundImpl twincodeInbound;
        @NonNull
        final List<AttributeNameValue> attributes;
        @NonNull
        final Consumer<TwincodeInbound> complete;

        UpdateTwincodePendingRequest(@NonNull Consumer<TwincodeInbound> complete,
                                     @NonNull TwincodeInbound twincodeInbound,
                                     @NonNull List<AttributeNameValue> attributes) {
            this.twincodeInbound = (TwincodeInboundImpl) twincodeInbound;
            this.attributes = attributes;
            this.complete = complete;
        }
    }

    private static final class TriggerPendingRequest extends PendingRequest {
        @NonNull
        final Runnable complete;

        TriggerPendingRequest(@NonNull Runnable complete) {
            this.complete = complete;
        }
    }

    private final TwincodeInboundServiceProvider mServiceProvider;
    private final HashMap<Long, PendingRequest> mPendingRequests = new HashMap<>();
    private final CryptoService mCryptoService;
    private final HashMap<String, InvocationListener> mInvocationListeners = new HashMap<>();
    private final HashMap<UUID, PendingInvocation> mPendingInvocations = new HashMap<>();

    public TwincodeInboundServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {

        super(twinlifeImpl, connection);

        setServiceConfiguration(new TwincodeInboundServiceConfiguration());

        // Register the binary IQ handlers for the invoke twincode event.
        connection.addPacketListener(IQ_INVOKE_TWINCODE_SERIALIZER, this::onInvokeTwincode);
        connection.addPacketListener(IQ_ON_GET_TWINCODE_SERIALIZER, this::onGetTwincode);
        connection.addPacketListener(IQ_ON_UPDATE_TWINCODE_SERIALIZER, this::onUpdateTwincode);
        connection.addPacketListener(IQ_ON_TRIGGER_PENDING_INVOCATIONS_SERIALIZER, this::onTriggerPendingInvocations);
        connection.addPacketListener(IQ_ON_ACKNOWLEDGE_INVOCATION_SERIALIZER, this::onAcknowledgeInvocation);
        connection.addPacketListener(IQ_ON_BIND_TWINCODE_SERIALIZER, this::onBindTwincode);
        connection.addPacketListener(IQ_ON_UNBIND_TWINCODE_SERIALIZER, this::onUnbindTwincode);

        mServiceProvider = new TwincodeInboundServiceProvider(this, twinlifeImpl.getDatabaseService());
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

        if (!(baseServiceConfiguration instanceof TwincodeInboundServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        TwincodeInboundServiceConfiguration twincodeInboundServiceConfiguration = new TwincodeInboundServiceConfiguration();

        setServiceConfiguration(twincodeInboundServiceConfiguration);
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
    // Implement TwincodeInboundService interface
    //

    @Override
    public void addListener(@NonNull String action, @NonNull InvocationListener observer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addObserver: action=" + action + " observer=" + observer);
        }

        mInvocationListeners.put(action, observer);
    }

    @Override
    public void getTwincode(@NonNull UUID twincodeInboundId, @NonNull TwincodeOutbound twincodeOutbound,
                            @NonNull Consumer<TwincodeInbound> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwincode: twincodeInboundId=" + twincodeInboundId + " twincodeOutbound=" + twincodeOutbound);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final TwincodeInbound twincodeInbound = mServiceProvider.loadTwincode(twincodeInboundId);
        if (twincodeInbound != null) {
            complete.onGet(ErrorCode.SUCCESS, twincodeInbound);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new GetTwincodePendingRequest(twincodeInboundId, twincodeOutbound, complete));
        }

        final GetTwincodeIQ getTwincodeIQ = new GetTwincodeIQ(IQ_GET_TWINCODE_SERIALIZER, requestId, twincodeInboundId);
        sendDataPacket(getTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void updateTwincode(@NonNull TwincodeInbound twincodeInbound, @NonNull List<AttributeNameValue> attributes,
                               @Nullable List<String> deleteAttributeNames, @NonNull Consumer<TwincodeInbound> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateTwincode: twincodeInbound=" + twincodeInbound +
                    " attributes=" + attributes + " deleteAttributeNames=" + deleteAttributeNames);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new UpdateTwincodePendingRequest(complete, twincodeInbound, attributes));
        }

        final UpdateTwincodeIQ updateTwincodeIQ = new UpdateTwincodeIQ(IQ_UPDATE_TWINCODE_SERIALIZER, requestId,
                twincodeInbound.getId(), attributes, deleteAttributeNames, null);
        sendDataPacket(updateTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void bindTwincode(@NonNull TwincodeInbound twincodeInbound, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "bindTwincode: twincodeInbound=" + twincodeInbound);
        }

        if (!isServiceOn()) {

            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new BindUnbindPendingRequest(twincodeInbound, complete));
        }

        final GetTwincodeIQ bindTwincodeIQ = new GetTwincodeIQ(IQ_BIND_TWINCODE_SERIALIZER, requestId, twincodeInbound.getId());
        sendDataPacket(bindTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void unbindTwincode(@NonNull TwincodeInbound twincodeInbound, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "unbindTwincode: twincodeInbound=" + twincodeInbound);
        }

        if (!isServiceOn()) {

            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new BindUnbindPendingRequest(twincodeInbound, complete));
        }

        final GetTwincodeIQ unbindTwincodeIQ = new GetTwincodeIQ(IQ_UNBIND_TWINCODE_SERIALIZER, requestId, twincodeInbound.getId());
        sendDataPacket(unbindTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void acknowledgeInvocation(@NonNull UUID invocationId, @NonNull ErrorCode errorCode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "acknowledgeInvocation: invocationId=" + invocationId + " errorCode=" + errorCode);
        }

        if (!isServiceOn()) {

            return;
        }

        // We must not return ITEM_NOT_FOUND if some element of the invocation is not available because
        // the server will invalidate the inbound twincode.  The only place where we can return it is
        // from onInvokeTwincode() and only when the inbound twincode was not associated with a valid subject.
        if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
            errorCode = ErrorCode.EXPIRED;
        }

        final AcknowledgeInvocationIQ invocationIQ = new AcknowledgeInvocationIQ(IQ_ACKNOWLEDGE_INVOCATION_SERIALIZER, newRequestId(), invocationId, errorCode);
        sendDataPacket(invocationIQ, DEFAULT_REQUEST_TIMEOUT);

        finishInvocation(invocationId);
    }

    @Override
    public void triggerPendingInvocations(@NonNull Runnable complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "triggerPendingInvocations");
        }

        if (!isServiceOn()) {

            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new TriggerPendingRequest(complete));
        }

        final TriggerPendingInvocationsIQ triggerPendingInvocationsIQ = new TriggerPendingInvocationsIQ(IQ_TRIGGER_PENDING_INVOCATIONS_SERIALIZER, requestId, null);
        sendDataPacket(triggerPendingInvocationsIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Wait until all pending invocations for the given twincode inbound Id have been processed
     * and execute the code block.  If there is no pending invocation for the twincode, the code
     * block is executed immediately.
     *
     * @param twincodeId the twincode inbound id
     * @param complete the code block to execute.
     */
    public void waitInvocations(@NonNull UUID twincodeId, @NonNull Runnable complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "waitInvocations twincodeId=" + twincodeId);
        }

        // If we have pending invocations, check if there is one for the given twincode inbound.
        // When found, the code block is appended to the waiting list and will be executed when
        // all invocations are processed.  The number of invocations received is in most cases
        // zero or one and rarely exceeds 5.
        synchronized (mPendingInvocations) {
            if (!mPendingInvocations.isEmpty()) {
                for (PendingInvocation checkInvocation : mPendingInvocations.values()) {
                    if (twincodeId.equals(checkInvocation.twincodeId)) {
                        if (checkInvocation.waitingRunnables == null) {
                            checkInvocation.waitingRunnables = new ArrayList<>();
                        }
                        checkInvocation.waitingRunnables.add(complete);
                        return;
                    }
                }
            }
        }

        complete.run();
    }

    public boolean hasPendingInvocations() {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasPendingInvocations");
        }

        synchronized (mPendingInvocations) {
            return !mPendingInvocations.isEmpty();
        }
    }

    //
    // Private Methods
    //

    private void finishInvocation(@NonNull UUID invocationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "finishInvocation invocationId=" + invocationId);
        }

        final PendingInvocation pendingInvocation;
        synchronized (mPendingInvocations) {
            pendingInvocation = mPendingInvocations.remove(invocationId);
            if (pendingInvocation == null) {
                return;
            }
            pendingInvocation.invocationList.remove(invocationId);
            if (!pendingInvocation.invocationList.isEmpty()) {
                return;
            }
        }

        // All invocation have been processed, if we have some code blocks execute them.
        if (pendingInvocation.waitingRunnables != null) {
            for (Runnable complete : pendingInvocation.waitingRunnables) {
                complete.run();
            }
        }
    }

    private void onGetTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetTwincode iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final GetTwincodePendingRequest request;
        synchronized (this) {
            request = (GetTwincodePendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final OnGetTwincodeIQ onGetTwincodeIQ = (OnGetTwincodeIQ) iq;
        final long modificationDate = onGetTwincodeIQ.getModificationDate();
        final List<AttributeNameValue> attributes = onGetTwincodeIQ.getAttributes();
        final TwincodeInbound twincodeInbound = mServiceProvider.importTwincode(request.twincodeId, request.twincodeOutbound, attributes, modificationDate);

        request.complete.onGet(twincodeInbound == null ? ErrorCode.NO_STORAGE_SPACE : ErrorCode.SUCCESS, twincodeInbound);
    }

    private void onUpdateTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateTwincode iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final UpdateTwincodePendingRequest request;
        synchronized (this) {
            request = (UpdateTwincodePendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final OnUpdateTwincodeIQ onUpdateTwincodeIQ = (OnUpdateTwincodeIQ) iq;
        final long modificationDate = onUpdateTwincodeIQ.getModificationDate();
        final TwincodeInboundImpl twincodeInbound = request.twincodeInbound;
        mServiceProvider.updateTwincode(twincodeInbound, request.attributes, modificationDate);
        request.complete.onGet(ErrorCode.SUCCESS, twincodeInbound);
    }

    private void onBindTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBindTwincode iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final BindUnbindPendingRequest request;
        synchronized (this) {
            request = (BindUnbindPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        request.complete.onGet(ErrorCode.SUCCESS, request.twincode.getId());
    }

    private void onUnbindTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUnbindTwincode: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final BindUnbindPendingRequest request;
        synchronized (this) {
            request = (BindUnbindPendingRequest) mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        request.complete.onGet(ErrorCode.SUCCESS, request.twincode.getId());
    }

    private void onInvokeTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onInvokeTwincode iq=" + iq);
        }

        if (!(iq instanceof InvokeTwincodeIQ)) {
            return;
        }

        final InvokeTwincodeIQ onInvokeTwincodeIQ = (InvokeTwincodeIQ) iq;
        final UUID invocationId = onInvokeTwincodeIQ.getInvocationId();
        if (invocationId == null) {
            return;
        }

        final RepositoryService repositoryService = mTwinlifeImpl.getRepositoryService();
        final UUID twincodeId = onInvokeTwincodeIQ.getTwincodeId();

        PendingInvocation pendingInvocation;
        RepositoryObject subject = null;
        synchronized (mPendingInvocations) {
            // Check if the invocation is already being processed.
            // This occurs if we disconnect and re-connect before having time to process the invocation.
            pendingInvocation = mPendingInvocations.get(invocationId);
            if (pendingInvocation != null) {
                return;
            }

            // Check for another invocation on the same twincode (this is rare but it happens
            // and must be handled for waitInvocationsForTwincode).
            for (PendingInvocation checkInvocation : mPendingInvocations.values()) {
                if (twincodeId.equals(checkInvocation.twincodeId)) {
                    pendingInvocation = checkInvocation;
                    pendingInvocation.invocationList.add(invocationId);
                    subject = pendingInvocation.subject.get();
                    mPendingInvocations.put(invocationId, pendingInvocation);
                    break;
                }
            }
        }

        // And if we know the subject, no need to look again in the database.
        if (subject == null) {
            subject = repositoryService.findObject(twincodeId);
            if (subject == null || subject.getTwincodeOutbound() == null) {
                // Send the ITEM_NOT_FOUND error so that the server is aware we don't recognize the twincode inbound anymore.
                final AcknowledgeInvocationIQ invocationIQ = new AcknowledgeInvocationIQ(IQ_ACKNOWLEDGE_INVOCATION_SERIALIZER,
                        newRequestId(), invocationId, ErrorCode.ITEM_NOT_FOUND);
                sendDataPacket(invocationIQ, DEFAULT_REQUEST_TIMEOUT);

                // Finish manually this invocation.
                finishInvocation(invocationId);
                return;
            }
        }

        final byte[] data = onInvokeTwincodeIQ.getData();
        final TwincodeInvocation invocation;
        if (data != null) {
            final CryptoService.DecipherResult cipherResult = mCryptoService.decrypt(subject.getTwincodeOutbound(), data);
            if (cipherResult.errorCode != ErrorCode.SUCCESS) {
                acknowledgeInvocation(invocationId, cipherResult.errorCode);
                return;
            }
            invocation = new TwincodeInvocation(onInvokeTwincodeIQ.getInvocationId(), subject, onInvokeTwincodeIQ.getActionName(),
                    cipherResult.attributes, cipherResult.peerTwincodeId, cipherResult.keyIndex, cipherResult.secretKey,
                    cipherResult.publicKey, cipherResult.trustMethod);
        } else {
            invocation = new TwincodeInvocation(onInvokeTwincodeIQ.getInvocationId(), subject, onInvokeTwincodeIQ.getActionName(),
                    onInvokeTwincodeIQ.getAttributes(), null, 0, null, null, TrustMethod.NONE);
        }

        // A twincode invocation can be handled by only one handler because we have to respond:
        // - if the handler returns NULL or QUEUED, it must acknowledge itself the invocation,
        // - otherwise the invocation is acknowledged with the returned code.
        final InvocationListener observer = mInvocationListeners.get(invocation.action);
        if (observer == null) {
            acknowledgeInvocation(invocationId, ErrorCode.BAD_REQUEST);
            return;
        }

        // This twincode is having its first invocation, remember it.
        if (pendingInvocation == null) {
            pendingInvocation = new PendingInvocation(twincodeId, invocationId, subject);
            synchronized (mPendingInvocations) {
                mPendingInvocations.put(invocationId, pendingInvocation);
            }
        }

        mTwinlifeExecutor.execute(() -> {
            final ErrorCode errorCode = observer.onInvokeTwincode(invocation);
            if (errorCode != null && errorCode != ErrorCode.QUEUED) {
                if (errorCode != ErrorCode.SUCCESS && Logger.DEBUG) {
                    Log.e(LOG_TAG, "Invocation '" + invocation.action + "' failed: " + errorCode);
                }

                acknowledgeInvocation(invocationId, errorCode);
            }
        });
    }

    private void onAcknowledgeInvocation(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onAcknowledgeInvocation: iq=" + iq);
        }

        receivedIQ(iq.getRequestId());
    }

    private void onTriggerPendingInvocations(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTriggerPendingInvocations: iq=" + iq);
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

        final TriggerPendingRequest triggerPendingRequest = (TriggerPendingRequest) request;
        triggerPendingRequest.complete.run();
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

        // The object no longer exists on the server, remove it from our local database.
        if (request instanceof GetTwincodePendingRequest) {
            final GetTwincodePendingRequest getPendingRequest = (GetTwincodePendingRequest) request;

            getPendingRequest.complete.onGet(iq.getErrorCode(), null);

        } else if (request instanceof BindUnbindPendingRequest) {
            final BindUnbindPendingRequest bindPendingRequest = (BindUnbindPendingRequest) request;
            if (iq.getErrorCode() == ErrorCode.ITEM_NOT_FOUND) {
                mServiceProvider.deleteObject(bindPendingRequest.twincode);
            }
            bindPendingRequest.complete.onGet(iq.getErrorCode(), null);
        } else if (request instanceof UpdateTwincodePendingRequest) {
            final UpdateTwincodePendingRequest updatePendingRequest = (UpdateTwincodePendingRequest) request;
            if (iq.getErrorCode() == ErrorCode.ITEM_NOT_FOUND) {
                mServiceProvider.deleteObject(updatePendingRequest.twincodeInbound);
            }
            updatePendingRequest.complete.onGet(iq.getErrorCode(), null);
        }
    }
}
