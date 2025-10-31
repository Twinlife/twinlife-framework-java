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

import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

public interface Decoder {

    boolean readBoolean() throws SerializerException;

    int readInt() throws SerializerException;

    long readLong() throws SerializerException;

    @NonNull
    UUID readUUID() throws SerializerException;

    @Nullable
    UUID readOptionalUUID() throws SerializerException;

    double readDouble() throws SerializerException;

    int readEnum() throws SerializerException;

    @NonNull
    String readString() throws SerializerException;

    @Nullable
    String readOptionalString() throws SerializerException;

    @SuppressWarnings("SameParameterValue")
    @NonNull
    ByteBuffer readBytes(@Nullable ByteBuffer old) throws SerializerException;

    @Nullable
    byte[] readOptionalBytes(@Nullable ByteBuffer old) throws SerializerException;

    @SuppressWarnings("SameParameterValue")
    void readFixed(@NonNull byte[] bytes, int start, int length) throws SerializerException;

    @Nullable
    List<BaseService.AttributeNameValue> readAttributes() throws SerializerException;

    boolean isEof();
}
