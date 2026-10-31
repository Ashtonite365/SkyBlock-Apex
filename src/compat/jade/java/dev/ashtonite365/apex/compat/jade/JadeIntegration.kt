// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jade

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.SkyblockServerUpdateEvent
import dev.ashtonite365.apex.repo.MiningRepoData
import dev.ashtonite365.apex.repo.RepoManager
import dev.ashtonite365.apex.util.Faults
import net.minecraft.world.level.block.Block
import dev.ashtonite365.apex.events.ReloadRegistrationEvent
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig

object JadeIntegration {
	@Config
	object TConfig : ManagedConfig("jade-integration", Category.GENERAL) {
		val miningProgress by toggle("progress") { true }
		val blockDetection by toggle("blocks") { true }
	}

	var customBlocks: Map<Block, MiningRepoData.CustomMiningBlock> = emptyMap()

	fun refreshBlockInfo() { customBlocks = if (isOnMiningIsland()) indexActive() else emptyMap() }

	private fun indexActive(): Map<Block, MiningRepoData.CustomMiningBlock> {
		val claimants = LinkedHashMap<Block, MutableList<MiningRepoData.CustomMiningBlock>>()
		for (custom in RepoManager.miningData.customMiningBlocks) for (variant in custom.blocks189) {
			if (!variant.isCurrentlyActive) continue
			val vanilla = variant.block ?: continue
			claimants.getOrPut(vanilla) { mutableListOf() }.add(custom)
		}
		val index = LinkedHashMap<Block, MiningRepoData.CustomMiningBlock>()
		for ((vanilla, owners) in claimants) {
			val owner = owners.singleOrNull()
			if (owner != null) index[vanilla] = owner
			else Faults.reportFault("Block info tooltip: $vanilla is claimed by more than one custom mining block; ignoring it.")
		}
		return index
	}

	@Subscribe
	fun onRepoReload(event: ReloadRegistrationEvent) { event.repo.registerReloadListener { refreshBlockInfo() } }

	@Subscribe
	fun onServerChange(event: SkyblockServerUpdateEvent) { refreshBlockInfo() }
}
