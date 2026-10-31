// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.settings

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import dev.ashtonite365.apex.gui.hud.editor.HudSnapping
import dev.ashtonite365.apex.settings.ConfigMigrator
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.settings.SettingPath

class SettingsFollowUpTest {
	private fun json(s: String) = Json.parseToJsonElement(s).jsonObject
	private val bundled = MigrationMap.bundled()
	private fun p(s: String) = SettingPath.parse(s)

	private val d13 = MigrationMap.parse(
		"""
		fishing-helper.nearby-creatures	fishing.fishing-helper.nearby-creatures	toggle
		sea-creature-extras.health-display	fishing.fishing-helper.nearby-creatures-health	toggle
		a.x	misc.a.x	toggle
		a.y	misc.a.y	integer
		""".trimIndent(),
		"",
		"""
		# comment
		fishing-helper.nearby-creatures=false,sea-creature-extras.health-display=false	fishing.fishing-helper.nearby-creatures	${'$'}1 || ${'$'}2
		fishing-helper.nearby-creatures=false,sea-creature-extras.health-display=false	fishing.fishing-helper.nearby-creatures-health	${'$'}2
		a.x=true,a.y=3	misc.a.all	${'$'}1 && !(${'$'}2 || false)
		a.x=true,a.y=3	misc.a.copy	${'$'}2
		""".trimIndent(),
	)

	@Test
	fun plusHealthOnApexCountOffMigratesAsOnWithHealthOn() {
		val m = ConfigMigrator(d13)
		val res = m.migrate(json("""{"sea-creature-extras":{"health-display":true},"fishing-helper":{"nearby-creatures":false}}"""))
		assertEquals(JsonPrimitive(true), m.lookup(res.tree, p("fishing.fishing-helper.nearby-creatures")))
		assertEquals(JsonPrimitive(true), m.lookup(res.tree, p("fishing.fishing-helper.nearby-creatures-health")))
		assertTrue(res.conflicts.any { it.startsWith("fishing.fishing-helper.nearby-creatures:") }, res.conflicts.toString())

		val only = m.migrate(json("""{"sea-creature-extras":{"health-display":true}}"""))
		assertEquals(JsonPrimitive(true), m.lookup(only.tree, p("fishing.fishing-helper.nearby-creatures")))

		val off = m.migrate(json("""{"sea-creature-extras":{"health-display":false},"fishing-helper":{"nearby-creatures":false}}"""))
		assertEquals(JsonPrimitive(false), m.lookup(off.tree, p("fishing.fishing-helper.nearby-creatures")))
		assertEquals(JsonPrimitive(false), m.lookup(off.tree, p("fishing.fishing-helper.nearby-creatures-health")))

		assertNull(m.lookup(m.migrate(json("{}")).tree, p("fishing.sea-creatures.nearby-creatures")))
	}

	@Test
	fun derivedExpressionsAndCopies() {
		val m = ConfigMigrator(d13)
		val t = m.migrate(json("""{"a":{"y":7}}""")).tree

		assertEquals(JsonPrimitive(true), m.lookup(t, p("misc.a.all")))
		assertEquals(JsonPrimitive(7), m.lookup(t, p("misc.a.copy")))
		assertEquals(JsonPrimitive(false), m.lookup(m.migrate(json("""{"a":{"x":false}}""")).tree, p("misc.a.all")))
		assertEquals(JsonPrimitive(3), m.lookup(m.migrate(json("""{"a":{"x":false}}""")).tree, p("misc.a.copy")))

		val once = m.migrate(json("""{"a":{"x":true,"y":1}}""")).tree
		assertEquals(once, m.migrate(once).tree)
		assertEquals(json("""{"a":{"x":true,"y":1}}"""), once[ConfigMigrator.LEGACY_KEY])
	}

	@Test
	fun derivedRowsAreValidated() {
		for (bad in listOf("${'$'}3", "${'$'}1 &&", "(${'$'}1", "${'$'}1 + ${'$'}2", "${'$'}0"))
			assertThrows<IllegalArgumentException>(bad) { MigrationMap.parse("", "", "a.x,a.y\tmisc.a.z\t$bad") }
		assertThrows<IllegalArgumentException> { MigrationMap.parse("", "", "a.x\tmisc.a.z\t${'$'}1\na.y\tmisc.a.z\t${'$'}1") }
		assertThrows<IllegalArgumentException> { MigrationMap.parse("", "", "nodot\tmisc.a.z\t${'$'}1") }
	}

	@Test
	fun bundledD13Rule() {
		val m = ConfigMigrator(bundled)
		val t = m.migrate(json("""{"sea-creature-extras":{"health-display":true},"fishing-helper":{"nearby-creatures":false}}""")).tree
		assertEquals(JsonPrimitive(true), m.lookup(t, p("fishing.sea-creatures.nearby-creatures")))
		assertEquals(1, bundled.derived.size)
	}

	@Test
	fun removedTimerDuplicatesKeepTheirValues() {
		val old = json(
			"""{"core-misc":{"cookie-warning":false,"cookie-warning-minutes":60,"sign-calculator":true},
			"crimson-isle":{"miniboss-timers":false},"rift-helper":{"time-left":true}}"""
		)
		val m = ConfigMigrator(bundled)
		val res = m.migrate(old)
		assertTrue(res.unmapped.isEmpty(), res.unmapped.toString())
		assertEquals(JsonPrimitive(false), m.lookup(res.tree, p("timers.buffs.cookie-warning")))
		assertEquals(JsonPrimitive(60), m.lookup(res.tree, p("timers.buffs.cookie-warning-minutes")))
		assertEquals(JsonPrimitive(false), m.lookup(res.tree, p("timers.crimson-isle.show-crimson-minibosses")))
		assertEquals(JsonPrimitive(true), m.lookup(res.tree, p("timers.rift.show-rift-time")))
		listOf("core-misc.cookie-warning", "core-misc.cookie-warning-minutes", "crimson-isle.miniboss-timers", "rift-helper.time-left")
			.forEach { assertNull(bundled[it], "$it must have no plain Misc row any more") }

		assertEquals(listOf("core-misc.cookie-warning", "timers-overlay.cookie-warning"), bundled.oldKeysFor(p("timers.buffs.cookie-warning")))
	}

	private fun source(rel: String): String {
		var dir: File? = File(System.getProperty("user.dir")).absoluteFile
		while (dir != null && !File(dir, rel).exists()) dir = dir.parentFile
		return File(requireNotNull(dir) { "cannot find $rel" }, rel).readText()
	}

	@Test
	fun duplicateFeaturesAreGoneFromMiscFiles() {
		val core = source("src/main/kotlin/features/misc/CoreMiscTweaks.kt")
		assertFalse("cookie-warning" in core || "Cookie Buff" in core, "CoreMiscTweaks still has the Booster Cookie warning")
		val islands = source("src/main/kotlin/features/islands/IslandHelpers.kt")
		assertFalse("\"miniboss-timers\"" in islands, "Crimson Isle miniboss timer toggle still present")
		assertFalse("toggle(\"time-left\")" in islands, "Rift time-left gate still present")
		val slayer = source("src/main/kotlin/features/skills/SlayerOverlay.kt")
		assertFalse("onCollectTimers" in slayer || "TimerCollectEvent" in slayer, "SlayerOverlay still feeds the Timers HUD")
		assertTrue("bestBossMillis" in slayer, "slayer PB storage must stay")

		assertTrue("\"crimson-minibosses\"" in islands && "\"rift-time\"" in islands)
	}

	@Test
	fun h3NearbySeaCreatureRows() {
		assertEquals("fishing.sea-creatures.nearby-creatures-rare-first", bundled.resolve("fishing-helper.nearby-creatures-rare-first").toString())
		assertEquals("fishing.sea-creatures.nearby-creatures-separate", bundled.resolve("fishing-helper.nearby-creatures-separate").toString())
		val m = ConfigMigrator(bundled)
		val res = m.migrate(json("""{"fishing-helper":{"nearby-creatures-rare-first":false,"nearby-creatures-separate":true}}"""))
		assertTrue(res.unmapped.isEmpty())
		assertEquals(JsonPrimitive(true), m.lookup(res.tree, p("fishing.sea-creatures.nearby-creatures-separate")))

		for ((legacy, live) in listOf("health-display" to "nearby-creatures-health", "health-hud" to "nearby-creatures-hud", "health-limit" to "nearby-creatures-limit")) {
			val target = p("fishing.sea-creatures.$live")
			assertEquals(target, bundled.resolve("fishing-helper.$live"))
			assertEquals(listOf("fishing-helper.$live", "sea-creature-extras.$legacy"), bundled.oldKeysFor(target))
		}
		val r2 = m.migrate(json("""{"sea-creature-extras":{"health-limit":4}}"""))
		assertEquals(JsonPrimitive(4), m.lookup(r2.tree, p("fishing.sea-creatures.nearby-creatures-limit")))
		val r3 = m.migrate(json("""{"sea-creature-extras":{"health-limit":4},"fishing-helper":{"nearby-creatures-limit":6}}"""))
		assertEquals(JsonPrimitive(6), m.lookup(r3.tree, p("fishing.sea-creatures.nearby-creatures-limit")))
	}

	@Test
	fun d9SavedPlusHighlightOnStaysOn() {
		val m = ConfigMigrator(bundled)
		val target = p("fishing.sea-creatures.highlight-rare-creatures")
		fun run(s: String) = m.lookup(m.migrate(json(s)).tree, target)
		assertEquals(JsonPrimitive(true), run("""{"sea-creature-settings":{"highlight":true},"fishing-helper":{"highlight-rare-creatures":false}}"""))
		assertEquals(JsonPrimitive(true), run("""{"sea-creature-settings":{"highlight":true}}"""))
		assertEquals(JsonPrimitive(true), run("""{"sea-creature-settings":{"highlight":false},"fishing-helper":{"highlight-rare-creatures":true}}"""))
		assertEquals(JsonPrimitive(false), run("""{"sea-creature-settings":{"highlight":false},"fishing-helper":{"highlight-rare-creatures":false}}"""))
		assertNull(run("{}"), "nothing saved: the ⚠ toggle keeps its OFF default")
	}

	@Test
	fun hudSnapping() {
		val on = HudSnapping { true }
		val start = intArrayOf(100, 100, 40, 20)

		assertEquals(HudSnapping.Drag(0, 100, 0, null), on.drag(start, -97, 0, 400, 300, emptyList(), true))

		assertEquals(180, on.drag(start, 81, 0, 400, 300, emptyList(), true).x)

		assertEquals(250, on.drag(start, 147, 0, 400, 300, listOf(intArrayOf(200, 10, 50, 10)), true).x)

		assertEquals(3, on.drag(start, -97, 0, 400, 300, emptyList(), false).x)
		assertEquals(3, HudSnapping { false }.drag(start, -97, 0, 400, 300, emptyList(), true).x)

		assertEquals(HudSnapping.Drag(360, 280, null, null), HudSnapping { false }.drag(start, 900, 900, 400, 300, emptyList(), true))

		assertArrayEquals(intArrayOf(101, 100), on.nudge(start, 1, 0, false, 400, 300))
		assertArrayEquals(intArrayOf(100, 90), on.nudge(start, 0, -1, true, 400, 300))
		assertArrayEquals(intArrayOf(0, 100), on.nudge(intArrayOf(5, 100, 40, 20), -1, 0, true, 400, 300))
	}
}
