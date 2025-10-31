/*
 *  Copyright (c) 2023-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.database;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Simple helper to build the SQL query and take into account the user's Filter.
 *
 */
public class QueryBuilder {

    private final StringBuilder mQuery;
    private final List<String> mParams;
    private boolean mHasWhere;

    public QueryBuilder(@NonNull String fields) {
        mQuery = new StringBuilder("SELECT ");
        mParams = new ArrayList<>();
        mQuery.append(fields);
        mHasWhere = false;
    }

    @NonNull
    public String getQuery() {
        return mQuery.toString();
    }

    @NonNull
    public String[] getParams() {
        return mParams.toArray(new String[0]);
    }

    public void append(@NonNull String sql) {
        mQuery.append(sql);
    }

    public void where(@NonNull String filter) {
        inWhere();
        mQuery.append(filter);
    }

    private void inWhere() {
        if (mHasWhere) {
            mQuery.append(" AND ");
        } else {
            mQuery.append(" WHERE ");
            mHasWhere = true;
        }
    }

    public void filterBefore(@NonNull String field, @Nullable Long timestamp) {
        if (timestamp != null) {
            inWhere();
            mQuery.append(field).append("<?");
            mParams.add(Long.toString(timestamp));
        }
    }

    public void filterOwner(@NonNull String field, @Nullable RepositoryObject owner) {
        if (owner != null) {
            inWhere();
            mQuery.append(field).append("=?");
            mParams.add(Long.toString(owner.getDatabaseId().getId()));
        }
    }

    public void filterName(@NonNull String field, @Nullable String name) {
        if (name != null) {
            inWhere();
            mQuery.append(field).append(" LIKE ?");

            // If the text to search contains a '%' we have to escape it (using '%' as escape failed for me).
            // Use the '^' as the escape character but if it occurs, we must also escape it.
            if (name.indexOf('%') >= 0) {
                name = name.replaceAll("\\^", "^^").replaceAll("%", "^%");
                mQuery.append(" ESCAPE '^'");
            }

            // Enclose the search text with the '%' pattern (similar to .* in regex).
            mParams.add("%" + name + "%");
        }
    }

    public void filterTwincode(@NonNull String field, @Nullable TwincodeOutbound twincodeOutbound) {
        if (twincodeOutbound != null) {
            inWhere();
            mQuery.append(field).append("=?");
            mParams.add(Long.toString(twincodeOutbound.getDatabaseId().getId()));
        }
    }

    public void filterUUID(@NonNull String field, @Nullable UUID uuid) {
        if (uuid != null) {
            inWhere();
            mQuery.append(field).append("=?");
            mParams.add(uuid.toString());
        }
    }

    public void filterInt(@NonNull String field, @Nullable Integer value) {
        if (value != null) {
            inWhere();
            mQuery.append(field).append("=?");
            mParams.add(Integer.toString(value));
        }
    }

    public void filterLong(@NonNull String field, @Nullable Long value) {
        if (value != null) {
            inWhere();
            mQuery.append(field).append("=?");
            mParams.add(Long.toString(value));
        }
    }

    public void filterNotLong(@NonNull String field, @Nullable Long value) {
        if (value != null) {
            inWhere();
            mQuery.append(field).append("!=?");
            mParams.add(Long.toString(value));
        }
    }

    public void filterIn(@NonNull String field, @NonNull Collection<?> list) {
        inWhere();
        mQuery.append(field).append(" IN (");
        boolean needSep = false;
        for (Object value : list) {
            if (needSep) {
                mQuery.append(", ");
            }
            needSep = true;
            mQuery.append("?");
            mParams.add(value.toString());
        }
        mQuery.append(")");
    }

    public void groupBy(@NonNull String field) {
        mQuery.append(" GROUP BY ").append(field);
    }

    public void order(@NonNull String order) {
        mQuery.append(" ORDER BY ").append(order);
    }

    public void limit(long count) {
        mQuery.append(" LIMIT ?");
        mParams.add(Long.toString(count));
    }

    public void filter(@NonNull String filter, @NonNull String param) {
        mQuery.append(filter);
        mParams.add(param);
    }
}