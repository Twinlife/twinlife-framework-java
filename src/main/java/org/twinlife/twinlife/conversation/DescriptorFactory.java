/*
 *  Copyright (c) 2024-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.conversation;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.ConversationService.Descriptor;
import org.twinlife.twinlife.ConversationService.DescriptorId;
import org.twinlife.twinlife.ConversationService.GeolocationDescriptor;

import java.util.UUID;

/**
 * Descriptor factory to create a descriptor for the Audio/Video call service.
 */
public abstract class DescriptorFactory {
    private static final String LOG_TAG = "DescriptorFactory";
    private static final boolean DEBUG = false;

    public DescriptorFactory() {
    }

    public abstract DescriptorId newDescriptorId();

    /**
     * Create a message descriptor to be sent through the conversation handler.
     *
     * @param message the message to send.
     * @param replyTo the optional descriptor for a reply.
     * @param copyAllowed whether the copy is allowed.
     * @return the message descriptor.
     */
    public Descriptor createMessage(@NonNull String message, @Nullable DescriptorId replyTo, boolean copyAllowed) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createMessage message=" + message + " replyTo=" + replyTo + " copyAllowed=" + copyAllowed);
        }

        final DescriptorId descriptorId = newDescriptorId();
        return new ObjectDescriptorImpl(descriptorId, 0, 0, null, replyTo, message, copyAllowed);
    }

    /**
     * Create a geolocation descriptor to be sent through the conversation handler.
     *
     * @param longitude the longitude
     * @param latitude the latitude.
     * @param altitude the altitude.
     * @return the geolocation descriptor.
     */
    public GeolocationDescriptor createGeolocation(double longitude, double latitude, double altitude,
                                                   double mapLongitudeDelta, double mapLatitudeDelta) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createGeolocation longitude=" + longitude + " latitude=" + latitude
                    + " altitude=" + altitude + " mapLongitudeDelta=" + mapLongitudeDelta + " mapLatitudeDelta=" + mapLatitudeDelta);
        }

        final DescriptorId descriptorId = newDescriptorId();
        return new GeolocationDescriptorImpl(descriptorId, 0, 0, longitude, latitude, altitude,
                mapLongitudeDelta, mapLatitudeDelta);
    }

    /**
     * Create a twincode descriptor to be sent through the conversation handler.
     *
     * @param twincodeId the invitation twincode to send.
     * @param schemaId the twincode schema id that describes the intent of the twincode.
     * @param publicKey the optional twincode public key (in Base64 URL).
     * @param replyTo the optional descriptor for a reply.
     * @param copyAllowed whether the copy is allowed.
     * @return the invitation descriptor.
     */
    public Descriptor createTwincodeDescriptor(@NonNull UUID twincodeId, @NonNull UUID schemaId, @Nullable String publicKey,
                                               @Nullable DescriptorId replyTo, boolean copyAllowed) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createInvitation twincodeId=" + twincodeId + " schemaId=" + schemaId
                    + " publicKey=" + publicKey + " replyTo=" + replyTo + " copyAllowed=" + copyAllowed);
        }

        final DescriptorId descriptorId = newDescriptorId();
        return new TwincodeDescriptorImpl(descriptorId, 0, 0, null, replyTo,
                twincodeId, schemaId, publicKey, copyAllowed);
    }
}
