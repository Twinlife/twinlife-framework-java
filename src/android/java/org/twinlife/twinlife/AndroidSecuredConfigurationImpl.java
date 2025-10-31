/*
 *  Copyright (c) 2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

public class AndroidSecuredConfigurationImpl implements ConfigurationService.SecuredConfiguration {
    private final String mName;
    private byte[] mData;
    private final boolean mCreated;

    AndroidSecuredConfigurationImpl(String name, byte[] data) {
        mName = name;
        mData = data;
        mCreated = mData != null;
    }

    @Override
    public String getName() {

        return mName;
    }

    @Override
    public byte[] getData() {

        return mData;
    }

    @Override
    public void setData(byte[] raw) {

        mData = raw;
    }

    boolean isCreated() {

        return mCreated;
    }
}
