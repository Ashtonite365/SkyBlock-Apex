// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.enchant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.enchant.*
import dev.ashtonite365.apex.features.menus.support.SlotText
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class EnchantConflictsTest {
	private val pools = EnchantConflicts.poolsFrom(Json.parseToJsonElement(
		javaClass.getResource(EnchantConflicts.BUNDLED)!!.readText()).jsonObject)

	private fun picker(name: String): EnchantModel {
		val d = RealDumpFixtures.load(name)
		val m = EnchantParser.parse(d.title, d.slots, 54, emptySet(), emptySet())!!
		return EnchantParser.withConflicts(m, d.slots, pools)
	}

	private fun itemLore(name: String) = RealDumpFixtures.load(name).slots.first { it.slot == 19 }.cleanLore

	@Test fun bundledDataHasConflictGroups() {
		assertTrue(pools.size > 10)

		assertTrue(pools.any { "ultimate_one_for_all" in it && "sharpness" in it })
		assertFalse(pools.any { "sharpness" in it && "smite" in it })
	}

	@Test fun realLoreShowsNoReplacement() {
		val known = setOf("sharpness", "smite", "bane of arthropods", "fire aspect", "experience")
		assertEquals(setOf("Sharpness"), EnchantConflicts.appliedFromLore(itemLore("enchant-1447-sharp-48619"), known))
		assertEquals(setOf("Sharpness", "Smite"), EnchantConflicts.appliedFromLore(itemLore("enchant-1447-smite-48793"), known))
		assertEquals(setOf("Bane of Arthropods", "Experience", "Fire Aspect", "Sharpness", "Smite"),
			EnchantConflicts.appliedFromLore(itemLore("enchant-1447-bane-49925"), known))
	}

	@Test fun pickerAfterSharpness() {
		val m = picker("enchant-1447-picker-48677")
		assertEquals(19, m.itemSlot)
		assertEquals(15, m.tiles.size)
		val sharp = m.tiles.first { it.option.base == "Sharpness" }
		assertTrue(sharp.applied); assertEquals(5, sharp.option.level)

		assertTrue(m.tiles.first { it.option.base == "Smite" }.missing)
		assertTrue(m.tiles.first { it.option.base == "Bane of Arthropods" }.missing)
		assertEquals(14, m.missingCount)
		assertTrue(m.tiles.none { it.conflictsWith != null })
	}

	@Test fun pickerAfterBaneCountsAndSort() {
		val m = picker("enchant-1447-picker-49983")
		assertEquals(setOf("Sharpness", "Smite", "Fire Aspect", "Experience", "Bane of Arthropods"), m.tiles.filter { it.applied }.map { it.option.base }.toSet())
		assertEquals(10, m.missingCount)
		val def = EnchantConflicts.groups(m.tiles, "Default").map { it.first }
		assertEquals(listOf("Applied", "Missing"), def)
		val mf = EnchantConflicts.groups(m.tiles, EnchantConflicts.MISSING_FIRST)
		assertEquals(listOf("Missing", "Applied"), mf.map { it.first })
		assertEquals(10, mf.first().second.size)
	}

	@Test fun conflictsNeverMissing() {
		val d = RealDumpFixtures.load("enchant-1447-picker-48677")
		val ofa = SlotText(43, "One For All", listOf("Removes all enchantments and grants +500% damage.", "", "One For All ✖", "", "Click to view!"))
		val base = EnchantParser.parse(d.title, d.slots + ofa, 54, emptySet(), emptySet())!!
		val m = EnchantParser.withConflicts(base, d.slots + ofa, pools)
		val t = m.tiles.first { it.option.base == "One For All" }
		assertFalse(t.missing)
		assertEquals("Sharpness", t.conflictsWith)
		assertEquals(14, m.missingCount)
		val mf = EnchantConflicts.groups(m.tiles, EnchantConflicts.MISSING_FIRST)
		assertEquals(listOf("Missing", "Applied", "Conflicts"), mf.map { it.first })
		assertTrue(mf.first().second.none { it.option.base == "One For All" })
		assertEquals(listOf("One For All"), mf.last().second.map { it.option.base })
	}

	@Test fun liveLoreOverridesData() {
		val fakePools = listOf(listOf("sharpness", "smite", "bane_of_arthropods"))
		val m = picker("enchant-1447-picker-48851")
		val d = RealDumpFixtures.load("enchant-1447-picker-48851")
		val r = EnchantParser.withConflicts(m, d.slots, fakePools)
		assertTrue(r.tiles.first { it.option.base == "Smite" }.applied)

		assertEquals("Sharpness", r.tiles.first { it.option.base == "Bane of Arthropods" }.conflictsWith)
		assertEquals("bane_of_arthropods", EnchantConflicts.idOf("Bane of Arthropods"))
		assertEquals("magmarizer", EnchantConflicts.idOf("Pyroclasm", mapOf("pyroclasm" to "magmarizer")))
	}
}

class EnchantCostLineTest {

	@Test fun costSkipsHeading() {
		val o = EnchantParser.option(SlotText(29, "Sharpness V", listOf("Increases melee damage dealt by 25%.", "", "Cost", "30 23 Exp Levels ✔", "", "Click to enchant!")))
		assertEquals(23, o.cost)
	}
}
