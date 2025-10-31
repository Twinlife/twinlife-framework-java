/*
 *  Copyright (c) 2020 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.accountMigration;

/**
 * File state information as transmitted by the peer.
 *
 * This indicates for a given file id, the current known size of that file on the peer.
 */
class FileState {

    final int mFileId;
    final long mOffset;

    FileState(int fileId, long offset) {

        this.mFileId = fileId;
        this.mOffset = offset;
    }
}
