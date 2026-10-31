// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.menus.support.EnchantLabel
import dev.ashtonite365.apex.features.menus.support.EnchantLabel.Label
import dev.ashtonite365.apex.test.features.menus.h3.RealDumpFixtures

class EnchantLabelTest {
	private fun slot(f: String, n: Int) = RealDumpFixtures.load(f).slots.first { it.slot == n }
	private val width: (String) -> Int = { it.replace(Regex("§."), "").length * 6 }

	@Test
	fun realBookLore() {
		val bank = slot("dungeon-chest-bank-book-1447", 12)
		assertEquals("Enchanted Book", bank.name)
		assertEquals(Label("Bank", 1, ultimate = true, rarity = "COMMON"), EnchantLabel.fromLore(bank.lore))
		val feather = slot("dungeon-chest-feather-book-1447", 12)
		assertEquals(Label("Feather Falling", 6, ultimate = false, rarity = "RARE"), EnchantLabel.fromLore(feather.lore))
		assertEquals("Infinite Quiver VI", EnchantLabel.fromLore(slot("dungeon-chest-feather-book-1447", 11).lore)?.text)

		assertEquals("Feather Falling VI", EnchantLabel.bazaarName("ENCHANTMENT_FEATHER_FALLING_6", feather.name, feather.lore))
		assertEquals("Bank I", EnchantLabel.bazaarName("ENCHANTMENT_ULTIMATE_BANK_1", bank.name, bank.lore))

		assertEquals("§9", EnchantLabel.style(EnchantLabel.fromLore(feather.lore)!!))
		assertEquals("§d§l", EnchantLabel.style(EnchantLabel.fromLore(bank.lore)!!))
	}

	@Test
	fun idFallback() {
		assertEquals(Label("Ultimate Wise", 5, ultimate = true), EnchantLabel.fromId("ENCHANTMENT_ULTIMATE_WISE_5"))
		assertEquals("Ultimate Wise V", EnchantLabel.fromId("ENCHANTMENT_ULTIMATE_WISE_5")!!.text)
		assertEquals("Sharpness VII", EnchantLabel.fromId("ENCHANTMENT_SHARPNESS_7")!!.text)
		assertEquals("Bane Of Arthropods X", EnchantLabel.fromId("ENCHANTMENT_BANE_OF_ARTHROPODS_10")!!.text)
		assertEquals("Ultimate Bank I", EnchantLabel.fromId("ULTIMATE_BANK;1")!!.text)
		assertNull(EnchantLabel.fromId("WITHER_ESSENCE"))

		assertEquals("Ultimate Wise V", EnchantLabel.bazaarName("ENCHANTMENT_ULTIMATE_WISE_5", "Enchanted Book"))
		assertEquals("Wither Essence", EnchantLabel.bazaarName("ESSENCE_WITHER", "Wither Essence"))
		assertNull(EnchantLabel.bazaarLabel("ESSENCE_WITHER", "Wither Essence"))

		assertTrue(EnchantLabel.bazaarLabel("ENCHANTMENT_ULTIMATE_WISE_5", "Enchanted Book", listOf("Wise V", "", "LEGENDARY"))!!.ultimate)
	}

	@Test
	fun wrapsNeverTruncates() {
		val l = EnchantLabel.fromId("ENCHANTMENT_ULTIMATE_LEGION_5")!!
		val lines = EnchantLabel.wrapStyled(l, 60, width)
		assertEquals(listOf("§d§lUltimate", "§d§lLegion V"), lines)
		val long = Label("Bane of Arthropods", 10)
		assertEquals(listOf("Bane of", "Arthropods", "X"), EnchantLabel.wrap(long.text, 60, width))
		assertEquals(listOf("Supercalifragilistic", "V"), EnchantLabel.wrap("Supercalifragilistic V", 60, width))
		assertEquals(listOf("Sharpness V"), EnchantLabel.wrap("Sharpness V", 200, width))

		assertEquals(long.text, EnchantLabel.wrap(long.text, 30, width).joinToString(" "))
	}

	@Test
	fun levelPagesSharpnessBaneLooting() {
		fun opt(f: String, n: Int) = slot(f, n).let { EnchantLabel.levelOption(it.name, it.lore) }
		val sharp5 = opt("enchant-item-sharpness-1447", 32)!!
		assertEquals(EnchantLabel.LevelOption(Label("Sharpness", 5), 30, 23, true), sharp5)
		assertEquals("Sharpness V · 30 → 23 Exp Levels", EnchantLabel.levelText(sharp5))
		assertEquals(EnchantLabel.LevelOption(Label("Sharpness", 1), 10, 8, true), opt("enchant-item-sharpness-1447", 22))
		assertEquals(EnchantLabel.LevelOption(Label("Bane of Arthropods", 3), 20, 15, true), opt("enchant-item-bane-1447", 24))
		assertEquals(EnchantLabel.LevelOption(Label("Bane of Arthropods", 4), 25, 19, false), opt("enchant-item-bane-1447", 31))
		assertEquals(EnchantLabel.LevelOption(Label("Bane of Arthropods", 5), 30, 23, false), opt("enchant-item-bane-1447", 32))
		assertEquals(EnchantLabel.LevelOption(Label("Looting", 1), 15, 12, true), opt("enchant-item-looting-1447", 21))
		assertEquals(EnchantLabel.LevelOption(Label("Looting", 3), 45, 34, true), opt("enchant-item-looting-1447", 24))

		for (n in listOf(19, 28, 45, 49, 50)) assertNull(opt("enchant-item-sharpness-1447", n), "slot $n")

		for (f in listOf("enchant-item-sharpness-1447", "enchant-item-bane-1447", "enchant-item-looting-1447")) {
			val opts = RealDumpFixtures.load(f).slots.filter { it.lore.any { l -> l.trim() == "Cost" } }
			assertTrue(opts.size >= 3, f)
			for (s in opts) {
				val o = EnchantLabel.levelOption(s.name, s.lore)!!
				assertTrue(o.fullCost!! > o.cost!!, "${s.name}: $o")
			}
		}

		val plain = EnchantLabel.levelOption("Smite II", listOf("Cost", "15 Exp Levels ✔"))!!
		assertEquals(15, plain.fullCost); assertEquals(15, plain.cost)
		assertEquals("Smite II · 15 Exp Levels", EnchantLabel.levelText(plain))
		assertFalse(EnchantLabel.levelOption("Smite II", emptyList())!!.cost != null)
	}
}
