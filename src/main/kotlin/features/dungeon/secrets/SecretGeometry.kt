// SPDX-License-Identifier: GPL-3.0-or-later

package dev.ashtonite365.apex.features.dungeon.secrets

typealias Pos3 = Triple<Int, Int, Int>

enum class TombBlock { AIR, CRACKED_BRICK, BRICK, OTHER_SOLID, UNLOADED }

object Tombs {
	const val MAX_VOLUME = 12
	const val MAX_HEIGHT = 3

	const val MIN_BRICK_SHARE = 0.6

	private val N = listOf(Pos3(1, 0, 0), Pos3(-1, 0, 0), Pos3(0, 1, 0), Pos3(0, -1, 0), Pos3(0, 0, 1), Pos3(0, 0, -1))
	private fun Pos3.plus(d: Pos3) = Pos3(first + d.first, second + d.second, third + d.third)

	data class Tomb(val air: Set<Pos3>, val cracked: Set<Pos3>) {
		val minX get() = air.minOf { it.first }; val maxX get() = air.maxOf { it.first }
		val minY get() = air.minOf { it.second }; val maxY get() = air.maxOf { it.second }
		val minZ get() = air.minOf { it.third }; val maxZ get() = air.maxOf { it.third }
		val centre get() = Triple((minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0)
	}

	fun pocket(start: Pos3, block: (Int, Int, Int) -> TombBlock): Tomb? {
		if (block(start.first, start.second, start.third) != TombBlock.AIR) return null
		val air = linkedSetOf(start)
		val queue = ArrayDeque(listOf(start))
		val shell = HashMap<Pos3, TombBlock>()
		while (queue.isNotEmpty()) {
			val p = queue.removeFirst()
			for (d in N) {
				val n = p.plus(d)
				if (n in air || n in shell) continue
				when (val b = block(n.first, n.second, n.third)) {
					TombBlock.UNLOADED -> return null
					TombBlock.AIR -> {
						air += n
						if (air.size > MAX_VOLUME) return null
						queue += n
					}
					else -> shell[n] = b
				}
			}
		}
		val h = air.maxOf { it.second } - air.minOf { it.second } + 1
		if (h > MAX_HEIGHT) return null
		val cracked = shell.filterValues { it == TombBlock.CRACKED_BRICK }.keys
		if (cracked.isEmpty()) return null
		val bricks = shell.values.count { it == TombBlock.CRACKED_BRICK || it == TombBlock.BRICK }
		if (bricks < MIN_BRICK_SHARE * shell.size) return null
		return Tomb(air, cracked)
	}

	fun find(cracked: Collection<Pos3>, block: (Int, Int, Int) -> TombBlock): List<Tomb> {
		val seen = HashSet<Pos3>()
		val out = ArrayList<Tomb>()
		for (c in cracked) for (d in N) {
			val a = c.plus(d)
			if (a in seen) continue
			val t = pocket(a, block) ?: continue
			seen += t.air
			out += t
		}
		return out
	}
}

object SuperboomRule {
	data class Params(val chestDepth: Int = 6, val chestRadius: Int = 8, val cryptRadius: Int = 6)

	data class Reason(val what: String, val at: Pos3, val distance: Double)

	fun normalAxis(wall: Collection<Pos3>): Int {
		val dx = wall.maxOf { it.first } - wall.minOf { it.first }
		val dz = wall.maxOf { it.third } - wall.minOf { it.third }
		return if (dx <= dz) 0 else 2
	}

	fun centre(wall: Collection<Pos3>) = Triple(
		(wall.minOf { it.first } + wall.maxOf { it.first } + 1) / 2.0,
		(wall.minOf { it.second } + wall.maxOf { it.second } + 1) / 2.0,
		(wall.minOf { it.third } + wall.maxOf { it.third } + 1) / 2.0)

	fun depthBehind(wall: Collection<Pos3>, t: Triple<Double, Double, Double>, viewer: Triple<Double, Double, Double>?): Double {
		val axis = normalAxis(wall)
		fun a(p: Triple<Double, Double, Double>) = if (axis == 0) p.first else p.third
		val lo = (if (axis == 0) wall.minOf { it.first } else wall.minOf { it.third }).toDouble()
		val hi = (if (axis == 0) wall.maxOf { it.first } else wall.maxOf { it.third }) + 1.0
		val v = a(t)
		val side = when {
			viewer == null -> 0
			a(viewer) < lo -> 1
			a(viewer) > hi -> -1
			else -> 0
		}
		return when (side) {
			1 -> v - hi
			-1 -> lo - v
			else -> maxOf(v - hi, lo - v)
		}
	}

	fun lateral(wall: Collection<Pos3>, t: Triple<Double, Double, Double>): Double {
		val axis = normalAxis(wall)
		fun gap(v: Double, lo: Int, hi: Int) = when { v < lo -> lo - v; v > hi + 1 -> v - hi - 1; else -> 0.0 }
		val along = if (axis == 0) gap(t.third, wall.minOf { it.third }, wall.maxOf { it.third }) else gap(t.first, wall.minOf { it.first }, wall.maxOf { it.first })
		val up = gap(t.second, wall.minOf { it.second }, wall.maxOf { it.second })
		return Math.hypot(along, up)
	}

	private fun c(p: Pos3) = Triple(p.first + 0.5, p.second + 0.5, p.third + 0.5)
	private fun dist(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>) =
		Math.sqrt((a.first - b.first).let { it * it } + (a.second - b.second).let { it * it } + (a.third - b.third).let { it * it })

	fun reason(
		wall: Collection<Pos3>, chests: Collection<Pos3>, crypts: Collection<Collection<Pos3>>,
		viewer: Triple<Double, Double, Double>?, p: Params = Params(),
	): Reason? {
		if (wall.isEmpty()) return null
		val mid = centre(wall)
		val hits = ArrayList<Reason>()
		for (ch in chests) {
			val t = c(ch)
			val depth = depthBehind(wall, t, viewer)
			if (depth <= 0.0) continue
			val d = dist(t, mid)
			if ((depth <= p.chestDepth && lateral(wall, t) <= p.chestDepth) || d <= p.chestRadius) hits += Reason("chest", ch, d)
		}
		for (crypt in crypts) for (b in crypt) {
			val t = c(b)
			val depth = depthBehind(wall, t, viewer)
			if (depth <= 0.0) continue
			if (depth <= p.cryptRadius && lateral(wall, t) <= p.cryptRadius) { hits += Reason("crypt", b, dist(t, mid)); break }
		}
		return hits.minByOrNull { it.distance }
	}
}
