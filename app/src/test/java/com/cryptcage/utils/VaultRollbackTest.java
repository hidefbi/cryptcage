/*
 * Copyright (C) 2026 hidefbi
 * Part of CryptCage, which is based on NoteCrypt Pro (see NOTICE.md).
 * Distributed under the GNU General Public License, version 3 or later.
 * See the LICENSE file in the project root.
 */
package com.cryptcage.utils;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VaultRollbackTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final char[] PASSWORD = "correct horse battery staple 42".toCharArray();

    /** Keeps the records in memory, the same way the app keeps them in preferences. */
    private static final class MemoryGuard implements GenerationGuard {
        final Map<String, Long> records = new HashMap<>();

        @Override
        public long seen(final String path) {
            final Long value = records.get(path);
            return value == null ? 0L : value;
        }

        @Override
        public void remember(final String path, final long generation) {
            if (generation > seen(path)) {
                records.put(path, generation);
            }
        }

        @Override
        public void forget(final String path) {
            records.remove(path);
        }
    }

    private static byte[] read(final File file) throws Exception {
        return Files.readAllBytes(file.toPath());
    }

    private static void write(final File file, final byte[] data) throws Exception {
        Files.write(file.toPath(), data);
    }

    private static void assertRolledBack(final File file, final MemoryGuard guard) {
        try {
            VaultSession.open(file, PASSWORD, guard).close();
            fail("older copy was opened");
        } catch (VaultException e) {
            assertEquals(VaultException.Reason.ROLLED_BACK, e.getReason());
        }
    }

    @Test
    public void olderCopyIsRefused() throws Exception {
        final File file = new File(folder.getRoot(), "vault.ncf");
        final MemoryGuard guard = new MemoryGuard();
        try (VaultSession session = VaultSession.create(file, PASSWORD, guard)) {
            session.addNote("first", "one", "");
            session.save();
        }
        final byte[] older = read(file);
        try (VaultSession session = VaultSession.open(file, PASSWORD, guard)) {
            session.addNote("second", "two", "");
            session.save();
        }
        write(file, older);
        assertRolledBack(file, guard);
    }

    @Test
    public void sameFileOpensAgain() throws Exception {
        final File file = new File(folder.getRoot(), "vault.ncf");
        final MemoryGuard guard = new MemoryGuard();
        VaultSession.create(file, PASSWORD, guard).close();
        VaultSession.open(file, PASSWORD, guard).close();
        VaultSession.open(file, PASSWORD, guard).close();
    }

    @Test
    public void freshGuardAcceptsAnyValidFile() throws Exception {
        final File file = new File(folder.getRoot(), "vault.ncf");
        final MemoryGuard writer = new MemoryGuard();
        try (VaultSession session = VaultSession.create(file, PASSWORD, writer)) {
            session.addNote("a", "b", "");
            session.save();
        }
        // A file moved to a new device has no record yet, so it opens and gets one.
        final MemoryGuard fresh = new MemoryGuard();
        VaultSession.open(file, PASSWORD, fresh).close();
        assertTrue(fresh.seen(file.getAbsolutePath()) > 0);
    }

    @Test
    public void truncatedFileIsRefused() throws Exception {
        final File file = new File(folder.getRoot(), "vault.ncf");
        final MemoryGuard guard = new MemoryGuard();
        try (VaultSession session = VaultSession.create(file, PASSWORD, guard)) {
            session.addNote("a", "b", "");
            session.save();
        }
        final byte[] whole = read(file);
        write(file, java.util.Arrays.copyOf(whole, 200));
        try {
            VaultSession.open(file, PASSWORD, guard).close();
            fail("truncated file was opened");
        } catch (VaultException e) {
            assertTrue(e.getReason() != null);
        }
    }

    @Test
    public void damagedNewestHeaderIsRefusedNotReplacedByOlderSlot() throws Exception {
        final File file = new File(folder.getRoot(), "vault.ncf");
        final MemoryGuard guard = new MemoryGuard();
        try (VaultSession session = VaultSession.create(file, PASSWORD, guard)) {
            session.addNote("a", "b", "");
            session.save();
            session.addNote("c", "d", "");
            session.save();
        }
        // Break the newest header slot so the older slot would be the only one left to read.
        final byte[] whole = read(file);
        final int offA = 128;
        final int offB = 192;
        final long genA = java.nio.ByteBuffer.wrap(whole, offA, 8).getLong();
        final long genB = java.nio.ByteBuffer.wrap(whole, offB, 8).getLong();
        final int newest = genA > genB ? offA : offB;
        // Zero the length field of the newest slot, which makes it an impossible value.
        for (int i = 16; i < 20; i++) {
            whole[newest + i] = 0;
        }
        write(file, whole);
        try {
            VaultSession.open(file, PASSWORD, guard).close();
            fail("damaged newest header was accepted");
        } catch (VaultException e) {
            assertTrue(e.getReason() != null);
        }
    }

    @Test
    public void tornHeaderWriteStillOpens() throws Exception {
        final File file = new File(folder.getRoot(), "vault.ncf");
        final MemoryGuard guard = new MemoryGuard();
        try (VaultSession session = VaultSession.create(file, PASSWORD, guard)) {
            session.addNote("a", "b", "");
            session.save();
        }
        final long recorded = guard.seen(file.getAbsolutePath());
        // A save that never finished: it wrote a newer slot, but the device did not record it.
        try (VaultSession session = VaultSession.open(file, PASSWORD, null)) {
            session.addNote("c", "d", "");
            session.save();
        }
        final byte[] whole = read(file);
        final long genA = java.nio.ByteBuffer.wrap(whole, 128, 8).getLong();
        final long genB = java.nio.ByteBuffer.wrap(whole, 192, 8).getLong();
        final int newest = genA > genB ? 128 : 192;
        // Tear the newest slot, leaving only the older one, which matches the recorded generation.
        java.util.Arrays.fill(whole, newest, newest + 64, (byte) 0);
        write(file, whole);
        VaultSession.open(file, PASSWORD, guard).close();
        assertEquals(recorded, guard.seen(file.getAbsolutePath()));
    }

    @Test
    public void compactionAndPasswordChangeNeverLowerTheRecord() throws Exception {
        final File file = new File(folder.getRoot(), "vault.ncf");
        final MemoryGuard guard = new MemoryGuard();
        final String path = file.getAbsolutePath();
        try (VaultSession session = VaultSession.create(file, PASSWORD, guard)) {
            session.addNote("a", "b", "");
            session.save();
            final long before = guard.seen(path);
            session.compact();
            assertTrue(guard.seen(path) >= before);
            final long afterCompact = guard.seen(path);
            session.changePassword("another long passphrase 2026".toCharArray());
            assertTrue(guard.seen(path) >= afterCompact);
        }
        // The copy written by compaction must still be accepted, so the generation was kept.
        VaultSession.open(file, "another long passphrase 2026".toCharArray(), guard).close();
    }

    @Test
    public void createDropsOldRecordForThePath() throws Exception {
        final File file = new File(folder.getRoot(), "vault.ncf");
        final MemoryGuard guard = new MemoryGuard();
        final String path = file.getAbsolutePath();
        guard.remember(path, 99L);
        VaultSession.create(file, PASSWORD, guard).close();
        assertTrue(guard.seen(path) < 99L);
        VaultSession.open(file, PASSWORD, guard).close();
    }

    @Test
    public void fileWithMagicButShortReportsCorrupt() throws Exception {
        final File file = new File(folder.getRoot(), "vault.ncf");
        final MemoryGuard guard = new MemoryGuard();
        try (VaultSession session = VaultSession.create(file, PASSWORD, guard)) {
            session.addNote("a", "b", "");
            session.save();
        }
        final byte[] whole = read(file);
        write(file, java.util.Arrays.copyOf(whole, 40));
        try {
            new RandomAccessFile(file, "r").close();
            VaultSession.open(file, PASSWORD, guard).close();
            fail("short file was opened");
        } catch (VaultException e) {
            assertTrue(e.getReason() != null);
        }
    }
}
