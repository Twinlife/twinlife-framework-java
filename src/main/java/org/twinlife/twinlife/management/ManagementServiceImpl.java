/*
 *  Copyright (c) 2012-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributor: Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *  Contributor: Tengfei Wang (Tengfei.Wang@twinlife-systems.com)
 *  Contributor: Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *  Contributor: Xiaobo Xie (Xiaobo.Xie@twinlife-systems.com)
 *  Contributor: Chedi Baccari (Chedi.Baccari@twinlife-systems.com)
 *  Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.management;

import android.annotation.SuppressLint;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.libwebsockets.ConnectionStats;
import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.ProxyDescriptor;
import org.twinlife.twinlife.Configuration;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.ErrorStats;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.DeviceInfo;
import org.twinlife.twinlife.Hostname;
import org.twinlife.twinlife.JobService;
import org.twinlife.twinlife.ManagementService;
import org.twinlife.twinlife.PackageInfo;
import org.twinlife.twinlife.TurnServer;
import org.twinlife.twinlife.TwinlifeAssertPoint;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.account.AccountServiceImpl;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utf8;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinlife.util.Version;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

public class ManagementServiceImpl extends BaseServiceImpl<ManagementService.ServiceObserver> implements ManagementService {
    private static final String LOG_TAG = "ManagementServiceImpl";
    private static final boolean DEBUG = false;

    private static final String HARDWARE_CPU_ABI = "cpu-abi";
    private static final String HARDWARE_DEVICE = "device";
    private static final String HARDWARE_HARDWARE = "hardware";
    private static final String HARDWARE_ID = "id";
    private static final String HARDWARE_MANUFACTURER = "manufacturer";
    private static final String HARDWARE_PRODUCT = "product";

    private static final String SOFTWARE_PACKAGE_NAME = "package";
    private static final String SOFTWARE_INSTALLER = "installer";
    private static final String SOFTWARE_SDK = "sdk";

    private static final String DEVICE_BUCKET = "bucket";
    private static final String DEVICE_CHARGING = "charging";
    private static final String DEVICE_BATTERY = "battery";
    private static final String DEVICE_LOW_RAM = "low-ram";
    private static final String DEVICE_RESTRICTED = "restricted";
    private static final String DEVICE_POWER_WHITE_LIST = "power-white-list";

    private static final String CONNECT_DNS_TIME = "dnsTime";
    private static final String CONNECT_TCP_TIME = "tcpTime";
    private static final String CONNECT_TLS_TIME = "tlsTime";
    private static final String CONNECT_TXN_TIME = "txnTime";

    private static final String DNS_ERROR_COUNTER = "dnsError";
    private static final String TCP_ERROR_COUNTER = "tcpError";
    private static final String TLS_ERROR_COUNTER = "tlsError";
    private static final String TXN_ERROR_COUNTER = "txnError";
    private static final String PROXY_ERROR_COUNTER = "proxyError";
    private static final String TLS_HOST_ERROR_COUNTER = "tlsHostError";
    private static final String TLS_VERIFY_ERROR_COUNTER = "tlsVerifyError";
    private static final String WEBSOCKET_COUNTER = "wsCount";

    private static final String VALIDATE_CONFIGURATION_HARDWARE = "hardware";
    private static final String VALIDATE_CONFIGURATION_SOFTWARE = "software";
    private static final String VALIDATE_CONFIGURATION_POWER_MANAGEMENT = "power-management";
    private static final String VALIDATE_CONFIGURATION_CONNECT_STATS = "connect-stats";
    private static final String VALIDATE_CONFIGURATION_TIMEZONE = "timezone";

    private static final String PREFERENCES = "ManagementService";
    private static final String PREFERENCES_ENVIRONMENT_ID = "EnvironmentId";
    private static final String PREFERENCES_PUSH_NOTIFICATION_TOKEN = "PushNotificationToken";

    private static final int MAX_EVENTS = 16;
    private static final int MAX_ASSERTIONS = 8;

    private static final long MIN_UPDATE_TTL = 120; // 2mn

    private static final UUID VALIDATE_CONFIGURATION_SCHEMA_ID = UUID.fromString("437466BB-B2AC-4A53-9376-BFE263C98220");
    private static final UUID SET_PUSH_TOKEN_SCHEMA_ID = UUID.fromString("3c1115d7-ed74-4445-b689-63e9c10eb50c");
    private static final UUID UPDATE_CONFIGURATION_SCHEMA_ID = UUID.fromString("3b726b45-c3fc-4062-8ecd-0ddab2dd1537");
    private static final UUID LOG_EVENT_SCHEMA_ID = UUID.fromString("a2065d6f-a7aa-43cd-9c0e-030ece70d234");
    private static final UUID ASSERTION_SCHEMA_ID = UUID.fromString("debcf418-2d3d-4477-97e1-8f7b4507ce8a");
    private static final UUID FEEDBACK_SCHEMA_ID = UUID.fromString("B3ED091A-4DB9-4C9B-9501-65F11811738B");

    private static final UUID ON_VALIDATE_CONFIGURATION_SCHEMA_ID = UUID.fromString("A0589646-2B24-4D22-BE5B-6215482C8748");
    private static final UUID ON_SET_PUSH_TOKEN_SCHEMA_ID = UUID.fromString("e7596131-6e4d-47f1-b8a0-c747d3ae70f9");
    private static final UUID ON_UPDATE_CONFIGURATION_SCHEMA_ID = UUID.fromString("2ab7ff5b-3043-4cbb-bb12-dda405fcd285");
    private static final UUID ON_LOG_EVENT_SCHEMA_ID = UUID.fromString("99286975-56dc-40d1-8df5-bce6b9e914f9");
    private static final UUID ON_FEEDBACK_SCHEMA_ID = UUID.fromString("DF59B7F3-D0D3-4A96-9B7A-1671B1627AEF");

    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_VALIDATE_CONFIGURATION_SERIALIZER = ValidateConfigurationIQ.createSerializer(VALIDATE_CONFIGURATION_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_SET_PUSH_TOKEN_SERIALIZER = SetPushTokenIQ.createSerializer(SET_PUSH_TOKEN_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_UPDATE_CONFIGURATION_SERIALIZER = UpdateConfigurationIQ.createSerializer(UPDATE_CONFIGURATION_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_LOG_EVENT_SERIALIZER = LogEventIQ.createSerializer(LOG_EVENT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_FEEDBACK_SERIALIZER = FeedbackIQ.createSerializer(FEEDBACK_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ASSERTION_SERIALIZER = AssertionIQ.createSerializer(ASSERTION_SCHEMA_ID, 1);

    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_VALIDATE_CONFIGURATION_SERIALIZER = OnValidateConfigurationIQ.createSerializer(ON_VALIDATE_CONFIGURATION_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UPDATE_CONFIGURATION_SERIALIZER = OnValidateConfigurationIQ.createSerializer(ON_UPDATE_CONFIGURATION_SCHEMA_ID, 2);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_SET_PUSH_TOKEN_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_SET_PUSH_TOKEN_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_LOG_EVENT_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_LOG_EVENT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_FEEDBACK_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_FEEDBACK_SCHEMA_ID, 1);

    static class Event {
        final String eventId;
        final long timestamp;
        String key;
        String value;
        Map<String, String> attributes;

        Event(String eventId, Map<String, String> attributes) {

            this.eventId = eventId;
            this.timestamp = System.currentTimeMillis();
            this.attributes = attributes;
        }
    }

    static class PendingRequest {
        @NonNull
        final List<Event> events;

        PendingRequest(@NonNull List<Event> events) {
            this.events = events;
        }
    }

    private volatile UUID mEnvironmentId;
    private volatile String mPushNotificationToken;
    private volatile boolean mSetPushNotificationToken = true;

    private final AtomicReference<List<Event>> mEvents = new AtomicReference<>(new ArrayList<>(MAX_EVENTS));
    private final List<AssertionIQ> mAssertions = new ArrayList<>(MAX_ASSERTIONS);
    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, PendingRequest> mPendingRequests = new HashMap<>();
    private final UUID mApplicationId;
    private volatile Configuration mConfiguration;
    private JobService.Job mRefreshJob;

    public ManagementServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection,
                                 @NonNull UUID applicationId) {
        super(twinlifeImpl, connection);

        setServiceConfiguration(new ManagementServiceConfiguration());
        mApplicationId = applicationId;

        mSerializerFactory.addSerializer(IQ_VALIDATE_CONFIGURATION_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_SET_PUSH_TOKEN_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_UPDATE_CONFIGURATION_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_LOG_EVENT_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_FEEDBACK_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_VALIDATE_CONFIGURATION_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_UPDATE_CONFIGURATION_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_SET_PUSH_TOKEN_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_LOG_EVENT_SERIALIZER);
        mSerializerFactory.addSerializer(IQ_ON_FEEDBACK_SERIALIZER);

        // Register the binary IQ handlers for the responses.
        connection.addPacketListener(IQ_ON_VALIDATE_CONFIGURATION_SERIALIZER, this::onValidateConfigurationIQ);
        connection.addPacketListener(IQ_ON_UPDATE_CONFIGURATION_SERIALIZER, this::onUpdateConfigurationIQ);
        connection.addPacketListener(IQ_ON_SET_PUSH_TOKEN_SERIALIZER, this::onSetPushNotificationTokenIQ);
        connection.addPacketListener(IQ_ON_LOG_EVENT_SERIALIZER, this::onLogEventIQ);
        connection.addPacketListener(IQ_ON_FEEDBACK_SERIALIZER, this::onFeedbackIQ);
    }

    @Nullable
    public String getNotificationKey() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getNotificationKey");
        }

        if (mEnvironmentId != null) {
            try {
                MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
                byte[] hash = messageDigest.digest(Utf8.getBytes(mEnvironmentId.toString()));
                return Utils.encodeBase64(hash);

            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "getNotificationKey", exception);
                }
            }
        }

        return null;
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof ManagementServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        ManagementServiceConfiguration managementServiceConfiguration = new ManagementServiceConfiguration();

        setServiceConfiguration(managementServiceConfiguration);
        setServiceOn(true);
        setConfigured(true);
    }

    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate");
        }

        super.onCreate();

        ConfigurationService configurationService = mTwinlifeImpl.getConfigurationService();
        ConfigurationService.Configuration savedConfig = configurationService.getConfiguration(PREFERENCES);

        // Get the environment from the account service.
        AccountServiceImpl accountServiceImpl = mTwinlifeImpl.getAccountServiceImpl();
        mEnvironmentId = accountServiceImpl.getEnvironmentId();
        if (mEnvironmentId == null) {

            String value = savedConfig.getString(PREFERENCES_ENVIRONMENT_ID, null);
            mEnvironmentId = Utils.UUIDFromString(value);
        }

        mConfiguration = new Configuration(new TurnServer[]{}, new Hostname[]{});

        mPushNotificationToken = savedConfig.getString(PREFERENCES_PUSH_NOTIFICATION_TOKEN, null);
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

    @Override
    public void onSignIn() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignIn");
        }

        super.onSignIn();

        validateConfiguration(newRequestId());
    }

    @Override
    public void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        super.onSignOut();

        sendEvents(true);

        // Erase the firebase push notification token.
        ConfigurationService configurationService = mTwinlifeImpl.getConfigurationService();
        ConfigurationService.Configuration savedConfig = configurationService.getConfiguration(PREFERENCES);
        configurationService.deleteConfiguration(savedConfig);

        mEnvironmentId = null;
        if (mRefreshJob != null) {
            mRefreshJob.cancel();
            mRefreshJob = null;
        }
    }

    //
    // Implement ManagementService interface
    //

    @NonNull
    private static String getPowerConfiguration(@NonNull DeviceInfo device) {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(DEVICE_BUCKET);
        stringBuilder.append(":");
        stringBuilder.append(device.getAppStandbyBucket());
        stringBuilder.append(",");
        stringBuilder.append(DEVICE_RESTRICTED);
        stringBuilder.append(":");
        stringBuilder.append(device.isBackgroundRestricted() ? "1" : "0");
        stringBuilder.append(",");
        stringBuilder.append(DEVICE_POWER_WHITE_LIST);
        stringBuilder.append(":");
        stringBuilder.append(device.isIgnoringBatteryOptimizations() ? "1" : "0");
        stringBuilder.append(",");
        stringBuilder.append(DEVICE_LOW_RAM);
        stringBuilder.append(":");
        stringBuilder.append(device.isLowRamDevice() ? "1" : "0");
        stringBuilder.append(",");
        stringBuilder.append(DEVICE_CHARGING);
        stringBuilder.append(":");
        stringBuilder.append(device.isCharging() ? "1" : "0");
        stringBuilder.append(",");
        stringBuilder.append(DEVICE_BATTERY);
        stringBuilder.append(":");
        stringBuilder.append(device.getBatteryLevel());
        stringBuilder.append(",run:");
        stringBuilder.append(device.getBackgroundTime());
        stringBuilder.append(":");
        stringBuilder.append(device.getForegroundTime());
        stringBuilder.append(":");
        stringBuilder.append(device.getAlarmCount());
        stringBuilder.append(":");
        stringBuilder.append(device.getNetworkLockCount());
        stringBuilder.append(":");
        stringBuilder.append(device.getProcessingLockCount());
        stringBuilder.append(":");
        stringBuilder.append(device.getInteractiveLockCount());
        stringBuilder.append(":");
        stringBuilder.append(device.getFCMCount());
        stringBuilder.append(":");
        stringBuilder.append(device.getFCMDowngradeCount());
        stringBuilder.append(":");
        stringBuilder.append(device.getFCMTotalDelay());

        return stringBuilder.toString();
    }

    @Nullable
    private String getConnectConfiguration() {

        ConnectionStats stats = mConnection.getConnectStats();
        if (stats == null) {
            return null;
        }

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(CONNECT_DNS_TIME);
        stringBuilder.append(":");
        stringBuilder.append(stats.dnsTime);
        stringBuilder.append(",");
        stringBuilder.append(CONNECT_TCP_TIME);
        stringBuilder.append(":");
        stringBuilder.append(stats.tcpConnectTime);
        stringBuilder.append(",");
        stringBuilder.append(CONNECT_TLS_TIME);
        stringBuilder.append(":");
        stringBuilder.append(stats.tlsConnectTime);
        stringBuilder.append(",");
        stringBuilder.append(CONNECT_TXN_TIME);
        stringBuilder.append(":");
        stringBuilder.append(stats.txnResponseTime);
        ErrorStats errorStats = mConnection.getErrorStats(true);
        if (errorStats != null) {
            if (errorStats.dnsErrorCount != 0) {
                stringBuilder.append(",");
                stringBuilder.append(DNS_ERROR_COUNTER);
                stringBuilder.append(":");
                stringBuilder.append(errorStats.dnsErrorCount);
            }
            if (errorStats.tcpErrorCount != 0) {
                stringBuilder.append(",");
                stringBuilder.append(TCP_ERROR_COUNTER);
                stringBuilder.append(":");
                stringBuilder.append(errorStats.tcpErrorCount);
            }
            if (errorStats.tlsErrorCount != 0) {
                stringBuilder.append(",");
                stringBuilder.append(TLS_ERROR_COUNTER);
                stringBuilder.append(":");
                stringBuilder.append(errorStats.tlsErrorCount);
            }
            if (errorStats.txnErrorCount != 0) {
                stringBuilder.append(",");
                stringBuilder.append(TXN_ERROR_COUNTER);
                stringBuilder.append(":");
                stringBuilder.append(errorStats.txnErrorCount);
            }
            if (errorStats.tlsVerifyErrorCount != 0) {
                stringBuilder.append(",");
                stringBuilder.append(TLS_VERIFY_ERROR_COUNTER);
                stringBuilder.append(":");
                stringBuilder.append(errorStats.tlsVerifyErrorCount);
            }
            if (errorStats.tlsHostErrorCount != 0) {
                stringBuilder.append(",");
                stringBuilder.append(TLS_HOST_ERROR_COUNTER);
                stringBuilder.append(":");
                stringBuilder.append(errorStats.tlsHostErrorCount);
            }
            if (errorStats.proxyErrorCount != 0) {
                stringBuilder.append(",");
                stringBuilder.append(PROXY_ERROR_COUNTER);
                stringBuilder.append(":");
                stringBuilder.append(errorStats.proxyErrorCount);
            }
            if (errorStats.createCounter != 0) {
                stringBuilder.append(",");
                stringBuilder.append(WEBSOCKET_COUNTER);
                stringBuilder.append(":");
                stringBuilder.append(errorStats.createCounter);
            }
        }
        stringBuilder.append(",connect:");
        stringBuilder.append(mConnection.getConnectCounter());
        stringBuilder.append(",rtt:");
        stringBuilder.append(mTwinlifeImpl.getEstimatedRTT());
        stringBuilder.append(",drift:");
        stringBuilder.append(mTwinlifeImpl.getServerTimeCorrection());
        final ProxyDescriptor proxy = mConnection.getActiveProxyDescriptor();
        if (proxy != null) {
            stringBuilder.append(",proxy:");
            if (proxy.isUserProxy()) {
                stringBuilder.append("2");
            } else {
                stringBuilder.append("1");
            }
        }

        return stringBuilder.toString();
    }

    @Override
    public void validateConfiguration(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "validateConfiguration: requestId=" + requestId);
        }

        if (!isServiceOn()) {

            return;
        }

        // Get the environment id from the account service as this is the reference:
        // - If it is known, it means this is an upgrade from an old version and as soon as the
        //   upgrade is finished, the account service will remember the environmentId.
        // - If it does not match, it means this is a re-installation and we got an old environmentId
        //   from the default preferences and we created a new account with its environmentId.
        AccountServiceImpl accountServiceImpl = mTwinlifeImpl.getAccountServiceImpl();
        UUID environmentId = accountServiceImpl.getEnvironmentId();
        if (environmentId == null) {
            environmentId = mEnvironmentId;
        } else {
            mEnvironmentId = environmentId;
        }

        final DeviceInfo device = mJobService.getDeviceInfo(false);
        final Map<String, String> services = new HashMap<>();
        final HashMap<BaseServiceId, String> baseServiceConfigurations = mTwinlifeImpl.getTwinlifeConfiguration();
        for (BaseServiceId baseServiceId : baseServiceConfigurations.keySet()) {
            services.put(SERVICE_NAMES[baseServiceId.ordinal()], baseServiceConfigurations.get(baseServiceId));
        }

        final Map<String, String> configs = new HashMap<>();

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(HARDWARE_CPU_ABI);
        stringBuilder.append(":");
        stringBuilder.append(Build.CPU_ABI);
        if (Build.DEVICE != null) {
            stringBuilder.append(",");
            stringBuilder.append(HARDWARE_DEVICE);
            stringBuilder.append(":");
            stringBuilder.append(Build.DEVICE);
        }
        if (Build.HARDWARE != null) {
            stringBuilder.append(",");
            stringBuilder.append(HARDWARE_HARDWARE);
            stringBuilder.append(":");
            stringBuilder.append(Build.HARDWARE);
        }
        if (Build.ID != null) {
            stringBuilder.append(",");
            stringBuilder.append(HARDWARE_ID);
            stringBuilder.append(":");
            stringBuilder.append(Build.ID);
        }
        if (Build.MANUFACTURER != null) {
            stringBuilder.append(",");
            stringBuilder.append(HARDWARE_MANUFACTURER);
            stringBuilder.append(":");
            stringBuilder.append(Build.MANUFACTURER);
        }
        if (Build.PRODUCT != null) {
            stringBuilder.append(",");
            stringBuilder.append(HARDWARE_PRODUCT);
            stringBuilder.append(":");
            stringBuilder.append(Build.PRODUCT);
        }
        configs.put(VALIDATE_CONFIGURATION_HARDWARE, stringBuilder.toString());

        PackageInfo packageInfo = mTwinlifeImpl.getPackageInfo();
        stringBuilder = new StringBuilder();
        stringBuilder.append(SOFTWARE_PACKAGE_NAME);
        stringBuilder.append(":");
        stringBuilder.append(packageInfo == null ? "" : packageInfo.packageName);
        stringBuilder.append(",");
        stringBuilder.append(SOFTWARE_INSTALLER);
        stringBuilder.append(":");
        String installer = packageInfo == null ? null : packageInfo.installerName;
        if (installer == null) {
            installer = "-";
        }
        stringBuilder.append(installer);
        stringBuilder.append(",");
        stringBuilder.append(SOFTWARE_SDK);
        stringBuilder.append(":");
        stringBuilder.append(Build.VERSION.SDK_INT);

        configs.put(VALIDATE_CONFIGURATION_SOFTWARE, stringBuilder.toString());

        if (BuildConfig.ENABLE_POWER_MANAGEMENT_REPORT) {
            configs.put(VALIDATE_CONFIGURATION_POWER_MANAGEMENT, getPowerConfiguration(device));
        }

        final String connectConfiguration = getConnectConfiguration();
        if (connectConfiguration != null) {
             configs.put(VALIDATE_CONFIGURATION_CONNECT_STATS, connectConfiguration);
        }

        // Send the default locale to have the language, country, variant, script and extension.
        String locale = Locale.getDefault().toString();
        configs.put(VALIDATE_CONFIGURATION_TIMEZONE, String.valueOf(TimeZone.getDefault().getRawOffset() / 1000L));

        ValidateConfigurationIQ iq = new ValidateConfigurationIQ(IQ_VALIDATE_CONFIGURATION_SERIALIZER, requestId,
                mJobService.getState(), environmentId,
                mPushNotificationToken == null ? null : ManagementService.PUSH_NOTIFICATION_FIREBASE_VARIANT,
                mPushNotificationToken, services, Build.BRAND, Build.MODEL, device.getOsName(), locale, "", configs);

        sendDataPacket(iq, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void updateConfiguration(long requestId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateConfiguration: requestId=" + requestId);
        }

        if (!isServiceOn()) {

            return;
        }

        UpdateConfigurationIQ updateConfigurationIQ = new UpdateConfigurationIQ(IQ_UPDATE_CONFIGURATION_SERIALIZER, requestId, mEnvironmentId);
        sendDataPacket(updateConfigurationIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void setPushNotificationToken(@NonNull String pushNotificationVariant, @NonNull String pushNotificationToken) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setPushNotificationToken: pushNotificationVariant=" + pushNotificationVariant + " pushNotificationToken=" + pushNotificationToken);
        }

        if (!pushNotificationVariant.isEmpty() && !ManagementService.PUSH_NOTIFICATION_FIREBASE_VARIANT.equals(pushNotificationVariant)) {

            return;
        }
        if (pushNotificationToken.equals(mPushNotificationToken)) {

            return;
        }

        mPushNotificationToken = pushNotificationToken;
        mSetPushNotificationToken = false;

        ConfigurationService configurationService = mTwinlifeImpl.getConfigurationService();
        ConfigurationService.Configuration savedConfig = configurationService.getConfiguration(PREFERENCES);

        savedConfig.setString(PREFERENCES_PUSH_NOTIFICATION_TOKEN, mPushNotificationToken);
        savedConfig.save();

        setPushNotificationTokenInternal();
    }

    @Override
    public boolean hasPushNotification() {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasPushNotification");
        }

        return mPushNotificationToken != null && !mPushNotificationToken.isEmpty();
    }

    @Override
    public void sendFeedback(@NonNull String email, @NonNull String subject, @NonNull String description,
                             @Nullable String logReport) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendFeedback: email=" + email + " subject=" + subject
                    + " description=" + description + " logReport=" + logReport);
        }

        StringBuilder stringBuilder = new StringBuilder();
        gatherInformation(stringBuilder);

        if (logReport != null) {
            stringBuilder.append("LogReport:\n");
            stringBuilder.append(logReport);
        }

        FeedbackIQ feedbackIQ = new FeedbackIQ(IQ_FEEDBACK_SERIALIZER, newRequestId(), email, subject, description, stringBuilder.toString());

        sendDataPacket(feedbackIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void error(@NonNull ErrorCode errorCode, @Nullable String errorParameter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "error: errorCode=" + errorCode + " errorParameter=" + errorParameter);
        }

        onError(DEFAULT_REQUEST_ID, errorCode, errorParameter);
    }

    @Override
    public void logEvent(@NonNull String eventId, @NonNull Map<String, String> attributes, boolean flush) {
        if (DEBUG) {
            Log.d(LOG_TAG, "logEvent: eventId=" + eventId + " attributes=" + attributes + " flush=" + flush);
        }

        mEvents.get().add(new Event(eventId, attributes));
        sendEvents(flush);
    }

    @Override
    public void assertion(@NonNull AssertPoint assertPoint, @Nullable AssertPoint.Values values,
                          boolean stackTrace, @Nullable Throwable exception) {
        if (DEBUG) {
            Log.d(LOG_TAG, "assertion: controlPoint=" + assertPoint + " values=" + values + " exception=" + exception);
        }

        if (stackTrace && exception == null) {
            try {
                throw new Exception();
            } catch (Exception lException) {
                lException.fillInStackTrace();
                exception = lException;
            }
        } else {
            stackTrace = false;
        }

        final long requestId = newRequestId();
        final AssertionIQ assertionIQ = new AssertionIQ(IQ_ASSERTION_SERIALIZER, requestId, mApplicationId,
                new Version(mTwinlifeImpl.getApplicationVersion()), assertPoint, values, stackTrace, exception);
        synchronized (mAssertions) {
            if (mAssertions.size() >= MAX_ASSERTIONS) {
                return;
            }
            mAssertions.add(assertionIQ);
        }
        if (isSignIn()) {
            flushAssertions();
        }
    }

    @Override
    @NonNull
    public String getLogReport() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getLogReport");
        }

        final String dbDump = mTwinlifeImpl.getDatabaseService().checkConsistency();
        final DeviceInfo device = mJobService.getDeviceInfo(false);
        final String power = getPowerConfiguration(device);
        final StringBuilder sb = new StringBuilder();
        final String connectConfiguration = getConnectConfiguration();

        sb.append(dbDump);
        sb.append("\nPower: ");
        sb.append(power);
        if (connectConfiguration != null) {
            sb.append("\nConnection: ");
            sb.append(connectConfiguration);
        }
        sb.append("\nTimezone: ");
        sb.append(TimeZone.getDefault().getRawOffset() / 1000L);

        final Map<String, ServiceStats> serviceStats = mTwinlifeImpl.getServiceStats();
        for (Map.Entry<String, ServiceStats> stat : serviceStats.entrySet()) {
            ServiceStats info = stat.getValue();
            if (info.sendPacketCount > 0 || info.sendErrorCount > 0 || info.sendDisconnectedCount > 0 || info.sendTimeoutCount > 0) {
                sb.append("\n");
                sb.append(stat.getKey());
                sb.append(": ");
                sb.append(Long.valueOf(info.sendPacketCount));
                sb.append(":");
                sb.append(Long.valueOf(info.sendDisconnectedCount));
                sb.append(":");
                sb.append(Long.valueOf(info.sendErrorCount));
                sb.append(":");
                sb.append(Long.valueOf(info.sendTimeoutCount));
            }
        }
        sb.append("\n");

        return sb.toString();
    }

    //
    // Public methods
    //

    public Configuration getConfiguration() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfiguration");
        }

        return mConfiguration;
    }

    //
    // Private methods
    //

    private void onValidateConfigurationIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onValidateConfigurationIQ: iq=" + iq);
        }

        if (!(iq instanceof OnValidateConfigurationIQ)) {
            return;
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        updateConfigurationInternal((OnValidateConfigurationIQ) iq);
        mTwinlifeImpl.onTwinlifeOnline();

        for (ManagementService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onValidateConfiguration(requestId));
        }

        setPushNotificationTokenInternal();

        flushAssertions();

        // If there are pending events, flush them now.
        if (!mEvents.get().isEmpty()) {
            sendEvents(true);
        }
    }

    private void onUpdateConfigurationIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateConfigurationIQ: iq=" + iq);
        }

        if (!(iq instanceof OnValidateConfigurationIQ)) {
            return;
        }

        receivedIQ(iq.getRequestId());

        updateConfigurationInternal((OnValidateConfigurationIQ) iq);
    }

    private void updateConfigurationInternal(@NonNull OnValidateConfigurationIQ onValidateConfigurationIQ) {

        if (mEnvironmentId == null) {
            mEnvironmentId = onValidateConfigurationIQ.environmentId;

        } else if (!onValidateConfigurationIQ.environmentId.equals(mEnvironmentId)) {
            mTwinlifeImpl.assertion(TwinlifeAssertPoint.ENVIRONMENT_ID, AssertPoint.createEnvironmentId(mEnvironmentId)
                    .putEnvironmentId(onValidateConfigurationIQ.environmentId));

            mEnvironmentId = onValidateConfigurationIQ.environmentId;
        }

        long ttl = onValidateConfigurationIQ.turnTTL;
        if (mRefreshJob != null) {
            mRefreshJob.cancel();
        }
        if (ttl < MIN_UPDATE_TTL) {
            ttl = MIN_UPDATE_TTL;
        }
        mRefreshJob = mJobService.scheduleIn("Refresh configuration", this::refreshConfiguration,
                ttl * 1000 / 2, JobService.Priority.UPDATE);

        Configuration configuration = new Configuration(onValidateConfigurationIQ.turnServers, onValidateConfigurationIQ.hostnames);

        configuration.features = onValidateConfigurationIQ.features;
        configuration.environmentId = mEnvironmentId;

        mConfiguration = configuration;

        mTwinlifeImpl.onUpdateConfiguration(mConfiguration);
    }

    private void setPushNotificationTokenInternal() {
        if (DEBUG) {
            Log.d(LOG_TAG, "setPushNotificationTokenInternal");
        }

        if (mSetPushNotificationToken) {

            return;
        }
        if (!isSignIn() || mPushNotificationToken == null || mEnvironmentId == null) {

            return;
        }

        final long requestId = newRequestId();
        SetPushTokenIQ pushTokenIQ = new SetPushTokenIQ(IQ_SET_PUSH_TOKEN_SERIALIZER, requestId,
                mEnvironmentId, ManagementService.PUSH_NOTIFICATION_FIREBASE_VARIANT, mPushNotificationToken);
        sendDataPacket(pushTokenIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    private void onSetPushNotificationTokenIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSetPushNotificationTokenIQ iq=" + iq);
        }

        receivedIQ(iq.getRequestId());

        // Now we know the server has our push token.
        mSetPushNotificationToken = true;
    }

    private void flushAssertions() {
        if (DEBUG) {
            Log.d(LOG_TAG, "flushAssertions");
        }

        while (true) {
            final AssertionIQ assertionIQ;
            synchronized (mAssertions) {
                if (mAssertions.isEmpty()) {
                    return;
                }
                assertionIQ = mAssertions.get(0);
                mAssertions.remove(0);
            }
            sendDataPacket(assertionIQ, DEFAULT_REQUEST_TIMEOUT);
        }
    }

    private void sendEvents(boolean flush) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendEvents: flush=" + flush);
        }

        if (!isSignIn()) {

            return;
        }

        if (!flush && mEvents.get().size() < MAX_EVENTS) {

            return;
        }

        List<Event> events = mEvents.getAndSet(new ArrayList<>(MAX_EVENTS));
        if (events.isEmpty()) {

            return;
        }

        long requestId = newRequestId();
        LogEventIQ logEventIQ = new LogEventIQ(IQ_LOG_EVENT_SERIALIZER, requestId, events);
        PendingRequest pendingRequest = new PendingRequest(events);
        synchronized (this) {
            mPendingRequests.put(requestId, pendingRequest);
        }
        sendDataPacket(logEventIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    private void onLogEventIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onLogEventIQ iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        synchronized (this) {
            mPendingRequests.remove(requestId);
        }
    }

    private void onFeedbackIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onFeedbackIQ iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);
    }

    protected void onErrorPacket(@NonNull BinaryErrorPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onErrorPacket iq=" + iq);
        }

        receivedIQ(iq.getRequestId());

        PendingRequest request;
        synchronized (this) {
            request = mPendingRequests.remove(iq.getRequestId());
            if (request == null) {

                return;
            }
        }

        // Prepare to send again the events if we failed due to network error.
        final ErrorCode errorCode = iq.getErrorCode();
        if (errorCode == ErrorCode.TWINLIFE_OFFLINE || errorCode == ErrorCode.TIMEOUT_ERROR) {
            mEvents.get().addAll(request.events);
        }
    }

    //
    // Private Methods
    //

    private void gatherInformation(StringBuilder stringBuilder) {
        if (DEBUG) {
            Log.d(LOG_TAG, "gatherInformation stringBuilder=" + stringBuilder);
        }

        PackageInfo packageInfo = mTwinlifeImpl.getPackageInfo();
        if (packageInfo == null) {

            return;
        }

        // Send the default locale to have the language, country, variant, script and extension.
        String locale = Locale.getDefault().toString();

        stringBuilder.append("Locale: ");
        stringBuilder.append(locale);
        stringBuilder.append("\n");
        stringBuilder.append("Package name: ");
        stringBuilder.append(packageInfo.packageName);
        stringBuilder.append("\n");
        stringBuilder.append("Package version: ");
        stringBuilder.append(packageInfo.versionName);
        stringBuilder.append(" (");
        stringBuilder.append(packageInfo.versionCode);
        stringBuilder.append(")\n");
        stringBuilder.append("Installer package name: ");
        stringBuilder.append(packageInfo.installerName);
        stringBuilder.append("\n");
        stringBuilder.append("Board: ");
        stringBuilder.append(Build.BOARD);
        stringBuilder.append("\n");
        stringBuilder.append("BootLoader: ");
        stringBuilder.append(Build.BOOTLOADER);
        stringBuilder.append("\n");
        stringBuilder.append("Brand: ");
        stringBuilder.append(Build.BRAND);
        stringBuilder.append("\n");
        stringBuilder.append("CPU ABI: ");
        stringBuilder.append(Build.CPU_ABI);
        stringBuilder.append("\n");
        stringBuilder.append("Device: ");
        stringBuilder.append(Build.DEVICE);
        stringBuilder.append("\n");
        stringBuilder.append("Display: ");
        stringBuilder.append(Build.DISPLAY);
        stringBuilder.append("\n");
        stringBuilder.append("Hardware: ");
        stringBuilder.append(Build.HARDWARE);
        stringBuilder.append("\n");
        stringBuilder.append("Id: ");
        stringBuilder.append(Build.ID);
        stringBuilder.append("\n");
        stringBuilder.append("Manufacturer: ");
        stringBuilder.append(Build.MANUFACTURER);
        stringBuilder.append("\n");
        stringBuilder.append("Model: ");
        stringBuilder.append(Build.MODEL);
        stringBuilder.append("\n");
        stringBuilder.append("Product: ");
        stringBuilder.append(Build.PRODUCT);
        stringBuilder.append("\n");
        stringBuilder.append("Radio: ");
        stringBuilder.append(Build.getRadioVersion());
        stringBuilder.append("\n");
        stringBuilder.append("OS version code name: ");
        stringBuilder.append(Build.VERSION.CODENAME);
        stringBuilder.append("\n");
        stringBuilder.append("OS version release: ");
        stringBuilder.append(Build.VERSION.RELEASE);
        stringBuilder.append("\n");
        stringBuilder.append("OS version SDK: ");
        stringBuilder.append(Build.VERSION.SDK_INT);
        stringBuilder.append("\n");
    }

    private void refreshConfiguration() {
        if (DEBUG) {
            Log.d(LOG_TAG, "refreshConfiguration");
        }

        if (mRefreshJob != null) {
            mRefreshJob.cancel();
        }
        mRefreshJob = mJobService.scheduleIn("Refresh configuration", this::refreshConfiguration,
                60 * 1000, JobService.Priority.UPDATE);

        if (Logger.INFO) {
            Logger.info(LOG_TAG, "Refresh configuration");
        }
        updateConfiguration(newRequestId());
    }
}
