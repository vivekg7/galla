# 003 — First run: Google sign-in first, this phone alone if not

Status: built (2026-11-06). Changes how sync (002) is turned on for a new
phone.

## Problem

Galla 0.2 opened a new install on the Shop tab, asking for the shop's name,
with sync further down the same screen. That order is wrong for the shop's
second and third phones. Someone types a shop name, maybe adds a product, and
then turns on sync, which has to warn that all of it will be replaced by the
shop's data from Drive. It also hides the choice that matters most: is this
phone one of the shop's, or the only one?

## Decision

**A new install opens on a welcome screen, with Google sign-in as its main
button.** Below it, in smaller type, "Continue without signing in". That
choice says plainly that the data then stays on this phone only. Galla
still works fully offline either way (001).

**After signing in, Galla looks in the account's Drive folder itself:**

- **Another phone already set the shop up:** this phone joins it at once and
  takes the shop's products, stock, bills and details. The welcome screen
  stays, showing progress, until the data is in. There is nothing to confirm,
  because a phone on its welcome screen has nothing of its own to lose. (Its
  empty database is still copied to `before-sync.db`, as in 002.)
- **No phone has synced yet:** this phone becomes the shop's first phone,
  then asks for the shop's details (name, address, UPI ID…). Those go up to
  Drive like any other edit.

**Without signing in,** the same details screen follows. Back returns to the
welcome screen until the shop has a name.

**The shop's name is required,** on that screen and in Shop. The slip opens
with it, and a blank name is how Galla knows a phone isn't set up yet.

**Signing in later stays in Shop → Sync between phones**, now labelled "Sign
in with Google". By then the phone has data of its own, so joining an
existing shop still asks before replacing it, as 002 describes.

The choice to stay on this phone (`app.local`) is a local setting, like
`sync.*`: it never syncs. The sign-in runs in the app's own scope, not the
screen's. Joining brings the shop's name in partway through, the Shell then
swaps screens, and the join must not be cancelled with the old screen.

## Rejected

- **Asking to confirm the join on the welcome screen.** The confirmation
  exists to protect data the phone already has, and here it has none.
- **Sign-in only, with no way to skip it.** Galla is offline first. A shop
  with one phone, or no Google account, must be able to use it.
- **Keeping the 0.2 order (details first, sync later).** It makes the second
  and third phones type details only to have them replaced.
