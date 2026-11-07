# 004 — Publishing on Google Play

Status: on internal testing (2026-11-06): 0.3.0 (code 3), Play App Signing
on, the Play OAuth client made. 0.3.1 (code 4) is on closed testing, for India
and Nepal, sent for review with the listing and App content (2026-11-07).
0.3.2 (code 5), the first build without Play's automatic protection, is on
internal testing (2026-11-07). On closed testing it waits as a saved change:
every Send for review since the first one returns "Precondition check
failed". The steps, the listing text and the Play Console's forms are in
[`docs/play-store.md`](../play-store.md).

## Problem

Galla reaches a phone today as a debug build installed over adb. That is
fine for the developer's phone, but not for the shop's three phones: they
need updates without a laptop, and they all need builds signed with the same
key, because Drive sync recognises the app by its package and signing key
(002). Play solves both. It also asks for things Galla has never had: a
signed app bundle, a store listing, and declarations about data and content.

## Decision

**Play App Signing, with a key Google makes.** Play holds the key that signs
what phones install. The key in `local/` becomes only the _upload_ key, which
proves an upload is ours. If it is lost, Play support can reset it, whereas a
lost signing key means a new app under a new package. No phone runs a
release build yet, so there is no existing signing key to carry over.

The cost: a release built on the laptop is signed with the upload key, not
Play's, so it can't update an install from Play, and Play can't update it.
Release builds go to phones only through Play. Development stays on the
debug build (`io.github.vivekg7.galla.debug`), which installs beside the
Play app.

**`make bundle` builds the app bundle (`.aab`) Play takes.** `make release`
still builds an APK, to try the minified build on an emulator. R8 shrinks
the release, and the first such build ran through the welcome screen, setup,
a bill, its slip and share, and a new product with no crash (2026-11-06).

**Sync needs a third OAuth client, for Play's key.** Google matches the
Android client by package and SHA-1. Play's signing key is not ours, so its
SHA-1 comes from Play Console (App integrity → App signing), and gets its own
Android client in the same Cloud project as the debug one. One project means
one hidden Drive folder: debug builds and Play builds sync with each other.

**The shop's phones move to the Play app through sync.** The Play app is a
different package from the debug build, so it installs beside it with an
empty database. Signing in on its welcome screen joins the shop from Drive
(003). Once it shows the shop's products and bills, the debug build can be
uninstalled. A phone that never synced moves with Shop → Save backup and
Restore instead.

**Internal testing first.** The internal track reaches up to 100 testers,
named by Google account, within minutes and without review. That covers the
shop's own phones. A public listing comes after it: a personal developer
account created since November 2023 must first run a closed test with at
least 12 testers for 14 days before Play opens production. An organisation
account skips that, but needs a D-U-N-S number.

**Declare no data collected, though sync sends data to Drive.** Play
counts as collected what leaves the phone, but its Data safety help exempts
data a user keeps in their own cloud storage, such as Google Drive, under
that provider's terms, when the developer never gets it. That is Galla's
sync: the shop's own account, its own Drive, and no server of ours. The
privacy policy still says what goes to Drive.

**Play's automatic protection off.** Play turns it on for a new app and adds
runtime checks to the code of every bundle uploaded while it is on, and only
Play's copy has them. On a phone where the check decides the app did not come
from Play, it kills the app at launch and opens Play's "Get this app from
Play" page. Nothing crashes, so no report reaches Android vitals. A tester's
vivo T4x (Android 16) closed 0.3.1 at every launch, though it was installed
from Play (2026-11-07). Galla's own release build opened on a stock Android 16
emulator, an Android 17 one and a moto g45. Play's APKs for that vivo,
installed on the moto, closed at launch and showed Play's page. That is the
check working as designed against a USB install, but it fits what the vivo
did. The check guards a paid app against copies, but Galla is free and its
source is public. What it can do instead is lock a shop out of its own tills.

## Rejected

- **Uploading our own key as the app signing key.** It would let a build
  from the laptop update a Play install. No phone needs that, and it makes a
  leaked or lost key ours to deal with, not Google's.
- **Staying off Play: sideloaded release APKs.** Every update would need
  each phone in hand, and "install unknown apps" left on.
- **Another store (F-Droid, Indus).** The shop's phones already have Play,
  and sign-in for sync goes through Play services anyway.
- **Declaring sync as collected** (name, address and phone, bills, UPI ID,
  products). It was the first answer, on the view that saying more costs
  nothing. But the listing would then tell a shop that Galla's developer
  gets its personal and financial data, and no one but the shop ever does.
- **Ads or a paid app.** Galla is for one shop, and ads would bring an SDK
  that goes online.
