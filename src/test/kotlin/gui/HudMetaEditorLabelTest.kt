package dev.ashtonite365.apex.test.gui

import dev.ashtonite365.apex.gui.config.HudMeta
import dev.ashtonite365.apex.gui.config.HudPosition
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HudMetaEditorLabelTest {
	private fun meta(key: String) = HudMeta(HudPosition(0, 0, 1f), Identifier.fromNamespaceAndPath("t", "t"), Component.translatable(key), 10, 10)

	@Test
	fun hiddenEditorLabelsHidesByTranslationKey() {
		val key = "apex.config.test-h6.hud"
		assertTrue(meta(key).showsEditorLabel)
		HudMeta.hiddenEditorLabels.add(key)
		try {
			assertFalse(meta(key).showsEditorLabel)
			assertTrue(meta("apex.config.test-h6.other").showsEditorLabel)
		} finally {
			HudMeta.hiddenEditorLabels.remove(key)
		}
		assertTrue(meta(key).showsEditorLabel)
	}
}
