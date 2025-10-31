/*
 *  Copyright (c) 2018 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.debug;

import org.twinlife.twinlife.util.EventMonitor;

import java.util.List;

/**
 * Produce a dump of performance events collected by the event monitor.
 */
public class PerfMonitorDump implements DebugServiceImpl.DumpListGenerator {

    /**
     * Produce the dump.
     *
     * @return A list of row/colums representing the dump.
     */
    @Override
    public List<String[]> getDump() {

        return EventMonitor.getMeasures();
    }
}
