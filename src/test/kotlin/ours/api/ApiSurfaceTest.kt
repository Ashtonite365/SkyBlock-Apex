// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.ours.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.api.v1.ApexExtension
import dev.ashtonite365.apex.api.v1.ApexItemWidget

class ApiSurfaceTest {
	@Test
	fun placementsKeepTheirLabels() {
		assertEquals("ITEM_LIST", ApexItemWidget.Placement.ITEM_LIST.name())
		assertEquals("RECIPE_SCREEN", ApexItemWidget.Placement.RECIPE_SCREEN.name())
		assertTrue(ApexItemWidget.Placement.ITEM_LIST !== ApexItemWidget.Placement.RECIPE_SCREEN)
	}

	@Test
	fun extensionDefaultsAreNeutral() {
		assertEquals("apex:v1", ApexExtension.ENTRYPOINT_NAME)
		val ext = object : ApexExtension {}
		ext.onLoad()

		val unsafe = sun.misc.Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null) as sun.misc.Unsafe
		val screen = unsafe.allocateInstance(net.minecraft.client.gui.screens.TitleScreen::class.java) as net.minecraft.client.gui.screens.Screen
		assertFalse(ext.shouldHideInventoryButtons(screen))
		assertTrue(ext.getExclusionZones(screen).isEmpty())
	}
}
