/*
 *  Copyright (c) 2018-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.security.MessageDigest;
import java.util.Random;

public class KeyProxyDescriptor extends ProxyDescriptor {
    private static final String LOG_TAG = "KeyProxyDescr..";

    private final String mKey;

    @Nullable
    public static String getProxyPath(@NonNull String proxyKey, @NonNull String host, int port) {

        try {
            Random random = new Random();
            StringBuilder stringBuilder = new StringBuilder();

            char[] characters = proxyKey.toCharArray();
            for (char character : characters) {
                if (character < 'a' || character > 'z') {

                    return null;
                }
                int value = character - 'a';
                value += random.nextInt(8) * 29;
                int quotient = value / 16;
                int remainder = value - (16 * quotient);
                stringBuilder.append((char) ('a' + quotient));
                stringBuilder.append((char) ('a' + remainder));
            }

            stringBuilder.append('/');

            String path = host + ':' + port;
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(path.getBytes());
            byte[] pathDigest = digest.digest();
            for (int value : pathDigest) {
                if (value < 0) {
                    value += 256;
                }
                int value1 = value / 16;
                int value2 = value - (16 * value1);
                value1 += random.nextInt(8) * 29;
                int quotient = value1 / 16;
                int remainder = value1 - (16 * quotient);
                stringBuilder.append((char) ('a' + quotient));
                stringBuilder.append((char) ('a' + remainder));
                value2 += random.nextInt(8) * 29;
                quotient = value2 / 16;
                remainder = value2 - (16 * quotient);
                stringBuilder.append((char) ('a' + quotient));
                stringBuilder.append((char) ('a' + remainder));
            }
            stringBuilder.append(".html");

            return stringBuilder.toString();
        } catch (Exception exception) {
            Log.e(LOG_TAG, "getProxyPath proxyKey=" + proxyKey + " host=" + host + " port=" + port + " exception=" + exception);
        }

        return null;
    }

    public KeyProxyDescriptor(@NonNull String address, int port, @Nullable String key) {

        super(address, port);

        mKey = key;
    }

    @Nullable
    public String getKey() {

        return mKey;
    }
}
