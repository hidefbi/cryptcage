# CryptCage Frequently Asked Questions(FAQs).
This is a compilation of Frequently Asked Questions for CryptCage. This document will aid prospective and existing end-users of the software as a self-service guide in cases where the developer can't be reached. This document is subject to change and modifications at any time. Thus it'll be updated from time to time consequently to suit any new changes to the software as new developments would be implemented which might cause a discrepancy with this current document.

**What is CryptCage?**
- CryptCage is an Android software that helps you secure your supposedly private notes in an encrypted database.

**What are the features of CryptCage?**

CryptCage has quite a handful of features they include;

- Multiple database creation with different passwords.
- Create a new note.
- Tag filter
- Favorite note
- Note sorting
- The database is encrypted with AES-256 in GCM mode, which also detects any change made to the file. The key is derived from your password with PBKDF2-HMAC-SHA256 (600,000 iterations) and a random salt. A new random nonce is used on every save.
- Saves are atomic: the file is replaced only after the new version is fully written, so an interrupted save does not corrupt your notes.
- The app locks itself after a minute in the background, and blocks screenshots.
- Note search.

**What are the rules for a password?**

- A new or changed password must have at least 12 characters. It also needs letters, digits or symbols mixed together, unless it is 16 characters or longer. Passwords that were accepted by older versions still open normally.

**Can I change my database password at a later time?**

- Yes, you can change the password of a database at any time. This is one of the features of CryptCage. To change the database password, 

- first, unlock the database, 
- click the 3 dots by the top right of the app, 
- select the option to change the password,
- input new password, confirm password and click OK.

**Can I delete a database?**

- Yes. Deleting a database removes its file from the app. CryptCage keeps no other copies of it, so the notes in it cannot be recovered afterwards.

**Where are my databases stored?**

- Databases are kept in the app's private storage. Other apps cannot read them, and CryptCage does not ask for any storage permission. To keep a copy somewhere else, use "Export encrypted copy" in the menu of an open database. To open a database that is stored elsewhere, use the import button on the start screen.

**Can I share my notes to other locations and mediums?**

Yes, notes in a database can be shared to other locations or mediums. To share a note,

- unlock the database
- open note in the database you wish to send or share
- click the 3 dots on the top right of the app
- select share option and choose where you wish to share the note to.

**I have note files in my device storage, can I import them into a database?**

- You can import a whole CryptCage database file with the import button on the start screen. Individual text files cannot be imported; copy the text into a new note in CryptCage instead.

**Important note is a feature of CryptCage, how can I mark a note as important?**

To mark a note as important or favorite note,
- unlock the database where the note is contained
- open the note 
- click the star icon on the top of the app and the note is now marked as important/favorite

**I like to format my texts, does CryptCage support markdown text styling?**

- No, CryptCage does not support markdown text styling, but the formats can be inputted when creating a note and you can preview with on a platform that supports markdown.

**What's the purpose of tags?**

- With tags, you can quickly access notes that carry that tag from the tag filter. For example, you tag a note with software. When you click the tag filter and select software, it brings out all the notes that have the software tag.

**Can I make a manual backup of my database?** 

- Yes. Open the database, choose "Export encrypted copy" from the menu and pick where to save it. The copy is still encrypted with the same password, so it is safe to keep it in cloud storage.

**I'm changing my phone, can I transfer my notes?**

- Yes. Export an encrypted copy on the old device, move the file to the new device, and import it on the start screen. Then open it with the same password.

**I forgot my password, can I recover my notes?** 

- No, lost passwords cannot be recovered. Keep the password somewhere safe, for example in a password manager.

**I used an older version of CryptCage. Will my databases still open?**

- Yes, with the same password. The first time you open an older database, it is rewritten in the current format automatically. Older versions looked for databases in the shared "CryptCage" folder; that folder is no longer read, so use the import button to bring those files in.

**Can you or someone else read my notes?** 

- No, they are only stored on your device and encrypted with the chosen password, unless that password is compromised or given to someone else.

**Can I read my notes on my PC or on iOS?** 

- No, this is available to only Android devices.

**Are there locked functionalities I need to pay for before use?**

- No, CryptCage is an open source software that is totally free and contains no unnecessary ads and in-app purchases.

**I'm skeptical about my privacy, does the developer have access to and collects any information externally when I utilize CryptCage?**

- No, the developer has no means of collecting your information when the software is utilized. Read the [Privacy policy](Privacy.md)

**I experienced a crash, how can and where do I send a crash report?

If you experienced a crash, you can send a detailed crash report to the developer through the issue tracker of the repository you obtained CryptCage from, preferably with a logcat, to enable the developer to pinpoint the cause of the crash and fix it accordingly.

**I need to make further inquiries, how can I contact the developer?**
For more inquiries, use the issue tracker of the repository you obtained CryptCage from.
