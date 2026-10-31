<!--
SPDX-License-Identifier: GPL-3.0-or-later
-->

# Config storage (SkyBlock Apex)

On disk, under `config/apex/`:

| what | where |
|------|-------|
| settings (`CONFIG`) | `<topic>.json` directly in the folder, one file per top-level key |
| global data (`STORAGE`) | `storage/<topic>.json` |
| per-profile data (`PROFILE`) | `profiles/<profile uuid>/<topic>.json` |
| schema version | `config.version` (`<number> apex`) |

`ApexConfigLoader` reads every folder into one JSON object per storage class, runs the version upgrades
(`ConfigFixEvent`, edited through `ConfigEditor`/`JsonSlot`), then hands each object to its holders. Any problem
during a load is written to `logs/apex/config-<id>.log`, and the files touched are copied to
`backups/apex/config-<id>/` before anything is overwritten. Saves are debounced: a change waits one second (up to three while a pending future is unfinished), then the next tick writes.
