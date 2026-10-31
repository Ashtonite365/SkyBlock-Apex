// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.EnchantPoolLogic
import dev.ashtonite365.apex.features.menus.enchant.EnchantConflicts

class MissingEnchantmentsConflictTest {
	private val json = Json.parseToJsonElement(javaClass.getResource(EnchantConflicts.BUNDLED)!!.readText()).jsonObject
	private val pools = EnchantConflicts.poolsFrom(json)
	private val sword = json["enchants"]!!.jsonObject["SWORD"]!!.jsonArray.map { it.jsonPrimitive.content }

	@Test fun conflictsWithAppliedAreNotMissing() {
		val s = EnchantConflicts.splitMissing(sword, setOf("sharpness"), pools)
		assertTrue("smite" in s.missing && "bane_of_arthropods" in s.missing)
		assertFalse("ultimate_one_for_all" in s.missing)
		assertEquals("sharpness", s.conflicts.toMap()["ultimate_one_for_all"])
		assertFalse("sharpness" in s.missing)
	}

	@Test fun sameAsPicker() {
		val ofa = EnchantConflicts.splitMissing(sword, setOf("sharpness"), pools).conflicts.toMap()["ultimate_one_for_all"]
		assertEquals(EnchantConflicts.idOf("Sharpness"), ofa)

		val fake = listOf(listOf("sharpness", "smite", "bane_of_arthropods"))
		val s = EnchantConflicts.splitMissing(listOf("sharpness", "smite", "bane_of_arthropods", "looting"), listOf("sharpness", "smite"), fake)
		assertEquals(listOf("looting"), s.missing)
		assertEquals(listOf("bane_of_arthropods" to "sharpness"), s.conflicts)

		assertTrue(EnchantPoolLogic.conflictsWith("sharpness", "ultimate_one_for_all", pools))
		assertFalse(EnchantPoolLogic.conflictsWith("sharpness", "smite", pools))
	}

	@Test fun caseInsensitiveIds() {
		val s = EnchantConflicts.splitMissing(sword, setOf("prosecute"), pools)
		assertFalse(s.missing.any { it.equals("prosecute", true) })
		assertEquals("prosecute", s.conflicts.toMap()["ultimate_one_for_all"])
	}

	@Test fun wrapsNeverTruncates() {
		val items = listOf("One For All (conflicts with Sharpness)", "Soul Eater", "Wise")
		val l = EnchantPoolLogic.wrapJoined(items, 100) { it.length * 6 }
		assertEquals(listOf("One For All (conflicts with Sharpness)", "Soul Eater, Wise"), l)
		assertEquals(items.joinToString(", "), l.joinToString(", "))
		assertEquals("Smite (conflicts with Sharpness)", EnchantPoolLogic.conflictText("Smite", "Sharpness"))
	}
}
