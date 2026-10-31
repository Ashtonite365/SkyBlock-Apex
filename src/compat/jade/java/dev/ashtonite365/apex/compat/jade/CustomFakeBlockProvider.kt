// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.compat.jade

import snownee.jade.api.Accessor
import snownee.jade.api.BlockAccessor
import snownee.jade.api.IWailaClientRegistration
import snownee.jade.api.callback.JadeRayTraceCallback
import net.minecraft.world.phys.HitResult
import dev.ashtonite365.apex.repo.MiningRepoData
import dev.ashtonite365.apex.util.mc.ApexDataComponentTypes

class CustomFakeBlockProvider(val registration: IWailaClientRegistration) : JadeRayTraceCallback {
	override fun onRayTrace(hitResult: HitResult, accessor: Accessor<*>, originalAccessor: Accessor<*>): Accessor<*>? =
		substitute(accessor) ?: accessor

	private fun substitute(seen: Accessor<*>): Accessor<*>? {
		val block = (seen as? BlockAccessor)?.takeIf { JadeIntegration.TConfig.blockDetection } ?: return null
		val known = JadeIntegration.customBlocks[block.block] ?: return null
		return registration.blockAccessor().from(block).serversideRep(known.getDisplayItem(block.block)).build()
	}

	companion object {

		@JvmStatic fun getCustomBlock(accessor: BlockAccessor): MiningRepoData.CustomMiningBlock? =
			accessor.serversideRep?.get(ApexDataComponentTypes.MINING_BLOCK_INFO)

		@JvmStatic fun hasCustomBlock(accessor: BlockAccessor): Boolean = getCustomBlock(accessor) != null
	}
}
