// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.kit.skyblock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.kit.skyblock.LoadoutKeybinds
import dev.ashtonite365.apex.features.kit.skyblock.PetKeybinds
import dev.ashtonite365.apex.features.kit.skyblock.Ragnarock

class KitSkyblockLogicTest {
	@Test
	fun ragnarockCancel() {
		assertTrue(Ragnarock.Logic.isCancel("Ragnarock was cancelled due to being hit!"))
		assertTrue(Ragnarock.Logic.isCancel("Ragnarock was cancelled due to taking damage!"))
		assertFalse(Ragnarock.Logic.isCancel("Ragnarock was cancelled due to boredom!"))
	}

	@Test
	fun ragnarockStrength() {
		assertEquals(1050, Ragnarock.Logic.strengthFromLore(listOf("Damage: +200", "Strength: +1,050 (+30)")))
		assertNull(Ragnarock.Logic.strengthFromLore(listOf("Damage: +200")))
		assertEquals(1575, Ragnarock.Logic.gained(1050))
		assertTrue(Ragnarock.Logic.isCastSound("entity.wolf.death", 1.4920635f))
		assertFalse(Ragnarock.Logic.isCastSound("entity.wolf.death", 1f))
		assertFalse(Ragnarock.Logic.isCastSound("entity.cat.death", 1.4920635f))
	}

	@Test
	fun petPages() {
		assertEquals(1 to 1, PetKeybinds.Logic.page("Pets"))
		assertEquals(2 to 3, PetKeybinds.Logic.page("(2/3) Pets"))
		assertNull(PetKeybinds.Logic.page("Pets Menu Extra"))
		assertEquals(9, PetKeybinds.Logic.petSlots.size)
	}

	@Test
	fun loadoutPages() {
		assertEquals(1 to 2, LoadoutKeybinds.Logic.page("(1/2) Loadout"))
		assertNull(LoadoutKeybinds.Logic.page("Wardrobe"))
		assertEquals(12, LoadoutKeybinds.Logic.loadoutSlots.size)
	}
}
