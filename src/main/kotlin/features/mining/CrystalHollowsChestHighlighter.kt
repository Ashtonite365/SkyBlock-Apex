// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.mining

import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Blocks
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.UseBlockEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw

object CrystalHollowsChestHighlighter {
	@Config
	object TConfig : ManagedConfig("crystal-hollows-chests", Category.MINING) {
		val enabled by riskyToggle("enabled", RiskLevel.RISKY)
		val colour by colour("colour") { ChromaColour.fromStaticRGB(255, 170, 0, 100) }
	}

	enum class Cell { CHEST, OPEN, OTHER }

	class SpawnWatch(private val radius: Int) {
		private var wasOpen: Set<BlockPos> = emptySet()
		private var scanned = false

		fun forget() {
			wasOpen = emptySet()
			scanned = false
		}

		fun scan(centre: BlockPos, cellAt: (BlockPos) -> Cell): List<BlockPos> {
			val open = HashSet<BlockPos>()
			val appeared = mutableListOf<BlockPos>()
			for (mutable in BlockPos.betweenClosed(centre.offset(-radius, -radius, -radius), centre.offset(radius, radius, radius))) {
				val pos = mutable.immutable()
				when (cellAt(pos)) {
					Cell.OPEN -> open += pos
					Cell.CHEST -> if (scanned && pos in wasOpen) appeared += pos
					Cell.OTHER -> {}
				}
			}
			wasOpen = open
			scanned = true
			return appeared
		}
	}

	private val watch = SpawnWatch(radius = 10)
	val marked = mutableSetOf<BlockPos>()

	fun isEnabled() = TConfig.enabled && SkyBlockSession.skyblockIsland == SkyBlockIsland.CRYSTAL_HOLLOWS

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		marked.clear()
		watch.forget()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!isEnabled()) return watch.forget()
		val world = Client.level ?: return
		marked.retainAll { world.getBlockState(it).block == Blocks.CHEST }
		if (event.tickCount % 2 != 0) return
		val centre = Client.player?.blockPosition() ?: return
		marked += watch.scan(centre) { pos ->
			when (world.getBlockState(pos).block) {
				Blocks.CHEST -> Cell.CHEST
				Blocks.AIR, Blocks.STONE -> Cell.OPEN
				else -> Cell.OTHER
			}
		}
	}

	@Subscribe
	fun onUseBlock(event: UseBlockEvent) {
		marked.remove(event.hitResult.blockPos)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!isEnabled() || marked.isEmpty()) return
		val rgb = TConfig.colour.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) { marked.forEach { block(it, rgb) } }
	}
}
