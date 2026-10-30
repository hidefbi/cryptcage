/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

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
    public void utf8TextRejectsBinaryAndBadUtf8() {
        assertTrue(FileKinds.isUtf8Text("client\nremote 10.0.0.1 1194\n益".getBytes(StandardCharsets.UTF_8)));
        assertFalse(FileKinds.isUtf8Text(new byte[]{'a', 0, 'b'}));
        assertFalse(FileKinds.isUtf8Text(new byte[]{(byte) 0xC3, 0x28}));
    }
}
