// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import com.google.gson.JsonParser
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.texengine.apex.ApexItems
import dev.ashtonite365.apex.texengine.apex.ApexSkulls
import dev.ashtonite365.apex.texengine.apex.ItemModelTypes
import dev.ashtonite365.apex.texengine.apex.LegacyItemModels
import dev.ashtonite365.apex.texengine.apex.PackJson
import dev.ashtonite365.apex.texengine.apex.PackJsonException
import dev.ashtonite365.apex.texengine.apex.ScreenItemOverrides
import dev.ashtonite365.apex.texengine.ItemModelCatalog
import com.mojang.serialization.JsonOps
import java.nio.file.Files
import net.minecraft.client.renderer.item.ClientItem
import net.minecraft.client.renderer.item.CuboidItemModelWrapper
import net.minecraft.client.renderer.item.ItemModels
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class ApexItemsTest {
	@BeforeEach
	fun setUp() {
		TexConfig.reset()
		ApexGolden.id
		ScreenItemOverrides.use(listOf())
		ScreenItemOverrides.screenChanged(null)
		ApexItems.clearCache()
	}

	@AfterEach
	fun tearDown() = setUp()

	private fun id(s: String) = Identifier.parse(s)
	private val none = object : ItemModelCatalog {
		override fun hasItemModel_Apex(identifier: Identifier) = false
	}

	@Test
	fun `pack json reads standard json like Gson`() {
		val text = """{"a":[1,2.50,-3e2,true,false,null,"x\"\\\u00e4"],"b":{"c":{}},"d":[]}"""
		assertEquals(JsonParser.parseString(text), PackJson.parse(text))
		assertEquals("2.50", PackJson.parse(text).asJsonObject["a"].asJsonArray[1].asString)
	}

	@Test
	fun `pack json accepts comments, trailing commas and unquoted strings`() {
		val text = """
			// leading comment
			{ /* block */ "model": item/x, "list": [1, 2,], unquoted: yes, }
		"""
		val o = PackJson.parse(text).asJsonObject
		assertEquals("item/x", o["model"].asString)
		assertEquals(2, o["list"].asJsonArray.size())
		assertEquals("yes", o["unquoted"].asString)
	}

	@Test
	fun `pack json rejects broken input`() {
		for (bad in listOf("{", "[1 2]", "{\"a\" 1}", "\"open", "{} x", "/* open")) {
			assertThrows(PackJsonException::class.java, { PackJson.parse(bad) }, bad)
		}
	}

	@Test
	fun `override cache is per stack identity and cleared on demand`() {
		val stack = Stacks.sb(Items.IRON_SWORD, id = "HYPERION")
		assertEquals(id("firmskyblock:hyperion"), ApexItems.overrideFor(stack, none))
		TexConfig.set("enabled", false)

		assertEquals(id("firmskyblock:hyperion"), ApexItems.overrideFor(stack, none))
		ApexItems.clearCache()
		assertNull(ApexItems.overrideFor(stack, none))
	}

	@Test
	fun `plain stacks share one cache entry per item`() {
		ScreenItemOverrides.use(ScreenItemOverrides.load(FixturePacks.manager("items")))
		ScreenItemOverrides.screenChanged(Component.literal("Bazaar"))
		val exists = object : ItemModelCatalog {
			override fun hasItemModel_Apex(identifier: Identifier) = true
		}
		assertEquals(id("firmskyblock:bz_icon"), ApexItems.overrideFor(ItemStack(Items.PAPER), exists))
		assertEquals(id("firmskyblock:bz_icon"), ApexItems.overrideFor(ItemStack(Items.PAPER), none))
	}

	@Test
	fun `screen change switches active screen overrides`() {
		ScreenItemOverrides.use(ScreenItemOverrides.load(FixturePacks.manager("items")))
		val paper = Stacks.sb(Items.PAPER)
		ScreenItemOverrides.screenChanged(Component.literal("Bazaar"))
		assertEquals(id("firmskyblock:bz_icon"), ScreenItemOverrides.modelFor(paper))
		ScreenItemOverrides.screenChanged(null)
		assertNull(ScreenItemOverrides.modelFor(paper))

		ScreenItemOverrides.screenChanged(Component.literal("Bazaar"))
		ScreenItemOverrides.use(ScreenItemOverrides.load(FixturePacks.manager("items")))
		assertEquals(id("firmskyblock:bz_icon"), ScreenItemOverrides.modelFor(paper))
	}

	@Test
	fun `legacy pack type ids decode too`() {
		Fakes.vanillaItemModelTypes
		ItemModelTypes.register()
		for (type in listOf("apex:predicates/legacy", "firmament:predicates/legacy")) {
			val r = ItemModels.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"""{"type":"$type","fallback":{"type":"minecraft:model","model":"m:f"}}"""))
			assertTrue(r.isSuccess, type)
		}
		for (type in listOf("apex:head_model", "firmament:head_model")) {
			val r = ItemModels.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
				"""{"type":"$type","head":{"type":"minecraft:model","model":"m:h"},"regular":{"type":"minecraft:model","model":"m:r"}}"""))
			assertTrue(r.isSuccess, type)
		}
	}

	@Test
	fun `legacy head model key with the legacy prefix`() {
		assertEquals(id("m:h"), ApexGolden.legacyHeadModel("""{"firmament:head_model":"m:h"}"""))
	}

	private fun pack(name: String, files: Map<String, String>): java.nio.file.Path {
		val root = java.nio.file.Path.of("texengine-test-packs").toAbsolutePath().resolve(name)
		if (Files.exists(root)) root.toFile().deleteRecursively()
		Files.createDirectories(root)
		Files.writeString(root.resolve("pack.mcmeta"), """{"pack":{"pack_format":1,"description":""}}""")
		for ((path, content) in files) {
			val f = root.resolve(path)
			Files.createDirectories(f.parent)
			Files.writeString(f, content)
		}
		return root
	}

	@Test
	fun `legacy synthesis and the pack order rule`() {
		val low = pack("low", mapOf(
			"assets/firmskyblock/items/both_low.json" to "{}",
			"assets/firmskyblock/models/item/both_low.json" to "{}",
			"assets/firmskyblock/items/real_only_low.json" to "{}",
		))
		val high = pack("high", mapOf(
			"assets/firmskyblock/models/item/real_only_low.json" to "{}",
			"assets/cittofirmgenerated/models/item/sub/dir.json" to """{"overrides":[{"model":"m:a","predicate":{}}]}""",
			"assets/firmskyblock/models/item/broken.json" to "{",
			"assets/other/models/item/ignored.json" to "{}",
		))
		val manager = FixturePacks.managerAt(low, high)
		val loaded = mapOf<Identifier, ClientItem>()
		val result = LegacyItemModels.addTo(manager, loaded)

		assertNull(result[id("firmskyblock:both_low")])
		val winner = result[id("firmskyblock:real_only_low")]!!
		assertEquals(id("firmskyblock:item/real_only_low"), (winner.model() as CuboidItemModelWrapper.Unbaked).model())
		assertTrue(result[id("cittofirmgenerated:sub/dir")]!!.model() is dev.ashtonite365.apex.texengine.apex.PredicateModel.Unbaked)

		assertEquals(id("firmskyblock:item/broken"), (result[id("firmskyblock:broken")]!!.model() as CuboidItemModelWrapper.Unbaked).model())
		assertNull(result[id("other:ignored")])
		TexConfig.set("legacy-minecraft-path-support", false)
		assertTrue(LegacyItemModels.addTo(manager, loaded).isEmpty())
	}

	@Test
	fun `skull texture ids`() {
		assertEquals(id("firmskyblock:textures/placedskull/abc123.png"), ApexSkulls.textureIdForUrl("http://textures.minecraft.net/texture/abc123"))

		assertEquals(id("firmskyblock:textures/placedskull/abc123.png"), ApexSkulls.textureIdForUrl("https://textures.minecraft.net/texture/ABC123"))
		assertNull(ApexSkulls.textureIdForUrl("http://textures.minecraft.net/texture/abc123/x"))
		assertNull(ApexSkulls.textureIdForUrl("https://example.com/texture/abc123"))
		assertEquals(id("firmskyblock:textures/placedskull/abc123.png"), ApexGolden.skullTextureId("http://textures.minecraft.net/texture/abc123"))
	}
}
