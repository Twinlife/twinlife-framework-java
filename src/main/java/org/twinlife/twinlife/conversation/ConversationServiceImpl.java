/*
 *  Copyright (c) 2015-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Houssem Temanni (Houssem.Temanni@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 *   Romain Kolb (romain.kolb@skyrock.com)
 */

package org.twinlife.twinlife.conversation;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.exifinterface.media.ExifInterface;

import org.twinlife.twinlife.AssertPoint;
import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.BaseServiceImpl;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.ConversationService;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.DisplayCallsMode;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.ImageTools;
import org.twinlife.twinlife.PeerConnectionService;
import org.twinlife.twinlife.PeerConnectionService.SdpEncryptionStatus;
import org.twinlife.twinlife.PeerConnectionService.StatType;
import org.twinlife.twinlife.PeerConnectionService.DataChannelConfiguration;
import org.twinlife.twinlife.PushNotificationContent;
import org.twinlife.twinlife.Offer;
import org.twinlife.twinlife.OfferToReceive;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TerminateReason;
import org.twinlife.twinlife.Serializer;
import org.twinlife.twinlife.SerializerException;
import org.twinlife.twinlife.TrustMethod;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwincodeInboundService;
import org.twinlife.twinlife.TwincodeInvocation;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.TwincodeOutboundService;
import org.twinlife.twinlife.Twinlife;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.conversation.ConversationConnection.State;
import org.twinlife.twinlife.conversation.ConversationServiceIQ.LeaveGroupIQ;
import org.twinlife.twinlife.conversation.ConversationServiceIQ.OnResultGroupIQ;
import org.twinlife.twinlife.conversation.ConversationServiceIQ.OnResultJoinIQ;
import org.twinlife.twinlife.conversation.ConversationServiceIQ.OnUpdateDescriptorTimestampIQ;
import org.twinlife.twinlife.conversation.ConversationServiceIQ.PushTransientObjectIQ;
import org.twinlife.twinlife.conversation.ConversationServiceIQ.RevokeInviteGroupIQ;
import org.twinlife.twinlife.conversation.ConversationServiceIQ.UpdateDescriptorTimestampIQ;
import org.twinlife.twinlife.conversation.ConversationServiceIQ.UpdateGroupMemberIQ;
import org.twinlife.twinlife.conversation.UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType;
import org.twinlife.twinlife.crypto.CryptoServiceImpl;
import org.twinlife.twinlife.crypto.SignatureInfoIQ;
import org.twinlife.twinlife.util.BinaryCompactDecoder;
import org.twinlife.twinlife.util.BinaryDecoder;
import org.twinlife.twinlife.util.BinaryEncoder;
import org.twinlife.twinlife.util.BinaryPacketIQ;
import org.twinlife.twinlife.util.ByteBufferInputStream;
import org.twinlife.twinlife.util.ErrorIQ;
import org.twinlife.twinlife.util.EventMonitor;
import org.twinlife.twinlife.util.IQ;
import org.twinlife.twinlife.util.Logger;
import org.twinlife.twinlife.util.SchemaKey;
import org.twinlife.twinlife.util.SerializerFactoryImpl;
import org.twinlife.twinlife.util.ServiceErrorIQ;
import org.twinlife.twinlife.util.ServiceRequestIQ;
import org.twinlife.twinlife.util.ServiceResultIQ;
import org.twinlife.twinlife.util.Utils;
import org.webrtc.AudioTrack;
import org.webrtc.MediaStreamTrack;
import org.webrtc.RtpSender;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static org.twinlife.twinlife.conversation.Operation.Type.SYNCHRONIZE_CONVERSATION;

public class ConversationServiceImpl extends BaseServiceImpl<ConversationService.ServiceObserver> implements ConversationService, PeerConnectionService.DataChannelObserver, PeerConnectionService.PeerConnectionObserver {

    private static final String LOG_TAG = "ConversationServiceImpl";
    private static final boolean INFO = false;
    private static final boolean DEBUG = false;

    private static final boolean ENABLE_HARD_RESET = false;

    public static final int MAJOR_VERSION_2 = 2;
    public static final int MAJOR_VERSION_1 = 1;

    static final int MINOR_VERSION_20 = 20; // Added UpdateObjectIQ 2025-05
    static final int MINOR_VERSION_19 = 19; // Added PushThumbnailIQ 2025-01
    static final int MINOR_VERSION_18 = 18; // Auth relations 2024-07
    static final int MINOR_VERSION_17 = 17; // Use new conversation binary for PushCommand, PushTransient, UpdateTimestamp (2024-09)
    // static final int MINOR_VERSION_16 = 16; // Technical update to fix an issue in 2023
    static final int MINOR_VERSION_15 = 15; // Added new reset conversation mode in 2023
    static final int MINOR_VERSION_14 = 14; // Annotation support in 2023
    static final int MINOR_VERSION_13 = 13; // Updated clear/reset conversation and annotation in 2022
    public static final int MINOR_VERSION_12 = 12; // Added new conversation binary IQ in 2021-05-27 (twinme 11.2.9)
    static final int MINOR_VERSION_11 = 11; // Added twinroom push command
    static final int MINOR_VERSION_10 = 10; // Added twincode descriptor in 2019
    static final int MINOR_VERSION_9 = 9;   // Added descriptor expiration in 2019
    static final int MINOR_VERSION_8 = 8;   // Added geolocation support in 2018
    static final int MINOR_VERSION_7 = 7;   // Added group support in 2018
    // static final int MINOR_VERSION_5 = 5;   // Changed descriptor timestamps in 2018
    static final int MINOR_VERSION_0 = 0;

    public static final int MAX_MAJOR_VERSION = MAJOR_VERSION_2;

    // The maximum minor number that is supported by the major version 2.
    public static final int MAX_MINOR_VERSION_2 = MINOR_VERSION_20;
    public static final int MAX_MINOR_VERSION_1 = MINOR_VERSION_0;

    /*
     * <pre>
     *
     * Date: 2018/09/18
     *
     * majorVersion: 2
     * minorVersion: 6
     *
     * ResetConversationIQ:
     *  Schema version 2
     * OnResetConversationIQ
     *  Schema version 2
     * PushObjectIQ
     *  Schema version 3
     * OnPushObjectIQ
     *  Schema version 2
     * PushTransientObjectIQ
     *  Schema version 2
     * OnPushTransientObjectIQ
     *  Schema version 1
     * PushFileIQ
     *  Schema version 5
     * OnPushFileIQ
     *  Schema version 1
     * PushFileChunkIQ
     *  Schema version 1
     * OnPushFileChunkIQ
     *  Schema version 1
     * UpdateDescriptorTimestampIQ
     *  Schema version 1
     * OnUpdateDescriptorTimestampIQ
     *  Schema version 1
     *
     * Date: 2018/09/05
     *
     * majorVersion: 2
     * minorVersion: 5
     *
     * ResetConversationIQ:
     *  Schema version 2
     * OnResetConversationIQ
     *  Schema version 2
     * PushObjectIQ
     *  Schema version 3
     * OnPushObjectIQ
     *  Schema version 2
     * PushTransientObjectIQ
     *  Schema version 2
     * OnPushTransientObjectIQ
     *  Schema version 1
     * PushFileIQ
     *  Schema version 4
     * OnPushFileIQ
     *  Schema version 1
     * PushFileChunkIQ
     *  Schema version 1
     * OnPushFileChunkIQ
     *  Schema version 1
     * UpdateDescriptorTimestampIQ
     *  Schema version 1
     * OnUpdateDescriptorTimestampIQ
     *  Schema version 1
     *
     * Date: 2018/05/29
     *
     * majorVersion: 2
     * minorVersion: 4
     *
     * ResetConversationIQ:
     *  Schema version 2
     * OnResetConversationIQ
     *  Schema version 2
     * PushObjectIQ
     *  Schema version 3
     * OnPushObjectIQ
     *  Schema version 2
     * PushTransientObjectIQ
     *  Schema version 2
     * OnPushTransientObjectIQ
     *  Schema version 1
     * PushFileIQ
     *  Schema version 4
     * OnPushFileIQ
     *  Schema version 1
     * PushFileChunkIQ
     *  Schema version 1
     * OnPushFileChunkIQ
     *  Schema version 1
     * UpdateDescriptorTimestampIQ
     *  Schema version 1
     * OnUpdateDescriptorTimestampIQ
     *  Schema version 1
     *
     *
     * Date: 2017/10/25
     *
     * majorVersion: 2
     * minorVersion: 3
     *
     * ResetConversationIQ:
     *  Schema version 2
     * OnResetConversationIQ
     *  Schema version 2
     * PushObjectIQ
     *  Schema version 3
     * OnPushObjectIQ
     *  Schema version 2
     * PushTransientObjectIQ
     *  Schema version 2
     * OnPushTransientObjectIQ
     *  Schema version 1
     * PushFileIQ
     *  Schema version 3
     * OnPushFileIQ
     *  Schema version 1
     * PushFileChunkIQ
     *  Schema version 1
     * OnPushFileChunkIQ
     *  Schema version 1
     * UpdateDescriptorTimestampIQ
     *  Schema version 1
     * OnUpdateDescriptorTimestampIQ
     *  Schema version 1
     *
     *
     * Date: 2017/01/29
     *
     * majorVersion: 2
     * minorVersion: 2
     *
     * ResetConversationIQ:
     *  Schema version 2
     * OnResetConversationIQ
     *  Schema version 2
     * PushObjectIQ
     *  Schema version 3
     * OnPushObjectIQ
     *  Schema version 2
     * PushTransientObjectIQ
     *  Schema version 2
     * OnPushTransientObjectIQ
     *  Schema version 1
     * PushFileIQ
     *  Schema version 3
     * OnPushFileIQ
     *  Schema version 1
     * PushFileChunkIQ
     *  Schema version 1
     * OnPushFileChunkIQ
     *  Schema version 1
     * UpdateDescriptorTimestampIQ
     *  Schema version 1
     * OnUpdateDescriptorTimestampIQ
     *  Schema version 1
     *
     *
     * Date: 2016/12/29
     *
     * majorVersion: 2
     * minorVersion: 1
     *
     * ResetConversationIQ:
     *  Schema version 2
     * OnResetConversationIQ
     *  Schema version 2
     * PushObjectIQ
     *  Schema version 3
     * OnPushObjectIQ
     *  Schema version 2
     * PushTransientObjectIQ
     *  Schema version 2
     * OnPushTransientObjectIQ
     *  Schema version 1
     * PushFileIQ
     *  Schema version 2
     * OnPushFileIQ
     *  Schema version 1
     * PushFileChunkIQ
     *  Schema version 1
     * OnPushFileChunkIQ
     *  Schema version 1
     * UpdateDescriptorTimestampIQ
     *  Schema version 1
     * OnUpdateDescriptorTimestampIQ
     *  Schema version 1
     *
     *
     * Date: 2016/09/08
     *
     * majorVersion: 2
     * minorVersion: 0
     *
     * ResetConversationIQ:
     *  Schema version 2
     * OnResetConversationIQ
     *  Schema version 2
     * PushObjectIQ
     *  Schema version 2
     * OnPushObjectIQ
     *  Schema version 2
     * PushTransientObjectIQ
     *  Schema version 1
     * OnPushTransientObjectIQ
     *  Schema version 1
     * PushFileIQ
     *  Schema version 1
     * OnPushFileIQ
     *  Schema version 1
     * PushFileChunkIQ
     *  Schema version 1
     * OnPushFileChunkIQ
     *  Schema version 1
     *
     *
     * majorVersion: 1
     * minorVersion: 0
     *
     * ResetConversationIQ
     *  Schema version 1
     * OnResetConversationIQ
     *  Schema version 1
     * PushObjectIQ
     *  Schema version 1
     * OnPushObjectIQ
     *  Schema version 1
     *
     * </pre>
     */

    static final int SERIALIZER_BUFFER_DEFAULT_SIZE = 1024;

    private static final int OPENING_TIMEOUT = 30; // s

    public static final int CHUNK_SIZE = 256 * 1024;

    /**
     * Handle the conversation synchronize twincode invocation: the peer has something to send for us.
     */
    private final class ConversationSynchronizeInvocation implements TwincodeInboundService.InvocationListener {

        @Override
        public ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationSynchronizeInvocation.onInvokeTwincode: invocation=" + invocation);
            }

            final TwincodeOutbound twincodeOutbound = invocation.subject.getTwincodeOutbound();
            final TwincodeOutbound peerTwincodeOutbound = invocation.subject.getPeerTwincodeOutbound();
            if (twincodeOutbound == null || peerTwincodeOutbound == null) {
                return ErrorCode.EXPIRED;
            }

            // We can receive a process invocation on the group: get the group member that sent us the synchronize invocation.
            final UUID peerTwincodeOutboundId;
            if (invocation.attributes != null) {
                peerTwincodeOutboundId = AttributeNameValue.getUUIDAttribute(invocation.attributes, ConversationProtocol.PARAM_MEMBER_TWINCODE_OUTBOUND_ID);
            } else {
                peerTwincodeOutboundId = null;
            }
            if (peerTwincodeOutboundId != null) {
                final Conversation conversation = getConversation(invocation.subject);
                if (!(conversation instanceof GroupConversationImpl)) {
                    return ErrorCode.EXPIRED;
                }

                final GroupConversationImpl groupConversation = (GroupConversationImpl) conversation;
                final GroupMemberConversationImpl groupMember = groupConversation.getMember(peerTwincodeOutboundId);
                if (groupMember == null) {
                    return ErrorCode.EXPIRED;
                }

                synchronizeConversation(groupMember);
            } else {
                final Conversation conversation = getOrCreateConversationImpl(invocation.subject, true);
                if (!(conversation instanceof ConversationImpl)) {
                    return ErrorCode.EXPIRED;
                }

                synchronizeConversation((ConversationImpl)conversation);
            }
            return ErrorCode.SUCCESS;
        }
    }

    /**
     * Handle the conversation need-secret twincode invocation: the peer needs our public key and secrets and failed
     * to accept an incoming encrypted P2P connection:
     * - if we know the peer public key, make a refresh-secret secure invocation to it,
     * - if we don't know its public key, or, our identity twincode has no public key, report the NOT_AUTHORIZED_OPERATION error.
     */
    private final class ConversationNeedSecretInvocation implements TwincodeInboundService.InvocationListener {

        @Override
        public ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationNeedSecretInvocation.onInvokeTwincode: invocation=" + invocation);
            }

            final TwincodeOutbound twincodeOutbound = invocation.subject.getTwincodeOutbound();
            final TwincodeInbound twincodeInbound = invocation.subject.getTwincodeInbound();
            if (twincodeOutbound == null || twincodeInbound == null) {
                return ErrorCode.EXPIRED;
            }

            // We can receive a process invocation on the group: get the group member that sent us the synchronize invocation.
            final UUID peerTwincodeOutboundId;
            if (invocation.attributes != null) {
                peerTwincodeOutboundId = AttributeNameValue.getUUIDAttribute(invocation.attributes, ConversationProtocol.PARAM_MEMBER_TWINCODE_OUTBOUND_ID);
            } else {
                peerTwincodeOutboundId = null;
            }

            final TwincodeOutbound peerTwincodeOutbound;
            if (peerTwincodeOutboundId != null) {
                final Conversation conversation = getConversation(invocation.subject);
                if (!(conversation instanceof GroupConversationImpl)) {
                    return ErrorCode.EXPIRED;
                }

                final GroupConversationImpl groupConversation = (GroupConversationImpl) conversation;
                final GroupMemberConversationImpl groupMember = groupConversation.getMember(peerTwincodeOutboundId);
                if (groupMember == null) {
                    return ErrorCode.EXPIRED;
                }
                peerTwincodeOutbound = groupMember.getPeerTwincodeOutbound();

            } else {
                peerTwincodeOutbound = invocation.subject.getPeerTwincodeOutbound();
            }
            if (peerTwincodeOutbound == null) {
                return ErrorCode.EXPIRED;
            }

            // If we don't know the peer public key, we cannot proceed.
            if (!peerTwincodeOutbound.isSigned()) {
                return ErrorCode.NOT_AUTHORIZED_OPERATION;
            }

            final List<AttributeNameValue> attributes = new ArrayList<>();
            attributes.add(new AttributeNameLongValue("requestTimestamp", System.currentTimeMillis()));

            // Make sure we have a private key and that our twincode is signed and the server knows our signature.
            // If the server does not know our signature, the peer will ignore any public key and secret from us.
            mTwincodeOutboundService.createPrivateKey(twincodeInbound, (ErrorCode errorCode, TwincodeOutbound twincodeOutbound1) -> {
                if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                    return;
                }

                if (errorCode != ErrorCode.SUCCESS) {
                    mTwincodeInboundService.acknowledgeInvocation(invocation.invocationId, errorCode);
                    return;
                }

                mTwincodeOutboundService.secureInvokeTwincode(twincodeOutbound, twincodeOutbound, peerTwincodeOutbound,
                        TwincodeOutboundService.INVOKE_URGENT | TwincodeOutboundService.CREATE_NEW_SECRET,
                        ConversationProtocol.ACTION_REFRESH_SECRET, attributes, (ErrorCode invokeErrorCode, UUID invocationId) -> {
                            Log.e(LOG_TAG, "Secure invoke result=" + errorCode);
                            if (invokeErrorCode == ErrorCode.TWINLIFE_OFFLINE) {
                                return;
                            }
                            mTwincodeInboundService.acknowledgeInvocation(invocation.invocationId, invokeErrorCode);
                        });
            });

            return ErrorCode.QUEUED;
        }
    }
    private static final String EVENT_ID_SECRET_EXCHANGE = "twinlife::conversation::secret-exchange";

    /**
     * Handle the conversation refresh secret twincode invocation: the peer does not know our secret or it is not able
     * to setup a P2P session with existing secrets.  This is handled in several steps by the same invocation handler
     * because these steps are very close.  The global process is the following:
     *
     * DEVICE-1  -- invokeTwincode("need-refresh") ===>        DEVICE-2
     *                                                         createPrivateKey()
     *                                                         CREATE_NEW_SECRET
     *          <== secureInvokeTwincode("refresh-secret") --
     * getSignedTwincode()
     * save DEVICE-2 secret
     * SEND_SECRET
     *           -- secureInvokeTwincode("on-refresh-secret") ===>
     *                                                         getSignedTwincode()
     *                                                         save DEVICE-1 secret
     *                                                         validateSecrets(DEVICE-2, DEVICE-1)
     *          <== secureInvokeTwincode("validate-secret")   -- (no secret sent)
     * getSignedTwincode()
     * validateSecrets(DEVICE-1, DEVICE-2)
     *
     * A call to validateSecrets() is necessary after a CREATE_NEW_SECRET or SEND_SECRET to make the secret usable for encryption.
     * CREATE_NEW_SECRET generates a new secret 1 or secret 2.
     * SEND_SECRET sends the existing secret but it is created if it does not exist (which means a validateSecrets() is necessary).
     */
    private final class ConversationRefreshSecretInvocation implements TwincodeInboundService.InvocationListener {

        @Override
        public ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation) {
            if (DEBUG) {
                Log.d(LOG_TAG, "ConversationRefreshSecretInvocation.onInvokeTwincode: invocation=" + invocation);
            }

            final TwincodeOutbound twincodeOutbound = invocation.subject.getTwincodeOutbound();
            final UUID peerTwincodeOutboundId = invocation.peerTwincodeId;
            if (invocation.publicKey == null || peerTwincodeOutboundId == null || invocation.attributes == null) {
                return ErrorCode.BAD_REQUEST;
            }
            if (twincodeOutbound == null) {
                return ErrorCode.EXPIRED;
            }
            // The secretKey can be null only for the validate-secret final invocation.
            if (invocation.secretKey == null && !invocation.action.equals(ConversationProtocol.ACTION_VALIDATE_SECRET)) {
                return ErrorCode.BAD_REQUEST;
            }

            mTwincodeOutboundService.getSignedTwincodeWithSecret(peerTwincodeOutboundId, invocation.publicKey, invocation.keyIndex, invocation.secretKey, TrustMethod.PEER,
                    (ErrorCode errorCode, TwincodeOutbound peerTwincodeOutbound) -> {

                // If we are offline or timed out don't acknowledge the invocation.
                if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                    return;
                }
                if (peerTwincodeOutbound == null || errorCode != ErrorCode.SUCCESS) {
                    mTwincodeInboundService.acknowledgeInvocation(invocation.invocationId, errorCode);
                    return;
                }

                if (invocation.action.equals(ConversationProtocol.ACTION_VALIDATE_SECRET)) {
                    mCryptoService.validateSecrets(twincodeOutbound, peerTwincodeOutbound);

                    // Emit a log to track execution duration of the whole secret exchange process.
                    final long startTime = AttributeNameLongValue.getLongAttribute(invocation.attributes, "requestTimestamp", 0);
                    if (startTime > 0) {
                        final long now = System.currentTimeMillis();
                        final Map<String, String> attributes = new HashMap<>();
                        attributes.put("duration", Long.toString(now - startTime));
                        attributes.put("invocationId", invocation.invocationId.toString());

                        mTwinlifeImpl.getManagementServiceImpl().logEvent(EVENT_ID_SECRET_EXCHANGE, attributes, true);
                    }
                    mTwincodeInboundService.acknowledgeInvocation(invocation.invocationId, errorCode);

                } else {
                    final String nextAction;
                    final int invokeOptions;
                    if (invocation.action.equals(ConversationProtocol.ACTION_ON_REFRESH_SECRET)) {
                        mCryptoService.validateSecrets(twincodeOutbound, peerTwincodeOutbound);
                        nextAction = ConversationProtocol.ACTION_VALIDATE_SECRET;
                        invokeOptions = TwincodeOutboundService.INVOKE_URGENT;
                    } else {
                        nextAction = ConversationProtocol.ACTION_ON_REFRESH_SECRET;
                        invokeOptions = TwincodeOutboundService.INVOKE_URGENT | TwincodeOutboundService.SEND_SECRET;
                    }

                    mTwincodeOutboundService.secureInvokeTwincode(twincodeOutbound, twincodeOutbound, peerTwincodeOutbound, invokeOptions,
                            nextAction, invocation.attributes,
                            (ErrorCode lErrorCode, UUID secureInvocationId) -> {
                                // If we are offline or timed out don't acknowledge the invocation.
                                if (lErrorCode == ErrorCode.TWINLIFE_OFFLINE) {
                                    return;
                                }
                                mTwincodeInboundService.acknowledgeInvocation(invocation.invocationId, errorCode);
                            });
                }
            });
            return ErrorCode.QUEUED;
        }
    }

    private final ConversationServiceProvider mServiceProvider;

    private final Object mPeerConnectionLock = new Object();
    private final HashMap<UUID, ConversationConnection> mPeerConnectionId2Conversation = new HashMap<>();
    private final Map<SchemaKey, Pair<Serializer, PeerConnectionBinaryPacketListener>> mBinaryListeners = new HashMap<>();
    private final ScheduledExecutorService mExecutor;
    private final ConversationServiceScheduler mScheduler;
    private final GroupConversationManager mGroupManager;

    private final Set<UUID> mAcceptedPushTwincode = new HashSet<>();
    private final ImageTools mImageTools;
    private final ConversationSynchronizeInvocation mConversationSynchronize;
    private final ConversationNeedSecretInvocation mConversationNeedSecret;
    private final ConversationRefreshSecretInvocation mConversationRefreshSecret;
    private final CryptoServiceImpl mCryptoService;
    private final TwincodeOutboundService mTwincodeOutboundService;
    private final TwincodeInboundService mTwincodeInboundService;
    private PeerConnectionService mPeerConnectionService;
    private SerializerFactoryImpl mSerializerFactory;

    static class ConversationThreadFactory implements ThreadFactory {

        public Thread newThread(@NonNull Runnable runnable) {

            return new Thread(runnable, "twinlife-conversation");
        }
    }

    public ConversationServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection, @NonNull ImageTools imageTools) {
        super(twinlifeImpl, connection);

        setServiceConfiguration(new ConversationServiceConfiguration());

        mServiceProvider = new ConversationServiceProvider(this, twinlifeImpl.getDatabaseService());
        mCryptoService = twinlifeImpl.getCryptoService();
        mTwincodeOutboundService = twinlifeImpl.getTwincodeOutboundService();
        mTwincodeInboundService = mTwinlifeImpl.getTwincodeInboundService();

        mExecutor = Executors.newSingleThreadScheduledExecutor(new ConversationThreadFactory());
        mScheduler = new ConversationServiceScheduler(twinlifeImpl, this, mServiceProvider, mExecutor);
        mImageTools = imageTools;

        mGroupManager = new GroupConversationManager(this, mServiceProvider, mScheduler);
        mConversationSynchronize = new ConversationSynchronizeInvocation();
        mConversationNeedSecret = new ConversationNeedSecretInvocation();
        mConversationRefreshSecret = new ConversationRefreshSecretInvocation();

        // Synchronize
        // Note: the processSynchronizeIQ() is special and cannot be handled through the packet listener.
        addPacketListener(OnSynchronizeIQ.IQ_ON_SYNCHRONIZE_SERIALIZER, this::processOnSynchronizeIQ);

        // Signature
        addPacketListener(SignatureInfoIQ.IQ_SIGNATURE_INFO_SERIALIZER, this::processSignatureInfoIQ);
        addPacketListener(OnSignatureInfoIQ.IQ_ON_SIGNATURE_SERIALIZER, this::processOnSignatureInfoIQ);
        addPacketListener(OnSignatureInfoIQ.IQ_ACK_SIGNATURE_SERIALIZER, this::processAckSignatureInfoIQ);

        // Reset conversation
        addPacketListener(ResetConversationIQ.IQ_RESET_CONVERSATION_SERIALIZER, this::processResetConversationIQ);
        addPacketListener(OnResetConversationIQ.IQ_ON_RESET_CONVERSATION_SERIALIZER, this::processOnResetConversationIQ);

        // Push object
        addPacketListener(PushObjectIQ.IQ_PUSH_OBJECT_SERIALIZER, this::processPushObjectIQ);
        addPacketListener(OnPushObjectIQ.IQ_ON_PUSH_OBJECT_SERIALIZER, this::processOnPushObjectIQ);

        // Update object
        addPacketListener(UpdateDescriptorIQ.IQ_UPDATE_DESCRIPTOR_SERIALIZER, this::processUpdateObjectIQ);
        addPacketListener(OnUpdateDescriptorIQ.IQ_ON_UPDATE_DESCRIPTOR_SERIALIZER, this::processOnUpdateObjectIQ);

        // Push transient and push command object (same handler for both).
        addPacketListener(PushTransientIQ.IQ_PUSH_TRANSIENT_SERIALIZER, this::processPushTransientIQ);
        addPacketListener(PushCommandIQ.IQ_PUSH_COMMAND_SERIALIZER, this::processPushTransientIQ);
        addPacketListener(OnPushCommandIQ.IQ_ON_PUSH_COMMAND_SERIALIZER, this::processOnPushCommandIQ);

        // Push twincode
        addPacketListener(PushTwincodeIQ.IQ_PUSH_TWINCODE_SERIALIZER_2, this::processPushTwincodeIQ);
        addPacketListener(PushTwincodeIQ.IQ_PUSH_TWINCODE_SERIALIZER_3, this::processPushTwincodeIQ);
        addPacketListener(OnPushTwincodeIQ.IQ_ON_PUSH_TWINCODE_SERIALIZER, this::processOnPushTwincodeIQ);

        // Push geolocation
        addPacketListener(PushGeolocationIQ.IQ_PUSH_GEOLOCATION_SERIALIZER, this::processPushGeolocationIQ);
        addPacketListener(OnPushGeolocationIQ.IQ_ON_PUSH_GEOLOCATION_SERIALIZER, this::processOnPushGeolocationIQ);

        // Push file
        addPacketListener(PushFileIQ.IQ_PUSH_FILE_SERIALIZER, this::processPushFileIQ);
        addPacketListener(PushThumbnailIQ.IQ_PUSH_THUMBNAIL_SERIALIZER, this::processPushThumbnailIQ);
        addPacketListener(OnPushFileIQ.IQ_ON_PUSH_FILE_SERIALIZER, this::processOnPushFileIQ);
        addPacketListener(PushFileChunkIQ.IQ_PUSH_FILE_CHUNK_SERIALIZER, this::processPushFileChunkIQ);
        addPacketListener(OnPushFileChunkIQ.IQ_ON_PUSH_FILE_CHUNK_SERIALIZER, this::processOnPushFileChunkIQ);

        // Update timestamps
        addPacketListener(UpdateTimestampIQ.IQ_UPDATE_TIMESTAMPS_SERIALIZER, this::processUpdateTimestampIQ);
        addPacketListener(OnUpdateTimestampIQ.IQ_ON_UPDATE_TIMESTAMP_SERIALIZER, this::processOnUpdateTimestampIQ);

        // Update annotation
        addPacketListener(UpdateAnnotationIQ.IQ_UPDATE_ANNOTATION_SERIALIZER, this::processUpdateAnnotationIQ);
        addPacketListener(OnUpdateAnnotationIQ.IQ_ON_UPDATE_ANNOTATION_SERIALIZER, this::processOnUpdateAnnotationIQ);

        // Invite group
        addPacketListener(InviteGroupIQ.IQ_INVITE_GROUP_SERIALIZER_2, this::processInviteGroupIQ);
        addPacketListener(OnInviteGroupIQ.IQ_ON_INVITE_GROUP_SERIALIZER, this::processOnInviteGroupIQ);

        // Join group
        addPacketListener(JoinGroupIQ.IQ_JOIN_GROUP_SERIALIZER_2, this::processJoinGroupIQ);
        addPacketListener(OnJoinGroupIQ.IQ_ON_JOIN_GROUP_SERIALIZER_2, this::processOnJoinGroupIQ);

        // Update group member permission
        addPacketListener(UpdatePermissionsIQ.IQ_UPDATE_PERMISSIONS_SERIALIZER, this::processUpdatePermissionsIQ);
        addPacketListener(OnUpdatePermissionsIQ.IQ_ON_UPDATE_PERMISSIONS_SERIALIZER, this::processOnUpdatePermissionsIQ);
    }

    //
    // Override BaseServiceImpl methods
    //

    @Override
    public void configure(@NonNull BaseServiceConfiguration baseServiceConfiguration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "configure: baseServiceConfiguration=" + baseServiceConfiguration);
        }

        if (!(baseServiceConfiguration instanceof ConversationServiceConfiguration)) {
            setConfigured(false);

            return;
        }

        ConversationServiceConfiguration conversationServiceConfiguration = new ConversationServiceConfiguration();

        setServiceConfiguration(conversationServiceConfiguration);
        setServiceOn(baseServiceConfiguration.serviceOn);
        setConfigured(true);

        mSerializerFactory = mTwinlifeImpl.getSerializerFactoryImpl();
        mPeerConnectionService = mTwinlifeImpl.getPeerConnectionService();
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDestroy");
        }

        super.onDestroy();

        mExecutor.shutdownNow();
    }

    @Override
    protected void onTwinlifeReady() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeReady");
        }

        mTwincodeInboundService.addListener(ConversationProtocol.ACTION_CONVERSATION_SYNCHRONIZE, mConversationSynchronize);
        mTwincodeInboundService.addListener(ConversationProtocol.ACTION_CONVERSATION_NEED_SECRET, mConversationNeedSecret);
        mTwincodeInboundService.addListener(ConversationProtocol.ACTION_REFRESH_SECRET, mConversationRefreshSecret);
        mTwincodeInboundService.addListener(ConversationProtocol.ACTION_ON_REFRESH_SECRET, mConversationRefreshSecret);
        mTwincodeInboundService.addListener(ConversationProtocol.ACTION_VALIDATE_SECRET, mConversationRefreshSecret);
        mGroupManager.onTwinlifeReady();
        mScheduler.loadOperations();
    }

    @Override
    public void onTwinlifeOnline() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTwinlifeOnline");
        }

        super.onTwinlifeOnline();

        mScheduler.onTwinlifeOnline();
    }

    @Override
    public void onSignOut() {
        if (DEBUG) {
            Log.d(LOG_TAG, "onSignOut");
        }

        super.onSignOut();

        synchronized (mPeerConnectionLock) {
            mPeerConnectionId2Conversation.clear();
        }
        mScheduler.removeAllOperations();

        deleteAllFiles();
    }

    //
    // Implement ConversationService interface
    //
    @Override
    @Nullable
    public Conversation getConversation(@NonNull RepositoryObject subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConversation: subject=" + subject);
        }

        return mServiceProvider.loadConversationWithSubject(subject);
    }

    @Override
    public void incomingPeerConnection(@NonNull UUID peerConnectionId, @NonNull RepositoryObject subject,
                                       @NonNull TwincodeOutbound peerTwincodeOutbound, boolean create) {
        if (DEBUG) {
            Log.d(LOG_TAG, "incomingPeerConnection: peerConnectionId=" + peerConnectionId + " subject=" + subject
                    + " peerTwincodeOutbound=" + peerTwincodeOutbound + " create=" + create);
        }

        if (!isServiceOn()) {

            return;
        }

        final Offer peerOffer = mPeerConnectionService.getPeerOffer(peerConnectionId);
        final SdpEncryptionStatus encryptionStatus = mPeerConnectionService.getSdpEncryptionStatus(peerConnectionId);
        if (peerOffer == null || encryptionStatus == null) {

            // The peer connection was terminated before we handle it.
            return;
        }

        if (!peerOffer.data) {
            mPeerConnectionService.terminatePeerConnection(peerConnectionId, TerminateReason.NOT_AUTHORIZED);

            return;
        }

        final Conversation conversation = getOrCreateConversationImpl(subject, create);
        final TwincodeOutbound twincodeOutbound = subject.getTwincodeOutbound();
        if (conversation == null || twincodeOutbound == null) {
            // An incoming P2P connection can happen on a group that we left, we must not accept it.
            if (Logger.WARN) {
                Logger.warn(LOG_TAG, "Terminate P2P connection: the group conversation was revoked");
            }
            mPeerConnectionService.terminatePeerConnection(peerConnectionId, TerminateReason.REVOKED);
            return;
        }

        if (Logger.INFO) {
            Logger.info(LOG_TAG, "incomingPeerConnection ", peerConnectionId, " conversation:",
                    conversation.getId());
        }

        final ConversationImpl conversationImpl;
        if (conversation instanceof GroupConversationImpl) {
            final GroupMemberConversationImpl groupMemberConversationImpl = ((GroupConversationImpl) conversation).getMember(peerTwincodeOutbound.getId());
            if (groupMemberConversationImpl == null) {
                // If the incoming group member is not known and the session-initiate is encrypted, we cannot proceed
                // because we don't know the secrets to use with that group member.  Reject with GONE to avoid
                // creating the incoming PeerConnection and failing with NO_PRIVATE_KEY.
                if (encryptionStatus != SdpEncryptionStatus.NONE) {
                    mPeerConnectionService.terminatePeerConnection(peerConnectionId, TerminateReason.GONE);
                    return;
                }
                conversationImpl = ((GroupConversationImpl) conversation).getIncomingConversation();
            } else {
                conversationImpl = groupMemberConversationImpl;
            }
        } else {
            conversationImpl = (ConversationImpl) conversation;
        }

        final ConversationConnection connection;
        final UUID previousIncomingPeerConnectionId;
        synchronized (mPeerConnectionLock) {
            connection = conversationImpl.canAcceptIncoming(mTwinlifeImpl);
            if (connection != null) {
                previousIncomingPeerConnectionId = connection.incomingPeerConnection(peerConnectionId);
                mPeerConnectionId2Conversation.put(peerConnectionId, connection);
            } else {
                previousIncomingPeerConnectionId = null;
            }
        }

        // Close a previous incoming P2P connection that was not yet opened.
        if (previousIncomingPeerConnectionId != null) {
            mPeerConnectionService.terminatePeerConnection(previousIncomingPeerConnectionId, TerminateReason.GONE);
        }
        if (connection == null) {
            mPeerConnectionService.terminatePeerConnection(peerConnectionId, TerminateReason.BUSY);
        } else {
            final Offer offer = new Offer(false, false, false, true);
            final OfferToReceive offerToReceive = new OfferToReceive(false, false, true);
            mPeerConnectionService.createIncomingPeerConnection(peerConnectionId, conversationImpl.getSubject(),
                    peerTwincodeOutbound, offer, offerToReceive, this, this, (ErrorCode errorCode, UUID id) -> {
                if (errorCode == ErrorCode.SUCCESS) {
                    synchronized (mPeerConnectionLock) {
                        connection.setIncomingPeerConnectionOpening();
                    }
                    mScheduler.startOperation(connection, State.OPENING);
                    connection.setOpenTimeout(mExecutor.schedule(() -> onOpenTimeout(connection), OPENING_TIMEOUT, TimeUnit.SECONDS));

                } else {
                    synchronized (mPeerConnectionLock) {
                        mPeerConnectionId2Conversation.remove(peerConnectionId);
                    }
                    close(connection, true, peerConnectionId, TerminateReason.fromErrorCode(errorCode));
                }
            });
        }
    }

    @Override
    public void updateConversation(@NonNull RepositoryObject subject, @NonNull TwincodeOutbound peerTwincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "synchronizeConversation: subject=" + subject + " peerTwincodeOutbound=" + peerTwincodeOutbound);
        }

        if (!isServiceOn()) {

            return;
        }

        final Conversation conversation = getOrCreateConversationImpl(subject, false);
        if (!(conversation instanceof ConversationImpl)) {
            return;
        }

        final ConversationImpl conversationImpl = (ConversationImpl) conversation;
        mServiceProvider.updateConversation(conversationImpl, peerTwincodeOutbound);
        synchronizeConversation(conversationImpl);
    }

    private void synchronizeConversation(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "synchronizeConversation conversationImpl=" + conversationImpl);
        }

        // Send the synchronize operation to the peer and try connecting to it.
        final Operation operation = mScheduler.getFirstOperation(conversationImpl);
        conversationImpl.touch();

        if (operation == null || operation.getType() != SYNCHRONIZE_CONVERSATION) {

            SynchronizeConversationOperation synchronizeConversationOperation = new SynchronizeConversationOperation(conversationImpl);
            mServiceProvider.storeOperation(synchronizeConversationOperation);
            mScheduler.addOperation(conversationImpl, synchronizeConversationOperation);
        } else {
            mScheduler.scheduleConversationOperations(conversationImpl);
        }
    }

    @Override
    @NonNull
    public List<Conversation> listConversations(@NonNull Filter<Conversation> filter) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listConversations filter=" + filter);
        }

        if (!isServiceOn()) {

            return new ArrayList<>();
        }

        return mServiceProvider.listConversations(filter);
    }

    @Override
    @Nullable
    public Conversation getOrCreateConversation(@NonNull RepositoryObject subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getOrCreateConversation: subject=" + subject);
        }

        if (!isServiceOn()) {

            return null;
        }

        return getOrCreateConversationImpl(subject, true);
    }

    @Override
    @Nullable
    public GroupConversation getGroupConversationWithGroupTwincodeId(@NonNull UUID groupTwincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getGroupConversationWithGroupTwincodeId: groupTwincodeId=" + groupTwincodeId);
        }

        return mServiceProvider.findGroupConversation(groupTwincodeId);
    }

    @Override
    @Nullable
    public GroupMemberConversation getGroupMemberConversation(@NonNull UUID groupTwincodeId, @NonNull UUID memberTwincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getGroupMemberConversation: groupTwincodeId=" + groupTwincodeId);
        }

        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
        if (groupConversation == null) {
            return null;
        }

        return groupConversation.getMember(groupTwincodeId);
    }

    @Override
    @NonNull
    public ErrorCode clearConversation(@NonNull Conversation conversation, long clearDate, @NonNull ClearMode clearMode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "clearConversation: conversation=" + conversation + " clearDate=" + clearDate + " clearMode=" + clearMode);
        }

        if (!isServiceOn()) {

            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        // If the user is not allowed to reset the conversation, switch to local mode.
        if (!conversation.hasPermission(Permission.RESET_CONVERSATION) && clearMode == ClearMode.CLEAR_BOTH) {
            clearMode = ClearMode.CLEAR_LOCAL;
        }

        if (clearMode == ClearMode.CLEAR_BOTH_MEDIA) {

            final long deleteTimestamp = System.currentTimeMillis();
            final List<DescriptorId>[] list = mServiceProvider.deleteMediaDescriptors(conversation, clearDate, deleteTimestamp);

            List<DescriptorId> deleteList = list[0];
            final List<DescriptorId> ownerDeleteList = list[1];
            final List<DescriptorId> peerDeleteList = list[2];

            final List<ConversationImpl> conversations = getConversations(conversation, null);
            if (!conversations.isEmpty()) {
                final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();

                // Send a delete operation for our own medias to each conversation member.
                if (ownerDeleteList != null) {
                    for (final ConversationImpl conversationImpl : conversations) {
                        conversationImpl.touch();

                        final List<Operation> operations = new ArrayList<>();
                        for (DescriptorId descriptorId : ownerDeleteList) {
                            UpdateDescriptorTimestampOperation updateDescriptorTimestampOperation
                                    = new UpdateDescriptorTimestampOperation(conversationImpl,
                                    UpdateDescriptorTimestampType.DELETE, descriptorId, deleteTimestamp);
                            operations.add(updateDescriptorTimestampOperation);
                        }
                        pendingOperations.put(conversationImpl, operations);
                    }
                }

                // Send a peer delete operation only to the peer that sent us the media.
                if (peerDeleteList != null) {
                    for (DescriptorId descriptorId : peerDeleteList) {
                        for (final ConversationImpl conversationImpl : conversations) {
                            conversationImpl.touch();

                            if (descriptorId.twincodeOutboundId.equals(conversationImpl.getPeerTwincodeOutboundId())) {
                                UpdateDescriptorTimestampOperation updateDescriptorTimestampOperation
                                        = new UpdateDescriptorTimestampOperation(conversationImpl,
                                        UpdateDescriptorTimestampType.PEER_DELETE, descriptorId, deleteTimestamp);
                                List<Operation> operations = (List<Operation>)pendingOperations.get(conversationImpl);
                                if (operations == null) {
                                    operations = new ArrayList<>();
                                    pendingOperations.put(conversationImpl, operations);
                                }
                                operations.add(updateDescriptorTimestampOperation);
                                break;
                            }
                        }
                    }
                }
                addOperations(pendingOperations);
            } else if (ownerDeleteList != null) {
                // No member, if we have sent some files, delete them.
                deleteDescriptorFiles(ownerDeleteList);
            }

            // Cleanup files at the end.
            if (peerDeleteList != null) {
                deleteDescriptorFiles(peerDeleteList);
            }
            if (deleteList != null) {
                deleteDescriptorFiles(deleteList);
            }

            // If this is a normal conversation, check if it still has some descriptors to update the isActive flag.
            if (conversation instanceof ConversationImpl) {
                ConversationImpl conversationImpl = (ConversationImpl) conversation;
                long count = mServiceProvider.getDescriptorCount(conversationImpl);
                conversationImpl.setIsActive(count != 0);
            }

            final ClearMode mode = clearMode;
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onResetConversation(conversation, mode));
            }

            return ErrorCode.SUCCESS;
        }

        final Map<UUID, DescriptorId> resetList = getDescriptorsToDelete(conversation, clearDate);
        if (resetList.isEmpty()) {

            return ErrorCode.SUCCESS;
        }

        final List<ConversationImpl> conversations = getConversations(conversation, null);
        final ClearDescriptorImpl clearDescriptorImpl;
        if (clearMode == ClearMode.CLEAR_BOTH && !conversations.isEmpty()) {

            // Create the clear descriptor to tell the peer a reset was made (it is not saved in the database).
            DescriptorId descriptorId = new DescriptorId(0, conversation.getTwincodeOutboundId(), newSequenceId());
            clearDescriptorImpl = new ClearDescriptorImpl(descriptorId, 0, clearDate);
        } else {
            clearDescriptorImpl = null;
        }

        final List<DescriptorId> clearMembers;

        // For a group, we must send the max sequence that we have removed for each member.
        // This is necessary for protocol version <= 2.12.0.
        if (conversation.isGroup()) {
            clearMembers = new ArrayList<>();

            for (Map.Entry<UUID, DescriptorId> resetId : resetList.entrySet()) {
                clearMembers.add(new DescriptorId(0, resetId.getKey(), resetId.getValue().sequenceId + 1));
            }
        } else {
            clearMembers = null;
        }

        // Send the reset operation to each peer (sequenceId is used only for protocol <= 2.12.0).
        final DescriptorId minDescriptorId = resetList.get(conversation.getTwincodeOutboundId());
        final long minSequenceId = minDescriptorId == null ? 0 : minDescriptorId.sequenceId + 1;
        for (final ConversationImpl conversationImpl : conversations) {

            conversationImpl.touch();

            DescriptorId peerDescriptorId = resetList.get(conversationImpl.getPeerTwincodeOutboundId());
            long minPeerSequenceId = peerDescriptorId == null ? 0 : peerDescriptorId.sequenceId + 1;

            ResetConversationOperation resetConversationOperation = new ResetConversationOperation(conversationImpl,
                    clearDescriptorImpl, minSequenceId, minPeerSequenceId, clearMembers, clearDate, clearMode);

            mServiceProvider.storeOperation(resetConversationOperation);
            if (clearMode == ClearMode.CLEAR_BOTH) {
                mScheduler.addOperation(conversationImpl, resetConversationOperation);
            } else {
                mScheduler.addDeferrableOperation(conversationImpl, resetConversationOperation);
            }
        }

        resetConversation(conversation, resetList, clearMode);

        final ClearMode mode = clearMode;
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onResetConversation(conversation, mode));
        }

        return ErrorCode.SUCCESS;
    }

    @Override
    @NonNull
    public ErrorCode deleteConversation(@NonNull RepositoryObject subject) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteConversation: subject=" + subject);
        }

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        final Conversation conversation = mServiceProvider.loadConversationWithSubject(subject);
        if (conversation == null) {
            return ErrorCode.ITEM_NOT_FOUND;
        }

        if (conversation instanceof GroupConversationImpl) {
            mGroupManager.deleteGroupConversation((GroupConversationImpl) conversation);
        } else {
            deleteConversation((ConversationImpl) conversation);

            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onDeleteConversation(conversation.getId()));
            }
        }
        return ErrorCode.SUCCESS;
    }

    @Override
    @Nullable
    public List<Descriptor> getConversationDescriptors(@NonNull Conversation conversation,  @NonNull DisplayCallsMode callsMode,
                                                       long beforeTimestamp, int maxDescriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDescriptors: conversation=" + conversation + " beforeTimestamp=" + beforeTimestamp +
                    " maxDescriptors=" + maxDescriptors);
        }

        if (!isServiceOn()) {

            return null;
        }

        return mServiceProvider.loadDescriptorImpls(conversation, null, callsMode, beforeTimestamp, maxDescriptors);
    }

    @Override
    public void getReplyTos(@NonNull List<Descriptor> descriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getReplyTos: descriptors.size()=" + descriptors.size());
        }

        if (!isServiceOn()) {

            return;
        }

        Map<Long, Descriptor> replies = getNeededReplies(descriptors);

        List<Descriptor> replyTos = mServiceProvider.loadDescriptorImpls(replies.keySet());

        for (Descriptor replyTo : replyTos) {
            Descriptor d = replies.get(replyTo.getDescriptorId().id);
            if (d != null) {
                d.setReplyToDescriptor(replyTo);
            }
        }
    }

    /**
     * For each {@link Descriptor} with a replyToDescriptorId, try to find the corresponding Descriptor
     * in the list. If found, set it. Otherwise add it to the map so {@link #getReplyTos(List)} knows
     * which descriptors to fetch from the DB.
     * @param descriptors
     * @return a {@link Map} containing the descriptors which are missing their replyToDescriptor,
     * with the replyToDescriptor's ID as the key.
     */
    @NonNull
    private Map<Long, Descriptor> getNeededReplies(@NonNull List<Descriptor> descriptors) {
        Map<Long, Descriptor> replies = new HashMap<>();

        for (Descriptor d : descriptors) {
            if (d.getReplyToDescriptorId() != null) {
                Descriptor loadedReplyTo = null;
                for (Descriptor maybeReplyTo : descriptors) {
                    if (d.getReplyToDescriptorId().equals(maybeReplyTo.getDescriptorId())) {
                        loadedReplyTo = maybeReplyTo;
                        break;
                    }
                }
                if (loadedReplyTo != null) {
                    d.setReplyToDescriptor(loadedReplyTo);
                } else {
                    replies.put(d.getReplyToDescriptorId().id, d);
                }
            }
        }
        return replies;
    }

    @Override
    @Nullable
    public List<Descriptor> getConversationTypeDescriptors(@NonNull Conversation conversation, @NonNull Descriptor.Type[] types,
                                                           @NonNull DisplayCallsMode callsMode, long beforeTimestamp, int maxDescriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDescriptors: conversation=" + conversation + " type=" + types + " beforeTimestamp=" + beforeTimestamp +
                    " maxDescriptors=" + maxDescriptors);
        }

        if (!isServiceOn()) {

            return null;
        }

        return mServiceProvider.loadDescriptorImpls(conversation, types, callsMode, beforeTimestamp, maxDescriptors);
    }

    @Override
    @Nullable
    public Map<Conversation, Descriptor> getLastConversationDescriptors(@NonNull Filter<Conversation> filter,
                                                                        @NonNull DisplayCallsMode callsMode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getLastConversationDescriptors: filter=" + filter + " callsMode=" + callsMode);
        }

        if (!isServiceOn()) {

            return null;
        }

        return mServiceProvider.listLastDescriptors(filter, callsMode);
    }

    @Override
    @Nullable
    public List<Descriptor> getDescriptors(@NonNull Descriptor.Type[] types, @NonNull DisplayCallsMode callsMode,
                                           long beforeTimestamp, int maxDescriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDescriptors: type=" + types + " callsMode=" + callsMode + " beforeTimestamp=" + beforeTimestamp +
                    " maxDescriptors=" + maxDescriptors);
        }

        if (!isServiceOn()) {

            return null;
        }

        return mServiceProvider.loadDescriptorImpls(null, types, callsMode, beforeTimestamp, maxDescriptors);
    }

    @Nullable
    public List<Pair<Conversation, Descriptor>> searchDescriptors(@NonNull List<Conversation> conversations,
                                                                  @NonNull String searchText,
                                                                  long beforeTimestamp, int maxDescriptors) {
        if (DEBUG) {
            Log.d(LOG_TAG, "searchDescriptors: searchText=" + searchText
                    + " beforeTimestamp=" + beforeTimestamp + " maxDescriptors=" + maxDescriptors);
        }

        if (!isServiceOn()) {

            return null;
        }

        return mServiceProvider.searchDescriptors(conversations, searchText, beforeTimestamp, maxDescriptors);
    }

    @Override
    @Nullable
    public Set<UUID> getConversationTwincodes(@NonNull Conversation conversation, @Nullable Descriptor.Type type, long beforeTimestamp) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConversationTwincodes: conversation=" + conversation + " type=" + type + " beforeTimestamp=" + beforeTimestamp);
        }

        if (!isServiceOn()) {

            return null;
        }

        return mServiceProvider.listDescriptorTwincodes(conversation, type, beforeTimestamp);
    }

    @Override
    public void forwardDescriptor(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                                  @NonNull DescriptorId descriptorId, boolean copyAllowed, long expireTimeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "forwardDescriptor: requestId=" + requestId + " descriptorId=" + descriptorId);
        }

        if (!isServiceOn()) {

            return;
        }

        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptorImpl == null || descriptorImpl.getDeletedTimestamp() > 0) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, descriptorId.toString());

            return;
        }

        if (!conversation.hasPermission(descriptorImpl.getPermission())) {
            onError(requestId, ErrorCode.NO_PERMISSION, null);

            return;
        }

        final List<ConversationImpl> conversations = getConversations(conversation, sendTo);
        final DescriptorImpl forwarded = mServiceProvider.createDescriptor(conversation, (long id, long sequenceId, long cid) -> {
            final DescriptorId forwardDescriptorId = new DescriptorId(id, conversation.getTwincodeOutboundId(), sequenceId);
            final DescriptorImpl result = descriptorImpl.createForward(forwardDescriptorId, cid, expireTimeout, sendTo, copyAllowed);

            // If we try to send on a group with no peer, mark a send failure (ie, we are the only one in the group!).
            if (result != null && conversations.isEmpty()) {
                result.setSentTimestamp(-1);
            }
            return result;
        });
        if (forwarded == null) {
            // Refuse to forward other descriptors
            onError(requestId, ErrorCode.NO_PERMISSION, null);
            return;
        }

        if (forwarded instanceof FileDescriptorImpl) {
            if (!copyFile((FileDescriptorImpl) descriptorImpl, (FileDescriptorImpl) forwarded)) {
                mServiceProvider.deleteDescriptor(conversation, forwarded.getDescriptorId());
                onError(requestId, ErrorCode.NO_STORAGE_SPACE, null);

                return;
            }
        }

        mServiceProvider.setAnnotation(descriptorImpl, AnnotationType.FORWARDED, 0);
        mServiceProvider.setAnnotation(forwarded, AnnotationType.FORWARD, 0);

        // Send the object to each peer.
        final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
        for (final ConversationImpl conversationImpl : conversations) {

            final Operation operation;

            if (forwarded instanceof ObjectDescriptorImpl) {
                ObjectDescriptorImpl objectDescriptorImpl = (ObjectDescriptorImpl) forwarded;

                operation = new PushObjectOperation(conversationImpl, objectDescriptorImpl);
            } else if (forwarded instanceof GeolocationDescriptorImpl) {
                GeolocationDescriptorImpl geolocationDescriptorImpl = (GeolocationDescriptorImpl) forwarded;

                operation = new PushGeolocationOperation(conversationImpl, geolocationDescriptorImpl);

            } else if (forwarded instanceof FileDescriptorImpl) {
                FileDescriptorImpl fileDescriptorImpl = (FileDescriptorImpl) forwarded;

                operation = new PushFileOperation(conversationImpl, fileDescriptorImpl);
            } else {
                break;
            }

            conversationImpl.touch();
            conversationImpl.setIsActive(true);
            pendingOperations.put(conversationImpl, operation);
        }
        addOperations(pendingOperations);

        // Notify push operation was queued.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onPushDescriptor(requestId, conversation, forwarded));
        }
    }

    /**
     * Proceed with adding a list of operations associated with different conversations.
     * The operations are saved in a single SQL transaction for a serious performance improvement.
     * The operations are then given to the conversation scheduler.
     *
     * @param pendingOperations the list of conversations, operation that were added.
     */
    void addOperations(@NonNull Map<ConversationImpl, Object> pendingOperations) {
        if (DEBUG) {
            Log.d(LOG_TAG, "addOperations: pendingOperations=" + pendingOperations);
        }

        // Store the operations using a single SQL transaction for performance improvement.
        mServiceProvider.storeOperations(pendingOperations.values());
        for (Map.Entry<ConversationImpl, Object> pending : pendingOperations.entrySet()) {
            final Object value = pending.getValue();
            if (value instanceof Operation) {
                mScheduler.addOperationAndSchedule(pending.getKey(), (Operation) value, false);
            } else if (value instanceof List) {
                for (Object operation : (List<?>) value) {
                    mScheduler.addOperationAndSchedule(pending.getKey(), (Operation) operation, false);
                }
            }
        }
        mScheduler.scheduleOperations();
    }

    @Override
    public void pushCommand(long requestId, @NonNull Conversation conversation, @NonNull Object object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushCommand: requestId=" + requestId + " conversation=" + conversation
                    + " object=" + object);
        }

        if (!isServiceOn()) {

            return;
        }

        final Serializer serializer = mSerializerFactory.getObjectSerializer(object);
        if (serializer == null || !conversation.hasPermission(Permission.SEND_COMMAND)) {
            final ErrorCode error = (serializer == null) ? ErrorCode.BAD_REQUEST : ErrorCode.NO_PERMISSION;
            onError(requestId, error, null);

            return;
        }

        final List<ConversationImpl> conversations = getConversations(conversation, null);

        // Create one object descriptor for the conversation.
        final TransientObjectDescriptorImpl commandDescriptorImpl = new TransientObjectDescriptorImpl(conversation.getTwincodeOutboundId(), 0, 0, serializer, object);

        // If we try to send on a group with no peer, mark a send failure (ie, we are the only one in the group!).
        if (conversations.isEmpty()) {
            commandDescriptorImpl.setReadTimestamp(-1);
            commandDescriptorImpl.setSentTimestamp(-1);
            commandDescriptorImpl.setReceivedTimestamp(-1);
        }

        // Send the object to each peer.
        for (final ConversationImpl conversationImpl : conversations) {
            conversationImpl.touch();
            conversationImpl.setIsActive(true);

            final PushCommandOperation pushCommandOperation = new PushCommandOperation(conversationImpl, commandDescriptorImpl);
            mScheduler.addOperation(conversationImpl, pushCommandOperation);
        }

        // Notify push operation was queued.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onPushDescriptor(requestId, conversation, commandDescriptorImpl));
        }
    }

    @Override
    public void pushMessage(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                            @Nullable DescriptorId replyTo, @NonNull String message, boolean copyAllowed, long expireTimeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushObject: requestId=" + requestId + " conversation=" + conversation
                    + " sendTo=" + sendTo + " replyTo=" + replyTo
                    + " message=" + message + " copyAllowed=" + copyAllowed + " expireTimeout=" + expireTimeout);
        }

        if (!isServiceOn()) {

            return;
        }

        // final Serializer serializer = mSerializerFactory.getObjectSerializer(object);
        if (!conversation.hasPermission(Permission.SEND_MESSAGE)) {
            onError(requestId, ErrorCode.NO_PERMISSION, null);

            return;
        }

        final List<ConversationImpl> conversations = getConversations(conversation, sendTo);

        // Verify that the replyTo descriptor exists at the time we are creating the entry (it can be deleted later).
        if (replyTo != null) {
            DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(replyTo);
            if (descriptorImpl == null) {

                onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
        }

        // Create one object descriptor for the conversation.
        final ObjectDescriptorImpl objectDescriptorImpl = (ObjectDescriptorImpl) mServiceProvider.createDescriptor(conversation, (long id, long sequenceId, long cid) -> {
            final DescriptorId descriptorId = new DescriptorId(id, conversation.getTwincodeOutboundId(), sequenceId);
            final ObjectDescriptorImpl result = new ObjectDescriptorImpl(descriptorId, cid, expireTimeout, sendTo, replyTo, message, copyAllowed);

            // If we try to send on a group with no peer, mark a send failure (ie, we are the only one in the group!).
            if (conversations.isEmpty()) {
                result.setSentTimestamp(-1);
            }
            return result;
        });
        if (objectDescriptorImpl == null) {
            onError(requestId, ErrorCode.NO_STORAGE_SPACE, null);
            return;
        }

        // Send the object to each peer.
        if (!conversations.isEmpty()) {
            final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
            for (final ConversationImpl conversationImpl : conversations) {
                conversationImpl.touch();
                conversationImpl.setIsActive(true);

                final PushObjectOperation pushObjectOperation = new PushObjectOperation(conversationImpl, objectDescriptorImpl);
                pendingOperations.put(conversationImpl, pushObjectOperation);
            }
            addOperations(pendingOperations);
        }

        // Notify push operation was queued.
        notifyPushDescriptor(requestId, conversation, objectDescriptorImpl);
    }

    private void notifyPushDescriptor(long requestId, @NonNull Conversation conversation, @NonNull Descriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "notifyPushDescriptor: requestId=" + requestId + " conversation=" + conversation + " descriptor=" + descriptor);
        }

        for (final ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onPushDescriptor(requestId, conversation, descriptor));
        }
    }

    @Override
    public void pushTransientObject(long requestId, @NonNull Conversation conversation, @NonNull Object object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushTransientObject: requestId=" + requestId + " conversation=" + conversation + " object=" + object);
        }

        if (!isServiceOn()) {

            return;
        }

        final Serializer serializer = mSerializerFactory.getObjectSerializer(object);
        if (serializer == null || !conversation.hasPermission(Permission.SEND_MESSAGE)) {
            final ErrorCode error = (serializer == null) ? ErrorCode.BAD_REQUEST : ErrorCode.NO_PERMISSION;
            onError(requestId, error, null);

            return;
        }

        // Send the transient object to each connected peer: drop the conversations which are not opened.
        final List<ConversationImpl> conversations = getConversations(conversation, null);
        for (int i = conversations.size() - 1; i >= 0; i--) {
            final ConversationImpl conversationImpl = conversations.get(i);
            final ConversationConnection connection = conversationImpl.getConnection();
            if (connection == null || !connection.isOpened()) {
                conversations.remove(i);
            } else {
                final int majorVersion = connection.getMaxPeerMajorVersion();
                final int minorVersion = connection.getMaxPeerMinorVersion(majorVersion);

                if (serializer.isSupported(majorVersion, minorVersion)) {
                    conversationImpl.touch();
                } else {
                    conversations.remove(i);
                }
            }
        }
        if (conversations.isEmpty()) {

            return;
        }

        // Create one object descriptor for the conversation.
        final TransientObjectDescriptorImpl transientObjectDescriptorImpl = new TransientObjectDescriptorImpl(conversation.getTwincodeOutboundId(), 0, 0, serializer,
                object);

        for (final ConversationImpl conversationImpl : conversations) {
            conversationImpl.touch();

            final PushTransientObjectOperation pushTransientObjectOperation = new PushTransientObjectOperation(conversationImpl, transientObjectDescriptorImpl);
            mScheduler.addOperation(conversationImpl, pushTransientObjectOperation);
        }

        // Notify push operation was queued.
        notifyPushDescriptor(requestId, conversation, transientObjectDescriptorImpl);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public void pushFile(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                         @Nullable DescriptorId replyTo, @NonNull Uri path, @NonNull String name,
                         @NonNull Descriptor.Type type, boolean toBeDeleted, boolean copyAllowed, long expireTimeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushFile: requestId=" + requestId + " conversation=" + conversation
                    + " sendTo=" + sendTo + " replyTo=" + replyTo + " path=" + path + " name=" + name
                    + " type=" + type + " toBeDeleted=" + toBeDeleted + " copyAllowed=" + copyAllowed + " expireTimeout=" + expireTimeout);
        }

        if (!isServiceOn()) {

            return;
        }

        final Permission perm;
        switch (type) {
            case IMAGE_DESCRIPTOR:
                perm = Permission.SEND_IMAGE;
                break;
            case AUDIO_DESCRIPTOR:
                perm = Permission.SEND_AUDIO;
                break;
            case VIDEO_DESCRIPTOR:
                perm = Permission.SEND_VIDEO;
                break;
            default:
                perm = Permission.SEND_FILE;
                break;
        }

        if (!conversation.hasPermission(perm)) {
            onError(requestId, ErrorCode.NO_PERMISSION, null);

            return;
        }

        // Verify that the replyTo descriptor exists at the time we are creating the entry (it can be deleted later).
        if (replyTo != null) {
            DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(replyTo);
            if (descriptorImpl == null) {

                onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
        }

        // Create one file descriptor for the conversation.
        final FileDescriptorImpl fileDescriptorImpl = importFileWithConversation(conversation, requestId, expireTimeout, sendTo, replyTo, path, name, type, toBeDeleted, copyAllowed);
        if (fileDescriptorImpl == null) {

            return;
        }

        final List<ConversationImpl> conversations = getConversations(conversation, sendTo);

        // If we try to send on a group with no peer, mark a send failure (ie, we are the only one in the group!).
        if (conversations.isEmpty()) {
            fileDescriptorImpl.setSentTimestamp(-1);
        }

        mServiceProvider.insertOrUpdateDescriptorImpl(conversation, fileDescriptorImpl);

        if (!conversations.isEmpty()) {
            // Send the file to each peer.
            final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
            for (final ConversationImpl conversationImpl : conversations) {
                conversationImpl.touch();
                conversationImpl.setIsActive(true);

                final PushFileOperation pushFileOperation = new PushFileOperation(conversationImpl, fileDescriptorImpl);
                pendingOperations.put(conversationImpl, pushFileOperation);
            }
            addOperations(pendingOperations);
        }

        // Notify push file operation was queued.
        notifyPushDescriptor(requestId, conversation, fileDescriptorImpl);
    }

    @Override
    public void pushGeolocation(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo,
                                @Nullable DescriptorId replyTo, double longitude, double latitude, double altitude,
                                double mapLongitudeDelta, double mapLatitudeDelta, @Nullable Uri localMapPath, long expiration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushGeolocation: requestId=" + requestId + " conversation=" + conversation
                    + " sendTo=" + sendTo + " replyTo=" + replyTo
                    + " longitude=" + longitude + " latitude=" + latitude + " altitude=" + altitude
                    + " mapLongitudeDelta=" + mapLongitudeDelta + " mapLatitudeDelta=" + mapLatitudeDelta
                    + " localMapPath=" + localMapPath + " expiration=" + expiration);
        }

        if (!isServiceOn()) {

            return;
        }

        if (!conversation.hasPermission(Permission.SEND_GEOLOCATION)) {
            onError(requestId, ErrorCode.NO_PERMISSION, null);

            return;
        }

        // Verify that the replyTo descriptor exists at the time we are creating the entry (it can be deleted later).
        if (replyTo != null) {
            DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(replyTo);
            if (descriptorImpl == null) {

                onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
        }

        final List<ConversationImpl> conversations = getConversations(conversation, sendTo);

        // Create one object descriptor for the conversation.
        final GeolocationDescriptorImpl descriptorImpl = (GeolocationDescriptorImpl) mServiceProvider.createDescriptor(conversation, (long id, long sequenceId, long cid) -> {
            final DescriptorId descriptorId = new DescriptorId(id, conversation.getTwincodeOutboundId(), sequenceId);
            final GeolocationDescriptorImpl result = new GeolocationDescriptorImpl(descriptorId, cid, expiration,
                    longitude, latitude, altitude, mapLongitudeDelta, mapLatitudeDelta);
            if (localMapPath != null) {
                result.setLocalMapPath(sequenceId + ".jpg");
            }

            // If we try to send on a group with no peer, mark a send failure (ie, we are the only one in the group!).
            if (conversations.isEmpty()) {
                result.setSentTimestamp(-1);
            }
            return result;
        });
        if (descriptorImpl == null) {
            onError(requestId, ErrorCode.NO_STORAGE_SPACE, null);
            return;
        }

        if (localMapPath != null) {
            saveFile(descriptorImpl.getTwincodeOutboundId(), descriptorImpl.getSequenceId(), localMapPath, "jpg", true);
        }

        if (!conversations.isEmpty()) {
            // Send the object to each peer.
            final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
            for (final ConversationImpl conversationImpl : conversations) {
                conversationImpl.touch();
                conversationImpl.setIsActive(true);

                final PushGeolocationOperation pushGeolocationOperation = new PushGeolocationOperation(conversationImpl, descriptorImpl);
                pendingOperations.put(conversationImpl, pushGeolocationOperation);
            }
            addOperations(pendingOperations);
        }

        // Notify push operation was queued.
        notifyPushDescriptor(requestId, conversation, descriptorImpl);
    }

    @Override
    public void updateGeolocation(long requestId, @NonNull Conversation conversation, @NonNull DescriptorId descriptorId,
                                  double longitude, double latitude, double altitude,
                                  double mapLongitudeDelta, double mapLatitudeDelta, @Nullable Uri localMapPath) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateGeolocation: requestId=" + requestId + " conversation=" + conversation + " descriptorId=" + descriptorId
                    + " longitude=" + longitude + " latitude=" + latitude + " altitude=" + altitude
                    + " mapLongitudeDelta=" + mapLongitudeDelta + " mapLatitudeDelta=" + mapLatitudeDelta
                    + " localMapPath=" + localMapPath);
        }

        if (!isServiceOn()) {

            return;
        }

        if (!conversation.hasPermission(Permission.SEND_GEOLOCATION)) {
            onError(requestId, ErrorCode.NO_PERMISSION, null);

            return;
        }

        DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (!(descriptorImpl instanceof GeolocationDescriptorImpl) || descriptorImpl.getDeletedTimestamp() > 0) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);

            return;
        }
        GeolocationDescriptorImpl geolocationDescriptorImpl = (GeolocationDescriptorImpl) descriptorImpl;
        geolocationDescriptorImpl.update(longitude, latitude, altitude, mapLongitudeDelta, mapLatitudeDelta);

        if (localMapPath != null) {
            Pair<ErrorCode, File> savedMap = saveFile(geolocationDescriptorImpl.getTwincodeOutboundId(), geolocationDescriptorImpl.getSequenceId(), localMapPath, "jpg", true);
            if (savedMap.second != null) {
                geolocationDescriptorImpl.setLocalMapPath(savedMap.second.getPath());
            }
        }

        final List<ConversationImpl> conversations = getConversations(conversation, descriptorImpl.getSendTo());

        // If we try to send on a group with no peer, mark a send failure (ie, we are the only one in the group!).
        if (conversations.isEmpty()) {
            geolocationDescriptorImpl.setReadTimestamp(-1);
            geolocationDescriptorImpl.setSentTimestamp(-1);
            geolocationDescriptorImpl.setReceivedTimestamp(-1);
        }

        mServiceProvider.updateDescriptor(geolocationDescriptorImpl);

        if (!conversations.isEmpty()) {
            // Send the object to each peer.
            final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
            for (final ConversationImpl conversationImpl : conversations) {
                conversationImpl.touch();
                conversationImpl.setIsActive(true);

                final PushGeolocationOperation pushGeolocationOperation = new PushGeolocationOperation(conversationImpl, geolocationDescriptorImpl);
                pendingOperations.put(conversationImpl, pushGeolocationOperation);
            }
            addOperations(pendingOperations);
        }

        // Notify update operation was queued.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(requestId, conversation, geolocationDescriptorImpl, UpdateType.CONTENT));
        }
    }

    @Override
    public void saveGeolocationMap(long requestId, @NonNull Conversation conversation, @NonNull DescriptorId descriptorId,
                                   @Nullable Uri localMapPath) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveGeolocationMap: requestId=" + requestId + " conversation=" + conversation + " descriptorId=" + descriptorId
                    + " localMapPath=" + localMapPath);
        }

        if (!isServiceOn()) {

            return;
        }

        DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (!(descriptorImpl instanceof GeolocationDescriptorImpl) || descriptorImpl.getDeletedTimestamp() > 0) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);

            return;
        }
        GeolocationDescriptorImpl geolocationDescriptorImpl = (GeolocationDescriptorImpl) descriptorImpl;

        if (localMapPath != null) {
            Pair<ErrorCode, File> savedMap = saveFile(geolocationDescriptorImpl.getTwincodeOutboundId(), geolocationDescriptorImpl.getSequenceId(), localMapPath, "jpg", true);
            if (savedMap.second != null) {
                geolocationDescriptorImpl.setLocalMapPath(savedMap.second.getPath());
            }
        }

        mServiceProvider.updateDescriptor(geolocationDescriptorImpl);

        // Notify update operation was queued.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(requestId, conversation, geolocationDescriptorImpl, UpdateType.CONTENT));
        }
    }

    @Override
    public void pushTwincode(long requestId, @NonNull Conversation conversation, @Nullable UUID sendTo, @Nullable DescriptorId replyTo,
                             @NonNull UUID twincodeId, @NonNull UUID schemaId, @Nullable String publicKey, boolean copyAllowed, long expireTimeout) {
        if (DEBUG) {
            Log.d(LOG_TAG, "pushTwincode: requestId=" + requestId + " conversation=" + conversation
                    + " sendTo=" + sendTo + " replyTo=" + replyTo + " twincodeId=" + twincodeId
                    + " schemaId=" + schemaId + " publicKey=" + publicKey + " copyAllowed=" + copyAllowed
                    + " expireTimeout=" + expireTimeout);
        }

        if (!isServiceOn()) {

            return;
        }

        if (!conversation.hasPermission(Permission.SEND_TWINCODE)) {
            onError(requestId, ErrorCode.NO_PERMISSION, null);

            return;
        }

        // Verify that the replyTo descriptor exists at the time we are creating the entry (it can be deleted later).
        if (replyTo != null) {
            DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(replyTo);
            if (descriptorImpl == null) {

                onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }
        }

        // If we have a group member, keep only the conversation that matches the group member.
        final List<ConversationImpl> conversations = getConversations(conversation, sendTo);

        // Create one twincode descriptor for the conversation.
        final TwincodeDescriptorImpl descriptorImpl = (TwincodeDescriptorImpl) mServiceProvider.createDescriptor(conversation, (long id, long sequenceId, long cid) -> {
            final DescriptorId descriptorId = new DescriptorId(id, conversation.getTwincodeOutboundId(), sequenceId);
            final TwincodeDescriptorImpl result = new TwincodeDescriptorImpl(descriptorId, cid,
                    expireTimeout, sendTo, replyTo, twincodeId, schemaId, publicKey, copyAllowed);

            // If we try to send on a group with no peer, mark a send failure (ie, we are the only one in the group!).
            if (conversations.isEmpty()) {
                result.setSentTimestamp(-1);
            }
            return result;
        });
        if (descriptorImpl == null) {
            onError(requestId, ErrorCode.NO_STORAGE_SPACE, null);
            return;
        }

        if (!conversations.isEmpty()) {
            // Send the object to each selected peer.
            final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
            for (final ConversationImpl conversationImpl : conversations) {
                conversationImpl.touch();
                conversationImpl.setIsActive(true);

                final PushTwincodeOperation pushTwincodeOperation = new PushTwincodeOperation(conversationImpl, descriptorImpl);
                pendingOperations.put(conversationImpl, pushTwincodeOperation);
            }
            addOperations(pendingOperations);
        }

        // Notify push operation was queued.
        notifyPushDescriptor(requestId, conversation, descriptorImpl);
    }

    @Override
    public void updateDescriptor(long requestId, @NonNull DescriptorId descriptorId, @Nullable String message,
                                 @Nullable Boolean copyAllowed, @Nullable Long expiration) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateDescriptor: requestId=" + requestId + " descriptorId=" + descriptorId
                    + " message=" + message + " copyAllowed=" + copyAllowed + " expiration=" + expiration);
        }

        if (!isServiceOn()) {

            return;
        }

        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptorImpl == null || descriptorImpl.getDeletedTimestamp() > 0) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }
        final Conversation conversation = mServiceProvider.loadConversationWithId(descriptorImpl.getConversationId());
        if (conversation == null) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        // The descriptor must have been sent by the current device.
        if (!descriptorImpl.getTwincodeOutboundId().equals(conversation.getTwincodeOutboundId())) {
            onError(requestId, ErrorCode.NO_PERMISSION, null);
            return;
        }

        final int updateFlags;
        if (descriptorImpl instanceof ObjectDescriptorImpl) {
            final ObjectDescriptorImpl objectDescriptor = (ObjectDescriptorImpl) descriptorImpl;
            if (!objectDescriptor.setMessage(message)) {
                message = null;
            }
            if (!objectDescriptor.setCopyAllowed(copyAllowed)) {
                copyAllowed = null;
            }
            if (!objectDescriptor.setExpireTimeout(expiration)) {
                expiration = null;
            }
            updateFlags = UpdateDescriptorOperation.buildFlags(message, copyAllowed, expiration);
            if (updateFlags != 0) {
                objectDescriptor.setEdited();
            }

        } else if (descriptorImpl instanceof FileDescriptorImpl) {
            final FileDescriptorImpl fileDescriptor = (FileDescriptorImpl) descriptorImpl;

            if (!fileDescriptor.setCopyAllowed(copyAllowed)) {
                copyAllowed = null;
            }
            if (!fileDescriptor.setExpireTimeout(expiration)) {
                expiration = null;
            }
            updateFlags = UpdateDescriptorOperation.buildFlags(null, copyAllowed, expiration);

        } else {
            onError(requestId, ErrorCode.BAD_REQUEST, null);
            return;
        }

        final UpdateType type = message != null ? UpdateType.CONTENT : UpdateType.PROTECTION;
        if (updateFlags != 0) {
            // If we have a group member, keep only the conversation that matches the group member.
            final List<ConversationImpl> conversations = getConversations(conversation, descriptorImpl.getSendTo());

            descriptorImpl.setUpdatedTimestamp(System.currentTimeMillis());
            mServiceProvider.updateDescriptor(descriptorImpl);

            if (!conversations.isEmpty()) {
                // Send the update to each selected peer.
                final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
                for (final ConversationImpl conversationImpl : conversations) {
                    conversationImpl.touch();
                    conversationImpl.setIsActive(true);

                    final UpdateDescriptorOperation updateDescriptorOperation = new UpdateDescriptorOperation(conversationImpl, descriptorImpl, updateFlags);
                    pendingOperations.put(conversationImpl, updateDescriptorOperation);
                }
                addOperations(pendingOperations);
            }
        }

        // Notify that the descriptor was updated.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(requestId, conversation, descriptorImpl, type));
        }
    }

    @Override
    public void startCall(long requestId, @NonNull RepositoryObject subject, boolean isVideo, boolean isIncoming) {
        if (DEBUG) {
            Log.d(LOG_TAG, "startCall: requestId=" + requestId + " subject=" + subject
                    + " isVideo=" + isVideo + " isIncoming=" + isIncoming);
        }

        if (!isServiceOn()) {

            return;
        }

        final Conversation conversation = getOrCreateConversationImpl(subject, true);
        if (conversation == null) {
            onError(requestId, ErrorCode.NO_STORAGE_SPACE, null);
            return;
        }

        // Create one descriptor for the conversation.
        final DescriptorImpl callDescriptorImpl = mServiceProvider.createDescriptor(conversation, (long id, long sequenceId, long cid) -> {
            final DescriptorId descriptorId = new DescriptorId(id, conversation.getTwincodeOutboundId(), sequenceId);
            return new CallDescriptorImpl(descriptorId, cid, isVideo, isIncoming);
        });
        if (callDescriptorImpl == null) {
            onError(requestId, ErrorCode.NO_STORAGE_SPACE, null);
            return;
        }

        if (isIncoming) {
            // Similar to the reception of a message/file, call onPopDescriptor() when we receive a call.
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onPopDescriptor(requestId, conversation, callDescriptorImpl));
            }
        } else {
            // And call onPushDescriptor() when we are the initiator of the call.
            notifyPushDescriptor(requestId, conversation, callDescriptorImpl);
        }
    }

    @Override
    public void acceptCall(long requestId, @NonNull UUID twincodeOutboundId, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "acceptCall: requestId=" + requestId + " twincodeOutboundId=" + twincodeOutboundId + " descriptorId=" + descriptorId);
        }
        if (!isServiceOn()) {

            return;
        }

        DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (!(descriptorImpl instanceof CallDescriptorImpl) || descriptorImpl.getDeletedTimestamp() > 0) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);

            return;
        }
        final Conversation conversation = mServiceProvider.loadConversationWithId(descriptorImpl.getConversationId());
        if (conversation == null) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);

            return;
        }

        CallDescriptorImpl callDescriptorImpl = (CallDescriptorImpl) descriptorImpl;
        callDescriptorImpl.setAcceptedCall();

        mServiceProvider.updateDescriptor(callDescriptorImpl);

        // Notify that the call descriptor timestamps was updated.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(requestId, conversation, callDescriptorImpl, UpdateType.TIMESTAMPS));
        }
    }

    @Override
    public void terminateCall(long requestId, @NonNull UUID twincodeOutboundId, @NonNull DescriptorId descriptorId, @NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "acceptCall: requestId=" + requestId + " twincodeOutboundId=" + twincodeOutboundId + " descriptorId=" + descriptorId);
        }
        if (!isServiceOn()) {

            return;
        }

        DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (!(descriptorImpl instanceof CallDescriptorImpl) || descriptorImpl.getDeletedTimestamp() > 0) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);

            return;
        }
        final Conversation conversation = mServiceProvider.loadConversationWithId(descriptorImpl.getConversationId());
        if (conversation == null) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, null);

            return;
        }

        CallDescriptorImpl callDescriptorImpl = (CallDescriptorImpl) descriptorImpl;
        callDescriptorImpl.setTerminateReason(terminateReason);

        mServiceProvider.updateDescriptor(callDescriptorImpl);

        // Notify that the call descriptor content was updated.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(requestId, conversation, callDescriptorImpl, UpdateType.CONTENT));
        }
    }

    @Override
    public void acceptPushTwincode(@NonNull UUID schemaId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "acceptPushTwincode: schemaId=" + schemaId);
        }

        if (!isServiceOn()) {

            return;
        }

        mAcceptedPushTwincode.add(schemaId);
    }

    /**
     * Get the descriptor that was sent with the given descriptor Id.
     *
     * @param descriptorId the descriptor id.
     * @return the descriptor or null if it is not valid or does not exist.
     */
    @Override
    @Nullable
    public Descriptor getDescriptor(@NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDescriptor: descriptorId=" + descriptorId);
        }

        if (!isServiceOn()) {

            return null;
        }

        // Retrieve the descriptor.
        return mServiceProvider.loadDescriptorImpl(descriptorId);
    }

    /**
     * Get the geolocation that was sent with the given descriptor Id.
     *
     * @param descriptorId the geolocation descriptor id.
     */
    @Override
    public void getGeolocation(@NonNull DescriptorId descriptorId, @NonNull Consumer<GeolocationDescriptor> consumer) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getGeolocation: descriptorId=" + descriptorId);
        }

        mTwinlifeExecutor.execute(() -> {
            if (!isServiceOn()) {
                consumer.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
                return;
            }

            // Retrieve the descriptor for the geolocation.
            final DescriptorImpl descriptor = mServiceProvider.loadDescriptorImpl(descriptorId);
            if (!(descriptor instanceof GeolocationDescriptor)) {
                consumer.onGet(ErrorCode.BAD_REQUEST, null);
                return;
            }

            if (descriptor.getDeletedTimestamp() > 0) {
                consumer.onGet(ErrorCode.ITEM_NOT_FOUND, null);
                return;
            }

            consumer.onGet(ErrorCode.SUCCESS, (GeolocationDescriptor) descriptor);
        });

    }

    @Override
    public void markDescriptorRead(long requestId, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "markDescriptorRead: requestId=" + requestId + " descriptorId=" + descriptorId);
        }

        if (!isServiceOn()) {

            return;
        }

        // Retrieve the descriptor for the conversation.
        final UUID twincodeOutboundId = descriptorId.twincodeOutboundId;
        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptorImpl == null) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, descriptorId.toString());

            return;
        }
        final Conversation conversation = mServiceProvider.loadConversationWithId(descriptorImpl.getConversationId());
        if (conversation == null) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, descriptorId.toString());

            return;
        }

        // A call descriptor is local only.
        if (descriptorImpl.getType() == Descriptor.Type.CALL_DESCRIPTOR) {
            onError(requestId, ErrorCode.BAD_REQUEST, null);

            return;
        }

        // Update the descriptor for the conversation.
        descriptorImpl.setReadTimestamp(System.currentTimeMillis());
        mServiceProvider.updateDescriptorImplTimestamps(descriptorImpl);

        // Send the descriptor update to the peer that sent us the message.
        final List<ConversationImpl> conversations = getConversations(conversation, null);
        for (final ConversationImpl conversationImpl : conversations) {
            conversationImpl.touch();

            if (!conversation.isGroup() || twincodeOutboundId.equals(conversationImpl.getPeerTwincodeOutboundId())) {
                final UpdateDescriptorTimestampOperation updateDescriptorTimestampOperation = new UpdateDescriptorTimestampOperation(conversationImpl,
                        UpdateDescriptorTimestampType.READ, descriptorId, descriptorImpl.getReadTimestamp());
                mServiceProvider.storeOperation(updateDescriptorTimestampOperation);
                if (descriptorImpl.getExpireTimeout() > 0) {
                    mScheduler.addOperation(conversationImpl, updateDescriptorTimestampOperation);
                } else {
                    mScheduler.addDeferrableOperation(conversationImpl, updateDescriptorTimestampOperation);
                }
            }
        }

        // Notify descriptor update operation was queued.
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onMarkDescriptorRead(requestId, conversation, descriptorImpl));
        }
    }

    @Override
    public void markDescriptorDeleted(long requestId, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "markDescriptorDeleted: requestId=" + requestId + " descriptorId=" + descriptorId);
        }

        if (!isServiceOn()) {

            return;
        }

        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptorImpl == null) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, descriptorId.toString());

            return;
        }
        final Conversation conversation = mServiceProvider.loadConversationWithId(descriptorImpl.getConversationId());
        if (conversation == null) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, descriptorId.toString());

            return;
        }

        // A call and clear descriptor must be deleted immediately: they are local only.
        final Descriptor.Type type = descriptorImpl.getType();
        if (type == Descriptor.Type.CALL_DESCRIPTOR || type == Descriptor.Type.CLEAR_DESCRIPTOR) {

            descriptorImpl.setDeletedTimestamp(System.currentTimeMillis());
            descriptorImpl.setPeerDeletedTimestamp(descriptorImpl.getDeletedTimestamp());
            deleteConversationDescriptor(requestId, conversation, descriptorImpl);
            return;
        }

        // If this is an invitation, we need some specific cleaning and we do the withdraw invitation process.
        boolean needPeerUpdate;
        if (descriptorImpl instanceof InvitationDescriptor) {
            withdrawInviteGroup(requestId, (InvitationDescriptor) descriptorImpl);
            needPeerUpdate = false;
        } else {
            needPeerUpdate = descriptorImpl.getSentTimestamp() > 0;

            // If the peer has deleted the descriptor, we can delete it immediately.
            if (descriptorImpl.getPeerDeletedTimestamp() != 0 && !conversation.isGroup()) {
                needPeerUpdate = false;
            }

            // Update the deleted timestamp the first time it is deleted (the user can perform the action several times).
            if (descriptorImpl.getDeletedTimestamp() == 0) {
                descriptorImpl.setDeletedTimestamp(System.currentTimeMillis());
                if (!needPeerUpdate && descriptorImpl.getPeerDeletedTimestamp() == 0) {
                    descriptorImpl.setPeerDeletedTimestamp(descriptorImpl.getDeletedTimestamp());
                }
                mServiceProvider.updateDescriptorImplTimestamps(descriptorImpl);
            }
        }

        if (needPeerUpdate) {

            // Send the mark deleted descriptor to each peer.
            final List<ConversationImpl> conversations = getConversations(conversation, descriptorImpl.getSendTo());
            if (!conversations.isEmpty()) {
                final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
                for (final ConversationImpl conversationImpl : conversations) {
                    conversationImpl.touch();

                    UpdateDescriptorTimestampOperation updateDescriptorTimestampOperation = new UpdateDescriptorTimestampOperation(conversationImpl,
                            UpdateDescriptorTimestampType.DELETE, descriptorId, descriptorImpl.getDeletedTimestamp());
                    pendingOperations.put(conversationImpl, updateDescriptorTimestampOperation);
                }
                addOperations(pendingOperations);
            }

            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onMarkDescriptorDeleted(requestId, conversation, descriptorImpl));
            }

        } else {
            deleteConversationDescriptor(requestId, conversation, descriptorImpl);
        }
    }

    /**
     * Set the annotation with the value on the descriptor.  If the annotation already exists, the value
     * is updated.
     *
     * @param descriptorId the descriptor to mark.
     * @param type the annotation type (except FORWARD and FORWARDED which are managed internally).
     * @param value the value to associate.
     * @return SUCCESS if the annotation is updated.
     */
    @NonNull
    @Override
    public ErrorCode setAnnotation(@NonNull DescriptorId descriptorId,
                                   @NonNull AnnotationType type, int value) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setAnnotation: descriptorId=" + descriptorId
                    + " type=" + type + " value=" + value);
        }

        if (!isServiceOn()) {

            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        // The FORWARD and FORWARDED are annotations managed internally.
        if (type == AnnotationType.FORWARD || type == AnnotationType.FORWARDED) {

            return ErrorCode.BAD_REQUEST;
        }

        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptorImpl == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }
        final Conversation conversation = mServiceProvider.loadConversationWithId(descriptorImpl.getConversationId());
        if (conversation == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        // The Call and Clear descriptors are local only.
        final Descriptor.Type descriptorType = descriptorImpl.getType();
        if (descriptorType == Descriptor.Type.CALL_DESCRIPTOR || descriptorType == Descriptor.Type.CLEAR_DESCRIPTOR) {

            return ErrorCode.BAD_REQUEST;
        }

        final boolean modified = mServiceProvider.setAnnotation(descriptorImpl, type, value);
        if (!modified) {

            return ErrorCode.SUCCESS;
        }

        // Something was modified on the descriptor, prepare to send an UpdateAnnotationsIQ.
        final List<ConversationImpl> conversations = getConversations(conversation, null);
        if (!conversations.isEmpty()) {
            final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
            for (final ConversationImpl conversationImpl : conversations) {
                conversationImpl.touch();

                final UpdateAnnotationsOperation updateAnnotationsOperation = new UpdateAnnotationsOperation(conversationImpl,
                        descriptorId);
                pendingOperations.put(conversationImpl, updateAnnotationsOperation);
            }
            addOperations(pendingOperations);
        }

        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID,
                    conversation, descriptorImpl, UpdateType.LOCAL_ANNOTATIONS));
        }

        return ErrorCode.SUCCESS;
    }

    /**
     * Remove the annotation on the descriptor.  The operation can only remove the annotations that the current
     * device has set.
     *
     * @param descriptorId the descriptor id to unmark.
     * @param type the annotation type to remove (except FORWARD and FORWARDED).
     * @return SUCCESS if the annotation is removed.
     */
    @NonNull
    @Override
    public ErrorCode deleteAnnotation(@NonNull DescriptorId descriptorId, @NonNull AnnotationType type) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteAnnotation: descriptorId=" + descriptorId
                    + " type=" + type);
        }

        if (!isServiceOn()) {

            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        // The FORWARD and FORWARDED are annotations managed internally.
        if (type == AnnotationType.FORWARD || type == AnnotationType.FORWARDED) {

            return ErrorCode.BAD_REQUEST;
        }

        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptorImpl == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }
        final Conversation conversation = mServiceProvider.loadConversationWithId(descriptorImpl.getConversationId());
        if (conversation == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        // A call and clear descriptor must be deleted immediately: they are local only.
        final Descriptor.Type descriptorType = descriptorImpl.getType();
        if (descriptorType == Descriptor.Type.CALL_DESCRIPTOR || descriptorType == Descriptor.Type.CLEAR_DESCRIPTOR) {

            return ErrorCode.BAD_REQUEST;
        }

        final boolean modified = mServiceProvider.deleteAnnotation(descriptorImpl, type);
        if (!modified) {

            return ErrorCode.SUCCESS;
        }

        // Something was modified on the descriptor, prepare to send an UpdateAnnotationsIQ.
        final List<ConversationImpl> conversations = getConversations(conversation, null);
        if (!conversations.isEmpty()) {
            final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
            for (final ConversationImpl conversationImpl : conversations) {
                conversationImpl.touch();

                final UpdateAnnotationsOperation updateAnnotationsOperation = new UpdateAnnotationsOperation(conversationImpl,
                        descriptorId);
                pendingOperations.put(conversationImpl, updateAnnotationsOperation);
            }
            addOperations(pendingOperations);
        }

        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID,
                    conversation, descriptorImpl, UpdateType.LOCAL_ANNOTATIONS));
        }

        return ErrorCode.SUCCESS;
    }

    /**
     * Toggle the descriptor annotation:
     * <p>
     * - If the annotation with the same value exists, it is removed,
     * - If the annotation with another value exists, the annotation is updated with the new value,
     * - If the annotation does not exist, it is added as a local annotation.
     * <p>
     * When the annotation is inserted, modified or removed, the annotation update is propagated to the peers.
     *
     * @param descriptorId the descriptor to mark.
     * @param type the annotation type (except FORWARD and FORWARDED which are managed internally).
     * @param value the value to associate.
     * @return SUCCESS if the annotation is updated.
     */
    @NonNull
    @Override
    public ErrorCode toggleAnnotation(@NonNull DescriptorId descriptorId,
                                      @NonNull AnnotationType type, int value) {
        if (DEBUG) {
            Log.d(LOG_TAG, "toggleAnnotation: descriptorId=" + descriptorId
                    + " type=" + type + " value=" + value);
        }

        if (!isServiceOn()) {

            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        // The FORWARD and FORWARDED are annotations managed internally.
        if (type == AnnotationType.FORWARD || type == AnnotationType.FORWARDED) {

            return ErrorCode.BAD_REQUEST;
        }

        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptorImpl == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }
        final Conversation conversation = mServiceProvider.loadConversationWithId(descriptorImpl.getConversationId());
        if (conversation == null) {

            return ErrorCode.ITEM_NOT_FOUND;
        }

        // The Call and Clear descriptors are local only.
        final Descriptor.Type descriptorType = descriptorImpl.getType();
        if (descriptorType == Descriptor.Type.CALL_DESCRIPTOR || descriptorType == Descriptor.Type.CLEAR_DESCRIPTOR) {

            return ErrorCode.BAD_REQUEST;
        }

        final boolean modified = mServiceProvider.toggleAnnotation(descriptorImpl, type, value);
        if (!modified) {

            return ErrorCode.SUCCESS;
        }

        // Something was modified on the descriptor, prepare to send an UpdateAnnotationsIQ.
        final List<ConversationImpl> conversations = getConversations(conversation, null);
        if (!conversations.isEmpty()) {
            final Map<ConversationImpl, Object> pendingOperations = new HashMap<>();
            for (final ConversationImpl conversationImpl : conversations) {
                conversationImpl.touch();

                final UpdateAnnotationsOperation updateAnnotationsOperation = new UpdateAnnotationsOperation(conversationImpl,
                        descriptorId);
                pendingOperations.put(conversationImpl, updateAnnotationsOperation);
            }
            addOperations(pendingOperations);
        }

        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID,
                    conversation, descriptorImpl, UpdateType.LOCAL_ANNOTATIONS));
        }

        return ErrorCode.SUCCESS;
    }

    @Nullable
    public Map<TwincodeOutbound, DescriptorAnnotation> listAnnotations(@NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listAnnotations: descriptorId=" + descriptorId);
        }

        if (!isServiceOn()) {

            return null;
        }

        return mServiceProvider.listAnnotations(descriptorId);
    }

    @Override
    public void deleteDescriptor(long requestId, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteDescriptor: requestId=" + requestId + " descriptorId=" + descriptorId);
        }

        if (!isServiceOn()) {

            return;
        }

        // Retrieve the descriptor for the conversation.
        final UUID twincodeOutboundId = descriptorId.twincodeOutboundId;
        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (descriptorImpl == null) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, descriptorId.toString());

            return;
        }

        final Conversation conversation = mServiceProvider.loadConversationWithId(descriptorImpl.getConversationId());
        if (conversation == null) {
            onError(requestId, ErrorCode.ITEM_NOT_FOUND, descriptorId.toString());

            return;
        }

        // A call or clear descriptor must be deleted immediately: they are local only.
        final Descriptor.Type type = descriptorImpl.getType();
        if (type == Descriptor.Type.CALL_DESCRIPTOR || type == Descriptor.Type.CLEAR_DESCRIPTOR) {

            descriptorImpl.setDeletedTimestamp(System.currentTimeMillis());
            descriptorImpl.setPeerDeletedTimestamp(descriptorImpl.getDeletedTimestamp());
            deleteConversationDescriptor(requestId, conversation, descriptorImpl);
            return;
        }

        // Send the PEER_DELETE update to the peer that sent us the message.
        if (descriptorImpl.getDeletedTimestamp() == 0) {
            final List<ConversationImpl> conversations = getConversations(conversation, null);
            for (final ConversationImpl conversationImpl : conversations) {
                if (!conversation.isGroup() || twincodeOutboundId.equals(conversationImpl.getPeerTwincodeOutboundId())) {
                    conversationImpl.touch();

                    UpdateDescriptorTimestampOperation updateDescriptorTimestampOperation = new UpdateDescriptorTimestampOperation(conversationImpl,
                            UpdateDescriptorTimestampType.PEER_DELETE, descriptorId, System.currentTimeMillis());
                    mServiceProvider.storeOperation(updateDescriptorTimestampOperation);
                    if (descriptorImpl.getExpireTimeout() > 0) {
                        mScheduler.addOperation(conversationImpl, updateDescriptorTimestampOperation);
                    } else {
                        mScheduler.addDeferrableOperation(conversationImpl, updateDescriptorTimestampOperation);
                    }
                }
            }
        }

        deleteConversationDescriptor(requestId, conversation, descriptorImpl);
    }

    /**
     * Get the thumbnail associated with the image or video descriptor.
     *
     * @param descriptor the image or video descriptor.
     * @return the optional thumbnail bitmap.
     */
    @Nullable
    public Bitmap getDescriptorThumbnail(@NonNull FileDescriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDescriptorThumbnail: descriptor=" + descriptor);
        }

        if (!(descriptor instanceof FileDescriptorImpl)) {

            return null;
        }

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {

            return null;
        }

        FileDescriptorImpl descriptorImpl = (FileDescriptorImpl) descriptor;
        return descriptorImpl.getThumbnail(filesDir);
    }

    /**
     * Get the thumbnail file  associated with the image or video descriptor.
     *
     * @param descriptor the image or video descriptor.
     * @return the optional thumbnail file.
     */
    @Nullable
    public File getDescriptorThumbnailFile(@NonNull FileDescriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDescriptorThumbnailFile: descriptor=" + descriptor);
        }

        if (!(descriptor instanceof FileDescriptorImpl)) {

            return null;
        }

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {

            return null;
        }

        FileDescriptorImpl descriptorImpl = (FileDescriptorImpl) descriptor;
        File file = descriptorImpl.getThumbnailFile(filesDir);
        if (file != null && !file.exists()) {
            file = null;
        }
        return file;
    }

    //
    // Group operations
    //

    /**
     * Create a new group conversation.
     *
     * @param group              the group repository object.
     * @param owner              when set the group is created in the JOINED state otherwise it is in the CREATED state.
     * @return the group conversation instance or null.
     */
    @Nullable
    @Override
    public GroupConversation createGroup(@NonNull RepositoryObject group, boolean owner) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createGroup: group=" + group + " owner=" + owner);
        }

        if (!isServiceOn()) {
            return null;
        }

        return mGroupManager.createGroup(group, owner);
    }

    @Override
    public ErrorCode inviteGroup(long requestId, @NonNull Conversation conversation, @NonNull RepositoryObject group, @NonNull String name) {
        if (DEBUG) {
            Log.d(LOG_TAG, "inviteGroup: requestId=" + requestId + " conversation=" + conversation + " group=" + group + " name=" + name);
        }

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
        return mGroupManager.inviteGroup(requestId, conversation, group, name);
    }

    @Override
    public ErrorCode withdrawInviteGroup(long requestId, @NonNull InvitationDescriptor descriptor) {
        if (DEBUG) {
            Log.d(LOG_TAG, "withdrawInviteGroup: requestId=" + requestId + " invitation=" + descriptor);
        }

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        return mGroupManager.withdrawInviteGroup(requestId, descriptor);
    }

    @Override
    public InvitationDescriptor getInvitation(@NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getInvitation: descriptorId=" + descriptorId);
        }

        if (!isServiceOn()) {

            return null;
        }

        // Retrieve the descriptor for the invitation.
        final DescriptorImpl descriptor = mServiceProvider.loadDescriptorImpl(descriptorId);
        if (!(descriptor instanceof InvitationDescriptorImpl)) {

            return null;
        }

        return (InvitationDescriptorImpl) descriptor;
    }

    @Override
    public ErrorCode joinGroup(long requestId, @NonNull DescriptorId descriptorId, @Nullable RepositoryObject group) {
        if (DEBUG) {
            Log.d(LOG_TAG, "joinGroup: requestId=" + requestId + " descriptorId=" + descriptorId + " group=" + group);
        }

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        return mGroupManager.joinGroup(requestId, descriptorId, group);
    }

    @Override
    public ErrorCode leaveGroup(long requestId, @NonNull RepositoryObject group, @NonNull UUID memberTwincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "leaveGroup: requestId=" + requestId + " group=" + group + " memberTwincodeId=" + memberTwincodeId);
        }

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        return mGroupManager.leaveGroup(requestId, group, memberTwincodeId);
    }

    @Override
    public ErrorCode subscribeGroup(long requestId, @NonNull RepositoryObject group,
                                    @NonNull TwincodeOutbound memberTwincode, long permissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "subscribeGroup: requestId=" + requestId + " group=" + group
                    + " memberTwincode=" + memberTwincode
                    + " permissions=" + permissions);
        }
        EventMonitor.info(LOG_TAG, "Subscribe group as ", memberTwincode);

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        return mGroupManager.subscribeGroup(requestId, group, memberTwincode, permissions);
    }

    @Override
    public ErrorCode registeredGroup(long requestId, @NonNull RepositoryObject group, @NonNull TwincodeOutbound adminTwincode,
                                     long adminPermissions, long permissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "registeredGroup: requestId=" + requestId + " group=" + group
                    + " adminTwincode=" + adminTwincode + " permissions=" + permissions);
        }
        EventMonitor.info(LOG_TAG, "Registered in group with admin ", adminTwincode);

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        return mGroupManager.registeredGroup(requestId, group, adminTwincode, adminPermissions, permissions);
    }

    @Override
    @NonNull
    public ErrorCode setPermissions(@NonNull RepositoryObject group, @Nullable UUID memberTwincodeId, long permissions) {
        if (DEBUG) {
            Log.d(LOG_TAG, "setPermissions: group=" + group + " memberTwincodeId=" + memberTwincodeId);
        }

        if (!isServiceOn()) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }

        return mGroupManager.setPermissions(group, memberTwincodeId, permissions);
    }

    @NonNull
    public Map<UUID, InvitationDescriptor> listPendingInvitations(@NonNull RepositoryObject group) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listPendingInvitations: group=" + group);
        }

        return mServiceProvider.listPendingInvitations(group);
    }

    /**
     * Called by the ConversationServiceProvider when one or several conversation are deleted.
     * We must notify the scheduler and remove these conversations and drop their operations.
     *
     * @param list the list of conversations.
     */
    void notifyDeletedConversation(@NonNull List<ConversationImpl> list) {
        if (DEBUG) {
            Log.d(LOG_TAG, "notifyDeletedConversation: list=" + list);
        }

        mExecutor.execute(() -> {
            for (ConversationImpl conversation : list) {
                mScheduler.deleteConversation(conversation);

                // If there is a P2P connection for this conversation, close it.
                // We must close it with SUCCESS to inform the peer (if necessary),
                // that everything is ok from our side, even if the P2P connection
                // did not succeed: we want to avoid that the peer triggers a
                // twincode invocation since on our side the object is dead now.
                final ConversationConnection connection = conversation.getConnection();
                if (connection != null) {
                    closeConnection(connection, TerminateReason.SUCCESS);
                }
            }
        });
    }

    //
    // Private Methods
    //
    @NonNull
    static List<ConversationImpl> getConversations(@NonNull Conversation conversation, @Nullable UUID sendTo) {
        if (conversation instanceof ConversationImpl) {
            final List<ConversationImpl> result = new ArrayList<>();
            result.add((ConversationImpl) conversation);
            return result;

        } else if (conversation instanceof GroupConversationImpl) {
            final GroupConversationImpl groupConversation = (GroupConversationImpl) conversation;

            return groupConversation.getConversations(sendTo);
        } else {
            return new ArrayList<>();
        }
    }

    private long newSequenceId() {
        if (DEBUG) {
            Log.d(LOG_TAG, "newSequenceId");
        }

        return mServiceProvider.newSequenceId();
    }

    /**
     * Methods used by the Operation classes for the implementation of execute().
     */

    @Nullable
    DescriptorImpl loadDescriptorWithId(long descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadDescriptorWithId descriptorId=" + descriptorId);
        }

        return mServiceProvider.loadDescriptorWithId(descriptorId);
    }

    void updateDescriptorImplTimestamps(@NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateDescriptorImplTimestamps descriptorImpl=" + descriptorImpl);
        }

        mServiceProvider.updateDescriptorImplTimestamps(descriptorImpl);
    }

    @NonNull
    List<DescriptorAnnotation> loadLocalAnnotations(@NonNull Conversation conversation, @NonNull DescriptorId descriptorId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadLocalAnnotations conversation=" + conversation + " descriptorId=" + descriptorId);
        }

        return mServiceProvider.loadLocalAnnotations(conversation, descriptorId);
    }

    void deleteFileDescriptor(@NonNull ConversationConnection connection, @NonNull FileDescriptorImpl fileDescriptorImpl,
                              @NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteFileDescriptor connection=" + connection + " fileDescriptorImpl=" + fileDescriptorImpl);
        }

        mScheduler.removeOperation(operation);

        final ConversationImpl conversationImpl = connection.getConversation();

        // File was removed, send a delete descriptor operation.
        UpdateDescriptorTimestampOperation updateDescriptorTimestampOperation = new UpdateDescriptorTimestampOperation(conversationImpl,
                UpdateDescriptorTimestampOperation.UpdateDescriptorTimestampType.DELETE, fileDescriptorImpl.getDescriptorId(),
                fileDescriptorImpl.getDeletedTimestamp());
        mServiceProvider.storeOperation(updateDescriptorTimestampOperation);
        mScheduler.addOperation(conversationImpl, updateDescriptorTimestampOperation);
    }

    @NonNull
    ErrorCode invokeJoinOperation(@NonNull ConversationImpl conversationImpl, @NonNull GroupJoinOperation groupOperation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "invokeJoinOperation conversationImpl=" + conversationImpl + " groupOperation=" + groupOperation);
        }

        return mGroupManager.invokeJoinOperation(conversationImpl, groupOperation);
    }

    @NonNull
    ErrorCode invokeAddMemberOperation(@NonNull ConversationImpl conversationImpl, @NonNull GroupJoinOperation groupOperation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "invokeAddMemberOperation conversationImpl=" + conversationImpl + " groupOperation=" + groupOperation);
        }

        return mGroupManager.invokeAddMemberOperation(conversationImpl, groupOperation);
    }

    @NonNull
    ErrorCode invokeLeaveOperation(@NonNull ConversationImpl conversationImpl, @NonNull GroupLeaveOperation groupOperation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "invokeLeaveOperation conversationImpl=" + conversationImpl + " groupOperation=" + groupOperation);
        }

        return mGroupManager.invokeLeaveOperation(conversationImpl, groupOperation);
    }

    @Nullable
    SignatureInfoIQ createSignature(@NonNull ConversationConnection connection, @NonNull UUID groupTwincodeId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createSignature connection=" + connection + " groupTwincodeId=" + groupTwincodeId);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final GroupConversationImpl groupConversation = mServiceProvider.findGroupConversation(groupTwincodeId);
        final TwincodeOutbound memberTwincode = groupConversation == null ? null : groupConversation.getTwincodeOutbound();
        final TwincodeOutbound peerTwincode = conversationImpl.getPeerTwincodeOutbound();
        return memberTwincode == null || peerTwincode == null ? null : mCryptoService.getSignatureInfoIQ(memberTwincode, peerTwincode, false);
    }

    @NonNull
    ErrorCode operationNotSupported(@NonNull ConversationConnection connection, @Nullable DescriptorImpl descriptorImpl) {
        if (descriptorImpl != null) {
            descriptorImpl.setReceivedTimestamp(-1);
            descriptorImpl.setReadTimestamp(-1);
            updateDescriptor(descriptorImpl, connection.getConversation());
        }

        return ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER;
    }

    @Override
    public void onAcceptPeerConnection(@NonNull UUID peerConnectionId, @NonNull Offer offer) {

    }

    @Override
    public void onChangeConnectionState(@NonNull UUID peerConnectionId, @NonNull PeerConnectionService.ConnectionState state) {

    }

    @Override
    public void onAddLocalAudioTrack(@NonNull UUID peerConnectionId, @NonNull RtpSender sender, @NonNull AudioTrack audioTrack) {

    }

    @Override
    public void onAddRemoteMediaStreamTrack(@NonNull UUID peerConnectionId, @NonNull MediaStreamTrack mediaStream) {

    }

    @Override
    public void onRemoveRemoteTrack(@NonNull UUID peerConnectionId, @NonNull String trackId) {

    }

    @Override
    public void onPeerHoldCall(@NonNull UUID peerConnectionId){
        //NOOP
    }

    @Override
    public void onPeerResumeCall(@NonNull UUID peerConnectionId){
        //NOOP
    }

    @Override
    public void onTerminatePeerConnection(@NonNull UUID peerConnectionId, @NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onTerminatePeerConnection: peerConnectionId=" + peerConnectionId + " terminateReason=" + terminateReason);
        }

        final ConversationConnection conversation;
        synchronized (mPeerConnectionLock) {
            conversation = mPeerConnectionId2Conversation.remove(peerConnectionId);
        }
        if (conversation == null) {

            return;
        }
        close(conversation, false, peerConnectionId, terminateReason);
    }

    @Override
    @NonNull
    public DataChannelConfiguration getConfiguration(@NonNull UUID peerConnectionId, @NonNull SdpEncryptionStatus encryptionStatus) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConfiguration: peerConnectionId=" + peerConnectionId + " encryptionStatus=" + encryptionStatus);
        }

        return new DataChannelConfiguration(VERSION, encryptionStatus == SdpEncryptionStatus.NONE);
    }

    @Override
    public void onDataChannelOpen(@NonNull UUID peerConnectionId, @NonNull String peerVersion, boolean leadingPadding) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDataChannelOpen: peerConnectionId=" + peerConnectionId + " peerVersion=" + peerVersion);
        }

        final ConversationConnection connection;
        synchronized (mPeerConnectionLock) {
            connection = mPeerConnectionId2Conversation.get(peerConnectionId);
        }
        if (connection == null) {
            mPeerConnectionService.terminatePeerConnection(peerConnectionId, TerminateReason.GONE);
            return;
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerVersion(peerVersion);
        connection.updateLeadingPadding(leadingPadding);

        final boolean open;
        synchronized (mPeerConnectionLock) {
            open = connection.openPeerConnection(peerConnectionId);
        }
        if (!open) {
            close(connection, false, peerConnectionId, TerminateReason.GONE);
            mPeerConnectionService.terminatePeerConnection(peerConnectionId, TerminateReason.GONE);

        } else if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_12)) {
            final long now = System.currentTimeMillis();
            final UUID resourceId = conversationImpl.getResourceId();

            // Until we receive the peer device state, do as if it has some pending operations.
            connection.setPeerDeviceState(ConversationConnection.DEVICE_STATE_HAS_OPERATIONS);
            final SynchronizeIQ iq = new SynchronizeIQ(SynchronizeIQ.IQ_SYNCHRONIZE_SERIALIZER, newRequestId(),
                    conversationImpl.getTwincodeOutboundId(), resourceId, now);

            mPeerConnectionService.sendPacket(peerConnectionId, StatType.IQ_SET_SYNCHRONIZE, iq);

            connection.setSynchronizeKeys(checkPublicKey(connection, peerConnectionId, conversationImpl));
        } else {
            final Operation firstOperation = mScheduler.startOperation(connection, State.OPEN);
            if (firstOperation != null) {
                sendOperationInternal(connection, firstOperation);
            }
        }
    }

    private boolean checkPublicKey(@NonNull ConversationConnection connection, @NonNull UUID peerConnectionId, @NonNull ConversationImpl conversationImpl) {

        if (connection.isSupported(MAJOR_VERSION_2, MINOR_VERSION_18)) {
            final TwincodeOutbound twincodeOutbound = conversationImpl.getTwincodeOutbound();
            final TwincodeInbound twincodeInbound = conversationImpl.getTwincodeInbound();
            final TwincodeOutbound peerTwincodeOutbound = conversationImpl.getPeerTwincodeOutbound();

            // The 3 twincodes must be valid AND we must ignore the special case where
            // twincodeOutbound == peerTwincodeOutbound which could occur for a group member conversation
            // iff the member is not immediately recognized.
            if (twincodeOutbound != null && twincodeInbound != null && peerTwincodeOutbound != null
                    && !twincodeOutbound.getId().equals(peerTwincodeOutbound.getId())) {
                if (!twincodeOutbound.isSigned()) {
                    createAndSendPublicKey(peerConnectionId, twincodeInbound, twincodeOutbound, peerTwincodeOutbound);
                    return true;
                } else {
                    final SdpEncryptionStatus sdpEncryptionStatus = mPeerConnectionService.getSdpEncryptionStatus(peerConnectionId);
                    if (sdpEncryptionStatus != SdpEncryptionStatus.ENCRYPTED) {
                        SignatureInfoIQ iq = mTwinlifeImpl.getCryptoService().getSignatureInfoIQ(twincodeOutbound, peerTwincodeOutbound,
                                sdpEncryptionStatus == SdpEncryptionStatus.ENCRYPTED_NEED_RENEW);

                        if (iq == null) {
                            return false;
                        }
                        connection.sendPacket(StatType.IQ_SET_SIGNATURE_INFO, iq);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void createAndSendPublicKey(@NonNull UUID peerConnectionId, @NonNull TwincodeInbound twincodeInbound,
                                        @NonNull TwincodeOutbound twincodeOutbound, @NonNull TwincodeOutbound peerTwincodeOutbound) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createAndSendKey: twincodeInbound= " + twincodeInbound + " twincodeOutbound: " + twincodeOutbound
                    + " peerTwincodeOutbound=" + peerTwincodeOutbound);
        }

        mTwincodeOutboundService.createPrivateKey(twincodeInbound, (status, signedTwincode) -> {
            if (status != ErrorCode.SUCCESS || signedTwincode == null) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "Could not create private key for twincode + " + twincodeOutbound + ": " + status);
                }
                return;
            }

            if (!signedTwincode.isSigned() || !signedTwincode.getId().equals(twincodeOutbound.getId())) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "Invalid signed twincode + " + signedTwincode + ": " + status);
                }
                return;
            }

            SignatureInfoIQ iq = mTwinlifeImpl.getCryptoService().getSignatureInfoIQ(signedTwincode, peerTwincodeOutbound, false);

            if (iq == null) {
                return;
            }

            mPeerConnectionService.sendPacket(peerConnectionId, StatType.IQ_SET_SIGNATURE_INFO, iq);
        });
    }

    @Override
    public void onDataChannelClosed(@NonNull UUID peerConnectionId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDataChannelClosed: peerConnectionId=" + peerConnectionId);
        }
    }

    @Override
    public void onDataChannelMessage(@NonNull UUID peerConnectionId, @NonNull ByteBuffer buffer, boolean leadingPadding) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDataChannelMessage: peerConnectionId=" + peerConnectionId + " bytes=" + buffer);
        }

        Exception exception = null;
        IQ iq = null;
        IQ unkownIQ = null;
        UUID schemaId = null;
        int schemaVersion = -1;
        try {
            final ByteBufferInputStream inputStream = new ByteBufferInputStream(buffer);
            final BinaryDecoder binaryDecoder;
            if (leadingPadding) {
                binaryDecoder = new BinaryDecoder(inputStream);
            } else {
                binaryDecoder = new BinaryCompactDecoder(inputStream);
            }
            schemaId = binaryDecoder.readUUID();
            schemaVersion = binaryDecoder.readInt();
            SchemaKey key = new SchemaKey(schemaId, schemaVersion);

            final Pair<Serializer, PeerConnectionBinaryPacketListener> listener = mBinaryListeners.get(key);
            if (listener != null) {
                final BinaryPacketIQ bIq = (BinaryPacketIQ) listener.first.deserialize(mSerializerFactory, binaryDecoder);

                mPeerConnectionService.incrementStat(peerConnectionId, StatType.IQ_RECEIVE_SET_COUNT);

                final ConversationConnection connection = preparePeerConversation(peerConnectionId, null, null);
                if (connection == null) {
                    return;
                }

                // Process the packet from the conversation executor thread to avoid blocking
                // the WebRTC signaling thread.
                mExecutor.execute(() -> {
                    try {
                        listener.second.processPacket(connection, bIq);

                    } catch (Exception ex) {
                        mTwinlifeImpl.exception(ConversationAssertPoint.ON_DATA_CHANNEL_IQ, ex,
                                AssertPoint.createPeerConnectionId(peerConnectionId)
                                        .put(connection.getConversation().getSubject())
                                        .putSchemaId(listener.first.schemaId)
                                        .putSchemaVersion(listener.first.schemaVersion));
                    }
                });
                return;
            }

            // Synchronize IQ is special because it must make a specific call to preparePeerConversation() to give
            // the peer twincode id and the peer resource id.
            if (SynchronizeIQ.SCHEMA_ID.equals(schemaId)) {
                if (SynchronizeIQ.SCHEMA_VERSION_1 == schemaVersion) {
                    BinaryPacketIQ bIq = (BinaryPacketIQ) SynchronizeIQ.IQ_SYNCHRONIZE_SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);

                    processSynchronizeIQ(peerConnectionId, bIq);
                    return;
                }
            }

            if (ResetConversationIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.ResetConversationIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.ResetConversationIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                } else if (schemaVersion < ConversationServiceIQ.ResetConversationIQ.SCHEMA_VERSION) {
                    // Reject very old versions properly.
                    iq = unkownIQ = (IQ) ServiceRequestIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (OnResetConversationIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.OnResetConversationIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.OnResetConversationIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                } else if (schemaVersion < ConversationServiceIQ.OnResetConversationIQ.SCHEMA_VERSION) {
                    // Reject very old versions properly.
                    iq = unkownIQ = (IQ) ServiceRequestIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (PushObjectIQ.SCHEMA_ID.equals(schemaId)) {
                if (schemaVersion == ConversationServiceIQ.PushObjectIQ.SCHEMA_VERSION) {
                    iq = (IQ) ConversationServiceIQ.PushObjectIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                } else {
                    if (schemaVersion < ConversationServiceIQ.PushObjectIQ.SCHEMA_VERSION) {
                        // Reject very old versions properly.
                        iq = unkownIQ = (IQ) ServiceRequestIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                    }
                }

            } else if (OnPushObjectIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.OnPushObjectIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.OnPushObjectIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                } else if (ConversationServiceIQ.OnPushObjectIQ.SCHEMA_VERSION_1 == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.OnPushObjectIQ.SERIALIZER_1.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (PushTransientIQ.SCHEMA_ID.equals(schemaId)) {
                if (PushTransientObjectIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) PushTransientObjectIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                } else {
                    return; // Ignore very old version of PushTransient message.
                }

            } else if (OnPushCommandIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.OnPushCommandIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.OnPushCommandIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                } else {
                    return; // Ignore very old version of OnPushCommand
                }

            } else if (PushFileIQ.SCHEMA_ID.equals(schemaId)) {
                if (schemaVersion == ConversationServiceIQ.PushFileIQ.SCHEMA_VERSION) {
                    iq = (IQ) ConversationServiceIQ.PushFileIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                } else {
                    if (schemaVersion < ConversationServiceIQ.PushFileIQ.SCHEMA_VERSION) {
                        // Reject very old versions properly.
                        iq = unkownIQ = (IQ) ServiceRequestIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                    }
                }

            } else if (OnPushFileIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.OnPushFileIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.OnPushFileIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (PushFileChunkIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.PushFileChunkIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.PushFileChunkIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (OnPushFileChunkIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.OnPushFileChunkIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.OnPushFileChunkIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (UpdateTimestampIQ.SCHEMA_ID.equals(schemaId)) {
                if (UpdateDescriptorTimestampIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) UpdateDescriptorTimestampIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (OnUpdateTimestampIQ.SCHEMA_ID.equals(schemaId)) {
                if (OnUpdateDescriptorTimestampIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) OnUpdateDescriptorTimestampIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (InviteGroupIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.InviteGroupIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.InviteGroupIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (JoinGroupIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.JoinGroupIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.JoinGroupIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (LeaveGroupIQ.SCHEMA_ID.equals(schemaId)) {
                if (LeaveGroupIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) LeaveGroupIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (RevokeInviteGroupIQ.SCHEMA_ID.equals(schemaId)) {
                if (RevokeInviteGroupIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) RevokeInviteGroupIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (UpdatePermissionsIQ.SCHEMA_ID.equals(schemaId)) {
                if (UpdateGroupMemberIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) UpdateGroupMemberIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (PushCommandIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.PushCommandIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.PushCommandIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (PushGeolocationIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.PushGeolocationIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.PushGeolocationIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (PushTwincodeIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.PushTwincodeIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.PushTwincodeIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (OnPushGeolocationIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.OnPushGeolocationIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.OnPushGeolocationIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (OnPushTwincodeIQ.SCHEMA_ID.equals(schemaId)) {
                if (ConversationServiceIQ.OnPushTwincodeIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ConversationServiceIQ.OnPushTwincodeIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (OnInviteGroupIQ.SCHEMA_ID.equals(schemaId)) {
                if (OnResultGroupIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) OnResultGroupIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (OnJoinGroupIQ.SCHEMA_ID.equals(schemaId)) {
                if (OnResultJoinIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) OnResultJoinIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (ServiceErrorIQ.SCHEMA_ID.equals(schemaId)) {
                if (ServiceErrorIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ServiceErrorIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else if (ErrorIQ.SCHEMA_ID.equals(schemaId)) {
                if (ErrorIQ.SCHEMA_VERSION == schemaVersion) {
                    iq = (IQ) ErrorIQ.SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);
                }

            } else {
                iq = unkownIQ = (IQ) IQ.IQ_SERIALIZER.deserialize(mSerializerFactory, binaryDecoder);

            }
        } catch (Exception lException) {
            exception = lException;
        }

        if (iq == null) {
            mTwinlifeImpl.exception(ConversationAssertPoint.ON_DATA_CHANNEL_IQ, exception,
                    AssertPoint.createPeerConnectionId(peerConnectionId)
                            .putSchemaId(schemaId)
                            .putSchemaVersion(schemaVersion));

            return;
        }

        String from = iq.getFrom();
        int index = from.indexOf('/');
        UUID resourceId = null;
        UUID peerTwincodeOutbandId = null;
        if (index != -1) {
            resourceId = Utils.UUIDFromString(from.substring(index + 1));
            peerTwincodeOutbandId = Utils.UUIDFromString(from.substring(0, index));
        }

        final ConversationConnection connection = preparePeerConversation(peerConnectionId, peerTwincodeOutbandId, resourceId);
        if (connection == null) {
            return;
        }

        if (unkownIQ != null) {
            final ErrorIQ errorIQ;
            if (unkownIQ instanceof ServiceRequestIQ) {
                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) unkownIQ;
                errorIQ = new ServiceErrorIQ(serviceRequestIQ.getId(), connection.getFrom(), connection.getTo(),
                        ErrorIQ.Type.CANCEL, ErrorIQ.FEATURE_NOT_IMPLEMENTED, schemaId, schemaVersion, serviceRequestIQ.getRequestId(),
                        serviceRequestIQ.getService(), serviceRequestIQ.getAction(), serviceRequestIQ.getMajorVersion(),
                        serviceRequestIQ.getMinorVersion());

            } else {
                errorIQ = new ErrorIQ(iq.getId(), connection.getFrom(), connection.getTo(),
                        ErrorIQ.Type.CANCEL, ErrorIQ.FEATURE_NOT_IMPLEMENTED, schemaId,
                        schemaVersion);
            }
            sendErrorIQ(connection.getConversation(), errorIQ);

            return;
        }

        final IQ lIQ = iq;
        final UUID lSchemaId = schemaId;
        final int lSchemaVersion = schemaVersion;
        mExecutor.execute(() -> processIQ(peerConnectionId, connection, lSchemaId, lSchemaVersion, lIQ));
    }

    /**
     * Registers a packet listener with this connection. A packet filter
     * determines which packets will be delivered to the listener. If the same
     * packet listener is added again with a different filter, only the new
     * filter will be used.
     *
     * @param packetListener the packet listener to notify of new received packets.
     */
    void addPacketListener(@NonNull Serializer serializer, @NonNull PeerConnectionBinaryPacketListener packetListener) {

        final SchemaKey key = new SchemaKey(serializer.schemaId, serializer.schemaVersion);

        mBinaryListeners.put(key, new Pair<>(serializer, packetListener));
    }

    @Nullable
    private ConversationConnection preparePeerConversation(@NonNull UUID peerConnectionId,
                                                           @Nullable UUID peerTwincodeOutboundId, @Nullable UUID peerResourceId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "preparePeerConversation: peerConnectionId=" + peerConnectionId
                    + " peerTwincodeOutboundId=" + peerTwincodeOutboundId + " peerResourceId=" + peerResourceId);
        }

        ConversationConnection connection;
        synchronized (mPeerConnectionLock) {
            connection = mPeerConnectionId2Conversation.get(peerConnectionId);
        }
        if (connection == null) {
            return null;
        }

        ConversationImpl conversationImpl = connection.getConversation();
        GroupConversationImpl groupConversation = conversationImpl.getGroup();
        if (groupConversation != null && groupConversation.getIncomingConversation() == conversationImpl) {
            if (peerTwincodeOutboundId == null) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Invalid connection object ", connection);
                }
                return null;
            }

            ConversationImpl memberConversationImpl = groupConversation.getMember(peerTwincodeOutboundId);
            if (memberConversationImpl == null) {
                EventMonitor.info(LOG_TAG, "add implicit member in: ", groupConversation.toLogLine(), " memberTwincodeId=", peerTwincodeOutboundId);

                Log.e(LOG_TAG, "Add implicit in " + conversationImpl.getDatabaseId() + " member " + peerTwincodeOutboundId);
                memberConversationImpl = mServiceProvider.createGroupMemberConversation(groupConversation, peerTwincodeOutboundId, 0L, null);
                if (memberConversationImpl == null) {
                    return null;
                }
            }

            // Move the incoming P2P conversation to the group member conversation and
            // setup a new idle timer for the group member conversation.
            ConversationConnection oldConnection = connection;
            synchronized (mPeerConnectionLock) {
                connection = memberConversationImpl.transferConnection(oldConnection);
                mPeerConnectionId2Conversation.put(peerConnectionId, connection);
            }
            // Tell the scheduler the group incoming conversation can be dropped and it must now track the member conversation.
            // The group incoming conversation has no operation but the member's conversation can have pending operations.
            mScheduler.close(oldConnection);
            mScheduler.startOperation(connection, State.OPEN);

            conversationImpl = memberConversationImpl;
        }
        if (peerResourceId != null && !peerResourceId.equals(Twincode.NOT_DEFINED)) {
            boolean updated = false;
            boolean hardReset = false;

            UUID lResourceId = conversationImpl.getPeerResourceId();
            if (lResourceId == null) {
                updated = true;
                Log.e(LOG_TAG, "For " + conversationImpl.getDatabaseId() + " set resource to " + peerResourceId);
            } else if (!lResourceId.equals(peerResourceId)) {
                updated = true;
                hardReset = true;
                EventMonitor.info(LOG_TAG, "Changing conversation peer resourceId from ", lResourceId, " to ", peerResourceId);
                Log.e(LOG_TAG, "Hard reset for " + conversationImpl.getDatabaseId() + " set resource to " + peerResourceId);
            }

            if (hardReset && ENABLE_HARD_RESET) {
                final Map<UUID, DescriptorId> resetList = getDescriptorsToDelete(conversationImpl, Long.MAX_VALUE);
                resetConversation(conversationImpl, resetList, ClearMode.CLEAR_BOTH);

                final Conversation conversation = conversationImpl.getMainConversation();
                for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                    mTwinlifeExecutor.execute(() -> serviceObserver.onResetConversation(conversation, ClearMode.CLEAR_BOTH));
                }

                if (peerTwincodeOutboundId != null) {

                    // Create a clear descriptor with the peer's twincode and use a fixed sequence number.
                    final long now = System.currentTimeMillis();
                    final DescriptorImpl clearDescriptorImpl = mServiceProvider.createDescriptor(conversation, (long id, long sequenceId, long cid) -> {
                        final DescriptorId descriptorId = new DescriptorId(id, peerTwincodeOutboundId, 1);
                        final ClearDescriptorImpl result = new ClearDescriptorImpl(descriptorId, cid, now);
                        result.setSentTimestamp(now);
                        result.setReceivedTimestamp(now);
                        return result;
                    });

                    if (clearDescriptorImpl != null) {
                        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                            mTwinlifeExecutor.execute(() -> serviceObserver.onPopDescriptor(DEFAULT_REQUEST_ID, conversation, clearDescriptorImpl));
                        }
                    }
                }
            }
            if (hardReset) {
                mTwinlifeImpl.assertion(ConversationAssertPoint.RESET_CONVERSATION,
                        AssertPoint.createPeerConnectionId(peerConnectionId)
                                .put(conversationImpl.getSubject())
                                .putResourceId(lResourceId)
                                .putResourceId(peerResourceId));
            }

            // Update after the hard reset to make sure it was made completely (if it was interrupted, we will do it again).
            if (updated) {
                conversationImpl.setPeerResourceId(peerResourceId);
                mServiceProvider.updateConversation(conversationImpl, null);
            }
        }

        connection.touch();
        return connection;
    }

    @Nullable
    Conversation getConversationWithId(@NonNull DatabaseIdentifier conversationId) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getConversationWithId: conversationId=" + conversationId);
        }

        return mServiceProvider.loadConversationWithId(conversationId.getId());
    }

    @Nullable
    private Conversation getOrCreateConversationImpl(@NonNull RepositoryObject subject, boolean create) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getOrCreateConversationImpl: subject=" + subject + " create=" + create);
        }

        final Conversation conversation = mServiceProvider.loadConversationWithSubject(subject);
        if (conversation != null || !create) {
            return conversation;
        }

        final Conversation newConversation = mServiceProvider.createConversation(subject);
        if (newConversation != null) {
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onCreateConversation(newConversation));
            }
        }
        return newConversation;
    }

    @NonNull
    private Map<UUID, DescriptorId> getDescriptorsToDelete(@NonNull Conversation conversation, long resetDate) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDescriptorsToDelete: conversation=" + conversation + " resetDate=" + resetDate);
        }

        // Step 1: identify for each twincode of the conversation, the max sequenceId inclusive that must be removed.
        // (other steps are handled by resetConversation).
        return mServiceProvider.listDescriptorsToDelete(conversation, null, resetDate);
    }

    boolean resetConversation(@NonNull Conversation conversation,
                              @NonNull Map<UUID, DescriptorId> resetList,
                              @NonNull ClearMode clearMode) {
        if (DEBUG) {
            Log.d(LOG_TAG, "resetConversationImpl: conversation=" + conversation + " resetList=" + resetList + " clearMode=" + clearMode);
        }

        if (conversation instanceof ConversationImpl) {
            ConversationImpl conversationImpl = (ConversationImpl) conversation;

            // Step 2: remove the files associated with descriptors.
            deleteUnreacheableFiles(conversation, resetList, clearMode);

            // Step 3: now, it is safe to delete the descriptors from the DB.
            final List<Long> deletedOperations = new ArrayList<>();
            boolean result = mServiceProvider.deleteDescriptors(conversation, resetList,
                    clearMode == ClearMode.CLEAR_MEDIA, deletedOperations);

            // Step 4: remove the pending operations for the descriptors that are deleted.
            if (!deletedOperations.isEmpty()) {
                mScheduler.removeOperations(conversationImpl, deletedOperations);
            }

            // Step 5: check if we still have some descriptor for this conversation.
            long count = mServiceProvider.getDescriptorCount(conversationImpl);
            conversationImpl.setIsActive(count != 0);

            return result;

        } else {

            // Step 4: remove the files associated with descriptors.
            deleteUnreacheableFiles(conversation, resetList, clearMode);

            // Step 5: now, it is safe to delete the descriptors from the DB.
            final List<Long> deletedOperations = new ArrayList<>();
            return mServiceProvider.deleteDescriptors(conversation, resetList,
                    clearMode == ClearMode.CLEAR_MEDIA, deletedOperations);
        }
    }

    void deleteConversation(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteConversation: conversation=" + conversationImpl);
        }

        EventMonitor.info(LOG_TAG, "Delete conversation ", conversationImpl.getId(), " to ", conversationImpl.getPeerTwincodeOutboundId());


        // Check if this conversation is part of a group and delete the member.
        final GroupConversationImpl groupConversation = conversationImpl.getGroup();
        if (groupConversation != null) {
            groupConversation.delMember(conversationImpl.getPeerTwincodeOutboundId());
        }
        mScheduler.deleteConversation(conversationImpl);
        mServiceProvider.deleteConversation(conversationImpl);

        deleteFiles(conversationImpl);

        // @todo: we should notify here that a conversation was removed.
    }

    private void connect(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "connect: conversationImpl=" + conversationImpl);
        }

        // Don't try to start a new P2P connection if we are offline.
        if (!isTwinlifeOnline() || isShutdown()) {
            return;
        }

        if (!conversationImpl.hasPeer()) {
            return;
        }

        final PushNotificationContent notificationContent = mScheduler.prepareNotification(conversationImpl);
        if (notificationContent == null) {
            return;
        }

        final ConversationConnection connection;
        synchronized (mPeerConnectionLock) {
            connection = conversationImpl.startOutgoing(mTwinlifeImpl);
            if (connection == null) {
                return;
            }
        }

        final Offer offer = new Offer(false, false, false, true);
        final OfferToReceive offerToReceive = new OfferToReceive(false, false, true);
        notificationContent.timeToLive = OPENING_TIMEOUT * 1000;

        mPeerConnectionService.createOutgoingPeerConnection(conversationImpl.getSubject(),
                conversationImpl.getPeerTwincodeOutbound(), offer, offerToReceive, notificationContent,
                this, this, (ErrorCode errorCode, UUID peerConnectionId) -> {
            if (errorCode == ErrorCode.SUCCESS && peerConnectionId != null) {
                synchronized (mPeerConnectionLock) {
                    connection.outgoingPeerConnection(peerConnectionId);
                    mPeerConnectionId2Conversation.put(peerConnectionId, connection);
                }
                connection.setOpenTimeout(mExecutor.schedule(() -> onOpenTimeout(connection), OPENING_TIMEOUT, TimeUnit.SECONDS));

            } else {
                close(connection, false, null, TerminateReason.fromErrorCode(errorCode));
            }
        });
    }

    private void onOpenTimeout(@NonNull ConversationConnection connection) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onOpenTimeout: connection=" + connection);
        }

        final UUID peerConnectionId;
        synchronized (mPeerConnectionLock) {
            if (connection.getOutgoingState() == State.OPENING) {
                peerConnectionId = connection.getOutgoingPeerConnectionId();
            } else if (connection.getIncomingState() == State.OPENING) {
                peerConnectionId = connection.getIncomingPeerConnectionId();
            } else {
                peerConnectionId = null;
            }
        }
        if (peerConnectionId != null) {
            close(connection, false, peerConnectionId, TerminateReason.TIMEOUT);
            mPeerConnectionService.terminatePeerConnection(peerConnectionId, TerminateReason.TIMEOUT);
        }
    }

    private void close(@NonNull ConversationConnection connection, boolean isIncoming, @Nullable UUID peerConnectionId, @NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "close: isIncoming=" + isIncoming + " peerConnectionId=" + peerConnectionId + " terminateReason=" + terminateReason);
        }

        final ConversationImpl conversationImpl = connection.getConversation();

        // A DISCONNECTED or CONNECTIVITY_ERROR indicates that the current P2P data channel is now broken.
        // we want to retry immediately if the connection was opened.
        boolean retryImmediately = (terminateReason == TerminateReason.DISCONNECTED
                || terminateReason == TerminateReason.CONNECTIVITY_ERROR);
        synchronized (mPeerConnectionLock) {
            retryImmediately = retryImmediately && connection.isOpened();
            final Boolean result = connection.closePeerConnection(peerConnectionId, isIncoming);
            if (result == null) {
                if (DEBUG) {
                    Log.d(LOG_TAG, "conversion close one direction still opened: peerConnectionId=" + peerConnectionId + " terminateReason=" + terminateReason);
                }

                return;
            }
            if (peerConnectionId != null) {
                mPeerConnectionId2Conversation.remove(peerConnectionId);
            }
            isIncoming = result;
        }

        conversationImpl.nextDelay(terminateReason);
        final boolean synchronizePeerNotification = mScheduler.close(connection);

        // This peer has gone or was revoked, there is no need to try again nor to keep the conversation.
        // Inform the upper layer so that the conversation is cleaned.
        if (terminateReason == TerminateReason.REVOKED) {
            EventMonitor.info(LOG_TAG, "Peer ", conversationImpl.getPeerTwincodeOutboundId(), " is ", terminateReason);
            deleteConversation(conversationImpl);

            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onRevoked(conversationImpl));
            }
            return;
        }

        // Try to handle and recover from some errors.
        if (terminateReason == TerminateReason.NOT_ENCRYPTED
                || terminateReason == TerminateReason.DECRYPT_ERROR
                || terminateReason == TerminateReason.NO_PUBLIC_KEY
                || terminateReason == TerminateReason.NO_PRIVATE_KEY
                || terminateReason == TerminateReason.NO_SECRET_KEY) {
            final TwincodeOutbound twincodeOutbound = conversationImpl.getTwincodeOutbound();
            final TwincodeOutbound peerTwincodeOutbound = conversationImpl.getPeerTwincodeOutbound();
            if (twincodeOutbound != null && twincodeOutbound.isSigned() && peerTwincodeOutbound != null
                    && !peerTwincodeOutbound.getId().equals(twincodeOutbound.getId())) {

                // The need-secret process can work only if our twincode is signed and the peer knows our public key.
                //
                // Outgoing P2P:
                // - if we failed to create the P2P connection, we must trigger the need-secret because there
                //   is an issue on some public key, private key, or secret key.
                //   In that case, the peer does not receive any terminate reason because the session-initiate is not sent.
                //   => (!isIncoming && peerConnectionId == null)
                // - if our outgoing P2P was refused with NOT_ENCRYPTED, we must also trigger the need-secret
                //   => (!isIncoming && terminateReason == TerminateReason.NOT_ENCRYPTED)
                // - if we get a NO_SECRET_KEY, we either failed to decrypt the peer SDP or the peer failed
                //
                // Incoming P2P:
                // - we almost always trigger the need-secret except for the NOT_ENCRYPTED case which is
                //   handled by the outgoing peer.
                // - if we are missing the keys, send a need-secret invocation.
                // - if we have an EncryptError it means the two peers are now de-synchronized
                //   on their secrets (mostly due to a bug), the need-secret is also triggered but only on one side.
                if ((!isIncoming && (peerConnectionId == null || terminateReason == TerminateReason.NOT_ENCRYPTED))
                        || (isIncoming && terminateReason != TerminateReason.NOT_ENCRYPTED)) {
                    List<AttributeNameValue> attributes = new ArrayList<>();

                    // For a group, add our own member twincode so that the peer can identify us within the group.
                    if (conversationImpl.isGroup()) {
                        attributes.add(new AttributeNameStringValue(ConversationProtocol.PARAM_MEMBER_TWINCODE_OUTBOUND_ID, twincodeOutbound.getId().toString()));
                    }

                    mTwincodeOutboundService.invokeTwincode(peerTwincodeOutbound, TwincodeOutboundService.INVOKE_WAKEUP,
                            ConversationProtocol.ACTION_CONVERSATION_NEED_SECRET, attributes, (ErrorCode errorCode, UUID invocationId) -> {
                            });
                }
            }
        }

        retryImmediately = retryImmediately && mScheduler.hasOperations(conversationImpl);
        if (!retryImmediately && synchronizePeerNotification) {
            askConversationSynchronize(conversationImpl);
        }

        if (retryImmediately) {
            EventMonitor.info(LOG_TAG,"Close ", conversationImpl.getPeerTwincodeOutboundId(),
                    " retry immediately");
            executeOperation(conversationImpl);
        } else {
            EventMonitor.info(LOG_TAG,"Close ", conversationImpl.getPeerTwincodeOutboundId(),
                    " retry in ", conversationImpl.getDelay());
            mScheduler.scheduleConversationOperations(conversationImpl);
        }
    }

    @NonNull
    List<ConversationService.ServiceObserver> getObservers() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getObservers");
        }

        return getServiceObservers();
    }

    void askConversationSynchronize(ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "askConversationSynchronize: conversationImpl=" + conversationImpl);
        }

        conversationImpl.setNeedSynchronize();
        List<AttributeNameValue> attributes = new ArrayList<>();

        // For a group, add our own member twincode so that the peer can identify us within the group.
        if (conversationImpl.isGroup()) {
            attributes.add(new AttributeNameStringValue(ConversationProtocol.PARAM_MEMBER_TWINCODE_OUTBOUND_ID, conversationImpl.getTwincodeOutboundId().toString()));
        }

        final TwincodeOutbound peerTwincodeOutbound = conversationImpl.getPeerTwincodeOutbound();
        mTwincodeOutboundService.invokeTwincode(peerTwincodeOutbound, TwincodeOutboundService.INVOKE_WAKEUP,
                ConversationProtocol.ACTION_CONVERSATION_SYNCHRONIZE, attributes, (ErrorCode errorCode, UUID invocationId) -> {
            if (errorCode == ErrorCode.SUCCESS && invocationId != null) {
                conversationImpl.clearNeedSynchronize();
                SynchronizeConversationOperation synchronizeConversationOperation = new SynchronizeConversationOperation(conversationImpl);
                mServiceProvider.storeOperation(synchronizeConversationOperation);
                mScheduler.addOperation(conversationImpl, synchronizeConversationOperation);

            } else if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                final GroupConversationImpl group = conversationImpl.getGroup();
                if (group != null) {
                    EventMonitor.info(LOG_TAG, "Member ", peerTwincodeOutbound, " of group conversation ", conversationImpl.getId(), " not found");

                    // The group member was removed.
                    mGroupManager.delMember(group, peerTwincodeOutbound.getId());
                } else {
                    EventMonitor.info(LOG_TAG, "Peer ", peerTwincodeOutbound, " of conversation ", conversationImpl.getId(), " not found");

                    deleteConversation(conversationImpl);
                }
            }
        });
    }

    void closeConnection(@NonNull ConversationConnection connection, @NonNull TerminateReason terminateReason) {
        if (DEBUG) {
            Log.d(LOG_TAG, "closeConnection: connection=" + connection + " terminateReason=" + terminateReason);
        }

        final UUID peerConnectionId = connection.getPeerConnectionId();
        if (peerConnectionId != null) {
            mPeerConnectionService.terminatePeerConnection(peerConnectionId, terminateReason);
        }
        close(connection, false, peerConnectionId, terminateReason);
    }

    void executeOperation(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "executeOperation: conversationImpl=" + conversationImpl);
        }

        if (INFO) {
            Log.i(LOG_TAG, "executeOperation: conversationId=" + conversationImpl.getId());
        }
        mExecutor.execute(() -> executeOperationInternal(conversationImpl));
    }

    void executeFirstOperation(@NonNull ConversationImpl conversationImpl, @NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "executeFirstOperation: conversationImpl=" + conversationImpl
                    + " operation=" + operation);
        }

        if (INFO) {
            Log.i(LOG_TAG, "executeFirstOperation: conversationId=" + conversationImpl.getId()
                    + " operation=" + operation);
        }
        mExecutor.execute(() -> {
            if (operation.isInvoke()) {
                final ErrorCode errorCode = operation.executeInvoke(this, conversationImpl);
                if (errorCode != ErrorCode.QUEUED) {
                    mScheduler.finishInvokeOperation(operation, conversationImpl);
                }
            } else {
                final ConversationConnection connection = conversationImpl.getConnection();
                if (connection != null && connection.isOpened()) {
                    sendOperationInternal(connection, operation);
                } else {
                    executeOperationInternal(conversationImpl);
                }
            }
        });
    }

    void executeNextOperation(@NonNull ConversationConnection connection, @NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "executeNextOperation: connection=" + connection
                    + " operation=" + operation);
        }

        mExecutor.execute(() -> sendOperationInternal(connection, operation));
    }

    void executeOperationInternal(@NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "executeOperationInternal: conversationImpl=" + conversationImpl);
        }

        final State state;
        final ConversationConnection connection;
        synchronized (mPeerConnectionLock) {
            connection = conversationImpl.getConnection();
            state = connection == null ? State.CLOSED : connection.getState();
        }

        switch (state) {
            case CLOSED:
                connect(conversationImpl);
                return;

            case OPENING:
                return;

            case OPEN:
                final Operation operation = mScheduler.getFirstOperation(conversationImpl);
                if (operation == null) {
                    return;
                }

                sendOperationInternal(connection, operation);
                break;
        }
    }

    private void sendOperationInternal(@NonNull ConversationConnection connection, @NonNull Operation operation) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendOperationInternal: connection=" + connection);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        try {
            final ErrorCode errorCode;
            if (operation.isInvoke()) {
                errorCode = operation.executeInvoke(this, conversationImpl);
            } else {
                errorCode = operation.execute(connection);
            }
            if (errorCode != ErrorCode.QUEUED) {
                mScheduler.finishOperation(operation, connection);
            }

        } catch (Exception exception) {
            mScheduler.finishOperation(operation, connection);
            mTwinlifeImpl.exception(ConversationAssertPoint.SEND_OP, exception,
                    AssertPoint.createPeerConnectionId(connection.getPeerConnectionId())
                            .put(operation.getType()));
        }
    }

    private int getDeviceState(@NonNull ConversationConnection connection) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDeviceState: connection=" + connection);
        }

        int deviceState = 0;

        if (mScheduler.hasOperations(connection.getConversation())) {
            deviceState |= ConversationConnection.DEVICE_STATE_HAS_OPERATIONS;
        }

        if (mJobService.isForeground()) {
            deviceState |= ConversationConnection.DEVICE_STATE_FOREGROUND;
        }

        if (connection.isSynchronizingKeys()) {
            deviceState |= ConversationConnection.DEVICE_STATE_SYNCHRONIZE_KEYS;
        }

        return deviceState;
    }

    void deleteConversationDescriptor(long requestId, @NonNull Conversation conversation, @NonNull DescriptorImpl descriptorImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteDescriptor: descriptorImpl=" + descriptorImpl);
        }

        mServiceProvider.deleteDescriptorImpl(conversation, descriptorImpl);

        // If this is a normal conversation, check if it still has some descriptors to update the isActive flag.
        if (conversation instanceof ConversationImpl) {
            ConversationImpl conversationImpl = (ConversationImpl) conversation;
            long count = mServiceProvider.getDescriptorCount(conversationImpl);
            conversationImpl.setIsActive(count != 0);
        }

        DescriptorId[] descriptorIds = new DescriptorId[] {
                descriptorImpl.getDescriptorId()
        };
        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onDeleteDescriptors(requestId,
                    conversation, descriptorIds));
        }
    }

    private void sendErrorIQ(@NonNull ConversationImpl conversationImpl, @NonNull ErrorIQ errorIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "sendErrorIQ: conversationImpl=" + conversationImpl + " serviceErrorIQ=" + errorIQ);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        UUID peerConnectionId = conversationImpl.getPeerConnectionId();
        if (peerConnectionId == null) {
            return;
        }
        try {
            BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (errorIQ instanceof ServiceErrorIQ) {
                ServiceErrorIQ.SERIALIZER.serialize(mSerializerFactory, binaryEncoder, errorIQ);
            } else {
                ErrorIQ.SERIALIZER.serialize(mSerializerFactory, binaryEncoder, errorIQ);
            }
            mPeerConnectionService.sendMessage(peerConnectionId, StatType.IQ_ERROR, outputStream.toByteArray());

        } catch (Exception exception) {
            mTwinlifeImpl.exception(ConversationAssertPoint.SEND_ERROR_IQ, exception,
                    AssertPoint.createPeerConnectionId(peerConnectionId));
        }
    }

    private void processIQ(@NonNull UUID peerConnectionId, @NonNull ConversationConnection connection,
                           @NonNull UUID schemaId, int schemaVersion, @NonNull IQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processIQ: connection=" + connection + " schemaId=" + schemaId + " schemaVersion=" + schemaVersion + " iq=" + iq);
        }

        String description;
        boolean processed = false;
        Exception exception = null;
        switch (iq.getType()) {
            case GET:
                break;

            case SET:
                ServiceRequestIQ serviceRequestIQ = (ServiceRequestIQ) iq;

                try {
                    mPeerConnectionService.incrementStat(peerConnectionId, StatType.IQ_RECEIVE_SET_COUNT);

                    switch (serviceRequestIQ.getAction()) {
                        case ConversationServiceIQ.RESET_CONVERSATION_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyResetConversationIQ(connection, (ConversationServiceIQ.ResetConversationIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.RESET_CONVERSATION_ACTION_1:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_1) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_1) {
                                    processLegacyResetConversationIQ(connection, (ConversationServiceIQ.ResetConversationIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.PUSH_OBJECT_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyPushObjectIQ(connection, (ConversationServiceIQ.PushObjectIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.PUSH_OBJECT_ACTION_1:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_1) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_1) {
                                    processLegacyPushObjectIQ(connection, (ConversationServiceIQ.PushObjectIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.PUSH_TRANSIENT_OBJECT_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyPushTransientObjectIQ(connection, (PushTransientObjectIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.PUSH_COMMAND_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyPushCommandIQ(connection, (ConversationServiceIQ.PushCommandIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.PUSH_TWINCODE_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyPushTwincodeIQ(connection, (ConversationServiceIQ.PushTwincodeIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.PUSH_FILE_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyPushFileIQ(connection, (ConversationServiceIQ.PushFileIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.PUSH_FILE_CHUNK_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyPushFileChunkIQ(connection, (ConversationServiceIQ.PushFileChunkIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.PUSH_GEOLOCATION_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyPushGeolocationIQ(connection, (ConversationServiceIQ.PushGeolocationIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.UPDATE_DESCRIPTOR_TIMESTAMP_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyUpdateDescriptorTimestampIQ(connection, (UpdateDescriptorTimestampIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.INVITE_GROUP_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyInviteGroupIQ(connection, (ConversationServiceIQ.InviteGroupIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.REVOKE_INVITE_GROUP_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyRevokeInviteGroupIQ(connection, (RevokeInviteGroupIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.JOIN_GROUP_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyJoinGroupIQ(connection, (ConversationServiceIQ.JoinGroupIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.LEAVE_GROUP_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyLeaveGroupIQ(connection, (LeaveGroupIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.UPDATE_GROUP_MEMBER_ACTION:
                            if (serviceRequestIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceRequestIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processLegacyUpdateGroupMemberIQ(connection, (UpdateGroupMemberIQ) serviceRequestIQ);
                                    processed = true;
                                }
                            }
                            break;

                    }
                } catch (Exception lException) {
                    mTwinlifeImpl.exception(ConversationAssertPoint.PROCESS_IQ, lException,
                            AssertPoint.createPeerConnectionId(peerConnectionId));
                }

                if (!processed) {
                    ServiceErrorIQ serviceErrorIQ = new ServiceErrorIQ(serviceRequestIQ.getId(), connection.getFrom(), connection.getTo(),
                            ErrorIQ.Type.CANCEL, ErrorIQ.BAD_REQUEST, schemaId, schemaVersion, serviceRequestIQ.getRequestId(), serviceRequestIQ.getService(),
                            serviceRequestIQ.getAction(), serviceRequestIQ.getMajorVersion(), serviceRequestIQ.getMinorVersion());

                    sendErrorIQ(connection.getConversation(), serviceErrorIQ);
                }
                break;

            case RESULT:
                ServiceResultIQ serviceReplyIQ = (ServiceResultIQ) iq;

                try {
                    mPeerConnectionService.incrementStat(peerConnectionId, StatType.IQ_RECEIVE_RESULT_COUNT);

                    switch (serviceReplyIQ.getAction()) {
                        case ConversationServiceIQ.ON_RESET_CONVERSATION_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyResetConversationIQ(connection, (ConversationServiceIQ.OnResetConversationIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_RESET_CONVERSATION_ACTION_1:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_1) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_1) {
                                    processOnLegacyResetConversationIQ(connection, (ConversationServiceIQ.OnResetConversationIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_PUSH_COMMAND_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyPushCommandIQ(connection, (ConversationServiceIQ.OnPushCommandIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_PUSH_OBJECT_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyPushObjectIQ(connection, (ConversationServiceIQ.OnPushObjectIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_PUSH_OBJECT_ACTION_1:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_1) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_1) {
                                    processOnLegacyPushObjectIQ(connection, (ConversationServiceIQ.OnPushObjectIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_PUSH_FILE_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyPushFileIQ(connection, (ConversationServiceIQ.OnPushFileIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_PUSH_FILE_CHUNK_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyPushFileChunkIQ(connection, (ConversationServiceIQ.OnPushFileChunkIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_PUSH_GEOLOCATION_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyPushGeolocationIQ(connection, (ConversationServiceIQ.OnPushGeolocationIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_PUSH_TWINCODE_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyPushTwincodeIQ(connection, (ConversationServiceIQ.OnPushTwincodeIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_UPDATE_DESCRIPTOR_TIMESTAMP_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyUpdateDescriptorTimestampIQ(connection, (OnUpdateDescriptorTimestampIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_INVITE_GROUP_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyInviteGroupIQ(connection, (OnResultGroupIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_REVOKE_INVITE_GROUP_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyRevokeInviteGroupIQ(connection, (OnResultGroupIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_JOIN_GROUP_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyJoinGroupIQ(connection, (OnResultJoinIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_LEAVE_GROUP_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyLeaveGroupIQ(connection, (OnResultGroupIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;

                        case ConversationServiceIQ.ON_UPDATE_GROUP_MEMBER_ACTION:
                            if (serviceReplyIQ.getMajorVersion() == MAJOR_VERSION_2) {
                                if (serviceReplyIQ.getMinorVersion() <= MAX_MINOR_VERSION_2) {
                                    processOnLegacyUpdateGroupMemberIQ(connection, (OnResultGroupIQ) serviceReplyIQ);
                                    processed = true;
                                }
                            }
                            break;
                    }

                } catch (Exception lException) {
                    exception = lException;
                }

                // If an exception is raised or if we have not handled the response packet, report an assertion but
                // there is no point it returning an error because it will be ignored.
                if (!processed) {
                    mTwinlifeImpl.exception(ConversationAssertPoint.PROCESS_RESULT_IQ, exception,
                            AssertPoint.createPeerConnectionId(peerConnectionId));
                }
                break;

            case ERROR:
                mPeerConnectionService.incrementStat(peerConnectionId, StatType.IQ_RECEIVE_ERROR_COUNT);

                if (iq instanceof ServiceErrorIQ) {
                    ServiceErrorIQ serviceErrorIQ = (ServiceErrorIQ) iq;
                    processServiceErrorIQ(connection, serviceErrorIQ);
                    processed = true;

                } else if (iq instanceof ErrorIQ) {
                    ErrorIQ errorIQ = (ErrorIQ) iq;
                    processErrorIQ(connection.getConversation(), errorIQ);
                    processed = true;

                }
        }
    }

    private void processResetConversationIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processResetConversationIQ: connection=" + connection + " iq=" + iq);
        }

        final ResetConversationIQ resetConversationIQ = (ResetConversationIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        final ClearDescriptorImpl clearDescriptorImpl = resetConversationIQ.clearDescriptorImpl;
        final long clearTimestamp = resetConversationIQ.clearTimestamp + connection.getPeerTimeCorrection();
        final long receivedTimestamp;
        if (clearDescriptorImpl == null || (resetConversationIQ.clearMode != ClearMode.CLEAR_BOTH || !conversationImpl.hasPermission(Permission.RESET_CONVERSATION))) {
            // Send him back a receive failure.
            receivedTimestamp = -1;

            if (!conversationImpl.isGroup()) {
                // Mark every descriptor as deleted by the peer.  We get a list of descriptors that are now deleted
                // if they are both deleted locally and by the peer.
                List<DescriptorId> resetList = mServiceProvider.markDescriptorDeleted(conversationImpl, clearTimestamp,
                        clearTimestamp, conversationImpl.getTwincodeOutboundId(), resetConversationIQ.clearMode == ClearMode.CLEAR_MEDIA);
                if (resetList != null) {
                    final DescriptorId[] descriptorIds = resetList.toArray(new DescriptorId[1]);

                    for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                        mTwinlifeExecutor.execute(() -> serviceObserver.onDeleteDescriptors(DEFAULT_REQUEST_ID, conversationImpl, descriptorIds));
                    }
                }
            }

        } else {
            // This is a reset conversation on both sides and the user has the permission.

            final Map<UUID, DescriptorId> resetList = getDescriptorsToDelete(conversationImpl.getMainConversation(), clearTimestamp);

            boolean wasActive = conversationImpl.getMainConversation().isActive();
            resetConversation(conversationImpl.getMainConversation(), resetList, ClearMode.CLEAR_BOTH);

            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onResetConversation(conversationImpl.getMainConversation(), ClearMode.CLEAR_BOTH));
            }

            if (wasActive) {
                popDescriptor(clearDescriptorImpl, connection);

                receivedTimestamp = clearDescriptorImpl.getReceivedTimestamp();
            } else {
                receivedTimestamp = System.currentTimeMillis();
            }
        }

        int deviceState = getDeviceState(connection);
        OnPushIQ onPushObjectIQ = new OnPushIQ(OnResetConversationIQ.IQ_ON_RESET_CONVERSATION_SERIALIZER, resetConversationIQ.getRequestId(), deviceState, receivedTimestamp);

        connection.sendPacket(StatType.IQ_RESULT_RESET_CONVERSATION, onPushObjectIQ);
    }

    private void processLegacyResetConversationIQ(@NonNull ConversationConnection connection,
                                                  @NonNull ConversationServiceIQ.ResetConversationIQ resetConversationIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyResetConversationIQ: connection=" + connection + " resetConversationIQ=" + resetConversationIQ);
        }

        // Verify that the user can reset the conversation.
        final ConversationImpl conversationImpl = connection.getConversation();
        if (conversationImpl.hasPermission(Permission.RESET_CONVERSATION)) {

            final Map<UUID, DescriptorId> resetList = new HashMap<>();
            final GroupConversationImpl group = conversationImpl.getGroup();
            final UUID peerTwincodeOutboundId = conversationImpl.getPeerTwincodeOutboundId();

            if (group != null) {

                List<DescriptorId> resetMembers = resetConversationIQ.resetMembers;
                if (resetMembers != null) {
                    // Notes:
                    // - Messages that have been sent are associated with the GroupConversationImpl object.
                    // - Messages that we have received are associated with a GroupMemberConversationImpl object.
                    // - The ResetGroupMember holds the max sequence Id for a member as seen by the sender.
                    // - For a group, the call to resetConversation() clears only one direction.
                    for (DescriptorId reset : resetMembers) {
                        resetList.put(reset.twincodeOutboundId, reset);
                    }
                }
            } else {
                resetList.put(conversationImpl.getTwincodeOutboundId(), new DescriptorId(0, conversationImpl.getTwincodeOutboundId(), resetConversationIQ.peerMinSequenceId));
                resetList.put(peerTwincodeOutboundId, new DescriptorId(0, peerTwincodeOutboundId, resetConversationIQ.minSequenceId));
            }

            if (resetConversation(conversationImpl, resetList, ClearMode.CLEAR_BOTH)) {

                for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                    mTwinlifeExecutor.execute(() -> serviceObserver.onResetConversation(conversationImpl, ClearMode.CLEAR_BOTH));
                }

                // Create a clear descriptor with the peer's twincode and use a fixed sequence number.
                final long now = System.currentTimeMillis();
                final DescriptorImpl clearDescriptorImpl = mServiceProvider.createDescriptor(conversationImpl.getMainConversation(),
                        (long id, long sequenceId, long cid) -> {
                    final DescriptorId descriptorId = new DescriptorId(id, peerTwincodeOutboundId, 1);
                    final ClearDescriptorImpl result = new ClearDescriptorImpl(descriptorId, cid, now);
                    result.setSentTimestamp(now);
                    result.setReceivedTimestamp(now);
                    return result;
                });

                if (clearDescriptorImpl != null) {
                    for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                        mTwinlifeExecutor.execute(() -> serviceObserver.onPopDescriptor(DEFAULT_REQUEST_ID, conversationImpl, clearDescriptorImpl));
                    }
                }
            }
        }

        int majorVersion = resetConversationIQ.getMajorVersion();
        int minorVersion = resetConversationIQ.getMinorVersion();

        ConversationServiceIQ.OnResetConversationIQ onResetConversationIQ = new ConversationServiceIQ.OnResetConversationIQ(resetConversationIQ.getId(), conversationImpl.getFrom(), conversationImpl.getTo(),
                resetConversationIQ.getRequestId(), majorVersion, minorVersion);

        boolean serialized = false;
        Exception exception = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        try {
            BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion == MAJOR_VERSION_2) {
                ConversationServiceIQ.OnResetConversationIQ.SERIALIZER.serialize(mSerializerFactory, binaryEncoder, onResetConversationIQ);
                serialized = true;
            }
        } catch (Exception lException) {
            exception = lException;
        }

        if (serialized) {
            connection.sendMessage(StatType.IQ_RESULT_RESET_CONVERSATION, outputStream.toByteArray());

        } else {
            mTwinlifeImpl.exception(ConversationAssertPoint.PROCESS_RESET_CONVERSATION_IQ, exception,
                    AssertPoint.createPeerConnectionId(connection.getPeerConnectionId()));
        }
    }

    private void processPushObjectIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processPushObjectIQ: connection=" + connection + " iq=" + iq);
        }

        final PushObjectIQ pushObjectIQ = (PushObjectIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        ObjectDescriptorImpl objectDescriptorImpl = pushObjectIQ.objectDescriptorImpl;

        // Verify that the user can send us messages.
        if (conversationImpl.hasPermission(Permission.SEND_MESSAGE)) {
            popDescriptor(objectDescriptorImpl, connection);
        } else {
            // Send him back a receive failure.
            objectDescriptorImpl.setReceivedTimestamp(-1);
        }

        int deviceState = getDeviceState(connection);
        OnPushIQ onPushObjectIQ = new OnPushIQ(OnPushObjectIQ.IQ_ON_PUSH_OBJECT_SERIALIZER, pushObjectIQ.getRequestId(), deviceState, objectDescriptorImpl.getReceivedTimestamp());

        connection.sendPacket(StatType.IQ_RESULT_PUSH_OBJECT, onPushObjectIQ);
    }

    private void processLegacyPushObjectIQ(@NonNull ConversationConnection connection,
                                           @NonNull ConversationServiceIQ.PushObjectIQ pushObjectIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyPushObjectIQ: connection=" + connection + " pushObjectRequestIQ=" + pushObjectIQ);
        }

        ObjectDescriptorImpl objectDescriptorImpl = pushObjectIQ.objectDescriptorImpl;
        final ConversationImpl conversationImpl = connection.getConversation();

        // Verify that the user can send us messages.
        if (conversationImpl.hasPermission(Permission.SEND_MESSAGE)) {
            // Invalidate the read timestamp because we could receive a value.
            objectDescriptorImpl.setReadTimestamp(0);
            popDescriptor(objectDescriptorImpl, connection);
        } else {
            // Send him back a receive failure.
            objectDescriptorImpl.setReceivedTimestamp(-1);
        }

        int majorVersion = pushObjectIQ.getMajorVersion();
        int minorVersion = pushObjectIQ.getMinorVersion();

        ConversationServiceIQ.OnPushObjectIQ onPushObjectIQ = new ConversationServiceIQ.OnPushObjectIQ(pushObjectIQ.getId(), conversationImpl.getFrom(), conversationImpl.getTo(), pushObjectIQ.getRequestId(),
                majorVersion, minorVersion, objectDescriptorImpl.getReceivedTimestamp());

        boolean serialized = false;
        Exception exception = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        try {
            BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion == MAJOR_VERSION_2) {
                ConversationServiceIQ.OnPushObjectIQ.SERIALIZER.serialize(mSerializerFactory, binaryEncoder, onPushObjectIQ);
                serialized = true;
            } else if (majorVersion == MAJOR_VERSION_1) {
                ConversationServiceIQ.OnPushObjectIQ.SERIALIZER_1.serialize(mSerializerFactory, binaryEncoder, onPushObjectIQ);
                serialized = true;
            }
        } catch (Exception lException) {
            exception = lException;
        }

        if (serialized) {
            byte[] content = outputStream.toByteArray();
            connection.sendMessage(StatType.IQ_RESULT_PUSH_OBJECT, content);
        } else {
            mTwinlifeImpl.exception(ConversationAssertPoint.PROCESS_PUSH_OBJECT_IQ, exception,
                    AssertPoint.createPeerConnectionId(connection.getPeerConnectionId())
                            .putVersion(majorVersion, minorVersion));
        }
    }

    private void processUpdateObjectIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processUpdateObjectIQ: connection=" + connection + " iq=" + iq);
        }

        final UpdateDescriptorIQ updateDescriptorIQ = (UpdateDescriptorIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        final long receiveTimestamp;

        // Verify that the user can send us messages.
        if (conversationImpl.hasPermission(Permission.SEND_MESSAGE)) {

            final DescriptorImpl descriptor = mServiceProvider.loadDescriptorImpl(updateDescriptorIQ.descriptorId);
            if (descriptor != null) {
                receiveTimestamp = System.currentTimeMillis();

                boolean updated;
                final UpdateType updateType;
                if (descriptor instanceof ObjectDescriptorImpl) {
                    final ObjectDescriptorImpl objectDescriptor = (ObjectDescriptorImpl) descriptor;

                    updated = objectDescriptor.setMessage(updateDescriptorIQ.message);
                    updateType = updated ? UpdateType.CONTENT : UpdateType.PROTECTION;
                    updated |= objectDescriptor.setCopyAllowed(updateDescriptorIQ.copyAllowed);
                    updated |= objectDescriptor.setExpireTimeout(updateDescriptorIQ.expiredTimeout);
                    if (updated) {
                        objectDescriptor.setEdited();
                    }

                } else if (descriptor instanceof FileDescriptorImpl) {
                    final FileDescriptorImpl fileDescriptor = (FileDescriptorImpl) descriptor;

                    updated = fileDescriptor.setCopyAllowed(updateDescriptorIQ.copyAllowed);
                    updated |= fileDescriptor.setExpireTimeout(updateDescriptorIQ.expiredTimeout);
                    updateType = UpdateType.PROTECTION;
                } else {
                    updated = false;
                    updateType = UpdateType.PROTECTION;
                }
                if (updated) {
                    descriptor.setUpdatedTimestamp(connection.getAdjustedTime(updateDescriptorIQ.updatedTimestamp));
                    mServiceProvider.updateDescriptor(descriptor);

                    // If the message was inserted, propagate it to upper layers through the onPopDescriptor callback.
                    // Otherwise, we already know the message and we only need to acknowledge the sender.
                    connection.getConversation().setIsActive(true);

                    final Conversation lCconversation = connection.getMainConversation();
                    for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                        mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID, lCconversation, descriptor, updateType));
                    }
                }
            } else {
                receiveTimestamp = -1;
            }
        } else {
            // Send him back a receive failure.
            receiveTimestamp = -1;
        }

        int deviceState = getDeviceState(connection);
        OnPushIQ onPushObjectIQ = new OnPushIQ(OnUpdateDescriptorIQ.IQ_ON_UPDATE_DESCRIPTOR_SERIALIZER, updateDescriptorIQ.getRequestId(), deviceState, receiveTimestamp);

        connection.sendPacket(StatType.IQ_RESULT_UPDATE_OBJECT, onPushObjectIQ);
    }

    private void processPushTransientIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processPushTransientIQ: connection=" + connection + " iq=" + iq);
        }

        final PushTransientIQ pushTransientIQ = (PushTransientIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        final TransientObjectDescriptorImpl transientObjectDescriptorImpl = pushTransientIQ.transientDescriptorImpl;
        transientObjectDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());

        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onPopDescriptor(DEFAULT_REQUEST_ID, conversationImpl,
                    transientObjectDescriptorImpl));
        }

        if (pushTransientIQ.flags != 0) {
            int deviceState = getDeviceState(connection);
            OnPushIQ onPushTransientIQ = new OnPushIQ(OnPushCommandIQ.IQ_ON_PUSH_COMMAND_SERIALIZER, pushTransientIQ.getRequestId(), deviceState, transientObjectDescriptorImpl.getReceivedTimestamp());

            connection.sendPacket(StatType.IQ_RESULT_PUSH_TRANSIENT, onPushTransientIQ);
        }
    }

    private void processLegacyPushTransientObjectIQ(@NonNull ConversationConnection connection, @NonNull ConversationServiceIQ.PushTransientObjectIQ pushTransientObjectIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyPushTransientObjectIQ: connection=" + connection + " pushTransientObjectRequestIQ=" + pushTransientObjectIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final TransientObjectDescriptorImpl transientObjectDescriptorImpl = pushTransientObjectIQ.transientObjectDescriptorImpl;
        transientObjectDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());

        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onPopDescriptor(DEFAULT_REQUEST_ID, conversationImpl,
                    transientObjectDescriptorImpl));
        }
    }

    private void processLegacyPushCommandIQ(@NonNull ConversationConnection connection,
                                            @NonNull ConversationServiceIQ.PushCommandIQ pushCommandIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyPushCommandIQ: connection=" + connection + " pushCommandIQ=" + pushCommandIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final TransientObjectDescriptorImpl commandDescriptorImpl = pushCommandIQ.commandDescriptorImpl;
        commandDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());

        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onPopDescriptor(DEFAULT_REQUEST_ID, conversationImpl,
                    commandDescriptorImpl));
        }

        int majorVersion = pushCommandIQ.getMajorVersion();
        int minorVersion = pushCommandIQ.getMinorVersion();

        ConversationServiceIQ.OnPushCommandIQ onPushCommandIQ = new ConversationServiceIQ.OnPushCommandIQ(pushCommandIQ.getId(),
                connection.getFrom(), connection.getTo(), pushCommandIQ.getRequestId(),
                majorVersion, minorVersion, commandDescriptorImpl.getReceivedTimestamp());

        byte[] data = onPushCommandIQ.serialize(mSerializerFactory, majorVersion, minorVersion);
        connection.sendMessage(StatType.IQ_RESULT_PUSH_TRANSIENT, data);
    }

    private void processLegacyPushFileIQ(@NonNull ConversationConnection connection, @NonNull ConversationServiceIQ.PushFileIQ pushFileIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyPushFileIQ: connection=" + connection + " pushFileIQ=" + pushFileIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        FileDescriptorImpl fileDescriptorImpl = pushFileIQ.fileDescriptorImpl;
        Permission permission;
        switch (fileDescriptorImpl.getType()) {
            case FILE_DESCRIPTOR:
            case NAMED_FILE_DESCRIPTOR:
                permission = Permission.SEND_FILE;
                break;

            case AUDIO_DESCRIPTOR:
                permission = Permission.SEND_AUDIO;
                break;

            case IMAGE_DESCRIPTOR:
                permission = Permission.SEND_IMAGE;
                break;

            case VIDEO_DESCRIPTOR:
                permission = Permission.SEND_VIDEO;
                break;

            default:
                permission = null;
                break;
        }

        // Verify that the user can send us file/audio/image/video.
        if (conversationImpl.hasPermission(permission)) {
            // Create the path only when the permission is granted.
            String path = getPath(fileDescriptorImpl.getTwincodeOutboundId(), fileDescriptorImpl.getSequenceId(), fileDescriptorImpl.getExtension());
            if (path == null) {
                // Don't create the file descriptor if we cannot save the file, report a failure to the caller.
                fileDescriptorImpl.setReceivedTimestamp(-1);
            } else {

                fileDescriptorImpl.setPath(path);
                // Invalidate the read timestamp because we could receive a value.
                fileDescriptorImpl.setReadTimestamp(0);
                popDescriptor(fileDescriptorImpl, connection);
            }
        } else {
            // Send him back a receive failure.
            fileDescriptorImpl.setReceivedTimestamp(-1);
        }

        int majorVersion = pushFileIQ.getMajorVersion();
        int minorVersion = pushFileIQ.getMinorVersion();

        ConversationServiceIQ.OnPushFileIQ onPushFileIQ = new ConversationServiceIQ.OnPushFileIQ(pushFileIQ.getId(), conversationImpl.getFrom(), conversationImpl.getTo(), pushFileIQ.getRequestId(),
                majorVersion, minorVersion, fileDescriptorImpl.getReceivedTimestamp());

        boolean serialized = false;
        Exception exception = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        try {
            BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion == MAJOR_VERSION_2) {
                ConversationServiceIQ.OnPushFileIQ.SERIALIZER.serialize(mSerializerFactory, binaryEncoder, onPushFileIQ);
                serialized = true;
            }
        } catch (Exception lException) {
            exception = lException;
        }

        if (serialized) {
            connection.sendMessage(StatType.IQ_RESULT_PUSH_FILE, outputStream.toByteArray());

        } else {
            mTwinlifeImpl.exception(ConversationAssertPoint.PROCESS_PUSH_FILE_IQ, exception,
                    AssertPoint.createPeerConnectionId(connection.getPeerConnectionId())
                            .putVersion(majorVersion, minorVersion));
        }
    }

    private void processPushThumbnailIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processPushThumbnailIQ: connection=" + connection + " iq=" + iq);
        }

        final PushFileChunkIQ pushChunkIQ = (PushFileChunkIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        if (pushChunkIQ.chunk == null) {
            return;
        }

        // Verify that the user can send us file/audio/image/video.
        if (conversationImpl.hasPermission(Permission.SEND_FILE)
            || conversationImpl.hasPermission(Permission.SEND_AUDIO)
            || conversationImpl.hasPermission(Permission.SEND_IMAGE)
            || conversationImpl.hasPermission(Permission.SEND_VIDEO)) {
            writeThumbnail(pushChunkIQ.descriptorId, pushChunkIQ.chunk, pushChunkIQ.chunkStart > 0);
        }
    }

    private void processPushFileIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processPushFileIQ: connection=" + connection + " iq=" + iq);
        }

        final PushFileIQ pushFileIQ = (PushFileIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        FileDescriptorImpl fileDescriptorImpl = pushFileIQ.fileDescriptorImpl;
        Permission permission;
        switch (fileDescriptorImpl.getType()) {
            case FILE_DESCRIPTOR:
            case NAMED_FILE_DESCRIPTOR:
                permission = Permission.SEND_FILE;
                break;

            case AUDIO_DESCRIPTOR:
                permission = Permission.SEND_AUDIO;
                break;

            case IMAGE_DESCRIPTOR:
                permission = Permission.SEND_IMAGE;
                break;

            case VIDEO_DESCRIPTOR:
                permission = Permission.SEND_VIDEO;
                break;

            default:
                permission = null;
                break;
        }

        // Verify that the user can send us file/audio/image/video.
        if (conversationImpl.hasPermission(permission)) {
            // Create the path only when the permission is granted.
            String path = saveThumbnail(fileDescriptorImpl, pushFileIQ.thumbnail);
            if (path == null) {
                // Don't create the file descriptor if we cannot save the file, report a failure to the caller.
                fileDescriptorImpl.setReceivedTimestamp(-1);
            } else {

                fileDescriptorImpl.setPath(path);
                // Invalidate the read timestamp because we could receive a value.
                fileDescriptorImpl.setReadTimestamp(0);
                popDescriptor(fileDescriptorImpl, connection);

            }
        } else {
            // Send him back a receive failure.
            fileDescriptorImpl.setReceivedTimestamp(-1);
        }

        int deviceState = getDeviceState(connection);
        OnPushIQ onPushFileIQ = new OnPushIQ(OnPushFileIQ.IQ_ON_PUSH_FILE_SERIALIZER, pushFileIQ.getRequestId(), deviceState, fileDescriptorImpl.getReceivedTimestamp());

        connection.sendPacket(StatType.IQ_RESULT_PUSH_FILE, onPushFileIQ);
    }

    private void processPushFileChunkIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processPushFileChunkIQ: connection=" + connection + " iq=" + iq);
        }

        final PushFileChunkIQ pushFileChunkIQ = (PushFileChunkIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        final FileDescriptorImpl fileDescriptorImpl;
        OnPushFileChunkIQ onPushFileChunkIQ;
        int deviceState = getDeviceState(connection);
        boolean isAvailable;
        DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(pushFileChunkIQ.descriptorId);
        if (!(descriptorImpl instanceof FileDescriptorImpl)) {
            // The descriptor may not exist if there was a creation failure in processPushFileIQ().
            // If we don't respond, the other peer will hang until we reply.  By returning a -1 receive time and
            // a LONG value, the peer will mark the file as not being received and will also stop sending us more chunks.
            onPushFileChunkIQ = new OnPushFileChunkIQ(OnPushFileChunkIQ.IQ_ON_PUSH_FILE_CHUNK_SERIALIZER, pushFileChunkIQ.getRequestId(), deviceState, -1L, pushFileChunkIQ.timestamp, -1L);
            fileDescriptorImpl = null;
            isAvailable = false;

        } else {
            fileDescriptorImpl = (FileDescriptorImpl) descriptorImpl;
            long now;

            long end = connection.writeChunk(mTwinlifeImpl.getFilesDir(), fileDescriptorImpl, pushFileChunkIQ.chunkStart, pushFileChunkIQ.chunk);
            if (end >= 0) {
                now = System.currentTimeMillis();
                fileDescriptorImpl.setEnd(end);
                fileDescriptorImpl.setUpdatedTimestamp(now);
                fileDescriptorImpl.setReceivedTimestamp(now);
                mServiceProvider.updateDescriptor(fileDescriptorImpl);
                isAvailable = fileDescriptorImpl.isAvailable();
            } else {
                // Something wrong happened when saving the file, report and error to the peer.
                // (SD card could be full).
                // Don't return a negative value: we want the peer to stop sending more chunks.
                // The file being incomplete, there is no way to access and view it so we remove
                // the file descriptor and the file itself.
                end = Long.MAX_VALUE;
                now = -1L;
                isAvailable = false;
                fileDescriptorImpl.setUpdatedTimestamp(now);
                fileDescriptorImpl.setReceivedTimestamp(now);
                deleteConversationDescriptor(DEFAULT_REQUEST_ID, conversationImpl, fileDescriptorImpl);
            }
            onPushFileChunkIQ = new OnPushFileChunkIQ(OnPushFileChunkIQ.IQ_ON_PUSH_FILE_CHUNK_SERIALIZER, pushFileChunkIQ.getRequestId(), deviceState, now, pushFileChunkIQ.timestamp, end);
        }

        connection.sendPacket(StatType.IQ_RESULT_PUSH_FILE_CHUNK, onPushFileChunkIQ);

        // Notify the progress only when we are in foreground or if we have received the complete file.
        if (fileDescriptorImpl != null && (isAvailable || (deviceState & ConversationConnection.DEVICE_STATE_FOREGROUND) != 0)) {
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID,
                        conversationImpl, fileDescriptorImpl, UpdateType.CONTENT));
            }
        }
    }

    private void processLegacyPushFileChunkIQ(@NonNull ConversationConnection connection,
                                              @NonNull ConversationServiceIQ.PushFileChunkIQ pushFileChunkIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyPushFileChunkIQ: connection=" + connection + " pushFileChunkIQ=" + pushFileChunkIQ);
        }

        int majorVersion = pushFileChunkIQ.getMajorVersion();
        int minorVersion = pushFileChunkIQ.getMinorVersion();

        // TBD chunkStart...
        final FileDescriptorImpl fileDescriptorImpl;
        final ConversationImpl conversationImpl = connection.getConversation();
        ConversationServiceIQ.OnPushFileChunkIQ onPushFileChunkIQ;
        DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(pushFileChunkIQ.descriptorId);
        if (!(descriptorImpl instanceof FileDescriptorImpl)) {
            // The descriptor may not exist if there was a creation failure in processPushFileIQ().
            // If we don't respond, the other peer will hang until we reply.  By returning a -1 receive time and
            // a LONG value, the peer will mark the file as not being received and will also stop sending us more chunks.
            onPushFileChunkIQ = new ConversationServiceIQ.OnPushFileChunkIQ(pushFileChunkIQ.getId(), connection.getFrom(), connection.getTo(),
                    pushFileChunkIQ.getRequestId(), majorVersion, minorVersion, -1, Long.MAX_VALUE);
            fileDescriptorImpl = null;

        } else {
            fileDescriptorImpl = (FileDescriptorImpl) descriptorImpl;
            long end = connection.writeChunk(mTwinlifeImpl.getFilesDir(), fileDescriptorImpl, pushFileChunkIQ.chunkStart, pushFileChunkIQ.chunk);
            long now;

            if (pushFileChunkIQ.chunk.length <= end) {
                now = System.currentTimeMillis();
                fileDescriptorImpl.setEnd(end);
                fileDescriptorImpl.setUpdatedTimestamp(now);
                fileDescriptorImpl.setReceivedTimestamp(now);
                mServiceProvider.updateDescriptor(fileDescriptorImpl);
            } else {
                // Something wrong happened when saving the file, report and error to the peer.
                // (SD card could be full).
                // Don't return a negative value: we want the peer to stop sending more chunks.
                // The file being incomplete, there is no way to access and view it so we remove
                // the file descriptor and the file itself.
                end = Long.MAX_VALUE;
                now = -1;
                fileDescriptorImpl.setUpdatedTimestamp(now);
                fileDescriptorImpl.setReceivedTimestamp(now);
                deleteConversationDescriptor(DEFAULT_REQUEST_ID, conversationImpl, fileDescriptorImpl);
            }
            onPushFileChunkIQ = new ConversationServiceIQ.OnPushFileChunkIQ(pushFileChunkIQ.getId(), connection.getFrom(), connection.getTo(),
                    pushFileChunkIQ.getRequestId(), majorVersion, minorVersion, now, end);
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
        binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

        if (majorVersion == MAJOR_VERSION_1) {
            throw new SerializerException();
        }

        ConversationServiceIQ.OnPushFileChunkIQ.SERIALIZER.serialize(mSerializerFactory, binaryEncoder, onPushFileChunkIQ);
        connection.sendMessage(StatType.IQ_RESULT_PUSH_FILE_CHUNK, outputStream.toByteArray());

        if (fileDescriptorImpl != null) {
            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID,
                        conversationImpl, fileDescriptorImpl, UpdateType.CONTENT));
            }
        }
    }

    private void processPushGeolocationIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processPushGeolocationIQ: connection=" + connection + " iq=" + iq);
        }

        final PushGeolocationIQ pushGeolocationIQ = (PushGeolocationIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        GeolocationDescriptorImpl geolocationDescriptorImpl = pushGeolocationIQ.geolocationDescriptorImpl;

        // Verify that the user can send us geolocation.
        if (conversationImpl.hasPermission(Permission.SEND_GEOLOCATION)) {
            DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(geolocationDescriptorImpl.getDescriptorId());
            if (descriptorImpl == null) {

                geolocationDescriptorImpl.setLocalMapPath(null);
                popDescriptor(geolocationDescriptorImpl, connection);

            } else if (descriptorImpl instanceof GeolocationDescriptorImpl) {
                GeolocationDescriptorImpl currentGeolocationDescriptorImpl = (GeolocationDescriptorImpl) descriptorImpl;

                // We already know the geolocation and it was updated, propagate through onUpdateDescriptor.
                if (currentGeolocationDescriptorImpl.update(geolocationDescriptorImpl)) {
                    currentGeolocationDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());
                    mServiceProvider.updateDescriptor(currentGeolocationDescriptorImpl);

                    for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                        mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID, conversationImpl, geolocationDescriptorImpl, UpdateType.CONTENT));
                    }
                }
            }
        } else {
            // Send him back a receive failure.
            geolocationDescriptorImpl.setReceivedTimestamp(-1);
        }

        int deviceState = getDeviceState(connection);
        OnPushIQ onPushGeolocationIQ = new OnPushIQ(OnPushGeolocationIQ.IQ_ON_PUSH_GEOLOCATION_SERIALIZER, pushGeolocationIQ.getRequestId(), deviceState, geolocationDescriptorImpl.getReceivedTimestamp());

        connection.sendPacket(StatType.IQ_RESULT_PUSH_GEOLOCATION, onPushGeolocationIQ);
    }

    private void processLegacyPushGeolocationIQ(@NonNull ConversationConnection connection,
                                                @NonNull ConversationServiceIQ.PushGeolocationIQ pushGeolocationIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyPushGeolocationIQ: connection=" + connection
                    + " pushGeolocationIQ=" + pushGeolocationIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        GeolocationDescriptorImpl geolocationDescriptorImpl = pushGeolocationIQ.geolocationDescriptorImpl;

        // Verify that the user can send us geolocation.
        if (conversationImpl.hasPermission(Permission.SEND_GEOLOCATION)) {
            DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(geolocationDescriptorImpl.getDescriptorId());
            if (descriptorImpl == null) {

                geolocationDescriptorImpl.setLocalMapPath(null);
                // Invalidate the read timestamp because we could receive a value.
                geolocationDescriptorImpl.setReadTimestamp(0);
                popDescriptor(geolocationDescriptorImpl, connection);

            } else if (descriptorImpl instanceof GeolocationDescriptorImpl) {
                GeolocationDescriptorImpl currentGeolocationDescriptorImpl = (GeolocationDescriptorImpl) descriptorImpl;

                // We already know the geolocation and it was updated, propagate through onUpdateDescriptor.
                if (currentGeolocationDescriptorImpl.update(geolocationDescriptorImpl)) {
                    currentGeolocationDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());
                    mServiceProvider.updateDescriptor(currentGeolocationDescriptorImpl);

                    for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                        mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID, conversationImpl, geolocationDescriptorImpl, UpdateType.CONTENT));
                    }
                }
            }
        } else {
            // Send him back a receive failure.
            geolocationDescriptorImpl.setReceivedTimestamp(-1);
        }

        int majorVersion = pushGeolocationIQ.getMajorVersion();
        int minorVersion = pushGeolocationIQ.getMinorVersion();

        ConversationServiceIQ.OnPushGeolocationIQ onPushObjectIQ = new ConversationServiceIQ.OnPushGeolocationIQ(pushGeolocationIQ.getId(), conversationImpl.getFrom(), conversationImpl.getTo(), pushGeolocationIQ.getRequestId(),
                majorVersion, minorVersion, geolocationDescriptorImpl.getReceivedTimestamp());

        byte[] data = onPushObjectIQ.serialize(mSerializerFactory, majorVersion, minorVersion);
        connection.sendMessage(StatType.IQ_RESULT_PUSH_GEOLOCATION, data);
    }

    private void processPushTwincodeIQ(@NonNull ConversationConnection connection,
                                       @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processPushTwincodeIQ: connection=" + connection + " iq=" + iq);
        }

        final PushTwincodeIQ pushTwincodeIQ = (PushTwincodeIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        TwincodeDescriptorImpl twincodeDescriptorImpl = pushTwincodeIQ.twincodeDescriptorImpl;

        // Verify that the user can send us twincodes and that we recognize the schema.
        if (conversationImpl.hasPermission(Permission.SEND_TWINCODE) && mAcceptedPushTwincode.contains(twincodeDescriptorImpl.getSchemaId())) {
            DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(twincodeDescriptorImpl.getDescriptorId());
            if (descriptorImpl == null) {
                popDescriptor(twincodeDescriptorImpl, connection);
            }
        } else {
            // Send him back a receive failure.
            twincodeDescriptorImpl.setReceivedTimestamp(-1);
        }

        int deviceState = getDeviceState(connection);
        OnPushIQ onPushTwincodeIQ = new OnPushIQ(OnPushTwincodeIQ.IQ_ON_PUSH_TWINCODE_SERIALIZER, pushTwincodeIQ.getRequestId(), deviceState, twincodeDescriptorImpl.getReceivedTimestamp());

        connection.sendPacket(StatType.IQ_RESULT_PUSH_TWINCODE, onPushTwincodeIQ);
    }

    private void processLegacyPushTwincodeIQ(@NonNull ConversationConnection connection,
                                             @NonNull ConversationServiceIQ.PushTwincodeIQ pushTwincodeIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyPushTwincodeIQ: connection=" + connection
                    + " pushTwincodeIQ=" + pushTwincodeIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        TwincodeDescriptorImpl twincodeDescriptorImpl = pushTwincodeIQ.twincodeDescriptorImpl;

        // Verify that the user can send us twincodes and that we recognize the schema.
        if (conversationImpl.hasPermission(Permission.SEND_TWINCODE) && mAcceptedPushTwincode.contains(twincodeDescriptorImpl.getSchemaId())) {
            DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(twincodeDescriptorImpl.getDescriptorId());
            if (descriptorImpl == null) {
                // Invalidate the read timestamp because we could receive a value.
                twincodeDescriptorImpl.setReadTimestamp(0);
                popDescriptor(twincodeDescriptorImpl, connection);
            }
        } else {
            // Send him back a receive failure.
            twincodeDescriptorImpl.setReceivedTimestamp(-1);
        }

        int majorVersion = pushTwincodeIQ.getMajorVersion();
        int minorVersion = pushTwincodeIQ.getMinorVersion();

        ConversationServiceIQ.OnPushTwincodeIQ onPushTwincodeIQ = new ConversationServiceIQ.OnPushTwincodeIQ(pushTwincodeIQ.getId(), conversationImpl.getFrom(), conversationImpl.getTo(), pushTwincodeIQ.getRequestId(),
                majorVersion, minorVersion, twincodeDescriptorImpl.getReceivedTimestamp());

        byte[] data = onPushTwincodeIQ.serialize(mSerializerFactory, majorVersion, minorVersion);
        connection.sendMessage(StatType.IQ_RESULT_PUSH_TWINCODE, data);
    }

    private void processUpdateAnnotationIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processUpdateAnnotationIQ: connection=" + connection + " iq=" + iq);
        }

        final UpdateAnnotationIQ updateAnnotationIQ = (UpdateAnnotationIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        final long timestamp;
        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(updateAnnotationIQ.descriptorId);
        if (descriptorImpl != null) {
            boolean modified = false;
            final Set<TwincodeOutbound> annotatingUsers = new HashSet<>();
            for (final Map.Entry<UUID, List<DescriptorAnnotation>> annotationEntry : updateAnnotationIQ.annotations.entrySet()) {
                final UUID peerTwincodeOutboundId = annotationEntry.getKey();
                final List<DescriptorAnnotation> list = annotationEntry.getValue();

                // A twinroom engine can send us back our annotations but we don't want to insert them again.
                if (!peerTwincodeOutboundId.equals(conversationImpl.getTwincodeOutboundId())) {
                    modified |= mServiceProvider.setAnnotations(descriptorImpl, peerTwincodeOutboundId, list, annotatingUsers);
                }
            }
            if (modified && !annotatingUsers.isEmpty()) {

                for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                    mTwinlifeExecutor.execute(() -> {
                        for (TwincodeOutbound twincodeOutbound : annotatingUsers) {
                            serviceObserver.onUpdateAnnotation(DEFAULT_REQUEST_ID, conversationImpl, descriptorImpl, twincodeOutbound);
                        }
                    });
                }
            }
            timestamp = System.currentTimeMillis();
        } else {
            timestamp = -1L;
        }

        int deviceState = getDeviceState(connection);
        OnPushIQ onUpdateAnnotationIQ = new OnPushIQ(OnUpdateAnnotationIQ.IQ_ON_UPDATE_ANNOTATION_SERIALIZER, updateAnnotationIQ.getRequestId(),
                deviceState, timestamp);

        connection.sendPacket(StatType.IQ_RESULT_UPDATE_OBJECT, onUpdateAnnotationIQ);
    }

    private void processInviteGroupIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processInviteGroupIQ: connection=" + connection + " iq=" + iq);
        }

        final InviteGroupIQ inviteGroupIQ = (InviteGroupIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        final long receivedTimestamp = mGroupManager.processInviteGroup(conversationImpl, inviteGroupIQ.invitationDescriptorImpl);

        int deviceState = getDeviceState(connection);
        OnPushIQ onInviteGroupIQ = new OnPushIQ(OnInviteGroupIQ.IQ_ON_INVITE_GROUP_SERIALIZER, inviteGroupIQ.getRequestId(),
                deviceState, receivedTimestamp);

        connection.sendPacket(StatType.IQ_RESULT_INVITE_GROUP, onInviteGroupIQ);
    }

    private void processLegacyInviteGroupIQ(@NonNull ConversationConnection connection,
                                            @NonNull ConversationServiceIQ.InviteGroupIQ inviteGroupIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processInviteGroupIQ: connection=" + connection + " InviteGroupIQ=" + inviteGroupIQ);
        }

        int majorVersion = inviteGroupIQ.getMajorVersion();
        int minorVersion = inviteGroupIQ.getMinorVersion();
        final ConversationImpl conversationImpl = connection.getConversation();

        mGroupManager.processInviteGroup(conversationImpl, inviteGroupIQ.invitationDescriptor);

        OnResultGroupIQ onInviteGroupIQ = new OnResultGroupIQ(inviteGroupIQ.getId(), connection.getFrom(), connection.getTo(),
                ConversationServiceIQ.ON_INVITE_GROUP_ACTION, inviteGroupIQ.getRequestId(), majorVersion, minorVersion);

        final boolean needLeadingPadding = connection.needLeadingPadding();
        byte[] content = onInviteGroupIQ.serialize(mSerializerFactory, majorVersion, minorVersion, needLeadingPadding);
        connection.sendMessage(StatType.IQ_RESULT_INVITE_GROUP, content);
    }

    private void processLegacyRevokeInviteGroupIQ(@NonNull ConversationConnection connection, @NonNull RevokeInviteGroupIQ revokeInviteGroupIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyRevokeInviteGroupIQ: connection=" + connection + " RevokeInviteGroupIQ=" + revokeInviteGroupIQ);
        }

        int majorVersion = revokeInviteGroupIQ.getMajorVersion();
        int minorVersion = revokeInviteGroupIQ.getMinorVersion();
        final ConversationImpl conversationImpl = connection.getConversation();

        mGroupManager.processRevokeInviteGroup(conversationImpl, revokeInviteGroupIQ.invitationDescriptor.getDescriptorId());

        OnResultGroupIQ onRevokeInviteGroupIQ = new OnResultGroupIQ(revokeInviteGroupIQ.getId(), connection.getFrom(), connection.getTo(),
                ConversationServiceIQ.ON_REVOKE_INVITE_GROUP_ACTION, revokeInviteGroupIQ.getRequestId(), majorVersion, minorVersion);

        final boolean needLeadingPadding = connection.needLeadingPadding();
        byte[] content = onRevokeInviteGroupIQ.serialize(mSerializerFactory, majorVersion, minorVersion, needLeadingPadding);
        connection.sendMessage(StatType.IQ_RESULT_WITHDRAW_INVITE_GROUP, content);
    }

    private void processJoinGroupIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processJoinGroupIQ: connection=" + connection + " iq=" + iq);
        }

        final JoinGroupIQ joinGroupIQ = (JoinGroupIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        if (joinGroupIQ.memberTwincodeId != null && joinGroupIQ.publicKey != null) {
            // Invitation was accepted: get the signed twincode and verify it.
            mTwincodeOutboundService.getSignedTwincodeWithSecret(joinGroupIQ.memberTwincodeId, joinGroupIQ.publicKey, 1, joinGroupIQ.secretKey, TrustMethod.PEER, (ErrorCode errorCode, TwincodeOutbound twincodeOutbound) -> {

                // If we are offline or timed out don't acknowledge the joinIQ: the peer must retry
                // (we must force a close of the P2P connection in case it is still opened).
                if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                    close(connection, false, connection.getPeerConnectionId(), TerminateReason.CONNECTIVITY_ERROR);
                    return;
                }

                final GroupConversationManager.JoinResult joinResult;
                if (twincodeOutbound != null) {
                    joinResult = mGroupManager.processJoinGroup(conversationImpl, joinGroupIQ.groupTwincodeId,
                            joinGroupIQ.invitationDescriptorId, twincodeOutbound, joinGroupIQ.publicKey);
                } else {
                    mGroupManager.processRejectJoinGroup(conversationImpl, joinGroupIQ.invitationDescriptorId);
                    joinResult = null;
                }

                final int deviceState = getDeviceState(connection);
                final SignatureInfoIQ signatureInfo = joinResult != null && joinResult.inviterMemberTwincode != null ? mCryptoService.getSignatureInfoIQ(joinResult.inviterMemberTwincode, twincodeOutbound, false) : null;
                final OnJoinGroupIQ onJoinGroupIQ;
                if (signatureInfo != null) {
                    mCryptoService.validateSecrets(joinResult.inviterMemberTwincode, twincodeOutbound);
                    // User joined the group
                    onJoinGroupIQ = OnJoinGroupIQ.ok(joinGroupIQ.getRequestId(), deviceState,
                            signatureInfo, joinResult.inviterPermissions, joinResult.memberPermissions,
                            null, joinResult.signature, joinResult.members);
                } else {
                    // Invitation was withdrawn.
                    onJoinGroupIQ = OnJoinGroupIQ.fail(joinGroupIQ.getRequestId(), deviceState);
                }
                connection.sendPacket(StatType.IQ_RESULT_JOIN_GROUP, onJoinGroupIQ);
            });
        } else {
            // Invitation was refused.
            mGroupManager.processRejectJoinGroup(conversationImpl, joinGroupIQ.invitationDescriptorId);

            int deviceState = getDeviceState(connection);
            OnJoinGroupIQ onJoinGroupIQ = OnJoinGroupIQ.fail(joinGroupIQ.getRequestId(), deviceState);

            connection.sendPacket(StatType.IQ_RESULT_JOIN_GROUP, onJoinGroupIQ);
        }
    }

    private void processLegacyJoinGroupIQ(@NonNull ConversationConnection connection,
                                          @NonNull ConversationServiceIQ.JoinGroupIQ joinGroupIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyJoinGroupIQ: connection=" + connection + " InviteGroupIQ=" + joinGroupIQ);
        }

        int majorVersion = joinGroupIQ.getMajorVersion();
        int minorVersion = joinGroupIQ.getMinorVersion();

        final ConversationImpl conversationImpl = connection.getConversation();
        final UUID groupTwincodeId = joinGroupIQ.getGroupTwincodeId();
        final DescriptorId descriptorId;
        final InvitationDescriptor.Status status;
        final UUID memberTwincodeId = joinGroupIQ.getMemberTwincodeId();
        if (joinGroupIQ.invitationDescriptor != null) {
            descriptorId = joinGroupIQ.invitationDescriptor.getDescriptorId();
            status = joinGroupIQ.invitationDescriptor.getStatus();
        } else {
            descriptorId = null;
            status = InvitationDescriptor.Status.ACCEPTED;
        }
        if (memberTwincodeId != null && status == InvitationDescriptor.Status.ACCEPTED) {
            mTwincodeOutboundService.getTwincode(memberTwincodeId, TwincodeOutboundService.REFRESH_PERIOD, (ErrorCode errorCode, TwincodeOutbound twincodeOutbound) -> {

                // If we are offline or timed out don't acknowledge the joinIQ: the peer must retry
                // (we must force a close of the P2P connection in case it is still opened).
                if (errorCode == ErrorCode.TIMEOUT_ERROR || errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                    close(connection, false, connection.getPeerConnectionId(), TerminateReason.CONNECTIVITY_ERROR);
                    return;
                }

                final GroupConversationManager.JoinResult result;
                if (twincodeOutbound == null) {
                    // New member twincode does not exist, reject the invitation if we have one.
                    if (descriptorId != null) {
                        mGroupManager.processRejectJoinGroup(conversationImpl, descriptorId);
                    }
                    result = null;
                } else if (descriptorId != null) {
                    // Accept the invitation.
                    result = mGroupManager.processJoinGroup(conversationImpl, groupTwincodeId, descriptorId, twincodeOutbound, null);

                    // We must add the inviter twincode in the result list for the Legacy OnJoinIQ.
                    if (result != null && result.inviterMemberTwincode != null) {
                        result.members.add(new OnJoinGroupIQ.MemberInfo(result.inviterMemberTwincode.getId(), null, result.inviterPermissions));
                    }
                } else {
                    // A new member has joined the group (invited by someone else).
                    result = mGroupManager.processJoinGroup(groupTwincodeId, twincodeOutbound, joinGroupIQ.permissions);
                }
                try {
                    final OnResultJoinIQ onJoinGroupIQ;
                    if (result == null) {
                        onJoinGroupIQ = new OnResultJoinIQ(joinGroupIQ.getId(), connection.getFrom(), connection.getTo(),
                                ConversationServiceIQ.ON_JOIN_GROUP_ACTION, joinGroupIQ.getRequestId(), majorVersion, minorVersion,
                                InvitationDescriptor.Status.WITHDRAWN, 0, null);
                    } else {
                        onJoinGroupIQ = new OnResultJoinIQ(joinGroupIQ.getId(), connection.getFrom(), connection.getTo(),
                                ConversationServiceIQ.ON_JOIN_GROUP_ACTION, joinGroupIQ.getRequestId(), majorVersion, minorVersion,
                                result.status, result.memberPermissions, result.members);
                    }
                    byte[] content = onJoinGroupIQ.serialize(mSerializerFactory, majorVersion, minorVersion);
                    connection.sendMessage(StatType.IQ_RESULT_JOIN_GROUP, content);
                } catch (SerializerException exception) {
                    if (Logger.ERROR) {
                        Log.e(LOG_TAG, "Serialize exception", exception);
                    }
                }
            });
        } else {
            if (descriptorId != null) {
                mGroupManager.processRejectJoinGroup(conversationImpl, descriptorId);
            }

            OnResultJoinIQ onJoinGroupIQ = new OnResultJoinIQ(joinGroupIQ.getId(), connection.getFrom(), connection.getTo(),
                    ConversationServiceIQ.ON_JOIN_GROUP_ACTION, joinGroupIQ.getRequestId(), majorVersion, minorVersion,
                    InvitationDescriptor.Status.WITHDRAWN, 0, null);

            byte[] content = onJoinGroupIQ.serialize(mSerializerFactory, majorVersion, minorVersion);
            connection.sendMessage(StatType.IQ_RESULT_JOIN_GROUP, content);
        }
    }

    private void processLegacyLeaveGroupIQ(@NonNull ConversationConnection connection, @NonNull LeaveGroupIQ leaveGroupIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyLeaveGroupIQ: connection=" + connection + " InviteGroupIQ=" + leaveGroupIQ);
        }

        int majorVersion = leaveGroupIQ.getMajorVersion();
        int minorVersion = leaveGroupIQ.getMinorVersion();

        mGroupManager.processLeaveGroup(leaveGroupIQ.groupTwincodeId, leaveGroupIQ.memberTwincodeId);

        final boolean needLeadingPadding = connection.needLeadingPadding();
        OnResultGroupIQ onLeaveGroupIQ = new OnResultGroupIQ(leaveGroupIQ.getId(), connection.getFrom(), connection.getTo(),
                ConversationServiceIQ.ON_LEAVE_GROUP_ACTION, leaveGroupIQ.getRequestId(), majorVersion, minorVersion);

        byte[] content = onLeaveGroupIQ.serialize(mSerializerFactory, majorVersion, minorVersion, needLeadingPadding);
        connection.sendMessage(StatType.IQ_RESULT_LEAVE_GROUP, content);
    }

    private void processUpdatePermissionsIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processUpdatePermissionsIQ: connection=" + connection + " iq=" + iq);
        }

        final UpdatePermissionsIQ updatePermissionsIQ = (UpdatePermissionsIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        mGroupManager.processUpdateGroupMember(conversationImpl, updatePermissionsIQ.groupTwincodeId,
                updatePermissionsIQ.memberTwincodeId, updatePermissionsIQ.permissions);

        int deviceState = getDeviceState(connection);
        OnPushIQ onUpdateTimestampIQ = new OnPushIQ(OnUpdatePermissionsIQ.IQ_ON_UPDATE_PERMISSIONS_SERIALIZER, updatePermissionsIQ.getRequestId(),
                deviceState, 0);

        connection.sendPacket(StatType.IQ_RESULT_UPDATE_GROUP_MEMBER, onUpdateTimestampIQ);
    }

    private void processLegacyUpdateGroupMemberIQ(@NonNull ConversationConnection connection, @NonNull UpdateGroupMemberIQ updateGroupMemberIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyUpdateGroupMemberIQ: connection=" + connection + " updateGroupMemberIQ=" + updateGroupMemberIQ);
        }

        int majorVersion = updateGroupMemberIQ.getMajorVersion();
        int minorVersion = updateGroupMemberIQ.getMinorVersion();
        final ConversationImpl conversationImpl = connection.getConversation();

        mGroupManager.processUpdateGroupMember(conversationImpl, updateGroupMemberIQ.groupTwincodeId,
                updateGroupMemberIQ.memberTwincodeId, updateGroupMemberIQ.permissions);

        final boolean needLeadingPadding = connection.needLeadingPadding();
        OnResultGroupIQ onUpdateGroupMemberIQ = new OnResultGroupIQ(updateGroupMemberIQ.getId(), connection.getFrom(), connection.getTo(),
                ConversationServiceIQ.ON_UPDATE_GROUP_MEMBER_ACTION, updateGroupMemberIQ.getRequestId(), majorVersion, minorVersion);

        byte[] content = onUpdateGroupMemberIQ.serialize(mSerializerFactory, majorVersion, minorVersion, needLeadingPadding);
        connection.sendMessage(StatType.IQ_RESULT_UPDATE_GROUP_MEMBER, content);
    }

    private void processUpdateTimestampIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processUpdateAnnotationIQ: connection=" + connection + " iq=" + iq);
        }

        // Return 0 for success and -1 for error (no need for the real timestamp).
        long timestamp = -1L;
        final ConversationImpl conversationImpl = connection.getConversation();
        final UpdateTimestampIQ updateTimestampIQ = (UpdateTimestampIQ) iq;
        final DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(updateTimestampIQ.descriptorId);
        if (descriptorImpl != null) {
            final Conversation conversation = conversationImpl.getMainConversation();
            switch (updateTimestampIQ.timestampType) {
                case READ:
                    descriptorImpl.setReadTimestamp(connection.getAdjustedTime(updateTimestampIQ.timestamp));
                    updateDescriptor(descriptorImpl, conversationImpl);
                    timestamp = 0;
                    break;

                case DELETE:
                    descriptorImpl.setDeletedTimestamp(connection.getAdjustedTime(updateTimestampIQ.timestamp));
                    // Save the descriptor timestamps in case we are stopped while removing the file (slow operation).
                    mServiceProvider.updateDescriptorImplTimestamps(descriptorImpl);
                    deleteConversationDescriptor(DEFAULT_REQUEST_ID, conversation, descriptorImpl);
                    timestamp = 0;
                    break;

                case PEER_DELETE:
                    if (descriptorImpl.getPeerDeletedTimestamp() == 0) {
                        descriptorImpl.setPeerDeletedTimestamp(connection.getAdjustedTime(updateTimestampIQ.timestamp));
                        updateDescriptor(descriptorImpl, conversationImpl);
                    }
                    timestamp = 0;
                    break;
            }
        }

        int deviceState = getDeviceState(connection);
        OnPushIQ onUpdateTimestampIQ = new OnPushIQ(OnUpdateTimestampIQ.IQ_ON_UPDATE_TIMESTAMP_SERIALIZER, updateTimestampIQ.getRequestId(),
                deviceState, timestamp);

        connection.sendPacket(StatType.IQ_RESULT_UPDATE_OBJECT, onUpdateTimestampIQ);
    }

    private void processLegacyUpdateDescriptorTimestampIQ(@NonNull ConversationConnection connection,
                                                          @NonNull UpdateDescriptorTimestampIQ updateDescriptorTimestampIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processLegacyUpdateDescriptorTimestampIQ: connection=" + connection + " updateDescriptorTimestampIQ=" + updateDescriptorTimestampIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(updateDescriptorTimestampIQ.descriptorId);
        if (descriptorImpl != null) {
            final Conversation conversation = conversationImpl.getMainConversation();
            switch (updateDescriptorTimestampIQ.timestampType) {
                case READ:
                    descriptorImpl.setReadTimestamp(connection.getAdjustedTime(updateDescriptorTimestampIQ.timestamp));
                    updateDescriptor(descriptorImpl, conversationImpl);
                    break;

                case DELETE:
                    descriptorImpl.setDeletedTimestamp(connection.getAdjustedTime(updateDescriptorTimestampIQ.timestamp));
                    // Save the descriptor timestamps in case we are stopped while removing the file (slow operation).
                    mServiceProvider.updateDescriptorImplTimestamps(descriptorImpl);
                    deleteConversationDescriptor(DEFAULT_REQUEST_ID, conversation, descriptorImpl);
                    break;

                case PEER_DELETE:
                    if (descriptorImpl.getPeerDeletedTimestamp() == 0) {
                        descriptorImpl.setPeerDeletedTimestamp(connection.getAdjustedTime(updateDescriptorTimestampIQ.timestamp));
                        updateDescriptor(descriptorImpl, conversationImpl);
                    }
                    break;
            }
        }

        int majorVersion = updateDescriptorTimestampIQ.getMajorVersion();
        int minorVersion = updateDescriptorTimestampIQ.getMinorVersion();

        OnUpdateDescriptorTimestampIQ onUpdateDescriptorTimestampIQ = new OnUpdateDescriptorTimestampIQ(updateDescriptorTimestampIQ.getId(),
                connection.getFrom(), connection.getTo(), updateDescriptorTimestampIQ.getRequestId(), majorVersion, minorVersion);

        boolean serialized = false;
        Exception exception = null;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(SERIALIZER_BUFFER_DEFAULT_SIZE);
        try {
            BinaryEncoder binaryEncoder = new BinaryEncoder(outputStream);
            binaryEncoder.writeFixed(PeerConnectionService.LEADING_PADDING, 0, PeerConnectionService.LEADING_PADDING.length);

            if (majorVersion == MAJOR_VERSION_2) {
                OnUpdateDescriptorTimestampIQ.SERIALIZER.serialize(mSerializerFactory, binaryEncoder, onUpdateDescriptorTimestampIQ);
                serialized = true;
            }
        } catch (Exception lException) {
            exception = lException;
        }

        if (serialized) {
            connection.sendMessage(StatType.IQ_RESULT_UPDATE_OBJECT, outputStream.toByteArray());

        } else {
            mTwinlifeImpl.exception(ConversationAssertPoint.PROCESS_UPDATE_DESCRIPTOR_IQ, exception,
                    AssertPoint.createPeerConnectionId(connection.getPeerConnectionId())
                            .putVersion(majorVersion, minorVersion));
        }
    }

    private void processSynchronizeIQ(@NonNull UUID peerConnectionId, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processSynchronizeIQ: peerConnectionId=" + peerConnectionId + " iq=" + iq);
        }

        final SynchronizeIQ synchronizeIq = (SynchronizeIQ) iq;
        final ConversationConnection connection = preparePeerConversation(peerConnectionId, synchronizeIq.peerTwincodeOutboundId, synchronizeIq.resourceId);
        if (connection == null) {

            return;
        }

        long now = System.currentTimeMillis();
        int deviceState = getDeviceState(connection);
        OnSynchronizeIQ responseIQ = new OnSynchronizeIQ(OnSynchronizeIQ.IQ_ON_SYNCHRONIZE_SERIALIZER, iq.getRequestId(), deviceState, now, synchronizeIq.timestamp);
        connection.sendPacket(StatType.IQ_RESULT_SYNCHRONIZE, responseIQ);
    }

    private void processOnSynchronizeIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnSynchronizeIQ: connection=" + connection + " iq=" + iq);
        }

        final OnSynchronizeIQ onSynchronizeIQ = (OnSynchronizeIQ) iq;
        connection.setPeerDeviceState(onSynchronizeIQ.deviceState);
        connection.adjustPeerTime(onSynchronizeIQ.timestamp, onSynchronizeIQ.senderTimestamp);
        final Operation firstOperation = mScheduler.startOperation(connection, State.OPEN);
        if (firstOperation != null) {
            sendOperationInternal(connection, firstOperation);
        }
    }

    private void processSignatureInfoIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {

        final SignatureInfoIQ signatureInfoIQ = (SignatureInfoIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();

        // Accept the signature info only for the peer conversation twincode.
        final TwincodeOutbound twincodeOutbound = conversationImpl.getTwincodeOutbound();
        final TwincodeOutbound peerTwincodeOutbound = conversationImpl.getPeerTwincodeOutbound();
        final SdpEncryptionStatus sdpEncryptionStatus = mPeerConnectionService.getSdpEncryptionStatus(connection.getPeerConnectionId());
        if (twincodeOutbound == null || peerTwincodeOutbound == null || !peerTwincodeOutbound.getId().equals(signatureInfoIQ.twincodeOutboundId)) {
            mTwinlifeImpl.assertion(ConversationAssertPoint.PROCESS_SIGNATURE_ERROR,
                    AssertPoint.createPeerConnectionId(connection.getPeerConnectionId())
                            .put(conversationImpl.getSubject())
                            .put(peerTwincodeOutbound)
                            .putTwincodeId(signatureInfoIQ.twincodeOutboundId)
                            .put(sdpEncryptionStatus));
            return;
        }

        // If the SDPs are encrypted, this is a renew and we must only save the new secret.
        // We must not call the onSignatureInfo observer because this will force a
        // trust of the twincode: if it's not trusted, the relation was established with public keys
        // and the user must use the QR-code or video certification process.
        if (sdpEncryptionStatus != SdpEncryptionStatus.NONE) {
            mCryptoService.saveSecretKey(twincodeOutbound, peerTwincodeOutbound, signatureInfoIQ.secret, signatureInfoIQ.keyIndex);

            int deviceState = getDeviceState(connection);
            OnPushIQ resultIq = new OnPushIQ(OnSignatureInfoIQ.IQ_ON_SIGNATURE_SERIALIZER, iq.getRequestId(), deviceState, System.currentTimeMillis());
            connection.sendPacket(StatType.IQ_RESULT_SIGNATURE_INFO, resultIq);
            return;
        }

        mTwincodeOutboundService.getSignedTwincodeWithSecret(signatureInfoIQ.twincodeOutboundId, signatureInfoIQ.publicKey, signatureInfoIQ.keyIndex, signatureInfoIQ.secret, TrustMethod.AUTO, (result, signedTwincode) -> {
            if (DEBUG) {
                Log.d(LOG_TAG, "Got signed twincode "+signedTwincode+", result= "+result);
            }

            if (result != ErrorCode.SUCCESS || signedTwincode == null) {
                return;
            }

            // Only one of the two peer marks the twincode as trusted: this allows both peers
            // to validate their relation either with QR-code or through the video call.
            if (signatureInfoIQ.twincodeOutboundId.compareTo(twincodeOutbound.getId()) < 0) {
                for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                    mTwinlifeExecutor.execute(() -> serviceObserver.onSignatureInfo(conversationImpl, signedTwincode));
                }
            }

            int deviceState = getDeviceState(connection);
            OnPushIQ resultIq = new OnPushIQ(OnSignatureInfoIQ.IQ_ON_SIGNATURE_SERIALIZER, iq.getRequestId(), deviceState, System.currentTimeMillis());
            connection.sendPacket(StatType.IQ_RESULT_SIGNATURE_INFO, resultIq);
        });
    }

    private void processOnSignatureInfoIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnSignatureInfoIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onSignatureInfoIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onSignatureInfoIQ.deviceState);
        connection.setSynchronizeKeys(false);

        final TwincodeOutbound peerTwincodeOutbound = conversationImpl.getPeerTwincodeOutbound();
        final TwincodeOutbound twincodeOutbound = conversationImpl.getTwincodeOutbound();
        if (twincodeOutbound != null && peerTwincodeOutbound != null) {
            mCryptoService.validateSecrets(twincodeOutbound, peerTwincodeOutbound);
        }

        // Send a last OnPushIQ to propagate our new device state to the peer and acknowledge our validateSecrets().
        final long now = System.currentTimeMillis();
        final int deviceState = getDeviceState(connection);
        final OnPushIQ ackIQ = new OnPushIQ(OnSignatureInfoIQ.IQ_ACK_SIGNATURE_SERIALIZER, iq.getRequestId(), deviceState, now);
        connection.sendPacket(StatType.IQ_RESULT_SYNCHRONIZE, ackIQ);
    }

    private void processAckSignatureInfoIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processAckSignatureInfoIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onAckSignatureInfoIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onAckSignatureInfoIQ.deviceState);

        // When one of the FLAG_ENCRYPT is missing, check the { twincodeOutbound, peerTwincodeOutbound } key association
        // and set the FLAG_ENCRYPT on the twincodes if everything is met:
        // - we have sent our public keys and secrets and the peer acknowledged,
        // - we have received the peer secrets.
        final TwincodeOutbound peerTwincodeOutbound = conversationImpl.getPeerTwincodeOutbound();
        final TwincodeOutbound twincodeOutbound = conversationImpl.getTwincodeOutbound();
        if (twincodeOutbound != null && peerTwincodeOutbound != null
                && (!twincodeOutbound.isEncrypted() || !peerTwincodeOutbound.isEncrypted())) {
            mTwincodeOutboundService.associateTwincodes(twincodeOutbound, null, peerTwincodeOutbound);
        }
    }

    private void processOnResetConversationIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnResetConversationIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onResetConversationIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onResetConversationIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onResetConversationIQ.getRequestId());
        if (operation instanceof ResetConversationOperation) {
            ResetConversationOperation resetConversationOperation = (ResetConversationOperation) operation;
            ClearDescriptorImpl clearDescriptorImpl = resetConversationOperation.getClearDescriptorImpl();

            // Update the received timestamp only the first time.
            if (clearDescriptorImpl != null && clearDescriptorImpl.getReceivedTimestamp() <= 0) {
                clearDescriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onResetConversationIQ.receivedTimestamp));
                updateDescriptor(clearDescriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyResetConversationIQ(@NonNull ConversationConnection connection,
                                                    @NonNull ConversationServiceIQ.OnResetConversationIQ onResetConversationIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyResetConversationIQ: connection=" + connection + " onResetConversationIQ=" + onResetConversationIQ);
        }

        if (INFO) {
            Log.i(LOG_TAG, "processOnLegacyResetConversationIQ: connection=" + connection);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onResetConversationIQ.getRequestId());
        if (operation instanceof ResetConversationOperation) {
            ResetConversationOperation resetConversationOperation = (ResetConversationOperation) operation;
            ClearDescriptorImpl clearDescriptorImpl = resetConversationOperation.getClearDescriptorImpl();

            // Update the received timestamp only the first time.  Since the peer is using an old
            // version, we don't know the receive timestamp and use our local time.
            if (clearDescriptorImpl != null && clearDescriptorImpl.getReceivedTimestamp() <= 0) {
                clearDescriptorImpl.setReceivedTimestamp(System.currentTimeMillis());
                updateDescriptor(clearDescriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnPushCommandIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnPushCommandIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onPushCommandIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onPushCommandIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushCommandIQ.getRequestId());
        if (operation instanceof PushCommandOperation) {
            PushCommandOperation pushCommandOperation = (PushCommandOperation) operation;
            TransientObjectDescriptorImpl commandDescriptorImpl = pushCommandOperation.getCommandDescriptorImpl();
            commandDescriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onPushCommandIQ.receivedTimestamp));

            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID, conversationImpl,
                        commandDescriptorImpl, UpdateType.TIMESTAMPS));
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyPushCommandIQ(@NonNull ConversationConnection connection, @NonNull ConversationServiceIQ.OnPushCommandIQ onPushCommandIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyPushCommandIQ: connection=" + connection + " onPushCommandIQ=" + onPushCommandIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushCommandIQ.getRequestId());
        if (operation instanceof PushCommandOperation) {
            PushCommandOperation pushCommandOperation = (PushCommandOperation) operation;
            TransientObjectDescriptorImpl commandDescriptorImpl = pushCommandOperation.getCommandDescriptorImpl();
            commandDescriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onPushCommandIQ.receivedTimestamp));

            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID, conversationImpl,
                        commandDescriptorImpl, UpdateType.TIMESTAMPS));
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnPushObjectIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnPushObjectIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onPushObjectIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onPushObjectIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushObjectIQ.getRequestId());
        if (operation instanceof PushObjectOperation) {
            PushObjectOperation pushObjectOperation = (PushObjectOperation) operation;
            ObjectDescriptorImpl objectDescriptorImpl = pushObjectOperation.getObjectDescriptorImpl();

            // Update the received timestamp only the first time.
            if (objectDescriptorImpl != null && objectDescriptorImpl.getReceivedTimestamp() <= 0) {
                objectDescriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onPushObjectIQ.receivedTimestamp));
                updateDescriptor(objectDescriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyPushObjectIQ(@NonNull ConversationConnection connection, @NonNull ConversationServiceIQ.OnPushObjectIQ onPushObjectIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyPushObjectIQ: connection=" + connection + " onPushObjectIQ=" + onPushObjectIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushObjectIQ.getRequestId());
        if (operation instanceof PushObjectOperation) {
            PushObjectOperation pushObjectOperation = (PushObjectOperation) operation;
            ObjectDescriptorImpl objectDescriptorImpl = pushObjectOperation.getObjectDescriptorImpl();

            // Update the received timestamp only the first time.
            if (objectDescriptorImpl != null && objectDescriptorImpl.getReceivedTimestamp() <= 0) {
                objectDescriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onPushObjectIQ.receivedTimestamp));
                updateDescriptor(objectDescriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnUpdateObjectIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnUpdateObjectIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onPushObjectIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onPushObjectIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushObjectIQ.getRequestId());
        if (operation instanceof UpdateDescriptorOperation) {
            final UpdateDescriptorOperation updateDescriptorOperation = (UpdateDescriptorOperation) operation;
            final DescriptorImpl descriptorImpl = updateDescriptorOperation.getDescriptorImpl();

            if (descriptorImpl != null && descriptorImpl.getReceivedTimestamp() < descriptorImpl.getUpdatedTimestamp()
                    && descriptorImpl.getUpdatedTimestamp() > descriptorImpl.getCreatedTimestamp()) {
                descriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onPushObjectIQ.receivedTimestamp));
                updateDescriptor(descriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnPushFileIQ(@NonNull ConversationConnection connection,
                                     @NonNull BinaryPacketIQ iq) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnPushFileIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onPushFileIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onPushFileIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushFileIQ.getRequestId());
        if (operation instanceof FileOperation) {
            FileOperation pushFileOperation = (FileOperation) operation;
            FileDescriptorImpl fileDescriptorImpl = pushFileOperation.getFileDescriptorImpl();
            if (fileDescriptorImpl != null) {
                if (onPushFileIQ.receivedTimestamp > 0 && pushFileOperation.getChunkStart() == FileOperation.NOT_INITIALIZED) {
                    // We keep the same request id on the operation because we are ready to send the data chunks in batch mode.
                    // Send the first data chunks immediately.
                    pushFileOperation.setChunkStart(0L);
                    mServiceProvider.updateFileOperation(pushFileOperation);
                    // sendPushFileChunkIQ(conversationImpl, pushFileOperation, fileDescriptorImpl);
                    pushFileOperation.execute(connection);
                    return;
                }

                // The receiver does not accept the file (permission denied or file system is full).
                // Update the received timestamp only the first time.
                if (fileDescriptorImpl.getReceivedTimestamp() <= 0) {
                    fileDescriptorImpl.setReceivedTimestamp(-1L);
                }
                updateDescriptor(fileDescriptorImpl, conversationImpl);

            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyPushFileIQ(@NonNull ConversationConnection connection,
                                           @NonNull ConversationServiceIQ.OnPushFileIQ onPushFileIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyPushFileIQ: connection=" + connection + " onPushFileIQ=" + onPushFileIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushFileIQ.getRequestId());
        if (operation instanceof FileOperation) {
            FileOperation pushFileOperation = (FileOperation) operation;
            FileDescriptorImpl fileDescriptorImpl = pushFileOperation.getFileDescriptorImpl();
            if (fileDescriptorImpl != null) {
                if (onPushFileIQ.receivedTimestamp > 0 && pushFileOperation.getChunkStart() == FileOperation.NOT_INITIALIZED) {
                    pushFileOperation.updateRequestId(Operation.NO_REQUEST_ID);
                    pushFileOperation.setChunkStart(0L);
                    mServiceProvider.updateFileOperation(pushFileOperation);
                    // sendPushFileChunkIQ(conversationImpl, pushFileOperation, fileDescriptorImpl);
                    pushFileOperation.execute(connection);
                    return;
                }

                // The receiver does not accept the file (permission denied or file system is full).
                // Update the received timestamp only the first time.
                if (fileDescriptorImpl.getReceivedTimestamp() <= 0) {
                    fileDescriptorImpl.setReceivedTimestamp(-1L);
                }
                updateDescriptor(fileDescriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnPushFileChunkIQ(@NonNull ConversationConnection connection,
                                          @NonNull BinaryPacketIQ iq) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnPushFileChunkIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushFileChunkIQ onPushFileChunkIQ = (OnPushFileChunkIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onPushFileChunkIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushFileChunkIQ.getRequestId());
        if (operation != null) {
            boolean done = false;
            if (operation instanceof FileOperation) {
                FileOperation pushFileOperation = (FileOperation) operation;
                FileDescriptorImpl fileDescriptorImpl = pushFileOperation.getFileDescriptorImpl();
                if (fileDescriptorImpl != null) {
                    if (onPushFileChunkIQ.nextChunkStart < fileDescriptorImpl.getLength()) {
                        // We keep the same request id on the operation and continue sending more chunks.
                        pushFileOperation.setChunkStart(onPushFileChunkIQ.nextChunkStart);
                        connection.updateEstimatedRTT(onPushFileChunkIQ.senderTimestamp);
                        // sendPushFileChunkIQ(conversationImpl, pushFileOperation, fileDescriptorImpl);
                        pushFileOperation.execute(connection);
                        return;
                    } else {
                        // Update the received timestamp only the first time.
                        if (fileDescriptorImpl.getReceivedTimestamp() <= 0) {
                            long timestamp = connection.getAdjustedTime(onPushFileChunkIQ.receivedTimestamp);
                            fileDescriptorImpl.setUpdatedTimestamp(timestamp);
                            fileDescriptorImpl.setReceivedTimestamp(timestamp);
                        }
                        updateDescriptor(fileDescriptorImpl, conversationImpl);

                        done = true;
                    }
                }

                // TBD - removed file
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyPushFileChunkIQ(@NonNull ConversationConnection connection,
                                                @NonNull ConversationServiceIQ.OnPushFileChunkIQ onPushFileChunkIQ) throws SerializerException {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyPushFileChunkIQ: connection=" + connection + " onPushFileChunkIQ=" + onPushFileChunkIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushFileChunkIQ.getRequestId());
        if (operation != null) {
            boolean done = false;
            if (operation instanceof FileOperation) {
                FileOperation pushFileOperation = (FileOperation) operation;
                FileDescriptorImpl fileDescriptorImpl = pushFileOperation.getFileDescriptorImpl();
                if (fileDescriptorImpl != null) {
                    if (onPushFileChunkIQ.nextChunkStart < fileDescriptorImpl.getLength()) {
                        pushFileOperation.updateRequestId(Operation.NO_REQUEST_ID);
                        pushFileOperation.setChunkStart(onPushFileChunkIQ.nextChunkStart);
                        mServiceProvider.updateFileOperation(pushFileOperation);
                        // sendPushFileChunkIQ(conversationImpl, pushFileOperation, fileDescriptorImpl);
                        pushFileOperation.execute(connection);
                        return;
                    } else {
                        // Update the received timestamp only the first time.
                        if (fileDescriptorImpl.getReceivedTimestamp() <= 0) {
                            long timestamp = connection.getAdjustedTime(onPushFileChunkIQ.receivedTimestamp);
                            fileDescriptorImpl.setUpdatedTimestamp(timestamp);
                            fileDescriptorImpl.setReceivedTimestamp(timestamp);
                        }
                        updateDescriptor(fileDescriptorImpl, conversationImpl);
                        done = true;
                    }
                }

                // TBD - removed file
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnPushGeolocationIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnPushGeolocationIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onPushGeolocationIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onPushGeolocationIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushGeolocationIQ.getRequestId());
        if (operation instanceof PushGeolocationOperation) {
            PushGeolocationOperation pushGeolocationOperation = (PushGeolocationOperation) operation;
            GeolocationDescriptorImpl geolocationDescriptorImpl = pushGeolocationOperation.getGeolocationDescriptorImpl();

            // Update the received timestamp only the first time.
            if (geolocationDescriptorImpl != null && geolocationDescriptorImpl.getReceivedTimestamp() <= 0) {
                geolocationDescriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onPushGeolocationIQ.receivedTimestamp));
                updateDescriptor(geolocationDescriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyPushGeolocationIQ(@NonNull ConversationConnection connection, @NonNull ConversationServiceIQ.OnPushGeolocationIQ onPushGeolocationIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyPushGeolocationIQ: connection=" + connection + " onPushGeolocationIQ=" + onPushGeolocationIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushGeolocationIQ.getRequestId());
        if (operation instanceof PushGeolocationOperation) {
            PushGeolocationOperation pushGeolocationOperation = (PushGeolocationOperation) operation;
            GeolocationDescriptorImpl geolocationDescriptorImpl = pushGeolocationOperation.getGeolocationDescriptorImpl();

            // Update the received timestamp only the first time.
            if (geolocationDescriptorImpl != null && geolocationDescriptorImpl.getReceivedTimestamp() <= 0) {
                geolocationDescriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onPushGeolocationIQ.receivedTimestamp));
                updateDescriptor(geolocationDescriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyPushTwincodeIQ(@NonNull ConversationConnection connection, @NonNull ConversationServiceIQ.OnPushTwincodeIQ onPushTwincodeIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyPushTwincodeIQ: connection=" + connection + " onPushTwincodeIQ=" + onPushTwincodeIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushTwincodeIQ.getRequestId());
        if (operation instanceof PushTwincodeOperation) {
            PushTwincodeOperation pushTwincodeOperation = (PushTwincodeOperation) operation;
            TwincodeDescriptorImpl twincodeDescriptorImpl = pushTwincodeOperation.getTwincodeDescriptorImpl();

            // Update the received timestamp only the first time.
            if (twincodeDescriptorImpl != null && twincodeDescriptorImpl.getReceivedTimestamp() <= 0) {
                twincodeDescriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onPushTwincodeIQ.receivedTimestamp));
                updateDescriptor(twincodeDescriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnPushTwincodeIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnPushTwincodeIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onPushTwincodeIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onPushTwincodeIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onPushTwincodeIQ.getRequestId());
        if (operation instanceof PushTwincodeOperation) {
            PushTwincodeOperation pushTwincodeOperation = (PushTwincodeOperation) operation;
            TwincodeDescriptorImpl twincodeDescriptorImpl = pushTwincodeOperation.getTwincodeDescriptorImpl();

            // Update the received timestamp only the first time.
            if (twincodeDescriptorImpl != null && twincodeDescriptorImpl.getReceivedTimestamp() <= 0) {
                twincodeDescriptorImpl.setReceivedTimestamp(connection.getAdjustedTime(onPushTwincodeIQ.receivedTimestamp));
                updateDescriptor(twincodeDescriptorImpl, conversationImpl);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnUpdateAnnotationIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnUpdateAnnotationIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onUpdateAnnotationIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onUpdateAnnotationIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onUpdateAnnotationIQ.getRequestId());

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnUpdateTimestampIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnUpdateTimestampIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onUpdateTimestampIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onUpdateTimestampIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onUpdateTimestampIQ.getRequestId());
        if (operation instanceof UpdateDescriptorTimestampOperation) {
            final UpdateDescriptorTimestampOperation updateDescriptorTimestampOperation = (UpdateDescriptorTimestampOperation) operation;
            if (updateDescriptorTimestampOperation.getTimestampType() == UpdateDescriptorTimestampType.DELETE) {
                DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(updateDescriptorTimestampOperation.getUpdateDescriptorId());
                if (descriptorImpl != null) {
                    if (descriptorImpl.getPeerDeletedTimestamp() == 0) {
                        descriptorImpl.setPeerDeletedTimestamp(System.currentTimeMillis());
                        // Save the descriptor timestamps in case we are stopped while removing the file (slow operation).
                        mServiceProvider.updateDescriptorImplTimestamps(descriptorImpl);
                    }
                    deleteConversationDescriptor(DEFAULT_REQUEST_ID, conversationImpl, descriptorImpl);

                    for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                        mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID,
                                conversationImpl, descriptorImpl, UpdateType.TIMESTAMPS));
                    }
                }
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyUpdateDescriptorTimestampIQ(@NonNull ConversationConnection connection, @NonNull OnUpdateDescriptorTimestampIQ onUpdateDescriptorTimestampIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyUpdateDescriptorTimestampIQ: connection=" + connection + " onUpdateDescriptorTimestampIQ=" + onUpdateDescriptorTimestampIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onUpdateDescriptorTimestampIQ.getRequestId());
        if (operation instanceof UpdateDescriptorTimestampOperation) {
            final UpdateDescriptorTimestampOperation updateDescriptorTimestampOperation = (UpdateDescriptorTimestampOperation) operation;
            if (updateDescriptorTimestampOperation.getTimestampType() == UpdateDescriptorTimestampType.DELETE) {
                DescriptorImpl descriptorImpl = mServiceProvider.loadDescriptorImpl(updateDescriptorTimestampOperation.getUpdateDescriptorId());
                if (descriptorImpl != null) {
                    if (descriptorImpl.getPeerDeletedTimestamp() == 0) {
                        descriptorImpl.setPeerDeletedTimestamp(System.currentTimeMillis());
                        // Save the descriptor timestamps in case we are stopped while removing the file (slow operation).
                        mServiceProvider.updateDescriptorImplTimestamps(descriptorImpl);
                    }
                    deleteConversationDescriptor(DEFAULT_REQUEST_ID, conversationImpl, descriptorImpl);

                    for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                        mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID,
                                conversationImpl, descriptorImpl, UpdateType.TIMESTAMPS));
                    }
                }
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnInviteGroupIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnInviteGroupIQ: connection=" + connection + " iq=" + iq);
        }

        final OnPushIQ onInviteGroupIQ = (OnPushIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        connection.setPeerDeviceState(onInviteGroupIQ.deviceState);

        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onInviteGroupIQ.getRequestId());
        if (operation instanceof GroupInviteOperation) {
            final GroupInviteOperation groupOperation = (GroupInviteOperation) operation;

            // Update the invitation descriptor to mark is as received.
            final InvitationDescriptorImpl invitation = groupOperation.getInvitationDescriptorImpl();
            if (invitation != null) {
                invitation.setReceivedTimestamp(onInviteGroupIQ.receivedTimestamp);
                mServiceProvider.updateDescriptorImplTimestamps(invitation);

                // Notify that the invitation descriptor was changed.
                for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                    mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID,
                            conversationImpl, invitation, UpdateType.TIMESTAMPS));
                }
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyInviteGroupIQ(@NonNull ConversationConnection connection, @NonNull OnResultGroupIQ onInviteGroupIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyInviteGroupIQ: connection=" + connection + " onInviteGroupIQ=" + onInviteGroupIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onInviteGroupIQ.getRequestId());
        if (operation instanceof GroupInviteOperation) {
            final GroupInviteOperation groupOperation = (GroupInviteOperation) operation;

            // Update the invitation descriptor to mark is as received.
            final InvitationDescriptorImpl invitation = groupOperation.getInvitationDescriptorImpl();
            if (invitation != null) {
                invitation.setReceivedTimestamp(System.currentTimeMillis());
                mServiceProvider.updateDescriptorImplTimestamps(invitation);

                // Notify that the invitation descriptor was changed.
                for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                    mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID,
                            conversationImpl, invitation, UpdateType.TIMESTAMPS));
                }
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyRevokeInviteGroupIQ(@NonNull ConversationConnection connection, @NonNull OnResultGroupIQ onRevokeInviteGroupIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyRevokeInviteGroupIQ: connection=" + connection + " onRevokeInviteGroupIQ=" + onRevokeInviteGroupIQ);
        }

        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onRevokeInviteGroupIQ.getRequestId());
        if (operation instanceof GroupInviteOperation) {
            final GroupInviteOperation groupOperation = (GroupInviteOperation) operation;

            // Update the invitation descriptor: mark is as deleted for the observers and remove it from the database.
            final InvitationDescriptorImpl invitation = groupOperation.getInvitationDescriptorImpl();
            if (invitation != null) {
                invitation.setPeerDeletedTimestamp(System.currentTimeMillis());
                deleteConversationDescriptor(DEFAULT_REQUEST_ID, conversationImpl, invitation);
            }

        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnJoinGroupIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnJoinGroupIQ: connection=" + connection + " iq=" + iq);
        }

        // add other members in group
        final OnJoinGroupIQ onJoinGroupIQ = (OnJoinGroupIQ) iq;
        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onJoinGroupIQ.getRequestId());
        if (operation instanceof GroupJoinOperation) {
            final GroupJoinOperation groupOperation = (GroupJoinOperation) operation;
            final InvitationDescriptorImpl invitationDescriptor = groupOperation.getInvitationDescriptorImpl();
            if (onJoinGroupIQ.inviterTwincodeId != null && onJoinGroupIQ.inviterPublicKey != null) {
                mTwincodeOutboundService.getSignedTwincodeWithSecret(onJoinGroupIQ.inviterTwincodeId, onJoinGroupIQ.inviterPublicKey, 1, onJoinGroupIQ.inviterSecretKey, TrustMethod.PEER, (ErrorCode errorCode, TwincodeOutbound twincodeOutbound) -> {

                    // If we are offline or timed out don't finish the operation: we must retry it.
                    // (we must force a close of the P2P connection in case it is still opened).
                    if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                        close(connection, false, connection.getPeerConnectionId(), TerminateReason.CONNECTIVITY_ERROR);
                        return;
                    }
                    if (twincodeOutbound != null) {
                        mGroupManager.processOnJoinGroup(conversationImpl, groupOperation.getGroupId(), invitationDescriptor,
                                twincodeOutbound, onJoinGroupIQ.inviterPermissions, onJoinGroupIQ.members,
                                onJoinGroupIQ.permissions, onJoinGroupIQ.inviterSignature);
                    } else if (invitationDescriptor != null) {
                        mGroupManager.processOnJoinGroupWithdrawn(invitationDescriptor);
                    }
                    mScheduler.finishOperation(operation, connection);
                });
            } else {
                if (invitationDescriptor != null) {
                    mGroupManager.processOnJoinGroupWithdrawn(invitationDescriptor);
                }
                mScheduler.finishOperation(operation, connection);
            }
        } else {
            mScheduler.finishOperation(operation, connection);
        }
    }

    private void processOnLegacyJoinGroupIQ(@NonNull ConversationConnection connection, @NonNull OnResultJoinIQ onJoinGroupIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyJoinGroupIQ: connection=" + connection + " onJoinGroupIQ=" + onJoinGroupIQ);
        }

        // add other members in group
        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onJoinGroupIQ.getRequestId());
        if (operation instanceof GroupJoinOperation) {
            final GroupJoinOperation groupOperation = (GroupJoinOperation) operation;
            final InvitationDescriptorImpl invitationDescriptor = groupOperation.getInvitationDescriptorImpl();
            if (onJoinGroupIQ.status == InvitationDescriptor.Status.JOINED) {
                mGroupManager.processOnJoinGroup(conversationImpl, groupOperation.getGroupId(), invitationDescriptor,
                        null, 0, onJoinGroupIQ.members, onJoinGroupIQ.permissions, null);
            } else if (invitationDescriptor != null) {
                mGroupManager.processOnJoinGroupWithdrawn(invitationDescriptor);
            }
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyLeaveGroupIQ(@NonNull ConversationConnection connection, @NonNull OnResultGroupIQ onLeaveGroupIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyLeaveGroupIQ: connection=" + connection + " onLeaveGroupIQ=" + onLeaveGroupIQ);
        }

        // The leave operation has finished and the peer has removed the member from its group.
        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onLeaveGroupIQ.getRequestId());
        if (operation instanceof GroupLeaveOperation) {
            final GroupLeaveOperation groupOperation = (GroupLeaveOperation) operation;
            mGroupManager.processOnLeaveGroup(groupOperation.getGroupId(), groupOperation.getMemberId(), conversationImpl.getPeerTwincodeOutboundId());
        }

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnUpdatePermissionsIQ(@NonNull ConversationConnection connection, @NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnUpdatePermissionsIQ: connection=" + connection + " iq=" + iq);
        }

        // The update group member operation has finished.
        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), iq.getRequestId());

        mScheduler.finishOperation(operation, connection);
    }

    private void processOnLegacyUpdateGroupMemberIQ(@NonNull ConversationConnection connection, @NonNull OnResultGroupIQ onUpdateGroupMemberIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processOnLegacyUpdateGroupMemberIQ: connection=" + connection + " onUpdateGroupMemberIQ=" + onUpdateGroupMemberIQ);
        }

        // The update group member operation has finished.
        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), onUpdateGroupMemberIQ.getRequestId());

        mScheduler.finishOperation(operation, connection);
    }

    private void processServiceErrorIQ(@NonNull ConversationConnection connection, @NonNull ServiceErrorIQ serviceErrorIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processServiceErrorIQ: connection=" + connection + " serviceErrorIQ=" + serviceErrorIQ);
        }

        if (INFO) {
            Log.i(LOG_TAG, "processServiceErrorIQ: connection=" + connection);
        }

        final long requestId = serviceErrorIQ.getRequestId();
        final ConversationImpl conversationImpl = connection.getConversation();
        final Operation operation = mScheduler.getOperation(conversationImpl.getDatabaseId(), requestId);
        if (operation != null) {

            // Handle the error according to the operation:
            // - for a PUSH_FILE, mark the descriptor as being not sent (can happen if there not not enough space, storage constraints, ...),
            // - for a PUSH_OBJECT, mark the descriptor as being not sent (should not happen except when a bug is there),
            // - for SYNCHRONIZE_CONVERSATION, RESET_CONVERSATION was can safely ignore (or, close the P2P connection?),
            // - for a PUSH_TRANSIENT_OBJECT we can ignore,
            // - for a UPDATE_DESCRIPTOR_TIMESTAMP we can also ignore as there is nothing we can do on our side.
            if (operation.getType() == Operation.Type.PUSH_FILE && operation instanceof PushFileOperation) {
                PushFileOperation pushFileOperation = (PushFileOperation) operation;
                FileDescriptorImpl fileDescriptorImpl = pushFileOperation.getFileDescriptorImpl();
                if (fileDescriptorImpl != null) {
                    // Set the timestamp to a negative value to indicate nothing was sent.
                    fileDescriptorImpl.setReceivedTimestamp(-1);
                    fileDescriptorImpl.setReadTimestamp(-1);
                    mServiceProvider.updateDescriptorImplTimestamps(fileDescriptorImpl);

                    for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                        mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID, conversationImpl, fileDescriptorImpl,
                                UpdateType.TIMESTAMPS));
                    }
                    onError(DEFAULT_REQUEST_ID, ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER, conversationImpl.getId().toString());
                }
            } else if (operation.getType() == Operation.Type.PUSH_OBJECT && operation instanceof PushObjectOperation) {
                PushObjectOperation pushObjectOperation = (PushObjectOperation) operation;
                ObjectDescriptorImpl objectDescriptorImpl = pushObjectOperation.getObjectDescriptorImpl();
                if (objectDescriptorImpl != null) {
                    objectDescriptorImpl.setReceivedTimestamp(-1);
                    objectDescriptorImpl.setReadTimestamp(-1);
                    updateDescriptor(objectDescriptorImpl, conversationImpl);
                }
            } else if (operation.getType() == Operation.Type.INVITE_GROUP && operation instanceof GroupInviteOperation) {
                GroupInviteOperation inviteOperation = (GroupInviteOperation) operation;
                InvitationDescriptorImpl invitation = inviteOperation.getInvitationDescriptorImpl();
                if (invitation != null) {
                    invitation.setReceivedTimestamp(-1);
                    invitation.setReadTimestamp(-1);
                    updateDescriptor(invitation, conversationImpl);
                }
            }
        }
        mScheduler.finishOperation(operation, connection);
    }

    private void processErrorIQ(@NonNull ConversationImpl conversationImpl, @NonNull ErrorIQ errorIQ) {
        if (DEBUG) {
            Log.d(LOG_TAG, "processErrorIQ: conversationImpl=" + conversationImpl + " errorIQ=" + errorIQ);
        }

        if (INFO) {
            Log.i(LOG_TAG, "processErrorIQ: conversationImpl=" + conversationImpl);
        }

        Operation operation = mScheduler.getFirstActiveOperation(conversationImpl);
        if (operation != null) {
            final long requestId = operation.getRequestId();
            onError(requestId, ErrorCode.FEATURE_NOT_SUPPORTED_BY_PEER, conversationImpl.getId().toString());
        }
        mScheduler.finishInvokeOperation(operation, conversationImpl);
    }

    private void popDescriptor(@NonNull DescriptorImpl descriptor, @NonNull ConversationConnection connection) {
        if (DEBUG) {
            Log.d(LOG_TAG, "popDescriptor: descriptor=" + descriptor + " connection=" + connection);
        }

        final Conversation lCconversation = connection.getMainConversation();
        descriptor.adjustCreatedAndSentTimestamps(connection.getPeerTimeCorrection());
        descriptor.setReceivedTimestamp(System.currentTimeMillis());
        ConversationServiceProvider.Result result = mServiceProvider.insertOrUpdateDescriptorImpl(lCconversation, descriptor);

        // If the message was inserted, propagate it to upper layers through the onPopDescriptor callback.
        // Otherwise, we already know the message and we only need to acknowledge the sender.
        if (result == ConversationServiceProvider.Result.STORED) {
            connection.getConversation().setIsActive(true);

            for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
                mTwinlifeExecutor.execute(() -> serviceObserver.onPopDescriptor(DEFAULT_REQUEST_ID, lCconversation, descriptor));
            }
        }
    }

    private void updateDescriptor(@NonNull DescriptorImpl descriptor, @NonNull ConversationImpl conversationImpl) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateDescriptor: descriptor=" + descriptor + " conversation=" + conversationImpl);
        }

        mServiceProvider.updateDescriptorImplTimestamps(descriptor);

        for (ConversationService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateDescriptor(DEFAULT_REQUEST_ID, conversationImpl, descriptor,
                    UpdateType.TIMESTAMPS));
        }
    }

    private void writeThumbnail(@NonNull DescriptorId descriptorId, @NonNull byte[] thumbnail, boolean append) {
        if (DEBUG) {
            Log.d(LOG_TAG, "writeThumbnail: descriptorId=" + descriptorId + " append=" + append);
        }

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {

            return;
        }

        File conversationDir = new File(filesDir, Twinlife.CONVERSATIONS_DIR);
        File cdir = new File(conversationDir, descriptorId.twincodeOutboundId.toString());
        if (!cdir.exists() && !cdir.mkdirs()) {

            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot create directory: ", cdir);
            }
            mTwinlifeImpl.error(ErrorCode.NO_STORAGE_SPACE, null);

            return;
        }

        String name = String.valueOf(descriptorId.sequenceId);

        // Save the thumbnail data in a specific file.
        String thumbnailName = name + "-thumbnail.jpg";
        File thumbnailFile = new File(cdir, thumbnailName);

        try (FileOutputStream output = new FileOutputStream(thumbnailFile, append)) {
            output.write(thumbnail);

        } catch (Exception exception) {

            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot save thumbnail: ", thumbnailFile, exception);
            }
            Utils.deleteFile(LOG_TAG, thumbnailFile);

            mTwinlifeImpl.error(ErrorCode.NO_STORAGE_SPACE, null);
        }
    }

    @Nullable
    private String saveThumbnail(@NonNull FileDescriptorImpl fileDescriptor, @Nullable byte[] thumbnail) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveThumbnail: fileDescriptor=" + fileDescriptor);
        }

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {

            return null;
        }

        File conversationDir = new File(filesDir, Twinlife.CONVERSATIONS_DIR);
        File cdir = new File(conversationDir, fileDescriptor.getTwincodeOutboundId().toString());
        if (!cdir.exists() && !cdir.mkdirs()) {

            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot create directory: ", cdir);
            }
            mTwinlifeImpl.error(ErrorCode.NO_STORAGE_SPACE, null);

            return null;
        }

        String name = String.valueOf(fileDescriptor.getSequenceId());

        // Save the thumbnail data in a specific file.
        if (thumbnail != null) {
            String thumbnailName = name + "-thumbnail.jpg";
            File thumbnailFile = new File(cdir, thumbnailName);

            try (FileOutputStream output = new FileOutputStream(thumbnailFile)) {
                output.write(thumbnail);

            } catch (Exception exception) {

                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Cannot save thumbnail: ", thumbnailFile, exception);
                }
                Utils.deleteFile(LOG_TAG, thumbnailFile);

                mTwinlifeImpl.error(ErrorCode.NO_STORAGE_SPACE, null);

                return null;
            }
        }

        if (fileDescriptor.getExtension() != null) {
            name = name + "." + fileDescriptor.getExtension();
        }

        File file = new File(cdir, name);
        if (!file.exists()) {

            boolean created = false;
            try {
                created = file.createNewFile();
            } catch (IOException exception) {
                if (Logger.ERROR) {
                    Logger.error(LOG_TAG, "Cannot create file: ", file, exception);
                }
            }
            if (!created) {
                // Report a global error.
                mTwinlifeImpl.error(ErrorCode.NO_STORAGE_SPACE, null);

                return null;
            }
        }

        return file.getAbsolutePath();
    }

    @Nullable
    private String getPath(@NonNull UUID twincodeOutboundId, long sequenceId, @Nullable String extension) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getPath: twincodeOutboundId=" + twincodeOutboundId + " sequenceId=" + sequenceId + " extension=" + extension);
        }

        final File filesDir = mTwinlifeImpl.getFilesDir();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(filesDir.getAbsolutePath());
        stringBuilder.append("/conversations/");
        stringBuilder.append(twincodeOutboundId);
        stringBuilder.append('/');
        stringBuilder.append(sequenceId);
        if (extension != null) {
            stringBuilder.append('.');
            stringBuilder.append(extension);
        }
        String path = stringBuilder.toString();

        File file = new File(path);
        if (!file.exists()) {
            File directory = new File(file.getParent());
            if (!directory.exists()) {
                boolean created = false;
                try {
                    created = directory.mkdirs();
                } catch (Exception exception) {
                    Log.d(LOG_TAG, "Cannot create directory: " + exception);
                }
                if (!created) {
                    // Report a global error.
                    mTwinlifeImpl.error(ErrorCode.NO_STORAGE_SPACE, null);

                    return null;
                }
            }

            boolean created = false;
            try {
                created = file.createNewFile();
            } catch (IOException exception) {
                Log.d(LOG_TAG, "Cannot create file: " + exception);
            }
            if (!created) {
                // Report a global error.
                mTwinlifeImpl.error(ErrorCode.NO_STORAGE_SPACE, null);

                return null;
            }
        }

        return path;
    }

    /**
     * Copy the file and thumbnail associated with the source descriptor to the destination descriptor.
     * If there is not enough space, the destination file and thumbnail are removed.
     *
     * @param source the source descriptor.
     * @param destination the destination descriptor.
     * @return true if the copy succeeded.
     */
    private boolean copyFile(@NonNull FileDescriptorImpl source, @NonNull FileDescriptorImpl destination) {
        if (DEBUG) {
            Log.d(LOG_TAG, "copyFile: source=" + source + " destination=" + destination);
        }

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {

            return false;
        }

        File srcPath = new File(filesDir, source.getPath());
        File dstPath = new File(filesDir, destination.getPath());
        if (Utils.copyFile(srcPath, dstPath) != ErrorCode.SUCCESS) {
            Utils.deleteFile(LOG_TAG, dstPath);

            return false;
        }

        File srcThumbPath = source.getThumbnailFile(filesDir);
        if (srcThumbPath != null) {

            File dstThumbPath = destination.getThumbnailFile(filesDir);
            if (dstThumbPath != null) {

                if (Utils.copyFile(srcThumbPath, dstThumbPath) != ErrorCode.SUCCESS) {
                    Utils.deleteFile(LOG_TAG, dstPath);
                    Utils.deleteFile(LOG_TAG, dstThumbPath);

                    return false;
                }
            }
        }

        return true;
    }

    @NonNull
    private Pair<ErrorCode, File> saveFile(@NonNull UUID twincodeOutboundId, long sequenceId, @NonNull Uri path, @Nullable String extension, boolean toBeDeleted) {
        if (DEBUG) {
            Log.d(LOG_TAG, "saveFile: twincodeOutboundId=" + twincodeOutboundId + " path=" + path + " extension=" + extension + " toBeDeleted=" + toBeDeleted);
        }

        File deleteFile = null;
        if (toBeDeleted && "file".equals(path.getScheme())) {
            deleteFile = new File(path.getPath());
        }

        String toPath = getPath(twincodeOutboundId, sequenceId, extension);
        if (toPath == null) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "Cannot create path for ", path);
            }
            if (deleteFile != null) {
                Utils.deleteFile(LOG_TAG, deleteFile);
            }
            return new Pair<>(ErrorCode.FILE_NOT_FOUND, null);
        }
        File file = new File(toPath);

        // If the source is a file and must be deleted, try to move it to the target (if it fails, we use the copy).
        if (deleteFile != null && deleteFile.renameTo(file)) {
            return new Pair<>(ErrorCode.SUCCESS, file);
        }

        // Save the file to send in the conversation directory.
        ErrorCode errorCode = Utils.copyUriToFile(mTwinlifeImpl.getContentResolver(), path, file);
        if (deleteFile != null) {
            Utils.deleteFile(LOG_TAG, deleteFile);
        }

        if (errorCode != ErrorCode.SUCCESS) {
            Utils.deleteFile(LOG_TAG, file);
            file = null;
            if (errorCode == ErrorCode.NO_PERMISSION) {
                errorCode = ErrorCode.FILE_NOT_SUPPORTED;
            }
        }
        return new Pair<>(errorCode, file);
    }

    @SuppressLint("DefaultLocale")
    @Nullable
    private FileDescriptorImpl importFileWithConversation(@NonNull Conversation conversationImpl, long requestId, long expireTimeout,
                                                          @Nullable UUID sendTo, @Nullable DescriptorId replyTo, @NonNull Uri path,
                                                          @NonNull String name, @NonNull Descriptor.Type type, boolean toBeDeleted,
                                                          boolean copyAllowed) {
        if (DEBUG) {
            Log.d(LOG_TAG, "importFileWithConversation: conversationId=" + conversationImpl + " path=" + path
                    + " name=" + name + " type=" + type + " toBeDeleted=" + toBeDeleted + " copyAllowed=" + copyAllowed);
        }
        long sequenceId = newSequenceId();

        String extension = null;
        int index = name.lastIndexOf(".");
        if (index >= 0) {
            extension = name.substring(index + 1).toLowerCase();
        }

        final UUID twincodeOutboundId = conversationImpl.getTwincodeOutboundId();
        final Pair<ErrorCode, File> result = saveFile(twincodeOutboundId, sequenceId, path, extension, toBeDeleted);
        if (result.first != ErrorCode.SUCCESS) {
            onError(requestId, result.first, name);
            return null;
        }
        final File toPath = result.second;

        // Get more file information with the local copy.
        long length = toPath.length();

        FileDescriptorImpl fileDescriptorImpl = null;
        switch (type) {
            case IMAGE_DESCRIPTOR:
                if (length == 0 || extension == null || extension.isEmpty()) {
                    break;
                }

                int orientation;
                try {
                    ExifInterface exifInterface = new ExifInterface(toPath.getPath());
                    orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
                } catch (Exception exception) {
                    // no Exif information
                    orientation = ExifInterface.ORIENTATION_NORMAL;
                }

                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(toPath.getPath(), options);
                if (options.outWidth > 0 && options.outHeight > 0) {
                    int width;
                    int height;
                    switch (orientation) {
                        case ExifInterface.ORIENTATION_ROTATE_90:
                        case ExifInterface.ORIENTATION_ROTATE_270:
                            //noinspection SuspiciousNameCombination
                            width = options.outHeight;
                            //noinspection SuspiciousNameCombination
                            height = options.outWidth;
                            break;

                        default:
                            width = options.outWidth;
                            height = options.outHeight;
                            break;
                    }

                    boolean hasThumbnail = createThumbnail(toPath, width, height);
                    fileDescriptorImpl = new ImageDescriptorImpl(twincodeOutboundId, sequenceId, expireTimeout, sendTo, replyTo, toPath.getPath(),
                            extension, length, width, height, copyAllowed, hasThumbnail);
                }
                break;

            case AUDIO_DESCRIPTOR: {
                // Note: we cannot use the try() with resource since it is not supported on SDK < 29.
                MediaMetadataRetriever retriever = null;
                try {
                    retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(toPath.getPath());
                    String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    retriever.close();
                    retriever = null;
                    int duration = 0;
                    if (value != null) {
                        duration = Integer.parseInt(value) / 1000;
                    }

                    fileDescriptorImpl = new AudioDescriptorImpl(twincodeOutboundId, sequenceId,
                            expireTimeout, sendTo, replyTo, toPath.getPath(), extension, length, length, duration, copyAllowed, false);
                } catch (Exception ex) {
                    if (Logger.ERROR) {
                        Logger.error(LOG_TAG, "Cannot save audio: ", ex);
                    }
                    if (retriever != null) {
                        try {
                            retriever.close();
                        } catch (Exception ignored) {

                        }
                    }
                }
                break;
            }

            case VIDEO_DESCRIPTOR: {
                // Note: we cannot use the try() with resource since it is not supported on SDK < 29.
                //fileDescriptorImpl = VideoDescriptorImpl.createVideo(twincodeOutboundId, sequenceId, toPath);
                MediaMetadataRetriever retriever = null;
                try {
                    retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(toPath.getPath());
                    String value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    int duration = 0;
                    if (value != null) {
                        duration = Integer.parseInt(value) / 1000;
                    }
                    value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    int width = 0;
                    if (value != null) {
                        width = Integer.parseInt(value);
                    }
                    value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    int height = 0;
                    if (value != null) {
                        height = Integer.parseInt(value);
                    }
                    value = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                    if (value != null) {
                        int rotation = Integer.parseInt(value);
                        if (rotation == 90 || rotation == 270) {
                            int swap = height;
                            //noinspection SuspiciousNameCombination
                            height = width;
                            width = swap;
                        }
                    }

                    File thumbnailFile = getThumbnailPath(toPath);
                    if (thumbnailFile != null) {
                        Bitmap thumbnail = retriever.getFrameAtTime(0);
                        if (thumbnail != null) {
                            try (FileOutputStream out = new FileOutputStream(thumbnailFile)) {
                                if (Logger.ERROR) {
                                    Logger.error(LOG_TAG, "Video thumbnail width=", thumbnail.getWidth(),
                                            " height=", thumbnail.getHeight());
                                }
                                thumbnail.compress(Bitmap.CompressFormat.JPEG, 85, out);

                            } catch (Throwable exception) {
                                // Catch a possible out of memory exception but also a failure to write on disk.
                                if (Logger.ERROR) {
                                    Logger.error(LOG_TAG, "Video thumbnail", exception);
                                }
                                Utils.deleteFile(LOG_TAG, thumbnailFile);
                                thumbnailFile = null;
                            }
                        } else {
                            thumbnailFile = null;
                        }
                    }
                    retriever.close();
                    retriever = null;

                    fileDescriptorImpl = new VideoDescriptorImpl(twincodeOutboundId, sequenceId,
                            expireTimeout, sendTo, replyTo, toPath.getPath(), extension, length, width, height, duration, copyAllowed, thumbnailFile != null);
                } catch (Exception ex) {
                    if (Logger.ERROR) {
                        Logger.error(LOG_TAG, "Cannot save video: ", ex);
                    }
                    if (retriever != null) {
                        try {
                            retriever.close();
                        } catch (Exception ignored) {

                        }
                    }
                }
                break;
            }

            case NAMED_FILE_DESCRIPTOR: {
                fileDescriptorImpl = new NamedFileDescriptorImpl(twincodeOutboundId, sequenceId,
                        expireTimeout, sendTo, replyTo, toPath.getPath(), extension, length, length, name, copyAllowed, false);
                break;
            }

            default:
                break;
        }
        if (fileDescriptorImpl != null) {
            fileDescriptorImpl.setUpdatedTimestamp(System.currentTimeMillis());
        } else {
            Utils.deleteFile(LOG_TAG, toPath);
            onError(requestId, ErrorCode.FILE_NOT_SUPPORTED, name);
        }

        return fileDescriptorImpl;
    }

    static final long THUMBNAIL_MIN_LENGTH = 100 * 1024; // 100K

    @Nullable
    private File getThumbnailPath(@NonNull File sourceFile) {
        if (DEBUG) {
            Log.d(LOG_TAG, " getThumbnailPath: sourceFile=" + sourceFile);
        }

        String path = sourceFile.getPath();
        int pos = path.lastIndexOf('.');
        if (pos < 0) {

            return null;
        }

        String thumbnailPath = path.substring(0, pos) + "-thumbnail.jpg";
        return new File(thumbnailPath);
    }

    private boolean createThumbnail(@NonNull File imageFile, int width, int height) {
        if (DEBUG) {
            Log.d(LOG_TAG, " createThumbnail: imageFile=" + imageFile + " width=" + width + " height=" + height);
        }

        long length = imageFile.length();
        if (length < THUMBNAIL_MIN_LENGTH) {

            return false;
        }

        File thumbnailFile = getThumbnailPath(imageFile);
        if (thumbnailFile == null) {

            return false;
        }
        try {
            return mImageTools.copyImage(imageFile, thumbnailFile, 640, 640, false);
        } catch (Exception exception) {
            if (Logger.ERROR) {
                Logger.error(LOG_TAG, "copyImage", exception);
            }
            Utils.deleteFile(LOG_TAG, thumbnailFile);
            return false;
        }
    }

    void deleteFiles(@NonNull Conversation conversation) {
        if (DEBUG) {
            Log.d(LOG_TAG, " deleteFiles: conversationImpl=" + conversation);
        }

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {

            return;
        }

        filesDir = new File(filesDir, Twinlife.CONVERSATIONS_DIR);

        // Remove the files we have sent except for a group member conversation when the member is removed.
        if (!conversation.isGroup() || (conversation instanceof GroupConversationImpl)) {
            File directory = new File(filesDir, conversation.getTwincodeOutboundId().toString());
            if (directory.exists()) {
                if (!Utils.deleteDirectory(directory) && Logger.ERROR) {
                    Logger.error(LOG_TAG, "deleteFiles: deleteDirectory() failed: ", directory);
                }
            }
        }

        File directory = new File(filesDir, conversation.getPeerTwincodeOutboundId().toString());
        if (directory.exists()) {
            if (!Utils.deleteDirectory(directory) && Logger.ERROR) {
                Logger.error(LOG_TAG, "deleteFiles: deleteDirectory() failed: ", directory);
            }
        }
    }

    private static boolean isSmallImage(@NonNull File file) {

        String name = file.getName();
        if (!name.endsWith(".jpg") && !name.endsWith(".gif") && !name.endsWith(".png")) {
            return false;
        }

        return file.length() <= THUMBNAIL_MIN_LENGTH;
    }

    private void deleteDescriptorFiles(@NonNull List<DescriptorId> descriptorIds) {
        if (DEBUG) {
            Log.d(LOG_TAG, " deleteDescriptorFiles: descriptorIds=" + descriptorIds);
        }

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {

            return;
        }

        final Map<UUID, Set<Long>> deleteMap = new HashMap<>();
        for (DescriptorId descriptorId : descriptorIds) {
            Set<Long> seqList = deleteMap.get(descriptorId.twincodeOutboundId);
            if (seqList == null) {
                seqList = new HashSet<>();
                deleteMap.put(descriptorId.twincodeOutboundId, seqList);
            }
            seqList.add(descriptorId.sequenceId);
        }

        filesDir = new File(filesDir, Twinlife.CONVERSATIONS_DIR);
        for (Map.Entry<UUID, Set<Long>> peer : deleteMap.entrySet()) {
            File directory = new File(filesDir, peer.getKey().toString());
            if (directory.exists()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    final Set<Long> seqList = peer.getValue();
                    for (File file : files) {
                        String name = file.getName();
                        int index = name.indexOf('.');
                        if (index > 0) {
                            try {
                                // We must take into account thumbnail files in the form <sequence>-thumbnail.<ext>.
                                // The thumbnail file is kept when CLEAR_MEDIA is used, it is removed in other modes.
                                int lastPos = name.indexOf('-');
                                long sequenceId;
                                if (lastPos <= 0 || lastPos > index) {
                                    sequenceId = Long.parseLong(name.substring(0, index));
                                } else {
                                    sequenceId = Long.parseLong(name.substring(0, lastPos));
                                }
                                if (seqList.contains(sequenceId)) {
                                    Utils.deleteFile(LOG_TAG, file);
                                }
                            } catch (Exception exception) {
                                if (Logger.ERROR) {
                                    Logger.error(LOG_TAG, " deleteDescriptorFiles failed:\n" +
                                            " file=" + file + "\n" +
                                            " exception=" + exception + "\n");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void deleteUnreacheableFiles(@NonNull Conversation conversation,
                                         @NonNull Map<UUID, DescriptorId> resetList,
                                         @NonNull ClearMode clearMode) {
        if (DEBUG) {
            Log.d(LOG_TAG, " deleteUnreacheableFiles: conversationImpl=" + conversation + " clearMode=" + clearMode);
        }

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir == null) {

            return;
        }

        filesDir = new File(filesDir, Twinlife.CONVERSATIONS_DIR);
        for (DescriptorId descriptorId : resetList.values()) {
            File directory = new File(filesDir, descriptorId.twincodeOutboundId.toString());
            if (directory.exists()) {
                File[] files = directory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        String name = file.getName();
                        int index = name.indexOf('.');
                        if (index > 0) {
                            try {
                                // We must take into account thumbnail files in the form <sequence>-thumbnail.<ext>.
                                // The thumbnail file is kept when CLEAR_MEDIA is used, it is removed in other modes.
                                int lastPos = name.indexOf('-');
                                long sequenceId;
                                if (lastPos <= 0 || lastPos > index) {
                                    sequenceId = Long.parseLong(name.substring(0, index));
                                } else if (clearMode != ClearMode.CLEAR_MEDIA) {
                                    sequenceId = Long.parseLong(name.substring(0, lastPos));
                                } else {
                                    sequenceId = Long.MAX_VALUE;
                                }
                                if (sequenceId <= descriptorId.sequenceId && (clearMode != ClearMode.CLEAR_MEDIA || !isSmallImage(file))) {
                                    Utils.deleteFile(LOG_TAG, file);
                                }
                            } catch (Exception exception) {
                                if (Logger.ERROR) {
                                    Logger.error(LOG_TAG, " deleteUnreacheableFiles failed:\n" +
                                            " conversation=" + conversation + "\n" +
                                            " file=" + file + "\n" +
                                            " exception=" + exception + "\n");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void deleteAllFiles() {
        if (DEBUG) {
            Log.d(LOG_TAG, " deleteAllFiles");
        }

        File filesDir = mTwinlifeImpl.getFilesDir();
        if (filesDir != null) {
            String path = filesDir.getAbsolutePath() + "/conversations";
            File directory = new File(path);
            if (directory.exists()) {
                if (!Utils.deleteDirectory(directory) && Logger.WARN) {
                    Logger.warn(LOG_TAG, "deleteAllFiles: deleteDirectory() failed: ", directory);
                }
            }
        }
    }

    /**
     * Package private interface to iterate over the list of known conversations.
     * <p>
     * This is a simplified version of the Visitor design pattern.
     */
    interface ConversationVisitor {

        void visit(Conversation conversation, OperationList operations);
    }

    /**
     * Package private operation to iterate over the list of known conversations.
     * <p>
     * Used by the ConversationsDump and OperationsDump for debugging.
     *
     * @param visitor the object to call for each conversation.
     */
    void iterateConversations(ConversationVisitor visitor) {

    }
}
