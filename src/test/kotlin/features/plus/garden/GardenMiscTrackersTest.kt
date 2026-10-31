// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.plus.garden

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.garden.GardenMiscTrackers as T

class GardenMiscTrackersTest {
	@Test
	fun chat() {
		val ids = { n: String -> n.uppercase().replace(' ', '_') }
		T.handle("    +15 Garden Experience", ids)
		assertEquals(15.0, T.sessionXp)
		T.handle("OVERFLOW! Your Turing Sugar Cane Hoe Mk. III has just dropped a Tool Exp Capsule!", ids)
		assertEquals(1, T.capsules["Turing Sugar Cane Hoe Mk. III"])
		T.handle("WOAH! You caught a case of the CROP FEVER for 60 seconds!", ids)
		T.handle("RARE DROP! You dropped 48x Enchanted Melon Slice!", ids)
		assertEquals(48L, T.feverTracker.current.items["ENCHANTED_MELON_SLICE"])
		T.handle("GONE! Your CROP FEVER has been cured!", ids)
		assertFalse(T.inFever)
		T.handle("RARE CROP! Cane Knot (+137.6)", ids)
		T.handle("RARE CROP! Seasoning (+115) (automatically donated)", ids)
		assertEquals(setOf("CANE_KNOT", "SEASONING"), T.rareTracker.current.items.keys)
	}
}
