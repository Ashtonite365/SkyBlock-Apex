// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.rift

import org.joml.Vector2i
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.EquipmentSlot
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.plus.drawPlusLines
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText

object PlusRiftLivingCave {
	@Config
	object TConfig : ManagedConfig("rift-living-cave", Category.RIFT) {
		val defenseBlocks by toggle("defense-blocks") { false }
		val livingMetal by toggle("living-metal") { false }
		val snakes by toggle("snakes") { false }
		val suitProgress by toggle("suit-progress") { false }
		val hud by position("hud", 140, 50) { Vector2i(5, 420) }
	}

	private const val METAL_MILLIS = 4000L
	private const val METAL_RANGE_SQ = 49.0

	private fun inRift() = SkyBlockSession.skyblockIsland == SkyBlockIsland.RIFT

	private val defenseBlocks = HashSet<BlockPos>()
	private val metalSeenAt = HashMap<BlockPos, Long>()
	val snakes = SnakeTracker()

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		defenseBlocks.clear()
		metalSeenAt.clear()
		snakes.snakes.clear()
	}

	private fun isDefenseBlock(name: String) = name.endsWith("stained_glass") || name == "diamond_block"

	private fun trackDefense(pos: BlockPos, from: String, to: String) {
		if (from == "air" && isDefenseBlock(to)) defenseBlocks.add(pos)
		if (to == "air") defenseBlocks.remove(pos)
	}

	private fun trackMetal(pos: BlockPos, from: String, to: String) {
		val me = Client.player
		if (to == "lapis_ore" && me != null && me.distanceToSqr(pos.x + .5, pos.y + .5, pos.z + .5) < METAL_RANGE_SQ)
			metalSeenAt[pos] = System.currentTimeMillis()
		if (from == "lapis_ore") metalSeenAt.remove(pos)
	}

	private fun trackSnakes(pos: BlockPos, from: String, to: String) {
		val point = SnakeTracker.P(pos.x, pos.y, pos.z)
		when {
			to == "lapis_block" -> snakes.added(point, System.currentTimeMillis())
			from == "lapis_block" -> snakes.removed(point)
		}
	}

	@Subscribe
	fun onBlock(event: PlusBlockChangeEvent) {
		if (!inRift()) return
		val from = event.oldName
		val to = event.newName
		if (TConfig.defenseBlocks) trackDefense(event.pos, from, to)
		if (TConfig.livingMetal) trackMetal(event.pos, from, to)
		if (TConfig.snakes) trackSnakes(event.pos, from, to)
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		val now = System.currentTimeMillis()
		metalSeenAt.values.removeIf { now - it > METAL_MILLIS }
		snakes.expire(now)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!inRift()) return
		WorldDraw.drawInWorld(event) {
			if (TConfig.defenseBlocks) for (pos in defenseBlocks) block(pos, 0x50FF0000)
			if (TConfig.livingMetal) for (pos in metalSeenAt.keys) block(pos, 0x6000AAFF)
			if (TConfig.snakes) for (snake in snakes.snakes) {
				for ((i, part) in snake.blocks.withIndex()) block(BlockPos(part.x, part.y, part.z), if (i == 0) 0x80FF0000.toInt() else 0x4000FF00)
			}
		}
	}

	@Subscribe
	fun onHud(event: HudRenderEvent) {
		if (!(TConfig.suitProgress && inRift())) return
		val me = Client.player ?: return
		val suit = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
			.map(me::getItemBySlot)
			.filter { !it.isEmpty && it.view().customDataTag.getInt("lm_evo").isPresent }
		if (suit.isEmpty()) return
		event.drawPlusLines(TConfig.hud) {
			add("§7Living Metal Suit Progress")
			for (piece in suit) {
				val evo = minOf(piece.view().customDataTag.getInt("lm_evo").get(), 100)
				add("§7${piece.hoverName.bareText}: ${RiftRules.progressBar(evo / 100.0)} §e$evo%")
			}
		}
	}
}
