// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.test.texengine

import io.github.moulberry.repo.data.Rarity
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

interface GoldenStringMatcher {
	fun matches(string: String): Boolean
	fun matches(text: Component): Boolean
}

data class GoldenLayout(val slotMoves: List<Triple<Int, Int, Int>>, val signLineCount: Int?)

data class GoldenArmor(val assetId: Identifier?, val slot: EquipmentSlot? = null)

data class GoldenHeadPick(val explicitHead: Boolean, val drawn: Identifier?)

interface GoldenEngine {

	val id: String

	fun stringMatcher(json: String): GoldenStringMatcher
	fun numberMatcher(json: String): ((Number) -> Boolean)?
	fun rarityMatcher(json: String): ((Rarity) -> Boolean)?

	fun predicate(json: String?): (ItemStack) -> Boolean

	fun skyBlockModelId(repoItemId: String): Identifier

	fun legacySelect(modelJson: String, stack: ItemStack): Identifier? = legacySelector(modelJson)(stack)

	fun legacySelector(modelJson: String): (ItemStack) -> Identifier?

	fun legacyHeadModel(modelJson: String): Identifier?

	fun decodesPredicateModelType(json: String): Boolean

	fun itemOverride(manager: ResourceManager, stack: ItemStack, screenTitle: Component?, exists: Set<Identifier>): Identifier?

	fun loadArmor(manager: ResourceManager)
	fun armor(stack: ItemStack, slot: EquipmentSlot): GoldenArmor

	fun armorAssetLayers(assetId: Identifier): List<Triple<String, Identifier, Int?>>?

	fun loadBlocks(manager: ResourceManager)

	fun blockReplacement(islandMode: String, state: BlockState, pos: BlockPos?, inFallback: Boolean = false): Identifier?

	fun blockSelector(islandMode: String): (BlockState, BlockPos?) -> Identifier?

	fun loadScreenLayouts(manager: ResourceManager)

	fun activeLayout(title: Component, sign: Boolean = false): GoldenLayout?

	fun loadTextColors(manager: ResourceManager)
	fun textColor(text: Component, original: Int): Int

	fun loadTextReplacements(manager: ResourceManager)
	fun replaceText(text: Component): Component

	fun skullTextureId(skinUrl: String): Identifier?

	fun headPick(definitionJson: String, stack: ItemStack): GoldenHeadPick
}
