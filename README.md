# CryptCage

CryptCage keeps your notes and files in one encrypted vault file on your Android device. You open the vault with a master password, and optionally with a PIN or your fingerprint.

The app has no network access and no account. Nothing leaves the device unless you export it yourself.

## What it does

- Keep notes with a title, tags and a body. Star the important ones.
- Keep files next to your notes. Text, PDF, `.ovpn` and `.conf` files get their own icon in the list.
- Filter the list by all items, notes or files, and by tag. Search looks through titles and bodies.
- Make an entry read-only. A read-only entry can still be opened and viewed, but it cannot be edited, starred, copied or deleted.
- Drag entries into your own order.
- Use undo and redo in the note editor. Text you have not saved yet is kept as a draft in the vault.
- Keep several vaults, each with its own password.
- Change the master password from the settings. The current password is asked first.
- Unlock with a 4 to 8 digit PIN, or with the fingerprint or face the device already uses.
- Lock the vault after a set time without any touch, or when the screen turns off. The default is 5 minutes, and you can choose "Never".
- Clear copied text from the clipboard after a set time.
- Block screenshots and recent-apps previews. You can allow them in the settings.
- Export a whole vault as an encrypted copy, or export notes and files as a plain ZIP, an encrypted ZIP or an unencrypted 7z archive. The 7z option needs Android 8.0 or later.
- Choose a light, dark or system look, and a colour theme (blue, sand or olive).

## Security

Each vault is one file. Its contents are encrypted with AES-256-GCM. The key comes from your password with PBKDF2-HMAC-SHA256 and 600,000 iterations. Every save uses a new random nonce, and a save replaces the old file only after the new one is complete.

The PIN and fingerprint unlock keep a copy of the master password, wrapped with a key in the Android Keystore that cannot be exported. Changing the master password removes those copies. See [SECURITY.md](SECURITY.md) for the format and the details.

If you forget the master password, the data cannot be recovered.

## Requirements

- Android 5.0 (API 21) or later.
- Vaults from version 1.x cannot be opened by this version. Open them with version 2.0 or 2.1 first, then update.

## Building

You need JDK 17 and the Android SDK. Then run:

```
./gradlew installDebug
```

[BUILD.md](BUILD.md) has the details.

## License

CryptCage is free software under the GNU General Public License, version 3 or later. See [LICENSE](LICENSE).

It is based on NoteCrypt Pro. [NOTICE.md](NOTICE.md) lists the changes and the original copyright notices.
