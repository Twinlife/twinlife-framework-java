/*
 *  Copyright (c) 2013-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributor: Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *  Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.notification;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.Notification;
import org.twinlife.twinlife.NotificationService;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.database.DatabaseObjectImpl;

import java.util.UUID;

public class NotificationImpl extends DatabaseObjectImpl implements Notification {
    private static final String LOG_TAG = "NotificationImpl";
    private static final boolean DEBUG = false;

    private final int mSysId;
    @NonNull
    private final UUID mNotificationId;
    private final long mCreationDate;
    @NonNull
    private final RepositoryObject mSubject;
    @NonNull
    private final NotificationService.NotificationType mNotificationType;
    @Nullable
    private final DescriptorId mDescriptorId;
    private final ConversationService.AnnotationType mAnnotationType;
    private final int mAnnotationValue;
    private final TwincodeOutbound mUserTwincode;
    private int mFlags;

    NotificationImpl(@NonNull DatabaseIdentifier id, int sysId, @NonNull UUID notificationId, long creationDate,
                     @NonNull RepositoryObject subject, @NonNull NotificationService.NotificationType type,
                     @Nullable DescriptorId descriptorId, int flags,
                     @Nullable TwincodeOutbound userTwincode, @Nullable ConversationService.AnnotationType annotationType,
                     int annotationValue) {
        super(id);
        if (DEBUG) {
            Log.d(LOG_TAG, "NotificationImpl: id=" + id + " id=" + notificationId);
        }

        mSysId = sysId;
        mNotificationId = notificationId;
        mSubject = subject;
        mCreationDate = creationDate;
        mNotificationType = type;
        mDescriptorId = descriptorId;
        mFlags = flags;
        mAnnotationType = annotationType;
        mAnnotationValue = annotationValue;
        mUserTwincode = userTwincode;
    }

    //
    // Package specific Methods
    //

    @Override
    @NonNull
    public UUID getId() {

        return mNotificationId;
    }

    @Override
    @NonNull
    public NotificationService.NotificationType getNotificationType() {

        return mNotificationType;
    }

    @Override
    @NonNull
    public UUID getOriginatorId() {

        return mSubject.getId();
    }

    @Override
    @NonNull
    public RepositoryObject getSubject() {

        return mSubject;
    }

    @Override
    public long getTimestamp() {

        return mCreationDate;
    }

    @Override
    public boolean isAcknowledged() {

        return mFlags != 0;
    }

    @Override
    @Nullable
    public DescriptorId getDescriptorId() {

        return mDescriptorId;
    }

    @Override
    public int getSystemNotificationId() {

        return mSysId;
    }

    @Nullable
    public TwincodeOutbound getUser() {

        return mUserTwincode;
    }

    @Nullable
    public ConversationService.AnnotationType getAnnotationType() {

        return mAnnotationType;
    }

    public int getAnnotationValue() {

        return mAnnotationValue;
    }

    void acknowledge() {

        mFlags = 1;
    }

    int getFlags() {

        return mFlags;
    }

    @Override
    @NonNull
    public String toString() {

        return "Notification[" +
                getDatabaseId() +
                " id=" +
                mNotificationId +
                " creationDate=" +
                mCreationDate +
                " subject=" +
                mSubject +
                "]";
    }
}
