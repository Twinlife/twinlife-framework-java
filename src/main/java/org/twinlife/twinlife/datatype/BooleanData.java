/*
 *  Copyright (c) 2014-2017 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife.datatype;

import android.graphics.Bitmap;

import java.util.UUID;

public class BooleanData extends PrimitiveData<Boolean> {

    BooleanData() {
    }

    public BooleanData(String name, Boolean value) {

        super(name, value);
    }

    @Override
    public String getType() {

        return "boolean";
    }

    @Override
    public Bitmap getBitmapValue() {

        return null;
    }

    @Override
    public boolean getBooleanValue() {

        return mValue;
    }

    @Override
    public long getLongValue() {

        return 0;
    }

    @Override
    public String getStringValue() {

        return mValue ? "true" : "false";
    }

    @Override
    public UUID getUUIDValue() {

        return null;
    }

    @Override
    Boolean fromXml(String value) {

        return "true".equals(value);
    }
}