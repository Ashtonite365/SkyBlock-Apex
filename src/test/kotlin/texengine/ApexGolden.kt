// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import dev.ashtonite365.apex.texengine.apex.ApexArmor
import dev.ashtonite365.apex.texengine.apex.ApexBlocks
import dev.ashtonite365.apex.texengine.apex.ApexItems
import dev.ashtonite365.apex.texengine.apex.ApexScreens
import dev.ashtonite365.apex.texengine.apex.ApexTextColors
import dev.ashtonite365.apex.texengine.apex.ApexTexts
import net.minecraft.client.gui.screens.inventory.SignEditScreen
import dev.ashtonite365.apex.texengine.apex.ApexSkulls
import dev.ashtonite365.apex.texengine.apex.HeadModel
import dev.ashtonite365.apex.texengine.apex.ItemModelIds
import dev.ashtonite365.apex.texengine.apex.ItemModelTypes
import dev.ashtonite365.apex.texengine.apex.LegacyItemModels
import dev.ashtonite365.apex.texengine.apex.NumberTest
import dev.ashtonite365.apex.texengine.apex.PredicateModel
import dev.ashtonite365.apex.texengine.apex.ScreenItemOverrides
import dev.ashtonite365.apex.texengine.apex.Settings
import dev.ashtonite365.apex.texengine.apex.TextureConfig
import dev.ashtonite365.apex.texengine.ItemModelCatalog
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.item.CuboidItemModelWrapper
import net.minecraft.client.renderer.item.ItemModel
import net.minecraft.client.renderer.item.ItemModelResolver
import net.minecraft.client.renderer.item.ItemModels
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.world.entity.ItemOwner
import net.minecraft.world.item.ItemDisplayContext
import dev.ashtonite365.apex.texengine.apex.Predicates
import dev.ashtonite365.apex.texengine.apex.RarityTest
import dev.ashtonite365.apex.texengine.apex.TextMatch
import io.github.moulberry.repo.data.Rarity
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

object ApexGolden : GoldenEngine {
	init {

		Settings.source = TextureConfig
	}

	override val id get() = "apex"

	override fun stringMatcher(json: String): GoldenStringMatcher {
		val m = TextMatch.read(JsonParser.parseString(json))
		return object : GoldenStringMatcher {
			override fun matches(string: String) = m.test(string)
			override fun matches(text: Component) = m.test(text)
		}
	}

	override fun numberMatcher(json: String): ((Number) -> Boolean)? {
		val m = NumberTest.readOrNull(JsonParser.parseString(json)) ?: return null
		return { m.test(it.toDouble()) }
	}

	override fun rarityMatcher(json: String): ((Rarity) -> Boolean)? {
		val m = RarityTest.readOrNull(JsonParser.parseString(json)) ?: return null
		return m::test
	}

	override fun predicate(json: String?): (ItemStack) -> Boolean {
		val p = Predicates.read(json?.let(Json::obj), "golden")
		return { p.test(it) }
	}

	override fun skyBlockModelId(repoItemId: String): Identifier = ItemModelIds.forSkyBlockId(repoItemId)

	private class Chosen(val modelId: Identifier?) : ItemModel {
		override fun update(
			state: ItemStackRenderState, stack: ItemStack, resolver: ItemModelResolver, context: ItemDisplayContext,
			level: ClientLevel?, owner: ItemOwner?, seed: Int,
		) = Unit
	}

	private val base: ItemModel.Unbaked =
		CuboidItemModelWrapper.Unbaked(Identifier.fromNamespaceAndPath("golden", "fallback"), java.util.Optional.empty(), listOf())

	private fun plainId(model: ItemModel.Unbaked) = (model as CuboidItemModelWrapper.Unbaked).model()

	override fun legacySelector(modelJson: String): (ItemStack) -> Identifier? {
		val unbaked = LegacyItemModels.fromModelJson(Json.obj(modelJson), base) as? PredicateModel.Unbaked ?: return { null }
		val baked = PredicateModel.Baked(Chosen(null), unbaked.cases.map {
			Chosen(plainId(it.model)) to dev.ashtonite365.apex.texengine.apex.Predicates.read(it.predicate.orElse(null))
		})
		return { stack -> (baked.select(stack, null) as Chosen).modelId }
	}

	override fun legacyHeadModel(modelJson: String): Identifier? {
		val unbaked = LegacyItemModels.fromModelJson(Json.obj(modelJson), base) as? HeadModel.Unbaked ?: return null
		return plainId(unbaked.worn)
	}

	override fun decodesPredicateModelType(json: String): Boolean {
		Fakes.vanillaItemModelTypes
		ItemModelTypes.register()
		val result = ItemModels.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
		result.error().ifPresent { println("[golden] predicate model decode error: ${it.message()}") }
		return result.isSuccess
	}

	override fun itemOverride(manager: ResourceManager, stack: ItemStack, screenTitle: Component?, exists: Set<Identifier>): Identifier? {
		ScreenItemOverrides.use(ScreenItemOverrides.load(manager))
		ScreenItemOverrides.screenChanged(screenTitle)
		ApexItems.clearCache()
		return ApexItems.overrideFor(stack, object : ItemModelCatalog {
			override fun hasItemModel_Apex(identifier: Identifier) = identifier in exists
		})
	}

	override fun skullTextureId(skinUrl: String): Identifier? {
		ApexSkulls.clearCache()
		return ApexSkulls.textureIdFor(Stacks.profile(Stacks.textureValue(skinUrl)).partialProfile())
	}

	override fun loadArmor(manager: ResourceManager) {
		ApexArmor.use(ApexArmor.load(manager))
		ApexArmor.useResources(manager)
	}

	override fun armor(stack: ItemStack, slot: EquipmentSlot): GoldenArmor {
		val equippable = ApexArmor.equippableFor(stack, slot) ?: return GoldenArmor(null)
		return GoldenArmor(equippable.assetId().map { it.identifier() }.orElse(null), equippable.slot())
	}

	override fun armorAssetLayers(assetId: Identifier): List<Triple<String, Identifier, Int?>>? {
		val info = ApexArmor.clientInfoFor(assetId) ?: return null
		return info.layers().entries.sortedBy { it.key.name }.flatMap { (type, layers) ->
			layers.map { Triple(type.name, it.textureId(), it.dyeable().flatMap { d -> d.colorWhenUndyed() }.orElse(null)) }
		}
	}
	override fun loadBlocks(manager: ResourceManager) {
		ApexBlocks.use(ApexBlocks.build(ApexBlocks.parse(manager), null))
	}

	private fun island(mode: String) {
		val before = ApexBlocks.onTableChanged
		ApexBlocks.onTableChanged = {}
		try {
			ApexBlocks.useIsland(mode)
		} finally {
			ApexBlocks.onTableChanged = before
		}
	}

	override fun blockReplacement(islandMode: String, state: BlockState, pos: BlockPos?, inFallback: Boolean): Identifier? {
		island(islandMode)
		if (inFallback) ApexBlocks.enterFallback()
		try {
			return ApexBlocks.entryFor(state, pos)?.replacement
		} finally {
			if (inFallback) ApexBlocks.exitFallback()
		}
	}

	override fun blockSelector(islandMode: String): (BlockState, BlockPos?) -> Identifier? {
		island(islandMode)
		return { state, pos -> ApexBlocks.entryFor(state, pos)?.replacement }
	}
	override fun loadScreenLayouts(manager: ResourceManager) {
		ApexScreens.use(ApexScreens.load(manager))
	}

	override fun activeLayout(title: Component, sign: Boolean): GoldenLayout? {
		val screen = if (sign) Fakes.screen(title, SignEditScreen::class.java) else Fakes.screen(title)
		ApexScreens.screenChanged(screen)
		val layout = ApexScreens.current ?: return null
		ApexScreens.screenChanged(null)
		return GoldenLayout(layout.slotMoves.map { Triple(it.index, it.x, it.y) }, layout.signLines?.size)
	}

	override fun loadTextColors(manager: ResourceManager) {
		ApexTextColors.use(ApexTextColors.load(manager))
	}

	override fun textColor(text: Component, original: Int): Int = ApexTextColors.colorFor(text, original)

	override fun loadTextReplacements(manager: ResourceManager) {
		ApexTexts.use(ApexTexts.load(manager))
	}

	override fun replaceText(text: Component): Component = ApexTexts.rewrite(text)

	override fun headPick(definitionJson: String, stack: ItemStack): GoldenHeadPick {
		Fakes.vanillaItemModelTypes
		ItemModelTypes.register()
		val unbaked = WornHead.decode(definitionJson) ?: return GoldenHeadPick(false, null)
		val drawn = arrayOfNulls<Identifier>(1)
		val model: ItemModel = if (unbaked is HeadModel.Unbaked)
			HeadModel.Baked(WornHead.Leaf(WornHead.leafId(unbaked.worn), drawn), WornHead.Leaf(WornHead.leafId(unbaked.held), drawn))
		else WornHead.Leaf(WornHead.leafId(unbaked), drawn)
		return WornHead.run(model, stack, drawn, ApexItems::openWornHeadPass, ApexItems::closeWornHeadPass)
	}
}
