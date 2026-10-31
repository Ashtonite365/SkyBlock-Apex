// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class GoldenScreensTest {
	private val engine: GoldenEngine = ApexGolden

	@BeforeEach
	fun loadConfigDefaults() = TexConfig.reset()

	@AfterEach
	fun resetConfig() = TexConfig.reset()

	@Test
	fun `G-SCR-1 layouts`() {
		engine.loadScreenLayouts(FixturePacks.manager("screens"))
		assertEquals(GoldenLayout(listOf(Triple(0, 1, 2)), null), engine.activeLayout(Component.literal("Bazaar")))
		assertNull(engine.activeLayout(Component.literal("Edit Sign")))
		assertEquals(5, engine.activeLayout(Component.literal("Edit Sign"), sign = true)?.slotMoves?.single()?.second)
		assertEquals(4, engine.activeLayout(Component.literal("Edit Sign"), sign = true)?.signLineCount)
		assertNull(engine.activeLayout(Component.literal("Other")))
		TexConfig.set("screen-layouts", false)
		assertNull(engine.activeLayout(Component.literal("Bazaar")))
	}

	@Test
	fun `G-SCR-3 sign lines must be four`() {
		val loaded = runCatching { engine.loadScreenLayouts(FixturePacks.manager("screens_badsign")) }
		if (loaded.isSuccess) assertNull(engine.activeLayout(Component.literal("Bad")))
		engine.loadScreenLayouts(FixturePacks.manager())
	}

	@Test
	fun `G-COL-1 text colours`() {
		engine.loadTextColors(FixturePacks.manager("colors"))
		assertEquals(255, engine.textColor(Component.literal("Bazaar"), 7))
		assertEquals(16777215, engine.textColor(Component.literal("Other"), 7))
		engine.loadTextColors(FixturePacks.manager())
		assertEquals(7, engine.textColor(Component.literal("Bazaar 2"), 7))
	}

	private fun texts() = engine.loadTextReplacements(FixturePacks.manager("texts"))

	@Test
	fun `G-TXT-1 regex group injection keeps part styles`() {
		texts()
		val text = Component.literal("Price: 1,000 Coins").withStyle(ChatFormatting.GOLD)
		val out = engine.replaceText(text)
		assertEquals("Price: 1,000 ¢", out.string)
		assertEquals(net.minecraft.network.chat.TextColor.fromLegacyFormat(ChatFormatting.GOLD), out.toFlatList().first().style.color)
	}

	@Test
	fun `G-TXT-2 no outer match leaves the text unchanged`() {
		texts()
		val text = Component.literal("Nothing here")
		assertEquals(text, engine.replaceText(text))
	}

	@Test
	fun `G-TXT-3 style filter`() {
		texts()
		val text = Component.literal("Gems ").append(Component.literal("Gems").withStyle(ChatFormatting.BOLD))
		assertEquals("Gems G", engine.replaceText(text).string)
	}

	@Test
	fun `G-TXT-4 equals sub-match is literal`() {
		texts()
		assertEquals("abc X", engine.replaceText(Component.literal("abc a.c")).string)
	}

	@Test
	fun `G-TXT-5 files apply cumulatively against the original text`() {
		texts()
		assertEquals("Baz Baz", engine.replaceText(Component.literal("Foo Bar")).string)

		assertEquals("Bar", engine.replaceText(Component.literal("Foo")).string)
	}

	@Test
	fun `G-SKT-1 skull texture id from a skin url`() {
		assertEquals(Identifier.parse("firmskyblock:textures/placedskull/abc123.png"),
			engine.skullTextureId("http://textures.minecraft.net/texture/abc123"))
		assertEquals(Identifier.parse("firmskyblock:textures/placedskull/abc123.png"),
			engine.skullTextureId("https://textures.minecraft.net/texture/abc123"))
		assertNull(engine.skullTextureId("http://example.com/texture/abc123"))
	}

	@Test
	fun `G-SKT-2 uppercase hex is lower-cased`() {

		assertEquals(Identifier.parse("firmskyblock:textures/placedskull/abc123.png"),
			engine.skullTextureId("http://textures.minecraft.net/texture/ABC123"))
	}
}
