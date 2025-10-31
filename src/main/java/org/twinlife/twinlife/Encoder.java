/*
 *  Copyright (c) 2015-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public interface Encoder {

    void writeBoolean(boolean value) throws SerializerException;

    void writeZero() throws SerializerException;

    void writeInt(int value) throws SerializerException;

    void writeLong(long value) throws SerializerException;

    void writeUUID(@NonNull UUID value) throws SerializerException;

    void writeOptionalUUID(@Nullable UUID value) throws SerializerException;

    void writeDouble(double value) throws SerializerException;

    void writeEnum(int value) throws SerializerException;

    void writeString(@NonNull String value) throws SerializerException;

    void writeOptionalString(@Nullable String value) throws SerializerException;

    void writeData(@NonNull byte[] bytes) throws SerializerException;

    void writeOptionalBytes(@Nullable byte[] bytes) throws SerializerException;

    @SuppressWarnings({"SameParameterValue", "unused"})
    void writeBytes(@NonNull byte[] bytes, int start, int length) throws SerializerException;

    void writeFixed(@NonNull byte[] bytes, int start, int length) throws SerializerException;

    void writeAttributes(@Nullable List<BaseService.AttributeNameValue> attributes) throws SerializerException;
}
