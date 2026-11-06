# Galla

Apni Dukan, Apna Galla!

An Android app for running one kirana shop, working with no internet:

- **Inventory**: products, prices, stock, low-stock warnings, and a history
  of every stock change. Restock by scanning what is on the shelf.
- **Barcodes**: scan them with the phone's camera. Products with no barcode
  get one, printable as a sheet of labels (A4 PDF).
- **Bills**: scan or find items, weigh loose ones by grams or by rupees, add
  a discount, then charge. Each bill gets a payment slip with a **UPI QR
  code** for its exact total, paid to your own UPI ID. Share the slip on
  WhatsApp or anywhere else.
- **Backup**: save the whole shop to one file, and restore it on this
  phone or a new one.
- **Sync between phones** (optional): the shop's phones share products,
  stock, bills and shop details through the shop's Google Drive. Each phone
  keeps selling offline and catches up when it is online.

Galla goes online only to sync, and only when sync is on. Why it's built this
way: [docs/plans/001](docs/plans/001_offline_shop_app.md), and for sync
[docs/plans/002](docs/plans/002_sync_between_phones.md), which also has the
one-time Google Cloud setup sync needs.

## Build

Needs Android Studio, or just its bundled JDK, and the Android SDK.

```sh
make install   # build the debug app and install it on the phone over adb
make check     # ktlint, Android lint, unit tests
make test-device  # database migration and sync tests, on an emulator
make release   # release APK; signed if local/keystore.properties exists
make bundle    # release app bundle for Google Play
```

`local/keystore.properties` holds `storeFile`, `storePassword`, `keyAlias`
and `keyPassword`, with `storeFile` relative to the repository root. The
`local/` folder is git-ignored. Publishing on Google Play, and what to fill
in on Play Console: [docs/play-store.md](docs/play-store.md).

## Credits

- Icons: path data from Google's
  [Material Icons](https://github.com/google/material-design-icons),
  Apache 2.0.
- Barcode and QR drawing: [ZXing](https://github.com/zxing/zxing),
  Apache 2.0.
- Scanning: Google ML Kit barcode scanning (bundled model).
