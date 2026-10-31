// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.features.menus.generic

import dev.ashtonite365.apex.features.menus.CustomMenu
import dev.ashtonite365.apex.features.menus.MenuRegistry
import dev.ashtonite365.apex.features.menus.MenuRegistry.Resolution
import dev.ashtonite365.apex.features.menus.generic.GenericWrapper
import dev.ashtonite365.apex.features.menus.skyblock.ElectionMenu
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GenericFallbackTest {
	private val menu = object : CustomMenu by ElectionMenu { override val enabled = true }

	@Test fun unknownAndFailedMenusAreWrapped() {
		assertTrue(MenuRegistry.coveredByGeneric(Resolution.None))
		assertTrue(MenuRegistry.coveredByGeneric(Resolution.Fallback(menu, "could not parse the page")))
	}

	@Test fun deliberateChoicesAndRealViewsAreNot() {
		assertFalse(MenuRegistry.coveredByGeneric(Resolution.Vanilla(menu)))
		assertFalse(MenuRegistry.coveredByGeneric(Resolution.Vanilla(menu, sectionOff = true)))
		assertFalse(MenuRegistry.coveredByGeneric(Resolution.SelfHosted(menu)))
	}

	@Test fun storagePagesStayWithTheStorageView() {
		assertTrue(GenericWrapper.featureOwned("Storage", foreignGuiInstalled = false))
		assertTrue(GenericWrapper.featureOwned("Anything", foreignGuiInstalled = true))
	}
}
