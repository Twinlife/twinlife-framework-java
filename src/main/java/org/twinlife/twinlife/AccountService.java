/*
 *  Copyright (c) 2012-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import androidx.annotation.NonNull;

@SuppressWarnings("unused")
public interface AccountService extends BaseService<AccountService.ServiceObserver> {

    String VERSION = "2.3.0";

    enum AuthenticationAuthority {
        // Account is not registered yet.
        UNREGISTERED,

        // Using the device account model.
        DEVICE,

        // Using the user+password model (not used anymore/legacy).
        TWINLIFE,

        // Account is disabled.
        DISABLED
    }

    enum MerchantIdentification {
        // Purchase is made using Google platform.
        MERCHANT_GOOGLE,

        // Purchase is made using an external platform.
        MERCHANT_EXTERNAL
    }

    class AccountServiceConfiguration extends BaseServiceConfiguration {

        public AuthenticationAuthority defaultAuthenticationAuthority;

        public AccountServiceConfiguration() {

            super(BaseServiceId.ACCOUNT_SERVICE_ID, VERSION, true);
        }
    }

    interface ServiceObserver extends BaseService.ServiceObserver {

        void onSignIn();

        void onSignInError(@NonNull ErrorCode errorCode);

        void onSignOut();

        void onCreateAccount(long requestId);

        void onDeleteAccount(long requestId);

        void onSubscribeUpdate(long requestId, @NonNull ErrorCode errorCode);
    }

    class DefaultServiceObserver extends BaseService.DefaultServiceObserver implements ServiceObserver {

        @Override
        public void onSignIn() {
        }

        @Override
        public void onSignInError(@NonNull ErrorCode errorCode) {
        }

        @Override
        public void onSignOut() {
        }

        @Override
        public void onCreateAccount(long requestId) {
        }

        @Override
        public void onDeleteAccount(long requestId) {
        }

        @Override
        public void onSubscribeUpdate(long requestId, @NonNull ErrorCode errorCode) {

        }
    }

    AuthenticationAuthority getAuthenticationAuthority();

    boolean isReconnectable();

    boolean isFeatureSubscribed(@NonNull String feature);

    void createAccount(long requestId, @NonNull String etoken);

    void signOut();

    void deleteAccount(long requestId);

    void subscribeFeature(long requestId, @NonNull MerchantIdentification merchantId,
                          @NonNull String purchaseProductId, @NonNull String purchaseToken, @NonNull String purchaseOrderId);

    void cancelFeature(long requestId, @NonNull MerchantIdentification merchantId,
                          @NonNull String purchaseToken, @NonNull String purchaseOrderId);
}
