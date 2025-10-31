/*
 *  Copyright (c) 2013-2018 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife.datatype;

import android.util.Log;

import org.twinlife.twinlife.SerializerException;
import org.xmlpull.v1.XmlPullParser;

import java.util.Collection;
import java.util.HashMap;

public class RecordData extends Data {
    private static final String LOG_TAG = "RecordData";
    private static final boolean DEBUG = false;

    private final HashMap<String, Data> mFields;

    public RecordData() {

        mFields = new HashMap<>();
    }

    public RecordData(String name) {
        super(name);

        mFields = new HashMap<>();
    }

    public int size() {

        return mFields.size();
    }

    public Data getData(String name) {

        return mFields.get(name);
    }

    public void addData(Data data) {

        if (data.getName() != null) {
            mFields.put(data.getName(), data);
        }
    }

    @Override
    public boolean isRecordData() {

        return true;
    }

    @Override
    public void parse(XmlPullParser parser) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "parse parser=" + parser);
        }

        mName = parser.getAttributeValue("", "name");

        try {
            int eventType = parser.next();
            while (eventType == XmlPullParser.START_TAG && "field".equals(parser.getName())) {
                String type = parser.getAttributeValue("", "type");
                Data field = null;
                if (type != null) {
                    switch (type) {
                        case "array":
                            field = new ArrayData();
                            break;

                        case "boolean":
                            field = new BooleanData();
                            break;

                        case "long":
                            field = new LongData();
                            break;

                        case "record":
                            field = new RecordData();
                            break;

                        case "string":
                            field = new StringData();
                            break;

                        case "void":
                            field = new VoidData();
                            break;

                        case "cdata":
                            field = new CDATAData();
                            break;
                    }
                }

                if (field != null) {
                    field.parse(parser);
                    mFields.put(field.getName(), field);
                }

                eventType = parser.next();
            }
        } catch (Exception exception) {
            throw new SerializerException(exception);
        }
    }

    @Override
    public void toXml(StringBuilder stringBuilder) {
        if (DEBUG) {
            Log.d(LOG_TAG, "toXml: stringBuilder=" + stringBuilder);
        }

        stringBuilder.append("<field");
        if (mName != null) {
            stringBuilder.append(" name='");
            stringBuilder.append(mName);
            stringBuilder.append("'");
        }
        Collection<Data> values = mFields.values();
        if (values.isEmpty()) {
            stringBuilder.append(" type='record'/>");
        } else {
            stringBuilder.append(" type='record'>");
            for (Data field : values) {
                field.toXml(stringBuilder);
            }
            stringBuilder.append("</field>");
        }
    }
}