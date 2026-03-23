/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class BinaryEncoderTest {
    private static final long[] INT_VALUES = {0, 1, 1000, 1000 * 1000, Integer.MAX_VALUE};

    /**
     * Check that longs are encoded as ints when they are <= Integer.MAX_VALUE.
     */
    @Test
    public void longEncodingTest() throws SerializerException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        BinaryEncoder encoder = new BinaryEncoder(outputStream);

        // Write longs <= Integer.MAX_VALUE
        for (long value : INT_VALUES) {
            encoder.writeLong(value);
        }

        // Write longs > Integer.MAX_VALUE twice:
        // First to decode them as longs
        encoder.writeLong(Integer.MAX_VALUE + 1L);
        encoder.writeLong(Long.MAX_VALUE);
        // Then to decode them as ints
        encoder.writeLong(Integer.MAX_VALUE + 1L);
        encoder.writeLong(Long.MAX_VALUE);

        BinaryDecoder decoder = new BinaryDecoder(new ByteArrayInputStream(outputStream.toByteArray()));

        // Read longs <= Integer.MAX_VALUE as ints. They should be decoded properly, as they are effectively stored as ints.
        for (long value : INT_VALUES) {
            assertEquals(value, decoder.readInt());
        }

        // Read longs > Integer.MAX_VALUE as longs. They should be decoded properly.
        assertEquals(Integer.MAX_VALUE + 1L, decoder.readLong());
        assertEquals(Long.MAX_VALUE, decoder.readLong());

        // Read longs > Integer.MAX_VALUE as ints:
        // Integer.MAX_VALUE + 1 should overflow...
        assertEquals(0, decoder.readInt());
        // ...while Long.MAX_VALUE should fail entirely and throw
        assertThrows(SerializerException.class, decoder::readInt);
    }
}
