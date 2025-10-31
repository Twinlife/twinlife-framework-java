/*
 *  Copyright (c) 2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.UUID;

/**
 * Interface used when importing a RepositoryObject from the server or from a previous database version.
 */
public interface RepositoryImportService {

    void importObject(@NonNull RepositoryObject object,
                      @Nullable UUID twincodeFactoryId,
                      @Nullable UUID twincodeInboundId,
                      @Nullable UUID twincodeOutboundId,
                      @Nullable UUID peerTwincodeOutboundId,
                      @Nullable UUID ownerId);
}
