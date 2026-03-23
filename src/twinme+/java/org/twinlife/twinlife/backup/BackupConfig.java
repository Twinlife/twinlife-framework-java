/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup;

interface BackupConfig {
    /**
     * Magic bytes identifying a twinme+ backup file ("TPBK")
     */
    byte[] FILE_SIGNATURE = new byte[] {0x54, 0x50, 0x42, 0x4b};
}