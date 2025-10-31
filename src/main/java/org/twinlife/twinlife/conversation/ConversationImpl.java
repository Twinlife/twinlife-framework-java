/*
 *  Copyright (c) 2015-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.ConversationService.Conversation;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.database.DatabaseObjectImpl;

import java.util.UUID;

public class ConversationImpl extends DatabaseObjectImpl implements ConversationService.Conversation {
    private static final String LOG_TAG = "ConversationImpl";
    private static final boolean DEBUG = false;

    static final UUID SCHEMA_ID = UUID.fromString("7589801a-83ba-4ce2-af50-46994088053e");
    static final int SCHEMA_VERSION = 4;

    private static final int FAST_RETRY1_DELAY = 20 * 1000; // 20 sec (must be > 8 sec to get a Firebase wakeup)
    private static final int FAST_RETRY2_DELAY = 30 * 1000; // 30 sec (must be > 8 sec to get a Firebase wakeup)
    private static final int LONG_RETRY_DELAY = 60 * 1000; // 1 min

    /**
     * Backoff table to retry connection to a peer.
     * - retry two times quite quickly,
     * - pause for a longer time that doubles until it reaches 60 mins.
     */
    private static final int[] mBackoffDelays = {
            FAST_RETRY1_DELAY,
            FAST_RETRY2_DELAY,
            4 * LONG_RETRY_DELAY,
            16 * LONG_RETRY_DELAY,
            32 * LONG_RETRY_DELAY,
            60 * LONG_RETRY_DELAY,
            120 * LONG_RETRY_DELAY
    };

    @NonNull
    private final UUID mId;
    @NonNull
    private final RepositoryObject mContact;
    private final long mCreationDate;
    @NonNull
    private final UUID mTwincodeOutboundId;

    @NonNull
    private final UUID mResourceId;
    private long mLastRetryDate;
    private int mFlags;
    @Nullable
    private volatile UUID mPeerResourceId;
    private volatile boolean mIsActive;
    private volatile long mDelay;
    private int mDelayPos;
    private boolean mNeedSynchronize;

    private volatile long mLastConnectDate;
    @Nullable
    private ConversationConnection mConnection;

    /**
     * A bitmap of permissions granted to the user on this conversation.
     */
    protected long mPermissions;

    ConversationImpl(@NonNull DatabaseIdentifier identifier, @NonNull UUID conversationId, @NonNull RepositoryObject contact,
                     long creationDate, @NonNull UUID resourceId, @Nullable UUID peerResourceId,
                     long permissions, long lastConnectDate, long lastRetryDate, int flags) {
        super(identifier);

        mId = conversationId;
        mContact = contact;
        mCreationDate = creationDate;
        mResourceId = resourceId;
        mPeerResourceId = peerResourceId;
        mPermissions = permissions;
        mLastConnectDate = lastConnectDate;
        mLastRetryDate = lastRetryDate;
        mFlags = flags;

        final TwincodeOutbound twincodeOutbound = mContact.getTwincodeOutbound();
        if (twincodeOutbound != null) {
            mTwincodeOutboundId = twincodeOutbound.getId();
        } else {
            mTwincodeOutboundId = Twincode.NOT_DEFINED;
        }

        mDelay = 0;
        mNeedSynchronize = false;
        mPermissions = permissions;
    }

    synchronized void update(@Nullable UUID peerResourceId, long permissions, long lastConnectDate, long lastRetryDate, int flags) {

        mPeerResourceId = peerResourceId;
        mPermissions = permissions;
        mLastConnectDate = lastConnectDate;
        mLastRetryDate = lastRetryDate;
        mFlags = flags;
    }

    //
    // Implements Conversation methods
    //

    @Override
    @NonNull
    public UUID getId() {

        return mId;
    }

    @Override
    @NonNull
    public UUID getTwincodeOutboundId() {

        return mTwincodeOutboundId;
    }

    @Nullable
    @Override
    public TwincodeOutbound getTwincodeOutbound() {

        return mContact.getTwincodeOutbound();
    }

    @Override
    @NonNull
    public UUID getPeerTwincodeOutboundId() {

        final TwincodeOutbound peerTwincodeOutbound = getPeerTwincodeOutbound();
        return peerTwincodeOutbound == null ? Twincode.NOT_DEFINED : peerTwincodeOutbound.getId();
    }

    @Nullable
    @Override
    public TwincodeOutbound getPeerTwincodeOutbound() {

        return mContact.getPeerTwincodeOutbound();
    }

    @Override
    public boolean hasPeer() {

        return mContact.canCreateP2P();
    }

    @Override
    @NonNull
    public UUID getTwincodeInboundId() {

        final TwincodeInbound twincodeInbound = mContact.getTwincodeInbound();
        return twincodeInbound == null ? Twincode.NOT_DEFINED : twincodeInbound.getId();
    }

    @Override
    @Nullable
    public TwincodeInbound getTwincodeInbound() {

        return mContact.getTwincodeInbound();
    }

    @Override
    @NonNull
    public UUID getContactId() {

        return mContact.getId();
    }

    @Override
    @NonNull
    public RepositoryObject getSubject() {

        return mContact;
    }

    @Override
    @Nullable
    public UUID getPeerConnectionId() {

        return mConnection == null ? null : mConnection.getPeerConnectionId();
    }

    @Override
    public boolean isActive() {

        return mIsActive;
    }

    @Override
    public boolean isGroup() {

        // If the conversation is linked to a group, this is a group conversation.
        return false;
    }

    @Override
    public boolean isConversation(UUID id) {

        return mId.equals(id);
    }

    @Override
    public boolean hasPermission(ConversationService.Permission p) {

        return (p != null) && (mPermissions & (1L << p.ordinal())) != 0;
    }

    //
    // Package specific Methods
    //

    @NonNull
    Conversation getMainConversation() {

        return this;
    }

    @Nullable
    GroupConversationImpl getGroup() {

        return null;
    }

    void setPermissions(long permissions) {

        mPermissions = permissions;
    }

    int getFlags() {

        return mFlags;
    }

    @Nullable
    UUID getPeerResourceId() {

        return mPeerResourceId;
    }

    void setPeerResourceId(@Nullable UUID resourceId) {

        mPeerResourceId = resourceId;
    }

    @NonNull
    UUID getResourceId() {

        return mResourceId;
    }

    void setIsActive(boolean isActive) {

        mIsActive = isActive;
    }

    long getPermissions() {

        return mPermissions;
    }

    void touch() {

        if (mConnection != null) {
            mConnection.touch();
        }
    }

    @NonNull
    ConversationConnection.State getState() {

        if (mConnection == null) {
            return ConversationConnection.State.CLOSED;
        } else {
            return mConnection.getState();
        }
    }

    @Nullable
    ConversationConnection getConnection() {

        return mConnection;
    }

    @Nullable
    ConversationConnection canAcceptIncoming(@NonNull TwinlifeImpl twinlifeImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "canAcceptIncoming");
        }

        // If one of the IN/OUT connection is opened, we are busy.
        if (mConnection != null) {
            final Boolean state = mConnection.canAcceptIncoming();

            if (state != null) {
                return state ? mConnection : null;
            }

            final TwincodeOutbound twincodeOutbound = mContact.getTwincodeOutbound();
            if (twincodeOutbound == null) {
                return null;
            }

            if (twincodeOutbound.getId().compareTo(getPeerTwincodeOutboundId()) > 0) {
                return null;
            }
        }

        // Incoming P2P is accepted, create the connection instance.
        mConnection = new ConversationConnection(twinlifeImpl, this, true);
        return mConnection;
    }

    @Nullable
    ConversationConnection startOutgoing(@NonNull TwinlifeImpl twinlife) {
        if (DEBUG) {
            Log.d(LOG_TAG, "canStartOutgoing");
        }

        if (mConnection != null) {
            if (mConnection.canStartOutgoing()) {
                return mConnection;
            } else {
                return null;
            }
        }

        // Outgoing P2P is accepted, create the connection instance.
        mConnection = new ConversationConnection(twinlife, this, false);
        return mConnection;
    }

    @NonNull
    ConversationConnection transferConnection(@NonNull ConversationConnection oldConnection) {

        final ConversationImpl oldConversationImpl = oldConnection.getConversation();

        mConnection = oldConnection.transferPeerConnection(this);
        oldConversationImpl.mConnection = null;
        return mConnection;
    }

    void closeConnection() {
        if (DEBUG) {
            Log.d(LOG_TAG, "closeConnection");
        }

        mConnection = null;
    }

    long getDelay() {

        return mDelay;
    }

    void resetDelay() {

        mDelay = 0;
        mDelayPos = 0;
    }

    void nextDelay(@NonNull TerminateReason terminateReason) {

        switch (terminateReason) {
            // Long running error, retrying will not help, use the highest retry delay.
            case GONE:
            case REVOKED:
            case NOT_AUTHORIZED:
            case NOT_ENCRYPTED:
            case NO_PRIVATE_KEY:
            case NO_PUBLIC_KEY:
            case NO_SECRET_KEY:
            case ENCRYPT_ERROR:
            case DECRYPT_ERROR:
                mDelayPos = mBackoffDelays.length - 1;
                break;

            // Transient error, we can be more aggressive on the retry.
            case BUSY:
            case DISCONNECTED:
            case SUCCESS:
                mDelayPos = 0;
                break;

            // Connectivity error, use the backoff table.
            case CONNECTIVITY_ERROR:
            case UNKNOWN:
            case GENERAL_ERROR:
            case TIMEOUT:

                // Other errors should not happen.
            case CANCEL:
            case DECLINE:
            default:
                if (mDelayPos + 1 < mBackoffDelays.length) {
                    mDelayPos++;
                }
                break;
        }
        mDelay = mBackoffDelays[mDelayPos];
    }

    void setLastConnectDate(long date) {

        mLastConnectDate = date;
    }

    long getLastConnectDate() {

        return mLastConnectDate;
    }

    long getLastRetryDate() {

        return mLastRetryDate;
    }

    boolean needSynchronize() {

        return mNeedSynchronize;
    }

    void setNeedSynchronize() {

        mNeedSynchronize = true;
    }

    void clearNeedSynchronize() {

        mNeedSynchronize = false;
    }

    String getFrom() {

        return getTwincodeOutboundId() + "/" + mResourceId;
    }

    String getTo() {

        return getPeerTwincodeOutboundId().toString();
    }

    //
    // Override Object methods
    //

    @Override
    @NonNull
    public String toString() {

        StringBuilder stringBuilder = new StringBuilder("ConversationImpl[");
        stringBuilder.append(getDatabaseId()).append(" uuid=").append(mId);
        stringBuilder.append(" contact=").append(mContact);
        stringBuilder.append(" resourceId=").append(mResourceId);
        stringBuilder.append(" peerResourceId=").append(mPeerResourceId);
        stringBuilder.append(" isActive=").append(mIsActive);
        stringBuilder.append("]");
        return stringBuilder.toString();
    }
}
