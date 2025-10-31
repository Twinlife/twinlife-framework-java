/*
 *  Copyright (c) 2013-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Olivier Dupont (Oliver.Dupont@twin.life)
 */

package org.twinlife.twinlife.datatype;

import android.graphics.Bitmap;

import org.twinlife.twinlife.util.StringUtils;
import org.twinlife.twinlife.util.Utils;

import java.util.UUID;

public class StringData extends PrimitiveData<String> {

    StringData() {
    }

    public StringData(String name, String value) {

        super(name, value);
    }

    @Override
    public String getType() {

        return "string";
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

        return 0;
    }

    @Override
    public String getStringValue() {

        return mValue;
    }

    @Override
    public UUID getUUIDValue() {

        return Utils.UUIDFromString(mValue);
    }

    @Override
    String fromXml(String value) {

        return value;
    }

    @Override
    protected void toXml(StringBuilder stringBuilder) {

        stringBuilder.append("<field");
        if (mName != null) {
            stringBuilder.append(" name='");
            stringBuilder.append(mName);
            stringBuilder.append("'");
        }
        stringBuilder.append(" type='");
        stringBuilder.append(getType());
        stringBuilder.append("'>");
        stringBuilder.append("<value>");
        stringBuilder.append(StringUtils.escapeForXML(mValue));
        stringBuilder.append("</value>");
        stringBuilder.append("</field>");
    }
}