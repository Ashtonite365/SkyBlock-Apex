package dev.ashtonite365.apex.features.items

import io.github.notenoughupdates.moulconfig.ChromaColour
import kotlin.math.abs
import kotlin.math.floor
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.HudRenderEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.customDataTag
import dev.ashtonite365.apex.util.mc.view
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.repoItemId
import dev.ashtonite365.apex.util.skyblock.SkyBlockItems
import dev.ashtonite365.apex.util.tr

object EtherwarpOverlay {
	@Config
	object TConfig : ManagedConfig("etherwarp-overlay", Category.MISC) {
		val enabled by toggle("etherwarp-overlay") { false }
		val sneakOnly by toggle("only-show-while-sneaking") { true }
		val fillCube by toggle("cube") { false }
		val okColour by colour("cube-colour") { ChromaColour.fromStaticRGB(172, 0, 255, 60) }
		val noSpaceColour by colour("cube-colour-fail") { ChromaColour.fromStaticRGB(255, 0, 172, 60) }

		val interactableColour by colour("cube-colour-tooclose") { ChromaColour.fromStaticRGB(0, 255, 0, 60) }
		val tooFarColour by colour("cube-colour-toofar") { ChromaColour.fromStaticRGB(255, 255, 0, 60) }
		val outline by toggle("wireframe") { true }

		val outlineWidth by integer("outline-width", 1, 10) { 2 }
		val showReason by toggle("failure-text") { true }
	}

	enum class Verdict(val reason: Component?, val tint: () -> ChromaColour) {
		LANDS(null, { TConfig.okColour }),
		INTERACTABLE_IN_WAY(tr("apex.etherwarp.fail.interactable", "Interactable in the way"), { TConfig.interactableColour }),
		OUT_OF_RANGE(tr("apex.etherwarp.fail.toofar", "Too far"), { TConfig.tooFarColour }),
		NO_ROOM(tr("apex.etherwarp.fail.nospace", "No space"), { TConfig.noSpaceColour }),
	}

	const val BASE_RANGE = 57.0

	fun range(tunedTransmission: Int): Double = BASE_RANGE + tunedTransmission.coerceIn(0, 4)

	fun evaluate(interactable: Boolean, hitDistance: Double?, range: Double, above1Free: Boolean, above2Free: Boolean): Verdict =
		when {
			interactable -> Verdict.INTERACTABLE_IN_WAY
			hitDistance == null || hitDistance > range -> Verdict.OUT_OF_RANGE
			!above1Free || !above2Free -> Verdict.NO_ROOM
			else -> Verdict.LANDS
		}

	fun etherwarpRange(repoItemId: String?, ethermerge: Boolean, tunedTransmission: Int, sneaking: Boolean): Double? =
		when (repoItemId) {
			SkyBlockItems.ETHERWARP_CONDUIT.skyblockId -> range(tunedTransmission)
			SkyBlockItems.ASPECT_OF_THE_END.skyblockId, SkyBlockItems.ASPECT_OF_THE_VOID.skyblockId ->
				if (ethermerge && sneaking) range(tunedTransmission) else null
			else -> null
		}

	fun raycast(start: Vec3, dir: Vec3, maxDistance: Double, solid: (BlockPos) -> Boolean): Pair<BlockPos, Double>? {
		val cell = intArrayOf(floor(start.x).toInt(), floor(start.y).toInt(), floor(start.z).toInt())
		val origin = doubleArrayOf(start.x, start.y, start.z)
		val direction = doubleArrayOf(dir.x, dir.y, dir.z)
		val step = IntArray(3) { if (direction[it] > 0) 1 else if (direction[it] < 0) -1 else 0 }

		val nextCrossing = DoubleArray(3) {
			when {
				step[it] > 0 -> (cell[it] + 1 - origin[it]) / direction[it]
				step[it] < 0 -> (cell[it] - origin[it]) / direction[it]
				else -> Double.POSITIVE_INFINITY
			}
		}
		val spacing = DoubleArray(3) { if (step[it] == 0) Double.POSITIVE_INFINITY else abs(1 / direction[it]) }
		fun here() = BlockPos(cell[0], cell[1], cell[2])
		if (solid(here())) return here() to 0.0
		while (true) {
			var axis = 0
			if (nextCrossing[1] < nextCrossing[axis]) axis = 1
			if (nextCrossing[2] < nextCrossing[axis]) axis = 2
			val entered = nextCrossing[axis]
			if (entered > maxDistance) return null
			cell[axis] += step[axis]
			nextCrossing[axis] += spacing[axis]
			if (solid(here())) return here() to entered
		}
	}

	class BlockGroup(val blocks: Set<Block>, val tags: Set<TagKey<Block>>) {
		operator fun contains(state: BlockState): Boolean = state.block in blocks || tags.any { state.`is`(it) }
	}

	val interactables by lazy {
		BlockGroup(
			setOf(
				Blocks.CHEST, Blocks.TRAPPED_CHEST, Blocks.ENDER_CHEST, Blocks.CRAFTING_TABLE, Blocks.FURNACE,
				Blocks.DISPENSER, Blocks.DROPPER, Blocks.HOPPER, Blocks.LEVER, Blocks.ENCHANTING_TABLE,
				Blocks.BREWING_STAND, Blocks.CAULDRON, Blocks.WATER_CAULDRON,
			),
			setOf(BlockTags.ANVIL, BlockTags.DOORS, BlockTags.FENCE_GATES, BlockTags.TRAPDOORS),
		)
	}

	val passable by lazy {
		BlockGroup(
			setOf(
				Blocks.LADDER, Blocks.REPEATER, Blocks.COMPARATOR, Blocks.COCOA, Blocks.SEA_PICKLE,
				Blocks.BIG_DRIPLEAF_STEM, Blocks.MOSS_CARPET, Blocks.PALE_MOSS_CARPET,

				Blocks.CREEPER_HEAD, Blocks.DRAGON_HEAD, Blocks.PIGLIN_HEAD, Blocks.PLAYER_HEAD, Blocks.ZOMBIE_HEAD,
				Blocks.SKELETON_SKULL, Blocks.WITHER_SKELETON_SKULL,
				Blocks.CREEPER_WALL_HEAD, Blocks.DRAGON_WALL_HEAD, Blocks.PIGLIN_WALL_HEAD, Blocks.PLAYER_WALL_HEAD,
				Blocks.ZOMBIE_WALL_HEAD, Blocks.SKELETON_WALL_SKULL, Blocks.WITHER_SKELETON_WALL_SKULL,
			),
			setOf(BlockTags.FLOWER_POTS, BlockTags.WOOL_CARPETS),
		)
	}

	val solidAnyway by lazy { BlockGroup(emptySet(), setOf(BlockTags.ALL_SIGNS, BlockTags.ALL_HANGING_SIGNS, BlockTags.BANNERS)) }

	fun canPassThrough(world: BlockGetter, pos: BlockPos): Boolean {
		val state = world.getBlockState(pos)
		if (state in solidAnyway) return false
		if (state in passable) return true
		return state.getCollisionShape(world, pos).isEmpty
	}

	fun wireColour(argb: Int): Int = argb or (0xFF shl 24)

	private fun heldRange(sneaking: Boolean): Double? {
		val stack = Client.mainHandStack.view()
		val data = stack.customDataTag
		return etherwarpRange(
			stack.repoItemId?.skyblockId,
			data.getIntOr("ethermerge", 0) == 1,
			data.getIntOr("tuned_transmission", 0),
			sneaking,
		)
	}

	private var shownResult: Verdict? = null

	@Subscribe
	fun drawTarget(event: WorldRenderLastEvent) {
		shownResult = null
		if (!TConfig.enabled) return
		val player = Client.player ?: return
		val sneaking = player.isShiftKeyDown
		if (TConfig.sneakOnly && !sneaking) return
		val range = heldRange(sneaking) ?: return
		val level = player.level()
		val eye = player.eyePosition

		val hit = raycast(eye, player.lookAngle.normalize(), range * 2) { !canPassThrough(level, it) }
		val crosshair = Client.game.hitResult as? BlockHitResult
		val opensSomething = crosshair != null && crosshair.type == HitResult.Type.BLOCK &&
			crosshair.location.distanceTo(eye) <= player.blockInteractionRange() &&
			level.getBlockState(crosshair.blockPos) in interactables
		val target = if (opensSomething) crosshair!!.blockPos else hit?.first
		val clearAbove = target != null && canPassThrough(level, target.above())
		val clearAbove2 = target != null && canPassThrough(level, target.above(2))
		val verdict = evaluate(opensSomething, hit?.second, range, clearAbove, clearAbove2)
		shownResult = verdict
		if (target == null) return
		val argb = verdict.tint().getEffectiveColourRGB()
		WorldDraw.drawInWorld(event) {
			if (TConfig.fillCube) block(target, argb)
			if (TConfig.outline) wireframeCube(target, TConfig.outlineWidth.toFloat(), wireColour(argb))
		}
	}

	@Subscribe
	fun drawReason(event: HudRenderEvent) {
		if (!TConfig.showReason) return
		val verdict = shownResult ?: return
		val text = verdict.reason ?: return
		val ctx = event.context
		val font = Client.font
		val x = ctx.guiWidth() / 2 - font.width(text) / 2
		val y = ctx.guiHeight() / 2 + 10
		ctx.text(font, text, x, y, wireColour(verdict.tint().getEffectiveColourRGB()), true)
	}
}
