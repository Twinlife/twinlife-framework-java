/*
 *  Copyright (c) 2013-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.datatype;

import android.util.Log;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameBooleanValue;
import org.twinlife.twinlife.BaseService.AttributeNameListValue;
import org.twinlife.twinlife.BaseService.AttributeNameLongValue;
import org.twinlife.twinlife.BaseService.AttributeNameStringValue;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.BaseService.AttributeNameUUIDValue;
import org.twinlife.twinlife.BaseService.AttributeNameVoidValue;
import org.twinlife.twinlife.SerializerException;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ArrayData extends Data {
    private static final String LOG_TAG = "ArrayData";
    private static final boolean DEBUG = false;

    private final List<Data> mFields;

    public ArrayData() {

        mFields = new ArrayList<>();
    }

    public ArrayData(String name) {

        super(name);

        mFields = new ArrayList<>();
    }

    public void addData(Data value) {

        mFields.add(value);
    }

    public void addData(List<?> attributes) {

        if (attributes == null) {

            return;
        }

        for (Object attribute : attributes) {
            Data attributeField;
            if (attribute instanceof AttributeNameBooleanValue) {
                AttributeNameBooleanValue lAttribute = (AttributeNameBooleanValue) attribute;
                attributeField = new BooleanData(lAttribute.name, (Boolean) lAttribute.value);
                addData(attributeField);
            } else if (attribute instanceof AttributeNameLongValue) {
                AttributeNameLongValue lAttribute = (AttributeNameLongValue) attribute;
                attributeField = new LongData(lAttribute.name, (Long) lAttribute.value);
                addData(attributeField);
            } else if (attribute instanceof AttributeNameStringValue) {
                AttributeNameStringValue lAttribute = (AttributeNameStringValue) attribute;
                attributeField = new StringData(lAttribute.name, (String) lAttribute.value);
                addData(attributeField);
            } else if (attribute instanceof AttributeNameVoidValue) {
                AttributeNameVoidValue lAttribute = (AttributeNameVoidValue) attribute;
                attributeField = new VoidData(lAttribute.name);
                addData(attributeField);
            } else if (attribute instanceof BaseService.AttributeNameUUIDValue) {
                AttributeNameUUIDValue lAttribute = (AttributeNameUUIDValue) attribute;
                attributeField = new UUIDData(lAttribute.name, (UUID) lAttribute.value);
                addData(attributeField);
            } else if (attribute instanceof AttributeNameListValue) {
                AttributeNameListValue lAttribute = (AttributeNameListValue) attribute;
                ArrayData arrayData = new ArrayData(lAttribute.name);
                addData(arrayData);
                arrayData.addData((List<?>) lAttribute.value);
            }
        }
    }

    public List<AttributeNameValue> getData() {

        List<AttributeNameValue> attributes = new ArrayList<>();
        for (Data field : mFields) {
            if (field.isPrimitiveData()) {
                PrimitiveData<?> primitiveData = (PrimitiveData<?>) field;
                String type = primitiveData.getType();
                if (type != null) {
                    switch (type) {
                        case "boolean":
                            attributes.add(new AttributeNameBooleanValue(primitiveData.getName(), primitiveData.getBooleanValue()));
                            break;

                        case "long":
                            attributes.add(new AttributeNameLongValue(primitiveData.getName(), primitiveData.getLongValue()));
                            break;

                        case "string":
                            attributes.add(new AttributeNameStringValue(primitiveData.getName(), primitiveData.getStringValue()));
                            break;

                        case "uuid":
                            attributes.add(new AttributeNameUUIDValue(primitiveData.getName(), primitiveData.getUUIDValue()));
                            break;
                    }
                }
            } else if (field.isVoidData()) {
                attributes.add(new AttributeNameVoidValue(field.getName()));
            } else if (field.isArrayData()) {
                attributes.add(new AttributeNameListValue(field.getName(), ((ArrayData) field).getData()));
            }
        }

        return attributes;
    }

    public List<Data> getValues() {

        return mFields;
    }

    @Override
    public boolean isArrayData() {

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

                        case "uuid":
                            field = new UUIDData();
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
                    mFields.add(field);
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

        if (mFields.isEmpty()) {
            stringBuilder.append(" type='array'/>");
        } else {
            stringBuilder.append(" type='array'>");
            for (Data field : mFields) {
                field.toXml(stringBuilder);
            }
            stringBuilder.append("</field>");
        }
    }
}