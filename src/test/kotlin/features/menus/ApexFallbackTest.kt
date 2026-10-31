// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.FallbackPlanner
import dev.ashtonite365.apex.features.menus.support.FallbackPlanner.Decision
import dev.ashtonite365.apex.features.menus.support.SlotText

class ApexFallbackTest {
	private fun page(to: String?) = listOf(SlotText(4, "§aFarming XV"), SlotText(48, "§aGo Back", listOfNotNull(to?.let { "§7To $it" })))

	@Test
	fun ownParentChosen() {
		val p = FallbackPlanner({ it in setOf("Your Skills", "SkyBlock Menu") })
		assertEquals(Decision.GoBack(48, "Your Skills"), p.decide("Farming Skill", page("Your Skills"), 0))
	}

	@Test
	fun parentFailsStepsUpToSkyBlockMenu() {
		val p = FallbackPlanner({ it in setOf("Your Skills", "SkyBlock Menu") })
		assertEquals(Decision.GoBack(48, "Your Skills"), p.decide("Farming Skill", page("Your Skills"), 0))

		assertEquals(Decision.GoBack(48, "SkyBlock Menu"), p.decide("Your Skills", page("SkyBlock Menu"), 500))

		val q = FallbackPlanner({ it == "SkyBlock Menu" })
		assertEquals(Decision.GoBack(48, "Heart of the Mountain"), q.decide("Commission Milestones", page("Heart of the Mountain"), 0))
	}

	@Test
	fun allFailVanilla() {
		val p = FallbackPlanner({ false })
		val d = p.decide("Hunting Box", page("SkyBlock Menu"), 0)
		assertTrue(d is Decision.Vanilla && d.reason.startsWith("no Apex parent can render"), "$d")

		val r = FallbackPlanner({ it == "SkyBlock Menu" })
		assertTrue(r.decide("SkyBlock Menu", page(null), 0) is Decision.Vanilla)
	}

	@Test
	fun loopGuard() {
		val p = FallbackPlanner({ true }, maxDepth = 3, windowMs = 10_000)
		assertTrue(p.decide("A", page("B"), 0) is Decision.GoBack)
		assertTrue(p.decide("B", page("A"), 100) is Decision.GoBack)
		val again = p.decide("A", page("B"), 200)
		assertTrue(again is Decision.Vanilla && again.reason.contains("loop guard"), "$again")

		val d = FallbackPlanner({ true }, maxDepth = 2)
		assertTrue(d.decide("X1", page("X2"), 0) is Decision.GoBack)
		assertTrue(d.decide("X2", page("X3"), 1) is Decision.GoBack)
		assertTrue((d.decide("X3", page("X4"), 2) as Decision.Vanilla).reason.contains("hops"))

		assertTrue(p.decide("A", page("B"), 60_000) is Decision.GoBack)
	}

	@Test
	fun noGoBackSlot() {
		val p = FallbackPlanner({ true })
		assertEquals(Decision.Vanilla("no Go Back slot"), p.decide("Crystal Nucleus RNG Meter", listOf(SlotText(49, "§cClose")), 0))
	}
}
