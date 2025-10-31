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

import java.util.Map;

@SuppressWarnings("unused")
public interface ManagementService extends BaseService<ManagementService.ServiceObserver> {

    String VERSION = "2.2.0";

    String PUSH_NOTIFICATION_APNS_VARIANT = "APNS";
    String PUSH_NOTIFICATION_FIREBASE_VARIANT = "Firebase";

    class ManagementServiceConfiguration extends BaseServiceConfiguration {

        public ManagementServiceConfiguration() {

            super(BaseServiceId.MANAGEMENT_SERVICE_ID, VERSION, true);
        }
    }

    interface ServiceObserver extends BaseService.ServiceObserver {

        void onValidateConfiguration(long requestId);
    }

    class DefaultServiceObserver extends BaseService.DefaultServiceObserver implements ServiceObserver {

        @Override
        public void onValidateConfiguration(long requestId) {
        }
    }

    void setPushNotificationToken(@NonNull String pushNotificationVariant, @NonNull String pushNotificationToken);

    void validateConfiguration(long requestId);

    void updateConfiguration(long requestId);

    /**
     * Check if we have the configuration to receive remote push notification to wakeup the application.
     *
     * @return true if the remote push notification is supported.
     */
    boolean hasPushNotification();

    void sendFeedback(@NonNull String email, @NonNull String subject, @NonNull String description,
                      @Nullable String logReport);

    void error(@NonNull ErrorCode errorCode, @Nullable String errorParameter);

    void logEvent(@NonNull String eventId, @NonNull Map<String, String> attributes, boolean flush);

    @NonNull
    String getLogReport();

    /**
     * Get the configuration returned by the server to make WebRTC connexions.
     * (this is not exposed for Android).
     *
     * @return the WebRTC configuration provided by the server.
     */
    Configuration getConfiguration();

    /**
     * Report a failed assertion in the application.  The report contains only technical data
     * to indicate the failed assertion, an optional list of values composed of integers, longs
     * or UUIDs, and an optional exception.
     *
     * @param assertPoint the control point representing the assertion.
     * @param values the optional list of values.
     * @param stackTrace send a stack trace of the position where assertion is called.
     * @param exception the optional exception.
     */
    void assertion(@NonNull AssertPoint assertPoint, @Nullable AssertPoint.Values values,
                   boolean stackTrace, @Nullable Throwable exception);
}
