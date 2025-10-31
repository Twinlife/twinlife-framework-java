/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.ArrayList;

/**
 * Manages a list of transport candidates that must be sent.
 *
 * The list can contain transport candidates that are already in the process of being transferred.
 * We don't want to send them another time but we want to be sure they are received because if we
 * get a timeout or connection error, we have a chance to retry with a new connection.  This class
 * is also shared with the Openfire server since it has the same constraints.
 *
 * Each TransportCandidate is associated with a requestId that indicates the pending request that
 * was made to send the candidate.  Such transport candidate is ignored if we have to send the list
 * again and it is removed from the list when the get the acknowledge (by calling remove(long requestId)).
 * If some timeout occurs, we must call 'cancel(long requestId)' and we are ready to send the
 * transport again.
 *
 * A Web-RTC candidate is replaced internally by using a fixed dictionary composed of well known
 * pre-defined strings, each string is replaced by a single character value.  Such packing allows
 * reduce by 40% the size (useful for 3G network) while keeping fast packing speed.
 */
public class TransportCandidateList {

    private final List<TransportCandidate> mCandidates;

    public TransportCandidateList() {

        mCandidates = new ArrayList<>();
    }

    /**
     * Check if we have some transport candidate to send.
     *
     * @return true if the list is flushed and there is no transport candidate to send.
     */
    public synchronized boolean isFlushed() {

        for (final TransportCandidate candidate : mCandidates) {
            if (candidate.requestId == 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Indicates that a new transport candidate is available.
     *
     * @param id the id.
     * @param label the label.
     * @param sdp the ICE candidate.
     */
    public synchronized void addCandidate(int id, @NonNull String label, @Nullable String sdp) {

        mCandidates.add(new TransportCandidate(id, label, sdp, false));
    }

    /**
     * Indicates that a transport candidate is no longer available.
     *
     * @param id the id.
     * @param label the label.
     * @param sdp the ICE candidate.
     */
    public synchronized void removeCandidate(int id, @NonNull String label, @Nullable String sdp) {

        mCandidates.add(new TransportCandidate(id, label, sdp, true));
    }

    /**
     * Clear the list of candidates.
     */
    public synchronized void clear() {

        mCandidates.clear();
    }

    /**
     * Expand the received compact SDP string by using the pre-defined dictionary.
     *
     * @param sdp the SDP content to expand.
     * @return the expanded content.
     */
    @NonNull
    public static String expand(@NonNull String sdp) {
        final StringBuilder sb = new StringBuilder(128);

        final char[] content = sdp.toCharArray();
        for (char c : content) {
            if (c >= mapToDictionary.length) {
                sb.append(c);
            } else {
                final int pos = mapToDictionary[c];
                if (pos < 0) {
                    sb.append(c);
                } else {
                    sb.append(dictionary[pos]);
                }
            }
        }
        return sb.toString();
    }

    /**
     * Build the transport info SDP for a new request to send all the transport candidates
     * not yet sent.
     *
     * @param requestId the new request id.
     * @return the SDP
     */
    @NonNull
    public synchronized Sdp buildSdp(long requestId) {

        final StringBuilder sb = new StringBuilder(512);
        for (final TransportCandidate candidate : mCandidates) {
            if (candidate.requestId == 0) {
                candidate.requestId = requestId;

                if (sb.length() > 0) {
                    sb.append('\r');
                }

                if (candidate.removed) {
                    sb.append('-');
                } else {
                    sb.append('+');
                }
                sb.append(candidate.label);
                sb.append('\t');
                sb.append(candidate.id);
                sb.append('\t');
                String sdp = candidate.sdp;
                int pos = 0;
                int len = sdp.length();
                if (sdp.startsWith(dictionary[0])) {
                    sb.append(dictionaryMap[0]);
                    pos = dictionary[0].length();
                }
                while (pos < len) {
                    final char c = sdp.charAt(pos);
                    if (c != ' ') {
                        sb.append(c);
                        pos++;
                    } else {
                        final int dict = findDictionary(sdp.substring(pos));
                        if (dict > 0) {
                            pos += dictionary[dict].length();
                            sb.append(dictionaryMap[dict]);
                        } else {
                            sb.append(sdp.charAt(pos));
                            pos++;
                        }
                    }
                }
            }
        }

        return new Sdp(sb.toString());
    }

    /**
     * Remove all transport candidate associated with the give request id.
     *
     * @param requestId the request id.
     */
    public synchronized void remove(long requestId) {

        int index = mCandidates.size();
        while (index >= 1) {
            index--;

            final TransportCandidate candidate = mCandidates.get(index);
            if (candidate.requestId == requestId) {
                mCandidates.remove(index);
            }
        }
    }

    /**
     * Cancel the request and prepare to send again all transport candidate
     * with the given request id.
     *
     * @param requestId the request id.
     */
    public synchronized void cancel(long requestId) {

        for (final TransportCandidate candidate : mCandidates) {

            if (candidate.requestId == requestId) {
                candidate.requestId = 0;
            }
        }
    }

    // The SDP candidate is composed of pre-defined strings.
    static final String[] dictionary = {
            "candidate:", // 1
            " udp ",      // 2
            " tcptype",   // 14
            " relay",     // 4
            " typ",       // 5
            " host",      // 6
            " srflx",     // 7
            " relay",     // 8
            " raddr",     // 10
            " ufrag",     // 11
            " rport",     // 12
            " tcp ",      // 3
            " passive",   // 15
            " network-cost", // 16
            " network-id", // 17
            " generation"  // 18
    };
    static final char[] dictionaryMap = {
            (char) 1, (char) 2, (char) 14,
            (char) 4, (char) 5, (char) 6,
            (char) 7, (char) 8, (char) 10,
            (char) 11, (char) 12, (char) 3,
            (char) 15, (char) 16, (char) 17,
            (char) 18
    };
    static final int[] mapToDictionary = {
            -1,
            0,
            1,
            11,
            3,
            4,
            5,
            6,
            7,
            -1,
            8,
            9,
            10,
            11,
            2,
            12,
            13,
            14,
            15
    };

    private static int findDictionary(String content) {
        for (int i = 1; i < dictionary.length; i++) {
            String dict = dictionary[i];
            if (content.startsWith(dict)) {
                return i;
            }
        }

        return -1;
    }

}
