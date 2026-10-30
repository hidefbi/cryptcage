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

The password is converted to UTF-8 bytes and run through PBKDF2 with HMAC-SHA256 for 600,000 iterations, which produces a 256-bit key. The key is kept in memory while the vault is open. It is wiped when the vault locks. The password itself is not kept after the key is derived.

New passwords must have at least 12 characters, and must mix character kinds unless they are 16 characters or longer. Existing passwords are not checked when they are used to open a vault.

There is no recovery. A lost password means the notes cannot be read.

## Files from version 1.x

Version 1.x databases are not read by this version. Open them with version 2.0 or 2.1 first;
those versions rewrite each file in the current format on opening.

## Writing and locking

Every change is written to a temporary file in the same folder, flushed to the disk, and then renamed over the database. An interrupted write therefore leaves the previous version in place.

The vault locks when the app has been in the background for a minute, or when the user closes it from the menu. Locking first saves any pending change and then wipes the key.

The app blocks screenshots and recent-apps previews on every screen. Application data is excluded from system backups.

## Import and export

Export copies the encrypted file to a location the user picks. Import copies a file the user picks into the private folder after checking that it starts with a vault header. The app never scans shared storage.

## Known limits

- Anyone who knows the password can read the notes. Choose a long, unique password.
- While a vault is open, its notes are in memory. A malicious program with full access to the device, or a rooted device, can read them. Lock the vault when you step away.
- Java strings cannot be overwritten, so a password typed into a text field may remain in memory until it is garbage collected.
- The password dialog uses the system keyboard. Keyboards that learn words may store what you type.
- Key derivation takes about a second on a typical phone. That cost is what makes guessing expensive.

## Reporting a problem

If you find a security problem, report it privately when you can instead of opening a public issue.
