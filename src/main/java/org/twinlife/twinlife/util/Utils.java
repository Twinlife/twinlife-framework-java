/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributor:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Olivier Dupont (Oliver.Dupont@twin.life)
 */

package org.twinlife.twinlife.util;

import android.content.ContentResolver;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.net.Uri;
import android.os.Looper;
import android.os.StatFs;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.UUID;

import org.twinlife.twinlife.BaseService.ErrorCode;

public class Utils {
    private static final String LOG_TAG = "Utils";
    private static final boolean DEBUG = false;

    private static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    /**
     * Test the main thread.
     *
     * @return Returns true if we are running from the main UI thread.
     */
    public static boolean isMainThread() {

        return Thread.currentThread() == Looper.getMainLooper().getThread();
    }

    /**
     * Encodes a byte array into a base64 String.
     *
     * @param data a byte array to encode.
     * @return a base64 encode String.
     */
    public static String encodeBase64(byte[] data) {

        return StringUtils.encodeBase64(data, false);
    }

    public static String encodeBase64URL(byte[] data) {

        String base64 = Base64.encodeBytes(data, 0, data.length, Base64.DONT_BREAK_LINES | Base64.URL_SAFE);
        int sep = base64.indexOf('=');
        return sep > 0 ? base64.substring(0, sep) : base64;
    }

    @Nullable
    public static byte[] decodeBase64(@NonNull String data) {

        return Base64.decode(data, Base64.NO_OPTIONS);
    }

    @Nullable
    public static byte[] decodeBase64URL(@NonNull String data) {

        return Base64.decode(data, Base64.URL_SAFE);
    }

    @SuppressWarnings({"unused", "SameParameterValue"})
    public static String bytesToHex(@Nullable byte[] bytes, int length) {

        if (bytes == null) {

            return null;
        }

        length = Math.min(length, bytes.length);
        char[] hexChars = new char[length * 2];
        for (int j = 0; j < length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }

    @SuppressWarnings("unused")
    @Nullable
    public static String bytesToHex(@Nullable byte[] bytes) {

        if (bytes == null) {

            return null;
        }

        char[] hexChars = new char[bytes.length * 2 + ((bytes.length - 1) / 2)];
        for (int i = 0, j = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            if (i > 0 && (i % 2) == 0) {
                hexChars[j++] = ' ';
            }
            hexChars[j++] = hexArray[v >>> 4];
            hexChars[j++] = hexArray[v & 0x0F];
        }

        return new String(hexChars);
    }

    public static boolean deleteDirectory(@NonNull File directory) {

        boolean success = true;
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        success = success && deleteDirectory(file);
                    } else if (!file.delete()) {
                        success = false;
                    }
                }
            }
        }

        return success && directory.delete();
    }

    private static final int CHUNK_SIZE = 256 * 1024;

    public static ErrorCode copyFile(@NonNull String path, @NonNull String toPath) {

        return copyFile(new File(path), new File(toPath));
    }

    public static ErrorCode copyFile(@NonNull File path, @NonNull File toPath) {

        File dirPath = toPath.getParentFile();
        if (dirPath != null && !dirPath.exists() && !dirPath.mkdirs() && Logger.WARN) {
            Logger.warn(LOG_TAG, "mkdir", dirPath);
        }

        try (InputStream input = new FileInputStream(path);
             FileOutputStream output = new FileOutputStream(toPath)) {
            return copyStream(input, output) ? ErrorCode.SUCCESS : ErrorCode.NO_STORAGE_SPACE;

        } catch (FileNotFoundException exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "saveFile: ", path, " not found");
            }
            return ErrorCode.FILE_NOT_FOUND;

        } catch (SecurityException exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "saveFile: ", path, " transferTo failed: ", exception);
            }
            return ErrorCode.NO_PERMISSION;

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "saveFile: ", path, " transferTo failed: ", exception);
            }
            return ErrorCode.NO_STORAGE_SPACE;
        }
    }

    /**
     * Copy the content by using the resolver in the target file.
     *
     * @param resolver the resolver to use.
     * @param path the source URI path to copy.
     * @param toPath the target destination file.
     * @return SUCCESS if the copy succeeded or an error code.
     */
    @NonNull
    public static ErrorCode copyUriToFile(@NonNull ContentResolver resolver, @NonNull Uri path, @NonNull File toPath) {

        File dirPath = toPath.getParentFile();
        if (dirPath != null && !dirPath.exists() && !dirPath.mkdirs() && Logger.WARN) {
            Logger.warn(LOG_TAG, "mkdir", dirPath);
        }

        // Save the file to send in the conversation directory.
        try (InputStream input = resolver.openInputStream(path)) {
            return copyStream(input, new FileOutputStream(toPath)) ? ErrorCode.SUCCESS : ErrorCode.NO_STORAGE_SPACE;

        } catch (FileNotFoundException exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "saveFile: ", path, " not found");
            }
            return ErrorCode.FILE_NOT_FOUND;

        } catch (SecurityException exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "saveFile: ", path, " transferTo failed: ", exception);
            }
            return ErrorCode.NO_PERMISSION;

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "saveFile: ", path, " transferTo failed: ", exception);
            }
            return ErrorCode.NO_STORAGE_SPACE;
        }
    }

    public static boolean copyStream(@NonNull InputStream input, @NonNull FileOutputStream output) {

        // Save the file to send in the conversation directory.
        FileChannel outputChannel = null;
        ReadableByteChannel inputChannel = null;
        boolean done = false;
        try {
            inputChannel = Channels.newChannel(input);
            outputChannel = output.getChannel();
            long position = 0;

            // Loop until transferFrom() has finished.  If the Uri refers to a network Uri (Google Drive),
            // the transferFrom() will get partial data chunks and since we can't tell the size of the input
            // stream, we stop only when the last transfer operation did nothing.  We must not use a too big
            // buffer to avoid OutOfMemoryError exception: use the 256K chunk size since this is what we will use
            // for the data transfer.
            while (true) {
                long result = outputChannel.transferFrom(inputChannel, position, CHUNK_SIZE);
                if (result == 0) {
                    done = true;
                    break;
                }
                position = position + result;
            }
        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "copyStream: transferTo() failed: ", exception);
            }
        } finally {
            try {
                input.close();
                if (inputChannel != null) {
                    inputChannel.close();
                }
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "copyStream: close() failed: ", exception);
                }
            }
            try {
                output.close();
                if (outputChannel != null) {
                    outputChannel.close();
                }
            } catch (Exception exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "close", exception);
                }
            }
        }
        return done;
    }

    /**
     * Delete the file and handle the error associated with such deletion.
     *
     * @param tag the logger tag.
     * @param file the file to delete.
     */
    public static void deleteFile(@NonNull String tag, @NonNull File file) {

        if (file.exists() && !file.delete() && Logger.WARN) {
            Logger.warn(tag, "Cannot delete file: ", file);
        }
    }

    /**
     * Cleanup old temporary files that should have been removed.
     *
     * @param dir the directory to cleanup.
     */
    public static void cleanupTemporaryDirectory(@NonNull File dir) {
        if (DEBUG) {
            Log.d(LOG_TAG, "cleanupTemporaryDirectory dir=" + dir);
        }

        FilenameFilter filter = (dir1, name) -> name.endsWith(".jpg") ||
                name.endsWith(".tmp") || name.endsWith(".m4a") || name.endsWith(".mp4");

        final File[] files = dir.listFiles(filter);
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    Utils.deleteFile(LOG_TAG, file);
                }
            }
        }
    }

    @Nullable
    public static UUID UUIDFromString(@Nullable String value) {

        if (value != null) {
            //noinspection EmptyCatchBlock
            try {
                return UUID.fromString(value);
            } catch (Exception exception) {
            }
        }

        return null;
    }

    /**
     * Convert a string to a UUID.  The UUID is either using the UUID format
     * or it is encoded in Base64 and provides a shorter string length.
     *
     * @param value the value to convert.
     * @return the UUID or null.
     */
    @Nullable
    public static UUID toUUID(@Nullable String value) {
        if (value == null) {
            return null;
        }
        try {
            // UUID format
            if (value.length() == 36) {
                return UUID.fromString(value);
            }

            // UUID encoded in base64
            final byte[] data = Base64.decode(value + "==", Base64.URL_SAFE);
            if (data.length != 16) {
                return null;
            }
            long msb = 0;
            long lsb = 0;
            for (int i=0; i<8; i++) {
                msb = (msb << 8) | (data[i] & 0xff);
            }
            for (int i=8; i<16; i++) {
                lsb = (lsb << 8) | (data[i] & 0xff);
            }
            return new UUID(msb, lsb);

        } catch (Exception ignore) {

            return null;
        }
    }

    /**
     * Convert the UUID to a base64 string.
     *
     * @param value the UUID to convert.
     * @return the URL base64 string.
     */
    @NonNull
    public static String toString(@NonNull UUID value) {
        final byte[] data = new byte[16];

        long val = value.getMostSignificantBits();
        data[7] = (byte) ((val) & 0x0FF);
        data[6] = (byte) ((val >> 8) & 0x0FF);
        data[5] = (byte) ((val >> 16) & 0x0FF);
        data[4] = (byte) ((val >> 24) & 0x0FF);
        data[3] = (byte) ((val >> 32) & 0x0FF);
        data[2] = (byte) ((val >> 40) & 0x0FF);
        data[1] = (byte) ((val >> 48) & 0x0FF);
        data[0] = (byte) ((val >> 56) & 0x0FF);

        val = value.getLeastSignificantBits();

        data[15] = (byte) ((val) & 0x0FF);
        data[14] = (byte) ((val >> 8) & 0x0FF);
        data[13] = (byte) ((val >> 16) & 0x0FF);
        data[12] = (byte) ((val >> 24) & 0x0FF);
        data[11] = (byte) ((val >> 32) & 0x0FF);
        data[10] = (byte) ((val >> 40) & 0x0FF);
        data[9] = (byte) ((val >> 48) & 0x0FF);
        data[8] = (byte) ((val >> 56) & 0x0FF);

        // Encoder and drop the two last '=' which are not necessary.
        return Base64.encodeBytes(data, 0, data.length, Base64.DONT_BREAK_LINES | Base64.URL_SAFE).substring(0, 22);
    }

    /**
     * Print the UUID for a log message.
     *
     * @param uuid the UUID to report.
     * @return the printable representation (possibly truncated) of the UUID.
     */
    public static String toLog(UUID uuid) {

        if (uuid == null) {
            return "null";
        } else {
            return uuid.toString().substring(0, 6);
        }
    }

    /**
     * Get the disk space available for the database file system.
     *
     * @param context the application context.
     * @return the disk space available in bytes.
     */
    public static long getDatabaseDiskSpace(@NonNull Context context) {

        File dir = context.getDatabasePath("test.db").getParentFile();
        if (dir == null) {
            dir = context.getFilesDir();
        }
        return getDiskSpace(dir);
    }

    /**
     * Get the disk space available for the files area.
     *
     * @param context the application context.
     * @return the disk space available in bytes.
     */
    public static long getFilesDiskSpace(@NonNull Context context) {

        File dir = context.getFilesDir();
        return getDiskSpace(dir);
    }

    /**
     * Get the disk space available for the file system where the file is stored.
     *
     * @param directory the directory to check
     * @return the disk space available in bytes.
     */
    public static long getDiskSpace(@NonNull File directory) {

        StatFs stat = new StatFs(directory.getPath());

        return stat.getBlockSizeLong() * stat.getAvailableBlocksLong();
    }

    /**
     * Get the disk space for the file system where the file is stored.
     *
     * @param directory the directory to check
     * @return the disk space in bytes.
     */
    public static long getDiskTotalSpace(@NonNull File directory) {

        StatFs stat = new StatFs(directory.getPath());

        return stat.getBlockSizeLong() * stat.getBlockCountLong();
    }

    /**
     * Get the disk space used for the file system where the file is stored.
     *
     * @param directory the directory to check
     * @return the disk space used in bytes.
     */
    public static long getDiskUsedSpace(@NonNull File directory) {

        long size = 0;

        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                size += getDiskUsedSpace(file);
            } else {
                size += file.length();
            }
        }

        return size;
    }

    /**
     * Returns true if both objects are equals.  Objects can be null.
     *
     * @param s1 first string
     * @param s2 second string
     * @return true if the two objects are both null or equal.
     */
    public static boolean equals(@Nullable Object s1, @Nullable Object s2) {

        if (s1 == s2) {
            return true;
        } else if (s1 == null) {
            return false;
        } else {
            return s1.equals(s2);
        }
    }

    /**
     * Escapes the node portion of a JID according to "JID Escaping" (JEP-0106).
     * Escaping replaces characters prohibited by node-prep with escape
     * sequences, as follows:
     * <p>
     * <p>
     * <table border="1">
     * <tr>
     * <td><b>Unescaped Character</b></td>
     * <td><b>Encoded Sequence</b></td>
     * </tr>
     * <tr>
     * <td>&lt;space&gt;</td>
     * <td>\20</td>
     * </tr>
     * <tr>
     * <td>"</td>
     * <td>\22</td>
     * </tr>
     * <tr>
     * <td>&</td>
     * <td>\26</td>
     * </tr>
     * <tr>
     * <td>'</td>
     * <td>\27</td>
     * </tr>
     * <tr>
     * <td>/</td>
     * <td>\2f</td>
     * </tr>
     * <tr>
     * <td>:</td>
     * <td>\3a</td>
     * </tr>
     * <tr>
     * <td>&lt;</td>
     * <td>\3c</td>
     * </tr>
     * <tr>
     * <td>&gt;</td>
     * <td>\3e</td>
     * </tr>
     * <tr>
     * <td>@</td>
     * <td>\40</td>
     * </tr>
     * <tr>
     * <td>\</td>
     * <td>\5c</td>
     * </tr>
     * </table>
     * <p>
     * <p>
     * This process is useful when the node comes from an external source that
     * doesn't conform to nodeprep. For example, a username in LDAP may be
     * "Joe Smith". Because the &lt;space&gt; character isn't a valid part of a
     * node, the username should be escaped to "Joe\20Smith" before being made
     * into a JID (e.g. "joe\20smith@example.com" after case-folding, etc. has
     * been applied).
     * <p>
     * <p>
     * All node escaping and un-escaping must be performed manually at the
     * appropriate time; the JID class will not escape or un-escape
     * automatically.
     *
     * @param node the node.
     * @return the escaped version of the node.
     */
    public static String escapeNode(String node) {
        if (node == null) {
            return null;
        }
        StringBuilder buf = new StringBuilder(node.length() + 8);
        for (int i = 0, n = node.length(); i < n; i++) {
            char c = node.charAt(i);
            switch (c) {
                case '"':
                    buf.append("\\22");
                    break;
                case '&':
                    buf.append("\\26");
                    break;
                case '\'':
                    buf.append("\\27");
                    break;
                case '/':
                    buf.append("\\2f");
                    break;
                case ':':
                    buf.append("\\3a");
                    break;
                case '<':
                    buf.append("\\3c");
                    break;
                case '>':
                    buf.append("\\3e");
                    break;
                case '@':
                    buf.append("\\40");
                    break;
                case '\\':
                    buf.append("\\5c");
                    break;
                default: {
                    if (Character.isWhitespace(c)) {
                        buf.append("\\20");
                    } else {
                        buf.append(c);
                    }
                }
            }
        }
        return buf.toString();
    }

    /**
     * Replace '&' by '&amp;'
     *
     * @param string the string to replace.
     * @return the replaced string.
     */
    @Nullable
    public static String escapeAmpersand(@Nullable String string) {

        if (string != null) {

            return string.replace("&", "&amp;");
        }

        return null;
    }

    public static boolean isValidHostname(@Nullable String hostname) {
        if (hostname == null) {
            return false;
        }
        try {
            InetAddress addr = InetAddress.getByName(hostname);
            return true;

        } catch (Exception ex) {
            return false;
        }
    }

    /// ProductConfig utils

    public static byte[] parseIP(String ip) {
        byte[] res = new byte[4];

        String[] ipBytes = ip.split("\\.");

        for (int i = 0; i < 4; i++) {
            String ipByte = ipBytes[i];
            res[i] = (byte) Integer.parseInt(ipByte);
        }
        return res;
    }

    @NonNull
    public static String toIPv4(long ipAddr) {

        return (ipAddr >> 24 & 0xFF) + "." + (ipAddr >> 16 & 0xFF) + "." + (ipAddr >> 8 & 0xFF) + "." + (ipAddr & 0xFF);
    }

    @NonNull
    public static String toIPv6(long ipHigh, long ipLow) {

        return toIPv6Part(ipHigh) + ":" + toIPv6Part(ipLow);
    }

    @NonNull
    public static String toIPv6Part(long value) {

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(":");
            }
            sb.append(hexArray[(int) (value >> 60) & 0x0F]);
            value = value << 4;
            sb.append(hexArray[(int) (value >> 60) & 0x0F]);
            value = value << 4;
        }
        return sb.toString();
    }

    // From https://www.baeldung.com/java-byte-arrays-hex-strings
    public static byte[] decodeHexString(String hexString) {
        if (hexString.length() % 2 == 1) {
            throw new IllegalArgumentException(
                    "Invalid hexadecimal String supplied.");
        }

        byte[] bytes = new byte[hexString.length() / 2];
        for (int i = 0; i < hexString.length(); i += 2) {
            bytes[i / 2] = hexToByte(hexString.substring(i, i + 2));
        }
        return bytes;
    }

    private static byte hexToByte(String hexString) {
        int firstDigit = toDigit(hexString.charAt(0));
        int secondDigit = toDigit(hexString.charAt(1));
        return (byte) ((firstDigit << 4) + secondDigit);
    }

    private static int toDigit(char hexChar) {
        int digit = Character.digit(hexChar, 16);
        if (digit == -1) {
            throw new IllegalArgumentException(
                    "Invalid Hexadecimal Character: " + hexChar);
        }
        return digit;
    }
}
