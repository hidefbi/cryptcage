/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import com.cryptcage.utils.PinRules;

/**
 * The PIN and the master passwords it can unlock.
 *
 * One PIN is shared by all vaults. Each vault that uses it has its own copy of the master
 * password, wrapped with a key that lives in the Android Keystore and cannot be exported. The
 * vault name is bound to that copy, so a copy cannot be moved to another vault. The PIN is
 * checked against a salted hash; the password copies are only opened after the PIN is right.
 *
 * PIN unlock needs Android 6.0 or later. On older versions {@link #supported()} is false and
 * the app asks for the master password as before.
 */
public final class PinStore {

    /** Result of checking a PIN. */
    public enum Check { OK, WRONG, WIPED, NOT_SET }

    private static final String PREFS = "pin_lock";
    private static final String KEY_SALT = "salt";
    private static final String KEY_HASH = "hash";
    private static final String KEY_FAILURES = "failures";
    private static final String CREDENTIAL_PREFIX = "cred_";
    private static final String ALIAS = "cryptcage_pin_wrap";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int SALT_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** A password held while the PIN dialog is open. Only one can be held at a time. */
    private static char[] heldPassword;

    private PinStore() {
    }

    public static boolean supported() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M;
    }

    public static boolean isEnabled(final Context context) {
        return prefs(context).contains(KEY_HASH);
    }

    /** True when this vault has a saved copy of its password. */
    public static boolean hasCredential(final Context context, final String vault) {
        return isEnabled(context) && prefs(context).contains(credentialKey(vault));
    }

    public static int failures(final Context context) {
        return prefs(context).getInt(KEY_FAILURES, 0);
    }

    /** Sets the PIN. The caller has checked it with {@link PinRules#isValid(String)}. */
    @RequiresApi(Build.VERSION_CODES.M)
    public static void enable(final Context context, final char[] pin) throws GeneralSecurityException {
        final byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        final byte[] hash = PinRules.hash(pin, salt);
        key();
        prefs(context).edit()
                .putString(KEY_SALT, encode(salt))
                .putString(KEY_HASH, encode(hash))
                .putInt(KEY_FAILURES, 0)
                .apply();
    }

    /**
     * Checks the PIN. A wrong PIN counts as a failure. When the failures reach the limit, the PIN
     * and every saved password copy are removed. The databases are not touched.
     */
    public static Check check(final Context context, final char[] pin) throws GeneralSecurityException {
        final SharedPreferences prefs = prefs(context);
        final byte[] salt = decode(prefs.getString(KEY_SALT, null));
        final byte[] stored = decode(prefs.getString(KEY_HASH, null));
        if (salt == null || stored == null) {
            return Check.NOT_SET;
        }
        if (PinRules.matches(stored, PinRules.hash(pin, salt))) {
            prefs.edit().putInt(KEY_FAILURES, 0).apply();
            return Check.OK;
        }
        final int failures = prefs.getInt(KEY_FAILURES, 0) + 1;
        if (PinRules.shouldWipe(failures)) {
            disable(context);
            return Check.WIPED;
        }
        prefs.edit().putInt(KEY_FAILURES, failures).apply();
        return Check.WRONG;
    }

    public static int attemptsLeft(final Context context) {
        return PinRules.attemptsLeft(failures(context));
    }

    /** Saves a copy of the vault's master password. Call only after the PIN has been checked. */
    @RequiresApi(Build.VERSION_CODES.M)
    public static void saveCredential(final Context context, final String vault, final char[] password)
            throws GeneralSecurityException {
        final byte[] plain = new String(password).getBytes(StandardCharsets.UTF_8);
        try {
            prefs(context).edit().putString(credentialKey(vault), encode(wrap(plain, vault))).apply();
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
    }

    /** Returns the saved password of the vault, or null when there is none or it cannot be read. */
    @RequiresApi(Build.VERSION_CODES.M)
    public static char[] loadCredential(final Context context, final String vault) {
        final byte[] blob = decode(prefs(context).getString(credentialKey(vault), null));
        if (blob == null) {
            return null;
        }
        try {
            final byte[] plain = unwrap(blob, vault);
            try {
                return new String(plain, StandardCharsets.UTF_8).toCharArray();
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            return null;
        }
    }

    /** Deletes the saved password of one vault, for example when the vault is deleted. */
    public static void removeCredential(final Context context, final String vault) {
        prefs(context).edit().remove(credentialKey(vault)).apply();
    }

    /** Turns PIN unlock off: removes the PIN, every saved password copy and the Keystore key. */
    public static void disable(final Context context) {
        prefs(context).edit().clear().apply();
        try {
            if (supported()) {
                final KeyStore store = KeyStore.getInstance(KEYSTORE);
                store.load(null);
                store.deleteEntry(ALIAS);
            }
        } catch (GeneralSecurityException | IOException e) {
            // The entry is gone or the store is unavailable. Without the PIN hash the copies are unusable anyway.
        }
    }

    /** Holds a copy of the password until the PIN dialog finishes. The caller keeps its own array. */
    public static synchronized void holdPassword(final char[] password) {
        dropPassword();
        heldPassword = password.clone();
    }

    /** Takes the held copy. It is no longer held after this call. */
    public static synchronized char[] takePassword() {
        final char[] password = heldPassword;
        heldPassword = null;
        return password;
    }

    /** Wipes and forgets the held copy. */
    public static synchronized void dropPassword() {
        if (heldPassword != null) {
            Arrays.fill(heldPassword, '\0');
        }
        heldPassword = null;
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

    /** Returns the IV followed by the ciphertext. The vault name is authenticated, not stored in the blob. */
    @RequiresApi(Build.VERSION_CODES.M)
    private static byte[] wrap(final byte[] plain, final String vault) throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        cipher.updateAAD(vault.getBytes(StandardCharsets.UTF_8));
        final byte[] iv = cipher.getIV();
        final byte[] body = cipher.doFinal(plain);
        final byte[] out = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(body, 0, out, iv.length, body.length);
        return out;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static byte[] unwrap(final byte[] blob, final String vault) throws GeneralSecurityException {
        if (blob.length <= IV_BYTES) {
            throw new GeneralSecurityException("short blob");
        }
        final byte[] iv = Arrays.copyOfRange(blob, 0, IV_BYTES);
        final byte[] body = Arrays.copyOfRange(blob, IV_BYTES, blob.length);
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
        cipher.updateAAD(vault.getBytes(StandardCharsets.UTF_8));
        return cipher.doFinal(body);
    }

    /** The Keystore key, created on first use. It cannot be exported. */
    @RequiresApi(Build.VERSION_CODES.M)
    private static SecretKey key() throws GeneralSecurityException {
        try {
            final KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            final Key existing = store.getKey(ALIAS, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
            final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            return generator.generateKey();
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }
}
