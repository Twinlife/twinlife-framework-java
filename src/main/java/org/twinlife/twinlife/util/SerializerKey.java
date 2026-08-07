/*
 *  Copyright (c) 2015-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import java.util.UUID;

public class SerializerKey {
    @NonNull
    private final UUID schemaId;
    private final int schemaVersion;

    public SerializerKey(@NonNull UUID schemaId, int schemaVersion) {

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
