/*
 *  Copyright (c) 2021-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * PushFile IQ.
 * <p>
 * Schema version 7
 * Date: 2021/04/07
 *
 * <pre>
 * {
 *  "schemaId":"8359efba-fb7e-4378-a054-c4a9e2d37f8f",
 *  "schemaVersion":"7",
 *
 *  "type":"record",
 *  "name":"PushFileIQ",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"twincodeOutboundId", "type":"uuid"}
 *     {"name":"sequenceId", "type":"long"}
 *     {"name":"sendToTwincodeOutboundId", "type":["null", "UUID"]},
 *     {"name":"replyTo", "type":["null", {
 *         {"name":"twincodeOutboundId", "type":"uuid"},
 *         {"name":"sequenceId", "type":"long"}
 *     }},
 *     {"name":"createdTimestamp", "type":"long"}
 *     {"name":"sentTimestamp", "type":"long"}
 *     {"name":"expireTimeout", "type":"long"}
 *     {"name":"extension", "type":["null", "String"]}
 *     {"name":"length", "type":"long"}
 *     {"name":"copyAllowed", "type":"boolean"}
 *     {"name":"thumbnail", [null, "type":"bytes"]}
 *     {"name":"descriptorType", "type":"enum"},
 *     {"name":"width", "type":"int"}
 *     {"name":"height", "type":"int"}
 *     {"name":"duration", "type":"long"}
 *  ]
 * }
 *
 * </pre>
 */
class PushFileIQ extends BinaryPacketIQ {

    static final UUID SCHEMA_ID = UUID.fromString("8359efba-fb7e-4378-a054-c4a9e2d37f8f");
    static final int SCHEMA_VERSION_7 = 7;
    static final BinaryPacketIQSerializer IQ_PUSH_FILE_SERIALIZER = createSerializer(SCHEMA_ID, SCHEMA_VERSION_7);

    @NonNull
    final FileDescriptorImpl fileDescriptorImpl;
    @Nullable
    final byte[] thumbnail;

    PushFileIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId,
               @NonNull FileDescriptorImpl fileDescriptorImpl, @Nullable byte[] thumbnail) {

        super(serializer, requestId);

        this.fileDescriptorImpl = fileDescriptorImpl;
        this.thumbnail = thumbnail;
    }

    @NonNull
    static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new PushFileIQSerializer(schemaId, schemaVersion);
    }

    //
    // Override Object methods
    //
    @Override
    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" fileDescriptorImpl=");
            stringBuilder.append(fileDescriptorImpl);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("PushFileIQ: ");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }

    static class PushFileIQSerializer extends BinaryPacketIQSerializer {

        PushFileIQSerializer(@NonNull UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, PushFileIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            PushFileIQ pushFileIQ = (PushFileIQ) object;

            FileDescriptorImpl fileDescriptor = pushFileIQ.fileDescriptorImpl;
            encoder.writeUUID(fileDescriptor.getTwincodeOutboundId());
            encoder.writeLong(fileDescriptor.getSequenceId());
            encoder.writeOptionalUUID(fileDescriptor.getSendTo());
            DescriptorId replyTo = fileDescriptor.getReplyToDescriptorId();
            if (replyTo == null) {
                encoder.writeEnum(0);
            } else {
                encoder.writeEnum(1);
                encoder.writeUUID(replyTo.twincodeOutboundId);
                encoder.writeLong(replyTo.sequenceId);
            }
            encoder.writeLong(fileDescriptor.getCreatedTimestamp());
            encoder.writeLong(fileDescriptor.getSentTimestamp());
            encoder.writeLong(fileDescriptor.getExpireTimeout());
            encoder.writeOptionalString(fileDescriptor.getExtension());
            encoder.writeLong(fileDescriptor.getLength());
            encoder.writeBoolean(fileDescriptor.isCopyAllowed());
            if (pushFileIQ.thumbnail != null) {
                encoder.writeEnum(pushFileIQ.thumbnail.length != 0 ? 1 : 2);
                encoder.writeData(pushFileIQ.thumbnail);
            } else {
                encoder.writeEnum(0);
            }

            switch (fileDescriptor.getType()) {
                case FILE_DESCRIPTOR:
                    encoder.writeEnum(0);
                    break;

                case IMAGE_DESCRIPTOR:
                    ImageDescriptorImpl imageDescriptor = (ImageDescriptorImpl) fileDescriptor;
                    encoder.writeEnum(1);
                    encoder.writeInt(imageDescriptor.getWidth());
                    encoder.writeInt(imageDescriptor.getHeight());

                    break;

                case AUDIO_DESCRIPTOR:
                    AudioDescriptorImpl audioDescriptor = (AudioDescriptorImpl) fileDescriptor;
                    encoder.writeEnum(2);
                    encoder.writeLong(audioDescriptor.getDuration());
                    break;

                case VIDEO_DESCRIPTOR:
                    VideoDescriptorImpl videoDescriptor = (VideoDescriptorImpl) fileDescriptor;
                    encoder.writeEnum(3);
                    encoder.writeInt(videoDescriptor.getWidth());
                    encoder.writeInt(videoDescriptor.getHeight());
                    encoder.writeLong(videoDescriptor.getDuration());
                    break;

                case NAMED_FILE_DESCRIPTOR:
                    NamedFileDescriptorImpl namedFileDescriptor = (NamedFileDescriptorImpl) fileDescriptor;
                    encoder.writeEnum(4);
                    encoder.writeString(namedFileDescriptor.getName());
                    break;

                default:
                    throw new SerializerException();
            }

        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            final long requestId = decoder.readLong();
            final UUID twincodeOutboundId = decoder.readUUID();
            final long sequenceId = decoder.readLong();
            final UUID sendTo = decoder.readOptionalUUID();
            final DescriptorId replyTo = DescriptorImpl.DescriptorImplSerializer_4.readOptionalDescriptorId(decoder);
            final long createdTimestamp = decoder.readLong();
            final long sentTimestamp = decoder.readLong();
            final long expireTimeout = decoder.readLong();

            final String extension = decoder.readOptionalString();

            final long length = decoder.readLong();
            final boolean copyAllowed = decoder.readBoolean();
            final FileDescriptorImpl fileDescriptor;
            int width, height;
            byte[] thumbnail;
            boolean hasThumbnail;
            switch (decoder.readEnum()) {
                case 1:
                    thumbnail = decoder.readBytes(null).array();
                    hasThumbnail = true;
                    break;

                case 2:
                    // Ignore
                    decoder.readBytes(null);
                    thumbnail = null;
                    hasThumbnail = true;
                    break;

                default:
                    thumbnail = null;
                    hasThumbnail = false;
                    break;
            }
            long duration;
            String name;
            switch (decoder.readEnum()) {
                case 0: // FILE_DESCRIPTOR
                    fileDescriptor = new FileDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo,
                            copyAllowed, hasThumbnail, createdTimestamp, sentTimestamp, length, extension);
                    break;

                case 1: // IMAGE_DESCRIPTOR
                    width = decoder.readInt();
                    height = decoder.readInt();
                    fileDescriptor = new ImageDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo,
                            copyAllowed, hasThumbnail, createdTimestamp, sentTimestamp, length, extension, width, height);
                    break;

                case 2: // AUDIO_DESCRIPTOR
                    duration = decoder.readLong();
                    fileDescriptor = new AudioDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo,
                            copyAllowed, hasThumbnail, createdTimestamp, sentTimestamp, length, extension, duration);
                    break;

                case 3: // VIDEO_DESCRIPTOR
                    width = decoder.readInt();
                    height = decoder.readInt();
                    duration = decoder.readLong();
                    fileDescriptor = new VideoDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo,
                            copyAllowed, hasThumbnail, createdTimestamp, sentTimestamp, length, extension, width, height, duration);
                    break;

                case 4: // NAMED_FILE_DESCRIPTOR
                    name = decoder.readString();
                    fileDescriptor = new NamedFileDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo,
                            copyAllowed, hasThumbnail, createdTimestamp, sentTimestamp, length, extension, name);
                    break;

                default:
                    throw new SerializerException();
            }

            return new PushFileIQ(this, requestId, fileDescriptor, thumbnail);
        }
    }
}
