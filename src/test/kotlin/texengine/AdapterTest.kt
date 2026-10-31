package dev.ashtonite365.apex.test.texengine

import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.texengine.ApexTextureEngines
import dev.ashtonite365.apex.texengine.BlockHooks
import dev.ashtonite365.apex.texengine.apex.ApexEngine
import dev.ashtonite365.apex.texengine.apex.ApexArmor
import dev.ashtonite365.apex.texengine.apex.ApexItems
import dev.ashtonite365.apex.texengine.apex.ApexSkulls
import dev.ashtonite365.apex.texengine.apex.Settings
import dev.ashtonite365.apex.texengine.apex.TextureConfig
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(AutoBootstrapExtension::class)
@Tag("texengine-golden")
class AdapterTest {
	@Test
	fun `default engine is the apex one with every area its own`() {
		assertSame(ApexEngine, ApexTextureEngines.active)
		assertEquals("apex", ApexTextureEngines.active.id)
		assertSame(ApexItems, ApexEngine.items)
		assertSame(ApexSkulls, ApexEngine.skulls)
		assertSame(ApexArmor, ApexEngine.armor)
		assertSame(dev.ashtonite365.apex.texengine.apex.ApexBlocks, ApexEngine.blocks)
		assertSame(dev.ashtonite365.apex.texengine.apex.ApexScreens, ApexEngine.screens)
		assertSame(dev.ashtonite365.apex.texengine.apex.ApexTexts, ApexEngine.texts)
	}

	@Test
	fun `the engine reads the user's texture config`() {
		ApexTextureEngines.active
		assertSame(TextureConfig, Settings.source)
		assertEquals("custom-skyblock-textures", TextureConfig.name)
	}

	@Test
	fun `texture config keeps every saved key with its default`() {
		TexConfig.reset()
		val expected = linkedMapOf<String, Any>(
			"enabled" to true, "skulls-enabled" to true, "cache-forever" to true, "cache-duration" to 1,
			"model-overrides" to true, "armor-overrides" to true, "block-overrides" to true,
			"legacy-minecraft-path-support" to true, "legacy-cit" to true, "recolor-text" to true, "screen-layouts" to true,
		)
		assertEquals(expected.keys.toList(), TextureConfig.allOptions.keys.toList())
		for ((key, value) in expected) assertEquals(value, TextureConfig.allOptions.getValue(key).get(), key)
	}

	@Test
	fun `block hooks keep the original model when nothing is replaced`() {
		val state = Blocks.STONE.defaultBlockState()
		assertNull(ApexTextureEngines.active.blocks.modelFor(state, null))
		assertNull(ApexTextureEngines.active.blocks.soundFor(state, null))
		val original = Fakes.alloc(net.minecraft.client.renderer.block.dispatch.SingleVariant::class.java)
		assertSame(state, BlockHooks.beforeRendererGet(state))
		assertSame(original, BlockHooks.positionless(original, state))
		assertSame(original, BlockHooks.afterRendererGet(original, state, net.minecraft.core.BlockPos.ZERO))
		assertSame(original, BlockHooks.positionless(original, state))
	}

	@Test
	fun `no active layout and the no-layout title changes nothing but colour rules`() {
		assertNull(ApexTextureEngines.active.screens.active)
		val title = ApexTextureEngines.active.screens.noLayoutTitle
		val text = Component.literal("Chest")
		assertSame(text, title.text(text))
		assertEquals(7, title.y(7))
	}
}
