/*
 *  Copyright (c) 2012-2023 twinlife SA.
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

import androidx.annotation.NonNull;
import android.util.Log;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.ConnectivityService;
import org.twinlife.twinlife.TwinlifeImpl;

public class EngineConnectivityServiceImpl extends BaseServiceImpl implements ConnectivityService {
    private static final String LOG_TAG = "ConnectivityServiceImpl";
    private static final boolean DEBUG = false;

    public EngineConnectivityServiceImpl(TwinlifeImpl twinlifeImpl, Connection connection) {

        super(twinlifeImpl, connection);

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
    }

    @Override
    public void onConnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onConnect");
        }

        super.onConnect();

        for (BaseService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(((ConnectivityService.ServiceObserver) serviceObserver)::onConnect);
        }
    }

    @Override
    public void onDisconnect() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDisconnect");
        }

        super.onDisconnect();

        for (BaseService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(((ConnectivityService.ServiceObserver) serviceObserver)::onDisconnect);
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
            Log.d(LOG_TAG, "isConnectedNetwork");
        }

        return true;
    }

    //
    // Public Methods
    //

    @Override
    public boolean waitForConnectedNetwork(int timeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "waitForConnectedNetwork timeout=" + timeout);
        }

        return true;
    }

    public void signalAll() {
        if (DEBUG) {
            Log.d(LOG_TAG, "signalAll");
        }
    }
}
