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
import android.content.pm.PackageManager;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import androidx.annotation.RequiresApi;

import com.cryptcage.utils.PinEnvelope;
import com.cryptcage.utils.PinRules;

/**
 * The PIN and the master passwords it can unlock.
 *
 * A saved password goes through two layers. The inner layer is AES-GCM with a device key from the
 * Android Keystore, which cannot be exported and can only be used while the device is unlocked
 * (Android 9 and later). The outer layer is AES-GCM with a key derived from the PIN. Both layers
 * are bound to the vault name, so a copy cannot be moved to another vault. The device key alone
 * cannot open a copy, and the PIN alone cannot either.
 *
 * One PIN is shared by all vaults. Wrong PINs are counted, and after {@link PinRules#MAX_FAILURES}
 * of them the PIN and every saved copy are removed. The databases are not changed.
 *
 * PIN unlock needs Android 6.0 or later. On older versions {@link #supported()} is false.
 */
public final class PinStore {

    /** Result of checking a PIN key against the verifier. */
    public enum Check { OK, WRONG, WIPED, NOT_SET }

    private static final String PREFS = "pin_lock_v2";
    private static final String KEY_SALT = "salt";
    private static final String KEY_VERIFIER = "verifier";
    private static final String KEY_FAILURES = "failures";
    private static final String VAULT_PREFIX = "vault_";
    private static final String DEVICE_ALIAS = "cryptcage_pin_device_key";
    private static final String OLD_ALIAS = "cryptcage_pin_wrap";
    private static final String OLD_PREFS = "pin_lock";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String DEVICE_AAD_PREFIX = "cryptcage-device:";
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
        return prefs(context).contains(KEY_VERIFIER);
    }

    /** True when this vault has a saved copy of its password. */
    public static boolean hasCredential(final Context context, final String vault) {
        return isEnabled(context) && prefs(context).contains(vaultKey(vault));
    }

    public static int attemptsLeft(final Context context) {
        return PinRules.attemptsLeft(prefs(context).getInt(KEY_FAILURES, 0));
    }

    /**
     * Sets the PIN. Returns its key so the caller can save the first copy without deriving it again.
     * The caller wipes the key.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    public static byte[] enable(final Context context, final char[] pin) throws GeneralSecurityException {
        final byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        final byte[] key = PinEnvelope.deriveKey(pin, salt);
        try {
            final byte[] verifier = PinEnvelope.verifier(key);
            deviceKey(context, true);
            prefs(context).edit().clear().apply();
            prefs(context).edit()
                    .putString(KEY_SALT, encode(salt))
                    .putString(KEY_VERIFIER, encode(verifier))
                    .putInt(KEY_FAILURES, 0)
                    .apply();
            deleteLegacy(context);
            return Arrays.copyOf(key, key.length);
        } finally {
            Arrays.fill(key, (byte) 0);
        }
    }

    /** The PIN key for a PIN, from the stored salt. Null when no PIN is set. The caller wipes it. */
    public static byte[] deriveKey(final Context context, final char[] pin) throws GeneralSecurityException {
        final byte[] salt = decode(prefs(context).getString(KEY_SALT, null));
        if (salt == null) {
            return null;
        }
        return PinEnvelope.deriveKey(pin, salt);
    }

    /**
     * Checks a PIN key against the verifier. A wrong key counts as a failure, and the limit removes
     * the PIN and every saved copy.
     */
    public static Check verify(final Context context, final byte[] key) {
        final SharedPreferences prefs = prefs(context);
        final byte[] verifier = decode(prefs.getString(KEY_VERIFIER, null));
        if (verifier == null) {
            return Check.NOT_SET;
        }
        if (PinEnvelope.verifies(key, verifier)) {
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

    /** Saves a copy of the vault's password. The PIN key must already be checked. */
    @RequiresApi(Build.VERSION_CODES.M)
    public static void saveCredential(final Context context, final String vault, final byte[] key,
                                      final char[] password) throws GeneralSecurityException {
        final byte[] plain = new String(password).getBytes(StandardCharsets.UTF_8);
        final byte[] inner;
        try {
            inner = deviceWrap(context, vault, plain);
        } finally {
            Arrays.fill(plain, (byte) 0);
        }
        final byte[] outer = PinEnvelope.wrapForVault(key, vault, inner);
        prefs(context).edit().putString(vaultKey(vault), encode(outer)).apply();
    }

    /** Returns the saved password of the vault, or null when there is none or it cannot be opened. */
    @RequiresApi(Build.VERSION_CODES.M)
    public static char[] loadCredential(final Context context, final String vault, final byte[] key) {
        final byte[] stored = decode(prefs(context).getString(vaultKey(vault), null));
        if (stored == null) {
            return null;
        }
        try {
            final byte[] inner = PinEnvelope.unwrapForVault(key, vault, stored);
            final byte[] plain = deviceUnwrap(context, vault, inner);
            try {
                return new String(plain, StandardCharsets.UTF_8).toCharArray();
            } finally {
                Arrays.fill(plain, (byte) 0);
            }
        } catch (GeneralSecurityException | RuntimeException e) {
            return null;
        }
    }

    /** Deletes the saved password of one vault, for example when the vault is deleted. */
    public static void removeCredential(final Context context, final String vault) {
        prefs(context).edit().remove(vaultKey(vault)).apply();
    }

    /** Turns PIN unlock off: removes the PIN, every saved copy and the device key. */
    public static void disable(final Context context) {
        prefs(context).edit().clear().apply();
        deleteLegacy(context);
        deleteDeviceKey();
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

    private static String vaultKey(final String vault) {
        return VAULT_PREFIX + vault;
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

    /** Inner layer: IV followed by the AES-GCM ciphertext, bound to the vault name. */
    @RequiresApi(Build.VERSION_CODES.M)
    private static byte[] deviceWrap(final Context context, final String vault, final byte[] plain)
            throws GeneralSecurityException {
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey(context, true));
        cipher.updateAAD(deviceAad(vault));
        final byte[] iv = cipher.getIV();
        final byte[] body = cipher.doFinal(plain);
        final byte[] out = new byte[iv.length + body.length];
        System.arraycopy(iv, 0, out, 0, iv.length);
        System.arraycopy(body, 0, out, iv.length, body.length);
        return out;
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static byte[] deviceUnwrap(final Context context, final String vault, final byte[] inner)
            throws GeneralSecurityException {
        if (inner.length <= IV_BYTES) {
            throw new GeneralSecurityException("short inner copy");
        }
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, deviceKey(context, false),
                new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(inner, 0, IV_BYTES)));
        cipher.updateAAD(deviceAad(vault));
        return cipher.doFinal(Arrays.copyOfRange(inner, IV_BYTES, inner.length));
    }

    private static byte[] deviceAad(final String vault) {
        return (DEVICE_AAD_PREFIX + vault).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The device key. Usable only while the device is unlocked (Android 9 and later), and kept in
     * StrongBox when the hardware has it. Created on first use when create is true.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private static SecretKey deviceKey(final Context context, final boolean create) throws GeneralSecurityException {
        try {
            final KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            final Key existing = store.getKey(DEVICE_ALIAS, null);
            if (existing instanceof SecretKey) {
                return (SecretKey) existing;
            }
            if (!create) {
                throw new GeneralSecurityException("no device key");
            }
            final KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    && context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)) {
                try {
                    generator.init(deviceKeySpec(true));
                    return generator.generateKey();
                } catch (GeneralSecurityException | RuntimeException e) {
                    // StrongBox refused the key (it throws a runtime exception). The normal Keystore is used instead.
                }
            }
            generator.init(deviceKeySpec(false));
            return generator.generateKey();
        } catch (IOException e) {
            throw new GeneralSecurityException(e);
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private static KeyGenParameterSpec deviceKeySpec(final boolean strongBox) {
        final KeyGenParameterSpec.Builder spec = new KeyGenParameterSpec.Builder(DEVICE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            spec.setUnlockedDeviceRequired(true);
            if (strongBox) {
                spec.setIsStrongBoxBacked(true);
            }
        }
        return spec.build();
    }

    private static void deleteDeviceKey() {
        deleteAlias(DEVICE_ALIAS);
    }

    private static void deleteLegacy(final Context context) {
        context.getApplicationContext().getSharedPreferences(OLD_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        deleteAlias(OLD_ALIAS);
    }

    private static void deleteAlias(final String alias) {
        try {
            final KeyStore store = KeyStore.getInstance(KEYSTORE);
            store.load(null);
            store.deleteEntry(alias);
        } catch (GeneralSecurityException | IOException e) {
            // Already gone, or the store is unavailable. Nothing else to clean up.
        }
    }
}
