// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.devices

import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.Blocks

object SharpshooterHighlight {
	@Config
	object TConfig : ManagedConfig("sharpshooter", Category.DUNGEONS) {
		val enabled by riskyToggle("enabled", RiskLevel.GREY_AREA)
		val completeAlert by toggle("complete-alert") { true }
	}

	val TARGETS: List<BlockPos> = buildList {
		for (y in intArrayOf(130, 128, 126)) for (x in intArrayOf(68, 66, 64)) add(BlockPos(x, y, 50))
	}

	private val COMPLETED = Regex("^(.{1,16}) completed a device! \\((\\d)/(\\d)\\)$")
	private const val LIT = 0x80FF55FF.toInt()
	private const val HIT = 0x8055FFFF.toInt()
	private const val NEAR_SQ = 40.0 * 40.0

	fun deviceCompleter(line: String): String? {
		val m = COMPLETED.find(line.trim()) ?: return null
		return m.groupValues[1]
	}

	private fun nearDevice(): Boolean {
		val self = Client.player ?: return false
		return self.distanceToSqr(66.0, 128.0, 50.0) < NEAR_SQ
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!TConfig.enabled || !SkyBlockSession.inSkyBlock || !nearDevice()) return
		val world = Client.level ?: return
		WorldDraw.drawInWorld(event) {
			TARGETS.forEach { target ->
				val state = world.getBlockState(target)
				when {
					state.`is`(Blocks.EMERALD_BLOCK) -> block(target, LIT)
					state.`is`(Blocks.DYED_TERRACOTTA.blue) -> block(target, HIT)
				}
			}
		}
	}

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!TConfig.enabled || !TConfig.completeAlert || !nearDevice()) return
		if (deviceCompleter(event.bareText) != Client.playerName) return
		Client.hud.setTitle(Component.literal("§aDevice complete"))
	}
}
