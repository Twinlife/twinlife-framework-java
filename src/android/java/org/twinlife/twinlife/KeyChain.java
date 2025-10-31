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
 * - starting with Android 6.0 (M), we could use the new keystore API and the encryption uses AES-CBC with
 *   a 256-bit key.  Back in 2018, some Android implementation were broken and we had to sometimes fallback
 *   in using the RSA key.
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
    private static final String AES_MODE = "AES/CBC/PKCS7Padding";
    private static final String RSA_MODE = "RSA/ECB/PKCS1Padding";
    private static final String TWINLIFE_SECURED_PREFERENCES = "TwinlifeSecuredPreferences";
    private static final String TWINLIFE_SECURED_KEY = "TwinlifeSecuredKey";
    private static final String TWINLIFE_BAD_JELLY_BEAN = "TwinlifeBadJellyBean";
    private static final int IV_LENGTH_BYTES = 16;
    private static final byte[] UUID1 = {-112, -102, 4, -13, 88, 2, 69, -13, -77, 50, -83, 81, 22, 76, -14, -89};
    private static final byte[] UUID2 = {1, -115, -24, -96, 27, -27, 74, -49, -74, -34, 90, 106, 102, 103, 8, 126};

    @NonNull
    private final Context mContext;
    @NonNull
    private final Key mSecuredKey;
    @NonNull
    private final String mKeyPrefix;
    private final boolean mCreated;
    private final boolean mIsDefaultSecretKey;
    private final Twinlife mTwinlife;

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
        boolean created = false;
        try {
            final KeyStore keyStore = KeyStore.getInstance(AndroidKeyStore);
            keyStore.load(null);

            securedKey = getSecureKey(keyStore, sharedPreferences, twinlife);
            if (securedKey == null) {
                // Generate a secure key: the secure key must be inserted in the AndroidKeyStore.
                // We then retry getting the secure key to make sure we can extract it from the keystore and use it.
                // If this fails, we try another method until all possible methods have been checked.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    generateSecuredKeyM();
                    securedKey = getSecureKey(keyStore, sharedPreferences, twinlife);
                }

                // Android 5.x up to Android 6.0 if there are issues with generateSecureKeyM().
                if (securedKey == null && Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                    generateSecuredKeyJellyBeanMR2(context, sharedPreferences);
                    securedKey = getSecureKey(keyStore, sharedPreferences, twinlife);
                }
                created = securedKey != null;
            }

        } catch (Exception exception) {
            Log.e(LOG_TAG, "KeyChain exception", exception);

            mTwinlife.exception(AndroidAssertPoint.KEYCHAIN, exception, null);
        }

        mIsDefaultSecretKey = securedKey == null;
        if (mIsDefaultSecretKey) {
            mTwinlife.assertion(AndroidAssertPoint.KEYCHAIN_USE_DEFAULT, null);
            securedKey = getDefaultSecretKey();
        }
        mSecuredKey = securedKey;
        mCreated = created;

        // To migrate safely from the legacy storage with the default secret key and use
        // a key from the Android Keystore, the entries are prefixed by 'ks.':
        // - the 'ks.TwinlifeSecuredConfiguration' is encrypted by the Android keystore key,
        // - the 'TwinlifeSecuredConfiguration' if it exists, is encrypted by the default secret key
        //   when `LEGACY_NO_KEYSTORE` is true, but it is encrypted with the Android keystore key
        //   for others (Skred).
        mKeyPrefix = BuildConfig.LEGACY_NO_KEYSTORE ? "ks." : "";
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

                // Something wrong occurred with the key: remove it to get a new one.
                try {
                    keyStore.deleteEntry(TWINLIFE_SECRET_KEY);
                } catch (Exception lException) {
                    Log.e(LOG_TAG, "getSecureKey: exception=" + lException);
                }
            }
            return null;

        } catch (Exception exception) {
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
            if (!BuildConfig.LEGACY_NO_KEYSTORE) {
                return null;
            }

            // If the old entry that was encrypted by using the default secret key exists, use it and
            // save it with the new encryption key defined in the Android keystore.
            securedData = sharedPreferences.getString(key, null);
            if (securedData == null) {
                return null;
            }

            byte[] data = decrypt(getDefaultSecretKey(), Base64.decode(securedData, Base64.DEFAULT), 0);
            if (data == null) {
                mTwinlife.assertion(AndroidAssertPoint.KEYCHAIN_DECRYPT, null);
                return null;
            }
            if (!mIsDefaultSecretKey) {
                updateKeyChain(key, data);
            }
            return data;
        }

        return decrypt(mSecuredKey, Base64.decode(securedData, Base64.DEFAULT), 0);
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

        } catch (Exception exception) {
            Log.d(LOG_TAG, "Cannot remove key from keystore: " + exception.getMessage());
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static void generateSecuredKeyM() {
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
        }
    }

    @SuppressLint("ApplySharedPref")
    private static void generateSecuredKeyJellyBeanMR2(@NonNull Context context, @NonNull SharedPreferences sharedPreferences) {
        if (DEBUG) {
            Log.d(LOG_TAG, "generateSecuredKeyJellyBeanMR2");
        }

        if (sharedPreferences.getBoolean(TWINLIFE_BAD_JELLY_BEAN, false)) {
            return;
        }

        KeyPairGeneratorSpec.Builder builder = new KeyPairGeneratorSpec.Builder(context);
        builder.setAlias(TWINLIFE_SECRET_KEY);
        Calendar start = Calendar.getInstance();
        Calendar end = Calendar.getInstance();
        end.add(Calendar.YEAR, 30);
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
                edit.commit();
                return;
            }

        } catch (Exception exception) {
            Log.e(LOG_TAG, "generateSecuredKeyJellyBeanMR2: exception=" + exception);
        }

        // Cleanup when something is wrong and mark it as a failed environment.
        // It seems that some Samsung devices running Android 4.3..Android 5.1 are having problems on their keystore.
        // Other devices don't have the issue.  Mark the fact that we failed.
        edit.putBoolean(TWINLIFE_BAD_JELLY_BEAN, true);
        edit.remove(TWINLIFE_SECURED_KEY);
        edit.commit();
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
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.ENCRYPT_MODE, mSecuredKey);
            byte[] iv = cipher.getIV();
            byte[] encryptedData = cipher.doFinal(data);
            byte[] ivEncryptedData = new byte[iv.length + encryptedData.length];
            System.arraycopy(iv, 0, ivEncryptedData, 0, IV_LENGTH_BYTES);
            System.arraycopy(encryptedData, 0, ivEncryptedData, IV_LENGTH_BYTES, encryptedData.length);
            return ivEncryptedData;
        } catch (Exception exception) {
            Log.e(LOG_TAG, "encrypt: exception=" + exception);

            mTwinlife.exception(AndroidAssertPoint.KEYCHAIN_ENCRYPT, exception, null);
        }

        return null;
    }

    @Nullable
    static byte[] decrypt(@NonNull Key securedKey, @NonNull byte[] ivEncryptedData, int length) {
        if (DEBUG) {
            Log.d(LOG_TAG, "decrypt: ivEncryptedData=" + Arrays.toString(ivEncryptedData));
        }

        if (length <= 0) {
            length = ivEncryptedData.length;
        }
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            System.arraycopy(ivEncryptedData, 0, iv, 0, IV_LENGTH_BYTES);
            byte[] encryptedData = new byte[length - IV_LENGTH_BYTES];
            System.arraycopy(ivEncryptedData, IV_LENGTH_BYTES, encryptedData, 0, encryptedData.length);
            Cipher cipher = Cipher.getInstance(AES_MODE);
            cipher.init(Cipher.DECRYPT_MODE, securedKey, new IvParameterSpec(iv));

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
