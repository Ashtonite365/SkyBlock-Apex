# M2-H2 custom menus — shared-file change requests (for H4)

Branch `m2-h2` (off `menus2` 42b4c372). No shared file was edited; H4 please apply the following.

## 1. MenuRegistry.denied(): exact carve-out for Melody's song list (src/main/kotlin/features/menus/MenuRegistry.kt)
`resolve()` checks `denied(title)` BEFORE any title/slot match, so neither the menu's title regex nor a
slotPredicate can carve the list out from inside the menu: it has to happen in `denied()`. Keep both `"harp"`
and `"melody"` in DENY_TITLES and exempt ONLY the exact selection-window title (`Melody ♫`, dump
ashpc-menudump-0924.log:7368, logged `-> denied`):

```kotlin
	/** Exact titles exempt from DENY_TITLES: Melody's song LIST (display-only Apex menu). The harp game window
	 *  ("Harp - <song>") and anything else containing "melody"/"harp" stay denied. */
	val DENY_EXCEPTIONS = setOf("melody ♫", "melody")

	fun denied(title: String) = strip(title).lowercase().let { t -> t !in DENY_EXCEPTIONS && DENY_TITLES.any { it in t } }
```

Tests in MelodyMenuTest already prove the harp side: `Harp - <song>` stays denied, MelodyParser.TITLE never
matches it, and the parser rejects harp-style slots. When applying, flip `MelodyMenuTest.listDeniedUntilH4` to
`assertFalse(MenuRegistry.denied("Melody ♫"))`. There is no harp solver in the codebase (only SolidMenus'
`"harp"/"melody"` pane-solidity deny entries, which stay as they are); nothing auto-plays.

## 2. REWRITE_PLAN.md / inventory.tsv — Custom Menus subsections (new holders, all CUSTOM_MENUS)
| Old holder | → Subsection | File | Settings |
|---|---|---|---|
| `custom-menu-skills` | skills | features/menus/skills/SkillsMenu.kt | `mode` [choice], `width` [int], `height` [int], `scale` [int], `accent` [colour], `panel-rewards`, `panel-average`, `reset` [btn] |
| `custom-menu-skill-page` | skill-page | features/menus/skills/SkillPageMenu.kt | `mode`, `width`, `height`, `scale`, `accent`, `panel-rewards`, `panel-locked`, `reset` [btn] |
| `custom-menu-dungeoneering` | dungeoneering | features/menus/skills/DungeoneeringMenu.kt | `mode`, `width`, `height`, `scale`, `accent`, `reset` [btn] |
| `custom-menu-skyblock-levelling` | skyblock-levelling | features/menus/levelling/LevellingMenu.kt | `mode`, `width`, `height`, `scale`, `accent`, `panel-ranking`, `panel-rewards`, `reset` [btn] |
| `custom-menu-spooky-rewards` | spooky-rewards | features/menus/events/SpookyRewardsMenu.kt | `mode`, `width`, `height`, `scale`, `accent`, `reset` [btn] |
| `custom-menu-hub-selector` | hub-selector | features/menus/hub/HubSelectorMenu.kt | `mode`, `width`, `height`, `scale`, `accent`, `panel-full-hubs`, `panel-server-id`, `reset` [btn] |
| `custom-menu-melody` | melody | features/menus/melody/MelodyMenu.kt | `mode`, `width`, `height`, `scale`, `accent`, `reset` [btn] |

None is ⚠ / auto-click (no chains; every click is one user click via MenuClicks). Settings totals: +7 holders,
+55 settings (5 base + reset each = 42, plus 9 panel toggles + … recount from the table when merging), 0 ⚠.
Custom Menus count in the plan header goes 12 → 19. OWNERS.tsv / SPLIT.md: add the 7 files (and the new
`features/menus/{skills,levelling,events,hub,melody}/` dirs) under H1/C1 "features/menus/**" (already covered by glob, check).

## 3. en_us.json translations (keys follow the existing custom-menu-* pattern)
For each id in skills, skill-page, dungeoneering, skyblock-levelling, spooky-rewards, hub-selector, melody:
`apex.config.custom-menu-<id>` (title), `.mode`, `.width`, `.height`, `.scale`, `.accent`, `.reset` and their
`.description` keys, copied from `custom-menu-sam` with the names:
- skills "Your Skills"; panels `panel-rewards` "Next-level rewards on tiles", `panel-average` "Skill average in header"
- skill-page "Skill pages (all skills)"; `panel-rewards` "Rewards on level tiles", `panel-locked` "Show locked levels"
- dungeoneering "Dungeoneering"
- skyblock-levelling "SkyBlock Levelling"; `panel-ranking` "Global ranking", `panel-rewards` "Rewards on level tiles"
- spooky-rewards "Spooky Rewards"
- hub-selector "Hub Selector"; `panel-full-hubs` "Show full hubs", `panel-server-id` "Show server id"
- melody "Melody's Harp (song list)" — description: "Display-only song list. The harp game itself stays vanilla; no auto-play."

## 3b. Holders added in m2c-h2 (append to the table in §2; same base settings + reset each)
| Old holder | → Subsection | File | Settings |
|---|---|---|---|
| `custom-menu-hunting-box` | hunting-box | features/menus/hunting/HuntingBoxMenu.kt | base, `reset` |
| `custom-menu-attribute-menu` | attribute-menu | features/menus/hunting/AttributeMenu.kt | base, `reset` |
| `custom-menu-leveling-rewards` | leveling-rewards | features/menus/levelling/LevellingSubMenus.kt | base, `reset` |
| `custom-menu-rewards` | rewards (incl. "Level N Rewards") | features/menus/levelling/LevellingSubMenus.kt | base, `reset` |
| `custom-menu-emblems` | emblems | features/menus/levelling/LevellingSubMenus.kt | base, `reset` |
| `custom-menu-xp-guide` | xp-guide (XP Guide + every leveling task page) | features/menus/levelling/XpGuideMenu.kt | base, `panel-completed`, `reset` |
| `custom-menu-garden-levels` | garden-levels | features/menus/skills/GardenLevelsMenu.kt | base, `panel-rewards`, `panel-locked`, `reset` |
None ⚠ / auto-click. Also: H5 PetCareMenus.kt got a 3-line change (Fann's preview pet group, commit 9a48e5f7) — tell H5.
SkillPageModel.skill is now nullable + `label` (Garden reuses parseTrack); no other file references it.

## 4. Registration
Each menu registers itself from its own `@Subscribe onInit(ClientInitEvent)` (same as SamMenu); if the rewrite's
subscriber index is generated, nothing to do; if it is a hand-maintained list, add the 7 menu objects.

## 5. Captures still needed (real slot dumps, Debug: Log Slot Dump ON, stay ≥1 s on each page)
- A MAXED skill page (no account skill is maxed): `maxed` is only tested on a derived fixture.
- Garden Levels scrolled past level 15 (only one window captured; range is read from tiles regardless).
- Hunting "Attribute Menu" with ≥1 attribute unlocked (real dump had 0; tile test is derived).
- Heart of the Forest / Core of the Mountain windows if H5 wants fixtures for its HeartMenus (HOTM id 44 captured, 1107).
