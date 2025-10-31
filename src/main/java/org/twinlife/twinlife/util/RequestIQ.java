/*
 *  Copyright (c) 2015-2017 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributor: Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

/*
 * <pre>
 *
 * Schema version 1
 *
 * {
 *  "type":"record",
 *  "name":"RequestIQ",
 *  "namespace":"org.twinlife.schemas",
 *  "super":"org.twinlife.schemas.IQ"
 *  "fields":
 *  []
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.Decoder;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.SerializerFactory;

import java.util.UUID;

public class RequestIQ extends IQ {

    public static class RequestIQSerializer extends IQSerializer {

        RequestIQSerializer(UUID schemaId, int schemaVersion, Class<?> clazz) {

            super(schemaId, schemaVersion, clazz);
        }

        @Override
        @NonNull
        public Object deserialize(@NonNull SerializerFactory serializerFactory, @NonNull Decoder decoder) throws SerializerException {

            IQ iq = (IQ) super.deserialize(serializerFactory, decoder);

            return new RequestIQ(iq);
        }
    }

    RequestIQ(String from, String to) {

        super(from, to, IQ.Type.SET);
    }

    RequestIQ(IQ iq) {

        super(iq);
    }

    //
    // Override Object methods
    //

    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("RequestIQ:\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }
}
