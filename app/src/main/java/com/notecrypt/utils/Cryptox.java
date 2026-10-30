package com.notecrypt.utils;

import java.nio.CharBuffer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Primitives used by the vault file format: PBKDF2-HMAC-SHA256 for the key,
 * AES-256-GCM for the data.
 */
final class Cryptox {

    static final int KEY_BYTES = 32;
    static final int SALT_BYTES = 32;
    static final int NONCE_BYTES = 12;
    static final int TAG_BITS = 128;
    static final int DEFAULT_ITERATIONS = 600_000;
    static final int MIN_ITERATIONS = 100_000;
    static final int MAX_ITERATIONS = 5_000_000;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Cryptox() {
    }

    static byte[] randomBytes(final int length) {
        final byte[] out = new byte[length];
        RANDOM.nextBytes(out);
        return out;
    }

    /**
     * PBKDF2 written on top of HmacSHA256 so the password bytes are always UTF-8,
     * whichever crypto provider the device uses.
     */
    static byte[] pbkdf2Sha256(final char[] password, final byte[] salt, final int iterations)
            throws GeneralSecurityException {
        final byte[] passwordBytes = toUtf8(password);
        if (passwordBytes.length == 0) {
            throw new GeneralSecurityException("empty password");
        }
        try {
            final Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(passwordBytes, "HmacSHA256"));
            mac.update(salt);
            mac.update(new byte[]{0, 0, 0, 1});
            byte[] u = mac.doFinal();
            final byte[] result = u.clone();
            for (int i = 1; i < iterations; i++) {
                u = mac.doFinal(u);
                for (int j = 0; j < result.length; j++) {
                    result[j] ^= u[j];
                }
            }
            Arrays.fill(u, (byte) 0);
            return result;
        } finally {
            Arrays.fill(passwordBytes, (byte) 0);
        }
    }

    static byte[] toUtf8(final char[] chars) {
        final ByteBuffer buffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars));
        final byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        if (buffer.hasArray()) {
            Arrays.fill(buffer.array(), (byte) 0);
        }
        return out;
    }

    /** AES-256-GCM. The result is the nonce followed by the ciphertext and the tag. */
    static byte[] seal(final byte[] key, final byte[] aad, final byte[] plain) throws GeneralSecurityException {
        final byte[] nonce = randomBytes(NONCE_BYTES);
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        final byte[] body = cipher.doFinal(plain);
        final byte[] out = new byte[NONCE_BYTES + body.length];
        System.arraycopy(nonce, 0, out, 0, NONCE_BYTES);
        System.arraycopy(body, 0, out, NONCE_BYTES, body.length);
        return out;
    }

    /** Input is the nonce followed by the ciphertext and the tag. Throws if the tag does not match. */
    static byte[] open(final byte[] key, final byte[] aad, final byte[] nonceAndBody) throws GeneralSecurityException {
        final byte[] nonce = Arrays.copyOfRange(nonceAndBody, 0, NONCE_BYTES);
        final byte[] body = Arrays.copyOfRange(nonceAndBody, NONCE_BYTES, nonceAndBody.length);
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
        cipher.updateAAD(aad);
        return cipher.doFinal(body);
    }
}
