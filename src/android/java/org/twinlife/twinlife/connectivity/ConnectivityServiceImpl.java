/*
 *  Copyright (c) 2012-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Tengfei Wang (Tengfei.Wang@twinlife-systems.com)
 *   Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *   Xiaobo Xie (Xiaobo.Xie@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.connectivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.os.Build;
import android.util.Log;

import org.twinlife.twinlife.ProxyDescriptor;
import org.twinlife.twinlife.SNIProxyDescriptor;
import org.twinlife.twinlife.ConfigurationService;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.ConnectivityService;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class ConnectivityServiceImpl extends BaseServiceImpl<ConnectivityService.ServiceObserver> implements ConnectivityService {
    private static final String LOG_TAG = "ConnectivityServiceImpl";
    private static final boolean DEBUG = false;

    private static final int MAX_LEASE = 64;

    private static final String WEB_SOCKET_CONNECTION_PREFERENCES = "WebSocketConnection";
    private static final String WEB_SOCKET_CONNECTION_PREFERENCES_ACTIVE_PROXY_DESCRIPTOR_INDEX = "ActiveProxyDescriptorIndex";
    private static final String WEB_SOCKET_CONNECTION_PREFERENCES_ACTIVE_PROXY_DESCRIPTOR_LEASE = "ActiveProxyDescriptorLease";
    private static final String WEB_SOCKET_CONNECTION_PREFERENCES_USER_PROXIES = "UserProxies";
    private static final String WEB_SOCKET_CONNECTION_PREFERENCES_USER_PROXY_ENABLE = "UserProxyEnable";

    private class ConnectivityReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConnectivityReceiver.onReceive");
            }

            String action = intent.getAction();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(action)) {
                updateNetworkConnectivity();
            }
        }
    }

    private volatile boolean mIsConnectedNetwork = false;
    @Nullable
    private final ConnectivityManager mConnectivityManager;
    private final ConnectivityReceiver mConnectivityReceiver;
    private final ReentrantLock mConnectedLock = new ReentrantLock();
    private final Condition mConnectedCondition = mConnectedLock.newCondition();
    private final Context mContext;
    private final ConfigurationService mConfigurationService;
    private final List<ProxyDescriptor> mUserProxies;
    private final ProxyDescriptor[] mProxyDescriptors;
    private String mUserProxyConfig;
    private ProxyDescriptor mLastProxyDescriptor;
    private int mActiveProxyDescriptorLease;
    private int mActiveProxyIndex;
    private boolean mUserProxyEnabled;

    public ConnectivityServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Context context, @NonNull Connection connection) {

        super(twinlifeImpl, connection);

        Context appContext = context.getApplicationContext();
        mContext = context;
        mConnectivityManager = (ConnectivityManager) appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        mConnectivityReceiver = new ConnectivityReceiver();
        mConfigurationService = twinlifeImpl.getConfigurationService();
        mUserProxies = new ArrayList<>();
        mProxyDescriptors = connection.getDefaultProxies();
        mUserProxyConfig = "";
        mUserProxyEnabled = true;
        setServiceConfiguration(new ConnectivityServiceConfiguration());
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        ConnectivityServiceConfiguration connectivityServiceConfiguration = new ConnectivityServiceConfiguration();

        if (!(baseServiceConfiguration instanceof ConnectivityServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        setServiceConfiguration(connectivityServiceConfiguration);
        setServiceOn(true);
        setConfigured(true);

        final ConfigurationService.Configuration savedConfiguration = mConfigurationService.getConfiguration(WEB_SOCKET_CONNECTION_PREFERENCES);
        mUserProxyConfig = savedConfiguration.getString(WEB_SOCKET_CONNECTION_PREFERENCES_USER_PROXIES, "");
        mUserProxyEnabled = savedConfiguration.getBoolean(WEB_SOCKET_CONNECTION_PREFERENCES_USER_PROXY_ENABLE, false);
        final String[] proxies = mUserProxyConfig.split(" ");
        for (String proxy : proxies) {
            ProxyDescriptor proxyDescriptor = SNIProxyDescriptor.create(proxy);
            if (proxyDescriptor != null) {
                mUserProxies.add(proxyDescriptor);
            }
        }

        mActiveProxyIndex = savedConfiguration.getInt(WEB_SOCKET_CONNECTION_PREFERENCES_ACTIVE_PROXY_DESCRIPTOR_INDEX, -1);
        mActiveProxyDescriptorLease = savedConfiguration.getInt(WEB_SOCKET_CONNECTION_PREFERENCES_ACTIVE_PROXY_DESCRIPTOR_LEASE, MAX_LEASE);
        if (mActiveProxyIndex < 0) {
            mLastProxyDescriptor = null;
        } else if (mActiveProxyIndex < mUserProxies.size()) {
            mLastProxyDescriptor = mUserProxyEnabled ? mUserProxies.get(mActiveProxyIndex) : null;
        } else {
            int index = mActiveProxyIndex - mUserProxies.size();
            if (mProxyDescriptors != null && index < mProxyDescriptors.length) {
                mLastProxyDescriptor = mProxyDescriptors[index];
            } else {
                mActiveProxyIndex = -1;
                savedConfiguration.setInt(WEB_SOCKET_CONNECTION_PREFERENCES_ACTIVE_PROXY_DESCRIPTOR_INDEX, -1);
                savedConfiguration.save();
            }
        }
    }

    @Override
    public void onCreate() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreate");
        }

        super.onCreate();

        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        mContext.registerReceiver(mConnectivityReceiver, filter);

        updateNetworkConnectivity();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDestroy");
        }

        super.onDestroy();

        mContext.unregisterReceiver(mConnectivityReceiver);
    }

    @Override
    public void onConnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConnect");
        }

        super.onConnect();

        for (ConnectivityService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(serviceObserver::onConnect);
        }
    }

    @Override
    public void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        super.onDisconnect();

        for (ConnectivityService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(serviceObserver::onDisconnect);
        }
    }

    /**
     * Check if some network connectivity is provided.
     *
     * @return True if the network is available.
     */
    @Override
    public boolean isConnectedNetwork() {
        if (DEBUG) {
            Log.d(LOG_TAG, "hasNetwork");
        }

        updateNetworkConnectivity();
        return mIsConnectedNetwork;
    }

    @Nullable
    public ProxyDescriptor getProxyDescriptor() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getProxyDescriptor");
        }

        return mLastProxyDescriptor;
    }

    /**
     * Get a list of proxy that was configured by the user.
     *
     * @return list of user configured proxies.
     */
    @NonNull
    public List<ProxyDescriptor> getUserProxies() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getUserProxies");
        }

        synchronized (this) {
            return new ArrayList<>(mUserProxies);
        }
    }

    /**
     * Set the list of proxies configured by the user.
     *
     * @param proxies the new list of proxies.
     */
    @Override
    public void setUserProxies(@NonNull List<ProxyDescriptor> proxies) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setUserProxies proxies=" + proxies);
        }

        String newConfig;
        synchronized (this) {
            mUserProxies.clear();

            StringBuilder sb = new StringBuilder();
            for (ProxyDescriptor proxy : proxies) {
                if (proxy.isUserProxy()) {
                    if (sb.length() > 0) {
                        sb.append(" ");
                    }
                    sb.append(proxy.getDescriptor());
                    mUserProxies.add(proxy);
                }
            }
            newConfig = sb.toString();
            if (newConfig.equals(mUserProxyConfig)) {
                return;
            }
            mUserProxyConfig = newConfig;
        }

        final ConfigurationService.Configuration savedConfiguration = mConfigurationService.getConfiguration(WEB_SOCKET_CONNECTION_PREFERENCES);
        if (mLastProxyDescriptor != null) {
            saveProxyDescriptor(savedConfiguration, mLastProxyDescriptor);
        }
        savedConfiguration.setString(WEB_SOCKET_CONNECTION_PREFERENCES_USER_PROXIES, newConfig);
        savedConfiguration.save();

        reconnect();
    }

    /**
     * Check if user proxies are enabled.
     * @return true if user proxies are enabled.
     */
    public boolean isUserProxyEnabled() {

        return mUserProxyEnabled;
    }

    /**
     * Set the user proxy enable configuration.
     * @param enable true to enable user proxies.
     */
    public void setUserProxyEnabled(boolean enable) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setUserProxyEnabled enable=" + enable);
        }

        if (mUserProxyEnabled != enable) {
            mUserProxyEnabled = enable;

            final ConfigurationService.Configuration savedConfiguration = mConfigurationService.getConfiguration(WEB_SOCKET_CONNECTION_PREFERENCES);
            savedConfiguration.setBoolean(WEB_SOCKET_CONNECTION_PREFERENCES_USER_PROXY_ENABLE, enable);
            savedConfiguration.save();
            reconnect();
        }
    }

    @Override
    public void setProxyDescriptor(@Nullable ProxyDescriptor proxyDescriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setProxyDescriptor proxyDescriptor=" + proxyDescriptor);
        }

        final ConfigurationService.Configuration savedConfiguration = mConfigurationService.getConfiguration(WEB_SOCKET_CONNECTION_PREFERENCES);

        if (saveProxyDescriptor(savedConfiguration, proxyDescriptor)) {
            savedConfiguration.save();
        }
    }

    //
    // Public Methods
    //

    @Override
    public boolean waitForConnectedNetwork(long timeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "waitForConnectedNetwork timeout=" + timeout);
        }

        updateNetworkConnectivity();
        if (mIsConnectedNetwork || timeout == 0) {

            return mIsConnectedNetwork;
        }

        if (Logger.INFO) {
            Log.i(LOG_TAG, "Waiting for network connection for " + timeout + " ms");
        }
        try {
            mConnectedLock.lock();
            boolean ignored = mConnectedCondition.await(timeout, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) {
        } finally {
            mConnectedLock.unlock();
        }

        // We were suspended for some time, the network could have changed, get its status again.
        updateNetworkConnectivity();

        return mIsConnectedNetwork;
    }

    public void signalAll() {
        if (DEBUG) {
            Log.d(LOG_TAG, "signalAll");
        }

        mConnectedLock.lock();
        mConnectedCondition.signalAll();
        mConnectedLock.unlock();
    }

    //
    // Private Methods
    //

    private boolean saveProxyDescriptor(@NonNull ConfigurationService.Configuration savedConfiguration, @Nullable ProxyDescriptor proxyDescriptor) {

        int index = -1;
        boolean needSave;
        synchronized (this) {
            if (proxyDescriptor != null) {
                boolean found = false;
                for (ProxyDescriptor proxy : mUserProxies) {
                    index++;
                    if (proxyDescriptor == proxy) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Important note: the proxyDescriptor instance can contain a SNI configuration that is random
                    // and therefore we may not find in mProxyDescriptors the instance that the WebSocketConnection
                    // is giving us, and we must record in mLastProxyDescriptor the instance we have and not what we received.
                    if (mProxyDescriptors != null) {
                        for (ProxyDescriptor proxy : mProxyDescriptors) {
                            index++;
                            if (proxyDescriptor == proxy || proxyDescriptor.isSame(proxy)) {
                                proxyDescriptor = proxy;
                                found = true;
                                break;
                            }
                        }
                    }
                    if (!found) {
                        index = -1;
                    }
                }
            }
            mLastProxyDescriptor = proxyDescriptor;
            if (index != mActiveProxyIndex) {
                mActiveProxyDescriptorLease = index < 0 ? 0 : MAX_LEASE;
                needSave = true;
            } else if (index >= 0) {
                mActiveProxyDescriptorLease--;
                if (mActiveProxyDescriptorLease <= 0) {
                    index = -1;
                }
                needSave = true;
            } else {
                needSave = false;
            }
            mActiveProxyIndex = index;
        }
        if (needSave) {
            savedConfiguration.setInt(WEB_SOCKET_CONNECTION_PREFERENCES_ACTIVE_PROXY_DESCRIPTOR_LEASE, mActiveProxyDescriptorLease);
            savedConfiguration.setInt(WEB_SOCKET_CONNECTION_PREFERENCES_ACTIVE_PROXY_DESCRIPTOR_INDEX, index);
        }
        return needSave;
    }

    private void reconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "reconnect");
        }

        mLastProxyDescriptor = null;
        if (mTwinlifeImpl.isConnected()) {
            mTwinlifeImpl.disconnect();
        } else {
            mTwinlifeImpl.connect();
        }
    }

    private void updateNetworkConnectivity() {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateNetworkConnectivity");
        }

        final boolean connectedNetwork;
        if (mConnectivityManager == null) {
            connectedNetwork = true;
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network nw = mConnectivityManager.getActiveNetwork();
                if (nw == null) {
                    connectedNetwork = false;
                } else {
                    NetworkCapabilities actNw = mConnectivityManager.getNetworkCapabilities(nw);
                    connectedNetwork = actNw != null && (actNw.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                            || actNw.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                            || actNw.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                            || actNw.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH));
                }
            } else {
                // Get the network information without holding any lock since this is a long operation.
                NetworkInfo networkInfo = mConnectivityManager.getActiveNetworkInfo();
                //noinspection deprecation
                connectedNetwork = networkInfo != null && networkInfo.isConnected();
            }
        }

        synchronized (this) {
            if (mIsConnectedNetwork == connectedNetwork) {

                return;
            }

            mIsConnectedNetwork = connectedNetwork;
        }

        if (connectedNetwork) {
            onNetworkConnect();
        } else {
            onNetworkDisconnect();
        }
    }

    private void onNetworkConnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onNetworkConnect");
        }

        EventMonitor.event("Network connected");

        mConnectedLock.lock();
        mConnectedCondition.signalAll();
        mConnectedLock.unlock();

        for (ConnectivityService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(serviceObserver::onNetworkConnect);
        }
    }

    private void onNetworkDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onNetworkDisconnect");
        }

        EventMonitor.event("Network disconnected");

        mTwinlifeImpl.disconnect();

        for (ConnectivityService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(serviceObserver::onNetworkDisconnect);
        }
    }
}
