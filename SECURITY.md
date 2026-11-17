# Security notes

This file describes how CryptCage protects a database and what it does not protect against.

## What is stored

A database is one file in the app's private storage. Each file holds all of its notes: titles, bodies and tags, plus the starred flag. Nothing else is written to disk, and the file contains no readable copy of the password.

## File format (version 2)

| Offset | Size | Content |
|---|---|---|
| 0 | 4 | ASCII `NCP2` |
| 4 | 1 | format version, `2` |
| 5 | 1 | key derivation, `1` = PBKDF2-HMAC-SHA256 |
| 6 | 4 | iteration count, big endian |
| 10 | 1 | salt length, `32` |
| 11 | 32 | random salt |
| 43 | 12 | random GCM nonce |
| 55 | rest | ciphertext followed by the 16-byte authentication tag |

The first 43 bytes are passed to AES-GCM as additional authenticated data, so changing the header is detected in the same way as changing the ciphertext.

## Keys and passwords

The password is converted to UTF-8 bytes and run through PBKDF2 with HMAC-SHA256 for 600,000 iterations, which produces a 256-bit key. The PIN uses the same count now. A PIN set by an older version is moved to 600,000 the next time it unlocks. The key is kept in memory while the vault is open. It is wiped when the vault locks. The password itself is not kept after the key is derived.

New passwords must have at least 12 characters. Common passwords and simple patterns are refused. Existing passwords are not checked when they are used to open a vault.

There is no recovery. A lost password means the notes cannot be read.

## Files from version 1.x

Version 1.x databases are not read by this version. Open them with version 2.0 or 2.1 first;
those versions rewrite each file in the current format on opening.

## Writing and locking

Every change is written to a temporary file in the same folder, flushed to the disk, and then renamed over the database. An interrupted write therefore leaves the previous version in place.

Each vault file has a version number that goes up on every save. The newest number this device has seen for a vault is kept in the app's own preferences, outside the vault file. If a file comes back with an older number, the app refuses to open it. This stops someone from quietly putting an old copy back in place. Clearing the app's data, or moving the vault to another device, starts that record again.

The vault locks when the app has been in the background for a minute, or when the user closes it from the menu. Locking first saves any pending change and then wipes the key.

The app blocks screenshots and recent-apps previews on every screen unless Screenshot mode is turned on in the settings. Application data is excluded from system backups.

## Import and export

Export copies the encrypted file to a location the user picks. Import copies a file the user picks into the private folder after checking that it starts with a vault header. The app never scans shared storage.

## Saved copies

PIN and fingerprint unlock keep a copy of the master password for each vault. A copy is removed when:

- the vault is deleted,
- the master password is changed (the copies would hold the old one),
- PIN unlock is turned off, or the PIN is entered wrong five times (this removes the PIN copies only),
- a new fingerprint is enrolled. Android then drops the key, so the fingerprint copies cannot be read and are cleared.

## Key rounds

Vaults are keyed with PBKDF2-HMAC-SHA256 at 600,000 rounds. A vault made with fewer rounds is rekeyed the first time it opens with its password. That takes a second or two, once. If the rekey fails, the vault is left as it was.

A memory-hard function such as Argon2id is the next step. It needs a new field in the file header and support for reading both formats, so it is not in this version.

## Known limits

- Anyone who knows the password can read the notes. Choose a long, unique password.
- While a vault is open, its notes are in memory. A malicious program with full access to the device, or a rooted device, can read them. Lock the vault when you step away.
- Java strings cannot be overwritten, so a password typed into a text field may remain in memory until it is garbage collected.
- PIN and fingerprint unlock keep a copy of the master password on the device, wrapped by a key in the Android Keystore. Turn them off if you do not want that copy.
- A plain ZIP or 7z export has no password. Anyone who gets that file can read it.
- The encrypted copy is a normal vault file. It uses the same key settings as the vault, so it can only be opened in CryptCage.
- Deleting a vault removes the file, but the flash storage underneath may keep the old bytes for a while. The app does not promise a secure erase.
- Accessibility services can read what is on the screen, including open notes.
- The clipboard is cleared after the set time only if it still holds the copy CryptCage made.
- The password dialog uses the system keyboard. Keyboards that learn words may store what you type.
- Key derivation takes about a second on a typical phone. That cost is what makes guessing expensive.

## Release signing

Release APKs are signed with the same key as version 2.3 and later. The SHA-256 fingerprint of that certificate is:

`2E:DE:A3:2F:04:6C:2B:BA:7F:DC:58:85:27:9E:A4:D1:4A:BE:6D:BE:CA:7D:E0:6D:39:38:8B:DF:5C:67:D6:E7`

To check a downloaded APK:

    apksigner verify --print-certs cryptcage.apk

The SHA-256 line has to match. The build also stops when the signing values are missing, so an unsigned release cannot come out by accident.

## Reporting a problem

If you find a security problem, report it privately when you can instead of opening a public issue.
