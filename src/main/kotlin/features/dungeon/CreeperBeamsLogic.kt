/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex: Dungeons Creeper Beams puzzle, pure logic (rebuilt from scratch; replaces the old table port
 * CreeperBeams.kt). No Minecraft state is read here, so it is unit-tested on synthetic layouts.
 *
 * The room: Hypixel keeps a creeper still in the middle of the room, standing on a sea lantern. Beam ends sit on
 * the walls, floor and ceiling as sea lanterns; each has one partner, and the straight beam between the two passes
 * through the creeper. A completed beam turns both ends into prismarine. Hypixel can also start a pair with one end
 * already prismarine, so prismarine blocks in the room are beam ends too (the CreeperBeams notes the same).
 *
 * Pairing (see [pair]). The ends are every sea lantern AND prismarine block in the room except the creeper's own
 * column (see [beamEnds]); that set does not change as beams are completed (lantern -> prismarine), so the pairing
 * is stable for the whole puzzle.
 *  0. No vertical beams (fix-dungeon-doors-1018): the sea lantern the creeper stands on is part of the room, not a
 *     beam end, but it was paired with a ceiling block straight above it, which drew a straight-up line through the
 *     creeper in whatever colour slot it got. The creeper's column is dropped from the ends and any pair whose two
 *     ends share an x/z column is rejected ([isVertical]).
 *  1. The beams meet at a focal point on the creeper's vertical centre line; its exact height relative to the
 *     creeper's feet is not trusted (mods disagree: about 0.75 to 2.2 blocks), so [FOCUS_OFFSETS] are all tried.
 *  2. For a focal point f, an edge joins two ends a, b when they lie on opposite sides of f
 *     (dot(a - f, b - f) < 0) and the segment between their block centres passes within [PASS_RADIUS] of f.
 *  3. Matching (each end used at most once): most pairs first, then the smallest summed pass distance (closest
 *     pass wins). Exact bitmask DP up to [EXACT_LIMIT] ends; greedy by pass distance above that.
 *  4. The focal height with the best matching (same order: most pairs, then smallest sum) wins.
 *  Ends with no partner (odd or stray blocks) stay unpaired and are not drawn. Offline check against the public
 * lantern layout (not shipped): 4 or 5 random lit pairs are paired exactly right in about 94% of layouts,
 *  whatever the creeper's feet height; the misses are geometric near-ties.
 *
 * Display (see [Tracker]): a pair is drawn while at least one end is still a sea lantern; once both ends are
 * prismarine it is complete and removed. Colours stay with a pair.
 */
package dev.ashtonite365.apex.features.dungeon

import kotlin.math.sqrt
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.core.BlockPos

object CreeperBeamsLogic {

	const val PASS_RADIUS = 1.5

	val FOCUS_OFFSETS: List<Double> = (0..14).map { -0.5 + 0.25 * it }

	const val EXACT_LIMIT = 16

	data class Hitbox(val minX: Double, val minY: Double, val minZ: Double, val maxX: Double, val maxY: Double, val maxZ: Double) {
		val cx: Double get() = (minX + maxX) / 2
		val cz: Double get() = (minZ + maxZ) / 2

		val eyeY: Double get() = minY + (maxY - minY) * 0.85

		val base: BlockPos get() = BlockPos(Math.floor(cx).toInt(), Math.floor(minY - 0.5).toInt(), Math.floor(cz).toInt())
	}

	data class Pairing(val a: BlockPos, val b: BlockPos, val pass: Double) {
		val key: Set<BlockPos> get() = setOf(a, b)
	}

	data class Beam(val a: BlockPos, val b: BlockPos, val colour: Int) {
		val key: Set<BlockPos> get() = setOf(a, b)
	}

	fun passDistance(a: BlockPos, b: BlockPos, fx: Double, fy: Double, fz: Double): Double {
		val ax = a.x + 0.5; val ay = a.y + 0.5; val az = a.z + 0.5
		val dx = b.x + 0.5 - ax; val dy = b.y + 0.5 - ay; val dz = b.z + 0.5 - az
		val len2 = dx * dx + dy * dy + dz * dz
		val t = if (len2 == 0.0) 0.0 else (((fx - ax) * dx + (fy - ay) * dy + (fz - az) * dz) / len2).coerceIn(0.0, 1.0)
		val px = ax + dx * t - fx; val py = ay + dy * t - fy; val pz = az + dz * t - fz
		return sqrt(px * px + py * py + pz * pz)
	}

	fun isVertical(a: BlockPos, b: BlockPos): Boolean = a.x == b.x && a.z == b.z

	fun beamEnds(ends: Collection<BlockPos>, box: Hitbox): Set<BlockPos> {
		val base = box.base
		return ends.filterTo(HashSet()) { it.x != base.x || it.z != base.z }
	}

	fun oppositeSides(a: BlockPos, b: BlockPos, fx: Double, fy: Double, fz: Double): Boolean =
		(a.x + 0.5 - fx) * (b.x + 0.5 - fx) + (a.y + 0.5 - fy) * (b.y + 0.5 - fy) + (a.z + 0.5 - fz) * (b.z + 0.5 - fz) < 0

	private class Result(val pairs: List<Pair<Int, Int>>, val sum: Double)

	fun pair(ends: Collection<BlockPos>, box: Hitbox, radius: Double = PASS_RADIUS): List<Pairing> {

		val pts = ends.toSortedSet(compareBy<BlockPos>({ it.x }, { it.y }, { it.z })).toList()
		val n = pts.size
		if (n < 2) return emptyList()
		var best: Result? = null
		var bestCost: Array<DoubleArray>? = null
		for (offset in FOCUS_OFFSETS) {
			val fy = box.minY + offset
			val cost = Array(n) { DoubleArray(n) { Double.NaN } }
			for (i in 0 until n) for (j in i + 1 until n) {
				if (isVertical(pts[i], pts[j]) || !oppositeSides(pts[i], pts[j], box.cx, fy, box.cz)) continue
				val d = passDistance(pts[i], pts[j], box.cx, fy, box.cz)
				if (d <= radius) { cost[i][j] = d; cost[j][i] = d }
			}
			val r = if (n <= EXACT_LIMIT) exact(n, cost) else greedy(n, cost)
			val b = best
			if (b == null || r.pairs.size > b.pairs.size || (r.pairs.size == b.pairs.size && r.sum < b.sum - 1e-9)) {
				best = r; bestCost = cost
			}
		}
		val cost = bestCost ?: return emptyList()
		return best!!.pairs.map { (i, j) -> Pairing(pts[i], pts[j], cost[i][j]) }
	}

	private fun exact(n: Int, cost: Array<DoubleArray>): Result {
		val size = 1 shl n
		val count = IntArray(size) { -1 }
		val sum = DoubleArray(size)
		val pick = IntArray(size)
		fun solve(mask: Int) {
			if (count[mask] >= 0) return
			var i = 0
			while (i < n && (mask shr i) and 1 == 1) i++
			if (i >= n) { count[mask] = 0; sum[mask] = 0.0; pick[mask] = -1; return }
			val skip = mask or (1 shl i)
			solve(skip)
			var bestC = count[skip]; var bestS = sum[skip]; var bestJ = -1
			for (j in i + 1 until n) {
				if ((mask shr j) and 1 == 1 || cost[i][j].isNaN()) continue
				val rest = mask or (1 shl i) or (1 shl j)
				solve(rest)
				val c = count[rest] + 1; val s = sum[rest] + cost[i][j]
				if (c > bestC || (c == bestC && s < bestS - 1e-12)) { bestC = c; bestS = s; bestJ = j }
			}
			count[mask] = bestC; sum[mask] = bestS; pick[mask] = bestJ
		}
		solve(0)
		val out = mutableListOf<Pair<Int, Int>>()
		var mask = 0
		while (true) {
			var i = 0
			while (i < n && (mask shr i) and 1 == 1) i++
			if (i >= n) break
			val j = pick[mask]
			if (j >= 0) { out += i to j; mask = mask or (1 shl i) or (1 shl j) } else mask = mask or (1 shl i)
		}
		return Result(out, sum[0])
	}

	private fun greedy(n: Int, cost: Array<DoubleArray>): Result {
		val edges = (0 until n).flatMap { i -> (i + 1 until n).filter { !cost[i][it].isNaN() }.map { j -> Triple(i, j, cost[i][j]) } }
			.sortedBy { it.third }
		val used = BooleanArray(n)
		val out = edges.mapNotNull { (i, j) -> if (used[i] || used[j]) null else { used[i] = true; used[j] = true; i to j } }
		return Result(out, out.sumOf { (i, j) -> cost[i][j] })
	}

	class Tracker(private val radius: Double = PASS_RADIUS) {
		var beams: List<Beam> = emptyList()
			private set
		private var solvedFor: Pair<Set<BlockPos>, BlockPos>? = null
		private var solved: List<Beam> = emptyList()

		fun reset() { beams = emptyList(); solved = emptyList(); solvedFor = null }

		fun update(box: Hitbox, lanterns: Set<BlockPos>, prismarine: Set<BlockPos>): List<Beam> {
			val ends = beamEnds(lanterns + prismarine, box)
			val key = ends to box.base
			if (key != solvedFor) {

				val previous = solved.associate { it.key to it.colour }
				val fresh = pair(ends, box, radius)
				val used = mutableSetOf<Int>()
				val next = mutableListOf<Beam>()
				val late = mutableListOf<Pairing>()
				for (p in fresh) {
					val c = previous[p.key]
					if (c != null && used.add(c)) next += Beam(p.a, p.b, c) else late += p
				}
				for (p in late) {
					var c = 0
					while (c in used) c++
					used += c
					next += Beam(p.a, p.b, c)
				}
				solved = next.sortedBy { it.colour }
				solvedFor = key
			}

			beams = solved.filter { it.a in lanterns || it.b in lanterns }
			return beams
		}
	}

	const val NEW_TOGGLE_KEY = "creeper-beams-solver"
	const val OLD_TOGGLE_KEY = "creeper-beams"

	fun foldOldToggle(saved: JsonObject): JsonObject {
		if (OLD_TOGGLE_KEY !in saved) return saved
		val on = (saved[OLD_TOGGLE_KEY] as? JsonPrimitive)?.content == "true"
		val out = saved.toMutableMap()
		out.remove(OLD_TOGGLE_KEY)
		if (on && NEW_TOGGLE_KEY !in out) out[NEW_TOGGLE_KEY] = JsonPrimitive(true)
		return JsonObject(out)
	}

	fun isPuzzleCreeper(hasCustomName: Boolean, alive: Boolean): Boolean = !hasCustomName && alive

	const val MIN_ROOM_BLOCKS = 4

	object RoomGate {
		const val ORIGIN = -200
		const val STEP = 32
		const val SIZE = 31

		fun cellOf(v: Double): Int = Math.floorDiv(Math.floor(v).toInt() - ORIGIN, STEP)
		fun cellOf(x: Double, z: Double): Pair<Int, Int> = cellOf(x) to cellOf(z)

		fun inFootprint(cell: Pair<Int, Int>, x: Double, z: Double): Boolean {
			val minX = ORIGIN + STEP * cell.first
			val minZ = ORIGIN + STEP * cell.second
			return x >= minX && x < minX + SIZE && z >= minZ && z < minZ + SIZE
		}

		fun sameRoom(cx: Double, cz: Double, px: Double, pz: Double): Boolean {
			val cell = cellOf(cx, cz)
			return cell == cellOf(px, pz) && inFootprint(cell, px, pz)
		}

		fun shouldClear(room: Pair<Int, Int>?, px: Double, pz: Double): Boolean = room != null && !inFootprint(room, px, pz)
	}

	object Placement {
		const val ROOM_NAME = "Creeper Beams"

		const val TOUCH_EPS = 1e-4

		const val MAX_HOVER = 1.5

		const val CENTRE_RADIUS = 4.0

		const val FLOOR_STEP = 2
		const val FLOOR_MARGIN = 2
		const val FLOOR_MAX_DROP = 24
		const val FLOOR_MIN_COLUMNS = 8

		enum class Verdict { OK, FLOOR_UNKNOWN, BELOW_FLOOR, OTHER_ROOM, OTHER_CELL, OFF_CENTRE, BURIED, NOT_STANDING, HIDDEN }

		interface BlockView {

			fun collides(x: Int, y: Int, z: Int, box: Hitbox): Boolean

			fun collisionTop(x: Int, y: Int, z: Int): Double?

			fun blocksLine(x: Int, y: Int, z: Int, ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double): Boolean

			fun name(x: Int, y: Int, z: Int): String
		}

		class Cubes(
			private val solid: (Int, Int, Int) -> Boolean,
			private val names: (Int, Int, Int) -> String = { x, y, z -> if (solid(x, y, z)) "solid" else "air" },
		) : BlockView {
			override fun collides(x: Int, y: Int, z: Int, box: Hitbox) = solid(x, y, z) &&
				box.minX < x + 1 && box.maxX > x && box.minY < y + 1 && box.maxY > y && box.minZ < z + 1 && box.maxZ > z
			override fun collisionTop(x: Int, y: Int, z: Int) = if (solid(x, y, z)) y + 1.0 else null
			override fun blocksLine(x: Int, y: Int, z: Int, ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double) = solid(x, y, z)
			override fun name(x: Int, y: Int, z: Int) = names(x, y, z)
		}

		fun roomAllows(roomName: String?): Boolean = roomName == null || roomName.equals(ROOM_NAME, ignoreCase = true)

		fun belowFloor(feetY: Double, floorTop: Int): Boolean = feetY < floorTop - TOUCH_EPS

		fun centreOf(cell: Pair<Int, Int>): Pair<Double, Double> =
			(RoomGate.ORIGIN + RoomGate.STEP * cell.first + RoomGate.SIZE / 2.0) to (RoomGate.ORIGIN + RoomGate.STEP * cell.second + RoomGate.SIZE / 2.0)

		fun centreDistance(box: Hitbox): Double {
			val (mx, mz) = centreOf(RoomGate.cellOf(box.cx, box.cz))
			return Math.hypot(box.cx - mx, box.cz - mz)
		}

		private fun shrunk(b: Hitbox) = Hitbox(b.minX + TOUCH_EPS, b.minY + TOUCH_EPS, b.minZ + TOUCH_EPS, b.maxX - TOUCH_EPS, b.maxY - TOUCH_EPS, b.maxZ - TOUCH_EPS)

		fun buried(box: Hitbox, view: BlockView): BlockPos? {
			val inner = shrunk(box)
			for (x in Math.floor(inner.minX).toInt()..Math.floor(inner.maxX).toInt())
				for (y in Math.floor(inner.minY).toInt()..Math.floor(inner.maxY).toInt())
					for (z in Math.floor(inner.minZ).toInt()..Math.floor(inner.maxZ).toInt())
						if (view.collides(x, y, z, inner)) return BlockPos(x, y, z)
			return null
		}

		fun standing(box: Hitbox, view: BlockView): Boolean = support(box, view) != null

		fun support(box: Hitbox, view: BlockView): BlockPos? {
			val top = Math.floor(box.minY - TOUCH_EPS).toInt()
			val bottom = Math.floor(box.minY - MAX_HOVER - TOUCH_EPS).toInt()
			for (y in top downTo bottom)
				for (x in Math.floor(box.minX + TOUCH_EPS).toInt()..Math.floor(box.maxX - TOUCH_EPS).toInt())
					for (z in Math.floor(box.minZ + TOUCH_EPS).toInt()..Math.floor(box.maxZ - TOUCH_EPS).toInt()) {
						val t = view.collisionTop(x, y, z) ?: continue
						if (t <= box.minY + 0.02 && t >= box.minY - MAX_HOVER - 0.02) return BlockPos(x, y, z)
					}
			return null
		}

		fun lineClear(ax: Double, ay: Double, az: Double, bx: Double, by: Double, bz: Double, blocked: (Int, Int, Int) -> Boolean): Boolean {
			var x = Math.floor(ax).toInt(); var y = Math.floor(ay).toInt(); var z = Math.floor(az).toInt()
			val ex = Math.floor(bx).toInt(); val ey = Math.floor(by).toInt(); val ez = Math.floor(bz).toInt()
			val dx = bx - ax; val dy = by - ay; val dz = bz - az
			val sx = if (dx > 0) 1 else -1; val sy = if (dy > 0) 1 else -1; val sz = if (dz > 0) 1 else -1
			fun first(p: Double, cell: Int, d: Double, s: Int) = if (d == 0.0) Double.POSITIVE_INFINITY else ((if (s > 0) cell + 1 else cell) - p) / d
			var tx = first(ax, x, dx, sx); var ty = first(ay, y, dy, sy); var tz = first(az, z, dz, sz)
			val ddx = if (dx == 0.0) Double.POSITIVE_INFINITY else Math.abs(1 / dx)
			val ddy = if (dy == 0.0) Double.POSITIVE_INFINITY else Math.abs(1 / dy)
			val ddz = if (dz == 0.0) Double.POSITIVE_INFINITY else Math.abs(1 / dz)
			for (step in 0..4096) {
				if (blocked(x, y, z)) return false
				if (x == ex && y == ey && z == ez) return true
				if (tx <= ty && tx <= tz) { if (tx > 1) return true; x += sx; tx += ddx }
				else if (ty <= tz) { if (ty > 1) return true; y += sy; ty += ddy }
				else { if (tz > 1) return true; z += sz; tz += ddz }
			}
			return true
		}

		fun roomFloor(cell: Pair<Int, Int>, startY: Int, view: BlockView): Int? {
			val minX = RoomGate.ORIGIN + RoomGate.STEP * cell.first
			val minZ = RoomGate.ORIGIN + RoomGate.STEP * cell.second
			val counts = HashMap<Int, Int>()
			val last = RoomGate.SIZE - 1 - FLOOR_MARGIN
			for (dx in FLOOR_MARGIN..last step FLOOR_STEP) for (dz in FLOOR_MARGIN..last step FLOOR_STEP) {
				val x = minX + dx; val z = minZ + dz
				if (view.collisionTop(x, startY, z) != null) continue
				var y = startY - 1
				var top: Double? = null
				while (y >= startY - FLOOR_MAX_DROP) { top = view.collisionTop(x, y, z); if (top != null) break; y-- }
				if (top == null) continue
				val f = Math.floor(top + 1e-3).toInt()
				counts[f] = (counts[f] ?: 0) + 1
			}
			val best = counts.entries.maxWithOrNull(compareBy<Map.Entry<Int, Int>>({ it.value }, { it.key })) ?: return null
			return if (best.value >= FLOOR_MIN_COLUMNS) best.key else null
		}

		fun floorFor(roomFloor: Int?, standFloor: Int?): Int? = roomFloor ?: standFloor

		data class Eye(val x: Double, val y: Double, val z: Double)

		data class Judgement(val verdict: Verdict, val detail: String = "")

		fun judge(box: Hitbox, eye: Eye, roomName: String?, floorTop: Int?, view: BlockView): Judgement {

			if (floorTop == null) return Judgement(Verdict.FLOOR_UNKNOWN)
			if (belowFloor(box.minY, floorTop)) return Judgement(Verdict.BELOW_FLOOR, "feetY=%.2f < floorTop=%d".format(box.minY, floorTop))
			if (!roomAllows(roomName)) return Judgement(Verdict.OTHER_ROOM, "room=$roomName")
			if (!RoomGate.sameRoom(box.cx, box.cz, eye.x, eye.z)) return Judgement(Verdict.OTHER_CELL)
			val off = centreDistance(box)
			if (off > CENTRE_RADIUS) return Judgement(Verdict.OFF_CENTRE, "%.1f from centre".format(off))
			buried(box, view)?.let { return Judgement(Verdict.BURIED, "overlaps ${view.name(it.x, it.y, it.z)}@${it.x},${it.y},${it.z}") }
			if (!standing(box, view)) return Judgement(Verdict.NOT_STANDING)
			val clear = lineClear(eye.x, eye.y, eye.z, box.cx, box.eyeY, box.cz) { x, y, z -> view.blocksLine(x, y, z, eye.x, eye.y, eye.z, box.cx, box.eyeY, box.cz) }
			if (!clear) return Judgement(Verdict.HIDDEN, "no clear line from your eyes")
			return Judgement(Verdict.OK)
		}

		data class Report(val id: Int, val box: Hitbox, val judgement: Judgement, val feet: String, val below: String, val head: String) {
			val verdict: Verdict get() = judgement.verdict
			fun line(): String {
				val fy = Math.floor(box.minY).toInt(); val hy = Math.floor(box.eyeY).toInt()
				return "creeper id=$id pos=%.2f,%.2f,%.2f feet=%s@%d below=%s@%d head=%s@%d centreDist=%.1f -> %s%s".format(
					box.cx, box.minY, box.cz, feet, fy, below, fy - 1, head, hy, centreDistance(box), verdict,
					if (judgement.detail.isEmpty()) "" else " (${judgement.detail})")
			}
		}

		data class Selection(val chosen: Int?, val reports: List<Report>)

		fun select(candidates: List<Pair<Int, Hitbox>>, eye: Eye, roomName: String?, floorTop: Int?, view: BlockView): Selection {
			val reports = candidates.sortedBy { centreDistance(it.second) }.map { (id, box) ->
				val bx = Math.floor(box.cx).toInt(); val bz = Math.floor(box.cz).toInt(); val fy = Math.floor(box.minY).toInt()
				Report(id, box, judge(box, eye, roomName, floorTop, view), view.name(bx, fy, bz), view.name(bx, fy - 1, bz), view.name(bx, Math.floor(box.eyeY).toInt(), bz))
			}
			return Selection(reports.firstOrNull { it.verdict == Verdict.OK }?.id, reports)
		}

		private val FACES = arrayOf(intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 1, 0), intArrayOf(0, -1, 0), intArrayOf(0, 0, 1), intArrayOf(0, 0, -1))

		fun exposed(p: BlockPos, openAt: (Int, Int, Int) -> Boolean): Boolean =
			FACES.any { openAt(p.x + it[0], p.y + it[1], p.z + it[2]) }

		fun seenFrom(p: BlockPos, box: Hitbox, openAt: (Int, Int, Int) -> Boolean, view: BlockView): Boolean {
			val fx = box.cx; val fy = box.eyeY; val fz = box.cz
			val colX = Math.floor(box.cx).toInt(); val colZ = Math.floor(box.cz).toInt(); val feet = Math.floor(box.minY).toInt()
			for (f in FACES) {
				if (!openAt(p.x + f[0], p.y + f[1], p.z + f[2])) continue

				for ((u, v) in listOf(0.0 to 0.0, 0.3 to 0.3, -0.3 to 0.3, 0.3 to -0.3, -0.3 to -0.3)) {
					val tx = p.x + 0.5 + f[0] * 0.51 + (if (f[0] == 0) u else 0.0)
					val ty = p.y + 0.5 + f[1] * 0.51 + (if (f[1] == 0) (if (f[0] == 0) v else u) else 0.0)
					val tz = p.z + 0.5 + f[2] * 0.51 + (if (f[2] == 0) v else 0.0)
					val clear = lineClear(fx, fy, fz, tx, ty, tz) { x, y, z ->
						!(x == p.x && y == p.y && z == p.z) && !(x == colX && z == colZ && y < feet) &&
							view.blocksLine(x, y, z, fx, fy, fz, tx, ty, tz)
					}
					if (clear) return true
				}
			}
			return false
		}

		fun endsInRoom(blocks: Set<BlockPos>, floorTop: Int?, openAt: (Int, Int, Int) -> Boolean, box: Hitbox? = null, view: BlockView? = null): Set<BlockPos> =
			blocks.filterTo(HashSet()) {
				(floorTop == null || it.y >= floorTop - 1) && exposed(it, openAt) &&
					(box == null || view == null || seenFrom(it, box, openAt, view))
			}

		class FloorTracker {
			var cell: Pair<Int, Int>? = null
				private set
			var floorTop: Int? = null
				private set

			fun reset() { cell = null; floorTop = null }

			fun update(cell: Pair<Int, Int>, onGround: Boolean, feetY: Double): Int? {
				if (cell != this.cell) { this.cell = cell; floorTop = null }
				if (onGround) {
					val top = Math.floor(feetY + 1e-3).toInt()
					floorTop = floorTop?.let { minOf(it, top) } ?: top
				}
				return floorTop
			}
		}
	}
}
