/*
 *  Copyright (c) 2019 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The consumer interface describes the lambda operation that is called by the service to provide the result of a get operation.
 * Some service operations will call the lambda asynchronously.  The status (or error code) of the operation is reported
 * through the onGet lambda.
 */
public interface Consumer<T> {

    void onGet(@NonNull BaseService.ErrorCode status, @Nullable T object);
}
