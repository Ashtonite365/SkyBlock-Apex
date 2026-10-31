// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.repo

import io.github.moulberry.repo.IReloadable
import io.github.moulberry.repo.NEURepository
import java.util.Collections
import java.util.NavigableMap
import java.util.TreeMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.serializer
import kotlin.streams.asSequence
import net.minecraft.world.level.block.Block
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import dev.ashtonite365.apex.repo.ReforgeStore.kJson
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.ItemId
import dev.ashtonite365.apex.util.mc.ApexDataComponentTypes
import dev.ashtonite365.apex.util.mc.ItemView
import dev.ashtonite365.apex.util.mc.NeedsComponents
import dev.ashtonite365.apex.util.mc.displayNameAccordingToNbt
import dev.ashtonite365.apex.util.mc.loadItemFromNbt
import dev.ashtonite365.apex.util.mc.itemEditor
import dev.ashtonite365.apex.util.catalogItemId

class MiningRepoData
	: IReloadable {
	var customMiningAreas: Map<SkyBlockIsland, CustomMiningArea> = emptyMap(); private set
	var customMiningBlocks: List<CustomMiningBlock> = emptyList(); private set
	var toolsByBreakingPower: NavigableMap<BreakingPowerKey, SBItemStack> = Collections.emptyNavigableMap(); private set

	data class BreakingPowerKey(val breakingPower: Int, val itemId: ItemId? = null) {
		companion object Companion {
			private val byToolKind: Comparator<ItemId> = compareBy<ItemId> { id ->
				id.skyblockId.contains("PICK") || id.skyblockId.contains("BING")
			}.then(naturalOrder())

			val COMPARATOR: Comparator<BreakingPowerKey> = compareBy<BreakingPowerKey> { key -> key.breakingPower }.thenBy(nullsFirst(byToolKind)) { key -> key.itemId }
		}
	}

	override fun reload(repo: NEURepository): Unit {
		customMiningAreas = SafeRepoConstants.file(repo, "mining/custom_mining_areas.json", "custom mining areas")
			?.kJson(serializer<Map<SkyBlockIsland, CustomMiningArea>>()) ?: emptyMap()

		val blockFiles = SafeRepoConstants.tree(repo, "mining/blocks", "custom mining block hardness").asSequence()
		customMiningBlocks = blockFiles.filter { file -> file.path.endsWith(".json") }
			.map { file -> file.kJson(CustomMiningBlock.serializer()) }.toList()

		val tools = TreeMap<BreakingPowerKey, SBItemStack>(BreakingPowerKey.COMPARATOR)
		for (item in repo.items.itemsSafe.values) {
			val tool = SBItemStack(item.catalogItemId)
			val power = tool.breakingPower
			if (power > 0) tools[BreakingPowerKey(power, tool.catalogItemId)] = tool
		}
		toolsByBreakingPower = Collections.unmodifiableNavigableMap(tools)
	}

	fun getToolsThatCanBreak(breakingPower: Int): Collection<SBItemStack> =
		toolsByBreakingPower.tailMap(BreakingPowerKey(breakingPower), true).values

	@Serializable
	data class CustomMiningBlock(val breakingPower: Int = 0, val blockStrength: Int = 0, val name: String? = null,
		val baseDrop: ItemId? = null, val blocks189: List<Block189> = emptyList(),
	) {
		@Transient
		val dropItem: SBItemStack? = if (baseDrop == null) null else SBItemStack(baseDrop)

		@OptIn(ExpensiveItemCacheApi::class, NeedsComponents::class)
		private val dropDisplay: ItemStack? by lazy {
			val upgraded = dropItem?.asImmutableItemStack()?.currentCopy()
			upgraded?.let { tag(it) }
			upgraded
		}

		private fun tag(stack: ItemStack) {
			val edit = stack.itemEditor()
			edit.set(ApexDataComponentTypes.MINING_BLOCK_INFO, this)
			name?.let { label -> edit.displayNameAccordingToNbt = Component.literal(label) }
		}

		fun getDisplayItem(block: Block): ItemStack = dropDisplay ?: ItemStack(block).apply { tag(this) }
	}

	@Serializable
	data class Block189(val itemId: String, val damage: Short = 0, val onlyIn: List<SkyBlockIsland>? = null) {
		@Transient
		val block: Block? = modernBlock()

		fun isActiveIn(location: SkyBlockIsland) = onlyIn?.contains(location) ?: true

		val isCurrentlyActive: Boolean get() = isActiveIn(SkyBlockSession.skyblockIsland ?: SkyBlockIsland.NIL)

		companion object {

			fun blockOf(item: ItemView?): Block? = (item?.itemType?.value() as? BlockItem)?.block
		}

		@OptIn(ExpensiveItemCacheApi::class)
		private fun modernBlock(): Block? {
			val legacy = CompoundTag()
			legacy.putString("id", itemId)
			legacy.putShort("Damage", damage)
			val fixed = ItemCache.convert189ToModern(legacy) ?: return null
			return blockOf(loadItemFromNbt(fixed))
		}
	}

	@Serializable
	data class CustomMiningArea(val isSpecialMining: Boolean = true)
}
