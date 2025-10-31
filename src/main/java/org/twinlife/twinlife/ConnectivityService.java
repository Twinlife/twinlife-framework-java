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

import java.util.List;

public interface ConnectivityService extends BaseService<ConnectivityService.ServiceObserver> {

    String VERSION = "1.2.0";

    int MAX_PROXIES = 4;

    interface ServiceObserver extends BaseService.ServiceObserver {

        void onNetworkConnect();

        void onNetworkDisconnect();

        void onConnect();

        void onDisconnect();
    }

    class DefaultServiceObserver extends BaseService.DefaultServiceObserver implements ServiceObserver {

        @Override
        public void onNetworkConnect() {
        }

        @Override
        public void onNetworkDisconnect() {
        }

        @Override
        public void onConnect() {
        }

        @Override
        public void onDisconnect() {
        }
    }

    class ConnectivityServiceConfiguration extends BaseServiceConfiguration {

        public ConnectivityServiceConfiguration() {

            super(BaseServiceId.CONNECTIVITY_SERVICE_ID, VERSION, true);
        }
    }

    boolean waitForConnectedNetwork(long timeout);

    void signalAll();

    /**
     * Check if some network connectivity is provided.
     *
     * @return True if the network is available.
     */
    boolean isConnectedNetwork();

    @Nullable
    ProxyDescriptor getProxyDescriptor();

    /**
     * Get a list of proxy that was configured by the user.
     *
     * @return list of user configured proxies.
     */
    @NonNull
    List<ProxyDescriptor> getUserProxies();

    /**
     * Check if user proxies are enabled.
     * @return true if user proxies are enabled.
     */
    boolean isUserProxyEnabled();

    /**
     * Set the user proxy enable configuration.
     * @param enable true to enable user proxies.
     */
    void setUserProxyEnabled(boolean enable);

    /**
     * Set the list of proxies configured by the user.
     *
     * @param proxies the new list of proxies.
     */
    void setUserProxies(@NonNull List<ProxyDescriptor> proxies);

    void setProxyDescriptor(@Nullable ProxyDescriptor proxyDescriptor);

}
