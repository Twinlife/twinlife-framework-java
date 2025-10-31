/*
 *  Copyright (c) 2015-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

@SuppressWarnings("unused")
public interface SerializerFactory {

    @Nullable
    Serializer getObjectSerializer(@NonNull Object object);

    @Nullable
    Serializer getSerializer(@NonNull UUID schemaId, int schemaVersion);
}