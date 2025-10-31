/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SdpTest {

    private String getSDP(String name) {
        try (InputStream is = getClass().getResourceAsStream(name)) {
            if (is == null) {
                return null;
            }

            return new String(is.readAllBytes());
        } catch (IOException exception) {
            return null;
        }
    }

    @Test
    public void testNoCompress() {
        Sdp sdp = new Sdp("test");

        assertFalse(sdp.isCompressed());
        assertEquals("test", sdp.getSdp());

        byte[] data = sdp.getData();
        assertNotNull(data);
        assertEquals(4, sdp.getLength());

        sdp = new Sdp(data, 4, false, 0);
        assertEquals("test", sdp.getSdp());
    }

    @Test
    public void testCompress() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("a=ice-options:trickle renomination");
            sb.append(UUID.randomUUID().toString());
        }

        Sdp sdp = new Sdp(sb.toString());
        assertTrue(sdp.isCompressed());

        byte[] data = sdp.getData();
        assertNotNull(data);
        assertTrue(sdp.getLength() < sb.length());
        assertTrue(data.length >= sdp.getLength());
        assertEquals(sb.toString(), sdp.getSdp());

        sdp = new Sdp(data, sdp.getLength(), true, 0);
        assertEquals(sb.toString(), sdp.getSdp());
    }

    @Test
    public void testTransportCandidate() {
        for (int i = 0; i < TransportCandidateList.dictionary.length; i++) {
            int map = TransportCandidateList.dictionaryMap[i];

            int pos = TransportCandidateList.mapToDictionary[map];
            assertEquals(i, pos);
        }

        String candidate = "candidate:1052210311 1 tcp 1518280447 192.168.0.72 50417 typ host tcptype passive generation 0 ufrag KjZR network-id 1 network-cost 10";
        TransportCandidateList candidates = new TransportCandidateList();
        candidates.addCandidate(1, "data", candidate);

        Sdp sdp = candidates.buildSdp(1);
        assertNotNull(sdp);
        String packedSdp = sdp.getSdp();
        assertEquals("+data\t1\t\u00011052210311 1\u00031518280447 192.168.0.72 50417\u0005\u0006\u000E\u000F\u0012 0\u000B KjZR\u0011 1\u0010 10", packedSdp);

        TransportCandidate[] result = sdp.getCandidates();
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(candidate, result[0].sdp);

        candidate = "candidate:1882490999 1 udp 2122260223 192.168.0.72 49743 typ host generation 0 ufrag YlZr network-id 1 network-cost 10";
        candidates = new TransportCandidateList();
        candidates.addCandidate(1, "data", candidate);

        sdp = candidates.buildSdp(1);
        packedSdp = sdp.getSdp();
        assertEquals("+data\t1\t\u00011882490999 1\u00022122260223 192.168.0.72 49743\u0005\u0006\u0012 0\u000B YlZr\u0011 1\u0010 10", packedSdp);

        result = sdp.getCandidates();
        assertNotNull(result);
        assertEquals(1, result.length);
        assertEquals(candidate, result[0].sdp);

    }

    static final String C1 = "candidate:560224848 1 udp 2113937151 192.168.122.20 51950 typ host generation 0 ufrag xKtp network-cost 999";
    static final String C2 = "candidate:560224848 1 udp 2113937151 192.168.122.20 38306 typ host generation 0 ufrag LIMa network-cost 999";
    static final String C3 = "candidate:842163049 1 udp 1685987071 37.171.3.77 2692 typ srflx raddr 192.168.8.154 rport 48531 generation 0 ufrag atY7 network-id 3 network-cost 10";
    static final String C4 = "candidate:577336358 1 udp 25043199 195.201.85.202 61645 typ relay raddr 37.171.3.77 rport 2761 generation 0 ufrag atY7 network-id 3 network-cost 10";
    static final String C5 = "candidate:577336358 1 udp 25108735 195.201.85.202 58159 typ relay raddr 2a01:cb1e:53:50b9:3401:a631:6f24:7107 rport 50299 generation 0 ufrag sXqx network-id 6 network-cost 900";
    static final String C6 = "candidate:3717173733 1 udp 1685987071 92.184.112.141 50150 typ srflx raddr 192.0.0.1 rport 50150 generation 0 ufrag xlqh network-id 5 network-cost 900";
    static final String C7 = "candidate:1827309782 1 udp 41886207 195.201.85.202 61739 typ relay raddr 2a01:cb1e:53:50b9:3401:a631:6f24:7107 rport 58554 generation 0 ufrag XQtu network-id 6 network-cost 900";
    static final String[] C_Ref = {
            C1, C2, C3, C4, C5, C6, C7
    };

    static final String T1 = "candidate:4129134249 1 udp 8191 188.40.37.178 53273 typ relay raddr 95.216.116.152 rport 38325 generation 0 ufrag dcvq network-cost 999";
    static final String T2 = "candidate:4129134249 1 udp 7935 188.40.37.178 63102 typ relay raddr 78.46.99.72 rport 52669 generation 0 ufrag cCbC network-cost 999";
    static final String T3 = "candidate:4129134249 1 udp 8191 188.40.37.178 64313 typ relay raddr 78.46.99.72 rport 39429 generation 0 ufrag cCbC network-cost 999";
    static final String T4 = "candidate:1271151965 1 udp 16785663 88.198.53.79 64211 typ relay raddr 78.46.99.72 rport 36295 generation 0 ufrag cCbC network-cost 999";
    static final String T5 = "candidate:88588717 1 udp 33563135 88.198.53.79 63041 typ relay raddr 78.46.99.72 rport 42107 generation 0 ufrag cCbC network-cost 999";
    static final String[] T_Ref = {
            T1, T2, T3, T4, T5
    };

    @Test
    public void testProdCandidates() {
        for (String c : T_Ref) {
            TransportCandidateList list = new TransportCandidateList();
            list.addCandidate(1, "1", c);
            final Sdp sdp = list.buildSdp(1);
            assertNotNull(sdp);
            final TransportCandidate[] candidates = sdp.getCandidates();
            assertNotNull(candidates);
            assertEquals(1, candidates.length);

            TransportCandidate tc = candidates[0];
            assertNotNull(tc);
            assertEquals(c, tc.sdp);
        }
    }

    @Test
    public void testCandidates() {
        for (int i = 0; i < 10; i++) {
            TransportCandidateList warmup = new TransportCandidateList();
            warmup.addCandidate(1, "data", C_Ref[0]);
            warmup.addCandidate(1, "data", C_Ref[1]);
            warmup.addCandidate(1, "data", C_Ref[2]);
            warmup.buildSdp(12);
        }

        for (int i = 0; i < C_Ref.length; i++) {
            TransportCandidateList candidates = new TransportCandidateList();

            for (int k = 0; k <= i; k++) {
                if (k < 5) {
                    candidates.addCandidate(k, "data" + k, C_Ref[k]);
                } else {
                    candidates.removeCandidate(k, "data" + k, C_Ref[k]);
                }
            }

            long t = System.nanoTime();
            Sdp sdp = candidates.buildSdp(123);
            assertNotNull(sdp);
            t = System.nanoTime() - t;

            TransportCandidate[] result = sdp.getCandidates();
            assertNotNull(result);
            assertEquals(i + 1, result.length);
            int totLength = 0;
            for (int k = 0; k < result.length; k++) {
                TransportCandidate c = result[k];
                assertNotNull(c.label);
                assertEquals("data" + k, c.label);
                assertEquals(k, c.id);
                assertEquals(C_Ref[k], c.sdp);
                assertEquals(k >= 5, c.removed);

                totLength += c.sdp.length() + c.label.length() + 1;
            }

            System.out.println("Encoded " + (i + 1) + " in " + t + " ns size reduced from "
                    + totLength + " to " + sdp.getLength());
        }
    }

    private void saveTestSDP(@NonNull String name, @NonNull String sdp) throws Exception {
        File file = new File(name);
        try (FileOutputStream outputStream = new FileOutputStream(file)) {
            outputStream.write(sdp.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void checkFilterSDP(String name, String expect) throws Exception {
        String sdp = getSDP(name);
        assertNotNull(sdp);

        String sdpExpect = getSDP(expect);
        assertNotNull(sdpExpect);

        long t = System.nanoTime();
        String res = null;
        for (int i = 0; i < 1000; i++) {
            res = Sdp.filterCodecs(sdp);
        }
        t = System.nanoTime() - t;
        System.out.println("Filter " + name + " in " + (t / 1000) + " ns");

        // saveTestSDP(expect, res);
        assertEquals(sdpExpect, res, "Invalid SDP filter for " + name);
    }

    @Test
    public void testFilterSDP() throws Exception {
        checkFilterSDP("sdp-firefox.txt", "sdp-firefox-result.txt");
        checkFilterSDP("sdp-safari-video.txt", "sdp-safari-video-result.txt");
        checkFilterSDP("sdp-safari-audio.txt", "sdp-safari-audio-result.txt");
        checkFilterSDP("sdp-android-filtered.txt", "sdp-android-filtered-result.txt");
        checkFilterSDP("sdp-android-audio-filtered.txt", "sdp-android-audio-filtered-result.txt");
    }
}
