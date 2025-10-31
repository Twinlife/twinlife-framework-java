/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.util;

import androidx.annotation.NonNull;

import java.io.Serializable;

public class Version implements Comparable<Version>, Serializable {
    public final int major;
    public final int minor;
    public final int patch;

    public Version(int major, int minor) {
        this.major = major;
        this.minor = minor;
        this.patch = 0;
    }

    /**
     * Create a version object splitting the string into major, minor and patch components.
     *
     * @param version the version in the form <major>.<minor>.<patch>
     */
    public Version(@NonNull String version) {

        final String[] numbers = version.trim().split("\\.");
        if (numbers.length > 0) {
            major = toInteger(numbers[0]);
            if (numbers.length > 1) {
                minor = toInteger(numbers[1]);
                if (numbers.length > 2) {
                    patch = toInteger(numbers[2]);
                } else {
                    patch = 0;
                }
            } else {
                minor = 0;
                patch = 0;
            }
        } else {
            major = 0;
            minor = 0;
            patch = 0;
        }
    }

    /**
     * Compare two versions.
     *
     * @param   second the object to be compared.
     * @return  a negative integer, zero, or a positive integer as this object
     *          is less than, equal to, or greater than the specified object.
     */
    public int compareTo(@NonNull Version second) {

        int result = major - second.major;
        if (result != 0) {
            return result;
        }

        result = minor - second.minor;
        if (result != 0) {
            return result;
        }

        return patch - second.patch;
    }

    @NonNull
    public String toString() {

        return major + "." + minor + "." + patch;
    }

    /**
     * Convert the string to an integer handling errors.
     *
     * @param value the value to convert.
     * @return the integer value or 0.
     */
    static int toInteger(@NonNull String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }
}