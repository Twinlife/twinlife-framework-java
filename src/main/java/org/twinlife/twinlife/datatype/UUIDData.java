/*
 *  Copyright (c) 2013-2020 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.datatype;

import android.graphics.Bitmap;

import org.twinlife.twinlife.util.Utils;

import java.util.UUID;

public class UUIDData extends PrimitiveData<UUID> {

    UUIDData() {
    }

    public UUIDData(String name, UUID value) {

        super(name, value);
    }

    @Override
    public String getType() {

        return "uuid";
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

        return mValue.toString();
    }

    @Override
    public UUID getUUIDValue() {

        return mValue;
    }

    @Override
    UUID fromXml(String value) {

        return Utils.UUIDFromString(value);
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
        stringBuilder.append(mValue.toString());
        stringBuilder.append("</value>");
        stringBuilder.append("</field>");
    }
}