# Texture engine benchmarks

Baseline for the texture engine rewrite (TEST-PLAN.md §3). Run with the `benchTest` Gradle task; raw numbers land in
`build/bench/results-<engine>.tsv`. Harness: 5 warm-up rounds, 10 measured rounds, median per operation; allocations from
the thread allocation counter. Machine: the shared build box (8 vCPU Xeon, JDK 25), `--max-workers=2`, so treat the
numbers as relative, not absolute.

Baseline taken Mon 26 Oct 2026, 22:53 AEDT, inherited engine, branch feat-texengine-1026 (M2).

The inherited engine was removed in M10 (27 Oct 2026). Its columns below are kept as the historical baseline; the bench
tests now run the Apex engine only.

| Id | Case | inherited ns/op | inherited B/op | apex ns/op | apex B/op |
|---|---|---|---|---|---|
| B-ARM | 100 entities x 4 pieces, fresh copies | 3115.3 | 1205.4 | | |
| B-BLK | 0 replacements, 0 areas, 16^3 section x 50 | 7.2 | 0.0 | | |
| B-BLK | 0 replacements, 0 areas, no position (far terrain path) | 6.6 | 0.0 | | |
| B-BLK | 10 replacements, 0 areas, 16^3 section x 50 | 8.2 | 5.0 | | |
| B-BLK | 10 replacements, 0 areas, no position (far terrain path) | 7.7 | 5.0 | | |
| B-BLK | 10 replacements, 3 areas, 16^3 section x 50 | 9.7 | 29.1 | | |
| B-BLK | 10 replacements, 3 areas, no position (far terrain path) | 7.9 | 5.0 | | |
| B-BLK | 200 replacements, 3 areas, 16^3 section x 50 | 19.0 | 56.4 | | |
| B-BLK | 200 replacements, 3 areas, no position (far terrain path) | 15.1 | 32.0 | | |
| B-BLK | 200 replacements, 20 areas, 16^3 section x 50 | 21.0 | 56.6 | | |
| B-BLK | 200 replacements, 20 areas, no position (far terrain path) | 13.0 | 32.0 | | |
| B-SKULL | 10k lookups, 200 textures (id derivation only) | 2199.5 | 3312.0 | | |
| B-TEXT | 0 files, 100k texts (20% match) | 37.5 | 207.8 | | |
| B-TEXT | 5 files, 100k texts (20% match) | 1040.5 | 1999.7 | | |
| B-TEXT | 50 files, 100k texts (20% match) | 9412.9 | 17623.7 | | |
| B-RLD | 500 screen overrides + 200 block files + 50 text files | 9502294.5 | 5374068.0 | | |
| B-OVR | 1k stacks x 1 overrides | 227.8 | 168.0 | | |
| B-OVR | 1k stacks x 20 overrides | 8656.9 | 9934.8 | | |
| B-OVR | 1k stacks x 200 overrides | 66116.1 | 69257.9 | | |

Notes:
- B-SKULL measures only the texture id derivation from the profile (decode + url match). The texture existence check
  needs the game's resource manager and is not part of this number.
- B-OVR uses a pre-parsed legacy override list; one op = selecting the model for one stack.
- B-BLK one op = one block lookup; "no position" is the far-terrain / fallback path.
- B-RLD one op = parsing 500 screen-scoped item overrides, 200 block files and 50 text files once. Legacy model
  synthesis is not covered (it is private to a mixin in the inherited engine).
- Targets for the new engine (TEST-PLAN): no slower anywhere; B-TEXT 0-file case and B-SKULL at least 5x faster.

## B-JSON: pack JSON reader (M5)

The Apex engine reads pack files with its own lenient JSON reader (`texengine/apex/PackJson.kt`) instead of parsing
each file with kotlinx (lenient) and then again with Gson. One 39,601-character legacy model document, median of 10
rounds after 5 warm-up rounds (`benchTest --tests '*PackJsonBench'`, JDK 25, box):

| Reader | ns/op | bytes/op |
|---|---:|---:|
| kotlinx lenient + Gson (what the Apex engine would otherwise need) | 827,746 | 2,295,984 |
| Gson alone (strict input only, reference) | 154,078 | 416,408 |
| PackJson (Apex) | 130,383 | 411,416 |

## B-ARM / B-ARM-RLD: armour (M6)

Old (inherited) engine vs Apex engine on the same synthetic packs and stacks (`src/test/kotlin/texengine/ArmorBench.kt`,
`benchTest --tests '*ArmorBench'`, raw output `build/bench/b-arm.txt`). Same harness as above (5 warm-up, 10 measured
rounds, median). Packs: 125 armour sets x 4 pieces = 500 `overrides/armor_models` files, each with a `layers` base and
5 display-name variants. Taken Mon 26 Oct 2026, ~23:58 AEDT, box, JDK 25.

B-ARM one op = choosing the armour look for one worn piece (what the armour layer asks per piece per frame).
B-ARM-RLD one op = loading all armour override files once (a resource reload, F3+T).

| Case | inherited ns/op | inherited B/op | apex ns/op | apex B/op |
|---|---:|---:|---:|---:|
| B-ARM 100 entities x 4 pieces, fresh stack copies | 2,988 | 1,205 | 1,097 | 892 |
| B-ARM 100 entities x 4 pieces, same stacks | 1,645 | 924 | 1,008 | 816 |
| B-ARM 400 non-SkyBlock pieces (vanilla armour) | 577 | 328 | 286 | 264 |
| B-ARM-RLD 4 files | 211,886 | 61,712 | 132,004 | 60,948 |
| B-ARM-RLD 500 files | 11,151,916 | 6,986,288 | 8,710,276 | 7,112,988 |

Notes:
- The Apex engine builds each look's `Equippable` once per slot and reuses it, instead of building one per lookup.
- The first Apex reload run allocated 4x more than the old engine (27.8 MB for 500 files): pack files were read through
  a buffered reader with large per-file buffers. `PackFiles.read` now reads each file's bytes whole; the table above is
  after that fix. Apex reload allocation is still ~2% above the old engine at 500 files (it keeps every file sorted by
  id so "later file wins" is deterministic); time is ~22% lower.

## B-TEXT / B-COL / B-SCR: screens, text colours, text replacements (M7)

Old (inherited) engine vs Apex engine on the same synthetic packs and texts (`src/test/kotlin/texengine/ScreensBench.kt`,
`benchTest --tests '*ScreensBench'`, raw output `build/bench/b-scr.txt`). Same harness (5 warm-up, 10 measured rounds,
median). Taken Tue 27 Oct 2026, ~00:55 AEDT, box, JDK 25.

B-TEXT one op = one GUI text draw through text replacement. Text packs: N files, each matching `.*Coins.*` and
replacing `([0-9]+) Coins`. B-COL one op = one container-title colour lookup (20 overrides). B-SCR one op = choosing the
layout on a screen change (200 layout files, labels `Menu <n>`). *-RLD one op = loading all files once (a reload).

| Case | inherited ns/op | inherited B/op | apex ns/op | apex B/op |
|---|---:|---:|---:|---:|
| B-TEXT 0 files, 100k texts | 59.8 | 207.8 | 17.1 | 56.0 |
| B-TEXT 5 files, 100k texts (20% match) | 965.0 | 1,999.7 | 355.0 | 966.2 |
| B-TEXT 50 files, 100k texts (20% match) | 9,448.4 | 17,623.7 | 2,810.8 | 6,654.2 |
| B-TEXT 5 files, 10k two-part styled lines (all match) | 2,077.2 | 6,735.9 | 1,132.8 | 4,008.0 |
| B-TXT-RLD 50 text files | 3,094,929 | 606,756 | 2,142,987 | 552,672 |
| B-COL same 2 title components per frame | 975.0 | 64.0 | 220.8 | 8.0 |
| B-COL 1k new title components | 199.8 | 56.0 | 576.2 | 176.0 |
| B-SCR first of 200 layouts matches | 5,003.5 | 776 | 5,427.5 | 776 |
| B-SCR last of 200 layouts matches | 52,819 | 54,904 | 63,117 | 31,024 |
| B-SCR no layout matches | 45,950 | 56,352 | 59,020 | 30,880 |
| B-SCR-RLD 200 layout files | 4,761,919 | 1,874,960 | 3,251,764 | 2,224,232 |

Notes:
- Text replacement returns the same component when no file applies, and `.*literal.*` match regexes use a plain
  `contains` (checked equal to the regex by a randomized test).
- Title colours keep a 2-entry memo by component identity; a title is normally the same component every frame, which is
  the 4x faster row. A new component each lookup is ~0.4 µs slower than the old engine (memo miss plus the lookup).
- Layout choice runs once per screen change, not per frame; the ~10–13 µs extra in the 200-layout worst case is
  below one frame's noise, with ~45% less allocation.

## B-BLK-HOOK / B-BLK-FAR: block replacements (M8)

Old (inherited) engine vs Apex engine on the same synthetic packs and the same 16^3 section of 64 kinds of block
(`src/test/kotlin/texengine/BlocksBench.kt`, `benchTest --tests '*BlocksBench'`, raw output `build/bench/b-blk.txt`).
Same harness (5 warm-up, 10 measured rounds, median). Taken Tue 27 Oct 2026, ~01:35 AEDT, box, JDK 25.

One op = one block. B-BLK-HOOK is the whole chunk-renderer path per block: fallback scope around the game's own model
lookup with the position-less hook inside it, then the positioned lookup, with one reused `MutableBlockPos` (as vanilla
and Sodium do). B-BLK-FAR is the position-less lookup alone (far terrain, particles, other callers). Areas are 3x16x16
boxes along x. The inherited engine is measured on its selection only (`getReplacement`), which is less work than the
model it actually hands to the renderer; Apex is measured on `modelFor`, the real hook path. Both select the same
blocks in every case (asserted).

| Case | inherited HOOK ns | inherited B | apex HOOK ns | apex B | inherited FAR ns | inherited B | apex FAR ns | apex B |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| 0 replacements | 34.9 | 0 | 4.9 | 0 | 6.8 | 0 | 0.6 | 0 |
| 10 replacements, 0 areas | 46.8 | 5.0 | 11.6 | 0 | 8.4 | 5.0 | 6.4 | 0 |
| 10 replacements, 3 areas | 46.1 | 5.1 | 11.1 | 0 | 9.5 | 5.0 | 4.3 | 0 |
| 10 replacements, 20 areas | 47.0 | 5.1 | 11.9 | 0 | 10.4 | 5.0 | 4.6 | 0 |
| 200 replacements, 0 areas | 41.4 | 32.0 | 16.5 | 0 | 15.2 | 32.0 | 7.5 | 0 |
| 200 replacements, 3 areas | 51.5 | 32.4 | 15.0 | 0 | 15.1 | 32.0 | 7.6 | 0 |
| 200 replacements, 20 areas | 49.6 | 32.6 | 16.2 | 0 | 15.5 | 32.0 | 6.1 | 0 |

Notes:
- Apex keeps one flat table per island indexed by block-state id (256-entry pages), built once per reload and
  published through one volatile field; a lookup is a volatile read, the state id, two array reads and, with areas, int
  compares. No island = one null check (0.6 ns). Nothing is allocated (also checked by `ApexBlocksTest`, ThreadMXBean).
- Islands with the same entries share one table, so moving between them asks for no chunk rebuild.
