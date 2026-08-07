/*
 *  Copyright (c) 2017-2026 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStore.PrivateKeyEntry;
import java.security.KeyStore.SecretKeyEntry;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.interfaces.RSAKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.security.auth.x500.X500Principal;

/**
 * This implementation has 10 years of history to support various versions of Android:
 * - in the early ages in 2017, the implementation was more an obfuscation of data with an encryption key
 *   that is embedded within the code.  This was never used on Skred but was still used on twinme
 *   due to application backward compatibility to avoid breaking existing devices.
 * - starting with Android 4.3 (JettyBeanMR1) an implementation was created and made with the keystore API.
 *   For Android 4.3, it was using an RSA key with default size and starting with Android 4.4 it was using
 *   a 2048-bit RSA key.  This RSA key is still used on Android 5.x (support for Android 4.x has been dropped
 *   on twinme and Skred in 2023).
 * - starting with Android 6.0 (M), we could use the new keystore API and the encryption uses AES-CBC or AES-GCM with
 *   a 256-bit key.  Back in 2018, some Android implementation were broken, and we had to sometimes fallback
 *   in using the RSA key.
 * - we changed the encryption from AES/CBC to AES/GCM to detect tampering (mostly due to bugs and not attacks).
 *   Due to this, we handle the migration of encryption but because Android Keystore is buggy we also have to
 *   fall back to previous encryption mechanisms.
 * - the Android 5.0 and 5.1 (JellyBean) was removed on 2026-05-05 to drop less secure implementation.
 *   We still have to handle some legacy decryption.
 * - the default encryption/obfuscation key is only used to decrypt content in the case where migration is
 *   necessary.
 * - because Android Keystore is broken on some devices, we have to define fallbacks for the
 *   key creation and use:
 *   - AES-GCM if we successfully create and verify encryption/decryption succeeds,
 *   - AES-CBC if AES-GCM failed, but we also verify encryption/decryption succeeds,
 *   - RSA as a last resort if AES-GCM and AES-CBC failed
 *     We also try several sizes: 4096, 3072, 2048 and 1024 as final generation.
 * At the time we created this implementation, the AndroidX Crypto library with the encrypted shared preference
 * was not available.  Since that encrypted shared preference library is deprecated now, we are still not using it
 * (and it is the reason why we don't and won't use it).
 *
 */
final class KeyChain {
    private static final String LOG_TAG = "KeyChain";
    private static final boolean DEBUG = false;

    private static final String AndroidKeyStore = "AndroidKeyStore";
    private static final String TWINLIFE_SECRET_KEY = "TwinlifeSecretKey";
    private static final String TWINLIFE_GCM_KEY = "TwinlifeGCMKey";
    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final String AES_MODE = "AES/CBC/PKCS7Padding";
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";
    private static final String TWINLIFE_SECURED_PREFERENCES = "TwinlifeSecuredPreferences";
    private static final String TWINLIFE_SECURED_KEY = "TwinlifeSecuredKey";
    private static final String TWINLIFE_BAD_GCM = "TwinlifeBadGCM";
    private static final String GCM_PREFIX = "gs."; // Prefix used when the value is encrypted with AES_GCM
    private static final String CBC_PREFIX = "ks."; // Prefix used when the value is encrypted with AES_MODE
    private static final int IV_LENGTH_BYTES = 16;
    private static final byte[] UUID1 = {-112, -102, 4, -13, 88, 2, 69, -13, -77, 50, -83, 81, 22, 76, -14, -89};
    private static final byte[] UUID2 = {1, -115, -24, -96, 27, -27, 74, -49, -74, -34, 90, 106, 102, 103, 8, 126};
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_GCM_SIZE = 12; // bytes (recommended for GCM)

    @NonNull
    private final Context mContext;
    @Nullable
    private final Key mSecuredKey;
    @Nullable
    private final Key mOldSecuredKey;
    @NonNull
    private final String mKeyPrefix;
    private final Twinlife mTwinlife;
    private final boolean mIsGCMKey;
    private boolean mGCMError;

    //
    // Private Methods
    //

    KeyChain(@NonNull Context context, @NonNull Twinlife twinlife) {
        if (DEBUG) {
            Log.d(LOG_TAG, "KeyChain: context=" + context);
        }

        final SharedPreferences sharedPreferences = context.getSharedPreferences(TWINLIFE_SECURED_PREFERENCES, Context.MODE_PRIVATE);
        mContext = context;
        mTwinlife = twinlife;
        mGCMError = false;

        Key securedKey = null;
        Key oldSecuredKey = null;
        boolean hasGCMKey = false;
        try {
            final KeyStore keyStore = KeyStore.getInstance(AndroidKeyStore);
            keyStore.load(null);

            // See if there was some previous errors when encrypting using GCM (some device are having issues
            // and raise a java.security.InvalidKeyException when we try to encrypt using AES-GCM).
            // Get or generate a secure key: the secure key must be inserted in the AndroidKeyStore.
            // We then retry getting the secure key to make sure we can extract it from the keystore and use it.
            // If this fails, we try another method until all possible methods have been checked.
            mGCMError = sharedPreferences.getBoolean(TWINLIFE_BAD_GCM, false);
            if (!mGCMError) {
                // Get or create the AES-GCM encryption key.
                for (int retry = 0; retry < 5 && securedKey == null; retry++) {
                    securedKey = loadOrGenerateGCMKey(twinlife, keyStore, retry);
                }
                hasGCMKey = securedKey != null;

                // AES-GCM is broken on this device.
                if (securedKey == null) {
                    markGCMError(twinlife, sharedPreferences);
                    mGCMError = true;
                }
            }

            // Get the old key if it exists, we will use the old AES-CBC encryption mode.
            // (even if we have the AES-GCM key, this old key is necessary to migrate from the legacy storage).
            oldSecuredKey = getSecureKey(keyStore, sharedPreferences, twinlife);
            if (securedKey == null) {
                if (oldSecuredKey == null) {
                    // If the AES-GCM failed to create the key, build one with AES-CBC.
                    for (int retry = 0; retry < 5 && oldSecuredKey == null; retry++) {
                        oldSecuredKey = loadOrGenerateCBCKey(twinlife, keyStore, retry);
                    }

                    // If the AES-GCM and AES-CBC failed to create the key, fallback to creation of an RSA key.
                    // Android 5.x up to Android 6.0 if there are issues with generateSecureKeyM().
                    // - retry 0, 1 => RSA 4096
                    // - retry 2, 3 => RSA 3072
                    // - retry 4, 5 => RSA 2048
                    // - retry 6, 7 => RSA 1024 (less secure)
                    // If this works, the encryption key will be protected by the RSA key.
                    for (int retry = 0; retry < 8 && oldSecuredKey == null; retry++) {
                        oldSecuredKey = loadOrGenerateCBCKeyFromRSA(twinlife, context, sharedPreferences, keyStore, retry);
                    }
                }

                // If the GCM encryption key does not exist, use the old key with AES-CBC.
                if (oldSecuredKey != null) {
                    securedKey = oldSecuredKey;
                    oldSecuredKey = null;
                }
            }

        } catch (Exception exception) {
            mTwinlife.exception(AndroidAssertPoint.KEYCHAIN, exception, null);
        }

        mIsGCMKey = hasGCMKey;
        mSecuredKey = securedKey;
        mOldSecuredKey = oldSecuredKey;

        // To migrate safely from the legacy storage with the default secret key and use
        // a key from the Android Keystore, the entries are prefixed by 'gs.':
        // - the 'gs.TwinlifeSecuredConfiguration' is encrypted by the Android keystore key using AES/GCM,
        // - the 'ks.TwinlifeSecuredConfiguration' is encrypted by the Android keystore key using AES/CBC/PKCS7Padding,
        // - the 'TwinlifeSecuredConfiguration' if it exists, is encrypted by the default secret key
        //   when `LEGACY_NO_KEYSTORE` is true, but it is encrypted with the Android keystore key
        //   for others (Skred).  Encryption mode is AES/CBC/PKCS7Padding.
        // - if we failed to create the Keychain key, there will be a null `mSecureKey` and the
        //   user will see a KEYSTORE_ERROR (42) in the fatal activity view.
        mKeyPrefix = mIsGCMKey ? GCM_PREFIX : BuildConfig.LEGACY_NO_KEYSTORE && securedKey != null ? CBC_PREFIX : "";
    }

    static class RSASecretKeySpec extends SecretKeySpec {
        final int keySize;
        RSASecretKeySpec(byte[] key, int size) {
            super(key, "AES");
            keySize = size;
        }
    }

    @Nullable
    private static SecretKey getSecureKey(@NonNull KeyStore keyStore, @NonNull SharedPreferences sharedPreferences, @NonNull Twinlife twinlife) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getSecureKey");
        }

        try {
            final Entry entry = keyStore.getEntry(TWINLIFE_SECRET_KEY, null);
            if (entry != null) {
                if (entry instanceof SecretKeyEntry) {
                    final SecretKeyEntry secretKeyEntry = (SecretKeyEntry) entry;
                    if (secretKeyEntry.getSecretKey() != null) {
                        return secretKeyEntry.getSecretKey();
                    }
                } else if (entry instanceof PrivateKeyEntry) {
                    final PrivateKeyEntry privateKeyEntry = (PrivateKeyEntry) entry;
                    final PrivateKey privateKey = privateKeyEntry.getPrivateKey();
                    final int keySizeBits;
                    if (privateKey instanceof RSAKey) {
                        keySizeBits = ((RSAKey) privateKey).getModulus().bitLength();
                    } else {
                        keySizeBits = 2048;
                    }
                    final String securedData = sharedPreferences.getString(TWINLIFE_SECURED_KEY, null);

                    if (securedData != null) {
                        final byte[] encryptedData = Base64.decode(securedData, Base64.DEFAULT);
                        final byte[] data = rsaDecrypt(privateKey, encryptedData);
                        if (data != null) {
                            return new RSASecretKeySpec(data, keySizeBits);
                        }
                    }
                }
            }
        } catch (NullPointerException ignored) {
            // Note: a NullPointerException is sometimes raised by keyStore.getEntry() despite our alias that is NEVER null.
            // This is a bug on some OEM firmware.

        } catch (Exception exception) {
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_LOAD_KEY, exception, null);
        }
        return null;
    }

    @NonNull
    ConfigurationService.SecuredMethod getSecuredMethod() {

        if (mIsGCMKey) {
            return ConfigurationService.SecuredMethod.KEYSTORE_AES_GCM;
        } else if (mSecuredKey instanceof RSASecretKeySpec) {
            final RSASecretKeySpec k = (RSASecretKeySpec) mSecuredKey;
            switch (k.keySize) {
                case 4096:
                    return ConfigurationService.SecuredMethod.KEYSTORE_RSA_4096_AES_CBC;
                case 3072:
                    return ConfigurationService.SecuredMethod.KEYSTORE_RSA_3072_AES_CBC;
                case 2048:
                    return ConfigurationService.SecuredMethod.KEYSTORE_RSA_2048_AES_CBC;
                default:
                    return ConfigurationService.SecuredMethod.KEYSTORE_RSA_1024_AES_CBC;
            }
        } else if (mSecuredKey != null) {
            return ConfigurationService.SecuredMethod.KEYSTORE_AES_CBC;
        } else {
            return ConfigurationService.SecuredMethod.KEYSTORE_ERROR;
        }
    }

    @Nullable
    byte[] getKeyChainData(@NonNull String key) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getKeyChainData: key=" + key);
        }

        final SharedPreferences sharedPreferences = mContext.getSharedPreferences(TWINLIFE_SECURED_PREFERENCES, Context.MODE_PRIVATE);
        String securedData = sharedPreferences.getString(mKeyPrefix + key, null);
        if (securedData == null) {
            // No keyprefix means we failed to create a key in the keychain, there is nothing to migrate.
            if (mKeyPrefix.isEmpty()) {
                return null;
            }

            // Handle keychain migration:
            // - for legacy twinme, if we have an old encryption key, look for the ks.<key> content,
            //   BUT if there is no ks.<key>, look for <key> and use the default encryption key.
            // - for legacy Skred, look for <key> and use either the old encryption key if it exists
            //   of the default encryption key.
            final Key decryptKey;
            if (BuildConfig.LEGACY_NO_KEYSTORE && mOldSecuredKey != null) {
                securedData = sharedPreferences.getString(CBC_PREFIX + key, null);
                if (securedData != null) {
                    decryptKey = mOldSecuredKey;
                } else {
                    securedData = sharedPreferences.getString(key, null);
                    decryptKey = getDefaultSecretKey();
                }
            } else {
                securedData = sharedPreferences.getString(key, null);
                decryptKey = mOldSecuredKey != null ? mOldSecuredKey : getDefaultSecretKey();
            }
            if (securedData == null) {
                return null;
            }

            if (mOldSecuredKey == null) {
                mTwinlife.assertion(AndroidAssertPoint.KEYCHAIN_MIGRATION, AssertPoint.createMarker(key.length()));
            }
            byte[] data = decrypt(decryptKey, Base64.decode(securedData, Base64.DEFAULT), 0, false);
            if (BuildConfig.LEGACY_NO_KEYSTORE && data == null) {
                securedData = sharedPreferences.getString(key, null);
                data = decrypt(getDefaultSecretKey(), Base64.decode(securedData, Base64.DEFAULT), 0, false);
                mTwinlife.assertion(AndroidAssertPoint.KEYCHAIN_MIGRATION_LEGACY, AssertPoint.createMarker(key.length()));
            }
            if (data == null) {
                mTwinlife.assertion(AndroidAssertPoint.KEYCHAIN_DECRYPT_LEGACY, AssertPoint.createMarker(key.length()));
                return null;
            }

            // If we have a valid new encryption key, store by using the new encryption key.
            // Note: we keep the current entry for this step: it will be removed at a next launch.
            if (mSecuredKey != null && (!mIsGCMKey || !mGCMError)) {
                updateKeyChain(key, data);
            }
            return data;
        }

        if (mSecuredKey == null) {
            return null;
        }
        final byte[] data = decrypt(mSecuredKey, Base64.decode(securedData, Base64.DEFAULT), 0, mIsGCMKey);
        if (data == null) {
            mTwinlife.assertion(AndroidAssertPoint.KEYCHAIN_DECRYPT, AssertPoint.createMarker(mIsGCMKey ? 1 : 0).putLength(key.length()));

        } else if (!mKeyPrefix.isEmpty() && sharedPreferences.getString(key, null) != null) {
            // If the old entry still existed in the shared preference, we must now remove it.
            // That entry was encrypted with the default key, and it was kept voluntarily to recover in case
            // of errors in the deployment of the new gs. and ks. modes (because Android Keystore is unreliable).
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.remove(key);
            if (GCM_PREFIX.equals(mKeyPrefix)) {
                edit.remove(CBC_PREFIX + key);
            }
            edit.apply();
        }
        return data;
    }

    @SuppressLint("ApplySharedPref")
    @NonNull
    ErrorCode createKeyChain(String key, byte[] data) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createKeyChainInternal: key=" + key + " data=" + Arrays.toString(data));
        }

        byte[] encryptedData = encrypt(data);
        if (encryptedData != null) {
            String securedData = Base64.encodeToString(encryptedData, Base64.DEFAULT);
            SharedPreferences sharedPreferences = mContext.getSharedPreferences(TWINLIFE_SECURED_PREFERENCES, Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putString(mKeyPrefix + key, securedData);
            edit.commit();

            return ErrorCode.SUCCESS;
        }

        return ErrorCode.KEYSTORE_ERROR;
    }

    @SuppressLint("ApplySharedPref")
    @NonNull
    ErrorCode updateKeyChain(@NonNull String key, @NonNull byte[] data) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateKeyChainInternal: key=" + key + " data=" + Arrays.toString(data));
        }

        byte[] encryptedData = encrypt(data);
        if (encryptedData != null) {
            String securedData = Base64.encodeToString(encryptedData, Base64.DEFAULT);
            SharedPreferences sharedPreferences = mContext.getSharedPreferences(TWINLIFE_SECURED_PREFERENCES, Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.putString(mKeyPrefix + key, securedData);
            edit.commit();

            return ErrorCode.SUCCESS;
        }

        return ErrorCode.KEYSTORE_ERROR;
    }

    @SuppressWarnings("SameReturnValue")
    @NonNull
    ErrorCode removeKeyChain(String key) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeKeyChainInternal: key=" + key);
        }

        SharedPreferences sharedPreferences = mContext.getSharedPreferences(TWINLIFE_SECURED_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.remove(key);
        edit.remove(mKeyPrefix + key);
        edit.remove(GCM_PREFIX + key);
        edit.remove(CBC_PREFIX + key);
        edit.apply();
        return ErrorCode.SUCCESS;
    }

    @SuppressLint("ApplySharedPref")
    void removeAllKeyChain() {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeAllKeyChain");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mContext.deleteSharedPreferences(TWINLIFE_SECURED_PREFERENCES);
        } else {
            SharedPreferences sharedPreferences = mContext.getSharedPreferences(TWINLIFE_SECURED_PREFERENCES, Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = sharedPreferences.edit();
            edit.clear();
            edit.commit();
        }

        try {
            final KeyStore keyStore = KeyStore.getInstance(AndroidKeyStore);
            keyStore.load(null);
            try {
                keyStore.deleteEntry(TWINLIFE_SECRET_KEY);
            } catch (KeyStoreException ex) {
                Log.d(LOG_TAG, "Cannot remove key from keystore: " + ex.getMessage());
            }
            try {
                keyStore.deleteEntry(TWINLIFE_GCM_KEY);
            } catch (KeyStoreException ex) {
                Log.d(LOG_TAG, "Cannot remove key from keystore: " + ex.getMessage());
            }

        } catch (Exception exception) {
            Log.d(LOG_TAG, "Cannot remove key from keystore: " + exception.getMessage());
        }
    }

    @Nullable
    private static SecretKey loadOrGenerateGCMKey(@NonNull Twinlife twinlife, @NonNull KeyStore keyStore, int retry) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadOrGenerateGCMKey twinlife=" + twinlife + " keyStore=" + keyStore + " retry=" + retry);
        }

        // Load the GCM key if it exists (no need to verify, we assume it is good).
        try {
            final Entry entry = keyStore.getEntry(TWINLIFE_GCM_KEY, null);
            if (entry instanceof SecretKeyEntry) {
                final SecretKeyEntry secretKeyEntry = (SecretKeyEntry) entry;
                final SecretKey secretKey = secretKeyEntry.getSecretKey();
                if (secretKey != null) {
                    return secretKey;
                }
            }
        } catch (NullPointerException ignored) {
            // Note: a NullPointerException is sometimes raised by keyStore.getEntry() despite our alias that is NEVER null.
            // This is a bug on some OEM firmware.

        } catch (Exception exception) {
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_LOAD_GCM, exception, AssertPoint.createMarker(retry));
        }

        // Generate a GCM key, load it from the keystore and verify by encrypting/decrypting some
        // content that it is functional (some devices have issues with the keystore API).
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore);
            KeyGenParameterSpec.Builder keySpec = new KeyGenParameterSpec.Builder(TWINLIFE_GCM_KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
            keySpec.setBlockModes(KeyProperties.BLOCK_MODE_GCM);
            keySpec.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE);
            keySpec.setKeySize(256);

            keyGenerator.init(keySpec.build());
            SecretKey secretKey = keyGenerator.generateKey();

            final Entry entry = keyStore.getEntry(TWINLIFE_GCM_KEY, null);
            if (entry instanceof SecretKeyEntry) {
                final SecretKeyEntry secretKeyEntry = (SecretKeyEntry) entry;
                secretKey = secretKeyEntry.getSecretKey();
            }

            final boolean result = verifyKey(twinlife, secretKey, true);

            // Something wrong occurred with the key: remove it to get a new one on a next retry.
            if (!result) {
                try {
                    keyStore.deleteEntry(TWINLIFE_GCM_KEY);
                } catch (Exception exception) {
                    Log.e(LOG_TAG, "getSecureKey: exception=" + exception);
                }

                // Android Keystore is sometimes buggy and unreliable, some OEM have issues to generate random numbers
                // pause a few milliseconds and retry (as per some obscure recommendation).
                try {
                    Thread.sleep(500);
                } catch (Exception ignored) {

                }
            }

            return result ? secretKey : null;

        } catch (Exception exception) {
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_CREATE_GCM, exception, AssertPoint.createMarker(retry));
            return null;
        }
    }

    @Nullable
    private static SecretKey loadOrGenerateCBCKey(@NonNull Twinlife twinlife, @NonNull KeyStore keyStore, int retry) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadOrGenerateCBCKey twinlife=" + twinlife + " keyStore=" + keyStore + " retry=" + retry);
        }

        try {
            final Entry entry = keyStore.getEntry(TWINLIFE_SECRET_KEY, null);
            if (entry instanceof SecretKeyEntry) {
                final SecretKeyEntry secretKeyEntry = (SecretKeyEntry) entry;
                final SecretKey secretKey = secretKeyEntry.getSecretKey();
                if (secretKey != null) {
                    return secretKey;
                }
            }
        } catch (NullPointerException ignored) {
            // Note: a NullPointerException is sometimes raised by keyStore.getEntry() despite our alias that is NEVER null.
            // This is a bug on some OEM firmware.

        } catch (Exception exception) {
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_LOAD_CBC, exception, AssertPoint.createMarker(retry));
        }

        // Generate an AES-CBC key, load it from the keystore and verify by encrypting/decrypting some
        // content that it is functional (some devices have issues with the keystore API).
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore);
            KeyGenParameterSpec.Builder keySpec = new KeyGenParameterSpec.Builder(TWINLIFE_SECRET_KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
            keySpec.setBlockModes(KeyProperties.BLOCK_MODE_CBC);
            keySpec.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);
            keySpec.setKeySize(256);

            keyGenerator.init(keySpec.build());
            SecretKey secretKey = keyGenerator.generateKey();

            final Entry entry = keyStore.getEntry(TWINLIFE_SECRET_KEY, null);
            if (entry instanceof SecretKeyEntry) {
                final SecretKeyEntry secretKeyEntry = (SecretKeyEntry) entry;
                secretKey = secretKeyEntry.getSecretKey();
            }

            final boolean result = verifyKey(twinlife, secretKey, false);

            // Something wrong occurred with the key: remove it to get a new one on a next retry.
            if (!result) {
                try {
                    keyStore.deleteEntry(TWINLIFE_SECRET_KEY);
                } catch (Exception exception) {
                    Log.e(LOG_TAG, "getSecureKey: exception=" + exception);
                }

                // Android Keystore is sometimes buggy and unreliable, some OEM have issues to generate random numbers
                // pause a few milliseconds and retry (as per some obscure recommendation).
                try {
                    Thread.sleep(500);
                } catch (Exception ignored) {

                }
            }

            return result ? secretKey : null;

        } catch (Exception exception) {
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_CREATE_CBC, exception, AssertPoint.createMarker(retry));
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("ApplySharedPref")
    @Nullable
    private static SecretKey loadOrGenerateCBCKeyFromRSA(@NonNull Twinlife twinlife, @NonNull Context context, @NonNull SharedPreferences sharedPreferences,
                                                         @NonNull KeyStore keyStore, int retry) {
        if (DEBUG) {
            Log.d(LOG_TAG, "loadOrGenerateCBCKeyFromRSA twinlife=" + twinlife + " keyStore=" + keyStore + " retry=" + retry);
        }

        SecretKey secretKey = getSecureKey(keyStore, sharedPreferences, twinlife);
        if (secretKey != null) {
            return secretKey;
        }

        KeyPairGeneratorSpec.Builder builder = new KeyPairGeneratorSpec.Builder(context);
        builder.setAlias(TWINLIFE_SECRET_KEY);
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.set(Calendar.YEAR, 2049 - retry - 1); // Do not exceed 2049 as exceed DERUTCTime in ASN.1 DER encoding.
        builder.setSubject(new X500Principal("CN=" + TWINLIFE_SECRET_KEY));
        builder.setSerialNumber(BigInteger.TEN).setStartDate(start.getTime()).setEndDate(end.getTime());
        final int keySize;
        switch (retry) {
            case 0:
            case 1:
                keySize = 4096;
                break;
            case 2:
            case 3:
                keySize = 3072;
                break;
            case 4:
            case 5:
                keySize = 2048;
                break;
            default:
                keySize = 1024;
                break;
        }
        builder.setKeySize(keySize);

        SharedPreferences.Editor edit = sharedPreferences.edit();
        try {
            KeyPairGeneratorSpec keyPairGeneratorSpec = builder.build();
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA", AndroidKeyStore);
            keyPairGenerator.initialize(keyPairGeneratorSpec);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            byte[] data = randomBytes(32);
            byte[] encryptedData = rsaEncrypt(keyPair.getPublic(), data);
            if (encryptedData != null) {
                edit.putString(TWINLIFE_SECURED_KEY, Base64.encodeToString(encryptedData, Base64.DEFAULT));
                edit.commit();
            }

            secretKey = getSecureKey(keyStore, sharedPreferences, twinlife);

            final boolean result = verifyKey(twinlife, secretKey, false);

            // Something wrong occurred with the key: remove it to get a new one on a next retry.
            if (!result) {
                try {
                    keyStore.deleteEntry(TWINLIFE_SECRET_KEY);
                } catch (Exception exception) {
                    Log.e(LOG_TAG, "getSecureKey: exception=" + exception);
                }

                // Android Keystore is sometimes buggy and unreliable, some OEM have issues to generate random numbers
                // pause a few milliseconds and retry (as per some obscure recommendation).
                try {
                    Thread.sleep(500);
                } catch (Exception ignored) {

                }
            }

            return result ? secretKey : null;

        } catch (Exception exception) {
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_BAD_JELLY_BEAN, exception, AssertPoint.createMarker(retry).putLength(keySize));
        }
        return null;
    }

    /**
     * Given the secret key that was created, verify by encrypting and decrypting that it is
     * correctly supported.
     * @param twinlife the twinlife instance to report failure
     * @param secretKey the secret key to verify.
     * @param isGCMKey true when using AES/GCM otherwise AES/CBC.
     * @return true if the secret key can be used.
     */
    private static boolean verifyKey(@NonNull Twinlife twinlife, @Nullable SecretKey secretKey, boolean isGCMKey) {
        if (DEBUG) {
            Log.d(LOG_TAG, "verifyKey secretKey=" + secretKey + " isGCMKey=" + isGCMKey);
        }

        if (secretKey == null) {
            return false;
        }
        try {
            final byte[] testData = randomBytes(64);
            final byte[] data = encrypt(secretKey, isGCMKey, testData);
            final byte[] decryptedData = decrypt(secretKey, data, 0, isGCMKey);
            if (decryptedData == null) {
                twinlife.assertion(AndroidAssertPoint.KEYCHAIN_VERIFY_FAILED, AssertPoint.createMarker(isGCMKey ? 1 : 0));
                return false;
            }
            if (!Arrays.equals(testData, decryptedData)) {
                twinlife.assertion(AndroidAssertPoint.KEYCHAIN_VERIFY_FAILED, AssertPoint.createMarker(isGCMKey ? 3 : 2));
                return false;
            }
            return true;

        } catch (Exception exception) {
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_VERIFY, exception, AssertPoint.createMarker(isGCMKey ? 1 : 0));
            return false;
        }
    }

    private static Key getDefaultSecretKey() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDefaultSecretKey");
        }

        SecretKeySpec secretKeySpec;
        int length = UUID1.length;
        byte[] data = new byte[length];
        for (int i = 0; i < length; i++) {
            data[i] = (byte) (UUID1[i] ^ UUID2[length - 1 - i]);
        }

        secretKeySpec = new SecretKeySpec(data, "AES");

        return secretKeySpec;
    }

    private static byte[] rsaEncrypt(Key key, byte[] data) {
        if (DEBUG) {
            Log.d(LOG_TAG, "rsaEncrypt: key=" + key + " data=" + Arrays.toString(data));
        }

        try {
            Cipher cipher = Cipher.getInstance(RSA_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, key);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            CipherOutputStream cipherOutputStream = new CipherOutputStream(outputStream, cipher);
            cipherOutputStream.write(data);
            cipherOutputStream.close();

            return outputStream.toByteArray();
        } catch (Exception exception) {
            Log.e(LOG_TAG, "rsaEncrypt: exception=" + exception);
        }

        return null;
    }

    private static byte[] rsaDecrypt(Key key, byte[] encryptedData) {
        if (DEBUG) {
            Log.d(LOG_TAG, "rsaDecrypt: key=" + key + " encryptedData=" + Arrays.toString(encryptedData));
        }

        try {
            Cipher cipher = Cipher.getInstance(RSA_MODE);
            cipher.init(Cipher.DECRYPT_MODE, key);
            CipherInputStream cipherInputStream = new CipherInputStream(new ByteArrayInputStream(encryptedData), cipher);
            ArrayList<Byte> values = new ArrayList<>();
            int nextByte;
            while ((nextByte = cipherInputStream.read()) != -1) {
                values.add((byte) nextByte);
            }
            cipherInputStream.close();

            byte[] data = new byte[values.size()];
            for (int i = 0; i < data.length; i++) {
                data[i] = values.get(i);
            }

            return data;
        } catch (Exception exception) {
            Log.e(LOG_TAG, "rsaDecrypt: exception=" + exception);
        }

        return null;
    }

    @Nullable
    private byte[] encrypt(@NonNull byte[] data) {
        if (DEBUG) {
            Log.d(LOG_TAG, "encrypt: data=" + Arrays.toString(data));
        }

        // If we failed to obtain a correct secure key, we can't encrypt and report some error.
        if (mSecuredKey == null) {
            return null;
        }
        try {
            return encrypt(mSecuredKey, mIsGCMKey, data);

        } catch (Exception exception) {
            mTwinlife.exception(AndroidAssertPoint.KEYCHAIN_ENCRYPT, exception, AssertPoint.createMarker(mIsGCMKey ? 1 : 0));

            // Record the GCM encryption error.
            if (mIsGCMKey) {
                mGCMError = true;

                SharedPreferences sharedPreferences = mContext.getSharedPreferences(TWINLIFE_SECURED_PREFERENCES, Context.MODE_PRIVATE);
                markGCMError(mTwinlife, sharedPreferences);
            }
        }
        return null;
    }

    private static void markGCMError(@NonNull Twinlife twinlife, @NonNull SharedPreferences sharedPreferences) {
        if (DEBUG) {
            Log.d(LOG_TAG, "markGCMError: twinlife=" + twinlife + " sharedPreferences=" + sharedPreferences);
        }

        final SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.putBoolean(TWINLIFE_BAD_GCM, true);
        edit.apply();

        twinlife.assertion(AndroidAssertPoint.KEYCHAIN_GCM_BROKEN, null);
    }

    @NonNull
    private static byte[] encrypt(@NonNull Key secureKey, boolean isGCMKey, @NonNull byte[] data) throws Exception {
        if (DEBUG) {
            Log.d(LOG_TAG, "encrypt: data=" + Arrays.toString(data));
        }

        final Cipher cipher = Cipher.getInstance(isGCMKey ? AES_GCM : AES_MODE);
        cipher.init(Cipher.ENCRYPT_MODE, secureKey);
        final byte[] encryptedData = cipher.doFinal(data);
        byte[] iv = cipher.getIV();
        final byte[] ivEncryptedData = new byte[iv.length + encryptedData.length];
        System.arraycopy(iv, 0, ivEncryptedData, 0, iv.length);
        System.arraycopy(encryptedData, 0, ivEncryptedData, iv.length, encryptedData.length);
        return ivEncryptedData;
    }

    @Nullable
    private static byte[] decrypt(@NonNull Key securedKey, @NonNull byte[] ivEncryptedData, int length, boolean useGCM) {
        if (DEBUG) {
            Log.d(LOG_TAG, "decrypt: ivEncryptedData=" + Arrays.toString(ivEncryptedData));
        }

        if (length <= 0) {
            length = ivEncryptedData.length;
        }
        try {
            final Cipher cipher;
            final byte[] iv;
            if (useGCM) {
                iv = new byte[IV_GCM_SIZE];
                System.arraycopy(ivEncryptedData, 0, iv, 0, IV_GCM_SIZE);
                cipher = Cipher.getInstance(AES_GCM);
                GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
                cipher.init(Cipher.DECRYPT_MODE, securedKey, spec);
            } else {
                iv = new byte[IV_LENGTH_BYTES];
                System.arraycopy(ivEncryptedData, 0, iv, 0, IV_LENGTH_BYTES);
                cipher = Cipher.getInstance(AES_MODE);
                cipher.init(Cipher.DECRYPT_MODE, securedKey, new IvParameterSpec(iv));
            }

            final byte[] encryptedData = new byte[length - iv.length];
            System.arraycopy(ivEncryptedData, iv.length, encryptedData, 0, encryptedData.length);
            return cipher.doFinal(encryptedData);
        } catch (Exception exception) {
            Log.e(LOG_TAG, "decrypt: exception=" + exception);
        }
        return null;
    }

    @NonNull
    private static byte[] randomBytes(int length) {
        if (DEBUG) {
            Log.d(LOG_TAG, "randomBytes: length=" + length);
        }

        SecureRandom random = new SecureRandom();
        byte[] b = new byte[length];
        random.nextBytes(b);

        return b;
    }
}
