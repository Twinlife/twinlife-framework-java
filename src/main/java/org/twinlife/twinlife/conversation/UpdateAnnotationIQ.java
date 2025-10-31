/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.AnnotationType;
import org.twinlife.twinlife.ConversationService.DescriptorAnnotation;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Update annotation IQ.
 * <p>
 * Schema version 1
 *  Date: 2023/01/10
 *
 * <pre>
 * {
 *  "schemaId":"a4bb8ccd-0b4b-43be-80ca-4714bedc2f79",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"UpdateAnnotationIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"mode", "type":"int"},
 *     {"name":"peerAnnotationCount", "type":"int"},
 *     {"name":"peerAnnotations": [
 *       {"name":"twincodeOutboundId", "type":"uuid"}
 *       {"name":"annotationCount", "type":"int"},
 *       {"name":"annotations": [
 *         {"name":"annotationType", "type":"int"}
 *         {"name":"annotationValue", "type":"int"}
 *       ]}
 *     ]}
 * }
 *
 * </pre>
 */
class UpdateAnnotationIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("a4bb8ccd-0b4b-43be-80ca-4714bedc2f79");
    static final int SCHEMA_VERSION_1 = 1;
    static final BinaryPacketIQSerializer IQ_UPDATE_ANNOTATION_SERIALIZER = UpdateAnnotationIQ.createSerializer(SCHEMA_ID, SCHEMA_VERSION_1);

    enum UpdateType {
        SET_ANNOTATION,
        ADD_ANNOTATION,
        DEL_ANNOTATION
    }

    @NonNull
    final DescriptorId descriptorId;
    @NonNull
    final UpdateType mode;
    @NonNull
    final Map<UUID, List<DescriptorAnnotation>> annotations;

    UpdateAnnotationIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull DescriptorId descriptorId,
                       @NonNull UpdateType updateType, @NonNull Map<UUID, List<DescriptorAnnotation>> annotations) {

        super(serializer, requestId);

        this.descriptorId = descriptorId;
        this.mode = updateType;
        this.annotations = annotations;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new UpdateAnnotationIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" descriptorId=");
            stringBuilder.append(descriptorId);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("UpdateAnnotationIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class UpdateAnnotationIQSerializer extends BinaryPacketIQSerializer {

        UpdateAnnotationIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, UpdateAnnotationIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            final UpdateAnnotationIQ updateAnnotationIQ = (UpdateAnnotationIQ) object;

            encoder.writeUUID(updateAnnotationIQ.descriptorId.twincodeOutboundId);
            encoder.writeLong(updateAnnotationIQ.descriptorId.sequenceId);
            switch (updateAnnotationIQ.mode) {
                case SET_ANNOTATION:
                    encoder.writeEnum(1);
                    break;

                case ADD_ANNOTATION:
                    encoder.writeEnum(2);
                    break;

                case DEL_ANNOTATION:
                    encoder.writeEnum(3);
                    break;
            }
            encoder.writeInt(updateAnnotationIQ.annotations.size());
            for (final Map.Entry<UUID, List<DescriptorAnnotation>> annotationEntry : updateAnnotationIQ.annotations.entrySet()) {
                final List<DescriptorAnnotation> annotations = annotationEntry.getValue();
                encoder.writeUUID(annotationEntry.getKey());
                encoder.writeInt(annotations.size());
                for (final DescriptorAnnotation annotation : annotations) {
                    switch (annotation.getType()) {
                        case FORWARD:
                            encoder.writeEnum(1);
                            break;

                        case FORWARDED:
                            encoder.writeEnum(2);
                            break;

                        case SAVE:
                            encoder.writeEnum(3);
                            break;

                        case LIKE:
                            encoder.writeEnum(4);
                            break;

                        case POLL:
                            encoder.writeEnum(5);
                            break;

                        default:
                            throw new SerializerException("Invalid annotation");
                    }
                    encoder.writeInt(annotation.getValue());
                }
            }
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final DescriptorId descriptorId = new DescriptorId(0, twincodeOutboundId, sequenceId);
            final UpdateType mode;
            switch (decoder.readEnum()) {
                case 1:
                    mode = UpdateType.SET_ANNOTATION;
                    break;

                case 2:
                    mode = UpdateType.ADD_ANNOTATION;
                    break;

                case 3:
                    mode = UpdateType.DEL_ANNOTATION;
                    break;

                default:
                    throw new SerializerException();
            }

            final Map<UUID, List<DescriptorAnnotation>> annotations = new HashMap<>();
            int count = decoder.readInt();
            while (count > 0) {
                count--;
                final UUID twincodeId = decoder.readUUID();
                final List<DescriptorAnnotation> list = new ArrayList<>();
                int annotationCount = decoder.readInt();

                annotations.put(twincodeId, list);
                while (annotationCount > 0) {
                    annotationCount--;
                    final int updateType = decoder.readEnum();
                    final int value = decoder.readInt();
                    switch (updateType) {
                        case 1:
                            list.add (new DescriptorAnnotation(AnnotationType.FORWARD, value, 0));
                            break;

                        case 2:
                            list.add (new DescriptorAnnotation(AnnotationType.FORWARDED, value, 0));
                            break;

                        case 3:
                            list.add (new DescriptorAnnotation(AnnotationType.SAVE, value, 0));
                            break;

                        case 4:
                            list.add (new DescriptorAnnotation(AnnotationType.LIKE, value, 0));
                            break;

                        case 5:
                            list.add (new DescriptorAnnotation(AnnotationType.POLL, value, 0));
                            break;

                        default:
                            // Ignore this annotation
                            break;
                    }
                }
            }

            return new UpdateAnnotationIQ(this, requestId, descriptorId, mode, annotations);
        }
    }
}
