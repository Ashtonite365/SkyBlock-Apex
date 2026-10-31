// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.kit.devices

import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ItemFrame
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import io.github.notenoughupdates.moulconfig.ChromaColour
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.util.kit.EntityInteractCancellableEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.UseBlockEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.kit.BlockChangeEvent
import dev.ashtonite365.apex.util.kit.BossPhase
import dev.ashtonite365.apex.util.kit.BossPhaseTracker
import dev.ashtonite365.apex.util.skyblock.DungeonUtil

object KitDevices {
	@Config
	object TConfig : ManagedConfig("f7-devices", Category.DUNGEONS) {
		val simonSays by riskyToggle("simon-says", RiskLevel.RISKY)
		val simonBlockWrong by riskyToggle("simon-says-block-wrong", RiskLevel.RISKY)
		val firstColour by colour("simon-first-colour") { ChromaColour.fromStaticRGB(85, 255, 85, 128) }
		val secondColour by colour("simon-second-colour") { ChromaColour.fromStaticRGB(255, 170, 0, 128) }
		val thirdColour by colour("simon-third-colour") { ChromaColour.fromStaticRGB(255, 85, 85, 128) }
		val arrowAlign by riskyToggle("arrow-align", RiskLevel.RISKY)
		val arrowBlockWrong by riskyToggle("arrow-align-block-wrong", RiskLevel.RISKY)
	}

	private val simon = SimonSaysState()
	private val arrows = ArrowAlignState()

	private var arrowHints: Map<Int, Int> = emptyMap()

	fun inP3() = DungeonUtil.isInDungeonIsland && BossPhaseTracker.current == BossPhase.GOLDOR

	private fun resetArrows() {
		arrows.reset()
		arrowHints = emptyMap()
	}

	private fun playerWithin(pos: BlockPos, radius: Double): Boolean {
		val p = Client.player ?: return false
		return p.blockPosition().distSqr(pos) < radius * radius
	}

	private val sneaking get() = Client.player?.isShiftKeyDown == true

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		simon.reset()
		simon.firstPhase = true
		resetArrows()
	}

	@Subscribe
	fun onTick(event: TickEvent) {
		if (!inP3()) return resetArrows()
		if (TConfig.simonSays) simon.onTick(litButtons())
		if (!TConfig.arrowAlign || !playerWithin(ArrowAlignLogic.CENTER, 14.0)) return resetArrows()
		arrows.update(frameRotations(), System.currentTimeMillis())
		arrowHints = arrows.clicks()
	}

	private fun litButtons(): Int {
		val world = Client.level ?: return 0
		return (120..123).sumOf { y -> (92..95).count { z -> world.getBlockState(BlockPos(110, y, z)).block == Blocks.STONE_BUTTON } }
	}

	private fun frameRotations(): List<Int> {
		val out = IntArray(25) { -1 }
		val frames = Client.level?.getEntitiesOfClass(ItemFrame::class.java, AABB(ArrowAlignLogic.CENTER).inflate(6.0)) { it.item.`is`(Items.ARROW) }
		frames?.forEach { frame ->
			val i = ArrowAlignLogic.indexOf(frame.blockPosition())
			if (i != null) out[i] = frame.rotation
		}
		return out.toMutableList()
	}

	@Subscribe
	fun onBlockChange(event: BlockChangeEvent) {
		if (!TConfig.simonSays || !inP3()) return
		val pos = event.pos
		val before = event.old
		val after = event.new
		val pressed = after.block == Blocks.STONE_BUTTON && after.getValue(BlockStateProperties.POWERED)
		if (pos == SimonSaysState.START_BUTTON) {
			if (pressed) simon.onStartPressed()
			return
		}
		if (before.block == Blocks.SEA_LANTERN && after.block == Blocks.OBSIDIAN && SimonSaysState.inGrid(pos, 111))
			simon.onLanternShown(pos.immutable())
		if (before.block == Blocks.STONE_BUTTON && pressed && SimonSaysState.inGrid(pos, 110))
			simon.onButtonPressed(pos.immutable())
	}

	@Subscribe
	fun onUse(event: UseBlockEvent) {
		if (!TConfig.simonSays || !inP3()) return
		val pos = event.hitResult.blockPos
		if (simon.order.isEmpty() || !SimonSaysState.inGrid(pos, 110)) return

		if (TConfig.simonBlockWrong && !sneaking && !simon.isCorrectClick(pos)) event.cancel()
	}

	@Subscribe
	fun onEntityInteract(event: EntityInteractCancellableEvent) {
		if (!TConfig.arrowAlign || !inP3()) return
		val frame = event.entity as? ItemFrame ?: return
		if (!frame.item.`is`(Items.ARROW)) return
		val i = ArrowAlignLogic.indexOf(frame.blockPosition()) ?: return

		if (TConfig.arrowBlockWrong && arrows.shouldBlock(i, sneaking)) {
			event.cancel()
			return
		}
		arrows.onClick(i, System.currentTimeMillis())
		arrowHints = arrows.clicks()
	}

	private fun simonColour(order: Int) = when (order) {
		0 -> TConfig.firstColour
		1 -> TConfig.secondColour
		else -> TConfig.thirdColour
	}

	private fun clicksColour(n: Int) = when {
		n < 3 -> "§a"
		n < 5 -> "§6"
		else -> "§c"
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!inP3()) return
		val buttons = if (TConfig.simonSays) simon.remaining() else emptyList()
		val hints = arrowHints
		if (buttons.isEmpty() && hints.isEmpty()) return
		WorldDraw.drawInWorld(event) {
			for ((order, b) in buttons.withIndex()) {
				val face = AABB(b.x + 0.875, b.y + 0.375, b.z + 0.3125, b.x + 1.0, b.y + 0.625, b.z + 0.6875)
				box(face, simonColour(order).getEffectiveColourRGB())
			}
			hints.forEach { (i, n) ->
				val at = ArrowAlignLogic.posOf(i)
				text(Vec3(at.x + 0.2, at.y + 0.6, at.z + 0.5), Component.literal(clicksColour(n) + n))
			}
		}
	}
}
