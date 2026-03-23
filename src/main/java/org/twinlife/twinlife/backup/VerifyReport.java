/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup;

import androidx.annotation.NonNull;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VerifyReport implements Serializable {

    @NonNull
    private final Map<UUID, List<UUID>> added;
    @NonNull
    private final Map<UUID, List<UUID>> deleted;
    @NonNull
    private final Map<UUID, List<UUID>> modified;
    @NonNull
    private final Map<UUID, List<UUID>> upToDate;

    public VerifyReport(@NonNull Map<UUID, List<UUID>> added, @NonNull Map<UUID, List<UUID>> deleted, @NonNull Map<UUID, List<UUID>> modified, @NonNull Map<UUID, List<UUID>> upToDate) {
        this.added = added;
        this.deleted = deleted;
        this.modified = modified;
        this.upToDate = upToDate;
    }

    @NonNull
    List<UUID> getDeletedTwincodeIds() {
        List<UUID> twincodeIds = new ArrayList<>();

        for (List<UUID> twincodeId : deleted.values()) {
            twincodeIds.addAll(twincodeId);
        }

        return twincodeIds;
    }

    @NonNull
    List<UUID> getAddedTwincodeIds() {
        List<UUID> twincodeIds = new ArrayList<>();

        for (List<UUID> twincodeId : added.values()) {
            twincodeIds.addAll(twincodeId);
        }

        return twincodeIds;
    }

    @NonNull
    List<UUID> getModifiedTwincodeIds() {
        List<UUID> twincodeIds = new ArrayList<>();

        for (List<UUID> twincodeId : modified.values()) {
            twincodeIds.addAll(twincodeId);
        }

        return twincodeIds;
    }


    @NonNull
    List<UUID> getUpToDateTwincodeIds() {
        List<UUID> twincodeIds = new ArrayList<>();

        for (List<UUID> twincodeId : upToDate.values()) {
            twincodeIds.addAll(twincodeId);
        }

        return twincodeIds;
    }

    @NonNull
    public RestoreContent.Stats getStats(@NonNull UUID schemaId) {
        List<UUID> addedList = added.get(schemaId);
        List<UUID> deletedList = deleted.get(schemaId);
        List<UUID> modifiedList = modified.get(schemaId);
        List<UUID> utdList = upToDate.get(schemaId);

        int a = addedList != null ? addedList.size() : 0;
        int d = deletedList != null ? deletedList.size() : 0;
        int m = modifiedList != null ? modifiedList.size() : 0;
        int u = utdList != null ? utdList.size() : 0;

        return new RestoreContent.Stats(a, d, m, u);
    }

    @NonNull
    @Override
    public String toString() {
        return "VerifyReport{" +
                "added=" + getAddedTwincodeIds().size() +
                ", deleted=" + getDeletedTwincodeIds().size() +
                ", modified=" + getModifiedTwincodeIds().size() +
                ", upToDate=" + getUpToDateTwincodeIds().size() +
                "}";
    }
}
