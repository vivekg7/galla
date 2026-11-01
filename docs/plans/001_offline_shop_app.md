# 001 — An offline shop app for one kirana

Status: built (v0.1.0, 2026-10-31). Revised by
[002](002_sync_between_phones.md): the shop's phones now sync through Google
Drive, so the app goes online for that, and only for that.

## Problem

The owner runs a kirana (grocery) shop and tried Vanik, a shop app for kirana
stores. Vanik keeps everything in Firebase behind a phone-number login, so it
does nothing without internet, and it does far more than one shop needs:
online orders, udhaar khata, analytics, Razorpay.

The owner wants three things from it, working with no internet at all:

1. Inventory: products, prices, stock, restocking.
2. Barcodes: scan them, and make them for products that have none.
3. A payment slip for each bill, with a UPI QR code for the exact amount,
   paid to the owner's own UPI ID.

The app runs on one Android phone (a Moto g45 5G, Android 15) at the
counter. No printer: the owner said so.

## What Vanik does, for reference

From the APK in `local/` (v4.3.5, `com.mycompany.vaniq`; never committed, and
nothing here is derived from its code):

- Built with FlutterFlow on Firebase Auth, Firestore, Storage and Cloud
  Functions. Inventory, sales and customers are Firestore collections.
- Scanning uses ML Kit's bundled model, so scanning itself works offline.
- Billing scans items into a cart and asks "how much" for loose items. The
  bill is shared as an image (WhatsApp).
- It has no UPI QR code and no way to make barcodes. Galla adds both.
- It ships a list of 17,499 barcodes and product names. **Not used**: it is
  Vanik's data, and its Indian coverage is patchy (the fourth row is German
  sardines). Galla's catalogue is the shop's own, built by scanning.

## Decisions

**Native Kotlin and Jetpack Compose**, one activity, no DI framework, no
navigation library (a list is the back stack). It's Android only, so
nothing is gained from a cross-platform engine. The build mirrors the owner's
other app, dhun: AGP 9, version catalog, ktlint run from its own jar.

**Offline is enforced, not just intended.** The merged manifest removes
`INTERNET` and `ACCESS_NETWORK_STATE` (ML Kit's logging libraries ask for
them), so Android itself stops the app from reaching the network. The only
permission left is `CAMERA`.

**Scanning: CameraX with ML Kit's bundled barcode model**
(`com.google.mlkit:barcode-scanning`). The Play-services variant is smaller,
but it downloads its model on first use, which needs internet. Formats are
limited to the retail and shelf ones (EAN/UPC, Code 128/39/93, ITF) plus
QR. A code held still in front of the camera counts once: the same code
must leave view for 1.5 s to count again. A second unit of an item is added
with the + button.

**Making barcodes: in-store EAN-13.** GS1 reserves EAN-13 numbers starting
with 2 for use inside one shop, so they never clash with a manufacturer's
code. Galla's are `200` + the product id in nine digits + the check digit.
They are derived from the id, so there is no counter to keep in sync. A
product saved with no barcode gets one automatically, so everything in the
shop scans, including a rice bin with a label on it. Labels are an A4 PDF,
3 × 8 to a page (the common 24-up sticker sheet), shared to be printed
anywhere. They are drawn with ZXing, which runs on the phone.

**UPI QR: NPCI's deep link,**
`upi://pay?pa=<id>&pn=<payee>&am=<total>&cu=INR&tn=Bill <no>`, drawn as a
QR code with ZXing. Any UPI app fills in the payee and the amount. The `@`
in the UPI ID is left unencoded, because some apps don't decode `%40`. No
`tr` (transaction reference) is sent: that parameter is for registered
merchants, and a personal UPI ID with one can be refused.

Because Galla is offline it can't see a payment arrive. The bill is saved
as **Unpaid** when charged. The shopkeeper checks their UPI app and taps
**Paid by UPI** or **Paid in cash**. The QR appears on the screen (for the
customer to scan at the counter) and on the shared slip while the bill is
unpaid.

**The slip is one drawing.** `render/Slip.kt` draws it to a bitmap, and both
the screen and the shared PNG use that bitmap, so they can't disagree.

**Data: Room on SQLite, money in paise, quantity in thousandths.** Both are
stored as `Long`: there is no floating point anywhere money is counted.
Thousandths make loose goods exact: 250 g of a ₹62/kg item is 250 × 6200 /
1000 = 1550 paise. When the customer asks for an amount ("₹20 of
sugar"), the line costs exactly that amount and the weight is rounded to the
gram. Pricing the rounded 435 g instead would charge ₹20.01. Every stock change goes through `ShopDao.moveStock`,
which also writes a `stock_move` row, so the log always explains the count.
Checkout saves the bill, its lines and the stock changes in one
transaction. A bill line copies the name and price at the time of sale, so
editing a product never changes an old slip. Cancelling a bill marks it
cancelled and puts its items back. Stock may go below zero, because selling
an item that was never restocked is real life, and the shop must not be
blocked at the counter.

No `fallbackToDestructiveMigration`: this is the shop's only copy of its
data. A schema change needs a migration, written against the schemas
exported to `app/schemas/`.

**Backup is a file the owner keeps.** Settings → Save backup writes the
whole database through Android's file picker: to a pen drive, to Downloads,
or to Drive when online. The copy is made with SQLite's `VACUUM INTO`, which
gives a consistent single file while the app keeps running. That needs
SQLite 3.27, so the app needs **Android 11 (API 30)** or newer. Restoring
first checks that the file is a Galla backup no newer than the app, keeps
the current data in `files/before-restore.db`, then replaces the database
and restarts. Shop settings live in the database (the `setting` table), not
in SharedPreferences, so a backup holds everything.

Android's own backup (`allowBackup`) stays on as a second safety net. It is
the OS copying app data to the owner's Google account. The app takes no
part in it and gets no network access from it.

**Scope of v1:** English only. The app ID is `io.github.vivekg7.galla`,
following dhun. The debug build adds `.debug`, so it can sit beside a
release install. Only ARM builds of the native scanner are shipped (phones,
not emulators), which saves 12 MB.

## Rejected

- **Reusing Vanik's product list** (above). The other source considered was
  the Indian subset of Open Food Facts (ODbL, needs a credit line). The
  owner chose to build the catalogue by scanning.
- **Firebase or any sync.** Offline is the requirement, and sync is what
  makes Vanik need a network. Later reversed for Drive sync, when three people
  needed the same data on three phones: [002](002_sync_between_phones.md).
- **Bluetooth ESC/POS slip printing.** There is no printer. If one comes,
  the slip bitmap can be sent to it as it is.
- **Barcodes with the weight or price built in** (EAN-13 variable-measure,
  prefix 2 plus PLU plus weight). They need a label-printing scale. Revisit
  if the shop gets one.
- **Udhaar khata, online orders, analytics, multiple shops, login.** Not
  wanted. Today's takings (cash, UPI, unpaid) are on the Sales tab, because
  the bills already hold the numbers.
