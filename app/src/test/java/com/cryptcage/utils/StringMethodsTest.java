/*
 * Copyright (C) Ludovico de Nittis
 * Modified by hidefbi on 2026-10-31. The changes are listed in NOTICE.md.
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StringMethodsTest {

    @Test
    public void nameWithoutExtension() {
        assertEquals("file_name", StringMethods.getNameDB("/test_path/file_name.ncf"));
        assertEquals("file_name", StringMethods.getNameDB("/test_path/file_name"));
    }

    @Test
    public void caseInsensitiveContains() {
        assertTrue(StringMethods.containsIgnoreCase("STRING1", "StRiNg1"));
        assertFalse(StringMethods.containsIgnoreCase("STRING1", "StRiNg12"));
        assertFalse(StringMethods.containsIgnoreCase(null, "x"));
    }

    @Test
    public void namesAreSanitised() {
        assertEquals("my notes", StringMethods.sanitizeName("  my notes.ncf "));
        assertEquals("etcpasswd", StringMethods.sanitizeName("../../etc/passwd"));
        assertEquals("工作笔记", StringMethods.sanitizeName("工作笔记"));
        assertNull(StringMethods.sanitizeName("../.."));
        assertNull(StringMethods.sanitizeName("   "));
        assertNull(StringMethods.sanitizeName(null));
    }
}
