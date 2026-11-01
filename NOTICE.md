# Notice

CryptCage is a modified version of NoteCrypt Pro, written by Ludovico de Nittis. The original program is distributed under the GNU General Public License, version 3 or later. CryptCage is distributed under the same license. The full text is in the LICENSE file.

The original source files had no copyright headers. Each Java file in this version now has a short header: files changed from the original name the original author and the modifier, and files added for CryptCage name their author.

## Changes

This list covers the changes made by hidefbi on 2026-10-31 and the earlier changes in this repository. It is not a line-by-line history; the git log has that.

- The application is renamed to CryptCage, and the package name is now `com.cryptcage`.
- The vault file has a new format. The data is stored as encrypted blocks with two header slots. The old single-file format is no longer read.
- A vault can hold up to 1 GiB of content. Note bodies and attached files are read only when they are opened, and the note list is built from an index.
- Saves write only the changed records. When enough space is unused, the file is compacted.
- Changing the password rewrites the header of the file.
- Screens were redesigned with Material 3 and layouts that adapt to the screen width.
- The start screen was rebuilt as cards: the most recent database, an expandable list of the saved databases, the field for a new database, and file import.
- A second colour palette, Sand, was added next to the blue one. It works with the light, dark and system modes.
- A new launcher icon was made: a shield with a padlock. It has adaptive, monochrome and legacy versions.
- The bottom bar of the note list was removed. A menu above the round button holds adding a note, importing a file and exporting all notes. Settings is in the top right.
- A screenshot mode was added to the settings. It is off by default, and when it is on, notes can be captured.
- Any supported file type can be imported, and files can be added from the start screen.
- Unused code, contributor names and developer links were removed. The license and copyright notices were kept.
- A long press on a note opens a menu with select, export, copy, read-only and delete. The list can be reordered by dragging, and a read-only note cannot be changed, starred or deleted.
- The note editor has undo and redo. Unsaved text is kept as a draft sealed with the vault key, and it is offered again when the note is opened. A note body can be at most 8 MB when it is written. KeePass databases (.kdbx) can be stored as files.
- Settings are grouped into appearance, security and about. The clipboard timeout is set as hours, minutes and seconds, and copied note text is cleared from the clipboard after that time. The master password can be changed from the settings. The current password is asked for first. After a change, the PIN and biometric copies of that database are removed, because they still hold the old password.
- The vault locks after the time set in the security settings passes with no touch or key press. The default is 5 minutes, and "Never" turns this lock off. It replaces the fixed one-minute lock that started when the app went to the background. The lock when the screen turns off is unchanged.
- The password dialog shows a strength meter while the password is typed. The accept or reject rule did not change.
- A PIN of 4 to 8 digits can unlock a database in place of its master password, on Android 6.0 and later. Each database keeps its own copy of the master password, wrapped with a non-exportable Keystore key. Five wrong PINs remove the PIN and the copies, not the databases.
- Biometric unlock can open a database with the fingerprint or face the device already has. Each database keeps its own copy of the master password, encrypted with a Keystore key that needs a biometric check for every use. Turning it off removes the copies and the key. A new fingerprint enrolment invalidates the key on Android 7 and later.
- Export can write a plain ZIP, an encrypted ZIP, or a plain 7z archive. The 7z archive uses Apache Commons Compress and xz, and it is offered from Android 8.0. An encrypted 7z archive is not written yet.
- The About page replaces the toast. It shows the version, the licence note and a donation card with BTC, ETH and XMR addresses. Tapping an address copies it. The addresses are the same ones KeePassPN uses, and donations do not unlock anything.
- The settings rows are grouped into cards under section titles. The cards are drawn by CardGroupDecoration.
- The olive palette is the default. The unlocked screen has a dark header with the vault name and a search field, filter chips for all items, notes and files with their counts, note cards with an icon tile, and a rounded floating button.
- A read-only entry no longer offers Copy (duplicate) or Copy to clipboard. It can still be viewed, shared and exported.
- The header of the unlocked screen uses the colour of the chosen palette instead of a fixed dark colour. The search field is a light card with an outline.
- Test coverage was added for the storage format, compaction, password change and the vault session.

## Dates

- Original work: NoteCrypt Pro, by Ludovico de Nittis.
- Changes by hidefbi: 2026-10-31 (headers and this notice); earlier changes are dated in the git history.
