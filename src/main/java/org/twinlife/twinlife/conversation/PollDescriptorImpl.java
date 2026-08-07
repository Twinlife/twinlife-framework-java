/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

class PollDescriptorImpl extends DescriptorImpl implements ConversationService.PollDescriptor {
    private static final String LOG_TAG = "PollDescriptorImpl";
    private static final boolean DEBUG = false;

    public PollDescriptorImpl(@NonNull DescriptorId descriptorId, long cid,
                              long creationDate, long sendDate, long receiveDate, long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                              int flags, @NonNull String content) {

        super(descriptorId, cid, null, null, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout);

        if (DEBUG) {
            Log.d(LOG_TAG, "PollDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " creationDate=" + creationDate + " sendDate=" + sendDate + " receiveDate=" + receiveDate + " readDate=" + readDate + " updateDate=" + updateDate + " peerDeleteDate=" + peerDeleteDate + " deleteDate=" + deleteDate + " expireTimeout=" + expireTimeout + " flags=" + flags + " content=" + content);
        }

        mMultipleChoicesAllowed = (flags & FLAG_MULTIPLE_CHOICES) != 0;
        mCopyAllowed = (flags & FLAG_COPY_ALLOWED) != 0;

        String[] args = extract(content);

        mQuestion = extractString(args, 0, "");
        int nbChoices = (int) extractLong(args, 1, 0);
        mChoices = new ArrayList<>(nbChoices);

        int currentIndex = 2;

        for (int i = 0; i < nbChoices; i++) {
            //extract() can't return null with a non-null content.
            //noinspection DataFlowIssue
            if (currentIndex + 1 >= args.length) {
                Log.e(LOG_TAG, "Truncated/malformed poll choices in content string: "+content);
                break;
            }
            int position = (int) extractLong(args, currentIndex, 0);
            String label = extractString(args, currentIndex + 1, "");
            mChoices.add(new Choice(position, label));
            currentIndex += 2;
        }

        Collections.sort(mChoices);
    }

    private final boolean mMultipleChoicesAllowed;
    private boolean mCopyAllowed;
    @NonNull
    private final String mQuestion;
    @NonNull
    private final List<Choice> mChoices;

    PollDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                       long createdTimestamp, long sentTimestamp, boolean multipleChoicesAllowed, @NonNull String question, @NonNull List<Choice> choices, boolean copyAllowed) {

        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, null, createdTimestamp, sentTimestamp);

        if (DEBUG) {
            Log.d(LOG_TAG, "PollDescriptorImpl: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId + " expireTimeout=" + expireTimeout + " sendTo=" + sendTo + " createdTimestamp=" + createdTimestamp + " sentTimestamp=" + sentTimestamp + " multipleChoicesAllowed=" + multipleChoicesAllowed + " question=" + question + " choices=" + choices + " copyAllowed=" + copyAllowed);
        }

        mMultipleChoicesAllowed = multipleChoicesAllowed;
        mQuestion = question;
        mChoices = new ArrayList<>(choices);
        Collections.sort(mChoices);
        mCopyAllowed = copyAllowed;
    }

    PollDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, long expireTimeout, boolean multipleChoicesAllowed, @NonNull String question, @NonNull List<Choice> choices, boolean copyAllowed) {

        super(descriptorId, cid, expireTimeout, null, null);

        if (DEBUG) {
            Log.d(LOG_TAG, "PollDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " expireTimeout=" + expireTimeout + " multipleChoicesAllowed=" + multipleChoicesAllowed + " question=" + question + " choices=" + choices + " copyAllowed=" + copyAllowed);
        }

        mMultipleChoicesAllowed = multipleChoicesAllowed;
        mQuestion = question;
        mChoices = new ArrayList<>(choices);
        Collections.sort(mChoices);
        mCopyAllowed = copyAllowed;
    }

    @NonNull
    public String getQuestion() {
        return mQuestion;
    }

    @NonNull
    public List<Choice> getChoices() {
        return mChoices;
    }

    public boolean isMultipleChoicesAllowed() {
        return mMultipleChoicesAllowed;
    }

    public boolean isCopyAllowed() {
        return mCopyAllowed;
    }

    @NonNull
    @Override
    public Map<UUID, List<Choice>> getVotes() {

        Map<UUID, List<Choice>> votes = new HashMap<>();

        for (Map.Entry<UUID, List<ConversationService.DescriptorAnnotation>> entry : getAnnotations().entrySet()) {
            UUID twincodeOutboundId = entry.getKey();
            for (ConversationService.DescriptorAnnotation annotation : entry.getValue()) {
                if (annotation.getType() == ConversationService.AnnotationType.POLL) {
                    votes.put(twincodeOutboundId, Choice.fromAnnotationValue(annotation.getValue(), mChoices));
                    break;
                }
            }
        }

        return votes;
    }

    /*
     * Override Descriptor methods
     */

    @NonNull
    @Override
    public Type getType() {

        return Type.POLL_DESCRIPTOR;
    }

    @Override
    int getFlags() {
        int flags = mCopyAllowed ? FLAG_COPY_ALLOWED : 0;
        flags |= mMultipleChoicesAllowed ? FLAG_MULTIPLE_CHOICES : 0;
        return flags;
    }

    @Override
    @Nullable
    String serialize() {
        StringBuilder result = new StringBuilder(mQuestion + FIELD_SEPARATOR + mChoices.size());

        for (Choice choice : mChoices) {
            result.append(FIELD_SEPARATOR)
                    .append(choice.position)
                    .append(FIELD_SEPARATOR)
                    .append(choice.label);
        }

        return result.toString();
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" mMultipleChoicesAllowed=");
        stringBuilder.append(mMultipleChoicesAllowed);
        stringBuilder.append(" mQuestion=");
        stringBuilder.append(mQuestion);
        stringBuilder.append(" mChoices=");
        stringBuilder.append(Arrays.toString(mChoices.toArray()));
        stringBuilder.append(" mCopyAllowed=");
        stringBuilder.append(mCopyAllowed);
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("PollDescriptorImpl\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }
}
