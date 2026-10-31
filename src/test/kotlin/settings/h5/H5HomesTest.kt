// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.settings.h5

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.dungeon.DungeonsHome
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.features.dungeon.SectionHome
import dev.ashtonite365.apex.features.fishing.FishingHelper
import dev.ashtonite365.apex.features.plus.fishing.PlusSeaCreatureExtras
import dev.ashtonite365.apex.features.plus.fishing.PlusSeaCreatureSettings
import dev.ashtonite365.apex.gui.config.KeyBindingHandler
import dev.ashtonite365.apex.keybindings.ApexKeyBindings
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.SettingsRegistry
import dev.ashtonite365.apex.util.data.ManagedConfig

object H5Overlay {
	val REMOVED = emptySet<String>()
	val REPOINTED = mapOf(
		"sea-creature-extras.health-display" to "fishing.fishing-helper.nearby-creatures-health",
		"sea-creature-extras.health-hud" to "fishing.fishing-helper.nearby-creatures-hud",
		"sea-creature-extras.health-limit" to "fishing.fishing-helper.nearby-creatures-limit",
	)

	val H3_NEW = setOf("fishing-helper.nearby-creatures-health", "fishing-helper.nearby-creatures-limit",
		"fishing-helper.nearby-creatures-rare-first", "fishing-helper.nearby-creatures-separate", "fishing-helper.nearby-creatures-hud")
	val NEW_DUPLICATES = listOf(
		"puzzle-solvers.spirit-leap-keybinds,puzzle-solvers.leap-class-keybinds\tdungeons.puzzle-solvers.spirit-leap-keybinds\tANY_TRUE",
	)

	val FOLDED = setOf("sea-creature-settings.highlight", "puzzle-solvers.leap-class-keybinds",
		"sea-creature-extras.health-display", "sea-creature-extras.health-hud", "sea-creature-extras.health-limit",
		"fishing-helper.golden-fish-timer")

	private fun res(n: String) = MigrationMap::class.java.getResourceAsStream(n)!!.bufferedReader().readText()

	val map: MigrationMap by lazy {
		val rows = res(MigrationMap.RESOURCE).lines().mapNotNull { line ->
			val c = line.split('\t')
			when {
				line.startsWith("#") || c.size < 3 -> line
				c[0] in REMOVED -> null
				c[0] in REPOINTED -> (listOf(c[0], REPOINTED.getValue(c[0])) + c.drop(2)).joinToString("\t")
				else -> line
			}
		}
		MigrationMap.parse(rows.joinToString("\n"), res(MigrationMap.DUPLICATES_RESOURCE) + "\n" + NEW_DUPLICATES.joinToString("\n"))
	}
}

class H5HomesTest {
	private val map = H5Overlay.map

	private val configs: List<ManagedConfig> = listOf(
		dev.ashtonite365.apex.features.dungeon.BloodMobEsp.TConfig, dev.ashtonite365.apex.features.dungeon.ShadowAssassinFinder.TConfig,
		dev.ashtonite365.apex.features.dungeon.BreakableWalls.TConfig, dev.ashtonite365.apex.features.dungeon.secrets.DungeonSecrets.TConfig,
		dev.ashtonite365.apex.features.dungeon.DungeonChestProfit.TConfig,
		dev.ashtonite365.apex.features.dungeon.DungeonLootMarks.TConfig, dev.ashtonite365.apex.features.dungeon.DungeonMap.TConfig,
		dev.ashtonite365.apex.features.dungeon.DungeonScore.TConfig, dev.ashtonite365.apex.features.dungeon.DungeonSplits.TConfig,
		dev.ashtonite365.apex.features.dungeon.DungeonStatusHuds.TConfig, PuzzleSolvers.TConfig,
		dev.ashtonite365.apex.features.dungeon.TerminalExtras.TConfig, dev.ashtonite365.apex.features.dungeon.TerminalSolver.TConfig,
		dev.ashtonite365.apex.features.kuudra.KuudraInfo.TConfig, dev.ashtonite365.apex.features.kuudra.KuudraProfit.TConfig,
		dev.ashtonite365.apex.features.kuudra.KuudraSupplies.TConfig, dev.ashtonite365.apex.features.kit.splits.KuudraSplits.TConfig,
		dev.ashtonite365.apex.features.kit.devices.KitDevices.TConfig,
		dev.ashtonite365.apex.features.kit.devices.SharpshooterHighlight.TConfig, dev.ashtonite365.apex.features.kit.dungeon.DungeonExtras.TConfig,
		dev.ashtonite365.apex.features.kit.boss.WitherDragonHelper.TConfig, dev.ashtonite365.apex.features.kit.boss.KingRelicHelper.TConfig,
		dev.ashtonite365.apex.features.plus.dungeon.DungeonPlusPorts.TConfig,
		dev.ashtonite365.apex.features.mining.CommissionFeatures.TConfig, dev.ashtonite365.apex.features.mining.CrystalHollowsChestHighlighter.TConfig,
		dev.ashtonite365.apex.features.mining.CrystalHollowsMinimap.TConfig, dev.ashtonite365.apex.features.mining.CrystalHollowsOverlay.TConfig,
		dev.ashtonite365.apex.features.mining.CrystalHollowsWaypoints.TConfig, dev.ashtonite365.apex.features.mining.DwarvenMinesWaypoints.TConfig,
		dev.ashtonite365.apex.features.mining.FossilExcavator.TConfig, dev.ashtonite365.apex.features.mining.MetalDetectorSolver.TConfig,
		dev.ashtonite365.apex.features.mining.MiningExtras.TConfig, dev.ashtonite365.apex.features.mining.MiningLoot.TConfig,
		dev.ashtonite365.apex.features.mining.MiningOverlay.TConfig, dev.ashtonite365.apex.features.mining.MiningRisky.TConfig,
		dev.ashtonite365.apex.features.mining.MiningSolvers.TConfig, dev.ashtonite365.apex.features.mining.PickaxeAbility.TConfig,
		dev.ashtonite365.apex.features.mining.PowderGrindingOverlay.TConfig, dev.ashtonite365.apex.features.mining.PristineProfitTracker.TConfig,
		dev.ashtonite365.apex.features.mining.WishingCompassSolver.TConfig,
		dev.ashtonite365.apex.features.garden.GardenTools.TConfig, dev.ashtonite365.apex.features.garden.HideComposterNoises.TConfig,
		dev.ashtonite365.apex.features.misc.TrevorHelper.TConfig, dev.ashtonite365.apex.features.plus.garden.GardenPlots.TConfig,
		dev.ashtonite365.apex.features.plus.garden.GardenWarps.TConfig, dev.ashtonite365.apex.features.plus.garden.SprayTracker.TConfig,
		FishingHelper.TConfig, dev.ashtonite365.apex.features.fishing.FishingRisky.TConfig, dev.ashtonite365.apex.features.fishing.FishingTracker.TConfig,
		dev.ashtonite365.apex.features.plus.fishing.PlusFishingProfit.TConfig, dev.ashtonite365.apex.features.plus.fishing.PlusFishingSmall.TConfig,
		dev.ashtonite365.apex.features.plus.fishing.PlusLavaReplacement.TConfig, PlusSeaCreatureExtras.TConfig, PlusSeaCreatureSettings.TConfig,
		dev.ashtonite365.apex.features.plus.fishing.PlusThunderSparks.TConfig, dev.ashtonite365.apex.features.plus.fishing.PlusTrophyFillet.TConfig,
		dev.ashtonite365.apex.features.plus.fishing.PlusTrophyHelpers.TConfig,
	)

	private fun isDynamicKey(c: ManagedConfig, key: String) = c.name == "puzzle-solvers" && key.startsWith("spirit-leap-key-")

	private fun json(s: String) = Json.parseToJsonElement(s).jsonObject

	private val before by lazy { dev.ashtonite365.apex.settings.LayoutMoves.bundled.entries.associate { (o, n) -> n to o } }
	private fun mainBefore(e: dev.ashtonite365.apex.settings.MigrationEntry) =
		before[e.newPath.toString()]?.let { SettingPath.parse(it).main } ?: e.newPath.main

	@Test
	fun homesCoverTheirSectionsExactly() {
		val homes = SectionHome.H5
		assertEquals(listOf(MainSection.DUNGEONS, MainSection.SECRETS, MainSection.MINING, MainSection.FARMING, MainSection.FISHING), homes.map { it.main })
		assertEquals(57, homes.sumOf { it.holders.size })
		for (home in homes) {

			val mapped = map.entries.filter { mainBefore(it) == home.main && it.oldKey !in home.borrowed }.map { it.oldConfig }.toSet()
			assertEquals(mapped, home.holders.toSet(), "holders of ${home.main}")
			assertEquals(home.borrowed.toSet(), map.entries.filter { mainBefore(it) == home.main && it.oldConfig !in home.holders }.map { it.oldKey }.toSet())
			assertEquals(home.holders.size, home.holders.distinct().size)
		}
		assertEquals(configs.map { it.name }.toSet(), homes.flatMap { it.holders }.toSet())
	}

	@Test
	fun homesInstallWithLinksAndNoDuplicateClaims() {
		val reg = SettingsRegistry.default(map)
		SectionHome.H5.forEach { it.install(reg, map) }
		SectionHome.H5.forEach { it.install(reg, map) }
		for (s in listOf("kuudra-info", "kuudra-supplies", "kuudra-profit"))
			assertEquals("kuudra", reg.get(MainSection.DUNGEONS, s)!!.group)

		for (s in listOf("kuudra-splits", "fresh-tools")) assertEquals("kuudra", reg.get(MainSection.TIMERS, s)!!.group)
		assertTrue(reg.get(MainSection.TIMERS, "boss-timers")!! in reg.linked(MainSection.DUNGEONS))
		assertTrue(reg.get(MainSection.FARMING, "trevor")!! in reg.linked(MainSection.TIMERS))
		assertTrue(reg.get(MainSection.TIMERS, "garden")!! in reg.linked(MainSection.FARMING))
		assertTrue(reg.get(MainSection.TIMERS, "mining")!! in reg.linked(MainSection.MINING))
		assertTrue(reg.get(MainSection.TIMERS, "fishing")!! in reg.linked(MainSection.FISHING))

		val health = map.duplicates.single { "sea-creature-extras.health-display" in it.oldKeys }
		assertEquals("sea-creatures", health.target.subsection)
		assertEquals(map["fishing-helper.nearby-creatures"]!!.newPath.subsection, health.target.subsection)
	}

	@Test
	fun everyOptionOfMyHoldersIsMappedIntoItsHome() {
		val homeOf = SectionHome.H5.flatMap { h -> h.holders.map { it to h.main } }.toMap()
		val borrowedBy = SectionHome.H5.flatMap { h -> h.borrowed.map { it to h.main } }.toMap()
		for (c in configs) {
			val keys = c.sortedOptions.map { it.propertyName }.filterNot { isDynamicKey(c, it) || "${c.name}.$it" in H5Overlay.H3_NEW }
			for (k in keys) {
				val e = map["${c.name}.$k"]
				assertTrue(e != null, "${c.name}.$k has no migration row")
				assertEquals(borrowedBy["${c.name}.$k"] ?: homeOf[c.name], mainBefore(e!!), "${c.name}.$k")
			}
			val stale = map.entries.filter { it.oldConfig == c.name && it.oldOption !in c.sortedOptions.map { o -> o.propertyName } && it.oldKey !in H5Overlay.FOLDED }
			assertTrue(stale.isEmpty(), "rows without an option: $stale")
		}
	}

	@Test
	fun riskyOptionsStayOffWithWarning() {
		for (c in configs) {
			c.explicitDefaultLoad()
			for (o in c.sortedOptions) {
				val kind = map["${c.name}.${o.propertyName}"]?.kind
				if (o.riskLevel.isRisky) {

					val parent = o.parentToggle
					val subOfOffParent = parent != null && parent.riskLevel.isRisky && parent.default() == false
					if (!subOfOffParent) assertEquals(false, o.default(), "${c.name}.${o.propertyName} ⚠ must default OFF")
					if (kind != null) assertEquals("riskyToggle", kind, "${c.name}.${o.propertyName}")
				} else if (kind != null) {
					assertFalse(kind == "riskyToggle", "${c.name}.${o.propertyName} lost its ⚠")
				}
			}
		}
	}

	@Test
	fun keybindsGoThroughApexKeyBindings() {
		val registered = ApexKeyBindings.keyBindings.values.toSet()
		var n = 0
		for (c in configs) for (o in c.sortedOptions) if (o.handler is KeyBindingHandler) {
			n++
			assertTrue(o in registered, "${c.name}.${o.propertyName} is not a ApexKeyBindings keybind")
		}
		assertTrue(n >= 7 + 9, "found $n keybinds")
	}

	@Test
	fun mergedDuplicatesHaveOneOption() {

		assertNull(PlusSeaCreatureSettings.TConfig.allOptions["highlight"])
		assertTrue(FishingHelper.TConfig.allOptions.getValue("highlight-rare-creatures").riskLevel.isRisky)

		assertNull(PlusSeaCreatureExtras.TConfig.allOptions["health-display"])

		assertNull(PuzzleSolvers.TConfig.allOptions["leap-class-keybinds"])
		PuzzleSolvers.TConfig.explicitDefaultLoad()
		assertFalse(PuzzleSolvers.TConfig.leapClassKeybinds)
		assertEquals(PuzzleSolvers.TConfig.spiritLeapKeybinds, PuzzleSolvers.TConfig.leapClassKeybinds)
	}

	@Test
	fun d14FoldKeepsTheSavedValue() {
		assertEquals(json("""{"spirit-leap-keybinds":true,"leap-scale":120}"""),
			PuzzleSolvers.foldLeapClassKeybinds(json("""{"leap-class-keybinds":true,"leap-scale":120}""")))
		assertEquals(json("""{"spirit-leap-keybinds":true}"""),
			PuzzleSolvers.foldLeapClassKeybinds(json("""{"leap-class-keybinds":false,"spirit-leap-keybinds":true}""")))
		assertEquals(json("""{}"""), PuzzleSolvers.foldLeapClassKeybinds(json("""{"leap-class-keybinds":false}""")))
		val same = json("""{"spirit-leap-keybinds":false}""")
		assertEquals(same, PuzzleSolvers.foldLeapClassKeybinds(same))
	}

	@Test
	fun migrationMergesH5Duplicates() {
		val m = ConfigMigrator(map)
		val res = m.migrate(json("""{
			"puzzle-solvers":{"leap-class-keybinds":true,"spirit-leap-keybinds":false},
			"sea-creature-settings":{"highlight":true,"highlight-names":"Jawbus"},
			"fishing-helper":{"golden-fish-timer":false,"nearby-creatures":true},
			"timers-overlay":{"show-golden-fish":true},
			"sea-creature-extras":{"health-display":true,"health-limit":3,"health-hud":{"x":1}}
		}""".trimIndent()))
		fun v(p: String) = m.lookup(res.tree, SettingPath.parse(p))
		assertEquals(JsonPrimitive(true), v("dungeons.spirit-leap.spirit-leap-keybinds"))
		assertEquals(JsonPrimitive(true), v("fishing.sea-creatures.highlight-rare-creatures"))
		assertEquals(JsonPrimitive("Jawbus"), v("fishing.sea-creatures.highlight-names"))
		assertEquals(JsonPrimitive(true), v("timers.fishing.show-golden-fish"))
		assertEquals(JsonPrimitive(true), v("fishing.sea-creatures.nearby-creatures-health"))
		assertEquals(JsonPrimitive(3), v("fishing.sea-creatures.nearby-creatures-limit"))
		assertEquals(json("""{"x":1}"""), v("fishing.sea-creatures.nearby-creatures-hud"))
		assertTrue(res.unmapped.isEmpty())
	}

	@Test
	fun dungeonsHomeKeepsKuudraTogether() {

		val subs = DungeonsHome.paths(map).filter { it.main == MainSection.DUNGEONS }.map { it.subsection }.distinct()
		val k = subs.indexOf("kuudra-info")
		assertEquals(listOf("kuudra-info", "kuudra-supplies", "kuudra-profit"), subs.subList(k, k + 3))
		val shown = dev.ashtonite365.apex.settings.LayoutOrder.bundled.subsections(MainSection.DUNGEONS)
		assertEquals(listOf("kuudra-info", "kuudra-profit", "kuudra-supplies"), shown.filter { it.startsWith("kuudra-") })
	}
}
