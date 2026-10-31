// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.mining

import dev.ashtonite365.apex.util.render.WaypointType
import kotlin.math.ceil
import kotlin.math.floor
import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ActionBarEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.gui.config.RiskLevel
import dev.ashtonite365.apex.util.Alerts
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.bareText

object MetalDetectorSolver {
	@Config
	object TConfig : ManagedConfig("metal-detector", Category.MINING) {
		val enabled by riskyToggle("enabled", RiskLevel.GREY_AREA)
		val showPossible by toggle("show-possible") { true }
	}

	val keeperOffsets = mapOf(
		"diamond" to Vec3i(33, 0, 3),
		"lapis" to Vec3i(-33, 0, -3),
		"emerald" to Vec3i(-3, 0, 33),
		"gold" to Vec3i(3, 0, -33),
	)

	val knownChestOffsets: Set<Vec3i> = intArrayOf(
		-38, -22, 26, 38, -22, -26, -40, -22, 18, -41, -20, 22, -5, -21, 16,
		40, -22, -30, -42, -20, -28, -43, -22, -40, 42, -19, -41, 43, -21, -16,
		-1, -22, -20, 6, -21, 28, 7, -21, 11, 7, -21, 22, -12, -21, -44,
		12, -22, 31, 12, -22, -22, 12, -21, 7, 12, -21, -43, -14, -21, 43,
		-14, -21, 22, -17, -21, 20, -20, -22, 0, 1, -21, 20, 19, -22, 29,
		20, -22, 0, 20, -21, -26, -23, -22, 40, 22, -21, -14, -24, -22, 12,
		23, -22, 26, 23, -22, -39, 24, -22, 27, 25, -22, 17, 29, -21, -44,
		-31, -21, -12, -31, -21, -40, 30, -21, -25, -32, -21, -40, -36, -20, 42,
		-37, -21, -14, -37, -21, -22,
	).toList().chunked(3) { (x, y, z) -> Vec3i(x, y, z) }.toCollection(LinkedHashSet())

	private const val READING = "TREASURE: "
	private const val BRUTE_MIN_Y = 65
	private const val BRUTE_MAX_Y = 75

	fun readDistance(text: String): Double? {
		if (READING !in text) return null
		return text.substringAfter(READING).substringBefore("m").filter { it.isDigit() || it == '.' }.toDoubleOrNull()
	}

	fun fits(eye: Vec3, spot: BlockPos, distance: Double): Boolean =
		Math.rint(eye.distanceTo(Vec3(spot.x.toDouble(), spot.y + 1.0, spot.z.toDouble())) * 10) / 10 == distance

	sealed interface Outcome {

		data object Unsteady : Outcome

		data object Dug : Outcome

		data object Lost : Outcome
		data class Narrowed(val left: Int) : Outcome

		data class Solved(val spot: BlockPos) : Outcome
	}

	class Narrowing {
		var spots: Set<BlockPos> = emptySet()
			private set
		private var lastEye: Vec3? = null
		private var lastDistance = -1.0
		private var told = false

		fun clear() {
			spots = emptySet()
			lastEye = null
			lastDistance = -1.0
			told = false
		}

		fun feed(eye: Vec3, distance: Double, centre: BlockPos?, solidAt: (BlockPos) -> Boolean): Outcome {
			val before = lastEye
			val steady = before != null && before.distanceTo(eye) <= 0.1 && kotlin.math.abs(lastDistance - distance) < 0.2
			lastEye = eye
			lastDistance = distance
			if (!steady) return Outcome.Unsteady
			if (spots.size == 1 && !fits(eye, spots.single(), distance)) {
				clear()
				return Outcome.Dug
			}
			val left = when {
				spots.isNotEmpty() -> spots.filterTo(LinkedHashSet()) { fits(eye, it, distance) }
				centre != null -> knownChestOffsets.mapTo(LinkedHashSet()) { centre.offset(it) }.filterTo(LinkedHashSet()) { fits(eye, it, distance) }
				else -> openSpots(eye, distance, solidAt)
			}
			if (left.isEmpty()) {
				clear()
				return Outcome.Lost
			}
			spots = left
			if (left.size == 1 && !told) {
				told = true
				return Outcome.Solved(left.single())
			}
			return Outcome.Narrowed(left.size)
		}

		private fun openSpots(eye: Vec3, distance: Double, solidAt: (BlockPos) -> Boolean): Set<BlockPos> {
			val found = LinkedHashSet<BlockPos>()
			val reach = floor(-distance).toInt()..ceil(distance).toInt()
			val baseX = floor(eye.x).toInt()
			val baseZ = floor(eye.z).toInt()
			for (dz in reach) for (dx in reach) for (y in BRUTE_MIN_Y..BRUTE_MAX_Y) {
				val spot = BlockPos(baseX + dx, y, baseZ + dz)
				if (fits(eye, spot, distance) && solidAt(spot) && !solidAt(spot.above())) found += spot
			}
			return found
		}
	}

	private val narrowing = Narrowing()
	var minesCenter: BlockPos? = null
	val possibleBlocks: Set<BlockPos> get() = narrowing.spots

	fun reset() = narrowing.clear()

	private fun on() = TConfig.enabled && SkyBlockSession.skyblockIsland == SkyBlockIsland.CRYSTAL_HOLLOWS

	@Subscribe
	fun onWorld(event: WorldReadyEvent) {
		reset()
		minesCenter = null
	}

	private fun findCentre() {
		if (minesCenter != null) return
		val player = Client.player ?: return
		val keeper = player.level().getEntitiesOfClass(ArmorStand::class.java, player.boundingBox.inflate(48.0)) {
			it.customName?.bareText?.contains("Keeper of ") == true
		}.firstOrNull() ?: return
		val gem = keeper.customName!!.bareText.substringAfter("Keeper of ").trim().lowercase()
		minesCenter = keeper.blockPosition().offset(keeperOffsets[gem] ?: return)
		Client.showChat(Component.literal("§e[SkyBlock Apex] Metal detector: located Mines of Divan center via Keeper."))
	}

	@Subscribe
	fun onActionBar(event: ActionBarEvent) {
		if (!on()) return
		val distance = readDistance(event.bareText) ?: return
		val player = Client.player ?: return
		findCentre()
		val eye = Vec3(player.x, player.y + (player.eyeHeight - 1.62), player.z)
		val world = player.level()
		when (val outcome = narrowing.feed(eye, distance, minesCenter) { !world.getBlockState(it).isAir }) {
			Outcome.Lost -> Client.showChat(Component.literal("§c[SkyBlock Apex] Metal detector: no solution, resetting."))
			is Outcome.Solved -> {
				Client.showChat(Component.literal("§e[SkyBlock Apex] Metal detector: found solution at ${outcome.spot.toShortString()}"))
				Alerts.show(Component.literal("§6Treasure found"))
			}
			else -> {}
		}
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!on()) return
		val spots = possibleBlocks
		if (spots.isEmpty()) return
		WorldDraw.drawInWorld(event, WaypointType.MINING_SOLVERS) {
			when {
				spots.size == 1 -> waypoint(spots.single(), Component.literal("§6Treasure"))
				TConfig.showPossible && spots.size < 40 -> spots.forEach { block(it, 0x50FFAA00) }
			}
		}
	}
}
