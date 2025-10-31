/*
 *  Copyright (c) 2015-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

public class SerializerFactoryImpl implements SerializerFactory {
    private static final String LOG_TAG = "SerializerFactoryImpl";
    private static final boolean DEBUG = false;

    private static class SerializerKey {
        @NonNull
        private final UUID schemaId;
        private final int schemaVersion;

        SerializerKey(@NonNull UUID schemaId, int schemaVersion) {

            this.schemaId = schemaId;
            this.schemaVersion = schemaVersion;
        }

        //
        // Override Object methods
        //

        @Override
        public boolean equals(Object object) {

            if (this == object) {

                return true;
            }
            if (!(object instanceof SerializerKey)) {

                return false;
            }

            SerializerKey serializerKey = (SerializerKey) object;

            return serializerKey.schemaId.equals(schemaId) && serializerKey.schemaVersion == schemaVersion;
        }

        @Override
        public int hashCode() {

            int result = 17;
            result = 31 * result + schemaId.hashCode();
            result = 31 * result + schemaVersion;

            return result;
        }
    }

    private final Object mSerializersLock = new Object();
    private final HashMap<Class<?>, Serializer> mClass2Serializers = new HashMap<>();
    private final HashMap<SerializerKey, Serializer> mSerializers = new HashMap<>();

    public void addSerializer(Serializer serializer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addSerializer serializer=" + serializer);
        }

        synchronized (mSerializersLock) {
            mClass2Serializers.put(serializer.clazz, serializer);
            SerializerKey serializerKey = new SerializerKey(serializer.schemaId, serializer.schemaVersion);
            mSerializers.put(serializerKey, serializer);
        }
    }

    public void addSerializers(Serializer[] serializers) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addSerializers serializers=" + Arrays.toString(serializers));
        }

        synchronized (mSerializersLock) {
            for (Serializer serializer : serializers) {
                mClass2Serializers.put(serializer.clazz, serializer);
                SerializerKey serializerKey = new SerializerKey(serializer.schemaId, serializer.schemaVersion);
                mSerializers.put(serializerKey, serializer);
            }
        }
    }

    @Nullable
    public Serializer getObjectSerializer(@NonNull Object object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSerializer object=" + object);
        }

        synchronized (mSerializersLock) {

            return mClass2Serializers.get(object.getClass());
        }
    }

    @Nullable
    public Serializer getSerializer(@NonNull UUID schemaId, int schemaVersion) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSerializer schemaId=" + schemaId + " schemaVersion=" + schemaVersion);
        }

        SerializerKey serializerKey = new SerializerKey(schemaId, schemaVersion);
        synchronized (mSerializersLock) {

            return mSerializers.get(serializerKey);
        }
    }
}
