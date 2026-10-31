// SPDX-License-Identifier: GPL-3.0-or-later
package dev.ashtonite365.apex.features.world

import dev.ashtonite365.apex.util.render.WaypointType
import net.minecraft.world.phys.Vec3
import io.github.moulberry.repo.data.Coordinate
import me.shedaniel.math.Color
import io.github.notenoughupdates.moulconfig.ChromaColour
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.AABB
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.SkyblockServerUpdateEvent
import dev.ashtonite365.apex.events.TickEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.repo.SafeRepoConstants
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.SkyBlockSession
import dev.ashtonite365.apex.util.SkyBlockIsland
import dev.ashtonite365.apex.util.blockPos
import dev.ashtonite365.apex.util.data.Config
import dev.ashtonite365.apex.util.data.ManagedConfig
import dev.ashtonite365.apex.util.data.ProfileSpecificDataHolder
import dev.ashtonite365.apex.util.render.WorldDraw.Companion.drawInWorld
import dev.ashtonite365.apex.util.bareText

object FairySouls {

	@Serializable
	data class Progress(
		@SerialName("foundSouls")
		val byIsland: MutableMap<SkyBlockIsland, MutableSet<Int>> = mutableMapOf()
	)

	@Config
	object CollectedStore : ProfileSpecificDataHolder<Progress>(serializer(), "found-fairysouls", { Progress() })

	@Config
	object TConfig : ManagedConfig("fairy-souls", Category.MISC) {

		val enabled by toggle("enabled") { true }
		val displaySouls: Boolean by toggle("show") { false }
		val beaconBeams by toggle("beacon-beams") { false }
		val beaconColour by colour("beacon-colour") { ChromaColour.fromStaticRGB(176, 0, 255, 90) }
		val beaconMaxDistance by integer("beacon-max-distance", 16, 512) { 160 }
		val lineToNearest by toggle("line-to-nearest") { false }
		val lineColour by colour("line-colour") { ChromaColour.fromStaticRGB(255, 120, 255, 255) }
		val resetSouls by button("reset") { forgetProgress() }
	}

	val identifier: String
		get() = TConfig.name

	const val REACH = 5
	private const val REACH_SQ = REACH * REACH

	private val COLLECT_MESSAGES = setOf("SOUL! You found a Fairy Soul!", "You have already found that Fairy Soul!")
	private val SOUL_BLOCK_COLOUR = Color.ofRGBA(176, 0, 255, 128).color

	var island: SkyBlockIsland? = null

	var islandSouls: List<Coordinate> = emptyList()

	var uncollected: List<Coordinate> = emptyList()

	private fun soulsOn(where: SkyBlockIsland): List<Coordinate>? = SafeRepoConstants.fairySoulLocations()[where.serverMode]

	private fun forgetProgress() {
		if (!CollectedStore.isLoaded) return
		CollectedStore.data.byIsland.clear()
		CollectedStore.markDirty()
		recountUncollected()
	}

	fun recountUncollected() {
		val where = island
		if (where == null || !CollectedStore.isLoaded) { uncollected = emptyList(); return }
		val found = CollectedStore.data.byIsland[where] ?: emptySet()
		uncollected = islandSouls.filterIndexed { i, _ -> i !in found }
	}

	fun reloadIslandSouls() {
		islandSouls = island?.let(::soulsOn) ?: emptyList()
		recountUncollected()
	}

	fun soulInReach(): Coordinate? {
		val player = Client.player ?: return null
		val feet = player.blockPosition()
		return islandSouls.minByOrNull { it.blockPos.distSqr(feet) }?.takeIf { it.blockPos.distSqr(feet) <= REACH_SQ }
	}

	private fun recordClickedSoul() {
		val where = island ?: return
		val soul = soulInReach() ?: return
		val index = islandSouls.indexOf(soul)
		if (index < 0 || !CollectedStore.isLoaded) return
		CollectedStore.data.byIsland.getOrPut(where) { mutableSetOf() }.add(index)
		CollectedStore.markDirty()
		recountUncollected()
	}

	private fun closestUncollected(): BlockPos? {
		debugSoul?.let { return it }
		val player = Client.player ?: return null
		var best: BlockPos? = null
		var bestSq = Double.MAX_VALUE
		for (soul in uncollected) {
			val pos = soul.blockPos
			val d = player.distanceToSqr(Vec3.atCenterOf(pos))
			if (d < bestSq) { bestSq = d; best = pos }
		}
		return best
	}

	private val debugSoul: BlockPos? = System.getenv("APEX_DEBUG_FAIRYSOUL")?.let { raw ->
		raw.split(",").mapNotNull { it.trim().toIntOrNull() }.takeIf { it.size == 3 }?.let { BlockPos(it[0], it[1], it[2]) }
	}

	private val lineEnabled: Boolean get() = debugSoul != null || (TConfig.enabled && TConfig.lineToNearest && island != null)

	@Subscribe
	fun steerLine(event: TickEvent) {
		if (!lineEnabled) { FairySoulPathfinder.clear(); return }
		FairySoulPathfinder.update(closestUncollected())
	}

	private fun halfStrength(argb: Int): Int = (argb ushr 1) and 0x7F7F7F7F

	@Subscribe
	fun paintBeamsAndLine(event: WorldRenderLastEvent) {
		val beams = TConfig.enabled && TConfig.beaconBeams && island != null
		if (!beams && !lineEnabled) return
		val player = Client.player ?: return
		drawInWorld(event, WaypointType.FAIRY_SOULS) {
			if (beams) {
				val colour = TConfig.beaconColour.getEffectiveColourRGB()
				val maxSq = TConfig.beaconMaxDistance.toDouble().let { it * it }
				for (soul in uncollected) {
					val pos = soul.blockPos
					if (player.distanceToSqr(Vec3.atCenterOf(pos)) > maxSq) continue
					box(AABB(pos.x + 0.3, pos.y.toDouble(), pos.z + 0.3, pos.x + 0.7, pos.y + 256.0, pos.z + 0.7), colour)
				}
			}
			if (lineEnabled) {
				val result = FairySoulPathfinder.result ?: return@drawInWorld
				if (result.points.size < 2) return@drawInWorld
				val colour = TConfig.lineColour.getEffectiveColourRGB()
				line(result.points, if (result.found) colour else halfStrength(colour), 3f)
			}
		}
	}

	@Subscribe
	fun paintSoulBlocks(event: WorldRenderLastEvent) {
		if (!TConfig.enabled || !TConfig.displaySouls || uncollected.isEmpty()) return
		drawInWorld(event, WaypointType.FAIRY_SOULS) {
			for (soul in uncollected) block(soul.blockPos, SOUL_BLOCK_COLOUR)
		}
	}

	@Subscribe
	fun readChat(event: ProcessChatEvent) {
		if (!TConfig.enabled) return
		if (event.text.bareText in COLLECT_MESSAGES) recordClickedSoul()
	}

	@Subscribe
	fun islandChanged(event: SkyblockServerUpdateEvent) {
		island = event.currentLocation?.skyblockIsland
		reloadIslandSouls()
	}
}
