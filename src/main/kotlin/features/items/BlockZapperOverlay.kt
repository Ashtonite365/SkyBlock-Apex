package dev.ashtonite365.apex.features.items

import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.WorldKeyboardEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems

object BlockZapperOverlay {
	@Config
	object TConfig : ManagedConfig("block-zapper-overlay", Category.MISC) {
		val enabled by toggle("block-zapper-overlay") { false }
		val fill by colour("color") { ChromaColour.fromStaticRGB(160, 0, 0, 60) }
		val undoKey by keyBindingWithDefaultUnbound("undo-key")
	}

	val unzappable: List<Block> = listOf(
		Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES,
		Blocks.PUMPKIN, Blocks.PUMPKIN_STEM, Blocks.MELON, Blocks.MELON_STEM,
		Blocks.CACTUS, Blocks.SUGAR_CANE, Blocks.NETHER_WART, Blocks.TALL_GRASS, Blocks.SUNFLOWER,
		Blocks.FARMLAND, Blocks.BREWING_STAND, Blocks.SNOW, Blocks.RED_MUSHROOM, Blocks.BROWN_MUSHROOM,
	)
	private val banned = unzappable.toHashSet()

	private val preference = listOf(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP, Direction.DOWN)

	const val STEP_LIMIT = 164

	fun zapArea(start: BlockPos, blockAt: (BlockPos) -> Block): Set<BlockPos> {
		val type = blockAt(start)
		fun eligible(p: BlockPos) = blockAt(p) == type && blockAt(p.above()) !in banned
		if (type in banned || !eligible(start)) return emptySet()
		val result = LinkedHashSet<BlockPos>().apply { add(start) }
		fun open(from: BlockPos) = preference.asSequence().map(from::relative).filter { it !in result && eligible(it) }
		val pending = ArrayDeque<BlockPos>()
		var cur = start
		while (result.size < STEP_LIMIT) {
			val options = open(cur).take(2).toList()
			if (options.isEmpty()) {
				while (pending.isNotEmpty() && open(pending.last()).none()) pending.removeLast()
				cur = pending.lastOrNull() ?: break
				continue
			}
			if (options.size > 1 && pending.lastOrNull() != cur) pending.addLast(cur)
			cur = options.first()
			result.add(cur)
		}
		return result
	}

	private fun holdsZapper(): Boolean =
		Client.player?.mainHandItem?.view()?.repoItemId == SkyBlockItems.BLOCK_ZAPPER

	@Subscribe
	fun drawPreview(event: WorldRenderLastEvent) {
		if (!TConfig.enabled || !holdsZapper()) return
		val level = Client.player?.level() ?: return
		val aim = Client.game.hitResult as? BlockHitResult ?: return
		if (aim.type != HitResult.Type.BLOCK) return
		val cells = zapArea(aim.blockPos) { level.getBlockState(it).block }
		if (cells.isEmpty()) return
		val argb = TConfig.fill.getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) { for (p in cells) block(p, argb) }
	}

	@Subscribe
	fun undoKey(event: WorldKeyboardEvent) {
		if (!holdsZapper() || !event.matches(TConfig.undoKey)) return
		Client.runCommand("undozap")
	}
}
