/*
 *  Copyright (c) 2021-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.libwebsockets.ErrorCategory;
import org.libwebsockets.ConnectionStats;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.ByteBufferInputStream;
import org.twinlife.twinlife.util.SchemaKey;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

public abstract class Connection {
    private static final String LOG_TAG = "Connection";
    private static final boolean DEBUG = false;

    static class WebSocketThreadFactory implements ThreadFactory {

        public Thread newThread(@NonNull Runnable runnable) {

            return new Thread(runnable, "websocket-reader");
        }
    }

    @NonNull
    private final SerializerFactory mSerializerFactory;
    @NonNull
    protected final Map<SchemaKey, Pair<Serializer, BinaryPacketListener>> mBinaryListeners = new HashMap<>();
    @Nullable
    protected final ProxyDescriptor[] mProxyDescriptors;
    @NonNull
    protected final ConnectionListener mConnectionListener;
    @Nullable
    protected volatile ProxyDescriptor mActiveProxyDescriptor;
    protected final ExecutorService mReaderExecutorService;

    // Error counters for analysis of connexion issues.
    // These counters are incremented when errors occur and cleared
    // when they are returned to the server (if we can!).
    private final AtomicLong mDnsErrorCounter = new AtomicLong(0);
    private final AtomicLong mTcpErrorCounter = new AtomicLong(0);
    private final AtomicLong mTlsErrorCounter = new AtomicLong(0);
    private final AtomicLong mTxnErrorCounter = new AtomicLong(0);
    private final AtomicLong mProxyErrorCounter = new AtomicLong(0);
    private final AtomicLong mVerifyErrorCounter = new AtomicLong(0);
    private final AtomicLong mTlsHostnameErrorCounter = new AtomicLong(0);
    protected final AtomicLong mCreateSocketCounter = new AtomicLong(0);
    protected final AtomicLong mConnectedCounter = new AtomicLong(0);

    /**
     * Create a new Connection to the Openfire server.
     *
     */
    protected Connection(@NonNull ConnectionListener connectionListener,
                         @NonNull SerializerFactory serializerFactory, @Nullable ProxyDescriptor[] proxyDescriptors) {

        mConnectionListener = connectionListener;
        mSerializerFactory = serializerFactory;
        mProxyDescriptors = proxyDescriptors;
        mReaderExecutorService = Executors.newSingleThreadExecutor(new WebSocketThreadFactory());
    }

    /**
     * Returns true if currently connected to the Openfire server.
     *
     * @return true if connected.
     */
    public abstract boolean isConnected();

    /**
     * Create the connection to the Openfire server.
     */
    public abstract void connect();

    public abstract void service(int timeout);

    public abstract void wakeupWorker();

    /**
     * Sends the specified packet to the server.
     *
     * @param packet the packet to send.
     * return true if the packet was sent and false if the connection was closed.
     */
    public abstract boolean sendDataPacket(byte[] packet);

    /**
     * Closes the connection.
     */
    public abstract void disconnect();

    /**
     * Get stats collected during establishment of the web socket connection.
     *
     * @return the connection stats or null if we don't have stats.
     */
    @Nullable
    public abstract ConnectionStats getConnectStats();

    public long getConnectCounter() {

        return mConnectedCounter.get();
    }

    /**
     * Get stats about errors and retries to connect to the server.
     *
     * @param reset when true clear the counters after getting their values.
     * @return the error stats or null if we don't know the errors.
     */
    @Nullable
    public ErrorStats getErrorStats(boolean reset) {

        final long dnsError = reset ? mDnsErrorCounter.getAndSet(0) : mDnsErrorCounter.get();
        final long tcpError = reset ? mTcpErrorCounter.getAndSet(0) : mTcpErrorCounter.get();
        final long tlsError = reset ? mTlsErrorCounter.getAndSet(0) : mTlsErrorCounter.get();
        final long proxyError = reset ? mProxyErrorCounter.getAndSet(0) : mProxyErrorCounter.get();
        final long xtsError = reset ? mTxnErrorCounter.getAndSet(0) : mProxyErrorCounter.get();
        final long tlsVerifyError = reset ? mVerifyErrorCounter.getAndSet(0) : mVerifyErrorCounter.get();
        final long tlsHostError = reset ? mTlsHostnameErrorCounter.getAndSet(0) : mTlsHostnameErrorCounter.get();
        final long createCounter = mCreateSocketCounter.get();
        final long connectCounter = mConnectedCounter.get();

        if (dnsError == 0 && tcpError == 0 && tlsError == 0 && proxyError == 0 && xtsError == 0
                && tlsVerifyError == 0 && tlsHostError == 0 && createCounter == 0 && connectCounter == 0) {
            return null;
        }

        return new ErrorStats(dnsError, tcpError, tlsError, xtsError, proxyError, tlsVerifyError, tlsHostError, createCounter, connectCounter);
    }

    /**
     * Destroy the connection object.
     */
    public abstract void destroy();

    /**
     * Get the server domain string.
     *
     * @return the server domain.
     */
    @NonNull
    public abstract String getDomain();

    /**
     * Get the current connection status.
     *
     * @return the connection status.
     */
    @NonNull
    public abstract ConnectionStatus getConnectionStatus();

    public ProxyDescriptor getActiveProxyDescriptor() {

        return mActiveProxyDescriptor;
    }

    @Nullable
    public ProxyDescriptor[] getDefaultProxies() {

        return mProxyDescriptors;
    }

    /**
     * Registers a packet listener with this connection. A packet filter
     * determines which packets will be delivered to the listener. If the same
     * packet listener is added again with a different filter, only the new
     * filter will be used.
     *
     * @param packetListener the packet listener to notify of new received packets.
     */
    public void addPacketListener(@NonNull Serializer serializer, @NonNull BinaryPacketListener packetListener) {

        final SchemaKey key = new SchemaKey(serializer.schemaId, serializer.schemaVersion);

        mBinaryListeners.put(key, new Pair<>(serializer, packetListener));
    }

    protected void recordError(@NonNull ErrorCategory errorCategory) {

        switch (errorCategory) {
            case ERR_DNS:
                mDnsErrorCounter.incrementAndGet();
                break;

            case ERR_CONNECT:
            case ERR_TCP:
                mTcpErrorCounter.incrementAndGet();
                break;

            case ERR_TLS_HOSTNAME:
            case ERR_INVALID_CA:
                mTlsHostnameErrorCounter.incrementAndGet();
                break;

            case ERR_TLS:
                mTlsErrorCounter.incrementAndGet();
                break;

            case ERR_PROXY:
                mProxyErrorCounter.incrementAndGet();
                break;

            case ERR_NONE:
            case ERR_RESOURCE:
                // Don't track these error.
                break;

            default:
                mTxnErrorCounter.incrementAndGet();
                break;
        }
    }

    /**
     * Called when the web socket connection is closed to notify the connection
     * listener we lost the server connection.
     */
    protected void onClose() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onClose");
        }

        mConnectionListener.onDisconnect(ErrorCategory.ERR_NONE);
    }

    protected void onBinaryMessageInternal(@NonNull ByteBuffer buffer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onBinaryMessageInternal: buffer=" + buffer);
        }

        try {
            final ByteBufferInputStream inputStream = new ByteBufferInputStream(buffer);
            BinaryDecoder binaryDecoder = new BinaryCompactDecoder(inputStream);
            UUID schemaId = binaryDecoder.readUUID();
            int version = binaryDecoder.readInt();
            SchemaKey key = new SchemaKey(schemaId, version);
            Pair<Serializer, BinaryPacketListener> listener = mBinaryListeners.get(key);
            if (listener != null) {
                BinaryPacketIQ iq = (BinaryPacketIQ) listener.first.deserialize(mSerializerFactory, binaryDecoder);
                mReaderExecutorService.submit(() -> listener.second.processPacket(iq));
            }

        } catch (Exception ex) {
            Log.e(LOG_TAG, "Internal error " + ex.getMessage() + " connected=" + isConnected(), ex);

        }
    }
}
