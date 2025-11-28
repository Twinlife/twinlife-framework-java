/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.SerializerFactoryImpl;
import org.twinlife.twinlife.util.Utils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Timeout management
 * <p>
 * The timeout management is global to a BaseServiceImpl instance: there is only one timeout deadline for all pending requests.
 * This allows to have a simple implementation but the timeout is less precise.
 * <p>
 * When a first request is sent, a timeout deadline is computed for the request and a Job is scheduled with the JobService.
 * The job is scheduled TIMEOUT_CHECK_DELAY (2.0) seconds after the deadline. The requestId is added to the pendingRequestIdList set.
 * <p>
 * When another request is sent, a new deadline time is computed and it replaces the current deadline. The scheduled job
 * is not modified. The requestId is also added to the pendingRequestIdList set.
 * <p>
 * When the timeout job is executed, we look at the deadline and if it was passed, all pending requests are reported
 * with an error.  If the deadline has not passed and we still have pending requests, a new job is scheduled TIMEOUT_CHECK_DELAY
 * seconds after the deadline.
 * <p>
 * When a response is received, we expect the receive handler to call receivedIQ so that we remove the requestId from the pending list.
 */
public abstract class BaseServiceImpl <Observer extends BaseService.ServiceObserver> implements BaseService<Observer> {
    private static final String LOG_TAG = "BaseServiceImpl";
    private static final boolean DEBUG = false;
    private static final boolean INFO = BuildConfig.ENABLE_INFO_LOG;

    protected static final String[] SERVICE_NAMES = {
            "account.twinlife",
            "conversation.twinlife",
            "connectivity.twinlife",
            "management.twinlife",
            "notification.twinlife",
            "peer-connection.twinlife",
            "repository.twinlife",
            "factory.twincode.twinlife",
            "inbound.twincode.twinlife",
            "outbound.twincode.twinlife",
            "image.twinlife",
            "account-migration.twinlife",
            "callservice.twinlife",
            "cryptoservice.twinlife"
    };

    public static final int DATABASE_ERROR_DELAY_GUARD = 2 * 120 * 1000; // 2 minutes

    private static final long TIMEOUT_CHECK_DELAY = 2000;

    private final CopyOnWriteArrayList<Observer> mServiceObservers = new CopyOnWriteArrayList<>();
    private volatile boolean mSignIn = false;
    private volatile boolean mOnline = false;
    private volatile boolean mServiceOn = false;
    private volatile boolean mConfigured = false;
    private volatile boolean mShutdown = false;
    private volatile long mLastDatabaseErrorTime = 0L;
    private final AtomicInteger mSendCount = new AtomicInteger();
    private final AtomicInteger mSendErrorCount = new AtomicInteger();
    private final AtomicInteger mSendDisconnectedCount = new AtomicInteger();
    private final AtomicInteger mSendTimeoutCount = new AtomicInteger();
    private final AtomicInteger mDatabaseFullCount = new AtomicInteger();
    private final AtomicInteger mDatabaseIOCount = new AtomicInteger();
    private final AtomicInteger mDatabaseErrorCount = new AtomicInteger();
    @NonNull
    protected final JobService mJobService;
    private Map<Long, Boolean> mPendingRequestList;
    private long mNextDeadline;
    private JobService.Job mScheduleJobId;

    @NonNull
    protected final TwinlifeImpl mTwinlifeImpl;
    @NonNull
    protected final Connection mConnection;
    @NonNull
    protected final SerializerFactoryImpl mSerializerFactory;
    private BaseServiceConfiguration mServiceConfiguration;
    protected final Executor mTwinlifeExecutor;

    protected BaseServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection) {
        if (DEBUG) {
            Log.d(LOG_TAG, "BaseServiceImpl: twinlifeImpl=" + twinlifeImpl + " connection=" + connection);
        }

        mTwinlifeImpl = twinlifeImpl;
        mConnection = connection;
        mJobService = twinlifeImpl.getJobService();
        mSerializerFactory = mTwinlifeImpl.getSerializerFactoryImpl();
        mTwinlifeExecutor = mTwinlifeImpl.getTwinlifeExecutor();
    }

    public File getFilesDir() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getFilesDir");
        }

        return mTwinlifeImpl.getFilesDir();
    }

    //
    // Implement BaseService interface
    //

    @Override
    public BaseServiceId getId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getId");
        }

        return mServiceConfiguration.id;
    }

    @Override
    @NonNull
    public String getVersion() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getVersion");
        }

        return mServiceConfiguration.version;
    }

    @Override
    public boolean isSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isSignIn");
        }

        return mSignIn;
    }

    @Override
    public boolean isTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isTwinlifeOnline");
        }

        return mOnline;
    }

    @Override
    public void addServiceObserver(@NonNull Observer serviceObserver) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addServiceObserver serviceObserver=" + serviceObserver);
        }

        mServiceObservers.addIfAbsent(serviceObserver);
    }

    @Override
    public void removeServiceObserver(@NonNull Observer serviceObserver) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeServiceObserver serviceObserver=" + serviceObserver);
        }

        mServiceObservers.remove(serviceObserver);
    }

    public TwinlifeImpl getTwinlifeImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getTwinlifeImpl");
        }

        return mTwinlifeImpl;
    }

    public SerializerFactoryImpl getSerializerFactoryImpl() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSerializerFactory");
        }

        return mSerializerFactory;
    }

    @Override
    @NonNull
    public ServiceStats getServiceStats() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getServiceStats");
        }

        ServiceStats result = new ServiceStats();

        result.sendErrorCount = mSendErrorCount.get();
        result.sendPacketCount = mSendCount.get();
        result.sendDisconnectedCount = mSendDisconnectedCount.get();
        result.sendTimeoutCount = mSendTimeoutCount.get();
        result.databaseErrorCount = mDatabaseErrorCount.get();
        result.databaseFullCount = mDatabaseFullCount.get();
        result.databaseIOCount = mDatabaseIOCount.get();

        return result;
    }

    @Override
    @NonNull
    public String getServiceName() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getServiceName");
        }

        return SERVICE_NAMES[getId().ordinal()];
    }

    //
    // Protected Methods
    //

    @NonNull
    protected List<Observer> getServiceObservers() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getServiceObservers");
        }

        return mServiceObservers;
    }

    protected void setServiceOn(boolean serviceOn) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setServiceOn serviceOn=" + serviceOn);
        }

        mServiceOn = serviceOn;
    }

    @Override
    public boolean isServiceOn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isServiceOn");
        }

        // For development, abort and crash if we call a service from the main UI thread: this is forbidden.
        if (BuildConfig.ENABLE_CHECKS && Utils.isMainThread()) {
            Log.e(LOG_TAG, "Service operation MUST NOT be called from main UI thread!");
            mTwinlifeImpl.assertion(TwinlifeAssertPoint.SERVICE, null);
        }
        return mServiceOn;
    }

    /**
     * Same as isServiceOn() but allows to be called from any thread.
     *
     * @return true if the service is enabled and ready.
     */
    protected boolean isServiceReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isServiceReady");
        }

        return mServiceOn;
    }

    protected boolean isShutdown() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isShutdown");
        }

        return mShutdown;
    }

    protected long newRequestId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "newRequestId");
        }

        return mTwinlifeImpl.newRequestId();
    }

    protected void setConfigured(boolean configured) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setConfigured configured=" + configured);
        }

        mConfigured = configured;
        if (!mConfigured) {
            mServiceOn = false;
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected boolean isConfigured() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isConfigured");
        }

        return mConfigured;
    }

    protected BaseServiceConfiguration getServiceConfiguration() {

        return mServiceConfiguration;
    }

    protected void setServiceConfiguration(BaseServiceConfiguration baseServiceConfiguration) {

        mServiceConfiguration = baseServiceConfiguration;
    }

    @SuppressWarnings("unused")
    protected abstract void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration);

    protected void onCreate() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate");
        }
    }

    protected void onConfigure() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConfigure");
        }
    }

    @SuppressWarnings("WeakerAccess")
    protected void onUpdateConfiguration(@NonNull Configuration configuration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateConfiguration configuration=" + configuration);
        }
    }

    protected void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }
    }

    protected void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        mOnline = true;
    }

    public void onTwinlifeSuspend() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeSuspend");
        }

        mShutdown = true;
    }

    protected void onDestroy() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDestroy");
        }

        mServiceOn = false;
    }

    protected void onConnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConnect");
        }
    }

    protected void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        mSignIn = false;
        mOnline = false;

        Map<Long, Boolean> pendingRequests;
        synchronized (this) {
            pendingRequests = mPendingRequestList;
            mPendingRequestList = null;
            if (mScheduleJobId != null) {
                mScheduleJobId.cancel();
                mScheduleJobId = null;
            }
        }
        if (pendingRequests != null) {
            onTimeout(pendingRequests);
        }
    }

    protected void onSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignIn");
        }

        mSignIn = true;
    }

    protected void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        mSignIn = false;
        mOnline = false;
    }

    protected ErrorCode sendDataPacket(@NonNull BinaryPacketIQ iq, long timeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendDataPacket iq=" + iq + " timeout=" + timeout);
        }

        long requestId = iq.getRequestId();
        try {
            if (mSignIn) {
                byte[] packet = iq.serializeCompact(mSerializerFactory);
                packetTimeout(requestId, timeout, true);
                if (mConnection.sendDataPacket(packet)) {
                    mSendCount.incrementAndGet();
                    return ErrorCode.SUCCESS;
                }
            }
            mSendDisconnectedCount.incrementAndGet();
            onErrorPacket(new BinaryErrorPacketIQ(requestId, ErrorCode.TWINLIFE_OFFLINE));
            return ErrorCode.TWINLIFE_OFFLINE;
        } catch (Exception ex) {
            onErrorPacket(new BinaryErrorPacketIQ(requestId, ErrorCode.LIBRARY_ERROR));
            return ErrorCode.TWINLIFE_OFFLINE;
        }
    }

    protected void sendResponse(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendResponse iq=" + iq);
        }

        try {
            if (mSignIn) {
                byte[] packet = iq.serializeCompact(mSerializerFactory);
                if (mConnection.sendDataPacket(packet)) {
                    mSendCount.incrementAndGet();
                    return;
                }
            }
            mSendDisconnectedCount.incrementAndGet();
        } catch (Exception ex) {
            if (Logger.INFO) {
                Logger.info(LOG_TAG, "sendResponse", ex, " iq=", iq);
            }
        }
    }

    protected void packetTimeout(long requestId, long timeout, boolean isBinary) {
        if (DEBUG) {
            Log.d(LOG_TAG, "packetTimeout requestId=" + requestId + " timeout=" + timeout + " isBinary=" + isBinary);
        }

        long nextDeadline = System.currentTimeMillis() + timeout + TIMEOUT_CHECK_DELAY;
        synchronized (this) {
            mNextDeadline = nextDeadline;
            if (mPendingRequestList == null) {
                mPendingRequestList = new HashMap<>();
            }
            mPendingRequestList.put(requestId, isBinary);
            if (mScheduleJobId == null) {
                mScheduleJobId = mJobService.scheduleAfter("server timeout", this::onPacketTimeout, mNextDeadline, JobService.Priority.CONNECT);
            }
        }
    }

    protected boolean receivedIQ(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "receivedIQ requestId=" + requestId);
        }

        Boolean result = null;
        synchronized (this) {
            if (mPendingRequestList != null) {
                result = mPendingRequestList.remove(requestId);
                if (mPendingRequestList.isEmpty() && mScheduleJobId != null) {
                    mScheduleJobId.cancel();
                    mScheduleJobId = null;
                }
            }
        }
        return result != null && result;
    }

    protected void onError(long requestId, ErrorCode status, String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError requestId=" + requestId + " status=" + status + " errorParameter=" + errorParameter);
        }

        for (BaseService.ServiceObserver serviceObserver : mServiceObservers) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onError(requestId, status, errorParameter));
        }
    }

    protected void onErrorPacket(@NonNull BinaryErrorPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onErrorPacket iq=" + iq);
        }

        receivedIQ(iq.getRequestId());
    }

    public ErrorCode onDatabaseException(@Nullable Exception exception) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDatabaseException: exception=" + exception);
        }

        if (INFO) {
            Log.e(LOG_TAG, "Database exception:", exception);
        }

        if (exception == null) {
            return ErrorCode.SUCCESS;
        }

        ErrorCode errorCode;
        if ((exception instanceof DatabaseException)) {
            DatabaseException dbException = (DatabaseException) exception;

            if (dbException.isDatabaseFull()) {
                mDatabaseFullCount.incrementAndGet();
                errorCode = ErrorCode.NO_STORAGE_SPACE;

            } else if (dbException.isDiskError()) {
                mDatabaseIOCount.incrementAndGet();
                errorCode = ErrorCode.NO_STORAGE_SPACE;

            } else {
                mDatabaseErrorCount.incrementAndGet();
                errorCode = ErrorCode.DATABASE_ERROR;
            }

            long now = System.currentTimeMillis();
            if (now < mLastDatabaseErrorTime + DATABASE_ERROR_DELAY_GUARD) {
                return errorCode;
            }
            mLastDatabaseErrorTime = now;
        } else {
            errorCode = ErrorCode.LIBRARY_ERROR;
        }

        mTwinlifeImpl.error(errorCode, exception.getMessage());
        return errorCode;
    }

    private void onPacketTimeout() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onPacketTimeout");
        }

        long now = System.currentTimeMillis();
        Map<Long, Boolean> pendingRequests = null;
        synchronized (this) {
            mScheduleJobId = null;
            if (mNextDeadline < now) {
                pendingRequests = mPendingRequestList;
                mPendingRequestList = null;
            } else {
                mScheduleJobId = mJobService.scheduleAfter("server timeout", this::onPacketTimeout, mNextDeadline, JobService.Priority.CONNECT);
            }
        }
        if (pendingRequests != null) {
            onTimeout(pendingRequests);
        }
    }

    private void onTimeout(Map<Long, Boolean> requestIds) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTimeout requestIds=" + requestIds);
        }

        for (Map.Entry<Long, Boolean> requestInfo : requestIds.entrySet()) {
            mSendTimeoutCount.incrementAndGet();
            if (requestInfo.getValue()) {
                onErrorPacket(new BinaryErrorPacketIQ(requestInfo.getKey(), ErrorCode.TWINLIFE_OFFLINE));
            } else {
                onError(requestInfo.getKey(), ErrorCode.TWINLIFE_OFFLINE, null);
            }
        }
    }

    //
    // Serialization support
    //

    private enum AttributeNameValueType {
        ATTRIBUTE_NAME_BITMAP_VALUE, // Deprecated, unused but do not remove.
        ATTRIBUTE_NAME_BOOLEAN_VALUE,
        ATTRIBUTE_NAME_LONG_VALUE,
        ATTRIBUTE_NAME_STRING_VALUE,
        ATTRIBUTE_NAME_VOID_VALUE,
        ATTRIBUTE_NAME_UUID_VALUE
    }

    public static void serialize(@NonNull AttributeNameValue attribute, @NonNull DataOutputStream dataOutputStream) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "serialize attribute=" + attribute + " dataOutputStream=" + dataOutputStream);
        }

        try {
            if (attribute instanceof AttributeNameBooleanValue) {
                dataOutputStream.writeInt(AttributeNameValueType.ATTRIBUTE_NAME_BOOLEAN_VALUE.ordinal());
                dataOutputStream.writeUTF(attribute.name);
                dataOutputStream.writeBoolean((Boolean) attribute.value);
            } else if (attribute instanceof AttributeNameLongValue) {
                dataOutputStream.writeInt(AttributeNameValueType.ATTRIBUTE_NAME_LONG_VALUE.ordinal());
                dataOutputStream.writeUTF(attribute.name);
                dataOutputStream.writeLong((Long) attribute.value);
            } else if (attribute instanceof AttributeNameStringValue) {
                dataOutputStream.writeInt(AttributeNameValueType.ATTRIBUTE_NAME_STRING_VALUE.ordinal());
                dataOutputStream.writeUTF(attribute.name);
                dataOutputStream.writeUTF((String) attribute.value);
            } else if (attribute instanceof AttributeNameVoidValue) {
                dataOutputStream.writeInt(AttributeNameValueType.ATTRIBUTE_NAME_VOID_VALUE.ordinal());
                dataOutputStream.writeUTF(attribute.name);
            } else if (attribute instanceof AttributeNameUUIDValue) {
                dataOutputStream.writeInt(AttributeNameValueType.ATTRIBUTE_NAME_UUID_VALUE.ordinal());
                dataOutputStream.writeUTF(attribute.name);
                dataOutputStream.writeUTF(attribute.value.toString());
            }
        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "serialize", exception);
            }
            throw new SerializerException(exception);
        }
    }

    @Nullable
    public static AttributeNameValue deserialize(@NonNull DataInputStream dataInputStream) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "deserialize dataInputStream=" + dataInputStream);
        }

        try {
            int type = dataInputStream.readInt();
            if (type < 0 || type >= AttributeNameValueType.values().length) {

                return null;
            }

            String name;
            switch (AttributeNameValueType.values()[type]) {
                case ATTRIBUTE_NAME_BOOLEAN_VALUE:
                    name = dataInputStream.readUTF();
                    boolean booleanValue = dataInputStream.readBoolean();

                    return new AttributeNameBooleanValue(name, booleanValue);

                case ATTRIBUTE_NAME_LONG_VALUE:
                    name = dataInputStream.readUTF();
                    long longValue = dataInputStream.readLong();

                    return new AttributeNameLongValue(name, longValue);

                case ATTRIBUTE_NAME_STRING_VALUE:
                    name = dataInputStream.readUTF();
                    String stringValue = dataInputStream.readUTF();

                    return new AttributeNameStringValue(name, stringValue);

                case ATTRIBUTE_NAME_UUID_VALUE:
                    name = dataInputStream.readUTF();
                    String uuidValue = dataInputStream.readUTF();

                    return new AttributeNameUUIDValue(name, Utils.UUIDFromString(uuidValue));

                case ATTRIBUTE_NAME_VOID_VALUE:
                    name = dataInputStream.readUTF();

                    return new AttributeNameVoidValue(name);
            }

            return null;
        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "deserialize", exception);
            }
            throw new SerializerException(exception);
        }
    }
}
