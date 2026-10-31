// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.test.ours.hud

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.gui.config.FeatureRisk
import dev.ashtonite365.apex.gui.config.HudMetaHandler
import dev.ashtonite365.apex.gui.hud.editor.HudEditors
import dev.ashtonite365.apex.test.gui.config.AllRegisteredConfigs

@Tag("moulplatform")
class HudEditorsSpecTest {
	init {
		dev.ashtonite365.apex.test.ApexTestBootstrap.bootstrapMinecraft()
		dev.ashtonite365.apex.test.ours.gui.GuiComponentsEquivalenceTest.platformReady
	}

	private val managed by lazy { AllRegisteredConfigs.configs }

	@Test
	fun hudsAndSearchEntries() {
		assertEquals("SkyBlock Apex", HudEditors.MOD_NAME)

		val hudOptions = managed.flatMap { c -> c.sortedOptions.filter { it.handler is HudMetaHandler } }
		val unloaded = hudOptions.filter { it._actualValue == null }
		@Suppress("UNCHECKED_CAST")
		for (o in unloaded) (o as dev.ashtonite365.apex.gui.config.ManagedOption<Any>)._actualValue = o.default()
		try {
			val huds = hudOptions.map { it.value }
			assertTrue(huds.isNotEmpty())
			assertEquals(huds, HudEditors.allHuds(managed))
		} finally {
			for (o in unloaded) o._actualValue = null
		}
		val labels = managed.flatMap { c -> FeatureRisk.visibleOptions(c).map { it.labelText } }
		val entries = HudEditors.searchOptions(managed)
		assertEquals(labels, entries.map { it.title })
		assertTrue(entries.all { it.description.isEmpty() })
	}
}
