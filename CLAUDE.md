# Project rules

What Galla is and why it is built this way:
[`docs/plans/001_offline_shop_app.md`](docs/plans/001_offline_shop_app.md).
Read the plans before proposing a change; a rejected idea is listed there.

## Product rules

- **Offline first.** Everything works with no network. The only thing that
  goes online is Drive sync, when it is on (plan 002). A new feature that
  needs the network is out of scope; don't widen what sync sends either.
- **The shop's data is on this phone only.** Never add
  `fallbackToDestructiveMigration`. A schema change bumps the version and
  ships a migration with a test in `MigrationTest` (`make test-device`), and
  `Db.VERSION` moves with it. A new table or column the phones share also
  needs its sync trigger (`Db.Triggers`) and a merge rule in `Sync`.
- **Money is paise and quantity is thousandths, as `Long`.** No `Double`
  or `Float` anywhere near a price.
- **Stock changes only through `ShopDao.moveStock`** (or the transactions
  that call it), so the `stock_move` log always explains the count. Sync
  adds stock up again from that log.
- **New rows take their id from the phone's range** (`ShopDao.idBase`),
  never SQLite's autoincrement: phones that sync must not hand out the same
  id.
- **Never commit anything from Vanik.** The APK and anything extracted from
  it stay in the git-ignored `local/`. Describe what it _does_ in our own
  words, never how its code does it.

## How to work here

- Anything larger than a bug fix gets a numbered plan in `docs/plans/`:
  the problem, the decision and why, and what was rejected.
- `make fmt` before committing; `make check` must pass. A change to the
  database or to sync also needs `make test-device` on an emulator.
- Test on the phone (`make install`), not only in unit tests: the camera,
  the share sheet and the slip only show their bugs on a device.
- Keep it compact: no wrappers that only forward, no abstraction with one
  caller. Add a dependency only when twenty lines of our own will not do.
