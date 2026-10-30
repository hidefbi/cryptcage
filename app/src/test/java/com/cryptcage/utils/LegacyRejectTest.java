package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileOutputStream;

import org.junit.Test;

public class LegacyRejectTest {

    /** A first-release file starts with the Java serialization header, which is not a vault. */
    @Test
    public void firstReleaseFileIsRefused() throws Exception {
        final File file = File.createTempFile("old", ".ncf");
        try {
            final byte[] data = new byte[128];
            data[0] = (byte) 0xAC;
            data[1] = (byte) 0xED;
            final FileOutputStream out = new FileOutputStream(file);
            out.write(data);
            out.close();
            try {
                VaultFile.open(file, "long enough password".toCharArray());
                fail("first-release files must not be opened");
            } catch (VaultException e) {
                assertEquals(VaultException.Reason.NOT_A_VAULT, e.getReason());
            }
        } finally {
            file.delete();
        }
    }
}
