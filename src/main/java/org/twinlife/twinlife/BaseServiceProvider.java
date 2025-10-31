/*
 *  Copyright (c) 2013-2017 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

@SuppressWarnings("unused")
public interface BaseServiceProvider {

    void onCreate(@NonNull Database db);

    void onUpgrade(@NonNull Database db, int oldVersion, int newVersion) throws DatabaseException;

    void onOpen(@NonNull Database db);
}
