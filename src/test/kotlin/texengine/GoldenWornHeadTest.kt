// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import dev.ashtonite365.apex.test.testutil.AutoBootstrapExtension
import dev.ashtonite365.apex.util.mc.ItemModelFallback
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.ClientItem
import net.minecraft.client.renderer.item.CuboidItemModelWrapper
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.core.component.DataComponents
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

object WornHead {

	class Leaf(val id: Identifier?, private val drawn: Array<Identifier?>) : ItemModel {
		override fun update(
			state: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver, context: ItemDisplayContext,
			level: ClientLevel?, owner: ItemOwner?, seed: Int,
		) {
			drawn[0] = id
		}
	}

	fun leafId(unbaked: ItemModel.Unbaked): Identifier? = (unbaked as? CuboidItemModelWrapper.Unbaked)?.model()

	fun decode(definitionJson: String): ItemModel.Unbaked? =
		ClientItem.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(definitionJson)).result().orElse(null)?.model()

	fun run(model: ItemModel, stack: ItemStack, drawn: Array<Identifier?>, open: () -> Unit, close: () -> Boolean): GoldenHeadPick {
		val state = ItemStackRenderState()
		var headVariant: Boolean
		open()
		try {
			model.update(state, stack, Fakes.alloc(ItemModelResolver::class.java), ItemDisplayContext.HEAD, null, null, 0)
		} finally {
			headVariant = close()
		}
		return GoldenHeadPick(headVariant, drawn[0])
	}
}

data class GoldenWornHead(val model: Identifier?, val explicitHead: Boolean, val drawn: Identifier?)

@Tag("texengine-golden")
@ExtendWith(AutoBootstrapExtension::class)
class GoldenWornHeadTest {
	private val engine: GoldenEngine = ApexGolden

	@BeforeEach
	fun loadConfigDefaults() = TexConfig.reset()

	@AfterEach
	fun resetConfig() = TexConfig.reset()

	private fun id(s: String) = Identifier.parse(s)

	private val stormSkin = "http://textures.minecraft.net/texture/f83159c5bb7d80454d2d646b15774b7116aabcb3af5b6777a27573fc45f7854f"
	private val hypixelModel = id("hypixel_skyblock:item/dungeon/storm_helmet")

	private fun storm(withHypixelModel: Boolean): ItemStack {
		val stack = Stacks.head(Stacks.textureValue(stormSkin))
		val tagged = Stacks.sb(stack.item, id = "WISE_WITHER_HELMET")
		stack.set(DataComponents.CUSTOM_DATA, tagged.get(DataComponents.CUSTOM_DATA))
		if (withHypixelModel) stack.set(DataComponents.ITEM_MODEL, hypixelModel)
		return stack
	}

	private fun pack(name: String, files: Map<String, String>): Path {
		val root = Path.of("build", "texengine-test-packs", "worn-head", name).toAbsolutePath()
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

	private fun plain(model: String) = """{"model":{"type":"minecraft:model","model":"$model"}}"""
	private fun headModel(type: String) =
		"""{"model":{"type":"$type","head":{"type":"minecraft:model","model":"firmskyblock:item/storm_worn"},""" +
			""""regular":{"type":"minecraft:model","model":"firmskyblock:item/storm_icon"}}}"""

	private val hypixelPack by lazy {
		pack("hypixel", mapOf("assets/hypixel_skyblock/items/item/dungeon/storm_helmet.json" to plain("hypixel_skyblock:item/dungeon/storm_helmet")))
	}

	private fun wornHead(manager: ResourceManager, stack: ItemStack): GoldenWornHead {
		fun items(m: Identifier) = m.withPath { "items/$it.json" }
		fun legacy(m: Identifier) = m.withPath { "models/item/$it.json" }
		fun read(file: Identifier) = manager.getResource(file).map { r -> r.openAsReader().use { it.readText() } }.orElse(null)
		val exists = { m: Identifier -> read(items(m)) != null || (m.namespace == "firmskyblock" && read(legacy(m)) != null) }
		val override = engine.itemOverride(manager, stack, null, setOf())
		val model = if (override != null && exists(override)) override
		else ItemModelFallback.choose(stack.get(DataComponents.ITEM_MODEL), stack.item.components().get(DataComponents.ITEM_MODEL), exists)
		model ?: return GoldenWornHead(null, false, null)
		read(items(model))?.let { val pick = engine.headPick(it, stack); return GoldenWornHead(model, pick.explicitHead, pick.drawn) }
		read(legacy(model))?.let { json -> engine.legacyHeadModel(json)?.let { return GoldenWornHead(model, true, it) } }
		return GoldenWornHead(model, false, null)
	}

	@Test
	fun `G-HEAD-1 no pack - the worn head stays the skull with its own skin`() {
		val none = FixturePacks.managerAt(pack("empty", mapOf()))
		assertEquals(GoldenWornHead(id("minecraft:player_head"), false, null), wornHead(none, storm(withHypixelModel = false)))

		assertEquals(GoldenWornHead(id("minecraft:player_head"), false, null), wornHead(none, storm(withHypixelModel = true)))
	}

	@Test
	fun `G-HEAD-2 Hypixel server pack - item model is used for icons but the worn head stays the skull`() {
		val worn = wornHead(FixturePacks.managerAt(hypixelPack), storm(withHypixelModel = true))
		assertEquals(GoldenWornHead(hypixelModel, false, hypixelModel), worn)
	}

	@Test
	fun `G-HEAD-3 SkyBlock-id head model replaces the worn skull, also over Hypixel's item model`() {
		val sb = pack("sb-head", mapOf("assets/firmskyblock/items/wise_wither_helmet.json" to headModel("apex:head_model")))
		val expected = GoldenWornHead(id("firmskyblock:wise_wither_helmet"), true, id("firmskyblock:item/storm_worn"))
		assertEquals(expected, wornHead(FixturePacks.managerAt(sb), storm(withHypixelModel = false)))
		assertEquals(expected, wornHead(FixturePacks.managerAt(hypixelPack, sb), storm(withHypixelModel = true)))
		assertEquals(expected, wornHead(FixturePacks.managerAt(sb, hypixelPack), storm(withHypixelModel = true)))
	}

	@Test
	fun `G-HEAD-4 legacy models-item head key replaces the worn skull`() {
		val legacy = pack("sb-legacy", mapOf(
			"assets/firmskyblock/models/item/wise_wither_helmet.json" to """{"parent":"item/generated","apex:head_model":"firmskyblock:item/storm_worn"}"""))
		assertEquals(GoldenWornHead(id("firmskyblock:wise_wither_helmet"), true, id("firmskyblock:item/storm_worn")),
			wornHead(FixturePacks.managerAt(hypixelPack, legacy), storm(withHypixelModel = true)))
	}

	@Test
	fun `G-HEAD-5 inside a head selection a head model marks explicit-head and draws its head model, a plain model does not`() {
		val drawn = engine.headPick(headModel("apex:head_model"), storm(false))
		assertTrue(drawn.explicitHead)
		assertEquals(id("firmskyblock:item/storm_worn"), drawn.drawn)
		val plainPick = engine.headPick(plain("hypixel_skyblock:item/dungeon/storm_helmet"), storm(true))
		assertFalse(plainPick.explicitHead)
	}

	@Test
	fun `G-HEAD-6 placed-skull texture id for the worn skin`() {
		assertEquals(id("firmskyblock:textures/placedskull/f83159c5bb7d80454d2d646b15774b7116aabcb3af5b6777a27573fc45f7854f.png"),
			engine.skullTextureId(stormSkin))
	}
}
