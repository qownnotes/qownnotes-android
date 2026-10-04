# Privacy Policy

QOwnNotes Mobile does not include advertising, analytics, crash reporting, or developer-operated
telemetry. The QOwnNotes project does not receive note content, account credentials, or application
usage data from the app.

## Nextcloud Data

QOwnNotes Mobile connects only to the Nextcloud server selected through the Nextcloud Files Android
app. Nextcloud Files manages the login credentials. The Nextcloud Single Sign-On library stores
account records, including an SSO access token, in QOwnNotes Mobile's private SharedPreferences.
The token is used through Nextcloud Files; QOwnNotes Mobile does not copy it or the user's password
into its notes database.

The app stores downloaded notes, account metadata, synchronization state, and pending changes in
local application storage. It sends note data and synchronization requests to the selected
Nextcloud server through the Nextcloud Notes API. Removing an account from QOwnNotes Mobile removes
that account's local data but does not remove the Nextcloud account or delete its server notes.

Browsing note versions and remote trash uses the selected Nextcloud server only when its optional
QOwnNotesAPI app is installed.

## External Images

Images are enabled by default whenever a note is opened. Viewing that note can send a request to
each HTTPS host referenced by its Markdown images. Those hosts can observe the request metadata,
including the device's IP address. The note menu's **Load images** toggle disables image loading for
the current view; it resets to enabled when the note is reopened. Local Nextcloud note attachments
are requested from the selected Nextcloud server using Single Sign-On.

## Android Backups

Android may back up local notes, account metadata, and settings according to the device's backup
configuration. The Single Sign-On preferences containing account access tokens are excluded from
Android cloud backups and device-to-device transfers. After restoring app data, account access
must be granted again through Nextcloud Files.

## Android Sharing

Text shared into QOwnNotes Mobile is stored as a local note and synchronized to the selected
Nextcloud account. QOwnNotes Mobile does not send it to the QOwnNotes project.

## Contact

Questions and privacy-related reports can be filed at
<https://github.com/qownnotes/qownnotes-android/issues>.
