/*
 *  Copyright (c) 2013-2024 twinlife SA.
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

@SuppressWarnings("unused")
public interface TwincodeFactoryService extends BaseService<BaseService.ServiceObserver> {

    String VERSION = "2.2.0";

    class TwincodeFactoryServiceConfiguration extends BaseServiceConfiguration {

        public TwincodeFactoryServiceConfiguration() {

            super(BaseServiceId.TWINCODE_FACTORY_SERVICE_ID, VERSION, false);
        }
    }

    /**
     * Create a set of twincodes with the factory, inbound, outbound, switch.
     * Bind the inbound twincode to the device.  Each twincode is configured with
     * its own attributes.
     *
     * @param twincodeFactoryAttributes the factory attributes.
     * @param twincodeInboundAttributes the inbound twincode attributes.
     * @param twincodeOutboundAttributes the outbound twincode attributes.
     * @param twincodeSwitchAttributes the switch twincode attributes.
     * @param twincodeSchemaId the schemaId associated with the twincode.
     * @param complete the handler executed when the operation completes or an error occurred.
     */
    void createTwincode(@NonNull List<AttributeNameValue> twincodeFactoryAttributes,
                        @Nullable List<AttributeNameValue> twincodeInboundAttributes,
                        @Nullable List<AttributeNameValue> twincodeOutboundAttributes,
                        @Nullable List<AttributeNameValue> twincodeSwitchAttributes,
                        @NonNull UUID twincodeSchemaId,
                        @NonNull Consumer<TwincodeFactory> complete);

    /**
     * Delete the twincode factory and the associated inbound, outbound and switch twincodes.
     *
     * @param twincodeFactoryId the twincode factory id.
     * @param complete the handler executed when the operation completes or an error occurred.
     */
    void deleteTwincode(@NonNull UUID twincodeFactoryId, @NonNull Consumer<UUID> complete);
}
