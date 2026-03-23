/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

public class VerifyResult {

    public static class Present<T> extends VerifyResult {
        @NonNull
        public final T object;

        public final boolean modified;

        public Present(@NonNull T object, boolean modified) {
            this.object = object;
            this.modified = modified;
        }

        @NonNull
        @Override
        public String toString() {
            return "VerifyResult.Present{" +
                    "object=" + object +
                    ", modified=" + modified +
                    '}';
        }
    }

    public static class Absent<T> extends VerifyResult {
        @NonNull
        public final UUID id;

        @Nullable
        public final UUID schemaId;

        @NonNull
        public final Class<T> type;

        public Absent(@NonNull UUID id, @NonNull Class<T> type) {
            this.id = id;
            this.schemaId = null;
            this.type = type;
        }

        public Absent(@NonNull UUID id, @NonNull UUID schemaId, @NonNull Class<T> type) {
            this.id = id;
            this.schemaId = schemaId;
            this.type = type;
        }

        @NonNull
        @Override
        public String toString() {
            return "VerifyResult.Absent{" +
                    "id=" + id +
                    ", schemaId=" + schemaId +
                    ", type=" + type +
                    '}';
        }
    }


    private VerifyResult() {
    }
}
