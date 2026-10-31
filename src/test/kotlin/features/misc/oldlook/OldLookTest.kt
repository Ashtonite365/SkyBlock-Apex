package dev.ashtonite365.apex.test.features.misc.oldlook

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.UUID
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackLocationInfo
import net.minecraft.server.packs.PackResources
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.metadata.MetadataSectionType
import net.minecraft.server.packs.repository.PackSource
import net.minecraft.server.packs.resources.IoSupplier
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.item.component.DyedItemColor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import dev.ashtonite365.apex.features.misc.ServerResourcePackSkip
import dev.ashtonite365.apex.features.misc.oldlook.ItemsOnlyPackResources
import dev.ashtonite365.apex.features.misc.oldlook.OldLookBundle
import dev.ashtonite365.apex.features.misc.oldlook.OldLookItems
import dev.ashtonite365.apex.features.misc.oldlook.OldLookPolicy
import dev.ashtonite365.apex.features.misc.oldlook.OldLookPolicy.ModeAction
import dev.ashtonite365.apex.features.misc.oldlook.OldLookPolicy.PackMode
import dev.ashtonite365.apex.features.misc.oldlook.OldLookResolution
import dev.ashtonite365.apex.settings.MigrationMap
import dev.ashtonite365.apex.test.ApexTestBootstrap

class OldLookTest {
	companion object {
		val bundle: OldLookBundle by lazy { OldLookItems.loadBundle() }
	}

	private fun look(item: String) = (bundle.resolve(item, null, null) as OldLookResolution.Look)

	@Test
	fun bundleParsesWithAllItemsAndRules() {
		assertEquals(8851, bundle.items.size)
		assertEquals(11, bundle.rules.size)
		assertEquals(5505, bundle.items.values.count { it.isHead && it.skin != null })
		assertEquals(8, bundle.items.values.count { it.isHead && it.skin == null })
	}

	@Test
	fun everyItemAndPotionExistsIn263() {
		ApexTestBootstrap.bootstrapMinecraft()
		val ids = bundle.items.values.map { it.item } + bundle.rules.flatMap { listOfNotNull(it.item, it.elseItem) }
		val missing = ids.distinct().filter { BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(it)).isEmpty }
		assertEquals(emptyList<String>(), missing)
		val potions = bundle.items.values.mapNotNull { it.potion }.distinct()
			.filter { BuiltInRegistries.POTION.get(Identifier.withDefaultNamespace(it)).isEmpty }
		assertEquals(emptyList<String>(), potions)
	}

	@Test
	fun licencesShipNextToTheData() {
		val dir = java.io.File(OldLookItems::class.java.getResource("/assets/apex/old_look/old_look.json")!!.toURI()).parentFile
		val licences = dir.listFiles()!!.filter { it.name.startsWith("LICENSE-") }
		assertEquals(2, licences.size, licences.toString())
		assertTrue(licences.any { it.name == "LICENSE-DYNAMIC-RULES.txt" })
		for (f in licences) assertTrue(f.readText().contains("Copyright"), f.name)
		assertEquals(setOf("old_look.json") + licences.map { it.name }, dir.list()!!.toSet())
	}

	@Test
	fun lookupBySkyBlockId() {
		assertEquals("iron_sword", look("HYPERION").item)
		assertEquals("bamboo", look("BAMBOO").item)
		val book = look("ENCHANTED_BOOK")
		assertTrue(book.entry!!.glint)
		val pet = look("BAL;4")
		assertEquals("player_head", pet.item)
		assertEquals("704396fb-e7f0-3a33-bb57-d9bd34d6c7c9", pet.entry!!.uuid)
		assertEquals(943725, look("ABYSSAL_BOOTS").entry!!.color)
		assertEquals("water", look("BOTTLE_OF_JYRRE").entry!!.potion)

		assertEquals("paper", look("BINGO_CARD").item)
	}

	@Test
	fun uncoveredUnknownAndSkinlessKeepTheirLook() {
		assertEquals(OldLookResolution.Keep, bundle.resolve("INFINIPOT", null, null))
		assertEquals(OldLookResolution.Keep, bundle.resolve("MONSTER_EGGS", null, null))
		assertEquals(OldLookResolution.Keep, bundle.resolve("NOT_AN_ITEM_XYZ", null, null))
		assertEquals(OldLookResolution.Keep, bundle.resolve(null, null, null))
		assertEquals(OldLookResolution.Keep, bundle.resolve("BLACKSMITH_NPC", null, null))
	}

	@Test
	fun stateRulesComeBeforeEntries() {
		ApexTestBootstrap.bootstrapMinecraft()
		val attune = CompoundTag().apply { putInt("td_attune_mode", 1) }
		assertEquals(OldLookResolution.Look("golden_sword", null), bundle.resolve("HYPERION", attune, null))
		for ((mode, item) in listOf(0 to "stone_sword", 2 to "iron_sword", 3 to "diamond_sword"))
			assertEquals(item, (bundle.resolve("X", CompoundTag().apply { putInt("td_attune_mode", mode) }, null) as OldLookResolution.Look).item)

		val quiver = CompoundTag().apply { putString("quiver_arrow", "ARROW") }
		assertEquals("arrow", (bundle.resolve("RUNAANS_BOW", quiver, null) as OldLookResolution.Look).item)
		quiver.putInt("td_attune_mode", 3)
		assertEquals("diamond_sword", (bundle.resolve("RUNAANS_BOW", quiver, null) as OldLookResolution.Look).item)
	}

	@Test
	fun katanaCooldownFungiAndShovelRules() {
		ApexTestBootstrap.bootstrapMinecraft()
		assertEquals(OldLookResolution.Cooldown("golden_sword", "diamond_sword"), bundle.resolve("VORPAL_KATANA", null, null))
		assertEquals("red_mushroom", (bundle.resolve("FUNGI_CUTTER_2", CompoundTag().apply { putString("fungi_cutter_mode", "RED") }, null) as OldLookResolution.Look).item)
		assertEquals("brown_mushroom", (bundle.resolve("FUNGI_CUTTER", CompoundTag().apply { putString("fungi_cutter_mode", "BROWN") }, null) as OldLookResolution.Look).item)
		assertEquals("iron_hoe", look("FUNGI_CUTTER").item)
		assertEquals("golden_shovel", (bundle.resolve("CARNIVAL_SHOVEL", null, "item/event/carnival/carnival_shovel_gold") as OldLookResolution.Look).item)
		assertEquals("diamond_shovel", (bundle.resolve("CARNIVAL_SHOVEL", null, "carnival_shovel_diamond") as OldLookResolution.Look).item)
		assertEquals("iron_shovel", (bundle.resolve("CARNIVAL_SHOVEL", null, "something_else") as OldLookResolution.Look).item)

		assertEquals("iron_sword", (bundle.resolve("HYPERION", CompoundTag().apply { putString("fungi_cutter_mode", "RED") }, null) as OldLookResolution.Look).item)
	}

	@Test
	fun onlyWhileSkippingWithClassicLooksOn() {
		assertTrue(OldLookPolicy.active(skipPack = true, classicLooks = true))
		assertFalse(OldLookPolicy.active(skipPack = false, classicLooks = true))
		assertFalse(OldLookPolicy.active(skipPack = true, classicLooks = false))
		assertFalse(OldLookPolicy.active(skipPack = false, classicLooks = false))
	}

	@Test
	fun paperAndServerModelsAreCandidatesVanillaModelsAreNot() {
		assertTrue(OldLookPolicy.replaces("minecraft", "paper"))
		assertTrue(OldLookPolicy.replaces("hypixel_skyblock", "item/island_relevant/winter/weapons/ice_spray_wand"))
		assertFalse(OldLookPolicy.replaces("minecraft", "diamond_sword"))
		assertFalse(OldLookPolicy.replaces("minecraft", "player_head"))
		assertFalse(OldLookPolicy.replaces(null, null))
	}

	@Test
	fun settingsDefaultsAndPlacement() {
		ApexTestBootstrap.bootstrapMinecraft()
		val opts = ServerResourcePackSkip.TConfig.sortedOptions.associateBy { it.propertyName }
		assertEquals(true, opts["classic-item-looks"]!!.default())
		assertEquals(true, opts["keep-new-item-textures"]!!.default())
		assertEquals(false, opts["skip-pack"]!!.default())
		for (k in listOf("classic-item-looks", "keep-new-item-textures"))
			assertEquals(MigrationMap.bundled()["hypixel-resource-pack.skip-pack"]!!.group, MigrationMap.bundled()["hypixel-resource-pack.$k"]!!.group)
	}

	@Test
	fun packModeFollowsBothSettings() {
		assertEquals(PackMode(wanted = true, itemsOnly = false), OldLookPolicy.packMode(skipPack = false, keepNewerTextures = true))
		assertEquals(PackMode(wanted = true, itemsOnly = false), OldLookPolicy.packMode(skipPack = false, keepNewerTextures = false))
		assertEquals(PackMode(wanted = true, itemsOnly = true), OldLookPolicy.packMode(skipPack = true, keepNewerTextures = true))
		assertEquals(PackMode(wanted = false, itemsOnly = false), OldLookPolicy.packMode(skipPack = true, keepNewerTextures = false))
	}

	@Test
	fun modeChangesApplyLive() {
		val full = PackMode(true, false); val items = PackMode(true, true); val none = PackMode(false, false)
		assertEquals(ModeAction.NONE, OldLookPolicy.onModeChange(null, items, true, true))
		assertEquals(ModeAction.NONE, OldLookPolicy.onModeChange(items, items, true, true))

		assertEquals(ModeAction.RELOAD, OldLookPolicy.onModeChange(full, items, true, true))
		assertEquals(ModeAction.RELOAD, OldLookPolicy.onModeChange(items, full, true, true))

		assertEquals(ModeAction.POP_SERVER_PACK, OldLookPolicy.onModeChange(items, none, true, true))
		assertEquals(ModeAction.RELOAD, OldLookPolicy.onModeChange(full, none, true, false))
		assertEquals(ModeAction.RELOAD, OldLookPolicy.onModeChange(full, none, false, true))

		assertEquals(ModeAction.REAPPLY_SERVER_PACK, OldLookPolicy.onModeChange(none, items, true, false))
		assertEquals(ModeAction.REAPPLY_SERVER_PACK, OldLookPolicy.onModeChange(none, full, true, false))
		assertEquals(ModeAction.RELOAD, OldLookPolicy.onModeChange(none, full, false, false))
	}

	@Test
	fun itemsOnlyViewKeepsItemFilesOnly() {
		val hyp = "hypixel_skyblock"
		for (p in listOf("items/item/x.json", "models/item/x.json", "textures/item/x.png", "textures/item/x.png.mcmeta", "textures/block/y.png"))
			assertTrue(OldLookPolicy.itemsOnlyServes(hyp, p), p)
		for (p in listOf("sounds.json", "sounds/music/a.ogg", "font/default.json", "textures/gui/stats.png", "shaders/core/x.fsh", "equipment/x.json", "textures/entity/equipment/x.png", "lang/en_us.json"))
			assertFalse(OldLookPolicy.itemsOnlyServes(hyp, p), p)
		assertTrue(OldLookPolicy.itemsOnlyServes("minecraft", "atlases/items.json"))
		for (p in listOf("items/paper.json", "models/item/diamond_sword.json", "textures/item/player_head.png", "textures/gui/sprites/hud/heart/full.png", "font/default.json"))
			assertFalse(OldLookPolicy.itemsOnlyServes("minecraft", p), p)
		assertFalse(OldLookPolicy.itemsOnlyServes("other", "items/x.json"))
	}

	@Test
	fun itemsOnlyWrapperHidesTheRest() {
		val files = mapOf(
			Identifier.parse("hypixel_skyblock:models/item/a.json") to "a",
			Identifier.parse("hypixel_skyblock:textures/gui/b.png") to "b",
			Identifier.parse("minecraft:textures/item/c.png") to "c",
			Identifier.parse("other:items/d.json") to "d",
		)
		val fake = object : PackResources {
			override fun getResource(type: PackType, id: Identifier): IoSupplier<InputStream>? =
				files[id]?.let { v -> IoSupplier { ByteArrayInputStream(v.toByteArray()) } }
			override fun listResources(type: PackType, ns: String, path: String, out: PackResources.ResourceOutput) =
				files.keys.filter { it.namespace == ns && it.path.startsWith(path) }.forEach { out.accept(it, getResource(type, it)!!) }
			override fun getNamespaces(type: PackType) = files.keys.map { it.namespace }.toSet()
			override fun location() = PackLocationInfo("server/test", Component.literal("t"), PackSource.SERVER, java.util.Optional.empty())
			override fun getRootResource(vararg path: String): IoSupplier<InputStream>? = null
			override fun <T : Any> getMetadataSection(type: MetadataSectionType<T>): T? = null
			override fun close() {}
		}
		val view = ItemsOnlyPackResources(fake)
		val t = PackType.CLIENT_RESOURCES
		assertNotNull(view.getResource(t, Identifier.parse("hypixel_skyblock:models/item/a.json")))
		assertNull(view.getResource(t, Identifier.parse("hypixel_skyblock:textures/gui/b.png")))
		assertNull(view.getResource(t, Identifier.parse("minecraft:textures/item/c.png")))
		assertEquals(setOf("hypixel_skyblock", "minecraft"), view.getNamespaces(t))
		val listed = mutableListOf<Identifier>()
		view.listResources(t, "hypixel_skyblock", "", { id, _ -> listed.add(id) })
		view.listResources(t, "minecraft", "textures", { id, _ -> listed.add(id) })
		view.listResources(t, "other", "", { id, _ -> listed.add(id) })
		assertEquals(listOf(Identifier.parse("hypixel_skyblock:models/item/a.json")), listed)

		assertNotNull(view.getResource(PackType.SERVER_DATA, Identifier.parse("other:items/d.json")))
	}

	@Test
	fun headsAreBuiltFromTheSkinAndCached() {
		ApexTestBootstrap.bootstrapMinecraft()
		val entry = bundle.items["BAL;4"]!!
		val stack = OldLookItems.buildStack(entry.item, entry, null, null)!!
		assertEquals(Items.PLAYER_HEAD, stack.item)
		val profile = stack.get(DataComponents.PROFILE)!!.partialProfile()
		assertEquals(UUID.fromString("704396fb-e7f0-3a33-bb57-d9bd34d6c7c9"), profile.id())
		assertEquals(entry.skin, profile.properties()["textures"].single().value())
		assertTrue(OldLookItems.profileFor(entry) === OldLookItems.profileFor(entry))

		val a = OldLookItems.headProfile(entry.skin!!, null).partialProfile().id()
		assertEquals(a, OldLookItems.headProfile(entry.skin!!, null).partialProfile().id())
	}

	@Test
	fun colourPotionAndGlint() {
		ApexTestBootstrap.bootstrapMinecraft()
		val boots = bundle.items["ABYSSAL_BOOTS"]!!
		assertEquals(943725, OldLookItems.buildStack(boots.item, boots, null, null)!!.get(DataComponents.DYED_COLOR)!!.rgb())

		assertEquals(0x123456, OldLookItems.buildStack(boots.item, boots, null, DyedItemColor(0x123456))!!.get(DataComponents.DYED_COLOR)!!.rgb())
		val jyrre = bundle.items["BOTTLE_OF_JYRRE"]!!
		assertTrue(OldLookItems.buildStack(jyrre.item, jyrre, null, null)!!.get(DataComponents.POTION_CONTENTS)!!.`is`(Potions.WATER))
		val book = bundle.items["ENCHANTED_BOOK"]!!
		assertEquals(true, OldLookItems.buildStack(book.item, book, null, null)!!.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
		assertEquals(false, OldLookItems.buildStack(book.item, book, false, null)!!.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
		val sword = bundle.items["HYPERION"]!!
		assertNull(OldLookItems.buildStack(sword.item, sword, null, null)!!.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))
		assertEquals(Identifier.parse("minecraft:iron_sword"), OldLookItems.buildStack(sword.item, sword, null, null)!!.get(DataComponents.ITEM_MODEL))
		assertNull(OldLookItems.buildStack("not_an_item", null, null, null))
		assertNull(OldLookItems.buildStack("air", null, null, null))
	}

	@Test
	fun hooksAreMergedIntoTheirTargets() {
		val resolver = Class.forName("net.minecraft.client.renderer.item.ItemModelResolver").declaredMethods.map { it.name }
		for (h in listOf("classicLookLayers", "classicLookSwap", "classicLookSwapScale"))
			assertTrue(resolver.any { it.endsWith("\$$h") }, "$h not merged: $resolver")
		val pack = Class.forName("net.minecraft.server.packs.repository.Pack").declaredMethods.map { it.name }
		assertTrue(pack.any { it.endsWith("\$itemsOnly") }, "itemsOnly not merged: $pack")
	}
}
