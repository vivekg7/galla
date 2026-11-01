# Galla

Apni Dukan, Apna Galla!

An Android app for running one kirana shop, with no internet at all:

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

The app has no permission to use the internet; Android enforces that. Why
it's built this way: [docs/plans/001](docs/plans/001_offline_shop_app.md).

## Build

Needs Android Studio, or just its bundled JDK, and the Android SDK.

```sh
make install   # build the debug app and install it on the phone over adb
make check     # ktlint, Android lint, unit tests
make release   # release APK; signed if local/keystore.properties exists
```

`local/keystore.properties` holds `storeFile`, `storePassword`, `keyAlias`
and `keyPassword`, with `storeFile` relative to the repository root. The
`local/` folder is git-ignored.

## Credits

- Icons: path data from Google's
  [Material Icons](https://github.com/google/material-design-icons),
  Apache 2.0.
- Barcode and QR drawing: [ZXing](https://github.com/zxing/zxing),
  Apache 2.0.
- Scanning: Google ML Kit barcode scanning (bundled model).
