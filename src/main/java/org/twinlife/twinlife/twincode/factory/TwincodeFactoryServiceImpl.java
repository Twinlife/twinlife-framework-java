/*
 *  Copyright (c) 2013-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.twincode.factory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.DatabaseCursor;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwincodeFactoryService;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.crypto.CryptoServiceImpl;
import org.twinlife.twinlife.database.DatabaseServiceImpl;
import org.twinlife.twinlife.database.Tables;
import org.twinlife.twinlife.database.Transaction;
import org.twinlife.twinlife.twincode.outbound.TwincodeOutboundImpl;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class TwincodeFactoryServiceImpl extends BaseServiceImpl<TwincodeFactoryService.ServiceObserver> implements TwincodeFactoryService {
    private static final String LOG_TAG = "TwincodeFactoryServi...";
    private static final boolean DEBUG = false;

    private static class PendingRequest {
    }

    private static final class CreatePendingRequest extends PendingRequest {
        @Nullable
        final List<AttributeNameValue> inboundAttributes;
        @Nullable
        final List<AttributeNameValue> outboundAttributes;
        @NonNull
        final List<AttributeNameValue> factoryAttributes;
        @NonNull
        final Consumer<TwincodeFactory> complete;

        CreatePendingRequest(@Nullable List<AttributeNameValue> inboundAttributes,
                             @Nullable List<AttributeNameValue> outboundAttributes,
                             @NonNull List<AttributeNameValue> factoryAttributes,
                             @NonNull Consumer<TwincodeFactory> complete) {
            this.inboundAttributes = inboundAttributes;
            this.outboundAttributes = outboundAttributes;
            this.factoryAttributes = factoryAttributes;
            this.complete = complete;
        }
    }

    private static final class DeletePendingRequest extends PendingRequest {
        @NonNull
        final UUID factoryId;
        @NonNull
        final Consumer<UUID> complete;

        DeletePendingRequest(@NonNull UUID factoryId, @NonNull Consumer<UUID> complete) {
            this.factoryId = factoryId;
            this.complete = complete;
        }
    }

    private static final UUID CREATE_TWINCODE_SCHEMA_ID = UUID.fromString("8184d22a-980c-40a3-90c3-02ff4732e7b9");
    private static final UUID ON_CREATE_TWINCODE_SCHEMA_ID = UUID.fromString("6c0442f5-b0bf-4b7e-9ae5-40ad720b1f71");

    private static final UUID DELETE_TWINCODE_SCHEMA_ID = UUID.fromString("cf8f2889-4ee2-4e50-a26a-5cbd475bb07a");
    private static final UUID ON_DELETE_TWINCODE_SCHEMA_ID = UUID.fromString("311945f8-24c5-451c-aee3-bcd154aca963");

    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_CREATE_TWINCODE_SERIALIZER = CreateTwincodeIQ.createSerializer(CREATE_TWINCODE_SCHEMA_ID, 2);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_CREATE_TWINCODE_SERIALIZER = OnCreateTwincodeIQ.createSerializer(ON_CREATE_TWINCODE_SCHEMA_ID, 1);

    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_DELETE_TWINCODE_SERIALIZER = DeleteTwincodeIQ.createSerializer(DELETE_TWINCODE_SCHEMA_ID, 1);
    public static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_DELETE_TWINCODE_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_DELETE_TWINCODE_SCHEMA_ID, 1);

    private final HashMap<Long, PendingRequest> mPendingRequests = new HashMap<>();
    private final DatabaseServiceImpl mDatabaseService;
    private final TwincodeOutboundService mTwincodeOutboundService;
    private final CryptoServiceImpl mCryptoService;

    public TwincodeFactoryServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {
        super(twinlifeImpl, connection);

        mDatabaseService = twinlifeImpl.getDatabaseService();
        mCryptoService = twinlifeImpl.getCryptoService();
        mTwincodeOutboundService = twinlifeImpl.getTwincodeOutboundService();
        setServiceConfiguration(new TwincodeFactoryServiceConfiguration());

        // Register the binary IQ handlers for the responses.
        connection.addPacketListener(IQ_ON_CREATE_TWINCODE_SERIALIZER, this::onCreateTwincode);
        connection.addPacketListener(IQ_ON_DELETE_TWINCODE_SERIALIZER, this::onDeleteTwincode);
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof TwincodeFactoryServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        TwincodeFactoryServiceConfiguration twincodeFactoryServiceConfiguration = new TwincodeFactoryServiceConfiguration();

        setServiceConfiguration(twincodeFactoryServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);
    }

    //
    // Implement TwincodeFactoryService interface
    //

    /**
     * Create a set of twincodes with the factory, inbound, outbound, switch.
     * Bind the inbound twincode to the device.  Each twincode is configured with
     * its own attributes.
     *
     * @param twincodeFactoryAttributes the factory attributes.
     * @param twincodeInboundAttributes the inbound twincode attributes.
     * @param twincodeOutboundAttributes the outbound twincode attributes.
     * @param twincodeSwitchAttributes the switch twincode attributes.
     * @param twincodeSchemaId the schemaId associated with the twincode.
     * @param complete the handler executed when the operation completes or an error occurred.
     */
    public void createTwincode(@NonNull List<AttributeNameValue> twincodeFactoryAttributes,
                               @Nullable List<AttributeNameValue> twincodeInboundAttributes,
                               @Nullable List<AttributeNameValue> twincodeOutboundAttributes,
                               @Nullable List<AttributeNameValue> twincodeSwitchAttributes,
                               @NonNull UUID twincodeSchemaId,
                               @NonNull Consumer<TwincodeFactory> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createTwincode: twincodeFactoryAttributes=" + twincodeFactoryAttributes);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new CreatePendingRequest(twincodeInboundAttributes,
                    twincodeOutboundAttributes, twincodeFactoryAttributes, complete));
        }

        final CreateTwincodeIQ createTwincodeIQ = new CreateTwincodeIQ(IQ_CREATE_TWINCODE_SERIALIZER, requestId,
                CreateTwincodeIQ.BIND_INBOUND_OPTION, twincodeFactoryAttributes, twincodeInboundAttributes,
                null, twincodeSwitchAttributes, twincodeSchemaId);
        sendDataPacket(createTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    /**
     * Delete the twincode factory and the associated inbound, outbound and switch twincodes.
     *
     * @param twincodeFactoryId the twincode factory id.
     * @param complete the handler executed when the operation completes or an error occurred.
     */
    public void deleteTwincode(@NonNull UUID twincodeFactoryId, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteTwincode: twincodeFactoryId=" + twincodeFactoryId);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (mPendingRequests) {
            mPendingRequests.put(requestId, new DeletePendingRequest(twincodeFactoryId, complete));
        }

        final DeleteTwincodeIQ deleteTwincodeIQ = new DeleteTwincodeIQ(IQ_DELETE_TWINCODE_SERIALIZER, requestId,
                twincodeFactoryId, 0);
        sendDataPacket(deleteTwincodeIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    //
    // Private Methods
    //

    private void onCreateTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateTwincode iq=" + iq);
        }

        if (!(iq instanceof OnCreateTwincodeIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        final OnCreateTwincodeIQ onCreateTwincodeIQ = (OnCreateTwincodeIQ) iq;

        // Get the pending request or terminate.
        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        final CreatePendingRequest createPendingRequest = (CreatePendingRequest) request;
        try (Transaction transaction = mDatabaseService.newTransaction()) {
            final long now = System.currentTimeMillis();
            final UUID factoryId = onCreateTwincodeIQ.getFactoryTwincodeId();
            final TwincodeOutbound twincodeOutbound = transaction.storeTwincodeOutbound(onCreateTwincodeIQ.getOutboundTwincodeId(),
                    createPendingRequest.outboundAttributes, TwincodeOutboundImpl.CREATION_FLAGS,
                    now, TwincodeOutboundService.NO_REFRESH_PERIOD, 0, 0);
            final TwincodeInbound twincodeInbound = transaction.storeTwincodeInbound(onCreateTwincodeIQ.getInboundTwincodeId(),
                    twincodeOutbound, factoryId, createPendingRequest.inboundAttributes, 0, now);
            mCryptoService.createPrivateKey(transaction, twincodeInbound, twincodeOutbound);
            transaction.commit();

            final TwincodeFactoryImpl twincodeFactoryImpl = new TwincodeFactoryImpl(factoryId, now, twincodeInbound, twincodeOutbound,
                    onCreateTwincodeIQ.getSwitchTwincodeId(), createPendingRequest.factoryAttributes);
            if (createPendingRequest.outboundAttributes != null) {
                // Update the twincode outbound with the attributes after the creation
                // so that these attributes are signed.
                mTwincodeOutboundService.updateTwincode(twincodeOutbound, createPendingRequest.outboundAttributes, null,
                        (ErrorCode errorCode, TwincodeOutbound twincodeOutbound2) -> createPendingRequest.complete.onGet(errorCode, twincodeFactoryImpl));
            } else {
                createPendingRequest.complete.onGet(ErrorCode.SUCCESS, twincodeFactoryImpl);
            }
        } catch (Exception exception) {
            createPendingRequest.complete.onGet(onDatabaseException(exception), null);
        }
    }

    private void onDeleteTwincode(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteTwincode iq=" + iq);
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

        final DeletePendingRequest deletePendingRequest = (DeletePendingRequest) request;

        removeTwincode(deletePendingRequest.factoryId);
        deletePendingRequest.complete.onGet(ErrorCode.SUCCESS, deletePendingRequest.factoryId);
    }

    private void removeTwincode(@NonNull UUID twincodeFactoryId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeTwincode twincodeFactoryId=" + twincodeFactoryId);
        }

        try (Transaction transaction = mDatabaseService.newTransaction()) {

            try (DatabaseCursor cursor = mDatabaseService.rawQuery("SELECT ti.id, ti.twincodeId, twout.id, twout.twincodeId"
                    + " FROM twincodeInbound AS ti "
                    + " LEFT JOIN twincodeOutbound AS twout ON ti.twincodeOutbound = twout.id"
                    + " WHERE ti.factoryId=?", new String[] {
                    twincodeFactoryId.toString()
            })) {
                while (cursor.moveToNext()) {
                    long inboundId = cursor.getLong(0);
                    transaction.deleteWithId(Tables.TWINCODE_INBOUND, inboundId);
                    UUID twincodeInboundId = cursor.getUUID(1);
                    mDatabaseService.evictCacheWithObjectId(twincodeInboundId);

                    long outboundId = cursor.getLong(2);
                    UUID twincodeOutboundId = cursor.getUUID(3);
                    transaction.deleteWithId(Tables.TWINCODE_KEYS, outboundId);
                    transaction.deleteWithId(Tables.SECRET_KEYS, outboundId);
                    transaction.deleteWithId(Tables.TWINCODE_OUTBOUND, outboundId);
                    mDatabaseService.evictCacheWithObjectId(twincodeOutboundId);
                }
            }
            transaction.commit();

        } catch (Exception exception) {
            onDatabaseException(exception);
        }
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
        }
        if (request == null) {
            return;
        }

        if (request instanceof CreatePendingRequest) {
            final CreatePendingRequest createPendingRequest = (CreatePendingRequest) request;

            createPendingRequest.complete.onGet(iq.getErrorCode(), null);

        } else if (request instanceof DeletePendingRequest) {
            final DeletePendingRequest deletePendingRequest = (DeletePendingRequest) request;

            removeTwincode(deletePendingRequest.factoryId);
            deletePendingRequest.complete.onGet(iq.getErrorCode(), null);
        }
    }
}
