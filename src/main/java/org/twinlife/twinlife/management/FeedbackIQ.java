/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.management;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.BuildConfig;
import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.Encoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.UUID;

/**
 * Feedback IQ.
 *
 * Schema version 1
 * <pre>
 * {
 *  "schemaId":"B3ED091A-4DB9-4C9B-9501-65F11811738B",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"FeedbackIQ",
 *  "namespace":"org.twinlife.schemas.account",
 *  "super":"org.twinlife.schemas.BinaryPacketIQ"
 *  "fields": [
 *     {"name":"email", "type":"String"},
 *     {"name":"subject", "type":"String"},
 *     {"name":"feedbackDescription", "type":"String"},
 *     {"name":"deviceDescription", "type":"String"},
 *  ]
 * }
 *
 * </pre>
 */
class FeedbackIQ extends BinaryPacketIQ {

    private static class FeedbackIQSerializer extends BinaryPacketIQSerializer {

        FeedbackIQSerializer(UUID schemaId, int schemaVersion) {

            super(schemaId, schemaVersion, FeedbackIQ.class);
        }

        @Override
        public void serialize(@NonNull SerializerFactory serializerFactory, @NonNull Encoder encoder,
                              @NonNull Object object) throws SerializerException {

            super.serialize(serializerFactory, encoder, object);

            FeedbackIQ feedbackIQ = (FeedbackIQ) object;

            encoder.writeString(feedbackIQ.email);
            encoder.writeString(feedbackIQ.subject);
            encoder.writeString(feedbackIQ.feedbackDescription);
            encoder.writeString(feedbackIQ.deviceDescription);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory,
                                  @NonNull Decoder decoder) throws SerializerException {

            throw new SerializerException();
        }
    }

    @NonNull
    public static BinaryPacketIQSerializer createSerializer(@NonNull UUID schemaId, int schemaVersion) {

        return new FeedbackIQSerializer(schemaId, schemaVersion);
    }

    @NonNull
    final String email;
    @NonNull
    final String subject;
    @NonNull
    final String feedbackDescription;
    @NonNull
    final String deviceDescription;

    FeedbackIQ(@NonNull BinaryPacketIQSerializer serializer, long requestId, @NonNull String email, @NonNull String subject,
               @NonNull String feedbackDescription, @NonNull String deviceDescription) {

        super(serializer, requestId);

        this.email = email;
        this.subject = subject;
        this.feedbackDescription = feedbackDescription;
        this.deviceDescription = deviceDescription;
    }

    //
    // Override Object methods
    //

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        if (BuildConfig.ENABLE_DUMP) {
            super.appendTo(stringBuilder);
            stringBuilder.append(" email=");
            stringBuilder.append(email);
            stringBuilder.append(" subject=");
            stringBuilder.append(subject);
            stringBuilder.append(" feedbackDescription=");
            stringBuilder.append(feedbackDescription);
            stringBuilder.append(" deviceDescription=");
            stringBuilder.append(deviceDescription);
        }
    }

    @NonNull
    public String toString() {

        if (BuildConfig.ENABLE_DUMP) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("FeedbackIQ\n");
            appendTo(stringBuilder);

            return stringBuilder.toString();
        } else {
            return "";
        }
    }
}
