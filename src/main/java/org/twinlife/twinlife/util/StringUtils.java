/**
 * $RCSfile$
 * $Revision: 11823 $
 * $Date: 2010-08-15 08:20:48 -0500 (Sun, 15 Aug 2010) $
 * <p>
 * Copyright 2003-2007 Jive Software.
 * <p>
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.twinlife.twinlife.util;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * A collection of utility methods for String objects.
 */
public class StringUtils {

    private static final char[] QUOTE_ENCODE = "&quot;".toCharArray();
    private static final char[] APOS_ENCODE = "&apos;".toCharArray();
    private static final char[] AMP_ENCODE = "&amp;".toCharArray();
    private static final char[] LT_ENCODE = "&lt;".toCharArray();
    private static final char[] GT_ENCODE = "&gt;".toCharArray();

    /**
     * Escapes all necessary characters in the String so that it can be used in
     * an XML doc.
     *
     * @param string the string to escape.
     * @return the string with appropriate characters escaped.
     */
    public static String escapeForXML(String string) {
        if (string == null) {
            return null;
        }
        char ch;
        int i = 0;
        int last = 0;
        char[] input = string.toCharArray();
        int len = input.length;
        StringBuilder out = new StringBuilder((int) (len * 1.3));
        for (; i < len; i++) {
            ch = input[i];
            if (ch > '>') {
            } else if (ch == '<') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(LT_ENCODE);
            } else if (ch == '>') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(GT_ENCODE);
            } else if (ch == '&') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                // Do nothing if the string is of the form &#235; (unicode
                // value)
                if (!(len > i + 5 && input[i + 1] == '#' && Character.isDigit(input[i + 2]) && Character.isDigit(input[i + 3]) && Character.isDigit(input[i + 4]) && input[i + 5] == ';')) {
                    last = i + 1;
                    out.append(AMP_ENCODE);
                }
            } else if (ch == '"') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(QUOTE_ENCODE);
            } else if (ch == '\'') {
                if (i > last) {
                    out.append(input, last, i - last);
                }
                last = i + 1;
                out.append(APOS_ENCODE);
            }
        }
        if (last == 0) {
            return string;
        }
        if (i > last) {
            out.append(input, last, i - last);
        }
        return out.toString();
    }

    /**
     * Used by the hash method.
     */
    private static MessageDigest digest = null;

    /**
     * Hashes a String using the SHA-1 algorithm and returns the result as a
     * String of hexadecimal numbers. This method is synchronized to avoid
     * excessive MessageDigest object creation. If calling this method becomes a
     * bottleneck in your code, you may wish to maintain a pool of MessageDigest
     * objects instead of using this method.
     * <p>
     * A hash is a one-way function -- that is, given an input, an output is
     * easily computed. However, given the output, the input is almost
     * impossible to compute. This is useful for passwords since we can store
     * the hash and a hacker will then have a very hard time determining the
     * original password.
     *
     * @param data the String to compute the hash of.
     * @return a hashed version of the passed-in String
     */
    public synchronized static String hash(String data) {
        if (digest == null) {
            try {
                digest = MessageDigest.getInstance("SHA-1");
            } catch (NoSuchAlgorithmException nsae) {
                System.err.println("Failed to load the SHA-1 MessageDigest. " + "Jive will be unable to function normally.");
            }
        }
        // Now, compute hash.
        try {
            digest.update(data.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            System.err.println(e);
        }
        return encodeHex(digest.digest());
    }

    /**
     * Encodes an array of bytes as String representation of hexadecimal.
     *
     * @param bytes an array of bytes to convert to a hex string.
     * @return generated hex string.
     */
    public static String encodeHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);

        for (byte aByte : bytes) {
            if (((int) aByte & 0xff) < 0x10) {
                hex.append("0");
            }
            hex.append(Integer.toString((int) aByte & 0xff, 16));
        }

        return hex.toString();
    }

    /**
     * Encodes a byte array into a bse64 String.
     *
     * @param data       The byte arry to encode.
     * @param lineBreaks True if the encoding should contain line breaks and false if
     *                   it should not.
     * @return A base64 encoded String.
     */
    public static String encodeBase64(byte[] data, boolean lineBreaks) {
        return encodeBase64(data, 0, data.length, lineBreaks);
    }

    /**
     * Encodes a byte array into a bse64 String.
     *
     * @param data       The byte arry to encode.
     * @param offset     the offset of the bytearray to begin encoding at.
     * @param len        the length of bytes to encode.
     * @param lineBreaks True if the encoding should contain line breaks and false if
     *                   it should not.
     * @return A base64 encoded String.
     */
    public static String encodeBase64(byte[] data, int offset, int len, boolean lineBreaks) {
        return Base64.encodeBytes(data, offset, len, (lineBreaks ? Base64.NO_OPTIONS : Base64.DONT_BREAK_LINES));
    }

    /**
     * Decodes a base64 String.
     *
     * @param data a base64 encoded String to decode.
     * @return the decoded String.
     */
    public static byte[] decodeBase64(String data) {
        return Base64.decode(data);
    }

    private StringUtils() {
        // Not instantiable.
    }
}
