/*
 *  Copyright (c) 2014 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributor: Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife.datatype;

import android.util.Log;

import org.twinlife.twinlife.SerializerException;
import org.xmlpull.v1.XmlPullParser;

public class VoidData extends Data {
    private static final String LOG_TAG = "VoidData";
    private static final boolean DEBUG = false;

    VoidData() {

    }

    VoidData(String name) {
        super(name);

    }

    public boolean isVoidData() {

        return true;
    }

    @SuppressWarnings("SameReturnValue")
    private String getType() {

        return "void";
    }

    @Override
    protected void parse(XmlPullParser parser) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "parse parser=" + parser);
        }

        mName = parser.getAttributeValue("", "name");

        try {
            int eventType = parser.next();
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
        stringBuilder.append("</field>");
    }
}