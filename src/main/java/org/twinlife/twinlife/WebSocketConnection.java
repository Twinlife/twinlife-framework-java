/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.libwebsockets.ConnectionStats;
import org.libwebsockets.Container;
import org.libwebsockets.Observer;
import org.libwebsockets.ErrorCategory;
import org.libwebsockets.Session;
import org.libwebsockets.SocketProxyDescriptor;
import org.twinlife.twinlife.util.Logger;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class WebSocketConnection extends Connection implements Observer {
    private static final String LOG_TAG = "WebSocketConnection";
    private static final boolean DEBUG = false;
    private static final boolean INFO = Logger.INFO;

    private static final int MAX_PROXIES = 6;
    private static final int PROXY_START_DELAY = ((5000 / 8) << 12) & 0x003FF000;      // around 5000ms, see wscontainer.h in libwebsockets
    private static final int PROXY_FIRST_START_DELAY = ((500 / 8) << 22) & 0x7FC00000; // around 500ms

    private final Twinlife mTwinlife;
    private final Container mContainer;
    private final List<KeyProxyDescriptor> mKeyProxies;
    private final List<SNIProxyDescriptor> mSNIProxies;
    @NonNull
    private final ProxyDescriptor[] mShuffledProxyDescriptors;
    private final Object mConnectionLock = new Object();
    private final Random mRandom;
    private long mShuffledDeadline;
    @Nullable
    private Session mSession;
    private long mSessionId;
    private boolean mIsConnected = false;
    private boolean mDisconnecting = false;
    private boolean mConnecting = false;
    private ConnectionStats mConnectStats;
    @Nullable
    private ProxyDescriptor[] mProxies;

    /**
     * Holds the initial configuration used while creating the connection.
     */
    private final WebSocketConnectionConfiguration mConfig;

    public WebSocketConnection(@NonNull TwinlifeImpl twinlife, @NonNull WebSocketConnectionConfiguration configuration,
                               @NonNull TwinlifeConfiguration twinlifeConfiguration) {
        super(twinlife, twinlife.getSerializerFactory(), twinlifeConfiguration.proxies);

        if (DEBUG) {
            Log.d(LOG_TAG, "WebSocketConnection");
        }

        mKeyProxies = new ArrayList<>();
        mSNIProxies = new ArrayList<>();
        mRandom = new Random();
        mConfig = configuration;
        mTwinlife = twinlife;
        mContainer = new Container(0);

        // Separate the Keyed proxies vs the SNI ones.
        if (twinlifeConfiguration.proxies != null) {
            String[] sniList = twinlifeConfiguration.tokens;
            String[] tld = new String[sniList.length];
            String[] domains = new String[sniList.length];
            String[] site = new String[sniList.length];
            for (int i = 0; i < sniList.length; i++) {
                String sni = sniList[i];
                String[] parts = sni.split("\\.");
                tld[i] = parts[2];
                domains[i] = parts[1];
                site[i] = parts[0];
            }
            for (ProxyDescriptor proxy : twinlifeConfiguration.proxies) {
                if (proxy instanceof KeyProxyDescriptor) {
                    mKeyProxies.add((KeyProxyDescriptor) proxy);
                } else if (proxy instanceof SNIProxyDescriptor) {
                    String sni = createSNI(site, domains, tld);
                    mSNIProxies.add(new SNIProxyDescriptor(proxy.getAddress(), proxy.getPort(), proxy.getSTUNPort(), sni, false));
                }
            }

            // See how many proxies we can use for the setupProxies() algorithm where we try
            // to allocate one SNI proxy and one keyed proxy randomly until we fill the MAX_PROXIES.
            int count = Math.min(mKeyProxies.size(), mSNIProxies.size());
            if (count > 0) {
                count = count * 2;
            } else {
                count = mSNIProxies.size();
            }
            if (count > MAX_PROXIES) {
                count = MAX_PROXIES;
            }
            mShuffledProxyDescriptors = new ProxyDescriptor[count];
        } else {
            mShuffledProxyDescriptors = new ProxyDescriptor[0];
        }
        mShuffledDeadline = 0;
    }

    @NonNull
    private String createSNI(@NonNull String[] site, @NonNull String[] domain, @NonNull String[] tld) {

        int siteIdx = mRandom.nextInt(site.length);
        int domainIdx = mRandom.nextInt(domain.length);
        int tldIdx = mRandom.nextInt(domain.length);

        return site[siteIdx] + "." + domain[domainIdx] + "." + tld[tldIdx];
    }

    public void destroy() {

        disconnect();

        mReaderExecutorService.shutdownNow();
    }

    private void setupProxies() {
        if (DEBUG) {
            Log.d(LOG_TAG, "setupProxies");
        }

        // Shuffle the proxy descriptors randomly each 4 hours and try to get one keyed proxy
        // followed by one SNI proxy to increase the chance to try different proxy modes.
        final long now = System.currentTimeMillis();
        if (mShuffledProxyDescriptors.length > 0 && mShuffledDeadline < now) {
            mShuffledDeadline = now + 3 * 3600 * 1000L + (long) (mRandom.nextInt(4 * 3600)) * 1000L; // Note: we cannot use nextLong()
            final List<KeyProxyDescriptor> ksList = new ArrayList<>(mKeyProxies);
            final List<SNIProxyDescriptor> sList = new ArrayList<>(mSNIProxies);
            final int length = mShuffledProxyDescriptors.length;
            for (int i = 0; i < length; i++) {
                ProxyDescriptor proxyDescriptor = null;
                if ((i & 0x01) != 0 && !ksList.isEmpty()) {
                    int j = mRandom.nextInt(ksList.size());
                    proxyDescriptor = ksList.remove(j);
                }
                if (!sList.isEmpty() && (proxyDescriptor == null || (i & 0x01) == 0)) {
                    int j = mRandom.nextInt(sList.size());
                    proxyDescriptor = sList.remove(j);
                }
                mShuffledProxyDescriptors[i] = proxyDescriptor;
            }
        }
    }

    /**
     * Get the server domain string.
     *
     * @return the server domain.
     */
    @Override
    @NonNull
    public String getDomain() {

        return mConfig.getDomain();
    }

    @Override
    public boolean isConnected() {
        if (DEBUG) {
            Log.d(LOG_TAG, "isConnected=" + mIsConnected);
        }

        synchronized (mConnectionLock) {
            return mIsConnected;
        }
    }

    public void wakeupWorker() {
        if (DEBUG) {
            Log.d(LOG_TAG, "wakeupWorker isConnected=" + mIsConnected);
        }

        mContainer.triggerWorker();
    }

    @Override
    public void connect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "connect");
        }

        synchronized (mConnectionLock) {
            if (mConnecting || mIsConnected) {
                return;
            }
            mConnecting = true;
            mSessionId++;
        }

        if (INFO) {
            Log.i(LOG_TAG, "connecting to " + mConfig.getHost() + " as session " + mSessionId);
        }
        mCreateSocketCounter.incrementAndGet();

        // Before connecting, get the list of user proxies followed by the shuffled pre-defined list.
        final ConnectivityService connectivityService = mTwinlife.getConnectivityService();
        final List<ProxyDescriptor> userProxies = connectivityService.isUserProxyEnabled() ? connectivityService.getUserProxies() : new ArrayList<>();
        final ProxyDescriptor lastProxyDescriptor = connectivityService.getProxyDescriptor();
        final int proxyOffset = (lastProxyDescriptor == null ? 0 : 1);
        setupProxies();

        mProxies = new ProxyDescriptor[userProxies.size() + mShuffledProxyDescriptors.length + proxyOffset];
        if (lastProxyDescriptor != null) {
            mProxies[0] = lastProxyDescriptor;
        }
        for (int i = 0; i < userProxies.size(); i++) {
            mProxies[i + proxyOffset] = userProxies.get(i);
        }
        System.arraycopy(mShuffledProxyDescriptors, 0, mProxies, userProxies.size() + proxyOffset, mShuffledProxyDescriptors.length);
        int method = mConfig.isSecure() ? Container.CONFIG_SECURE : 0;
        method |= Container.CONFIG_DIRECT_CONNECT;
        if (proxyOffset > 0) {
            method |= Container.CONFIG_FIRST_PROXY | PROXY_FIRST_START_DELAY;
        }
        method |= PROXY_START_DELAY;
        int proxyCount = Math.min(mProxies.length, MAX_PROXIES + proxyOffset);
        SocketProxyDescriptor[] proxies = new SocketProxyDescriptor[proxyCount];
        for (int i = 0; i < proxyCount; i++) {
            ProxyDescriptor proxy = mProxies[i];
            if (proxy instanceof SNIProxyDescriptor) {
                SNIProxyDescriptor sniProxyDescriptor = (SNIProxyDescriptor) proxy;
                proxies[i] = SocketProxyDescriptor.createSNIProxy(proxy.getAddress(), proxy.getPort(), false, sniProxyDescriptor.getCustomSNI());
            } else if (proxy instanceof KeyProxyDescriptor) {
                KeyProxyDescriptor keyProxyDescriptor = (KeyProxyDescriptor) proxy;
                String path = KeyProxyDescriptor.getProxyPath(keyProxyDescriptor.getKey(), mConfig.getHost(), mConfig.getPort());
                proxies[i] = SocketProxyDescriptor.createKeyProxy(proxy.getAddress(), proxy.getPort(), path);
            }
        }
        mSession = mContainer.create(this, mSessionId, mConfig.getPort(), mConfig.getHost(), null,
                mConfig.getPath(), method, 20000, proxies);

        if (mSession == null) {
            synchronized (mConnectionLock) {
                mConnecting = false;
            }
        }
    }

    @Override
    public void disconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "disconnect");
        }

        if (INFO) {
            Log.i(LOG_TAG, "disconnecting " + mSessionId + " from " + mConfig.getHost());
        }

        final boolean closed;
        synchronized (mConnectionLock) {
            mDisconnecting = mIsConnected;
            mIsConnected = false;
            if (mSession != null) {
                closed = mSession.close();
                mSession = null;
            } else {
                closed = true;
            }
        }
        if (!closed) {
            mConnectionListener.onDisconnect(ErrorCategory.ERR_NONE);
            synchronized (mConnectionLock) {
                mDisconnecting = false;
            }
        }
    }

    /**
     * Get stats collected during establishment of the web socket connection.
     *
     * @return the connection stats.
     */
    @Override
    @Nullable
    public ConnectionStats getConnectStats() {

        return mConnectStats;
    }

    /**
     * Get the current connection status.
     *
     * @return the connection status.
     */
    @NonNull
    public ConnectionStatus getConnectionStatus() {

        final ConnectionStatus result;
        synchronized (mConnectionLock) {
            if (mIsConnected) {
                result = ConnectionStatus.CONNECTED;
            } else if (mConnecting) {
                result = ConnectionStatus.CONNECTING;
            } else {
                result = ConnectionStatus.NO_SERVICE;
            }
        }

        if (DEBUG) {
            Log.d(LOG_TAG, "getConnectionStatus " + result);
        }
        return result;
    }

    @Override
    public boolean sendDataPacket(byte[] packet) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendPacket packet.length=" + packet.length);
        }

        synchronized (mConnectionLock) {
            if (mSession != null) {
                return mSession.sendMessage(packet, true);
            } else {
                return false;
            }
        }
    }

    @Override
    public void onConnect(long sessionId, @NonNull ConnectionStats[] stats, int active) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConnect sessionId=" + sessionId + " stats=" + stats
                    + " active=" + active);
        }

        final ProxyDescriptor proxyDescriptor;
        synchronized (mConnectionLock) {
            mConnecting = false;
            mIsConnected = true;
            mConnectStats = stats[active];
            // mProxies is the list that we gave to libwebsocket and the proxyIndex gives us the proxy descriptor used.
            if (mConnectStats.proxyIndex >= 0 && mProxies != null && mConnectStats.proxyIndex < mProxies.length) {
                mActiveProxyDescriptor = mProxies[mConnectStats.proxyIndex];
            } else {
                mActiveProxyDescriptor = null;
            }
            proxyDescriptor = mActiveProxyDescriptor;
        }

        mConnectedCounter.incrementAndGet();

        if (INFO) {
            Log.i(LOG_TAG, "connected to " + mConnectStats.ipAddr
                    + (proxyDescriptor != null ? " with proxy " + proxyDescriptor.getDescriptor() : "")
                    + " as session " + mSessionId);
        }
        final ConnectivityService connectivityService = mTwinlife.getConnectivityService();
        connectivityService.setProxyDescriptor(proxyDescriptor);
        mConnectionListener.onConnect();
    }

    @Override
    public void onConnectError(long sessionId, @NonNull ConnectionStats[] stats, int error) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConnectError sessionId=" + sessionId + " stats=" + stats
                    + " error=" + error);
        }

        final ErrorCategory errorCategory = ErrorCategory.toErrorCategory(error);

        if (INFO) {
            Log.i(LOG_TAG, "connection " + mSessionId + " to " + mConfig.getHost() + " failed: " + errorCategory);
        }
        recordError(errorCategory);
        synchronized (mConnectionLock) {
            mSession = null;
            mConnecting = false;
            mIsConnected = false;
            mDisconnecting = true;
        }
        mConnectionListener.onDisconnect(errorCategory);
        synchronized (mConnectionLock) {
            mDisconnecting = false;
        }
    }

    @Override
    public void onReceive(long sessionId, @NonNull ByteBuffer message, boolean binary) {

        onBinaryMessageInternal(message);
    }

    @Override
    public void onClose(long sessionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onClose sessionId=" + sessionId);
        }

        synchronized (mConnectionLock) {
            mSession = null;
            mConnecting = false;
            mIsConnected = false;
            mDisconnecting = true;
        }
        if (INFO) {
            Log.i(LOG_TAG, "connection " + mSessionId + " to " + mConfig.getHost() + " closed explicitly");
        }
        onClose();

        synchronized (mConnectionLock) {
            mDisconnecting = false;
        }
    }

    public void service(int timeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "service timeout=" + timeout);
        }

        mContainer.service(timeout);
    }
}
