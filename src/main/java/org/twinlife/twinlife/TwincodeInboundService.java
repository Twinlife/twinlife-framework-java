/*
 *  Copyright (c) 2013-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.UUID;

public interface TwincodeInboundService extends BaseService<BaseService.ServiceObserver> {

    String VERSION = "3.3.1";

    class TwincodeInboundServiceConfiguration extends BaseServiceConfiguration {

        public TwincodeInboundServiceConfiguration() {

            super(BaseServiceId.TWINCODE_INBOUND_SERVICE_ID, VERSION, false);
        }
    }

    interface InvocationListener {

        @Nullable
        ErrorCode onInvokeTwincode(@NonNull TwincodeInvocation invocation);
    }

    void addListener(@NonNull String action, @NonNull InvocationListener observer);

    void getTwincode(@NonNull UUID twincodeInboundId, @NonNull TwincodeOutbound twincodeOutbound,
                     @NonNull Consumer<TwincodeInbound> complete);

    void bindTwincode(@NonNull TwincodeInbound twincodeInbound, @NonNull Consumer<UUID> complete);

    void unbindTwincode(@NonNull TwincodeInbound twincodeInbound, @NonNull Consumer<UUID> complete);

    void updateTwincode(@NonNull TwincodeInbound twincodeInbound, @NonNull List<AttributeNameValue> attributes,
                        @Nullable List<String> deleteAttributeNames,
                        @NonNull Consumer<TwincodeInbound> complete);

    void acknowledgeInvocation(@NonNull UUID invocationId, @NonNull ErrorCode errorCode);

    void triggerPendingInvocations(@NonNull Runnable complete);

    /**
     * Wait until all pending invocations for the given twincode inbound Id have been processed
     * and execute the code block.  If there is no pending invocation for the twincode, the code
     * block is executed immediately.
     *
     * @param twincodeId the twincode inbound id
     * @param complete the code block to execute.
     */
    void waitInvocations(@NonNull UUID twincodeId, @NonNull Runnable complete);

    /**
     * Check if we have some pending invocations being processed.
     *
     * @return true if some pending invocations are being processed.
     */
    boolean hasPendingInvocations();
}
