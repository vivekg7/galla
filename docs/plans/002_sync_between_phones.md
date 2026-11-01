# 002 — Sync between the shop's phones through Google Drive

Status: built (v0.2.0, 2026-11-01). Revises two decisions of
[001](001_offline_shop_app.md): "offline, enforced" and "no sync".

## Problem

Three people work in the shop, each with their own phone. Each phone sells
and restocks, and all three must see the same products, stock, bills and shop
details. They trust each other. The owner doesn't want to run a server.

Galla 0.1 assumed one phone, in three places:

- Ids counted 1, 2, 3… on each phone, and the in-store barcode was made from
  the product id. Two phones would hand out product #57, and the same barcode
  for two different items.
- Bill numbers were "highest so far + 1", so two phones would both print
  Bill #101.
- The only way to move data was a backup file, and restoring one replaces the
  whole database. It can't merge two phones' sales.

## Decision

**Google Drive's hidden app folder, through the shop's one Google account.**
Every phone signs in to the same account (a Gmail made for the shop). Galla
asks only for `drive.appdata`, Drive's per-app hidden folder: it doesn't
appear among the account's files, and the permission reaches nothing else in
Drive. Each Google account gets its own copy of that folder, so three
personal accounts would never see each other's data. Sharing a folder from
one person's Drive needs full Drive access, which is a restricted scope:
unreviewed, Google signs the other phones out every 7 days. One shop
account avoids both problems. Sign-in goes through Play services'
`AuthorizationClient`. Google matches the app by package name and signing
key, so no client ID or secret is in the code.

**The internet permission is back, for sync only.** Selling, scanning,
slips and backups still work with no network. A phone sells offline and
catches up when it is online. ML Kit's usage logging would also have gone
online, so the manifest removes its upload backend (`cct`): the logs are
dropped on the phone (`Transport backend 'cct' is not registered`).

**Each phone writes only files of its own.** Two phones never write one
file, so Drive never has to choose between two versions:

- `phone-NN.json`: phone NN has joined.
- `b-NN-SSSSSSSS.json.gz`: phone NN's changes, in batches numbered 1, 2, 3…
  A phone applies another phone's batches strictly in order. A missing number
  means "not listed yet": it stops there, and next time lists the whole
  folder again.
- `snap-NN-TIME.json.gz`: the whole shop as phone NN saw it, plus how far
  it had got with each phone's batches. Every phone uploads one a week. A
  joining phone starts from the newest snapshot and needs only the batches
  after it, not every batch since the shop started.

Batches are gzipped JSON rows, read and written with the database's own
column names. A file says which schema it was written with. A phone with an
older Galla stops syncing and asks to be updated, rather than guess.

**What has changed is recorded by triggers, not DAO code.** While sync is
on, SQLite triggers add every changed row's key to the `pending` table. An
upload sends the current state of those rows, then clears them. Triggers
mean no write path can forget. They stay quiet while sync is off, and while
another phone's changes are being applied: the `sync.applying` setting
exists only inside that transaction.

**How rows merge:**

- **Products, bills (paid, cancelled) and shop details: the later edit
  wins**, by `updatedAt`. Shop details are one setting per field, and Save
  writes only the fields that changed. If one phone fixes the address and
  another the UPI ID, both edits stay.
- **Bill lines and stock moves are never edited.** A row already present is
  skipped, so applying a file twice changes nothing.
- **Stock is added up again from the moves** after each apply. The log was
  already the truth about stock (001), and a move can arrive before its
  product. A sale is not an edit of the product, so `moveStock` no longer
  touches `updatedAt`. Otherwise a sale would silently undo another phone's
  price change.
- **Both phones cancel the same bill:** the returns are numbered after the
  bill line (`-lineId`), so the two phones make the same row, and it counts
  once.
- **Two phones give one barcode to two new products:** the product with the
  lower id keeps it on every phone, and the other loses its barcode until
  someone scans it again.

**Ids are numbered per phone.** Phone n numbers from n × 10⁹ + 1, for
products, bills, bill lines and moves alike. A phone that never synced, or
the first phone to turn sync on, is phone 0: its numbers and printed labels
stay as they were. In-store barcodes become `2` + the phone in two digits +
the product number in nine digits + the check digit. Phone 0's codes still
start `200`. That allows at most 100 phone numbers, and a number is never
reused. Bill numbers show the phone: `57` on phone 0, `2-57` on phone 2.

**Joining replaces the joining phone's data.** The first phone uploads what
it has. Every later phone takes the shop's data from Drive, after keeping
its own in `files/before-sync.db`. Merging two separately-kept catalogues
(two different "Sugar"s, conflicting barcodes, overlapping ids) is a much
bigger feature than the shop needs: the other two phones start empty.

**A phone must not sync as someone it isn't.** If the database came from
another install, it holds an old phone's number and an old count of uploaded
batches, and new bills would reuse ids already in Drive. So sync turns itself
off when either happens:

- **Settings → Restore:** the restored copy has its sync settings removed
  before it goes live.
- **Android's own backup restored onto a new install:** the database holds
  an id kept in `noBackupFilesDir`, which a restore never brings back. The
  phone sees it doesn't match.

Turning sync on again rejoins as a new phone, with the shop's data from
Drive.

**When it syncs:** while Galla is on screen. It syncs on opening, every two
minutes, and half a minute after a change. A shop's phone has Galla open
while it sells. Background sync would need WorkManager and its scheduling
for little gain.

## Setup (once, by the owner)

1. Make a Google account for the shop, and add it to all three phones.
2. In Google Cloud Console, create a project and enable the Google Drive API.
3. Set up the OAuth consent screen: External, with the shop account as a
   test user. Then publish it ("In production"). `drive.appdata` is a
   non-sensitive scope, so Google's review shouldn't be needed. An app left in
   "Testing" may have to sign in again every 7 days.
4. Create an OAuth client of type Android for each build the phones run:
   package `io.github.vivekg7.galla` (release) or
   `io.github.vivekg7.galla.debug` (debug), with the SHA-1 of the key that
   signs it (`keytool -list -v -keystore <keystore>`). All three phones must
   run builds signed with the same key.
5. On the phone with the shop's data: Shop → Turn on sync, pick the shop
   account. Then do the same on the other two phones.

## Rejected

- **Syncthing (phone to phone over the shop's Wi-Fi).** It would have kept
  Galla itself offline, but it needs another app on each phone, kept
  running, and the Android version is a community fork. The owner chose
  Drive.
- **Firebase.** It means a login, a hosted database and Google's SDKs, and it
  is the design that makes Vanik useless without internet.
- **Writing to Drive through the file picker (no API).** Drive doesn't let
  another app open a whole Drive folder through the picker, so a phone
  couldn't reliably find the other phones' files.
- **One shared file that every phone rewrites.** Two phones saving at once
  would overwrite each other's changes, and each sync would re-upload the
  whole shop.
- **Merging a joining phone's own data.** See above. The joining phone's
  data is kept in `before-sync.db`, not lost.
- **Deleting old batches.** They are small, and listing asks only for files
  newer than the last one seen, so they cost nothing per sync. Revisit if the
  folder ever gets slow.
