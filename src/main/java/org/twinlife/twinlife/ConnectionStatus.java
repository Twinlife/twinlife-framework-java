/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife;

/**
 * State of the connection with the server.
 */
public enum ConnectionStatus {
    // Not connected because there is no Internet connection.
    NO_INTERNET,

    // Internet connection detected but we timed out in connecting to the server.
    NO_SERVICE,

    // Currently trying to connect.
    CONNECTING,

    // Connected to the server.
    CONNECTED
}
