/*
 *  Copyright (c) 2018-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.RepositoryService.StatType;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

/**
 * Collect statistics about an object (contact, group).
 *
 * Each time a stat entry is incremented:
 *  - some points are added depending on the per-stat point table,
 *  - a scale is applied depending on the per-stat scale table.
 *
 * Once every day, the object usage score is computed from the accumulated points and scales.
 * A scale is computed for every object by using the scale of other objects.
 * Pseudo algorithm:
 *
 * scales[] = { 1.0 }
 * foreach contact c1:
 *   foreach contact c2:
 *     if c1 != c2:
 *       scales[c2] = scales[c2] * c1.scale
 *
 * Then, the object points are added, the points and scale are re-initialized:
 *
 * foreach contact c1:
 *   c1.usageScore = scales[c1] * c1.usageScore + c1.points
 *   c1.points = 0
 *   c1.scale = 1.0
 *
 */
class ObjectStatImpl {
    // Order in which the stats are serialized and de-serialized (for version 3).
    // - new stats NB_TWINCODE_SENT and NB_TWINCODE_RECEIVED
    private static final StatType[] serializeOrder = {
            StatType.NB_MESSAGE_SENT,
            StatType.NB_MESSAGE_SENT,
            StatType.NB_MESSAGE_SENT,
            StatType.NB_FILE_SENT,
            StatType.NB_IMAGE_SENT,
            StatType.NB_VIDEO_SENT,
            StatType.NB_AUDIO_SENT,
            StatType.NB_GEOLOCATION_SENT,
            StatType.NB_TWINCODE_SENT,
            StatType.NB_MESSAGE_RECEIVED,
            StatType.NB_FILE_RECEIVED,
            StatType.NB_IMAGE_RECEIVED,
            StatType.NB_VIDEO_RECEIVED,
            StatType.NB_AUDIO_RECEIVED,
            StatType.NB_GEOLOCATION_RECEIVED,
            StatType.NB_TWINCODE_RECEIVED,
            StatType.NB_AUDIO_CALL_SENT,
            StatType.NB_VIDEO_CALL_SENT,
            StatType.NB_AUDIO_CALL_RECEIVED,
            StatType.NB_VIDEO_CALL_RECEIVED,
            StatType.NB_AUDIO_CALL_MISSED,
            StatType.NB_VIDEO_CALL_MISSED
    };

    // Order in which the stats are serialized and de-serialized (for version 2).
    // - new stats NB_GEOLOCATION_SENT and NB_GEOLOCATION_RECEIVED
    private static final StatType[] serializeOrder_V2 = {
            StatType.NB_MESSAGE_SENT,
            StatType.NB_MESSAGE_SENT,
            StatType.NB_MESSAGE_SENT,
            StatType.NB_FILE_SENT,
            StatType.NB_IMAGE_SENT,
            StatType.NB_VIDEO_SENT,
            StatType.NB_AUDIO_SENT,
            StatType.NB_GEOLOCATION_SENT,
            StatType.NB_MESSAGE_RECEIVED,
            StatType.NB_FILE_RECEIVED,
            StatType.NB_IMAGE_RECEIVED,
            StatType.NB_VIDEO_RECEIVED,
            StatType.NB_AUDIO_RECEIVED,
            StatType.NB_GEOLOCATION_RECEIVED,
            StatType.NB_AUDIO_CALL_SENT,
            StatType.NB_VIDEO_CALL_SENT,
            StatType.NB_AUDIO_CALL_RECEIVED,
            StatType.NB_VIDEO_CALL_RECEIVED,
            StatType.NB_AUDIO_CALL_MISSED,
            StatType.NB_VIDEO_CALL_MISSED
    };

    // Order in which the stats are serialized and de-serialized (for version 1).
    private static final StatType[] serializeOrder_V1 = {
            StatType.NB_MESSAGE_SENT,
            StatType.NB_MESSAGE_SENT,
            StatType.NB_MESSAGE_SENT,
            StatType.NB_FILE_SENT,
            StatType.NB_IMAGE_SENT,
            StatType.NB_VIDEO_SENT,
            StatType.NB_AUDIO_SENT,
            StatType.NB_MESSAGE_RECEIVED,
            StatType.NB_FILE_RECEIVED,
            StatType.NB_IMAGE_RECEIVED,
            StatType.NB_VIDEO_RECEIVED,
            StatType.NB_AUDIO_RECEIVED,
            StatType.NB_AUDIO_CALL_SENT,
            StatType.NB_VIDEO_CALL_SENT,
            StatType.NB_AUDIO_CALL_RECEIVED,
            StatType.NB_VIDEO_CALL_RECEIVED,
            StatType.NB_AUDIO_CALL_MISSED,
            StatType.NB_VIDEO_CALL_MISSED
    };
    static final UUID SCHEMA_ID = UUID.fromString("859eee5f-8fb4-44a2-acf2-e14d3c12c160");
    static final int SCHEMA_VERSION = 3;
    static final int SCHEMA_VERSION_2 = 2;
    static final int SCHEMA_VERSION_1 = 1;

    static class ObjectStatImplSerializer extends Serializer {

        ObjectStatImplSerializer() {

            super(SCHEMA_ID, SCHEMA_VERSION, ObjectStatImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            encoder.writeUUID(schemaId);
            encoder.writeInt(schemaVersion);

            ObjectStatImpl stats = (ObjectStatImpl) object;
            encoder.writeDouble(stats.mScore);
            encoder.writeDouble(stats.mScale);
            encoder.writeDouble(stats.mPoints);
            encoder.writeLong(stats.mLastMessageDate);

            for (StatType kind : serializeOrder) {
                encoder.writeLong(stats.mStatCounters[kind.ordinal()]);
            }

            for (StatType kind : serializeOrder) {
                encoder.writeLong(stats.mReferenceCounters[kind.ordinal()]);
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {
            throw new SerializerException("Trying to serialize using a wrong serializer version");
        }

        @NonNull
        public Object deserialize(long databaseId, @NonNull Decoder decoder) throws SerializerException {

            double score = decoder.readDouble();
            double scale = decoder.readDouble();
            double points = decoder.readDouble();
            long lastMessageDate = decoder.readLong();

            long[] statCounters = new long[StatType.values().length];
            for (StatType kind : serializeOrder) {
                statCounters[kind.ordinal()] = decoder.readLong();
            }

            long[] referenceCounters = new long[StatType.values().length];
            for (StatType kind : serializeOrder) {
                referenceCounters[kind.ordinal()] = decoder.readLong();
            }

            return new ObjectStatImpl(databaseId, score, scale, points, statCounters, referenceCounters, lastMessageDate);
        }
    }
    static final ObjectStatImplSerializer SERIALIZER = new ObjectStatImplSerializer();

    static class ObjectStatImplSerializer_2 extends Serializer {

        ObjectStatImplSerializer_2() {

            super(SCHEMA_ID, SCHEMA_VERSION_2, ObjectStatImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            throw new SerializerException("Trying to serialize using a wrong serializer version");
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {
            throw new SerializerException("Trying to serialize using a wrong serializer version");
        }

        @NonNull
        public Object deserialize(long databaseId, @NonNull Decoder decoder) throws SerializerException {

            double score = decoder.readDouble();
            double scale = decoder.readDouble();
            double points = decoder.readDouble();
            long lastMessageDate = decoder.readLong();

            long[] statCounters = new long[StatType.values().length];
            for (StatType kind : serializeOrder_V2) {
                statCounters[kind.ordinal()] = decoder.readLong();
            }

            long[] referenceCounters = new long[StatType.values().length];
            for (StatType kind : serializeOrder_V2) {
                referenceCounters[kind.ordinal()] = decoder.readLong();
            }

            return new ObjectStatImpl(databaseId, score, scale, points, statCounters, referenceCounters, lastMessageDate);
        }
    }
    static final ObjectStatImplSerializer_2 SERIALIZER_2 = new ObjectStatImplSerializer_2();

    static class ObjectStatImplSerializer_1 extends Serializer {

        ObjectStatImplSerializer_1() {

            super(SCHEMA_ID, SCHEMA_VERSION_1, ObjectStatImpl.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder, @NonNull Object object) throws SerializerException {

            throw new SerializerException("Trying to serialize using a wrong serializer version");
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {
            throw new SerializerException("Trying to serialize using a wrong serializer version");
        }

        @NonNull
        public Object deserialize(long databaseId, @NonNull Decoder decoder) throws SerializerException {

            double score = decoder.readDouble();
            double scale = decoder.readDouble();
            double points = decoder.readDouble();
            long lastMessageDate = decoder.readLong();

            long[] statCounters = new long[StatType.values().length];
            for (StatType kind : serializeOrder_V1) {
                statCounters[kind.ordinal()] = decoder.readLong();
            }

            long[] referenceCounters = new long[StatType.values().length];
            for (StatType kind : serializeOrder_V1) {
                referenceCounters[kind.ordinal()] = decoder.readLong();
            }

            return new ObjectStatImpl(databaseId, score, scale, points, statCounters, referenceCounters, lastMessageDate);
        }
    }
    static final ObjectStatImplSerializer_1 SERIALIZER_1 = new ObjectStatImplSerializer_1();

    private final long mDatabaseId;
    @NonNull
    private final long[] mStatCounters;
    @NonNull
    private final long[] mReferenceCounters;
    private UUID mObjectSchemaId;
    private double mScore;
    private double mScale;
    private double mPoints;
    private long mLastMessageDate;

    ObjectStatImpl(long databaseId) {

        mDatabaseId = databaseId;
        mStatCounters = new long[StatType.values().length];
        mReferenceCounters = new long[StatType.values().length];
        mScore = 0.0;
        mScale = 1.0;
        mPoints = 0.0;
        mLastMessageDate = 0;
    }

    private ObjectStatImpl(long databaseId, double score, double scale, double points, @NonNull long[] statCounters,
                           @NonNull long[] referenceCounters, long lastMessageDate) {

        mDatabaseId = databaseId;
        mScore = score;
        mScale = scale;
        mPoints = points;
        mStatCounters = statCounters;
        mReferenceCounters = referenceCounters;
        mLastMessageDate = lastMessageDate;
    }

    long getDatabaseId() {

        return mDatabaseId;
    }

    UUID getObjectSchemaId() {

        return mObjectSchemaId;
    }

    void setObjectSchemaId(UUID schemaId) {

        mObjectSchemaId = schemaId;
    }

    double getScore() {

        return mScore;
    }

    long getLastMessageDate() {

        return mLastMessageDate;
    }

    //
    // Package specific Methods
    //

    double getScale() {

        return mScale;
    }

    boolean needReport() {

        for (int i = 0; i < mStatCounters.length; i++) {
            if (mStatCounters[i] != mReferenceCounters[i]) {
                return true;
            }
        }
        return false;
    }

    boolean checkpoint() {

        boolean result = false;
        for (int i = 0; i < mStatCounters.length; i++) {
            if (mStatCounters[i] != mReferenceCounters[i]) {
                mReferenceCounters[i] = mStatCounters[i];
                result = true;
            }
        }
        return result;
    }

    long[] getDiff() {

        long[] result = new long[StatType.values().length];

        for (int i = 0; i < result.length; i++) {
            result[i] = mStatCounters[i] - mReferenceCounters[i];
        }
        return result;
    }

    boolean updateScore(double scale) {

        double oldScore = mScore;
        mScore = mScore * scale + mPoints;
        mPoints = 0.0;
        mScale = 1.0;
        return oldScore != mScore;
    }

    void increment(StatType kind, @Nullable RepositoryService.Weight[] weights) {

        mStatCounters[kind.ordinal()]++;
        mLastMessageDate = System.currentTimeMillis();
        if (weights != null) {
            RepositoryService.Weight w = weights[kind.ordinal()];
            if (w != null) {
                mPoints = mPoints + w.points;
                mScale = mScale * w.scale;
            }
        }
    }

    void update(StatType kind, @Nullable RepositoryService.Weight[] weights, long value) {

        switch (kind) {
            case AUDIO_CALL_SENT_DURATION:
                increment(StatType.NB_AUDIO_CALL_SENT, weights);
                mStatCounters[kind.ordinal()] += value;
                break;

            case AUDIO_CALL_RECEIVED_DURATION:
                increment(StatType.NB_AUDIO_CALL_RECEIVED, weights);
                mStatCounters[kind.ordinal()] += value;
                break;

            case VIDEO_CALL_RECEIVED_DURATION:
                increment(StatType.NB_VIDEO_CALL_RECEIVED, weights);
                mStatCounters[kind.ordinal()] += value;
                break;

            case VIDEO_CALL_SENT_DURATION:
                increment(StatType.NB_VIDEO_CALL_SENT, weights);
                mStatCounters[kind.ordinal()] += value;
                break;

            default:
                // Refuse other stats.
                break;
        }
    }

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n");
        stringBuilder.append("ObjectStatImpl: [");
        boolean needSep = false;
        for (long v : mStatCounters) {
            if (needSep) {
                stringBuilder.append(",");
            }
            needSep = true;
            stringBuilder.append(v);
        }
        stringBuilder.append("]");
        return stringBuilder.toString();
    }
}
