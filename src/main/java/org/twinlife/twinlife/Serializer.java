/*
 *  Copyright (c) 2015-2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.UUID;

public abstract class Serializer {

    @NonNull
    public final UUID schemaId;
    public final int schemaVersion;
    @NonNull
    public final Class<?> clazz;

    public Serializer(@NonNull UUID schemaId, int schemaVersion, @NonNull Class<?> clazz) {

        this.schemaId = schemaId;
        this.schemaVersion = schemaVersion;
        this.clazz = clazz;
    }

    abstract public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException;

    @NonNull
    abstract public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException;

    public boolean isSupported(int majorVersion, int minorVersion) {

        return true;
    }
}