# Google Play

How Galla goes on Play, and what to paste into Play Console. Why it is done
this way: [plans/004](plans/004_play_store.md).

## Once

1. **Upload key.** Make it in the git-ignored `local/` folder, and keep a
   copy of the file and its password outside this laptop. With the password
   in `KSPASS`, so it is never typed or shown (PKCS12 keeps one password for
   the store and the key):

   ```sh
   keytool -genkeypair -keystore local/upload.jks -storetype PKCS12 \
     -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
     -dname "CN=Vivek Kumar Gupta, O=Galla, C=IN" \
     -storepass:env KSPASS -keypass:env KSPASS
   ```

   Then `local/keystore.properties`:

   ```properties
   storeFile=local/upload.jks
   storePassword=…
   keyAlias=upload
   keyPassword=…
   ```

2. **Developer account** at
   [play.google.com/console](https://play.google.com/console) (one-time
   fee, identity check). A personal account must run a 14-day closed test
   with at least 12 testers before it can publish to production. An
   organisation account needs a D-U-N-S number instead.
3. **Create the app:** name `Galla`, app (not game), free. Play App Signing
   stays on, with the key Google generates. Then turn automatic protection
   off, before the first upload: Protected with Play → Automatic protection →
   Manage → Installer check off → Save → Turn off. Play turns it on for a new
   app, and adds it to every bundle uploaded while it is on (App bundle
   explorer calls those "Enhanced"). Why: [plans/004](plans/004_play_store.md).
4. **First upload:** `make check && make bundle`, then upload
   `app/build/outputs/bundle/release/app-release.aab` to Test and release →
   Internal testing. Add the shop's Google accounts as testers. Each opens
   the opt-in link once, on the phone, and installs from Play.
5. **Sync on Play builds:** copy the SHA-1 under Test and release → App
   integrity → App signing (the _app signing_ key, not the upload key). In
   Google Cloud Console, in the same project as the debug client (`galla-513900`),
   create an OAuth client: Android, package `io.github.vivekg7.galla`, that
   SHA-1. Galla's is `6E:7E:11:4D:A3:77:39:E1:84:2D:C7:C6:6B:98:AE:40:D2:7F:47:EA`
   (client "galla play").
6. **Move each shop phone** to the Play app: install it, Sign in with Google
   on its welcome screen, and check that the shop's products and bills are
   there. Then uninstall the debug build.
7. **Closed testing** (Alpha track): a release of the current bundle (upload
   it, or add it from the library if internal testing has it), in India and
   Nepal only, with the same tester list and
   thecrylocompany@gmail.com for feedback. Play reviews it only once the
   store listing and every App content form below are done; then Publishing
   overview → Send for review.
8. **Open testing and production** stay locked until Play grants production
   access (the 12 testers over 14 days above). Until then a review that
   includes either fails with a bare "Precondition check failed", and takes
   every other change in that submission down with it. Keep their changes
   under Save for later in Publishing overview.

## Each release

1. Raise `versionCode` (by one) and `versionName` in `app/build.gradle.kts`.
   Play refuses a code it has seen.
2. `make check`, then `make install` and try the change on a phone.
3. `make release`, install `app/build/outputs/apk/release/app-release.apk`
   on an emulator or a phone without the Play app, and open each screen once,
   the scanner included. Only this build is minified: what R8 strips (0.3.2
   lost ML Kit's registrar constructors, so the scanner crashed on open) never
   shows in the debug app.
4. `make bundle`, upload the `.aab` to the track, and write what changed in
   the release notes.

## Store listing

**App name** (30 characters at most): Galla: Shop Billing & Stock

**Short description** (80 at most):

> Bills, stock and UPI QR codes for your kirana shop. Works without internet.

**Full description:**

> Galla runs one kirana shop from a phone: bills, stock and UPI payments.
> Everything works without internet.
>
> BILLS
> • Scan items with the phone's camera, or find them by name
> • Weigh loose items by grams or by rupees
> • Add a discount, then charge
> • Every bill gets a payment slip with a UPI QR code for its exact total,
> paid straight to your own UPI ID
> • Share the slip on WhatsApp or anywhere else
>
> STOCK
> • Products, prices, MRP and cost price
> • Low-stock warnings
> • A history of every stock change
> • Restock by scanning what is on the shelf
> • Products without a barcode get one, printable as a sheet of labels
>
> SALES
> • Today's takings, split into cash, UPI and unpaid
> • Every bill, with its slip; mark one unpaid or cancel it, and its items go
> back into stock
>
> YOUR DATA STAYS YOURS
> • No account, no ads, no analytics
> • Save the whole shop to one backup file, and restore it on a new phone
> • Optional: sign in with the shop's Google account to keep all the shop's
> phones in step. Galla keeps a copy in that account's own Drive, in a
> hidden folder only Galla can see
>
> Galla is free and open source: github.com/vivekg7/galla

**Category:** Business. **Contact email:** thecrylocompany@gmail.com (the Crylo developer account's). **Website:**
`https://github.com/vivekg7/galla`. **Privacy policy:**
`https://github.com/vivekg7/galla/blob/main/PRIVACY.md`.

**Graphics** are designed in the Galla file in Paper (page "Play Store"),
around real captures of the app with made-up data. Exports go in the
git-ignored `local/play-store/`.

- App icon: 512 × 512 PNG, the launcher's glyph on full-bleed green. Play
  rounds the corners itself.
- Feature graphic: 1024 × 500, no transparency.
- Phone screenshots: 2 to 8, 1080 × 1920, no transparency. Play allows the
  long side at most twice the short one, which rules out the emulator's own
  1080 × 2424 screen.
- Export as sRGB. Paper exports Display P3, which a page that ignores the
  colour profile shows washed out:
  `sips --matchTo "/System/Library/ColorSync/Profiles/sRGB Profile.icc" <file>`.

To capture screens, on an emulator with the debug build:

```sh
adb shell wm size 1080x1920
adb shell settings put global sysui_demo_allowed 1
adb shell am broadcast -a com.android.systemui.demo -e command enter
adb shell am broadcast -a com.android.systemui.demo -e command clock -e hhmm 1030
adb shell am broadcast -a com.android.systemui.demo -e command network -e mobile hide
adb exec-out screencap -p > bill.png
adb shell am broadcast -a com.android.systemui.demo -e command exit
adb shell wm size reset
```

## App content (Policy → App content)

| Form               | Answer                                                                                                                                                          |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Privacy policy     | The `PRIVACY.md` link above. Play wants it in the app too: Shop links to it, at the bottom                                                                      |
| App access         | All functionality available without special access ("Continue without signing in"; sync works with any Google account)                                          |
| Ads                | No ads                                                                                                                                                          |
| Content rating     | Category: utility/productivity. No violence, sexual content, language, drugs or gambling. Users don't interact with each other in the app. Expect Everyone / 3+ |
| Target audience    | 18 and over (a tool for running a business)                                                                                                                     |
| Advertising ID     | Not used (the merged manifest has no `AD_ID` permission)                                                                                                        |
| Financial features | None. The slip's QR code holds the shop's own UPI ID; the payment happens in the customer's UPI app                                                             |
| Government, health | No                                                                                                                                                              |
| Data safety        | Below                                                                                                                                                           |

### Data safety

- Collects or shares user data: **no**. Galla has no server. Sync goes from
  the phone to the shop's own Google Drive, through the account's own
  sign-in, and Play's Data safety help exempts exactly that: data a user
  puts in their own cloud storage, under that provider's terms, that the
  developer never gets. The store listing then says "No data collection
  declared" and "No data shared with third parties".
- If Galla ever sends anything anywhere else (a server, analytics, a crash
  reporter), this answer changes with it.
