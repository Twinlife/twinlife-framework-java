/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

import java.util.UUID;

/**
 * A roster member with its twincode, permission and public key.
 */
public class RosterId {

    public final UUID id;
    public final UUID schemaId;

    public RosterId(@NonNull UUID id, @NonNull UUID schemaId) {

        this.id = id;
        this.schemaId = schemaId;
    }

    public RosterId(@NonNull String value) {
        String[] parts = value.split(":");
        if (parts.length == 2) {
            this.id = UUID.fromString(parts[0]);
            this.schemaId = UUID.fromString(parts[1]);
        } else {
            throw new IllegalArgumentException("Invalid RosterId value: " + value);
        }
    }
}
