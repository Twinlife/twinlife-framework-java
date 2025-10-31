/*
 *  Copyright (c) 2013-2017 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife.datatype;

import android.graphics.Bitmap;

import java.util.UUID;

public class LongData extends PrimitiveData<Long> {

    LongData() {
    }

    public LongData(String name, Long value) {

        super(name, value);
    }

    @Override
    public String getType() {

        return "long";
    }

    @Override
    public Bitmap getBitmapValue() {

        return null;
    }

    @Override
    public boolean getBooleanValue() {

        return false;
    }

    @Override
    public long getLongValue() {

        return mValue;
    }

    @Override
    public String getStringValue() {

        return mValue.toString();
    }

    @Override
    public UUID getUUIDValue() {

        return null;
    }

    @Override
    Long fromXml(String value) {

        //noinspection EmptyCatchBlock
        try {

            return Long.valueOf(value);
        } catch (Exception exception) {
        }

        return 0L;
    }
}