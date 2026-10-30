package com.cryptcage.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;

/**
 * Reads and writes vault files.
 *
 * Layout (the first 43 bytes are the header and also serve as AAD):
 * <pre>
 *   0   "NCP2"            magic
 *   4   1                 format version
 *   5   1                 key derivation id (1 = PBKDF2-HMAC-SHA256)
 *   6   int32             iteration count
 *   10  1                 salt length (32)
 *   11  32                salt
 *   43  12                GCM nonce
 *   55  ...               ciphertext followed by the 16-byte tag
 * </pre>
 * Files from the first release are not read any more.
 */
public final class VaultFile {

    /** Largest vault we are willing to read or write. */
    public static final int MAX_BYTES = 64 * 1024 * 1024;

    private static final byte[] MAGIC = {'N', 'C', 'P', '2'};
    private static final int VERSION = 2;
    private static final int KDF_PBKDF2_SHA256 = 1;
    static final int HEADER_BYTES = 4 + 1 + 1 + 4 + 1 + Cryptox.SALT_BYTES;
    private static final int MIN_FILE_BYTES = HEADER_BYTES + Cryptox.NONCE_BYTES + Cryptox.TAG_BITS / 8 + 1;

    private VaultFile() {
    }

    /** A vault that was opened successfully, with the key needed to save it again. */
    public static final class Opened {
        public final DatabaseForNotes db;
        public final VaultKey key;

        Opened(final DatabaseForNotes db, final VaultKey key) {
            this.db = db;
            this.key = key;
        }
    }

    public static Opened open(final File file, final char[] password) throws VaultException {
        final byte[] bytes = readAll(file);
        if (bytes.length < MIN_FILE_BYTES || !hasMagic(bytes)) {
            throw new VaultException(VaultException.Reason.NOT_A_VAULT, null);
        }
        final byte[] header = Arrays.copyOfRange(bytes, 0, HEADER_BYTES);
        if (header[4] != VERSION || header[5] != KDF_PBKDF2_SHA256 || header[10] != Cryptox.SALT_BYTES) {
            throw new VaultException(VaultException.Reason.NOT_A_VAULT, null);
        }
        final int iterations = readInt(header, 6);
        if (iterations < Cryptox.MIN_ITERATIONS || iterations > Cryptox.MAX_ITERATIONS) {
            throw new VaultException(VaultException.Reason.NOT_A_VAULT, null);
        }
        final byte[] salt = Arrays.copyOfRange(header, 11, 11 + Cryptox.SALT_BYTES);
        final byte[] body = Arrays.copyOfRange(bytes, HEADER_BYTES, bytes.length);
        byte[] derived = null;
        byte[] payload = null;
        try {
            derived = Cryptox.pbkdf2Sha256(password, salt, iterations);
            payload = Cryptox.open(derived, header, body);
            final DatabaseForNotes db = DatabaseCodec.decode(payload);
            return new Opened(db, new VaultKey(salt, iterations, derived));
        } catch (GeneralSecurityException | IOException e) {
            wipe(derived);
            throw new VaultException(VaultException.Reason.BAD_PASSWORD_OR_DAMAGED, e);
        } finally {
            wipe(payload);
        }
    }

    /** Derives a key for a new password, with a fresh random salt. */
    public static VaultKey createKey(final char[] password) throws VaultException {
        final byte[] salt = Cryptox.randomBytes(Cryptox.SALT_BYTES);
        try {
            final byte[] key = Cryptox.pbkdf2Sha256(password, salt, Cryptox.DEFAULT_ITERATIONS);
            return new VaultKey(salt, Cryptox.DEFAULT_ITERATIONS, key);
        } catch (GeneralSecurityException e) {
            throw new VaultException(VaultException.Reason.INTERNAL, e);
        }
    }

    /** Encrypts the database with the key and replaces the file atomically. */
    public static void save(final File file, final DatabaseForNotes db, final VaultKey key) throws VaultException {
        byte[] payload;
        try {
            synchronized (db) {
                payload = DatabaseCodec.encode(db);
            }
        } catch (IOException e) {
            throw new VaultException(VaultException.Reason.INTERNAL, e);
        }
        try {
            final byte[] header = buildHeader(key.salt, key.iterations);
            final byte[] sealed = Cryptox.seal(key.material(), header, payload);
            final byte[] content = new byte[header.length + sealed.length];
            System.arraycopy(header, 0, content, 0, header.length);
            System.arraycopy(sealed, 0, content, header.length, sealed.length);
            writeAtomically(file, content);
        } catch (GeneralSecurityException e) {
            throw new VaultException(VaultException.Reason.INTERNAL, e);
        } catch (IOException e) {
            throw new VaultException(VaultException.Reason.IO, e);
        } finally {
            wipe(payload);
        }
    }

    /** True if the first bytes carry the vault magic. Used to check an import before copying it. */
    public static boolean looksLikeVault(final byte[] head) {
        return hasMagic(head);
    }

    static byte[] buildHeader(final byte[] salt, final int iterations) {
        final byte[] header = new byte[HEADER_BYTES];
        System.arraycopy(MAGIC, 0, header, 0, MAGIC.length);
        header[4] = VERSION;
        header[5] = KDF_PBKDF2_SHA256;
        writeInt(header, 6, iterations);
        header[10] = (byte) salt.length;
        System.arraycopy(salt, 0, header, 11, salt.length);
        return header;
    }

    private static boolean hasMagic(final byte[] bytes) {
        return bytes.length >= MAGIC.length && Arrays.equals(Arrays.copyOf(bytes, MAGIC.length), MAGIC);
    }

    private static int readInt(final byte[] b, final int off) {
        return ((b[off] & 0xff) << 24) | ((b[off + 1] & 0xff) << 16) | ((b[off + 2] & 0xff) << 8) | (b[off + 3] & 0xff);
    }

    private static void writeInt(final byte[] b, final int off, final int v) {
        b[off] = (byte) (v >>> 24);
        b[off + 1] = (byte) (v >>> 16);
        b[off + 2] = (byte) (v >>> 8);
        b[off + 3] = (byte) v;
    }

    static byte[] readAll(final File file) throws VaultException {
        if (file.length() > MAX_BYTES) {
            throw new VaultException(VaultException.Reason.TOO_LARGE, null);
        }
        final InputStream in;
        try {
            in = new FileInputStream(file);
        } catch (IOException e) {
            throw new VaultException(VaultException.Reason.IO, e);
        }
        try {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BYTES) {
                    throw new VaultException(VaultException.Reason.TOO_LARGE, null);
                }
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new VaultException(VaultException.Reason.IO, e);
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // nothing useful to do here
            }
        }
    }

    private static void writeAtomically(final File target, final byte[] bytes) throws IOException {
        final File dir = target.getAbsoluteFile().getParentFile();
        final File tmp = new File(dir, "." + target.getName() + ".tmp");
        FileOutputStream out = null;
        try {
            out = new FileOutputStream(tmp);
            out.write(bytes);
            out.flush();
            out.getFD().sync();
            out.close();
            out = null;
            if (!tmp.renameTo(target)) {
                throw new IOException("could not replace " + target.getName());
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                    // already failing, keep the original error
                }
            }
            if (tmp.exists()) {
                tmp.delete();
            }
        }
    }

    private static void wipe(final byte[] b) {
        if (b != null) {
            Arrays.fill(b, (byte) 0);
        }
    }
}
