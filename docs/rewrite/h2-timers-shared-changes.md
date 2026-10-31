<!-- SPDX-License-Identifier: GPL-3.0-or-later -->
# H2 (Timers, chunk C2): shared-file change requests for H4

Branch `rw-h2`. H2 did not edit any shared/union file. Please apply these when merging C2.
`TimerCatalog` (`src/main/kotlin/features/timers/TimerCatalog.kt`) is the source of truth for every Timers option's
subsection and new path; the tables below are generated from it.

## 1. `src/main/resources/assets/apex/settings/migration-map.tsv` (H6-owned; rows for C2)
Replace every `timers-overlay.*` row with this block (51 rows). Changes vs the skeleton: `show-experiments` ->
`timers.cooldowns`, `show-infusion` -> `timers.rift`, `show-custom` stays `timers.general`; `show-abilities` and
`show-trevor` rows are removed (merged into `ability-cooldowns.enabled` / `trevor.enabled`, D6/D7; keep those old keys
listed only in `duplicates.tsv`, or keep their rows pointing at the D6/D7 targets if the validator needs every
duplicate old key mapped); new rows for the per-timer options.
Also change `core-misc.cookie-warning` and `core-misc.cookie-warning-minutes`: drop their `misc.core-misc.*` rows (D12, see §2).
Rows for `mining-event-timers.enabled` and `treecapitator.enabled` stay (their values live on in `_legacy`; the
holders/options were removed in C2).

```tsv
timers-overlay.enabled	timers.general.enabled	toggle	
timers-overlay.hud	timers.general.hud	position	
timers-overlay.only-pending	timers.general.only-pending	toggle	
timers-overlay.open-calendar	timers.general.open-calendar	keyBindingWithDefaultUnbound	
timers-overlay.show-custom	timers.general.show-custom	toggle	
timers-overlay.show-golden-fish	timers.fishing.show-golden-fish	toggle	
timers-overlay.show-jacobs-contest	timers.contest.show-jacobs-contest	toggle	
timers-overlay.show-events	timers.event.show-events	toggle	
timers-overlay.show-dark-auction	timers.event.show-dark-auction	toggle	
timers-overlay.show-spooky-festival	timers.event.show-spooky-festival	toggle	
timers-overlay.show-season-of-jerry	timers.event.show-season-of-jerry	toggle	
timers-overlay.show-new-year	timers.event.show-new-year	toggle	
timers-overlay.show-traveling-zoo	timers.event.show-traveling-zoo	toggle	
timers-overlay.show-cake	timers.event.show-cake	toggle	
timers-overlay.show-jerry	timers.event.show-jerry	toggle	
timers-overlay.show-arachne	timers.event.show-arachne	toggle	
timers-overlay.show-puzzler	timers.mining.show-puzzler	toggle	
timers-overlay.show-fetchur	timers.mining.show-fetchur	toggle	
timers-overlay.show-mithril-powder	timers.mining.show-mithril-powder	toggle	
timers-overlay.show-gemstone-powder	timers.mining.show-gemstone-powder	toggle	
timers-overlay.show-pickaxe-ability	timers.mining.show-pickaxe-ability	toggle	
timers-overlay.show-sky-mall	timers.mining.show-sky-mall	toggle	
timers-overlay.show-mining-event	timers.mining.show-mining-event	toggle	
timers-overlay.show-mineshaft	timers.mining.show-mineshaft	toggle	
timers-overlay.show-slayer	timers.slayer.show-slayer	toggle	
timers-overlay.show-slayer-quest	timers.slayer.show-slayer-quest	toggle	
timers-overlay.show-slayer-boss	timers.slayer.show-slayer-boss	toggle	
timers-overlay.show-slayer-pb	timers.slayer.show-slayer-pb	toggle	
timers-overlay.slayer-pb-pace	timers.slayer.slayer-pb-pace	toggle	
timers-overlay.slayer-last-kill	timers.slayer.slayer-last-kill	toggle	
timers-overlay.show-rift-time	timers.rift.show-rift-time	toggle	
timers-overlay.show-infusion	timers.rift.show-infusion	toggle	
timers-overlay.show-crimson-minibosses	timers.crimson-isle.show-crimson-minibosses	toggle	
timers-overlay.show-crimson-quests	timers.crimson-isle.show-crimson-quests	toggle	
timers-overlay.show-heavy-pearls	timers.crimson-isle.show-heavy-pearls	toggle	
timers-overlay.show-treecapitator	timers.cooldowns.show-treecapitator	toggle	
timers-overlay.show-experiments	timers.cooldowns.show-experiments	toggle	
timers-overlay.show-cookie	timers.buffs.show-cookie	toggle	
timers-overlay.cookie-warning	timers.buffs.cookie-warning	toggle	
timers-overlay.cookie-warning-minutes	timers.buffs.cookie-warning-minutes	integer	
timers-overlay.cookie-expired-warning	timers.buffs.cookie-expired-warning	toggle	
timers-overlay.cookie-warning-title	timers.buffs.cookie-warning-title	toggle	
timers-overlay.show-god-potion	timers.buffs.show-god-potion	toggle	
timers-overlay.boss-tick-timers	timers.dungeon.boss-tick-timers	toggle	
timers-overlay.spirit-bear-timer	timers.dungeon.spirit-bear-timer	toggle	
timers-overlay.terracotta-timer	timers.dungeon.terracotta-timer	toggle	
timers-overlay.ability-cooldowns	timers.cooldowns.ability-cooldowns	toggle	
timers-overlay.treecapitator	timers.cooldowns.treecapitator	toggle	
timers-overlay.lore-timers	timers.items.lore-timers	toggle	
timers-overlay.garden-tools	timers.contest.garden-tools	toggle	
timers-overlay.trevor	timers.farming.trevor	toggle	
```

## 2. `src/main/resources/assets/apex/settings/duplicates.tsv`
Keep D1-D7 as they are. Add (D12, Booster Cookie hand-merge):
```tsv
core-misc.cookie-warning,timers-overlay.cookie-warning	timers.buffs.cookie-warning	FIRST_SAVED
core-misc.cookie-warning-minutes,timers-overlay.cookie-warning-minutes	timers.buffs.cookie-warning-minutes	FIRST_SAVED
```
D5 note: the old `mining-event-timers.enabled` gated both the mining event and the Sky Mall line. Ideal is an ALL_TRUE
policy also onto `timers.mining.show-sky-mall` (`mining-event-timers.enabled,timers-overlay.show-sky-mall`); with only
ANY_TRUE/FIRST_SAVED available, skip it (Sky Mall then defaults to shown).
D11 (slayer): no value merge needed. `timers-overlay.show-slayer` keeps its key and is now the Slayer group toggle.

## 3. `SettingsRegistry` (`src/main/kotlin/settings/SettingsSections.kt`)
`SPEC_TIMERS` should list Ashton's full Timers list in this order:
`listOf("general", "fishing", "dungeon", "contest", "event", "mining", "slayer", "rift", "crimson-isle", "cooldowns", "buffs", "items", "farming")`
(or `dev.ashtonite365.apex.features.timers.TimerSection.entries.map { it.id }`). Drop `experimentation` from Timers (Experiments moved to Cooldowns).
Links (no copies): `link(TIMERS, farming.trevor)`, `link(TIMERS, farming.garden-tools)` (contest/visitor timers, shown from
Timers > Contest/Farming); see `TimerCatalog.holders` (`isHome = false`).

## 4. `src/main/kotlin/gui/config/SettingsTabsTable.kt` (settings screen shell)
- `configTabs`: remove `"mining-event-timers"` (holder deleted, D5). Set `boss-tick-timers`, `spirit-bear-timer`,
  `terracotta-timer` to `Category.TIMERS` (Timers > Dungeon home per REWRITE_PLAN §4).
- `timerSubTabs`: remove `"timers-overlay.show-abilities"`, `"timers-overlay.show-trevor"`, `"mining-event-timers"`,
  `"core-misc.cookie-warning"`, `"core-misc.cookie-warning-minutes"`; derive every `timers-overlay.*` option's sub-tab from
  `TimerCatalog.option(key)?.section` instead of the hand list. `TimerSubTab` should become the 13 `TimerSection`s
  (or be replaced by them). SettingsTabsTest will fail until this is done (C2 did not run it, per the chunk rules).

## 5. `translations/en_us.json`
Merge `translations/rewrite/timers.json` (sorted). Remove `apex.config.timers-overlay.show-abilities*`,
`apex.config.timers-overlay.show-trevor*`, `apex.config.mining-event-timers*`, `apex.config.treecapitator.enabled*`,
and (after §6) `apex.config.core-misc.cookie-warning*`.

## 6. Other chunks' files (send to the owners)
- H6, `features/misc/CoreMiscTweaks.kt`: delete the Booster Cookie warning (`cookie-warning`, `cookie-warning-minutes`,
  `parseCookieMillis`, `onTick`, `onWorld`, `lastCheck`, `warned`) and its KDoc line; it is replaced by
  `features/timers/BoosterCookie*.kt` (same CookieWarning credit carried in the new file header). Until removed, both warn.
- H6, `features/skills/SlayerOverlay.kt`: delete `onCollectTimers` (its `"slayer"` lines are already suppressed via
  `TimerCatalog.retiredIds`; replaced by `features/timers/SlayerTimers*.kt`). Keep PB storage (`slayer-pbs`), read by Timers.
  Optional: `SlayerShMerges` time messages could use `SlayerTimersHud.state.lastKill` instead of their own TimeMarks.
- H6, `features/islands/IslandHelpers.kt`: drop the `crimson-isle.miniboss-timers` (D2) and `rift-helper.time-left` (D3)
  gates on the Timers lines (Timers' `show-crimson-minibosses` / `show-rift-time` now decide).
- H5, `features/fishing/FishingExtras.kt` / `fishing-helper.golden-fish-timer`: drop the toggle gate (D1); Timers' `show-golden-fish` decides.
- H5/H6: no change needed for D6/D7 (Timers no longer has `show-abilities` / `show-trevor`; homes gate their lines).

## 7. CREDITS / NOTICE
No new projects. New files carry (LGPL-3.0-or-later) and (LGPL-2.1) notices copied from the sources they merge.
