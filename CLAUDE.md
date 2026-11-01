# Project rules

What Galla is and why it is built this way:
[`docs/plans/001_offline_shop_app.md`](docs/plans/001_offline_shop_app.md).
Read the plans before proposing a change; a rejected idea is listed there.

## Product rules

- **Offline, enforced.** The manifest removes `INTERNET`; keep it that way.
  A feature that needs the network is out of scope, not a reason to add the
  permission back.
- **The shop's data is on this phone only.** Never add
  `fallbackToDestructiveMigration`. A schema change bumps the version and
  ships a migration tested against `app/schemas/`, and `Backup.DB_VERSION`
  moves with it.
- **Money is paise and quantity is thousandths, as `Long`.** No `Double`
  or `Float` anywhere near a price.
- **Stock changes only through `ShopDao.moveStock`** (or the transactions
  that call it), so the `stock_move` log always explains the count.
- **Never commit anything from Vanik.** The APK and anything extracted from
  it stay in the git-ignored `local/`. Describe what it _does_ in our own
  words, never how its code does it.

## How to work here

- Anything larger than a bug fix gets a numbered plan in `docs/plans/`:
  the problem, the decision and why, and what was rejected.
- `make fmt` before committing; `make check` must pass.
- Test on the phone (`make install`), not only in unit tests: the camera,
  the share sheet and the slip only show their bugs on a device.
- Keep it compact: no wrappers that only forward, no abstraction with one
  caller. Add a dependency only when twenty lines of our own will not do.
