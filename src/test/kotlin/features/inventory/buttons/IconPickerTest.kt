// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.inventory.buttons

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.buttons.IconPickerModel

class IconPickerTest {
	private val entries = IconPickerModel.build(
		vanilla = listOf("air" to "Air", "diamond_sword" to "Diamond Sword", "ender_chest" to "Ender Chest", "bone" to "Bone"),
		skyblock = listOf("ASPECT_OF_THE_END" to "§9Aspect of the End", "ENCHANTED_DIAMOND" to "§aEnchanted Diamond", "DIAMOND_SWORD" to "§fDiamond Sword"),
	)

	@Test
	fun buildsSkyblockThenVanillaWithoutAirAndStripsColours() {
		Assertions.assertEquals(listOf("ASPECT_OF_THE_END", "DIAMOND_SWORD", "ENCHANTED_DIAMOND", "bone", "diamond_sword", "ender_chest"), entries.map { it.icon })
		Assertions.assertEquals("Aspect of the End", entries[0].name)
		Assertions.assertTrue(entries[0].skyblock); Assertions.assertFalse(entries[3].skyblock)
	}

	@Test
	fun searchByNameIdAndWords() {
		val m = IconPickerModel(entries, columns = 2, rows = 2)
		m.search("diamond")
		Assertions.assertEquals(listOf("DIAMOND_SWORD", "ENCHANTED_DIAMOND", "diamond_sword"), m.filtered.map { it.icon })
		m.search("  SWORD diamond ")
		Assertions.assertEquals(listOf("DIAMOND_SWORD", "diamond_sword"), m.filtered.map { it.icon })
		m.search("aspect_of")
		Assertions.assertEquals(listOf("ASPECT_OF_THE_END"), m.filtered.map { it.icon })
		m.search("ender")
		Assertions.assertEquals(listOf("ender_chest"), m.filtered.map { it.icon })
		m.search("zzz"); Assertions.assertTrue(m.filtered.isEmpty()); Assertions.assertNull(m.pick(0, 0))
		m.search(""); Assertions.assertEquals(6, m.filtered.size)
	}

	@Test
	fun gridSelectionAndScrolling() {
		val m = IconPickerModel(entries, columns = 2, rows = 2)
		Assertions.assertEquals("ASPECT_OF_THE_END", m.pick(0, 0))
		Assertions.assertEquals("ENCHANTED_DIAMOND", m.pick(0, 1))
		Assertions.assertNull(m.pick(2, 0)); Assertions.assertNull(m.pick(0, 2)); Assertions.assertNull(m.pick(-1, 0))
		Assertions.assertEquals(3, m.totalRows); Assertions.assertEquals(1, m.maxScroll)
		m.scroll(5); Assertions.assertEquals(1, m.scrollRow)
		Assertions.assertEquals("ENCHANTED_DIAMOND", m.pick(0, 0))
		Assertions.assertEquals("ender_chest", m.pick(1, 1))
		m.scroll(-9); Assertions.assertEquals(0, m.scrollRow)
		m.scroll(1); m.search("bone")
		Assertions.assertEquals(0, m.scrollRow); Assertions.assertEquals("bone", m.pick(0, 0))
	}
}
