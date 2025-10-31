/*
 *  Copyright (c) 2014-2017 twinlife SA.
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

public class CDATAData extends Data {
    private static final String LOG_TAG = "CDATAData";
    private static final boolean DEBUG = false;

    private String mValue;

    @SuppressWarnings({"WeakerAccess", "SameReturnValue"})
    public String getType() {

        return "cdata";
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public Bitmap getBitmapValue() {

        return null;
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public boolean getBooleanValue() {

        return false;
    }

    @SuppressWarnings({"SameReturnValue", "unused"})
    public long getLongValue() {

        return 0L;
    }

    public String getStringValue() {

        return mValue;
    }

    CDATAData() {
    }

    public CDATAData(String name, String value) {
        super(name);

        mValue = value;
    }

    @SuppressWarnings("unused")
    public String getValue() {

        return mValue;
    }

    public boolean isCDATAData() {

        return true;
    }

    @Override
    protected void parse(XmlPullParser parser) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "parse parser=" + parser);
        }

        mName = parser.getAttributeValue("", "name");

        try {
            int eventType = parser.nextToken();
            if (eventType != XmlPullParser.CDSECT) {

                return;
            }

            mValue = parser.getText();

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
        stringBuilder.append("<![CDATA[");
        stringBuilder.append(getStringValue());
        stringBuilder.append("]]>");
        stringBuilder.append("</field>");
    }
}