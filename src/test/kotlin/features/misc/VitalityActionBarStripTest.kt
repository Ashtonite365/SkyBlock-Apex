// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.misc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import dev.ashtonite365.apex.features.misc.ActionBarStats
import dev.ashtonite365.apex.features.misc.StatBars

class VitalityActionBarStripTest {
	private val REAL_DEFENCE = "§r§6395/1,019\uE010     §a150\uE008     §b299/299\uE003     §4101/101\uE028     §r"
	private val REAL_LOCATION = "§r§6395/1,019\uE010     §7\uE067 §bVillage     §b299/299\uE003     §4100/101\uE028     §r"

	private val VIT_ONLY = StatBars.StripFlags(health = false, mana = false, defence = false, vitality = true)
	private val OFF = StatBars.StripFlags(health = false, mana = false, defence = false, vitality = false)

	private fun on(s: String) = StatBars.stripSegments(s, flags = VIT_ONLY)
	private fun off(s: String) = StatBars.stripSegments(s, flags = OFF)
	private fun plain(s: String) = s.replace(Regex("§."), "")

	@Test fun realSampleVitalityRemovedWhenOn() {
		assertEquals("§r§6395/1,019\uE010     §a150\uE008     §b299/299\uE003", on(REAL_DEFENCE))
		assertEquals("§r§6395/1,019\uE010     §7\uE067 §bVillage     §b299/299\uE003", on(REAL_LOCATION))
		for (s in listOf(REAL_DEFENCE, REAL_LOCATION)) {
			val p = ActionBarStats.parse(on(s))
			assertNull(p.vitality, "no vitality left in <${on(s)}>")
			assertEquals(395, p.health); assertEquals(299, p.mana)
		}
	}

	@Test fun realSampleUntouchedWhenOff() {
		assertEquals(REAL_DEFENCE, off(REAL_DEFENCE))
		assertEquals(REAL_LOCATION, off(REAL_LOCATION))
	}

	@Test fun noDoubleOrEdgeGaps() {
		for (s in listOf(REAL_DEFENCE, REAL_LOCATION, SYNTHETIC_MIDDLE, SYNTHETIC_FIRST)) {
			val out = plain(on(s))
			assertFalse(out.contains("      "), "double gap in <$out>")
			assertEquals(out.trim(), out, "leading/trailing gap in <$out>")
		}
	}

	@Test fun otherSegmentsAndColoursPreserved() {
		val out = on(REAL_DEFENCE)
		for (seg in listOf("§6395/1,019\uE010", "§a150\uE008", "§b299/299\uE003")) assertTrue(seg in out, "$seg in <$out>")
		assertTrue("§7\uE067 §bVillage" in on(REAL_LOCATION))
	}

	@Test fun textWithoutVitalityUnchanged() {

		val mount = "§rPress §r§rLeft Shift§r§r to dismount§r"
		assertEquals(mount, on(mount))

		val noVit = "§c1234/1500❤     §a250❈ Defense     §b893/893✎ Mana"
		assertEquals(noVit, on(noVit))
	}

	@Test fun vitalityOnlyLineBecomesEmpty() {
		val out = on("§4101/101\uE028     ")
		assertFalse(ActionBarStats.hasVisibleText(out), out)
	}

	@Test fun mergedWithExistingHiding() {
		val all = StatBars.StripFlags(health = true, mana = true, defence = true, vitality = true)
		assertFalse(ActionBarStats.hasVisibleText(StatBars.stripSegments(REAL_DEFENCE, flags = all)))
		assertEquals("§7\uE067 §bVillage", StatBars.stripSegments(REAL_LOCATION, flags = all))
		val hpOnly = StatBars.StripFlags(health = true, mana = false, defence = false, vitality = false)
		assertEquals("§a150\uE008     §b299/299\uE003     §4101/101\uE028", StatBars.stripSegments(REAL_DEFENCE, flags = hpOnly))
	}

	@Test fun flagFollowsShowVitality() {
		StatBars.TConfig.explicitDefaultLoad()
		StatBars.ActionBarConfig.explicitDefaultLoad()
		assertEquals(StatBars.TConfig.showVitality, StatBars.stripFlags().vitality)
		assertTrue(StatBars.stripFlags().vitality, "default Show Vitality is on (default unchanged)")
	}

	private val SYNTHETIC_MIDDLE = "§r§6395/1,019\uE010     §4101/101\uE028     §a150\uE008     §b299/299\uE003     §r"
	private val SYNTHETIC_FIRST = "§r§4101/101\uE028     §6395/1,019\uE010     §a150\uE008     §b299/299\uE003     §r"

	@Test fun syntheticVitalityInMiddleOrFirst() {
		assertEquals("§r§6395/1,019\uE010     §a150\uE008     §b299/299\uE003", on(SYNTHETIC_MIDDLE))
		assertEquals("§6395/1,019\uE010     §a150\uE008     §b299/299\uE003", on(SYNTHETIC_FIRST))
	}

	@Test fun retiredHideVitalityKeyIsDroppedOnLoad() {
		val saved = Json.parseToJsonElement("""{"hide-health":true,"hide-vitality":true,"hide-mana":false}""").jsonObject
		assertEquals(setOf("hide-health", "hide-mana"), StatBars.dropRetired(saved).keys)
		val clean = JsonObject(mapOf())
		assertSame(clean, StatBars.dropRetired(clean))
		val file = Json.parseToJsonElement("""{"stat-bars-action-bar":{"hide-vitality":true,"hide-mana":false}}""").jsonObject
		StatBars.ActionBarConfig.loadFrom(Unit, file)
		assertFalse("hide-vitality" in StatBars.ActionBarConfig.allOptions)
		assertFalse(StatBars.ActionBarConfig.hideMana, "other saved values still load")
		val written = StatBars.ActionBarConfig.saveTo(Unit)["stat-bars-action-bar"]!!.jsonObject
		assertFalse("hide-vitality" in written)
		assertTrue("hide-mana" in written)
		StatBars.ActionBarConfig.explicitDefaultLoad()
	}
}
