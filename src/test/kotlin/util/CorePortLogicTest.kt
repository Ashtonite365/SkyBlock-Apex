// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.auction.AuctionTools
import dev.ashtonite365.apex.features.auction.BinWarning
import dev.ashtonite365.apex.util.math.Calculator

class CorePortLogicTest {
	@Test
	fun calculator() {
		assertEquals("4500", Calculator.format(Calculator.evaluate("1.5k*3")))
		assertEquals("128", Calculator.format(Calculator.evaluate("2s")))
		assertEquals("7", Calculator.format(Calculator.evaluate("1+2*3")))
		assertEquals("9", Calculator.format(Calculator.evaluate("(1+2)^2")))
		assertEquals("-2", Calculator.format(Calculator.evaluate("-(1+1)")))
		assertEquals("2000000", Calculator.format(Calculator.evaluate("2m")))
		assertNull(Calculator.evaluateOrNull("hello"))
		assertNull(Calculator.evaluateOrNull("1/0"))
		assertTrue(Calculator.isPlainNumber("1,000"))
	}

	@Test
	fun signLines() {
		assertEquals(listOf("short", "", "", ""), AuctionTools.splitIntoSignLines("short"))
		assertEquals(listOf("Hyperion Wither", "Blade Necron", "", ""), AuctionTools.splitIntoSignLines("Hyperion Wither Blade Necron"))
	}

	@Test
	fun binWarning() {
		assertTrue(BinWarning.shouldWarn(80, 100.0, 10, 50, true))
		assertFalse(BinWarning.shouldWarn(95, 100.0, 10, 50, true))
		assertTrue(BinWarning.shouldWarn(200, 100.0, 10, 50, true))
		assertFalse(BinWarning.shouldWarn(200, 100.0, 10, 50, false))
	}
}

class CorePortLogicTest3 {
	@Test
	fun damageCommas() {
		assertEquals("§712.3M§r", dev.ashtonite365.apex.features.misc.DamageCommas.rewrite("§712,345,678"))
		assertNull(dev.ashtonite365.apex.features.misc.DamageCommas.rewrite("§7999"))
	}

	@Test
	fun searchCalculator() {
		val vars = Calculator.VariableProvider { if (it == "FOO") java.math.BigDecimal(10) else null }
		assertEquals("25", Calculator.format(Calculator.evaluate("2*FOO+5", vars)))
		assertNull(Calculator.evaluateOrNull("BAR*2", vars))
	}

	@Test
	fun enchantColours() {
		val rules = dev.ashtonite365.apex.features.inventory.EnchantColours.DEFAULT_RULES.split("|")
			.mapNotNull { dev.ashtonite365.apex.features.inventory.EnchantColours.parseRule(it) }
		assertEquals(3, rules.size)
		val ec = dev.ashtonite365.apex.features.inventory.EnchantColours
		val max = mapOf("Sharpness" to 7, "Smite" to 7, "Experience" to 5)

		assertEquals("§5Sharpness VI§9, §9Smite V", ec.replaceLine("§9Sharpness VI§9, §9Smite V", rules) { max[it] })
		assertEquals("§6Experience V", ec.replaceLine("§9Experience V", rules) { max[it] })
		assertEquals("§9Mystery X", ec.replaceLine("§9Mystery X", rules) { max[it] })

		assertEquals("§cSmite V", ec.replaceLine("§9Smite V", listOfNotNull(ec.parseRule("Smite:>:4:c:0"))))
	}
}
