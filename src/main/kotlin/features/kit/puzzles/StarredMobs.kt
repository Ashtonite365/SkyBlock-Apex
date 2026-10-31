/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: which name tags the starred-mob highlighter (PuzzleSolvers options) boxes, and the box outline edges.
 */
package dev.ashtonite365.apex.features.kit.puzzles

import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

object StarredMobs {

	private val DUNGEON_MOBS = listOf(
		"Lurker", "Dreadlord", "Souleater", "Zombie", "Skeleton", "Skeletor", "Sniper", "Super Archer", "Spider",
		"Fels", "Withermancer", "Lost Adventurer", "Angry Archaeologist", "Frozen Adventurer",
	)

	private val STARRED_TAG = Regex("^.*✯ .*\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?.?❤$")

	fun isDungeonMobName(name: String): Boolean {
		for (mob in DUNGEON_MOBS) if (name.contains(mob)) return true
		return false
	}

	enum class Kind { STARRED, LOST_ADVENTURER }

	const val LOST_ADVENTURER = "Lost Adventurer"

	fun isLostAdventurer(name: String): Boolean = name.contains(LOST_ADVENTURER, ignoreCase = true)

	fun highlightKind(name: String, starred: Boolean, strict: Boolean, lostAdventurer: Boolean): Kind? {
		if (lostAdventurer && isLostAdventurer(name)) return Kind.LOST_ADVENTURER
		if (starred && isStarred(name, strict)) return Kind.STARRED
		return null
	}

	fun isStarred(name: String, strict: Boolean): Boolean {
		if (!strict) return name.indexOf('✯') >= 0
		return isDungeonMobName(name) && STARRED_TAG.matches(name)
	}

	fun boxEdges(b: AABB): List<Pair<Vec3, Vec3>> {
		val lo = doubleArrayOf(b.minX, b.minY, b.minZ)
		val hi = doubleArrayOf(b.maxX, b.maxY, b.maxZ)
		val edges = ArrayList<Pair<Vec3, Vec3>>(12)

		for (axis in 0..2) {
			val (u, v) = when (axis) { 0 -> 1 to 2; 1 -> 0 to 2; else -> 0 to 1 }
			for (a in 0..1) for (c in 0..1) {
				val from = DoubleArray(3); val to = DoubleArray(3)
				from[u] = if (a == 0) lo[u] else hi[u]; to[u] = from[u]
				from[v] = if (c == 0) lo[v] else hi[v]; to[v] = from[v]
				from[axis] = lo[axis]; to[axis] = hi[axis]
				edges.add(Vec3(from[0], from[1], from[2]) to Vec3(to[0], to[1], to[2]))
			}
		}
		return edges
	}
}
