/*
 *  Copyright (c) 2017-2025 twinlife SA.
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
import androidx.annotation.RequiresApi;

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
import java.security.SecureRandom;
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
 *   a 256-bit key.  Back in 2018, some Android implementation were broken and we had to sometimes fallback
 *   in using the RSA key.
 * - we changed the encryption from AES/CBC to AES/GCM to detect tampering (mostly due to bugs and not attacks)
 *   Due to this, we handle the migration of encryption but because Android Keystore is buggy we also have to
 *   fallback to previous encryption mechanisms.
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
    private static final String TWINLIFE_BAD_JELLY_BEAN = "TwinlifeBadJellyBean";
    private static final String TWINLIFE_BAD_JELLY_BEAN2 = "TwinlifeBadJellyBean2";
    private static final int IV_LENGTH_BYTES = 16;
    private static final byte[] UUID1 = {-112, -102, 4, -13, 88, 2, 69, -13, -77, 50, -83, 81, 22, 76, -14, -89};
    private static final byte[] UUID2 = {1, -115, -24, -96, 27, -27, 74, -49, -74, -34, 90, 106, 102, 103, 8, 126};
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_GCM_SIZE = 12; // bytes (recommended for GCM)

    @NonNull
    private final Context mContext;
    @NonNull
    private final Key mSecuredKey;
    @Nullable
    private final Key mOldSecuredKey;
    @NonNull
    private final String mKeyPrefix;
    private final boolean mCreated;
    private final boolean mIsDefaultSecretKey;
    private final Twinlife mTwinlife;
    private final boolean mIsGCMKey;

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

        Key securedKey = null;
        Key oldSecuredKey = null;
        boolean created = false;
        boolean hasOldKey;
        boolean hasGCMKey = false;
        boolean badJellyBean = false;
        try {
            final KeyStore keyStore = KeyStore.getInstance(AndroidKeyStore);
            keyStore.load(null);

            // Get or create the AES-GCM encryption key.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                for (int retry = 0; retry < 5 && securedKey == null; retry++) {
                    hasGCMKey = keyStore.containsAlias(TWINLIFE_GCM_KEY);

                    // Generate a secure key: the secure key must be inserted in the AndroidKeyStore.
                    // We then retry getting the secure key to make sure we can extract it from the keystore and use it.
                    // If this fails, we try another method until all possible methods have been checked.
                    if (!hasGCMKey) {
                        hasGCMKey = generateGCMKeyM(twinlife);
                        created = hasGCMKey;
                    }
                    try {
                        final Entry entry = keyStore.getEntry(TWINLIFE_GCM_KEY, null);
                        if (entry instanceof SecretKeyEntry) {
                            final SecretKeyEntry secretKeyEntry = (SecretKeyEntry) entry;
                            securedKey = secretKeyEntry.getSecretKey();
                            hasGCMKey = securedKey != null;
                        }

                    } catch (Exception exception) {
                        // Note: a NullPointerException is sometimes raised by keyStore.getEntry() despite our alias that is NEVER null.
                        // This is a bug in some OEM firmware.
                        Log.e(LOG_TAG, "getSecureKey: exception=" + exception);
                        twinlife.exception(AndroidAssertPoint.KEYCHAIN, exception, AssertPoint.createMarker(retry));
                    }

                    // Something wrong occurred with the key: remove it to get a new one.
                    if (securedKey == null) {
                        try {
                            keyStore.deleteEntry(TWINLIFE_GCM_KEY);
                        } catch (Exception exception) {
                            Log.e(LOG_TAG, "getSecureKey: exception=" + exception);
                            twinlife.exception(AndroidAssertPoint.KEYCHAIN_REMOVE, exception, AssertPoint.createMarker(retry));
                        }

                        // Android Keystore is sometimes buggy and unreliable, some OEM have issues to generate random numbers
                        // pause a few milliseconds and retry (as per some obscure recommendation).
                        try {
                            Thread.sleep(500);
                        } catch (Exception ignored) {

                        }
                    }
                }
            }

            // Get the old key if it exists, we will use the old AES/CBC encryption mode.
            hasOldKey = keyStore.containsAlias(TWINLIFE_SECRET_KEY);
            if (hasOldKey) {
                oldSecuredKey = getSecureKey(keyStore, sharedPreferences, twinlife);
            }

            // Android 5.x up to Android 6.0 if there are issues with generateSecureKeyM().
            if (securedKey == null && oldSecuredKey == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                badJellyBean = generateSecuredKeyJellyBeanMR2(context, sharedPreferences, twinlife);
                oldSecuredKey = getSecureKey(keyStore, sharedPreferences, twinlife);
                created = oldSecuredKey != null;
            }

            // If the GCM encryption key does not exist, use the old key.
            if (securedKey == null && oldSecuredKey != null) {
                securedKey = oldSecuredKey;
                oldSecuredKey = null;
                hasGCMKey = false;
            }
        } catch (Exception exception) {
            Log.e(LOG_TAG, "KeyChain exception", exception);

            mTwinlife.exception(AndroidAssertPoint.KEYCHAIN, exception, null);
        }

        mIsDefaultSecretKey = securedKey == null;
        if (mIsDefaultSecretKey) {
            if (!badJellyBean) {
                mTwinlife.assertion(AndroidAssertPoint.KEYCHAIN_USE_DEFAULT, null);
            }
            securedKey = getDefaultSecretKey();
            hasGCMKey = false;
        }
        mIsGCMKey = hasGCMKey;
        mSecuredKey = securedKey;
        mOldSecuredKey = oldSecuredKey;
        mCreated = created;

        // To migrate safely from the legacy storage with the default secret key and use
        // a key from the Android Keystore, the entries are prefixed by 'gs.':
        // - the 'gs.TwinlifeSecuredConfiguration' is encrypted by the Android keystore key using AES/GCM,
        // - the 'ks.TwinlifeSecuredConfiguration' is encrypted by the Android keystore key using AES/CBC/PKCS7Padding,
        // - the 'TwinlifeSecuredConfiguration' if it exists, is encrypted by the default secret key
        //   when `LEGACY_NO_KEYSTORE` is true, but it is encrypted with the Android keystore key
        //   for others (Skred).  Encryption mode is AES/CBC/PKCS7Padding.
        // - if we failed to create the Keychain key and we fallback to using the default secret key
        //   continue using the old value without the 'gs.' prefix.  On some devices, the Keystore is
        //   buggy and some NullPointerException is raised by the getEntry().  Occurrence found:
        //   72.3%	android.5.x
        //   26.0%	android.13
        //   2.2%	android.12
        mKeyPrefix = mIsGCMKey ? "gs." : BuildConfig.LEGACY_NO_KEYSTORE && !mIsDefaultSecretKey ? "ks." : "";
    }

    @Nullable
    private static Key getSecureKey(@NonNull KeyStore keyStore, @NonNull SharedPreferences sharedPreferences, @NonNull Twinlife twinlife) {
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

                    final String securedData = sharedPreferences.getString(TWINLIFE_SECURED_KEY, null);

                    if (securedData != null) {
                        final byte[] encryptedData = Base64.decode(securedData, Base64.DEFAULT);
                        final byte[] data = rsaDecrypt(privateKeyEntry.getPrivateKey(), encryptedData);
                        if (data != null) {
                            return new SecretKeySpec(data, "AES");
                        }
                    }
                }
            }

        } catch (Exception exception) {
            // Note: a NullPointerException is sometimes raised by keyStore.getEntry() despite our alias that is NEVER null.
            // This is a bug on some OEM firmware.
            Log.e(LOG_TAG, "getSecureKey: exception=" + exception);
            twinlife.exception(AndroidAssertPoint.KEYCHAIN, exception, null);
        }
        return null;
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
                securedData = sharedPreferences.getString("ks." + key, null);
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

            byte[] data = decrypt(decryptKey, Base64.decode(securedData, Base64.DEFAULT), 0, false);
            if (BuildConfig.LEGACY_NO_KEYSTORE && data == null) {
                securedData = sharedPreferences.getString(key, null);
                data = decrypt(getDefaultSecretKey(), Base64.decode(securedData, Base64.DEFAULT), 0, false);
            }
            if (data == null) {
                mTwinlife.assertion(AndroidAssertPoint.KEYCHAIN_DECRYPT, AssertPoint.createMarker(1));
                return null;
            }
            updateKeyChain(key, data);
            return data;
        }

        final byte[] data = decrypt(mSecuredKey, Base64.decode(securedData, Base64.DEFAULT), 0, mIsGCMKey);
        if (data == null) {
            mTwinlife.assertion(AndroidAssertPoint.KEYCHAIN_DECRYPT, AssertPoint.createMarker(2));
        }
        return data;
    }

    @SuppressLint("ApplySharedPref")
    boolean createKeyChain(String key, byte[] data) {
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

            return true;
        }

        return false;
    }

    @SuppressLint("ApplySharedPref")
    boolean updateKeyChain(String key, byte[] data) {
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

            return true;
        }

        return false;
    }

    @SuppressLint("ApplySharedPref")
    void removeKeyChain(String key) {
        if (DEBUG) {
            Log.d(LOG_TAG, "removeKeyChainInternal: key=" + key);
        }

        SharedPreferences sharedPreferences = mContext.getSharedPreferences(TWINLIFE_SECURED_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor edit = sharedPreferences.edit();
        edit.remove(key);
        edit.remove(mKeyPrefix + key);
        edit.commit();
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

    @RequiresApi(Build.VERSION_CODES.M)
    private static boolean generateGCMKeyM(@NonNull Twinlife twinlife) {
        if (DEBUG) {
            Log.d(LOG_TAG, "generateGCMKeyM");
        }

        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore);
            KeyGenParameterSpec.Builder keySpec = new KeyGenParameterSpec.Builder(TWINLIFE_GCM_KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
            keySpec.setBlockModes(KeyProperties.BLOCK_MODE_GCM);
            keySpec.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE);
            keySpec.setKeySize(256);

            keyGenerator.init(keySpec.build());
            SecretKey secretKey = keyGenerator.generateKey();
            return secretKey != null;

        } catch (Exception exception) {
            Log.e(LOG_TAG, "generateSecuredKeyM: exception=" + exception);
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_CREATE_GCM, exception, null);
            return false;
        }
    }

    /* @RequiresApi(Build.VERSION_CODES.M)
    private static void generateSecuredKeyM(@NonNull Twinlife twinlife) {
        if (DEBUG) {
            Log.d(LOG_TAG, "generateSecuredKeyM");
        }

        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore);
            KeyGenParameterSpec.Builder keySpec = new KeyGenParameterSpec.Builder(TWINLIFE_SECRET_KEY, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT);
            keySpec.setBlockModes(KeyProperties.BLOCK_MODE_CBC);
            keySpec.setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7);
            keySpec.setKeySize(256);

            keyGenerator.init(keySpec.build());
            keyGenerator.generateKey();
        } catch (Exception exception) {
            Log.e(LOG_TAG, "generateSecuredKeyM: exception=" + exception);
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_CREATE, exception, null);
        }
    }*/

    @SuppressLint("ApplySharedPref")
    private static boolean generateSecuredKeyJellyBeanMR2(@NonNull Context context, @NonNull SharedPreferences sharedPreferences, @NonNull Twinlife twinlife) {
        if (DEBUG) {
            Log.d(LOG_TAG, "generateSecuredKeyJellyBeanMR2");
        }

        if (sharedPreferences.getBoolean(TWINLIFE_BAD_JELLY_BEAN2, false)) {
            return false;
        }

        KeyPairGeneratorSpec.Builder builder = new KeyPairGeneratorSpec.Builder(context);
        builder.setAlias(TWINLIFE_SECRET_KEY);
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.set(Calendar.YEAR, 2049); // Do not exceed 2049 as exceed DERUTCTime in ASN.1 DER encoding.
        builder.setSubject(new X500Principal("CN=" + TWINLIFE_SECRET_KEY));
        builder.setSerialNumber(BigInteger.TEN).setStartDate(start.getTime()).setEndDate(end.getTime());
        builder.setKeySize(4096);

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
                edit.remove(TWINLIFE_BAD_JELLY_BEAN);
                edit.remove(TWINLIFE_BAD_JELLY_BEAN2);
                edit.commit();
                return true;
            }

        } catch (Exception exception) {
            Log.e(LOG_TAG, "generateSecuredKeyJellyBeanMR2: exception=" + exception);
            twinlife.exception(AndroidAssertPoint.KEYCHAIN_BAD_JELLY_BEAN, exception, null);
        }

        // Cleanup when something is wrong and mark it as a failed environment.
        // It seems that some Samsung devices running Android 4.3..Android 5.1 are having problems on their keystore.
        // Other devices don't have the issue.  Mark the fact that we failed.
        edit.putBoolean(TWINLIFE_BAD_JELLY_BEAN2, true);
        edit.remove(TWINLIFE_SECURED_KEY);
        edit.commit();
        return false;
    }

    static Key getDefaultSecretKey() {
        if (DEBUG) {
            Log.d(LOG_TAG, "getDefaultSecretKey");
        }

        SecretKeySpec secretKeySpec;
        int length = Math.min(UUID1.length, UUID2.length);
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

        try {
            final Cipher cipher = Cipher.getInstance(mIsGCMKey ? AES_GCM : AES_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, mSecuredKey);
            final byte[] encryptedData = cipher.doFinal(data);
            byte[] iv = cipher.getIV();
            final byte[] ivEncryptedData = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, ivEncryptedData, 0, iv.length);
            System.arraycopy(encryptedData, 0, ivEncryptedData, iv.length, encryptedData.length);
            return ivEncryptedData;

        } catch (Exception exception) {
            Log.e(LOG_TAG, "encrypt: exception=" + exception);

            mTwinlife.exception(AndroidAssertPoint.KEYCHAIN_ENCRYPT, exception, null);
        }
        return null;
    }

    @Nullable
    static byte[] decrypt(@NonNull Key securedKey, @NonNull byte[] ivEncryptedData, int length, boolean useGCM) {
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
