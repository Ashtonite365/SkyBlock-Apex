// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.rewrite.h3

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.features.fishing.NearbySeaCreatures
import dev.ashtonite365.apex.features.fishing.NearbySeaCreatures.Creature
import dev.ashtonite365.apex.features.misc.ActionBarStats
import dev.ashtonite365.apex.features.misc.CompactTabList
import dev.ashtonite365.apex.features.misc.CustomScoreboard
import dev.ashtonite365.apex.features.skills.SkillOverlay
import dev.ashtonite365.apex.gui.config.HudPosition
import dev.ashtonite365.apex.gui.hud.HudSnap
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.HudSettings
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.SettingsRegistry
import dev.ashtonite365.apex.util.bareText

class HudChunkTest {
	private val map = MigrationMap.bundled()

	@Test
	fun hudSubsectionsMatchPlan() {

		assertEquals(listOf("style", "info-displays", "deployables", "tab-list", "scoreboard", "stat-bars", "skills", "sacks"), HudSettings.SUBSECTIONS)
		val inMap = HudSettings.hudEntries(map).map { it.newPath.subsection }.toSet()
		assertEquals(HudSettings.SUBSECTIONS.toSet(), inMap)
		val infoHolders = setOf("hud", "gap-misc", "misc-tweaks")
		for (e in HudSettings.hudEntries(map)) {
			if (e.newPath.subsection == "info-displays") assertTrue(e.oldConfig in infoHolders, e.oldKey)
			else if (e.oldConfig == "hud") assertEquals("style", e.newPath.subsection, e.oldKey)
			else assertEquals(HudSettings.HOLDERS[e.oldConfig], e.newPath.subsection, e.oldKey)
		}

		assertEquals(125, HudSettings.hudEntries(map).size)
	}

	@Test
	fun registerClaimsAndLinksPositions() {
		val reg = SettingsRegistry.default(map)
		val homes = HudSettings.registerInto(reg, map)

		assertEquals(HudSettings.SUBSECTIONS.toSet(), reg.subsections(MainSection.HUD).map { it.id }.toSet())
		assertTrue(homes.isNotEmpty())
		assertTrue(homes.none { it.main == MainSection.HUD })
		assertEquals(homes, reg.linked(MainSection.HUD))
		assertNotNull(homes.firstOrNull { it.path == "fishing.fishing-helper" })

		HudSettings.registerInto(reg, map)
		assertThrows<IllegalArgumentException> { reg.claim(SettingPath.parse("hud.stat-bars.enabled"), "other") }
	}

	@Test
	fun keybindsGroupedBySubsection() {
		val kb = HudSettings.keybindsBySubsection(map)
		assertEquals(setOf("stat-bars"), kb.keys)
		assertEquals(7, kb["stat-bars"]!!.size)
		assertTrue(kb["stat-bars"]!!.any { it.key == "open-editor" })
	}

	@Test
	fun hudHasNoRiskyDefaultOn() {

		assertEquals(listOf("gap-misc.players-in-range"), HudSettings.riskyEntries(map).map { it.oldKey })
		val o = dev.ashtonite365.apex.test.gui.config.AllRegisteredConfigs.configs.single { it.name == "gap-misc" }.sortedOptions.single { it.propertyName == "players-in-range" }
		assertEquals(false, o.default())
	}

	@Test
	fun dragSnapsToScreenAndOtherEdges() {
		val start = HudSnap.Rect(100, 100, 40, 20)

		assertEquals(HudSnap.Snapped(150, 135, null, null), HudSnap.drag(start, 50, 35, 400, 300))

		assertEquals(HudSnap.Snapped(150, 130, null, 150), HudSnap.drag(start, 50, 30, 400, 300))

		assertEquals(0, HudSnap.drag(start, -97, 0, 400, 300).x)

		val c = HudSnap.drag(start, 81, 0, 400, 300)
		assertEquals(180, c.x); assertEquals(200, c.guideX)

		val other = HudSnap.Rect(250, 0, 30, 30)
		val o = HudSnap.drag(start, 152, 0, 400, 300, listOf(other))
		assertEquals(250, o.guideX)
		assertTrue(o.x == 250 || o.x == 210)

		assertEquals(253, HudSnap.drag(start, 153, 0, 400, 300, listOf(other), enabled = false).x)
	}

	@Test
	fun dragClampsOnScreenAndNudges() {
		val r = HudSnap.Rect(10, 10, 40, 20)
		val s = HudSnap.drag(r, 1000, -1000, 400, 300)
		assertEquals(360, s.x); assertEquals(0, s.y)
		assertEquals(0, HudSnap.clamp(5, 500, 400))
		assertEquals(HudSnap.Rect(20, 9, 40, 20), HudSnap.nudge(r, 1, 0, true, 400, 300).let { HudSnap.nudge(it, 0, -1, false, 400, 300) })
	}

	@Test
	fun savedPositionRoundTripsAcrossScreenSizes() {
		val start = HudSnap.Rect(100, 100, 40, 20)
		val snapped = HudSnap.drag(start, 257, 178, 400, 300)
		assertEquals(360, snapped.x); assertEquals(280, snapped.y)
		val pos = HudPosition(0, 0, 1f)
		pos.place(snapped.x, snapped.y, 40, 20, 400, 300)
		val json = Json.encodeToString(HudPosition.serializer(), pos)
		val back = Json.decodeFromString(HudPosition.serializer(), json)
		assertEquals(pos, back)
		assertEquals(360, back.resolve(40, 20, 400, 300).x())

		val big = back.resolve(40, 20, 800, 600)
		assertEquals(760, big.x()); assertEquals(580, big.y())
	}

	@Test
	fun statBarsParse() {
		val p = ActionBarStats.parse("§c1,234/1,500❤     §a250§a❈ Defense     §b893/893✎ Mana")
		assertEquals(1234, p.health); assertEquals(1500, p.maxHealth); assertEquals(250, p.defence); assertEquals(893, p.maxMana)
		assertFalse(ActionBarStats.parse("§eYou are now AFK").any)
	}

	@Test
	fun scoreboardFilters() {
		assertFalse(CustomScoreboard.keepLine("   ", true, false, false))
		assertTrue(CustomScoreboard.keepLine("   ", false, false, false))
		assertFalse(CustomScoreboard.keepLine("www.hypixel.net", false, true, false))
		assertFalse(CustomScoreboard.keepLine("10/17/26 m12AB", false, false, true))
		assertTrue(CustomScoreboard.keepLine("10/17/26 m12AB", false, false, false))
		assertTrue(CustomScoreboard.keepLine("Purse: 1,000", true, true, true))
	}

	@Test
	fun tabListSortAndClean() {
		val lines = listOf("Players (3)", "[10] bob", "[300] Ashton ♲", "[50] alice").map { Component.literal(it) }
		val byLevel = CompactTabList.sortPlayers(lines, CompactTabList.PlayerSort.LEVEL, false).map { it.bareText }
		assertEquals(listOf("Players (3)", "[300] Ashton ♲", "[50] alice", "[10] bob"), byLevel)
		val byName = CompactTabList.sortPlayers(lines, CompactTabList.PlayerSort.NAME, true).map { it.bareText }
		assertEquals(listOf("Players (3)", "[10] bob", "[300] Ashton ♲", "[50] alice"), byName)
		assertEquals("Ashton", CompactTabList.cleanPlayerText("[300] Ashton ♲", true, true))
	}

	@Test
	fun skillOverlayParse() {
		assertEquals(Triple(12.5, "Farming", "1,234/5,000"), SkillOverlay.parseGain("§3+12.5 Farming (1,234/5,000)".replace("§3", "")))
		assertEquals(Triple(1200.0, "Mining", "45%"), SkillOverlay.parseGain("+1,200 Mining (45%)"))
		assertNull(SkillOverlay.parseGain("+12 Coins"))
		assertEquals(3766.0, SkillOverlay.remainingXp("1,234/5,000"))
		assertEquals(1.5e6 - 2000, SkillOverlay.remainingXp("2k/1.5M"))
		assertNull(SkillOverlay.remainingXp("45%"))
	}

	private val known = listOf("Sea Walker" to false, "Thunder" to true, "Lord Jawbus" to true, "Squid" to false)

	@Test
	fun seaCreatureFromTags() {
		assertEquals(Creature("Sea Walker", false, "1,500", "2k"), NearbySeaCreatures.fromTag("[Lv45] Sea Walker 1,500/2k❤", known))
		assertEquals(Creature("Lord Jawbus", true, "40M", "100M"), NearbySeaCreatures.fromTag("[Lv600] Lord Jawbus 40M/100M❤", known))
		assertNull(NearbySeaCreatures.fromTag("Sea Walker", known))
		assertNull(NearbySeaCreatures.fromTag("[Lv1] Zombie 10/10❤", known))
		assertEquals(1.5e6, NearbySeaCreatures.parseShort("1.5M"))
		assertEquals(3e9, NearbySeaCreatures.parseShort("3B"))
	}

	@Test
	fun seaCreatureSummaryOnlyByDefault() {
		val cs = listOf(Creature("Squid", false, "50", "100"), Creature("Thunder", true, "1M", "5M"))
		val (summary, list) = NearbySeaCreatures.lines(cs, "1m 5s", NearbySeaCreatures.Options(false, 5, true))
		assertEquals(listOf("§bSea creatures: §f2 §7(1m 5s)"), summary)
		assertTrue(list.isEmpty())
		assertTrue(NearbySeaCreatures.lines(emptyList(), null, NearbySeaCreatures.Options(true, 5, true)).first.isEmpty())
	}

	@Test
	fun seaCreatureHealthListMergedOptions() {
		val cs = listOf(
			Creature("Squid", false, "90", "100"),
			Creature("Sea Walker", false, "10", "100"),
			Creature("Thunder", true, "4M", "5M"),
			Creature("Lord Jawbus", true, "10M", "100M"),
		)
		val list = NearbySeaCreatures.lines(cs, null, NearbySeaCreatures.Options(true, 3, true)).second
		assertEquals(listOf(
			"§6Lord Jawbus §c10M§f/§a100M§c ❤",
			"§6Thunder §a4M§f/§a5M§c ❤",
			"§bSea Walker §c10§f/§a100§c ❤",
			"§7+1 more",
		), list)

		val raw = NearbySeaCreatures.lines(cs, null, NearbySeaCreatures.Options(true, 10, false)).second
		assertEquals("§bSquid §a90§f/§a100§c ❤", raw.first())
		assertEquals(4, raw.size)
	}

	@Test
	fun seaCreatureMigrationKeepsBothOldSettings() {
		val old = Json.parseToJsonElement("""
			{"fishing-helper": {"nearby-creatures": true},
			 "sea-creature-extras": {"health-display": true, "health-limit": 7, "health-hud": {"x": 5, "y": 120, "scale": 1.0}}}
		""").jsonObject
		val m = ConfigMigrator(map)
		val r = m.migrate(old)
		assertEquals(JsonPrimitive(true), m.lookup(r.tree, SettingPath.parse("fishing.sea-creatures.nearby-creatures")))
		assertEquals(JsonPrimitive(true), m.lookup(r.tree, SettingPath.parse("fishing.sea-creatures.nearby-creatures-health")))
		assertEquals(JsonPrimitive(7), m.lookup(r.tree, SettingPath.parse("fishing.sea-creatures.nearby-creatures-limit")))
		assertNotNull(m.lookup(r.tree, SettingPath.parse("fishing.sea-creatures.nearby-creatures-hud")))
		assertTrue(r.unmapped.isEmpty())
		assertTrue(map.entries.none { it.newPath.subsection == "sea-creature-extras" && it.newPath.key.startsWith("health") })
	}
}
