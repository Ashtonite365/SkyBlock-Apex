// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.plus.dungeon

import dev.ashtonite365.apex.util.render.WaypointType
import dev.ashtonite365.apex.annotations.Subscribe
import dev.ashtonite365.apex.events.ProcessChatEvent
import dev.ashtonite365.apex.events.WorldReadyEvent
import dev.ashtonite365.apex.events.WorldRenderLastEvent
import dev.ashtonite365.apex.features.dungeon.PuzzleSolvers
import dev.ashtonite365.apex.features.dungeon.TerminalExtras
import dev.ashtonite365.apex.util.Client
import dev.ashtonite365.apex.util.render.WorldDraw
import dev.ashtonite365.apex.util.skyblock.DungeonUtil
import dev.ashtonite365.apex.util.bareText
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.phys.Vec3

object PlusTerminalInfo {
	data class Spot(val pos: BlockPos, val section: Int, val text: String)

	private infix fun String.at(xyz: Triple<Int, Int, Int>) = this to BlockPos(xyz.first, xyz.second, xyz.third)
	private fun t(x: Int, y: Int, z: Int) = Triple(x, y, z)

	private val bySection: List<Pair<Int, List<Pair<String, BlockPos>>>> = listOf(
		1 to listOf("Terminal" at t(111, 113, 73), "Terminal" at t(111, 119, 79), "Terminal" at t(89, 112, 92), "Terminal" at t(89, 122, 101), "Lever" at t(106, 124, 113), "Lever" at t(94, 124, 113), "Device" at t(110, 119, 93)),
		2 to listOf("Terminal" at t(68, 109, 121), "Terminal" at t(59, 120, 122), "Terminal" at t(47, 109, 121), "Terminal" at t(40, 124, 122), "Terminal" at t(39, 108, 143), "Lever" at t(23, 132, 138), "Lever" at t(27, 124, 127), "Device" at t(60, 131, 142)),
		3 to listOf("Terminal" at t(-3, 109, 112), "Terminal" at t(-3, 119, 93), "Terminal" at t(19, 123, 93), "Terminal" at t(-3, 109, 77), "Lever" at t(14, 122, 55), "Lever" at t(2, 122, 55), "Device" at t(-2, 119, 77)),
		4 to listOf("Terminal" at t(41, 109, 29), "Terminal" at t(44, 121, 29), "Terminal" at t(67, 109, 29), "Terminal" at t(72, 115, 48), "Lever" at t(84, 121, 34), "Lever" at t(86, 128, 46), "Device" at t(63, 126, 35)),
	)

	val spots: List<Spot> = bySection.flatMap { (section, list) -> list.map { (kind, pos) -> Spot(pos, section, kind) } }

	fun closest(section: Int, pos: Vec3, done: Set<Spot>): Spot? {
		var best: Spot? = null
		var bestD = 0.0
		for (s in spots) {
			if (s.section != section || s in done) continue
			val d = Vec3.atCenterOf(s.pos).distanceToSqr(pos)
			if (best == null || d.compareTo(bestD) < 0) { best = s; bestD = d }
		}
		return best
	}
}

object DungeonPlusMerges {
	private val doneSpots = mutableSetOf<PlusTerminalInfo.Spot>()
	private const val SPOT_FILL = 0x6055FF55
	private const val DEATHMITE_FILL = 0x50AA0000

	private fun waypointsActive() = TerminalExtras.TConfig.waypoints && TerminalExtras.tracker.active

	@Subscribe
	fun onChat(event: ProcessChatEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		if (!waypointsActive()) return
		val line = event.bareText.trim()
		val name = dev.ashtonite365.apex.features.dungeon.TerminalTracker.completedRegex.matchEntire(line)?.groupValues?.get(1) ?: return
		val who = Client.level?.players()?.firstOrNull { it.gameProfile.name == name } ?: return
		val spot = PlusTerminalInfo.closest(TerminalExtras.tracker.section, who.position(), doneSpots) ?: return
		doneSpots.add(spot)
	}

	@Subscribe
	fun onRender(event: WorldRenderLastEvent) {
		if (!DungeonUtil.isInDungeonIsland) return
		WorldDraw.drawInWorld(event) {
			if (waypointsActive() && PlusDungeonState.isFloor("F7", "M7")) looks(WaypointType.TERMINALS) {
				val section = TerminalExtras.tracker.section
				for (spot in PlusTerminalInfo.spots) {
					if (spot.section != section || spot in doneSpots) continue
					block(spot.pos, SPOT_FILL)
					text(Vec3.atCenterOf(spot.pos), Component.literal("§a${spot.text}"))
				}
			}
			if (PuzzleSolvers.TConfig.highlightDeathmites) {
				val self = Client.player ?: return@drawInWorld
				val mobs = Client.level?.getEntitiesOfClass(Silverfish::class.java, self.boundingBox.inflate(40.0)) { it.isAlive }
				mobs?.forEach { mob -> if (hasDeathmiteTag(mob)) box(mob.boundingBox, DEATHMITE_FILL) }
			}
		}
	}

	private fun hasDeathmiteTag(mob: Silverfish): Boolean {
		val area = mob.boundingBox.inflate(0.5, 2.5, 0.5)
		val tags = Client.level?.getEntitiesOfClass(net.minecraft.world.entity.decoration.ArmorStand::class.java, area) {
			it.customName?.bareText?.contains("Deathmite") == true
		} ?: return false
		return tags.isNotEmpty()
	}

	@Subscribe
	fun onWorld(event: WorldReadyEvent) = doneSpots.clear()
}
