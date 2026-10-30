package com.notecrypt.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class FileKindsTest {

    @Test
    public void typeFollowsExtensionIgnoringCase() {
        assertEquals(FileKinds.Type.OVPN, FileKinds.typeOf("home.OVPN"));
        assertEquals(FileKinds.Type.CONF, FileKinds.typeOf("wg0.conf"));
        assertEquals(FileKinds.Type.PDF, FileKinds.typeOf("manual.pdf"));
        assertEquals(FileKinds.Type.TXT, FileKinds.typeOf("todo.txt"));
        assertNull(FileKinds.typeOf("setup.exe"));
        assertNull(FileKinds.typeOf("noextension"));
        assertNull(FileKinds.typeOf("trailingdot."));
        assertNull(FileKinds.typeOf(null));
    }

    @Test
    public void onlyTextTypesAreEditable() {
        assertTrue(FileKinds.isEditableText(FileKinds.Type.OVPN));
        assertTrue(FileKinds.isEditableText(FileKinds.Type.CONF));
        assertTrue(FileKinds.isEditableText(FileKinds.Type.TXT));
        assertFalse(FileKinds.isEditableText(FileKinds.Type.PDF));
    }

    @Test
    public void pdfNeedsHeaderSignature() {
        final byte[] pdf = "%PDF-1.7\n...".getBytes(StandardCharsets.US_ASCII);
        final byte[] text = "not a pdf".getBytes(StandardCharsets.US_ASCII);
        assertTrue(FileKinds.validContent(FileKinds.Type.PDF, pdf));
        assertFalse(FileKinds.validContent(FileKinds.Type.PDF, text));
    }

    @Test
    public void textRejectsBinaryAndBadUtf8() {
        assertTrue(FileKinds.validContent(FileKinds.Type.OVPN,
                "client\nremote 10.0.0.1 1194\n益".getBytes(StandardCharsets.UTF_8)));
        assertFalse(FileKinds.validContent(FileKinds.Type.TXT, new byte[]{'a', 0, 'b'}));
        assertFalse(FileKinds.validContent(FileKinds.Type.CONF, new byte[]{(byte) 0xC3, 0x28}));
    }

    @Test
    public void emptyAndOversizedAreRefused() {
        assertFalse(FileKinds.validContent(FileKinds.Type.TXT, new byte[0]));
        assertFalse(FileKinds.validContent(FileKinds.Type.TXT, new byte[(int) FileKinds.MAX_FILE_BYTES + 1]));
    }

    @Test
    public void cleanNameKeepsTypeExtension() {
        assertEquals("my vpn.ovpn", FileKinds.cleanFileName("../evil/my vpn.ovpn", FileKinds.Type.OVPN));
        assertEquals("office.conf", FileKinds.cleanFileName("office.conf", FileKinds.Type.CONF));
        assertEquals("notes.txt", FileKinds.cleanFileName("notes", FileKinds.Type.TXT));
        assertNull(FileKinds.cleanFileName("..", FileKinds.Type.TXT));
        assertNull(FileKinds.cleanFileName("   ", FileKinds.Type.TXT));
    }
}
