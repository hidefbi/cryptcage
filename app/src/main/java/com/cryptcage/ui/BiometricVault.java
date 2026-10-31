/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyPermanentlyInvalidatedException;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.RequiresApi;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import com.cryptcage.R;

/**
 * Unlocks a database with the fingerprint or face the device already has.
 *
 * Each database that uses it keeps its own copy of the master password. The copy is encrypted
 * with a Keystore key that needs a fresh biometric check for every use, and the database name is
 * bound to it. Enrolling a new fingerprint invalidates the key on Android 7 and later, so the
 * copies stop working and the app falls back to the password.
 *
 * Needs Android 6.0 or later and a device with strong biometrics.
 */
public final class BiometricVault {

    /** Outcome of a biometric prompt. */
    public interface Listener {
        /** The prompt passed. The cipher is ready for one use. */
        void onAuthenticated(Cipher cipher);

        /** The prompt was cancelled, failed, or the key is gone. */
        void onFailed();
    }

    private static final String PREFS = "biometric_lock";
    private static final String KEY_ENABLED = "enabled";
    private static final String CREDENTIAL_PREFIX = "bio_";
    private static final String ALIAS = "cryptcage_bio_wrap";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private BiometricVault() {
    }

    /** True when strong biometrics are enrolled and the OS is new enough. */
    public static boolean canUse(final Context context) {
        return unavailableReason(context) == 0;
    }

    /** The string that says why biometric unlock cannot be used on this device, or 0 when it can. */
    public static int unavailableReason(final Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return R.string.bio_reason_os;
        }
        final BiometricManager manager = BiometricManager.from(context);
        final int strong = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        if (strong == BiometricManager.BIOMETRIC_SUCCESS) {
            return 0;
        }
        if (strong == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            return R.string.bio_reason_none_enrolled;
        }
        final int weak = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        if (weak == BiometricManager.BIOMETRIC_SUCCESS) {
            return R.string.bio_reason_weak_only;
        }
        return R.string.bio_reason_no_hardware;
    }

    /**
     * Opens the system page for adding a fingerprint, and falls back to the security settings when
     * the device does not have that page. The user does it themselves; nothing is forced.
     */
    public static void openEnrollment(final Context context) {
        final Intent enroll = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            enroll.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                    BiometricManager.Authenticators.BIOMETRIC_STRONG);
        }
        try {
            context.startActivity(enroll);
        } catch (ActivityNotFoundException e) {
            context.startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        }
    }

    public static boolean isEnabled(final Context context) {
        return prefs(context).getBoolean(KEY_ENABLED, false);
    }

    /** True when this database has a saved copy of its password. */
    public static boolean hasCredential(final Context context, final String vault) {
        return isEnabled(context) && prefs(context).contains(credentialKey(vault));
    }

    /**
     * A cipher for saving a password. The first use creates the Keystore key. If the key was
     * invalidated by a new enrolment, it is replaced.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static Cipher encryptCipher() throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
        } catch (KeyPermanentlyInvalidatedException e) {
            deleteKey();
            cipher.init(Cipher.ENCRYPT_MODE, key(true));
        }
        return cipher;
    }

    /** A cipher for reading the saved password of one database. It still needs the biometric check. */
    @RequiresApi(Build.VERSION_CODES.M)
    public static Cipher decryptCipher(final Context context, final String vault) throws GeneralSecurityException {
        final byte[] blob = decode(prefs(context).getString(credentialKey(vault), null));
        if (blob == null || blob.length <= IV_BYTES) {
            throw new GeneralSecurityException("no saved password");
        }
        final byte[] iv = Arrays.copyOfRange(blob, 0, IV_BYTES);
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        try {
            cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(TAG_BITS, iv));
        } catch (KeyPermanentlyInvalidatedException e) {
            disable(context);
            throw new GeneralSecurityException(e);
        }
        return cipher;
    }

    /** Saves the password with a cipher that has passed the biometric check. */
    @RequiresApi(Build.VERSION_CODES.M)
    public static void saveCredential(final Context context, final String vault, final Cipher authorized,
                                      final char[] password) throws GeneralSecurityException {
        final byte[] plain = new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            authorized.updateAAD(vault.getBytes(StandardCharsets.UTF_8));
            final byte[] body = authorized.doFinal(plain);
            final byte[] iv = authorized.getIV();
            final byte[] blob = new byte[iv.length + body.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(body, 0, blob, iv.length, body.length);
            prefs(context).edit()
                    .putString(credentialKey(vault), encode(blob))
                    .putBoolean(KEY_ENABLED, true)
                    .apply();
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    /** Reads the saved password with a cipher that has passed the biometric check. */
    @RequiresApi(Build.VERSION_CODES.M)
    public static char[] loadCredential(final Context context, final String vault, final Cipher authorized)
            throws GeneralSecurityException {
        final byte[] blob = decode(prefs(context).getString(credentialKey(vault), null));
        if (blob == null || blob.length <= IV_BYTES) {
            throw new GeneralSecurityException("no saved password");
        }
        authorized.updateAAD(vault.getBytes(StandardCharsets.UTF_8));
        final byte[] plain = authorized.doFinal(Arrays.copyOfRange(blob, IV_BYTES, blob.length));
        try {
            return new String(plain, StandardCharsets.UTF_8).toCharArray();
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    /** Deletes the saved password of one database, for example when the database is deleted. */
    public static void removeCredential(final Context context, final String vault) {
        prefs(context).edit().remove(credentialKey(vault)).apply();
    }

    /** Turns biometric unlock off: removes every saved copy, the flag and the Keystore key. */
    public static void disable(final Context context) {
        prefs(context).edit().clear().apply();
        deleteKey();
    }

    /** Shows the system prompt for the cipher. The listener is called on the main thread. */
    @RequiresApi(Build.VERSION_CODES.M)
    public static void authenticate(final FragmentActivity activity, final Cipher cipher, final int title,
                                    final Listener listener) {
        final BiometricPrompt prompt = new BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(final BiometricPrompt.AuthenticationResult result) {
                        listener.onAuthenticated(result.getCryptoObject().getCipher());
                    }

                    @Override
                    public void onAuthenticationError(final int errorCode, final CharSequence message) {
                        listener.onFailed();
                    }
                });
        final BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(activity.getString(title))
                .setNegativeButtonText(activity.getString(R.string.bio_use_password))
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build();
        prompt.authenticate(info, new BiometricPrompt.CryptoObject(cipher));
    }

    private static String credentialKey(final String vault) {
        return CREDENTIAL_PREFIX + vault;
    }

    private static SharedPreferences prefs(final Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String encode(final byte[] bytes) {
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    private static byte[] decode(final String text) {
        return text == null ? null : Base64.decode(text, Base64.NO_WRAP);
    }

    /** The key for this feature. It is created when missing and only if create is true. */
    @RequiresApi(Build.VERSION_CODES.M)
    private static SecretKey key(final boolean create) throws GeneralSecurityException {
        try {
            final KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            final Key existing = store.getKey(ALIAS, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
            if (!create) {
                throw new GeneralSecurityException("no key");
            }
            final KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setUserAuthenticationRequired(true);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                spec.setInvalidatedByBiometricEnrollment(true);
            }
            final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(spec.build());
            return generator.generateKey();
        } catch (java.io.IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    private static void deleteKey() {
        try {
            final KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            store.deleteEntry(ALIAS);
        } catch (GeneralSecurityException | java.io.IOException e) {
            // Already gone, or the store is unavailable. Nothing else to clean up.
        }
    }
}
