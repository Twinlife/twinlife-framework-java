/*
 *  Copyright (c) 2018 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.debug;


import androidx.collection.CircularArray;

import org.twinlife.twinlife.util.EventMonitor;

import java.util.ArrayList;
import java.util.List;

/**
 * Produce a dump of events collected by the event monitor.
 */
public class EventMonitorDump implements DebugServiceImpl.DumpListGenerator {

    /**
     * Produce the dump.
     *
     * @return A list of row/colums representing the dump.
     */
    @Override
    public List<String[]> getDump() {

        final CircularArray<EventMonitor.Event> events = EventMonitor.getEvents();

        final ArrayList<String[]> result = new ArrayList<>();
        for (int i = 0; i < events.size(); i++) {
            try {
                result.add(new String[]{ events.get(i).toString() });
            } catch (Exception ex) {
                result.add(new String[]{ ex.toString() });
            }
        }
        return result;
    }
}
