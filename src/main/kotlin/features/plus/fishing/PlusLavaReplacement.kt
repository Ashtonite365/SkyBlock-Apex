// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.fishing

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import net.minecraft.client.renderer.block.FluidModel
import net.minecraft.client.renderer.chunk.ChunkSectionLayer
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids

object PlusLavaReplacement {
	@Config
	object TConfig : ManagedConfig("lava-replacement", Category.FISHING) {
		val enabled by riskyToggle("enabled", RiskLevel.GREY_AREA)
		val everywhere by toggle("everywhere") { false }
		val inKuudra by toggle("in-kuudra") { false }
		val inDungeons by toggle("in-dungeons") { false }
	}

	@Volatile
	private var replacing = false
	private var solidWater: FluidModel? = null

	@JvmStatic
	fun onModelsBaked(models: Map<Fluid, FluidModel>) {
		val water = models[Fluids.WATER]
		solidWater = water?.let { FluidModel(ChunkSectionLayer.SOLID, it.stillMaterial(), it.flowingMaterial(), it.overlayMaterial(), it.tintSource()) }
	}

	@JvmStatic
	fun replacement(state: FluidState, original: FluidModel): FluidModel {
		if (!replacing) return original
		val type = state.type
		if (type != Fluids.LAVA && type != Fluids.FLOWING_LAVA) return original
		return solidWater ?: original
	}

	fun shouldReplace(enabled: Boolean, onSkyblock: Boolean, island: SkyBlockIsland?, everywhere: Boolean, kuudra: Boolean, dungeons: Boolean): Boolean =
		when {
			!enabled || !onSkyblock -> false
			everywhere -> true
			island == SkyBlockIsland.CRIMSON_ISLE -> true
			island == SkyBlockIsland.KUUDRA -> kuudra
			island == SkyBlockIsland.DUNGEON -> dungeons
			else -> false
		}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (event.tickCount % 20 != 0) return
		val wanted = shouldReplace(TConfig.enabled, SkyBlockSession.inSkyBlock, SkyBlockSession.skyblockIsland, TConfig.everywhere, TConfig.inKuudra, TConfig.inDungeons)
		if (wanted == replacing) return
		replacing = wanted
		Client.game.levelExtractor.allChanged()
	}
}
