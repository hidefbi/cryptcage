# NoteCrypt
Keep your notes safe and secure with Note Crypt for Android!


* Create different encrypted, with password, database files.
* Add new notes in a database choosing for each of them: title, tags and text of the note.
* Filter by Tag the list of the notes.
* Mark the notes as important by clicking the star icon.
* Sorting notes by name or creation date.
* Change the password of an existing database.
* The database is encrypted with AES-256-GCM. The key comes from PBKDF2-HMAC-SHA256 (600,000 iterations) with a random salt, and every save uses a new random nonce. See SECURITY.md for the details.
* Saves are atomic, so an interrupted save cannot corrupt the database.
* Export an encrypted copy, or import a database file from anywhere on the device.
* The vault locks itself after a minute in the background, and screenshots are blocked.
* Ability to search through your notes.

If you forget your password, the notes will be inaccessible.

Databases from older versions still open with the same password and are upgraded automatically the first time they are opened.

Thanks to [naofum](https://github.com/naofum) for the Japanese translation and to [hanelore](https://github.com/hanelore) for the Brazilian Portuguese translation.

## Download

[<img src="https://f-droid.org/badge/get-it-on.png"
      alt="Get it on F-Droid"
      height="80">](https://f-droid.org/app/com.notecryptpro) [<img src="https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png"
      alt="Get it on Google Play"
      height="80">](https://play.google.com/store/apps/developer?id=Ludovico+de+Nittis)
