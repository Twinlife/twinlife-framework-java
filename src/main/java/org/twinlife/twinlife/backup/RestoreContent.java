/*
 *  Copyright (c) 2025-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.backup;

import androidx.annotation.NonNull;

import org.twinlife.twinlife.TwincodeInfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RestoreContent implements Serializable {
    public static class Stats implements Serializable {
        /**
         * Number of twincodes which only exist on the server (created after backup)
         */
        public final int added;

        /**
         * Number of twincodes which only exist on the device (deleted after backup)
         */
        public final int deleted;

        /**
         * Number of twincodes which exist on both the server and the device, and have been modified after backup.
         */
        public final int modified;


        /**
         * Number of twincodes which exist on both the server and the device, and have not been modified after backup.
         */
        public final int upToDate;

        public Stats(int added, int deleted, int modified, int upToDate) {
            this.added = added;
            this.deleted = deleted;
            this.modified = modified;
            this.upToDate = upToDate;
        }

        public boolean isStatsUpToDate() {

            return this.added == 0 && this.deleted == 0 && this.modified == 0;
        }
    }

    @NonNull
    private final Map<UUID, List<TwincodeInfo>> added;
    @NonNull
    private final Map<UUID, List<UUID>> deleted;
    @NonNull
    private final Map<UUID, List<UUID>> modified;
    @NonNull
    private final Map<UUID, List<UUID>> upToDate;

    public RestoreContent(@NonNull Map<UUID, List<TwincodeInfo>> added, @NonNull Map<UUID, List<UUID>> deleted, @NonNull Map<UUID, List<UUID>> modified, @NonNull Map<UUID, List<UUID>> upToDate) {
        this.added = added;
        this.deleted = deleted;
        this.modified = modified;
        this.upToDate = upToDate;
    }

    @NonNull
    List<TwincodeInfo> getAddedTwincodeInfos() {
        List<TwincodeInfo> twincodeInfos = new ArrayList<>();

        for (List<TwincodeInfo> twincodeInfo : added.values()) {
            twincodeInfos.addAll(twincodeInfo);
        }

        return twincodeInfos;
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
    public Stats getStats(@NonNull UUID schemaId) {
        List<TwincodeInfo> addedList = added.get(schemaId);
        List<UUID> deletedList = deleted.get(schemaId);
        List<UUID> modifiedList = modified.get(schemaId);
        List<UUID> utdList = upToDate.get(schemaId);

        int a = addedList != null ? addedList.size() : 0;
        int d = deletedList != null ? deletedList.size() : 0;
        int m = modifiedList != null ? modifiedList.size() : 0;
        int u = utdList != null ? utdList.size() : 0;

        return new Stats(a, d, m, u);
    }

    @NonNull
    @Override
    public String toString() {
        return "VerifyReport{" +
                "added=" + getAddedTwincodeInfos().size() +
                ", deleted=" + getDeletedTwincodeIds().size() +
                ", modified=" + getModifiedTwincodeIds().size() +
                ", upToDate=" + getUpToDateTwincodeIds().size() +
                "}";
    }
}
