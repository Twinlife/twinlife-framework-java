/*
 *  Copyright (c) 2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

/*
 * <pre>
 *
 * Schema version 1
 *  Date: 2026/07/01
 *
 * {
 *  "schemaId":"261f3bc9-8301-4b4e-914c-ca4b7b89a0f9",
 *  "schemaVersion":"1",
 *
 *  "type":"record",
 *  "name":"ContactShareDescriptor",
 *  "namespace":"org.twinlife.schemas.conversation",
 *  "super":"org.twinlife.schemas.conversation.Descriptor"
 *  "fields":
 *  [
 *   {"name":"name", "type":"String"},
 *   {"name":"avatar", "type":"bytes"},
 *   {"name":"status", "type":"enum"}
 *  ]
 * }
 *
 * </pre>
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.InvitationDescriptor;
import org.twinlife.twinlife.CryptoService;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.UUID;

class ContactShareDescriptorImpl extends DescriptorImpl implements ConversationService.ContactShareDescriptor {
    private static final String LOG_TAG = "ContactShareDescriptorImpl";
    private static final boolean DEBUG = false;

    @NonNull
    private final String mName;

    @NonNull
    private InvitationDescriptor.Status mStatus;

    private boolean mAutoAnswer;

    @Nullable
    private UUID mInvitationTwincodeOutboundId;

    @Nullable
    private CryptoService.PublicKeyData mInvitationTwincodeOutboundPubkey;

    @Nullable
    private final UUID mTargetContactId;

    ContactShareDescriptorImpl(@NonNull DescriptorId descriptorId, long cid,
                               long creationDate, long sendDate, long receiveDate, long readDate, long updateDate, long peerDeleteDate, long deleteDate, long expireTimeout,
                               int flags, @NonNull String content, long value) {

        super(descriptorId, cid, null, null, creationDate, sendDate, receiveDate, readDate,
                updateDate, peerDeleteDate, deleteDate, expireTimeout);

        if (DEBUG) {
            Logger.debug(LOG_TAG, "ContactShareDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " creationDate=" + creationDate + " sendDate=" + sendDate + " receiveDate=" + receiveDate + " readDate=" + readDate + " updateDate=" + updateDate + " peerDeleteDate=" + peerDeleteDate + " deleteDate=" + deleteDate + " expireTimeout=" + expireTimeout + " flags=" + flags + " content=" + content);
        }

        String[] args = extract(content);

        mName = extractString(args, 0, "");
        String invitationTwincodeOutboundIdStr = extractString(args, 1, null);
        if (invitationTwincodeOutboundIdStr != null && !invitationTwincodeOutboundIdStr.isEmpty()) {
            mInvitationTwincodeOutboundId = Utils.UUIDFromString(invitationTwincodeOutboundIdStr);
        }
        String targetContactIdStr = extractString(args, 2, null);
        mTargetContactId = Utils.UUIDFromString(targetContactIdStr);

        String pubkey = extractString(args, 3, null);
        if (pubkey != null && !pubkey.isEmpty()) {
            mInvitationTwincodeOutboundPubkey = CryptoService.PublicKeyData.create(pubkey);
        }

        mAutoAnswer = extractLong(args, 4, 0L) == 1L;

        mStatus = InvitationDescriptor.Status.toStatus((int) value);
    }

    ContactShareDescriptorImpl(@NonNull UUID twincodeOutboundId, long sequenceId, long expireTimeout, @Nullable UUID sendTo,
                               @Nullable DescriptorId replyTo, long createdTimestamp, long sentTimestamp, @NonNull String name, @NonNull InvitationDescriptor.Status status) {
        super(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, createdTimestamp, sentTimestamp);

        mName = name;
        mStatus = status;
        mTargetContactId = null;
        mInvitationTwincodeOutboundId = null;
        mInvitationTwincodeOutboundPubkey = null;
        mAutoAnswer = false;
    }

    ContactShareDescriptorImpl(@NonNull DescriptorId descriptorId, long cid, long expireTimeout, @NonNull String name, @NonNull UUID targetContactId) {

        super(descriptorId, cid, expireTimeout, null, null);

        if (DEBUG) {
            Logger.debug(LOG_TAG, "ContactShareDescriptorImpl: descriptorId=" + descriptorId + " cid=" + cid + " expireTimeout=" + expireTimeout + " name=" + name + " targetContactId=" + targetContactId);
        }

        mName = name;
        mStatus = InvitationDescriptor.Status.PENDING;
        mTargetContactId = targetContactId;
        mInvitationTwincodeOutboundId = null;
        mInvitationTwincodeOutboundPubkey = null;
        mAutoAnswer = false;
    }


    /*
     * Override Descriptor methods
     */

    @NonNull
    @Override
    public Type getType() {

        return Type.CONTACT_SHARE_DESCRIPTOR;
    }

    @Override
    @NonNull
    public String getName() {

        return mName;
    }

    @Override
    @NonNull
    public InvitationDescriptor.Status getStatus() {

        return mStatus;
    }

    public void setStatus(@NonNull InvitationDescriptor.Status status) {

        mStatus = status;
    }

    public boolean isAutoAnswer() {
        return mAutoAnswer;
    }

    public void setAutoAnswer(boolean autoAnswer) {
        this.mAutoAnswer = autoAnswer;
    }

    public void setInvitationTwincodeOutboundId(@Nullable UUID invitationTwincodeOutboundId) {
        mInvitationTwincodeOutboundId = invitationTwincodeOutboundId;
    }

    @Override
    @Nullable
    public UUID getInvitationTwincodeOutboundId() {
        return mInvitationTwincodeOutboundId;
    }


    public void setInvitationTwincodeOutboundPubkey(@Nullable CryptoService.PublicKeyData invitationTwincodeOutboundPubkey) {
        mInvitationTwincodeOutboundPubkey = invitationTwincodeOutboundPubkey;
    }

    @Override
    @Nullable
    public CryptoService.PublicKeyData getInvitationTwincodeOutboundPubkey() {
        return mInvitationTwincodeOutboundPubkey;
    }

    @Override
    @Nullable
    public UUID getTargetContactId() {
        return mTargetContactId;
    }

    @Override
    @Nullable
    String serialize() {

        return mName +
                FIELD_SEPARATOR +
                (mInvitationTwincodeOutboundId != null ? mInvitationTwincodeOutboundId : "") +
                FIELD_SEPARATOR +
                (mTargetContactId != null ? mTargetContactId : "") +
                FIELD_SEPARATOR +
                (mInvitationTwincodeOutboundPubkey != null ? mInvitationTwincodeOutboundPubkey.asString() : "") +
                FIELD_SEPARATOR +
                (mAutoAnswer ? "1" : "0");
    }

    @Override
    long getValue() {

        return mStatus.toInt();
    }

    @Override
    @Nullable
    public byte[] loadAvatarData(@Nullable File filesDir) {

        File thumbnail = getAvatarFile(filesDir);
        if (!thumbnail.exists()) {

            return null;
        }

        try (FileInputStream input = new FileInputStream(thumbnail)) {
            byte[] data = new byte[(int) thumbnail.length()];
            int read = input.read(data);

            if (read != thumbnail.length()) {
                return null;
            }

            return data;

        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.exception(LOG_TAG, exception, "Could not read avatar " + thumbnail.getAbsolutePath(), exception.getMessage());
            }
            return null;
        }
    }

    @Override
    void delete(@Nullable File filesDir) {
        if (DEBUG) {
            Logger.debug(LOG_TAG, "delete: filesDir=" + filesDir);
        }

        File avatar = getAvatarFile(filesDir);

        Utils.deleteFile(LOG_TAG, avatar);
    }

    void saveAvatarData(@Nullable File filesDir, @Nullable byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        File avatarFile = getAvatarFile(filesDir);

        Utils.deleteFile(LOG_TAG, avatarFile);

        if (avatarFile.exists()) {
            return;
        }

        File conversationDir = avatarFile.getParentFile();

        if (conversationDir != null && !conversationDir.exists() && !conversationDir.mkdirs()) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Could not create conversation dir " + conversationDir.getAbsolutePath());
            }
            return;
        }

        try (FileOutputStream output = new FileOutputStream(avatarFile, false)) {
            output.write(data);
        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.exception(LOG_TAG, exception, "Could not write avatar " + avatarFile.getAbsolutePath(), exception.getMessage());
            }
        }
    }

    protected void appendTo(@NonNull StringBuilder stringBuilder) {

        super.appendTo(stringBuilder);

        stringBuilder.append(" name=");
        stringBuilder.append(mName);
        stringBuilder.append(" status=");
        stringBuilder.append(mStatus);
        stringBuilder.append(" autoAnswer=");
        stringBuilder.append(mAutoAnswer);
        stringBuilder.append(" invitationTwincodeOutboundId=");
        stringBuilder.append(mInvitationTwincodeOutboundId);
        stringBuilder.append(" targetContactId=");
        stringBuilder.append(mTargetContactId);
        stringBuilder.append("\n");
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("ContactShareDescriptorImpl\n");
        appendTo(stringBuilder);

        return stringBuilder.toString();
    }

    @NonNull
    private File getAvatarFile(@Nullable File filesDir) {

        String subPath = Twinlife.CONVERSATIONS_DIR +
                '/' +
                getTwincodeOutboundId() +
                '/' +
                getSequenceId();

        return new File(filesDir, subPath);
    }
}
