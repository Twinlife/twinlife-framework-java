/*
 *  Copyright (c) 2013-2017 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife.datatype;

import android.graphics.Bitmap;
import android.util.Log;

import org.twinlife.twinlife.SerializerException;
import org.xmlpull.v1.XmlPullParser;

import java.util.UUID;

public abstract class PrimitiveData<T> extends Data {
    private static final String LOG_TAG = "PrimitiveData";
    private static final boolean DEBUG = false;

    T mValue;

    public abstract String getType();

    public abstract Bitmap getBitmapValue();

    public abstract boolean getBooleanValue();

    public abstract long getLongValue();

    public abstract String getStringValue();

    public abstract UUID getUUIDValue();

    abstract T fromXml(String value);

    PrimitiveData() {
    }

    PrimitiveData(String name, T value) {

        super(name);

        mValue = value;
    }

    public T getValue() {

        return mValue;
    }

    public boolean isPrimitiveData() {

        return true;
    }

    @Override
    protected void parse(XmlPullParser parser) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "parse parser=" + parser);
        }

        mName = parser.getAttributeValue("", "name");

        try {
            int eventType = parser.next();
            if (eventType != XmlPullParser.START_TAG || !"value".equals(parser.getName())) {

                return;
            }

            mValue = fromXml(parser.nextText());

            eventType = parser.next();
            if (eventType != XmlPullParser.END_TAG || !"field".equals(parser.getName())) {
                Log.e(LOG_TAG, "parse error: eventType=" + eventType);
            }

        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    @Override
    protected void toXml(StringBuilder stringBuilder) {
        if (DEBUG) {
            Log.d(LOG_TAG, "toXml: stringBuilder=" + stringBuilder);
        }

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
        stringBuilder.append(getStringValue());
        stringBuilder.append("</value>");
        stringBuilder.append("</field>");
    }
}