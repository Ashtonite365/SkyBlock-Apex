// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.generic

import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.generic.GenericWrapper
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StorageNotWrappedTest {
	private fun wrapped(title: String, inSkyBlock: Boolean = true, foreignGui: Boolean = false) =
		GenericWrapper.eligible(true, inSkyBlock, true, MenuRegistry.denied(title), MenuRegistry.vanillaOwner(title) != null,
			claimed = GenericWrapper.featureOwned(title, foreignGui))

	@Test fun storageTitleIsNotWrapped() {
		assertFalse(wrapped("Storage"))
		assertFalse(wrapped("Storage", foreignGui = false))
		assertTrue(GenericWrapper.featureOwned("Storage", false))
	}

	@Test fun storageStaysUnwrappedWhenSkyBlockIsDetectedLate() {
		assertFalse(wrapped("Storage", inSkyBlock = false))

		assertFalse(GenericWrapper.lateAttach(alreadyAttached = false, eligibleNow = wrapped("Storage", inSkyBlock = true)))
	}

	@Test fun featureOwnedMenusAreNeverWrapped() {
		for (t in listOf("Ender Chest (1/9)", "Ender Chest (9/9)", "Large Backpack (Slot #3)", "Jumbo Backpack ✦ (Slot #18)"))
			assertFalse(wrapped(t), t)

		assertFalse(wrapped("Some Unknown Chest", foreignGui = true))

		assertTrue(wrapped("Essence Guide"))
	}

	@Test fun fillerPanesAreEmptySpaceNotGreyBars() {
		assertTrue(GenericWrapper.isFiller("light_gray_stained_glass_pane", " "))
		assertTrue(GenericWrapper.isFiller("black_stained_glass_pane", "§r"))
		assertTrue(GenericWrapper.isFiller("gray_stained_glass_pane", ""))
		assertFalse(GenericWrapper.isFiller("light_gray_stained_glass_pane", "Locked Backpack Slot"))
		assertFalse(GenericWrapper.isFiller("ender_chest", " "))
	}
}
