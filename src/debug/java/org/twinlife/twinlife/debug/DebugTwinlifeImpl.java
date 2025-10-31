/*
 *  Copyright (c) 2012-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Tengfei Wang (Tengfei.Wang@twinlife-systems.com)
 *   Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *   Xiaobo Xie (Xiaobo.Xie@twinlife-systems.com)
 *   Chedi Baccari (Chedi.Baccari@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.debug;


import android.content.Context;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.AndroidTwinlifeImpl;
import org.twinlife.twinlife.DebugService;
import org.twinlife.twinlife.TwinlifeContextImpl;

/**
 * Debug Twinlife specific class.
 *
 * We inherit from the TwinlifeImpl class to have access to the protected member sServiceFactory
 * and install a factory that allows us to create the DebugService instance and install it during
 * the configuration and before the database is opened.
 *
 * The factory is installed by the initialize() static function which is called by the specific
 * TwinmeApplication that is activated with the "dev" flavor.
 */
public abstract class DebugTwinlifeImpl extends AndroidTwinlifeImpl {

    private static DebugServiceImpl sDebugService;

    public DebugTwinlifeImpl(@NonNull TwinlifeContextImpl twinlifeContext, Context context) {
        super(twinlifeContext, context);
    }

    /**
     * Install the specific service factory before TwinlifeImpl service is created.
     */
    public static void initialize() {
        sServiceFactory = (twinlife, connection) -> {

            DebugService.DebugServiceConfiguration configuration = new DebugService.DebugServiceConfiguration();
            sDebugService = new DebugServiceImpl(twinlife, connection);

            sDebugService.configure(configuration);
            return sDebugService;
        };
    }

    /**
     * Give access to the debug service directly (since it is not visible through the Twinlife interface).
     *
     * @return the debug service instance.
     */
    public static DebugService getDebugService() {

        return sDebugService;
    }
 }