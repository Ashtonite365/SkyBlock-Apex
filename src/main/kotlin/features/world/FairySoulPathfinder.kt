/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * SkyBlock Apex (26.3 port): the shared block-level walking pathfinder.
 * Written from scratch. Walking A* (2-high air over standable ground, 1-block step-ups, stairs, unlimited drops,
 * ladders/vines climbable, upward limit 6 growing by 2 per failed search) on a block snapshot taken on the client
 * thread (optionally a few columns per tick, so a big snapshot never stalls a frame); the search itself runs off the
 * render thread with a node cap. Used by the Fairy Souls "line to nearest soul" ([FairySoulPathfinder]) and the
 * dungeon Nearest Locked Door key path (KeyHighlight), each with its own [BlockPathPlanner] instance.
 * Display only: it never moves or clicks for you.
 */
package dev.ashtonite365.apex.features.world

import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import dev.ashtonite365.apex.Apex
import dev.ashtonite365.apex.util.Client

object SoulAStar {
	const val AIR: Byte = 0
	const val SOLID: Byte = 1
	const val CLIMB: Byte = 2
	const val BLOCKED: Byte = 3
	const val STAIR: Byte = 4

	const val UP_LIMIT_START = 6
	const val UP_LIMIT_STEP = 2
	const val MAX_UP_RETRIES = 8

	fun interface Grid { fun cell(x: Int, y: Int, z: Int): Byte }

	data class P(val x: Int, val y: Int, val z: Int)

	class Stats { var expanded = 0; var attempts = 0 }

	const val THIN = 0.4

	const val FLAT = 0.1875

	fun classifyShape(minX: Double, maxX: Double, minY: Double, maxY: Double, minZ: Double, maxZ: Double): Byte = when {
		maxY > 1.0 -> SOLID
		maxY <= FLAT -> AIR
		maxX - minX <= THIN && maxZ - minZ <= THIN -> AIR

		maxY - minY <= THIN && (maxX - minX <= THIN || maxZ - minZ <= THIN) -> AIR
		else -> SOLID
	}

	fun passable(g: Grid, x: Int, y: Int, z: Int) = g.cell(x, y, z).let { it == AIR || it == CLIMB }

	fun standable(g: Grid, x: Int, y: Int, z: Int): Boolean {
		if (!passable(g, x, y, z) || !passable(g, x, y + 1, z)) return false
		val below = g.cell(x, y - 1, z)
		return below == SOLID || below == STAIR || below == CLIMB || g.cell(x, y, z) == CLIMB
	}

	private val DIRS = arrayOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)

	fun findWithRetries(
		g: Grid, start: P, goal: P, nodeCap: Int = 20_000, maxDist: Int = 128, goalRadius: Double = 1.5, stats: Stats? = null,
		goalUp: Int = 2, sight: Boolean = false,
	): List<P>? {
		var up = UP_LIMIT_START
		for (attempt in 0..MAX_UP_RETRIES) {
			find(g, start, goal, nodeCap, maxDist, up, goalRadius, stats, goalUp, sight)?.let { return it }
			if (up >= maxDist) break
			up += UP_LIMIT_STEP
		}
		return null
	}

	fun find(
		g: Grid, start: P, goal: P, nodeCap: Int = 20_000, maxDist: Int = 128, maxUp: Int = Int.MAX_VALUE, goalRadius: Double = 1.5,
		stats: Stats? = null, goalUp: Int = 2, sight: Boolean = false,
	): List<P>? {
		if (!standable(g, start.x, start.y, start.z)) return null
		stats?.let { it.attempts++ }
		fun h(p: P): Double {
			val dx = (p.x - goal.x).toDouble(); val dy = (p.y - goal.y).toDouble(); val dz = (p.z - goal.z).toDouble()
			return sqrt(dx * dx + dy * dy + dz * dz)
		}
		fun isGoal(p: P): Boolean {
			val dx = (p.x - goal.x).toDouble(); val dz = (p.z - goal.z).toDouble()
			return sqrt(dx * dx + dz * dz) <= goalRadius && abs(p.y - goal.y) <= goalUp && (!sight || clearSight(g, p, goal))
		}
		val gs = HashMap<P, Double>()
		val parent = HashMap<P, P>()
		val closed = HashSet<P>()
		val open = PriorityQueue<Pair<Double, P>>(compareBy { it.first })
		gs[start] = 0.0
		open.add(h(start) to start)
		val next = ArrayList<P>(16)
		while (open.isNotEmpty()) {
			val cur = open.poll().second
			if (!closed.add(cur)) continue
			stats?.let { it.expanded++ }
			if (isGoal(cur)) {
				val out = ArrayList<P>()
				var k: P? = cur
				while (k != null) { out.add(k); k = parent[k] }
				out.reverse(); return out
			}
			if (closed.size > nodeCap) return null
			next.clear()
			neighbours(g, cur, next)
			val gc = gs[cur]!!
			for (n in next) {
				if (n in closed) continue
				if (maxOf(abs(n.x - start.x), abs(n.y - start.y), abs(n.z - start.z)) > maxDist) continue
				if (n.y - start.y > maxUp) continue
				val dx = n.x - cur.x; val dy = n.y - cur.y; val dz = n.z - cur.z
				val ng = gc + sqrt((dx * dx + dy * dy + dz * dz).toDouble())
				if (ng < (gs[n] ?: Double.MAX_VALUE)) {
					gs[n] = ng; parent[n] = cur
					open.add((ng + h(n)) to n)
				}
			}
		}
		return null
	}

	fun clearSight(g: Grid, from: P, goal: P): Boolean {
		val ax = from.x + 0.5; val ay = from.y + 1.6; val az = from.z + 0.5
		val bx = goal.x + 0.5; val by = goal.y + 0.5; val bz = goal.z + 0.5
		val len = sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az))
		val steps = maxOf(1, (len * 8).toInt())
		for (i in 0..steps) {
			val t = i.toDouble() / steps
			val x = kotlin.math.floor(ax + (bx - ax) * t).toInt(); val y = kotlin.math.floor(ay + (by - ay) * t).toInt(); val z = kotlin.math.floor(az + (bz - az) * t).toInt()
			if (x == goal.x && y == goal.y && z == goal.z) continue
			if (!passable(g, x, y, z)) return false
		}
		return true
	}

	internal fun neighbours(g: Grid, c: P, out: MutableList<P>) {

		if (g.cell(c.x, c.y, c.z) == CLIMB || g.cell(c.x, c.y + 1, c.z) == CLIMB) {
			if (standable(g, c.x, c.y + 1, c.z) && passable(g, c.x, c.y + 2, c.z)) out.add(P(c.x, c.y + 1, c.z))
		}
		if (g.cell(c.x, c.y - 1, c.z) == CLIMB && standable(g, c.x, c.y - 1, c.z)) out.add(P(c.x, c.y - 1, c.z))
		for ((dx, dz) in DIRS) {
			val x = c.x + dx; val z = c.z + dz
			val diagonal = dx != 0 && dz != 0

			fun sidesClear(y0: Int) = !diagonal || (passable(g, c.x + dx, y0, c.z) && passable(g, c.x + dx, y0 + 1, c.z) &&
				passable(g, c.x, y0, c.z + dz) && passable(g, c.x, y0 + 1, c.z + dz))
			val front = g.cell(x, c.y, z)
			when {
				standable(g, x, c.y, z) -> if (sidesClear(c.y)) out.add(P(x, c.y, z))

				front == STAIR && standable(g, x, c.y + 1, z) -> if (sidesClear(c.y + 1)) out.add(P(x, c.y + 1, z))

				front == SOLID && passable(g, c.x, c.y + 2, c.z) && standable(g, x, c.y + 1, z) ->
					if (sidesClear(c.y + 1)) out.add(P(x, c.y + 1, z))

				!sidesClear(c.y) -> {}
				passable(g, x, c.y, z) && passable(g, x, c.y + 1, z) -> {
					var y = c.y - 1
					while (passable(g, x, y, z)) {
						if (standable(g, x, y, z)) { out.add(P(x, y, z)); break }
						y--
					}
				}
			}
		}
	}

	fun simplify(path: List<P>): List<P> {
		if (path.size < 3) return path
		val out = arrayListOf(path[0])
		for (i in 1 until path.size - 1) {
			val a = out.last(); val b = path[i]; val c = path[i + 1]
			val d1 = Triple(b.x - a.x, b.y - a.y, b.z - a.z); val d2 = Triple(c.x - b.x, c.y - b.y, c.z - b.z)
			val collinear = d1.second == 0 && d2.second == 0 && d1.first.toLong() * d2.third == d1.third.toLong() * d2.first &&
				d1.first.toLong() * d2.first + d1.third.toLong() * d2.third > 0
			if (!collinear) out.add(b)
		}
		out.add(path.last()); return out
	}
}

class BlockSnapshot(val minX: Int, val minY: Int, val minZ: Int, val sx: Int, val sy: Int, val sz: Int, val cells: ByteArray) : SoulAStar.Grid {
	override fun cell(x: Int, y: Int, z: Int): Byte =
		if (x in minX until minX + sx && y in minY until minY + sy && z in minZ until minZ + sz)
			cells[((x - minX) * sy + (y - minY)) * sz + (z - minZ)]
		else SoulAStar.BLOCKED
}

class BlockPathPlanner(
	val nodeCap: Int,
	val maxDist: Int,
	val replanDistSq: Double,
	val minTicksBetween: Int,
	val cellsPerTick: Int,
	val straightFallback: Boolean,
	val keepLastFound: Boolean,
	val goalRadius: Double = 1.5,

	val goalUp: Int = 2,
	val goalSight: Boolean = false,

	val drawHeight: Double = 0.5,

	val margin: Int = 8,
	val maxSpan: Int = 193,
	val maxVolume: Long = 2_000_000,
) {

	interface Source {
		fun columnLoaded(x: Int, z: Int): Boolean
		fun cell(x: Int, y: Int, z: Int): Byte
	}

	data class Box(val minX: Int, val minY: Int, val minZ: Int, val maxX: Int, val maxY: Int, val maxZ: Int) {
		val sx get() = maxX - minX + 1
		val sy get() = maxY - minY + 1
		val sz get() = maxZ - minZ + 1
		val volume get() = sx.toLong() * sy * sz
	}

	data class Result(val points: List<Vec3>, val found: Boolean)

	data class Outcome(val found: Boolean, val reason: String?, val nodes: Int, val attempts: Int, val points: Int) {
		override fun toString() = if (found) "route found ($points points, $nodes nodes, $attempts tries)"
			else "no route: $reason ($nodes nodes, $attempts tries)"
	}

	@Volatile var result: Result? = null
		private set

	@Volatile var lastOutcome: Outcome? = null
		private set

	var plansStarted = 0; private set

	var synchronous = false

	@Volatile private var generation = 0
	private var job: Job? = null
	private var lastStart: BlockPos? = null
	private var lastTarget: BlockPos? = null
	private var tick = 0
	private var lastPlanTick = Int.MIN_VALUE / 2

	private class Capture(val box: Box, val start: BlockPos, val target: BlockPos, val cells: ByteArray) {
		var column = 0
		val columns get() = box.sx * box.sz
	}
	private var capture: Capture? = null

	fun clear() {
		generation++
		job?.cancel(); job = null
		capture = null
		result = null; lastStart = null; lastTarget = null
	}

	private fun point(p: SoulAStar.P) = Vec3(p.x + 0.5, p.y + drawHeight, p.z + 0.5)
	private fun point(b: BlockPos) = Vec3(b.x + 0.5, b.y + drawHeight, b.z + 0.5)
	private fun straight(start: BlockPos, target: BlockPos) = Result(listOf(point(start), point(target)), false)

	private fun boxFor(start: BlockPos, target: BlockPos, box: Box?, minY: Int, maxY: Int): Box {
		val b = box ?: Box(
			minOf(start.x, target.x) - margin, minOf(start.y, target.y) - margin, minOf(start.z, target.z) - margin,
			maxOf(start.x, target.x) + margin, maxOf(start.y, target.y) + margin, maxOf(start.z, target.z) + margin)
		return b.copy(minY = maxOf(b.minY, minY), maxY = minOf(b.maxY, maxY))
	}

	fun update(start: BlockPos?, target: BlockPos?, source: Source, minY: Int, maxY: Int, box: Box? = null) {
		if (target == null || start == null) { clear(); return }
		tick++
		val targetChanged = target != lastTarget
		capture?.let { c ->
			if (targetChanged) { capture = null } else { step(c, source); return }
		}
		val moved = lastStart?.let { it.distSqr(start) >= replanDistSq } ?: true
		if (!moved && !targetChanged) return
		if (!targetChanged && (job?.isActive == true || tick - lastPlanTick < minTicksBetween)) return
		if (targetChanged) {
			job?.cancel(); job = null; generation++
			if (!keepLastFound || !straightFallback) result = null
		}
		lastStart = start
		lastTarget = target
		lastPlanTick = tick
		val b = boxFor(start, target, box, minY, maxY)
		val fallback = if (straightFallback) straight(start, target) else null
		if (b.sy <= 0 || b.sx > maxSpan || b.sz > maxSpan || b.sy > maxSpan || b.volume > maxVolume) {
			lastOutcome = Outcome(false, "snapshot box too big (${b.sx}x${b.sy}x${b.sz})", 0, 0, 0)
			if (!keepLastFound || result?.found != true) result = fallback
			return
		}
		if (result == null || targetChanged) result = fallback
		plansStarted++
		val c = Capture(b, start, target, ByteArray(b.volume.toInt()))
		capture = c
		step(c, source)
	}

	private fun step(c: Capture, source: Source) {
		val b = c.box
		var budget = cellsPerTick
		while (c.column < c.columns && (budget > 0 || budget == cellsPerTick)) {
			val x = c.column / b.sz; val z = c.column % b.sz
			val wx = b.minX + x; val wz = b.minZ + z
			val loaded = source.columnLoaded(wx, wz)
			for (y in 0 until b.sy) {
				val i = (x * b.sy + y) * b.sz + z
				c.cells[i] = if (loaded) source.cell(wx, b.minY + y, wz) else SoulAStar.BLOCKED
			}
			budget -= b.sy
			c.column++
		}
		if (c.column < c.columns) return
		capture = null
		startSearch(BlockSnapshot(b.minX, b.minY, b.minZ, b.sx, b.sy, b.sz, c.cells), c.start, c.target)
	}

	private fun startSearch(snap: BlockSnapshot, start: BlockPos, target: BlockPos) {
		val gen = ++generation
		val work = {
			val from = startCell(snap, start)
			val stats = SoulAStar.Stats()
			val path = from?.let { SoulAStar.findWithRetries(snap, it, SoulAStar.P(target.x, target.y, target.z), nodeCap, maxDist, goalRadius, stats, goalUp, goalSight) }
			if (gen == generation) {
				lastOutcome = Outcome(path != null, when {
					path != null -> null
					from == null -> "start not standable (feet ${start.x},${start.y},${start.z}: ${(start.y - 3..start.y + 2).joinToString("") { "${snap.cell(start.x, it, start.z)}" }})"
					else -> "goal not reached within the ${nodeCap}-node cap / ${maxDist}-block range"
				}, stats.expanded, stats.attempts, path?.size ?: 0)
				result = when {
					path != null -> Result(SoulAStar.simplify(path).map { point(it) } + point(target), true)
					straightFallback -> straight(start, target)
					else -> null
				}
			}
		}
		if (synchronous) work() else job = Apex.coroutineScope.launch(Dispatchers.Default) { work() }
	}

	companion object {

		fun startCell(g: SoulAStar.Grid, start: BlockPos): SoulAStar.P? =
			listOf(0, -1, -2, -3, 1).map { SoulAStar.P(start.x, start.y + it, start.z) }.firstOrNull { SoulAStar.standable(g, it.x, it.y, it.z) }
	}

	class MinecraftSource(private val level: Level) : Source {
		private val m = BlockPos.MutableBlockPos()
		override fun columnLoaded(x: Int, z: Int) = level.chunkSource.hasChunk(x shr 4, z shr 4)
		override fun cell(x: Int, y: Int, z: Int): Byte {
			m.set(x, y, z)
			val state = level.getBlockState(m)
			return when {
				state.`is`(BlockTags.CLIMBABLE) || state.`is`(Blocks.VINE) -> SoulAStar.CLIMB
				state.`is`(BlockTags.FIRE) -> SoulAStar.AIR
				state.`is`(BlockTags.STAIRS) -> SoulAStar.STAIR
				else -> {
					val shape = state.getCollisionShape(level, m)
					if (shape.isEmpty) SoulAStar.AIR
					else shape.bounds().let { b -> SoulAStar.classifyShape(b.minX, b.maxX, b.minY, b.maxY, b.minZ, b.maxZ) }
				}
			}
		}
	}
}

object FairySoulPathfinder {
	const val NODE_CAP = 20_000
	const val MAX_DIST = 96

	private val planner = BlockPathPlanner(
		nodeCap = NODE_CAP, maxDist = MAX_DIST, replanDistSq = 9.0, minTicksBetween = 0,
		cellsPerTick = Int.MAX_VALUE, straightFallback = true, keepLastFound = false,
		margin = 8, maxSpan = MAX_DIST * 2 + 1, maxVolume = 2_000_000,

		goalRadius = 3.0, goalUp = 4, goalSight = true,
	)
	private val log = org.slf4j.LoggerFactory.getLogger("Apex/ApexSoulPath")
	private var loggedOutcome: String? = null

	val result: BlockPathPlanner.Result? get() = planner.result

	fun clear() = planner.clear()

	fun update(target: BlockPos?) {
		val player = Client.player
		val level = Client.level
		if (target == null || player == null || level == null) { clear(); return }
		planner.update(player.blockPosition(), target, BlockPathPlanner.MinecraftSource(level), level.minY, level.maxY)

		val o = planner.lastOutcome?.let { "soul ${target.x},${target.y},${target.z}: ${if (it.found) "route found (${it.points} points)" else it.reason}" }
		if (o != null && o != loggedOutcome) { loggedOutcome = o; log.info("[ApexSoulPath] {}", o) }
	}
}
