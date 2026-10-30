package com.notecrypt.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class FileTypesTest {

    private static byte[] ascii(final String text) {
        return text.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    public void extensionsAreRecognisedIgnoringCase() {
        assertEquals("ovpn", FileTypes.extensionOf("Home.OVPN"));
        assertEquals("", FileTypes.extensionOf("trailing."));
        assertEquals("", FileTypes.extensionOf("noext"));
        assertEquals(FileTypes.Category.PDF, FileTypes.kindOf("manual.pdf").category);
        assertNull(FileTypes.kindOf("setup.exe"));
    }

    @Test
    public void imageSignaturesAreChecked() {
        final byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0};
        assertTrue(FileTypes.check("a.png", png).matches);
        assertFalse(FileTypes.check("a.jpg", png).matches);
        assertTrue(FileTypes.check("a.jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}).matches);
        assertTrue(FileTypes.check("a.webp", ascii("RIFF\0\0\0\0WEBPVP8 ")).matches);
    }

    @Test
    public void archivesAndOfficeFilesAreChecked() {
        assertTrue(FileTypes.check("a.zip", ascii("PK\u0003\u0004rest")).matches);
        assertTrue(FileTypes.check("a.docx", ascii("PK\u0003\u0004rest")).matches);
        assertTrue(FileTypes.check("a.gz", new byte[]{(byte) 0x1F, (byte) 0x8B, 8}).matches);
        assertTrue(FileTypes.check("a.7z", new byte[]{0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C}).matches);
        assertTrue(FileTypes.check("a.rar", ascii("Rar!\u001a\u0007\u0000")).matches);
        assertFalse(FileTypes.check("a.rar", ascii("not an archive")).matches);
        final byte[] tar = new byte[512];
        System.arraycopy(ascii("ustar"), 0, tar, 257, 5);
        assertTrue(FileTypes.check("a.tar", tar).matches);
    }

    @Test
    public void packagesAndSecretsAreChecked() {
        assertTrue(FileTypes.check("a.apks", ascii("PK\u0003\u0004")).matches);
        assertTrue(FileTypes.check("a.dex", ascii("dex\n035\u0000")).matches);
        assertTrue(FileTypes.check("a.so", new byte[]{0x7F, 'E', 'L', 'F'}).matches);
        assertTrue(FileTypes.check("a.p12", new byte[]{1, 2, 3}).secret());
    }

    @Test
    public void editingFollowsTypeAndContent() {
        assertTrue(FileTypes.check("Main.java", ascii("class A {}")).editable());
        assertTrue(FileTypes.check("logo.svg", ascii("<svg/>")).editable());
        assertFalse(FileTypes.check("cert.pem", ascii("-----BEGIN")).editable());
        assertFalse(FileTypes.check("photo.png", ascii("text")).editable());
        assertFalse(FileTypes.check("Main.java", new byte[]{'a', 0, 'b'}).editable());
    }

    @Test
    public void emptyContentMatchesNothing() {
        assertFalse(FileTypes.check("a.txt", new byte[0]).matches);
    }
}
