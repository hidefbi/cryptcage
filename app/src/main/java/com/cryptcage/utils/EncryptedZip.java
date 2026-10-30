package com.cryptcage.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.FileHeader;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.EncryptionMethod;

/**
 * Writes a password-protected ZIP (WinZip AE-2, AES-256). Entry names keep their original form.
 *
 * After writing, the archive is opened again with the same password and every entry is read
 * back and compared with the bytes that were written. If anything is missing, extra, not
 * encrypted, or different, the file is deleted and an error is thrown.
 */
public final class EncryptedZip {

    private EncryptedZip() {
    }

    public static void write(final File out, final ZipExport.Plan plan, final char[] password) throws Exception {
        if (out.exists() && !out.delete()) {
            throw new IOException("cannot replace " + out.getName());
        }
        final Map<String, byte[]> expected = new LinkedHashMap<>();
        for (ZipExport.Entry entry : plan.entries) {
            expected.put(entry.name, entry.bytes);
        }
        expected.put(ZipExport.MANIFEST_NAME, ZipExport.manifest(plan.records, true));
        try {
            final ZipFile zip = new ZipFile(out, password);
            for (Map.Entry<String, byte[]> entry : expected.entrySet()) {
                add(zip, entry.getKey(), entry.getValue());
            }
            verify(out, password, expected);
        } catch (Exception e) {
            if (out.exists()) {
                out.delete();
            }
            throw e;
        }
    }

    private static void add(final ZipFile zip, final String name, final byte[] bytes) throws Exception {
        final ZipParameters params = new ZipParameters();
        params.setEncryptFiles(true);
        params.setEncryptionMethod(EncryptionMethod.AES);
        params.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
        params.setFileNameInZip(name);
        try (InputStream in = new ByteArrayInputStream(bytes)) {
            zip.addStream(in, params);
        }
    }

    private static void verify(final File out, final char[] password, final Map<String, byte[]> expected)
            throws Exception {
        final ZipFile zip = new ZipFile(out, password);
        final List<FileHeader> headers = zip.getFileHeaders();
        if (headers.size() != expected.size()) {
            throw new IOException("archive holds " + headers.size() + " entries, expected " + expected.size());
        }
        for (FileHeader header : headers) {
            if (!header.isEncrypted()) {
                throw new IOException("entry was not encrypted: " + header.getFileName());
            }
            final byte[] want = expected.get(header.getFileName());
            if (want == null) {
                throw new IOException("unexpected entry: " + header.getFileName());
            }
            final byte[] got = readAll(zip.getInputStream(header));
            if (!Arrays.equals(want, got)) {
                throw new IOException("content differs after writing: " + header.getFileName());
            }
        }
    }

    private static byte[] readAll(final InputStream in) throws IOException {
        try (InputStream source = in) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buf = new byte[8192];
            int n;
            while ((n = source.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }
}
