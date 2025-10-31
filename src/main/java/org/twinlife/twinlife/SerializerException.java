/*
 *  Copyright (c) 2017-2018 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

public class SerializerException extends Exception {

    public SerializerException() {

        super();
    }

    public SerializerException(Exception exception) {

        super(exception);
    }

    public SerializerException(String message) {

        super(message);
    }
}
