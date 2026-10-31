// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.timers

import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.timers.BoosterCookieLogic
import dev.ashtonite365.apex.features.timers.BoosterCookieLogic.Status
import dev.ashtonite365.apex.features.timers.BoosterCookieLogic.Warning
import dev.ashtonite365.apex.features.timers.SlayerTimerState
import dev.ashtonite365.apex.features.timers.TimerCatalog
import dev.ashtonite365.apex.features.timers.TimerSection
import dev.ashtonite365.apex.settings.MainSection
import dev.ashtonite365.apex.settings.SettingPath
import dev.ashtonite365.apex.settings.SettingsRegistry

class TimersRewriteTest {

	private val timersSource = generateSequence(File("").absoluteFile) { it.parentFile }
		.map { File(it, "src/main/kotlin/features/timers/Timers.kt") }.first { it.isFile }.readText()
	private val declaredKeys = Regex("""(?:toggle|integer|position|keyBindingWithDefaultUnbound|riskyToggle)\("([a-z0-9-]+)"""")
		.findAll(timersSource.substringAfter("object TConfig").substringBefore("\n\t}\n")).map { it.groupValues[1] }.toList()

	@Test
	fun everyOptionIsInTheCatalogExactlyOnce() {
		assertEquals(declaredKeys.toSet().size, declaredKeys.size, "duplicate key in TConfig")
		assertEquals(declaredKeys.toSet(), TimerCatalog.options.map { it.key }.toSet())
		assertEquals(TimerCatalog.options.size, TimerCatalog.options.map { it.key }.toSet().size)
		TimerCatalog.removedKeys.keys.forEach { assertFalse(it in declaredKeys, "$it was merged away") }
	}

	@Test
	fun ashtonsSubsectionsAllExist() {
		val wanted = listOf("fishing", "dungeon", "contest", "event", "mining", "slayer", "rift", "crimson-isle", "cooldowns", "buffs", "items", "farming")
		assertEquals(wanted, TimerSection.entries.map { it.id }.filter { it != "general" })
		for (s in TimerSection.entries.filter { it != TimerSection.GENERAL }) {
			val has = TimerCatalog.inSection(s).isNotEmpty() || TimerCatalog.holders.any { it.section == s }
			assertTrue(has, "empty subsection ${s.id}")
		}
	}

	@Test
	fun pathsAreValidAndClaimOnce() {
		val reg = SettingsRegistry()
		TimerSection.entries.forEach { reg.register(MainSection.TIMERS, it.id) }
		for (o in TimerCatalog.options) {
			val p = SettingPath.parse(o.path)
			assertEquals(MainSection.TIMERS, p.main)
			reg.claim(p, "timers-overlay")
		}
		assertEquals(1, TimerCatalog.holders.count { it.holder == "trevor" })
	}

	@Test
	fun timerIdsAreUniqueAndMastersExist() {
		val ids = TimerCatalog.options.mapNotNull { it.timerId }
		assertEquals(ids.toSet().size, ids.size)
		TimerCatalog.options.mapNotNull { it.master }.forEach { assertNotNull(TimerCatalog.option(it)) }
		ids.forEach { assertFalse(it in TimerCatalog.retiredIds) }
	}

	@Test
	fun visibilityHonoursMastersRetiredAndUnknown() {
		val off = setOf("show-events")
		val v = { k: String -> k !in off }
		assertFalse(TimerCatalog.isShown("dark-auction", v))
		assertTrue(TimerCatalog.isShown("jacobs-contest", v), "contest is its own subsection")
		assertTrue(TimerCatalog.isShown("cake", v))
		assertFalse(TimerCatalog.isShown("slayer", v), "legacy slayer lines are replaced by the merged timers")
		assertFalse(TimerCatalog.isShown("cookie", v))
		assertTrue(TimerCatalog.isShown("trevor", v), "trevor is gated by its home toggle (D7)")
		assertTrue(TimerCatalog.isShown("abilities", v), "abilities are gated by ability-cooldowns.enabled (D6)")
		assertFalse(TimerCatalog.isShown("slayer-boss") { it != "show-slayer" })
		assertFalse(TimerCatalog.isShown("booster-cookie") { it != "show-cookie" })
	}

	@Test
	fun noRiskyOptionDefaultsOn() {
		assertFalse(timersSource.contains("riskyToggle"), "Timers has no rule-breaking options")
		assertFalse(TimerCatalog.option("enabled")!!.default)
	}

	@Test
	fun migrationRowsKeepOldKeysAndMoveSplitOnes() {
		val rows = TimerCatalog.migrationRows { "toggle" }.associate { it.split('\t').let { r -> r[0] to r[1] } }
		assertEquals("timers.rift.show-infusion", rows["timers-overlay.show-infusion"])
		assertEquals("timers.cooldowns.show-experiments", rows["timers-overlay.show-experiments"])
		assertEquals("timers.buffs.cookie-warning", rows["timers-overlay.cookie-warning"])
		assertEquals("timers.general.enabled", rows["timers-overlay.enabled"])
	}

	@Test
	fun cookieParsesBothTabLayouts() {
		val footer = listOf("You have 3 boosters", "Cookie Buff", "4d 13h 2m", "Ranks, Boosters & MORE!")
		assertEquals(Status.Active("4d 13h 2m", 4.days + 13.hours + 2.minutes), BoosterCookieLogic.readStatus(footer))
		val widget = listOf("Profile: Apple", " Cookie Buff: 1h 5m")
		assertEquals(1.hours + 5.minutes, (BoosterCookieLogic.readStatus(widget) as Status.Active).remaining)
		assertEquals(Status.Inactive, BoosterCookieLogic.readStatus(listOf("Cookie Buff", "Not active! Obtain booster cookies from the community shop")))
		assertEquals(Status.Inactive, BoosterCookieLogic.readStatus(listOf("Cookie Buff: INACTIVE")))
		assertEquals(Status.Unknown, BoosterCookieLogic.readStatus(listOf("nothing here")))
		assertEquals(59.minutes, BoosterCookieLogic.parseRemaining("Less than an hour"))
		assertEquals(365.days + 60.days, BoosterCookieLogic.parseRemaining("1y 2M"))
		assertNull(BoosterCookieLogic.parseRemaining("soon"))
	}

	@Test
	fun cookieWarningsFireOnceAndReArm() {
		val w = BoosterCookieLogic.Warner()
		val th = 60.minutes
		assertNull(w.update(Status.Active("2h", 2.hours), th, true, true))
		assertEquals(Warning.LOW, w.update(Status.Active("50m", 50.minutes), th, true, true))
		assertNull(w.update(Status.Active("40m", 40.minutes), th, true, true))
		assertEquals(Warning.EXPIRED, w.update(Status.Inactive, th, true, true), "expired still fires after low")
		assertNull(w.update(Status.Inactive, th, true, true))
		assertNull(w.update(Status.Active("4d", 4.days), th, true, true), "new cookie re-arms")
		assertEquals(Warning.LOW, w.update(Status.Active("10m", 10.minutes), th, true, true))
		assertNull(BoosterCookieLogic.Warner().update(Status.Active("10m", 10.minutes), th, false, true))
		assertTrue(BoosterCookieLogic.isPending(Status.Active("10m", 10.minutes), th))
		assertFalse(BoosterCookieLogic.isPending(Status.Unknown, th))
	}

	@Test
	fun slayerQuestBossKillAndPbDelta() {
		var now = 1_000_000L
		val s = SlayerTimerState { now }
		val pb = { _: String -> 50.seconds }
		s.onChat("SLAYER QUEST STARTED!")
		s.onScoreboard("Revenant Horror IV", "(12/1,500) Combat XP", pb)
		now += 60_000
		assertEquals(60.seconds, s.questElapsed())
		s.onScoreboard("Revenant Horror IV", "Slay the boss!", pb)
		now += 40_000
		assertEquals(40.seconds, s.bossElapsed())
		val kill = s.onChat("NICE! SLAYER BOSS SLAIN!")!!
		assertEquals(40.seconds, kill.bossTime)
		assertEquals(100.seconds, kill.questTime)
		assertEquals((-10).seconds, kill.pbDelta)
		assertNull(s.onChat("SLAYER QUEST COMPLETE!"), "one kill per boss")

		s.onScoreboard("Revenant Horror IV", "Boss slain!", pb)
		assertNull(s.questElapsed())
		assertNotNull(s.recentKill())
		now += 31_000
		assertNull(s.recentKill())
		s.onScoreboard(null, null, pb)
		s.onScoreboard("Revenant Horror IV", "(0/1,500) Combat XP", pb)
		assertEquals(0.seconds, s.questElapsed(), "scoreboard fallback when the start message was missed")
	}

	@Test
	fun slayerFailureClearsTimers() {
		var now = 0L
		val s = SlayerTimerState { now }
		s.onScoreboard("Tarantula Broodfather III", "Slay the boss!", { null })
		now += 5_000
		s.onChat("SLAYER QUEST FAILED!")
		assertNull(s.bossElapsed())
		assertNull(s.questElapsed())
		assertEquals("§a-3s", SlayerTimerState.formatDelta((-3).seconds) { "${it.inWholeSeconds}s" })
	}
}
