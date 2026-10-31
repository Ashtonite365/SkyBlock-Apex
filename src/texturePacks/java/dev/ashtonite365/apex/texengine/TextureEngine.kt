/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: the texture engine adapter. Every hook (the mixins and the Sodium patch) calls the
 * engine only through these interfaces and [ApexTextureEngines]; Apex's own engine (texengine.apex) sits behind them.
 */
package dev.ashtonite365.apex.texengine

import com.mojang.authlib.GameProfile
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Function
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.block.dispatch.BlockStateModel
import net.minecraft.client.renderer.item.ClientItem
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.resources.model.EquipmentClientInfo
import net.minecraft.client.resources.model.ModelBaker
import net.minecraft.client.resources.model.ModelDiscovery
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.equipment.Equippable
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition

interface TextureEngine {

	val id: String
	val items: ItemModels
	val armor: ArmorModels
	val blocks: BlockModels
	val skulls: SkullTextures
	val screens: ScreenLayouts
	val texts: TextStyling
}

interface ItemModelCatalog {
	fun hasItemModel_Apex(identifier: Identifier): Boolean
}

interface ItemModels {

	fun overrideFor(stack: ItemStack, models: ItemModelCatalog): Identifier?

	fun extraItemDefinitions(manager: ResourceManager, loaded: Map<Identifier, ClientItem>): Map<Identifier, ClientItem>

	fun openWornHeadPass()
	fun closeWornHeadPass(): Boolean
}

interface ArmorModels {
	fun equippableFor(stack: ItemStack, slot: EquipmentSlot): Equippable?
	fun clientInfoFor(assetId: Identifier): EquipmentClientInfo?

	fun legacyLayerTexture(textureId: Identifier, leggings: Boolean): Identifier?
}

interface BlockModels {

	fun modelFor(state: BlockState, pos: BlockPos?): BlockStateModel?

	fun soundFor(state: BlockState, pos: BlockPos?): Identifier?
	fun enterFallback()
	fun exitFallback()
	fun addModelRoots(discovery: ModelDiscovery)
	fun bake(baker: ModelBaker, executor: Executor): CompletableFuture<Void?>
	fun loadBlockStates(
		original: CompletableFuture<Map<Identifier, List<Resource>>>,
		stateDefinitions: Function<Identifier, StateDefinition<Block, BlockState>?>,
		executor: Executor,
	): CompletableFuture<Map<Identifier, List<Resource>>>
}

interface SkullTextures {

	fun renderTypeFor(profile: GameProfile): RenderType?
}

data class LayoutMover(val x: Int, val y: Int, val width: Int? = null, val height: Int? = null)

interface LayoutBackground {
	val x: Int
	val y: Int
	val width: Int
	val height: Int

	fun draw(graphics: GuiGraphicsExtractor, screen: AbstractContainerScreen<*>)

	fun drawDirect(graphics: GuiGraphicsExtractor)
}

interface TitleMover {
	fun text(text: Component): Component
	fun x(font: Font, text: Component, x: Int): Int
	fun y(y: Int): Int
	fun color(text: Component, color: Int): Int
}

interface ActiveLayout {
	val background: LayoutBackground?
	val nameField: LayoutMover?
	fun signLine(index: Int): LayoutMover?

	val containerTitle: TitleMover
	val playerTitle: TitleMover
	val repairCostTitle: TitleMover
}

interface ScreenLayouts {

	val active: ActiveLayout?

	val noLayoutTitle: TitleMover
}

interface TextStyling {
	fun rewrite(text: Component): Component
}
