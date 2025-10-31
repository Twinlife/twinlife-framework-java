/*
 *  Copyright (c) 2022-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.util.Utf8;

import java.util.HashSet;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * SDP sent to the peer or received from the peer.
 * <p>
 * The SDP can be compressed or encrypted.
 */
public class Sdp {
    static int COMPRESS_LIMIT = 256;

    private final boolean mCompressed;
    private final byte[] mSdp;
    private final int mSdpLen;
    private final int mKeyIndex;

    /**
     * Create the SDP content from the binary content as received on the wire from the peer.
     *
     * @param content the content data.
     * @param length the content length (the buffer can be larger than the real content).
     * @param compressed true if the SDP is compressed.
     * @param keyIndex the encryption key index > 0 if the SDP is encrypted (not yes supported).
     */
    public Sdp(@NonNull byte[] content, int length, boolean compressed, int keyIndex) {
        mCompressed = compressed;
        mSdp = content;
        mSdpLen = length;
        mKeyIndex = keyIndex;
    }

    /**
     * Create the SDP with the given content and compress it if necessary.
     *
     * @param content the SDP content to send on the wire to the peer.
     */
    public Sdp(@NonNull String content) {

        mKeyIndex = 0;
        byte[] data = Utf8.getBytes(content);
        if (data.length < COMPRESS_LIMIT) {
            mSdp = data;
            mSdpLen = data.length;
            mCompressed = false;
        } else {
            mSdp = new byte[data.length];

            // Don't spend time on compression we better have bigger compressed content
            // but faster compression because sending data can be fast enough on most networks.
            final Deflater compresser = new Deflater(Deflater.BEST_SPEED);
            compresser.setInput(data);
            compresser.finish();

            final int len = compresser.deflate(mSdp);

            // Append the de-compressed size at the end so that we help the de-compression
            // by telling it the size of buffer to allocate.
            mSdp[len] = (byte) (data.length >> 8);
            mSdp[len + 1] = (byte) (data.length & 0x0ff);
            mSdpLen = len + 2;
            mCompressed = true;
            compresser.end();
        }
    }

    /**
     * Returns true if the SDP is compressed.
     *
     * @return true if the SDP is compressed.
     */
    public boolean isCompressed() {

        return mCompressed;
    }

    /**
     * Returns true if the SDP is encrypted.
     *
     * @return true if the SDP is encrypted.
     */
    public boolean isEncrypted() {

        return mKeyIndex > 0;
    }

    /**
     * Get the key index that was used for encryption.
     *
     * @return the key index or 0 if the SDP is not encrypted.
     */
    public int getKeyIndex() {

        return mKeyIndex;
    }

    /**
     * Get the SDP is de-compressed and clear form text.
     *
     * @return the SDP or null if there is a problem in decompressing it.
     */
    @Nullable
    public String getSdp() {

        if (mKeyIndex > 0) {
            return null;

        } else if (mCompressed) {
            try {
                // Get the size of final decompressed SDP by looking at two bytes at end of compressed buffer.
                final int len = ((int) (mSdp[mSdpLen - 1]) & 0x0FF) + (((int) (mSdp[mSdpLen - 2]) & 0x0FF) << 8);

                final Inflater decompresser = new Inflater();
                decompresser.setInput(mSdp, 0, mSdpLen - 2);

                final byte[] result = new byte[len];
                final int resultLength = decompresser.inflate(result);
                decompresser.end();
                if (resultLength == len) {
                    return Utf8.create(result, resultLength);
                }

            } catch (Exception exception) {
                return null;
            }

            return null;
        } else {
            return Utf8.create(mSdp, mSdpLen);
        }
    }

    /**
     * Get the transport candidates defined in the sdp.
     *
     * @return the array of transport candidates or null.
     */
    @Nullable
    public TransportCandidate[] getCandidates() {

        String sdp = getSdp();
        if (sdp == null) {

            return null;
        }

        String[] candidates = sdp.split("\r");
        TransportCandidate[] result = new TransportCandidate[candidates.length];
        for (int i = 0; i < candidates.length; i++) {
            String s = candidates[i];
            boolean removed = s.charAt(0) == '-';
            int pos = s.indexOf('\t');
            String label = "";
            int id = 0;
            String candidate = "";
            if (pos > 0) {
                label = s.substring(1, pos);
                int pos2 = s.indexOf('\t', pos + 1);
                if (pos2 > 0) {
                    try {
                        id = Integer.parseInt(s.substring(pos + 1, pos2));
                    } catch (NumberFormatException ignored) {
                    }
                    candidate = TransportCandidateList.expand(s.substring(pos2 + 1));
                }
            }
            result[i] = new TransportCandidate(id, label, candidate, removed);
        }
        return result;
    }

    /**
     * Get the SDP raw data as transmitted on the wire.
     *
     * @return the SDP content.
     */
    public byte[] getData() {

        return mSdp;
    }

    /**
     * Get the SDP raw data length.
     *
     * @return the SDP raw data length.
     */
    public int getLength() {

        return mSdpLen;
    }

    /**
     * List of Audio and Video codecs which are accepted ("rtx" is added because it is required).
     */
    private static final String[] CODECS = {"opus", "rtx", "VP8", "VP9", "H264", "AV1X"};

    /**
     * Filter the SDP to only keep codecs in the `CODECS` list.
     *
     * @param sdp the SDP to filter.
     * @return either the SDP unmodified or a new SDP filtered without unwanted codecs.
     */
    @NonNull
    public static String filterCodecs(@NonNull String sdp) {

        String[] lines = sdp.split("\n");
        Set<String> payloadTypes = new HashSet<>();
        boolean filtered = false;

        // Example produced by Firefox:
        // a=rtpmap:109 opus/48000/2^M
        // a=rtpmap:9 G722/8000/1^M
        // a=rtpmap:0 PCMU/8000^M
        // a=rtpmap:8 PCMA/8000^M
        // a=rtpmap:101 telephone-event/8000/1^M
        // a=rtpmap:120 VP8/90000^M
        // a=rtpmap:124 rtx/90000^M
        // a=rtpmap:121 VP9/90000^M
        // a=rtpmap:125 rtx/90000^M
        // a=rtpmap:126 H264/90000^M
        // a=rtpmap:127 rtx/90000^M
        // a=rtpmap:97 H264/90000^M
        // a=rtpmap:98 rtx/90000^M
        // a=rtpmap:123 ulpfec/90000^M
        // a=rtpmap:122 red/90000^M
        // a=rtpmap:119 rtx/90000^M
        // => we must keep 97, 98, 109, 119, 120, 121, 124, 125, 126, 127
        // => we want to drop 0, 8, 9, 101, 119, 122, 123
        for (String line : lines) {
            if (line.startsWith("a=rtpmap:")) {
                boolean found = false;
                for (String codec : CODECS) {
                    if (line.contains(codec)) {
                        final String payloadType = line.substring("a=rtpmap:".length(), line.indexOf(' '));
                        payloadTypes.add(payloadType);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    filtered = true;
                }
            }
        }
        if (!filtered || payloadTypes.isEmpty()) {
            return sdp;
        }

        // Example produced by Firefox:
        // a=fmtp:109 maxplaybackrate=48000;stereo=1;useinbandfec=1
        // a=fmtp:101 0-15
        // a=fmtp:126 profile-level-id=42e01f;level-asymmetry-allowed=1;packetization-mode=1
        // a=fmtp:97 profile-level-id=42e01f;level-asymmetry-allowed=1
        // a=fmtp:120 max-fs=12288;max-fr=60
        // a=fmtp:124 apt=120
        // a=fmtp:121 max-fs=12288;max-fr=60
        // a=fmtp:125 apt=121
        // a=fmtp:127 apt=126
        // a=fmtp:98 apt=97
        // a=fmtp:119 apt=122
        for (String line : lines) {
            if (line.startsWith("a=fmtp:")) {
                int aptPos = line.indexOf("apt=");
                if (aptPos > 0) {
                    String payloadType = line.substring("a=fmtp:".length(), line.indexOf(' '));
                    String assignedPayloadType = line.substring(aptPos + "apt=".length());
                    char lastCharacter = assignedPayloadType.charAt(assignedPayloadType.length() - 1);
                    if (lastCharacter == '\r') {
                        assignedPayloadType = assignedPayloadType.substring(0, assignedPayloadType.length() - 1);
                    }
                    if (payloadTypes.contains(assignedPayloadType)) {
                        payloadTypes.add(payloadType);
                    } else {
                        // Example with 'a=fmtp:119 apt=122', 122 is not in the accepted list, we must remote 119.
                        payloadTypes.remove(payloadType);
                    }
                }
            }
        }

        // Example from Firefox:
        // a=rtcp-fb:120 nack
        // a=rtcp-fb:120 nack pli
        // a=rtcp-fb:120 ccm fir
        // a=rtcp-fb:120 goog-remb
        // a=rtcp-fb:120 transport-cc
        // a=rtcp-fb:121 nack
        // a=rtcp-fb:121 nack pli
        // a=rtcp-fb:121 ccm fir
        // a=rtcp-fb:121 goog-remb
        // a=rtcp-fb:121 transport-cc
        // a=rtcp-fb:126 nack
        // a=rtcp-fb:126 nack pli
        // a=rtcp-fb:126 ccm fir
        // a=rtcp-fb:126 goog-remb
        // a=rtcp-fb:126 transport-cc
        // a=rtcp-fb:97 nack
        // a=rtcp-fb:97 nack pli
        // a=rtcp-fb:97 ccm fir
        // a=rtcp-fb:97 goog-remb
        // a=rtcp-fb:97 transport-cc
        // a=rtcp-fb:123 nack
        // a=rtcp-fb:123 nack pli
        // a=rtcp-fb:123 ccm fir
        // a=rtcp-fb:123 goog-remb
        // a=rtcp-fb:123 transport-cc
        // a=rtcp-fb:122 nack
        // a=rtcp-fb:122 nack pli
        // a=rtcp-fb:122 ccm fir
        // a=rtcp-fb:122 goog-remb
        // a=rtcp-fb:122 transport-cc
        // => we want to drop 122, 123 and keep others
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String payloadType;
            if (line.startsWith("a=rtpmap:")) {
                payloadType = line.substring("a=rtpmap:".length(), line.indexOf(' '));
            } else if (line.startsWith("a=rtcp-fb:")) {
                payloadType = line.substring("a=rtcp-fb:".length(), line.indexOf(' '));
            } else if (line.startsWith("a=fmtp:")) {
                payloadType = line.substring("a=fmtp:".length(), line.indexOf(' '));
            } else {
                continue;
            }

            if (!payloadTypes.contains(payloadType)) {
                lines[i] = null;
            }
        }

        // Example from Firefox:
        // m=audio 9 UDP/TLS/RTP/SAVPF 109 9 0 8 101
        // m=video 0 UDP/TLS/RTP/SAVPF 120 124 121 125 126 127 97 98 123 122 119
        // => we want to drop 0, 8, 9, 101, 122, 123 without changing priority order and generate:
        // m=audio 9 UDP/TLS/RTP/SAVPF 109
        // m=video 0 UDP/TLS/RTP/SAVPF 120 124 121 125 126 127 97 98
        StringBuilder sdpBuilder = new StringBuilder();
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            if (line.startsWith("m=audio ") || line.startsWith("m=video ")) {
                String[] elements = line.split(" ");
                StringBuilder lineBuilder = new StringBuilder();
                lineBuilder.append(elements[0]).append(' ');
                lineBuilder.append(elements[1]).append(' ');
                lineBuilder.append(elements[2]);
                for (int i = 3; i < elements.length; i++) {
                    String element = elements[i];
                    char lastCharacter = 0;
                    if (i == elements.length - 1) {
                        lastCharacter = element.charAt(element.length() - 1);
                        if (lastCharacter == '\r') {
                            element = element.substring(0, element.length() - 1);
                        }
                    }
                    if (payloadTypes.contains(element)) {
                        lineBuilder.append(' ').append(element);
                    }
                    if (lastCharacter == '\r') {
                        lineBuilder.append('\r');
                    }
                }
                line = lineBuilder.toString();
            }
            sdpBuilder.append(line).append("\n");
        }

        return sdpBuilder.toString();
    }

    @NonNull
    @Override
    public String toString() {

        return "SDP[len=" + mSdpLen + ", " + (mCompressed ? " compressed" : "") + (mKeyIndex > 0 ? " key=" + mKeyIndex : "") + "]";
    }
}
