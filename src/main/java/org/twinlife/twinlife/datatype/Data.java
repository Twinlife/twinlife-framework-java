/*
 *  Copyright (c) 2013-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife.datatype;

import org.twinlife.twinlife.SerializerException;
import org.xmlpull.v1.XmlPullParser;

public abstract class Data {

    String mName;

    Data() {
    }

    Data(String name) {

        mName = name;
    }

    public String getName() {

        return mName;
    }

    public boolean isArrayData() {

        return false;
    }

    public boolean isPrimitiveData() {

        return false;
    }

    public boolean isRecordData() {

        return false;
    }

    public boolean isVoidData() {

        return false;
    }

    public boolean isCDATAData() {

        return false;
    }

    abstract protected void parse(XmlPullParser parser) throws SerializerException;

    abstract protected void toXml(StringBuilder stringBuilder);
}