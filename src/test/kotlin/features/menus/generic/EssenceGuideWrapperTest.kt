// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.generic

import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.generic.GenericWrapper
import dev.ashtonite365.apex.features.menus.support.SlotText
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EssenceGuideWrapperTest {
	private fun dump(): Pair<String, List<SlotText>> {
		val lines = javaClass.getResourceAsStream("/menus-1031/essence-guide-slotdump.txt")!!.bufferedReader().readLines()
		val title = lines.first().substringAfter("title=")
		val re = Regex("slot=(\\d+) name=(.*?) lore=\\[(.*)]$")
		val slots = lines.drop(1).mapNotNull { re.find(it) }.map { m ->
			SlotText(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3].takeIf { it.isNotEmpty() }?.split(", ") ?: emptyList()) }
		return title to slots
	}

	@Test fun noSlotMatcherClaimsTheEssenceGuide() {
		val (t, s) = dump()
		assertEquals("Essence Guide", t); assertEquals(54, s.size)
		val claims = mapOf(
			"npc-shop" to { dev.ashtonite365.apex.features.menus.h6.NpcShopParser.parse(t, s) != null },
			"xp-guide" to { dev.ashtonite365.apex.features.menus.levelling.XpGuideParser.parse(t, s) != null },
			"rewards" to { dev.ashtonite365.apex.features.menus.levelling.RewardsParser.parse(t, s) != null },
			"emblems" to { dev.ashtonite365.apex.features.menus.levelling.EmblemsParser.parse(t, s) != null },
			"rng" to { dev.ashtonite365.apex.features.menus.rng.RngMeterParser.isRngMeter(s) },
			"chapter" to { dev.ashtonite365.apex.features.menus.quests.ChapterParser.matchesBySlots(t, s) },
			"reforge-guide" to { dev.ashtonite365.apex.features.menus.guides.ReforgeGuideParser.isDetail(s) },
			"bestiary" to { dev.ashtonite365.apex.features.menus.bestiary.BestiaryParser.isSubAreaPage(t, s) },
			"collections" to { dev.ashtonite365.apex.features.menus.collections.CollectionsParser.matchesItemPage(t, s) },
		).filter { runCatching { it.value() }.getOrDefault(false) }.keys
		assertEquals(emptySet<String>(), claims)
	}

	@Test fun essenceGuideGetsTheWrapper() {
		assertFalse(MenuRegistry.denied("Essence Guide"))
		assertEquals(null, MenuRegistry.vanillaOwner("Essence Guide"))
		assertTrue(GenericWrapper.eligible(isChest = true, inSkyBlock = true, enabled = true, denied = false, vanillaOwned = false, claimed = false))

		assertFalse(MenuRegistry.retiresGeneric(attached = false)); assertTrue(MenuRegistry.retiresGeneric(attached = true))
	}

	@Test fun aChestOpenedBeforeSkyBlockWasDetectedIsWrappedLater() {
		val g = dev.ashtonite365.apex.features.menus.generic.GenericWrapper
		assertFalse(g.eligible(true, inSkyBlock = false, enabled = true, denied = false, vanillaOwned = false, claimed = false))
		assertTrue(g.lateAttach(alreadyAttached = false, eligibleNow = g.eligible(true, true, true, false, false, false)))
		assertFalse(g.lateAttach(alreadyAttached = true, eligibleNow = true))
	}
}
