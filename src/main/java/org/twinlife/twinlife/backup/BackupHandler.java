/*
 *  Copyright (c) 2024-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public abstract class BackupHandler<T> {

    public interface Restorer<T> {
        @Nullable
        T restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException;

        @NonNull
        VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException;
    }

    protected final Map<Integer, Restorer<T>> mRestorers = new HashMap<>();

    public BackupHandler() {
        initDeserializers();
    }

    protected abstract void initDeserializers();

    public abstract void backup(@NonNull BinaryEncoder encoder) throws SerializerException;

    @Nullable
    public T restore(@NonNull BinaryDecoder decoder, boolean inPlace) throws SerializerException {
        int version = decoder.readInt();
        Restorer<T> restorer = mRestorers.get(version);
        if (restorer == null) {
            throw new SerializerException("No restorer found for version " + version);
        }

        return restorer.restore(decoder, inPlace);
    }

    @NonNull
    public VerifyResult verify(@NonNull BinaryDecoder decoder) throws SerializerException {
        int version = decoder.readInt();
        Restorer<T> restorer = mRestorers.get(version);
        if (restorer == null) {
            throw new SerializerException("No restorer found for version " + version);
        }

        return restorer.verify(decoder);
    }

    public Map<UUID, Integer> getStats() {
        return null;
    }
}