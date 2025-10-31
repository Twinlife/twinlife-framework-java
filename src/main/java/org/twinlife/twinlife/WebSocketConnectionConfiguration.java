/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributor: Zhuoyu Ma (Zhuoyu.Ma@twinlife-systems.com)
 *  Contributor: Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.net.URI;
import java.net.URISyntaxException;

public class WebSocketConnectionConfiguration {

    private final boolean mWss;
    @NonNull
    private final String mHost;
    private final int mPort;
    @NonNull
    private final String mDomain;
    @NonNull
    private final String mFilePath;

    public WebSocketConnectionConfiguration(boolean wss, @NonNull String host, int port, @NonNull String domain, @NonNull String filePath) {
        mHost = host;
        mPort = port;
        mDomain = domain;
        mWss = wss;
        mFilePath = filePath.charAt(0) != '/' ? '/' + filePath : filePath;
    }

    @NonNull
    URI getURI() throws URISyntaxException {

        return new URI((mWss ? "wss://" : "ws://") + mHost + ":" + mPort + mFilePath);
    }

    @NonNull
    public String getHost() {
        return mHost;
    }

    public int getPort() {
        return mPort;
    }

    @NonNull
    public String getPath() {
        return mFilePath;
    }

    public boolean isSecure() {
        return mWss;
    }

    @NonNull
    String getDomain() {

        return mDomain;
    }
}
