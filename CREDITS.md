# SkyBlock Apex credits

SkyBlock Apex is a modified version of Firmament, modified by Ashton (Ashtonite365), 2026.
SkyBlock Apex is based on Firmament by Linnea Gräf (nea89o) and contributors and is licensed GPL-3.0-or-later.
Kept by hand in step with `META-INF/apex/credits.json` (do not run `scripts/gen-credits.py`: it drops hand-kept lines).
The jar copy `src/main/resources/META-INF/apex/CREDITS.md` must stay byte-identical to this file (CreditsSyncTest).
This lists projects whose code, textures or data are in SkyBlock Apex. Ported code was rewritten clean-room in Oct 2026;
those projects stay listed for the textures and data still used, and the status line says what happened to their code.
Projects that only inspired a feature (reimplemented from descriptions, no code copied) are named here only; source file headers point to this file instead of naming them.


## Trademarks and affiliation

SkyBlock Apex is an unofficial, free fan-made mod. It is not an official Minecraft product and is not approved by,
endorsed by or associated with Mojang Studios or Microsoft. It is not affiliated with, endorsed by or sponsored by
Hypixel Inc.; "Hypixel" and "Hypixel SkyBlock" are trademarks of Hypixel Inc. and are used only to say which server
the mod works with. Other project names in this file are their owners' and are used only to give credit.

## NotEnoughUpdates (NEU)

- Authors: Moulberry and the NotEnoughUpdates contributors
- Licence: LGPL-3.0-or-later
- Source: https://github.com/NotEnoughUpdates/NotEnoughUpdates
- Formerly ported code (35 files listed in the Oct 2026 port audit: mining solvers and waypoints, overlays, experiment/minion/Abiphone helpers, dungeon map and chest profit, storage pieces, accessory bag, museum, calendar, price history, streamer mode and others): rewritten clean-room on 28 Oct 2026 from behaviour specs and verified by golden differential tests; the 28 Oct 2026 re-audit (token k-gram check against the upstream sources) found no copied code
- Texture: textures/gui/storage_pane_ctm.png (connected glass panes)
- Data: Puzzler solver start block and arrow directions (MiningStuff.java); repo constants glacite_tunnel_waypoints.json for gemstone waypoints
- Data/bindings: HOTM perk cost formulas (repo constants/hotmlayout.json, variable bindings as in profileviewer/hotm/HotmTreeScreen.kt) for the HOTM cost tooltip

## Firmament

- Authors: nea89o (Linnea Gräf) and the Firmament contributors
- Licence: GPL-3.0-or-later
- Source: https://github.com/nea89o/Firmament
- SkyBlock Apex is a modified version of Firmament, modified by Ashton (Ashtonite365), 2026 (GPL-3.0-or-later, section 5(a) notice). Firmament is by Linnea Gräf (nea89o) and the Firmament contributors
- Code: SkyBlock Apex started as a fork of Firmament. The code was rewritten clean-room in Oct 2026; still Firmament-derived per the 28 Oct 2026 re-audit: the MoulConfig GUI layout XML in assets/apex/gui, some util helper signatures and data tables (util/, util/mc, util/render), the access widener entries, the mod metadata layout (fabric.mod.json) and the test item/chat fixtures (src/test/resources/testdata)

## Feesh

- Authors: Sleepy-Panda
- Licence: Apache-2.0
- Source: https://github.com/Sleepy-Panda/Feesh
- Formerly ported code (the fishing tracker and sea creature table): rewritten clean-room on 28 Oct 2026 from behaviour specs and verified by golden differential tests; the 28 Oct 2026 re-audit (token k-gram check against the upstream sources) found no copied code
- Data: sea creature names, rarities and catch messages (now features/fishing/SeaCreatureBook.kt), used by the fishing tracker

## Skyblocker

- Authors: The Skyblocker contributors
- Licence: LGPL-3.0-only
- Source: https://github.com/SkyblockerMod/Skyblocker
- Formerly ported code (salvage helper / sellable loot highlighter, mining solvers, dungeon secrets parsing): rewritten clean-room on 28 Oct 2026 from behaviour specs and verified by golden differential tests; the 28 Oct 2026 re-audit (token k-gram check against the upstream sources) found no copied code
- Code (inherited from Firmament, rewritten clean-room 28 Oct 2026; class names and the pipeline/vertex-format shape remain): CustomRenderer, CustomRenderPipelines and the gui/pip RenderLineRenderer / RenderCircleProgressRenderer (util/render)
- Data: Fetchur riddle answers (skyblock/dwarven/Fetchur.java) in the mining Fetchur solver
- Data: Fossil Excavator fossil shapes, tile counts and progress percentages (skyblock/dwarven/fossil/FossilTypes.java), pane meaning from FossilCalculations.java
- Idea (reimplemented, no code copied): 2-digit sanity cap on the action bar 'x/y Secrets' room count (skyblock/dungeon/secrets/Room.java, commit a3f25d79) -> util/skyblock/DungeonTabSecrets.kt

## SkyBlock Apex style textures

- Authors: Ashton and the SkyBlock Apex contributors
- Licence: CC-BY-4.0, Copyright 2026 Ashtonite365 (all PNG textures under assets/apex unless listed elsewhere here)
- Textures (original, generated): styles/panel/flat_minimal, styles/panel/rounded_glow, styles/panel/classic_tooltip, styles/bar/rounded_background, styles/bar/rounded_fill (HUD style packs)
- Textures (original, generated by tools/gen_apex_textures_1031.py, 31 Oct 2026): Skyglass and Forge storage screen sets (textures/gui/sprites/storageoverlay/*, storageoverlay/forge/*), Skyglass and Forge player inventory (textures/gui/apex/inventory_skyglass.png, inventory_forge.png), and the GUI sprites that replaced the inherited ones (inventory_button_background, generic_vanilla_widget, completed_commission_background, selected_pet_background, hotm_perk_preset, widget/checkbox_*, widget/button_active, gui/bar.png, gui/circle.png, gui/waypoint_*); 31 Oct 2026 (tools/gen_lock_icons_1031.py): the 6x6 Skyglass and Forge lock icons slot_locked_* (chain link) and uuid_locked_* (star)

## Better Hypixel Chat

- Authors: viciscat
- Licence: GPL-3.0-or-later
- Source: https://github.com/viciscat/BetterHypixelChat
- Formerly ported code (centred-line and separator detection, splitLines hook): rewritten clean-room on 28 Oct 2026 from behaviour specs and verified by golden differential tests; the 28 Oct 2026 re-audit (token k-gram check against the upstream sources) found no copied code

## SkyblockAddons

- Authors: Biscuit Development & contributors
- Licence: MIT, Copyright (c) 2021 Biscuit Development (META-INF/apex/LICENSES/LICENSE-SkyblockAddons.txt)
- Source: https://github.com/BiscuitDevelopment/SkyblockAddons
- Behaviour idea only (no code, no art, no assets): the fancy warp menu (IslandWarpGui: island map, purple portal markers, click-to-warp, locked warps dimmed) -> Fast Travel fancy warp map (features/menus/skyblock/FancyWarp.kt, FastTravelMenu.kt), an original implementation over Apex's own layout

## SkyHanni

- Authors: hannibal002 & contributors
- Licence: LGPL-2.1
- Source: https://github.com/hannibal002/SkyHanni
- Formerly ported code (112 files listed in the Oct 2026 port audit, the H1-H6 and garden-1020 ports): rewritten clean-room on 28 Oct 2026 from behaviour specs and verified by golden differential tests; the 28 Oct 2026 re-audit (token k-gram check against the upstream sources) found no copied code (the last two, the sea creature extras and per-creature settings with the nearby health list, followed in the 28 Oct residue pass)
- Data: Thunder Sparks highlight skull texture from SkyHanni-REPO Skulls.json (MIT) (the highlight itself was rewritten clean-room)
- Idea only (no code copied): a Special Zealot is an Enderman carrying an End Portal Frame (MobHighlight.kt, Special Zealot highlighter) -> Special Zealot alert + path, Dragon Tracker options (features/misc/SpecialZealot.kt, Combat, ⚠ off by default)

## Stella

- Authors: Eclipse-5214 and the Stella contributors
- Licence: LGPL-3.0
- Source: https://github.com/Eclipse-5214/stella
- Idea (reimplemented 25 Oct 2026, no code copied): room-local/real coordinate transform by the room corner and a quarter-turn rotation (api/dungeons/map/Room.kt) -> features/dungeon/rooms/DungeonRooms.kt

## Odin

- Authors: odtheking and the Odin contributors
- Licence: BSD-3-Clause
- Source: https://github.com/odtheking/Odin
- Formerly ported code (42 files listed in the Oct 2026 port audit: chat commands, terminal solver/simulator/extras, dungeon status HUDs, splits, boss timers, devices, puzzles, Kuudra and kit features): rewritten clean-room on 28 Oct 2026 from behaviour specs and verified by golden differential tests; the 28 Oct 2026 re-audit (token k-gram check against the upstream sources) found no copied code
- Data: 8-ball answers and chat emote table (features/impl/skyblock/ChatCommands.kt) -> features/chat/PartyCommands.kt. Licence text in LICENSES/BSD-3-Clause.txt
- Data: Boulder solution table (src/main/resources/assets/odin/puzzles/boulder-solutions.json) -> src/main/resources/assets/apex/puzzles/boulder-solutions.json
- Data: Arrow Align wall layouts (features/impl/boss/ArrowAlign.kt) -> features/kit/devices/KitDevicesLogic.kt (the code there was rewritten clean-room on 28 Oct 2026 from a behaviour spec)
- Data: quiz answers (quiz-answers.json) and Ice Fill bundled paths (ice-fill-floors.json) (src/main/resources/assets/odin/puzzles/) -> src/main/resources/assets/apex/kit/puzzles/. Licence text in LICENSES/BSD-3-Clause.txt and assets/apex/kit/puzzles/LICENSE-Odin.txt
- Data: dungeon room identity fields from rooms.json (names, types, shapes, cores, secret and crypt counts), bundled as assets/apex/dungeon-rooms/secret-rooms.json by tools/build-secret-rooms.py from tools/secret-rooms-src/kit-rooms.json, and the room core hash convention (WorldScan.getRoomCore) used to match them -> features/dungeon/SecretRooms.kt. Licence text in LICENSES/BSD-3-Clause.txt
- Ideas only (Nearest Locked Door + Key Path, features/kit/dungeon/DoorLogic.kt, merged into Key Highlight): door slots on the 32-block room grid from -200,-200, wither door = coal block, blood door = red terracotta, key counting from 'has obtained Wither/Blood Key' chat (Odin DoorHighlight.kt / ScanUtils); door slots between map room cells (Skytils, AGPL-3.0, dungeon map); key/door chat lines (SkyHanni, LGPL-2.1, DungeonAPI). Written from scratch, no code copied.
- Idea (conventions checked against, no code copied): map head facing angle = 180 + yaw and map decoration yaw = rot * 360 / 16 (features/impl/dungeon/map/MapRenderer.kt, MapScan.kt) -> features/dungeon/DungeonMap.kt head-facing
- Data (bundled): Kuudra pearl lineup table from features/impl/nether/PearlWaypoints.kt -> assets/apex/kit/kuudra/pearl-lineups.json (⚠ Pearl Lineup Waypoints, kuudra-supplies)
- Data: Teleport Maze pad layout (TPMazeSolver room-relative pad table) -> features/dungeon/TeleportMaze.kt

## Dungeon Rooms Mod

- Authors: Quantizr and the Dungeon Rooms Mod contributors
- Licence: GPL-3.0-or-later
- Source: https://github.com/Quantizr/DungeonRoomsMod
- Idea (reimplemented, no code copied): tolerant action-bar secrets section parsing and the 'all secrets found' signal, now only the tolerant parsing (dungeons/catacombs/Waypoints.java, commit 4d9ce890) -> util/skyblock/DungeonTabSecrets.kt

## SkyHanni-REPO

- Authors: hannibal2 and the SkyHanni-REPO contributors
- Licence: MIT (src/main/resources/assets/apex/plus-repo/LICENSE-SkyHanni-REPO.txt)
- Source: https://github.com/hannibal002/SkyHanni-REPO
- Data (bundled snapshot, unmodified; also downloaded at runtime if enabled): constants/ Skulls, Rift parkour (RiftRace, RiftUpsideDownParkour, RiftLavaMazeParkour, RiftTubulator), EnigmaSouls, DanceRoomInstructions, RiftEffigies, rift/RiftWoodenButtons, rift/WiltedBerberisLocations, TrophyFish, SeaCreatures, fishing/ExcludedSeaCreatureAreas, foraging/StarlynContestRewards, DungeonHubRaces, CrimsonIsleReputation, DeepCavernsParkour, island_graphs/THE_RIFT -> src/main/resources/assets/apex/plus-repo/ (see SNAPSHOT.txt), loaded by util/skyhanni/PlusRepoSource
- Data (bundled snapshot fea17c0b, unmodified): constants/Garden.json and constants/AnitaUpgradeCosts.json (garden features; see SNAPSHOT.txt)

## NEU item repo (NotEnoughUpdates-REPO)

- Authors: the NotEnoughUpdates contributors
- Licence: as published by the repo
- Source: https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO
- Data (downloaded at runtime, not bundled): item, recipe, NPC and constants data read by repo/RepoManager
- Data (downloaded zip only, not bundled): constants/Enchants.json loreName -> nbtName, used by the Enchant Item picker to map enchant names to ids (EnchantConflicts.kt); no conflict groups are taken from it (it has none)
- Data (bundled snapshot, unmodified, offline fallback only): constants/enchants.json ("enchant_pools" conflict groups for the Enchant Item picker, features/menus/enchant/EnchantConflicts.kt). Licence: MIT, Copyright (c) 2020 Moulberry (src/main/resources/assets/apex/core-repo/LICENSE-NEU-REPO.txt, SNAPSHOT.txt)
- Data (bundled, derived): pre-resource-pack item looks (vanilla item id, head skin, leather colour, glint, potion) from items/ at 0ba055911be76fe3d7cb0dbed964063912c8f036, gap-filled from 20a302457375ddfd7005e192486ac86781729b0a -> assets/apex/old_look/old_look.json, used by Classic Item Looks (features/misc/oldlook). Licence: MIT, Copyright (c) 2020 Moulberry (assets/apex/old_look/LICENSE-NEU.txt)

## Detexturify

- Authors: Starred
- Licence: BSD-3-Clause (notice: assets/apex/old_look/LICENSE-DYNAMIC-RULES.txt)
- Source: https://github.com/skies-starred/Detexturify
- Data (derived rules, no code copied): which vanilla item an item shows by state (attunement mode swords, katana cooldown, quiver arrow) @ f7c685bc519960bb9bc631f23e25dae93afc7bcd -> the "dynamic" rules in assets/apex/old_look/old_look.json, used by Classic Item Looks (features/misc/oldlook)
- Idea: changing what ItemModelResolver draws for an item (and the swap animation lookups) instead of editing the item itself

## PackDisabler

- Authors: Noamm9
- Licence: CC0-1.0
- Source: https://github.com/Noamm9/PackDisabler
- Data (derived rules, no code copied): attunement and katana swords, fungi cutter mode mushrooms, carnival shovel tiers @ 20633a24cdb490c9d6d4fcb2ddb215636e298069 -> the "dynamic" rules in assets/apex/old_look/old_look.json, used by Classic Item Looks (features/misc/oldlook)

## MoulConfig

- Authors: Linnea Gräf, Moulberry and the NotEnoughUpdates contributors
- Licence: LGPL-3.0-or-later
- Source: https://github.com/NotEnoughUpdates/MoulConfig
- Library (shaded into the jar, local build of master 4eaeb73c for 26.3): config GUI used by the /apex settings screen (compat/moulconfig)

## Jarvis

- Authors: Linnea Gräf and the Jarvis contributors
- Licence: LGPL-3.0-or-later
- Source: https://github.com/nea89o/jarvis
- The vendored HUD editor (src/vendor/jarvis) was removed in Oct 2026 and replaced by SkyBlock Apex's own HUD editor (gui/hud/editor); no Jarvis code remains

## Elite (elitebot.dev)

- Authors: the Elite (elitebot.dev) developers
- Licence: API terms of use (no code copied)
- Source: https://elitebot.dev
- API: live AH lowest-BIN data from api.eliteskyblock.com (repo/LiveAuctionPrices.kt); contest schedule idea

## Hypixel SkyBlock Resource Pack

- Authors: Hypixel Inc.
- Licence: Hypixel Inc.'s own licence (not GPL; the LICENSE inside the pack)
- Source: https://api.hypixel.net/
- Not included in any jar. (C) Hypixel Inc., under its own licence, downloaded from Hypixel at runtime in every build (api.hypixel.net/v2/resources/packs, SHA-1 checked, cached in config/apex/cache), not distributed by Apex
- Only the kept parts are used (font glyphs, tooltip backgrounds, text shader; features/misc/BuiltinHypixelPack.kt)

## Ideas only

- Ideas only (no code copied; credited by courtesy): Skytils (AGPL-3.0), Partly Sane Skies (BSD-3-Clause), BtrBz (LGPL-3.0), Bazaar Utils (CC BY-NC-4.0), SkyBlock Profile Viewer (meowdding/skyblock-pv), Cowlection, Devonian (GPL-3.0), SkyOcean, FurfSky, CrystalsHud (LGPL-3.0), Patcher, SkyBlock Enhancements, Scale Me, Zoomify, Enhanced Sound Control, Stfu, Soopy/SkyHelper, Low On Fire (resource pack by Haikis, ARR; idea for the "Lower fire overlay" option), Enhanced Chat (GPL-3.0), Riccio Fishing Utils, SkyblockAddons Unofficial (LGPL-3.0).
- Connected texture idea: Fusion / Connected Glass by SuperMartijn642 (All Rights Reserved). Facts only (features/render/connected): no code, textures, models or wiki text used; tile art is generated at runtime from the currently loaded textures.
- Item Wiki hold-to-open UX (features/items/wiki/ItemWiki.kt, WikiHold.kt): the hold-a-key-over-an-item interaction (a hint line and a progress bar under the tooltip that appear only while the key is held, a linear fill, reset on release, open when full) is inspired by Create's Ponder (MIT). Idea only: no code, names or assets copied. Page data is read from the Hypixel SkyBlock wiki (hypixelskyblock.minecraft.wiki, MediaWiki API) at runtime.
- Item Wiki page look and fonts (features/items/wiki/WikiDoc.kt, WikiLayout.kt, ItemWikiScreen.kt; assets/apex/font/wiki): the overlay reproduces the Hypixel SkyBlock wiki's own page layout (infobox, tables, headings, navboxes, colours), measured from the live site; written from scratch, no wiki skin code copied. Text uses Noto Sans and Noto Serif (subsets, SIL Open Font License 1.1, Copyright 2022 The Noto Project Authors; licence text in LICENSES/OFL-1.1.txt). Page images are downloaded from the wiki at runtime and cached locally; none are bundled.
- Blaze puzzle solver (features/dungeon/BlazeOrder.kt, BlazeSolver.kt; rebuilt from scratch): the direction signal (reward chest with iron bars above, read at its start position before any kill: at the bottom of the room = lowest HP first, at the top = highest HP first, per the Hypixel wiki) is an idea from Skytils (AGPL-3.0, BlazeSolver.detectOrientation), cross-checked against Odin (BSD-3-Clause, BlazeSolver: Higher Blaze = ascending, Lower Blaze = descending). Ideas only, no code copied.
- Livid finder, F5/M5 (features/dungeon/LividLogic.kt, LividSolver.kt; rebuilt from scratch): the ceiling wool positions and colour table were verified against the sources of Odin (BSD-3-Clause, features/impl/boss/LividSolver.kt: wool at (5, 108, 43), white Vendetta, magenta Crossed, yellow Arcade, lime Smile, gray Doctor, purple Purple, blue Scream, green Frog, red Hockey, "<Name> Livid" player, no box while blind), SkyHanni (LGPL-2.1, features/dungeon/DungeonLividFinder.kt: wool at (6, 109, 43), "§c﴾ §c§lLivid" nametag colour, red at boss start) and Skytils (AGPL-3.0, features/impl/dungeons/LividFinder.kt: stained glass at (13, 107, 25) in the old arena, pink/light grey aliases, wait for blindness to end, box under the nametag). Upstream Firmament has no Livid finder. Ideas only, no code copied.
- Dungeon room corner lookup (features/dungeon/RoomCornerFinder.kt, used by DungeonRooms.currentRoomFrame and PuzzleRoom): finding the roof by scanning the room's centre column down and skipping gold blocks is an idea from Odin (BSD-3-Clause, features/impl/dungeon/map/WorldScan.kt getRoomCore). Idea only, no code copied.
- Creeper Beams solver (features/dungeon/CreeperBeamsLogic.kt, CreeperBeamsSolver.kt; rebuilt from scratch, replaces the earlier port of Odin's lantern table, which is no longer shipped): finding the room by the plain creeper standing on a sea lantern, pairing beam ends by a line through the creeper, counting prismarine as a beam end and hiding pairs whose ends are both prismarine are ideas from Skytils (AGPL-3.0, CreeperSolver) and Skyblocker (LGPL-3.0, skyblock/dungeon/puzzle/CreeperBeams). Odin (BSD-3-Clause, BeamsSolver + creeper-beams-solutions.json) was read only to check the pairing geometry offline. Ideas only, no code copied.
- Dungeon name-tag to body pairing (features/dungeon/TagBodyPairing.kt, used by the starred-mob highlight in PuzzleSolvers and by BloodMobEsp): the body overlapping the tag's armour stand box moved one block down, player bodies only with a version 2 UUID, invisible bodies skipped are ideas from Odin (BSD-3-Clause, features/impl/dungeon/Highlight.kt); the name stand's entity id being its body's id + 1 and version 4 UUID = real player are from SkyHanni (LGPL-2.1, utils/MobUtils.kt, data/mob/MobFilter.kt). Ideas only, no code copied.
- Ice Fill solver (features/dungeon/IceFillPath.kt, IceFillSolver.kt; rebuilt from scratch, Hamiltonian-path DFS with parity, connectivity and dead-end pruning): layer layout (NxN ice grid, start on the first row, exit tile past the last row, layers one block apart) cross-checked against Skytils (AGPL-3.0, IceFillSolver) and the bundled Odin data. Idea only, no code copied. The optional bundled patterns are still Odin's ice-fill-floors.json (BSD-3-Clause, see the Odin entry and assets/apex/kit/puzzles/LICENSE-Odin.txt), now consumed by IceFillSolver.kt through IceFillFloors.kt.
- Ice Path (silverfish) solver (features/dungeon/IcePathRoute.kt, IcePathSolver.kt; new, written from scratch, breadth-first search over slide moves): room layout (reward chest on packed ice with a hopper two above and a stone-brick wall behind, 17x17 board at the chest height starting four blocks in front of it, exit cells on the chest-side row next to the chest column) learned by reading Skytils (AGPL-3.0, IcePathSolver) and Skyblocker (LGPL-3.0, dungeon/puzzle/IcePath). Read for reference only, no code copied.
- "Lower fire overlay" mode of the Fire Overlay setting (Misc > Render > Fire Overlay; formerly labelled Low on Fire; features/misc/LowFireOverlay.kt, MiscRender.kt, mixins/HideFireOverlayPatch.java): the idea of keeping the first-person fire overlay low so it covers only the bottom of the screen comes from the "Low On Fire" resource pack by Haikis (https://modrinth.com/resourcepack/low-on-fire, licensed ARR). Idea only: no textures, files or code from the pack are used; the overlay is cropped and lowered in code. To be reviewed in the final credits pass.

## LibNinePatch

- Authors: Juuxel
- Licence: MPL-2.0 (META-INF/apex/LICENSES/LICENSE-LibNinePatch-MPL-2.0.txt)
- Source: https://github.com/Juuxel/LibNinePatch
- Library (unmodified, shaded inside MoulConfig): nine-patch texture rendering for the settings GUI

## Manningham Mills (mm)

- Authors: Chocohead (published by shedaniel as me.shedaniel:mm)
- Licence: MPL-2.0 (META-INF/apex/LICENSES/LICENSE-ManninghamMills-MPL-2.0.txt)
- Source: https://github.com/Chocohead/Fabric-ASM
- Library (unmodified nested jar mm-2.4.1): early-riser entrypoint (init/EarlyRiser)

## MixinExtras

- Authors: LlamaLad7
- Licence: MIT, Copyright (c) 2022-present LlamaLad7 (META-INF/apex/LICENSES/LICENSE-MixinExtras.txt)
- Source: https://github.com/LlamaLad7/MixinExtras
- Library (unmodified nested jar mixinextras-fabric-0.5.4): extra mixin injectors

## Hypixel Mod API

- Authors: Hypixel Inc.
- Licence: MIT, Copyright (c) 2024 Hypixel Inc. (META-INF/apex/LICENSES/LICENSE-HypixelModAPI.txt)
- Source: https://github.com/HypixelDev/ModAPI
- Library (unmodified nested jar hypixel-mod-api): Hypixel Mod API packets (location, party)

## NEU RepoParser

- Authors: Linnea Gräf and the NotEnoughUpdates contributors
- Licence: BSD-2-Clause, Copyright 2023 Linnea Gräf (META-INF/apex/LICENSES/LICENSE-RepoParser-BSD-2-Clause.txt)
- Source: https://github.com/NotEnoughUpdates/RepoParser
- Library (unmodified, shaded and relocated to dev.ashtonite365.apex.deps.repo): reads the NEU item repo

## Cloth BasicMath

- Authors: shedaniel
- Licence: Unlicense (META-INF/apex/LICENSES/LICENSE-cloth-basic-math-Unlicense.txt)
- Source: https://github.com/shedaniel/cloth-basic-math
- Library (unmodified nested jar basic-math-0.6.1)

## PrismarineJS minecraft-data

- Authors: the PrismarineJS contributors
- Licence: MIT (declared in the upstream README), Copyright (c) 2015-present PrismarineJS contributors (META-INF/apex/LICENSES/LICENSE-minecraft-data.txt)
- Source: https://github.com/PrismarineJS/minecraft-data
- Data: legacy item, enchantment and effect ids (legacy_data/*.json): unused, removed 31 Oct 2026 and no longer shipped in the jar

## Noto Sans and Noto Serif

- Authors: The Noto Project Authors
- Licence: OFL-1.1, Copyright 2022 The Noto Project Authors (LICENSES/OFL-1.1.txt)
- Source: https://github.com/notofonts/latin-greek-cyrillic
- Fonts (subsets): Item Wiki overlay text (assets/apex/font/wiki)

## Hypixel SkyBlock Wiki

- Authors: the Hypixel SkyBlock Wiki contributors
- Licence: CC BY-NC-SA 3.0 (https://creativecommons.org/licenses/by-nc-sa/3.0/)
- Source: https://hypixelskyblock.minecraft.wiki
- Item Wiki overlay (F12): page text and images are read from the wiki at runtime and shown with attribution; nothing from the wiki is bundled

## Rules

Only GPL, LGPL, MIT, BSD, Apache-2.0 or CC-BY (with attribution) code and textures. AGPL and CC BY-NC(-SA)
projects are reimplemented, never copied. Never Aaron's Mod, NC/NC-SA or all-rights-reserved assets. Every
copied file needs a header naming its source and licence, plus an entry in credits.json.

## Fast Travel map image

- textures/gui/apex/fast_travel_map.png: the Lotus Atoll region (below Backwater Bayou) was redrawn by us on 31 Oct 2026 (original drawing, tools/fast-travel-map/gen_atoll_patch.py; CC-BY-4.0, Ashtonite365). No Hypixel or wiki art is left in the image.
- The rest of the map is the old image supplied by Ashton; its original source is unconfirmed and it is not claimed as ours. To be confirmed or replaced.
- The previous image (with the wiki's Lotus Atoll render) was removed from the source tree on 1 Nov 2026 and is kept privately; it is not in the source repo or the jar.

## Unknown-origin textures

- textures/gui/sprites/tooltip/rarity/*: rarity tooltip sprites, origin unknown, not confirmed as ours. TODO: find the source or redraw them.
