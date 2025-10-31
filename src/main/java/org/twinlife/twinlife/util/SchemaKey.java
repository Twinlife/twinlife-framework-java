/*
 *  Copyright (c) 2020 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;


public class SchemaKey {

    public final UUID schemaId;
    public final int version;

    public SchemaKey(@NonNull UUID schemaId, int version) {
        this.schemaId = schemaId;
        this.version = version;
    }

    @Override
    public int hashCode() {

        return schemaId.hashCode() ^ version;
    }

    @Override
    public boolean equals(@Nullable Object object) {

        if (!(object instanceof SchemaKey)) {
            return false;
        }
        SchemaKey key = (SchemaKey)object;
        return schemaId.equals(key.schemaId) && version == key.version;
    }

    //
    // Override Object methods
    //
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(schemaId);
        stringBuilder.append(":");
        stringBuilder.append(version);

        return stringBuilder.toString();
    }
}
