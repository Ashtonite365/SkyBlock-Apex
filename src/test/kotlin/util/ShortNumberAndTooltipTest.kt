package dev.ashtonite365.apex.test.util

import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.inventory.RarityTooltips
import dev.ashtonite365.apex.util.parseShortNumber
import dev.ashtonite365.apex.util.parseShortNumberOrNull
import dev.ashtonite365.apex.util.skyblock.Rarity

class ShortNumberAndTooltipTest {
	@Test
	fun shortNumbers() {
		assertEquals(48.0, parseShortNumberOrNull("48\u00a77 Mana"))
		assertEquals(48.0, parseShortNumberOrNull("\u00a7b48\u00a7r"))
		assertEquals(1200.0, parseShortNumberOrNull("1.2k"))
		assertEquals(3_000_000.0, parseShortNumberOrNull("3M)"))
		assertEquals(12345.0, parseShortNumberOrNull("12,345"))
		assertEquals(-5.0, parseShortNumberOrNull("-5"))
		assertNull(parseShortNumberOrNull("\u00a7"))
		assertNull(parseShortNumberOrNull("abc"))
		assertEquals(0.0, parseShortNumber("??"))
	}

	@Test
	fun tooltipStyleResolution() {
		val hyp = Identifier.fromNamespaceAndPath("hypixel_skyblock", "legendary")
		val ours = RarityTooltips.styleFor(Rarity.LEGENDARY)
		val all: (Identifier) -> Boolean = { true }
		val onlyOurs: (Identifier) -> Boolean = { it.namespace == "apex" }
		val none: (Identifier) -> Boolean = { false }
		assertEquals(ours, RarityTooltips.resolve(hyp, Rarity.LEGENDARY, true, true, all))
		assertEquals(hyp, RarityTooltips.resolve(hyp, Rarity.LEGENDARY, true, false, all))
		assertEquals(ours, RarityTooltips.resolve(hyp, Rarity.LEGENDARY, true, false, onlyOurs))
		assertNull(RarityTooltips.resolve(hyp, Rarity.LEGENDARY, false, false, onlyOurs))
		assertNull(RarityTooltips.resolve(hyp, null, true, true, none))
		assertNull(RarityTooltips.resolve(null, null, true, true, all))
		assertEquals(ours, RarityTooltips.resolve(ours, null, true, true, none))
	}
}
